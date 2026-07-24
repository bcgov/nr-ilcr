import type { FC, ReactNode } from 'react'
import { Column, Grid, InlineNotification } from '@carbon/react'

type PageStateNotification = {
  kind: 'error' | 'warning' | 'info' | 'success'
  title: string
  subtitle?: string
}

type PageStateProps = {
  /** The page header band (rendered above the body grid). */
  header: ReactNode
  /** Optional full-width notification rendered as the first body column. */
  notification?: PageStateNotification
  /** Extra full-width body content (e.g. a loader or an action button). */
  children?: ReactNode
}

/**
 * The shared `app-page` chrome for a schedule page's non-content states
 * (context-missing, loading, load-error): the header band plus the
 * `app-page__body` grid, optionally leading with a full-width notification.
 *
 * Extracted so each guard-state return stops re-inlining the identical shell —
 * the pattern was duplicated across every guard branch and both schedule pages.
 */
const PageState: FC<PageStateProps> = ({ header, notification, children }) => (
  <div className="app-page">
    {header}
    <Grid fullWidth className="app-page__body">
      {notification && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind={notification.kind}
            lowContrast
            hideCloseButton
            title={notification.title}
            subtitle={notification.subtitle}
          />
        </Column>
      )}
      {children}
    </Grid>
  </div>
)

export default PageState
