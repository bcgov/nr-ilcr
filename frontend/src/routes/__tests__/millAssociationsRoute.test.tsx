import { describe, expect, test, vi } from 'vitest'
import { render, screen, waitFor } from '@/test-utils'

// The real createFileRoute demands a generated route tree; capturing the options object is all the
// validateSearch half needs — it is a pure function and is tested as one. It is the only thing
// between a garbage `userGuid` in the URL and a directory lookup that can only fail, so the
// whitelist is load-bearing rather than defensive (Story 22.3 D7).
const routerSearch: { current: { userGuid?: string } } = { current: {} }
const navigateSpy = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: unknown) => options,
  getRouteApi: () => ({
    useSearch: () => routerSearch.current,
    useNavigate: () => navigateSpy,
  }),
}))

// Records what the page was handed, so the hand-off can be asserted without standing up the whole
// users screen and its directory lookup.
const carried: { current: string | undefined } = { current: undefined }
vi.mock('@/components/millAssociations', () => ({
  default: ({ carriedUserGuid }: { carriedUserGuid?: string }) => {
    carried.current = carriedUserGuid
    return <p>users page</p>
  },
}))

import { Route } from '@/routes/mill-associations'

type Search = { userGuid?: string }
const validateSearch = (
  Route as unknown as { validateSearch: (search: Record<string, unknown>) => Search }
).validateSearch

const RouteComponent = (Route as unknown as { component: () => React.ReactElement }).component

const GUID = 'A'.repeat(32)

describe('mill-associations validateSearch (the S10 hand-off channel)', () => {
  test('an exactly-32-character userGuid passes through', () => {
    expect(validateSearch({ userGuid: GUID })).toEqual({ userGuid: GUID })
  })

  test('absent params yield an empty search — the shipped no-param path', () => {
    expect(validateSearch({})).toEqual({ userGuid: undefined })
  })

  test('a truncated, padded, blank or non-string guid is dropped rather than looked up', () => {
    // The lookup's GUID criterion is an EXACT 32-character match (@Size(min = 32, max = 32)), so
    // every one of these could only produce a failed request and a banner the administrator did
    // not ask for. They land the page in its ordinary no-selection state instead.
    expect(validateSearch({ userGuid: 'A'.repeat(31) }).userGuid).toBeUndefined()
    expect(validateSearch({ userGuid: 'A'.repeat(33) }).userGuid).toBeUndefined()
    expect(validateSearch({ userGuid: '' }).userGuid).toBeUndefined()
    expect(validateSearch({ userGuid: 12345 }).userGuid).toBeUndefined()
    expect(validateSearch({ userGuid: null }).userGuid).toBeUndefined()
  })

  test('unrelated params are not carried through — only userGuid is whitelisted', () => {
    expect(validateSearch({ userGuid: GUID, millId: 670 })).toEqual({ userGuid: GUID })
  })

  test('32 characters of the wrong ALPHABET are dropped — the whitelist is hex, not a ruler', () => {
    // Length alone would wave these through to a lookup that can only miss, producing exactly the
    // unrequested error banner the whitelist exists to prevent. GUIDs on this surface are 32 hex
    // characters, either case.
    expect(validateSearch({ userGuid: '!'.repeat(32) }).userGuid).toBeUndefined()
    expect(validateSearch({ userGuid: ' '.repeat(32) }).userGuid).toBeUndefined()
    expect(validateSearch({ userGuid: 'Z'.repeat(32) }).userGuid).toBeUndefined()
    expect(validateSearch({ userGuid: 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6' })).toEqual({
      userGuid: 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6',
    })
  })
})

describe('mill-associations route — consume and clear (AC8)', () => {
  test('a carried guid reaches the page and is stripped from the URL', async () => {
    routerSearch.current = { userGuid: GUID }
    navigateSpy.mockReset()
    carried.current = undefined

    render(<RouteComponent />)

    expect(carried.current).toBe(GUID)
    // CONSUMED: a later open of this page must not reuse a stale selection (UC-MILL-002 BR-02).
    // `replace`, so Back returns to the Mills page rather than re-entering this one with the param
    // still on it and re-selecting a user the administrator has already navigated away from.
    await waitFor(() => expect(navigateSpy).toHaveBeenCalledWith({ search: {}, replace: true }))
  })

  test('the carried value survives the clear that follows it', async () => {
    routerSearch.current = { userGuid: GUID }
    navigateSpy.mockReset()
    carried.current = undefined

    render(<RouteComponent />)
    await waitFor(() => expect(navigateSpy).toHaveBeenCalled())

    // Captured at first render, never re-read: a prop that followed the URL would hand the page an
    // undefined the instant the param was stripped, and the selection would never happen.
    routerSearch.current = {}
    expect(carried.current).toBe(GUID)
  })

  test('with no param the route navigates nowhere and hands the page nothing', async () => {
    routerSearch.current = {}
    navigateSpy.mockReset()
    carried.current = GUID

    render(<RouteComponent />)
    await screen.findByText('users page')

    // Inert by construction: the shipped page's whole mount behaviour is a contract with ~30 tests
    // behind it, and a stray navigate on every open would be a new one.
    expect(carried.current).toBeUndefined()
    expect(navigateSpy).not.toHaveBeenCalled()
  })
})
