import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  assertCompletePdf,
  extractBlobDetail,
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
