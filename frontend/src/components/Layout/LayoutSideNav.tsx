import type { FC } from 'react'
import { SideNav, SideNavItems, SideNavLink, SideNavMenu, SideNavMenuItem } from '@carbon/react'
import { Link, useLocation } from '@tanstack/react-router'
import useLayout from '@/context/layout/useLayout'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import { isNavigationMenu, visibleNavigationItems } from '@/routes/-navigation'

const LayoutSideNav: FC = () => {
  const { closeSideNav, dismissSideNav, isLargeViewport, isSideNavExpanded } = useLayout()
  const location = useLocation()
  const { hasRole } = useAuth()
  // Admin-only items (Administration) are hidden entirely for non-admins; the server still enforces
  // the 403 (MAINTAIN_CODE_TABLES), so this is UX, not the security boundary.
  const visibleItems = visibleNavigationItems(hasRole(ILCR_ROLES.admin))
  // At lg+ the nav sits beside the page, so it stays open across navigations — that is the whole
  // point of #316. Below lg it is an overlay ON TOP of the page just navigated to, so it still
  // closes; leaving it open there would hide the destination behind the menu after every click.
  const handleNavigate = isLargeViewport ? undefined : closeSideNav
  // Carbon routes BOTH its Escape handler and its onBlur handler through `onToggle`, and the blur
  // path fires on every focus exit — including clicking a nav link. Honour only the keyboard
  // dismissal, or the nav would slam shut the instant the user tabs or clicks away from it.
  const handleToggle = (event: { type?: string } | undefined, isExpanded: boolean) => {
    if (!isExpanded && event?.type === 'keydown') {
      dismissSideNav()
    }
  }

  return (
    <SideNav
      expanded={isSideNavExpanded}
      isPersistent={isSideNavExpanded}
      isChildOfHeader
      onToggle={handleToggle}
      onOverlayClick={dismissSideNav}
    >
      <SideNavItems>
        {visibleItems.map((item) => {
          if (isNavigationMenu(item)) {
            const hasActiveChild = item.items.some((sub) => sub.path === location.pathname)
            return (
              <SideNavMenu
                key={item.name}
                title={item.name}
                renderIcon={item.icon}
                defaultExpanded={hasActiveChild}
                isActive={hasActiveChild}
              >
                {item.items.map((sub) => (
                  <SideNavMenuItem
                    key={sub.name}
                    as={Link}
                    to={sub.path}
                    isActive={sub.path === location.pathname}
                    onClick={handleNavigate}
                  >
                    {sub.name}
                  </SideNavMenuItem>
                ))}
              </SideNavMenu>
            )
          }
          return (
            <SideNavLink
              key={item.name}
              as={Link}
              to={item.path}
              isActive={item.path === location.pathname}
              renderIcon={item.icon}
              onClick={handleNavigate}
            >
              {item.name}
            </SideNavLink>
          )
        })}
      </SideNavItems>
    </SideNav>
  )
}

export default LayoutSideNav
