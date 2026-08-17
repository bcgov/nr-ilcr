import type { ReactNode } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchAuthSession, signInWithRedirect, signOut } from 'aws-amplify/auth'
import { Hub } from 'aws-amplify/utils'
import apiService from '@/service/api-service'
import { AuthContext } from './AuthContext'
import type { AuthContextValue, AuthUser } from './types'

type Props = {
  children: ReactNode
}

// Local-dev "view as" override. Only ever read under import.meta.env.DEV, so it is inert (and
// tree-shaken) in deployed builds — a real session's role can never be overridden in DEV/TEST/PROD.
const DEV_ROLE_KEY = 'nr-ilcr.dev-role'

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
  const [devRole, setDevRole] = useState<string | null>(initialDevRole)
  const redirectingRef = useRef(false)

  const loadUser = useCallback(async () => {
    setIsLoading(true)
    try {
      const session = await fetchAuthSession()
      if (!session.tokens?.idToken) {
        setUser(null)
        return
      }
      const { data } = await apiService.getAxiosInstance().get<AuthUser>('/v1/me')
      setUser(data)
    } catch {
      // No/expired session or /me failure — treated as unauthenticated; the effect below bounces
      // to the Hosted UI.
      setUser(null)
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
    // Unauthenticated (and not mid-callback) → bounce to the Hosted UI exactly once.
    if (!isLoading && !user && !isOAuthCallback() && !redirectingRef.current) {
      redirectingRef.current = true
      void signIn()
    }
  }, [isLoading, user, signIn])

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
