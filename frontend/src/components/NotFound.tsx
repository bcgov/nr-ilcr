import type { FC } from 'react'
import { Button, Column, Grid } from '@carbon/react'
import { useNavigate } from '@tanstack/react-router'
import PageTitle from '@/components/core/PageTitle'

const NotFound: FC = () => {
  const navigate = useNavigate()
  const buttonClicked = () => {
    navigate({
      to: '/',
    })
  }
  return (
    <div className="app-page">
      <Grid fullWidth className="app-page__header">
        <PageTitle title="404" subtitle="The page you’re looking for does not exist." />
      </Grid>
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16}>
          <Button name="homeBtn" id="homeBtn" onClick={() => buttonClicked()}>
            Back Home
          </Button>
        </Column>
      </Grid>
    </div>
  )
}

export default NotFound
