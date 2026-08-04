import type { FC, ReactNode } from 'react'
import './index.scss'

interface SubPanelProps {
  /** The grey header-bar title. */
  readonly title: string
  readonly children: ReactNode
}

/**
 * A bordered panel with a grey header bar — the legacy sub-page panel look shared by the Schedule 1
 * and Schedule 3 cost sub-pages (the "Add …" form and the list each sit in one).
 */
const SubPanel: FC<SubPanelProps> = ({ title, children }) => (
  <section className="sub-panel">
    <h3 className="sub-panel__title">{title}</h3>
    <div className="sub-panel__body">{children}</div>
  </section>
)

export default SubPanel
