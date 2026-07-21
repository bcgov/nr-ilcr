import { renderHook } from '@testing-library/react'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'

describe('useMillYear', () => {
  test('provider supplies the 9050/2017 dev defaults', () => {
    const { result } = renderHook(() => useMillYear(), { wrapper: MillYearProvider })
    expect(result.current.millId).toBe(9050)
    expect(result.current.year).toBe(2017)
  })

  test('throws when used outside a provider', () => {
    expect(() => renderHook(() => useMillYear())).toThrow(/MillYearProvider/)
  })
})
