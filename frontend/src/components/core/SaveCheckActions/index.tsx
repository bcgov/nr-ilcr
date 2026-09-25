import type { FC } from 'react'
import { useId } from 'react'
import { Button, Column } from '@carbon/react'
import { CheckmarkOutline, Save } from '@carbon/icons-react'

type SaveCheckActionsProps = {
  /** Modifier class for the actions Column, e.g. {@code 'schedule-7b__actions'}. */
  readonly className: string
  readonly saveDisabled: boolean
  readonly checkDisabled: boolean
  /**
   * Why Check Status is greyed, when a page has a reason worth saying (Schedule 11: unsaved changes).
   * Rendered visually hidden and wired by `aria-describedby` only while the button is disabled — a
   * disabled Carbon button is not focusable, so greying alone tells a screen-reader user nothing.
   */
  readonly checkDisabledReason?: string
  readonly onSave: () => void
  readonly onCheckStatus: () => void
}

/**
 * The Save + Check Status pair that the list-style schedules (7A, 7B, 11) render both above and below
 * their report list, exactly as legacy did. Save is disabled separately from Check Status because it
 * additionally has nothing to do with an empty list — the batch endpoint rejects an empty body.
 *
 * Distinct from {@code ScheduleActions}, which is the Save / Check Status / Delete bar of the
 * single-document schedules: these pages delete a row, not the schedule.
 */
const SaveCheckActions: FC<SaveCheckActionsProps> = ({
  className,
  saveDisabled,
  checkDisabled,
  checkDisabledReason,
  onSave,
  onCheckStatus,
}) => {
  const hintId = useId()
  const showHint = checkDisabled && checkDisabledReason !== undefined
  return (
    <Column sm={4} md={8} lg={16} className={className}>
      <Button kind="primary" renderIcon={Save} disabled={saveDisabled} onClick={onSave}>
        Save
      </Button>
      <Button
        kind="tertiary"
        renderIcon={CheckmarkOutline}
        disabled={checkDisabled}
        onClick={onCheckStatus}
        aria-describedby={showHint ? hintId : undefined}
      >
        Check Status
      </Button>
      {showHint && (
        <span id={hintId} className="cds--visually-hidden">
          {checkDisabledReason}
        </span>
      )}
    </Column>
  )
}

export default SaveCheckActions
