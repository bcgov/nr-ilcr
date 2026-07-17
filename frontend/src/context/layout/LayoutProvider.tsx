import type { ReactNode } from 'react'
import { useMemo, useState } from 'react'
import LayoutContext from './LayoutContext'

export default function LayoutProvider({ children }: { children: ReactNode }) {
  const [isSideNavExpanded, setIsSideNavExpanded] = useState(false)

  const value = useMemo(
    () => ({
      closeSideNav: () => setIsSideNavExpanded(false),
      isSideNavExpanded,
      toggleSideNav: () => setIsSideNavExpanded((current) => !current),
    }),
    [isSideNavExpanded],
  )

  return <LayoutContext value={value}>{children}</LayoutContext>
}
