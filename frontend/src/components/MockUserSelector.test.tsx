import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthContext } from '@/context/auth/AuthContext'
import type { AuthContextValue } from '@/context/auth/types'
import MockAuthProvider from '@/context/auth/MockAuthProvider'
import MockUserSelector from './MockUserSelector'

describe('MockUserSelector', () => {
  it('renders the role selector under the mock provider', () => {
    render(
      <MockAuthProvider>
        <MockUserSelector />
      </MockAuthProvider>,
    )

    expect(screen.getByLabelText('Mock user')).toBeInTheDocument()
    // Exactly the two single-role users — there is no combined admin/submitter user.
    expect(screen.getAllByRole('option')).toHaveLength(2)
  })

  it('switches the active mock user when a role is picked', async () => {
    render(
      <MockAuthProvider>
        <MockUserSelector />
      </MockAuthProvider>,
    )
    const select = screen.getByLabelText<HTMLSelectElement>('Mock user')

    await userEvent.selectOptions(select, 'submitter')

    expect(select.value).toBe('submitter')
    expect(window.localStorage.getItem('nr-ilcr.mock-user')).toBe('submitter')
  })

  it('renders nothing under the real provider (no role switcher in deployed builds)', () => {
    const realValue: AuthContextValue = {
      user: {
        userGuid: 'GUID',
        displayName: 'Real User',
        email: null,
        identityProvider: 'idir',
        roles: ['ILCR_SUBMITTER'],
      },
      isAuthenticated: true,
      isLoading: false,
      hasRole: () => false,
      signIn: () => undefined,
      signOut: () => undefined,
    }

    render(
      <AuthContext value={realValue}>
        <MockUserSelector />
      </AuthContext>,
    )

    expect(screen.queryByLabelText('Mock user')).toBeNull()
  })
})
