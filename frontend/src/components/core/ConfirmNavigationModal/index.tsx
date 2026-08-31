import type { FC, ReactNode } from 'react'
import { Button, ComposedModal, ModalBody, ModalFooter, ModalHeader } from '@carbon/react'
import { ArrowRight, Close } from '@carbon/icons-react'
import './index.scss'

// The schedule -> supplementary-screen navigation confirm (#312 Overall 11): "make the cancel and
// continue buttons into regular sized buttons (with icons)". Carbon's base `<Modal>` only takes
// STRING button labels (primaryButtonText / secondaryButtonText) and cannot render an icon on them,
// so this uses `<ComposedModal>` with real <Button> children in the footer — regular size (Carbon
// default), each with a Carbon icon: Cancel = Close, Continue = ArrowRight. Accessible names stay
// "Cancel" / "Continue" (the icons are decorative), so callers and tests select by name unchanged.

type ConfirmNavigationModalProps = {
  readonly open: boolean
  /** Dialog title (e.g. "Leave Schedule 1"). */
  readonly heading: string
  /** Body copy — the confirm message. */
  readonly children: ReactNode
  readonly onCancel: () => void
  readonly onContinue: () => void
  /** Continue button label; defaults to "Continue" (some flows say "Save"). */
  readonly continueLabel?: string
}

const ConfirmNavigationModal: FC<ConfirmNavigationModalProps> = ({
  open,
  heading,
  children,
  onCancel,
  onContinue,
  continueLabel = 'Continue',
}) => (
  // onClose fires for the X / Escape / backdrop — route it to the same Cancel handler so every
  // dismissal path behaves identically to the old Modal's onRequestClose. `aria-label={heading}`
  // gives the dialog its accessible name: base <Modal> derived it from `modalHeading`, but
  // <ComposedModal> does NOT auto-label from the <ModalHeader> title, so screen readers (and
  // getByRole('dialog', { name })) would otherwise find an unnamed dialog.
  <ComposedModal
    open={open}
    onClose={onCancel}
    size="sm"
    aria-label={heading}
    className="confirm-nav-modal"
  >
    <ModalHeader title={heading} />
    <ModalBody>
      <p>{children}</p>
    </ModalBody>
    <ModalFooter>
      <Button kind="secondary" renderIcon={Close} onClick={onCancel}>
        Cancel
      </Button>
      <Button kind="primary" renderIcon={ArrowRight} onClick={onContinue}>
        {continueLabel}
      </Button>
    </ModalFooter>
  </ComposedModal>
)

export default ConfirmNavigationModal
