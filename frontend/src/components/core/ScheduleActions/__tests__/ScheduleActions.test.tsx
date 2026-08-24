import { render, screen } from '@/test-utils'
import ScheduleActions from '@/components/core/ScheduleActions'

// The shared Save / Check Status / Delete bar had no test of its own until defect #292's code review:
// its Delete gate was exercised only through the three schedule pages, so the composition of
// `editable` / `scheduleSaved` / `saving` — and the a11y hint that pays for greying the button rather
// than hiding it — could regress with every page suite green.

const props = {
  className: 'schedule-2__actions',
  editable: true,
  saving: false,
  scheduleSaved: true,
  onSave: () => undefined,
  onCheckStatus: () => undefined,
  onDelete: () => undefined,
}

const deleteButton = () => screen.getByRole('button', { name: 'Delete' })
const HINT = 'Available once the schedule is saved'

describe('ScheduleActions', () => {
  test('a saved, editable, idle schedule enables all three actions', () => {
    render(<ScheduleActions {...props} />)

    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Check Status' })).toBeEnabled()
    expect(deleteButton()).toBeEnabled()
  })

  test('Delete is disabled when nothing is saved, while entry stays possible (defect #292)', () => {
    render(<ScheduleActions {...props} scheduleSaved={false} />)

    expect(deleteButton()).toBeDisabled()
    // The whole point of the gate: a Licensee must still be able to enter and save data.
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Check Status' })).toBeEnabled()
  })

  test('an unsaved schedule explains why Delete is unavailable — to assistive tech only', () => {
    render(<ScheduleActions {...props} scheduleSaved={false} />)

    const hint = screen.getByText(HINT)
    // Greying rather than hiding (decision 1) costs a screen-reader user the button entirely — a
    // disabled Carbon button is not focusable — so the reason must be announced.
    expect(deleteButton()).toHaveAttribute('aria-describedby', hint.id)
    // …but NOT drawn: it read as clutter beside an already-greyed control (product call 2026-08-24).
    // Both halves are pinned, so neither can be lost — deleting the span would strip the reason from
    // the accessibility tree, and styling it back in would put the text back on the page.
    expect(hint).toHaveClass('cds--visually-hidden')
  })

  test('no hint on a saved schedule, and none on a read-only one', () => {
    const { unmount } = render(<ScheduleActions {...props} />)
    expect(screen.queryByText(HINT)).not.toBeInTheDocument()
    expect(deleteButton()).not.toHaveAttribute('aria-describedby')
    unmount()

    // Read-only: the bar is wholly disabled and the tombstone already states the non-Draft status,
    // so "save it first" would be misleading advice.
    render(<ScheduleActions {...props} editable={false} scheduleSaved={false} />)
    expect(screen.queryByText(HINT)).not.toBeInTheDocument()
    expect(deleteButton()).not.toHaveAttribute('aria-describedby')
  })

  test('read-only and in-flight states disable every action', () => {
    const { unmount } = render(<ScheduleActions {...props} editable={false} />)
    screen.getAllByRole('button').forEach((b) => expect(b).toBeDisabled())
    unmount()

    render(<ScheduleActions {...props} saving />)
    screen.getAllByRole('button').forEach((b) => expect(b).toBeDisabled())
  })

  test('showDelete=false renders the top bar without Delete or its hint', () => {
    render(<ScheduleActions {...props} showDelete={false} scheduleSaved={false} />)

    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(screen.queryByText(HINT)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })
})
