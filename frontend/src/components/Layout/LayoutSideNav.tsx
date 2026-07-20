import type { FC } from 'react'
import { SideNav, SideNavItems, SideNavLink, SideNavMenu, SideNavMenuItem } from '@carbon/react'
import { Link, useLocation } from '@tanstack/react-router'
import useLayout from '@/context/layout/useLayout'
import { NAVIGATION_ITEMS, isNavigationMenu } from '@/routes/-navigation'

const LayoutSideNav: FC = () => {
  const { closeSideNav, isSideNavExpanded } = useLayout()
  const location = useLocation()

  return (
    <SideNav expanded={isSideNavExpanded} isPersistent={isSideNavExpanded} isChildOfHeader>
      <SideNavItems>
        {NAVIGATION_ITEMS.map((item) => {
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
                    onClick={closeSideNav}
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
              onClick={closeSideNav}
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
