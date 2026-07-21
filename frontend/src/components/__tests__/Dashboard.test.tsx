import { vi } from 'vitest'
import { render, screen } from '@/test-utils'
import Dashboard from '@/components/Dashboard'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: vi.fn(),
}))

describe('Dashboard', () => {
  test('renders a heading with the correct text', () => {
    render(<Dashboard />)
    expect(screen.getByText(/ILCR Workspace/i)).toBeInTheDocument()
  })

  test('renders the current local principal and pending identity endpoint state', () => {
    render(<Dashboard />)

    expect(screen.getByText(/Alex Admin/i)).toBeInTheDocument()
    expect(screen.getByText(/ILCR_ADMIN/i)).toBeInTheDocument()
    expect(screen.getByText(/Identity endpoint pending/i)).toBeInTheDocument()
  })
})
