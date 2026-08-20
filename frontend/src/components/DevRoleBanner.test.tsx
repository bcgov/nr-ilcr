import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AuthContext } from '@/context/auth/AuthContext'
import type { AuthContextValue } from '@/context/auth/types'
import DevRoleBanner from './DevRoleBanner'

function authValue(devRoleSwitch: AuthContextValue['devRoleSwitch']): AuthContextValue {
  return {
    user: null,
    isAuthenticated: false,
    isLoading: false,
    hasRole: () => false,
    signIn: () => undefined,
    signOut: () => undefined,
    devRoleSwitch,
  }
}

describe('DevRoleBanner', () => {
  it('renders nothing when no override is active', () => {
    render(
      <AuthContext
        value={authValue({
          override: null,
          realRoles: ['ILCR_SUBMITTER'],
          setOverride: () => undefined,
        })}
      >
        <DevRoleBanner />
      </AuthContext>,
    )
    expect(screen.queryByText(/viewing as/)).toBeNull()
  })

  it('renders nothing when the override matches the real role (a no-op override)', () => {
    render(
      <AuthContext
        value={authValue({
          override: 'ILCR_SUBMITTER',
          realRoles: ['ILCR_SUBMITTER'],
          setOverride: () => undefined,
        })}
      >
        <DevRoleBanner />
      </AuthContext>,
    )
    expect(screen.queryByText(/viewing as/)).toBeNull()
  })

  it('renders nothing when devRoleSwitch is absent (deployed builds)', () => {
    render(
      <AuthContext value={authValue(undefined)}>
        <DevRoleBanner />
      </AuthContext>,
    )
    expect(screen.queryByText(/viewing as/)).toBeNull()
  })

  it('warns while an override is active, naming the overridden and the real role', () => {
    render(
      <AuthContext
        value={authValue({
          override: 'ILCR_ADMIN',
          realRoles: ['ILCR_SUBMITTER'],
          setOverride: () => undefined,
        })}
      >
        <DevRoleBanner />
      </AuthContext>,
    )
    expect(screen.getByText(/viewing as ILCR_ADMIN/)).toBeInTheDocument()
    expect(screen.getByText(/real role \(ILCR_SUBMITTER\)/)).toBeInTheDocument()
  })
})
