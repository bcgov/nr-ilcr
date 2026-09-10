import type { FC } from 'react'
import { Modal } from '@carbon/react'
import DirectoryPicker from '@/components/millAssociations/DirectoryPicker'
import type { DirectoryUser } from '@/interfaces/MillAssociation'

type AddUserModalProps = {
  readonly onAdd: (user: DirectoryUser) => void
  readonly onClose: () => void
  readonly busy: boolean
  /** The picker's own channel, so a lookup failure cannot erase a standing write message. */
  readonly onError: (message: string | null) => void
}

/**
 * The "Find and Add User" dialog (UC-MILL-001 S05, mills.xhtml:274).
 *
 * <p>Choosing a candidate IS the add — legacy's `rowSelect` called `addUserToMill` and hid the
 * dialog (mills.xhtml:298-299) — so there is no Add button inside the dialog and no confirmation
 * step, which legacy also had none of here.
 *
 * <p>The picker is the SHIPPED directory lookup, unmodified: its debounce, monotonic sequence
 * token, selection-echo guard and latching 404 all matter as much on this surface as on the users
 * screen, and re-implementing any of them would be re-earning four fixed bugs. `selected` is always
 * null because a candidate is spent the instant it is chosen — this dialog holds no selection of
 * its own to carry.
 *
 * <p>Legacy's dialog also searched IDIR/GOVERNMENT for the Auditors panel; that half retires with
 * the panel (DL-23, deviation (H)).
 */
const AddUserModal: FC<AddUserModalProps> = ({ onAdd, onClose, busy, onError }) => (
  <Modal
    open
    passiveModal
    size="lg"
    modalHeading="Find and Add User"
    aria-label="Find and Add User"
    onRequestClose={onClose}
  >
    <DirectoryPicker
      selected={null}
      disabled={busy}
      onSelect={(user) => {
        if (user) onAdd(user)
      }}
      onError={onError}
    />
  </Modal>
)

export default AddUserModal
