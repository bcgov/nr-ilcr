import type { FC } from 'react'
import { Button, Column, Grid } from '@carbon/react'
import { useNavigate } from '@tanstack/react-router'

const NotFound: FC = () => {
  const navigate = useNavigate()
  const buttonClicked = () => {
    navigate({
      to: '/',
    })
  }
  return (
    <Grid fullWidth className="app-page__body">
      <Column sm={4} md={8} lg={16}>
        <h1>404</h1>
        <p>The page you’re looking for does not exist.</p>
        <Button name="homeBtn" id="homeBtn" onClick={() => buttonClicked()}>
          Back Home
        </Button>
      </Column>
    </Grid>
  )
}

export default NotFound
