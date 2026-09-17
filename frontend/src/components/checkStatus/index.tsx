import type { FC, ReactNode } from 'react'
import { useRef, useState } from 'react'
import { Accordion, AccordionItem, Column, Grid, Modal } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import renderScheduleLoadState from '@/components/core/ScheduleLoadState'
import CheckStatusNotifications from '@/components/core/CheckStatusNotifications'
import NotificationColumn from '@/components/core/NotificationColumn'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import type {
  ScheduleCheckResult,
  TrackCheckResult,
  VerifyReportResponse,
} from '@/interfaces/CheckStatusSweep'
import type { TrackAction } from './CheckStatusActions'
import CheckStatusActions from './CheckStatusActions'
import useCheckStatusSweep from './useCheckStatusSweep'
import { flattenVerdict, SCHEDULE_TITLES } from './verdicts'
import './index.scss'

const api = () => apiService.getAxiosInstance()

const PAGE_TITLE = 'Check Status'
const DRAFT = 'D'
const SUBMITTED = 'S'
const VERIFIED = 'V'

// Legacy's confirmation, resolved from msg['confirmVerifySch1-10Msg'] before any server call
// (checkStatus.xhtml:201, messages.properties:103). Client-owned because it is pre-request chrome: no
// response carries it and the API bundle has no such key. A test asserts it byte for byte against that
// bundle, so the two cannot drift apart unnoticed. The header is legacy's own literal, shared by all
// eight of this page's dialogs, which is why the prompt body is the only reliable way to tell them apart.
export const CONFIRM_VERIFY_1_TO_10 = "Please confirm you'd like to set Schedules 1-10 to VERIFIED?"
export const CONFIRM_HEADING = 'Confirmation Required'

/** Shown only when a failure carries no problem+json body of its own (network, non-JSON 5xx). */
export const VERIFY_FAILED = 'The report could not be verified.'

// Client-authored hints beside a greyed button (legacy greyed with no explanation). Chosen here, by
// which half of the legacy rule failed, and passed down — the bar itself knows nothing about roles.
export const HINT_NOT_SUBMITTER = "Submitting is the licensee's action"
export const HINT_NOT_DRAFT = 'Available while Schedules 1-10 are in Draft'
export const HINT_NOT_DRAFT_11 = 'Available while Schedule 11 is in Draft'
export const HINT_NOT_ADMIN = "Verifying is an administrator's action"
export const HINT_NOT_SUBMITTED = 'Available once Schedules 1-10 are Submitted'
export const HINT_NOT_SUBMITTED_11 = 'Available once Schedule 11 is Submitted'

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
 * One attempt's single outcome. A discriminated union rather than a success channel beside an error
 * channel: legacy could render an error AND a success from the same click (CheckStatusMB.java:278-280
 * adds the error, then :284 adds the success anyway), and one slot makes that impossible instead of
 * merely asserted against. Text is always the server's — its success message or its ProblemDetail
 * `detail` (AD-8); the title beside it is the shared client vocabulary.
 */
type Outcome = {
  readonly kind: 'success' | 'error'
  readonly text: string
}

/**
 * How a track's Verified button behaves. `busy` greys it for the duration of a request the button
 * itself started — legacy left it clickable, and its server had no idempotency guard either, so this
 * is deliberate hardening rather than parity. A track with no wiring keeps an inert click, which is
 * what the reversals and Schedule 11 still have until their own stories land.
 */
type VerifyWiring = {
  readonly onClick: () => void
  readonly busy: boolean
}

const INERT: VerifyWiring = { onClick: () => undefined, busy: false }

/**
 * The legacy rules, exactly, evaluated on ONE track's own status code (UserSessionMB.java:502-570,
 * CheckStatusMB.java:162-192): Submit = that track in Draft AND the user is the licensee
 * (ILCR_SUBMITTER); Verified = that track Submitted AND the user is NOT the licensee (ILCR_ADMIN);
 * Set to Draft is RENDERED only for an admin while the track is Submitted, Set to Submit only for
 * an admin while it is Verified (both enabled whenever rendered — `canUserSetToDraft/Submit` is the
 * same admin test). Validity is not part of any of them — the eleven-schedule gate fires on the click,
 * server-side, and an administrator on a Submitted-but-failing report gets an enabled button and the
 * server's verbatim refusal. Display state only; the server is the authorization.
 */
const trackActions = (
  track: TrackCheckResult,
  isSubmitter: boolean,
  isAdmin: boolean,
  hints: { readonly notDraft: string; readonly notSubmitted: string },
  verifyWiring: VerifyWiring = INERT,
): TrackActions => {
  const canSubmit = isSubmitter && track.statusCode === DRAFT
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
      enabled: canSubmit,
      disabledReason: canSubmit ? undefined : isSubmitter ? hints.notDraft : HINT_NOT_SUBMITTER,
      onClick: () => undefined,
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
 * Nothing on this page navigates: the correct-and-re-check loop runs through the navigation menu, and
 * every visit remounts the route and re-issues the sweep.
 */
const CheckStatus: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()
  // Bumped after a transition commits, so both reads that describe the track — the sweep's status code
  // and the tombstone's status lines — come back from the server rather than being guessed at here.
  const [reloadToken, setReloadToken] = useState(0)
  const { data, isLoading, errorDetail } = useCheckStatusSweep(millId, year, reloadToken)
  const { hasRole } = useAuth()
  const [confirmingVerify, setConfirmingVerify] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  // Synchronous, unlike the state flag: two clicks inside one tick would both see `verifying` false.
  const busyRef = useRef(false)
  // Which button opened the prompt, so declining puts focus back where it was. The prompt is mounted
  // only while it is pending, so there is no dialog left for Carbon to restore focus from on close.
  const launcherRef = useRef<HTMLElement | null>(null)

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

  // Opening the prompt clears the last outcome and touches no server: legacy's Verified button was
  // type="button" whose whole behaviour was confirmVerify.show() (checkStatus.xhtml:57-62).
  const requestVerify = () => {
    launcherRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    setOutcome(null)
    setConfirmingVerify(true)
  }

  // Declining is client-side only — legacy's Cancel carried no action at all (checkStatus.xhtml:203).
  // Either bar can open the prompt, so focus returns to whichever button did, not to a fixed one.
  const cancelVerify = () => {
    setConfirmingVerify(false)
    launcherRef.current?.focus()
  }

  // The transition, hung off the prompt's Yes exactly as legacy hung it (checkStatus.xhtml:202). The
  // mill/year come from the sweep body, which the hook has already refused unless it echoes the
  // request's own context. Every branch re-checks that the context has not moved underneath it before
  // it writes to state, and the lock is released unconditionally — a conditional reset strands it.
  const confirmVerify = () => {
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    setConfirmingVerify(false)
    setVerifying(true)
    api()
      .post<VerifyReportResponse>(`/v1/check-status/verify?millId=${data.millId}&year=${data.year}`)
      .then((response) => {
        if (!isCurrent()) {
          return
        }
        setOutcome({ kind: 'success', text: response.data.message.text })
        setReloadToken((token) => token + 1)
      })
      .catch((error: unknown) => {
        if (!isCurrent()) {
          return
        }
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
    { onClick: requestVerify, busy: verifying },
  )
  const actions11 = trackActions(data.schedule11, isSubmitter, isAdmin, {
    notDraft: HINT_NOT_DRAFT_11,
    notSubmitted: HINT_NOT_SUBMITTED_11,
  })

  return (
    <div className="app-page schedule-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {outcome && (
          <NotificationColumn
            kind={outcome.kind}
            title={outcome.kind === 'success' ? 'Success' : 'Action failed'}
            subtitle={outcome.text}
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
      {confirmingVerify && (
        <Modal
          open
          modalHeading={CONFIRM_HEADING}
          primaryButtonText="Yes"
          secondaryButtonText="Cancel"
          onRequestClose={cancelVerify}
          onSecondarySubmit={cancelVerify}
          onRequestSubmit={confirmVerify}
        >
          <p>{CONFIRM_VERIFY_1_TO_10}</p>
        </Modal>
      )}
    </div>
  )
}

export default CheckStatus
