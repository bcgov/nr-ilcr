// Mirrors the merged mill-administration DTOs (nr-ilcr PR #457: millmaintenance/dto/*). Typed from
// the shipped records rather than a design-time pin — the users screen coded against a pin and
// shipped four contract divergences.
//
// Optionality here is not a guess. `spring.jackson.default-property-inclusion: non_null`
// (application.yml:5) drops every null field from the response, and these records carry no
// @JsonInclude of their own — so a nullable column arrives ABSENT, not as JSON null. Only the
// primitives cannot be null and are therefore always present: `millId` (long) and `revisionCount`
// (int) on AdminMill, `millId` on ImportableMill, `clientContactId` on ContactOption. That is what
// makes the optimistic-lock token safe to read straight off a mill.

/**
 * One mill on the administration surface (`millmaintenance/dto/AdminMill.java`).
 *
 * <p>Carries NO audit columns, so legacy's "Last Edited by / on date" line has no source here
 * (deviation (A)); extending the record is a logged follow-up, not this page's work.
 */
export interface AdminMill {
  readonly millId: number
  /** The optimistic-lock token: a write echoes the value it last read, never an incremented one. */
  readonly revisionCount: number
  /** A display identifier and never arithmetic — a String on the wire, so no thousands separator. */
  readonly millNumber?: string
  readonly millName?: string
  /** `ACT` or `CLS` — the CODE. Branch on this. */
  readonly millStatusCode?: string
  /** `Active` or `Close` — the code table's own LABEL. Render this (deviation (G)). */
  readonly statusDescription?: string
  /** `Y` or `N`, and ABSENT when the column was never set — which D5 refuses to guess at. */
  readonly headOfficeContactInd?: string
  readonly headOfficeContactId?: number
  readonly divisionContactId?: number
}

/** A ministry mill not yet tracked in ILCR, offered for import (BR-04). No revision — the row is new. */
export interface ImportableMill {
  readonly millId: number
  readonly millNumber?: string
  readonly millName?: string
}

/** One selectable contact for the head-office or division slot, from the mill's own client location. */
export interface ContactOption {
  readonly clientContactId: number
  readonly contactName?: string
}

/**
 * A mill search result set. A zero-match search is a 200 carrying the legacy not-found sentence
 * beside an empty list (S11), never an error status — so the criteria stay on screen for a retry.
 */
export interface MillSearchResponse {
  readonly results: readonly AdminMill[]
  /** Present ONLY on a zero-match search. */
  readonly messageKey?: string
  readonly message?: string
}

/**
 * A mill write outcome. Both message fields are ABSENT on import by design: legacy emitted no
 * confirmation sentence there and the detail panel appearing is the confirmation (deviation (I)).
 */
export interface AdminMillResponse {
  readonly mill: AdminMill
  readonly messageKey?: string
  readonly message?: string
}

/**
 * The whole editable panel, replaced on every save. A null contact id CLEARS that column — legacy's
 * blank selection stored null (MillDAO.java:226-234) — so a partial save is not expressible and an
 * omitted field is a 400.
 */
export interface SaveMillContactsRequest {
  /** Exactly `Y` or `N`; @NotNull @Pattern on the record, so anything else is a 400. */
  readonly headOfficeContactInd: string
  readonly headOfficeContactId: number | null
  readonly divisionContactId: number | null
  readonly revisionCount: number
}

/** The optimistic-lock token an activate or deactivate carries. Required — an absent field is a 400. */
export interface ChangeMillStatusRequest {
  readonly revisionCount: number
}

/**
 * The add body for the mill record's association panel. Declared here rather than reused: the users
 * surface's own request types were never typed in `MillAssociation.ts`, which carries only the
 * response records.
 */
export interface AddMillUserRequest {
  readonly userGuid: string
}

/** The toggle body for an association activate or deactivate on this surface. */
export interface ChangeMillUserRequest {
  readonly revisionCount: number
}

/** One resolved bundle string from `GET /v1/messages` (`dto/base/MessageInfo`). */
export interface MessageText {
  readonly key: string
  readonly text: string
}

/** The active mill status code. */
export const MILL_ACTIVE = 'ACT'

/** The closed mill status code. */
export const MILL_CLOSED = 'CLS'

/**
 * The search `Status:` option list, held client-side (deviation (C), D3): no endpoint exposes
 * `THE.ILCR_MILL_STATUS_CODE`, the domain is closed at exactly these two values
 * (`R__41_mill_status_code_reference.sql:14-19`), and the backend 400s anything else. A mill's OWN
 * status is still rendered from the server's `statusDescription`, never from this list.
 */
export const MILL_STATUS_OPTIONS = [
  { code: MILL_ACTIVE, description: 'Active' },
  { code: MILL_CLOSED, description: 'Close' },
] as const

/** One entry of the client-held status list. */
export type MillStatusOption = (typeof MILL_STATUS_OPTIONS)[number]
