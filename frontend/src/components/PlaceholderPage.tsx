import type { FC } from 'react'
import { Column, Grid } from '@carbon/react'
import EmptySection from '@/components/core/EmptySection'
import PageTitle from '@/components/core/PageTitle'

type PlaceholderPageProps = {
  description: string
  title: string
}

const PlaceholderPage: FC<PlaceholderPageProps> = ({ description, title }) => (
  <div className="app-page">
    <Grid fullWidth className="app-page__header">
      <PageTitle title={title} subtitle={description} />
    </Grid>
    <Grid fullWidth className="app-page__body">
      <Column sm={4} md={8} lg={16}>
        <EmptySection
          title={`${title} workspace`}
          description="This route is scaffolded for local development and will be filled in by the modernization slices."
        />
      </Column>
    </Grid>
  </div>
)

export default PlaceholderPage
