import { render, screen } from '@/test-utils'
import SaveCheckActions from '@/components/core/SaveCheckActions'

// The Save + Check Status pair the list-style schedules (7A, 7B) render above and below their report
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
})
