import { useCallback, useState } from 'react'
import { extractDetail } from '@/utils/error'

/**
 * The banner + in-flight state every schedule page keeps around its writes: the success line echoed
 * by the API, the "Action failed" line, the Check Status result, and the lock that stops a second
 * request while one is out. Extracted from the Schedule 7A/7B pages, which had grown identical copies
 * of all four pieces of state and the same four helpers over them.
 *
 * `TCheckResult` is the page's own check-status response shape — this hook only stores it; rendering
 * it is {@code CheckStatusNotifications}' job.
 */
type UseScheduleBannersResult<TCheckResult> = {
  readonly saving: boolean
  readonly message: string | null
  readonly actionError: string | null
  readonly checkResult: TCheckResult | null
  readonly setMessage: (text: string | null) => void
  /** For the page's OWN gate text (e.g. "correct these rows"); API failures go through `failed`. */
  readonly setActionError: (text: string | null) => void
  readonly setCheckResult: (result: TCheckResult | null) => void
  /** Drop every banner. Called before an action so a failure cannot leave a stale success notice. */
  readonly clearBanners: () => void
  /** `clearBanners` plus releasing the in-flight lock: the fresh-document (mill/year change) reset. */
  readonly resetBanners: () => void
  /** Surface an API failure, preferring its verbatim ProblemDetail over the caller's fallback. */
  readonly failed: (error: unknown, fallback: string) => void
  /**
   * Dispatch a guarded request. Returns the settled chain so a caller can await the WHOLE operation;
   * an {@code onSuccess} that itself returns a promise holds the in-flight lock until that promise
   * settles too (see {@code RunOptions.onSuccess}).
   */
  readonly run: <T>(request: Promise<{ data: T }>, options: RunOptions<T>) => Promise<void>
}

type RunOptions<T> = {
  /**
   * Shown only when the rejection carries no ProblemDetail detail of its own (AD-8). Pass {@code null}
   * to fail SILENTLY — leave no banner at all — for a resolve whose absence is self-explanatory (e.g.
   * Schedule 5's copy hint: a blank name in an obviously-new panel already carries the instruction).
   */
  readonly fallback: string | null
  /**
   * Applied only when the request resolves under the still-current context.
   *
   * <p>If it returns a promise, {@code run} awaits it before releasing the in-flight lock, so a write
   * that chains a follow-up request (typically a re-GET) is ONE locked operation. Returning the
   * chained {@code run(...)} is the idiom. Without this the lock released when the FIRST request
   * settled while the follow-up was still out, leaving a window in which `saving` is false but the
   * page state is mid-transition — on Schedule 2 that let a Save re-create the schedule the user had
   * just deleted, using the pre-delete figures, because the form had not been re-seeded yet
   * (defect #292, PR #351 review).
   */
  readonly onSuccess: (data: T) => void | Promise<unknown>
}

export const useScheduleBanners = <TCheckResult>(
  isCurrent: () => boolean,
): UseScheduleBannersResult<TCheckResult> => {
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<TCheckResult | null>(null)

  const clearBanners = useCallback(() => {
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
  }, [])

  const resetBanners = useCallback(() => {
    setSaving(false)
    clearBanners()
  }, [clearBanners])

  const failed = useCallback((error: unknown, fallback: string) => {
    // Keep entered values for correction; surface the API's verbatim detail.
    setActionError(extractDetail(error) || fallback)
  }, [])

  // Deliberately NOT memoized: `isCurrent` is a fresh closure over the render's mill/year, and a
  // memoized `run` would keep dispatching under a stale one.
  const run = <T>(request: Promise<{ data: T }>, { fallback, onSuccess }: RunOptions<T>) => {
    setSaving(true)
    // RETURNING onSuccess's result is what makes a chained follow-up part of this operation: a
    // promise returned here is awaited by the chain, so `.finally` — and the lock release — waits
    // for it. A void return behaves exactly as before.
    return request
      .then((response) => {
        if (isCurrent()) {
          return onSuccess(response.data)
        }
        return undefined
      })
      .catch((error: unknown) => {
        // fallback === null → fail silently (no banner); see RunOptions.fallback.
        if (isCurrent() && fallback !== null) {
          failed(error, fallback)
        }
      })
      .finally(() => {
        // On a context change the reset already cleared `saving`, and a request dispatched under the
        // NEW context may be in flight — a stale finally must not release its lock.
        if (isCurrent()) {
          setSaving(false)
        }
      })
  }

  return {
    saving,
    message,
    actionError,
    checkResult,
    setMessage,
    setActionError,
    setCheckResult,
    clearBanners,
    resetBanners,
    failed,
    run,
  }
}

export default useScheduleBanners
