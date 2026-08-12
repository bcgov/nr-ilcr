import type { FC } from 'react'
import { Modal } from '@carbon/react'

// Legacy's confirm dialog wording and its Yes/No answers, shared by the schedule pages that delete a
// row (schedule7A.xhtml:1250-1255, schedule7B.xhtml:534-539).
export const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'

type ConfirmDeleteModalProps = {
  readonly onCancel: () => void
  readonly onConfirm: () => void
}

/**
 * The delete confirmation. Callers mount it only while a delete is pending, so its Yes/No do not sit
 * in the accessibility tree competing with the row actions when no dialog is open. Cancelling sends
 * no request and leaves the record unchanged with no message.
 */
const ConfirmDeleteModal: FC<ConfirmDeleteModalProps> = ({ onCancel, onConfirm }) => (
  <Modal
    open
    danger
    modalHeading="Confirmation"
    primaryButtonText="Yes"
    secondaryButtonText="No"
    onRequestClose={onCancel}
    onRequestSubmit={onConfirm}
  >
    <p>{CONFIRM_DELETE}</p>
  </Modal>
)

export default ConfirmDeleteModal
