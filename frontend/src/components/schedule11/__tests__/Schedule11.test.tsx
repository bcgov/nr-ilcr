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

// jsdom lacks scrollIntoView; Carbon's Dropdown/ComboBox call it on the highlighted option.
window.HTMLElement.prototype.scrollIntoView = vi.fn()

import Schedule11 from '@/components/schedule11'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
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

  test('Comments is a TextArea with the 3500 counter (AC11 / BR-10)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ locations: [], totals: {} }))))
    render(<Schedule11 />)
    const user = userEvent.setup()

    const comments = await screen.findByLabelText('Comments')
    expect(comments.tagName).toBe('TEXTAREA')
    expect(screen.getByText('0/3500')).toBeInTheDocument()
    await user.type(comments, 'hello')
    expect(screen.getByText('5/3500')).toBeInTheDocument()
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
