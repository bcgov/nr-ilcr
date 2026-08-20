import type { ReactNode } from 'react'
import { useLocation } from '@tanstack/react-router'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/types'
import { isAdminOnlyPath } from '@/routes/-navigation'
import NoAccess from '@/components/NoAccess'
import NotAuthorized from '@/components/NotAuthorized'

type Props = {
  children: ReactNode
}

/**
 * Role gate at the app shell. A signed-in user with no ILCR role gets the no-access screen (O8/AC4);
 * a non-admin who reaches an admin-only route by direct URL gets a not-authorized screen (AC1). The
 * server independently enforces the 403 — this guard is the matching UX so a hidden menu is not the
 * only thing standing between a bookmarked admin link and the page.
 */
export default function RouteGuard({ children }: Props) {
  const { user, hasRole, isLoading } = useAuth()
  const { pathname } = useLocation()

  if (isLoading) {
    // Auth is still resolving (session + /me in flight). user is null and hasRole() is false during
    // this window, so rendering now would flash NoAccess/NotAuthorized before roles arrive. Wait.
    return null
  }
  if (user && (user.roles ?? []).length === 0) {
    return <NoAccess />
  }
  if (isAdminOnlyPath(pathname) && !hasRole(ILCR_ROLES.admin)) {
    return <NotAuthorized />
  }
  return <>{children}</>
}
