import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import {
  declaredRole,
  fireEvent,
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

// jsdom lacks scrollIntoView; Carbon's Dropdown/ComboBox call it on the highlighted option.
window.HTMLElement.prototype.scrollIntoView = vi.fn()

import Schedule11 from '@/components/schedule11'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import type { IlcrRole } from '@/context/auth/mockUsers'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import type SilvicultureLocationRequest from '@/interfaces/Schedule11Request'
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

// Preserves the literal whitespace the backend sends (the FLD-004 double space, the ERR-001 trailing
// space) so the verbatim-rendering assertions (AD-8) are not defeated by the default whitespace
// collapse/trim.
const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

// Type a term into the BEC ComboBox (fires the debounced search) and pick a suggestion by label.
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

async function pickBec(
  user: ReturnType<typeof userEvent.setup>,
  comboName: RegExp,
  term: string,
  optionLabel: string,
) {
  await user.type(screen.getByRole('combobox', { name: comboName }), term)
  await user.click(await screen.findByRole('option', { name: optionLabel }))
}

describe('Schedule 11 page (Story 25.3)', () => {
  test('renders locations + per-row and footer server totals with the legacy masks (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule11 />)

    const row = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
    expect(within(row).getByText('ICHdw1')).toBeInTheDocument()
    expect(within(row).getByText('No')).toBeInTheDocument()
    expect(within(row).getByText('120.5')).toBeInTheDocument()
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
    render(<Schedule11 />)

    // Row-scoped on purpose: the delete-confirm Modal stays mounted while the page is editable,
    // so the document also holds its closed footer's "Delete", which is deliberately icon-free.
    const iconRow = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
    for (const name of [/^edit$/i, /^delete$/i]) {
      expect(within(iconRow).getByRole('button', { name }).querySelector('svg')).not.toBeNull()
    }
    for (const name of [/^add$/i, /check status/i]) {
      for (const button of screen.getAllByRole('button', { name })) {
        expect(button.querySelector('svg')).not.toBeNull()
      }
    }
  })

  test('zero locations render an empty table + blank (not 0) footer totals, no error (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))))
    render(<Schedule11 />)

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
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    render(<Schedule11 />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Location'), 'North Ridge')
    await user.click(screen.getByRole('combobox', { name: /^Enhanced$/i }))
    await user.click(await screen.findByRole('option', { name: 'No' }))
    await pickBec(user, /^Biogeo\/Subzone\/Variant$/i, 'ICH', 'ICHdw1')
    await user.type(screen.getByLabelText('NAR(ha)'), '120.5')
    await user.type(screen.getByLabelText('Actual Cost ($)'), '25000')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(screen.getByText('North Ridge')).toBeInTheDocument()
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
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
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    render(<Schedule11 />)
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

  test('inline edit PUTs the row carrying its revisionCount and shows success (AC3)', async () => {
    let captured: SilvicultureLocationRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${LOCATIONS_URL}/9001`, async ({ request }) => {
        captured = (await request.json()) as SilvicultureLocationRequest
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    render(<Schedule11 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^edit$/i }))
    const location = screen.getByLabelText('Edit Location')
    await user.clear(location)
    await user.type(location, 'North Ridge Revised')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).not.toBeNull()
    expect(captured!.location).toBe('North Ridge Revised')
    expect(captured!.revisionCount).toBe(4)
    // The full edit body is pinned — a mis-seeded startEdit or mis-mapped buildBody must fail here.
    expect(captured!.biogeoclimaticCatalogueId).toBe(321)
    expect(captured!.enhancedIndicator).toBe(false)
    expect(captured!.netArea).toBe(120.5)
    expect(captured!.actualCost).toBe(25000)
    expect(captured!.plannedCost).toBe(10000)
    expect(captured!.comments).toBeNull()
  })

  test('inline edit prints no per-cell field labels, but every control keeps its name', async () => {
    // The column headers already name these fields; a visible per-cell label printed "Edit
    // Enhanced" / "Edit Biogeo/Subzone/Variant" as stray text above the controls in every row.
    // Hiding them must not cost the controls their accessible names.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule11 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^edit$/i }))
    const editRow = (screen.getByLabelText('Edit Location').closest('tr') ?? null) as HTMLElement

    // Dropdown keeps the label element but visually hides it (Carbon's hideLabel).
    expect(within(editRow).getByText('Edit Enhanced')).toHaveClass('cds--visually-hidden')
    // ComboBox has no hideLabel, so the visible label is dropped entirely.
    expect(within(editRow).queryByText('Edit Biogeo/Subzone/Variant')).not.toBeInTheDocument()

    // Both remain reachable by name — this is what would break if the labels were display:none'd
    // or the aria-label fallback were dropped.
    expect(within(editRow).getByRole('combobox', { name: 'Edit Enhanced' })).toBeInTheDocument()
    expect(
      within(editRow).getByRole('combobox', { name: 'Edit Biogeo/Subzone/Variant' }),
    ).toBeInTheDocument()

    // The Add panel below still shows its labels — hideLabel is row-scoped, not global.
    expect(screen.getByText('Enhanced')).not.toHaveClass('cds--visually-hidden')
  })

  test('an invalid inline edit blocks the PUT with the advisory error (AC5)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${LOCATIONS_URL}/9001`, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule11 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^edit$/i }))
    const nar = screen.getByLabelText('Edit NAR(ha)')
    await user.clear(nar)
    await user.type(nar, '1000000')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      screen.getByText('Entered NAR (ha) must be between 0 and 999,999.9.'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('edit Cancel restores the row with no request (AC3)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${LOCATIONS_URL}/9001`, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule11 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^edit$/i }))
    const location = screen.getByLabelText('Edit Location')
    await user.clear(location)
    await user.type(location, 'Changed But Discarded')
    // Scoped to the edit row — the (closed) delete modal mounts its own Cancel button.
    const editRow = location.closest('tr') as HTMLElement
    await user.click(within(editRow).getByRole('button', { name: /^cancel$/i }))

    expect(put).not.toHaveBeenCalled()
    const row = screen.getByText('North Ridge').closest('tr') as HTMLElement
    expect(within(row).queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByText('Changed But Discarded')).not.toBeInTheDocument()
  })

  test('delete confirm issues DELETE and shows the verbatim delete success (AC4)', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${LOCATIONS_URL}/9001`, () => {
        deleted = true
        return HttpResponse.json(
          doc({
            locations: [],
            totals: {},
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        )
      }),
    )
    render(<Schedule11 />)
    const user = userEvent.setup()

    await screen.findByText('North Ridge')
    // The row Delete opens the confirm; the always-mounted modal carries its own Delete primary.
    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    await waitFor(() => expect(deleted).toBe(true))
    await waitFor(() => expect(screen.queryByText('North Ridge')).not.toBeInTheDocument())
  })

  test('delete cancel closes the dialog, fires no request and shows no message (AC4)', async () => {
    const del = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${LOCATIONS_URL}/9001`, () => {
        del()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule11 />)
    const user = userEvent.setup()

    await screen.findByText('North Ridge')
    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^cancel$/i }))

    expect(del).not.toHaveBeenCalled()
    expect(screen.queryByText(/successfully/i)).not.toBeInTheDocument()
    expect(screen.getByText('North Ridge')).toBeInTheDocument()
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
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
    render(<Schedule11 />)
    const user = userEvent.setup()

    await screen.findByText('North Ridge')
    await user.click(screen.getByRole('button', { name: /check status/i }))

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
    render(<Schedule11 />)
    const user = userEvent.setup()

    await screen.findByText('North Ridge')
    await user.click(screen.getByRole('button', { name: /check status/i }))

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
    render(<Schedule11 />)
    const user = userEvent.setup()

    await screen.findByText('North Ridge')
    const button = screen.getByRole('button', { name: /check status/i })
    await user.click(button)
    // The in-flight lock disables the button (and the rest of the write surface) until it resolves.
    await waitFor(() => expect(button).toBeDisabled())
    expect(await screen.findByText('Status has been checked')).toBeInTheDocument()
    await waitFor(() => expect(button).toBeEnabled())
    expect(check).toHaveBeenCalledTimes(1)
  })

  test.each([
    [400, 'Please Select Mill and Reporting Year in the Home Page. '],
    [
      409,
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
    ],
    [404, 'Schedule not found.'],
  ])(
    'guard state %i renders the verbatim detail and suppresses content (AC8)',
    async (status, detail) => {
      server.use(http.get(URL, () => problemBody(status, detail)))
      render(<Schedule11 />)

      expect(await screen.findByText(detail, { normalizer: verbatim })).toBeInTheDocument()
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
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule11 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
  })

  test('read-only (editable:false) disables the write surface AND Check Status; values as text (AC9)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule11 />)

    const row = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
    // Values render as text, not inputs.
    expect(within(row).getByText('ICHdw1')).toBeInTheDocument()
    expect(within(row).queryByRole('textbox')).not.toBeInTheDocument()
    // Whole write surface gone / disabled.
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^edit$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument()
    // Check Status also disabled in read-only (S20/legacy parity).
    expect(screen.getByRole('button', { name: /check status/i })).toBeDisabled()
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
    render(<Schedule11 />)
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

    render(<StaleRaceHarness />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('South Valley')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('North Ridge')).not.toBeInTheDocument())
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
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    // Explicit initial context: the sibling stale-GET test persists 999/2020 to localStorage, and a
    // no-op "change" to the same context would defeat the race this test exists to exercise.
    render(
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

    expect(await screen.findByText('South Valley')).toBeInTheDocument()
    // Let the stale POST resolve, then confirm nothing from it landed.
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(screen.queryByText('North Ridge')).not.toBeInTheDocument()
    expect(screen.getByText('South Valley')).toBeInTheDocument()
  })
})

describe('Schedule 11 column sorting (legacy p:column sortBy parity)', () => {
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

  // Document order as the API returned it, which the third click must restore.
  const threeRows = [northRidge, midSlope, alderFlat]
  const DOC_ORDER = ['North Ridge', 'Mid Slope', 'Alder Flat', 'Totals']

  const renderSorted = async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ locations: threeRows }))))
    render(<Schedule11 />)
    await screen.findByText('North Ridge')
    return userEvent.setup()
  }

  // First cell of every body row, so assertions read as the visible order. The Totals row is part
  // of the same tbody, which is exactly why it appears in these expectations — it must stay last.
  const rowLabels = () => {
    const [, body] = screen.getAllByRole('rowgroup')
    return within(body)
      .getAllByRole('row')
      .map((row) => (row as HTMLTableRowElement).cells[0].textContent)
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

  test('sorting survives a save so the edited row does not jump back (AC3 interaction)', async () => {
    // The sort is presentation-only and deliberately outlives a mutation (index.tsx: a re-sort
    // after every add/edit would yank the row the user is working on back to document order).
    // The sort stays ACTIVE across the PUT here — releasing it first would test nothing.
    const user = await renderSorted()
    server.use(
      http.put(`${LOCATIONS_URL}/9003`, () =>
        HttpResponse.json(
          doc({
            locations: [northRidge, midSlope, { ...alderFlat, location: 'Zulu Flat' }],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        ),
      ),
    )

    await user.click(within(header('Location')).getByRole('button'))
    expect(rowLabels()).toEqual(['Alder Flat', 'Mid Slope', 'North Ridge', 'Totals'])

    // Rename the FIRST row to a name that sorts LAST, so the two failure modes separate: if the
    // save dropped the sort the refreshed rows come back in document order (North Ridge, Mid
    // Slope, Zulu Flat); if it kept a stale pre-save ordering Zulu Flat stays first.
    const alderRow = screen.getByText('Alder Flat').closest('tr') as HTMLElement
    await user.click(within(alderRow).getByRole('button', { name: /^edit$/i }))
    const location = screen.getByLabelText('Edit Location')
    await user.clear(location)
    await user.type(location, 'Zulu Flat')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(rowLabels()).toEqual(['Mid Slope', 'North Ridge', 'Zulu Flat', 'Totals'])
    expect(header('Location')).toHaveAttribute('aria-sort', 'ascending')
  })
})

/**
 * Schedule 11's original-value indicators (Story 16.2), and the two fields that deliberately carry
 * none. Both omissions were queried in review of PR #452, so they are pinned here rather than left
 * to a code comment — a later reader "completing" the set would introduce a false indicator on
 * every row, which is the opposite of the audit evidence this feature exists to give.
 *
 * `schedule11.xhtml` draws six indicator buttons. Five are reproduced. The sixth, on the Enhanced
 * control, cannot fire in legacy either: `BASIC_SILVICULTURE_REPORT_S_VW` does not select
 * `ENHANCED_IND`, so no submitted value for it exists (story finding F4 / deviation D5). Comments is
 * not one of the six at all — legacy persists `commentsOriginalVal` but declares no accessor and
 * draws no button (AC7).
 */
describe('Schedule 11 original-value indicators', () => {
  const corrected: SilvicultureLocation = {
    ...northRidge,
    location: 'North Ridge Revised',
    enhancedIndicator: true,
    comments: 'the ministry corrected this',
    originalValues: {
      location: { value: 'North Ridge', tooltip: 'Original Submission Value: North Ridge' },
      netArea: { value: '118', tooltip: 'Original Submission Value: 118.0' },
      actualCost: { value: '24000', tooltip: 'Original Submission Value: 24,000' },
    },
  }

  const openEdit = async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', locations: [corrected] }))),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
    )
    render(<Schedule11 />)
    await userEvent.click(await screen.findByRole('button', { name: /^edit$/i }))
    return screen.getByLabelText('Edit Location').closest('tr') as HTMLElement
  }

  test('renders them on the corrected fields, naming the field and describing the original', async () => {
    const row = await openEdit()

    const location = within(row).getByTestId('original-value-location')
    expect(location).toHaveAccessibleName('Location differs from the originally submitted value')
    expect(location).toHaveAccessibleDescription('Original Submission Value: North Ridge')
    expect(within(row).getByTestId('original-value-netArea')).toBeInTheDocument()
    expect(within(row).getByTestId('original-value-actualCost')).toBeInTheDocument()
  })

  test('Enhanced carries NO indicator — legacy draws one but it can never fire (F4/D5)', async () => {
    const row = await openEdit()

    // The control is there and has been corrected (false → true); the indicator is still absent,
    // because no submitted value for ENHANCED_IND can exist. Rendering one would take the
    // "added since submission" branch and flag every row of every submitted report.
    expect(within(row).getByRole('combobox', { name: 'Edit Enhanced' })).toBeInTheDocument()
    expect(within(row).queryByTestId('original-value-enhancedIndicator')).not.toBeInTheDocument()
  })

  test('Comments carries NO indicator — legacy declares none for this schedule (AC7)', async () => {
    const row = await openEdit()

    expect(within(row).getByLabelText('Edit Comments')).toHaveValue('the ministry corrected this')
    expect(within(row).queryByTestId('original-value-comments')).not.toBeInTheDocument()
  })

  test('no indicator anywhere at Draft', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ trackStatus: 'D', locations: [{ ...corrected, originalValues: null }] }),
        ),
      ),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
    )
    render(<Schedule11 />)
    await userEvent.click(await screen.findByRole('button', { name: /^edit$/i }))

    expect(screen.queryByTestId('original-value-location')).not.toBeInTheDocument()
    expect(screen.queryByTestId('original-value-netArea')).not.toBeInTheDocument()
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
// will assume otherwise. There is no shared `ScheduleActions` bar to disable: "Save" and "Delete"
// are per-row controls inside an inline editor, and when the document is not editable the page
// HIDES the whole action column (`index.tsx:538`, `:1087`, `:1113`), drops its column count from 10
// to 9 (`:872`), drops the Add panel entirely (`:965`) and never mounts the delete-confirm Modal at
// all (`:1121`). These arms assert that real behaviour rather than the disabled-button shape the
// other suites assert.
//
// Every user-facing string is asserted VERBATIM against `backend/src/main/resources/messages.properties`
// (AD-8).
// -------------------------------------------------------------------------------------------------

describe('Schedule 11 ministry correction at Submitted (Story 16.3)', () => {
  // The PINNED 16.1 matrix (`ScheduleEditability`), per track status — here, per SILVICULTURE track
  // status. Submitter edits at Draft only; admin edits at Submitted and Verified and is
  // DELIBERATELY read-only at Draft while the mill still owns the data. Anything else — a dead `O`,
  // a null status, an unknown role — is read-only.
  //
  // Reproduced rather than imported because the real rule lives in Java: this is the wire contract
  // the frontend is entitled to assume, and stating it makes falsification trivial. Each row is
  // load-bearing and was checked: `ILCR_ADMIN: ['D']` fails all nine admin arms; narrowing it to
  // `['S']` fails the Verified case alone (the hole that existed while no suite served `'V'`); and
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

  const SAVED = { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' }
  const DELETED = { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' }
  const CONFIRM = 'This will delete the current record. Do you want to continue?'

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
    expect(screen.queryByRole('button', { name: /^edit$/i })).not.toBeInTheDocument()
    // Zero delete-named buttons anywhere: the row's, AND the confirm Modal's primary — the Modal is
    // `editable`-gated, so there is no route to a DELETE even by reaching past a control.
    expect(screen.queryAllByRole('button', { name: /^delete$/i })).toHaveLength(0)
    expect(screen.queryByText(CONFIRM)).not.toBeInTheDocument()
    // Check Status is the one control that stays rendered; it is disabled (S20/legacy parity).
    expect(screen.getByRole('button', { name: /check status/i })).toBeDisabled()
  }

  test('admin at Submitted corrects a location and saves — PUT issued, SUC-001 verbatim', async () => {
    let captured: SilvicultureLocationRequest | null = null
    server.use(
      matrixGet(),
      http.put(`${LOCATIONS_URL}/9001`, async ({ request }) => {
        captured = (await request.json()) as SilvicultureLocationRequest
        // The echo is still Submitted — no transition endpoint exists (Epics 17–18 own that) and a
        // correction is not one — and its `editable` is recomputed by the same matrix.
        return HttpResponse.json(
          matrixDoc(request, {
            locations: [{ ...northRidge, actualCost: 31000, totalCost: 41000, revisionCount: 5 }],
            message: SAVED,
          }),
        )
      }),
    )
    renderAsAdmin(<Schedule11 />)
    const user = userEvent.setup()

    // Live write surface for an administrator at Submitted — the capability 16.1 granted and
    // nothing asserted until now. The server said so because the request carried ILCR_ADMIN; the
    // same handler answers the submitter arm below read-only.
    await user.click(await screen.findByRole('button', { name: /^edit$/i }))
    const editRow = screen.getByLabelText('Edit Location').closest('tr') as HTMLElement
    // This arm declared administrator AND the server was asked as one. Drop the `renderAsAdmin` and
    // the declaration half fails, even though the fallback would still send an admin header.
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expect(actionsHeader()).toBeInTheDocument()
    const actualCost = within(editRow).getByLabelText('Edit Actual Cost ($)')
    expect(actualCost).toBeEnabled()
    // fireEvent.change, not user.type: this page mounts an editor per row, so per-character typing
    // is O(rows × chars) and has timed out CI before. One change event is the same state write.
    fireEvent.change(actualCost, { target: { value: '31000' } })
    await user.click(within(editRow).getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).not.toBeNull()
    expect(captured!.actualCost).toBe(31000)
    expect(captured!.revisionCount).toBe(4)

    // "Saving must not move the status" is not assertable from the DOM: the document's track status
    // is rendered NOWHERE on any schedule page (the tombstone carries the working context's mill
    // status, not this document's). Three proxies stand in for it.
    //
    // (a) STRUCTURAL — the request body's exact top-level key set. It carries entered fields and the
    // optimistic-lock token only, so the client cannot express a status change even if it wanted to.
    expect(Object.keys(captured!).sort()).toEqual([
      'actualCost',
      'biogeoclimaticCatalogueId',
      'comments',
      'enhancedIndicator',
      'location',
      'netArea',
      'plannedCost',
      'revisionCount',
    ])
    // (b) MSW is strict (`onUnhandledRequest: 'error'`), so any call to a transition endpoint —
    // there is none in the backend — would have failed this test rather than passing silently.
    // (c) BEHAVIOURAL — the Submitted echo leaves the page editable, which also proves the page does
    // not re-derive editability from the status it was just handed (AD-9).
    const savedRow = screen.getByText('North Ridge').closest('tr') as HTMLElement
    expect(within(savedRow).getByRole('button', { name: /^edit$/i })).toBeEnabled()
    expect(actionsHeader()).toBeInTheDocument()
  })

  test('admin at Submitted can add a location — the POST path is live too', async () => {
    let captured: SilvicultureLocationRequest | null = null
    server.use(
      matrixGet({ locations: [], totals: {} }),
      http.get(BEC_URL, () => HttpResponse.json([{ id: 321, label: 'ICHdw1' }])),
      http.post(LOCATIONS_URL, async ({ request }) => {
        captured = (await request.json()) as SilvicultureLocationRequest
        return HttpResponse.json(matrixDoc(request, { message: SAVED }))
      }),
    )
    renderAsAdmin(<Schedule11 />)
    const user = userEvent.setup()

    // The Add panel is `editable`-gated as a whole (index.tsx:965), so its very presence at
    // Submitted is part of the correction capability the matrix granted this actor.
    fireEvent.change(await screen.findByLabelText('Location'), { target: { value: 'South Bench' } })
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
  })

  test('submitter at Submitted is read-only — the action column is HIDDEN, not disabled', async () => {
    // The SAME handler and the SAME Submitted document as the admin arm above. Only the acting
    // identity differs, and the matrix answers read-only for it. That is the whole point of
    // computing the flag: swap `renderAsSubmitter` for `renderAsAdmin` here and this arm fails.
    server.use(matrixGet())
    renderAsSubmitter(<Schedule11 />)

    const row = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
    expectReadOnly(row)
  })

  test('admin at DRAFT is read-only — the capability 16.1 deliberately removed', async () => {
    // The bidirectional half of the matrix: an administrator may correct a Submitted or Verified
    // track and may NOT edit a Draft one, which is the licensee's to complete. Without this arm a
    // gate widened to "admin can always edit" would pass every other test in the file.
    server.use(matrixGet({ trackStatus: 'D' }))
    renderAsAdmin(<Schedule11 />)

    const row = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expectReadOnly(row)
  })

  test('submitter at DRAFT still edits — the discriminator the read-only arms need', async () => {
    // Without this arm every editable path in this block is an ADMIN path, so a `matrixGet` that
    // answered read-only to EVERYONE — a typo in the submitter key, a resolver that lost the union —
    // would satisfy both read-only arms above and be invisible. Eleven sibling suites carry the same
    // discriminator for the same reason. It is also the licensee half of the hand-off chain: Draft
    // is the one status the mill owns, and the administrator is read-only there.
    server.use(matrixGet({ trackStatus: 'D' }))
    renderAsSubmitter(<Schedule11 />)

    const row = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
    expect(actionsHeader()).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: /^edit$/i })).toBeEnabled()
    expect(within(row).getByRole('button', { name: /^delete$/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /^add$/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /check status/i })).toBeEnabled()
  })

  test('admin at Submitted deletes behind the verbatim confirm — DELETE issued on confirm', async () => {
    const del = vi.fn()
    server.use(
      matrixGet(),
      http.delete(`${LOCATIONS_URL}/9001`, ({ request }) => {
        del()
        return HttpResponse.json(
          matrixDoc(request, { locations: [], totals: {}, message: DELETED }),
        )
      }),
    )
    renderAsAdmin(<Schedule11 />)
    const user = userEvent.setup()

    await screen.findByText('North Ridge')
    // The row Delete opens the confirm; the always-mounted modal carries its own Delete primary, so
    // the row's is the first.
    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = await screen.findByRole('dialog')
    // Verbatim, per the user ruling that the duplicated confirm modals are not to be touched:
    // assert the message where it stands (index.tsx:53).
    expect(within(dialog).getByText(CONFIRM)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    await waitFor(() => expect(del).toHaveBeenCalledTimes(1))
    expect(screen.queryByText('North Ridge')).not.toBeInTheDocument()
  })

  test('admin at Submitted cancels the delete — NO DELETE issued, document untouched', async () => {
    // The pre-existing AC4 cancel arm (line ~464) runs at Draft; the ministry-correction shape of it
    // is the one that matters, because Submitted is where an accidental confirm destroys data the
    // licensee already filed.
    const del = vi.fn()
    server.use(
      matrixGet(),
      http.delete(`${LOCATIONS_URL}/9001`, ({ request }) => {
        del()
        return HttpResponse.json(matrixDoc(request, { locations: [], totals: {} }))
      }),
    )
    renderAsAdmin(<Schedule11 />)
    const user = userEvent.setup()

    await screen.findByText('North Ridge')
    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^cancel$/i }))

    expect(del).not.toHaveBeenCalled()
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expect(screen.queryByText(/successfully/i)).not.toBeInTheDocument()
    // Row and its values survive untouched.
    const row = screen.getByText('North Ridge').closest('tr') as HTMLElement
    expect(within(row).getByText('25,000')).toBeInTheDocument()
    expect(actionsHeader()).toBeInTheDocument()
  })

  test('Check Status is available at Submitted and mutates nothing', async () => {
    let gets = 0
    let posts = 0
    server.use(
      http.get(URL, ({ request }) => {
        gets += 1
        return HttpResponse.json(matrixDoc(request))
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

    await screen.findByText('North Ridge')
    await waitFor(() => expect(gets).toBe(1))
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    const button = screen.getByRole('button', { name: /check status/i })
    // Enabled because the matrix made this document editable for this actor — the page reads only
    // the flag, never the role or the status itself (AD-9).
    expect(button).toBeEnabled()
    await user.click(button)

    expect(await screen.findByText('Status has been checked')).toBeInTheDocument()
    expect(screen.getByText('All requirements for this schedule have been met')).toBeInTheDocument()
    // Read-only: exactly one POST, and NO re-GET. A stable GET count is a real "nothing ran" signal
    // here because the confirmed-delete path does re-read, so a mutation would move this number.
    // Strict MSW covers the rest: an unhandled PUT/POST/DELETE to /locations would error the test.
    expect(posts).toBe(1)
    expect(gets).toBe(1)
    // Document unchanged in the DOM, and still editable.
    const row = screen.getByText('North Ridge').closest('tr') as HTMLElement
    expect(within(row).getByText('25,000')).toBeInTheDocument()
    expect(actionsHeader()).toBeInTheDocument()
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

      const row = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      if (expectEditable) {
        expect(actionsHeader()).toBeInTheDocument()
        expect(within(row).getByRole('button', { name: /^edit$/i })).toBeEnabled()
        expect(screen.getByRole('button', { name: /check status/i })).toBeEnabled()
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

      const row = (await screen.findByText('North Ridge')).closest('tr') as HTMLElement
      expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
      if (expectEditable) {
        expect(actionsHeader()).toBeInTheDocument()
        expect(within(row).getByRole('button', { name: /^edit$/i })).toBeEnabled()
        expect(screen.getByRole('button', { name: /check status/i })).toBeEnabled()
      } else {
        expectReadOnly(row)
      }
    },
  )
})
