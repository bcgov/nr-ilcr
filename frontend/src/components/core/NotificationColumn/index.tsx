import type { FC, Ref } from 'react'
import { Column, InlineNotification } from '@carbon/react'

type NotificationColumnProps = {
  kind: 'error' | 'warning' | 'info' | 'success'
  title: string
  subtitle?: string
  /**
   * Makes the banner a programmatic focus target (`tabIndex={-1}` — never in the tab order) so a
   * caller can move focus here when a result lands, which both announces it and brings it into view.
   * The idiom is schedule4's (`index.tsx:552-561`), where it replaced a `window.scrollTo` that moved
   * the viewport but not focus. Optional and defaulted off, so every existing caller is unaffected.
   */
  focusRef?: Ref<HTMLDivElement>
}

/**
 * A full-width `Column` wrapping a low-contrast `InlineNotification`. Every schedule page renders
 * this same success/error/warning banner shape (Save result, Action failed, Check Status, list
 * message) — extracted so those sites stop re-inlining the identical Column + InlineNotification
 * markup. Severity is always carried by BOTH the `kind` and an explicit `title` word, never colour
 * alone (WCAG 2.1 AA). Text is passed verbatim from the API where applicable (AD-8).
 */
const NotificationColumn: FC<NotificationColumnProps> = ({ kind, title, subtitle, focusRef }) => (
  <Column sm={4} md={8} lg={16} ref={focusRef} tabIndex={focusRef ? -1 : undefined}>
    <InlineNotification kind={kind} lowContrast title={title} subtitle={subtitle} />
  </Column>
)

export default NotificationColumn
