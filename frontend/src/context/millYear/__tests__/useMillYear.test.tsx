import { renderHook } from '@testing-library/react'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'

describe('useMillYear', () => {
  test('provider supplies the 514/2021 defaults', () => {
    const { result } = renderHook(() => useMillYear(), { wrapper: MillYearProvider })
    expect(result.current.millId).toBe(514)
    expect(result.current.year).toBe(2021)
  })

  test('throws when used outside a provider', () => {
    expect(() => renderHook(() => useMillYear())).toThrow(/MillYearProvider/)
  })
})
