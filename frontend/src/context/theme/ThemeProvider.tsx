import { Theme as CarbonTheme } from '@carbon/react'
import type { ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'
import { type AppTheme, ThemeContext } from './ThemeContext'

const THEME_STORAGE_KEY = 'nr-ilcr.theme'

function getInitialTheme(): AppTheme {
  if (typeof window === 'undefined') {
    return 'g10'
  }

  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    return storedTheme === 'g100' ? 'g100' : 'g10'
  } catch {
    return 'g10'
  }
}

export default function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<AppTheme>(getInitialTheme)

  useEffect(() => {
    document.documentElement.dataset.carbonTheme = theme
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme)
    } catch {
      // Storage can be disabled; theme still applies for the active session.
    }
  }, [theme])

  const value = useMemo(
    () => ({
      theme,
      toggleTheme: () => setTheme((currentTheme) => (currentTheme === 'g10' ? 'g100' : 'g10')),
    }),
    [theme],
  )

  return (
    <ThemeContext value={value}>
      <CarbonTheme theme={theme}>{children}</CarbonTheme>
    </ThemeContext>
  )
}
