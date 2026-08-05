import type { FC } from 'react'
import type WorkingContext from '@/interfaces/WorkingContext'
import type { TrackStatus } from '@/interfaces/WorkingContext'

// The legacy fallback for a blank status date [UserSessionMB.java:374].
const NOT_INITIATED = 'Not Initiated'

// One track's status line. Per AR6/AC2 each track renders independently by its OWN status; the two
// legacy cross-track bugs (shared 1-10 guard + crossed date pick) are deliberately not reproduced.
// `description || code` (not `??`) so a blank string also collapses to the fallback — legacy-faithful
// (legacy blank date → "Not Initiated"); the 1.2 backend already collapses blank→null, so this only
// guards the degenerate out-of-contract case.
const statusLine = (label: 'Sch 1-10' | 'Sch 11', status: TrackStatus): string => {
  const description = status.description || status.code || ''
  const date = status.date || NOT_INITIATED
  return `${label} - Status: ${description} - Date: ${date}`
}

// The three legacy working-context lines (Mill / Sch 1-10 / Sch 11). Presentational only: the caller
// owns the wrapper element + class names, so the global ContextBanner and the per-schedule tombstone
// style them differently while sharing the exact text. Defensive `?? ''` on the nullable mill columns
// (1.1 contract note).
const WorkingContextLines: FC<{ context: WorkingContext; lineClassName?: string }> = ({
  context,
  lineClassName,
}) => {
  const { millNumber, millName, reportYear, schedules1To10Status, schedule11Status } = context
  return (
    <>
      <p className={lineClassName}>
        {`Mill: ${millNumber ?? ''} ${millName ?? ''} - Year: ${reportYear}`}
      </p>
      {schedules1To10Status && (
        <p className={lineClassName}>{statusLine('Sch 1-10', schedules1To10Status)}</p>
      )}
      {schedule11Status && (
        <p className={lineClassName}>{statusLine('Sch 11', schedule11Status)}</p>
      )}
    </>
  )
}

export default WorkingContextLines
