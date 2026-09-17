import { describe, expect, test, vi } from 'vitest'
import { render, screen, userEvent } from '@/test-utils'
import ConfirmActionModal from '@/components/core/ConfirmActionModal'

// The generic "are you sure?" dialog for a status transition — legacy's `Confirmation Required`
// family (checkStatus.xhtml:189-221), as distinct from the delete confirms' `Confirmation`. Every
// literal is the caller's, so the same component carries all eight of legacy's transition dialogs.

const HEADING = 'Confirmation Required'
const MESSAGE = "Please confirm you'd like to SUBMIT Schedules 1-10?"

const setup = (over: Partial<Parameters<typeof ConfirmActionModal>[0]> = {}) => {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  render(
    <ConfirmActionModal
      open
      heading={HEADING}
      message={MESSAGE}
      confirmLabel="Yes"
      cancelLabel="Cancel"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...over}
    />,
  )
  return { onConfirm, onCancel }
}

describe('ConfirmActionModal', () => {
  test('renders a dialog named by its heading, carrying the message and the caller’s two labels; not the danger variant', () => {
    setup()
    expect(screen.getByRole('dialog', { name: HEADING })).toBeInTheDocument()
    expect(screen.getByText(MESSAGE)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Yes' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'No' })).not.toBeInTheDocument()
    // Not a delete: no danger styling. The `.cds--modal` check is the positive control for the selector.
    expect(document.querySelector('.cds--modal')).not.toBeNull()
    expect(document.querySelector('.cds--modal--danger')).toBeNull()
  })

  test('the confirm label fires onConfirm once and never onCancel', async () => {
    const { onConfirm, onCancel } = setup()
    await userEvent.click(screen.getByRole('button', { name: 'Yes' }))
    expect(onConfirm).toHaveBeenCalledOnce()
    expect(onCancel).not.toHaveBeenCalled()
  })

  test('the cancel label, the close control and Escape each fire onCancel and never onConfirm', async () => {
    const { onConfirm, onCancel } = setup()
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(onCancel).toHaveBeenCalledTimes(2)
    await userEvent.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalledTimes(3)
    expect(onConfirm).not.toHaveBeenCalled()
  })

  test('the labels are the caller’s — a different pair renders as given', () => {
    setup({ confirmLabel: 'Proceed', cancelLabel: 'Go back' })
    expect(screen.getByRole('button', { name: 'Proceed' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Go back' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Yes' })).not.toBeInTheDocument()
  })

  test('closed: the dialog is not shown', () => {
    setup({ open: false })
    expect(document.querySelector('.cds--modal')).not.toHaveClass('is-visible')
  })
})
