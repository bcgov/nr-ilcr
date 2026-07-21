import type { FC } from 'react'
import { Column, Grid, Tag, Tile } from '@carbon/react'
import useMockAuth from '@/context/auth/useMockAuth'
import EmptySection from '@/components/core/EmptySection'
import PageTitle from '@/components/core/PageTitle'

const Dashboard: FC = () => {
  const { user } = useMockAuth()

  return (
    <div className="app-page">
      <Grid fullWidth className="app-page__header">
        <PageTitle
          title="ILCR Workspace"
          subtitle="Interior Logging Cost Reports modernization scaffold for React and Spring Boot delivery."
        />
      </Grid>

      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={4} lg={5}>
          <Tile className="dashboard-summary">
            <p className="dashboard-summary__label">Current local principal</p>
            <h2>{user.displayName}</h2>
            <p className="dashboard-summary__meta">{user.email}</p>
            <p className="dashboard-summary__meta">{user.userName}</p>
            <div className="app-role-tags" aria-label="Current mock roles">
              {user.roles.map((role) => (
                <Tag key={role} type={role === 'ILCR_ADMIN' ? 'purple' : 'teal'}>
                  {role}
                </Tag>
              ))}
            </div>
          </Tile>
        </Column>

        <Column sm={4} md={4} lg={11}>
          <EmptySection
            title="Identity endpoint pending"
            description="The dashboard uses the local mock principal until the FAM-backed backend principal endpoint is available."
          />
        </Column>
      </Grid>
    </div>
  )
}

export default Dashboard
