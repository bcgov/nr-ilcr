import type { ReactNode } from 'react'
import AuthProvider from '@/context/auth/AuthProvider'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import ThemeProvider from '@/context/theme/ThemeProvider'

type Props = {
  children: ReactNode
}

export default function AppProviders({ children }: Props) {
  return (
    <ThemeProvider>
      <AuthProvider>
        <MillYearProvider>{children}</MillYearProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
