import type { ReactNode } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { fetchAuthSession, signInWithRedirect, signOut } from 'aws-amplify/auth'
import { Hub } from 'aws-amplify/utils'
import apiService from '@/service/api-service'
import { AuthContext } from './AuthContext'
import { useIdleTimeout } from './useIdleTimeout'
import type { AuthContextValue, AuthUser } from './types'

type Props = {
  children: ReactNode
}

/** A blocking failure that must NOT bounce to sign-in (doing so would loop on a valid session). */
type LoadError = { status?: number; message: string }

// Local-dev "view as" override. Only ever read under import.meta.env.DEV, so it is inert (and
// tree-shaken) in deployed builds — a real session's role can never be overridden in DEV/TEST/PROD.
const DEV_ROLE_KEY = 'nr-ilcr.dev-role'

// Idle timeout: sign out after 60 minutes with no user activity (the token stays refreshed while
// active). Shared-workstation safety.
const IDLE_TIMEOUT_MS = 60 * 60 * 1000

function initialDevRole(): string | null {
  if (!import.meta.env.DEV || typeof window === 'undefined') {
    return null
  }
  try {
    return window.localStorage.getItem(DEV_ROLE_KEY)
  } catch {
    return null
  }
}

function currentRoute(): string {
  return `${window.location.pathname}${window.location.search}`
}

function isOAuthCallback(): boolean {
  return new URLSearchParams(window.location.search).has('code')
}

/**
 * Restore the route the user asked for before being bounced to the Hosted UI. Amplify returns it as
 * the {@code customState} on the {@code customOAuthState} Hub event; we replace history and let the
 * router re-evaluate rather than full-reload.
 */
function restoreRoute(path: string): void {
  if (!path || path === currentRoute()) {
    return
  }
  window.history.replaceState(null, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export default function RealAuthProvider({ children }: Props) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<LoadError | null>(null)
  const [devRole, setDevRole] = useState<string | null>(initialDevRole)
  const redirectingRef = useRef(false)

  const loadUser = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const session = await fetchAuthSession()
      if (!session.tokens?.idToken) {
        setUser(null)
        return
      }
      // roles:[] (no ILCR group) is a valid, non-looping state — the user is set and the no-access
      // screen renders it; do NOT null the user here.
      const { data } = await apiService.getAxiosInstance().get<AuthUser>('/v1/me')
      setUser(data)
    } catch (err: unknown) {
      setUser(null)
      const status = axios.isAxiosError(err) ? err.response?.status : undefined
      if (status === 401) {
        // Token rejected/expired — leave error null so the effect bounces to re-authenticate.
        return
      }
      if (status === 403) {
        setError({ status, message: 'Your account is not permitted to use this application.' })
        return
      }
      // A 5xx, a network error, or an Amplify session failure: do NOT bounce — with a valid Cognito
      // session the Hosted UI would redirect straight back and loop. Offer a retry instead.
      setError({ status, message: 'The server is temporarily unavailable. Please try again.' })
    } finally {
      setIsLoading(false)
    }
  }, [])

  const signIn = useCallback(async () => {
    await signInWithRedirect({ customState: currentRoute() })
  }, [])

  const doSignOut = useCallback(async () => {
    // signOut() with the OAuth config redirects through the Cognito/loginproxy logout endpoint,
    // ending the upstream session (shared-workstation safety), then back to redirectSignOut.
    await signOut()
  }, [])

  useEffect(() => {
    const unsubscribe = Hub.listen('auth', ({ payload }) => {
      switch (payload.event) {
        case 'signedIn':
          void loadUser()
          break
        case 'signedOut':
        case 'tokenRefresh_failure':
          setUser(null)
          break
        case 'customOAuthState':
          restoreRoute(payload.data)
          break
        default:
          break
      }
    })
    void loadUser()
    return unsubscribe
  }, [loadUser])

  useEffect(() => {
    // Unauthenticated with no blocking error (and not mid-callback) → bounce to the Hosted UI once.
    // The !error guard is what breaks the redirect loop on 403/5xx: a valid Cognito session would
    // otherwise return immediately and re-trigger the failing /me.
    if (!isLoading && !user && !error && !isOAuthCallback() && !redirectingRef.current) {
      redirectingRef.current = true
      void signIn()
    }
  }, [isLoading, user, error, signIn])

  // Arm the 60-minute idle timeout once a real session exists; activity resets it, inactivity signs out.
  useIdleTimeout(doSignOut, IDLE_TIMEOUT_MS, user !== null)

  const realRoles = user?.roles ?? []
  const effectiveRoles = import.meta.env.DEV && devRole ? [devRole] : realRoles
  const effectiveUser = user ? { ...user, roles: effectiveRoles } : null

  const value: AuthContextValue = {
    user: effectiveUser,
    isAuthenticated: effectiveUser !== null,
    isLoading,
    hasRole: (role: string) => effectiveRoles.includes(role),
    signIn,
    signOut: doSignOut,
    devRoleSwitch: import.meta.env.DEV
      ? {
          override: devRole,
          realRoles,
          setOverride: (role: string | null) => {
            try {
              if (role) {
                window.localStorage.setItem(DEV_ROLE_KEY, role)
              } else {
                window.localStorage.removeItem(DEV_ROLE_KEY)
              }
            } catch {
              // Storage disabled — override still applies in memory for this session.
            }
            setDevRole(role)
          },
        }
      : undefined,
  }

  if (error) {
    // Blocking failure — render a screen (not a redirect) so a valid Cognito session can't loop.
    const accessDenied = error.status === 403
    return (
      <div role="alert" className="auth-error">
        <h2>{accessDenied ? 'Access denied' : 'Connection error'}</h2>
        <p>{error.message}</p>
        {accessDenied ? (
          <button type="button" onClick={() => void doSignOut()}>
            Sign out
          </button>
        ) : (
          <button type="button" onClick={() => void loadUser()}>
            Retry
          </button>
        )}
      </div>
    )
  }

  if (!user) {
    // Auth chrome while loading or being redirected to sign in. Labelled + non-visual-only for a11y.
    return (
      <div role="status" aria-live="polite" className="auth-signing-in">
        Signing in…
      </div>
    )
  }

  return <AuthContext value={value}>{children}</AuthContext>
}
