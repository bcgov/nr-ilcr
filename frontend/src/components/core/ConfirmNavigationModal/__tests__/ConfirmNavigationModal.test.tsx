import { describe, expect, test, vi } from 'vitest'
import { render, screen, userEvent } from '@/test-utils'
import ConfirmNavigationModal from '@/components/core/ConfirmNavigationModal'

// Story 30.7 / #312 Overall 11: the schedule -> supplementary-screen confirm must use REGULAR-sized
// buttons WITH icons. Carbon's base <Modal> can't render icons on its text buttons, so this uses
// ComposedModal with real <Button> children. These tests pin: the dialog is accessibly named, both
// buttons carry an icon and are NOT the small size, and Cancel/Continue fire the right handlers.

const setup = (over: Partial<Parameters<typeof ConfirmNavigationModal>[0]> = {}) => {
  const onCancel = vi.fn()
  const onContinue = vi.fn()
  render(
    <ConfirmNavigationModal
      open
      heading="Leave Schedule 1"
      onCancel={onCancel}
      onContinue={onContinue}
      {...over}
    >
      Any unsaved data will be lost.
    </ConfirmNavigationModal>,
  )
  return { onCancel, onContinue }
}

describe('ConfirmNavigationModal', () => {
  test('renders an accessibly-named dialog with the message', () => {
    setup()
    expect(screen.getByRole('dialog', { name: 'Leave Schedule 1' })).toBeInTheDocument()
    expect(screen.getByText('Any unsaved data will be lost.')).toBeInTheDocument()
  })

  test('Cancel and Continue are regular-sized buttons WITH icons (#312 Overall 11)', () => {
    setup()
    for (const name of ['Cancel', 'Continue']) {
      const button = screen.getByRole('button', { name })
      // renderIcon adds an <svg>; the accessible name stays the label (icon decorative).
      expect(button.querySelector('svg')).not.toBeNull()
      // Regular size, not Carbon's small button.
      expect(button.className).not.toMatch(/--btn--sm\b/)
    }
  })

  test('Continue fires onContinue; Cancel fires onCancel', async () => {
    const { onCancel, onContinue } = setup()
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }))
    expect(onContinue).toHaveBeenCalledOnce()
    expect(onCancel).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  test('supports a custom continue label', () => {
    setup({ continueLabel: 'Save' })
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Continue' })).not.toBeInTheDocument()
  })
})
