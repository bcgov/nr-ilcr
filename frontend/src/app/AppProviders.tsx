import type { ReactNode } from 'react'
import MockAuthProvider from '@/context/auth/MockAuthProvider'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import ThemeProvider from '@/context/theme/ThemeProvider'

type Props = {
  children: ReactNode
}

export default function AppProviders({ children }: Props) {
  return (
    <ThemeProvider>
      <MockAuthProvider>
        <MillYearProvider>{children}</MillYearProvider>
      </MockAuthProvider>
    </ThemeProvider>
  )
}
