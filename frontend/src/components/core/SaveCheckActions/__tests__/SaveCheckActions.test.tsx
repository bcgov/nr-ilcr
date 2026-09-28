import { render, screen } from '@/test-utils'
import SaveCheckActions from '@/components/core/SaveCheckActions'

// The Save + Check Status pair the list-style schedules (7A, 7B, 11) render above and below their report
// list. It had no test of its own: the two disabled gates were exercised only through the two page
// suites, and Story 30.3 initially reached `ScheduleActions` but not this bar — so 7A/7B kept a
// text-only Save and Check Status while every other schedule had icons (PR #381 review, paulushcgcj).

const props = {
  className: 'schedule-7b__actions',
  saveDisabled: false,
  checkDisabled: false,
  onSave: () => undefined,
  onCheckStatus: () => undefined,
}

describe('SaveCheckActions', () => {
  test('both actions carry a decorative icon (Story 30.3 / #312 Overall 6)', () => {
    render(<SaveCheckActions {...props} />)

    // renderIcon adds an <svg> inside the button; the accessible name stays the label text (the icon
    // is decorative), so selecting by name still works AND the icon is present.
    for (const name of ['Save', 'Check Status']) {
      expect(screen.getByRole('button', { name }).querySelector('svg')).not.toBeNull()
    }
  })

  test('Save and Check Status disable independently', () => {
    // Save additionally has nothing to do with an empty list — the batch endpoint rejects an empty
    // body — so the two gates are separate props and must not be collapsed into one.
    const { unmount } = render(<SaveCheckActions {...props} saveDisabled />)
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Check Status' })).toBeEnabled()
    unmount()

    render(<SaveCheckActions {...props} checkDisabled />)
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Check Status' })).toBeDisabled()
  })

  test('a disabled Check Status is described by its reason, when the page gives one', () => {
    // A disabled Carbon button cannot take focus, so greying alone tells a screen-reader user
    // nothing about why (Schedule 11: unsaved changes must be saved first).
    render(<SaveCheckActions {...props} checkDisabled checkDisabledReason="Save first" />)

    expect(screen.getByRole('button', { name: 'Check Status' })).toHaveAccessibleDescription(
      'Save first',
    )
  })

  test('the reason is NOT attached while Check Status is enabled, nor when none is given', () => {
    // An enabled button described as "save first" would tell the user something untrue.
    const { unmount } = render(<SaveCheckActions {...props} checkDisabledReason="Save first" />)
    const enabled = screen.getByRole('button', { name: 'Check Status' })
    expect(enabled).not.toHaveAttribute('aria-describedby')
    expect(screen.queryByText('Save first')).not.toBeInTheDocument()
    unmount()

    // 7A and 7B pass no reason: their greyed button stays exactly as it was.
    render(<SaveCheckActions {...props} checkDisabled />)
    expect(screen.getByRole('button', { name: 'Check Status' })).not.toHaveAttribute(
      'aria-describedby',
    )
  })

  test('two bars on one page describe their own buttons (ids do not collide)', () => {
    // Schedule 11 renders the bar above AND below its table; a fixed id would point both buttons at
    // the first bar's hint.
    render(
      <>
        <SaveCheckActions {...props} checkDisabled checkDisabledReason="Save first" />
        <SaveCheckActions {...props} checkDisabled checkDisabledReason="Save first" />
      </>,
    )

    const [top, bottom] = screen.getAllByRole('button', { name: 'Check Status' })
    expect(top.getAttribute('aria-describedby')).not.toBe(bottom.getAttribute('aria-describedby'))
    expect(top).toHaveAccessibleDescription('Save first')
    expect(bottom).toHaveAccessibleDescription('Save first')
  })
})
