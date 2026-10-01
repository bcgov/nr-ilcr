import { act, renderHook } from '@testing-library/react'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'

describe('useMillYear', () => {
  afterEach(() => {
    window.localStorage.clear()
  })

  // No dev-default seed: a first-ever visit has NO working context, so Home shows its
  // "Select Mill" / "Select Reporting Year" placeholders instead of someone else's mill.
  test('provider starts with no context when nothing is stored', () => {
    const { result } = renderHook(() => useMillYear(), { wrapper: MillYearProvider })
    expect(result.current.millId).toBeNull()
    expect(result.current.year).toBeNull()
  })

  test('persists mill/year to local storage when context changes', () => {
    const { result } = renderHook(() => useMillYear(), { wrapper: MillYearProvider })

    act(() => {
      result.current.setContext(516, 2024)
    })

    expect(window.localStorage.getItem('ilcr:mill-year-context')).toBe(
      JSON.stringify({ millId: 516, year: 2024 }),
    )
  })

  test('restores mill/year from local storage on mount', () => {
    window.localStorage.setItem(
      'ilcr:mill-year-context',
      JSON.stringify({ millId: 516, year: 2024 }),
    )

    const { result } = renderHook(() => useMillYear(), { wrapper: MillYearProvider })
    expect(result.current.millId).toBe(516)
    expect(result.current.year).toBe(2024)
  })

  test('uses explicit initial context instead of stored context', () => {
    window.localStorage.setItem(
      'ilcr:mill-year-context',
      JSON.stringify({ millId: 516, year: 2024 }),
    )

    const { result } = renderHook(() => useMillYear(), {
      wrapper: ({ children }) => (
        <MillYearProvider initial={{ millId: 999, year: 2000 }}>{children}</MillYearProvider>
      ),
    })

    expect(result.current.millId).toBe(999)
    expect(result.current.year).toBe(2000)
  })

  test('throws when used outside a provider', () => {
    expect(() => renderHook(() => useMillYear())).toThrow(/MillYearProvider/)
  })
})
