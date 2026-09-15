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
}

/**
 * GET /v1/check-status for the working mill/year, re-issued on every mill/year change and on every
 * mount — the correct-and-re-check loop IS the remount, so there is deliberately no retry, no polling
 * and no refetch-on-focus: one sweep is sixty server round trips.
 *
 * Errors are surfaced (this is the page's content, not passive chrome), as the verbatim `detail` when
 * there is one. A response for a PREVIOUS context is dropped twice over: the `active` flag ignores a
 * late resolve after the context changed, and staleness is re-derived in render from the body's own
 * millId/year so an in-flight previous context never lingers.
 */
export function useCheckStatusSweep(
  millId: number | null,
  year: number | null,
): UseCheckStatusSweepResult {
  const [data, setData] = useState<CheckStatusSweepResponse | null>(null)
  const [isLoading, setIsLoading] = useState(() => millId != null && year != null)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)

  useEffect(() => {
    if (millId == null || year == null) {
      return
    }
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<CheckStatusSweepResponse>(`/v1/check-status?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          if (response.data.millId !== millId || response.data.year !== year) {
            setErrorDetail(LOAD_FAILED)
          } else {
            setData(response.data)
          }
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || LOAD_FAILED)
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [millId, year])

  const stale = data != null && (data.millId !== millId || data.year !== year)
  return { data: stale ? null : data, isLoading, errorDetail }
}

export default useCheckStatusSweep
