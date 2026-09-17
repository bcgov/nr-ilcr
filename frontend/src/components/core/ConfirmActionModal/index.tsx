import type { FC } from 'react'
import { Modal } from '@carbon/react'

type ConfirmActionModalProps = {
  readonly open: boolean
  /** The dialog title — legacy's transition dialogs all read `Confirmation Required`. */
  readonly heading: string
  /** The question, a client-side literal taken verbatim from the legacy bundle by the caller. */
  readonly message: string
  readonly confirmLabel: string
  readonly cancelLabel: string
  readonly onConfirm: () => void
  /** Fires for the cancel button, the close control, Escape and a backdrop click alike. */
  readonly onCancel: () => void
}

/**
 * The "are you sure?" dialog for a status transition — legacy's `Confirmation Required` family
 * (checkStatus.xhtml:189-221), which is a different header and a different answer pair from the
 * delete confirms' `Confirmation` + Yes/No (`ConfirmDeleteModal`). Every literal is the caller's,
 * so one component carries all eight of legacy's transition dialogs, and it is never `danger`: a
 * submit or a verify is not a delete. Callers mount it only while a decision is pending.
 */
const ConfirmActionModal: FC<ConfirmActionModalProps> = ({
  open,
  heading,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
}) => (
  <Modal
    open={open}
    modalHeading={heading}
    primaryButtonText={confirmLabel}
    secondaryButtonText={cancelLabel}
    onRequestClose={onCancel}
    onSecondarySubmit={onCancel}
    onRequestSubmit={onConfirm}
  >
    <p>{message}</p>
  </Modal>
)

export default ConfirmActionModal
