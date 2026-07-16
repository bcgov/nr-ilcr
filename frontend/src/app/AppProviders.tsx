import type { ReactNode } from 'react'
import MockAuthProvider from '@/context/auth/MockAuthProvider'
import ThemeProvider from '@/context/theme/ThemeProvider'

type Props = {
  children: ReactNode
}

export default function AppProviders({ children }: Props) {
  return (
    <ThemeProvider>
      <MockAuthProvider>{children}</MockAuthProvider>
    </ThemeProvider>
  )
}
