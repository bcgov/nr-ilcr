import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/test-setup'
import RealAuthProvider from './RealAuthProvider'
import useAuth from './useAuth'

const mocks = vi.hoisted(() => ({
  fetchAuthSession: vi.fn(),
  signInWithRedirect: vi.fn(),
  signOut: vi.fn(),
  hubHandlerRef: { current: undefined as undefined | ((e: { payload: unknown }) => void) },
}))

vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: mocks.fetchAuthSession,
  signInWithRedirect: mocks.signInWithRedirect,
  signOut: mocks.signOut,
}))

vi.mock('aws-amplify/utils', () => ({
  Hub: {
    listen: (_channel: string, cb: (e: { payload: unknown }) => void) => {
      mocks.hubHandlerRef.current = cb
      return () => undefined
    },
  },
}))

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

function withSession(idToken: string | undefined) {
  mocks.fetchAuthSession.mockResolvedValue(
    idToken ? { tokens: { idToken: { toString: () => idToken } } } : { tokens: undefined },
  )
}

beforeEach(() => {
  mocks.fetchAuthSession.mockReset()
  mocks.signInWithRedirect.mockReset().mockResolvedValue(undefined)
  mocks.signOut.mockReset().mockResolvedValue(undefined)
  mocks.hubHandlerRef.current = undefined
  window.history.replaceState(null, '', '/')
  window.localStorage.clear()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('RealAuthProvider', () => {
  it('loads the current user from /api/v1/me once a session exists and renders children', async () => {
    withSession('id-token')
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
    expect(mocks.signInWithRedirect).not.toHaveBeenCalled()
  })

  it('redirects an unauthenticated visitor to the Hosted UI, preserving the deep link', async () => {
    withSession(undefined)
    window.history.replaceState(null, '', '/schedule-1?millId=514')

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(mocks.signInWithRedirect).toHaveBeenCalledTimes(1))
    expect(mocks.signInWithRedirect).toHaveBeenCalledWith({
      customState: '/schedule-1?millId=514',
    })
    // The app is gated behind auth — children are not shown, only the sign-in chrome.
    expect(screen.getByRole('status')).toHaveTextContent('Signing in')
    expect(screen.queryByTestId('authed')).toBeNull()
  })

  it('signs out through Amplify (which ends the upstream session via the logout chain)', async () => {
    withSession('id-token')
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
    expect(mocks.signOut).toHaveBeenCalledTimes(1)
  })

  it('restores the requested route on the customOAuthState Hub event', async () => {
    withSession('id-token')
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
    await waitFor(() => expect(mocks.hubHandlerRef.current).toBeDefined())

    mocks.hubHandlerRef.current?.({ payload: { event: 'customOAuthState', data: '/schedule-9' } })

    expect(window.location.pathname).toBe('/schedule-9')
  })

  it('shows a connection error (not a redirect loop) when the session lookup fails', async () => {
    // A rejected fetchAuthSession is a transient/Amplify failure — bouncing to the Hosted UI on a
    // valid Cognito session would return straight here and loop. Render an error with Retry instead.
    mocks.fetchAuthSession.mockRejectedValue(new Error('no session'))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Connection error'))
    expect(mocks.signInWithRedirect).not.toHaveBeenCalled()
  })

  it('shows a connection error and retries (no loop) when /me fails with 5xx', async () => {
    withSession('id-token')
    server.use(http.get(ME, () => new HttpResponse(null, { status: 500 })))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('temporarily unavailable'),
    )
    expect(mocks.signInWithRedirect).not.toHaveBeenCalled()

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
    withSession('id-token')
    server.use(http.get(ME, () => new HttpResponse(null, { status: 403 })))

    render(
      <RealAuthProvider>
        <Probe />
      </RealAuthProvider>,
    )

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Access denied'))
    expect(mocks.signInWithRedirect).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(mocks.signOut).toHaveBeenCalledTimes(1)
  })

  it('clears the user when the refresh token dies (tokenRefresh_failure Hub event)', async () => {
    withSession('id-token')
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

    mocks.hubHandlerRef.current?.({ payload: { event: 'tokenRefresh_failure' } })

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Signing in'))
  })

  it('lets local dev override the viewed role (frontend only — real roles preserved)', async () => {
    withSession('id-token')
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
