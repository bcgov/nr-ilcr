import type { FC, ReactNode } from 'react'
import { Content, HeaderContainer } from '@carbon/react'
import LayoutProvider from '@/context/layout/LayoutProvider'
import LayoutHeader from './LayoutHeader'
import './index.scss'

type Props = {
  readonly children: ReactNode
}

// The working-context info (legacy `#subMenu` strip) now renders inside each page's PageTitle header
// row (right of the breadcrumb) via `ContextBanner`, rather than as a separate full-width strip here.
const Layout: FC<Props> = ({ children }) => (
  <LayoutProvider>
    <HeaderContainer render={() => <LayoutHeader />} />
    <Content className="app-content">{children}</Content>
  </LayoutProvider>
)

export default Layout
