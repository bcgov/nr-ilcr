import type { FC } from 'react'
import { SideNav, SideNavItems, SideNavLink } from '@carbon/react'
import { Link, useLocation } from '@tanstack/react-router'
import useLayout from '@/context/layout/useLayout'
import { NAVIGATION_ITEMS } from '@/routes/-navigation'

const LayoutSideNav: FC = () => {
  const { closeSideNav, isSideNavExpanded } = useLayout()
  const location = useLocation()

  return (
    <SideNav expanded={isSideNavExpanded} isPersistent={isSideNavExpanded} isChildOfHeader>
      <SideNavItems>
        {NAVIGATION_ITEMS.map((item) => (
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
        ))}
      </SideNavItems>
    </SideNav>
  )
}

export default LayoutSideNav
