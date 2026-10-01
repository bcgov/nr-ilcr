import type { ProblemBody } from '@/interfaces/WorkingContext'

// The RFC 7807 `detail` from an axios error's problem+json body, if any.
export function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    return (error as { response?: { data?: ProblemBody } }).response?.data?.detail
  }
  return undefined
}

/**
 * The verbatim per-field message(s) from a problem+json body, for the endpoints that report several
 * refusals at once. Falls back to the joined `detail`, then to the caller's own last-resort text —
 * which is only reached when the server sent no problem body at all.
 *
 * Deduplicated: the contract does not guarantee distinct texts, a repeated text adds nothing on
 * screen, and unique texts keep a rendered notification list's React keys collision-free.
 *
 * Shared rather than page-local. The original note here said per-field extraction "stays
 * page-specific because each page owns its fallback text" — taking the fallback as an argument
 * settles that, and by the third identical copy the duplication cost more than the coupling.
 */
export function extractMessages(error: unknown, fallback: string): string[] {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: ProblemBody } }).response?.data
    const texts = [
      ...new Set(
        (data?.messages ?? [])
          .map((message) => message.text)
          .filter((text): text is string => Boolean(text)),
      ),
    ]
    if (texts.length > 0) {
      return texts
    }
    if (data?.detail) {
      return [data.detail]
    }
  }
  return [fallback]
}
