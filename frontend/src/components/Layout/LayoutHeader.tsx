import type { FC } from 'react'
import { Header, HeaderGlobalBar, HeaderMenuButton, HeaderName, SkipToContent } from '@carbon/react'
import { Link } from '@tanstack/react-router'
import MockUserSelector from '@/components/MockUserSelector'
import ThemeToggle from '@/components/ThemeToggle'
import useLayout from '@/context/layout/useLayout'
import LayoutSideNav from './LayoutSideNav'

const APP_NAME = 'Interior Logging Cost Reports (ILCR)'

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
        <ThemeToggle />
      </HeaderGlobalBar>
      <LayoutSideNav />
    </Header>
  )
}

export default LayoutHeader
