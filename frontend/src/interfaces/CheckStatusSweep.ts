// Mirrors the backend checkstatus sweep records (GET /v1/check-status?millId&year): CheckStatusSweepResponse,
// TrackCheckResult and ScheduleCheckResult. Every `verdict` is the schedule's OWN check-status response,
// untouched — the two shipped DTO families survive on the wire — so the twelve existing verdict interfaces
// are reused here and nothing is redeclared. Jackson omits nulls (non_null): `statusCode` is ABSENT when
// the track's status column is null (the seeded 514/2021 row has no silviculture code), never `null`.
//
// `verdict` carries no discriminator of its own; discriminate on `schedule`. The codes are the legacy UI's
// names — `7A`/`7B` UPPERCASE — unlike the lowercase route paths.

import type { MessageInfo } from '@/interfaces/Schedule1Response'
import type CheckStatusResponse from '@/interfaces/CheckStatusResponse'
import type { CheckStatusResponse as Schedule2CheckStatusResponse } from '@/interfaces/Schedule2Response'
import type { Schedule4CheckStatusResponse } from '@/interfaces/Schedule4Response'
import type { Schedule5CheckStatusResponse } from '@/interfaces/Schedule5Response'
import type { Schedule6CheckStatusResponse } from '@/interfaces/Schedule6Response'
import type { Schedule7aCheckStatusResponse } from '@/interfaces/Schedule7aResponse'
import type { Schedule7bCheckStatusResponse } from '@/interfaces/Schedule7bResponse'
import type { Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import type { Schedule9CheckStatusResponse } from '@/interfaces/Schedule9Response'
import type { Schedule10CheckStatusResponse } from '@/interfaces/Schedule10Response'
import type { Schedule11CheckStatusResponse } from '@/interfaces/Schedule11Response'

export type { MessageInfo }

export type ScheduleCode = '1' | '2' | '3' | '4' | '5' | '6' | '7A' | '7B' | '8' | '9' | '10' | '11'

/** One schedule's entry in the sweep: its wire code, the normalized validity, and its own verdict. */
export type ScheduleCheckResult =
  | {
      readonly schedule: '1'
      readonly requirementsMet: boolean
      readonly verdict: CheckStatusResponse
    }
  | {
      readonly schedule: '2'
      readonly requirementsMet: boolean
      readonly verdict: Schedule2CheckStatusResponse
    }
  | {
      readonly schedule: '3'
      readonly requirementsMet: boolean
      readonly verdict: CheckStatusResponse
    }
  | {
      readonly schedule: '4'
      readonly requirementsMet: boolean
      readonly verdict: Schedule4CheckStatusResponse
    }
  | {
      readonly schedule: '5'
      readonly requirementsMet: boolean
      readonly verdict: Schedule5CheckStatusResponse
    }
  | {
      readonly schedule: '6'
      readonly requirementsMet: boolean
      readonly verdict: Schedule6CheckStatusResponse
    }
  | {
      readonly schedule: '7A'
      readonly requirementsMet: boolean
      readonly verdict: Schedule7aCheckStatusResponse
    }
  | {
      readonly schedule: '7B'
      readonly requirementsMet: boolean
      readonly verdict: Schedule7bCheckStatusResponse
    }
  | {
      readonly schedule: '8'
      readonly requirementsMet: boolean
      readonly verdict: Schedule8CheckStatusResponse
    }
  | {
      readonly schedule: '9'
      readonly requirementsMet: boolean
      readonly verdict: Schedule9CheckStatusResponse
    }
  | {
      readonly schedule: '10'
      readonly requirementsMet: boolean
      readonly verdict: Schedule10CheckStatusResponse
    }
  | {
      readonly schedule: '11'
      readonly requirementsMet: boolean
      readonly verdict: Schedule11CheckStatusResponse
    }

/** One track's half of the sweep: its persisted status code and its verdicts in legacy tab order. */
export interface TrackCheckResult {
  /** `D` | `S` | `V` (dead `O` passes through). Absent when the status column is null. */
  readonly statusCode?: string
  /** True iff every schedule on the track is met. */
  readonly requirementsMet: boolean
  /** Eleven entries for Schedules 1–10 (7A and 7B separately), one for Schedule 11. Never re-sort. */
  readonly schedules: readonly ScheduleCheckResult[]
  /**
   * Whether Submit is OFFERED to the caller for this track — decided by the one server component the
   * submit endpoint also applies (`TrackCheckResult.java:18-23`), so the page never computes it.
   * The sweep sends it on BOTH tracks, each decided against that track's own status code. Typed
   * optional anyway, and read as `=== true`, so a body without it fails closed to "not offered".
   */
  readonly canSubmit?: boolean
}

export default interface CheckStatusSweepResponse {
  readonly millId: number
  readonly year: number
  readonly schedules1To10: TrackCheckResult
  readonly schedule11: TrackCheckResult
}

/**
 * The reply to `POST /api/v1/check-status/verify` and to `.../schedule11/verify` — one shape for both
 * tracks, as the server's one verify method returns. A shape of its own rather than a field added to
 * the sweep response: pinned sub-shapes are extended, never re-shaped (AD-12).
 *
 * `trackStatus` is the verified track's code after the transition — always `V` here, since a refused
 * transition answers 409 rather than this body. `message` carries the resolved legacy text with its
 * bundle key, because the API returns final text for the client to render rather than a code to look
 * up (AD-8). Nothing else about the new state travels: the category states, the status description and
 * date, and the per-schedule verdicts all require a re-read.
 */
export interface VerifyReportResponse {
  readonly trackStatus: string
  readonly message: { readonly key: string; readonly text: string }
}

/**
 * The reply to `POST /api/v1/check-status/set-to-draft` and to `.../set-to-submit` — the two admin
 * reversals share one shape because they differ only in the values it carries (`SetTrackStatusResponse.java`).
 * A third interface beside `VerifyReportResponse` rather than a reuse of it: the two are structurally
 * identical, but that name would lie about which transition produced the body, and its `trackStatus`
 * is documented as always `V`.
 *
 * `trackStatus` is the Schedules 1–10 code after the transition — `D` for Set to Draft, `S` for Set to
 * Submit — and is only ever present on a 200, since a refusal answers 409. The page renders `message.text`
 * and nothing else: the status line, the category states and the per-schedule verdicts all need a re-read,
 * which is what the `reloadToken` bump is for.
 */
export interface SetTrackStatusResponse {
  readonly trackStatus: string
  readonly message: MessageInfo
}
