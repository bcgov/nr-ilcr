import type { FC } from 'react'
import type { MessageInfo } from '@/interfaces/Schedule1Response'
import CheckStatusNotifications from '@/components/core/CheckStatusNotifications'
import NotificationColumn from '@/components/core/NotificationColumn'

/** The subset of a schedule's check-status response these banners read. */
type CheckResult = {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
}

type ScheduleBannersProps = {
  /** The API's verbatim success line from the last write (AD-8). */
  readonly message: string | null
  /** A failed action: the API's ProblemDetail detail, or the page's own save-gate text. */
  readonly actionError: string | null
  readonly checkResult: CheckResult | null
  /** Per-row "requirements met" lines, for the schedules whose API emits them. */
  readonly rowMessages?: readonly MessageInfo[]
  /** Distinguishes this page's check-status notification keys, e.g. `culvert`. */
  readonly keyPrefix: string
}

/**
 * The banner stack every schedule page opens its grid with: last write's result, last failure, last
 * Check Status result.
 *
 * NotificationColumn IS a Carbon Column, so this renders a fragment of direct grid children — place
 * it as a direct child of the page grid or the span classes lose their meaning and the groups
 * misalign.
 */
const ScheduleBanners: FC<ScheduleBannersProps> = ({
  message,
  actionError,
  checkResult,
  rowMessages,
  keyPrefix,
}) => (
  <>
    {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
    {actionError && (
      <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
    )}
    {checkResult && (
      <CheckStatusNotifications
        keyPrefix={keyPrefix}
        errors={checkResult.errors}
        rowMessages={rowMessages}
        requirementsMetMessage={checkResult.requirementsMetMessage}
        requirementsMet={checkResult.requirementsMet}
      />
    )}
  </>
)

export default ScheduleBanners
