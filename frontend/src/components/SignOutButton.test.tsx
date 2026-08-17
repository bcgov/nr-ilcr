import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthContext } from '@/context/auth/AuthContext'
import type { AuthContextValue } from '@/context/auth/types'
import SignOutButton from './SignOutButton'

function authValue(overrides: Partial<AuthContextValue>): AuthContextValue {
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
    ...overrides,
  }
}

describe('SignOutButton', () => {
  it('signs out a real authenticated session', async () => {
    const signOut = vi.fn()
    render(
      <AuthContext value={authValue({ signOut })}>
        <SignOutButton />
      </AuthContext>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(signOut).toHaveBeenCalledTimes(1)
  })

  it('is hidden in mock mode (fixed local dev user)', () => {
    render(
      <AuthContext
        value={authValue({ mock: { users: [], currentUserId: '', setUserId: () => undefined } })}
      >
        <SignOutButton />
      </AuthContext>,
    )
    expect(screen.queryByRole('button', { name: 'Sign out' })).toBeNull()
  })

  it('is hidden when there is no authenticated user', () => {
    render(
      <AuthContext value={authValue({ isAuthenticated: false, user: null })}>
        <SignOutButton />
      </AuthContext>,
    )
    expect(screen.queryByRole('button', { name: 'Sign out' })).toBeNull()
  })
})
