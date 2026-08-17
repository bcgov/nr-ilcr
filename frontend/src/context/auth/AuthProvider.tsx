import type { ReactNode } from 'react'
import { isMockAuth } from '@/env'
import MockAuthProvider from './MockAuthProvider'
import RealAuthProvider from './RealAuthProvider'

type Props = {
  children: ReactNode
}

/**
 * Single auth seam: the local mock provider or the real FAM/Cognito (Amplify) provider, chosen by
 * the runtime {@code isMockAuth()} gate. Deployed environments are never mock, so real users always
 * get the Amplify flow.
 */
export default function AuthProvider({ children }: Props) {
  if (isMockAuth()) {
    return <MockAuthProvider>{children}</MockAuthProvider>
  }
  return <RealAuthProvider>{children}</RealAuthProvider>
}
