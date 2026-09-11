import type { ReactElement, ReactNode } from 'react'
import { Column } from '@carbon/react'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageState from '@/components/core/PageState'

// Client-only chrome (no request behind it), from the legacy bundle
// (`millYearNotSelectedErrorMsg`, `messages.properties:9`) with ONE deliberate difference: the bundle
// value ends in a trailing space and this literal does not, matching the sibling convention for
// client-rendered chrome. That divergence is why every comparison below is trim-compared — the
// server raises the same sentence WITH its trailing space, and it must land on the same titled state
// rather than in the generic failure. Pinned against the real bundle file in this module's test.
export const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'

/**
 * `millNotActiveForCurrentYearMsg` (ERR-002), verbatim from the bundle
 * (`backend/src/main/resources/messages.properties:10`). The backend raises `MillClosedException`
 * when the selected mill is not `ACT` for the reporting year, which the global handler serves as a
 * 409 problem document carrying exactly this `detail`.
 *
 * Matching on the TEXT rather than on a status code is deliberate: the schedule pages' shared load
 * hook surfaces only `detail` (`useScheduleDocument.ts` → `extractDetail`), and this sentence is
 * raised by one exception and no other, so the text identifies the state unambiguously. It is NOT a
 * client-side status read — nothing here looks at `MillMaintenance.MILL_CLOSED`, at the mill's own
 * status code, or at anything the client could compute on its own (AD-9).
 */
export const ERR_MILL_CLOSED =
  'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.'

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
 * The non-content states every schedule page opens with, in the order they must be checked: no
 * mill/year selected, still loading, mill closed for the reporting year, load failed. Returns the
 * element to render, or null when the page has a document to show.
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

  // Checked BEFORE the generic failure below, which would otherwise swallow it into
  // "Unable to load <schedule>" — the wrong framing for the one guard that is not a failure at all
  // but a context the operator has to change on the Home Page. Legacy raised this as its own state
  // (ERR-002) and suppressed the form, and the title mirrors the mill/year guard above so the two
  // context guards read as one family rather than as an error and a near-error.
  //
  // TRIM-COMPARED, not `===`: the bundle's own header warns that some values intentionally end in
  // trailing space(s), so an exact match against a hand-typed client copy is one whitespace edit
  // away from silently rerouting a real guard into the generic failure. The SUBTITLE is still the
  // server's own `detail`, so nothing about the rendered text is normalised.
  if (errorDetail?.trim() === ERR_MILL_CLOSED) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Mill not active for Reporting Year',
          subtitle: errorDetail,
        }}
      />
    )
  }

  // The SERVER-raised twin of the `contextMissing` guard above. Without this branch the two context
  // guards are not a pair: the client-only path gets the titled state, while the same condition
  // arriving as a ProblemDetail (`MillYearNotSelectedException`, whose bundle text carries a real
  // trailing space) fell through to "Unable to load <schedule>" — an error framing for a context the
  // operator simply has to set on the Home Page.
  if (errorDetail?.trim() === ERR_MILL_YEAR_NOT_SELECTED) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Mill and Reporting Year required',
          subtitle: errorDetail,
        }}
      />
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
