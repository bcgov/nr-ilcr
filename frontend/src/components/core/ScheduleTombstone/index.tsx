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
  // array threads the current level (e.g. ["Special Log Transportation Systems", "Harbour Dump", "Towing"]).
  subtitle?: string | string[]
}

// The schedule "tombstone": a two-column page header shared by every schedule. Left = page identity
// (name + current sub-page); right = the working-context mill/status lines (the same data the
// ContextBanner shows). It replaces each schedule's PageTitle header (so it also owns the document.title
// side effect PageTitle set) — which means these pages do NOT render the PageTitle-hosted ContextBanner.
// The right column therefore carries the same "Working context" landmark ContextBanner uses, so screen
// readers get one labelled region here just as they do on the PageTitle pages (no page has both).
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
            <section className="schedule-tombstone__context" aria-label="Working context">
              <WorkingContextLines context={context} lineClassName="schedule-tombstone__line" />
            </section>
          )}
        </div>
      </Column>
    </Grid>
  )
}

export default ScheduleTombstone
