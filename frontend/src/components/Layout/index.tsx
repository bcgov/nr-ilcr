import type { FC, ReactNode } from 'react'
import { Content, HeaderContainer } from '@carbon/react'
import LayoutProvider from '@/context/layout/LayoutProvider'
import LayoutHeader from './LayoutHeader'
import './index.scss'

type Props = {
  children: ReactNode
}

const Layout: FC<Props> = ({ children }) => (
  <LayoutProvider>
    <HeaderContainer render={() => <LayoutHeader />} />
    <Content className="app-content">{children}</Content>
  </LayoutProvider>
)

export default Layout
