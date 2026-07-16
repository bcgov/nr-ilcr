import { AsleepFilled, LightFilled } from '@carbon/icons-react'
import { HeaderGlobalAction } from '@carbon/react'
import useTheme from '@/context/theme/useTheme'

export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'g100'

  return (
    <HeaderGlobalAction
      aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      tooltipAlignment="end"
      onClick={toggleTheme}
    >
      {isDark ? <AsleepFilled size={20} /> : <LightFilled size={20} />}
    </HeaderGlobalAction>
  )
}
