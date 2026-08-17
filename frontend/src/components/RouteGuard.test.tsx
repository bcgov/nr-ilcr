import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AuthContext } from '@/context/auth/AuthContext'
import type { AuthContextValue } from '@/context/auth/types'
import RouteGuard from './RouteGuard'

const location = vi.hoisted(() => ({ pathname: '/' }))
vi.mock('@tanstack/react-router', () => ({
  useLocation: () => location,
  useNavigate: () => vi.fn(),
}))

function authValue(roles: string[]): AuthContextValue {
  return {
    user: { userGuid: 'g', displayName: 'U', email: null, identityProvider: 'idir', roles },
    isAuthenticated: true,
    isLoading: false,
    hasRole: (role: string) => roles.includes(role),
    signIn: () => undefined,
    signOut: () => undefined,
  }
}

function renderGuard(roles: string[], pathname: string) {
  location.pathname = pathname
  return render(
    <AuthContext value={authValue(roles)}>
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
})
