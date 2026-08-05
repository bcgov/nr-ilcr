import type { FC, ReactNode } from 'react'
import { Content, HeaderContainer } from '@carbon/react'
import LayoutProvider from '@/context/layout/LayoutProvider'
import LayoutHeader from './LayoutHeader'
import Footer from './Footer'
import './index.scss'

type Props = {
  readonly children: ReactNode
}

// The legacy `#subMenu` mill/status strip that used to render here (ContextBanner) is superseded by
// the per-schedule ScheduleTombstone header, whose right column carries the same working-context
// lines. The banner is no longer rendered globally so the mill/status shows once, on the page header.
const Layout: FC<Props> = ({ children }) => (
  <LayoutProvider>
    <HeaderContainer render={() => <LayoutHeader />} />
    <Content className="app-content">{children}</Content>
    <Footer />
  </LayoutProvider>
)

export default Layout
