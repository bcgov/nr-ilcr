import { useEffect, useRef } from 'react'
import useMillYear from '@/context/millYear/useMillYear'

type UseScheduleContextGuardResult = {
  readonly millId: number | null
  readonly year: number | null
  /** True when either half of the context is unset, so the page must render its own gate instead. */
  readonly contextMissing: boolean
  /** False once mill/year has changed since the caller's request was dispatched. */
  readonly isCurrent: () => boolean
}

/**
 * The shared "is this response still for the context that asked for it?" guard for the schedule
 * pages. The GET path's stale-response guard lives inside {@code useScheduleDocument} (its active
 * flag); the write/check handlers need their own: a response dispatched under one mill/year must
 * never apply after the context changes (the document it echoes belongs to the OLD context). Each
 * handler closes over its dispatch-time context; the ref always holds the current one.
 */
export function useScheduleContextGuard(): UseScheduleContextGuardResult {
  const { millId, year } = useMillYear()

  const contextRef = useRef({ millId, year })
  useEffect(() => {
    contextRef.current = { millId, year }
  }, [millId, year])

  return {
    millId,
    year,
    contextMissing: millId === null || year === null,
    isCurrent: () => contextRef.current.millId === millId && contextRef.current.year === year,
  }
}
