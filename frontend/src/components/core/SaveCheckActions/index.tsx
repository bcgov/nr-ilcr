import type { FC } from 'react'
import { Button, Column } from '@carbon/react'
import { CheckmarkOutline, Save } from '@carbon/icons-react'

type SaveCheckActionsProps = {
  /** Modifier class for the actions Column, e.g. {@code 'schedule-7b__actions'}. */
  readonly className: string
  readonly saveDisabled: boolean
  readonly checkDisabled: boolean
  readonly onSave: () => void
  readonly onCheckStatus: () => void
}

/**
 * The Save + Check Status pair that the list-style schedules (7A, 7B) render both above and below
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
  onSave,
  onCheckStatus,
}) => (
  <Column sm={4} md={8} lg={16} className={className}>
    <Button kind="primary" renderIcon={Save} disabled={saveDisabled} onClick={onSave}>
      Save
    </Button>
    <Button
      kind="tertiary"
      renderIcon={CheckmarkOutline}
      disabled={checkDisabled}
      onClick={onCheckStatus}
    >
      Check Status
    </Button>
  </Column>
)

export default SaveCheckActions
