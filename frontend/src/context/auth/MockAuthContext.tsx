import { createContext } from 'react'
import type { IlcrRole, MockUser } from './mockUsers'
import { MOCK_USERS } from './mockUsers'

export type MockAuthContextValue = {
  user: MockUser
  users: MockUser[]
  setUserId: (id: string) => void
  hasRole: (role: IlcrRole) => boolean
}

export const MockAuthContext = createContext<MockAuthContextValue>({
  user: MOCK_USERS[0],
  users: MOCK_USERS,
  setUserId: () => undefined,
  hasRole: () => false,
})
