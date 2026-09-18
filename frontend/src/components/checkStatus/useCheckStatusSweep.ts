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
 * `reloadToken` re-issues the sweep for the SAME mill/year when the caller changes it: a status
 * transition has to re-read the track from the server, since the transition reply carries only its own
 * outcome. The last settled result stays current while that request is open, so the page keeps
 * rendering instead of flashing its loading state — and `isReloading` names that window, because what
 * is on screen during it is the status the transition just moved past.
 */
export function useCheckStatusSweep(
  millId: number | null,
  year: number | null,
  reloadToken = 0,
): UseCheckStatusSweepResult {
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    if (millId == null || year == null) {
      return
    }
    const controller = new AbortController()
    let active = true
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
          setSettled({ kind: 'error', millId, year, reloadToken, detail: LOAD_FAILED })
        } else {
          setSettled({ kind: 'data', millId, year, reloadToken, data: body })
        }
      })
      .catch((error: unknown) => {
        // An abort rejects too; `active` is already false by then, so it never becomes an error.
        if (active) {
          setSettled({
            kind: 'error',
            millId,
            year,
            reloadToken,
            detail: extractDetail(error) || LOAD_FAILED,
          })
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
