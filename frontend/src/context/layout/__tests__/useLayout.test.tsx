import { renderHook } from '@testing-library/react'
import useLayout from '@/context/layout/useLayout'

describe('useLayout', () => {
  test('throws a named error when used outside LayoutProvider', () => {
    // The guard is why every consumer can treat the context as non-optional. Without a test it is
    // the one line in the layout context that no suite executes.
    expect(() => renderHook(() => useLayout())).toThrow(
      'useLayout must be used inside LayoutProvider',
    )
  })
})
