import type { FC } from 'react'
import { Button, Column, Grid } from '@carbon/react'
import { Logout } from '@carbon/icons-react'
import useAuth from '@/context/auth/useAuth'

/**
 * Shown to an authenticated user whose FAM token carries no ILCR group (roles is empty). Rather than
 * a blank app or a 401 loop, they get a clear message and a way to sign out (O8). No working-context
 * banner — a user with no role has no mill/year context to show.
 */
const NoAccess: FC = () => {
  const { signOut } = useAuth()
  return (
    <div className="app-page">
      <Grid fullWidth className="app-page__header">
        <Column sm={4} md={8} lg={16}>
          <h1 className="page-title-heading">No ILCR access</h1>
          <p>
            Your account is signed in but has no ILCR role. Contact an administrator to request
            access.
          </p>
        </Column>
      </Grid>
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16}>
          <Button kind="secondary" renderIcon={Logout} onClick={() => void signOut()}>
            Sign out
          </Button>
        </Column>
      </Grid>
    </div>
  )
}

export default NoAccess
