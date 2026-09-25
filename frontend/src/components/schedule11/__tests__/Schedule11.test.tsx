import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import {
  declaredRole,
  fireEvent,
  getDefaultNormalizer,
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

// jsdom lacks scrollIntoView; Carbon's Dropdown/ComboBox call it on the highlighted option.
window.HTMLElement.prototype.scrollIntoView = vi.fn()

import Schedule11 from '@/components/schedule11'
import apiService from '@/service/api-service'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import type { IlcrRole } from '@/context/auth/mockUsers'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import type SilvicultureLocationRequest from '@/interfaces/Schedule11Request'
import type { LocationSaveAllRequest } from '@/interfaces/Schedule11Request'
import type { SilvicultureLocation } from '@/interfaces/Schedule11Response'

const URL = 'http://localhost:3000/api/v1/schedule11'
const LOCATIONS_URL = 'http://localhost:3000/api/v1/schedule11/locations'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule11/check-status'
const BEC_URL = 'http://localhost:3000/api/v1/schedule11/biogeoclimatic-catalogue'

const northRidge: SilvicultureLocation = {
  locationId: 9001,
  location: 'North Ridge',
  enhancedIndicator: false,
  biogeoclimaticCatalogueId: 321,
  becLabel: 'ICHdw1',
  netArea: 120.5,
  actualCost: 25000,
  plannedCost: 10000,
  totalCost: 35000,
  costPerNetArea: 290.4564,
  comments: null,
  revisionCount: 4,
}

// netArea 9 vs 60 vs 120.5 catches a lexicographic sort ("120.5" < "60" < "9" as strings).
// Alder Flat's null actualCost pins down where blank cells rank.
const midSlope: SilvicultureLocation = {
  locationId: 9002,
  location: 'Mid Slope',
  enhancedIndicator: true,
  biogeoclimaticCatalogueId: 322,
  becLabel: 'MSdm2',
  netArea: 60,
  actualCost: 5000,
  plannedCost: 1000,
  totalCost: 6000,
  costPerNetArea: 100,
  comments: 'second',
  revisionCount: 1,
}

const alderFlat: SilvicultureLocation = {
  locationId: 9003,
  location: 'Alder Flat',
  enhancedIndicator: false,
  biogeoclimaticCatalogueId: 323,
  becLabel: 'CWHxm1',
  netArea: 9,
  actualCost: null,
  plannedCost: 2000,
  totalCost: 2000,
  costPerNetArea: 222.22,
  comments: 'third',
  revisionCount: 1,
}

const TOTALS = {
  netArea: 120.5,
  actualCost: 25000,
  plannedCost: 10000,
  totalCost: 35000,
  costPerNetArea: 290.4564,
}

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  revisionCount: null,
  locations: [northRidge],
  totals: TOTALS,
  ...overrides,
})

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

const SAVED = { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' }
const CONFIRM = 'This will delete the current record. Do you want to continue?'
// Client chrome, stated here rather than imported: a test that compares the page's text against the
// constant that renders it pins nothing.
const SAVE_BLOCKED = 'Please correct the highlighted fields before saving.'
const CHECK_NEEDS_SAVE = 'Save your changes before checking status'
// messages.properties:63, verbatim (AD-8).
const STALE = 'This schedule was changed by another user. Please reload and try again.'

// Preserves the literal whitespace the backend sends (the FLD-004 double space, the ERR-001 trailing
// space) so the verbatim-rendering assertions (AD-8) are not defeated by the default whitespace
// collapse/trim.
const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

// Drives a mid-flight mill/year change so the stale-response guard can be exercised (module-level so
// it is not re-created per render — an @eslint-react rule forbids nested component definitions).
const StaleRaceHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <Schedule11 />
    </>
  )
}

// Type a term into the BEC ComboBox (fires the debounced search) and pick a suggestion by label.
async function pickBec(
  user: ReturnType<typeof userEvent.setup>,
  comboName: RegExp,
  term: string,
  optionLabel: string,
) {
  await user.type(screen.getByRole('combobox', { name: comboName }), term)
  await user.click(await screen.findByRole('option', { name: optionLabel }))
}

/**
 * The table row for a location. An editable row holds its values in inputs whose hidden labels name
 * the row by its SERVED location ("Location for North Ridge"); a read-only row renders the name as
 * cell text. One helper for both, so an arm reads the same either way.
 */
const queryRow = (name: string): HTMLElement | null =>
  (
    screen.queryByLabelText(`Location for ${name}`) ?? screen.queryByText(name, { selector: 'td' })
  )?.closest('tr') ?? null

const findRow = (name: string): Promise<HTMLElement> =>
  waitFor(() => {
    const row = queryRow(name)
    if (!row) {
      throw new Error(`no table row for "${name}"`)
    }
    return row
  })

// Save and Check Status render above AND below the table, so each name matches two buttons.
const saveButtons = () => screen.getAllByRole('button', { name: /^save$/i })
const checkButtons = () => screen.getAllByRole('button', { name: /check status/i })

// Change a row control by its per-row name. fireEvent.change, not user.type: every row mounts live
// inputs, so per-character typing is O(rows × chars) and has timed out CI before.
const changeRowField = (label: string, location: string, value: string) =>
  fireEvent.change(screen.getByLabelText(`${label} for ${location}`), { target: { value } })

// Flag a row for deletion through its confirm (legacy Schedule11MB.deleteLocation: nothing is sent).
const flagDelete = async (user: ReturnType<typeof userEvent.setup>, location: string) => {
  const row = await findRow(location)
  await user.click(within(row).getByRole('button', { name: /^delete$/i }))
  const dialog = await screen.findByRole('dialog')
  await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))
}

describe('Schedule 11 page (Story 25.3)', () => {
  test('renders each location as live inputs over the served values, with server totals masked (AC1)', async () => {
    // Story 26.2 D1: the page is legacy's always-editable table (schedule11.xhtml:204-374), so an
    // editable row holds its values in inputs. The two derived cells stay display-only (AD-5).
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderAsSubmitter(<Schedule11 />)

    const row = await findRow('North Ridge')
    expect(within(row).getByLabelText('Location for North Ridge')).toHaveValue('North Ridge')
    expect(
      within(row).getByRole('combobox', { name: 'Biogeo/Subzone/Variant for North Ridge' }),
    ).toHaveValue('ICHdw1')
    expect(within(row).getByText('No')).toBeInTheDocument()
    expect(within(row).getByLabelText('NAR(ha) for North Ridge')).toHaveValue('120.5')
    expect(within(row).getByLabelText('Actual Cost ($) for North Ridge')).toHaveValue('25000')
    expect(within(row).getByLabelText('Planned Cost ($) for North Ridge')).toHaveValue('10000')
    expect(within(row).getByText('35,000')).toBeInTheDocument()
    expect(within(row).getByText('290.46')).toBeInTheDocument()

    const totalsRow = screen.getByText('Totals').closest('tr') as HTMLElement
    expect(within(totalsRow).getByText('25,000')).toBeInTheDocument()
    expect(within(totalsRow).getByText('10,000')).toBeInTheDocument()
    expect(within(totalsRow).getByText('290.46')).toBeInTheDocument()
  })

  // Story 30.3 / #312 Overall 6. `renderIcon` puts an <svg> inside the button and leaves the
  // accessible name as the label text, so a by-name lookup still finds the button AND proves the
  // decorative icon is there — a later edit that drops an icon fails here.
  test('every primary and row action button carries its decorative icon', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderAsSubmitter(<Schedule11 />)

    // Row-scoped on purpose: the delete-confirm Modal stays mounted while the page is editable,
    // so the document also holds its closed footer's "Delete", which is deliberately icon-free.
    // The row carries Delete only — its per-row Edit went with the per-row editor (26.2 D1).
    const iconRow = await findRow('North Ridge')
    expect(
      within(iconRow)
        .getByRole('button', { name: /^delete$/i })
        .querySelector('svg'),
    ).not.toBeNull()
    for (const name of [/^add$/i, /^save$/i, /check status/i]) {
      for (const button of screen.getAllByRole('button', { name })) {
        expect(button.querySelector('svg')).not.toBeNull()
      }
    }
  })

  test('zero locations render an empty table + blank (not 0) footer totals, no error (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))))
    renderAsSubmitter(<Schedule11 />)

    expect(
      await screen.findByText(/no silviculture locations have been added/i),
    ).toBeInTheDocument()
    const totalsRow = screen.getByText('Totals').closest('tr') as HTMLElement
    // Blank, never "0".
    expect(within(totalsRow).queryByText('0')).not.toBeInTheDocument()
    expect(screen.queryByText(/unable to load/i)).not.toBeInTheDocument()
  })

  test('add a valid location POSTs and shows the verbatim success message (AC2)', async () => {
    let captured: SilvicultureLocationRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, async ({ request }) => {
        captured = (await request.json()) as SilvicultureLocationRequest
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '120.5')
    await user.type(screen.getByLabelText('Actual Cost ($)'), '25000')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(queryRow('North Ridge')).not.toBeNull()
    expect(captured).not.toBeNull()
    expect(captured!.location).toBe('North Ridge')
    expect(captured!.enhancedIndicator).toBe(false)
    expect(captured!.biogeoclimaticCatalogueId).toBe(321)
    expect(captured!.netArea).toBe(120.5)
    expect(captured!.actualCost).toBe(25000)
  })

  test('comma-grouped NAR and cost reach the wire as numbers, not NaN/null (P2)', async () => {
    // Legacy (US DecimalFormat) accepts grouped input; the page must parse "1,000" the same for BOTH
    // the advisory gate AND the request body. A Number()-based parse would reject NAR here (NaN ->
    // null -> backend @NotNull 400) — this pins the grouped value all the way to the POST body.
    let captured: SilvicultureLocationRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, async ({ request }) => {
        captured = (await request.json()) as SilvicultureLocationRequest
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '1,000.5')
    await user.type(screen.getByLabelText('Actual Cost ($)'), '10,000')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured!.netArea).toBe(1000.5)
    expect(captured!.actualCost).toBe(10000)
  })

  test('blank add fields block the POST with inline advisory errors (AC5)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.post(LOCATIONS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^add$/i }))

    expect(screen.getByText('Location: Value is required.')).toBeInTheDocument()
    expect(screen.getByText('Enhanced: Value is required.')).toBeInTheDocument()
    expect(screen.getByText('Biogeo/Subzone/Variant: Value is required.')).toBeInTheDocument()
    expect(screen.getByText('NAR(ha): Value is required.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('out-of-range NAR blocks the POST with the advisory range error (AC5)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'Yes' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '1000000')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(
      screen.getByText('Entered NAR (ha) must be between 0 and 999,999.9.'),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('an out-of-range cost blocks the POST with the advisory cost error (AC5)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'Yes' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '10')
    await user.type(screen.getByLabelText('Actual Cost ($)'), '100000000')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(
      screen.getByText('Entered cost must be between -99,999,999 and 99,999,999.'),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('fractional costs round to whole dollars before send (legacy Oracle rounding parity)', async () => {
    let captured: SilvicultureLocationRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, async ({ request }) => {
        captured = (await request.json()) as SilvicultureLocationRequest
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'Yes' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '10')
    // Legacy accepted fractional costs and Oracle COST NUMBER(15) ROUNDED them on insert; the
    // client reproduces that (half away from zero) so the Integer wire never silently truncates.
    await user.type(screen.getByLabelText('Actual Cost ($)'), '100.5')
    await user.type(screen.getByLabelText('Planned Cost ($)'), '-2.5')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured!.actualCost).toBe(101)
    expect(captured!.plannedCost).toBe(-3)
  })

  test('an edited row reaches the wire through Save — one bulk PUT carrying its revisionCount (AC3)', async () => {
    // Was "inline edit PUTs the row": the per-row PUT /locations/{id} is retired (Story 26.2 D1).
    // The full body is still pinned — a mis-seeded form or mis-mapped buildBody must fail here.
    let captured: LocationSaveAllRequest | null = null
    let puts = 0
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, async ({ request }) => {
        puts += 1
        captured = (await request.json()) as LocationSaveAllRequest
        return HttpResponse.json(
          doc({
            locations: [{ ...northRidge, location: 'North Ridge Revised', revisionCount: 5 }],
            message: SAVED,
          }),
        )
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('North Ridge')
    changeRowField('Location', 'North Ridge', 'North Ridge Revised')
    await user.click(saveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(puts).toBe(1)
    expect(captured).toEqual({
      locations: [
        {
          basicSilvicultureReportId: 9001,
          location: {
            location: 'North Ridge Revised',
            enhancedIndicator: false,
            biogeoclimaticCatalogueId: 321,
            netArea: 120.5,
            actualCost: 25000,
            plannedCost: 10000,
            comments: null,
            revisionCount: 4,
          },
        },
      ],
      deletedIds: [],
    })
    // The echo is now the page: the row carries its saved name and nothing is pending.
    expect(screen.getByLabelText('Location for North Ridge Revised')).toHaveValue(
      'North Ridge Revised',
    )
    for (const button of saveButtons()) {
      expect(button).toBeDisabled()
    }
  })

  test('row controls print no per-cell field labels, but every control keeps a per-row name', async () => {
    // The column headers already name these fields; a visible per-cell label would print as stray
    // text above the controls in every row. Hiding them must not cost the controls their names, and
    // each name says which row it is in — the bare names belong to the Add panel.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderAsSubmitter(<Schedule11 />)

    const row = await findRow('North Ridge')

    // Dropdown and TextInput keep the label element but visually hide it (Carbon's hideLabel).
    expect(within(row).getByText('Enhanced for North Ridge')).toHaveClass('cds--visually-hidden')
    expect(within(row).getByText('NAR(ha) for North Ridge')).toHaveClass('cds--visually-hidden')
    // ComboBox has no hideLabel, so the visible label is dropped entirely.
    expect(
      within(row).queryByText('Biogeo/Subzone/Variant for North Ridge'),
    ).not.toBeInTheDocument()

    // All remain reachable by name — this is what would break if the labels were display:none'd
    // or the aria-label fallback were dropped.
    expect(
      within(row).getByRole('combobox', { name: 'Enhanced for North Ridge' }),
    ).toBeInTheDocument()
    expect(
      within(row).getByRole('combobox', { name: 'Biogeo/Subzone/Variant for North Ridge' }),
    ).toBeInTheDocument()
    for (const label of [
      'Location',
      'NAR(ha)',
      'Actual Cost ($)',
      'Planned Cost ($)',
      'Comments',
    ]) {
      expect(within(row).getByLabelText(`${label} for North Ridge`)).toBeInTheDocument()
    }

    // The Add panel still shows its labels — hideLabel is row-scoped, not global.
    expect(screen.getByText('Enhanced')).not.toHaveClass('cds--visually-hidden')
  })

  test('one invalid row blocks the whole Save — nothing sent, the row flagged and scrolled to (AC3)', async () => {
    // Was "an invalid inline edit blocks the PUT". Legacy's JSF validated the whole table on Save;
    // the 7A precedent sends nothing while one row fails.
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    const northEl = document.getElementById('schedule-11-row-9001') as HTMLElement
    const midEl = document.getElementById('schedule-11-row-9002') as HTMLElement
    northEl.scrollIntoView = vi.fn()
    midEl.scrollIntoView = vi.fn()
    changeRowField('NAR(ha)', 'Mid Slope', '1000000')
    await user.click(saveButtons()[0])

    expect(screen.getByText(SAVE_BLOCKED)).toBeInTheDocument()
    const midRow = await findRow('Mid Slope')
    expect(
      within(midRow).getByText('Entered NAR (ha) must be between 0 and 999,999.9.'),
    ).toBeInTheDocument()
    expect(midEl.scrollIntoView).toHaveBeenCalledTimes(1)
    expect(northEl.scrollIntoView).not.toHaveBeenCalled()
    expect(put).not.toHaveBeenCalled()
  })

  test('with several invalid rows, Save flags each one and scrolls to the topmost AS SORTED', async () => {
    // Every failing row shows its own error, not only the first. The scroll follows what the user
    // sees: sorted by Location, Mid Slope is on top although North Ridge comes first in the document.
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    const northEl = document.getElementById('schedule-11-row-9001') as HTMLElement
    const midEl = document.getElementById('schedule-11-row-9002') as HTMLElement
    northEl.scrollIntoView = vi.fn()
    midEl.scrollIntoView = vi.fn()
    const header = screen
      .getByText('Location', { selector: '.cds--table-header-label' })
      .closest('th') as HTMLElement
    await user.click(within(header).getByRole('button'))
    changeRowField('Location', 'North Ridge', '')
    changeRowField('NAR(ha)', 'Mid Slope', '1000000')
    await user.click(saveButtons()[1])

    expect(screen.getByText(SAVE_BLOCKED)).toBeInTheDocument()
    expect(within(northEl).getByText('Location: Value is required.')).toBeInTheDocument()
    expect(
      within(midEl).getByText('Entered NAR (ha) must be between 0 and 999,999.9.'),
    ).toBeInTheDocument()
    expect(midEl.scrollIntoView).toHaveBeenCalledTimes(1)
    expect(northEl.scrollIntoView).not.toHaveBeenCalled()
    expect(put).not.toHaveBeenCalled()
  })

  test('a row whose catalogue id has no label blocks only a Save that edits it (BR-09, review D-R1)', async () => {
    // A dangling id (no FK in delivery) seeds no phantom selection. Untouched, the row is not sent,
    // so it cannot block another row's save; once edited, it must be re-picked before it can go.
    const bodies: LocationSaveAllRequest[] = []
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ locations: [{ ...northRidge, becLabel: null }, midSlope] })),
      ),
      http.put(LOCATIONS_URL, async ({ request }) => {
        bodies.push((await request.json()) as LocationSaveAllRequest)
        return HttpResponse.json(
          doc({ locations: [{ ...northRidge, becLabel: null }, midSlope], message: SAVED }),
        )
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    changeRowField('Comments', 'Mid Slope', 'the other row')
    await user.click(saveButtons()[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(bodies[0].locations.map((item) => item.basicSilvicultureReportId)).toEqual([9002])

    changeRowField('Comments', 'North Ridge', 'a note')
    await user.click(saveButtons()[0])

    expect(screen.getByText(SAVE_BLOCKED)).toBeInTheDocument()
    expect(screen.getByText('Biogeo/Subzone/Variant: Value is required.')).toBeInTheDocument()
    expect(bodies).toHaveLength(1)
  })

  test('an edit stays on the page until Save — typing sends nothing (replaces edit Cancel, AC3)', async () => {
    // The per-row editor and its Cancel are gone (26.2 D1): legacy's table had no per-row mode and no
    // cancel either — an unsaved edit is discarded by leaving the page. What 25.3's Cancel arm
    // protected, "no request without an explicit save", is what this pins now.
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)

    await findRow('North Ridge')
    // Nothing to save until something changes.
    for (const button of saveButtons()) {
      expect(button).toBeDisabled()
    }
    changeRowField('Location', 'North Ridge', 'Changed But Unsaved')

    expect(screen.getByLabelText('Location for North Ridge')).toHaveValue('Changed But Unsaved')
    for (const button of saveButtons()) {
      expect(button).toBeEnabled()
    }
    await delay(50)
    expect(put).not.toHaveBeenCalled()
  })

  test('delete confirm FLAGS the row — it leaves the table, nothing is sent, no message; Save sends it (AC4)', async () => {
    // Was "delete confirm issues DELETE". Legacy's Delete only flagged the row (Schedule11MB
    // .deleteLocation, :132-138); the page-level Save wrote it. Legacy's "Data deleted successfully"
    // at the flag was untrue at that moment and is not reproduced (26.2 D6(b), deviation (B)).
    let captured: LocationSaveAllRequest | null = null
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, async ({ request }) => {
        put()
        captured = (await request.json()) as LocationSaveAllRequest
        return HttpResponse.json(doc({ locations: [], totals: {}, message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    const row = await findRow('North Ridge')
    await user.click(within(row).getByRole('button', { name: /^delete$/i }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(CONFIRM)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() => expect(queryRow('North Ridge')).toBeNull())
    // The report still holds the row until Save, so the table does not claim none were added (P7).
    expect(
      screen.getByText('Every location is marked for deletion. Save to remove them.'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
    expect(screen.queryByText(/successfully/i)).not.toBeInTheDocument()

    await user.click(saveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledTimes(1)
    expect(captured).toEqual({ locations: [], deletedIds: [9001] })
  })

  test('delete cancel closes the dialog, flags nothing, fires no request and shows no message (AC4)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    const row = await findRow('North Ridge')
    await user.click(within(row).getByRole('button', { name: /^delete$/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^cancel$/i }))

    expect(put).not.toHaveBeenCalled()
    expect(screen.queryByText(/successfully/i)).not.toBeInTheDocument()
    expect(queryRow('North Ridge')).not.toBeNull()
    // Nothing became pending: Save stays greyed.
    for (const button of saveButtons()) {
      expect(button).toBeDisabled()
    }
  })

  test('BEC type-ahead hits the search endpoint with the typed term (AC6)', async () => {
    let requestedUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, ({ request }) => {
        requestedUrl = request.url
        return HttpResponse.json([{ id: 321, label: 'ICHdw1' }])
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(
      await screen.findByRole('combobox', { name: /^Biogeo\/Subzone\/Variant$/i }),
      'ICH',
    )

    expect(await screen.findByRole('option', { name: 'ICHdw1' })).toBeInTheDocument()
    await waitFor(() => expect(requestedUrl).toContain('q=ICH'))
  })

  test('BEC free text never chosen is treated as empty (forced selection, AC6)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([])),
      http.post(LOCATIONS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'Yes' }))
    // Type free text but never choose a suggestion.
    await user.type(
      screen.getByRole('combobox', { name: /^Biogeo\/Subzone\/Variant$/i }),
      'nomatch',
    )
    await user.type(screen.getByLabelText('NAR(ha)'), '10')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(screen.getByText('Biogeo/Subzone/Variant: Value is required.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('typing after a pick drops the BEC selection (forced selection, AC6)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'Yes' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    // Appending to the picked label breaks the resolved option — the stale id must not submit
    // under a label that no longer matches (BR-09).
    await user.type(screen.getByRole('combobox', { name: /^Biogeo\/Subzone\/Variant$/i }), 'x')
    await user.type(screen.getByLabelText('NAR(ha)'), '10')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(screen.getByText('Biogeo/Subzone/Variant: Value is required.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('clearing the BEC input fires no search request (client minQueryLength=1)', async () => {
    const search = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => {
        search()
        return HttpResponse.json([])
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    const combo = await screen.findByRole('combobox', { name: /^Biogeo\/Subzone\/Variant$/i })
    await user.type(combo, 'I')
    await user.clear(combo)
    // Past the debounce window: clearing must have cancelled the pending search outright.
    await delay(350)
    expect(search).not.toHaveBeenCalled()
  })

  test('Comments is a TextArea with a 3500 characters-remaining counter (AC11 / BR-10)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))))
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    const comments = await screen.findByLabelText('Comments')
    expect(comments.tagName).toBe('TEXTAREA')
    expect(screen.getByText('3500 characters remaining')).toBeInTheDocument()
    await user.type(comments, 'hello')
    expect(screen.getByText('3495 characters remaining')).toBeInTheDocument()
  })

  test('Check Status all-met renders SUC-004 and SUC-003 (AC7)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: true,
          errors: [],
          requirementsMetMessage: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
          message: { key: 'checkStatusMessage', text: 'Status has been checked' },
        }),
      ),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('North Ridge')
    await user.click(checkButtons()[0])

    expect(await screen.findByText('Status has been checked')).toBeInTheDocument()
    expect(screen.getByText('All requirements for this schedule have been met')).toBeInTheDocument()
  })

  test('Check Status missing cost renders the verbatim FLD-004 (double space) + SUC-004, no SUC-003 (AC7)', async () => {
    const fld004 = 'location  : North Ridge - Actual cost: Value Required'
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [{ key: 'missingRequiredFieldMsg', text: fld004 }],
          requirementsMetMessage: null,
          message: { key: 'checkStatusMessage', text: 'Status has been checked' },
        }),
      ),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('North Ridge')
    await user.click(checkButtons()[0])

    expect(await screen.findByText(fld004, { normalizer: verbatim })).toBeInTheDocument()
    expect(screen.getByText('Status has been checked')).toBeInTheDocument()
    expect(
      screen.queryByText('All requirements for this schedule have been met'),
    ).not.toBeInTheDocument()
  })

  test('Check Status locks while in flight — one POST per click (AC7)', async () => {
    const check = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, async () => {
        check()
        await delay(150)
        return HttpResponse.json({
          requirementsMet: true,
          errors: [],
          requirementsMetMessage: null,
          message: { key: 'checkStatusMessage', text: 'Status has been checked' },
        })
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('North Ridge')
    await user.click(checkButtons()[0])
    // The in-flight lock disables both bars (and the rest of the write surface) until it resolves.
    await waitFor(() => {
      for (const button of checkButtons()) {
        expect(button).toBeDisabled()
      }
    })
    expect(await screen.findByText('Status has been checked')).toBeInTheDocument()
    await waitFor(() => {
      for (const button of checkButtons()) {
        expect(button).toBeEnabled()
      }
    })
    expect(check).toHaveBeenCalledTimes(1)
  })

  // The third column is the notification TITLE, added on the #464 review round. The verbatim detail
  // was already asserted, but all three states used to share one heading — so a closed mill read as
  // "Unable to load Schedule 11", a load failure, rather than as the context the operator has to change
  // on the Home Page. Only the 404 is a failure; the other two are context guards with their own
  // titles, and the pairing below is what holds them apart.
  test.each([
    [
      400,
      'Please Select Mill and Reporting Year in the Home Page. ',
      'Mill and Reporting Year required',
    ],
    [
      409,
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
      'Mill not active for Reporting Year',
    ],
    [404, 'Schedule not found.', 'Unable to load Schedule 11'],
  ])(
    'guard state %i renders the verbatim detail under its own title and suppresses content (AC8)',
    async (status, detail, title) => {
      server.use(http.get(URL, () => problemBody(status, detail)))
      renderAsSubmitter(<Schedule11 />)

      expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
      expect(screen.getByText(title)).toBeInTheDocument()
      expect(screen.queryByText('Silviculture Locations')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    },
  )

  test('missing mill/year context short-circuits before any GET (AC8)', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    renderAsSubmitter(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule11 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
  })

  test('read-only (editable:false) renders masked text and disables Save AND Check Status (AC9)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderAsSubmitter(<Schedule11 />)

    const row = await findRow('North Ridge')
    // Values render as text with the legacy masks, not inputs.
    expect(within(row).getByText('ICHdw1')).toBeInTheDocument()
    expect(within(row).getByText('No')).toBeInTheDocument()
    expect(within(row).getByText('120.5')).toBeInTheDocument()
    expect(within(row).getByText('25,000')).toBeInTheDocument()
    expect(within(row).getByText('10,000')).toBeInTheDocument()
    expect(within(row).queryByRole('textbox')).not.toBeInTheDocument()
    // Whole write surface gone / disabled.
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument()
    // Save and Check Status stay rendered, both bars, disabled (S20/legacy parity) — and carry no
    // "save first" hint, which would be untrue on a page that cannot save.
    for (const button of [...saveButtons(), ...checkButtons()]) {
      expect(button).toBeDisabled()
    }
    for (const button of checkButtons()) {
      expect(button).not.toHaveAttribute('aria-describedby')
    }
  })

  test('a backend 400 renders the verbatim detail and retains the entered inputs (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, () =>
        problemBody(
          400,
          'Biogeo/Subzone/Variant code is invalid. The code must be corrected before the schedule can be saved.',
        ),
      ),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'Yes' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '120.5')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(
      await screen.findByText(
        'Biogeo/Subzone/Variant code is invalid. The code must be corrected before the schedule can be saved.',
      ),
    ).toBeInTheDocument()
    // Entered values retained for correction (inputs untouched on the .catch branch).
    expect(screen.getByLabelText('Location')).toHaveValue('North Ridge')
    expect(screen.getByLabelText('NAR(ha)')).toHaveValue('120.5')
  })

  // ---- Issue #332: the page's OWN fallback strings, reached with a detail-less failure. ----------
  // `extractDetail` returns `response.data.detail` or undefined, so an empty-bodied 500 (a gateway
  // timeout, a crashed backend) is what makes each `|| '…'` arm the text the user actually sees.
  // Every failure fixture above carries a detail, so none of these strings was asserted before.
  const detailLess500 = () => new HttpResponse(null, { status: 500 })

  test('a load failure carrying no detail falls back to the generic load message (#332)', async () => {
    server.use(http.get(URL, detailLess500))
    renderAsSubmitter(<Schedule11 />)

    // Exact match on purpose: the panel's TITLE is the same words without the full stop.
    expect(await screen.findByText('Unable to load Schedule 11.')).toBeInTheDocument()
    expect(screen.queryByText('Silviculture Locations')).not.toBeInTheDocument()
  })

  test('a detail-less add failure falls back to the generic Save message and retains the inputs (#332)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, detailLess500),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '120.5')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
    expect(screen.getByText('Action failed')).toBeInTheDocument()
    // Inputs are cleared only on success, and the in-flight lock releases for a retry.
    expect(screen.getByLabelText('Location')).toHaveValue('North Ridge')
    await waitFor(() => expect(screen.getByRole('button', { name: /^add$/i })).toBeEnabled())
  })

  test('a detail-less Save failure falls back to the generic Save message and keeps the edit (#332)', async () => {
    // Migrated from the per-row edit PUT (Story 26.2): the edit goes out on the page-level Save.
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, detailLess500),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('North Ridge')
    changeRowField('Location', 'North Ridge', 'North Ridge Revised')
    await user.click(saveButtons()[0])

    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
    // Pending work is kept on every refusal: the typed value stays, and Save is live for a retry.
    expect(screen.getByLabelText('Location for North Ridge')).toHaveValue('North Ridge Revised')
    await waitFor(() => expect(saveButtons()[0]).toBeEnabled())
  })

  test('a detail-less Save of a flagged delete falls back to the generic Save message and keeps the flag (#332)', async () => {
    // Migrated from the immediate DELETE (Story 26.2): Delete only flags, and the Save writes it.
    // Its old fallback, "Unable to delete location.", went with the per-row DELETE.
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, detailLess500),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await flagDelete(user, 'North Ridge')
    await user.click(saveButtons()[0])

    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
    // Nothing was applied: the row stays flagged (off the table) and no success banner appears.
    expect(queryRow('North Ridge')).toBeNull()
    expect(screen.queryByText(/successfully/i)).not.toBeInTheDocument()
    await waitFor(() => expect(saveButtons()[0]).toBeEnabled())
  })

  test('a detail-less Check Status failure falls back to the generic check message (#332)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, detailLess500),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('North Ridge')
    await user.click(checkButtons()[0])

    expect(await screen.findByText('Unable to check status.')).toBeInTheDocument()
    // No check result renders beside the error, and the in-flight lock releases for a retry.
    expect(screen.queryByText('Status checked')).not.toBeInTheDocument()
    await waitFor(() => expect(checkButtons()[0]).toBeEnabled())
  })

  test('a stale GET (mill/year changed mid-flight) is ignored (useScheduleDocument active flag)', async () => {
    // The initial context (13050) returns a slow docA; a mid-flight context change to 999 returns
    // docB immediately. The stale docA must never override docB.
    server.use(
      http.get(URL, async ({ request }) => {
        if (request.url.includes('millId=999')) {
          return HttpResponse.json(
            doc({ locations: [{ ...northRidge, location: 'South Valley' }] }),
          )
        }
        await delay(120)
        return HttpResponse.json(doc())
      }),
    )

    renderAsSubmitter(<StaleRaceHarness />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await findRow('South Valley')).toBeInTheDocument()
    await delay(200)
    expect(queryRow('North Ridge')).toBeNull()
  })

  test('a stale add response (mill/year changed mid-flight) is ignored', async () => {
    // The POST is dispatched under the initial context (13050); the context then changes to 999 and
    // its GET loads South Valley. When the slow POST resolves, its old-mill document and success
    // banner must NOT apply over the fresh context.
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(doc({ locations: [{ ...northRidge, location: 'South Valley' }] }))
          : HttpResponse.json(doc({ locations: [], totals: {} })),
      ),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, async () => {
        await delay(300)
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    // Explicit initial context: the sibling stale-GET test persists 999/2020 to localStorage, and a
    // no-op "change" to the same context would defeat the race this test exists to exercise.
    renderAsSubmitter(
      <MillYearProvider initial={{ millId: 13050, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'Yes' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '10')
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await findRow('South Valley')).toBeInTheDocument()
    // Let the stale POST resolve, then confirm nothing from it landed.
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(queryRow('North Ridge')).toBeNull()
    expect(queryRow('South Valley')).not.toBeNull()
  })

  test('a stale Save response (mill/year changed mid-flight) is ignored', async () => {
    // The bulk PUT's own guard: dispatched under 13050, resolved after the context moved to 999. Its
    // echo and banner belong to the old report and must not land on the new one.
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json(doc({ locations: [{ ...northRidge, location: 'South Valley' }] }))
          : HttpResponse.json(doc()),
      ),
      http.put(LOCATIONS_URL, async () => {
        await delay(300)
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    renderAsSubmitter(
      <MillYearProvider initial={{ millId: 13050, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await findRow('North Ridge')
    changeRowField('Comments', 'North Ridge', 'old mill note')
    await user.click(saveButtons()[0])
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await findRow('South Valley')).toBeInTheDocument()
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(queryRow('North Ridge')).toBeNull()
    // The fresh context starts clean: the old report's pending edit did not follow it.
    expect(screen.getByLabelText('Comments for South Valley')).toHaveValue('')
  })
})

describe('Schedule 11 page-level Save (Story 26.2)', () => {
  const southBench: SilvicultureLocation = {
    locationId: 9004,
    location: 'South Bench',
    enhancedIndicator: false,
    biogeoclimaticCatalogueId: 321,
    becLabel: 'ICHdw1',
    netArea: 12.5,
    actualCost: null,
    plannedCost: null,
    totalCost: null,
    costPerNetArea: null,
    comments: null,
    revisionCount: 1,
  }

  test('Save sends ONE PUT: the edited rows, with their revisions, plus the flagged ids — nothing untouched (AC1)', async () => {
    // Review D-R1 (ruled 1b, deviation (E)): only edited rows are sent. Legacy re-stamped every row,
    // but it had no optimistic lock; with ours an untouched row another session changed would refuse
    // the whole save. The flagged row appears ONLY in deletedIds.
    const bodies: LocationSaveAllRequest[] = []
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope, alderFlat] }))),
      http.put(LOCATIONS_URL, async ({ request }) => {
        bodies.push((await request.json()) as LocationSaveAllRequest)
        return HttpResponse.json(doc({ locations: [northRidge, alderFlat], message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    changeRowField('NAR(ha)', 'North Ridge', '130')
    await flagDelete(user, 'Mid Slope')
    await user.click(saveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(bodies).toHaveLength(1)
    const [body] = bodies
    expect(Object.keys(body).sort()).toEqual(['deletedIds', 'locations'])
    expect(body.deletedIds).toEqual([9002])
    // Alder Flat was never touched: it is not sent.
    expect(body.locations.map((item) => item.basicSilvicultureReportId)).toEqual([9001])
    expect(body.locations[0].location).toEqual({
      location: 'North Ridge',
      enhancedIndicator: false,
      biogeoclimaticCatalogueId: 321,
      netArea: 130,
      actualCost: 25000,
      plannedCost: 10000,
      comments: null,
      revisionCount: 4,
    })
  })

  test('Add keeps pending edits and flags, and an edit keeps the revision it was made against (S24)', async () => {
    // Add persists only the new row (legacy addLocation() -> save(true)). Its echo re-serves every
    // row; here North Ridge comes back at revision 5 — someone else saved it meanwhile. The user's
    // edit was made against 4, so Save must send 4 and let the server answer 409, not 5 and silently
    // overwrite the other change.
    const bodies: LocationSaveAllRequest[] = []
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({
            locations: [{ ...northRidge, revisionCount: 5 }, midSlope, southBench],
            message: SAVED,
          }),
        ),
      ),
      http.put(LOCATIONS_URL, async ({ request }) => {
        bodies.push((await request.json()) as LocationSaveAllRequest)
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    changeRowField('NAR(ha)', 'North Ridge', '130')
    await flagDelete(user, 'Mid Slope')

    fireEvent.change(screen.getByLabelText('Location'), { target: { value: 'South Bench' } })
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    fireEvent.change(screen.getByLabelText('NAR(ha)'), { target: { value: '12.5' } })
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    await findRow('South Bench')
    // Pending work survived the refreshed document.
    expect(screen.getByLabelText('NAR(ha) for North Ridge')).toHaveValue('130')
    expect(queryRow('Mid Slope')).toBeNull()
    for (const button of saveButtons()) {
      expect(button).toBeEnabled()
    }

    await user.click(saveButtons()[0])

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0].deletedIds).toEqual([9002])
    // The added row was never edited, so it is not sent (review D-R1).
    expect(bodies[0].locations.map((item) => item.basicSilvicultureReportId)).toEqual([9001])
    expect(bodies[0].locations[0].location).toMatchObject({ netArea: 130, revisionCount: 4 })
  })

  test('a refused Save keeps every pending edit and flag, and the same Save can be retried', async () => {
    // A stale row or a clash must never cost the user the corrections they made.
    const bodies: LocationSaveAllRequest[] = []
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))),
      http.put(LOCATIONS_URL, async ({ request }) => {
        bodies.push((await request.json()) as LocationSaveAllRequest)
        return problemBody(409, STALE)
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    changeRowField('Location', 'North Ridge', 'North Ridge Revised')
    await flagDelete(user, 'Mid Slope')
    await user.click(saveButtons()[0])

    expect(await screen.findByText(STALE)).toBeInTheDocument()
    expect(screen.getByLabelText('Location for North Ridge')).toHaveValue('North Ridge Revised')
    expect(queryRow('Mid Slope')).toBeNull()
    for (const button of saveButtons()) {
      expect(button).toBeEnabled()
    }
    for (const button of checkButtons()) {
      expect(button).toBeDisabled()
    }

    await user.click(saveButtons()[1])

    await waitFor(() => expect(bodies).toHaveLength(2))
    expect(bodies[1]).toEqual(bodies[0])
  })

  test('Save and Check Status render above AND below the table, and the bottom Save saves', async () => {
    // Legacy drew both rows (schedule11.xhtml:185-194, :420-429).
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    const row = await findRow('North Ridge')
    const table = screen.getByRole('table', { name: 'Silviculture Locations' })
    const [topSave, bottomSave] = saveButtons()
    const [topCheck, bottomCheck] = checkButtons()
    expect(saveButtons()).toHaveLength(2)
    expect(checkButtons()).toHaveLength(2)
    // Document order: one pair before the table, one after it.
    for (const before of [topSave, topCheck]) {
      expect(before.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    }
    for (const after of [bottomSave, bottomCheck]) {
      expect(after.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_PRECEDING).toBeTruthy()
    }

    changeRowField('Comments', 'North Ridge', 'bottom bar')
    expect(within(row).getByLabelText('Comments for North Ridge')).toHaveValue('bottom bar')
    await user.click(bottomSave)

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledTimes(1)
  })

  test('Save locks while in flight — row controls and both bars disabled, one PUT for two clicks', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, async () => {
        put()
        await delay(150)
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)

    await findRow('North Ridge')
    changeRowField('Comments', 'North Ridge', 'once')
    const [topSave, bottomSave] = saveButtons()
    fireEvent.click(topSave)
    fireEvent.click(bottomSave)

    await waitFor(() => expect(screen.getByLabelText('Location for North Ridge')).toBeDisabled())
    for (const button of [...saveButtons(), ...checkButtons()]) {
      expect(button).toBeDisabled()
    }
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledTimes(1)
  })

  test.each([
    ['an edit', 'edit'],
    ['a flagged delete', 'delete'],
  ])(
    'Check Status is greyed, and says why, while %s is unsaved — and comes back after the Save (D7)',
    async (_label, change) => {
      // The check reads what is STORED, so running it over unsaved changes would describe a report
      // nobody saved (26.2 D7(a), deviation (C)).
      server.use(
        http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))),
        http.put(LOCATIONS_URL, () => HttpResponse.json(doc({ message: SAVED }))),
      )
      renderAsSubmitter(<Schedule11 />)
      const user = userEvent.setup()

      await findRow('Mid Slope')
      for (const button of checkButtons()) {
        expect(button).toBeEnabled()
        expect(button).not.toHaveAttribute('aria-describedby')
      }

      if (change === 'edit') {
        changeRowField('Planned Cost ($)', 'Mid Slope', '1500')
      } else {
        await flagDelete(user, 'Mid Slope')
      }

      for (const button of checkButtons()) {
        expect(button).toBeDisabled()
        expect(button).toHaveAccessibleDescription(CHECK_NEEDS_SAVE)
      }

      await user.click(saveButtons()[0])

      expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
      for (const button of checkButtons()) {
        expect(button).toBeEnabled()
        expect(button).not.toHaveAttribute('aria-describedby')
      }
    },
  )

  test('a check verdict is cleared by flagging a row, too (the other direction)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: true,
          errors: [],
          requirementsMetMessage: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
          message: { key: 'checkStatusMessage', text: 'Status has been checked' },
        }),
      ),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    await user.click(checkButtons()[0])
    expect(await screen.findByText('Status has been checked')).toBeInTheDocument()

    await flagDelete(user, 'Mid Slope')

    // The verdict described a table that still held Mid Slope.
    expect(screen.queryByText('Status has been checked')).not.toBeInTheDocument()
    expect(
      screen.queryByText('All requirements for this schedule have been met'),
    ).not.toBeInTheDocument()
  })

  test('typing a value back to what was served is not a change: Save greys, Check Status returns', async () => {
    // A row is pending only while its form DIFFERS from the served row (review P6).
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderAsSubmitter(<Schedule11 />)

    await findRow('North Ridge')
    changeRowField('NAR(ha)', 'North Ridge', '120.55')
    for (const button of saveButtons()) {
      expect(button).toBeEnabled()
    }
    changeRowField('NAR(ha)', 'North Ridge', '120.5')

    for (const button of saveButtons()) {
      expect(button).toBeDisabled()
    }
    for (const button of checkButtons()) {
      expect(button).toBeEnabled()
      expect(button).not.toHaveAttribute('aria-describedby')
    }
    expect(put).not.toHaveBeenCalled()
  })

  test('a row edited and typed back is not sent with a real edit elsewhere', async () => {
    const bodies: LocationSaveAllRequest[] = []
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))),
      http.put(LOCATIONS_URL, async ({ request }) => {
        bodies.push((await request.json()) as LocationSaveAllRequest)
        return HttpResponse.json(doc({ locations: [northRidge, midSlope], message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    changeRowField('Comments', 'North Ridge', 'typed')
    changeRowField('Comments', 'North Ridge', '')
    changeRowField('Comments', 'Mid Slope', 'kept')
    await user.click(saveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(bodies[0].locations.map((item) => item.basicSilvicultureReportId)).toEqual([9002])
  })

  test('a flag or an edit on a row that leaves the document is dropped, never sent to a 404 (P1)', async () => {
    // Another session deleted Mid Slope and Alder Flat; the Add echo no longer serves them. The flag
    // on one and the edit on the other must not ride along on every later Save — the atomic save
    // would answer "Location not found." forever, with no control left to clear them.
    const bodies: LocationSaveAllRequest[] = []
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope, alderFlat] }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({
            locations: [northRidge, { ...northRidge, locationId: 9004, location: 'South Bench' }],
            message: SAVED,
          }),
        ),
      ),
      http.put(LOCATIONS_URL, async ({ request }) => {
        bodies.push((await request.json()) as LocationSaveAllRequest)
        return HttpResponse.json(doc({ message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Alder Flat')
    await flagDelete(user, 'Mid Slope')
    changeRowField('Comments', 'Alder Flat', 'about to vanish')

    fireEvent.change(screen.getByLabelText('Location'), { target: { value: 'South Bench' } })
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    fireEvent.change(screen.getByLabelText('NAR(ha)'), { target: { value: '12.5' } })
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    await findRow('South Bench')

    // Nothing that is still served is pending: Save greys and Check Status comes back.
    for (const button of saveButtons()) {
      expect(button).toBeDisabled()
    }
    for (const button of checkButtons()) {
      expect(button).toBeEnabled()
    }

    // A real edit afterwards goes out alone.
    changeRowField('Comments', 'North Ridge', 'real edit')
    await user.click(saveButtons()[0])
    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0].deletedIds).toEqual([])
    expect(bodies[0].locations.map((item) => item.basicSilvicultureReportId)).toEqual([9001])
  })

  test('flagging every row does not claim none were ever added (P7)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ locations: [northRidge, midSlope] }))))
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    await flagDelete(user, 'North Ridge')
    await flagDelete(user, 'Mid Slope')

    expect(
      screen.getByText('Every location is marked for deletion. Save to remove them.'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/no silviculture locations have been added/i)).not.toBeInTheDocument()
  })

  test('two rows sharing a location name get distinct control names, told apart by BEC (P8)', async () => {
    // The unique key is biogeo + location, so a name may repeat.
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ locations: [northRidge, { ...midSlope, location: 'North Ridge' }] }),
        ),
      ),
    )
    renderAsSubmitter(<Schedule11 />)

    expect(await screen.findByLabelText('NAR(ha) for North Ridge (ICHdw1)')).toHaveValue('120.5')
    expect(screen.getByLabelText('NAR(ha) for North Ridge (MSdm2)')).toHaveValue('60')
    expect(screen.queryByLabelText('NAR(ha) for North Ridge')).not.toBeInTheDocument()
  })

  test('a check verdict is cleared by the first change after it', async () => {
    // A verdict names fields by their value at the time; once the user edits, it describes a report
    // that is no longer on the screen.
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            {
              key: 'missingRequiredFieldMsg',
              text: 'location  : North Ridge - Actual cost: Value Required',
            },
          ],
          requirementsMetMessage: null,
          message: { key: 'checkStatusMessage', text: 'Status has been checked' },
        }),
      ),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('North Ridge')
    await user.click(checkButtons()[0])
    expect(await screen.findByText('Status has been checked')).toBeInTheDocument()

    changeRowField('Actual Cost ($)', 'North Ridge', '26000')

    expect(screen.queryByText('Status has been checked')).not.toBeInTheDocument()
    expect(screen.queryByText(/Actual cost: Value Required/)).not.toBeInTheDocument()
  })
})

describe('Schedule 11 column sorting (legacy p:column sortBy parity)', () => {
  // Document order as the API returned it, which the third click must restore.
  const threeRows = [northRidge, midSlope, alderFlat]
  const DOC_ORDER = ['North Ridge', 'Mid Slope', 'Alder Flat', 'Totals']

  const renderSorted = async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ locations: threeRows }))))
    renderAsSubmitter(<Schedule11 />)
    await findRow('North Ridge')
    return userEvent.setup()
  }

  // First cell of every body row, so assertions read as the visible order. The Totals row is part
  // of the same tbody, which is exactly why it appears in these expectations — it must stay last.
  // An editable row's first cell holds its name in an input, so read the input's value where there
  // is one.
  const rowLabels = () => {
    const [, body] = screen.getAllByRole('rowgroup')
    return within(body)
      .getAllByRole('row')
      .map((row) => {
        const cell = (row as HTMLTableRowElement).cells[0]
        return cell.querySelector('input')?.value ?? cell.textContent
      })
  }

  // Resolved via the label element, not getByRole('columnheader', { name }): Carbon renders its
  // screen-reader sort hint INSIDE the th, so the header's accessible name is really
  // "Click to sort rows by Location header in ascending order Location" — and it changes as the
  // sort state changes. The label div is stable and present in both the sortable and plain
  // branches of TableHeader. Scoping by selector also avoids colliding with the identically
  // named Add-panel field labels.
  const header = (name: string) =>
    screen.getByText(name, { selector: '.cds--table-header-label' }).closest('th') as HTMLElement

  test('every column carries a sort control except Comments and Actions (xhtml:353/364)', async () => {
    await renderSorted()

    for (const name of [
      'Location',
      'Biogeo/Subzone/Variant',
      'ES',
      'NAR(ha)',
      'Actual Cost ($)',
      'Planned Cost ($)',
      'Total Act Plus Plan Cost ($)',
      'Total/NAR(ha)',
    ]) {
      expect(within(header(name)).getByRole('button')).toBeInTheDocument()
    }
    // The two legacy columns with no sortBy stay inert.
    expect(within(header('Comments')).queryByRole('button')).not.toBeInTheDocument()
    expect(within(header('Actions')).queryByRole('button')).not.toBeInTheDocument()
  })

  test('a text column cycles ascending -> descending -> document order', async () => {
    const user = await renderSorted()
    expect(rowLabels()).toEqual(DOC_ORDER)

    await user.click(within(header('Location')).getByRole('button'))
    expect(rowLabels()).toEqual(['Alder Flat', 'Mid Slope', 'North Ridge', 'Totals'])
    expect(header('Location')).toHaveAttribute('aria-sort', 'ascending')

    await user.click(within(header('Location')).getByRole('button'))
    expect(rowLabels()).toEqual(['North Ridge', 'Mid Slope', 'Alder Flat', 'Totals'])
    expect(header('Location')).toHaveAttribute('aria-sort', 'descending')

    // Third click releases the sort — back to the order the API sent.
    await user.click(within(header('Location')).getByRole('button'))
    expect(rowLabels()).toEqual(DOC_ORDER)
    expect(header('Location')).toHaveAttribute('aria-sort', 'none')
  })

  test('numeric columns sort by value, not lexicographically', async () => {
    const user = await renderSorted()

    await user.click(within(header('NAR(ha)')).getByRole('button'))
    // Lexicographic would give 120.5 < 60 < 9.
    expect(rowLabels()).toEqual(['Alder Flat', 'Mid Slope', 'North Ridge', 'Totals'])

    await user.click(within(header('NAR(ha)')).getByRole('button'))
    expect(rowLabels()).toEqual(['North Ridge', 'Mid Slope', 'Alder Flat', 'Totals'])
  })

  test('blank cells rank last in BOTH directions, never above real values', async () => {
    const user = await renderSorted()

    // Alder Flat has no actual cost: 5,000 then 25,000 then the blank.
    await user.click(within(header('Actual Cost ($)')).getByRole('button'))
    expect(rowLabels()).toEqual(['Mid Slope', 'North Ridge', 'Alder Flat', 'Totals'])

    // Descending flips the two real values but must NOT lift the blank to the top.
    await user.click(within(header('Actual Cost ($)')).getByRole('button'))
    expect(rowLabels()).toEqual(['North Ridge', 'Mid Slope', 'Alder Flat', 'Totals'])
  })

  test('booleans sort false-before-true (legacy sorted the raw indicator, xhtml:255)', async () => {
    const user = await renderSorted()

    await user.click(within(header('ES')).getByRole('button'))
    // North Ridge (No) and Alder Flat (No) precede Mid Slope (Yes).
    expect(rowLabels().slice(0, 2).sort()).toEqual(['Alder Flat', 'North Ridge'])
    expect(rowLabels()[2]).toBe('Mid Slope')
  })

  test('sorting one column resets any other column to unsorted', async () => {
    const user = await renderSorted()

    await user.click(within(header('Location')).getByRole('button'))
    await user.click(within(header('Location')).getByRole('button'))
    expect(header('Location')).toHaveAttribute('aria-sort', 'descending')

    // Switching columns starts fresh at ascending rather than inheriting 'descending'.
    await user.click(within(header('NAR(ha)')).getByRole('button'))
    expect(header('NAR(ha)')).toHaveAttribute('aria-sort', 'ascending')
    expect(header('Location')).toHaveAttribute('aria-sort', 'none')
    expect(rowLabels()).toEqual(['Alder Flat', 'Mid Slope', 'North Ridge', 'Totals'])
  })

  test('an unsaved edit re-sorts nothing, and the sort survives the Save (AC3 interaction)', async () => {
    // The sort is presentation-only and deliberately outlives a mutation (index.tsx: a re-sort
    // after every save would yank the row the user is working on back to document order). It orders
    // by the SERVED values, so typing never moves the row under the user's cursor either.
    const user = await renderSorted()
    server.use(
      http.put(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({
            locations: [northRidge, midSlope, { ...alderFlat, location: 'Zulu Flat' }],
            message: SAVED,
          }),
        ),
      ),
    )

    await user.click(within(header('Location')).getByRole('button'))
    expect(rowLabels()).toEqual(['Alder Flat', 'Mid Slope', 'North Ridge', 'Totals'])

    // Rename the FIRST row to a name that sorts LAST, so the failure modes separate: if the save
    // dropped the sort the refreshed rows come back in document order (North Ridge, Mid Slope, Zulu
    // Flat); if it kept a stale pre-save ordering Zulu Flat stays first.
    changeRowField('Location', 'Alder Flat', 'Zulu Flat')
    expect(rowLabels()).toEqual(['Zulu Flat', 'Mid Slope', 'North Ridge', 'Totals'])
    await user.click(saveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(rowLabels()).toEqual(['Mid Slope', 'North Ridge', 'Zulu Flat', 'Totals'])
    expect(header('Location')).toHaveAttribute('aria-sort', 'ascending')
  })
})

/**
 * A location the ministry has corrected: its originals are the Licensee's submission (16.2), as the
 * backend reads them from the snapshot views (26.2 AC 7). Shared by the indicator block and the 16.3
 * block below.
 */
const corrected: SilvicultureLocation = {
  ...northRidge,
  location: 'North Ridge Revised',
  enhancedIndicator: true,
  comments: 'the ministry corrected this',
  originalValues: {
    location: { value: 'North Ridge', tooltip: 'Original Submission Value: North Ridge' },
    // Compared by id, SHOWN by label (legacy schedule11.xhtml:251, 26.2 AC 7).
    biogeoclimaticCatalogueId: { value: '320', tooltip: 'Original Submission Value: ICHmw2' },
    netArea: { value: '118', tooltip: 'Original Submission Value: 118.0' },
    actualCost: { value: '24000', tooltip: 'Original Submission Value: 24,000' },
    plannedCost: { value: '9000', tooltip: 'Original Submission Value: 9,000' },
  },
}

/**
 * Schedule 11's original-value indicators (Story 16.2), and the two fields that deliberately carry
 * none. Both omissions were queried in review of PR #452, so they are pinned here rather than left
 * to a code comment — a later reader "completing" the set would introduce a false indicator on
 * every row, which is the opposite of the audit evidence this feature exists to give.
 *
 * `schedule11.xhtml` draws six indicator buttons. Five are reproduced. The sixth, on the Enhanced
 * control, never showed a true original in legacy either: its "original" was the CURRENT persisted
 * value (Schedule11DAO.java:226), and `BASIC_SILVICULTURE_REPORT_S_VW` does not select
 * `ENHANCED_IND` (16.2 F4/D5; 26.2 D3). Comments is not one of the six at all — legacy persists
 * `commentsOriginalVal` but declares no accessor and draws no button (AC7).
 */
describe('Schedule 11 original-value indicators', () => {
  const renderCorrected = async (location: SilvicultureLocation = corrected) => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', locations: [location] }))),
    )
    renderAsAdmin(<Schedule11 />)
    return findRow(location.location)
  }

  test('renders them on the corrected fields, naming the field and describing the original', async () => {
    const row = await renderCorrected()

    const location = within(row).getByTestId('original-value-location')
    expect(location).toHaveAccessibleName('Location differs from the originally submitted value')
    expect(location).toHaveAccessibleDescription('Original Submission Value: North Ridge')
    expect(within(row).getByTestId('original-value-netArea')).toHaveAccessibleDescription(
      'Original Submission Value: 118.0',
    )
    expect(within(row).getByTestId('original-value-actualCost')).toHaveAccessibleDescription(
      'Original Submission Value: 24,000',
    )
    expect(within(row).getByTestId('original-value-plannedCost')).toHaveAccessibleDescription(
      'Original Submission Value: 9,000',
    )
  })

  test('the BEC indicator compares by id but describes the original by its LABEL (26.2 AC 7)', async () => {
    // Legacy showed the catalogue label (schedule11.xhtml:251); an id in the tooltip would mean
    // nothing to a verifier.
    const row = await renderCorrected()

    const bec = within(row).getByTestId('original-value-biogeoclimaticCatalogueId')
    expect(bec).toHaveAccessibleName(
      'Biogeo/Subzone/Variant differs from the originally submitted value',
    )
    expect(bec).toHaveAccessibleDescription('Original Submission Value: ICHmw2')
  })

  test('an unchanged BEC carries no indicator — the served id is what is compared, not the label', async () => {
    // The tooltip SHOWS the label, but the value the server compares against is the catalogue id
    // (26.2 AC 7). Comparing the label with that id would flag every row of every submitted report.
    const row = await renderCorrected({
      ...corrected,
      originalValues: {
        ...corrected.originalValues,
        biogeoclimaticCatalogueId: { value: '321', tooltip: 'Original Submission Value: ICHdw1' },
      },
    })

    expect(within(row).getByTestId('original-value-location')).toBeInTheDocument()
    expect(
      within(row).queryByTestId('original-value-biogeoclimaticCatalogueId'),
    ).not.toBeInTheDocument()
  })

  test('an indicator tracks the unsaved value: correcting back to the submission clears it', async () => {
    // Legacy re-rendered the icon on the ajax change; the modern page compares the live form value.
    const row = await renderCorrected()

    changeRowField('Location', 'North Ridge Revised', 'North Ridge')
    changeRowField('NAR(ha)', 'North Ridge Revised', '118.0')

    expect(within(row).queryByTestId('original-value-location')).not.toBeInTheDocument()
    // Numeric fields compare by value, so "118.0" is the submitted 118.
    expect(within(row).queryByTestId('original-value-netArea')).not.toBeInTheDocument()
    expect(within(row).getByTestId('original-value-actualCost')).toBeInTheDocument()
  })

  test('a location added since submission flags every tracked field with the empty original', async () => {
    // The server gives such a row an entry for every tracked field with nothing on file (26.2 AC 7);
    // any value it holds is a value added since submission (16.2 branch 3).
    const emptyOriginal = { value: '', tooltip: 'Original Submission Value: ' }
    const added: SilvicultureLocation = {
      ...midSlope,
      originalValues: {
        location: emptyOriginal,
        biogeoclimaticCatalogueId: emptyOriginal,
        netArea: emptyOriginal,
        actualCost: emptyOriginal,
        plannedCost: emptyOriginal,
      },
    }
    const row = await renderCorrected(added)

    for (const field of [
      'location',
      'biogeoclimaticCatalogueId',
      'netArea',
      'actualCost',
      'plannedCost',
    ]) {
      expect(within(row).getByTestId(`original-value-${field}`)).toHaveAccessibleDescription(
        /^Original Submission Value:\s*$/,
      )
    }
    expect(within(row).queryByTestId('original-value-enhancedIndicator')).not.toBeInTheDocument()
    expect(within(row).queryByTestId('original-value-comments')).not.toBeInTheDocument()
  })

  test('Enhanced carries NO indicator — legacy draws one but it never showed a true original (F4/D5, 26.2 D3)', async () => {
    const row = await renderCorrected()

    // The control is there and has been corrected (false → true); the indicator is still absent,
    // because no submitted value for ENHANCED_IND exists. Rendering one would take the "added since
    // submission" branch and flag every row of every submitted report.
    expect(
      within(row).getByRole('combobox', { name: 'Enhanced for North Ridge Revised' }),
    ).toBeInTheDocument()
    expect(within(row).queryByTestId('original-value-enhancedIndicator')).not.toBeInTheDocument()
  })

  test('Comments carries NO indicator — legacy declares none for this schedule (AC7)', async () => {
    const row = await renderCorrected()

    expect(within(row).getByLabelText('Comments for North Ridge Revised')).toHaveValue(
      'the ministry corrected this',
    )
    expect(within(row).queryByTestId('original-value-comments')).not.toBeInTheDocument()
  })

  test('a read-only row whose catalogue label is missing compares its served id — no false flag (P5)', async () => {
    // The live row forces a re-pick (BR-09); a read-only row cannot re-pick, and still holds the id
    // it was saved with. Comparing '' against the submission would flag a field nobody changed.
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({
            trackStatus: 'S',
            editable: false,
            locations: [
              {
                ...northRidge,
                becLabel: null,
                originalValues: {
                  biogeoclimaticCatalogueId: {
                    value: '321',
                    tooltip: 'Original Submission Value: ICHdw1',
                  },
                },
              },
            ],
          }),
        ),
      ),
    )
    renderAsSubmitter(<Schedule11 />)

    const row = await findRow('North Ridge')
    expect(
      within(row).queryByTestId('original-value-biogeoclimaticCatalogueId'),
    ).not.toBeInTheDocument()
  })

  test('no indicator anywhere at Draft', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ trackStatus: 'D', locations: [{ ...corrected, originalValues: null }] }),
        ),
      ),
    )
    renderAsSubmitter(<Schedule11 />)
    await findRow('North Ridge Revised')

    expect(screen.queryByTestId(/^original-value-/)).not.toBeInTheDocument()
  })
})

// -------------------------------------------------------------------------------------------------
// Story 16.3 — the ministry correction journey on the SILVICULTURE track.
//
// Story 16.1 shipped a server-side role×status editability matrix: a submitter edits only at Draft,
// an administrator edits at Submitted and Verified and is deliberately READ-ONLY at Draft. The page
// learns all of that from the ONE server-computed `editable` boolean and never derives it from the
// status or the acting role (AD-9). Nothing proved the Submitted half of that: every pre-existing
// `'S'` fixture in this file pairs it with `editable: false`, so no test in the suite ever saved,
// deleted or checked status while a correction was actually permitted.
//
// These arms therefore do NOT hardcode `editable`. The MSW GET (and every write echo) COMPUTES it
// from the 16.1 matrix over the acting role the request actually carried in `X-Mock-Groups`, the
// same way Schedule 1's Story 16.3 block does. That is what makes the role declaration
// load-bearing rather than decorative: the admin-at-Submitted arm and the submitter-at-Submitted
// arm below are served by the SAME handler over the SAME document and differ only in who is
// asking, so a broken identity helper shows up as the two arms agreeing.
//
// Schedule 11 is the track that most needed its own evidence. It is the BASIC SILVICULTURE track,
// not the Schedules 1–10 track, and the two are independent by design: legacy's
// `disableUserInputSchedule11()` is a verbatim twin of `disableUserInput()` differing only in the
// getter it reads (`MILL_SILVICULTUR_STATUS_CODE` — the truncated legacy spelling — versus
// `ILCR_MILL_REPORT_STATUS_CODE`). On this document `trackStatus` is therefore the SILVICULTURE
// code, not the 1–10 code (`Schedule11Response.java:7-11`), so the matrix below is applied to it as
// this page's own status and to nothing else.
//
// Schedule 11 also expresses read-only DIFFERENTLY from its eleven siblings, and a future reader
// will assume otherwise. Save and Check Status stay rendered and disabled, as on every page, but the
// row controls do not: when the document is not editable the page HIDES the whole action column,
// renders each row as text, drops its column count from 10 to 9, drops the Add panel entirely and
// never mounts the delete-confirm Modal at all. These arms assert that real behaviour.
//
// Story 26.2 moved the writes onto the page-level Save (one bulk PUT), so the arms below that
// corrected through a per-row PUT or DELETE now correct through that Save instead.
//
// Every user-facing string is asserted VERBATIM against `backend/src/main/resources/messages.properties`
// (AD-8).
// -------------------------------------------------------------------------------------------------

describe('Schedule 11 ministry correction at Submitted and Verified (Stories 16.3, 26.4)', () => {
  // The correction arms below are status-agnostic: legacy opened the page to the administrator at S
  // and at V (UserSessionMB.java:438-444) and nothing after that gate reads the status, so each runs
  // at both. Parameterised rather than copied, so the two statuses cannot drift apart — and so `'V'`
  // is SERVED, which is what a matrix narrowed to `['S']` must fail against.
  const STATUSES = [
    ['Submitted', 'S'],
    ['Verified', 'V'],
  ] as const

  // The PINNED 16.1 matrix (`ScheduleEditability`), per track status — here, per SILVICULTURE track
  // status. Submitter edits at Draft only; admin edits at Submitted and Verified and is
  // DELIBERATELY read-only at Draft while the mill still owns the data. Anything else — a dead `O`,
  // a null status, an unknown role — is read-only.
  //
  // Reproduced rather than imported because the real rule lives in Java: this is the wire contract
  // the frontend is entitled to assume, and stating it makes falsification trivial. Each row is
  // load-bearing and was checked: `ILCR_ADMIN: ['D']` fails all nine admin arms; narrowing it to
  // `['S']` fails every Verified admin arm (the hole that existed while no suite served `'V'`); and
  // `ILCR_SUBMITTER: []` fails the submitter-at-Draft discriminator alone.
  const EDITABLE_STATUSES: Record<string, readonly string[]> = {
    ILCR_ADMIN: ['S', 'V'],
    ILCR_SUBMITTER: ['D'],
  }

  /**
   * The acting role as the request actually carried it. `api-service` mirrors the selected mock
   * user's roles onto `X-Mock-Groups` (api-service.ts:11-17), so this is the same signal the real
   * mock backend gates on — not something the test asserts about itself.
   *
   * The throw is a belt-and-braces guard only, and MUST NOT be mistaken for the identity check:
   * `mockUserGroups()` calls `findMockUser(localStorage…)`, which falls back to `MOCK_USERS[0]` —
   * the ADMIN — so the header is ALWAYS sent and this branch can never fire.
   *
   * Nor is `sentRole` alone the guard. It records what the request CARRIED, and a declared admin and
   * the `MOCK_USERS[0]` fallback admin carry exactly the same header — so an arm that lost its
   * `renderAsAdmin` would still be served, and still asserted, as an administrator. What guards the
   * DECLARATION is `declaredRole()` (test-utils): it reports what this test itself seeded, and null
   * when nothing did. `expectActingAs` below asserts both halves, in the test BODY — an assertion
   * thrown inside an MSW resolver surfaces as a request failure and gets misattributed, so neither
   * can live here.
   */
  const actingRole = (request: Request): string => {
    const header = request.headers.get('X-Mock-Groups')
    if (!header) {
      throw new Error('request carried no X-Mock-Groups header — the acting identity was not sent')
    }
    return header
  }

  // The identity the last editability-bearing request actually carried.
  let sentRole: string | null = null
  beforeEach(() => {
    sentRole = null
  })

  /**
   * Both halves of the identity claim every arm's name makes: the role this test DECLARED, and the
   * role the request CARRIED. Neither is redundant — see `actingRole` above for why the wire half
   * cannot distinguish a declared admin from the silent `MOCK_USERS[0]` fallback, and the
   * declaration half cannot tell you what was actually sent.
   */
  const expectActingAs = (role: IlcrRole, header: string) => {
    expect(declaredRole()).toBe(role)
    expect(sentRole).toBe(header)
  }

  /**
   * A Schedule 11 document — Submitted on its own silviculture track unless overridden — whose
   * `editable` is COMPUTED by the matrix for whoever asked. Extends the suite's `doc()` builder; the
   * computed flag always wins, so no test can quietly hand itself the answer.
   *
   * The matrix is applied to `trackStatus` and ONLY to `trackStatus`, which on this response is the
   * silviculture code. Write echoes go through it too, so a saved document cannot smuggle an
   * editability the matrix would not grant.
   */
  const matrixDoc = (request: Request, over: Record<string, unknown> = {}) => {
    const body = doc({ trackStatus: 'S', ...over })
    const header = actingRole(request)
    sentRole = header
    // `api-service` sends `roles.join(',')` and `ScheduleEditability.forCaller` UNIONS the permitted
    // statuses across every role the caller holds (ScheduleEditability.java:45-46, :84-90). Keying
    // on the raw header instead would encode the wrong rule — and legacy's "read only the first
    // role" bug is exactly what the union replaced. Unknown roles contribute nothing (fail-closed,
    // AD-7), so an unrecognised header is read-only rather than accidentally privileged.
    const permitted = new Set(
      header.split(',').flatMap((role) => EDITABLE_STATUSES[role.trim()] ?? []),
    )
    return { ...body, editable: permitted.has(String(body.trackStatus)) }
  }

  /** GET that answers `editable` per the matrix for whoever is asking. */
  const matrixGet = (over: Record<string, unknown> = {}) =>
    http.get(URL, ({ request }) => HttpResponse.json(matrixDoc(request, over)))

  // The header's action column is this page's read-only tell, so ask about it directly.
  const actionsHeader = () => screen.queryByRole('columnheader', { name: 'Actions' })

  /** Schedule 11's read-only shape — hidden column, no Add panel, no confirm modal (see above). */
  const expectReadOnly = (row: HTMLElement) => {
    // Values render as text, not controls.
    expect(within(row).getByText('ICHdw1')).toBeInTheDocument()
    expect(within(row).queryByRole('textbox')).not.toBeInTheDocument()
    // The Actions column is GONE, not disabled: 9 columns, not 10. Scoped to THIS table (its
    // aria-label), like the row-scoped cell count below — a document-wide count would let any future
    // table elsewhere on the page fail these editability arms for an unrelated reason, with an error
    // message pointing at editability.
    expect(actionsHeader()).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('table', { name: 'Silviculture Locations' })).getAllByRole(
        'columnheader',
      ),
    ).toHaveLength(9)
    expect(within(row).getAllByRole('cell')).toHaveLength(9)
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    // Zero delete-named buttons anywhere: the row's, AND the confirm Modal's primary — the Modal is
    // `editable`-gated, so there is no route to a delete even by reaching past a control.
    expect(screen.queryAllByRole('button', { name: /^delete$/i })).toHaveLength(0)
    expect(screen.queryByText(CONFIRM)).not.toBeInTheDocument()
    // Save and Check Status are the controls that stay rendered, in both bars; all are disabled
    // (S20/legacy parity).
    const bars = [...saveButtons(), ...checkButtons()]
    expect(bars).toHaveLength(4)
    for (const button of bars) {
      expect(button).toBeDisabled()
    }
  }

  test.each(STATUSES)(
    'admin at %s corrects a location and saves — one bulk PUT, SUC-001 verbatim',
    async (_status, trackStatus) => {
      let captured: LocationSaveAllRequest | null = null
      server.use(
        matrixGet({ trackStatus }),
        http.put(LOCATIONS_URL, async ({ request }) => {
          captured = (await request.json()) as LocationSaveAllRequest
          // The echo keeps the served status — a correction is not a transition — and its `editable` is
          // recomputed by the same matrix.
          return HttpResponse.json(
            matrixDoc(request, {
              trackStatus,
              locations: [{ ...northRidge, actualCost: 31000, totalCost: 41000, revisionCount: 5 }],
              message: SAVED,
            }),
          )
        }),
      )
      renderAsAdmin(<Schedule11 />)
      const user = userEvent.setup()

      // Live write surface for an administrator at S or V — the capability 16.1 granted. The server
      // said so because the request carried ILCR_ADMIN; the same handler answers the submitter arm
      // below read-only.
      const row = await findRow('North Ridge')
      // This arm declared administrator AND the server was asked as one. Drop the `renderAsAdmin` and
      // the declaration half fails, even though the fallback would still send an admin header.
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      expect(actionsHeader()).toBeInTheDocument()
      const actualCost = within(row).getByLabelText('Actual Cost ($) for North Ridge')
      expect(actualCost).toBeEnabled()
      fireEvent.change(actualCost, { target: { value: '31000' } })
      await user.click(saveButtons()[0])

      expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
      expect(captured).not.toBeNull()
      const [item] = captured!.locations
      expect(item.location.actualCost).toBe(31000)
      expect(item.location.revisionCount).toBe(4)

      // "Saving must not move the status" is not assertable from the DOM: the document's track status
      // is rendered NOWHERE on any schedule page (the tombstone carries the working context's mill
      // status, not this document's). Three proxies stand in for it.
      //
      // (a) STRUCTURAL — the request body's exact key sets, at every level. It carries rows, flagged
      // ids, entered fields and the optimistic-lock token only, so the client cannot express a status
      // change even if it wanted to.
      expect(Object.keys(captured!).sort()).toEqual(['deletedIds', 'locations'])
      expect(Object.keys(item).sort()).toEqual(['basicSilvicultureReportId', 'location'])
      expect(Object.keys(item.location).sort()).toEqual([
        'actualCost',
        'biogeoclimaticCatalogueId',
        'comments',
        'enhancedIndicator',
        'location',
        'netArea',
        'plannedCost',
        'revisionCount',
      ])
      // (b) MSW is strict (`onUnhandledRequest: 'error'`), so any call to a transition endpoint would
      // have failed this test rather than passing silently.
      // (c) BEHAVIOURAL — the echo at the same status leaves the page editable, which also proves the page does
      // not re-derive editability from the status it was just handed (AD-9).
      const savedRow = await findRow('North Ridge')
      expect(within(savedRow).getByRole('button', { name: /^delete$/i })).toBeEnabled()
      expect(within(savedRow).getByLabelText('Actual Cost ($) for North Ridge')).toHaveValue(
        '31000',
      )
      expect(actionsHeader()).toBeInTheDocument()
    },
  )

  test.each(STATUSES)(
    'admin at %s can add a location — the POST path is live too',
    async (_status, trackStatus) => {
      let captured: SilvicultureLocationRequest | null = null
      server.use(
        matrixGet({ trackStatus, locations: [], totals: {} }),
        http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
        http.post(LOCATIONS_URL, async ({ request }) => {
          captured = (await request.json()) as SilvicultureLocationRequest
          return HttpResponse.json(matrixDoc(request, { trackStatus, message: SAVED }))
        }),
      )
      renderAsAdmin(<Schedule11 />)
      const user = userEvent.setup()

      // The Add panel is `editable`-gated as a whole, so its very presence at S or V is part of the
      // correction capability the matrix granted this actor.
      fireEvent.change(await screen.findByLabelText('Location'), {
        target: { value: 'South Bench' },
      })
      await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
      await user.click(await screen.findByRole('option', { name: 'No' }))
      await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
      fireEvent.change(screen.getByLabelText('NAR(ha)'), { target: { value: '12.5' } })
      await user.click(screen.getByRole('button', { name: /^add$/i }))

      expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      expect(captured).not.toBeNull()
      expect(captured!.location).toBe('South Bench')
      // The add body carries no `revisionCount` (create has no token to echo) and, again, no status.
      expect(Object.keys(captured!).sort()).toEqual([
        'actualCost',
        'biogeoclimaticCatalogueId',
        'comments',
        'enhancedIndicator',
        'location',
        'netArea',
        'plannedCost',
      ])
    },
  )

  test.each(STATUSES)(
    'submitter at %s is read-only — the action column is HIDDEN, not disabled',
    async (_status, trackStatus) => {
      // The SAME handler and the SAME document as the admin arm above. Only the acting
      // identity differs, and the matrix answers read-only for it. That is the whole point of
      // computing the flag: swap `renderAsSubmitter` for `renderAsAdmin` here and this arm fails.
      server.use(matrixGet({ trackStatus }))
      renderAsSubmitter(<Schedule11 />)

      const row = await findRow('North Ridge')
      expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
      expectReadOnly(row)
    },
  )

  test('admin at DRAFT is read-only — the capability 16.1 deliberately removed', async () => {
    // The bidirectional half of the matrix: an administrator may correct a Submitted or Verified
    // track and may NOT edit a Draft one, which is the licensee's to complete. Without this arm a
    // gate widened to "admin can always edit" would pass every other test in the file.
    server.use(matrixGet({ trackStatus: 'D' }))
    renderAsAdmin(<Schedule11 />)

    const row = await findRow('North Ridge')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expectReadOnly(row)
  })

  test('submitter at DRAFT still edits and saves — the discriminator the read-only arms need', async () => {
    // Without this arm every editable path in this block is an ADMIN path, so a `matrixGet` that
    // answered read-only to EVERYONE — a typo in the submitter key, a resolver that lost the union —
    // would satisfy both read-only arms above and be invisible. Eleven sibling suites carry the same
    // discriminator for the same reason. It is also the licensee half of the hand-off chain: Draft
    // is the one status the mill owns, and the administrator is read-only there. Story 26.2: the
    // licensee saves through the same page-level Save the ministry does.
    const put = vi.fn()
    server.use(
      matrixGet({ trackStatus: 'D' }),
      http.put(LOCATIONS_URL, ({ request }) => {
        put()
        return HttpResponse.json(matrixDoc(request, { trackStatus: 'D', message: SAVED }))
      }),
    )
    renderAsSubmitter(<Schedule11 />)
    const user = userEvent.setup()

    const row = await findRow('North Ridge')
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
    expect(actionsHeader()).toBeInTheDocument()
    expect(within(row).getByLabelText('Location for North Ridge')).toBeEnabled()
    expect(within(row).getByRole('button', { name: /^delete$/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /^add$/i })).toBeInTheDocument()
    for (const button of checkButtons()) {
      expect(button).toBeEnabled()
    }

    changeRowField('Comments', 'North Ridge', 'licensee note')
    await user.click(saveButtons()[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledTimes(1)
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
  })

  test.each(STATUSES)(
    'admin at %s deletes behind the verbatim confirm — the flag goes out on Save',
    async (_status, trackStatus) => {
      let captured: LocationSaveAllRequest | null = null
      const put = vi.fn()
      server.use(
        matrixGet({ trackStatus }),
        http.put(LOCATIONS_URL, async ({ request }) => {
          put()
          captured = (await request.json()) as LocationSaveAllRequest
          return HttpResponse.json(
            matrixDoc(request, { trackStatus, locations: [], totals: {}, message: SAVED }),
          )
        }),
      )
      renderAsAdmin(<Schedule11 />)
      const user = userEvent.setup()

      const row = await findRow('North Ridge')
      await user.click(within(row).getByRole('button', { name: /^delete$/i }))
      const dialog = await screen.findByRole('dialog')
      // Verbatim, per the user ruling that the duplicated confirm modals are not to be touched.
      expect(within(dialog).getByText(CONFIRM)).toBeInTheDocument()
      await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

      await waitFor(() => expect(queryRow('North Ridge')).toBeNull())
      // Confirming only flagged it: data the licensee filed is not destroyed until Save.
      expect(put).not.toHaveBeenCalled()
      await user.click(saveButtons()[0])

      expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      expect(put).toHaveBeenCalledTimes(1)
      expect(captured).toEqual({ locations: [], deletedIds: [9001] })
    },
  )

  test.each(STATUSES)(
    'admin at %s cancels the delete — nothing flagged, nothing sent, document untouched',
    async (_status, trackStatus) => {
      // Past Draft an accidental confirm destroys data the licensee already filed.
      const put = vi.fn()
      server.use(
        matrixGet({ trackStatus }),
        http.put(LOCATIONS_URL, ({ request }) => {
          put()
          return HttpResponse.json(matrixDoc(request, { trackStatus, locations: [], totals: {} }))
        }),
      )
      renderAsAdmin(<Schedule11 />)
      const user = userEvent.setup()

      const row = await findRow('North Ridge')
      await user.click(within(row).getByRole('button', { name: /^delete$/i }))
      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: /^cancel$/i }))

      expect(put).not.toHaveBeenCalled()
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      expect(screen.queryByText(/successfully/i)).not.toBeInTheDocument()
      // Row and its values survive untouched, and nothing is pending.
      const kept = await findRow('North Ridge')
      expect(within(kept).getByLabelText('Actual Cost ($) for North Ridge')).toHaveValue('25000')
      expect(actionsHeader()).toBeInTheDocument()
      for (const button of saveButtons()) {
        expect(button).toBeDisabled()
      }
    },
  )

  test.each(STATUSES)(
    'Check Status is available at %s and mutates nothing',
    async (_status, trackStatus) => {
      let gets = 0
      let posts = 0
      server.use(
        http.get(URL, ({ request }) => {
          gets += 1
          return HttpResponse.json(matrixDoc(request, { trackStatus }))
        }),
        http.post(CHECK_URL, () => {
          posts += 1
          return HttpResponse.json({
            requirementsMet: true,
            errors: [],
            // Schedule 11's met key is `scheduleRequirementsMetMsg` (Schedule11Service.java:68),
            // read from this service rather than assumed: the met-message key is NOT uniform. It
            // varies by LEVEL, not by schedule — Schedule 4 emits BOTH, `locationRequirementsMetMsg`
            // for a per-location result and `scheduleRequirementsMetMsg` for the schedule-level one.
            // Schedule 11's check is schedule-level, so it is the schedule-level key.
            requirementsMetMessage: {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
            message: { key: 'checkStatusMessage', text: 'Status has been checked' },
          })
        }),
      )
      renderAsAdmin(<Schedule11 />)
      const user = userEvent.setup()

      await findRow('North Ridge')
      await waitFor(() => expect(gets).toBe(1))
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      const [button] = checkButtons()
      // Enabled because the matrix made this document editable for this actor — the page reads only
      // the flag, never the role or the status itself (AD-9).
      expect(button).toBeEnabled()
      await user.click(button)

      expect(await screen.findByText('Status has been checked')).toBeInTheDocument()
      expect(
        screen.getByText('All requirements for this schedule have been met'),
      ).toBeInTheDocument()
      // Read-only: exactly one POST, and NO re-GET. Strict MSW covers the rest: an unhandled PUT/POST
      // to /locations would error the test.
      expect(posts).toBe(1)
      expect(gets).toBe(1)
      // Document unchanged in the DOM, and still editable.
      const row = await findRow('North Ridge')
      expect(within(row).getByLabelText('Actual Cost ($) for North Ridge')).toHaveValue('25000')
      expect(actionsHeader()).toBeInTheDocument()
    },
  )

  // ---- Story 26.2 AC 7: the indicators reach every viewer at Submitted and at Verified ---------
  //
  // Legacy's indicators were icons beside always-present, merely disabled inputs, so a read-only
  // viewer saw them too (schedule11.xhtml:214-325; 26.2 D2) — and `isSubmit` is true at S AND V
  // (UserSessionMB.java:541-554). Both roles are served the SAME document by the SAME handler.
  test.each(
    STATUSES.flatMap(([status, trackStatus]) =>
      (
        [
          ['admin (correcting, live row)', ILCR_ROLES.admin, 'ILCR_ADMIN'],
          ['submitter (read-only row)', ILCR_ROLES.submitter, 'ILCR_SUBMITTER'],
        ] as const
      ).map(([label, role, header]) => [status, label, role, header, trackStatus] as const),
    ),
  )(
    'the original-value indicators render at %s for the %s',
    async (_status, _label, role, header, trackStatus) => {
      server.use(matrixGet({ trackStatus, locations: [corrected] }))
      if (role === ILCR_ROLES.admin) {
        renderAsAdmin(<Schedule11 />)
      } else {
        renderAsSubmitter(<Schedule11 />)
      }

      const row = await findRow('North Ridge Revised')
      expectActingAs(role, header)
      for (const field of [
        'location',
        'biogeoclimaticCatalogueId',
        'netArea',
        'actualCost',
        'plannedCost',
      ]) {
        expect(within(row).getByTestId(`original-value-${field}`)).toBeInTheDocument()
      }
      expect(within(row).getByTestId('original-value-location')).toHaveAccessibleDescription(
        'Original Submission Value: North Ridge',
      )
    },
  )

  test.each(STATUSES)(
    'at %s, the indicators survive a correction save and a second one — the baseline is the submission',
    async (_status, trackStatus) => {
      // Each save's echo re-serves the originals from the submitted snapshot, not from the last save
      // (26.2 AC 7): the second correction must still be compared with what the licensee filed.
      let saves = 0
      server.use(
        matrixGet({ trackStatus, locations: [corrected] }),
        http.put(LOCATIONS_URL, async ({ request }) => {
          saves += 1
          const body = (await request.json()) as LocationSaveAllRequest
          return HttpResponse.json(
            matrixDoc(request, {
              trackStatus,
              locations: [
                {
                  ...corrected,
                  actualCost: body.locations[0].location.actualCost,
                  revisionCount: corrected.revisionCount + saves,
                },
              ],
              message: SAVED,
            }),
          )
        }),
      )
      renderAsAdmin(<Schedule11 />)
      const user = userEvent.setup()

      await findRow('North Ridge Revised')
      for (const value of ['26000', '27000']) {
        changeRowField('Actual Cost ($)', 'North Ridge Revised', value)
        await user.click(saveButtons()[0])
        await waitFor(() =>
          expect(screen.getByLabelText('Actual Cost ($) for North Ridge Revised')).toHaveValue(
            value,
          ),
        )
        await waitFor(() => {
          for (const button of saveButtons()) {
            expect(button).toBeDisabled()
          }
        })
        const row = await findRow('North Ridge Revised')
        expect(within(row).getByTestId('original-value-actualCost')).toHaveAccessibleDescription(
          'Original Submission Value: 24,000',
        )
        expect(within(row).getByTestId('original-value-location')).toBeInTheDocument()
      }
      expect(saves).toBe(2)
    },
  )

  test('admin at Verified: a confirmed delete sends nothing until Save — S07, spied at the transport', async () => {
    // MSW's strict mode would already fail an unhandled DELETE; this watches the axios instance the
    // page actually talks through, so "nothing was sent" is asserted rather than inferred. Positive
    // control: the same spy sees the one PUT that Save sends, so a spy on the wrong instance fails.
    const transport = apiService.getAxiosInstance()
    const putSpy = vi.spyOn(transport, 'put')
    const deleteSpy = vi.spyOn(transport, 'delete')
    const postSpy = vi.spyOn(transport, 'post')
    server.use(
      matrixGet({ trackStatus: 'V' }),
      http.put(LOCATIONS_URL, ({ request }) =>
        HttpResponse.json(
          matrixDoc(request, { trackStatus: 'V', locations: [], totals: {}, message: SAVED }),
        ),
      ),
    )
    renderAsAdmin(<Schedule11 />)
    const user = userEvent.setup()

    const row = await findRow('North Ridge')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    await user.click(within(row).getByRole('button', { name: /^delete$/i }))
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: /^delete$/i }),
    )
    await waitFor(() => expect(queryRow('North Ridge')).toBeNull())

    expect(putSpy).not.toHaveBeenCalled()
    expect(deleteSpy).not.toHaveBeenCalled()
    expect(postSpy).not.toHaveBeenCalled()
    // A confirmed delete that is never saved is simply lost on leaving — legacy parity (26.2 fences):
    // there is no navigation guard to find here, and none should be added.

    await user.click(saveButtons()[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(putSpy).toHaveBeenCalledTimes(1)
    expect(putSpy.mock.calls[0][1]).toEqual({ locations: [], deletedIds: [9001] })
    expect(deleteSpy).not.toHaveBeenCalled()
    putSpy.mockRestore()
    deleteSpy.mockRestore()
    postSpy.mockRestore()
  })

  test('admin at Verified: Add persists at once and keeps a pending edit and a flag (S04/S24)', async () => {
    const added: SilvicultureLocation = {
      ...midSlope,
      locationId: 9004,
      location: 'Late Bench',
      revisionCount: 0,
    }
    const posts = vi.fn()
    const bodies: LocationSaveAllRequest[] = []
    server.use(
      matrixGet({ trackStatus: 'V', locations: [northRidge, midSlope] }),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, ({ request }) => {
        posts()
        return HttpResponse.json(
          matrixDoc(request, {
            trackStatus: 'V',
            locations: [northRidge, midSlope, added],
            message: SAVED,
          }),
        )
      }),
      http.put(LOCATIONS_URL, async ({ request }) => {
        bodies.push((await request.json()) as LocationSaveAllRequest)
        return HttpResponse.json(matrixDoc(request, { trackStatus: 'V', message: SAVED }))
      }),
    )
    renderAsAdmin(<Schedule11 />)
    const user = userEvent.setup()

    await findRow('Mid Slope')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    changeRowField('NAR(ha)', 'North Ridge', '130')
    await flagDelete(user, 'Mid Slope')

    fireEvent.change(screen.getByLabelText('Location'), { target: { value: 'Late Bench' } })
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    fireEvent.change(screen.getByLabelText('NAR(ha)'), { target: { value: '4' } })
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    // S04: the Add went out on its own, at once, before any Save.
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(posts).toHaveBeenCalledTimes(1)
    expect(bodies).toHaveLength(0)
    // S24: the pending edit and the flag survived the refreshed document.
    await findRow('Late Bench')
    expect(screen.getByLabelText('NAR(ha) for North Ridge')).toHaveValue('130')
    expect(queryRow('Mid Slope')).toBeNull()

    await user.click(saveButtons()[0])
    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0].deletedIds).toEqual([9002])
    expect(bodies[0].locations.map((item) => item.basicSilvicultureReportId)).toEqual([9001])
  })

  test.each([
    ['Submitted silviculture track while 1–10 is Draft', 'S', 'D', true],
    ['Draft silviculture track while 1–10 is Submitted', 'D', 'S', false],
  ])(
    'editability follows the SILVICULTURE track, not the 1–10 track: %s',
    async (_label, silvicultureStatus, otherTrackStatus, expectEditable) => {
      // TRACK AWARENESS, and no more than that — the claim is deliberately narrow, because this is
      // the client and the client cannot see the other track.
      //
      // WHAT THIS SHOWS: the page reads the ONE status field it is given, and that field is the
      // silviculture track's — `trackStatus` on this response is `MILL_SILVICULTUR_STATUS_CODE`
      // (`Schedule11Response.java:7-11`), never `ILCR_MILL_REPORT_STATUS_CODE`. Editability follows
      // it in both directions.
      //
      // WHAT IT DOES NOT SHOW: `millReportStatus` below is NOT a field of `Schedule11Response` and
      // this suite's own handler cannot read it, so its presence is inert by construction — a
      // tautology of the double, not evidence. It is served only to state the intent in code: a
      // reader who later adds a real 1–10 field must not wire this page to it.
      //
      // WHERE THE REAL CROSS-TRACK PROOF LIVES: the backend's opposing-track fixtures. `R__50` /
      // `R__51` give each mill one track's status under test and `'D'` on the other — mill 735 is
      // 1–10 `'D'` with silviculture `'S'`, mill 736 is 1–10 `'D'` with silviculture `'V'` — so a
      // gate reading the wrong column fails there. Story 16.4's e2e covers it end to end.
      server.use(matrixGet({ trackStatus: silvicultureStatus, millReportStatus: otherTrackStatus }))
      renderAsAdmin(<Schedule11 />)

      const row = await findRow('North Ridge')
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      if (expectEditable) {
        expect(actionsHeader()).toBeInTheDocument()
        expect(within(row).getByRole('button', { name: /^delete$/i })).toBeEnabled()
        for (const button of checkButtons()) {
          expect(button).toBeEnabled()
        }
      } else {
        expectReadOnly(row)
      }
    },
  )

  // ---- C: the silviculture statuses nothing else in the frontend ever serves -----------------
  //
  // `ILCR_ADMIN: ['S', 'V']` is pinned in twelve suites and `'V'` was served ZERO times repo-wide,
  // so narrowing the backend matrix to `{'S'}` alone would have left every frontend suite green.
  // These three cases close that hole on this page's own track, and pin the fail-closed edges the
  // backend documents (ScheduleEditability.java:42-46): a dead `'O'` passes through read-only, and a
  // null status row — legacy renders "Not Initiated" — is read-only rather than accidentally open.
  test.each<[string, string | null, boolean]>([
    ['Verified (V) — an admin may still correct a signed-off report', 'V', true],
    ['Opened (O) — the dead legacy status passes through read-only (A-8)', 'O', false],
    ['no status row (null) — legacy renders "Not Initiated"; fail closed', null, false],
  ])(
    'admin editability across the full silviculture status column: %s',
    async (_label, trackStatus, expectEditable) => {
      server.use(matrixGet({ trackStatus }))
      renderAsAdmin(<Schedule11 />)

      const row = await findRow('North Ridge')
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      if (expectEditable) {
        expect(actionsHeader()).toBeInTheDocument()
        expect(within(row).getByRole('button', { name: /^delete$/i })).toBeEnabled()
        for (const button of checkButtons()) {
          expect(button).toBeEnabled()
        }
      } else {
        expectReadOnly(row)
      }
    },
  )
})
