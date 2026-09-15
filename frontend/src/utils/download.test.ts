import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  assertCompletePdf,
  extractBlobDetail,
  extractBlobMessages,
  triggerDownload,
  TruncatedPdfError,
} from './download'

describe('triggerDownload', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('creates an object URL, clicks a download anchor, and revokes the URL only after the click task', () => {
    vi.useFakeTimers()
    const createObjectURL = vi.fn(() => 'blob:mock-url')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    triggerDownload(new Blob(['%PDF-1.4']), 'schedules_print.pdf')

    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(click).toHaveBeenCalledTimes(1)
    // Revocation is deferred (revoking in the click's task cancels the download in Firefox/Safari).
    expect(revokeObjectURL).not.toHaveBeenCalled()

    vi.runAllTimers()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
    vi.useRealTimers()
  })
})

describe('assertCompletePdf', () => {
  const complete = () => new Blob([`%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n%%EOF\n`])

  it('accepts a PDF carrying both the header and the trailer', async () => {
    await expect(assertCompletePdf(complete())).resolves.toBeUndefined()
  })

  it('accepts a trailer followed by padding, since %%EOF need not be the final byte', async () => {
    await expect(
      assertCompletePdf(new Blob(['%PDF-1.7 body %%EOF\n\n   '])),
    ).resolves.toBeUndefined()
  })

  it('rejects a body truncated after the header — the shape a failed export streams', async () => {
    // The header survives truncation (it is at the FRONT), which is why the trailer is the check
    // that discriminates. This is the exact 200-application/pdf-then-die case from the backend.
    await expect(assertCompletePdf(new Blob(['%PDF-1.4 half a document']))).rejects.toThrow(
      TruncatedPdfError,
    )
  })

  it('rejects an empty body', async () => {
    await expect(assertCompletePdf(new Blob([]))).rejects.toThrow(TruncatedPdfError)
  })

  it('rejects a body that was never a PDF', async () => {
    await expect(assertCompletePdf(new Blob(['<html>gateway timeout</html>']))).rejects.toThrow(
      TruncatedPdfError,
    )
  })

  it('finds a trailer only within the tail window, not arbitrarily far back', async () => {
    // %%EOF buried under 2KB of trailing bytes is not a well-formed end-of-file.
    const buried = new Blob([`%PDF-1.4 %%EOF${'x'.repeat(2048)}`])
    await expect(assertCompletePdf(buried)).rejects.toThrow(TruncatedPdfError)
  })

  it('FAILS CLOSED when the blob cannot be read, and carries the original failure as cause', async () => {
    // Review feedback on #415: a rejected read is not evidence the body is whole. This check is the
    // only thing between an already-committed stream and the user's disk, so an unreadable blob has
    // to take the same retryable path as a short one.
    const unreadable = {
      size: 4096,
      slice: () => ({ text: () => Promise.reject(new Error('NotReadableError')) }),
    } as unknown as Blob
    await expect(assertCompletePdf(unreadable)).rejects.toThrow(TruncatedPdfError)
    await assertCompletePdf(unreadable).catch((error: unknown) => {
      expect((error as Error).cause).toBeInstanceOf(Error)
      expect(((error as Error).cause as Error).message).toBe('NotReadableError')
    })
  })

  it('FAILS CLOSED when the body is not a Blob at all', async () => {
    const notABlob = { size: 12 } as unknown as Blob
    await expect(assertCompletePdf(notABlob)).rejects.toThrow(TruncatedPdfError)
  })
})

describe('extractBlobDetail', () => {
  it('surfaces the truncated-stream message, which arrives as a bare Error after a 200', async () => {
    const message = await extractBlobDetail(new TruncatedPdfError('did not download completely'))
    expect(message).toBe('did not download completely')
  })

  it('parses the RFC 7807 detail from a Blob error body (responseType: blob)', async () => {
    const blob = new Blob([JSON.stringify({ detail: 'Select at least one schedule.' })], {
      type: 'application/problem+json',
    })
    expect(await extractBlobDetail({ response: { data: blob } })).toBe(
      'Select at least one schedule.',
    )
  })

  it('returns undefined when the blob body is not JSON', async () => {
    expect(await extractBlobDetail({ response: { data: new Blob(['not json']) } })).toBeUndefined()
  })

  it('falls back to the plain-object detail extractor for non-blob errors', async () => {
    expect(await extractBlobDetail({ response: { data: { detail: 'plain detail' } } })).toBe(
      'plain detail',
    )
  })
})

describe('extractBlobMessages', () => {
  const FALLBACK = 'Unable to generate the data extract.'

  const problemBlob = (body: unknown) =>
    new Blob([JSON.stringify(body)], { type: 'application/problem+json' })

  it('returns EVERY distinct message text from a Blob body, in server order', async () => {
    // The reason this function exists: extractBlobDetail would collapse these to the single joined
    // `detail`, undoing the accumulating gate. The repeated text proves the dedup is inherited from
    // extractMessages rather than reimplemented here — one copy survives, in first-seen position.
    const blob = problemBlob({
      status: 400,
      detail: 'Start Year: Value is required.; Please select at least one Mill for extracting.',
      messages: [
        { key: 'startYear', text: 'Start Year: Value is required.' },
        { key: 'mills', text: 'Please select at least one Mill for extracting.' },
        { key: 'mills-again', text: 'Please select at least one Mill for extracting.' },
        { key: 'schedules', text: 'Please select at least one Schedule for extracting.' },
      ],
    })
    expect(await extractBlobMessages({ response: { data: blob } }, FALLBACK)).toEqual([
      'Start Year: Value is required.',
      'Please select at least one Mill for extracting.',
      'Please select at least one Schedule for extracting.',
    ])
  })

  it('returns [detail] when the Blob body carries no messages array', async () => {
    const blob = problemBlob({ status: 500, detail: 'Something was rejected.' })
    expect(await extractBlobMessages({ response: { data: blob } }, FALLBACK)).toEqual([
      'Something was rejected.',
    ])
  })

  it('returns [fallback] when the Blob body is not JSON', async () => {
    // A 502 HTML page from a gateway arrives as a Blob too; it is not evidence of any refusal, so
    // the caller's own last-resort sentence is what goes on screen — the same thing extractMessages
    // does with a response that carried no problem body.
    const html = new Blob(['<html>bad gateway</html>'])
    expect(await extractBlobMessages({ response: { data: html } }, FALLBACK)).toEqual([FALLBACK])
  })

  it('surfaces the truncated-stream message, which arrives as a bare Error after a 200', async () => {
    expect(
      await extractBlobMessages(new TruncatedPdfError('did not download completely'), FALLBACK),
    ).toEqual(['did not download completely'])
  })

  it('defers to extractMessages for a non-blob axios-shaped error', async () => {
    // The blob path is an ADAPTER over extractMessages, not a second implementation: a plain-object
    // body has to give exactly what the non-blob pages get, dedup and detail precedence included.
    const plain = {
      response: {
        data: {
          status: 400,
          detail: 'joined detail',
          messages: [
            { key: 'a', text: 'first' },
            { key: 'b', text: 'second' },
            { key: 'c', text: 'first' },
          ],
        },
      },
    }
    expect(await extractBlobMessages(plain, FALLBACK)).toEqual(['first', 'second'])
    const detailOnly = { response: { data: { detail: 'only detail' } } }
    expect(await extractBlobMessages(detailOnly, FALLBACK)).toEqual(['only detail'])
    // No response at all (a network failure) is the fallback, by the same deferral.
    expect(await extractBlobMessages(new Error('Network Error'), FALLBACK)).toEqual([FALLBACK])
  })
})
