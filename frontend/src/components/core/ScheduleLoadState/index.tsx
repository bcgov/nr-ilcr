import type { ReactElement, ReactNode } from 'react'
import { Column } from '@carbon/react'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageState from '@/components/core/PageState'

// Client-only chrome (no request behind it), verbatim from the legacy bundle. The literal has no
// trailing space (sibling convention); the SERVER's own context error (with its real trailing space)
// still renders verbatim when a request returns it.
export const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'

type ScheduleLoadStateOptions = {
  /** The page header band, rendered above every one of these states. */
  readonly header: ReactNode
  /** The schedule's display name, e.g. `Schedule 7B` — the only text that varies between pages. */
  readonly scheduleName: string
  readonly contextMissing: boolean
  readonly isLoading: boolean
  /**
   * The load failure's verbatim `detail`. Covers the context guards AND the action-key denial: those
   * ProblemDetails and the 403 all arrive here and each renders with the work area suppressed.
   */
  readonly errorDetail?: string | null
}

/**
 * The three non-content states every schedule page opens with, in the order they must be checked:
 * no mill/year selected, still loading, load failed. Returns the element to render, or null when the
 * page has a document to show.
 *
 * A plain function rather than a component because each of these is an EARLY RETURN from the page —
 * the work area must not render alongside them.
 */
export const renderScheduleLoadState = ({
  header,
  scheduleName,
  contextMissing,
  isLoading,
  errorDetail,
}: ScheduleLoadStateOptions): ReactElement | null => {
  if (contextMissing) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Mill and Reporting Year required',
          subtitle: ERR_MILL_YEAR_NOT_SELECTED,
        }}
      />
    )
  }

  if (isLoading) {
    return (
      <PageState header={header}>
        <Column sm={4} md={8} lg={16}>
          <LoadingScreen label={`Loading ${scheduleName}`} />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: `Unable to load ${scheduleName}`,
          subtitle: errorDetail,
        }}
      />
    )
  }

  return null
}

export default renderScheduleLoadState
