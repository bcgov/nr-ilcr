import type { FC } from 'react'
import { Button, Column, Grid, Tag, Tile } from '@carbon/react'
import { ArrowRight } from '@carbon/icons-react'
import { useNavigate } from '@tanstack/react-router'
import useAuth from '@/context/auth/useAuth'
import PageTitle from '@/components/core/PageTitle'

/**
 * ILCR home landing. The scaffold's placeholder users-CRUD demo (which fetched the non-existent
 * `/v1/users` endpoint — ILCR has no users domain) has been removed. The real Home page with a
 * mill/year selector is a deferred Epic-1 item; until then the default mill/year is set in
 * {@code millYearDefaults.ts} and this page just links into the schedules.
 */
const Dashboard: FC = () => {
  const { user } = useAuth()
  const navigate = useNavigate()

  if (!user) {
    return null
  }

  return (
    <div className="app-page">
      <Grid fullWidth className="app-page__header">
        <PageTitle
          title="ILCR Workspace"
          subtitle="Interior Logging Cost Report — modernization workspace."
        />
      </Grid>

      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={4} lg={5}>
          <Tile className="dashboard-summary">
            <h2>{user.displayName}</h2>
            <p className="dashboard-summary__meta">{user.email}</p>
            <div className="app-role-tags" aria-label="Current roles">
              {user.roles.map((role) => (
                <Tag key={role} type={role === 'ILCR_ADMIN' ? 'purple' : 'teal'}>
                  {role}
                </Tag>
              ))}
            </div>
          </Tile>
        </Column>

        <Column sm={4} md={4} lg={11}>
          <Tile>
            {/* Unclassed headings, deliberately: this landing screen keeps Carbon's own scale (h2
                2rem / h3 1.75rem) rather than the 1.25rem section-heading standard the schedules
                take. CSP does the same — its landing page's `__section-heading` and `__tile-heading`
                carry no font-size rule at all — so normalising these would move AWAY from the app
                the client points at (#411 Overall 11). */}
            <h3>Schedules</h3>
            <p>Open Schedule 1 for the mill and reporting year in context.</p>
            <Button
              kind="primary"
              renderIcon={ArrowRight}
              onClick={() => navigate({ to: '/schedule-1' })}
            >
              Open Schedule 1
            </Button>
          </Tile>
        </Column>
      </Grid>
    </div>
  )
}

export default Dashboard
