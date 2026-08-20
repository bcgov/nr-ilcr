import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthContext } from '@/context/auth/AuthContext'
import type { AuthContextValue } from '@/context/auth/types'
import DevRoleSwitcher from './DevRoleSwitcher'

function authValue(devRoleSwitch: AuthContextValue['devRoleSwitch']): AuthContextValue {
  return {
    user: {
      userGuid: 'g',
      displayName: 'U',
      email: null,
      identityProvider: 'idir',
      roles: ['ILCR_SUBMITTER'],
    },
    isAuthenticated: true,
    isLoading: false,
    hasRole: () => false,
    signIn: () => undefined,
    signOut: () => undefined,
    devRoleSwitch,
  }
}

describe('DevRoleSwitcher', () => {
  it('renders nothing when devRoleSwitch is absent (deployed builds / mock mode)', () => {
    render(
      <AuthContext value={authValue(undefined)}>
        <DevRoleSwitcher />
      </AuthContext>,
    )
    expect(screen.queryByLabelText('View as role')).toBeNull()
  })

  it('lets a dev override the viewed role on a real session', async () => {
    const setOverride = vi.fn()
    render(
      <AuthContext
        value={authValue({ override: null, realRoles: ['ILCR_SUBMITTER'], setOverride })}
      >
        <DevRoleSwitcher />
      </AuthContext>,
    )

    await userEvent.selectOptions(screen.getByLabelText('View as role'), 'ILCR_ADMIN')

    expect(setOverride).toHaveBeenCalledWith('ILCR_ADMIN')
  })

  it('clears the override back to the real role', async () => {
    const setOverride = vi.fn()
    render(
      <AuthContext
        value={authValue({ override: 'ILCR_ADMIN', realRoles: ['ILCR_SUBMITTER'], setOverride })}
      >
        <DevRoleSwitcher />
      </AuthContext>,
    )

    await userEvent.selectOptions(
      screen.getByLabelText('View as role'),
      screen.getByRole('option', { name: /Real/ }),
    )

    expect(setOverride).toHaveBeenCalledWith(null)
  })
})
