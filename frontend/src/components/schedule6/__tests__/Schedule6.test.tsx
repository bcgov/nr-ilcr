import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import { getDefaultNormalizer, render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

// jsdom lacks scrollIntoView; Carbon calls it on focus-managed widgets.
window.HTMLElement.prototype.scrollIntoView = vi.fn()

import Schedule6 from '@/components/schedule6'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import type { RoadRecordRequest, GeneralCommentsRequest } from '@/interfaces/Schedule6Request'
import type { RoadRecord } from '@/interfaces/Schedule6Response'

const URL = 'http://localhost:3000/api/v1/schedule6'
const RECORDS_URL = 'http://localhost:3000/api/v1/schedule6/records'
const COMMENTS_URL = 'http://localhost:3000/api/v1/schedule6/general-comments'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule6/check-status'

// A TSA record (areaType = the TSA code, supplyBlock set, tflNumber null — BR-02).
const tsaRecord: RoadRecord = {
  recordId: 9501,
  revisionCount: 3,
  areaType: '01',
  tflNumber: null,
  supplyBlock: '01B',
  rmg: '12',
  volume: 1000,
  cost: 50000,
  costPerVolume: 50,
  comments: 'Culvert replacement',
}

// A TFL record whose revisionCount is 0 — the falsy token a coerced default would silently mask.
const tflRecord: RoadRecord = {
  recordId: 9502,
  revisionCount: 0,
  areaType: 'TFL',
  tflNumber: '01',
  supplyBlock: null,
  rmg: '25',
  volume: 500,
  cost: 25000,
  costPerVolume: 50,
  comments: null,
}

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  generalComments: 'Season summary',
  roadRecords: [tsaRecord],
  totalVolume: 1000,
  totalCost: 50000,
  totalCostPerVolume: 50,
  ...overrides,
})

// The S18 lone-comment state: the placeholder row is excluded server-side, totals are 0/0/null.
const loneCommentDoc = (overrides: Record<string, unknown> = {}) =>
  doc({
    roadRecords: [],
    generalComments: 'Only a general comment',
    totalVolume: 0,
    totalCost: 0,
    totalCostPerVolume: null,
    ...overrides,
  })

// A document belonging to a DIFFERENT mill/year, with every rendered value distinct from doc()'s so
// a stale response landing over it is unmistakable.
const otherContextDoc = () =>
  doc({
    roadRecords: [
      {
        ...tsaRecord,
        recordId: 8301,
        volume: 777,
        cost: 111,
        costPerVolume: null,
        comments: 'Other mill record',
      },
    ],
    generalComments: 'Other mill comment',
    totalVolume: 777,
    totalCost: 111,
    totalCostPerVolume: null,
  })

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

// Preserves the literal whitespace the backend sends (the ERR-001 trailing space, the composed
// check-status spacing) so the verbatim-rendering assertions (AD-8) survive the default collapse.
const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

// The accordion row for a 1-based ORDINAL (not a recordId) — the title's own identifier.
const rowPanel = (ordinal: number): HTMLElement =>
  screen
    .getByRole('button', { name: `Road Maintenance report Id: ${ordinal}` })
    .closest('li') as HTMLElement

const totalsRegion = (): HTMLElement => screen.getByRole('region', { name: 'Totals' })
const commentsRegion = (): HTMLElement => screen.getByRole('region', { name: 'General Comments' })

// Save and Check Status render on BOTH action bars — above the records and below the General Comment
// — mirroring legacy's saveButton0/saveButton1 pair, so every query is plural (the same convention
// Schedules 1 and 3 use for their duplicated bars). `[0]` is the TOP bar in DOM order; a row
// editor's own Save sorts after it, so indexing the first stays unambiguous with an editor open.
const barSaveButtons = (): HTMLElement[] => screen.getAllByRole('button', { name: /^save$/i })
const checkStatusButtons = (): HTMLElement[] =>
  screen.getAllByRole('button', { name: /check status/i })

// Drives a mid-flight mill/year change so the stale-response guards can be exercised (module-level
// so it is not re-created per render — an @eslint-react rule forbids nested component definitions).
const StaleRaceHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <Schedule6 />
    </>
  )
}

// Open the Add panel and fill the fields a valid TSA record needs.
async function openAddPanel(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: /^add$/i }))
  return screen.getByRole('region', { name: 'Add Road Maintenance report' })
}

// The provider persists any un-`initial`ed context change to localStorage (MillYearProvider.tsx:67);
// without this, the stale-race tests' setContext(999, 2020) leaks into every later bare render and
// test order silently decides which mill the page loads.
afterEach(() => {
  window.localStorage.clear()
})

describe('Schedule 6 page (Story 8.3)', () => {
  // ---- Defect #291: the record's $ / m³ tracks entry, on blur. ------------------------------------
  //
  // The fixture is self-consistent (50000/1000 = 50), so the load assertion below is a genuine
  // mirror-vs-server comparison rather than the mirror measured against hand arithmetic in this file.

  /** The `$ / m³` value inside a panel — the third FieldValue of the derived block. */
  const rateIn = (panel: HTMLElement): string | null =>
    within(panel).getByText('$ / m³').closest('div')?.textContent?.replace('$ / m³', '') ?? null

  test('on load the edit form reproduces the served rate exactly (#291 AC5)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))

    expect(rateIn(rowPanel(1))).toBe('50.00') // 50,000 / 1,000, the served figure
  })

  test('typing alone leaves the rate alone; blurring the cost recalculates it (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))

    const cost = within(rowPanel(1)).getByLabelText('Cost $')
    await user.clear(cost)
    await user.type(cost, '75000')
    expect(rateIn(rowPanel(1))).toBe('50.00') // not per keystroke

    await user.tab()
    expect(rateIn(rowPanel(1))).toBe('75.00') // 75,000 / 1,000
  })

  test('blurring the volume recalculates the rate too (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))

    const volume = within(rowPanel(1)).getByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '2000')
    await user.tab()
    expect(rateIn(rowPanel(1))).toBe('25.00') // 50,000 / 2,000

    // Clearing it blanks the rate rather than dividing by zero.
    await user.clear(within(rowPanel(1)).getByLabelText('Volume m³'))
    await user.tab()
    expect(rateIn(rowPanel(1))).toBe('') // the mask renders a blank, not an em dash
  })

  test('the same blur re-applies the comma mask to the field itself (#291)', async () => {
    // Legacy's handlers re-rendered the INPUT alongside the rate (`render="vol cal ..."`,
    // schedule6.xhtml:153,163,364,383), which re-ran the converter and put the mask back. Schedule 6
    // is the only page whose blur moves a derived cell, so it was the only one that could leave
    // `50000` sitting unmasked next to a freshly-formatted rate.
    //
    // This test exists because that re-mask shipped INERT: `commitRate` called `groupInput` without
    // importing it, so every blur threw a ReferenceError *after* the rate had been applied. The rate
    // updated, the mask never came back, and the throw surfaced only as a Vitest unhandled error --
    // which fails no test. Assert the field, not just the rate.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))

    const volume = within(rowPanel(1)).getByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '1000000')
    expect(volume).toHaveValue('1000000') // mid-entry: untouched, so the caret is not moved
    await user.tab()
    expect(within(rowPanel(1)).getByLabelText('Volume m³')).toHaveValue('1,000,000')

    const cost = within(rowPanel(1)).getByLabelText('Cost $')
    await user.clear(cost)
    await user.type(cost, '2500000')
    await user.tab()
    expect(within(rowPanel(1)).getByLabelText('Cost $')).toHaveValue('2,500,000')
    expect(rateIn(rowPanel(1))).toBe('2.50') // 2,500,000 / 1,000,000
  })

  test('the footer totals do NOT move during entry — legacy left them until Save (#291)', async () => {
    // The deliberate boundary: totalVol/totalCos/totalCal appear in NO legacy render or update
    // target, so refreshing them from the document is already faithful and they are not mirrored.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    const totalsBefore = totalsRegion().textContent

    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))
    const cost = within(rowPanel(1)).getByLabelText('Cost $')
    await user.clear(cost)
    await user.type(cost, '999999')
    await user.tab()

    expect(rateIn(rowPanel(1))).toBe('1,000.00') // 999,999/1,000 = 999.999 -> scale 2 -> 1,000.00
    // Asserted against the LITERAL footer text, not against itself: comparing the region to a
    // snapshot of itself also passed if the footer rendered nothing (code review 2026-08-21).
    expect(totalsRegion().textContent).toBe(totalsBefore)
    expect(totalsRegion().textContent).toContain('50,000') // the served total, unmoved
  })

  test('the Add panel shows a rate as soon as both halves are committed (#291)', async () => {
    // It previously passed a hardcoded blank, so a new record showed no rate until the first save.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    expect(rateIn(panel)).toBe('')

    await user.type(within(panel).getByLabelText('Volume m³'), '1000')
    await user.type(within(panel).getByLabelText('Cost $'), '50000')
    await user.tab()
    expect(rateIn(panel)).toBe('50.00')
  })

  test('accordion titles use the 1-based ORDINAL, never recordId (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [tsaRecord, tflRecord] }))))
    render(<Schedule6 />)

    expect(
      await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Road Maintenance report Id: 2' }),
    ).toBeInTheDocument()
    // The seeded recordIds are 4-digit — a title carrying one means the wrong identifier was used.
    expect(screen.queryByText(/Road Maintenance report Id: 9501/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Road Maintenance report Id: 9502/)).not.toBeInTheDocument()
  })

  test('an opened row shows the six legacy-labelled fields with rmg and $/m³ read-only (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' }))
    const panel = rowPanel(1)

    for (const label of [
      'TSA or TFL',
      'TFL',
      'Supply Block',
      'RMG',
      'Volume m³',
      'Cost $',
      '$ / m³',
      'Comments',
    ]) {
      expect(within(panel).getByText(label)).toBeInTheDocument()
    }
    expect(within(panel).getByText('01')).toBeInTheDocument()
    expect(within(panel).getByText('01B')).toBeInTheDocument()
    expect(within(panel).getByText('12')).toBeInTheDocument()
    expect(within(panel).getByText('1,000')).toBeInTheDocument()
    expect(within(panel).getByText('50,000')).toBeInTheDocument()
    expect(within(panel).getByText('50.00')).toBeInTheDocument()
    expect(within(panel).getByText('Culvert replacement')).toBeInTheDocument()
    // Display state renders values as text, never as editable inputs.
    expect(within(panel).queryByRole('textbox')).not.toBeInTheDocument()
  })

  // Deviation (B): the row Delete + confirm dialog is un-sliced by the UC and 8.2 shipped no DELETE.
  test('no Delete control is rendered anywhere (deviation B)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [tsaRecord, tflRecord] }))))
    render(<Schedule6 />)

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('totals render the three server figures with the legacy masks (AC6)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ totalVolume: 1234567, totalCost: 7654321, totalCostPerVolume: 6.2 }),
        ),
      ),
    )
    render(<Schedule6 />)

    const totals = await waitFor(() => totalsRegion())
    expect(within(totals).getByText('1,234,567')).toBeInTheDocument()
    expect(within(totals).getByText('7,654,321')).toBeInTheDocument()
    expect(within(totals).getByText('6.20')).toBeInTheDocument()
  })

  test('S18 lone comment: empty-list placeholder, totals 0 / 0 / BLANK (AC1, AC6, deviation J)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(loneCommentDoc())))
    render(<Schedule6 />)

    // Legacy's empty substitute list has no emptyMessage, so PrimeFaces rendered its default.
    expect(await screen.findByText('No records found.')).toBeInTheDocument()
    const totals = totalsRegion()
    // totalVolume/totalCost are real zeros and must show; totalCostPerVolume is null (0/0) -> blank.
    expect(within(totals).getAllByText('0')).toHaveLength(2)
    expect(within(totals).queryByText('0.00')).not.toBeInTheDocument()
    // The general comment stays visible in the lone-comment state.
    expect(within(commentsRegion()).getByLabelText('General Comments')).toHaveValue(
      'Only a general comment',
    )
  })

  test('the Add toggle flips Add ⇄ Close and reveals the add panel (AC2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: /^add$/i })
    expect(
      screen.queryByRole('region', { name: 'Add Road Maintenance report' }),
    ).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^add$/i }))
    expect(screen.getByRole('region', { name: 'Add Road Maintenance report' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^close$/i })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^close$/i }))
    expect(
      screen.queryByRole('region', { name: 'Add Road Maintenance report' }),
    ).not.toBeInTheDocument()
  })

  test('Add Report POSTs the entered record, echoes the message verbatim and clears the form (AC2)', async () => {
    let captured: RoadRecordRequest | null = null
    let capturedUrl: string | null = null
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ roadRecords: [], totalVolume: 0, totalCost: 0, totalCostPerVolume: null }),
        ),
      ),
      http.post(RECORDS_URL, async ({ request }) => {
        captured = (await request.json()) as RoadRecordRequest
        capturedUrl = request.url
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    await user.type(within(panel).getByLabelText('Supply Block'), '01B')
    await user.type(within(panel).getByLabelText('Volume m³'), '1,000')
    await user.type(within(panel).getByLabelText('Cost $'), '50000')
    await user.type(within(panel).getByLabelText('Comments'), 'Culvert replacement')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).not.toBeNull()
    // The 8.2 endpoints require the mill/year request params — a path without them 400s in delivery.
    const params = new window.URL(capturedUrl!).searchParams
    expect(params.get('millId')).toBe('13050')
    expect(params.get('year')).toBe('2017')
    // The echoed document replaces the page state wholesale: the new row and its totals render
    // without a reload (the GET served an empty list and zero totals).
    expect(
      await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' }),
    ).toBeInTheDocument()
    const totals = totalsRegion()
    expect(within(totals).getByText('1,000')).toBeInTheDocument()
    expect(within(totals).getByText('50,000')).toBeInTheDocument()
    expect(within(totals).getByText('50.00')).toBeInTheDocument()
    expect(captured!.areaType).toBe('01')
    expect(captured!.supplyBlock).toBe('01B')
    // Grouped input reaches the wire as a number, not the NaN/null Number('1,000') would yield.
    expect(captured!.volume).toBe(1000)
    expect(captured!.cost).toBe(50000)
    expect(captured!.comments).toBe('Culvert replacement')
    // Derived values are never sent (AD-5/AD-12), and revisionCount belongs to the PUT only.
    expect(captured).not.toHaveProperty('rmg')
    expect(captured).not.toHaveProperty('costPerVolume')
    expect(captured!.revisionCount).toBeUndefined()
    // Legacy's add() collapses the panel before saving (Schedule6MB.java:203) — reopening it must
    // show cleared inputs (add-is-save: they clear only on success).
    await waitFor(() =>
      expect(
        screen.queryByRole('region', { name: 'Add Road Maintenance report' }),
      ).not.toBeInTheDocument(),
    )
    const reopened = await openAddPanel(user)
    expect(within(reopened).getByLabelText('TSA or TFL')).toHaveValue('')
    expect(within(reopened).getByLabelText('Volume m³')).toHaveValue('')
  })

  test('BR-02: switching TSA → TFL clears supplyBlock in the POSTed body (AC3)', async () => {
    let captured: RoadRecordRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, async ({ request }) => {
        captured = (await request.json()) as RoadRecordRequest
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    const areaType = within(panel).getByLabelText('TSA or TFL')
    const supplyBlock = within(panel).getByLabelText('Supply Block')
    await user.type(areaType, '01')
    await user.type(supplyBlock, '01B')
    // Now switch the classification: the counterpart must be CLEARED in state, not merely disabled —
    // a disabled-but-populated field still serializes, which the server would silently absorb.
    await user.clear(areaType)
    await user.type(areaType, 'TFL')
    await user.type(within(panel).getByLabelText('TFL'), '01')

    expect(within(panel).getByLabelText('Supply Block')).toHaveValue('')
    expect(within(panel).getByLabelText('Supply Block')).toBeDisabled()
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured!.areaType).toBe('TFL')
    expect(captured!.tflNumber).toBe('01')
    expect(captured!.supplyBlock).toBeNull()
  })

  test('BR-02: switching TFL → TSA clears tflNumber in the POSTed body (AC3)', async () => {
    let captured: RoadRecordRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, async ({ request }) => {
        captured = (await request.json()) as RoadRecordRequest
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    const areaType = within(panel).getByLabelText('TSA or TFL')
    await user.type(areaType, 'TFL')
    await user.type(within(panel).getByLabelText('TFL'), '02')
    await user.clear(areaType)
    await user.type(areaType, '01')

    expect(within(panel).getByLabelText('TFL')).toHaveValue('')
    expect(within(panel).getByLabelText('TFL')).toBeDisabled()
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured!.areaType).toBe('01')
    expect(captured!.tflNumber).toBeNull()
  })

  test('the Add panel leaves RMG and $/m³ blank until the server answers (deviation D)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    // Both are server-derived (AD-5) and there is no derive endpoint — they must not be guessed.
    expect(within(panel).getByText('RMG')).toBeInTheDocument()
    expect(within(panel).getByText('$ / m³')).toBeInTheDocument()
    expect(within(panel).queryByText('12')).not.toBeInTheDocument()
  })

  test('inline edit PUTs the row with ITS OWN revisionCount, including a falsy 0 (AC4)', async () => {
    const captured: Record<number, RoadRecordRequest> = {}
    let capturedUrl: string | null = null
    // The echo returns the SAVED row (volume 2,000) rather than the pre-edit one, so the render
    // assertions below can prove the response document is applied to page state. An echo of the
    // unchanged record cannot: the success banner rides its own setter, so a dropped setData() would
    // still show the banner over stale values.
    const savedTsaRecord: RoadRecord = { ...tsaRecord, volume: 2000, costPerVolume: 25 }
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [tsaRecord, tflRecord] }))),
      http.put(`${RECORDS_URL}/:recordId`, async ({ request, params }) => {
        captured[Number(params.recordId)] = (await request.json()) as RoadRecordRequest
        capturedUrl = request.url
        return HttpResponse.json(
          doc({
            roadRecords: [savedTsaRecord, tflRecord],
            totalVolume: 2500,
            totalCost: 75000,
            totalCostPerVolume: 30,
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))
    const volume = within(rowPanel(1)).getByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '2000')
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // The PUT carries the mill/year request params the 8.2 endpoint requires.
    const params = new window.URL(capturedUrl!).searchParams
    expect(params.get('millId')).toBe('13050')
    expect(params.get('year')).toBe('2017')
    expect(captured[9501].revisionCount).toBe(3)
    // The whole seeded body is pinned — a mis-seeded editor or mis-mapped body must fail here.
    expect(captured[9501].areaType).toBe('01')
    expect(captured[9501].supplyBlock).toBe('01B')
    expect(captured[9501].tflNumber).toBeNull()
    expect(captured[9501].volume).toBe(2000)
    expect(captured[9501].cost).toBe(50000)
    expect(captured[9501].comments).toBe('Culvert replacement')
    // The echoed document replaces page state: the editor is torn down and the row re-renders the
    // SAVED volume, not the pre-edit 1,000 — a save that only banners is a silent data-staleness bug.
    expect(within(rowPanel(1)).queryByLabelText('Volume m³')).not.toBeInTheDocument()
    expect(within(rowPanel(1)).getByText('2,000')).toBeInTheDocument()
    expect(within(totalsRegion()).getByText('2,500')).toBeInTheDocument()

    // Row 2's token is 0: it must travel as 0, never be dropped or coerced by a falsy check.
    await user.click(within(rowPanel(2)).getByRole('button', { name: /^edit$/i }))
    await user.click(within(rowPanel(2)).getByRole('button', { name: /^save$/i }))
    await waitFor(() => expect(captured[9502]).toBeDefined())
    expect(captured[9502].revisionCount).toBe(0)
    expect(captured[9502].areaType).toBe('TFL')
    expect(captured[9502].tflNumber).toBe('01')
    expect(captured[9502].supplyBlock).toBeNull()
    expect(captured[9502].comments).toBeNull()
  })

  test('edit Cancel restores the display state and issues no request (AC4)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${RECORDS_URL}/9501`, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))
    const volume = within(rowPanel(1)).getByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '4321')
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^cancel$/i }))

    expect(put).not.toHaveBeenCalled()
    expect(within(rowPanel(1)).queryByLabelText('Volume m³')).not.toBeInTheDocument()
    expect(within(rowPanel(1)).getByText('1,000')).toBeInTheDocument()
  })

  test('an open editor blocks the other rows’ Edit and the Add toggle (AC4)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [tsaRecord, tflRecord] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))

    expect(within(rowPanel(2)).getByRole('button', { name: /^edit$/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /^add$/i })).toBeDisabled()
  })

  test('Save and Check Status render on both bars, Add on the top one only (legacy saveButton0/1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    expect(barSaveButtons()).toHaveLength(2)
    expect(checkStatusButtons()).toHaveLength(2)
    // Legacy's bottom bar carried no add control (schedule6.xhtml:515-529); the toggle belongs with
    // the entry panel it opens.
    expect(screen.getAllByRole('button', { name: /^add$/i })).toHaveLength(1)
    // The top bar precedes the records, the second bar follows the General Comment.
    const [topSave, bottomSave] = barSaveButtons()
    expect(topSave.compareDocumentPosition(commentsRegion())).toBe(
      window.Node.DOCUMENT_POSITION_FOLLOWING,
    )
    expect(bottomSave.compareDocumentPosition(commentsRegion())).toBe(
      window.Node.DOCUMENT_POSITION_PRECEDING,
    )
  })

  test('the bottom bar’s Save fires the same General Comment PUT as the top one', async () => {
    let calls = 0
    let captured: GeneralCommentsRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(COMMENTS_URL, async ({ request }) => {
        calls += 1
        captured = (await request.json()) as GeneralCommentsRequest
        return HttpResponse.json(doc({ generalComments: 'Revised summary' }))
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const region = await waitFor(() => commentsRegion())
    const comments = within(region).getByLabelText('General Comments')
    await user.clear(comments)
    await user.type(comments, 'Revised summary')
    // The LAST Save — the bar below the comment, the one legacy reporters reached for after typing.
    const saves = barSaveButtons()
    await user.click(saves[saves.length - 1])

    await waitFor(() => {
      expect(calls).toBe(1)
    })
    expect(captured).toEqual({ generalComments: 'Revised summary' })
  })

  test('the General Comment saves independently via PUT /general-comments (AC5)', async () => {
    let captured: GeneralCommentsRequest | null = null
    let capturedUrl: string | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(COMMENTS_URL, async ({ request }) => {
        captured = (await request.json()) as GeneralCommentsRequest
        capturedUrl = request.url
        return HttpResponse.json(
          doc({
            generalComments: 'Revised summary',
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const region = await waitFor(() => commentsRegion())
    const comments = within(region).getByLabelText('General Comments')
    await user.clear(comments)
    await user.type(comments, 'Revised summary')
    await user.click(barSaveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toEqual({ generalComments: 'Revised summary' })
    // The PUT carries the mill/year request params the 8.2 endpoint requires.
    const params = new window.URL(capturedUrl!).searchParams
    expect(params.get('millId')).toBe('13050')
    expect(params.get('year')).toBe('2017')
  })

  test('the General Comment saves with zero road records (the BR-09 placeholder branch, AC5)', async () => {
    let captured: GeneralCommentsRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [], generalComments: null }))),
      http.put(COMMENTS_URL, async ({ request }) => {
        captured = (await request.json()) as GeneralCommentsRequest
        return HttpResponse.json(loneCommentDoc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const region = await waitFor(() => commentsRegion())
    await user.type(within(region).getByLabelText('General Comments'), 'First note')
    await user.click(barSaveButtons()[0])

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured!.generalComments).toBe('First note')
  })

  test('blanking the General Comment sends null to clear it (BR-09, AC5)', async () => {
    let captured: GeneralCommentsRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(COMMENTS_URL, async ({ request }) => {
        captured = (await request.json()) as GeneralCommentsRequest
        return HttpResponse.json(doc({ generalComments: null }))
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const region = await waitFor(() => commentsRegion())
    await user.clear(within(region).getByLabelText('General Comments'))
    await user.click(barSaveButtons()[0])

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured!.generalComments).toBeNull()
  })

  test('the General Comment counter is Carbon’s n/3500 (AC5, deviation G)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ generalComments: null }))))
    render(<Schedule6 />)

    const region = await waitFor(() => commentsRegion())
    expect(within(region).getByLabelText('General Comments').tagName).toBe('TEXTAREA')
    expect(within(region).getByText('0/3500')).toBeInTheDocument()
  })

  test('the per-record Comments field caps at 400, not legacy’s 3500 (deviation E)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    // The column is ILCR_COST_REPORT_DETAIL.COMMENTS VARCHAR2(400 BYTE); a 3500 cap here would walk
    // the user straight into a server rejection (legacy's own textareas did exactly that).
    expect(within(panel).getByLabelText('Comments')).toHaveAttribute('maxlength', '400')
    expect(within(panel).getByText('0/400')).toBeInTheDocument()
  })

  test('missing mill/year context short-circuits before any GET (AC7 / S06)', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule6 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
  })

  test.each([
    [400, 'Please Select Mill and Reporting Year in the Home Page. '],
    [
      409,
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
    ],
    [404, 'Schedule not found.'],
  ])(
    'guard state %i renders the API detail verbatim and suppresses content (AC7 / S06–S08)',
    async (status, detail) => {
      server.use(http.get(URL, () => problemBody(status, detail)))
      render(<Schedule6 />)

      expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Totals' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /check status/i })).not.toBeInTheDocument()
    },
  )

  test('editable:false disables every control including Check Status, content stays visible (AC8 / S17)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule6 />)

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    expect(screen.getByRole('button', { name: /^add$/i })).toBeDisabled()
    expect(within(rowPanel(1)).getByRole('button', { name: /^edit$/i })).toBeDisabled()
    const region = commentsRegion()
    expect(within(region).getByLabelText('General Comments')).toBeDisabled()
    // Both bars, not just the first: a read-only reporter must not find a live Save at either end.
    barSaveButtons().forEach((button) => {
      expect(button).toBeDisabled()
    })
    // Deviation (H): the API only needs VIEW_SCHEDULE, but legacy gates the button on edit rights.
    checkStatusButtons().forEach((button) => {
      expect(button).toBeDisabled()
    })
    // Read-only still shows the data.
    expect(within(rowPanel(1)).getByText('1,000')).toBeInTheDocument()
    expect(within(totalsRegion()).getByText('50,000')).toBeInTheDocument()
    expect(within(region).getByLabelText('General Comments')).toHaveValue('Season summary')
  })

  test('Check Status MET renders the single schedule banner verbatim (AC9 / S10)', async () => {
    let capturedUrl: string | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, ({ request }) => {
        capturedUrl = request.url
        return HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
          ],
          records: [],
        })
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(checkStatusButtons()[0])

    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Value Required/)).not.toBeInTheDocument()
    // The POST carries the mill/year request params the 8.2 endpoint requires.
    const params = new window.URL(capturedUrl!).searchParams
    expect(params.get('millId')).toBe('13050')
    expect(params.get('year')).toBe('2017')
  })

  test('a schedule-level message on an ISSUES outcome renders as an error, never success (AC9)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      // The backend sends messages only on MET today; severity must still follow the outcome
      // discriminant so contract drift can never paint a failure under a green banner.
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [{ key: 'someScheduleLevelMsg', text: 'Schedule-level failure text' }],
          records: [],
        }),
      ),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(checkStatusButtons()[0])

    expect(await screen.findByText('Schedule-level failure text')).toBeInTheDocument()
    expect(screen.getByText('Action required')).toBeInTheDocument()
    expect(screen.queryByText('Requirements met')).not.toBeInTheDocument()
  })

  test('Check Status ISSUES renders each composed line plus the clean rows’ met banners (AC9 / S09, S11)', async () => {
    const costLine = 'Road : 1 - TSA or TFL (Cost $) : Value Required'
    const blockLine = 'Road : 1 - Supply Block : Value Required'
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [tsaRecord, tflRecord] }))),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          records: [
            {
              recordId: 9501,
              rowCounter: 1,
              met: false,
              metMessage: null,
              issues: [
                {
                  field: 'supplyBlock',
                  message: { key: 'missingRequiredFieldMsg', text: blockLine },
                },
                { field: 'cost', message: { key: 'missingRequiredFieldMsg', text: costLine } },
              ],
            },
            {
              recordId: 9502,
              rowCounter: 2,
              met: true,
              metMessage: {
                key: 'roadRequirementsMetMsg',
                text: 'All requirements for 2 have been met.',
              },
              issues: [],
            },
          ],
        }),
      ),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(checkStatusButtons()[0])

    expect(await screen.findByText(blockLine, { normalizer: verbatim })).toBeInTheDocument()
    expect(screen.getByText(costLine, { normalizer: verbatim })).toBeInTheDocument()
    expect(screen.getByText('All requirements for 2 have been met.')).toBeInTheDocument()
    // Severity is carried by kind AND a title word, never colour alone (NFR1).
    expect(screen.getAllByText('Action required')).toHaveLength(2)
    expect(screen.getByText('Requirements met')).toBeInTheDocument()
  })

  test('a met record whose metMessage is ABSENT (not null) renders nothing and does not crash (deviation I)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [tsaRecord, tflRecord] }))),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          records: [
            {
              recordId: 9501,
              rowCounter: 1,
              met: false,
              issues: [
                {
                  field: 'cost',
                  message: {
                    key: 'missingRequiredFieldMsg',
                    text: 'Road : 1 - TSA or TFL (Cost $) : Value Required',
                  },
                },
              ],
            },
            // Jackson NON_NULL omits metMessage entirely rather than sending null.
            { recordId: 9502, rowCounter: 2, met: true, issues: [] },
          ],
        }),
      ),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(checkStatusButtons()[0])

    expect(
      await screen.findByText('Road : 1 - TSA or TFL (Cost $) : Value Required', {
        normalizer: verbatim,
      }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Requirements met')).not.toBeInTheDocument()
  })

  test('Check Status locks while in flight — one POST per click (AC11)', async () => {
    const check = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, async () => {
        check()
        await delay(150)
        return HttpResponse.json({ outcome: 'MET', messages: [], records: [] })
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    const button = checkStatusButtons()[0]
    await user.click(button)
    await waitFor(() => expect(button).toBeDisabled())
    await waitFor(() => expect(button).toBeEnabled())
    expect(check).toHaveBeenCalledTimes(1)
  })

  test('Add Report locks while in flight — one POST per double-click (AC11)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, async () => {
        post()
        await delay(150)
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    const button = within(panel).getByRole('button', { name: /^add report$/i })
    await user.click(button)
    // Second click lands while the first POST is in flight — the saving lock must swallow it, or a
    // double-click creates a duplicate road record.
    await user.click(button)
    await waitFor(() =>
      expect(
        screen.queryByRole('region', { name: 'Add Road Maintenance report' }),
      ).not.toBeInTheDocument(),
    )
    expect(post).toHaveBeenCalledTimes(1)
  })

  test('Check Status is disabled while unsaved entries are on screen (dirty gate)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    const check = checkStatusButtons()[0]

    // Legacy's full postback applied the on-screen values before evaluating; the modern check reads
    // only the DB, so a verdict must never contradict visible unsaved input.
    const panel = await openAddPanel(user)
    expect(check).toBeEnabled() // open but empty — nothing unsaved yet
    await user.type(within(panel).getByLabelText('TSA or TFL'), '0')
    expect(check).toBeDisabled()
    await user.clear(within(panel).getByLabelText('TSA or TFL'))
    expect(check).toBeEnabled()
    await user.click(screen.getByRole('button', { name: /^close$/i }))

    // An open row editor is unsaved input from the first keystroke it might carry — disabled outright.
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))
    expect(check).toBeDisabled()
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^cancel$/i }))
    expect(check).toBeEnabled()
  })

  test('a row served without a revisionCount surfaces an error on Save, never a silent no-op (AC4)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ roadRecords: [{ ...tsaRecord, revisionCount: null }] })),
      ),
      http.put(`${RECORDS_URL}/9501`, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^save$/i }))

    // 8.1 always serves the token, so this is a contract-regression surface: it must be VISIBLE.
    expect(await screen.findByText(/missing its revision token/i)).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('a later invalid submit clears the prior success banner (AC10)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, () =>
        HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        ),
      ),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    // Re-open and submit an invalid (blank) form: the stale success banner must not sit beside the
    // fresh field error telling the user the bad entry was saved.
    const reopened = await openAddPanel(user)
    await user.click(within(reopened).getByRole('button', { name: /^add report$/i }))
    expect(screen.getByText('TSA or TFL: Value is required.')).toBeInTheDocument()
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
  })

  test('a blank area type blocks the POST with the advisory message (AC10 / S12)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(screen.getByText('TSA or TFL: Value is required.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('a TFL record missing its TFL number blocks the POST (AC10 / BR-03)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), 'TFL')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(
      screen.getByText('Entered TFL number is not valid for Interior Regions.'),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('an out-of-range volume blocks the POST with the advisory range message (AC10 / S14)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    await user.type(within(panel).getByLabelText('Volume m³'), '10000000')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(screen.getByText('Entered volume must be between 0 and 9,999,999.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('a fractional cost is rounded half-away-from-zero before send (whole-dollar wire)', async () => {
    let captured: RoadRecordRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, async ({ request }) => {
        captured = (await request.json()) as RoadRecordRequest
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    await user.type(within(panel).getByLabelText('Cost $'), '-2.5')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured!.cost).toBe(-3)
  })

  test('a backend 400 renders the detail verbatim and retains every entered value (AC10)', async () => {
    const detail = 'Entered TFL number is not valid for Interior Regions.'
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      http.post(RECORDS_URL, () => problemBody(400, detail)),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), 'TFL')
    await user.type(within(panel).getByLabelText('TFL'), '99')
    await user.type(within(panel).getByLabelText('Volume m³'), '1000')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(await screen.findByText(detail)).toBeInTheDocument()
    // Values stay put so the entry can be corrected and resubmitted.
    expect(within(panel).getByLabelText('TSA or TFL')).toHaveValue('TFL')
    expect(within(panel).getByLabelText('TFL')).toHaveValue('99')
    expect(within(panel).getByLabelText('Volume m³')).toHaveValue('1,000')
  })

  test('a load failure carrying no detail falls back to the generic load message (AC7)', async () => {
    // A network failure has no `response`, so extractDetail yields undefined and mapLoadError's
    // client-owned fallback must fill in — the guard-state tests only cover the verbatim branch, and
    // an unfilled fallback renders an error panel with an empty subtitle.
    server.use(http.get(URL, () => HttpResponse.error()))
    render(<Schedule6 />)

    expect(await screen.findByText('Unable to load Schedule 6.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Totals' })).not.toBeInTheDocument()
  })

  test('a detail-less add failure falls back to the record-save message (AC10)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))),
      // A 500 with no problem+json body: the verbatim branch has nothing to render, so the page's own
      // fallback must surface rather than an "Action failed" banner with no subtitle.
      http.post(RECORDS_URL, () => new HttpResponse(null, { status: 500 })),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    await user.type(within(panel).getByLabelText('Volume m³'), '1000')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
    // add-is-save: the panel and its values survive a server-side failure so the entry is not retyped.
    expect(screen.getByRole('region', { name: 'Add Road Maintenance report' })).toBeInTheDocument()
    expect(within(panel).getByLabelText('Volume m³')).toHaveValue('1,000')
  })

  test('an edit failure renders the detail verbatim and leaves the editor open (AC4 / AC10)', async () => {
    const detail = 'Entered RMG could not be resolved for the supplied Supply Block.'
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${RECORDS_URL}/9501`, () => problemBody(400, detail)),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))
    const volume = within(rowPanel(1)).getByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '2000')
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText(detail)).toBeInTheDocument()
    // The failure branch must not run onSuccess: the editor stays open holding the rejected value so
    // it can be corrected, page state is not replaced, and no success banner appears alongside.
    expect(within(rowPanel(1)).getByLabelText('Volume m³')).toHaveValue('2,000')
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    // The in-flight lock releases on the error path too, or Save is dead until reload.
    await waitFor(() =>
      expect(within(rowPanel(1)).getByRole('button', { name: /^save$/i })).toBeEnabled(),
    )
  })

  test('a General Comment failure falls back to its OWN message and keeps the text (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(COMMENTS_URL, () => new HttpResponse(null, { status: 500 })),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const region = await waitFor(() => commentsRegion())
    await user.clear(within(region).getByLabelText('General Comments'))
    await user.type(within(region).getByLabelText('General Comments'), 'Revised summary')
    await user.click(barSaveButtons()[0])

    // The comment mutation owns a DIFFERENT fallback than the record mutations — sharing the record
    // string here would misattribute which save failed.
    expect(await screen.findByText('Comments could not be saved.')).toBeInTheDocument()
    expect(screen.queryByText('Schedule could not be saved.')).not.toBeInTheDocument()
    expect(within(commentsRegion()).getByLabelText('General Comments')).toHaveValue(
      'Revised summary',
    )
  })

  test.each([
    [
      'renders a problem+json detail verbatim',
      () => problemBody(400, 'Schedule status could not be evaluated.'),
      'Schedule status could not be evaluated.',
    ],
    [
      'falls back when the failure carries no detail',
      () => new HttpResponse(null, { status: 500 }),
      'Unable to check status.',
    ],
  ])(
    'a failed Check Status %s, paints no verdict and unlocks (AC9 / AC11)',
    async (_case, respond, expected) => {
      server.use(
        http.get(URL, () => HttpResponse.json(doc())),
        http.post(CHECK_URL, respond),
      )
      render(<Schedule6 />)
      const user = userEvent.setup()

      await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
      await user.click(checkStatusButtons()[0])

      // handleCheckStatus owns its own error text and lock, separate from runMutation's.
      expect(await screen.findByText(expected)).toBeInTheDocument()
      expect(screen.queryByText('Requirements met')).not.toBeInTheDocument()
      expect(screen.queryByText('Action required')).not.toBeInTheDocument()
      await waitFor(() => expect(checkStatusButtons()[0]).toBeEnabled())
    },
  )

  test('a success response with NO message applies the document silently (optional field)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ roadRecords: [], totalVolume: 0, totalCost: 0, totalCostPerVolume: null }),
        ),
      ),
      // `message` is optional on the 8.2 response. It always arrives today, but an absent one must
      // apply the document and banner nothing — never a success banner with an undefined subtitle.
      http.post(RECORDS_URL, () => HttpResponse.json(doc())),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    await user.type(within(panel).getByLabelText('Volume m³'), '1000')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    // The document still lands (row + totals) and the panel still collapses...
    expect(
      await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' }),
    ).toBeInTheDocument()
    expect(within(totalsRegion()).getByText('1,000')).toBeInTheDocument()
    await waitFor(() =>
      expect(
        screen.queryByRole('region', { name: 'Add Road Maintenance report' }),
      ).not.toBeInTheDocument(),
    )
    // ...with no banner of either kind.
    expect(screen.queryByText('Success')).not.toBeInTheDocument()
    expect(screen.queryByText('Action failed')).not.toBeInTheDocument()
  })

  test('a stale GET (mill/year changed mid-flight) is ignored (AC11)', async () => {
    server.use(
      http.get(URL, async ({ request }) => {
        if (request.url.includes('millId=999')) {
          return HttpResponse.json(otherContextDoc())
        }
        await delay(120)
        return HttpResponse.json(doc())
      }),
    )
    render(<StaleRaceHarness />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Other mill record')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Culvert replacement')).not.toBeInTheDocument())
  })

  test('a stale add response (mill/year changed mid-flight) never applies (AC11)', async () => {
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(otherContextDoc())
          : HttpResponse.json(doc({ roadRecords: [] })),
      ),
      http.post(RECORDS_URL, async () => {
        await delay(300)
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    // Explicit initial context: the sibling stale-GET test persists 999/2020, and a no-op "change"
    // to the same context would defeat the race this test exists to exercise.
    render(
      <MillYearProvider initial={{ millId: 13050, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Other mill record')).toBeInTheDocument()
    // Let the stale POST resolve, then confirm nothing from it landed.
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(screen.queryByText('Culvert replacement')).not.toBeInTheDocument()
    expect(screen.getByText('Other mill record')).toBeInTheDocument()
  })

  test('a stale check-status response (mill/year changed mid-flight) never applies (AC11)', async () => {
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(otherContextDoc())
          : HttpResponse.json(doc()),
      ),
      http.post(CHECK_URL, async () => {
        await delay(300)
        return HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
          ],
          records: [],
        })
      }),
    )
    render(
      <MillYearProvider initial={{ millId: 13050, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(checkStatusButtons()[0])
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Other mill record')).toBeInTheDocument()
    // Let the stale check response resolve, then confirm the old mill's verdict never painted onto
    // the new mill's page (handleCheckStatus guards independently of runMutation).
    await delay(400)
    expect(
      screen.queryByText('All requirements for this schedule have been met'),
    ).not.toBeInTheDocument()
  })

  test('a stale edit response (mill/year changed mid-flight) never applies (AC11)', async () => {
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(otherContextDoc())
          : HttpResponse.json(doc()),
      ),
      http.put(`${RECORDS_URL}/9501`, async () => {
        await delay(300)
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    render(
      <MillYearProvider initial={{ millId: 13050, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^edit$/i }))
    await user.click(within(rowPanel(1)).getByRole('button', { name: /^save$/i }))
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Other mill record')).toBeInTheDocument()
    // Let the stale PUT resolve. The edit branch carries the widest blast radius of the four: an
    // unguarded response would banner a save the new mill never made AND overwrite its rows.
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(screen.queryByText('Culvert replacement')).not.toBeInTheDocument()
    expect(screen.getByText('Other mill record')).toBeInTheDocument()
  })

  test('a stale comment response (mill/year changed mid-flight) never applies (AC11)', async () => {
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(otherContextDoc())
          : HttpResponse.json(doc()),
      ),
      http.put(COMMENTS_URL, async () => {
        await delay(300)
        return HttpResponse.json(
          doc({
            generalComments: 'Revised summary',
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    render(
      <MillYearProvider initial={{ millId: 13050, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    // Waits for the document to land before the save fires the race.
    await waitFor(() => commentsRegion())
    await user.click(barSaveButtons()[0])
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Other mill record')).toBeInTheDocument()
    // The comment PUT returns the WHOLE document, so an unguarded stale response replaces the new
    // mill's rows and comment with the old mill's — the fourth mutation, guarded like the other three.
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(screen.queryByText('Culvert replacement')).not.toBeInTheDocument()
    expect(within(commentsRegion()).getByLabelText('General Comments')).toHaveValue(
      'Other mill comment',
    )
  })

  test('changing mill/year resets the add panel, banners and check result (AC11)', async () => {
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(doc({ roadRecords: [] }))
          : HttpResponse.json(doc({ roadRecords: [] })),
      ),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
          ],
          records: [],
        }),
      ),
    )
    render(
      <MillYearProvider initial={{ millId: 13050, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    // Check first (the dirty gate disables the button once the Add panel holds a value), then dirty
    // the Add panel so the context change has both a check result and an open panel to reset.
    await waitFor(() => expect(checkStatusButtons()[0]).toBeEnabled())
    await user.click(checkStatusButtons()[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    const panel = await openAddPanel(user)
    await user.type(within(panel).getByLabelText('TSA or TFL'), '01')

    await user.click(screen.getByRole('button', { name: /change/i }))

    await waitFor(() =>
      expect(
        screen.queryByText('All requirements for this schedule have been met'),
      ).not.toBeInTheDocument(),
    )
    expect(
      screen.queryByRole('region', { name: 'Add Road Maintenance report' }),
    ).not.toBeInTheDocument()
  })
})
