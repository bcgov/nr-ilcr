import { use } from 'react'
import { AuthContext } from './AuthContext'

export default function useAuth() {
  return use(AuthContext)
}
