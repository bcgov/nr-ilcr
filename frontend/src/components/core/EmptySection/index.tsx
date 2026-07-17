import type { FC } from 'react'
import { UserSearch } from '@carbon/pictograms-react'
import './index.scss'

type EmptySectionProps = {
  description: string
  title: string
}

const EmptySection: FC<EmptySectionProps> = ({ description, title }) => (
  <section className="empty-section">
    <UserSearch aria-hidden className="empty-section__pictogram" />
    <h2>{title}</h2>
    <p>{description}</p>
  </section>
)

export default EmptySection
