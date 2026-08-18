import { afterEach, describe, expect, it, vi } from 'vitest'
import { extractBlobDetail, triggerDownload } from './download'

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

describe('extractBlobDetail', () => {
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
