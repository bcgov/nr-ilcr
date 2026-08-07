import type { FC } from 'react'
import { Button, Column } from '@carbon/react'

type ScheduleActionsProps = {
  /** Modifier class for the actions Column, e.g. {@code 'schedule-3__actions'}. */
  className: string
  editable: boolean
  saving: boolean
  checking: boolean
  onSave: () => void
  onCheckStatus: () => void
  onDelete: () => void
}

/**
 * The Save / Check Status / Delete action bar shared by the top-level schedule pages. The disabled
 * logic is identical across schedules (writes gated on `editable` and in-flight requests); each page
 * only differs by its section modifier class, so that is the single prop that varies.
 */
const ScheduleActions: FC<ScheduleActionsProps> = ({
  className,
  editable,
  saving,
  checking,
  onSave,
  onCheckStatus,
  onDelete,
}) => (
  <Column sm={4} md={8} lg={16} className={className}>
    <Button kind="primary" size="md" disabled={!editable || saving} onClick={onSave}>
      Save
    </Button>
    <Button
      kind="tertiary"
      size="md"
      disabled={!editable || saving || checking}
      onClick={onCheckStatus}
    >
      Check Status
    </Button>
    <Button kind="danger--tertiary" size="md" disabled={!editable || saving} onClick={onDelete}>
      Delete
    </Button>
  </Column>
)

export default ScheduleActions
