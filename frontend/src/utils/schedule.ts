// Shared, tiny predicates about a served schedule document. Kept out of the page components so the
// rules below live in exactly one place — four hand-written copies of the comment was a code-review
// finding on defect #292.

/**
 * Whether a schedule document describes a PERSISTED record — legacy `isScheduleOpen()`, which tested
 * that a summary row exists (`Schedule2MB.java:152-158`, and the identical gate on Schedules 1/3).
 * `revisionCount` is the optimistic-lock token the server issues once the summary exists, so its
 * presence is the client-side proxy for "there is something to delete".
 *
 * The comparison is a LOOSE `!= null`, and that is load-bearing: the backend runs
 * `default-property-inclusion: non_null`, so a null `revisionCount` is OMITTED from the GET body and
 * an unsaved (or just-deleted) document serves `undefined`. A strict `!== null` is always true
 * against `undefined` — that inert gate IS defect #292, which offered Delete on a schedule that had
 * never been saved and reported "Data deleted successfully" for it. Verified sound against the
 * delivery schema: `THE.ILCR_REPORT_SUMMARY.REVISION_COUNT` is NOT NULL, so a persisted summary
 * always carries a token (0 of 91 rows null on the real-data-seeded image, 2026-08-24).
 *
 * Do not "tidy" this to `!==`, and do not inline it — the audit of this whole `=== null`-against-an-
 * omitted-field family (see `deferred-work.md`) needs one site to fix, not four.
 */
export const isScheduleSaved = (doc: { readonly revisionCount?: number | null }): boolean =>
  doc.revisionCount != null
