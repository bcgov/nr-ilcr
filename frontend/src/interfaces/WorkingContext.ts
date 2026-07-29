// Mirrors the backend millcontext WorkingContext DTO (Stories 1.1/1.2 pinned wire contract,
// GET /v1/mill-context), AMENDED by Story 1.3 to carry the SUC-001 `message` on every 200 (AC7).
//
// Jackson `non_null` omits any null field, so millNumber/millName, both track statuses, and message
// are all optional — render defensively. The status fields feed Story 1.4's banner; the full type is
// kept here so 1.4 reuses it unchanged. `message` is present on every 200 but is only DISPLAYED
// after an explicit Save (1.4's banner load must ignore it).

// Success message carried on the 200 response (AD-8): the frontend renders `text` verbatim and never
// hardcodes SUC-* strings. Mirrors interfaces/Schedule1Response.ts MessageInfo.
export interface MessageInfo {
  readonly key: string
  readonly text: string
}

// One workflow track's status (Schedules 1–10 or Schedule 11). All members are nullable/omitted.
export interface TrackStatus {
  readonly code?: string | null
  readonly description?: string | null
  readonly date?: string | null
}

export default interface WorkingContext {
  readonly millId: number
  readonly millNumber?: string | null
  readonly millName?: string | null
  readonly reportYear: number
  readonly schedules1To10Status?: TrackStatus | null
  readonly schedule11Status?: TrackStatus | null
  readonly millViewable: boolean
  readonly message?: MessageInfo | null
}

// The RFC 7807 problem+json body returned on 400 (S04/S05/S08) and other error paths. `messages`
// carries the per-field verbatim texts (both when both fields are missing); `detail` is the joined
// fallback. The frontend renders `messages[].text`, falling back to `detail` — never hardcoded.
export interface ProblemBody {
  readonly detail?: string
  readonly messages?: readonly MessageInfo[]
}
