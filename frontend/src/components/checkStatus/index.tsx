import type { FC, ReactNode } from 'react'
import { useEffect, useRef, useState } from 'react'
import { Accordion, AccordionItem, Column, Grid } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import renderScheduleLoadState from '@/components/core/ScheduleLoadState'
import CheckStatusNotifications from '@/components/core/CheckStatusNotifications'
import ConfirmActionModal from '@/components/core/ConfirmActionModal'
import NotificationColumn from '@/components/core/NotificationColumn'
import type {
  MessageInfo,
  ScheduleCheckResult,
  TrackCheckResult,
  VerifyReportResponse,
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
// Set to Draft / Set to Submit render by legacy's own `rendered=` rules and legacy had them working;
// only the transition is deferred (Epic 18). Until it lands the button is GREYED with this hint
// rather than left live and silently inert: Story 17.2 is what first makes Verified reachable from
// inside the app, so `Set to Submit` is now on the screen an administrator lands on immediately
// after a successful verify, where a dead control reads as a broken one.
export const HINT_NOT_WIRED = 'This action is not available yet'
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
/**
 * Legacy's verify confirmation, verbatim (messages.properties:103, resolved in the view by
 * checkStatus.xhtml:201). Client-owned for the same reason as the submit prompt — it is pre-request
 * chrome that no response could carry — and pinned byte for byte against the backend bundle by
 * `__tests__/confirmText.test.ts`, so the two cannot drift apart unnoticed.
 */
export const CONFIRM_VERIFY_1_TO_10 = "Please confirm you'd like to set Schedules 1-10 to VERIFIED?"
/** Client fallback for a submit failure that carries no ProblemDetail `detail` (network, a 401). */
export const SUBMIT_FAILED = 'Unable to submit Schedules 1-10.'
/** The same, for verify: shown only when a failure carries no problem+json body of its own. */
export const VERIFY_FAILED = 'The report could not be verified.'

/** The submit endpoint's 200 body: the server's message and nothing else (MessageResponse.java). */
type SubmitResponse = {
  readonly message: MessageInfo
}

/**
 * The page's ONE settled outcome. A discriminated union rather than a success channel beside an
 * error channel (Story 17.2, D5): legacy could render an error AND a success from the same click
 * (CheckStatusMB.java:278-280 adds the error, then :284 adds the success anyway), and one slot makes
 * that impossible instead of merely asserted against. It is shared by submit and verify because the
 * two are reachable in sequence — a submit success leaves the track Submitted, which is exactly when
 * an administrator's Verified lights up — so separate channels would let one action's banner sit
 * under the other's. Text is always the server's; the title beside it is the shared vocabulary.
 */
type Outcome = {
  readonly kind: 'success' | 'error'
  readonly text: string
}

/**
 * How a track's Verified button behaves. `busy` greys it from the click until the page is showing
 * post-transition truth — the POST AND the refresh that follows it, not just the POST. Legacy needed
 * no such flag: its one ajax round trip delivered the message and the re-rendered, re-gated button
 * together, so there was never an instant where the screen said "verified" while the button still
 * offered to verify. Ours reads the status from a SECOND request, and between the two the sweep
 * still answers Submitted, so without this the button re-enables and a second POST is reachable —
 * which the server then refuses with the support-escalation 409, painting over the success the user
 * just earned. A track with no wiring keeps an inert click, which is Schedule 11's until Epic 26.
 */
type VerifyWiring = {
  readonly onClick: () => void
  readonly busy: boolean
}

const INERT: VerifyWiring = { onClick: () => undefined, busy: false }

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
 * Submit only for an admin while it is Verified (legacy enabled both whenever rendered —
 * `canUserSetToDraft/Submit` is the same admin test; ours greys them until Epic 18 supplies the
 * transition, see `reversal`). Submit's gate is passed in (see `SubmitGate`), Verified's wiring too
 * (see `VerifyWiring`). Validity is not part of any of them — the eleven-schedule gate fires on the
 * click, server-side, and an administrator on a Submitted-but-failing report gets an enabled button
 * and the server's verbatim refusal. Display state only; the server is the authorization. The
 * role/status expressions that remain here choose only the hint text beside a greyed button — which
 * half of the legacy rule the user fails — never whether it is offered.
 */
const trackActions = (
  track: TrackCheckResult,
  isSubmitter: boolean,
  isAdmin: boolean,
  hints: { readonly notDraft: string; readonly notSubmitted: string },
  submitGate: SubmitGate,
  verifyWiring: VerifyWiring = INERT,
): TrackActions => {
  const canVerify = isAdmin && track.statusCode === SUBMITTED
  // Legacy rendered these ENABLED for an admin and they worked. Ours has no transition behind it
  // until Epic 18, so it ships greyed: `onClick` absent is exactly the "no handler ⇒ disabled"
  // contract TrackAction documents, which means Epic 18 enables the button by supplying a handler
  // and changes nothing here. `enabled` still carries legacy's own rule so that is a one-line move.
  const reversal: TrackAction = {
    enabled: isAdmin,
    disabledReason: isAdmin ? HINT_NOT_WIRED : HINT_NOT_ADMIN,
    onClick: undefined,
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
      enabled: canVerify && !verifyWiring.busy,
      disabledReason: canVerify ? undefined : isAdmin ? hints.notSubmitted : HINT_NOT_ADMIN,
      onClick: verifyWiring.onClick,
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
 * Submit and Verified (Schedules 1–10) are the live transitions: each asks legacy's `Confirmation Required`
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
  const { data, isLoading, errorDetail, isReloading } = useCheckStatusSweep(
    millId,
    year,
    reloadToken,
  )
  const { hasRole } = useAuth()

  // Which transition is awaiting its answer, if any — one mount serves both prompts, because only
  // one can be pending and the page renders the 1–10 bar twice from the same action object.
  const [confirming, setConfirming] = useState<'submit' | 'verify' | null>(null)
  const [saving, setSaving] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)

  // Synchronous, unlike the state flag: two clicks inside one tick would both see `verifying` false.
  const busyRef = useRef(false)
  // Which button opened the prompt, so declining puts focus back where it was. The prompt is mounted
  // only while pending, so there is no dialog left for Carbon to restore focus from on close.
  const launcherRef = useRef<HTMLElement | null>(null)
  // Focus the outcome for THIS action only — a ref flag rather than state, so the effect sets nothing.
  // Confirming unmounts the prompt and the launching button may now be greyed, so without this focus
  // lands on <body>: the user is dropped to the top of the document and never told what happened.
  const focusOutcomeRef = useRef(false)
  const outcomeRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!focusOutcomeRef.current) return
    if (outcome === null) return
    focusOutcomeRef.current = false
    outcomeRef.current?.focus()
  }, [outcome])

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

  // Opening a prompt touches no server: legacy's buttons were `type="button"` whose whole behaviour
  // was `confirmSubmit.show()` / `confirmVerify.show()` (checkStatus.xhtml:51-62). The launching
  // button is recorded so declining can put focus back on it — either 1–10 bar can open either
  // prompt, so the active element is the only honest source of "which button opened this".
  const openPrompt = (which: 'submit' | 'verify') => {
    launcherRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    setConfirming(which)
  }

  const requestSubmit = () => {
    if (saving) return
    openPrompt('submit')
  }

  const requestVerify = () => {
    setOutcome(null)
    openPrompt('verify')
  }

  // Legacy's Cancel was `type="button"` with no action: close the dialog, touch nothing else.
  const cancelConfirm = () => {
    setConfirming(null)
    launcherRef.current?.focus()
  }

  const confirmSubmit = () => {
    setConfirming(null)
    setOutcome(null)
    setSaving(true)
    focusOutcomeRef.current = true
    apiService
      .getAxiosInstance()
      .post<SubmitResponse>(`/v1/check-status/submit?millId=${millId}&year=${year}`)
      .then((response) => {
        if (!isCurrent()) return
        setOutcome({ kind: 'success', text: response.data.message.text })
        setReloadToken((token) => token + 1)
      })
      .catch((error: unknown) => {
        if (!isCurrent()) return
        setOutcome({ kind: 'error', text: extractDetail(error) || SUBMIT_FAILED })
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

  // The verify transition, hung off the prompt's Yes exactly as legacy hung it (checkStatus.xhtml:202).
  // The mill/year come from the sweep body, which the hook has already refused unless it echoes the
  // request's own context. Every branch re-checks that the context has not moved underneath it before
  // it writes to state, and the lock is released unconditionally — a conditional reset strands it.
  const confirmVerify = () => {
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    focusOutcomeRef.current = true
    setConfirming(null)
    setVerifying(true)
    apiService
      .getAxiosInstance()
      .post<VerifyReportResponse>(`/v1/check-status/verify?millId=${data.millId}&year=${data.year}`)
      .then((response) => {
        if (!isCurrent()) return
        setOutcome({ kind: 'success', text: response.data.message.text })
        setReloadToken((token) => token + 1)
      })
      .catch((error: unknown) => {
        if (!isCurrent()) return
        setOutcome({ kind: 'error', text: extractDetail(error) || VERIFY_FAILED })
      })
      .finally(() => {
        busyRef.current = false
        setVerifying(false)
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
    {
      offered: data.schedules1To10.canSubmit === true,
      onClick: requestSubmit,
      // A 200 means this transition is complete. Keep the pair locked even if the re-sweep is still
      // in flight or fails and the hook deliberately preserves the last good (Draft) payload.
      busy: saving || outcome?.kind === 'success',
    },
    { onClick: requestVerify, busy: verifying || isReloading },
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
        {outcome && (
          // focusRef makes it a programmatic focus target only — never in the tab order.
          <NotificationColumn
            kind={outcome.kind}
            title={outcome.kind === 'success' ? 'Success' : 'Action failed'}
            subtitle={outcome.text}
            focusRef={outcomeRef}
          />
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
          message={confirming === 'submit' ? CONFIRM_SUBMIT_1_TO_10 : CONFIRM_VERIFY_1_TO_10}
          confirmLabel="Yes"
          cancelLabel="Cancel"
          onConfirm={confirming === 'submit' ? confirmSubmit : confirmVerify}
          onCancel={cancelConfirm}
        />
      )}
    </div>
  )
}

export default CheckStatus
