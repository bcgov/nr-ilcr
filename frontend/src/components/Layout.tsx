import type { FC } from 'react'
import { useState } from 'react'
import {
  Content,
  Header,
  HeaderGlobalBar,
  HeaderMenuButton,
  HeaderName,
  SideNav,
  SideNavItems,
  SideNavLink,
  SkipToContent,
} from '@carbon/react'
import { Document, Home, UserMultiple } from '@carbon/icons-react'
import MockUserSelector from '@/components/MockUserSelector'
import ThemeToggle from '@/components/ThemeToggle'

type Props = {
  children: React.ReactNode
}

const Layout: FC<Props> = ({ children }) => {
  const [isSideNavExpanded, setIsSideNavExpanded] = useState(false)

  return (
    <>
      <Header aria-label="Interior Logging Cost Reports (ILCR)">
        <SkipToContent />
        <HeaderMenuButton
          aria-label={isSideNavExpanded ? 'Close menu' : 'Open menu'}
          isActive={isSideNavExpanded}
          isCollapsible
          onClick={() => setIsSideNavExpanded((current) => !current)}
        />
        <HeaderName href="/" prefix="">
          Interior Logging Cost Reports
        </HeaderName>
        <HeaderGlobalBar>
          <MockUserSelector />
          <ThemeToggle />
        </HeaderGlobalBar>
        <SideNav expanded={isSideNavExpanded} isPersistent={isSideNavExpanded} isChildOfHeader>
          <SideNavItems>
            <SideNavLink href="/" renderIcon={Home} isActive>
              Dashboard
            </SideNavLink>
            <SideNavLink href="/" renderIcon={Document}>
              Submissions
            </SideNavLink>
            <SideNavLink href="/" renderIcon={UserMultiple}>
              Mill Associations
            </SideNavLink>
          </SideNavItems>
        </SideNav>
      </Header>
      <Content className={`app-content ${isSideNavExpanded ? 'app-content--with-nav' : ''}`}>
        {children}
      </Content>
    </>
  )
}

export default Layout
