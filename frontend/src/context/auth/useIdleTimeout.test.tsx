import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useIdleTimeout } from '@/context/auth/useIdleTimeout'

describe('useIdleTimeout', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  test('calls onIdle after the timeout when there is no activity', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(onIdle, 1000, true))

    expect(onIdle).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(1000))
    expect(onIdle).toHaveBeenCalledTimes(1)
  })

  test('activity resets the countdown', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(onIdle, 1000, true))

    act(() => vi.advanceTimersByTime(700))
    act(() => window.dispatchEvent(new Event('keydown')))
    act(() => vi.advanceTimersByTime(700)) // 1400ms elapsed, but only 700ms since activity
    expect(onIdle).not.toHaveBeenCalled()

    act(() => vi.advanceTimersByTime(300)) // now 1000ms since the last activity
    expect(onIdle).toHaveBeenCalledTimes(1)
  })

  test('does nothing when disabled', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(onIdle, 1000, false))

    act(() => vi.advanceTimersByTime(5000))
    expect(onIdle).not.toHaveBeenCalled()
  })
})
