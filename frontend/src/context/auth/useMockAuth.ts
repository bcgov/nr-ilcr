import { use } from 'react'
import { MockAuthContext } from './MockAuthContext'

export default function useMockAuth() {
  return use(MockAuthContext)
}
