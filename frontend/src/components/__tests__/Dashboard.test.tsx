import { vi } from 'vitest'
import { render, screen, userEvent } from '@/test-utils'

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}))

import Dashboard from '@/components/Dashboard'

describe('Dashboard', () => {
  test('renders the ILCR Workspace heading (no users API call)', () => {
    render(<Dashboard />)
    expect(screen.getByText(/ILCR Workspace/i)).toBeInTheDocument()
  })

  test('Open Schedule 1 navigates to the schedule page', async () => {
    mockNavigate.mockClear()
    const user = userEvent.setup()
    render(<Dashboard />)
    await user.click(screen.getByRole('button', { name: /open schedule 1/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-1' })
  })
})
