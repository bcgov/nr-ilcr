import { createContext } from 'react'
import type { AuthContextValue } from './types'

export const AuthContext = createContext<AuthContextValue>({
  user: null,
  isAuthenticated: false,
  isLoading: false,
  hasRole: () => false,
  signIn: () => undefined,
  signOut: () => undefined,
})
