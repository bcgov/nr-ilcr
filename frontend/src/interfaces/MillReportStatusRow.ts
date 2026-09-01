/**
 * One row of the Mill Status Report table — mirrors the backend `MillReportStatusRow` DTO served by
 * `GET /api/v1/reports/mill-status?year=` (Story 19.2 / UC-MRPT-004).
 *
 * The seven milestone strings are RAW: they carry the legacy status prefix (`"O: 2021-01-05"`,
 * `"D: "`) exactly as the reporting view holds it, because the page's O/D/S/V legend is what decodes
 * that letter. Render them verbatim — never strip, never reformat — and render `null` as an EMPTY
 * line, never the text `null`.
 *
 * Both track column groups share `openDate`: the view has no Schedule 11 opened column and the
 * Schedule 11 track has no independent opened date, so legacy renders the same value twice.
 *
 * Every nullable field is declared OPTIONAL as well as nullable: the backend sets Jackson
 * `default-property-inclusion: non_null` (application.yml:5), so a null column is omitted from the
 * body entirely rather than sent as JSON null. The house convention (see `MillSummary`) is to model
 * both shapes, and every read of these fields must therefore go through `??`.
 */
export default interface MillReportStatusRow {
  /** The mill id — the React row key. NOT the mill number, and never rendered. */
  readonly millId: number
  /** Mill Number — the first (sortable) column. */
  readonly millNumber?: string | null
  /** Mill (licensee) name — the second sortable column. Plain text; the drill-down is Story 19.3. */
  readonly millName?: string | null
  /** Selling-price zone description, or null when absent/unreadable — the page renders `-`. */
  readonly region?: string | null
  /** Whether the mill was active IN THIS REPORTING YEAR — rendered `Yes`/`No`, not the status today. */
  readonly active: boolean
  /** Raw Schedules 1–10 Opened milestone. Rendered in BOTH track groups. */
  readonly openDate?: string | null
  /** Raw Schedules 1–10 Draft milestone. */
  readonly draftDate?: string | null
  /** Raw Schedules 1–10 Submitted milestone. */
  readonly submitDate?: string | null
  /** Raw Schedules 1–10 Verified milestone. */
  readonly verifyDate?: string | null
  /** Raw Schedule 11 Draft milestone. */
  readonly silviDraftDate?: string | null
  /** Raw Schedule 11 Submitted milestone. */
  readonly silviSubmitDate?: string | null
  /** Raw Schedule 11 Verified milestone. */
  readonly silviVerifyDate?: string | null
}
