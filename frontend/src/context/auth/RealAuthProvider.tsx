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

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    isLoading,
    hasRole: (role: string) => user?.roles.includes(role) ?? false,
    signIn,
    signOut: doSignOut,
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
