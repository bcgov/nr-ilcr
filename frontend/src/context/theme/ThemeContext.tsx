import { createContext } from 'react'

export type AppTheme = 'g10' | 'g100'

export type ThemeContextValue = {
  theme: AppTheme
  toggleTheme: () => void
}

export const ThemeContext = createContext<ThemeContextValue>({
  theme: 'g10',
  toggleTheme: () => undefined,
})
