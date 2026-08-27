import type { FC, ReactNode } from 'react'
import { Content, HeaderContainer } from '@carbon/react'
import DevRoleBanner from '@/components/DevRoleBanner'
import LayoutProvider from '@/context/layout/LayoutProvider'
import useLayout from '@/context/layout/useLayout'
import LayoutHeader from './LayoutHeader'
import Footer from './Footer'
import './index.scss'

type Props = {
  readonly children: ReactNode
}

// Module scope, NOT an inline arrow. Carbon's HeaderContainer uses `render` as a component TYPE
// (`jsx(Children, ...)`), so a fresh arrow identity on each LayoutShell render remounts the entire
// header subtree on every side-nav toggle — destroying the button the user just pressed and dropping
// keyboard focus to <body>.
const renderHeader = () => <LayoutHeader />

// The legacy `#subMenu` mill/status strip that used to render here (ContextBanner) is superseded by
// the per-schedule ScheduleTombstone header, whose right column carries the same working-context
// lines. The banner is no longer rendered globally so the mill/status shows once, on the page header.
//
// Split out of `Layout` because it reads the layout context: `Layout` is what renders LayoutProvider,
// so it cannot call useLayout itself (that throws — see useLayout.ts).
const LayoutShell: FC<Props> = ({ children }) => {
  const { isLargeViewport, isSideNavExpanded } = useLayout()
  // Carbon's own content offsets are sibling selectors (`.cds--side-nav ~ .cds--content`), and they
  // never match here: the SideNav is a child of <Header> (LayoutHeader) while <Content> is the
  // header's sibling, so the two are not siblings. That was harmless while the expanded nav was a
  // transient user action; #316 makes it the DEFAULT at lg+, so the app owns the offset — see
  // index.scss. The fixed footer needs the same treatment or the nav paints over its left 16rem.
  //
  // Gated on isLargeViewport as well as expansion. The SCSS rule is already scoped to `lg`, but
  // keying the class off expansion alone put it in the DOM at every width — so the only thing
  // stopping a 16rem off-screen shove on a phone was a media query nothing tests. Now the DOM says
  // what it means, and a unit test can assert it.
  const isInset = isSideNavExpanded && isLargeViewport

  return (
    <>
      <HeaderContainer render={renderHeader} />
      {/* Inside Content so it inherits the fixed-header top offset (.cds--header ~ .cds--content);
          rendered at the top of the flow above the fixed brand header would hide it. */}
      <Content className={isInset ? 'app-content app-content--nav-expanded' : 'app-content'}>
        <DevRoleBanner />
        {children}
      </Content>
      <Footer className={isInset ? 'app-footer--nav-expanded' : undefined} />
    </>
  )
}

const Layout: FC<Props> = ({ children }) => (
  <LayoutProvider>
    <LayoutShell>{children}</LayoutShell>
  </LayoutProvider>
)

export default Layout
