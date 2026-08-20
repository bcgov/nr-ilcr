import { Logout } from '@carbon/icons-react'
import { HeaderGlobalAction } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'

/**
 * Sign-out control for a real FAM session. {@code signOut()} runs the Cognito/loginproxy logout chain
 * (ending the upstream session so a shared workstation re-prompts), then redirects back. Hidden in
 * mock mode — a fixed local dev user has nothing to sign out of (use the mock selector) — and when
 * there is no authenticated user.
 */
export default function SignOutButton() {
  const { isAuthenticated, mock, signOut } = useAuth()

  if (!isAuthenticated || mock) {
    return null
  }

  return (
    <HeaderGlobalAction aria-label="Sign out" tooltipAlignment="end" onClick={() => void signOut()}>
      <Logout size={20} />
    </HeaderGlobalAction>
  )
}
