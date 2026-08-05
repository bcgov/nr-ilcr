import type { FC } from 'react'
import { useEffect } from 'react'
import { Column, Grid } from '@carbon/react'
import useMillYear from '@/context/millYear/useMillYear'
import useWorkingContext from '@/components/core/WorkingContext/useWorkingContext'
import WorkingContextLines from '@/components/core/WorkingContext/WorkingContextLines'
import './index.scss'

type Props = {
  // The page name — left column, e.g. "Schedule 2".
  title: string
  // The sub-page trail under the title, rendered breadcrumb-style. A single string is one crumb; an
  // array threads the current level (e.g. ["Special Log Transportation Costs", "Harbour Dump", "Towing"]).
  subtitle?: string | string[]
}

// The schedule "tombstone": a two-column page header shared by every schedule. Left = page identity
// (name + current sub-page); right = the working-context mill/status lines (the same data the global
// ContextBanner shows). It replaces each schedule's PageTitle header, so it also owns the document.title
// side effect PageTitle set. The right column reuses the shared useWorkingContext/WorkingContextLines
// so it stays identical to the banner; it is intentionally NOT a landmark region (the global banner
// already owns the "Working context" landmark — a second one would duplicate it for screen readers).
const ScheduleTombstone: FC<Props> = ({ title, subtitle }) => {
  const { millId, year } = useMillYear()
  const context = useWorkingContext(millId, year)

  useEffect(() => {
    document.title = `${title} | ILCR`
  }, [title])

  // Normalize the subtitle to a crumb trail so callers can pass either one label or a threaded level.
  const crumbs = subtitle == null ? [] : Array.isArray(subtitle) ? subtitle : [subtitle]

  return (
    <Grid fullWidth className="app-page__header">
      <Column sm={4} md={8} lg={16}>
        <div className="schedule-tombstone">
          <div className="schedule-tombstone__identity">
            <h1 className="schedule-tombstone__title">{title}</h1>
            {crumbs.length > 0 && (
              <p className="schedule-tombstone__subtitle">
                {crumbs.map((crumb, index) => (
                  <span key={crumb}>
                    {index > 0 && (
                      <span className="schedule-tombstone__crumb-sep" aria-hidden="true">
                        {' › '}
                      </span>
                    )}
                    {crumb}
                  </span>
                ))}
              </p>
            )}
          </div>
          {context && (
            <div className="schedule-tombstone__context">
              <WorkingContextLines context={context} lineClassName="schedule-tombstone__line" />
            </div>
          )}
        </div>
      </Column>
    </Grid>
  )
}

export default ScheduleTombstone
