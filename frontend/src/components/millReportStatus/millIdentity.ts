import type MillReportStatusRow from '@/interfaces/MillReportStatusRow'

/**
 * How a mill names itself on the Mill Status Report — ONE definition, because two derivations of the
 * same fallback chain drifted apart in review round 1.
 *
 * The drill-down needs a mill's identity twice: the table needs a visible LABEL for the Mill cell's
 * control, and the page needs a download FILENAME. Both fall back through the same chain, and the
 * backend applies the same fallback again when it sets `Content-Disposition`
 * (`ReportController.drillDownFilename`) — so this is one rule with three consumers, and it belongs
 * in one place rather than being spelled out at each call site.
 */

/**
 * Absent, for a nullable text column: null, undefined, empty, or whitespace-only.
 *
 * **Whitespace-only counts, and that is the point.** `??` alone does not: it passes `''` and
 * `'   '` straight through as if they were values. `MILL.MILL_NAME` is a nullable `VARCHAR2(100)`
 * with no non-blank constraint (`backend/src/test/resources/db/V1__the_schedule1_snapshot.sql:9`),
 * so a whitespace-only name is a reachable delivery state — and with `??` it produced a ghost
 * Button whose label was invisible and whose accessible name was
 * "Generate the mill information report for    ", which is precisely what the fallback chain exists
 * to prevent. The backend already used `isBlank` for its half of the filename contract, so `??`
 * also made the two sides disagree.
 */
const isBlank = (value: string | null | undefined): boolean =>
  value === null || value === undefined || value.trim() === ''

/**
 * A mill's number, or `null` when it has none — the sortable Mill Number column's value and the
 * filename's first choice.
 *
 * Deliberately shared with the sort extractor so "no mill number" is ONE case everywhere. It also
 * fixes a quiet sort bug: `Number('   ')` is `0`, not `NaN`, so a whitespace-only number used to
 * sort ahead of every real mill instead of last.
 */
export const millNumberOrNull = (row: MillReportStatusRow): string | null =>
  isBlank(row.millNumber) ? null : (row.millNumber as string).trim()

/**
 * The Mill cell's visible label and accessible name: the mill NAME, falling back to its number and
 * then its id.
 *
 * A mill with neither name nor number is referential corruption rather than an absent optional
 * field, but the cell still has to render something activatable — a ghost Button with no text is an
 * invisible control, which is worse than an ugly label. The mill id is never otherwise shown, and
 * appears only in that last-resort case.
 */
export const millLabel = (row: MillReportStatusRow): string => {
  if (!isBlank(row.millName)) {
    return (row.millName as string).trim()
  }
  return millNumberOrNull(row) ?? String(row.millId)
}

/**
 * The drill-down's download filename, `mill_<millNumber>_print.pdf` — parity-bound to legacy
 * (`PrintSchedulesMB.java:332`), which built it from the mill NUMBER.
 *
 * Built here from the clicked row rather than read off the response's `Content-Disposition`, which
 * is how 19.1's report page names its file too. The backend derives the identical name from the
 * same mill with the same mill-id fallback, so the two must agree — and they now agree on blanks as
 * well, which they did not before review round 1.
 */
export const millReportFilename = (row: MillReportStatusRow): string =>
  `mill_${millNumberOrNull(row) ?? row.millId}_print.pdf`
