import type { FC } from 'react'
import { useId } from 'react'
import { Button, Column } from '@carbon/react'

type CheckStatusActionsProps = {
  /** Whether the current role and the 1–10 track's status allow submission — decided by the page. */
  readonly canSubmit: boolean
  /** Why Submit is greyed, when it is; rendered visually hidden for assistive tech. */
  readonly disabledReason?: string
  /** The click target. Supplied by the confirm-dialog story; absent, the button stays disabled. */
  readonly onSubmit?: () => void
}

/**
 * The Check Status action bar — legacy rendered it twice, above and below the Schedules 1–10
 * accordion, and so does the page: one element placed twice with the same props, never two bars with
 * their own gates. Submit is always rendered and greyed rather than hidden, exactly as legacy did
 * (`disabled="#{!checkStatusMB.canUserSubmitReport(false)}"`).
 *
 * A disabled Carbon button is not focusable, so greying alone tells a screen-reader user nothing —
 * less than legacy's greying told a sighted one. The hint restores that, `cds--visually-hidden` so it
 * stays out of the visual page, and `aria-describedby` is wired only while the hint is rendered so no
 * dangling id is ever announced. `useId` keeps the two instances' hints distinct.
 */
const CheckStatusActions: FC<CheckStatusActionsProps> = ({
  canSubmit,
  disabledReason,
  onSubmit,
}) => {
  const hintId = useId()
  const disabled = !canSubmit || onSubmit === undefined
  const showHint = disabled && disabledReason !== undefined
  return (
    <Column sm={4} md={8} lg={16} className="check-status__actions">
      <Button
        kind="primary"
        disabled={disabled}
        onClick={onSubmit}
        aria-describedby={showHint ? hintId : undefined}
      >
        Submit
      </Button>
      {showHint && (
        <span id={hintId} className="cds--visually-hidden">
          {disabledReason}
        </span>
      )}
    </Column>
  )
}

export default CheckStatusActions
