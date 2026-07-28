import type { FC, ReactNode } from 'react'
import { Content, HeaderContainer } from '@carbon/react'
import LayoutProvider from '@/context/layout/LayoutProvider'
import LayoutHeader from './LayoutHeader'
import ContextBanner from './ContextBanner'
import './index.scss'

type Props = {
  readonly children: ReactNode
}

// The ContextBanner is the legacy `#subMenu` strip: it sits inside the content flow, above the page
// body, on every route (Layout wraps all routes via routes/__root.tsx) — not in the Carbon Header,
// which is a fixed top bar (Story 1.4 Pinned Decision 2).
const Layout: FC<Props> = ({ children }) => (
  <LayoutProvider>
    <HeaderContainer render={() => <LayoutHeader />} />
    <Content className="app-content">
      <ContextBanner />
      {children}
    </Content>
  </LayoutProvider>
)

export default Layout
