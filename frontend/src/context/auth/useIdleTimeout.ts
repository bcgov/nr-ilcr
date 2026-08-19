import { useEffect, useRef } from 'react'

// User activity that keeps the session alive. Pointer/keyboard/scroll/touch cover both desktop and
// tablet; each resets the idle timer (a cheap clearTimeout + setTimeout).
const ACTIVITY_EVENTS = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart'] as const

/**
 * Sign the user out after {@code timeoutMs} of INACTIVITY (60-minute idle timeout). Amplify keeps the
 * access/id token refreshed while the user is active, so an active session never expires; this adds
 * the idle cap for shared-workstation safety. Only armed when {@code enabled} (an authenticated
 * session); any tracked activity resets the countdown.
 *
 * @param onIdle called once when the idle window elapses (e.g. sign out)
 * @param timeoutMs the inactivity window in milliseconds
 * @param enabled arm the timer only when true (e.g. authenticated)
 */
export function useIdleTimeout(onIdle: () => void, timeoutMs: number, enabled: boolean): void {
  // Keep the latest callback without re-arming the listeners each render.
  const onIdleRef = useRef(onIdle)
  onIdleRef.current = onIdle

  useEffect(() => {
    if (!enabled) {
      return undefined
    }
    let timer: ReturnType<typeof setTimeout>
    const reset = () => {
      clearTimeout(timer)
      timer = setTimeout(() => onIdleRef.current(), timeoutMs)
    }
    ACTIVITY_EVENTS.forEach((event) => window.addEventListener(event, reset, { passive: true }))
    reset()
    return () => {
      clearTimeout(timer)
      ACTIVITY_EVENTS.forEach((event) => window.removeEventListener(event, reset))
    }
  }, [enabled, timeoutMs])
}
