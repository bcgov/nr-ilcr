import type { ReactNode } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import { getDefaultNormalizer, render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule7b from '@/components/schedule7b'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import type CulvertRequest from '@/interfaces/Schedule7bRequest'
import type { Culvert } from '@/interfaces/Schedule7bResponse'

const URL = 'http://localhost:3000/api/v1/schedule7b'
const CULVERTS_URL = `${URL}/culverts`
const CHECK_URL = `${URL}/check-status`

// Schedule 7B has exactly ONE code list. `RP` is here on purpose: the 2026-08-11 Round Plastic addition
// is code-table DATA, so a new code must reach this dropdown with no frontend change.
const CODE_LISTS = {
  culvertTypes: [
    { code: 'R', description: 'Round' },
    { code: 'O', description: 'Others' },
    { code: 'RP', description: 'Round Plastic' },
  ],
}

const mainHaul: Culvert = {
  culvertReportId: 7801,
  rowCounter: 1,
  culvertTypeCode: 'R',
  spanSize: 1200,
  riseSize: 900,
  length: 12.5,
  culvertPieceCount: 3,
  materialCost: 4000,
  installCost: 1500,
  totalCost: 5500,
  comments: 'Main haul road',
  revisionCount: 3,
}

const culvertAt = (id: number, rowCounter: number, overrides: Partial<Culvert> = {}): Culvert => ({
  ...mainHaul,
  culvertReportId: id,
  rowCounter,
  comments: `Culvert ${String(rowCounter)}`,
  ...overrides,
})

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  culverts: [mainHaul],
  codeLists: CODE_LISTS,
  ...overrides,
})

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

// Preserves the literal whitespace the backend sends (the check-status ` : Value Required` spacing, the
// ERR-003 trailing space) so verbatim-rendering assertions are not defeated by the default
// collapse/trim.
const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

// The accordion body for one culvert, opened so its fields are reachable.
async function openCulvert(user: ReturnType<typeof userEvent.setup>, rowCounter: number) {
  await user.click(
    await screen.findByRole('button', { name: `Culvert report Id: ${String(rowCounter)}` }),
  )
}

const field = (label: RegExp | string) => screen.getByLabelText(label)

// Scope to one culvert's accordion panel. Every row renders its own editor at once, so an unscoped
// field query matches as many elements as there are culverts.
const culvertPanel = (culvertReportId: number) =>
  within(
    document
      .getElementById(`culvert-${String(culvertReportId)}-spanSize`)
      ?.closest('.cds--accordion__item') as HTMLElement,
  )

// The Add panel's own fields (it renders CulvertFields with idPrefix="add"). Scoped like
// `culvertPanel` because the rows carry the SAME labels — collapsed, but still in the DOM — so an
// unscoped query is ambiguous the moment the schedule holds a culvert.
const addPanel = () =>
  within(document.getElementById('add-spanSize')?.closest('.schedule-7b__section') as HTMLElement)

// The Carbon Modal root, so its confirm button is distinguishable from the row action that opened it.
const deleteModal = async () => within(await screen.findByRole('presentation'))

// Save is a PAGE-level action covering every culvert at once (legacy parity) — a culvert row carries
// only Delete — so it drives every write test. It renders above and below the list; either serves.
const savePage = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getAllByRole('button', { name: 'Save' })[0])

type SaveAllBody = { culverts: { culvertReportId: number; culvert: CulvertRequest }[] }

// The page-level Save sends the whole schedule, so a test asserting on "the request for culvert N"
// pulls that culvert's entry out of the batch.
const entryFor = (body: SaveAllBody | null, culvertReportId: number): CulvertRequest | undefined =>
  body?.culverts.find((entry) => entry.culvertReportId === culvertReportId)?.culvert

// Drives a mill/year change mid-flight so the stale-response guard can be exercised. Module-level so it
// is not re-created per render (an @eslint-react rule forbids nested component definitions).
const ContextSwitchHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(600, 2019)}>
        switch context
      </button>
      <Schedule7b />
    </>
  )
}

// Fill the Add panel with a complete, valid culvert.
async function fillAddForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(addPanel().getByRole('combobox', { name: /Type/i }))
  await user.click(await addPanel().findByRole('option', { name: 'Round' }))
  await user.type(addPanel().getByLabelText('Span (mm)'), '900')
  await user.type(addPanel().getByLabelText('No of Pieces'), '2')
}

describe('Schedule 7B page', () => {
  test('renders each culvert as an accordion row with legacy labels and the server total (AC1, AC2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)

    expect(await screen.findByRole('button', { name: 'Culvert report Id: 1' })).toBeInTheDocument()
    await openCulvert(user, 1)

    // Legacy's erratic label spacing is transcribed as-is: "Span (mm)" but "Rise(mm)", "Length(m)",
    // "Install costs($)". These are what the page actually shows, so they are what the labels say.
    // Every numeric field displays through its legacy `f:convertNumber` mask — grouped, with that
    // field's fixed decimal count. Span/rise carry `#,###,##0`, so 1200 reads `1,200`, not `1200`.
    expect(field('Span (mm)')).toHaveValue('1,200')
    expect(field('Rise(mm)')).toHaveValue('900')
    expect(field('Length(m)')).toHaveValue('12.5')
    expect(field('No of Pieces')).toHaveValue('3')
    // Money through the costConverter's `##,###,###`.
    expect(field('Material costs ($)')).toHaveValue('4,000')
    expect(field('Install costs($)')).toHaveValue('1,500')
    expect(screen.getByRole('combobox', { name: /Type/i })).toHaveTextContent('Round')

    // The server-computed total renders as plain text, never as a field. Asserting the absence of a
    // control matters as much as the value: a page that let a reporter type over a server total would
    // still pass a value-only check.
    expect(screen.getByText('5,500')).toBeInTheDocument()
    expect(screen.queryByLabelText('Total costs($)')).not.toBeInTheDocument()
  })

  test('a null total renders blank, never 0 (AC2, BR-05)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({
            culverts: [{ ...mainHaul, materialCost: null, installCost: null, totalCost: null }],
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    // Assert the total's value node is genuinely EMPTY. Merely excluding the literal "0" would also
    // pass on "5,500", on an em dash, or on a component that failed to render at all.
    const values = document.querySelectorAll('.schedule-7b__total-value')
    expect(values).toHaveLength(1)
    expect(values[0]).toHaveTextContent('')
    expect(screen.queryByText('5,500')).not.toBeInTheDocument()
  })

  test('an empty culvert list is a valid document, not an error (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))))
    render(<Schedule7b />)

    expect(await screen.findByText(/no culvert reports have been added/i)).toBeInTheDocument()
    expect(screen.queryByText(/unable to load/i)).not.toBeInTheDocument()
  })

  test('the Type dropdown offers every served code, including a newly maintained one (BR-03)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))))
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await user.click(addPanel().getByRole('combobox', { name: /Type/i }))

    // The options come from the document's year-filtered code list, so a Table Maintenance addition
    // (`RP`, 2026-08-11) reaches the form with no frontend change. A hardcoded enum would fail here.
    expect(await addPanel().findByRole('option', { name: 'Round' })).toBeInTheDocument()
    expect(addPanel().getByRole('option', { name: 'Others' })).toBeInTheDocument()
    expect(addPanel().getByRole('option', { name: 'Round Plastic' })).toBeInTheDocument()
  })

  test('Add/Close toggles the add panel and Add Report POSTs, showing the verbatim success (AC1)', async () => {
    let captured: CulvertRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))),
      http.post(CULVERTS_URL, async ({ request }) => {
        captured = (await request.json()) as CulvertRequest
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    expect(screen.getByText('Add a Culvert report')).toBeInTheDocument()

    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toMatchObject({ culvertTypeCode: 'R', spanSize: 900, culvertPieceCount: 2 })
    // The total is derived and legacy renders it disabled — a client must never be able to supply it.
    expect(captured).not.toHaveProperty('totalCost')
    expect(captured).not.toHaveProperty('rowCounter')
    // "Not entered" stays distinguishable from zero.
    expect((captured as unknown as CulvertRequest).materialCost).toBeNull()
    expect((captured as unknown as CulvertRequest).comments).toBeNull()
  })

  test('costs entered on the Add form reach the wire and come back on the row', async () => {
    // The reported symptom was blank cost fields, so this pins the whole path: typed into the Add
    // panel → POST body → echoed document → rendered on the row, masked.
    let captured: CulvertRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))),
      http.post(CULVERTS_URL, async ({ request }) => {
        captured = (await request.json()) as CulvertRequest
        const body = captured
        return HttpResponse.json(
          doc({
            culverts: [
              {
                ...mainHaul,
                materialCost: body.materialCost,
                installCost: body.installCost,
                totalCost: (body.materialCost ?? 0) + (body.installCost ?? 0),
              },
            ],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.type(addPanel().getByLabelText('Material costs ($)'), '4000')
    await user.type(addPanel().getByLabelText('Install costs($)'), '1500')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toMatchObject({ materialCost: 4000, installCost: 1500 })

    await openCulvert(user, 1)
    expect(field('Material costs ($)')).toHaveValue('4,000')
    expect(field('Install costs($)')).toHaveValue('1,500')
    // The derived total is the server's; it renders as text beside the two costs.
    expect(screen.getByText('5,500')).toBeInTheDocument()
  })

  test('a rejected add keeps entered values and shows the verbatim server detail (AC3)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))),
      http.post(CULVERTS_URL, () => problemBody(400, 'The selected culvert type is not valid.')),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(await screen.findByText('The selected culvert type is not valid.')).toBeInTheDocument()
    expect(addPanel().getByLabelText('Span (mm)')).toHaveValue('900')
  })

  test('advisory validation blocks a doomed add and never fires the request (AC1, AC3)', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))),
      http.post(CULVERTS_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await user.type(addPanel().getByLabelText('Span (mm)'), '900')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    // Exactly TWO fields are required on this schedule — Type and No of Pieces. Asserting the count is
    // what stops a copy of Schedule 7A's twelve-field rule from creeping in.
    expect(await screen.findAllByText('Value Required')).toHaveLength(2)
    expect(posted).toBe(false)
  })

  test('the page-level Save PUTs every culvert in ONE request with its own revisionCount', async () => {
    let captured: SaveAllBody | null = null
    let calls = 0
    let putUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [mainHaul, culvertAt(7802, 2)] }))),
      http.put(CULVERTS_URL, async ({ request }) => {
        calls += 1
        captured = (await request.json()) as SaveAllBody
        putUrl = request.url
        return HttpResponse.json(
          doc({
            culverts: [mainHaul, culvertAt(7802, 2)],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    const span = culvertPanel(7801).getByLabelText('Span (mm)')
    await user.clear(span)
    await user.type(span, '1500')
    await savePage(user)

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // ONE request for the whole schedule — the point of the page-level Save (legacy
    // Schedule7bMB.save()). N per-row PUTs would still show the banner, so the call count is the
    // assertion that matters.
    expect(calls).toBe(1)
    expect(entryFor(captured, 7801)).toMatchObject({ spanSize: 1500, revisionCount: 3 })
    // The untouched row rides along with its served values and its OWN revision token.
    expect(entryFor(captured, 7802)).toMatchObject({
      spanSize: 1200,
      length: 12.5,
      culvertPieceCount: 3,
      materialCost: 4000,
      comments: 'Culvert 2',
      revisionCount: 3,
    })
    // The write is scoped to the working context, not to whatever the document body echoed.
    expect(putUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(putUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
  })

  test('a culvert row carries ONLY Delete — saving is page-level, as in legacy', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    expect(culvertPanel(7801).getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    expect(culvertPanel(7801).queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    // Save appears twice at page level — above and below the list, as legacy rendered it.
    expect(screen.getAllByRole('button', { name: 'Save' })).toHaveLength(2)
  })

  test('a stale PUT surfaces the verbatim 409 conflict message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CULVERTS_URL, () =>
        problemBody(409, 'This schedule was changed by another user. Please reload and try again.'),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await screen.findByRole('button', { name: 'Culvert report Id: 1' })
    await savePage(user)

    expect(
      await screen.findByText(
        'This schedule was changed by another user. Please reload and try again.',
      ),
    ).toBeInTheDocument()
  })

  test('an invalid inline edit is blocked before the PUT and names the row on the cost message (S23)', async () => {
    let put = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CULVERTS_URL, () => {
        put = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    const material = culvertPanel(7801).getByLabelText('Material costs ($)')
    await user.clear(material)
    await user.type(material, '100000000')
    await savePage(user)

    // Legacy prefixed the row id onto the cost validator message on list rows only, and the
    // page-level Save is exactly the case where the reporter needs to know which row failed.
    expect(
      await screen.findByText('Id: 1 - Entered cost must be between -99,999,999 and 99,999,999.'),
    ).toBeInTheDocument()
    expect(put).toBe(false)
  })

  test('Save names the blocking rows and reveals the first, even on another page', async () => {
    // Seven culverts (two pages). Row 6 — page 2, and collapsed — is the only invalid one, exactly the
    // legacy-data case: a stored culvert with a NULL piece count.
    const culverts = Array.from({ length: 7 }, (_, index) =>
      culvertAt(7801 + index, index + 1, index === 5 ? { culvertPieceCount: null } : {}),
    )
    let called = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts }))),
      http.put(CULVERTS_URL, () => {
        called = true
        return HttpResponse.json(doc({ culverts }))
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    expect(await screen.findByRole('button', { name: 'Culvert report Id: 1' })).toBeInTheDocument()

    await savePage(user)

    // Without the banner the button reads as dead: no request, no error, no way to find the row.
    expect(await screen.findByText(/Cannot save.*Culvert report Id: 6/)).toBeInTheDocument()
    expect(called).toBe(false)
    // And the offending row is actually reachable — paged to and EXPANDED, not merely named. Asserted
    // on aria-expanded, not toBeVisible(): Carbon collapses an accordion with CSS, which jsdom does not
    // apply, so a visibility assertion here passes just as well on a collapsed row and cannot fail.
    const row6 = await screen.findByRole('button', { name: 'Culvert report Id: 6' })
    await waitFor(() => {
      expect(row6).toHaveAttribute('aria-expanded', 'true')
    })
  })

  test('a REPEAT Save failure re-reveals the same row after the user collapsed it', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ culverts: [culvertAt(7801, 1, { culvertPieceCount: null })] })),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    const row = await screen.findByRole('button', { name: 'Culvert report Id: 1' })

    await savePage(user)
    await waitFor(() => {
      expect(row).toHaveAttribute('aria-expanded', 'true')
    })

    // The reporter collapses it without fixing anything.
    await user.click(row)
    expect(row).toHaveAttribute('aria-expanded', 'false')

    // Save again. Carbon only re-syncs `open` when the prop CHANGES (AccordionItem.js:54-58), so
    // tracking just "the row Save revealed" left the prop stuck at true and this second Save named the
    // row in the banner without ever reopening it.
    await savePage(user)
    expect(await screen.findByText(/Cannot save.*Culvert report Id: 1/)).toBeInTheDocument()
    await waitFor(() => {
      expect(row).toHaveAttribute('aria-expanded', 'true')
    })
  })

  test('Total costs($) previews material + install LIVE, as legacy re-rendered it', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))))
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    const total = () =>
      addPanel()
        .getByText('Total costs($)')
        .parentElement?.querySelector('.schedule-7b__total-value')

    // Blank until a cost is entered — never "0", which would assert a figure the data lacks.
    expect(total()).toHaveTextContent('')

    await user.type(addPanel().getByLabelText('Material costs ($)'), '450')
    await waitFor(() => {
      expect(total()).toHaveTextContent('450')
    })
    await user.type(addPanel().getByLabelText('Install costs($)'), '1500')
    // The figure from the reported legacy screenshot: 450 + 1,500 = 1,950, with no save involved.
    await waitFor(() => {
      expect(total()).toHaveTextContent('1,950')
    })
  })

  test('an UNTOUCHED row shows the SERVED total, not a client recompute (AD-5/BR-05)', async () => {
    // Every other total assertion in this suite uses a fixture whose totalCost equals material +
    // install, so a recompute and the served figure are indistinguishable. Here they diverge on
    // purpose: the server is authoritative, so 9,999 is what an untouched row must show even though
    // the two costs beside it sum to 5,500. (A divergence is not hypothetical — the served total is
    // whatever Schedule7bService computed at write time, and legacy rows predate today's rules.)
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [{ ...mainHaul, totalCost: 9999 }] }))),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    expect(screen.getByText('9,999')).toBeInTheDocument()
    expect(screen.queryByText('5,500')).not.toBeInTheDocument()

    // ...until the reporter edits the row, at which point legacy's live preview takes over and the
    // stale served figure must go.
    const material = culvertPanel(7801).getByLabelText('Material costs ($)')
    await user.clear(material)
    await user.type(material, '2000')
    await waitFor(() => {
      expect(screen.getByText('3,500')).toBeInTheDocument()
    })
    expect(screen.queryByText('9,999')).not.toBeInTheDocument()
  })

  test('an edited row total tracks the edit, not the last-saved figure', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    // Served total is 4,000 + 1,500 = 5,500.
    expect(screen.getByText('5,500')).toBeInTheDocument()

    const material = culvertPanel(7801).getByLabelText('Material costs ($)')
    await user.clear(material)
    await user.type(material, '2000')
    await waitFor(() => {
      expect(screen.getByText('3,500')).toBeInTheDocument()
    })
    // The stale served figure must be gone, not merely joined by the new one.
    expect(screen.queryByText('5,500')).not.toBeInTheDocument()
  })

  test('the total preview is never sent — the server stays the sole authority (AD-5/BR-05)', async () => {
    let captured: CulvertRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))),
      http.post(CULVERTS_URL, async ({ request }) => {
        captured = (await request.json()) as CulvertRequest
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.type(addPanel().getByLabelText('Material costs ($)'), '450')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    await waitFor(() => {
      expect(captured).not.toBeNull()
    })
    expect(captured).not.toHaveProperty('totalCost')
  })

  test('stale inline errors do not survive a successful delete', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ culverts: [culvertAt(7801, 1, { culvertPieceCount: null }), culvertAt(7802, 2)] }),
        ),
      ),
      http.delete(`${CULVERTS_URL}/7802`, () =>
        HttpResponse.json(
          doc({
            culverts: [culvertAt(7801, 1, { culvertPieceCount: null })],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await screen.findByRole('button', { name: 'Culvert report Id: 1' })

    await savePage(user)
    expect(await screen.findByText('Value Required')).toBeInTheDocument()

    await openCulvert(user, 2)
    await user.click(culvertPanel(7802).getByRole('button', { name: 'Delete' }))
    await user.click((await deleteModal()).getByRole('button', { name: 'Yes' }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // Red "Value Required" under a field while the banner says the delete succeeded reads as though the
    // delete itself failed validation.
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('the page-level Save is disabled when the schedule holds no culverts', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))))
    render(<Schedule7b />)

    expect(await screen.findByText(/no culvert reports have been added/i)).toBeInTheDocument()
    for (const button of screen.getAllByRole('button', { name: 'Save' })) {
      expect(button).toBeDisabled()
    }
    // Check Status stays enabled — it is a read-only readiness query, not a write.
    for (const button of screen.getAllByRole('button', { name: 'Check Status' })) {
      expect(button).toBeEnabled()
    }
  })

  test('delete confirms with the legacy Yes/No dialog and reports SUC-002 (S04)', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [mainHaul, culvertAt(7802, 2)] }))),
      http.delete(`${CULVERTS_URL}/7801`, () => {
        deleted = true
        return HttpResponse.json(
          doc({
            culverts: [culvertAt(7802, 1)],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    await user.click(culvertPanel(7801).getByRole('button', { name: 'Delete' }))
    expect(
      await screen.findByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()

    await user.click((await deleteModal()).getByRole('button', { name: 'Yes' }))
    await waitFor(() => {
      expect(deleted).toBe(true)
    })
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('deleting the LAST culvert still reports SUC-002 — 7B has no empty-schedule message (S04)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${CULVERTS_URL}/7801`, () =>
        HttpResponse.json(
          doc({
            culverts: [],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    await user.click(culvertPanel(7801).getByRole('button', { name: 'Delete' }))
    await user.click((await deleteModal()).getByRole('button', { name: 'Yes' }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // "Any data was saved. The Schedule is empty." belongs to Schedule 7A alone; rendering it here
    // would be a fabricated message.
    expect(screen.queryByText(/The Schedule is empty/)).not.toBeInTheDocument()
  })

  test('answering No to the delete confirm sends nothing and leaves the row (S05)', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${CULVERTS_URL}/7801`, () => {
        deleted = true
        return HttpResponse.json(doc({ culverts: [] }))
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    await user.click(culvertPanel(7801).getByRole('button', { name: 'Delete' }))
    await user.click((await deleteModal()).getByRole('button', { name: 'No' }))

    expect(deleted).toBe(false)
    expect(screen.getByRole('button', { name: 'Culvert report Id: 1' })).toBeInTheDocument()
    // A cancelled delete carries no message at all.
    expect(screen.queryByText('Data deleted successfully')).not.toBeInTheDocument()
  })

  test('Check Status renders the type-conditional lines verbatim, spacing included (AC3)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [mainHaul, culvertAt(7802, 2)] }))),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            {
              key: 'missingRequiredFieldMsg',
              text: 'Culvert Report Id : 1 - Culvert Type Round - Span size: Value Required',
            },
            {
              key: 'missingRequiredFieldMsg',
              text: 'Culvert Report Id : 2 - Culvert Type Others - Comments: Value Required',
            },
            {
              key: 'missingRequiredFieldMsg',
              text: 'Culvert Report Id: 2 - Length : Value Required',
            },
          ],
          requirementsMetMessage: null,
        }),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click((await screen.findAllByRole('button', { name: 'Check Status' }))[0])

    // Legacy's own inconsistency must survive byte-for-byte: `Id : ` on the two type-conditional lines,
    // `Id: ` plus a trailing space before the colon on the unconditional ones.
    expect(
      await screen.findByText(
        'Culvert Report Id : 1 - Culvert Type Round - Span size: Value Required',
        { normalizer: verbatim },
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Culvert Report Id : 2 - Culvert Type Others - Comments: Value Required', {
        normalizer: verbatim,
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Culvert Report Id: 2 - Length : Value Required', { normalizer: verbatim }),
    ).toBeInTheDocument()
    // Unlike Schedule 7A there is no per-culvert all-met line, so a schedule with gaps shows errors
    // alone and no schedule-wide banner.
    expect(
      screen.queryByText('All requirements for this schedule have been met'),
    ).not.toBeInTheDocument()
  })

  test('Check Status shows the schedule-wide banner when every culvert passes (SUC-003)', async () => {
    let posts = 0
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () => {
        posts += 1
        return HttpResponse.json({
          requirementsMet: true,
          errors: [],
          requirementsMetMessage: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        })
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click((await screen.findAllByRole('button', { name: 'Check Status' }))[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    // Read-only (BR-07): the check mutates nothing and the page must not follow it with a write.
    expect(posts).toBe(1)
  })

  test('Check Status is offered both above and below the list', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7b />)
    expect(await screen.findAllByRole('button', { name: 'Check Status' })).toHaveLength(2)
  })

  test('no GET fires and ERR-003 renders when mill/year is unset (S11)', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule7b />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
  })

  test.each([
    [
      409,
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
    ],
    [404, 'Schedule not found.'],
    // The action-key denial (S30). The shared handler's text is what ships — assert the render, not
    // legacy's unreachable ERR-005 wording.
    [403, 'You do not have permission to perform this action.'],
  ])(
    'a %i load guard renders its verbatim detail and suppresses the work area (S12, S13, S30)',
    async (status, detail) => {
      server.use(http.get(URL, () => problemBody(status, detail)))
      render(<Schedule7b />)

      expect(await screen.findByText(detail)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Check Status' })).not.toBeInTheDocument()
    },
  )

  test('read-only (editable:false) disables every write control AND Check Status (S14, STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    // Culverts still display, and every control stays PRESENT but disabled — legacy bound `disabled`
    // and never removed a control, so asserting absence here would hide a regression that re-enabled
    // the whole write surface.
    expect(field('Span (mm)')).toHaveValue('1,200')
    expect(field('Span (mm)')).toBeDisabled()
    expect(field('Material costs ($)')).toBeDisabled()
    expect(field('Comments')).toBeDisabled()
    expect(screen.getByRole('combobox', { name: /Type/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(culvertPanel(7801).getByRole('button', { name: 'Delete' })).toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Save' })) {
      expect(button).toBeDisabled()
    }
    // The endpoint permits Check Status at any status (Story 13.1 deviation 6), but legacy disabled the
    // button outside Draft — the parity lives here.
    for (const button of screen.getAllByRole('button', { name: 'Check Status' })) {
      expect(button).toBeDisabled()
    }
  })

  test('a read-only page fires no write when a disabled control is clicked (S14)', async () => {
    let wrote = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))),
      http.put(CULVERTS_URL, () => {
        wrote = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await screen.findByRole('button', { name: 'Culvert report Id: 1' })

    await savePage(user)
    expect(wrote).toBe(false)
  })

  test('the list paginates five culverts per page and page 2 is reachable (S31)', async () => {
    const culverts = Array.from({ length: 7 }, (_, index) => culvertAt(7801 + index, index + 1))
    server.use(http.get(URL, () => HttpResponse.json(doc({ culverts }))))
    const user = userEvent.setup()
    render(<Schedule7b />)

    expect(await screen.findByRole('button', { name: 'Culvert report Id: 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Culvert report Id: 5' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Culvert report Id: 6' })).not.toBeInTheDocument()

    // Without this the whole Pagination control could be removed and the suite would not notice.
    await user.click(screen.getByRole('button', { name: /next page/i }))
    expect(await screen.findByRole('button', { name: 'Culvert report Id: 6' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Culvert report Id: 7' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Culvert report Id: 1' })).not.toBeInTheDocument()
  })

  test('five or fewer culverts render no pagination control (S31)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7b />)
    await screen.findByRole('button', { name: 'Culvert report Id: 1' })
    expect(screen.queryByRole('button', { name: /next page/i })).not.toBeInTheDocument()
  })

  test('deleting the last row of a page falls back, and a later add does not resurrect it (S31)', async () => {
    const six = Array.from({ length: 6 }, (_, index) => culvertAt(7801 + index, index + 1))
    const five = six.slice(0, 5)
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: six }))),
      http.delete(`${CULVERTS_URL}/7806`, () => HttpResponse.json(doc({ culverts: five }))),
      // The add re-grows the schedule to two pages — the state that used to strand the reporter.
      http.post(CULVERTS_URL, () => HttpResponse.json(doc({ culverts: six }))),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)

    await screen.findByRole('button', { name: 'Culvert report Id: 1' })
    await user.click(screen.getByRole('button', { name: /next page/i }))
    await screen.findByRole('button', { name: 'Culvert report Id: 6' })

    await user.click(culvertPanel(7806).getByRole('button', { name: 'Delete' }))
    await user.click((await deleteModal()).getByRole('button', { name: 'Yes' }))

    // Page 2 no longer exists: the reporter must land on page 1, not on an empty slice.
    expect(await screen.findByRole('button', { name: 'Culvert report Id: 1' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /next page/i })).not.toBeInTheDocument()

    // A page number merely clamped at the point of slicing would still hold 2 here, so re-growing the
    // list would silently jump away from the row just added.
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(await screen.findByRole('button', { name: 'Culvert report Id: 1' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Culvert report Id: 6' })).not.toBeInTheDocument()
  })

  test('a culvert stored with null attributes renders blanks and is blocked at Save', async () => {
    // Legacy rows predate even the two required fields, so Check Status exists to flag them. Jackson
    // omits nulls, so these keys arrive ABSENT — the page must not assume a string.
    const bare = { culvertReportId: 7809, rowCounter: 1, revisionCount: 0 } as unknown as Culvert
    let put = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ culverts: [bare] }))),
      http.put(CULVERTS_URL, () => {
        put = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    expect(field('Span (mm)')).toHaveValue('')
    expect(field('Comments')).toHaveValue('')

    // Advisory validation must reject rather than crash on the absent values.
    await savePage(user)
    expect(await screen.findAllByText('Value Required')).toHaveLength(2)
    expect(put).toBe(false)
  })

  test('a whole length still shows its one legacy decimal (12 → 12.0)', async () => {
    // The reported symptom: `NUMBER(7,1)` returns 12.0, JSON carries 12, and a raw seed renders "12"
    // where the legacy screen showed "12.0" (`mask.bigDecimal.6digits1decimal` = `###,##0.0`). The
    // backend already normalizes the scale; the mask is what preserves it through to the field.
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ culverts: [{ ...mainHaul, length: 12, spanSize: 350 }] })),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    expect(field('Length(m)')).toHaveValue('12.0')
    // A three-digit span has no separator to show, which is why the span in the reported screenshot
    // looked correct while the length did not.
    expect(field('Span (mm)')).toHaveValue('350')
  })

  test('every masked numeric field re-formats on blur, not just the money', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    const span = culvertPanel(7801).getByLabelText('Span (mm)')
    await user.clear(span)
    await user.type(span, '1200')
    await user.tab()
    // Legacy re-rendered each field through its converter on every `change` event, so leaving the box
    // grouped what you had typed. Before this, only the two cost fields did.
    expect(span).toHaveValue('1,200')

    const length = culvertPanel(7801).getByLabelText('Length(m)')
    await user.clear(length)
    await user.type(length, '9')
    await user.tab()
    expect(length).toHaveValue('9.0')

    const pieces = culvertPanel(7801).getByLabelText('No of Pieces')
    await user.clear(pieces)
    await user.type(pieces, '1200')
    await user.tab()
    expect(pieces).toHaveValue('1,200')
  })

  test('a masked field keeps unparseable text on screen for correction', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    const span = culvertPanel(7801).getByLabelText('Span (mm)')
    await user.clear(span)
    await user.type(span, 'abc')
    await user.tab()
    // Silently rewriting or blanking a typo would lose what the reporter meant to fix.
    expect(span).toHaveValue('abc')
  })

  test('a typed cost regroups on blur and still crosses the wire ungrouped', async () => {
    let captured: SaveAllBody | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CULVERTS_URL, async ({ request }) => {
        captured = (await request.json()) as SaveAllBody
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    const install = culvertPanel(7801).getByLabelText('Install costs($)')
    await user.clear(install)
    await user.type(install, '1234567')
    await user.tab()
    expect(install).toHaveValue('1,234,567')

    await savePage(user)
    await waitFor(() => {
      expect(captured).not.toBeNull()
    })
    // The separators are display only — a grouped string on the wire would be rejected as non-numeric.
    expect(entryFor(captured, 7801)?.installCost).toBe(1234567)
  })

  test('the comments counter counts UP toward the 3500 limit, and typing stops at the cap', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    // Carbon's counter is used-of-limit, matching the 7A twin (legacy's own counterTemplate counted
    // down — a recorded deviation, decided by the team). 'Main haul road' is 14 characters.
    expect(screen.getByText('14/3500')).toBeInTheDocument()
    await user.type(field('Comments'), '!')
    expect(screen.getByText('15/3500')).toBeInTheDocument()
    // Carbon applies maxLength alongside the counter, reproducing legacy's hard `maxlength="3500"`.
    expect(field('Comments')).toHaveAttribute('maxLength', '3500')
  })

  test('legacy field chrome is reproduced: ten comment rows and autocomplete off', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    // Legacy sized THIS page's comment box `rows="10"` (schedule7B.xhtml:221,490) where its 7A twin
    // used two — a deliberate per-page difference, not a layout choice to normalize.
    expect(field('Comments')).toHaveAttribute('rows', '10')
    // Every legacy inputText carried `autocomplete="off"`.
    for (const label of ['Span (mm)', 'Rise(mm)', 'Length(m)', 'No of Pieces'] as const) {
      expect(field(label)).toHaveAttribute('autocomplete', 'off')
    }
  })

  test('an inline error clears as soon as the user corrects that field', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7b />)
    await openCulvert(user, 1)

    await user.clear(culvertPanel(7801).getByLabelText('No of Pieces'))
    await savePage(user)
    expect(await screen.findByText('Value Required')).toBeInTheDocument()

    await user.type(culvertPanel(7801).getByLabelText('No of Pieces'), '4')
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('closing and reopening the add panel discards the draft and its errors', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ culverts: [] }))))
    const user = userEvent.setup()
    render(<Schedule7b />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await user.type(addPanel().getByLabelText('Span (mm)'), '900')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))
    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Close' }))
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(addPanel().getByLabelText('Span (mm)')).toHaveValue('')
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('a write echo dispatched under a superseded mill/year is discarded', async () => {
    server.use(
      // The module-level `URL` const shadows the global, so match on the raw string.
      http.get(URL, ({ request }) =>
        HttpResponse.json(
          request.url.includes(`millId=${String(DEFAULT_MILL_ID)}`)
            ? doc()
            : doc({ culverts: [culvertAt(8801, 1, { comments: 'Other mill culvert' })] }),
        ),
      ),
      http.put(CULVERTS_URL, async () => {
        // Land after the context has already moved on.
        await delay(60)
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<ContextSwitchHarness />)
    await screen.findByRole('button', { name: 'Culvert report Id: 1' })

    await savePage(user)
    await user.click(screen.getByRole('button', { name: 'switch context' }))

    // The stale echo must not repaint the new context's document or its banner.
    expect(await screen.findByRole('button', { name: 'Culvert report Id: 1' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    })
  })
})
