import { beforeEach, describe, expect, test } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getDefaultNormalizer } from '@testing-library/react'
import { render, screen, userEvent, waitFor, within } from '@/test-utils'
import { server } from '@/test-setup'
import MillAssociations from '../index'

const API = 'http://localhost:3000/api'
const LOOKUP = `${API}/v1/users/lookup`
const MILLS = `${API}/v1/mills`

const GUID = 'A'.repeat(32)
const OTHER_GUID = 'B'.repeat(32)

const ADA = {
  userGuid: GUID,
  displayName: 'Ada Lovelace',
  idpUsername: 'ALOVELAC',
  identityProvider: 'IDIR',
}

const MILL_LIST = [
  { millId: 670, millNumber: '670', millName: 'Cedar Mill', millStatusCode: 'ACT' },
  { millId: 671, millNumber: '671', millName: 'Closed Mill', millStatusCode: 'CLS' },
  { millId: 672, millNumber: '672', millName: 'Spruce Mill', millStatusCode: 'ACT' },
]

const activeOn670 = {
  userGuid: GUID,
  millId: 670,
  millNumber: '670',
  millName: 'Cedar Mill',
  status: 'ACTIVE' as const,
  activeDate: '2026-03-04',
  revisionCount: 7,
}

const endedOn671 = {
  userGuid: GUID,
  millId: 671,
  millNumber: '671',
  millName: 'Closed Mill',
  status: 'ENDED' as const,
  inactiveDate: '2025-11-30',
  revisionCount: 2,
}

// Preserves the literal whitespace the backend sends so the verbatim-rendering assertions (AD-8)
// are not defeated by the default whitespace collapse.
const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

/**
 * Serves the assignment list from a live array. The page re-reads after every successful write
 * rather than splicing the returned row in, so a write test has to move this array the way the
 * database would — a static fixture would answer the second read with the pre-write state.
 */
const assignmentsFrom = (rows: unknown[]) => {
  const seen: URLSearchParams[] = []
  server.use(
    http.get(`${API}/v1/submitters/:userGuid/mills`, ({ request }) => {
      seen.push(new URL(request.url).searchParams)
      return HttpResponse.json(rows)
    }),
  )
  return seen
}

const assignmentsAre = (...rows: unknown[]) => assignmentsFrom(rows)

beforeEach(() => {
  server.use(
    http.get(MILLS, () => HttpResponse.json(MILL_LIST)),
    http.get(LOOKUP, () => HttpResponse.json([ADA])),
  )
})

/** Search the directory and choose Ada — the entry point for everything else on this screen. */
const selectAda = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.type(screen.getByRole('combobox', { name: /user id/i }), 'AL')
  await user.click(await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }))
}

const assignmentsTable = () => screen.getByRole('table', { name: /associated mills/i })

const rowFor = (millNumber: string) =>
  within(assignmentsTable())
    .getAllByRole('row')
    .find((row) => within(row).queryByText(millNumber))!

describe('Users page — the screen itself', () => {
  test('the assignments panel appears only once a user has been chosen', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670)
    render(<MillAssociations />)

    // Legacy renders the Associated Mills panel on `#{usersMB.userSelected}` alone (users.xhtml:71).
    expect(screen.queryByRole('table', { name: /associated mills/i })).not.toBeInTheDocument()

    await selectAda(user)

    expect(await screen.findByRole('table', { name: /associated mills/i })).toBeInTheDocument()
  })

  test('assignments are requested with includeEnded=true, or every ended row AC2 asks for is hidden', async () => {
    const user = userEvent.setup()
    const seen = assignmentsAre(activeOn670, endedOn671)
    render(<MillAssociations />)

    await selectAda(user)

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0].get('includeEnded')).toBe('true')
  })

  test('the five legacy columns render, with the legacy Active/Inactive wording', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670, endedOn671)
    render(<MillAssociations />)
    await selectAda(user)

    const headers = within(await screen.findByRole('table', { name: /associated mills/i }))
      .getAllByRole('columnheader')
      .map((cell) => cell.textContent)
    expect(headers.slice(0, 5)).toEqual([
      'Mill #',
      'Mill Name',
      'User To Mill Status',
      'Activation Date',
      'Deactivation Date',
    ])

    // users.xhtml:64-66 renders literally "Active" / "Inactive"; the wire value stays ENDED.
    expect(within(rowFor('670')).getByText('Active')).toBeInTheDocument()
    expect(within(rowFor('671')).getByText('Inactive')).toBeInTheDocument()
    expect(within(rowFor('670')).getByText('2026-03-04')).toBeInTheDocument()
    expect(within(rowFor('671')).getByText('2025-11-30')).toBeInTheDocument()
  })

  test('a mill that no longer resolves renders as a dash rather than blanking the row', async () => {
    const user = userEvent.setup()
    // millNumber/millName are ABSENT, not null: Jackson non_null drops them (application.yml:5).
    assignmentsAre({
      userGuid: GUID,
      millId: 999,
      status: 'ACTIVE',
      activeDate: '2026-01-02',
      revisionCount: 1,
    })
    render(<MillAssociations />)
    await selectAda(user)

    const cells = within(await screen.findByRole('table', { name: /associated mills/i }))
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
    expect(cells[0]).toBe('—')
    expect(cells[1]).toBe('—')
    // The row still carries its dates and its action, so the assignment stays endable.
    expect(cells[3]).toBe('2026-01-02')
  })

  test('server order is preserved — the table never re-sorts what the backend pinned', async () => {
    const user = userEvent.setup()
    // Deliberately not ascending by mill number: findByUser pins ascending MILL ID, and 672 here
    // precedes 671. A client-side sort would reorder these two and disagree with the backend.
    assignmentsAre(
      { ...activeOn670, millId: 672, millNumber: '672', millName: 'Spruce Mill' },
      endedOn671,
    )
    render(<MillAssociations />)
    await selectAda(user)

    const order = within(await screen.findByRole('table', { name: /associated mills/i }))
      .getAllByRole('row')
      .slice(1)
      .map((row) => within(row).getAllByRole('cell')[0].textContent)
    expect(order).toEqual(['672', '671'])
  })
})

describe('Users page — display name (AC2)', () => {
  test('the name comes from the directory result, since the assignment never carries one', async () => {
    const user = userEvent.setup()
    // displayName is absent on every assignment row the service produces (AssignmentService:285).
    assignmentsAre(activeOn670)
    render(<MillAssociations />)

    await selectAda(user)

    expect(await screen.findByText(/Ada Lovelace/)).toBeInTheDocument()
  })

  test('a directory candidate with no name falls back to the GUID, never to a blank', async () => {
    const user = userEvent.setup()
    server.use(http.get(LOOKUP, () => HttpResponse.json([{ ...ADA, displayName: null }])))
    assignmentsAre(activeOn670)
    render(<MillAssociations />)

    await user.type(screen.getByRole('combobox', { name: /user id/i }), 'AL')
    await user.click(await screen.findByRole('option', { name: 'ALOVELAC' }))

    expect(await screen.findByText(GUID)).toBeInTheDocument()
  })
})

describe('Users page — assign and reactivate (AC3)', () => {
  test('adding a mill posts only the GUID and renders the backend sentence verbatim', async () => {
    const user = userEvent.setup()
    const rows: unknown[] = [activeOn670]
    assignmentsFrom(rows)
    let body: unknown
    const message = 'Mill 672 - Spruce Mill has been activated for user AAAA -  .'
    const assigned = { ...activeOn670, millId: 672, millNumber: '672', millName: 'Spruce Mill' }
    server.use(
      http.post(`${API}/v1/mills/:millId/submitters`, async ({ request, params }) => {
        body = await request.json()
        expect(params.millId).toBe('672')
        rows.push(assigned)
        return HttpResponse.json({
          assignment: assigned,
          messageKey: 'user.activate.mill',
          message,
        })
      }),
    )
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(await screen.findByRole('combobox', { name: /mill/i }))
    await user.click(await screen.findByRole('option', { name: /672 - Spruce Mill/ }))
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    // Rebuilding this sentence client-side is what AD-8 forbids; an argument swap must fail here.
    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()
    expect(body).toEqual({ userGuid: GUID })
    expect(within(assignmentsTable()).getByText('672')).toBeInTheDocument()
  })

  test('re-adding an already-assigned mill is a WARNING that changes nothing, not an error', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670)
    const message = 'User AAAA is already associated to mill Cedar Mill. Please verify.'
    server.use(
      http.post(`${API}/v1/mills/:millId/submitters`, () =>
        HttpResponse.json({
          assignment: activeOn670,
          messageKey: 'user.not.associated.to.mill',
          message,
        }),
      ),
    )
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(await screen.findByRole('combobox', { name: /mill/i }))
    await user.click(await screen.findByRole('option', { name: /670 - Cedar Mill/ }))
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    const notice = await screen.findByText(message, { normalizer: verbatim })
    // The key is the semantic opposite of its text: it fires when the user IS already associated.
    expect(notice.closest('.cds--inline-notification')).toHaveClass(
      'cds--inline-notification--warning',
    )
  })

  test('reviving an assignment on a closed mill is refused, and says so verbatim', async () => {
    const user = userEvent.setup()
    assignmentsAre(endedOn671)
    const detail = 'You must activate the mill before activating any users.'
    server.use(http.post(`${API}/v1/mills/:millId/submitters`, () => problemBody(409, detail)))
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(within(rowFor('671')).getByRole('button', { name: /activate/i }))

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    // Refused means unchanged: the row must not have been optimistically flipped.
    expect(within(rowFor('671')).getByText('Inactive')).toBeInTheDocument()
  })
})

describe('Users page — ending an assignment (AC4)', () => {
  test('End sends the row own revisionCount and renders the backend sentence verbatim', async () => {
    const user = userEvent.setup()
    const rows: unknown[] = [activeOn670]
    assignmentsFrom(rows)
    let body: unknown
    const message = 'Mill 670 - Cedar Mill has been deactivated for user AAAA -  .'
    const ended = {
      ...activeOn670,
      status: 'ENDED',
      activeDate: null,
      inactiveDate: '2026-08-26',
      revisionCount: 8,
    }
    server.use(
      http.patch(`${API}/v1/mills/:millId/submitters/:userGuid`, async ({ request, params }) => {
        body = await request.json()
        expect(params.userGuid).toBe(GUID)
        rows[0] = ended
        return HttpResponse.json({
          assignment: ended,
          messageKey: 'user.deactivate.mill',
          message,
        })
      }),
    )
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(within(rowFor('670')).getByRole('button', { name: /deactivate/i }))

    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()
    expect(body).toEqual({ revisionCount: 7 })
    expect(within(rowFor('670')).getByText('Inactive')).toBeInTheDocument()
  })

  test('legacy has NO confirmation step here — the click itself ends the assignment (ALT-001)', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670)
    let called = false
    server.use(
      http.patch(`${API}/v1/mills/:millId/submitters/:userGuid`, () => {
        called = true
        return HttpResponse.json({
          assignment: {
            ...activeOn670,
            status: 'ENDED',
            activeDate: null,
            inactiveDate: '2026-08-26',
          },
          messageKey: 'user.deactivate.mill',
          message: 'done',
        })
      }),
    )
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(within(rowFor('670')).getByRole('button', { name: /deactivate/i }))

    await waitFor(() => expect(called).toBe(true))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('a 409 re-reads the list and surfaces the conflict instead of retrying blindly', async () => {
    const user = userEvent.setup()
    let listCalls = 0
    server.use(
      http.get(`${API}/v1/submitters/:userGuid/mills`, () => {
        listCalls += 1
        // The second read shows what actually happened: someone else already ended it.
        return HttpResponse.json([
          listCalls === 1
            ? activeOn670
            : { ...activeOn670, status: 'ENDED', activeDate: null, inactiveDate: '2026-08-25' },
        ])
      }),
      http.patch(`${API}/v1/mills/:millId/submitters/:userGuid`, () =>
        problemBody(409, 'This record was changed by another user. Reload and try again.'),
      ),
    )
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(within(rowFor('670')).getByRole('button', { name: /deactivate/i }))

    expect(
      await screen.findByText(/changed by another user/i, { normalizer: verbatim }),
    ).toBeInTheDocument()
    await waitFor(() => expect(listCalls).toBe(2))
    expect(within(rowFor('670')).getByText('Inactive')).toBeInTheDocument()
  })

  test('an ended row offers Activate and never Deactivate (STA-002)', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670, endedOn671)
    render(<MillAssociations />)
    await selectAda(user)

    await screen.findByRole('table', { name: /associated mills/i })
    expect(within(rowFor('671')).getByRole('button', { name: /activate/i })).toBeInTheDocument()
    expect(
      within(rowFor('671')).queryByRole('button', { name: /deactivate/i }),
    ).not.toBeInTheDocument()
    expect(within(rowFor('670')).getByRole('button', { name: /deactivate/i })).toBeInTheDocument()
    expect(
      within(rowFor('670')).queryByRole('button', { name: /^activate$/i }),
    ).not.toBeInTheDocument()
  })
})

describe('Users page — the account flag (AC5)', () => {
  test('activating renders the backend sentence verbatim and swaps the control (STA-001)', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670)
    const message = 'User AAAA -   has been activated.'
    server.use(
      http.patch(`${API}/v1/submitters/:userGuid`, async ({ request }) => {
        expect(await request.json()).toEqual({ active: true })
        return HttpResponse.json({
          account: { userGuid: GUID, activeInd: 'Y', roleName: 'LICENSEE', revisionCount: 4 },
          messageKey: 'user.activated',
          message,
        })
      }),
    )
    render(<MillAssociations />)
    await selectAda(user)

    const account = screen.getByRole('table', { name: /user details/i })
    await user.click(within(account).getByRole('button', { name: /^activate account$/i }))

    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()
    // Mutually exclusive: an active account offers only deactivation.
    expect(
      within(account).getByRole('button', { name: /^deactivate account$/i }),
    ).toBeInTheDocument()
    expect(
      within(account).queryByRole('button', { name: /^activate account$/i }),
    ).not.toBeInTheDocument()
    expect(within(account).getByText('Y')).toBeInTheDocument()
  })

  test('ERR-002 renders verbatim and ABOVE the assignments table, as its own text promises', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670)
    const detail =
      "User AAAA -   has an 'Active' 'User To Mill Status' on one or more 'Associated Mills' listed below. All 'User To Mill Status' must be set to 'Inactive' before deactivation of this user is permitted."
    server.use(http.patch(`${API}/v1/submitters/:userGuid`, () => problemBody(409, detail)))
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(screen.getByRole('button', { name: /^deactivate account$/i }))

    const notice = await screen.findByText(detail, { normalizer: verbatim })
    // "listed below" is only true if it is rendered above the table it refers to.
    expect(notice.compareDocumentPosition(assignmentsTable())).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
  })

  test('deactivating a never-provisioned account says not-found, and leaves the flag alone', async () => {
    const user = userEvent.setup()
    assignmentsAre()
    const detail = 'The requested user account could not be found.'
    server.use(http.patch(`${API}/v1/submitters/:userGuid`, () => problemBody(404, detail)))
    render(<MillAssociations />)
    await selectAda(user)

    await user.click(screen.getByRole('button', { name: /^deactivate account$/i }))

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    // Nothing is known about the flag, so nothing may be claimed about it. Columns are
    // [User ID, Name, Role, Active, Actions]; a rejected write must leave Active unresolved.
    const cells = within(screen.getByRole('table', { name: /user details/i })).getAllByRole('cell')
    expect(cells[3].textContent).toBe('—')
  })
})

describe('Users page — resilience (AC6)', () => {
  test('a directory outage is reported but leaves the assignments view fully working', async () => {
    const user = userEvent.setup()
    assignmentsAre(activeOn670)
    render(<MillAssociations />)
    await selectAda(user)
    await screen.findByRole('table', { name: /associated mills/i })

    const detail = 'The user directory is currently unavailable. Please try again later.'
    server.use(http.get(LOOKUP, () => problemBody(502, detail)))
    const combo = screen.getByRole('combobox', { name: /user id/i })
    await user.clear(combo)
    await user.type(combo, 'ZZ')

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    // The two surfaces share no state: the already-loaded assignments and their actions survive.
    expect(within(rowFor('670')).getByRole('button', { name: /deactivate/i })).toBeEnabled()
  })

  test('a failed assignment load surfaces the API text rather than an empty table', async () => {
    const user = userEvent.setup()
    const detail = 'The requested mill assignment could not be found.'
    server.use(http.get(`${API}/v1/submitters/:userGuid/mills`, () => problemBody(404, detail)))
    render(<MillAssociations />)

    await selectAda(user)

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
  })

  test('a response for a user the admin has already moved off does not land', async () => {
    const user = userEvent.setup()
    server.use(
      http.get(LOOKUP, () =>
        HttpResponse.json([
          ADA,
          { ...ADA, userGuid: OTHER_GUID, displayName: 'Grace Hopper', idpUsername: 'GHOPPER' },
        ]),
      ),
      http.get(`${API}/v1/submitters/:userGuid/mills`, async ({ params }) => {
        if (params.userGuid === GUID) {
          await new Promise((resolve) => setTimeout(resolve, 200))
          return HttpResponse.json([activeOn670])
        }
        return HttpResponse.json([
          {
            ...activeOn670,
            userGuid: OTHER_GUID,
            millId: 672,
            millNumber: '672',
            millName: 'Spruce Mill',
          },
        ])
      }),
    )
    render(<MillAssociations />)

    await selectAda(user)
    const combo = screen.getByRole('combobox', { name: /user id/i })
    await user.clear(combo)
    await user.type(combo, 'GH')
    await user.click(await screen.findByRole('option', { name: 'Grace Hopper (GHOPPER)' }))

    expect(await screen.findByText('672')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 400))
    // Ada's slower list must never repaint Grace's assignments.
    expect(within(assignmentsTable()).queryByText('670')).not.toBeInTheDocument()
  })
})
