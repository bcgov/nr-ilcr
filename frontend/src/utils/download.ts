import { extractDetail } from '@/utils/error'

/**
 * Trigger a browser download of a {@link Blob} under {@code filename} via a temporary object URL and a
 * synthetic anchor click. Used for binary API responses (e.g. the Print Schedules PDF) that the app
 * fetches with {@code responseType: 'blob'} rather than navigating the browser to the endpoint.
 */
export function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  // Defer revocation: revoking synchronously in the same task as the synthetic <a download> click can
  // cancel the download in Firefox/Safari (Chrome tolerates it). Let the browser start reading the blob
  // first. A detached anchor needs no appendChild/remove.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/** A response that arrived as {@code application/pdf} but is not a complete PDF. */
export class TruncatedPdfError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options)
    this.name = 'TruncatedPdfError'
  }
}

const PDF_MAGIC = '%PDF-'
const PDF_TRAILER = '%%EOF'

/**
 * How far back from the end to look for the trailer. {@code %%EOF} is the last token of a
 * well-formed PDF, but incremental updates and linearised files can leave whitespace or a short
 * pad after it, so the check is "the tail CONTAINS it" rather than "the file ENDS with it".
 */
const TRAILER_WINDOW = 1024

const INCOMPLETE =
  'The report did not download completely. Please try again — if it keeps failing, contact the ' +
  'ILCR administrator.'

/**
 * Reject a PDF response that is not whole.
 *
 * <p><strong>A backstop, not the guarantee.</strong> It used to be the only defence: the report
 * endpoints exported INSIDE the streamed response, so the 200 and the {@code application/pdf}
 * headers were committed before the first byte existed and an export failure could not be turned
 * into {@code problem+json} by any server-side handler. Checking the bytes here could never close
 * that hole, because a truncated PDF can carry a plausible {@code %PDF-} header and even a
 * {@code %%EOF} trailer from an earlier incremental section — so a cut file can pass this check.
 *
 * <p>The backend no longer relies on it. It exports to a temp file BEFORE choosing a status code
 * (`PdfSpooler`), so a generation failure is now an ordinary 500 {@code undefinedError} with no
 * body, and it sends a real {@code Content-Length}, so a transfer cut short is a short read the
 * browser fails outright — axios rejects and the {@code .catch} branch runs. Both failures reach
 * the same banner as any other error, and neither depends on this function.
 *
 * <p>What is left for it: a response that is length-complete but not a PDF at all — an intermediary
 * (gateway, proxy, SSO re-auth) answering 200 with an HTML error page, or one that re-chunks and
 * drops the {@code Content-Length} the backend set. Cheap, and it fails the same way everything
 * else does, so it stays.
 *
 * <p>Throws {@link TruncatedPdfError}, whose message {@link extractBlobDetail} surfaces, so every
 * call site's existing error branch puts the retryable banner up and saves no file.
 *
 * <p><strong>Fails CLOSED when the blob cannot be read.</strong> A rejected read is not evidence
 * that the body is fine — it is the absence of evidence either way, so passing on it would defeat
 * the point of checking at all. The unreadable case is also not a rescued download: the browser has
 * to read the same blob to save it. The original failure travels as {@code cause}.
 */
export async function assertCompletePdf(blob: Blob): Promise<void> {
  // A missing body fails closed for the same reason an unreadable one does; it also keeps the
  // property access below from throwing a raw TypeError past every call site's error branch.
  if (!blob || blob.size === 0) {
    throw new TruncatedPdfError(INCOMPLETE)
  }
  let head: string
  let tail: string
  try {
    head = await blob.slice(0, PDF_MAGIC.length).text()
    tail = await blob.slice(Math.max(0, blob.size - TRAILER_WINDOW)).text()
  } catch (cause) {
    throw new TruncatedPdfError(INCOMPLETE, { cause })
  }
  if (!head.startsWith(PDF_MAGIC) || !tail.includes(PDF_TRAILER)) {
    throw new TruncatedPdfError(INCOMPLETE)
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
  // Not an axios error at all: the stream guard above throws this AFTER a 200, so it reaches the
  // same error branches and needs its message carried through them.
  if (error instanceof TruncatedPdfError) {
    return error.message
  }
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
