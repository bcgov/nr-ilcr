import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useIdleTimeout } from '@/context/auth/useIdleTimeout'

const STORAGE_KEY = 'nr-ilcr.last-activity'

describe('useIdleTimeout', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: 0 })
    localStorage.clear()
  })
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

  test('activity in another tab keeps this tab signed in', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(onIdle, 1000, true))

    // Another tab of the same session sees activity and pushes the shared stamp forward. This tab
    // never fired an activity event of its own, but must honour the shared stamp and not sign out.
    act(() => vi.advanceTimersByTime(700))
    act(() => localStorage.setItem(STORAGE_KEY, String(Date.now())))

    act(() => vi.advanceTimersByTime(400)) // this tab's original 1000ms deadline passes
    expect(onIdle).not.toHaveBeenCalled()

    act(() => vi.advanceTimersByTime(600)) // 1000ms since the cross-tab activity
    expect(onIdle).toHaveBeenCalledTimes(1)
  })

  test('signs out when the shared stamp is already older than the window (e.g. after sleep)', () => {
    const onIdle = vi.fn()
    // Simulate a machine that slept: a stale stamp is present and wall-clock has moved well past the
    // window by the time the hook first checks. The elapsed comparison — not a stalled timer — decides.
    vi.setSystemTime(5000)
    localStorage.setItem(STORAGE_KEY, '0')

    renderHook(() => useIdleTimeout(onIdle, 1000, true))
    // markActive on mount refreshes the stamp, so mount alone doesn't sign out...
    expect(onIdle).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(1000))
    expect(onIdle).toHaveBeenCalledTimes(1)
  })

  test('does nothing when disabled', () => {
    const onIdle = vi.fn()
    renderHook(() => useIdleTimeout(onIdle, 1000, false))

    act(() => vi.advanceTimersByTime(5000))
    expect(onIdle).not.toHaveBeenCalled()
  })
})
