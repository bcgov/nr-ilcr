import type { FC } from 'react'
import { Header, HeaderGlobalBar, HeaderMenuButton, HeaderName, SkipToContent } from '@carbon/react'
import { Link } from '@tanstack/react-router'
import DevRoleSwitcher from '@/components/DevRoleSwitcher'
import MockUserSelector from '@/components/MockUserSelector'
import SignOutButton from '@/components/SignOutButton'
import ThemeToggle from '@/components/ThemeToggle'
import useLayout from '@/context/layout/useLayout'
import LayoutSideNav from './LayoutSideNav'

const APP_NAME = 'Interior Logging Cost Report (ILCR)'

const LayoutHeader: FC = () => {
  const { isSideNavExpanded, toggleSideNav } = useLayout()

  return (
    <Header aria-label={APP_NAME} className="bc-header">
      <SkipToContent />
      <HeaderMenuButton
        aria-label={isSideNavExpanded ? 'Close menu' : 'Open menu'}
        isActive={isSideNavExpanded}
        isCollapsible
        onClick={toggleSideNav}
      />
      <HeaderName as={Link} to="/" prefix="">
        {APP_NAME}
      </HeaderName>
      <HeaderGlobalBar>
        <MockUserSelector />
        <DevRoleSwitcher />
        <ThemeToggle />
        <SignOutButton />
      </HeaderGlobalBar>
      <LayoutSideNav />
    </Header>
  )
}

export default LayoutHeader
