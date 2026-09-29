import { describe, expect, test, vi } from 'vitest'
import { render, screen, waitFor } from '@/test-utils'

// The mirror of millAssociationsRoute.test.tsx for the reverse (user -> mill) leg. validateSearch
// is a pure function and is tested as one; it is the only thing between a garbage `millId` in the
// URL and a mill read that can only fail.
const routerSearch: { current: { millId?: number } } = { current: {} }
const navigateSpy = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: unknown) => options,
  getRouteApi: () => ({
    useSearch: () => routerSearch.current,
    useNavigate: () => navigateSpy,
  }),
}))

const carried: { current: number | undefined } = { current: undefined }
vi.mock('@/components/mills', () => ({
  default: ({ carriedMillId }: { carriedMillId?: number }) => {
    carried.current = carriedMillId
    return <p>mills page</p>
  },
}))

import { Route } from '@/routes/mills'

type Search = { millId?: number }
const validateSearch = (
  Route as unknown as { validateSearch: (search: Record<string, unknown>) => Search }
).validateSearch

const RouteComponent = (Route as unknown as { component: () => React.ReactElement }).component

describe('mills validateSearch (the UC-MILL-002 S01 hand-off channel)', () => {
  test('a positive whole-number millId passes through', () => {
    expect(validateSearch({ millId: 670 })).toEqual({ millId: 670 })
  })

  test('absent params yield an empty search — the shipped no-param path', () => {
    expect(validateSearch({})).toEqual({ millId: undefined })
  })

  test('a zero, negative, fractional, unsafe or non-number id is dropped rather than read', () => {
    // GET /v1/admin/mills/{millId} binds a `long`: each of these could only produce a failed
    // request and a banner the administrator did not ask for.
    expect(validateSearch({ millId: 0 }).millId).toBeUndefined()
    expect(validateSearch({ millId: -1 }).millId).toBeUndefined()
    expect(validateSearch({ millId: 1.5 }).millId).toBeUndefined()
    expect(validateSearch({ millId: Number.MAX_SAFE_INTEGER + 1 }).millId).toBeUndefined()
    expect(validateSearch({ millId: Number.NaN }).millId).toBeUndefined()
    expect(validateSearch({ millId: '670' }).millId).toBeUndefined()
    expect(validateSearch({ millId: null }).millId).toBeUndefined()
  })

  test('unrelated params are not carried through — only millId is whitelisted', () => {
    expect(validateSearch({ millId: 670, userGuid: 'A'.repeat(32) })).toEqual({ millId: 670 })
  })
})

describe('mills route — consume and clear (UC-MILL-002 BR-02)', () => {
  test('a carried id reaches the page and is stripped from the URL', async () => {
    routerSearch.current = { millId: 670 }
    navigateSpy.mockReset()
    carried.current = undefined

    render(<RouteComponent />)

    expect(carried.current).toBe(670)
    // `replace`, so Back returns to the Users page rather than re-entering this one with the param
    // still on it.
    await waitFor(() => expect(navigateSpy).toHaveBeenCalledWith({ search: {}, replace: true }))
  })

  test('the carried value survives the clear that follows it', async () => {
    routerSearch.current = { millId: 670 }
    navigateSpy.mockReset()
    carried.current = undefined

    render(<RouteComponent />)
    await waitFor(() => expect(navigateSpy).toHaveBeenCalled())

    routerSearch.current = {}
    expect(carried.current).toBe(670)
  })

  test('with no param the route navigates nowhere and hands the page nothing', async () => {
    routerSearch.current = {}
    navigateSpy.mockReset()
    carried.current = 670

    render(<RouteComponent />)
    await screen.findByText('mills page')

    expect(carried.current).toBeUndefined()
    expect(navigateSpy).not.toHaveBeenCalled()
  })
})
