import type { FC, ReactNode } from 'react'
import { Accordion, AccordionItem, Column, Grid } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import renderScheduleLoadState from '@/components/core/ScheduleLoadState'
import CheckStatusNotifications from '@/components/core/CheckStatusNotifications'
import type { ScheduleCheckResult, TrackCheckResult } from '@/interfaces/CheckStatusSweep'
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
 * The legacy rules, exactly, evaluated on ONE track's own status code (UserSessionMB.java:502-570,
 * CheckStatusMB.java:162-192): Submit = that track in Draft AND the user is the licensee
 * (ILCR_SUBMITTER); Verified = that track Submitted AND the user is NOT the licensee (ILCR_ADMIN);
 * Set to Draft is RENDERED only for an admin while the track is Submitted, Set to Submit only for
 * an admin while it is Verified (both enabled whenever rendered — `canUserSetToDraft/Submit` is the
 * same admin test). Validity is not part of any of them — the ten-schedule gate fires on the click,
 * server-side. Display state only; the server is the authorization. Clicks are inert until the
 * transition stories supply the handlers; the gates are unaffected by that.
 */
const trackActions = (
  track: TrackCheckResult,
  isSubmitter: boolean,
  isAdmin: boolean,
  hints: { readonly notDraft: string; readonly notSubmitted: string },
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
 * Nothing on this page navigates: the correct-and-re-check loop runs through the navigation menu, and
 * every visit remounts the route and re-issues the sweep.
 */
const CheckStatus: FC = () => {
  const { millId, year, contextMissing } = useScheduleContextGuard()
  const { data, isLoading, errorDetail } = useCheckStatusSweep(millId, year)
  const { hasRole } = useAuth()

  const header = <ScheduleTombstone title={PAGE_TITLE} />
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

  const isSubmitter = hasRole(ILCR_ROLES.submitter)
  const isAdmin = hasRole(ILCR_ROLES.admin)
  const actions1To10 = trackActions(data.schedules1To10, isSubmitter, isAdmin, {
    notDraft: HINT_NOT_DRAFT,
    notSubmitted: HINT_NOT_SUBMITTED,
  })
  const actions11 = trackActions(data.schedule11, isSubmitter, isAdmin, {
    notDraft: HINT_NOT_DRAFT_11,
    notSubmitted: HINT_NOT_SUBMITTED_11,
  })

  return (
    <div className="app-page schedule-page">
      {header}
      <Grid fullWidth className="app-page__body">
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
    </div>
  )
}

export default CheckStatus
