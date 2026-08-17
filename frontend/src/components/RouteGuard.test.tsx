import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthContext } from '@/context/auth/AuthContext'
import type { AuthContextValue } from '@/context/auth/types'
import RouteGuard from './RouteGuard'

const location = vi.hoisted(() => ({ pathname: '/' }))
vi.mock('@tanstack/react-router', () => ({
  useLocation: () => location,
  useNavigate: () => vi.fn(),
  // NotAuthorized renders <Button as={Link} to="/">; stand in with an anchor so the destination is
  // assertable declaratively. (Don't spread Carbon's button props — it passes href={undefined},
  // which would clobber the href we set from `to`.)
  Link: ({ to, children }: { to: string; children: ReactNode }) => (
    <a href={typeof to === 'string' ? to : '#'}>{children}</a>
  ),
}))

type AuthOverrides = { isLoading?: boolean; signOut?: () => void }

function authValue(roles: string[], overrides: AuthOverrides = {}): AuthContextValue {
  return {
    user: { userGuid: 'g', displayName: 'U', email: null, identityProvider: 'idir', roles },
    isAuthenticated: true,
    isLoading: overrides.isLoading ?? false,
    hasRole: (role: string) => roles.includes(role),
    signIn: () => undefined,
    signOut: overrides.signOut ?? (() => undefined),
  }
}

function renderGuard(roles: string[], pathname: string, overrides: AuthOverrides = {}) {
  location.pathname = pathname
  return render(
    <AuthContext value={authValue(roles, overrides)}>
      <RouteGuard>
        <div>APP CONTENT</div>
      </RouteGuard>
    </AuthContext>,
  )
}

describe('RouteGuard', () => {
  it('renders the app for a submitter on a non-admin route', () => {
    renderGuard(['ILCR_SUBMITTER'], '/schedule-1')
    expect(screen.getByText('APP CONTENT')).toBeInTheDocument()
  })

  it('blocks a submitter from an admin-only route by direct URL (not authorized)', () => {
    renderGuard(['ILCR_SUBMITTER'], '/code-tables')
    expect(screen.getByText('Not authorized')).toBeInTheDocument()
    expect(screen.queryByText('APP CONTENT')).toBeNull()
  })

  it('renders the app for an admin on an admin-only route', () => {
    renderGuard(['ILCR_ADMIN'], '/code-tables')
    expect(screen.getByText('APP CONTENT')).toBeInTheDocument()
  })

  it('shows the no-access screen for a signed-in user with no ILCR role', () => {
    renderGuard([], '/schedule-1')
    expect(screen.getByText('No ILCR access')).toBeInTheDocument()
    expect(screen.queryByText('APP CONTENT')).toBeNull()
  })

  it('renders nothing while auth is still loading (no flash of no-access/not-authorized)', () => {
    // roles:[] on an admin path would normally show a gate; while loading, neither should appear.
    const { container } = renderGuard([], '/code-tables', { isLoading: true })
    expect(screen.queryByText('No ILCR access')).toBeNull()
    expect(screen.queryByText('Not authorized')).toBeNull()
    expect(screen.queryByText('APP CONTENT')).toBeNull()
    expect(container).toBeEmptyDOMElement()
  })

  it('blocks a submitter on an admin sub-path (sub-route / trailing slash)', () => {
    renderGuard(['ILCR_SUBMITTER'], '/code-tables/edit')
    expect(screen.getByText('Not authorized')).toBeInTheDocument()
    expect(screen.queryByText('APP CONTENT')).toBeNull()
  })

  it('lets a user with no role sign out from the no-access screen', async () => {
    const signOut = vi.fn()
    renderGuard([], '/schedule-1', { signOut })

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(signOut).toHaveBeenCalledTimes(1)
  })

  it('offers a Back Home link to "/" from the not-authorized screen', () => {
    renderGuard(['ILCR_SUBMITTER'], '/code-tables')
    const backHome = screen.getByRole('link', { name: 'Back Home' })
    expect(backHome).toHaveAttribute('href', '/')
  })
})
