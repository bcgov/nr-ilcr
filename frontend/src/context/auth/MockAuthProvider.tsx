import type { ReactNode } from 'react'
import { useMemo, useState } from 'react'
import type { MockAuthContextValue } from './MockAuthContext'
import { MockAuthContext } from './MockAuthContext'
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
  const user = findMockUser(selectedUserId)

  const value = useMemo<MockAuthContextValue>(
    () => ({
      user,
      users: MOCK_USERS,
      setUserId: (id: string) => {
        persistUserId(id)
        setSelectedUserId(id)
      },
      hasRole: (role) => user.roles.includes(role),
    }),
    [user],
  )

  return <MockAuthContext value={value}>{children}</MockAuthContext>
}
