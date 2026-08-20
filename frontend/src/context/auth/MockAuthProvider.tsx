import type { ReactNode } from 'react'
import { useMemo, useState } from 'react'
import { AuthContext } from './AuthContext'
import type { AuthContextValue, AuthUser } from './types'
import { MOCK_USER_STORAGE_KEY, MOCK_USERS, findMockUser } from './mockUsers'

type Props = {
  children: ReactNode
}

function getInitialUserId() {
  if (typeof window === 'undefined') {
    return MOCK_USERS[0].id
  }

  try {
    return window.localStorage.getItem(MOCK_USER_STORAGE_KEY) ?? MOCK_USERS[0].id
  } catch {
    return MOCK_USERS[0].id
  }
}

function persistUserId(id: string) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(MOCK_USER_STORAGE_KEY, id)
  } catch {
    // Storage can be disabled in locked-down browsers; selection still works in memory.
  }
}

export default function MockAuthProvider({ children }: Props) {
  const [selectedUserId, setSelectedUserId] = useState(getInitialUserId)
  const mockUser = findMockUser(selectedUserId)

  const value = useMemo<AuthContextValue>(() => {
    const user: AuthUser = {
      userGuid: mockUser.userName,
      displayName: mockUser.displayName,
      email: mockUser.email,
      identityProvider: 'MOCK',
      roles: [...mockUser.roles],
    }
    return {
      user,
      isAuthenticated: true,
      isLoading: false,
      hasRole: (role: string) => mockUser.roles.includes(role as (typeof mockUser.roles)[number]),
      signIn: () => undefined,
      signOut: () => undefined,
      mock: {
        users: MOCK_USERS,
        currentUserId: selectedUserId,
        setUserId: (id: string) => {
          persistUserId(id)
          setSelectedUserId(id)
        },
      },
    }
  }, [mockUser, selectedUserId])

  return <AuthContext value={value}>{children}</AuthContext>
}
