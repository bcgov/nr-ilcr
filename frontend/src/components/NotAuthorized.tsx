import type { FC } from 'react'
import { Button, Column, Grid } from '@carbon/react'
import { ArrowLeft } from '@carbon/icons-react'
import { Link } from '@tanstack/react-router'

/**
 * Shown when a signed-in user reaches a page their role does not permit (e.g. a submitter opening an
 * admin route by direct URL). The server is the real boundary (it 403s the API); this is the UX. It
 * deliberately renders no working-context banner — the page is a dead end, not a schedule.
 */
const NotAuthorized: FC = () => {
  return (
    <div className="app-page">
      <Grid fullWidth className="app-page__header">
        <Column sm={4} md={8} lg={16}>
          <h1 className="page-title-heading">Not authorized</h1>
          <p>You do not have permission to view this page.</p>
        </Column>
      </Grid>
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16}>
          <Button as={Link} to="/" name="homeBtn" renderIcon={ArrowLeft}>
            Back Home
          </Button>
        </Column>
      </Grid>
    </div>
  )
}

export default NotAuthorized
