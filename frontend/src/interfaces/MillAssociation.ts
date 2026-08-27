// Mirrors the merged licensee-account/mill-assignment DTOs (nr-ilcr PR #356: assignment/dto/*,
// userlookup/dto/DirectoryUser). Typed from the shipped records rather than the Story 2.1 design
// pin, which predates four changes the pin never carried.
//
// Optionality here is not a guess. `spring.jackson.default-property-inclusion: non_null`
// (application.yml:5) drops every null field from the response, and these DTOs carry no
// @JsonInclude of their own — so a nullable column arrives ABSENT, not as JSON null. The two
// primitives cannot be null and are therefore always present: `millId` (long) and `revisionCount`
// (int). That is what makes the optimistic-lock token safe to read straight off a row.

/** One dated submitter-to-mill assignment. */
export interface MillSubmitter {
  /** Exactly 32 characters — the `custom:idp_user_id` claim, and the association key. */
  readonly userGuid: string
  /**
   * Always absent today: the service maps this to null on every row (AssignmentService:285) because
   * a name is deliberately never persisted. The screen resolves it from the directory result that
   * produced the assignment, and falls back to the GUID.
   */
  readonly displayName?: string | null
  readonly millId: number
  /** Absent when the mill no longer resolves through the selectable-mill lookup. */
  readonly millNumber?: string | null
  readonly millName?: string | null
  readonly status: 'ACTIVE' | 'ENDED'
  /** ISO date, no time. Absent once the assignment is ended. */
  readonly activeDate?: string | null
  /** ISO date, no time. Absent while the assignment is active. */
  readonly inactiveDate?: string | null
  readonly revisionCount: number
}

/** A licensee's ILCR account row. `activeInd` is display/administrative state only — never a lockout. */
export interface SubmitterAccount {
  readonly userGuid: string
  readonly activeInd: 'Y' | 'N'
  readonly roleName: string
  readonly revisionCount: number
}

/**
 * Every assignment write answers 200 with this envelope, including the refusal to re-assign an
 * already-active pair — which changes nothing and is distinguishable only by `messageKey`.
 */
export interface AssignmentResponse {
  readonly assignment: MillSubmitter
  readonly messageKey: string
  /** Resolved verbatim legacy text; rendered as-is (AD-8). */
  readonly message: string
}

/** An account write's result. */
export interface AccountResponse {
  readonly account: SubmitterAccount
  readonly messageKey: string
  readonly message: string
}

/** One directory candidate for the picker. Holding one proves nothing about role or mill access. */
export interface DirectoryUser {
  readonly userGuid: string
  readonly displayName?: string | null
  readonly idpUsername: string
  readonly identityProvider: string
}

/** The one warning outcome of an assign: the pair was already active, so nothing changed. */
export const MSG_ALREADY_ASSIGNED = 'user.not.associated.to.mill'

/** The two identity providers the lookup serves; they take different criteria and must not be mixed. */
export const IDP_IDIR = 'IDIR'
export const IDP_BCEID_BUSINESS = 'BCEIDBUSINESS'
