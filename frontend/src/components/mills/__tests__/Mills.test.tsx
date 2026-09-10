import { beforeEach, describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { act, getDefaultNormalizer } from '@testing-library/react'
import { render, screen, userEvent, waitFor, within } from '@/test-utils'
import { server } from '@/test-setup'
import Mills from '../index'

const navigateSpy = vi.fn()
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateSpy,
}))

const API = 'http://localhost:3000/api'
const ADMIN_MILLS = `${API}/v1/admin/mills`
const IMPORTABLE = `${ADMIN_MILLS}/importable`
const LOOKUP = `${API}/v1/users/lookup`
const MESSAGES = `${API}/v1/messages`

const GUID = 'A'.repeat(32)
const OTHER_GUID = 'B'.repeat(32)

const CEDAR = {
  millId: 670,
  revisionCount: 3,
  millNumber: '670',
  millName: 'Cedar Mill',
  millStatusCode: 'ACT',
  statusDescription: 'Active',
  headOfficeContactInd: 'Y',
  headOfficeContactId: 11,
}

const CLOSED = {
  millId: 671,
  revisionCount: 1,
  millNumber: '671',
  millName: 'Closed Mill',
  millStatusCode: 'CLS',
  // The delivery code table's own description, which really is "Close" (deviation (G)).
  statusDescription: 'Close',
  headOfficeContactInd: 'N',
}

const CONTACTS = [
  { clientContactId: 11, contactName: 'Ada Lovelace' },
  { clientContactId: 12, contactName: 'Grace Hopper' },
]

const ACTIVE_ROW = {
  userGuid: GUID,
  millId: 670,
  millNumber: '670',
  millName: 'Cedar Mill',
  status: 'ACTIVE' as const,
  activeDate: '2026-03-04',
  revisionCount: 7,
}

const ENDED_ROW = {
  userGuid: OTHER_GUID,
  millId: 670,
  millNumber: '670',
  millName: 'Cedar Mill',
  status: 'ENDED' as const,
  inactiveDate: '2025-11-30',
  revisionCount: 2,
}

/** Never activated: absent activeDate AND a revision still at 0 (22.2 deviation (E)). */
const NEVER_ACTIVATED_ROW = {
  userGuid: OTHER_GUID,
  millId: 670,
  millNumber: '670',
  millName: 'Cedar Mill',
  status: 'ENDED' as const,
  // Really its CREATION date — the add writes INACTIVE_DATE = now — not an end date.
  inactiveDate: '2026-09-01',
  revisionCount: 0,
}

const ADA = {
  userGuid: GUID,
  displayName: 'Ada Lovelace',
  idpUsername: 'ALOVELAC',
  identityProvider: 'IDIR',
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
 * Deterministic settle: a fixed number of macrotask turns, not a wall-clock sleep — enough for a
 * released response's promise chain to run to completion without racing a loaded runner (the
 * PR #276 settle-sleep lesson). Fake timers are abandoned project policy for MSW-backed suites.
 */
const drainEventLoop = async (turns = 20) => {
  for (let i = 0; i < turns; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

const searchAnswers = (body: unknown, status = 200) => {
  const seen: URLSearchParams[] = []
  server.use(
    http.get(ADMIN_MILLS, ({ request }) => {
      seen.push(new URL(request.url).searchParams)
      return status === 200
        ? HttpResponse.json(body)
        : problemBody(status, body as unknown as string)
    }),
  )
  return seen
}

/**
 * Serves the association list from a LIVE array. The page re-reads after every successful write
 * rather than splicing the returned row in, so a write test has to move this array the way the
 * database would — a static fixture answers the second read with the pre-write state.
 */
const usersFrom = (rows: unknown[]) => {
  const seen: URLSearchParams[] = []
  server.use(
    http.get(`${ADMIN_MILLS}/:millId/users`, ({ request }) => {
      seen.push(new URL(request.url).searchParams)
      return HttpResponse.json(rows)
    }),
  )
  return seen
}

const usersAre = (...rows: unknown[]) => usersFrom(rows)

beforeEach(() => {
  navigateSpy.mockReset()
  server.use(
    // Registered BEFORE the `:millId` pattern, which would otherwise swallow "importable".
    http.get(IMPORTABLE, () => HttpResponse.json([])),
    http.get(ADMIN_MILLS, () => HttpResponse.json({ results: [CEDAR] })),
    http.get(`${ADMIN_MILLS}/:millId/contact-options`, () => HttpResponse.json(CONTACTS)),
    http.get(`${ADMIN_MILLS}/:millId/users`, () => HttpResponse.json([])),
    http.get(`${ADMIN_MILLS}/:millId`, () => HttpResponse.json(CEDAR)),
    http.get(LOOKUP, () => HttpResponse.json([ADA])),
    http.get(MESSAGES, () =>
      HttpResponse.json({
        key: 'confirmImportMill',
        text: 'The mill will be imported into ILCR. Are you sure you would like to continue?',
      }),
    ),
  )
})

const selectMillDialog = () => screen.getByRole('dialog', { name: /find and select mill$/i })

const importDialog = () => screen.getByRole('dialog', { name: /find and select mill to import/i })

const detailPanel = () => screen.getByRole('region', { name: /mill details/i })

const usersTable = () => screen.getByRole('table', { name: /associated licensee user/i })

const rowFor = (guid: string) =>
  within(usersTable())
    .getAllByRole('row')
    .find((row) => within(row).queryByText(guid))!

/** Open Select Mill, search, and choose Cedar — the entry point for everything below. */
const selectCedar = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(screen.getByRole('button', { name: 'Select Mill' }))
  await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
  await user.click(await screen.findByRole('button', { name: 'Select mill 670' }))
  await screen.findByRole('region', { name: /mill details/i })
}

describe('Mills page — route, empty state (AC1)', () => {
  test('the empty state offers exactly two controls, and no panel at all', async () => {
    render(<Mills />)

    expect(await screen.findByRole('button', { name: 'Select Mill' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import Mill' })).toBeInTheDocument()

    // ABSENT, not disabled: legacy gates all three panels on `rendered="#{millsMB.millSelected}"`
    // (mills.xhtml:28, :110), so there is nothing to tab into before a mill is chosen.
    expect(screen.queryByRole('region', { name: /mill details/i })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('table', { name: /associated licensee user/i }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Change Mill' })).not.toBeInTheDocument()
  })

  test('nothing is requested until the administrator asks for it', async () => {
    let searches = 0
    server.use(
      http.get(ADMIN_MILLS, () => {
        searches += 1
        return HttpResponse.json({ results: [CEDAR] })
      }),
    )
    render(<Mills />)
    await screen.findByRole('button', { name: 'Select Mill' })
    await drainEventLoop()

    // Legacy's page opened on an empty screen with no query behind it; an unbounded search on
    // mount would be a new (and, per 22.1 D8, deliberately unpaged) cost nobody asked for.
    expect(searches).toBe(0)
  })
})

describe('Mills page — selecting a mill (AC2)', () => {
  test('the dialog offers the three legacy criteria and the three legacy columns, in order', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))

    const dialog = selectMillDialog()
    expect(within(dialog).getByRole('textbox', { name: 'Number:' })).toBeInTheDocument()
    expect(within(dialog).getByRole('textbox', { name: 'Name:' })).toBeInTheDocument()
    expect(within(dialog).getByRole('combobox', { name: 'Status:' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Search' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Clear' })).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Search' }))

    const headers = within(await screen.findByRole('table', { name: /mill search results/i }))
      .getAllByRole('columnheader')
      .map((cell) => cell.textContent)
    expect(headers).toEqual(['Mill Number', 'Mill Name', 'Status'])
  })

  test('choosing a result row IS the select action — there is no OK button', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))

    const dialog = selectMillDialog()
    // mills.xhtml:216-217 selects on rowSelect and hides the dialog in the same breath.
    expect(within(dialog).queryByRole('button', { name: /^ok$/i })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /^select$/i })).not.toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: 'Select mill 670' }))

    expect(await screen.findByRole('region', { name: /mill details/i })).toBeInTheDocument()
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: /find and select mill$/i }),
      ).not.toBeInTheDocument(),
    )
  })

  test('the three criteria are sent as typed, and an omitted one is not a filter', async () => {
    const user = userEvent.setup()
    const seen = searchAnswers({ results: [CEDAR] })
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))

    const dialog = selectMillDialog()
    await user.type(within(dialog).getByRole('textbox', { name: 'Number:' }), '670')
    await user.click(within(dialog).getByRole('combobox', { name: 'Status:' }))
    await user.click(await screen.findByRole('option', { name: 'Active' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0].get('millNumber')).toBe('670')
    expect(seen[0].get('status')).toBe('ACT')
    // An empty Name must not travel as `millName=`, which would be a filter on the empty string.
    expect(seen[0].has('millName')).toBe(false)
  })

  test('a zero-match search keeps the dialog open and renders ERR-001 verbatim off the 200', async () => {
    const user = userEvent.setup()
    const message =
      'No mill matching this criteria has been found. Please ensure the ilcr_mill_status_xref ID matches with mill ID or try importing the mill.'
    searchAnswers({ results: [], messageKey: 'mill.not.found', message })
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))

    // Not an error response (MillMaintenanceController:52-60): the criteria stay up for a retry.
    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()
    expect(selectMillDialog()).toBeInTheDocument()
    expect(within(selectMillDialog()).getByRole('textbox', { name: 'Number:' })).toBeEnabled()
  })

  test('a non-numeric mill number renders the S15 converter text off the 400', async () => {
    const user = userEvent.setup()
    const detail =
      "Number: 'abc' must be replaced with a number consisting of one or more digits - decimal values are not accepted."
    server.use(http.get(ADMIN_MILLS, () => problemBody(400, detail)))
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.type(within(selectMillDialog()).getByRole('textbox', { name: 'Number:' }), 'abc')
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))

    // Legacy swallowed this entirely — mills.xhtml:211 updates only the result table — so serving
    // it is a strengthening, not a deviation.
    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    expect(selectMillDialog()).toBeInTheDocument()
  })

  test('the Status criterion is a closed two-value list, so an unknown code is unreachable', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('combobox', { name: 'Status:' }))

    // D3: no endpoint serves ILCR_MILL_STATUS_CODE, so the list is client-held and pinned from
    // delivery. "Close", not "Closed" — the code table's own description (deviation (C), (G)).
    const options = (await screen.findAllByRole('option')).map((option) => option.textContent)
    expect(options).toEqual(['Any', 'Active', 'Close'])
  })

  test('Clear empties the criteria without searching', async () => {
    const user = userEvent.setup()
    const seen = searchAnswers({ results: [CEDAR] })
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    const number = within(selectMillDialog()).getByRole('textbox', { name: 'Number:' })
    await user.type(number, '670')
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Clear' }))

    expect(number).toHaveValue('')
    await drainEventLoop()
    expect(seen).toHaveLength(0)

    // Positive control: the same pipeline still searches, so the absence above is a real refusal
    // rather than a dialog that can never issue a request.
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(seen).toHaveLength(1))
  })

  test('a 403 on the search renders the API text — the backend, not the menu, is authoritative', async () => {
    const user = userEvent.setup()
    const detail = 'You do not have permission to perform this action.'
    server.use(http.get(ADMIN_MILLS, () => problemBody(403, detail)))
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
  })
})

describe('Mills page — importing a mill (AC3)', () => {
  const IMPORTABLE_ROW = { millId: 750, millNumber: '750', millName: 'Fresh Mill' }

  const openImport = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(screen.getByRole('button', { name: 'Import Mill' }))
    await user.click(within(importDialog()).getByRole('button', { name: 'Search' }))
    return screen.findByRole('button', { name: 'Import mill 750' })
  }

  test('the import dialog offers Number and Name only — an unimported mill has no status', async () => {
    const user = userEvent.setup()
    server.use(http.get(IMPORTABLE, () => HttpResponse.json([IMPORTABLE_ROW])))
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Import Mill' }))

    const dialog = importDialog()
    expect(within(dialog).getByRole('textbox', { name: 'Number:' })).toBeInTheDocument()
    expect(within(dialog).getByRole('textbox', { name: 'Name:' })).toBeInTheDocument()
    // BR-04, mills.xhtml:239-243: the import dialog has no Status criterion at all.
    expect(within(dialog).queryByRole('combobox', { name: 'Status:' })).not.toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Search' }))
    const headers = within(await screen.findByRole('table', { name: /importable mill/i }))
      .getAllByRole('columnheader')
      .map((cell) => cell.textContent)
    expect(headers).toEqual(['Mill Number', 'Mill Name', 'Import'])
  })

  test('the import is confirmed with the CNF-001 text FETCHED from the bundle, never hardcoded', async () => {
    const user = userEvent.setup()
    server.use(http.get(IMPORTABLE, () => HttpResponse.json([IMPORTABLE_ROW])))
    const seen: URLSearchParams[] = []
    server.use(
      http.get(MESSAGES, ({ request }) => {
        seen.push(new URL(request.url).searchParams)
        return HttpResponse.json({
          key: 'confirmImportMill',
          text: 'The mill will be imported into ILCR. Are you sure you would like to continue?',
        })
      }),
    )
    render(<Mills />)
    await user.click(await openImport(user))

    const confirm = await screen.findByRole('dialog', { name: /confirmation/i })
    expect(
      within(confirm).getByText(
        'The mill will be imported into ILCR. Are you sure you would like to continue?',
        { normalizer: verbatim },
      ),
    ).toBeInTheDocument()
    expect(within(confirm).getByRole('button', { name: 'Yes' })).toBeInTheDocument()
    expect(within(confirm).getByRole('button', { name: 'No' })).toBeInTheDocument()
    expect(seen.map((params) => params.get('key'))).toContain('confirmImportMill')
  })

  test('cancelling the confirmation issues NO import and leaves the results intact', async () => {
    const user = userEvent.setup()
    server.use(http.get(IMPORTABLE, () => HttpResponse.json([IMPORTABLE_ROW])))
    let imports = 0
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/import`, () => {
        imports += 1
        return HttpResponse.json({ mill: { ...IMPORTABLE_ROW, revisionCount: 0 } })
      }),
    )
    render(<Mills />)
    await user.click(await openImport(user))
    await user.click(
      within(await screen.findByRole('dialog', { name: /confirmation/i })).getByRole('button', {
        name: 'No',
      }),
    )

    await drainEventLoop()
    expect(imports).toBe(0)
    // primefaces-fix-4.0.js:38-43 — a cancelled confirm left the dialog and its results standing.
    expect(importDialog()).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import mill 750' })).toBeInTheDocument()

    // Positive control: the same control does import once confirmed.
    await user.click(screen.getByRole('button', { name: 'Import mill 750' }))
    await user.click(
      within(await screen.findByRole('dialog', { name: /confirmation/i })).getByRole('button', {
        name: 'Yes',
      }),
    )
    await waitFor(() => expect(imports).toBe(1))
  })

  test('a successful import selects the mill and shows NO success sentence', async () => {
    const user = userEvent.setup()
    server.use(http.get(IMPORTABLE, () => HttpResponse.json([IMPORTABLE_ROW])))
    const imported = {
      millId: 750,
      revisionCount: 0,
      millNumber: '750',
      millName: 'Fresh Mill',
      millStatusCode: 'CLS',
      statusDescription: 'Close',
      headOfficeContactInd: 'Y',
    }
    server.use(
      // AdminMillResponse omits BOTH message fields on import, by design — legacy emitted none
      // (MillsMB.java:385-418 has no addInfoMessage) and none is to be invented (deviation (I)).
      http.post(`${ADMIN_MILLS}/:millId/import`, () => HttpResponse.json({ mill: imported })),
    )
    render(<Mills />)
    await user.click(await openImport(user))
    await user.click(
      within(await screen.findByRole('dialog', { name: /confirmation/i })).getByRole('button', {
        name: 'Yes',
      }),
    )

    const detail = await screen.findByRole('region', { name: /mill details/i })
    expect(within(detail).getByText(/Fresh Mill/)).toBeInTheDocument()
    // The confirmation IS the panel appearing. Any sentence here is invented text.
    expect(screen.queryByText(/has been/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/imported/i)).not.toBeInTheDocument()
  })

  test('an imported mill lands Closed, so Activate is the action offered (BR-03)', async () => {
    const user = userEvent.setup()
    server.use(http.get(IMPORTABLE, () => HttpResponse.json([IMPORTABLE_ROW])))
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/import`, () =>
        HttpResponse.json({
          mill: {
            millId: 750,
            revisionCount: 0,
            millNumber: '750',
            millName: 'Fresh Mill',
            millStatusCode: 'CLS',
            statusDescription: 'Close',
            headOfficeContactInd: 'Y',
          },
        }),
      ),
      http.get(`${ADMIN_MILLS}/:millId/contact-options`, () => HttpResponse.json([])),
      http.get(`${ADMIN_MILLS}/:millId/users`, () => HttpResponse.json([])),
    )
    render(<Mills />)
    await user.click(await openImport(user))
    await user.click(
      within(await screen.findByRole('dialog', { name: /confirmation/i })).getByRole('button', {
        name: 'Yes',
      }),
    )

    const detail = await screen.findByRole('region', { name: /mill details/i })
    expect(within(detail).getByRole('button', { name: 'Activate' })).toBeInTheDocument()
    expect(within(detail).queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
    expect(within(detail).getByText('Close')).toBeInTheDocument()
  })

  test('a rolled-back import surfaces ERR-003 — the page own only 500 business path', async () => {
    const user = userEvent.setup()
    server.use(http.get(IMPORTABLE, () => HttpResponse.json([IMPORTABLE_ROW])))
    const detail = 'ILCR cannot import the Mill. Please refer to logs.'
    server.use(http.post(`${ADMIN_MILLS}/:millId/import`, () => problemBody(500, detail)))
    render(<Mills />)
    await user.click(await openImport(user))
    await user.click(
      within(await screen.findByRole('dialog', { name: /confirmation/i })).getByRole('button', {
        name: 'Yes',
      }),
    )

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /mill details/i })).not.toBeInTheDocument()
  })

  test('an already-tracked mill is refused 409 with its verbatim text', async () => {
    const user = userEvent.setup()
    server.use(http.get(IMPORTABLE, () => HttpResponse.json([IMPORTABLE_ROW])))
    const detail = 'This mill is already tracked in ILCR and cannot be imported again.'
    server.use(http.post(`${ADMIN_MILLS}/:millId/import`, () => problemBody(409, detail)))
    render(<Mills />)
    await user.click(await openImport(user))
    await user.click(
      within(await screen.findByRole('dialog', { name: /confirmation/i })).getByRole('button', {
        name: 'Yes',
      }),
    )

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
  })

  test('the import control carries an accessible name, which legacy never gave it', async () => {
    const user = userEvent.setup()
    server.use(
      http.get(IMPORTABLE, () =>
        HttpResponse.json([IMPORTABLE_ROW, { millId: 751, millNumber: '751', millName: 'Two' }]),
      ),
    )
    render(<Mills />)
    await openImport(user)

    // mills.xhtml:258 rendered `value=""` with no title or alt, so every row's control was an
    // unnamed icon and a screen-reader user could not tell them apart.
    expect(screen.getByRole('button', { name: 'Import mill 750' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import mill 751' })).toBeInTheDocument()
  })
})

describe('Mills page — mill detail and the contact save (AC4)', () => {
  test('the identity line renders the SERVER status description, not a client label', async () => {
    const user = userEvent.setup()
    searchAnswers({ results: [CLOSED] })
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select mill 671' }))

    const detail = await screen.findByRole('region', { name: /mill details/i })
    expect(within(detail).getByText(/671 - Closed Mill/)).toBeInTheDocument()
    // "Close", not "Closed" (deviation (G)) — and hardcoding either would fail this.
    expect(within(detail).getByText('Close')).toBeInTheDocument()
    expect(within(detail).queryByText('Closed')).not.toBeInTheDocument()
  })

  test('mill number and name are not editable anywhere on this screen', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await selectCedar(user)

    // Legacy writes nothing on THE.MILL (MillDAO.java:216-254); there is no rename and no create.
    const detail = detailPanel()
    expect(within(detail).queryByRole('textbox')).not.toBeInTheDocument()
    expect(within(detail).getAllByRole('combobox')).toHaveLength(3)
  })

  test('the three editable controls carry the legacy labels, spacing and all', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await selectCedar(user)

    const detail = detailPanel()
    expect(within(detail).getByRole('combobox', { name: 'Head Office :' })).toBeInTheDocument()
    expect(
      within(detail).getByRole('combobox', { name: 'Head Office Contact :' }),
    ).toBeInTheDocument()
    expect(within(detail).getByRole('combobox', { name: 'Division Contact :' })).toBeInTheDocument()
  })

  test('the contact lists come ONLY from contact-options, in the order served', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await selectCedar(user)

    await user.click(within(detailPanel()).getByRole('combobox', { name: 'Division Contact :' }))
    const options = (await screen.findAllByRole('option')).map((option) => option.textContent)
    // The server orders by CONTACT_NAME (22.1 D4a) — take it as given and add no sort. The first
    // entry is the explicit "no contact" choice, which is a DELETE and must read as a choice.
    expect(options).toEqual(['(None)', 'Ada Lovelace', 'Grace Hopper'])
  })

  test('Save sends all three editable fields plus the revision, every time', async () => {
    const user = userEvent.setup()
    let body: unknown
    server.use(
      http.put(`${ADMIN_MILLS}/:millId/contacts`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          mill: { ...CEDAR, revisionCount: 4 },
          messageKey: 'mill.updated',
          message: 'Mill 670 - Cedar Mill has been saved.',
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(body).toBeDefined())
    // A partial save is not expressible: SaveMillContactsRequest 400s on an omitted field, and a
    // null contact CLEARS the column rather than leaving it alone.
    expect(body).toEqual({
      headOfficeContactInd: 'Y',
      headOfficeContactId: 11,
      divisionContactId: null,
      revisionCount: 3,
    })
  })

  test('clearing a contact sends null, which CLEARS the stored column', async () => {
    const user = userEvent.setup()
    let body: unknown
    server.use(
      http.put(`${ADMIN_MILLS}/:millId/contacts`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          mill: { ...CEDAR, revisionCount: 4, headOfficeContactId: undefined },
          messageKey: 'mill.updated',
          message: 'Mill 670 - Cedar Mill has been saved.',
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)

    await user.click(within(detailPanel()).getByRole('combobox', { name: 'Head Office Contact :' }))
    await user.click(await screen.findByRole('option', { name: '(None)' }))
    await user.click(within(detailPanel()).getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(body).toBeDefined())
    expect((body as { headOfficeContactId: unknown }).headOfficeContactId).toBeNull()
  })

  test('a successful save renders SUC-001 verbatim and takes the NEXT revision from the response', async () => {
    const user = userEvent.setup()
    const message = 'Mill 670 - Cedar Mill has been saved.'
    const bodies: unknown[] = []
    server.use(
      http.put(`${ADMIN_MILLS}/:millId/contacts`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({
          mill: { ...CEDAR, revisionCount: 4 },
          messageKey: 'mill.updated',
          message,
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Save' }))
    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()

    await user.click(within(detailPanel()).getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(bodies).toHaveLength(2))
    // 4 came off the write response. Incrementing locally would also read 4 here — but would drift
    // the moment the server skipped a value, so the response is the only source.
    expect((bodies[1] as { revisionCount: number }).revisionCount).toBe(4)
  })

  test('a contact outside the client location is refused 409, and is NOT pre-validated away', async () => {
    const user = userEvent.setup()
    const detail =
      "The selected contact does not belong to this mill's client location. Please choose from the listed contacts."
    let puts = 0
    server.use(
      http.put(`${ADMIN_MILLS}/:millId/contacts`, () => {
        puts += 1
        return problemBody(409, detail)
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    // The option list is deliberately UNFILTERED (22.1 D4a), so BR-09 is the server's call and the
    // request has to actually be made for the administrator to learn that.
    expect(puts).toBe(1)
  })

  test('an unset head-office indicator renders no selection and gates Save (D5)', async () => {
    const user = userEvent.setup()
    // ABSENT, not null: Jackson non_null drops it when the column was never set.
    const unset = { ...CEDAR, headOfficeContactInd: undefined }
    searchAnswers({ results: [unset] })
    let puts = 0
    server.use(
      http.put(`${ADMIN_MILLS}/:millId/contacts`, () => {
        puts += 1
        return HttpResponse.json({ mill: CEDAR, messageKey: 'mill.updated', message: 'saved' })
      }),
    )
    render(<Mills />)
    await selectCedar(user)

    const detail = detailPanel()
    const indicator = within(detail).getByRole('combobox', { name: 'Head Office :' })
    // Legacy offered no blank option and no default, so a stored NULL rendered "No" and the first
    // Save silently wrote N — changing Mill Information output for that mill (deviation (E)).
    expect(indicator).not.toHaveTextContent('No')
    expect(indicator).not.toHaveTextContent('Yes')

    const save = within(detail).getByRole('button', { name: 'Save' })
    expect(save).toBeDisabled()
    await user.click(save)
    await drainEventLoop()
    expect(puts).toBe(0)

    // Advisory only (AD-6): the gate lifts the moment a real choice is made, and the server
    // re-validates regardless.
    await user.click(indicator)
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await user.click(within(detailPanel()).getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(puts).toBe(1))
  })

  test('switching mills repaints the contact selections rather than keeping the previous ones', async () => {
    const user = userEvent.setup()
    searchAnswers({ results: [CEDAR, CLOSED] })
    server.use(
      http.get(`${ADMIN_MILLS}/:millId/contact-options`, ({ params }) =>
        HttpResponse.json(params.millId === '670' ? CONTACTS : []),
      ),
    )
    render(<Mills />)
    await selectCedar(user)
    expect(
      within(detailPanel()).getByRole('combobox', { name: 'Head Office Contact :' }),
    ).toHaveTextContent('Ada Lovelace')

    await user.click(within(detailPanel()).getByRole('button', { name: 'Change Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select mill 671' }))

    // An `undefined` selectedItem flips Carbon to uncontrolled and keeps the PREVIOUS mill's
    // contact painted over a null state (MillAssociations.test.tsx:732-751).
    await waitFor(() =>
      expect(
        within(detailPanel()).getByRole('combobox', { name: 'Head Office Contact :' }),
      ).not.toHaveTextContent('Ada Lovelace'),
    )
  })
})

describe('Mills page — status actions (AC5)', () => {
  test('exactly one of Deactivate / Activate renders, chosen by the status CODE', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await selectCedar(user)

    // STA-001, MillsMB.java:359-364: presence/absence, never enable/disable.
    const detail = detailPanel()
    expect(within(detail).getByRole('button', { name: 'Deactivate' })).toBeInTheDocument()
    expect(within(detail).queryByRole('button', { name: 'Activate' })).not.toBeInTheDocument()
  })

  test('neither status action is confirmed — CNF-001 is the only confirm on this screen', async () => {
    const user = userEvent.setup()
    let calls = 0
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/deactivate`, () => {
        calls += 1
        return HttpResponse.json({
          mill: { ...CEDAR, millStatusCode: 'CLS', statusDescription: 'Close', revisionCount: 4 },
          messageKey: 'mill.expired',
          message: 'Mill 670 - Cedar Mill has been deactivated.',
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Deactivate' }))

    // mills.xhtml:100-101 carries no p:confirm on either action.
    await waitFor(() => expect(calls).toBe(1))
    expect(screen.queryByRole('dialog', { name: /confirmation/i })).not.toBeInTheDocument()
  })

  test('a deactivation renders SUC-002 verbatim and SWAPS the offered action', async () => {
    const user = userEvent.setup()
    const message = 'Mill 670 - Cedar Mill has been deactivated.'
    let body: unknown
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/deactivate`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          mill: { ...CEDAR, millStatusCode: 'CLS', statusDescription: 'Close', revisionCount: 4 },
          messageKey: 'mill.expired',
          message,
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Deactivate' }))

    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()
    expect(body).toEqual({ revisionCount: 3 })
    expect(within(detailPanel()).getByRole('button', { name: 'Activate' })).toBeInTheDocument()
    expect(
      within(detailPanel()).queryByRole('button', { name: 'Deactivate' }),
    ).not.toBeInTheDocument()
  })

  test('S12 blocks the deactivation verbatim, and the status is left alone', async () => {
    const user = userEvent.setup()
    const detail = 'The selected mill has active users, you must deactivate them first.'
    server.use(http.post(`${ADMIN_MILLS}/:millId/deactivate`, () => problemBody(409, detail)))
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Deactivate' }))

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    // Refused means unchanged: the action must not have optimistically swapped.
    expect(within(detailPanel()).getByRole('button', { name: 'Deactivate' })).toBeInTheDocument()
  })

  test('the S12 block is NEVER pre-computed from the client row list', async () => {
    const user = userEvent.setup()
    // A row list that says there are no active users at all...
    usersAre(ENDED_ROW)
    let calls = 0
    const detail = 'The selected mill has active users, you must deactivate them first.'
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/deactivate`, () => {
        calls += 1
        return problemBody(409, detail)
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })
    await user.click(within(detailPanel()).getByRole('button', { name: 'Deactivate' }))

    // ...must still issue the request: the guard is a LIVE server-side query and the client's list
    // can legitimately disagree (22.1 deviation (D)).
    await waitFor(() => expect(calls).toBe(1))
    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
  })

  test('a partial-report-records refusal on activate renders its verbatim text (BR-07)', async () => {
    const user = userEvent.setup()
    searchAnswers({ results: [CLOSED] })
    const detail =
      "The selected mill's report records for the current year are incomplete, so it cannot be activated. Please refer to logs."
    server.use(http.post(`${ADMIN_MILLS}/:millId/activate`, () => problemBody(409, detail)))
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select mill 671' }))
    await user.click(within(detailPanel()).getByRole('button', { name: 'Activate' }))

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
  })

  test('a stale revision renders the shared conflict text unchanged, word "schedule" and all', async () => {
    const user = userEvent.setup()
    const detail = 'This schedule was changed by another user. Please reload and try again.'
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/deactivate`, () => problemBody(409, detail)),
      http.get(`${ADMIN_MILLS}/:millId`, () =>
        HttpResponse.json({ ...CEDAR, revisionCount: 9, millStatusCode: 'ACT' }),
      ),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Deactivate' }))

    // A known shared-key artefact on a Mills page. Re-wording it client-side is what AD-8 forbids.
    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
  })

  test('a 409 re-reads the mill instead of leaving a stale revision to retry with', async () => {
    const user = userEvent.setup()
    let reads = 0
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/deactivate`, () =>
        problemBody(409, 'This schedule was changed by another user. Please reload and try again.'),
      ),
      http.get(`${ADMIN_MILLS}/:millId`, () => {
        reads += 1
        return HttpResponse.json({ ...CEDAR, revisionCount: 9 })
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Deactivate' }))

    await screen.findByText(/changed by another user/i)
    await waitFor(() => expect(reads).toBe(1))

    // The re-read's revision is what the next attempt must carry, not the spent one.
    let body: unknown
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/deactivate`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          mill: { ...CEDAR, millStatusCode: 'CLS', statusDescription: 'Close', revisionCount: 10 },
          messageKey: 'mill.expired',
          message: 'done',
        })
      }),
    )
    await user.click(within(detailPanel()).getByRole('button', { name: 'Deactivate' }))
    await waitFor(() => expect(body).toEqual({ revisionCount: 9 }))
  })

  test('a double-click issues ONE request — the lock closes before the re-render', async () => {
    const user = userEvent.setup()
    let calls = 0
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/deactivate`, () => {
        calls += 1
        return HttpResponse.json({
          mill: { ...CEDAR, millStatusCode: 'CLS', statusDescription: 'Close', revisionCount: 4 },
          messageKey: 'mill.expired',
          message: 'deactivated once',
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)

    const button = within(detailPanel()).getByRole('button', { name: 'Deactivate' })
    // Both clicks inside ONE act: they land before React commits `disabled={busy}`, so both read
    // the same render's state — only a synchronously-set lock can refuse the second, which would
    // otherwise carry an already-spent revisionCount and answer a spurious 409.
    act(() => {
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(await screen.findByText('deactivated once')).toBeInTheDocument()
    expect(calls).toBe(1)
  })
})

describe('Mills page — the associated-user panel (AC6)', () => {
  test('there is ONE panel, headed singular, and no Auditors panel', async () => {
    const user = userEvent.setup()
    usersAre(ACTIVE_ROW)
    render(<Mills />)
    await selectCedar(user)

    expect(
      await screen.findByRole('table', { name: /associated licensee user/i }),
    ).toBeInTheDocument()
    // DL-23 retired the auditor role outright; the panel is retired, not deferred (22.2 dev (A)).
    expect(screen.queryByText(/associated auditors/i)).not.toBeInTheDocument()
  })

  test('rows are requested WITHOUT includeEnded, because it defaults true on this surface', async () => {
    const user = userEvent.setup()
    const seen = usersAre(ACTIVE_ROW, ENDED_ROW)
    render(<Mills />)
    await selectCedar(user)

    await waitFor(() => expect(seen).toHaveLength(1))
    // MillAssociationApi.java:57 — the opposite default to the users-screen endpoint, which the
    // frontend already carries an explicit `includeEnded: true` for. Sending false here would hide
    // every inactive row the panel and its Activate control exist for.
    expect(seen[0].has('includeEnded')).toBe(false)
  })

  test('the status cell reads Active / Inactive, and the row action is the single applicable one', async () => {
    const user = userEvent.setup()
    usersAre(ACTIVE_ROW, ENDED_ROW)
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })

    // mills.xhtml:123-124 renders literally "Active" / "Inactive"; the wire value stays ENDED.
    expect(within(rowFor(GUID)).getByText('Active')).toBeInTheDocument()
    expect(within(rowFor(OTHER_GUID)).getByText('Inactive')).toBeInTheDocument()

    expect(
      within(rowFor(GUID)).getByRole('button', { name: /^deactivate user/i }),
    ).toBeInTheDocument()
    expect(
      within(rowFor(GUID)).queryByRole('button', { name: /^activate user/i }),
    ).not.toBeInTheDocument()
    expect(
      within(rowFor(OTHER_GUID)).getByRole('button', { name: /^activate user/i }),
    ).toBeInTheDocument()
    expect(
      within(rowFor(OTHER_GUID)).queryByRole('button', { name: /^deactivate user/i }),
    ).not.toBeInTheDocument()
  })

  test('a never-activated row offers Activate — the deviation (E) derivation', async () => {
    const user = userEvent.setup()
    usersAre(NEVER_ACTIVATED_ROW)
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })

    // Both states serialize ENDED. `activeDate == null && revisionCount === 0` is the only thing
    // that separates a row created by this surface's add from one genuinely deactivated.
    const row = rowFor(OTHER_GUID)
    expect(within(row).getByRole('button', { name: /^activate user/i })).toBeInTheDocument()
    expect(within(row).getByText('Inactive')).toBeInTheDocument()
    // Its INACTIVE_DATE is really its creation date, so rendering it under "Deactivation Date"
    // would be a false statement about a mill that was never deactivated (deviation (L)).
    const cells = within(row)
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
    expect(cells[3]).toBe('—')
  })

  test('dates render dd/MM/yyyy, as they did at every one of legacy six sites', async () => {
    const user = userEvent.setup()
    usersAre(ACTIVE_ROW, ENDED_ROW)
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })

    // web.xml:92-93 pinned the server timezone and the six f:convertDateTime sites all read
    // dd/MM/yyyy with no time. The wire sends an ISO LocalDate.
    expect(within(rowFor(GUID)).getByText('04/03/2026')).toBeInTheDocument()
    expect(within(rowFor(OTHER_GUID)).getByText('30/11/2025')).toBeInTheDocument()
  })

  test('per-row actions are not confirmed, and send the ROW own revision', async () => {
    const user = userEvent.setup()
    const rows: unknown[] = [ACTIVE_ROW]
    usersFrom(rows)
    let body: unknown
    let seenGuid: string | undefined
    const message = 'Mill 670 - Cedar Mill has been deactivated for user AAAA -  .'
    server.use(
      http.post(
        `${ADMIN_MILLS}/:millId/users/:userGuid/deactivate`,
        async ({ request, params }) => {
          body = await request.json()
          seenGuid = params.userGuid as string
          rows[0] = { ...ACTIVE_ROW, status: 'ENDED', activeDate: null, inactiveDate: '2026-09-10' }
          return HttpResponse.json({
            assignment: rows[0],
            messageKey: 'user.deactivate.mill',
            message,
          })
        },
      ),
    )
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })
    await user.click(within(rowFor(GUID)).getByRole('button', { name: /^deactivate user/i }))

    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()
    // mills.xhtml:137, :140 fire immediately — the same ruling Scho gave for the users screen.
    expect(screen.queryByRole('dialog', { name: /confirmation/i })).not.toBeInTheDocument()
    expect(body).toEqual({ revisionCount: 7 })
    expect(seenGuid).toBe(GUID)
    // Legacy's row flipped only because the DAO mutated the object the table held; a DTO rebuild
    // has to refetch or nothing changes on screen.
    expect(within(rowFor(GUID)).getByText('Inactive')).toBeInTheDocument()
  })

  test('the association toggles POST — they are not the users screen PATCH', async () => {
    const user = userEvent.setup()
    usersAre(ENDED_ROW)
    let posted = false
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/users/:userGuid/activate`, () => {
        posted = true
        return HttpResponse.json({
          assignment: {
            ...ENDED_ROW,
            status: 'ACTIVE',
            activeDate: '2026-09-10',
            revisionCount: 3,
          },
          messageKey: 'user.activate.mill',
          message: 'activated',
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })
    await user.click(within(rowFor(OTHER_GUID)).getByRole('button', { name: /^activate user/i }))

    await waitFor(() => expect(posted).toBe(true))
  })

  test('Add opens the legacy-titled dialog, and choosing a candidate IS the add', async () => {
    const user = userEvent.setup()
    const rows: unknown[] = []
    usersFrom(rows)
    let body: unknown
    const message = 'Mill 670 - Cedar Mill has been activated for user AAAA -  .'
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/users`, async ({ request }) => {
        body = await request.json()
        rows.push(NEVER_ACTIVATED_ROW)
        return HttpResponse.json({
          assignment: NEVER_ACTIVATED_ROW,
          messageKey: 'user.activate.mill',
          message,
        })
      }),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(screen.getByRole('button', { name: 'Add' }))

    const dialog = await screen.findByRole('dialog', { name: /find and add user/i })
    await user.type(within(dialog).getByRole('combobox', { name: /user id/i }), 'AL')
    await user.click(await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }))

    // mills.xhtml:298-299 — rowSelect IS addUserToMill, and SUC-004 says "has been activated"
    // even though the row is created Inactive. Legacy's own contradiction, ratified as 22.2 D1.
    expect(await screen.findByText(message, { normalizer: verbatim })).toBeInTheDocument()
    expect(body).toEqual({ userGuid: GUID })
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /find and add user/i })).not.toBeInTheDocument(),
    )
  })

  test('a duplicate add is a WARNING on a 200, and the panel is unchanged', async () => {
    const user = userEvent.setup()
    let listCalls = 0
    server.use(
      http.get(`${ADMIN_MILLS}/:millId/users`, () => {
        listCalls += 1
        return HttpResponse.json([ACTIVE_ROW])
      }),
    )
    const message = 'User AAAA is already associated to mill Cedar Mill. Please verify.'
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/users`, () =>
        HttpResponse.json({
          assignment: ACTIVE_ROW,
          // The key is the semantic opposite of its own text: it fires when the user IS associated.
          messageKey: 'user.not.associated.to.mill',
          message,
        }),
      ),
    )
    render(<Mills />)
    await selectCedar(user)
    await user.click(screen.getByRole('button', { name: 'Add' }))
    const dialog = await screen.findByRole('dialog', { name: /find and add user/i })
    await user.type(within(dialog).getByRole('combobox', { name: /user id/i }), 'AL')
    await user.click(await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }))

    const notice = await screen.findByText(message, { normalizer: verbatim })
    // Only messageKey tells this apart from a successful add — and it arrives on a 200, so a
    // status-only branch renders it as a success.
    expect(notice.closest('.cds--inline-notification')).toHaveClass(
      'cds--inline-notification--warning',
    )
    // Nothing was written (MillAssociationService.java:97-101), so the one visible row stands.
    expect(within(usersTable()).getAllByRole('row')).toHaveLength(2)
    expect(within(rowFor(GUID)).getByText('Active')).toBeInTheDocument()
    expect(listCalls).toBeGreaterThanOrEqual(1)
  })

  test('activating on a Closed mill is refused with the S13 text, row unchanged (BR-02)', async () => {
    const user = userEvent.setup()
    searchAnswers({ results: [CLOSED] })
    usersAre({ ...ENDED_ROW, millId: 671, millNumber: '671', millName: 'Closed Mill' })
    const detail = 'You must activate the mill before activating any users.'
    server.use(
      http.post(`${ADMIN_MILLS}/:millId/users/:userGuid/activate`, () => problemBody(409, detail)),
    )
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select mill 671' }))
    await screen.findByRole('table', { name: /associated licensee user/i })
    await user.click(within(rowFor(OTHER_GUID)).getByRole('button', { name: /^activate user/i }))

    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    expect(within(rowFor(OTHER_GUID)).getByText('Inactive')).toBeInTheDocument()
  })

  test('the BR-02 block is never presented as a system-wide invariant', async () => {
    const user = userEvent.setup()
    searchAnswers({ results: [CLOSED] })
    usersAre({ ...ENDED_ROW, millId: 671, millNumber: '671', millName: 'Closed Mill' })
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select mill 671' }))
    await screen.findByRole('table', { name: /associated licensee user/i })

    // Ratified parity (deferred-work.md:1296-1316): the shipped users surface CAN create an active
    // association on a Closed mill, so the control must stay live and unqualified here. A disabled
    // button or a "cannot" tooltip would state a rule the system does not actually hold.
    const activate = within(rowFor(OTHER_GUID)).getByRole('button', { name: /^activate user/i })
    expect(activate).toBeEnabled()
    expect(screen.queryByText(/cannot be active/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/must be active before/i)).not.toBeInTheDocument()
  })

  test('a failed re-read withdraws the write sentence rather than standing over stale rows', async () => {
    const user = userEvent.setup()
    let listCalls = 0
    server.use(
      http.get(`${ADMIN_MILLS}/:millId/users`, () => {
        listCalls += 1
        return listCalls === 1
          ? HttpResponse.json([ACTIVE_ROW])
          : problemBody(500, 'The list could not be re-read.')
      }),
      http.post(`${ADMIN_MILLS}/:millId/users/:userGuid/deactivate`, () =>
        HttpResponse.json({
          assignment: { ...ACTIVE_ROW, status: 'ENDED', activeDate: null },
          messageKey: 'user.deactivate.mill',
          message: 'Mill 670 - Cedar Mill has been deactivated for user AAAA -  .',
        }),
      ),
    )
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })
    await user.click(within(rowFor(GUID)).getByRole('button', { name: /^deactivate user/i }))

    expect(
      await screen.findByText('The list could not be re-read.', { normalizer: verbatim }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/has been deactivated for user/)).not.toBeInTheDocument()
  })

  test('a list for a mill the administrator has already left does not repaint', async () => {
    const user = userEvent.setup()
    searchAnswers({ results: [CEDAR, CLOSED] })
    let releaseCedar!: () => void
    const cedarBlocked = new Promise<void>((resolve) => {
      releaseCedar = resolve
    })
    server.use(
      http.get(`${ADMIN_MILLS}/:millId/users`, async ({ params }) => {
        if (params.millId === '670') {
          // Held, not slept: Cedar's list can only land after Closed Mill's has rendered, so the
          // race this test exists for is guaranteed rather than hoped for.
          await cedarBlocked
          return HttpResponse.json([ACTIVE_ROW])
        }
        return HttpResponse.json([
          { ...ENDED_ROW, millId: 671, millNumber: '671', millName: 'Closed Mill' },
        ])
      }),
    )
    render(<Mills />)
    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select mill 670' }))
    await user.click(await screen.findByRole('button', { name: 'Change Mill' }))
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Search' }))
    await user.click(await screen.findByRole('button', { name: 'Select mill 671' }))

    expect(await screen.findByText(OTHER_GUID)).toBeInTheDocument()
    releaseCedar()
    await drainEventLoop()
    expect(within(usersTable()).queryByText(GUID)).not.toBeInTheDocument()
  })

  test('a directory outage is reported without disturbing the loaded panel', async () => {
    const user = userEvent.setup()
    usersAre(ACTIVE_ROW)
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })

    const detail = 'The user directory is currently unavailable. Please try again later.'
    server.use(http.get(LOOKUP, () => problemBody(502, detail)))
    await user.click(screen.getByRole('button', { name: 'Add' }))
    const dialog = await screen.findByRole('dialog', { name: /find and add user/i })
    await user.type(within(dialog).getByRole('combobox', { name: /user id/i }), 'ZZ')

    // findByText polls inside act(), which outlasts the picker's 250ms debounce without leaving
    // the resulting state updates to land outside a React batch.
    expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
    expect(within(rowFor(GUID)).getByRole('button', { name: /^deactivate user/i })).toBeEnabled()
  })
})

describe('Mills page — Change Mill (AC2, D4)', () => {
  test('Change Mill opens the select dialog, which legacy own button never managed', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await selectCedar(user)

    // mills.xhtml:102 called `searchSelectMill.show()` on the pre-4.0 global widget namespace with
    // no LEGACY_WIDGET_NAMESPACE param and no shim, and updated the dialog containers rather than
    // the inner form — so the dialog almost certainly never opened. Fixed (deviation (D)).
    await user.click(within(detailPanel()).getByRole('button', { name: 'Change Mill' }))
    expect(selectMillDialog()).toBeInTheDocument()
  })

  test('Change Mill leaves the current mill selected until a new row is chosen, and says nothing', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await selectCedar(user)
    await user.click(within(detailPanel()).getByRole('button', { name: 'Change Mill' }))

    // MillsMB.java:513-519 — legacy's clear(false) does NOT clear the selection, and
    // UC-MILL-002-slices.md:190 is [ARTIFACT SILENT] on any message for this transition.
    expect(detailPanel()).toBeInTheDocument()
    expect(within(detailPanel()).getByText(/670 - Cedar Mill/)).toBeInTheDocument()
    expect(screen.queryByRole('status')).toBeDefined()
    expect(screen.queryByText(/has been/i)).not.toBeInTheDocument()
  })

  test('criteria never leak between the two dialogs, nor across re-opens', async () => {
    const user = userEvent.setup()
    render(<Mills />)

    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    await user.type(within(selectMillDialog()).getByRole('textbox', { name: 'Number:' }), '670')
    await user.type(within(selectMillDialog()).getByRole('textbox', { name: 'Name:' }), 'Cedar')
    await user.click(within(selectMillDialog()).getByRole('button', { name: 'Close' }))

    // mills.xhtml:247 + MillsMB.java:518 — BOTH dialogs bound Number/Name to the same two bean
    // fields, so one dialog's criteria showed up in the other's and survived every re-open.
    await user.click(screen.getByRole('button', { name: 'Import Mill' }))
    expect(within(importDialog()).getByRole('textbox', { name: 'Number:' })).toHaveValue('')
    expect(within(importDialog()).getByRole('textbox', { name: 'Name:' })).toHaveValue('')
    await user.click(within(importDialog()).getByRole('button', { name: 'Close' }))

    await user.click(screen.getByRole('button', { name: 'Select Mill' }))
    expect(within(selectMillDialog()).getByRole('textbox', { name: 'Number:' })).toHaveValue('')
  })

  test('the two entry controls give way to Change Mill once a mill is selected', async () => {
    const user = userEvent.setup()
    render(<Mills />)
    await selectCedar(user)

    // Legacy gated both on `showSelectMillButton` and offered Change Mill in their place
    // (mills.xhtml:24, :26, :102) — so re-selecting is one control, not three.
    expect(screen.queryByRole('button', { name: 'Select Mill' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Import Mill' })).not.toBeInTheDocument()
    expect(within(detailPanel()).getByRole('button', { name: 'Change Mill' })).toBeInTheDocument()
  })
})

describe('Mills page — the jump-to-user link (AC8, S10)', () => {
  test('View renders on EVERY row, active or not, and carries the user across', async () => {
    const user = userEvent.setup()
    usersAre(ACTIVE_ROW, ENDED_ROW)
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })

    // mills.xhtml:144-151 has no `rendered` guard on View at all.
    expect(within(rowFor(GUID)).getByRole('button', { name: /^view user/i })).toBeInTheDocument()
    expect(
      within(rowFor(OTHER_GUID)).getByRole('button', { name: /^view user/i }),
    ).toBeInTheDocument()

    await user.click(within(rowFor(OTHER_GUID)).getByRole('button', { name: /^view user/i }))

    expect(navigateSpy).toHaveBeenCalledWith({
      to: '/mill-associations',
      search: { userGuid: OTHER_GUID },
    })
  })

  test('navigating away loses unsaved contact edits silently, exactly as legacy did', async () => {
    const user = userEvent.setup()
    usersAre(ACTIVE_ROW)
    render(<Mills />)
    await selectCedar(user)
    await screen.findByRole('table', { name: /associated licensee user/i })

    await user.click(within(detailPanel()).getByRole('combobox', { name: 'Division Contact :' }))
    await user.click(await screen.findByRole('option', { name: 'Grace Hopper' }))
    await user.click(within(rowFor(GUID)).getByRole('button', { name: /^view user/i }))

    // A hard redirect destroyed the view scope and confirmExitIfModified.js is not loaded by
    // mills.xhtml, so there is no unsaved-changes prompt to add.
    expect(navigateSpy).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog', { name: /confirm/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/unsaved/i)).not.toBeInTheDocument()
  })
})
