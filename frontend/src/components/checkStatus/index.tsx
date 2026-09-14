import type { FC } from 'react'
import { Accordion, AccordionItem, Column, Grid } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import renderScheduleLoadState from '@/components/core/ScheduleLoadState'
import CheckStatusNotifications from '@/components/core/CheckStatusNotifications'
import type { ScheduleCheckResult, TrackCheckResult } from '@/interfaces/CheckStatusSweep'
import CheckStatusActions from './CheckStatusActions'
import useCheckStatusSweep from './useCheckStatusSweep'
import { flattenVerdict, SCHEDULE_TITLES } from './verdicts'
import './index.scss'

const PAGE_TITLE = 'Check Status'
const DRAFT = 'D'

// Client-authored hints beside a greyed Submit (legacy greyed with no explanation). Chosen here, by
// which half of the legacy rule failed, and passed down — the bar itself knows nothing about roles.
export const HINT_NOT_DRAFT = 'Available while Schedules 1-10 are in Draft'
export const HINT_NOT_SUBMITTER = "Submitting is the licensee's action"

/**
 * One schedule's accordion item: the legacy tab title and its result lines, every one the server's
 * own text. A verdict with nothing to say (no failing line, no met line) renders an empty item rather
 * than a fabricated summary — legacy's tab was empty in that state too.
 */
const ScheduleSection: FC<{ entry: ScheduleCheckResult }> = ({ entry }) => {
  const verdict = flattenVerdict(entry)
  const hasLines =
    verdict.errors.length > 0 ||
    verdict.warnings.length > 0 ||
    verdict.requirementsMetMessage !== null
  return (
    <AccordionItem open title={SCHEDULE_TITLES[entry.schedule]}>
      {hasLines && (
        <Grid fullWidth className="check-status__lines">
          <CheckStatusNotifications
            errors={verdict.errors}
            warnings={verdict.warnings}
            requirementsMetMessage={verdict.requirementsMetMessage}
            requirementsMet={verdict.requirementsMet}
            keyPrefix={`check-${entry.schedule}`}
          />
        </Grid>
      )}
    </AccordionItem>
  )
}

/** One track's region: an explicit heading (an accordion title is a button, not a heading) and its items. */
const TrackRegion: FC<{ id: string; heading: string; track: TrackCheckResult }> = ({
  id,
  heading,
  track,
}) => (
  <Column sm={4} md={8} lg={16}>
    <section className="check-status__region" aria-labelledby={id}>
      <h2 className="check-status__heading" id={id}>
        {heading}
      </h2>
      <Accordion>
        {track.schedules.map((entry) => (
          <ScheduleSection key={entry.schedule} entry={entry} />
        ))}
      </Accordion>
    </section>
  </Column>
)

/**
 * The Check Status page: every schedule's verdict at once, in legacy tab order, under the two track
 * regions legacy's two accordions drew — all twelve open — with the Submit bar above and below the
 * Schedules 1–10 region as legacy's two button rows were. Both track status lines come from the
 * tombstone's /mill-context read; the sweep's status code drives only the Submit gate.
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

  // The legacy submit rule, exactly (UserSessionMB.canUserSubmitReport): the 1–10 track is in Draft
  // AND the user is the submitter. Validity is not part of it — the ten-schedule gate fires on the
  // click, server-side. This is display state only; the server is the authorization.
  const isSubmitter = hasRole(ILCR_ROLES.submitter)
  const isDraft = data.schedules1To10.statusCode === DRAFT
  const canSubmit = isSubmitter && isDraft
  const disabledReason = canSubmit ? undefined : isSubmitter ? HINT_NOT_DRAFT : HINT_NOT_SUBMITTER
  // Inert until the confirm-dialog story supplies the real handler; the gate above is unaffected.
  const onSubmit = () => undefined

  const actions = (
    <CheckStatusActions canSubmit={canSubmit} disabledReason={disabledReason} onSubmit={onSubmit} />
  )

  return (
    <div className="app-page schedule-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {actions}
        <TrackRegion
          id="check-status-heading-1-10"
          heading="Schedules 1–10"
          track={data.schedules1To10}
        />
        {actions}
        <TrackRegion id="check-status-heading-11" heading="Schedule 11" track={data.schedule11} />
      </Grid>
    </div>
  )
}

export default CheckStatus
