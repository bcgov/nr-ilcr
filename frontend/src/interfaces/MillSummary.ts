// Mirrors the backend millcontext MillSummary DTO (Story 1.1 pinned wire contract, GET /v1/mills).
// millNumber/millName are OPTIONAL: the columns are nullable and Jackson `non_null` omits a null
// value from the JSON (1.1 nullability decision), so the `{millNumber} - {millName}` label must
// render defensively. millStatusCode is always present (CLS mills are included in the list).
export default interface MillSummary {
  readonly millId: number
  readonly millNumber?: string | null
  readonly millName?: string | null
  readonly millStatusCode: string
}
