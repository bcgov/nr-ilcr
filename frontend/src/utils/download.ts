import { extractDetail } from '@/utils/error'

/**
 * Trigger a browser download of a {@link Blob} under {@code filename} via a temporary object URL and a
 * synthetic anchor click. Used for binary API responses (e.g. the Print Schedules PDF) that the app
 * fetches with {@code responseType: 'blob'} rather than navigating the browser to the endpoint.
 */
export function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  try {
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  } finally {
    URL.revokeObjectURL(url)
  }
}

/**
 * The RFC 7807 {@code detail} from an axios error whose response body is a {@link Blob}. When a
 * request uses {@code responseType: 'blob'}, a SUCCESS is the file but an ERROR body (the
 * {@code application/problem+json} the backend returns for 400/404/409) also arrives as a Blob, so it
 * must be read and parsed rather than accessed as an object. Falls back to the plain-object
 * {@link extractDetail} for non-blob errors.
 */
export async function extractBlobDetail(error: unknown): Promise<string | undefined> {
  const data = (error as { response?: { data?: unknown } } | undefined)?.response?.data
  if (data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text()) as { detail?: string }
      return parsed.detail
    } catch {
      return undefined
    }
  }
  return extractDetail(error)
}
