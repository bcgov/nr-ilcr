import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import Schedule8 from '@/components/schedule8'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import type { Page, Sample } from '@/interfaces/Schedule8Response'

// Schedule 8's samples/rates levels are URL-driven (search: pageId + sampleId), so render it inside a
// REAL memory router — the route search hooks + navigation (and the browser Back button) need router
// context. Tests that need the browser Back button build the router directly to reach router.history.
function makeRouter(initialUrl = '/schedule-8') {
  const rootRoute = createRootRoute()
  const scheduleRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/schedule-8',
    validateSearch: (s: Record<string, unknown>) => ({
      pageId: s.pageId == null || s.pageId === '' ? undefined : Number(s.pageId),
      sampleId: s.sampleId == null || s.sampleId === '' ? undefined : Number(s.sampleId),
    }),
    component: Schedule8,
  })
  return createRouter({
    routeTree: rootRoute.addChildren([scheduleRoute]),
    history: createMemoryHistory({ initialEntries: [initialUrl] }),
  })
}

const renderSchedule8 = (initialUrl = '/schedule-8') =>
  render(<RouterProvider router={makeRouter(initialUrl)} />)

// The page-editor code fields are Carbon Dropdowns (combobox trigger + option list): open the named
// dropdown, then click the option by its (description) label.
const selectOption = async (dropdown: string, option: RegExp | string) => {
  await userEvent.click(await screen.findByRole('combobox', { name: dropdown }))
  await userEvent.click(await screen.findByRole('option', { name: option }))
}

const URL = 'http://localhost:3000/api/v1/schedule8'
const PAGES_URL = `${URL}/pages`
const CHECK_URL = `${URL}/check-status`
const SAMPLES_8001 = `${URL}/pages/8001/samples`
const PAGE_CHECK_8001 = `${URL}/pages/8001/check-status`
const RATES_8101 = `${URL}/samples/8101/rates`

const sample8101: Sample = {
  id: 8101,
  revisionCount: 0,
  contractId: 'C-1',
  cutBlock: 'CB-1',
  groundBasePct: 100,
  grapplePct: 0,
  skylinePct: 0,
  highleadPct: 0,
  helicopterPct: 0,
  otherSkiddingPct: 0,
  percentTotal: 100,
  skylineSlopeDistance: null,
  skylineSupportNumber: null,
  supportAvgDistance: null,
  distance: null,
  cycleTime: null,
  uphillDirection: false,
  waterDumpDestination: false,
  skidTypeCode: null,
  skidTypeDescription: null,
  coniferousVolume: 1000,
  deciduousVolume: 500,
  actualHarvested: 1500,
  originalRate: 10,
  additionsTotal: 5,
  deductionsTotal: 2,
  finalRate: 13,
  additionCount: 1,
  deductionCount: 1,
  additions: [
    {
      id: 8201,
      revisionCount: 0,
      costItemCode: 82,
      itemDescription: 'Bridge build',
      costingRate: 5,
      costTypeCode: 'CT1',
      costTypeDescription: 'Fixed',
    },
  ],
  deductions: [
    {
      id: 8301,
      revisionCount: 0,
      costItemCode: 101,
      itemDescription: 'Road credit',
      costingRate: 2,
      costTypeCode: 'CT2',
      costTypeDescription: 'Variable',
    },
  ],
}

const fullPage: Page = {
  id: 8001,
  revisionCount: 0,
  division: 'North',
  license: 'LIC1',
  contact: 'Jane Roe',
  phone: '250-555-1212',
  cuttingPermit: 'CP1',
  supportCentre: 'SC1',
  supportCentreLabel: 'Support Centre 1',
  region: 'R1',
  regionLabel: 'Region 1',
  becZone: 'BZ1',
  becZoneLabel: 'BEC 1',
  tsaNumber: 'TSA1',
  tsaNumberLabel: 'TSA 1',
  tflNumber: null,
  tflNumberLabel: null,
  supplyBlock: 'A',
  supplyBlockLabel: 'Block A',
  comments: 'seed comment',
  sampleCount: 1,
  samples: [sample8101],
}

const emptyPage: Page = {
  id: 8002,
  revisionCount: 0,
  division: null,
  license: 'LIC2',
  contact: null,
  phone: null,
  cuttingPermit: null,
  supportCentre: 'SC2',
  supportCentreLabel: 'Support Centre 2',
  region: 'R1',
  regionLabel: 'Region 1',
  becZone: 'BZ1',
  becZoneLabel: 'BEC 1',
  tsaNumber: 'TSA1',
  tsaNumberLabel: 'TSA 1',
  tflNumber: null,
  tflNumberLabel: null,
  supplyBlock: 'B',
  supplyBlockLabel: 'Block B',
  comments: null,
  sampleCount: 0,
  samples: [],
}

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  pages: [fullPage, emptyPage],
  ...overrides,
})

const savedMsg = { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' }

describe('Schedule8 page level', () => {
  test('lists pages (composite label); Add New Page enabled (editable)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule8()

    // Each page shows the legacy composite label; the sample-count link now lives in the page editor.
    expect(await screen.findByText(/Page # 1 -TSA: TSA1 -CP: CP1/)).toBeInTheDocument()
    expect(screen.getByText(/Page # 2/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add new page/i })).toBeEnabled()
    expect(screen.getAllByRole('button', { name: /^edit$/i }).length).toBeGreaterThan(0)
  })

  // Story 30.3 / #312 Overall 6. `renderIcon` puts an <svg> inside the button and leaves the
  // accessible name as the label text, so a by-name lookup still finds the button AND proves the
  // decorative icon is there — a later edit that drops an icon fails here.
  test('every primary and row action button carries its decorative icon', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule8()

    // Row-scoped on purpose: the delete-confirm Modal stays mounted while the page is editable,
    // so the document also holds its closed footer's "Delete", which is deliberately icon-free.
    const iconRow = (await screen.findByText(/Page # 1 -TSA: TSA1 -CP: CP1/)).closest(
      'tr',
    ) as HTMLElement
    for (const name of [/^edit$/i, /^copy$/i, /^delete$/i]) {
      expect(within(iconRow).getByRole('button', { name }).querySelector('svg')).not.toBeNull()
    }
    for (const name of [/add new page/i, /check status/i]) {
      for (const button of screen.getAllByRole('button', { name })) {
        expect(button.querySelector('svg')).not.toBeNull()
      }
    }
  })

  test('Add New Page opens the editor with editable inputs', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))

    expect(screen.getByText('New Page')).toBeInTheDocument()
    expect(screen.getByLabelText('License')).toHaveValue('')
    expect(screen.getByRole('combobox', { name: 'Support Centre' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'TSA or TFL' })).toBeInTheDocument()
  })

  test('save a new page PUTs and shows the API success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () => HttpResponse.json(doc({ message: savedMsg }))),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    await userEvent.type(screen.getByLabelText('License'), 'LIC9')
    await selectOption('Support Centre', 'Support Centre 1')
    await selectOption('Region', 'Region 1')
    await selectOption('Biogeoclimatic Zone', 'BEC 1')
    await selectOption('TSA or TFL', 'TSA 1')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('Phone entry auto-formats digits as 222-222-2222', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    await userEvent.type(screen.getByLabelText('Phone'), '2505551212')

    expect(screen.getByLabelText('Phone')).toHaveValue('250-555-1212')
  })

  test('a partial phone blocks save with the complete-number message (no PUT)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    // Fill the required fields so only the (partial) phone can block the save.
    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    await userEvent.type(screen.getByLabelText('License'), 'LIC9')
    await selectOption('Support Centre', 'Support Centre 1')
    await selectOption('Region', 'Region 1')
    await selectOption('Biogeoclimatic Zone', 'BEC 1')
    await selectOption('TSA or TFL', 'TSA 1')
    await userEvent.type(screen.getByLabelText('Phone'), '250555')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      screen.getByText('Phone must be a complete 10-digit number (e.g. 250-555-1212).'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('blank required fields block save with verbatim Value Required (no PUT)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(put).not.toHaveBeenCalled()
  })

  test('Copy opens a prefilled editor (create path)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^copy$/i })[0])

    expect(screen.getByText('Copy Page')).toBeInTheDocument()
    expect(screen.getByLabelText('License')).toHaveValue('LIC1')
    // The seeded code (SC1) resolves to its option description in the combobox input value.
    expect(await screen.findByRole('combobox', { name: 'Support Centre' })).toHaveValue(
      'Support Centre 1',
    )
  })

  test('Check Status (all pages) renders the per-page / per-sample results', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          pages: [
            {
              id: 8001,
              met: false,
              issues: [
                {
                  field: 'Contact',
                  message: { key: 'missingRequiredFieldMsg', text: 'Value Required' },
                },
              ],
              samples: [
                {
                  id: 8101,
                  met: false,
                  issues: [
                    {
                      field: 'Skidding/Yarding',
                      message: { key: 'skiddingYardingEqualsCentPercent', text: 'Must equal 100%' },
                    },
                  ],
                },
              ],
            },
          ],
        }),
      ),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Must equal 100%')).toBeInTheDocument()
    expect(screen.getByText('Value Required')).toBeInTheDocument()
  })

  test('editable:false renders View and disables Add/Copy/Delete (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    expect(screen.getByRole('button', { name: /add new page/i })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /^view$/i }).length).toBeGreaterThan(0)
    screen.getAllByRole('button', { name: /^copy$/i }).forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /^delete$/i }).forEach((b) => expect(b).toBeDisabled())
  })

  test('guard: a failed load surfaces the API detail', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ detail: 'Schedule 8 is not open for this year.' }, { status: 404 }),
      ),
    )
    renderSchedule8()

    expect(await screen.findByText('Schedule 8 is not open for this year.')).toBeInTheDocument()
  })

  test('Edit an existing page PUTs (id + revision) and shows the success message', async () => {
    let body: unknown
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    // The Edit Page heading now carries the page label ("Edit Page — Page # 1 …").
    expect(screen.getByRole('heading', { name: /^Edit Page —/ })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(body).toMatchObject({ id: 8001, revisionCount: 0 })
    // Save stays on the record — the edit panel remains open.
    expect(screen.getByRole('heading', { name: /^Edit Page —/ })).toBeInTheDocument()
  })

  test('a failed save surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () =>
        HttpResponse.json({ detail: 'License already exists for this year.' }, { status: 409 }),
      ),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('License already exists for this year.')).toBeInTheDocument()
  })

  test('View renders read-only values (no inputs) and a Close button (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^view$/i })[0])
    expect(screen.getByText('View Page')).toBeInTheDocument()
    expect(screen.queryByLabelText('License')).not.toBeInTheDocument()
    expect(screen.getByText('seed comment')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^close$/i })).toBeInTheDocument()
  })

  test('Delete confirms, DELETEs, re-reads the doc, and shows the success message', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(deleted ? doc({ pages: [emptyPage] }) : doc())),
      http.delete(`${PAGES_URL}/8001`, () => {
        deleted = true
        return HttpResponse.json({ message: { key: 'x', text: 'Data deleted successfully' } })
      }),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // The deleted page's unique composite (-CP: CP1) is gone; the row numbers re-index, so assert on
    // its cutting permit rather than "Page # 1" (which the remaining page now occupies).
    await waitFor(() => expect(screen.queryByText(/-CP: CP1/)).not.toBeInTheDocument())
  })

  test('a failed delete surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${PAGES_URL}/8001`, () =>
        HttpResponse.json({ detail: 'Page has samples and cannot be deleted.' }, { status: 409 }),
      ),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Page has samples and cannot be deleted.')).toBeInTheDocument()
  })

  test('a failed Check Status surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({ detail: 'Check status is unavailable.' }, { status: 500 }),
      ),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Check status is unavailable.')).toBeInTheDocument()
  })

  test('Check Status success messages render as success notifications', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          messages: [{ key: 'allGood', text: 'All requirements met.' }],
          pages: [],
        }),
      ),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('All requirements met.')).toBeInTheDocument()
  })

  test('selecting TFL in TSA-or-TFL enables the TFL field and disables Supply Block (STA-002)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    // TFL is a free-text input (legacy ILCR-161), not a dropdown; Supply Block is a dropdown.
    expect(screen.getByRole('textbox', { name: 'TFL' })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: 'Supply Block' })).toBeEnabled()

    // The TSA-or-TFL selector carries the legacy 'TFL' marker; choosing it flips the TFL/Supply Block pair.
    await selectOption('TSA or TFL', 'TFL')
    expect(screen.getByRole('textbox', { name: 'TFL' })).toBeEnabled()
    expect(screen.getByRole('combobox', { name: 'Supply Block' })).toBeDisabled()
  })

  test('switching a saved TFL page to a TSA clears tflNumber before PUT (BR-03)', async () => {
    // Regression: editing a page that was saved as a TFL, then choosing a TSA in the TSA-or-TFL
    // selector must not leave the old tflNumber in the request — the backend treats any non-blank
    // tflNumber as TFL mode, so a stale value would silently re-persist the page as a TFL.
    const tflPage: Page = {
      ...fullPage,
      tsaNumber: 'TFL',
      tsaNumberLabel: 'TFL',
      tflNumber: 'TFL1',
      tflNumberLabel: 'TFL One',
      supplyBlock: null,
      supplyBlockLabel: null,
    }
    let body: { tsaNumber?: unknown; tflNumber?: unknown; supplyBlock?: unknown } | undefined
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ pages: [tflPage] }))),
      http.put(PAGES_URL, async ({ request }) => {
        body = (await request.json()) as typeof body
        return HttpResponse.json(doc({ pages: [tflPage], message: savedMsg }))
      }),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await selectOption('TSA or TFL', 'TSA 1')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(body).toMatchObject({ tsaNumber: 'TSA1', tflNumber: null })
  })

  test('Back closes the editor panel', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    expect(screen.getByText('New Page')).toBeInTheDocument()
    // The panel's discard button is now "Back" (the always-mounted modals still carry "Cancel").
    await userEvent.click(screen.getByRole('button', { name: /^back$/i }))
    expect(screen.queryByText('New Page')).not.toBeInTheDocument()
  })

  test('an empty document renders the no-pages placeholder', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [] }))))
    renderSchedule8()

    expect(await screen.findByText('No pages have been added.')).toBeInTheDocument()
  })

  test('a detail-less save error falls back to the generic client message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () => HttpResponse.json({}, { status: 500 })),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
  })

  test('a detail-less load error falls back to the generic load message', async () => {
    server.use(http.get(URL, () => HttpResponse.json({}, { status: 500 })))
    renderSchedule8()

    expect(await screen.findByText('Unable to load Schedule 8.')).toBeInTheDocument()
  })

  test('a detail-less delete error falls back to the generic delete message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${PAGES_URL}/8001`, () => HttpResponse.json({}, { status: 500 })),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Unable to delete page.')).toBeInTheDocument()
  })

  test('a detail-less Check Status error falls back to the generic message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () => HttpResponse.json({}, { status: 500 })),
    )
    renderSchedule8()
    await screen.findByText(/Page # 1/)

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Unable to check status.')).toBeInTheDocument()
  })

  test('a page with null tsa / cutting-permit renders the composite label with fallbacks', async () => {
    const bare: Page = {
      ...emptyPage,
      id: 8009,
      license: null,
      tsaNumber: null,
      cuttingPermit: null,
    }
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [bare] }))))
    renderSchedule8()

    // The composite still renders — empty TSA shows nothing after "-TSA:", cutting permit falls to " - ".
    const cell = await screen.findByText(
      (text) => text.startsWith('Page # 1') && text.includes('-CP:'),
    )
    expect(cell).toBeInTheDocument()
  })
})

describe('Schedule8 sample level', () => {
  const openSamples = async () => {
    renderSchedule8()
    await screen.findByText(/Page # 1/)
    await userEvent.click(screen.getAllByRole('button', { name: /^(edit|view)$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByRole('button', { name: /add new sample/i })
  }

  test('the TtT Samples link opens the sample list', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openSamples()
    expect(screen.getByRole('button', { name: /add new sample/i })).toBeEnabled()
    // Each sample renders the legacy composite label (row number + contract id).
    expect(screen.getByText(/Sample # 1 - C-1/)).toBeInTheDocument()
  })

  test('add a sample PUTs and shows the API success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, () => HttpResponse.json(doc({ message: savedMsg }))),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.type(screen.getByLabelText('Contract ID'), 'C-2')
    await userEvent.type(screen.getByLabelText('Ground Base %'), '100')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('a skidding total over 100 blocks Save (no PUT); under 100 is allowed (asymmetry)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, () => {
        put()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.type(screen.getByLabelText('Contract ID'), 'C-3')
    await userEvent.type(screen.getByLabelText('Ground Base %'), '60')
    await userEvent.type(screen.getByLabelText('Grapple %'), '50')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      await screen.findByText('Skidding/Yarding percentages can not total more than 100%.'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('single-page Check Status renders the page-scoped result (S14)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(PAGE_CHECK_8001, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          pages: [
            {
              id: 8001,
              met: false,
              issues: [],
              samples: [
                {
                  id: 8101,
                  met: false,
                  issues: [
                    {
                      field: 'Actual Harvested',
                      message: {
                        key: 'invalidLowerRangeZeroErrorMsg',
                        text: 'Must be greater than 0',
                      },
                    },
                  ],
                },
              ],
            },
          ],
        }),
      ),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Must be greater than 0')).toBeInTheDocument()
  })

  test('Back from a dirty sample editor confirms before leaving (S13)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.click(screen.getByRole('button', { name: /back to pages/i }))

    expect(
      screen.getByText('Unsaved data will be lost. Are you sure to continue?'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))
    expect(await screen.findByText('Page Summary')).toBeInTheDocument()
  })

  test('Back with a clean (closed) editor returns immediately without a confirm', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /back to pages/i }))
    expect(await screen.findByText('Page Summary')).toBeInTheDocument()
  })

  test('a nonzero Helicopter % reveals the Helicopter sub-block and enforces its fields', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, () => {
        put()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.type(screen.getByLabelText('Contract ID'), 'C-H')
    await userEvent.type(screen.getByLabelText('Helicopter %'), '100')

    // The Helicopter section is always shown; its fields become required once Helicopter % ≠ 0.
    expect(screen.getByLabelText('Distance (km)')).toBeInTheDocument()
    expect(screen.getByLabelText('Cycle Time (min)')).toBeInTheDocument()

    // Save is blocked because the conditional fields are blank.
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))
    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(put).not.toHaveBeenCalled()
  })

  test('a fully-filled Helicopter sample PUTs and shows the success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, () => HttpResponse.json(doc({ message: savedMsg }))),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.type(screen.getByLabelText('Contract ID'), 'C-H')
    await userEvent.type(screen.getByLabelText('Helicopter %'), '100')
    await userEvent.type(screen.getByLabelText('Distance (km)'), '500')
    await userEvent.type(screen.getByLabelText('Cycle Time (min)'), '12')
    await userEvent.selectOptions(screen.getByLabelText('Direction'), 'Y')
    await userEvent.selectOptions(screen.getByLabelText('Dump Destination'), 'N')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  }, 15000)

  test('a nonzero Other % reveals the Other Skid Type block and requires a non-NA skid type', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, () => {
        put()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.type(screen.getByLabelText('Contract ID'), 'C-O')
    await userEvent.type(screen.getByLabelText('Other %'), '100')

    // NA is treated as blank → still required.
    await selectOption('Skid Type', 'Not Applicable')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))
    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(put).not.toHaveBeenCalled()
  })

  test('a failed sample save surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, () =>
        HttpResponse.json({ detail: 'Contract ID must be unique within a page.' }, { status: 409 }),
      ),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.type(screen.getByLabelText('Contract ID'), 'C-9')
    await userEvent.type(screen.getByLabelText('Ground Base %'), '100')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Contract ID must be unique within a page.')).toBeInTheDocument()
  })

  test('Edit an existing sample PUTs (id + revision) and shows the success message', async () => {
    let body: unknown
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }))
    // The Edit Sample heading now carries the sample label ("Edit Sample — Sample # 1 …").
    expect(screen.getByRole('heading', { name: /^Edit Sample —/ })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(body).toMatchObject({ id: 8101, revisionCount: 0 })
    // Save stays on the record — the sample edit panel remains open.
    expect(screen.getByRole('heading', { name: /^Edit Sample —/ })).toBeInTheDocument()
  })

  test('Delete a sample DELETEs and shows the success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${SAMPLES_8001}/8101`, () =>
        HttpResponse.json(doc({ message: { key: 'x', text: 'Data deleted successfully' } })),
      ),
    )
    await openSamples()

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('a failed sample delete surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${SAMPLES_8001}/8101`, () =>
        HttpResponse.json(
          { detail: 'Sample is referenced and cannot be deleted.' },
          { status: 409 },
        ),
      ),
    )
    await openSamples()

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(
      await screen.findByText('Sample is referenced and cannot be deleted.'),
    ).toBeInTheDocument()
  })

  test('a failed single-page Check Status surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(PAGE_CHECK_8001, () =>
        HttpResponse.json({ detail: 'Page check unavailable.' }, { status: 500 }),
      ),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Page check unavailable.')).toBeInTheDocument()
  })

  test('View sample renders read-only values and a Close button (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderSchedule8()
    await screen.findByText(/Page # 1/)
    await userEvent.click(screen.getAllByRole('button', { name: /^(edit|view)$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByRole('button', { name: /add new sample/i })

    await userEvent.click(screen.getByRole('button', { name: /^view$/i }))
    expect(screen.getByText('View Sample')).toBeInTheDocument()
    expect(screen.queryByLabelText('Contract ID')).not.toBeInTheDocument()
    // The panel Close is the first Close-labelled control.
    await userEvent.click(screen.getAllByRole('button', { name: /^close$/i })[0])
    expect(screen.queryByText('View Sample')).not.toBeInTheDocument()
  })

  test('a detail-less sample save error falls back to the generic message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(SAMPLES_8001, () => HttpResponse.json({}, { status: 500 })),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }))
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Sample could not be saved.')).toBeInTheDocument()
  })

  test('a detail-less sample delete error falls back to the generic message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${SAMPLES_8001}/8101`, () => HttpResponse.json({}, { status: 500 })),
    )
    await openSamples()

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Unable to delete sample.')).toBeInTheDocument()
  })

  test('a detail-less single-page Check Status error falls back to the generic message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(PAGE_CHECK_8001, () => HttpResponse.json({}, { status: 500 })),
    )
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Unable to check status.')).toBeInTheDocument()
  })

  test('a sample with a null contract id renders the composite label with an empty suffix', async () => {
    const bareSample: Sample = { ...sample8101, id: 8155, contractId: null, cutBlock: null }
    const pageBare: Page = { ...fullPage, samples: [bareSample], sampleCount: 1 }
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [pageBare, emptyPage] }))))
    renderSchedule8()
    await screen.findByText(/Page # 1/)
    await userEvent.click(screen.getAllByRole('button', { name: /^(edit|view)$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByRole('button', { name: /add new sample/i })

    // Legacy parity: a blank contract id still renders "Sample # 1 -" (empty suffix, no placeholder).
    expect(screen.getByText(/^Sample # 1 -/)).toBeInTheDocument()
  })

  test('Cancel from the sample confirm modal keeps the editor open (S13)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openSamples()

    await userEvent.click(screen.getByRole('button', { name: /add new sample/i }))
    await userEvent.click(screen.getByRole('button', { name: /back to pages/i }))
    // Cancel the confirm — the editor should remain.
    const cancels = screen.getAllByRole('button', { name: /^cancel$/i })
    await userEvent.click(cancels[cancels.length - 1])
    expect(screen.getByText('New Sample')).toBeInTheDocument()
  })
})

describe('Schedule8 additions/deductions level', () => {
  const openRates = async () => {
    renderSchedule8()
    await screen.findByText(/Page # 1/)
    await userEvent.click(screen.getAllByRole('button', { name: /^(edit|view)$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByRole('button', { name: /add new sample/i })
    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }))
    await userEvent.click(screen.getByRole('button', { name: /Additions \(1\):/i }))
    await screen.findByText(/Additions \/ Deductions — Sample # 1 - C-1/i)
  }

  test('the Additions link opens the rate tables with the existing rows', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openRates()
    expect(screen.getByText('Bridge build')).toBeInTheDocument()
    expect(screen.getByText('Road credit')).toBeInTheDocument()
  })

  test('the levels are URL-driven; the browser Back button steps rates → samples → pages', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    // Build the router directly so the test can reach router.history (the browser Back button).
    const router = makeRouter()
    render(<RouterProvider router={router} />)
    await screen.findByText(/Page # 1/)

    // pages → samples (page 8001)
    await userEvent.click(screen.getAllByRole('button', { name: /^(edit|view)$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByRole('button', { name: /add new sample/i })
    expect(router.state.location.search).toMatchObject({ pageId: 8001 })

    // samples → rates (sample 8101)
    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }))
    await userEvent.click(screen.getByRole('button', { name: /Additions \(1\):/i }))
    await screen.findByText(/Additions \/ Deductions — Sample # 1 - C-1/i)
    expect(router.state.location.search).toMatchObject({ pageId: 8001, sampleId: 8101 })

    // browser Back: rates → samples
    router.history.back()
    await screen.findByRole('button', { name: /add new sample/i })
    expect(
      screen.queryByText(/Additions \/ Deductions — Sample # 1 - C-1/i),
    ).not.toBeInTheDocument()
    expect(router.state.location.search.pageId).toBe(8001)
    expect(router.state.location.search.sampleId).toBeUndefined()

    // browser Back: samples → pages
    router.history.back()
    await screen.findByRole('button', { name: /add new page/i })
    expect(screen.queryByRole('button', { name: /add new sample/i })).not.toBeInTheDocument()
    expect(router.state.location.search.pageId).toBeUndefined()
  })

  test('add an addition POSTs the rate sub-resource and shows the success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () => HttpResponse.json(doc({ message: savedMsg }))),
    )
    await openRates()

    await selectOption('Additions — Cost Item', 'Bridge Construction')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await selectOption('Additions — Cost Type', 'Fixed')
    await userEvent.click(screen.getByRole('button', { name: /add additions/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('Save commits a typed-but-not-yet-Added addition draft before leaving (review #3)', async () => {
    // Rate rows persist on Add, so Save is meaningful only for a draft the user typed but did not Add.
    // It must POST that draft (not silently discard it) and then return to the sample.
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, async ({ request }) => {
        post(await request.json())
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openRates()

    // Type an addition draft but do NOT click "Add additions" — then click the action-bar Save.
    await selectOption('Additions — Cost Item', 'Bridge Construction')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await selectOption('Additions — Cost Type', 'Fixed')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][0]).toMatchObject({ costingRate: 7 })
    // Back on the samples level (the rates panel closed).
    await waitFor(() =>
      expect(
        screen.queryByText(/Additions \/ Deductions — Sample # 1 - C-1/i),
      ).not.toBeInTheDocument(),
    )
  })

  test('delete a rate row DELETEs the sub-resource', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${RATES_8101}/8201`, () => {
        deleted = true
        return HttpResponse.json(doc({ message: { key: 'x', text: 'Data deleted successfully' } }))
      }),
    )
    await openRates()

    // Per-row Delete buttons + the modal's primary Delete. Click the first row Delete, then the
    // modal's last Delete to confirm.
    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    await waitFor(() => expect(deleted).toBe(true))
  })

  test('add a deduction POSTs the rate sub-resource and shows the success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () => HttpResponse.json(doc({ message: savedMsg }))),
    )
    await openRates()

    await selectOption('Deductions — Cost Item', 'Road Credit')
    await userEvent.type(screen.getByLabelText('Deductions — $/m³'), '3')
    await selectOption('Deductions — Cost Type', 'Variable')
    await userEvent.click(screen.getByRole('button', { name: /add deductions/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('blank required rate fields block the add (no POST) and show Value Required', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () => {
        post()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openRates()

    await userEvent.click(screen.getByRole('button', { name: /add additions/i }))

    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(post).not.toHaveBeenCalled()
  })

  test('an out-of-range $/m³ blocks the add with the verbatim rate message (no POST)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () => {
        post()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openRates()

    await selectOption('Additions — Cost Item', 'Bridge Construction')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '99999999')
    await selectOption('Additions — Cost Type', 'Fixed')
    await userEvent.click(screen.getByRole('button', { name: /add additions/i }))

    expect(
      await screen.findByText('Entered rate must be between 0 and 9,999,999.99.'),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('a failed add surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () =>
        HttpResponse.json({ detail: 'Cost item is not valid for additions.' }, { status: 400 }),
      ),
    )
    await openRates()

    await selectOption('Additions — Cost Item', 'Bridge Construction')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await selectOption('Additions — Cost Type', 'Fixed')
    await userEvent.click(screen.getByRole('button', { name: /add additions/i }))

    expect(await screen.findByText('Cost item is not valid for additions.')).toBeInTheDocument()
  })

  test('a failed delete surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${RATES_8101}/8201`, () =>
        HttpResponse.json({ detail: 'Row is locked and cannot be deleted.' }, { status: 409 }),
      ),
    )
    await openRates()

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Row is locked and cannot be deleted.')).toBeInTheDocument()
  })

  test('Cancel from the rates screen confirms and returns to the sample list', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openRates()

    // Make the form dirty to trigger the unsaved-changes confirm
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '5')

    // Cancel (the action-bar button, first in DOM) triggers the unsaved-changes confirm.
    await userEvent.click(screen.getAllByRole('button', { name: /^cancel$/i })[0])
    expect(
      screen.getByText('Unsaved data will be lost. Are you sure to continue?'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))

    expect(await screen.findByRole('button', { name: /add new sample/i })).toBeInTheDocument()
  })

  test('blank required deduction fields block the add and show Value Required', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () => {
        post()
        return HttpResponse.json(doc({ message: savedMsg }))
      }),
    )
    await openRates()

    await userEvent.click(screen.getByRole('button', { name: /add deductions/i }))

    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(post).not.toHaveBeenCalled()
  })

  test('Cancel from the delete-row confirm keeps the row', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openRates()

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const cancels = screen.getAllByRole('button', { name: /^cancel$/i })
    await userEvent.click(cancels[cancels.length - 1])

    expect(screen.getByText('Bridge build')).toBeInTheDocument()
  })

  test('Cancel from the rates confirm modal stays on the rates screen', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openRates()

    // Make the form dirty to trigger the unsaved-changes confirm
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '5')

    // Action-bar Cancel opens the confirm; dismissing it (the modal's Cancel, last in DOM) stays put.
    await userEvent.click(screen.getAllByRole('button', { name: /^cancel$/i })[0])
    const cancels = screen.getAllByRole('button', { name: /^cancel$/i })
    await userEvent.click(cancels[cancels.length - 1])

    expect(screen.getByText(/Additions \/ Deductions — Sample # 1 - C-1/i)).toBeInTheDocument()
  })

  test('a rate row with null description / cost-type description falls back to a dash / the code', async () => {
    const nullRowSample: Sample = {
      ...sample8101,
      additions: [
        {
          id: 8201,
          revisionCount: 0,
          costItemCode: 82,
          itemDescription: null,
          costingRate: 5,
          costTypeCode: 'CT1',
          costTypeDescription: null,
        },
      ],
    }
    const pageWithNulls: Page = { ...fullPage, samples: [nullRowSample] }
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [pageWithNulls, emptyPage] }))))
    await openRates()

    // Description cell falls back to the em-dash placeholder; cost-type falls back to the raw code.
    expect(screen.getByText('CT1')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })

  test('adding with a description succeeds even when the response omits a message', async () => {
    let body: { itemDescription?: string | null } = {}
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, async ({ request }) => {
        body = (await request.json()) as typeof body
        // No `message` in the response → the `?? null` fallback path.
        return HttpResponse.json(doc())
      }),
    )
    await openRates()

    await selectOption('Additions — Cost Item', 'Bridge Construction')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await selectOption('Additions — Cost Type', 'Fixed')
    await userEvent.type(screen.getByLabelText('Additions — Description'), 'Culvert')
    await userEvent.click(screen.getByRole('button', { name: /add additions/i }))

    await waitFor(() => expect(body.itemDescription).toBe('Culvert'))
    // No success banner because the response carried no message.
    expect(screen.queryByTitle('Success')).not.toBeInTheDocument()
  })

  test('empty rate tables and null-costing rows render placeholders and a zero total', async () => {
    const emptyRatesSample: Sample = {
      ...sample8101,
      id: 8166,
      additions: [],
      deductions: [
        {
          id: 8301,
          revisionCount: 0,
          costItemCode: 101,
          itemDescription: null,
          costingRate: null,
          costTypeCode: null,
          costTypeDescription: null,
        },
      ],
    }
    const pageEmpty: Page = { ...fullPage, samples: [emptyRatesSample], sampleCount: 1 }
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [pageEmpty, emptyPage] }))))
    await openRates()

    // The additions table is empty; the deductions table has a fully-null row.
    expect(screen.getByText('No rows have been added.')).toBeInTheDocument()
    // The Additions footer total is 0 (empty) and the null-costing deduction contributes 0.
    expect(screen.getAllByText('0').length).toBeGreaterThan(0)
  })

  test('a detail-less add error falls back to the generic row message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () => HttpResponse.json({}, { status: 500 })),
    )
    await openRates()

    await selectOption('Additions — Cost Item', 'Bridge Construction')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await selectOption('Additions — Cost Type', 'Fixed')
    await userEvent.click(screen.getByRole('button', { name: /add additions/i }))

    expect(await screen.findByText('Row could not be saved.')).toBeInTheDocument()
  })

  test('a detail-less delete error falls back to the generic row message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${RATES_8101}/8201`, () => HttpResponse.json({}, { status: 500 })),
    )
    await openRates()

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Unable to delete row.')).toBeInTheDocument()
  })

  test('read-only rates screen hides the add form and per-row Delete (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderSchedule8()
    await screen.findByText(/Page # 1/)
    await userEvent.click(screen.getAllByRole('button', { name: /^(edit|view)$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByRole('button', { name: /add new sample/i })
    await userEvent.click(screen.getByRole('button', { name: /^view$/i }))
    await userEvent.click(screen.getByRole('button', { name: /Additions \(1\):/i }))
    await screen.findByText(/Additions \/ Deductions — Sample # 1 - C-1/i)

    expect(screen.queryByLabelText('Additions — Cost Item')).not.toBeInTheDocument()
    // Read-only shows a single Close (the action-bar button, first in DOM) that returns immediately.
    await userEvent.click(screen.getAllByRole('button', { name: /^close$/i })[0])
    expect(await screen.findByRole('button', { name: /add new sample/i })).toBeInTheDocument()
  })

  test('stale PUT is ignored when context changes before it settles (Story 29.6)', async () => {
    let releasePut = () => {}
    const releasePromise = new Promise<void>((resolve) => {
      releasePut = resolve
    })

    server.use(
      http.get(URL, ({ request }) =>
        new window.URL(request.url).searchParams.get('millId') === '999'
          ? HttpResponse.json(
              doc({
                millId: 999,
                year: 2020,
                editable: false,
                pages: [
                  {
                    id: 999,
                    revisionCount: 1,
                    tsaNumber: 'TSA1',
                    supplyBlock: null,
                    cuttingPermit: 'Context 999/2020 loaded',
                    comments: null,
                    subPageRows: [],
                    categories: [],
                  },
                ],
              }),
            )
          : HttpResponse.json(doc()),
      ),
      http.put(PAGES_URL, async () => {
        await releasePromise
        return HttpResponse.json({
          ...doc(),
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )

    const rootRoute = createRootRoute()
    const scheduleRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: '/schedule-8',
      validateSearch: (s: Record<string, unknown>) => ({
        pageId: s.pageId == null || s.pageId === '' ? undefined : Number(s.pageId),
        sampleId: s.sampleId == null || s.sampleId === '' ? undefined : Number(s.sampleId),
      }),
      component: () => (
        <MillYearProvider initial={{ millId: 514, year: 2021 }}>
          {/* eslint-disable-next-line @typescript-eslint/no-use-before-define */}
          <StaleRaceHarness />
        </MillYearProvider>
      ),
    })
    const router = createRouter({
      routeTree: rootRoute.addChildren([scheduleRoute]),
      history: createMemoryHistory({ initialEntries: ['/schedule-8'] }),
    })
    render(<RouterProvider router={router} />)
    const user = userEvent.setup()

    await screen.findByText(/Page # 1/)
    await user.click(screen.getAllByRole('button', { name: /^(edit|view)$/i })[0])
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText(/Context 999\/2020 loaded/)).toBeInTheDocument()

    releasePut()
    await waitFor(() => {
      expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    })
  })
})

import useMillYear from '@/context/millYear/useMillYear'

const StaleRaceHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <Schedule8 />
    </>
  )
}
