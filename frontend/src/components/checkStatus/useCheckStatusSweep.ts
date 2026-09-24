import { useEffect, useState } from 'react'
import apiService from '@/service/api-service'
import type CheckStatusSweepResponse from '@/interfaces/CheckStatusSweep'
import { extractDetail } from '@/utils/error'

/** Client fallback for a failure that carries no ProblemDetail (network, non-JSON 5xx). */
export const LOAD_FAILED = 'Unable to load the Check Status results.'

type UseCheckStatusSweepResult = {
  readonly data: CheckStatusSweepResponse | null
  readonly isLoading: boolean
  readonly errorDetail: string | null
  /**
   * A reload is open for the context already on screen: the last settled result answered an EARLIER
   * `reloadToken`, so what the caller is rendering is the pre-transition state. Distinct from
   * `isLoading`, which means there is nothing to render at all. A caller whose controls are gated on
   * the status this hook returns must treat the window as busy — otherwise the gate answers from a
   * status the server has already moved past.
   */
  readonly isReloading: boolean
}

/**
 * One settled request, tagged with the context it was issued for — and with the `reloadToken` it
 * answered, so "this result predates the reload the caller asked for" is derivable in render like
 * everything else here, rather than tracked as a second piece of state.
 */
type Settled =
  | {
      readonly kind: 'data'
      readonly millId: number
      readonly year: number
      readonly reloadToken: number
      readonly data: CheckStatusSweepResponse
    }
  | {
      readonly kind: 'error'
      readonly millId: number
      readonly year: number
      readonly reloadToken: number
      readonly detail: string
    }

/**
 * GET /v1/check-status for the working mill/year, re-issued on every mill/year change and on every
 * mount — the correct-and-re-check loop IS the remount, so there is deliberately no retry, no polling
 * and no refetch-on-focus: one sweep is sixty server round trips.
 *
 * Everything the page reads is DERIVED in render from one piece of state, the last settled request
 * tagged with the context it answered. A result for another mill/year is simply not current, so a
 * context change shows the loading state on the very same render — no reset-in-effect, no blank
 * frame between the old result disappearing and the spinner appearing. The request in flight for the
 * previous context is aborted in the effect's cleanup, so its response is never even parsed, and the
 * `active` flag guards the promise chain that the abort rejects.
 *
 * Errors are surfaced (this is the page's content, not passive chrome), as the verbatim `detail` when
 * there is one. A 200 whose body names a different mill/year than the request is treated as a load
 * failure rather than rendered.
 *
 * `reloadToken` re-issues the sweep for the SAME context when the page knows the server's state has
 * moved (a submit or a verify landed, or a 409 said the page was stale). Bumping it re-runs the
 * effect even though `millId` and `year` are unchanged. Because the page renders from the last
 * settled result, the previous verdicts stay on screen while the re-fetch is in flight — an in-place
 * swap, with no loading frame. A re-fetch that FAILS must not take that result away: a page that has
 * just shown "successfully submitted" cannot fall back to a full-page load error over a transient
 * failure, so a failure for a context that already holds data keeps the data. The initial load has no
 * data to keep, so its failure behaviour is unchanged.
 *
 * `isReloading` names that in-flight window, because what is on screen during it is the status the
 * transition just moved past. Unlike main's effect-trigger use, the token IS read here — the settled
 * result is tagged with the one it answered — so the window is derived in render like everything else
 * rather than tracked as a second piece of state.
 */
export function useCheckStatusSweep(
  millId: number | null,
  year: number | null,
  /** Bump to re-fetch the same context after an action moved the server's state. */
  reloadToken = 0,
): UseCheckStatusSweepResult {
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    if (millId == null || year == null) {
      return
    }
    const controller = new AbortController()
    let active = true
    const fail = (detail: string) =>
      setSettled((previous) =>
        previous?.kind === 'data' && previous.millId === millId && previous.year === year
          ? // A failed RELOAD keeps the data that was already correct — but the reload is over, so it
            // is re-tagged with the token it answered. Returning `previous` unchanged would leave
            // `isReloading` true for good and strand every control gated on it.
            { ...previous, reloadToken }
          : { kind: 'error', millId, year, reloadToken, detail },
      )
    apiService
      .getAxiosInstance()
      .get<CheckStatusSweepResponse>(`/v1/check-status?millId=${millId}&year=${year}`, {
        signal: controller.signal,
      })
      .then((response) => {
        if (!active) {
          return
        }
        const body = response.data
        if (body.millId !== millId || body.year !== year) {
          fail(LOAD_FAILED)
        } else {
          setSettled({ kind: 'data', millId, year, reloadToken, data: body })
        }
      })
      .catch((error: unknown) => {
        // An abort rejects too; `active` is already false by then, so it never becomes an error.
        if (active) {
          fail(extractDetail(error) || LOAD_FAILED)
        }
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [millId, year, reloadToken])

  const hasContext = millId != null && year != null
  const current =
    settled != null && settled.millId === millId && settled.year === year ? settled : null
  return {
    data: current?.kind === 'data' ? current.data : null,
    errorDetail: current?.kind === 'error' ? current.detail : null,
    isLoading: hasContext && current === null,
    // True from the render that bumps the token until that request settles — the effect has not even
    // dispatched yet on the first of those renders, which is the point: there must be no frame in
    // which the caller believes the stale status is current.
    isReloading: hasContext && current !== null && current.reloadToken !== reloadToken,
  }
}

export default useCheckStatusSweep
