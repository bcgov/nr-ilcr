// Mirrors the backend `dto/base/OriginalValue` record (Story 16.2) — one field's originally-submitted
// value, retained for audit comparison once a report has left Draft (UC-CHK-005/UC-CHK-010 BR-04).
//
// A cross-schedule sub-shape pinned once and reused verbatim by all twelve schedules (AD-12), so it
// lives here rather than in any one schedule's interface file.

export interface OriginalValue {
  // The submitted value in canonical unformatted form ("60000", "60000.5", "Y", or raw text) — never
  // grouped and never currency-decorated, because this is what the typed value is compared against.
  // Empty when the snapshot column held no value.
  readonly value: string
  // The verbatim text to render, e.g. "Original Submission Value: 60,000", composed server-side from
  // the field's legacy converter rule. Rendered as-is and never assembled here (AD-8).
  readonly tooltip: string
}

// Per-field originals for one document or row, keyed by that object's own camelCase field name.
//
// Three states, all meaningful — see `utils/originalValue.ts` for the rule that reads them:
//   * `null`/absent  — the track is at Draft: no indicator renders anywhere.
//   * key present    — a submitted value is on file.
//   * key absent     — no submitted value on file for that field.
export type OriginalValues = Readonly<Record<string, OriginalValue>>
