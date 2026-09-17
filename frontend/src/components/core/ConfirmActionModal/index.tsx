import type { FC } from 'react'
import { Button, ComposedModal, ModalBody, ModalFooter, ModalHeader } from '@carbon/react'
import { Checkmark, Close } from '@carbon/icons-react'
import './index.scss'

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
 *
 * Same chrome as `ConfirmNavigationModal` (#312 Overall 11): `ComposedModal` with real, regular-sized
 * `<Button>`s carrying a Carbon icon — cancel = Close, confirm = Checkmark — because the base `Modal`
 * takes only string labels and cannot draw an icon. The icons are decorative; the accessible names
 * stay the labels. `aria-label={heading}` names the dialog, which `ComposedModal` does not derive
 * from its header on its own.
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
  <ComposedModal
    open={open}
    onClose={onCancel}
    size="sm"
    aria-label={heading}
    className="confirm-action-modal"
  >
    <ModalHeader title={heading} />
    <ModalBody>
      <p>{message}</p>
    </ModalBody>
    <ModalFooter>
      <Button kind="secondary" renderIcon={Close} onClick={onCancel}>
        {cancelLabel}
      </Button>
      <Button kind="primary" renderIcon={Checkmark} onClick={onConfirm}>
        {confirmLabel}
      </Button>
    </ModalFooter>
  </ComposedModal>
)

export default ConfirmActionModal
