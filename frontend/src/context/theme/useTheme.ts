import { use } from 'react'
import { ThemeContext } from './ThemeContext'

export default function useTheme() {
  return use(ThemeContext)
}
