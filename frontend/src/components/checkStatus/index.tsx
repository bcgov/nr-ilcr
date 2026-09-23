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
  SetTrackStatusResponse,
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
// Schedule 11's reversals only. Legacy rendered a Set to Draft / Set to Submit inside the Schedule 11
// tab too (checkStatus.xhtml:155-168, gated by `showSch11SetToDraft/Submit`) and both worked there;
// that track's transitions are Epic 26's, so ITS buttons keep legacy's `rendered=` presence but ship
// GREYED with this hint rather than live and silently inert, exactly as Schedule 11's own Submit and
// Verified do. The Schedules 1-10 pair no longer uses it: Story 18.2 supplied their transition.
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
/**
 * The two admin reversals' confirmations, verbatim (messages.properties:104-105, resolved in the view
 * by checkStatus.xhtml:189 and :193). Client-owned and pinned against the backend bundle for the same
 * reason as the verify prompt. Note the second says SUBMIT, not SUBMITTED, and that legacy's key names
 * carry a `Sche` typo the bundle preserves; the rendered text does not.
 */
export const CONFIRM_SET_TO_DRAFT_1_TO_10 =
  "Please confirm you'd like to set Schedules 1-10 to DRAFT?"
export const CONFIRM_SET_TO_SUBMIT_1_TO_10 =
  "Please confirm you'd like to set Schedules 1-10 to SUBMIT?"
/** Client fallback for a submit failure that carries no ProblemDetail `detail` (network, a 401). */
export const SUBMIT_FAILED = 'Unable to submit Schedules 1-10.'
/** The same, for verify: shown only when a failure carries no problem+json body of its own. */
export const VERIFY_FAILED = 'The report could not be verified.'
/** The same again, per reversal. Keyed on a MISSING `detail`, never on "was this a network error?":
 *  a 401 answers with Boot's `{timestamp,status,error,path}`, which has no `detail` either. */
export const SET_TO_DRAFT_FAILED = 'Schedules 1-10 could not be set to Draft.'
export const SET_TO_SUBMIT_FAILED = 'Schedules 1-10 could not be set to Submit.'

/** Which transition a prompt is asking about. One mount serves all four; only one can be pending. */
type Pending = 'submit' | 'verify' | 'setToDraft' | 'setToSubmit'

/** The prompt each pending transition asks, so the modal reads one lookup instead of a ternary chain. */
const CONFIRM_PROMPTS: Record<Pending, string> = {
  submit: CONFIRM_SUBMIT_1_TO_10,
  verify: CONFIRM_VERIFY_1_TO_10,
  setToDraft: CONFIRM_SET_TO_DRAFT_1_TO_10,
  setToSubmit: CONFIRM_SET_TO_SUBMIT_1_TO_10,
}

/** The two reversals differ only in these three values (`CheckStatusApi.java:181-220`). */
const REVERSALS = {
  setToDraft: { path: 'set-to-draft', fallback: SET_TO_DRAFT_FAILED },
  setToSubmit: { path: 'set-to-submit', fallback: SET_TO_SUBMIT_FAILED },
} as const

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
 * How one wired transition button behaves — Verified and, since Story 18.2, both Schedules 1-10
 * reversals. `busy` greys it from the click until the page is showing post-transition truth — the
 * POST AND the refresh that follows it, not just the POST. Legacy needed no such flag: its one ajax
 * round trip delivered the message and the re-rendered, re-gated button together, so there was never
 * an instant where the screen said "verified" while the button still offered to verify. Ours reads
 * the status from a SECOND request, and between the two the sweep still answers the old code, so
 * without this the button re-enables and a second POST is reachable — which the server then refuses
 * with a 409, painting over the success the user just earned. A track with no wiring keeps an inert
 * click, which is Schedule 11's until Epic 26.
 */
type TransitionWiring = {
  readonly onClick: () => void
  readonly busy: boolean
}

const INERT: TransitionWiring = { onClick: () => undefined, busy: false }

/**
 * The two reversals' wiring, absent on a track whose transitions have not shipped. Absent is NOT the
 * same as `INERT`: an inert `onClick` is still a function, which would leave the button live and
 * silently doing nothing. Absent leaves `onClick` undefined, which is `CheckStatusActions`' own
 * "no handler ⇒ disabled" contract, and the hint beside it says so.
 */
type ReversalWiring = {
  readonly setToDraft?: TransitionWiring
  readonly setToSubmit?: TransitionWiring
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
 * Submit only for an admin while it is Verified (legacy enabled both whenever rendered —
 * `canUserSetToDraft/Submit` is the same admin test, so once the object is built at all the user has
 * already passed it). Submit's gate is passed in (see `SubmitGate`), Verified's and the reversals'
 * wiring too. Validity is not part of any of them — the eleven-schedule gate fires on the
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
  verifyWiring: TransitionWiring = INERT,
  reversals: ReversalWiring = {},
): TrackActions => {
  const canVerify = isAdmin && track.statusCode === SUBMITTED
  // One reversal, built only inside its own `rendered=` state below — so the admin half of legacy's
  // rule has already passed and no not-an-admin hint is reachable here. Wired, the button is live and
  // greys only while its own POST and the re-sweep behind it are in flight; unwired (Schedule 11,
  // until Epic 26) it keeps legacy's presence but stays greyed and says why.
  const reversal = (wiring: TransitionWiring | undefined): TrackAction =>
    wiring === undefined
      ? // `enabled: false` AND no handler. The bar disables on either one
        // (`CheckStatusActions.tsx:41`), but saying it twice is not redundancy: a reader — or a
        // future simplification of `ActionButton` — that trusts `enabled` alone would otherwise turn
        // Schedule 11's dead reversals into live-and-inert buttons, which is the one outcome this
        // branch exists to prevent.
        { enabled: false, disabledReason: HINT_NOT_WIRED, onClick: undefined }
      : { enabled: !wiring.busy, onClick: wiring.onClick }
  return {
    setToDraft:
      isAdmin && track.statusCode === SUBMITTED ? reversal(reversals.setToDraft) : undefined,
    setToSubmit:
      isAdmin && track.statusCode === VERIFIED ? reversal(reversals.setToSubmit) : undefined,
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
 * Submit, Verified and the two admin reversals (Schedules 1–10) are the live transitions: each asks
 * legacy's `Confirmation Required`
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
  const [confirming, setConfirming] = useState<Pending | null>(null)
  const [saving, setSaving] = useState(false)
  const [verifying, setVerifying] = useState(false)
  // One flag for both reversals: they can never render together (one needs Submitted, the other
  // Verified), and after a transition the OTHER one appears — which must stay greyed until the
  // re-sweep lands, or it offers to undo a change the screen has not shown yet.
  const [reversing, setReversing] = useState(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)

  // Every piece of action state above belongs to ONE working context. mill/year lives in a provider,
  // so a context change does NOT remount this page — the sweep re-issues in place, which is what
  // `useCheckStatusSweep` and the response guards are all built around — and nothing was resetting
  // the settled outcome with it (Story 17.2 review, SScholefield). Left standing, the previous
  // context's banner reappears the moment the new sweep lands, `busy` derived from a success greys
  // that context's Submit against its own `canSubmit`, and a prompt still pending would answer for a
  // report the user never confirmed. Adjusted DURING render, not in an effect, so no committed render
  // ever reads the old context's outcome (React's "adjusting state when a prop changes").
  const contextKey = `${millId}/${year}`
  const [actionContext, setActionContext] = useState(contextKey)
  if (actionContext !== contextKey) {
    setActionContext(contextKey)
    setConfirming(null)
    setOutcome(null)
    // `reversing` is deliberately NOT reset here, and neither is `verifying`. Clearing it would
    // un-grey the new context's buttons while `busyRef` is still held by the old request, and
    // `busyRef` is what the confirm handlers actually check — so the control would be live and its
    // Yes would be swallowed with no explanation. The unconditional `.finally` releases both
    // together a moment later; until it does, greying is the honest thing to show.
  }

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
  const openPrompt = (which: Pending) => {
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

  // Follows verify, not submit: the outcome clears when the prompt OPENS. The two shipped handlers
  // disagree (`requestSubmit` leaves the previous banner standing) and verify's is the one that
  // survived review — a stale success sitting under a fresh question reads as though the question
  // has already been answered. Harmonising `requestSubmit` is deliberately out of this story (D3).
  const requestReversal = (which: 'setToDraft' | 'setToSubmit') => () => {
    setOutcome(null)
    openPrompt(which)
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
        // Released unconditionally, as verify's is: gated on `isCurrent()` it strands the lock when
        // the context moved while the POST was in flight, and the new context's Submit pair stays
        // greyed for the life of the mount. The two branches above are what must not write across a
        // context change; the lock is the opposite — it must always come off.
        setSaving(false)
      })
  }

  // The verify transition, hung off the prompt's Yes exactly as legacy hung it (checkStatus.xhtml:202).
  // The mill/year come from the sweep body, which the hook has already refused unless it echoes the
  // request's own context. Every branch re-checks that the context has not moved underneath it before
  // it writes to state, and the lock is released unconditionally — a conditional reset strands it.
  const confirmVerify = () => {
    // Closed BEFORE the guard returns. Story 18.2 put a second wired transition on the same status,
    // so this early return is now reachable from a prompt the user is looking at; leaving the dialog
    // mounted would give them a Yes that silently does nothing.
    setConfirming(null)
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    focusOutcomeRef.current = true
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
        // A 409 means the verdict this button was offered on has gone stale underneath it — the
        // gate now fails, the mill closed, or the status moved. Re-sweep so what is on screen
        // agrees with the reason in the banner, exactly as confirmSubmit does; without this the
        // user reads why it was refused while the table still shows the state that offered it.
        if (isConflict(error)) {
          setReloadToken((token) => token + 1)
        }
      })
      .finally(() => {
        busyRef.current = false
        setVerifying(false)
      })
  }

  // The two admin reversals, built from one factory because they differ only in their path, their
  // fallback text and the status they are offered at (CheckStatusApi.java:181-220 says as much of the
  // server halves). Shaped on `confirmVerify` and not on `confirmSubmit`: the synchronous `busyRef`
  // is what stops two clicks inside one tick from both passing a state flag that is still false, and
  // the unconditional release is what stops a context change mid-flight from stranding the lock.
  // Legacy hung its action on the dialog's Yes and hid the dialog in the same breath
  // (checkStatus.xhtml:190, :194); `setConfirming(null)` first is that, exactly.
  const confirmReversal = (which: 'setToDraft' | 'setToSubmit') => () => {
    setConfirming(null)
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    focusOutcomeRef.current = true
    setReversing(true)
    const { path, fallback } = REVERSALS[which]
    apiService
      .getAxiosInstance()
      .post<SetTrackStatusResponse>(
        `/v1/check-status/${path}?millId=${data.millId}&year=${data.year}`,
      )
      .then((response) => {
        if (!isCurrent()) return
        // The server's own words. A successful Set to Submit renders legacy's SUBMIT success text —
        // legacy reused that key and minted no "verification reversed" message, so there is none to
        // send (UC-CHK-018.md:135). It reads like a copy/paste slip and is parity.
        setOutcome({ kind: 'success', text: response.data.message.text })
        setReloadToken((token) => token + 1)
      })
      .catch((error: unknown) => {
        if (!isCurrent()) return
        // A banner, never `renderScheduleLoadState`: a refused transition must not blank a page whose
        // verdicts are still good. The branch is on the STATUS, never on the text — an error body
        // carries only RFC 7807 `detail`, with no key to read.
        setOutcome({ kind: 'error', text: extractDetail(error) || fallback })
        if (isConflict(error)) {
          setReloadToken((token) => token + 1)
        }
      })
      .finally(() => {
        busyRef.current = false
        setReversing(false)
      })
  }

  const isSubmitter = hasRole(ILCR_ROLES.submitter)
  const isAdmin = hasRole(ILCR_ROLES.admin)
  // Verified and the two reversals share ONE lock, because they share `busyRef` and — since Story
  // 18.2 — they share a status: at Submitted an administrator is offered both Verified and Set to
  // Draft. A per-button flag left the other one live during a transition, where its Yes hit the
  // `busyRef` guard and did nothing, and then, once the track had moved underneath it, sent a
  // request the server could only refuse with a 409 that painted over the success just earned.
  const transitionInFlight = verifying || reversing || isReloading
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
    { onClick: requestVerify, busy: transitionInFlight },
    // ONE in-flight term across all three, not one per button. `isReloading` keeps them greyed across
    // the refresh window, so the reversal that APPEARS after a successful one cannot be pressed
    // against a status the screen has not caught up to yet.
    {
      setToDraft: { onClick: requestReversal('setToDraft'), busy: transitionInFlight },
      setToSubmit: { onClick: requestReversal('setToSubmit'), busy: transitionInFlight },
    },
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
          // Lookups, not a four-branch ternary chain: a mis-paired prompt and handler is the one
          // defect this block can carry, and a chain is where it would hide.
          message={CONFIRM_PROMPTS[confirming]}
          confirmLabel="Yes"
          cancelLabel="Cancel"
          onConfirm={
            {
              submit: confirmSubmit,
              verify: confirmVerify,
              setToDraft: confirmReversal('setToDraft'),
              setToSubmit: confirmReversal('setToSubmit'),
            }[confirming]
          }
          onCancel={cancelConfirm}
        />
      )}
    </div>
  )
}

export default CheckStatus
