import type { FC } from 'react'
import { Column, InlineNotification } from '@carbon/react'

type NotificationColumnProps = {
  kind: 'error' | 'warning' | 'info' | 'success'
  title: string
  subtitle?: string
  // Optional hook for a page that renders SEVERAL of these at once and needs to assert how many
  // there are and in what order. Carbon's own InlineNotification is role="status", which a page may
  // also use for a live region, so role alone cannot distinguish them.
  testId?: string
}

/**
 * A full-width `Column` wrapping a low-contrast `InlineNotification`. Every schedule page renders
 * this same success/error/warning banner shape (Save result, Action failed, Check Status, list
 * message) — extracted so those sites stop re-inlining the identical Column + InlineNotification
 * markup. Severity is always carried by BOTH the `kind` and an explicit `title` word, never colour
 * alone (WCAG 2.1 AA). Text is passed verbatim from the API where applicable (AD-8).
 */
const NotificationColumn: FC<NotificationColumnProps> = ({ kind, title, subtitle, testId }) => (
  <Column sm={4} md={8} lg={16} data-testid={testId}>
    <InlineNotification kind={kind} lowContrast title={title} subtitle={subtitle} />
  </Column>
)

export default NotificationColumn
