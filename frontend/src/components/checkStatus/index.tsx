import type { FC, ReactNode } from 'react'
import { useEffect, useRef, useState } from 'react'
import { Accordion, AccordionItem, Column, Grid, InlineNotification } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import renderScheduleLoadState from '@/components/core/ScheduleLoadState'
import CheckStatusNotifications from '@/components/core/CheckStatusNotifications'
import ConfirmActionModal from '@/components/core/ConfirmActionModal'
import type {
  MessageInfo,
  ScheduleCheckResult,
  TrackCheckResult,
} from '@/interfaces/CheckStatusSweep'
import type { TrackAction } from './CheckStatusActions'
import CheckStatusActions from './CheckStatusActions'
import useCheckStatusSweep from './useCheckStatusSweep'
import { flattenVerdict, SCHEDULE_TITLES } from './verdicts'
import './index.scss'

const PAGE_TITLE = 'Check Status'
const DRAFT = 'D'
const SUBMITTED = 'S'
const VERIFIED = 'V'

// Client-authored hints beside a greyed button (legacy greyed with no explanation). Chosen here, by
// which half of the legacy rule failed, and passed down — the bar itself knows nothing about roles.
export const HINT_NOT_SUBMITTER = "Submitting is the licensee's action"
export const HINT_NOT_DRAFT = 'Available while Schedules 1-10 are in Draft'
export const HINT_NOT_DRAFT_11 = 'Available while Schedule 11 is in Draft'
export const HINT_NOT_ADMIN = "Verifying is an administrator's action"
export const HINT_NOT_SUBMITTED = 'Available once Schedules 1-10 are Submitted'
export const HINT_NOT_SUBMITTED_11 = 'Available once Schedule 11 is Submitted'

/**
 * Legacy's confirmation, verbatim (messages.properties:102, resolved in the view by
 * checkStatus.xhtml:197). The API never sends this text, so it is the page's literal to hold.
 */
export const CONFIRM_SUBMIT_1_TO_10 = "Please confirm you'd like to SUBMIT Schedules 1-10?"
/** Client fallback for a submit failure that carries no ProblemDetail `detail` (network, a 401). */
export const SUBMIT_FAILED = 'Unable to submit Schedules 1-10.'

/** The submit endpoint's 200 body: the server's message and nothing else (MessageResponse.java). */
type SubmitResponse = {
  readonly message: MessageInfo
}

/** A 409 is the protocol's own "your view of the state is stale" — the one status that warrants a re-read. */
const isConflict = (error: unknown): boolean =>
  (error as { response?: { status?: number } }).response?.status === 409

/**
 * One schedule's accordion item: the legacy tab title and its result lines, every one the server's
 * own text. A verdict with nothing to say (no failing line, no met line — a payload outside the
 * contract) falls through to the shared renderer's "Status checked" line, as every schedule page
 * does, rather than an empty item. `footer` is the action bar legacy placed INSIDE the Schedule 11
 * tab, under its result (checkStatus.xhtml:152-183).
 */
const ScheduleSection: FC<{ entry: ScheduleCheckResult; footer?: ReactNode }> = ({
  entry,
  footer,
}) => {
  const verdict = flattenVerdict(entry)
  return (
    <AccordionItem open title={SCHEDULE_TITLES[entry.schedule]}>
      <Grid fullWidth className="check-status__lines">
        <CheckStatusNotifications
          errors={verdict.errors}
          warnings={verdict.warnings}
          requirementsMetMessage={verdict.requirementsMetMessage}
          requirementsMet={verdict.requirementsMet}
          keyPrefix={`check-${entry.schedule}`}
        />
        {footer}
      </Grid>
    </AccordionItem>
  )
}

/** One track's region: an explicit heading (an accordion title is a button, not a heading) and its items. */
const TrackRegion: FC<{
  id: string
  heading: string
  track: TrackCheckResult
  itemFooter?: ReactNode
}> = ({ id, heading, track, itemFooter }) => (
  <Column sm={4} md={8} lg={16}>
    <section className="check-status__region" aria-labelledby={id}>
      <h2 className="check-status__heading" id={id}>
        {heading}
      </h2>
      <Accordion>
        {track.schedules.map((entry) => (
          <ScheduleSection key={entry.schedule} entry={entry} footer={itemFooter} />
        ))}
      </Accordion>
    </section>
  </Column>
)

type TrackActions = {
  readonly setToDraft?: TrackAction
  readonly setToSubmit?: TrackAction
  readonly submit: TrackAction
  readonly verify: TrackAction
}

/**
 * How one track's Submit is offered. The two tracks read different sources until Epic 26: Schedules
 * 1–10 take the server's `canSubmit` off the sweep, Schedule 11 still evaluates the legacy client rule
 * — which is why the gate is a PARAMETER of the shared helper rather than a line inside it.
 */
type SubmitGate = {
  /** Whether Submit is offered at all. Authorization lives on the server; this only greys a button. */
  readonly offered: boolean
  /** The click. Absent while the track's transition story has not shipped, and the button stays greyed. */
  readonly onClick?: () => void
  /** True while a request is in flight: both bars grey together, so a second click cannot race the first. */
  readonly busy?: boolean
}

/**
 * The legacy rules, exactly, evaluated on ONE track's own status code (UserSessionMB.java:502-570,
 * CheckStatusMB.java:162-192): Verified = that track Submitted AND the user is NOT the licensee
 * (ILCR_ADMIN); Set to Draft is RENDERED only for an admin while the track is Submitted, Set to
 * Submit only for an admin while it is Verified (both enabled whenever rendered — `canUserSetToDraft/
 * Submit` is the same admin test). Submit's gate is passed in (see `SubmitGate`). Validity is not part
 * of any of them — the ten-schedule gate fires on the click, server-side. Display state only; the
 * server is the authorization. The role/status expressions that remain here choose only the hint text
 * beside a greyed button — which half of the legacy rule the user fails — never whether it is offered.
 */
const trackActions = (
  track: TrackCheckResult,
  isSubmitter: boolean,
  isAdmin: boolean,
  hints: { readonly notDraft: string; readonly notSubmitted: string },
  submitGate: SubmitGate,
): TrackActions => {
  const canVerify = isAdmin && track.statusCode === SUBMITTED
  const reversal: TrackAction = {
    enabled: isAdmin,
    disabledReason: isAdmin ? undefined : HINT_NOT_ADMIN,
    onClick: () => undefined,
  }
  return {
    setToDraft: isAdmin && track.statusCode === SUBMITTED ? reversal : undefined,
    setToSubmit: isAdmin && track.statusCode === VERIFIED ? reversal : undefined,
    submit: {
      enabled: submitGate.offered && !submitGate.busy,
      // Display text, not authorization: the wire decided `offered`; this only says why not.
      disabledReason: submitGate.offered
        ? undefined
        : isSubmitter
          ? hints.notDraft
          : HINT_NOT_SUBMITTER,
      onClick: submitGate.onClick ?? (() => undefined),
    },
    verify: {
      enabled: canVerify,
      disabledReason: canVerify ? undefined : isAdmin ? hints.notSubmitted : HINT_NOT_ADMIN,
      onClick: () => undefined,
    },
  }
}

/**
 * The Check Status page: every schedule's verdict at once, in legacy tab order, under the two track
 * regions legacy's two accordions drew — all twelve open — with the Schedules 1–10 action bar above
 * and below that region and the Schedule 11 action bar inside its tab, as legacy's three button rows
 * were. Both track status lines come from the tombstone's /mill-context read; the sweep's status
 * codes drive only the action gates.
 *
 * Submit (Schedules 1–10) is the one live transition: it asks legacy's `Confirmation Required`
 * question first, POSTs once, and renders the server's answer as a banner where legacy's
 * `p:messages` sat — first in the panel, above the top button row (checkStatus.xhtml:31-33) — then
 * re-reads both the sweep and the working context so everything on the page is the server's new
 * state. Legacy scrolled to the top so the message could be seen from the bottom row; here focus
 * moves to the banner, which scrolls it into view and announces it. A 409 also re-reads: it is the
 * server saying the page's picture is stale, and legacy's blocked submit always had its failing rows
 * already on screen (its gate and its panels read the same objects), so the re-read is what keeps
 * that true here. Nothing else on the page navigates: the correct-and-re-check loop runs through the
 * navigation menu, and every visit remounts the route and re-issues the sweep.
 */
const CheckStatus: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()
  const [reloadToken, setReloadToken] = useState(0)
  const { data, isLoading, errorDetail } = useCheckStatusSweep(millId, year, reloadToken)
  const { hasRole } = useAuth()

  const [confirming, setConfirming] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  // Focus the outcome for THIS action only — a ref flag rather than state, so the effect sets nothing.
  const focusOutcomeRef = useRef(false)
  const messageRef = useRef<HTMLDivElement>(null)
  const actionErrorRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!focusOutcomeRef.current) return
    if (message === null && actionError === null) return
    focusOutcomeRef.current = false
    ;(messageRef.current ?? actionErrorRef.current)?.focus()
  }, [message, actionError])

  const header = <ScheduleTombstone title={PAGE_TITLE} reloadToken={reloadToken} />
  const loadState = renderScheduleLoadState({
    header,
    scheduleName: PAGE_TITLE,
    contextMissing,
    isLoading,
    errorDetail,
  })
  if (loadState) {
    return loadState
  }
  if (!data) {
    return null
  }

  const requestSubmit = () => {
    if (saving) return
    setMessage(null)
    setActionError(null)
    setConfirming(true)
  }

  // Legacy's Cancel was `type="button"` with no action: close the dialog, touch nothing else.
  const cancelSubmit = () => setConfirming(false)

  const confirmSubmit = () => {
    setConfirming(false)
    setSaving(true)
    focusOutcomeRef.current = true
    apiService
      .getAxiosInstance()
      .post<SubmitResponse>(`/v1/check-status/submit?millId=${millId}&year=${year}`)
      .then((response) => {
        if (!isCurrent()) return
        setMessage(response.data.message.text)
        setReloadToken((token) => token + 1)
      })
      .catch((error: unknown) => {
        if (!isCurrent()) return
        setActionError(extractDetail(error) || SUBMIT_FAILED)
        if (isConflict(error)) {
          setReloadToken((token) => token + 1)
        }
      })
      .finally(() => {
        if (isCurrent()) {
          setSaving(false)
        }
      })
  }

  const isSubmitter = hasRole(ILCR_ROLES.submitter)
  const isAdmin = hasRole(ILCR_ROLES.admin)
  const actions1To10 = trackActions(
    data.schedules1To10,
    isSubmitter,
    isAdmin,
    { notDraft: HINT_NOT_DRAFT, notSubmitted: HINT_NOT_SUBMITTED },
    // `=== true`: the field is ABSENT (never `false`) where the server has no verdict to give.
    { offered: data.schedules1To10.canSubmit === true, onClick: requestSubmit, busy: saving },
  )
  const actions11 = trackActions(
    data.schedule11,
    isSubmitter,
    isAdmin,
    { notDraft: HINT_NOT_DRAFT_11, notSubmitted: HINT_NOT_SUBMITTED_11 },
    // Schedule 11 keeps the legacy client rule, and no click, until Epic 26 ships its own verdict.
    { offered: isSubmitter && data.schedule11.statusCode === DRAFT },
  )

  return (
    <div className="app-page schedule-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {message && (
          // tabIndex={-1}: a programmatic focus target only — never in the tab order.
          <Column sm={4} md={8} lg={16} ref={messageRef} tabIndex={-1}>
            <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
          </Column>
        )}
        {actionError && (
          <Column sm={4} md={8} lg={16} ref={actionErrorRef} tabIndex={-1}>
            <InlineNotification
              kind="error"
              lowContrast
              title="Action failed"
              subtitle={actionError}
            />
          </Column>
        )}
        <CheckStatusActions {...actions1To10} />
        <TrackRegion
          id="check-status-heading-1-10"
          heading="Schedules 1–10"
          track={data.schedules1To10}
        />
        <CheckStatusActions {...actions1To10} />
        <TrackRegion
          id="check-status-heading-11"
          heading="Schedule 11"
          track={data.schedule11}
          itemFooter={<CheckStatusActions {...actions11} />}
        />
      </Grid>
      {confirming && (
        <ConfirmActionModal
          open
          heading="Confirmation Required"
          message={CONFIRM_SUBMIT_1_TO_10}
          confirmLabel="Yes"
          cancelLabel="Cancel"
          onConfirm={confirmSubmit}
          onCancel={cancelSubmit}
        />
      )}
    </div>
  )
}

export default CheckStatus
