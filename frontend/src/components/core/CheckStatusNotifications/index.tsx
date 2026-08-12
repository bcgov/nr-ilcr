import type { FC } from 'react'
import type { MessageInfo } from '@/interfaces/Schedule1Response'
import NotificationColumn from '@/components/core/NotificationColumn'

type CheckStatusNotificationsProps = {
  /** One verbatim line per missing value, in the server's emission order. */
  readonly errors: readonly MessageInfo[]
  /**
   * Per-row "requirements met" lines, where the schedule emits them (Schedule 7A's `bridgeMessages`).
   * Schedules whose legacy screen emitted only the schedule-wide line pass nothing — inventing a
   * per-row message would be fabricating text the API never sent (AD-8).
   */
  readonly rowMessages?: readonly MessageInfo[]
  /** The schedule-wide all-met line; present only when every row passes. */
  readonly requirementsMetMessage: MessageInfo | null
  readonly requirementsMet: boolean
  /** Distinguishes this page's notification keys, e.g. `culvert`. */
  readonly keyPrefix: string
}

/**
 * The Check Status result banners shared by the schedule pages. Every line is the API's own text
 * (AD-8); the only client-authored wording is the last-resort pair below.
 *
 * NotificationColumn IS a Carbon Column, so this renders a fragment of direct grid children — the
 * caller must place it as a direct child of the page grid, alongside its other banners, or the span
 * classes lose their meaning and the two groups misalign.
 */
const CheckStatusNotifications: FC<CheckStatusNotificationsProps> = ({
  errors,
  rowMessages = [],
  requirementsMetMessage,
  requirementsMet,
  keyPrefix,
}) => (
  <>
    {errors.map((error, index) => (
      <NotificationColumn
        // Missing-value lines repeat verbatim across rows, so the list index is what keeps
        // otherwise-identical entries distinct.
        key={`${keyPrefix}-check-error-${String(index)}-${error.key}`}
        kind="error"
        title="Action required"
        subtitle={error.text}
      />
    ))}
    {rowMessages.map((met, index) => (
      <NotificationColumn
        key={`${keyPrefix}-check-met-${String(index)}-${met.key}`}
        kind="success"
        title="Requirements met"
        subtitle={met.text}
      />
    ))}
    {/* Present only when every row passes; mixed results carry no schedule-wide banner. */}
    {requirementsMetMessage && (
      <NotificationColumn
        kind="success"
        title="Requirements met"
        subtitle={requirementsMetMessage.text}
      />
    )}
    {/* A result carrying no message at all would otherwise render nothing, leaving the button looking
        dead. requirementsMet is the one field always populated. */}
    {errors.length === 0 && rowMessages.length === 0 && !requirementsMetMessage && (
      <NotificationColumn
        kind={requirementsMet ? 'success' : 'warning'}
        title="Status checked"
        subtitle={
          requirementsMet
            ? 'All requirements for this schedule have been met'
            : 'This schedule has outstanding requirements.'
        }
      />
    )}
  </>
)

export default CheckStatusNotifications
