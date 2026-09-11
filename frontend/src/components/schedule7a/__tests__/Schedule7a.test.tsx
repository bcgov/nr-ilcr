import type { ReactNode } from 'react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import {
  declaredRole,
  getDefaultNormalizer,
  render,
  renderAsAdmin,
  renderAsSubmitter,
  screen,
  waitFor,
  within,
} from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule7a from '@/components/schedule7a'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import type BridgeRequest from '@/interfaces/Schedule7aRequest'
import type { Bridge } from '@/interfaces/Schedule7aResponse'
import type { IlcrRole } from '@/context/auth/mockUsers'
import { ILCR_ROLES } from '@/context/auth/mockUsers'

const URL = 'http://localhost:3000/api/v1/schedule7a'
const BRIDGES_URL = `${URL}/bridges`
const CHECK_URL = `${URL}/check-status`

const CODE_LISTS = {
  constructionTypes: [
    { code: 'N', description: 'New' },
    { code: 'U', description: 'Used' },
  ],
  superstructureTypes: [{ code: 'STL', description: 'Steel' }],
  deckTypes: [{ code: 'WD', description: 'Wood' }],
  abutmentTypes: [{ code: 'CONC', description: 'Concrete' }],
  loadRatings: [{ code: 'L100', description: 'L-100' }],
}

const northFork: Bridge = {
  bridgeReportId: 7001,
  rowCounter: 1,
  locationName: 'North Fork Bridge',
  builtDate: '2020-06',
  constructionTypeCode: 'N',
  superstructureTypeCode: 'STL',
  deckTypeCode: 'WD',
  abutmentTypeCode: 'CONC',
  loadRatingCode: 'L100',
  lifeSpan: 50,
  abutmentHeight: 5.0,
  length: 20.0,
  width: 4.0,
  distance: 12,
  sitePlanCost: 1000,
  superstructureMaterialCost: 5000,
  superstructureDeliverCost: 500,
  superstructureInstallCost: 800,
  abutmentMaterialCost: 3000,
  abutmentDeliverCost: 300,
  abutmentInstallCost: 400,
  approachCost: 700,
  afterInstallCost: 200,
  otherCost: 100,
  comments: 'Spans the north fork',
  totalMaterial: 8000,
  totalDeliver: 800,
  totalInstall: 1200,
  grandTotal: 12000,
  revisionCount: 3,
}

const bridgeAt = (id: number, rowCounter: number, overrides: Partial<Bridge> = {}): Bridge => ({
  ...northFork,
  bridgeReportId: id,
  rowCounter,
  locationName: `Bridge ${String(rowCounter)}`,
  ...overrides,
})

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  bridges: [northFork],
  codeLists: CODE_LISTS,
  ...overrides,
})

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

// Preserves the literal whitespace the backend sends (the check-status double space, the ERR-001
// trailing space) so verbatim-rendering assertions are not defeated by the default collapse/trim.
const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

// The accordion body for one bridge, opened so its fields are reachable.
async function openBridge(user: ReturnType<typeof userEvent.setup>, rowCounter: number) {
  await user.click(
    await screen.findByRole('button', { name: `Bridge report Id: ${String(rowCounter)}` }),
  )
}

const field = (label: RegExp | string) => screen.getByLabelText(label)

// Scope to one bridge's accordion panel. Every row renders its own editor at once, so an unscoped
// field query matches as many elements as there are bridges.
const bridgePanel = (bridgeReportId: number) =>
  within(
    document
      .getElementById(`bridge-${String(bridgeReportId)}-locationName`)
      ?.closest('.cds--accordion__item') as HTMLElement,
  )

// The Carbon Modal root, so its confirm button is distinguishable from the row action that opened it.
const deleteModal = async () => within(await screen.findByRole('presentation'))

// Save is a PAGE-level action covering every bridge at once (legacy parity) — a bridge row carries
// only Delete — so it drives every write test. It renders above and below the list; either serves.
const savePage = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getAllByRole('button', { name: 'Save' })[0])

type SaveAllBody = { bridges: { bridgeReportId: number; bridge: BridgeRequest }[] }

// The page-level Save sends the whole schedule, so a test asserting on "the request for bridge N"
// pulls that bridge's entry out of the batch.
const entryFor = (body: SaveAllBody | null, bridgeReportId: number): BridgeRequest | undefined =>
  body?.bridges.find((entry) => entry.bridgeReportId === bridgeReportId)?.bridge

// Drives a mill/year change mid-flight so the stale-response guard can be exercised. Module-level so
// it is not re-created per render (an @eslint-react rule forbids nested component definitions).
const ContextSwitchHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(600, 2019)}>
        switch context
      </button>
      <Schedule7a />
    </>
  )
}

// The Add panel's own fields (it renders BridgeFields with idPrefix="add"). Scoped like
// `bridgePanel` because the bridge rows carry the SAME labels — collapsed, but still in the DOM — so
// an unscoped query is ambiguous the moment the schedule holds a bridge.
const addPanel = () =>
  within(
    document.getElementById('add-locationName')?.closest('.schedule-7a__section') as HTMLElement,
  )

// Enter a value into one Add-panel text field. Uses click+paste rather than `user.type` because
// every bridge row renders its own editor at once (see `bridgePanel`), so each character typed into
// the Add panel re-renders all of them. With the six bridges the pagination test below sets up, the
// 38 characters of this form cost ~5.6s — a third of the budget of the suite's slowest test, which
// was failing CI on the 20s timeout. Paste is a real user interaction that fires one input event per
// field, so the fields still go through the component's own onChange/validation path; it drops that
// to one re-render per field and cuts the test from ~11.6s to ~7.9s under coverage, with identical
// line/branch coverage of index.tsx and BridgeFields.tsx.
const enterField = async (
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  value: string,
) => {
  await user.click(addPanel().getByLabelText(label))
  await user.paste(value)
}

// Fill the Add panel with a complete, valid bridge.
async function fillAddForm(user: ReturnType<typeof userEvent.setup>) {
  await enterField(user, 'Name/Location of Bridge', 'South Creek Bridge')
  await enterField(user, 'Date', '2021-03')
  await enterField(user, 'Expected Life Span', '40')
  await enterField(user, 'Abutments Ht.(m)', '3.5')
  await enterField(user, 'Length (m)', '15.0')
  await enterField(user, 'Width (m)', '4.0')
  await enterField(user, 'Distance (km)', '8')
  for (const [name, option] of [
    ['New/Used', 'New'],
    ['Superstructure Type', 'Steel'],
    ['Decking Type', 'Wood'],
    ['Abutments Type', 'Concrete'],
    ['Load Rating', 'L-100'],
  ] as const) {
    await user.click(addPanel().getByRole('combobox', { name: new RegExp(name, 'i') }))
    await user.click(await addPanel().findByRole('option', { name: option }))
  }
}

describe('Schedule 7A page', () => {
  // ---- Defect #291: the four totals track entry, on blur. ----------------------------------------
  //
  // The `northFork` fixture is self-consistent — 8,000 / 800 / 1,200 / 12,000 all satisfy
  // Schedule7aService's formulas — so the first test below is a genuine mirror-vs-server comparison.

  /**
   * One of the four total values inside a container. Scoped to the `.schedule-7a__total` blocks and
   * matched on the label EXACTLY: the cost-field labels also contain "Material"/"Deliver"/"Install",
   * so a bare getByText matches several elements.
   */
  const totalIn = (container: HTMLElement, label: string): string | null | undefined => {
    const block = [...container.querySelectorAll('.schedule-7a__total')].find(
      (el) => el.firstElementChild?.textContent === label,
    )
    return block?.querySelector('.schedule-7a__total-value')?.textContent
  }

  const bridgeContainer = (bridgeReportId: number): HTMLElement =>
    document
      .getElementById(`bridge-${String(bridgeReportId)}-locationName`)
      ?.closest('.cds--accordion__item') as HTMLElement

  test('the mirror reproduces the served totals exactly (#291 AC5)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    const panel = bridgeContainer(7001)
    // Committing a cost WITHOUT changing it hands the row to the mirror, so these four figures now
    // come from the client and must still equal what the server sent.
    await user.click(bridgePanel(7001).getByLabelText(/Superstructure.*Material/i))
    await user.tab()

    expect(totalIn(panel, 'Material')).toBe('8,000')
    expect(totalIn(panel, 'Deliver')).toBe('800')
    expect(totalIn(panel, 'Install')).toBe('1,200')
    expect(totalIn(panel, 'Grand Total ($)')).toBe('12,000')
  })

  test('typing alone moves nothing; blurring a cost recalculates the totals (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    const panel = bridgeContainer(7001)
    const material = bridgePanel(7001).getByLabelText(/Superstructure.*Material/i)
    await user.clear(material)
    await user.type(material, '7000')
    expect(totalIn(panel, 'Material')).toBe('8,000') // not per keystroke

    await user.tab()
    expect(totalIn(panel, 'Material')).toBe('10,000') // 7000 + 3000 abutment
    expect(totalIn(panel, 'Grand Total ($)')).toBe('14,000') // 12,000 + 2,000
    // The other two pair totals are untouched.
    expect(totalIn(panel, 'Deliver')).toBe('800')
    expect(totalIn(panel, 'Install')).toBe('1,200')
  })

  test('clearing both halves blanks that total rather than showing 0 (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    const panel = bridgeContainer(7001)
    await user.clear(bridgePanel(7001).getByLabelText(/Superstructure.*Material/i))
    await user.tab()
    await user.clear(bridgePanel(7001).getByLabelText(/Abutment.*Material/i))
    await user.tab()

    expect(totalIn(panel, 'Material')).toBe('') // null, not 0
    expect(totalIn(panel, 'Grand Total ($)')).toBe('4,000') // 12,000 less the 8,000 material
  })

  test('the Add panel shows totals as soon as costs are committed (#291)', async () => {
    // It previously passed no `totals` at all, so all four read blank for the whole of entry.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^add$/i }))
    const addPanel = document
      .getElementById('add-locationName')
      ?.closest('.schedule-7a__section') as HTMLElement
    expect(totalIn(addPanel, 'Grand Total ($)')).toBe('')

    await user.type(within(addPanel).getByLabelText(/Superstructure.*Material/i), '5000')
    await user.tab()
    expect(totalIn(addPanel, 'Material')).toBe('5,000')
    expect(totalIn(addPanel, 'Grand Total ($)')).toBe('5,000')
  })

  test('the Save echo supersedes the row mirror — totals follow the server (#291 AC5)', async () => {
    // The invariant the whole design rests on, and the one no test in this batch asserted: after a
    // Save the derived cells must come from the echo, not from the pre-save client snapshot. Proven
    // broken by the code review — `rowCommitted` was never cleared, so the input reverted to the
    // echoed value while the total kept the stale mirror, permanently.
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      // The echo re-serves the ORIGINAL bridge, as a server that rejected or normalized the entry
      // would: the totals must snap back to 8,000 / 12,000.
      http.put(BRIDGES_URL, () => HttpResponse.json(doc())),
    )
    render(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    const panel = bridgeContainer(7001)
    const material = bridgePanel(7001).getByLabelText(/Superstructure.*Material/i)
    await user.clear(material)
    await user.type(material, '7000')
    await user.tab()
    expect(totalIn(panel, 'Material')).toBe('10,000') // the mirror, pre-Save

    await savePage(user)
    await waitFor(() => {
      expect(bridgePanel(7001).getByLabelText(/Superstructure.*Material/i)).toHaveValue('5,000')
    })
    // The field reverted to the echo; the total must have too.
    expect(totalIn(panel, 'Material')).toBe('8,000')
    expect(totalIn(panel, 'Grand Total ($)')).toBe('12,000')
  })

  test('a blur that changes nothing does not hand an untouched row to the mirror (#291 AC7)', async () => {
    // Legacy fired on `change`; a tab-through is not a change. Without this guard a stray Tab replaced
    // the served totals with a client recomputation, bypassing the AC7 test below.
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ bridges: [{ ...northFork, grandTotal: 999999 }] })),
      ),
    )
    render(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    const material = bridgePanel(7001).getByLabelText(/Superstructure.*Material/i)
    await user.click(material)
    await user.tab()

    expect(totalIn(bridgeContainer(7001), 'Grand Total ($)')).toBe('999,999')
  })

  test('an untouched row keeps the served totals — no client recomputation (#291 AC7)', async () => {
    // A stored grand total that disagrees with its own components: until the reporter commits a cost,
    // the row must show the server's figure, not a recomputed one.
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ bridges: [{ ...northFork, grandTotal: 999999 }] })),
      ),
    )
    render(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    expect(totalIn(bridgeContainer(7001), 'Grand Total ($)')).toBe('999,999')
  })

  test('renders each bridge as an accordion row with legacy labels and server totals (AC1, AC2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)

    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    await openBridge(user, 1)

    expect(field('Name/Location of Bridge')).toHaveValue('North Fork Bridge')
    expect(field('Date')).toHaveValue('2020-06')
    expect(field('Expected Life Span')).toHaveValue('50')
    expect(field('Distance (km)')).toHaveValue('12')
    // Money displays grouped, as the legacy costConverter formatted it. The measurements above do
    // not — legacy grouped costs only.
    expect(field('Site Plan / Gen. Arr. ($)')).toHaveValue('1,000')
    expect(field('Superstructure Material ($)')).toHaveValue('5,000')
    expect(field('Certification After install ($)')).toHaveValue('200')

    // Server-computed totals render as plain text (the schedule-3/4 derived-value style), never as
    // a field. Asserting the absence of a control matters as much as the value: a page that let a
    // reporter type over a server total would still pass a value-only check.
    expect(screen.getByText('12,000')).toBeInTheDocument()
    expect(screen.getByText('8,000')).toBeInTheDocument()
    expect(screen.queryByLabelText('Grand Total ($)')).not.toBeInTheDocument()
  })

  test('a null total renders blank, never 0 (AC2)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({
            bridges: [
              {
                ...northFork,
                totalMaterial: null,
                totalDeliver: null,
                totalInstall: null,
                grandTotal: null,
              },
            ],
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Assert every total's value node is genuinely EMPTY. Merely excluding the literal "0" would
    // also pass on "12,000", on an em dash, or on a component that failed to render at all.
    const values = document.querySelectorAll('.schedule-7a__total-value')
    expect(values).toHaveLength(4)
    for (const value of values) {
      expect(value).toHaveTextContent('')
    }
    expect(screen.queryByText('12,000')).not.toBeInTheDocument()
  })

  test('an empty bridge list is a valid document, not an error (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))))
    render(<Schedule7a />)

    expect(await screen.findByText(/no bridge reports have been added/i)).toBeInTheDocument()
    expect(screen.queryByText(/unable to load/i)).not.toBeInTheDocument()
  })

  test('Add/Close toggles the add panel and Add Report POSTs, showing the verbatim success (AC3)', async () => {
    let captured: BridgeRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, async ({ request }) => {
        captured = (await request.json()) as BridgeRequest
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    expect(screen.getByText('Add a Bridge report')).toBeInTheDocument()

    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toMatchObject({
      locationName: 'South Creek Bridge',
      builtDate: '2021-03',
      constructionTypeCode: 'N',
      loadRatingCode: 'L100',
      lifeSpan: 40,
      abutmentHeight: 3.5,
      distance: 8,
    })
    // Derived figures are server-owned and must never be sent.
    expect(captured).not.toHaveProperty('grandTotal')
    expect(captured).not.toHaveProperty('rowCounter')
  })

  test('a rejected add keeps entered values and shows the verbatim server detail (AC3)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, () =>
        problemBody(400, 'The date is not valid. Enter date in format: YYYY-MM.'),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(
      await screen.findByText('The date is not valid. Enter date in format: YYYY-MM.'),
    ).toBeInTheDocument()
    expect(field('Name/Location of Bridge')).toHaveValue('South Creek Bridge')
  })

  test('advisory validation blocks a doomed add and never fires the request (AC8)', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await user.type(field('Name/Location of Bridge'), 'Incomplete Bridge')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    // Twelve fields are required and only the name was filled, so eleven must be flagged. Asserting
    // the exact count is what stops validateBridge from short-circuiting after the first failure.
    expect(await screen.findAllByText('Value Required')).toHaveLength(11)
    expect(posted).toBe(false)
  })

  test('an inline correction PUTs with the row revisionCount read from the document (AC4)', async () => {
    let captured: SaveAllBody | null = null
    let putUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(BRIDGES_URL, async ({ request }) => {
        captured = (await request.json()) as SaveAllBody
        putUrl = request.url
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Site Plan / Gen. Arr. ($)'))
    await user.type(field('Site Plan / Gen. Arr. ($)'), '2000')
    await savePage(user)

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(entryFor(captured, 7001)).toMatchObject({ sitePlanCost: 2000, revisionCount: 3 })
    // The write is scoped to the working context, not to whatever the document body echoed.
    expect(putUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(putUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
  })

  test('editing one field on an untouched row preserves the other served values (AC4)', async () => {
    let captured: SaveAllBody | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(BRIDGES_URL, async ({ request }) => {
        captured = (await request.json()) as SaveAllBody
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Width (m)'))
    await user.type(field('Width (m)'), '6.5')
    await savePage(user)

    await waitFor(() => {
      expect(captured).not.toBeNull()
    })
    // The edited field changes; every other field still carries what the document served.
    expect(entryFor(captured, 7001)).toMatchObject({
      width: 6.5,
      locationName: 'North Fork Bridge',
      builtDate: '2020-06',
      superstructureTypeCode: 'STL',
      lifeSpan: 50,
      length: 20,
      distance: 12,
      abutmentMaterialCost: 3000,
      otherCost: 100,
      comments: 'Spans the north fork',
    })
  })

  test('a bridge row carries ONLY Delete — saving is page-level, as in legacy', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Legacy gave a row no Save and no Cancel (schedule7A.xhtml:1237 is the whole row action set).
    expect(bridgePanel(7001).getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    expect(bridgePanel(7001).queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(bridgePanel(7001).queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
    // Save appears twice at page level — above and below the list, as legacy rendered it.
    expect(screen.getAllByRole('button', { name: 'Save' })).toHaveLength(2)
  })

  test('a stale PUT surfaces the verbatim 409 conflict message (AC4)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(BRIDGES_URL, () =>
        problemBody(409, 'This schedule was changed by another user. Please reload and try again.'),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)
    await savePage(user)

    expect(
      await screen.findByText(
        'This schedule was changed by another user. Please reload and try again.',
      ),
    ).toBeInTheDocument()
  })

  test('delete confirms then reports the remaining-bridges message (AC5)', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.delete(`${BRIDGES_URL}/7001`, () => {
        deleted = true
        return HttpResponse.json(
          doc({
            bridges: [bridgeAt(7002, 1)],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    expect(
      await screen.findByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()

    await user.click((await deleteModal()).getByRole('button', { name: 'Yes' }))
    await waitFor(() => {
      expect(deleted).toBe(true)
    })
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('deleting the last bridge reports the empty-schedule message (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${BRIDGES_URL}/7001`, () =>
        HttpResponse.json(
          doc({
            bridges: [],
            message: {
              key: 'anyDataToSaveInfoMsg',
              text: 'Any data was saved. The Schedule is empty.',
            },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    await user.click((await deleteModal()).getByRole('button', { name: 'Yes' }))

    expect(
      await screen.findByText('Any data was saved. The Schedule is empty.'),
    ).toBeInTheDocument()
  })

  test('the page-level Save PUTs every bridge in ONE request, edited or not (legacy Save)', async () => {
    let captured: { bridges: { bridgeReportId: number; bridge: BridgeRequest }[] } | null = null
    let calls = 0
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.put(BRIDGES_URL, async ({ request }) => {
        calls += 1
        captured = (await request.json()) as typeof captured
        return HttpResponse.json(
          doc({
            bridges: [northFork, bridgeAt(7002, 2)],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)
    const rowOneName = bridgePanel(7001).getByLabelText('Name/Location of Bridge')
    await user.clear(rowOneName)
    await user.type(rowOneName, 'Edited Fork')

    await user.click(screen.getAllByRole('button', { name: 'Save' })[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // ONE request for the whole schedule — the point of the page-level Save. N per-row PUTs would
    // still show the success banner, so the call count is the assertion that matters.
    expect(calls).toBe(1)
    const body = captured as unknown as {
      bridges: { bridgeReportId: number; bridge: BridgeRequest }[]
    }
    expect(body.bridges).toHaveLength(2)
    expect(body.bridges[0].bridgeReportId).toBe(7001)
    expect(body.bridges[0].bridge.locationName).toBe('Edited Fork')
    // The untouched row rides along with its served values and its OWN revision token.
    expect(body.bridges[1].bridgeReportId).toBe(7002)
    expect(body.bridges[1].bridge.locationName).toBe('Bridge 2')
    expect(body.bridges[1].bridge.revisionCount).toBe(3)
  })

  test('the page-level Save validates every row and sends nothing when one is invalid', async () => {
    let called = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.put(BRIDGES_URL, () => {
        called = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)
    await user.clear(bridgePanel(7001).getByLabelText('Name/Location of Bridge'))

    await savePage(user)

    expect(await screen.findByText('Value Required')).toBeInTheDocument()
    // The server saves the batch atomically, so a body with a known-bad row could only be rejected
    // whole — the reporter would then have to guess which row was at fault.
    expect(called).toBe(false)
  })

  test('Save names the blocking rows and reveals the first, even on another page', async () => {
    // Seven bridges (two pages). Row 6 — page 2, and collapsed — is the only invalid one, exactly
    // the legacy-data case: a stored bridge with NULL required attributes.
    const bridges = Array.from({ length: 7 }, (_, index) =>
      bridgeAt(7001 + index, index + 1, index === 5 ? { locationName: null, distance: null } : {}),
    )
    let called = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges }))),
      http.put(BRIDGES_URL, () => {
        called = true
        return HttpResponse.json(doc({ bridges }))
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()

    await savePage(user)

    // Without the banner the button reads as dead: no request, no error, no way to find the row.
    expect(await screen.findByText(/Cannot save.*Bridge report Id: 6/)).toBeInTheDocument()
    expect(called).toBe(false)
    // And the offending row is actually reachable — paged to and expanded, not merely named.
    expect(await screen.findByRole('button', { name: 'Bridge report Id: 6' })).toBeInTheDocument()
    await waitFor(() => {
      expect(document.getElementById('bridge-7006-locationName')).toBeVisible()
    })
  })

  test('the page-level Save is disabled when the schedule holds no bridges', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))))
    render(<Schedule7a />)

    expect(await screen.findByText(/no bridge reports have been added/i)).toBeInTheDocument()
    for (const button of screen.getAllByRole('button', { name: 'Save' })) {
      expect(button).toBeDisabled()
    }
    // Check Status stays enabled — it is a read-only readiness query, not a write.
    for (const button of screen.getAllByRole('button', { name: 'Check Status' })) {
      expect(button).toBeEnabled()
    }
  })

  test('a typed cost regroups on blur and still crosses the wire ungrouped', async () => {
    let captured: SaveAllBody | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(BRIDGES_URL, async ({ request }) => {
        captured = (await request.json()) as SaveAllBody
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Other Costs ($)'))
    await user.type(field('Other Costs ($)'), '1234567')
    await user.tab()
    expect(field('Other Costs ($)')).toHaveValue('1,234,567')

    await savePage(user)
    await waitFor(() => {
      expect(captured).not.toBeNull()
    })
    // The separators are display only — a grouped string on the wire would be rejected as non-numeric.
    expect(entryFor(captured, 7001)?.otherCost).toBe(1234567)
  })

  test('the comments counter shows characters remaining (#312 Overall 10)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Remaining, not used-of-limit (legacy wording). 'Spans the north fork' is 20 characters.
    expect(screen.getByText('3480 characters remaining')).toBeInTheDocument()
    await user.type(field('Comments'), '!')
    expect(screen.getByText('3479 characters remaining')).toBeInTheDocument()
  })

  test('Check Status renders per-bridge failures and no schedule banner on mixed results (AC6)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            {
              key: 'missingRequiredFieldMsg',
              text: 'Bridge Report Id : 2 - Site Plan / Gen. Arr.  Cost : Value Required',
            },
          ],
          bridgeMessages: [
            { key: 'bridgeRequirementsMetMsg', text: 'All requirements for 1 have been met.' },
          ],
          requirementsMetMessage: null,
        }),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click((await screen.findAllByRole('button', { name: 'Check Status' }))[0])

    // The legacy label carries a double space before "Cost" — it must survive verbatim.
    expect(
      await screen.findByText(
        'Bridge Report Id : 2 - Site Plan / Gen. Arr.  Cost : Value Required',
        {
          normalizer: verbatim,
        },
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('All requirements for 1 have been met.')).toBeInTheDocument()
    expect(
      screen.queryByText('All requirements for this schedule have been met'),
    ).not.toBeInTheDocument()
  })

  test('Check Status shows the schedule-wide banner only when every bridge passes (AC6)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: true,
          errors: [],
          // Empty on an all-pass result: legacy emitted its per-bridge lines only when the schedule
          // as a whole failed, so the API sends the schedule-wide message alone (Schedule7aService).
          bridgeMessages: [],
          requirementsMetMessage: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        }),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click((await screen.findAllByRole('button', { name: 'Check Status' }))[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    // One success banner, not one per bridge plus a schedule-wide one.
    expect(screen.queryByText(/All requirements for 1 have been met/)).not.toBeInTheDocument()
  })

  test('Check Status is offered both above and below the list (AC6)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    expect(await screen.findAllByRole('button', { name: 'Check Status' })).toHaveLength(2)
  })

  test('no GET fires and ERR-001 renders when mill/year is unset (AC7)', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule7a />
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
  ])(
    'a %i load guard renders its verbatim detail and suppresses the work area (AC7)',
    async (status, detail) => {
      server.use(http.get(URL, () => problemBody(status, detail)))
      render(<Schedule7a />)

      expect(await screen.findByText(detail)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Check Status' })).not.toBeInTheDocument()
    },
  )

  test('read-only (editable:false) disables every write control AND Check Status (AC7)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Bridges still display, and every control stays PRESENT but disabled — legacy bound `disabled`
    // and never removed a control, so asserting absence here would hide a regression that re-enabled
    // the whole write surface.
    expect(field('Name/Location of Bridge')).toHaveValue('North Fork Bridge')
    expect(field('Name/Location of Bridge')).toBeDisabled()
    expect(field('Site Plan / Gen. Arr. ($)')).toBeDisabled()
    expect(screen.getByRole('combobox', { name: /New\/Used/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(bridgePanel(7001).getByRole('button', { name: 'Delete' })).toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Save' })) {
      expect(button).toBeDisabled()
    }
    // Legacy disabled Check Status outside Draft too, even though the endpoint permits it.
    for (const button of screen.getAllByRole('button', { name: 'Check Status' })) {
      expect(button).toBeDisabled()
    }
  })

  test('a read-only page fires no write when a disabled control is clicked (AC7)', async () => {
    let wrote = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))),
      http.put(BRIDGES_URL, () => {
        wrote = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await savePage(user)
    expect(wrote).toBe(false)
  })

  test('the list paginates five bridges per page and page 2 is reachable (AC1)', async () => {
    const bridges = Array.from({ length: 7 }, (_, index) => bridgeAt(7001 + index, index + 1))
    server.use(http.get(URL, () => HttpResponse.json(doc({ bridges }))))
    const user = userEvent.setup()
    render(<Schedule7a />)

    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bridge report Id: 5' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bridge report Id: 6' })).not.toBeInTheDocument()

    // Without this the whole Pagination control could be removed and the suite would not notice.
    await user.click(screen.getByRole('button', { name: /next page/i }))
    expect(await screen.findByRole('button', { name: 'Bridge report Id: 6' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bridge report Id: 7' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bridge report Id: 1' })).not.toBeInTheDocument()
  })

  test('five or fewer bridges render no pagination control (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    await screen.findByRole('button', { name: 'Bridge report Id: 1' })
    expect(screen.queryByRole('button', { name: /next page/i })).not.toBeInTheDocument()
  })

  test('deleting the last row of a page falls back, and a later add does not resurrect it (AC1)', async () => {
    const six = Array.from({ length: 6 }, (_, index) => bridgeAt(7001 + index, index + 1))
    const five = six.slice(0, 5)
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: six }))),
      http.delete(`${BRIDGES_URL}/7006`, () => HttpResponse.json(doc({ bridges: five }))),
      // The add re-grows the schedule to two pages — the state that used to strand the reporter.
      http.post(BRIDGES_URL, () => HttpResponse.json(doc({ bridges: six }))),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await screen.findByRole('button', { name: 'Bridge report Id: 1' })
    await user.click(screen.getByRole('button', { name: /next page/i }))
    await screen.findByRole('button', { name: 'Bridge report Id: 6' })

    await user.click(bridgePanel(7006).getByRole('button', { name: 'Delete' }))
    await user.click((await deleteModal()).getByRole('button', { name: 'Yes' }))

    // Page 2 no longer exists: the reporter must land on page 1, not on an empty slice.
    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /next page/i })).not.toBeInTheDocument()

    // A page number merely clamped at the point of slicing would still hold 2 here, so re-growing
    // the list would silently jump away from the row just added.
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bridge report Id: 6' })).not.toBeInTheDocument()
    // The suite's heaviest scenario by a wide margin: six bridges (every row renders its own full
    // editor, so ~800ms per Add-panel field interaction under coverage) plus a complete add flow.
    // It runs ~8s locally under coverage and ~2x that on the shared runner, so the suite-wide 20s in
    // vitest.config.ts left no headroom and it failed on wall-clock alone. Scoped here rather than
    // raising the global timeout, which would blunt hang detection for the other 485 tests.
  }, 60_000)

  test('a bridge stored with null attributes renders blanks and saves without throwing (AC2, AC4)', async () => {
    // Legacy rows predate the required-field validation, so Check Status exists to flag them. Jackson
    // omits nulls, so these keys arrive ABSENT — the page must not assume a string.
    const bare = {
      bridgeReportId: 7009,
      rowCounter: 1,
      revisionCount: 0,
    } as unknown as Bridge
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [bare] }))),
      http.put(BRIDGES_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    expect(field('Name/Location of Bridge')).toHaveValue('')
    expect(field('Distance (km)')).toHaveValue('')

    // Advisory validation must reject rather than crash on the absent values.
    await savePage(user)
    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(posted).toBe(false)
  })

  test('every open row re-derives from the echo once the page Save persists them all (AC4)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.put(BRIDGES_URL, () =>
        HttpResponse.json(
          doc({
            bridges: [northFork, bridgeAt(7002, 2)],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)
    await openBridge(user, 2)

    const rowOneName = document.getElementById('bridge-7001-locationName') as HTMLInputElement
    await user.clear(rowOneName)
    await user.type(rowOneName, 'EDITED')

    await savePage(user)
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    // The page Save sends EVERY row, so afterwards no editor holds work the server has not seen —
    // each re-derives from the echoed document. A row still showing local text would mean an edit
    // was silently kept outside the persisted state.
    await waitFor(() => {
      expect(document.getElementById('bridge-7001-locationName')).toHaveValue('North Fork Bridge')
    })
    expect(document.getElementById('bridge-7002-locationName')).toHaveValue('Bridge 2')
  })

  test('a dropdown shows the served code description, not a blank placeholder (AC2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Reading selectedItem back is what a hardcoded `selectedItem={null}` would break.
    expect(screen.getByRole('combobox', { name: /New\/Used/i })).toHaveTextContent('New')
    expect(screen.getByRole('combobox', { name: /Superstructure Type/i })).toHaveTextContent(
      'Steel',
    )
    expect(screen.getByRole('combobox', { name: /Decking Type/i })).toHaveTextContent('Wood')
    expect(screen.getByRole('combobox', { name: /Abutments Type/i })).toHaveTextContent('Concrete')
    expect(screen.getByRole('combobox', { name: /Load Rating/i })).toHaveTextContent('L-100')
  })

  test('a fractional cost is rounded and a blank cost is sent as null, not zero (AC3)', async () => {
    let captured: BridgeRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, async ({ request }) => {
        captured = (await request.json()) as BridgeRequest
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    // Half-away-from-zero, matching Oracle — the Integer wire would otherwise truncate to 1.
    await user.type(field('Other Costs ($)'), '1.5')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    await waitFor(() => {
      expect(captured).not.toBeNull()
    })
    expect(captured).toMatchObject({ otherCost: 2 })
    // "Not entered" must stay distinguishable from zero.
    expect((captured as unknown as BridgeRequest).sitePlanCost).toBeNull()
    expect((captured as unknown as BridgeRequest).approachCost).toBeNull()
    expect((captured as unknown as BridgeRequest).comments).toBeNull()
  })

  test('an invalid inline edit is blocked before the PUT (AC4, AC8)', async () => {
    let put = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(BRIDGES_URL, () => {
        put = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Length (m)'))
    await user.type(field('Length (m)'), '99999')
    await savePage(user)

    expect(
      await screen.findByText('Entered bridge length must be between 0.0 and 9,999.9'),
    ).toBeInTheDocument()
    expect(put).toBe(false)
  })

  test('an inline error clears as soon as the user corrects that field (AC8)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Length (m)'))
    await savePage(user)
    expect(await screen.findByText('Value Required')).toBeInTheDocument()

    await user.type(field('Length (m)'), '15.0')
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('closing and reopening the add panel discards the draft and its errors (AC3)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))))
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await user.type(field('Name/Location of Bridge'), 'Half typed')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))
    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Close' }))
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(field('Name/Location of Bridge')).toHaveValue('')
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('a write echo dispatched under a superseded mill/year is discarded (AC4)', async () => {
    server.use(
      // The module-level `URL` const shadows the global, so match on the raw string.
      http.get(URL, ({ request }) =>
        HttpResponse.json(
          request.url.includes(`millId=${String(DEFAULT_MILL_ID)}`)
            ? doc()
            : doc({ bridges: [bridgeAt(8001, 1, { locationName: 'Other Mill Bridge' })] }),
        ),
      ),
      http.put(BRIDGES_URL, async () => {
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
    await openBridge(user, 1)

    await savePage(user)
    await user.click(screen.getByRole('button', { name: 'switch context' }))

    // The stale echo must not repaint the new context's document or its banner.
    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    })
  })
})

// Story 30.3 / #312 Overall 6. `renderIcon` puts an <svg> inside the button and leaves the accessible
// name as the label text, so a by-name lookup still finds the button AND proves the decorative icon is
// there — a later edit that drops an icon fails here. Added for the #381 review (paulushcgcj): this
// page's action bar and add-new trigger were still text-only after 30.3 reached the shared bars.
describe('Schedule 7A action icons (Story 30.3 / #312 Overall 6)', () => {
  test('Save, Check Status and the Add toggle all carry their icon', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)

    // Save + Check Status come from the shared SaveCheckActions bar, which legacy renders both above
    // and below the list — so assert every instance.
    for (const name of [/^save$/i, /check status/i]) {
      for (const button of await screen.findAllByRole('button', { name })) {
        expect(button.querySelector('svg')).not.toBeNull()
      }
    }

    const toggle = screen.getByRole('button', { name: 'Add' })
    expect(toggle.querySelector('svg')).not.toBeNull()
    await user.click(toggle)
    expect(screen.getByRole('button', { name: 'Close' }).querySelector('svg')).not.toBeNull()
    expect(screen.getByRole('button', { name: 'Add Report' }).querySelector('svg')).not.toBeNull()
  })
})

// ---- Story 16.3: the ministry correction journey at Submitted ------------------------------------
//
// Story 16.1 shipped the role×status editability matrix: ILCR_SUBMITTER may edit only at Draft, while
// ILCR_ADMIN may edit at Submitted and Verified and is deliberately READ-ONLY at Draft. This page
// learns all of it from ONE server-computed boolean — `controlsDisabled = !editable || saving`
// (index.tsx:420) — and never derives it from `trackStatus` or from the client's role (AD-9).
//
// Every pre-16.3 `'S'` test in this file pairs it with `editable: false` (the submitter read-only
// case), so nothing here could tell a correct gate from a widened one and the ministry-correction
// journey was unverified. These arms close that, and they make the acting identity LOAD-BEARING
// rather than decorative in two separate ways:
//
//   1. the MSW handlers COMPUTE `editable` from the 16.1 matrix over the `X-Mock-Groups` header the
//      request actually carried, so `renderAsAdmin` versus `renderAsSubmitter` genuinely changes what
//      the server answers — swap the declaration on any arm and that arm fails; and
//   2. every arm additionally asserts, in its own body, WHICH role the request carried. That second
//      check is what catches a FORGOTTEN declaration, the defect this story exists to prevent: a
//      missing `renderAsAdmin` does not produce a missing header, because `api-service` builds the
//      header from `findMockUser(localStorage…)` which falls back to `MOCK_USERS[0]` — the ADMIN
//      (api-service.ts:11-17, mockUsers.ts:38). So an admin arm that lost its declaration would
//      otherwise still pass on the silent fallback that ran the e2e suite as the wrong role for a
//      month (Story 16.1 completion notes).
describe('Schedule 7A ministry correction at Submitted (Story 16.3)', () => {
  // The PINNED 16.1 matrix (`ScheduleEditability`), per track status. Submitter edits at Draft only;
  // admin edits at Submitted and Verified and is DELIBERATELY read-only at Draft while the mill
  // still owns the data. Every other cell — `O`, no track at all, an unrecognised role — fails
  // CLOSED.
  //
  // Reproduced here rather than imported because the real rule lives in Java: this is the wire
  // contract the frontend is entitled to assume, and stating it makes falsification trivial (flip
  // the admin entry to ['D'] and every admin-at-Submitted arm below must fail; narrow it to ['S']
  // and the Verified arm must fail).
  const EDITABLE_STATUSES: Record<string, readonly string[]> = {
    ILCR_ADMIN: ['S', 'V'],
    ILCR_SUBMITTER: ['D'],
  }

  // The role each request actually carried, captured by the handlers and asserted in the test BODY.
  // Deliberately NOT asserted inside the resolver: an expect() that throws in an MSW resolver is
  // reported as a failed REQUEST, so the failure is misattributed to the page's error handling
  // instead of naming the identity as the cause.
  let sentRole: string | null = null
  let writeRole: string | null = null

  beforeEach(() => {
    sentRole = null
    writeRole = null
  })

  /**
   * The acting role as the request actually carried it. `api-service` mirrors the selected mock
   * user's roles onto `X-Mock-Groups` (api-service.ts:13), so this is the same signal the real mock
   * backend gates on — not something the test asserts about itself. The throw is a belt-and-braces
   * on a malformed harness; it is NOT the identity guard (see the header note), because the header
   * is always sent.
   */
  const actingRole = (request: Request): string => {
    const header = request.headers.get('X-Mock-Groups')
    if (!header) {
      throw new Error('request carried no X-Mock-Groups header — the acting identity was not sent')
    }
    return header
  }

  /**
   * The matrix decision for one request, mirroring `ScheduleEditability.forCaller`: the header is
   * `roles.join(',')` (api-service.ts:13) and the server UNIONS the permitted statuses across every
   * role the caller holds. A mock user holds exactly one role today, so the split is unreachable —
   * but keying on the raw header would encode the wrong rule. An unrecognised role contributes
   * nothing, so anything off the matrix fails closed.
   */
  const editableFor = (request: Request, trackStatus: unknown): boolean => {
    const permitted = new Set(
      actingRole(request)
        .split(',')
        .flatMap((role) => EDITABLE_STATUSES[role] ?? []),
    )
    return permitted.has(String(trackStatus))
  }

  /**
   * A Submitted Schedule 7A document whose `editable` is COMPUTED from the matrix for whoever asked,
   * overriding the `editable: true` the suite's own `doc()` builder defaults to. Used for the write
   * echoes too, so "the page is still correctable after the save" is also role-driven.
   */
  const matrixDoc = (request: Request, over: Record<string, unknown> = {}) => {
    const body = doc({ trackStatus: 'S', ...over })
    return { ...body, editable: editableFor(request, body.trackStatus) }
  }

  /** GET that answers `editable` per the matrix for whoever is asking, recording who that was. */
  const matrixGet = (over: Record<string, unknown> = {}) =>
    http.get(URL, ({ request }) => {
      sentRole = actingRole(request)
      return HttpResponse.json(matrixDoc(request, over))
    })

  // Cost entry via click+clear+paste, never `user.type`: Schedule 7A mounts a full 27-field editor for
  // every visible bridge, so a per-character type is O(rows × chars) and has timed this suite out on
  // CI. Paste is a real interaction firing one input event, so the component's own onChange and
  // validation still run.
  const pasteInto = async (
    user: ReturnType<typeof userEvent.setup>,
    input: HTMLElement,
    value: string,
  ) => {
    await user.click(input)
    await user.clear(input)
    await user.paste(value)
  }

  /**
   * The acting identity, asserted BOTH ways — and it takes both.
   *
   * The wire header alone cannot catch a FORGOTTEN declaration: `api-service` builds
   * `X-Mock-Groups` from `findMockUser(localStorage…)`, which falls back to `MOCK_USERS[0]` — the
   * ADMIN (api-service.ts:11-17, mockUsers.ts:38) — so an admin arm that lost its `renderAsAdmin`
   * sends a header byte-identical to a declared admin's. (Verified: dropping `renderAsAdmin` from
   * all six admin arms below left every one of them green on the header check alone.)
   *
   * So assert the DECLARATION too, via the harness's own `declaredRole()`: it returns the role this
   * test seeded and `null` when nothing did, which is the only available signal separating a
   * declaration from the silent fallback that ran the e2e suite as the wrong role for a month
   * (Story 16.1 completion notes).
   */
  const expectActingAs = (role: IlcrRole) => {
    // (1) the role was DECLARED, not inherited. `declaredRole()` reports what THIS test seeded and
    // null when nothing did, so the storage key stays the harness's own business.
    expect(declaredRole()).toBe(role)
    // (2) ...and that identity is what the REQUEST carried, so it is not merely local bookkeeping:
    // this is the same signal the mock backend itself gates on.
    expect(sentRole).toBe(role)
  }

  /** The write surface of one bridge row plus the page-level bars, as one list. */
  const writeControls = () => [
    screen.getByRole('button', { name: 'Add' }),
    bridgePanel(7001).getByRole('button', { name: 'Delete' }),
    ...screen.getAllByRole('button', { name: 'Save' }),
    // Legacy disabled Check Status alongside every write control outside Draft, even though the
    // endpoint itself is read-only and permitted at any status (spec deviation 5).
    ...screen.getAllByRole('button', { name: 'Check Status' }),
  ]

  /** Every entry field of one bridge row, spanning all four widget kinds the editor renders. */
  const entryFields = () => [
    bridgePanel(7001).getByLabelText('Name/Location of Bridge'),
    bridgePanel(7001).getByLabelText('Site Plan / Gen. Arr. ($)'),
    bridgePanel(7001).getByLabelText('Other Costs ($)'),
    bridgePanel(7001).getByLabelText('Length (m)'),
    bridgePanel(7001).getByRole('combobox', { name: /New\/Used/i }),
    bridgePanel(7001).getByRole('combobox', { name: /Load Rating/i }),
    bridgePanel(7001).getByLabelText('Comments'),
  ]

  /** The read-only shape: the schedule still SHOWS, but nothing on it can be operated. */
  const expectReadOnly = () => {
    for (const control of [...entryFields(), ...writeControls()]) {
      expect(control).toBeDisabled()
    }
    // A locked screen, not a suppressed one — legacy bound `disabled` and never removed a control.
    expect(bridgePanel(7001).getByLabelText('Name/Location of Bridge')).toHaveValue(
      'North Fork Bridge',
    )
    // The confirm modal mounts only while a delete is pending, so there is no route past the
    // disabled button to a DELETE.
    expect(
      screen.queryByText('This will delete the current record. Do you want to continue?'),
    ).not.toBeInTheDocument()
  }

  /** The editable shape: every field and every action live for this actor at this status. */
  const expectCorrectable = () => {
    for (const control of [...entryFields(), ...writeControls()]) {
      expect(control).toBeEnabled()
    }
  }

  test('admin at Submitted corrects a bridge and saves it — SUC-001 verbatim, status unmoved', async () => {
    let captured: SaveAllBody | null = null
    let putCalls = 0
    // The echo is the corrected bridge over a track that is STILL 'S'. Saving cannot move status:
    // the backend has exactly one status writer (the year-open INSERT, ReportingYearRepository:139)
    // and no transition endpoint at all (Epics 17-18 own that).
    const corrected: Bridge = {
      ...northFork,
      sitePlanCost: 2000,
      grandTotal: 13000,
      revisionCount: 4,
    }
    server.use(
      matrixGet(),
      http.put(BRIDGES_URL, async ({ request }) => {
        putCalls += 1
        writeRole = actingRole(request)
        captured = (await request.json()) as SaveAllBody
        return HttpResponse.json(
          matrixDoc(request, {
            bridges: [corrected],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    renderAsAdmin(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    // The whole point of 16.1's admin row: at Submitted the write surface is LIVE for this actor.
    expectCorrectable()
    // ...and it is live because an ADMIN asked. A forgotten `renderAsAdmin` would still have sent a
    // header (the MOCK_USERS[0] fallback), so this is the check that names the identity.
    expectActingAs(ILCR_ROLES.admin)

    await pasteInto(user, bridgePanel(7001).getByLabelText('Site Plan / Gen. Arr. ($)'), '2000')
    await savePage(user)

    // SUC-001 verbatim (`dataSavedSuccesfullyInfoMsg`, messages.properties:173), rendered from the
    // API's own `message.text` and never a client literal (AD-8).
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(putCalls).toBe(1)
    // The correction was issued AS the admin, not merely fetched as one.
    expect(writeRole).toBe(ILCR_ROLES.admin)
    expect(entryFor(captured, 7001)).toMatchObject({ sitePlanCost: 2000, revisionCount: 3 })

    // "The save does not move status" is NOT assertable from the DOM: `trackStatus` is rendered
    // nowhere on any schedule page (the tombstone carries the working context's mill/year, not the
    // document's track). So it is asserted by three proxies instead:
    //   (a) the request body's exact top-level key set — the client structurally cannot ASK for a
    //       transition, because the only thing it sends is the bridge batch;
    const body = captured as unknown as Record<string, unknown>
    expect(Object.keys(body).sort()).toEqual(['bridges'])
    //       nor can any per-bridge entry smuggle one in;
    const entry = entryFor(captured, 7001) as unknown as Record<string, unknown>
    expect(Object.keys(entry)).not.toContain('trackStatus')
    expect(Object.keys(entry)).not.toContain('editable')
    //   (b) MSW is strict (`onUnhandledRequest: 'error'`), so a call to any transition endpoint that
    //       a future change introduced would fail this test rather than pass quietly; and
    //   (c) the echo is still Submitted, and the matrix still answers editable for this actor over
    //       it, so the page stays correctable on applying it — which doubles as the AD-9 assertion:
    //       a page deriving read-only from `trackStatus` would have locked itself here.
    expect(bridgePanel(7001).getByLabelText('Site Plan / Gen. Arr. ($)')).toHaveValue('2,000')
    expectCorrectable()
  })

  // The negative arm. Same track, same screen, the SAME handler — only the acting identity differs,
  // and the matrix answers `editable: false`, so the entire surface is dead. Without this, a widened
  // gate letting the licensee edit a Submitted report would pass the arm above unnoticed.
  test('submitter at Submitted is read-only — no entry, Save, Check Status or Delete', async () => {
    // Counters rather than throwing resolvers: a throw inside an MSW resolver surfaces as a failed
    // REQUEST (and is misattributed to the page's error handling), whereas a count asserted in the
    // body says plainly that no write left the client. The clicks below are what make it meaningful.
    let writes = 0
    server.use(
      matrixGet(),
      http.put(BRIDGES_URL, () => {
        writes += 1
        return HttpResponse.json(doc())
      }),
      http.delete(`${BRIDGES_URL}/7001`, () => {
        writes += 1
        return HttpResponse.json(doc({ bridges: [] }))
      }),
    )
    renderAsSubmitter(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    expectReadOnly()
    expectActingAs(ILCR_ROLES.submitter)

    // Clicking through the dead controls issues nothing: the disabled attribute is the gate, and the
    // handlers above would have counted anything that slipped past it.
    await savePage(user)
    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    expect(writes).toBe(0)
    expect(screen.queryByText('Confirmation')).not.toBeInTheDocument()
  })

  // The other direction of the same matrix, and the one nothing covered: 16.1 deliberately REMOVED
  // the administrator's edit rights at Draft, because the mill still owns its Draft data. Assert that
  // capability is gone, not merely that the administrator has rights somewhere.
  test('admin at Draft is read-only — the capability 16.1 removed', async () => {
    let writes = 0
    server.use(
      matrixGet({ trackStatus: 'D' }),
      http.put(BRIDGES_URL, () => {
        writes += 1
        return HttpResponse.json(doc())
      }),
      http.delete(`${BRIDGES_URL}/7001`, () => {
        writes += 1
        return HttpResponse.json(doc({ bridges: [] }))
      }),
    )
    renderAsAdmin(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    expectReadOnly()
    expectActingAs(ILCR_ROLES.admin)

    await savePage(user)
    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    expect(writes).toBe(0)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
  })

  // Guards the two read-only arms above against a degenerate handler — or a broken identity helper —
  // that simply answered `editable: false` to everything. The matrix DISCRIMINATES; it is not
  // uniformly closed outside the admin's own row.
  test('submitter at Draft still edits — the matrix discriminates, it is not uniformly closed', async () => {
    server.use(matrixGet({ trackStatus: 'D' }))
    renderAsSubmitter(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    expectCorrectable()
    expectActingAs(ILCR_ROLES.submitter)
  })

  // The rest of the admin row, and the cells that must fail closed. `'V'` is served NOWHERE else in
  // this repo's frontend tests, so without this arm the backend matrix could be narrowed from
  // {S,V} to {S} — losing the Verified correction outright — and every suite would stay green.
  // No mutation handler is registered on purpose: MSW is strict (`onUnhandledRequest: 'error'`), so
  // a write escaping any of these three states fails the test as an unhandled request.
  test.each([
    ['V', 'correctable — Verified, the second half of the admin row', true],
    ['O', 'read-only — Open is off the matrix entirely, so it fails closed', false],
    [null, 'read-only — no track at all (a year with no report row) fails closed', false],
  ])('admin at trackStatus %s is %s', async (trackStatus, _expectation, correctable) => {
    server.use(matrixGet({ trackStatus }))
    renderAsAdmin(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    if (correctable) {
      expectCorrectable()
    } else {
      expectReadOnly()
    }
    expectActingAs(ILCR_ROLES.admin)
  })

  // Delete per the matrix, both paths, for THIS actor and status. The confirm itself is untouched
  // (user ruling 2026-09-11) and asserted where it stands: Schedule 7A is one of only four pages on
  // the SHARED `core/ConfirmDeleteModal`, so it already shows legacy's header "Confirmation" and
  // Yes/No answers around the verbatim `confirmDeleteMsg` (messages.properties:202).
  test('admin at Submitted deletes behind the verbatim confirm', async () => {
    let deleteCalls = 0
    let deleteUrl = ''
    server.use(
      matrixGet(),
      http.delete(`${BRIDGES_URL}/7001`, ({ request }) => {
        deleteCalls += 1
        deleteUrl = request.url
        writeRole = actingRole(request)
        return HttpResponse.json(
          matrixDoc(request, {
            bridges: [],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        )
      }),
    )
    renderAsAdmin(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    const modal = await deleteModal()
    expect(await screen.findByText('Confirmation')).toBeInTheDocument()
    expect(
      screen.getByText('This will delete the current record. Do you want to continue?', {
        normalizer: verbatim,
      }),
    ).toBeInTheDocument()

    await user.click(modal.getByRole('button', { name: 'Yes' }))
    await waitFor(() => {
      expect(deleteCalls).toBe(1)
    })
    // DEL-001 verbatim (`dataDeletedSuccesfullyInfoMsg`, messages.properties:174).
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(screen.getByText('No bridge reports have been added.')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin)
    expect(writeRole).toBe(ILCR_ROLES.admin)
    // Scoped to the working context, like every other write on this page.
    expect(deleteUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(deleteUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
  })

  test('admin at Submitted cancelling the confirm issues NO delete and leaves the bridge alone', async () => {
    let deleteCalls = 0
    server.use(
      matrixGet(),
      http.delete(`${BRIDGES_URL}/7001`, ({ request }) => {
        deleteCalls += 1
        return HttpResponse.json(matrixDoc(request, { bridges: [] }))
      }),
    )
    renderAsAdmin(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    const modal = await deleteModal()
    expect(await screen.findByText('Confirmation')).toBeInTheDocument()
    await user.click(modal.getByRole('button', { name: 'No' }))

    expect(deleteCalls).toBe(0)
    expectActingAs(ILCR_ROLES.admin)
    expect(screen.queryByText('Confirmation')).not.toBeInTheDocument()
    // The document is untouched: the row, its values and the served totals all still stand, and no
    // success banner was raised.
    expect(bridgePanel(7001).getByLabelText('Name/Location of Bridge')).toHaveValue(
      'North Fork Bridge',
    )
    expect(bridgePanel(7001).getByLabelText('Site Plan / Gen. Arr. ($)')).toHaveValue('1,000')
    expect(screen.queryByText('Data deleted successfully')).not.toBeInTheDocument()
    expect(screen.queryByText('No bridge reports have been added.')).not.toBeInTheDocument()
    // And the schedule is still correctable — cancelling a delete must not lock the page.
    expect(bridgePanel(7001).getByRole('button', { name: 'Delete' })).toBeEnabled()
  })

  // Check Status is available to the correcting administrator and mutates nothing. Legacy gated the
  // BUTTON on edit rights (26 of 26), so at Submitted-and-editable it is live rather than dead.
  test('admin at Submitted can run Check Status, and it changes nothing', async () => {
    let checkCalls = 0
    let getCalls = 0
    let writes = 0
    server.use(
      http.get(URL, ({ request }) => {
        getCalls += 1
        sentRole = actingRole(request)
        return HttpResponse.json(matrixDoc(request))
      }),
      http.post(CHECK_URL, ({ request }) => {
        checkCalls += 1
        writeRole = actingRole(request)
        return HttpResponse.json({
          requirementsMet: true,
          errors: [],
          // Empty on an all-pass result: the API sends the schedule-wide message alone, because
          // legacy emitted its per-bridge lines only when the schedule as a whole failed.
          bridgeMessages: [],
          requirementsMetMessage: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        })
      }),
      // Counted, not thrown: the count is asserted in the body below, so "Check Status mutated
      // nothing" is a real assertion rather than a request failure attributed elsewhere.
      http.put(BRIDGES_URL, () => {
        writes += 1
        return HttpResponse.json(doc())
      }),
      http.delete(`${BRIDGES_URL}/7001`, () => {
        writes += 1
        return HttpResponse.json(doc({ bridges: [] }))
      }),
    )
    renderAsAdmin(<Schedule7a />)
    const user = userEvent.setup()
    await openBridge(user, 1)

    const buttons = screen.getAllByRole('button', { name: 'Check Status' })
    for (const button of buttons) {
      expect(button).toBeEnabled()
    }
    const getsBefore = getCalls
    await user.click(buttons[0])

    // SUC-002 verbatim (`scheduleRequirementsMetMsg`, messages.properties:184 — no trailing period).
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    expect(checkCalls).toBe(1)
    expectActingAs(ILCR_ROLES.admin)
    expect(writeRole).toBe(ILCR_ROLES.admin)
    // Exactly one POST, no write of any kind, and no re-GET: the check applies no document, so a
    // stable GET count is the real "nothing else ran" signal (the confirmed-delete path, by
    // contrast, re-renders from its own echo).
    expect(writes).toBe(0)
    expect(getCalls).toBe(getsBefore)
    // The document is exactly as served.
    expect(bridgePanel(7001).getByLabelText('Name/Location of Bridge')).toHaveValue(
      'North Fork Bridge',
    )
    expect(bridgePanel(7001).getByLabelText('Site Plan / Gen. Arr. ($)')).toHaveValue('1,000')
    expect(bridgePanel(7001).getByLabelText('Comments')).toHaveValue('Spans the north fork')
    // Still editable afterwards — a read-only check must not leave the page locked.
    expectCorrectable()
  })
})
