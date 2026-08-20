import type { FC } from 'react'
import { Button, Column } from '@carbon/react'

type ScheduleActionsProps = {
  /** Modifier class for the actions Column, e.g. {@code 'schedule-3__actions'}. */
  className: string
  editable: boolean
  saving: boolean
  onSave: () => void
  onCheckStatus: () => void
  onDelete: () => void
  /**
   * Whether to render Delete. Legacy put Delete on the BOTTOM bar only — the top bar carried Save and
   * Check Status alone (schedule1.xhtml:35-38 vs :796-803; schedule3.xhtml:37-38 vs :420-426). A page
   * rendering this bar twice therefore passes `false` for its top instance. Defaults to true so the
   * bottom instance needs no prop.
   */
  showDelete?: boolean
}

/**
 * The Save / Check Status / Delete action bar shared by the top-level schedule pages. The disabled
 * logic is identical across schedules (writes gated on `editable` and in-flight requests); each page
 * differs by its section modifier class and by whether this instance is the Delete-bearing one.
 */
const ScheduleActions: FC<ScheduleActionsProps> = ({
  className,
  editable,
  saving,
  onSave,
  onCheckStatus,
  onDelete,
  showDelete = true,
}) => (
  <Column sm={4} md={8} lg={16} className={className}>
    <Button kind="primary" size="md" disabled={!editable || saving} onClick={onSave}>
      Save
    </Button>
    <Button kind="tertiary" size="md" disabled={!editable || saving} onClick={onCheckStatus}>
      Check Status
    </Button>
    {showDelete && (
      <Button kind="danger--tertiary" size="md" disabled={!editable || saving} onClick={onDelete}>
        Delete
      </Button>
    )}
  </Column>
)

export default ScheduleActions
