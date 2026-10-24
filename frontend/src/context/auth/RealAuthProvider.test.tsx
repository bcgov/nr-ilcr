import type { ReactNode } from 'react'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { ErrorResponse } from 'oidc-client-ts'
import { server } from '@/test-setup'
import RouteGuard from '@/components/RouteGuard'
import RealAuthProvider from './RealAuthProvider'
import useAuth from './useAuth'
import { emitSessionExpired } from './sessionExpiredSignal'
import type * as OidcService from '@/service/oidc-service'

type UserLoaded = (user: unknown) => void | Promise<void>

const mocks = vi.hoisted(() => ({
  oidcUser: vi.fn(),
  completeOidcLogin: vi.fn(),
  startOidcLogin: vi.fn(),
  endOidcSession: vi.fn(),
  removeUser: vi.fn(),
  userLoaded: new Set<UserLoaded>(),
}))

vi.mock('@/service/oidc-service', async (importOriginal) => ({
  AUTH_CALLBACK_PATH: '/authCallback',
  isOidcConfigured: true,
  getOidcUser: mocks.oidcUser,
  restoreOidcUser: mocks.oidcUser,
  completeOidcLogin: mocks.completeOidcLogin,
  startOidcLogin: mocks.startOidcLogin,
  endOidcSession: mocks.endOidcSession,
  getOidcReturnPath: () => '/schedule-9',
  isSessionEnded: (await importOriginal<typeof OidcService>()).isSessionEnded,
  // Records userLoaded subscribers, so a test can raise the event as oidc-client-ts does.
  getUserManager: () => ({
    removeUser: mocks.removeUser,
    events: {
      addUserLoaded: (callback: UserLoaded) => {
        mocks.userLoaded.add(callback)
        return () => mocks.userLoaded.delete(callback)
      },
      removeUserLoaded: (callback: UserLoaded) => {
        mocks.userLoaded.delete(callback)
      },
    },
  }),
}))

// RouteGuard reads the current route.
vi.mock('@tanstack/react-router', () => ({
  useLocation: () => ({ pathname: '/' }),
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => <a href="/">{children}</a>,
}))

// oidc-client-ts raises userLoaded after storing a user: on every refresh-token renewal, and while
// completing the callback.
async function raiseUserLoaded(user: unknown) {
  for (const listener of mocks.userLoaded) {
    await listener(user)
  }
}

const ME = 'http://localhost:3000/api/v1/me'

function Probe() {
  const { user, isAuthenticated, hasRole, signOut } = useAuth()
  return (
    <div>
      <span data-testid="authed">{String(isAuthenticated)}</span>
      <span data-testid="name">{user?.displayName ?? ''}</span>
      <span data-testid="admin">{String(hasRole('ILCR_ADMIN'))}</span>
      <button type="button" onClick={() => void signOut()}>
        sign out
      </button>
    </div>
  )
}

function OverrideProbe() {
  const { hasRole, devRoleSwitch } = useAuth()
  return (
    <div>
      <span data-testid="admin">{String(hasRole('ILCR_ADMIN'))}</span>
      <span data-testid="real">{(devRoleSwitch?.realRoles ?? []).join(',')}</span>
      <button type="button" onClick={() => devRoleSwitch?.setOverride('ILCR_ADMIN')}>
        view as admin
      </button>
    </div>
  )
}

const pageMounted = vi.fn()
const pageUnmounted = vi.fn()
const loadingChanged = vi.fn()

/** Stands in for a routed page under the root RouteGuard. */
function RoutedPage() {
  useEffect(() => {
    pageMounted()
    return () => pageUnmounted()
  }, [])
  return <span data-testid="page">Schedule 9</span>
}

function LoadingProbe() {
  const { isLoading } = useAuth()
  useEffect(() => {
    loadingChanged(isLoading)
  }, [isLoading])
  return null
}

const ADMIN = {
  userGuid: 'ABC123',
  displayName: 'Alex Admin',
  email: null,
  identityProvider: 'idir',
  roles: ['ILCR_ADMIN'],
}

function withSession(accessToken: string | undefined) {
  mocks.oidcUser.mockResolvedValue(accessToken ? { access_token: accessToken } : null)
}

beforeEach(() => {
  mocks.oidcUser.mockReset()
  mocks.completeOidcLogin.mockReset()
  mocks.startOidcLogin.mockReset().mockResolvedValue(undefined)
  mocks.endOidcSession.mockReset().mockResolvedValue(undefined)
  mocks.removeUser.mockReset().mockResolvedValue(undefined)
  mocks.userLoaded.clear()
  window.history.replaceState(null, '', '/')
  window.localStorage.clear()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('RealAuthProvider', () => {
  it('loads the current user from /api/v1/me once a session exists and renders children', async () => {
    withSession('access-token')
    server.use(
      http.get(ME, () =>
        HttpResponse.json({
          userGuid: 'ABC123',
          displayName: 'Alex Admin',
          email: 'alex.admin@gov.bc.ca',
          identityProvider: 'idir',
          roles: ['ILCR_ADMIN'],
        }),
      ),
    )

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('authed')).toHaveTextContent('true'))
    expect(screen.getByTestId('name')).toHaveTextContent('Alex Admin')
    expect(screen.getByTestId('admin')).toHaveTextContent('true')
    expect(mocks.startOidcLogin).not.toHaveBeenCalled()
  })

  it('shows an unauthenticated visitor the landing page and signs in only from it', async () => {
    withSession(undefined)
    window.history.replaceState(null, '', '/schedule-1?millId=514')

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    // The app is gated behind auth — children are not shown, only the landing page.
    expect(await screen.findByRole('heading', { name: 'Welcome to ILCR' })).toBeInTheDocument()
    expect(screen.queryByTestId('authed')).toBeNull()
    expect(mocks.startOidcLogin).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: /log in with idir/i }))
    expect(mocks.startOidcLogin).toHaveBeenCalledExactlyOnceWith('idir')
    // startOidcLogin saves this deep link as the route to restore after the callback.
    expect(window.location.pathname + window.location.search).toBe('/schedule-1?millId=514')
  })

  it('does not remove stored credentials after a rejected callback exchange', async () => {
    window.history.replaceState(null, '', '/authCallback?code=spent&state=test')
    mocks.completeOidcLogin.mockRejectedValue(new ErrorResponse({ error: 'invalid_grant' }))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Connection error'))
    expect(mocks.removeUser).not.toHaveBeenCalled()
    expect(mocks.startOidcLogin).not.toHaveBeenCalled()
    expect(window.location.pathname).toBe('/')
  })

  it('signs out through OIDC (which ends the upstream session via the logout chain)', async () => {
    withSession('access-token')
    server.use(
      http.get(ME, () =>
        HttpResponse.json({
          userGuid: 'ABC123',
          displayName: 'Sam Submitter',
          email: 's@x.ca',
          identityProvider: 'idir',
          roles: ['ILCR_SUBMITTER'],
        }),
      ),
    )
    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('authed')).toHaveTextContent('true'))

    await userEvent.click(screen.getByRole('button', { name: 'sign out' }))
    expect(mocks.endOidcSession).toHaveBeenCalledTimes(1)
  })

  it('completes the callback once and restores the requested route', async () => {
    window.history.replaceState(null, '', '/authCallback?code=test&state=test')
    withSession('access-token')
    let meRequests = 0
    server.use(
      http.get(ME, () => {
        meRequests += 1
        return HttpResponse.json(ADMIN)
      }),
    )
    mocks.completeOidcLogin.mockImplementation(async () => {
      const session = { access_token: 'access-token' }
      await raiseUserLoaded(session)
      return session
    })

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('authed')).toHaveTextContent('true'))
    expect(window.location.pathname).toBe('/schedule-9')
    expect(mocks.completeOidcLogin).toHaveBeenCalledTimes(1)
    expect(meRequests).toBe(1)
  })

  it('keeps the routed page mounted without reloading /me when the token is renewed', async () => {
    withSession('access-token')
    let meRequests = 0
    server.use(
      http.get(ME, () => {
        meRequests += 1
        return HttpResponse.json(ADMIN)
      }),
    )
    render(
      <RealAuthProvider>
        <LoadingProbe />
        <RouteGuard>
          <RoutedPage />
        </RouteGuard>
      </RealAuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('page')).toBeInTheDocument())
    loadingChanged.mockClear()

    withSession('renewed-access-token')
    await act(async () => {
      await raiseUserLoaded({ access_token: 'renewed-access-token' })
    })
    // Give any reload the renewal might have started time to reach /me.
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(loadingChanged).not.toHaveBeenCalledWith(true)
    expect(meRequests).toBe(1)
    expect(pageMounted).toHaveBeenCalledTimes(1)
    expect(pageUnmounted).not.toHaveBeenCalled()
    expect(screen.getByTestId('page')).toBeInTheDocument()
  })

  it('keeps the landing page, with a retry, when the sign-in redirect cannot start', async () => {
    withSession(undefined)
    mocks.startOidcLogin.mockRejectedValueOnce(new Error('discovery unavailable'))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await userEvent.click(await screen.findByRole('button', { name: /log in with idir/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not start sign-in')

    await userEvent.click(screen.getByRole('button', { name: /log in with idir/i }))
    expect(mocks.startOidcLogin).toHaveBeenCalledTimes(2)
  })

  it('shows a connection error (not a redirect loop) when the session lookup fails', async () => {
    // A rejected session lookup is a transient/OIDC failure — redirecting to sign-in on a valid
    // SSO session would return straight here and loop. Render an error with Retry instead.
    mocks.oidcUser.mockRejectedValue(new Error('no session'))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Connection error'))
    expect(mocks.startOidcLogin).not.toHaveBeenCalled()
    expect(mocks.removeUser).not.toHaveBeenCalled()
  })

  it('shows a connection error and retries (no loop) when /me fails with 5xx', async () => {
    withSession('access-token')
    server.use(http.get(ME, () => new HttpResponse(null, { status: 500 })))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('temporarily unavailable'),
    )
    expect(mocks.startOidcLogin).not.toHaveBeenCalled()

    // Retry re-attempts /me; a now-healthy backend authenticates the user.
    server.use(
      http.get(ME, () =>
        HttpResponse.json({
          userGuid: 'ABC123',
          displayName: 'Alex Admin',
          email: null,
          identityProvider: 'idir',
          roles: ['ILCR_ADMIN'],
        }),
      ),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(screen.getByTestId('authed')).toHaveTextContent('true'))
  })

  it('shows access-denied with sign-out (not a loop) when /me forbids the user (403)', async () => {
    withSession('access-token')
    server.use(http.get(ME, () => new HttpResponse(null, { status: 403 })))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Access denied'))
    expect(mocks.startOidcLogin).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(mocks.endOidcSession).toHaveBeenCalledTimes(1)
  })

  it('ends the session when SSO rejects the refresh token', async () => {
    withSession('access-token')
    server.use(
      http.get(ME, () =>
        HttpResponse.json({
          userGuid: 'ABC123',
          displayName: 'Alex Admin',
          email: null,
          identityProvider: 'idir',
          roles: ['ILCR_ADMIN'],
        }),
      ),
    )
    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('authed')).toHaveTextContent('true'))

    await act(async () => emitSessionExpired())
    expect(mocks.endOidcSession).toHaveBeenCalledOnce()
    expect(screen.getByRole('status')).toHaveTextContent('Signing out')
    expect(mocks.startOidcLogin).not.toHaveBeenCalled()
  })

  it('lets local dev override the viewed role (frontend only — real roles preserved)', async () => {
    withSession('access-token')
    server.use(
      http.get(ME, () =>
        HttpResponse.json({
          userGuid: 'ABC123',
          displayName: 'Sam Submitter',
          email: null,
          identityProvider: 'idir',
          roles: ['ILCR_SUBMITTER'],
        }),
      ),
    )
    render(
      <RealAuthProvider>
        <OverrideProbe />
      </RealAuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('admin')).toHaveTextContent('false'))

    await userEvent.click(screen.getByRole('button', { name: 'view as admin' }))

    // The SPA now treats the user as admin, but the real token roles are unchanged (backend still 403s).
    await waitFor(() => expect(screen.getByTestId('admin')).toHaveTextContent('true'))
    expect(screen.getByTestId('real')).toHaveTextContent('ILCR_SUBMITTER')
  })
})
