import type { FC } from 'react'
import { Button, TableCell } from '@carbon/react'
import { Edit, TrashCan } from '@carbon/icons-react'

type RowActionButtonsProps = {
  /** Disables both buttons (e.g. while saving or another row is being edited). */
  disabled: boolean
  onEdit: () => void
  onDelete: () => void
}

/**
 * The trailing Edit / Delete cell shared by the schedule list sub-pages (Other Costs, Other
 * Acceptable Costs, Included Unacceptable Costs). Callers still gate the whole cell on `editable`;
 * this owns only the button pair, which was byte-for-byte identical across those pages.
 */
const RowActionButtons: FC<RowActionButtonsProps> = ({ disabled, onEdit, onDelete }) => (
  <TableCell>
    <Button kind="ghost" size="sm" disabled={disabled} renderIcon={Edit} onClick={onEdit}>
      Edit
    </Button>
    <Button
      kind="danger--tertiary"
      size="sm"
      disabled={disabled}
      renderIcon={TrashCan}
      onClick={onDelete}
    >
      Delete
    </Button>
  </TableCell>
)

export default RowActionButtons
