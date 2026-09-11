import type { IlcrRole } from '@/context/auth/mockUsers'
import { cleanup, render } from '@testing-library/react'
import { afterEach } from 'vitest'
import AppProviders from '@/app/AppProviders'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import { ILCR_ROLES, MOCK_USERS, MOCK_USER_STORAGE_KEY } from '@/context/auth/mockUsers'

afterEach(() => {
  cleanup()
  // Identity is global (localStorage), so it MUST NOT leak between tests: a suite that declares
  // `renderAsSubmitter` would otherwise hand the next test in the file a submitter it never asked
  // for, and the failure would look like a component bug. Cleared after unmount so the acting role
  // is either declared by the test or absent.
  clearActingRole()
})

// Every suite rendered through here expects a WORKING CONTEXT — schedule pages assert content, not
// the "Please Select Mill and Reporting Year" guard. That used to come from MillYearProvider's own
// 13050/2017 dev default inside AppProviders; now that the app starts with no context (so Home can
// show its Select Mill / Select Reporting Year placeholders), the test harness seeds it explicitly.
// Nested INSIDE AppProviders so it overrides the empty context for the tree under test, and a suite
// that needs a different (or deliberately empty) context still wins by rendering its own provider.
function customRender(ui: React.ReactElement, options = {}) {
  return render(ui, {
    wrapper: ({ children }) => (
      <AppProviders>
        <MillYearProvider initial={{ millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }}>
          {children}
        </MillYearProvider>
      </AppProviders>
    ),
    ...options,
  })
}

// ---- Acting identity (Story 16.3) ---------------------------------------------------------------
//
// `findMockUser(null)` falls through to `MOCK_USERS[0]` — the ADMIN (mockUsers.ts:38). So a suite
// that renders through `render()` alone silently acts as a Ministry Administrator and says nothing
// about it. That exact silent fallback ran the e2e suite as the wrong role for a month (Story 16.1
// completion notes), and it is why the role×status matrix shipped with no test that could tell a
// widened gate from a correct one.
//
// These helpers make the acting role a STATED part of the test. Seeding the storage key before
// render covers both consumers of the identity at once: `MockAuthProvider` reads it in its
// `useState` initialiser (MockAuthProvider.tsx:17), and `api-service` reads it per request to set
// `X-Mock-Groups` (api-service.ts:13) — so the browser identity and the wire identity always agree,
// the way they do in the app.
//
// This does NOT make the client decide anything: editability stays server-authoritative (AD-9), so
// a suite still has to serve an `editable` flag from MSW. The role declares WHO is acting; the
// document declares what they may do.
//
// WHAT THESE HELPERS DO NOT BUY — stated because the first version of this comment, and the twelve
// suites built on it, claimed more (review round, Story 16.3). A suite that forgets to declare a role
// still sends `X-Mock-Groups: ILCR_ADMIN`, because `api-service.mockUserGroups()` resolves the
// identity through the very same `findMockUser` fallback (api-service.ts:11-17). So:
//   * a SUBMITTER arm that loses its declaration fails loudly — it gets the admin and the
//     role-asserting suites report `expected 'ILCR_ADMIN' to be 'ILCR_SUBMITTER'`;
//   * an ADMIN arm that loses its declaration passes silently, because the fallback IS the admin.
// So do NOT describe the wire assertion as a guard against a forgotten declaration — it is not.
// `declaredRole()` below is: it reports what THIS test seeded, which is null when nothing declared a
// role, and that is the one thing a declared admin and a fallback admin do not share. An arm that
// asserts both says "this test declared admin" AND "the request carried admin". Failing that, the
// admin side rests on the paired submitter arms plus flipping the matrix itself.

/** What `renderAs*` seeded for the current test, or null when nothing declared a role. */
let declared: IlcrRole | null = null

/** The mock user holding `role` — there is exactly one per role (mockUsers.ts). */
function mockUserIdFor(role: IlcrRole): string {
  const user = MOCK_USERS.find((candidate) => candidate.roles.includes(role))
  if (!user) {
    throw new Error(`No mock user holds ${role}; MOCK_USERS and ILCR_ROLES have drifted apart.`)
  }
  return user.id
}

/**
 * Seed the acting identity. Deliberately NOT exported, and deliberately loud on failure.
 *
 * Not exported because the only safe moment to seed is before render — the provider reads the key
 * once, in its `useState` initialiser (`MockAuthProvider.tsx:17`) — and an exported seeder invites a
 * call after render, which would leave the BROWSER identity on the admin fallback while the WIRE
 * identity is the seeded role: precisely the disagreement these helpers exist to prevent. Going
 * through `renderAs` makes the ordering unrepresentable. (It was exported for suites that render
 * their own provider tree; all four such suites — Schedules 4, 8, 9, 10 — wrap `renderAs*` instead,
 * so the case never materialised.)
 *
 * Loud on failure, unlike its `clearActingRole` sibling, and the asymmetry is the point: clearing is
 * best-effort tidy-up, but if the SEED silently fails the test proceeds as `MOCK_USERS[0]` — the
 * admin — and a submitter arm quietly becomes an admin arm. A thrown error naming the role is the
 * only honest outcome.
 */
function seedActingRole(role: IlcrRole): void {
  try {
    window.localStorage.setItem(MOCK_USER_STORAGE_KEY, mockUserIdFor(role))
  } catch (cause) {
    throw new Error(
      `could not seed the acting role ${role}: localStorage is unavailable, so this test would ` +
        `silently run as ${MOCK_USERS[0].roles.join(',')} (the findMockUser fallback) instead`,
      { cause },
    )
  }
  declared = role
}

/**
 * The role THIS TEST declared — null if it rendered through bare `render()` and is therefore running
 * on the `MOCK_USERS[0]` admin fallback.
 *
 * This is the one assertion that closes the admin-direction hole. Asserting the `X-Mock-Groups`
 * header cannot: `api-service` resolves identity through the same fallback, so a declared admin and
 * an undeclared one are byte-identical on the wire (measured independently by four suites during the
 * Story 16.3 review round — dropping `renderAsAdmin` left every admin arm green). Asserting THIS
 * instead distinguishes them, because nothing is seeded when nothing was declared:
 *
 *     expect(declaredRole()).toBe(ILCR_ROLES.admin)
 *
 * Pair it with the wire assertion rather than replacing it — together they say "this test declared
 * admin" AND "the request actually carried admin", which is the full claim each arm's name makes.
 */
export function declaredRole(): IlcrRole | null {
  return declared
}

/** Drop the seeded identity. Called automatically after every test in this harness. */
export function clearActingRole(): void {
  // Reset FIRST and unconditionally: if the storage call throws, `declaredRole()` must still stop
  // reporting the previous test's role, or the next test inherits a declaration it never made — the
  // same leak in a different costume.
  declared = null
  try {
    window.localStorage.removeItem(MOCK_USER_STORAGE_KEY)
  } catch {
    // Storage can be unavailable in a locked-down environment; nothing to clear if so.
  }
}

/** Render as an explicitly named role, instead of inheriting the `MOCK_USERS[0]` admin fallback. */
export function renderAs(role: IlcrRole, ui: React.ReactElement, options = {}) {
  seedActingRole(role)
  return customRender(ui, options)
}

/** Render as `ILCR_ADMIN` — the ministry actor who may correct a Submitted track. */
export function renderAsAdmin(ui: React.ReactElement, options = {}) {
  return renderAs(ILCR_ROLES.admin, ui, options)
}

/** Render as `ILCR_SUBMITTER` — the licensee actor, read-only once the track leaves Draft. */
export function renderAsSubmitter(ui: React.ReactElement, options = {}) {
  return renderAs(ILCR_ROLES.submitter, ui, options)
}

export * from '@testing-library/react'
export { default as userEvent } from '@testing-library/user-event'
// override render export
export { customRender as render }
