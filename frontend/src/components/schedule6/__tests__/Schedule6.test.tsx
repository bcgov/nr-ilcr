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
import type { RoadRecordRequest, Schedule6SaveRequest } from '@/interfaces/Schedule6Request'
import type { RoadRecord } from '@/interfaces/Schedule6Response'

const URL = 'http://localhost:3000/api/v1/schedule6'
const RECORDS_URL = 'http://localhost:3000/api/v1/schedule6/records'
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

// Two TSAs and two supply blocks across DIFFERENT TSAs, so the narrowing test asserts something real
// rather than trivially passing on a single-TSA fixture. tsaRecord stores areaType '01' / supplyBlock
// '01B' — both resolvable here — proving describe() resolves a code to its description (corrections
// 2/3, retiring deviation (A)).
const codeLists = {
  tsaNumbers: [
    { code: '01', description: 'Arrowsmith TSA' },
    { code: '02', description: 'Boundary TSA' },
  ],
  supplyBlocks: [
    { code: '01B', description: 'Arrowsmith Block B' },
    { code: '02A', description: 'Boundary Block A' },
  ],
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
  codeLists,
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
// Schedules 1 and 3 use for their duplicated bars). `[0]` is the TOP bar in DOM order.
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

// Task 7 hazard 2: with N row forms instead of one, a mill/year change surviving edit is the state
// bug this per-row-map design most invites. Reuses the existing StaleRaceHarness's "change" button
// rather than adding a second mechanism for the same context switch.
async function switchContext(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /change/i }))
}

// Open the Add panel and fill the fields a valid TSA record needs.
async function openAddPanel(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: /^add$/i }))
  return screen.getByRole('region', { name: 'Add Road Maintenance report' })
}

// TSA or TFL is a CodeComboBox (corrections 2/3): selecting an option is the combo-box equivalent of
// the old `user.type` into a raw-code TextInput. `optionName` is the option's DESCRIPTION as rendered
// in the menu ('TFL' for the sentinel, since the sentinel's description IS the literal 'TFL').
async function selectAreaType(
  user: ReturnType<typeof userEvent.setup>,
  scope: HTMLElement,
  optionName: string,
) {
  await user.click(within(scope).getByRole('combobox', { name: /TSA or TFL/i }))
  await user.click(await screen.findByRole('option', { name: optionName }))
}

async function selectSupplyBlock(
  user: ReturnType<typeof userEvent.setup>,
  scope: HTMLElement,
  optionName: string,
) {
  await user.click(within(scope).getByRole('combobox', { name: /Supply Block/i }))
  await user.click(await screen.findByRole('option', { name: optionName }))
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
  //
  // These read the ROW directly, with no Edit click: correction 4 made every row editable on arrival
  // and retired the per-row editor the mirror was first written against, so a row's rate baseline is
  // seeded from the document (getRowRate) instead of by opening an editor.

  /** The `$ / m³` value inside a panel — the third FieldValue of the derived block. */
  const rateIn = (panel: HTMLElement): string | null =>
    within(panel).getByText('$ / m³').closest('div')?.textContent?.replace('$ / m³', '') ?? null

  test('on load each row reproduces the served rate exactly (#291 AC5)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })

    expect(rateIn(rowPanel(1))).toBe('50.00') // 50,000 / 1,000, the served figure
  })

  test('typing alone leaves the rate alone; blurring the cost recalculates it (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })

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
    //
    // The re-mask now lives in each field's own `onBlur` rather than in commitRate: Cost is masked to
    // whole dollars (##,###,###) and Volume through groupInput, and both mask whether or not the rate
    // commit passes its gate. Hence the two separate assertions below.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })

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

  // Task 7 (correction 4): rows are editable on arrival, so the six entered fields are inputs, not
  // text; only RMG and $/m³ stay read-only text (server-derived, AD-5). Converted from getByText to
  // display-value assertions per the Task 7 instruction reversing Task 2's read-only-row guidance.
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
    // Corrections 2/3: the row resolves the stored CODE to its DESCRIPTION, not the raw code.
    expect(within(panel).getByRole('combobox', { name: /TSA or TFL/i })).toHaveDisplayValue(
      'Arrowsmith TSA',
    )
    expect(within(panel).getByRole('combobox', { name: /Supply Block/i })).toHaveDisplayValue(
      'Arrowsmith Block B',
    )
    // RMG and $/m³ are server-derived (AD-5) and stay read-only text.
    expect(within(panel).getByText('12')).toBeInTheDocument()
    expect(within(panel).getByText('50.00')).toBeInTheDocument()
    // Volume/Cost/Comments are live form inputs, seeded GROUPED like every sibling schedule (fix 2):
    // 1000/50000 must read 1,000/50,000, not the bare digit string.
    expect(within(panel).getByLabelText('Volume m³')).toHaveValue('1,000')
    expect(within(panel).getByLabelText('Cost $')).toHaveValue('50,000')
    expect(within(panel).getByLabelText('Comments')).toHaveValue('Culvert replacement')
    // Legacy rows were always directly editable (schedule6.xhtml:248-431) -- no read-only display
    // state to fall back into, so the row's textboxes are present from the moment it opens.
    expect(within(panel).getAllByRole('textbox').length).toBeGreaterThan(0)
  })

  // Corrections 2/3: legacy rendered both controls as a selectOneMenu over the code's DESCRIPTION
  // (schedule6.xhtml:265-323). Task 7 collapses RecordDisplay/RecordEditor into one always-editable
  // row, so this now asserts the combo box's DISPLAY VALUE rather than rendered text.
  test('a row resolves the stored code to its description, not the raw code (corrections 2/3)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' }))
    const panel = rowPanel(1)

    // tsaRecord stores areaType '01' / supplyBlock '01B'.
    expect(await within(panel).findByRole('combobox', { name: /TSA or TFL/i })).toHaveDisplayValue(
      'Arrowsmith TSA',
    )
    expect(within(panel).getByRole('combobox', { name: /Supply Block/i })).toHaveDisplayValue(
      'Arrowsmith Block B',
    )
    expect(within(panel).queryByDisplayValue('01')).not.toBeInTheDocument()
    expect(within(panel).queryByDisplayValue('01B')).not.toBeInTheDocument()
  })

  // Final-review I3: deviation (f) still lets the write path store an areaType with NO
  // TSA_NUMBER_CODE row at all (unlike the Supply Block case, which is protected by the backend's
  // own TSA-numbers union arm). Before this fix the combo box found no matching option and rendered
  // blank over a value that is really there.
  test('a row whose areaType has no code-table row still displays the stored code', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ roadRecords: [{ ...tsaRecord, areaType: '09' }] })),
      ),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' }))
    const panel = rowPanel(1)

    expect(await within(panel).findByRole('combobox', { name: /TSA or TFL/i })).toHaveDisplayValue(
      '09',
    )
  })

  test('offers the TFL sentinel first in the area-type options', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    await user.click(within(panel).getByRole('combobox', { name: /TSA or TFL/i }))
    const options = screen.getAllByRole('option').map((o) => o.textContent)
    // Legacy adds TFL to the TOP of the cache list (LookUpCacheDAO.java:230). Schedule 10 appends it
    // last; fidelity to Schedule 6's OWN legacy source wins here.
    expect(options[0]).toBe('TFL')
  })

  test('narrows supply blocks to the chosen TSA', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    // supplyBlocksFor('') is empty by design (the legacy control's cleared state) — narrowing must be
    // proven with a TSA actually chosen first, or the assertion below would be vacuously true.
    await user.click(within(panel).getByRole('combobox', { name: /TSA or TFL/i }))
    await user.click(await screen.findByRole('option', { name: 'Arrowsmith TSA' }))

    await user.click(within(panel).getByRole('combobox', { name: /Supply Block/i }))
    const options = screen.getAllByRole('option').map((o) => o.textContent)
    expect(options).toEqual(['Arrowsmith Block B'])
  })

  // Fix 1: the shared field grid's 10rem minimum track truncates a description like "Arrowsmith TSA"
  // — only the two combo boxes get the wide modifier (TFL's TextInput holds a 2-character code and
  // stays narrow).
  test('the TSA/Supply Block combo boxes get the wide modifier, TFL stays narrow (fix 1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    const areaTypeCombo = within(panel).getByRole('combobox', { name: /TSA or TFL/i })
    const supplyBlockCombo = within(panel).getByRole('combobox', { name: /Supply Block/i })
    const tfl = within(panel).getByLabelText('TFL')

    expect(areaTypeCombo.closest('.schedule-6__field--wide')).not.toBeNull()
    expect(supplyBlockCombo.closest('.schedule-6__field--wide')).not.toBeNull()
    expect(tfl.closest('.schedule-6__field--wide')).toBeNull()
  })

  // 'edit mode shows the description for the stored code, not the raw code' DELETED (Task 7): there is
  // no edit mode any more -- a row's combo box always shows the description, which the rewritten
  // 'a row resolves the stored code to its description' test above already covers without an Edit
  // click.

  // Deviation (B) retired by task 4 (correction 1): DELETE now ships. Verbatim legacy copy from
  // messages.properties:31 and schedule6.xhtml:433-450.
  test('asks for confirmation with the legacy wording before deleting', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    const user = userEvent.setup()
    await user.click(
      await screen.findByRole('button', { name: 'Delete Road Maintenance Report 1' }),
    )

    expect(await screen.findByText('Confirmation')).toBeInTheDocument()
    expect(
      screen.getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Yes' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'No' })).toBeInTheDocument()
  })

  test('sends no request when the confirmation is declined', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${RECORDS_URL}/:id`, () => {
        deleted = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()
    await user.click(
      await screen.findByRole('button', { name: 'Delete Road Maintenance Report 1' }),
    )
    await user.click(screen.getByRole('button', { name: 'No' }))

    expect(deleted).toBe(false)
    // Declining must not merely hide the modal — a stranded pendingDeleteId would resurrect the
    // dialog on the next unrelated render.
    expect(screen.queryByText('Confirmation')).not.toBeInTheDocument()
  })

  test('deletes the row and renders the API success message', async () => {
    let deletedId: string | undefined
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${RECORDS_URL}/:id`, ({ params }) => {
        deletedId = String(params.id)
        return HttpResponse.json(
          doc({ roadRecords: [], message: { key: 'x', text: 'Data deleted successfully' } }),
        )
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()
    await user.click(
      await screen.findByRole('button', { name: 'Delete Road Maintenance Report 1' }),
    )
    await user.click(screen.getByRole('button', { name: 'Yes' }))

    // The recordId travels in the URL, never the ordinal shown in the accordion title.
    await waitFor(() => {
      expect(deletedId).toBe('9501')
    })
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(screen.getByText('No records found.')).toBeInTheDocument()
  })

  test('surfaces the API detail verbatim when the delete fails', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${RECORDS_URL}/:id`, () => problemBody(409, 'Schedule is not editable.')),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()
    await user.click(
      await screen.findByRole('button', { name: 'Delete Road Maintenance Report 1' }),
    )
    await user.click(screen.getByRole('button', { name: 'Yes' }))

    expect(await screen.findByText('Schedule is not editable.')).toBeInTheDocument()
    // The row must still be there to retry against — a failed delete is not a silent data loss.
    expect(within(rowPanel(1)).getByLabelText('Volume m³')).toHaveValue('1,000')
  })

  test('Delete is disabled when the schedule is not editable', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule6 />)

    expect(
      await screen.findByRole('button', { name: 'Delete Road Maintenance Report 1' }),
    ).toBeDisabled()
  })

  // 'Delete stays enabled while another row is being edited' DELETED (Task 7): there is no longer a
  // per-row "editor open" state for Delete to stay independent of -- every row is always live at once,
  // so Delete's gating on editable/saving only is already exercised by the test above and by the
  // editable:false test below.

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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
    await selectSupplyBlock(user, panel, 'Arrowsmith Block B')
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
    // toHaveProperty, not `captured!.revisionCount` — Task 8 dropped revisionCount from
    // RoadRecordRequest, so the member access is a TS2339 against the current type while asserting
    // the same thing. This form matches the two lines above and stays valid once the field is gone.
    expect(captured).not.toHaveProperty('revisionCount')
    // Legacy's add() collapses the panel before saving (Schedule6MB.java:203) — reopening it must
    // show cleared inputs (add-is-save: they clear only on success).
    await waitFor(() =>
      expect(
        screen.queryByRole('region', { name: 'Add Road Maintenance report' }),
      ).not.toBeInTheDocument(),
    )
    const reopened = await openAddPanel(user)
    // A ComboBox's empty (closed) menu carries the same aria-labelledby as its input, so
    // getByLabelText finds two elements; getByRole('combobox', ...) is unambiguous.
    expect(within(reopened).getByRole('combobox', { name: /TSA or TFL/i })).toHaveDisplayValue('')
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
    await selectSupplyBlock(user, panel, 'Arrowsmith Block B')
    // Now switch the classification: the counterpart must be CLEARED in state, not merely disabled —
    // a disabled-but-populated field still serializes, which the server would silently absorb.
    await selectAreaType(user, panel, 'TFL')
    await user.type(within(panel).getByLabelText('TFL'), '01')

    const supplyBlockCombo = within(panel).getByRole('combobox', { name: /Supply Block/i })
    expect(supplyBlockCombo).toHaveDisplayValue('')
    expect(supplyBlockCombo).toBeDisabled()
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
    await selectAreaType(user, panel, 'TFL')
    await user.type(within(panel).getByLabelText('TFL'), '02')
    await selectAreaType(user, panel, 'Arrowsmith TSA')

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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
    // Both are server-derived (AD-5) and there is no derive endpoint — they must not be guessed.
    expect(within(panel).getByText('RMG')).toBeInTheDocument()
    expect(within(panel).getByText('$ / m³')).toBeInTheDocument()
    expect(within(panel).queryByText('12')).not.toBeInTheDocument()
  })

  test('has no Edit button and rows are editable on arrival', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule6 />)
    // Legacy rows were always directly editable under their bare field names
    // (schedule6.xhtml:248-431); there was no edit mode to enter.
    expect(await screen.findByDisplayValue('Arrowsmith TSA')).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
  })

  test('saves every row and the general comment in one request, each row with its own token (AC4)', async () => {
    let body: unknown
    // The echo returns the SAVED row (volume 2,000) rather than the pre-edit one, so the render
    // assertions below can prove the response document is applied to page state. An echo of the
    // unchanged record cannot: the success banner rides its own setter, so a dropped setData() would
    // still show the banner over stale values.
    const savedTsaRecord: RoadRecord = { ...tsaRecord, volume: 2000, costPerVolume: 25 }
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [tsaRecord, tflRecord] }))),
      http.put(URL, async ({ request }) => {
        body = await request.json()
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
    const volume = within(rowPanel(1)).getByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '2000')
    await user.click(barSaveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    const saveBody = body as Schedule6SaveRequest
    expect(saveBody.generalComments).toBe('Season summary')
    // EVERY served row travels, each with its own revision token — an omitted row is a 400.
    expect(saveBody.records).toHaveLength(2)
    expect(saveBody.records[0]).toMatchObject({
      recordId: 9501,
      revisionCount: 3,
      areaType: '01',
      supplyBlock: '01B',
      tflNumber: null,
      volume: 2000,
      cost: 50000,
      comments: 'Culvert replacement',
    })
    // Row 2's token is 0: it must travel as 0, never be dropped or coerced by a falsy check.
    expect(saveBody.records[1]).toMatchObject({
      recordId: 9502,
      revisionCount: 0,
      areaType: 'TFL',
      tflNumber: '01',
      supplyBlock: null,
      comments: null,
    })
    // The echoed document replaces page state: the row re-renders the SAVED volume, not the pre-edit
    // 1,000 — a save that only banners is a silent data-staleness bug. Grouped (fix 2): re-seeded via
    // the same numStrGroup as every sibling schedule.
    expect(within(rowPanel(1)).getByLabelText('Volume m³')).toHaveValue('2,000')
    expect(within(totalsRegion()).getByText('2,500')).toBeInTheDocument()
  })

  // Hazard 3 is guarded by TWO independent mechanisms, and this test pins each to the assertion that
  // actually exercises it (deleting either mechanism should fail exactly one of the two assertions
  // below, not both):
  //  - The revisionCount token comes straight off `data.roadRecords`, refreshed by applyDocument's
  //    setData BEFORE the onSuccess callback where the re-seed runs (index.tsx :453-454 -> :497) --
  //    so the token assertion guards applyDocument/setData, NOT the re-seed.
  //  - RoadRecordFormValues carries no revisionCount field at all, so the re-seed was never "protecting
  //    a token" -- what it actually does is adopt the server's canonical/normalised FORM VALUES (e.g.
  //    volume) over the user's draft. The volume assertion is what genuinely pins the re-seed: delete
  //    it and the row keeps showing the typed '2000' forever, because rowForms[9501] stays populated
  //    and getRowForm never falls back to the document's own value.
  // The echo deliberately returns a volume AND a revisionCount the draft does not hold (2500/4, not
  // the typed 2000/the originally-served 3), so neither assertion can pass by coincidence.
  test('re-seeds row forms from the save echo with the server’s canonical values (hazard 3)', async () => {
    let secondBody: unknown
    let calls = 0
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(URL, async ({ request }) => {
        calls += 1
        if (calls === 1) {
          return HttpResponse.json(
            doc({ roadRecords: [{ ...tsaRecord, volume: 2500, revisionCount: 4 }] }),
          )
        }
        secondBody = await request.json()
        return HttpResponse.json(
          doc({ roadRecords: [{ ...tsaRecord, volume: 2500, revisionCount: 4 }] }),
        )
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const volume = await screen.findByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '2000')
    await user.click(barSaveButtons()[0])

    // The server's answer (2500), not the draft (2000), is what the row shows after the echo —
    // grouped (fix 2), the same as every re-seed.
    expect(await screen.findByLabelText('Volume m³')).toHaveValue('2,500')

    await user.click(barSaveButtons()[0])
    await waitFor(() => expect(secondBody).toBeDefined())
    // The second Save's token is the ECHOED 4, not the originally-served 3. This guards
    // applyDocument/setData (index.tsx :453-454 -> :497), NOT the re-seed below: handleSave reads
    // revisionCount off `data.roadRecords`, which applyDocument refreshes BEFORE the onSuccess
    // callback where the re-seed runs -- so this assertion would still pass even with the re-seed
    // deleted. It is the VOLUME assertion above that genuinely pins the re-seed: rowForms keeps
    // showing the typed '2000' draft until something re-seeds it from the echo.
    expect((secondBody as Schedule6SaveRequest).records[0]).toMatchObject({
      revisionCount: 4,
      volume: 2500,
    })
  })

  test('posts the on-screen values to check status, not the stored ones', async () => {
    let body: { records: { cost: number | null }[] } | undefined
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, async ({ request }) => {
        body = (await request.json()) as typeof body
        return HttpResponse.json({ outcome: 'MET', messages: [], records: [] })
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await user.clear(await screen.findByLabelText(/Cost \$/i))
    await user.click(checkStatusButtons()[0])
    await waitFor(() => {
      expect(body).toBeDefined()
    })
    // The verdict must describe what the user is looking at, not what is stored.
    expect(body?.records[0].cost).toBeNull()
  })

  test('leaves Check Status enabled while rows are dirty (no longer gated on dirtiness)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await user.clear(await screen.findByLabelText(/Cost \$/i))
    // Legacy gated it on disableReportEdits() only (schedule6.xhtml:226-229), never on dirtiness.
    expect(checkStatusButtons()[0]).toBeEnabled()
    // The one genuine residue of retiring per-row Cancel: typing in a row alone (no Save click) must
    // still issue no request.
    expect(put).not.toHaveBeenCalled()
  })

  test('reseeds every row form when the mill/year context changes (hazard 2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<StaleRaceHarness />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText(/Cost \$/i)
    await user.clear(cost)
    await user.type(cost, '999')
    expect(cost).toHaveValue('999')

    server.use(http.get(URL, () => HttpResponse.json(otherContextDoc())))
    await switchContext(user)

    // With N row forms instead of one, a surviving edit is the state bug this design most invites.
    expect(await screen.findByDisplayValue('111')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('999')).not.toBeInTheDocument()
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

  // Save is now ONE PUT of the whole document (Task 5/7, retiring deviation C): the general comment
  // travels alongside every road record in a single transaction, rather than its own endpoint.
  test('the bottom bar’s Save fires the same whole-document PUT as the top one', async () => {
    let calls = 0
    let captured: Schedule6SaveRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(URL, async ({ request }) => {
        calls += 1
        captured = (await request.json()) as Schedule6SaveRequest
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
    expect(captured!.generalComments).toBe('Revised summary')
    // The served row travels unchanged alongside the comment — an omitted row would 400.
    expect(captured!.records).toHaveLength(1)
    expect(captured!.records[0].recordId).toBe(9501)
  })

  test('the General Comment saves via the whole-document PUT (AC5)', async () => {
    let captured: Schedule6SaveRequest | null = null
    let capturedUrl: string | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(URL, async ({ request }) => {
        captured = (await request.json()) as Schedule6SaveRequest
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
    expect(captured!.generalComments).toBe('Revised summary')
    // The PUT carries the mill/year request params the 8.2 endpoint requires.
    const params = new window.URL(capturedUrl!).searchParams
    expect(params.get('millId')).toBe('13050')
    expect(params.get('year')).toBe('2017')
  })

  test('the General Comment saves with zero road records (the BR-09 placeholder branch, AC5)', async () => {
    let captured: Schedule6SaveRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ roadRecords: [], generalComments: null }))),
      http.put(URL, async ({ request }) => {
        captured = (await request.json()) as Schedule6SaveRequest
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
    expect(captured!.records).toEqual([])
  })

  test('blanking the General Comment sends null to clear it (BR-09, AC5)', async () => {
    let captured: Schedule6SaveRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(URL, async ({ request }) => {
        captured = (await request.json()) as Schedule6SaveRequest
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
    expect(within(rowPanel(1)).getByRole('combobox', { name: /TSA or TFL/i })).toBeDisabled()
    expect(within(rowPanel(1)).getByLabelText('Volume m³')).toBeDisabled()
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
    // Read-only (disabled) still shows the data, grouped (fix 2).
    expect(within(rowPanel(1)).getByLabelText('Volume m³')).toHaveValue('1,000')
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
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

  // 'Check Status is disabled while unsaved entries are on screen (dirty gate)' DELETED (Task 7,
  // correction 4): this rule is explicitly retired. Legacy's full postback applied on-screen values
  // before evaluating and never disabled Check Status for dirtiness (schedule6.xhtml:226-229); the
  // modern body now posts those on-screen values itself. Replaced by 'leaves Check Status enabled
  // while rows are dirty' above.

  test('a row served without a revisionCount surfaces an error on Save, never a silent no-op (hazard 1, AC4)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ roadRecords: [{ ...tsaRecord, revisionCount: null }] })),
      ),
      http.put(URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(barSaveButtons()[0])

    // 8.1 always serves the token, so this is a contract-regression surface: it must be VISIBLE, and
    // nothing may be sent -- a coerced 0 would silently bypass the stale-edit check for this row.
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
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
    await selectAreaType(user, panel, 'TFL')
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
    await user.type(within(panel).getByLabelText('Cost $'), '-2.5')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured!.cost).toBe(-3)
  })

  // Fix 2: Schedule 6 was the one sibling schedule still seeding numeric fields with the bare digit
  // string and never re-grouping on blur (Schedules 1, 1-other-costs, 7b and 9 all already do). These
  // pin the three moving parts: the seed, the two blur masks, and — the assertion that actually makes
  // this safe rather than merely cosmetic — that the wire still gets the plain number.
  test('typing into Volume and blurring re-groups it with thousands separators (fix 2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    const volume = within(panel).getByLabelText('Volume m³')
    await user.type(volume, '15000')
    expect(volume).toHaveValue('15000')
    await user.tab()

    expect(volume).toHaveValue('15,000')
  })

  test('typing a fractional Cost and blurring re-groups it through the fixed-0 mask (fix 2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    const cost = within(panel).getByLabelText('Cost $')
    await user.type(cost, '1500.7')
    await user.tab()

    // Legacy's mask for this field is whole-dollar (##,###,### / moneyMask above, no decimals), and
    // roundCost already sends 1501 to the wire — the display must agree with what gets stored.
    expect(cost).toHaveValue('1,501')
  })

  test('invalid text typed into Volume is left unchanged on blur, not silently rewritten (fix 2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    const volume = within(panel).getByLabelText('Volume m³')
    await user.type(volume, 'abc')
    await user.tab()

    // groupInput's contract: a typo stays on screen for the user to correct rather than being
    // blanked or silently rewritten.
    expect(volume).toHaveValue('abc')
  })

  test('invalid text typed into Cost is left unchanged on blur, not silently rewritten (fix 2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ roadRecords: [] }))))
    render(<Schedule6 />)
    const user = userEvent.setup()

    const panel = await openAddPanel(user)
    const cost = within(panel).getByLabelText('Cost $')
    await user.type(cost, 'abc')
    await user.tab()

    expect(cost).toHaveValue('abc')
  })

  // The one assertion that matters most here: proving the display-only regrouping never reaches the
  // wire. If groupInput's comma leaked into the parsed body, or roundCost's mask silently changed what
  // gets stored, this is where it would show up.
  test('a grouped Volume/Cost display still sends the plain number on save (fix 2)', async () => {
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
    const volume = within(panel).getByLabelText('Volume m³')
    await user.type(volume, '15000')
    await user.tab()
    expect(volume).toHaveValue('15,000')

    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    // The screen shows "15,000"; the wire must still carry the number 15000, not the grouped string
    // and not NaN/null (which stripGroup's absence would produce).
    expect(captured!.volume).toBe(15000)
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
    await selectAreaType(user, panel, 'TFL')
    await user.type(within(panel).getByLabelText('TFL'), '99')
    await user.type(within(panel).getByLabelText('Volume m³'), '1000')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(await screen.findByText(detail)).toBeInTheDocument()
    // Values stay put so the entry can be corrected and resubmitted.
    expect(within(panel).getByRole('combobox', { name: /TSA or TFL/i })).toHaveDisplayValue('TFL')
    expect(within(panel).getByLabelText('TFL')).toHaveValue('99')
    // Blurred (focus left Volume for the Add Report button) and grouped (fix 2).
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
    await user.type(within(panel).getByLabelText('Volume m³'), '1000')
    await user.click(within(panel).getByRole('button', { name: /^add report$/i }))

    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
    // add-is-save: the panel and its values survive a server-side failure so the entry is not retyped.
    expect(screen.getByRole('region', { name: 'Add Road Maintenance report' })).toBeInTheDocument()
    // Blurred and grouped (fix 2).
    expect(within(panel).getByLabelText('Volume m³')).toHaveValue('1,000')
  })

  test('a Save failure renders the API detail verbatim and retains every entered value (AC4 / AC10)', async () => {
    const detail = 'Entered RMG could not be resolved for the supplied Supply Block.'
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(URL, () => problemBody(400, detail)),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    const volume = within(rowPanel(1)).getByLabelText('Volume m³')
    await user.clear(volume)
    await user.type(volume, '2000')
    await user.click(barSaveButtons()[0])

    expect(await screen.findByText(detail)).toBeInTheDocument()
    // The failure branch must not run onSuccess: the row keeps the rejected value so it can be
    // corrected, page state is not replaced, and no success banner appears alongside. Blurred and
    // grouped (fix 2).
    expect(within(rowPanel(1)).getByLabelText('Volume m³')).toHaveValue('2,000')
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    // The in-flight lock releases on the error path too, or Save is dead until reload.
    await waitFor(() => expect(barSaveButtons()[0]).toBeEnabled())
  })

  test('a detail-less Save failure falls back to the generic Save message and keeps the comment (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(URL, () => new HttpResponse(null, { status: 500 })),
    )
    render(<Schedule6 />)
    const user = userEvent.setup()

    const region = await waitFor(() => commentsRegion())
    await user.clear(within(region).getByLabelText('General Comments'))
    await user.type(within(region).getByLabelText('General Comments'), 'Revised summary')
    await user.click(barSaveButtons()[0])

    // handleSaveEdit/handleSaveComments are gone -- one Save, one fallback string.
    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
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
    await selectAreaType(user, panel, 'Arrowsmith TSA')
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

  // The retired 'stale edit' and 'stale comment' tests collapse into one: Save is now a single
  // whole-document PUT carrying both the row and the general comment, so there is only one save
  // response left to guard against landing stale.
  test('a stale Save response (mill/year changed mid-flight) never applies (AC11)', async () => {
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(otherContextDoc())
          : HttpResponse.json(doc()),
      ),
      http.put(URL, async () => {
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

    await screen.findByRole('button', { name: 'Road Maintenance report Id: 1' })
    await user.click(barSaveButtons()[0])
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Other mill record')).toBeInTheDocument()
    // Let the stale PUT resolve. Save carries the widest blast radius: an unguarded response would
    // banner a save the new mill never made AND overwrite both its rows and its general comment.
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(screen.queryByText('Culvert replacement')).not.toBeInTheDocument()
    expect(screen.getByText('Other mill record')).toBeInTheDocument()
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

    await waitFor(() => expect(checkStatusButtons()[0]).toBeEnabled())
    await user.click(checkStatusButtons()[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    const panel = await openAddPanel(user)
    await selectAreaType(user, panel, 'Arrowsmith TSA')

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
