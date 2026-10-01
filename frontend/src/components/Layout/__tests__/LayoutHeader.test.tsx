import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { render, screen } from '@/test-utils'
import LayoutProvider from '@/context/layout/LayoutProvider'

// LayoutHeader renders a TanStack Router `Link` and several dev-only chrome children that carry their
// own effects. This suite asserts ONLY the app-name copy (Story 30.1 / #312), so stub the router
// (Link passthrough) and the chrome children to keep the render hermetic — the same
// brittleness-avoidance idiom as Home.test.tsx / Schedule1.test.tsx.
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => children,
  useLocation: () => ({ pathname: '/' }),
  useNavigate: () => vi.fn(),
}))
vi.mock('@/components/MockUserSelector', () => ({ default: () => null }))
vi.mock('@/components/DevRoleSwitcher', () => ({ default: () => null }))
vi.mock('@/components/SignOutButton', () => ({ default: () => null }))
vi.mock('@/components/ThemeToggle', () => ({ default: () => null }))

// Import AFTER the mocks so vi.mock hoisting applies (same ordering as Home.test.tsx).
import LayoutHeader from '@/components/Layout/LayoutHeader'

describe('LayoutHeader — app name (Story 30.1 / #312)', () => {
  test('renders the SINGULAR app name "Interior Logging Cost Report (ILCR)"', () => {
    render(
      <LayoutProvider>
        <LayoutHeader />
      </LayoutProvider>,
    )

    // #312: "Interior Logging Cost Report (ILCR) -- no s at the end of report".
    expect(screen.getByText('Interior Logging Cost Report (ILCR)')).toBeInTheDocument()

    // The same APP_NAME constant drives the <Header aria-label>, so the banner landmark's accessible
    // name must be singular too.
    expect(
      screen.getByRole('banner', { name: 'Interior Logging Cost Report (ILCR)' }),
    ).toBeInTheDocument()

    // Regression guard: the plural must not come back.
    expect(screen.queryByText(/Interior Logging Cost Reports/)).not.toBeInTheDocument()
  })
})
