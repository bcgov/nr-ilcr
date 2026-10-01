import type { FC, ReactNode } from 'react'
import { useId } from 'react'
import './index.scss'

interface SubPanelProps {
  /** The grey header-bar title. */
  readonly title: string
  readonly children: ReactNode
}

/**
 * A bordered panel with a grey header bar — the legacy sub-page panel look shared by the Schedule 1
 * and Schedule 3 cost sub-pages (the "Add …" form and the list each sit in one), and now the mill
 * administration screen's two sections (mills.xhtml:28, :110). Wired as an ARIA landmark rather than
 * a bare heading: a `<section>` only earns the `region` role with an explicit name, so callers that
 * queried `getByRole('region', { name: … })` off the old `aria-labelledby` heading would otherwise
 * lose that landmark on the switch to this shared component.
 */
const SubPanel: FC<SubPanelProps> = ({ title, children }) => {
  const titleId = useId()
  return (
    <section className="sub-panel" aria-labelledby={titleId}>
      <h3 className="sub-panel__title" id={titleId}>
        {title}
      </h3>
      <div className="sub-panel__body">{children}</div>
    </section>
  )
}

export default SubPanel
