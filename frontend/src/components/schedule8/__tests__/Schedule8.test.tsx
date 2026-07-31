import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the other schedules.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule8 from '@/components/schedule8'
import type { Page, Sample } from '@/interfaces/Schedule8Response'

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
  test('lists pages with sample counts; Add New Page enabled (editable)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule8 />)

    expect(await screen.findByText('LIC1')).toBeInTheDocument()
    expect(screen.getByText('LIC2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add new page/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /TtT Samples \(1\)/i })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /^edit$/i }).length).toBeGreaterThan(0)
  })

  test('Add New Page opens the editor with editable inputs', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))

    expect(screen.getByText('New Page')).toBeInTheDocument()
    expect(screen.getByLabelText('License')).toHaveValue('')
    expect(screen.getByLabelText('Support Centre')).toBeInTheDocument()
    expect(screen.getByLabelText('TSA or TFL')).toBeInTheDocument()
  })

  test('save a new page PUTs and shows the API success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () => HttpResponse.json(doc({ message: savedMsg }))),
    )
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    await userEvent.type(screen.getByLabelText('License'), 'LIC9')
    await userEvent.type(screen.getByLabelText('Support Centre'), 'SC1')
    await userEvent.type(screen.getByLabelText('Region'), 'R1')
    await userEvent.type(screen.getByLabelText('Biogeoclimatic Zone'), 'BZ1')
    await userEvent.type(screen.getByLabelText('TSA or TFL'), 'TSA1')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
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
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(put).not.toHaveBeenCalled()
  })

  test('Copy opens a prefilled editor (create path)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getAllByRole('button', { name: /^copy$/i })[0])

    expect(screen.getByText('Copy Page')).toBeInTheDocument()
    expect(screen.getByLabelText('License')).toHaveValue('LIC1')
    expect(screen.getByLabelText('Support Centre')).toHaveValue('SC1')
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
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Must equal 100%')).toBeInTheDocument()
    expect(screen.getByText('Value Required')).toBeInTheDocument()
  })

  test('editable:false renders View and disables Add/Copy/Delete (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule8 />)
    await screen.findByText('LIC1')

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
    render(<Schedule8 />)

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
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    expect(screen.getByText('Edit Page')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(body).toMatchObject({ id: 8001, revisionCount: 0 })
  })

  test('a failed save surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () =>
        HttpResponse.json({ detail: 'License already exists for this year.' }, { status: 409 }),
      ),
    )
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('License already exists for this year.')).toBeInTheDocument()
  })

  test('View renders read-only values (no inputs) and a Close button (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule8 />)
    await screen.findByText('LIC1')

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
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('LIC1')).not.toBeInTheDocument())
  })

  test('a failed delete surfaces the verbatim ProblemDetail.detail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${PAGES_URL}/8001`, () =>
        HttpResponse.json({ detail: 'Page has samples and cannot be deleted.' }, { status: 409 }),
      ),
    )
    render(<Schedule8 />)
    await screen.findByText('LIC1')

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
    render(<Schedule8 />)
    await screen.findByText('LIC1')

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
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('All requirements met.')).toBeInTheDocument()
  })

  test('typing TFL in TSA-or-TFL enables the TFL field and disables Supply Block (STA-002)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    expect(screen.getByLabelText('TFL')).toBeDisabled()
    expect(screen.getByLabelText('Supply Block')).toBeEnabled()

    await userEvent.type(screen.getByLabelText('TSA or TFL'), 'TFL')
    expect(screen.getByLabelText('TFL')).toBeEnabled()
    expect(screen.getByLabelText('Supply Block')).toBeDisabled()
  })

  test('Cancel closes the editor panel', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /add new page/i }))
    expect(screen.getByText('New Page')).toBeInTheDocument()
    // The always-mounted delete modal contributes a hidden Cancel; the panel Cancel is the first.
    await userEvent.click(screen.getAllByRole('button', { name: /^cancel$/i })[0])
    expect(screen.queryByText('New Page')).not.toBeInTheDocument()
  })

  test('an empty document renders the no-pages placeholder', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [] }))))
    render(<Schedule8 />)

    expect(await screen.findByText('No pages have been added.')).toBeInTheDocument()
  })

  test('a detail-less save error falls back to the generic client message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(PAGES_URL, () => HttpResponse.json({}, { status: 500 })),
    )
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()
  })

  test('a detail-less load error falls back to the generic load message', async () => {
    server.use(http.get(URL, () => HttpResponse.json({}, { status: 500 })))
    render(<Schedule8 />)

    expect(await screen.findByText('Unable to load Schedule 8.')).toBeInTheDocument()
  })

  test('a detail-less delete error falls back to the generic delete message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${PAGES_URL}/8001`, () => HttpResponse.json({}, { status: 500 })),
    )
    render(<Schedule8 />)
    await screen.findByText('LIC1')

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
    render(<Schedule8 />)
    await screen.findByText('LIC1')

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Unable to check status.')).toBeInTheDocument()
  })

  test('null labels/license render the em-dash and Page-id fallbacks in the summary', async () => {
    const bare: Page = {
      ...emptyPage,
      id: 8009,
      license: null,
      supportCentre: null,
      supportCentreLabel: null,
      region: null,
      regionLabel: null,
    }
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [bare] }))))
    render(<Schedule8 />)

    expect(await screen.findByText('Page 8009')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
  })
})

describe('Schedule8 sample level', () => {
  const openSamples = async () => {
    render(<Schedule8 />)
    await screen.findByText('LIC1')
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByText(/Samples — LIC1/i)
  }

  test('the TtT Samples link opens the sample list', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openSamples()
    expect(screen.getByRole('button', { name: /add new sample/i })).toBeEnabled()
    expect(screen.getByText('C-1')).toBeInTheDocument()
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

    // The Helicopter sub-block is now visible.
    expect(screen.getByLabelText('Distance')).toBeInTheDocument()
    expect(screen.getByLabelText('Cycle Time')).toBeInTheDocument()

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
    await userEvent.type(screen.getByLabelText('Distance'), '500')
    await userEvent.type(screen.getByLabelText('Cycle Time'), '12')
    await userEvent.selectOptions(screen.getByLabelText('Direction'), 'Y')
    await userEvent.selectOptions(screen.getByLabelText('Dump Destination'), 'N')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

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
    await userEvent.type(screen.getByLabelText('Skid Type'), 'NA')
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
    expect(screen.getByText('Edit Sample')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(body).toMatchObject({ id: 8101, revisionCount: 0 })
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
    render(<Schedule8 />)
    await screen.findByText('LIC1')
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByText(/Samples — LIC1/i)

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

  test('a sample with null contract id / cut block renders em-dash placeholders', async () => {
    const bareSample: Sample = { ...sample8101, id: 8155, contractId: null, cutBlock: null }
    const pageBare: Page = { ...fullPage, samples: [bareSample], sampleCount: 1 }
    server.use(http.get(URL, () => HttpResponse.json(doc({ pages: [pageBare, emptyPage] }))))
    render(<Schedule8 />)
    await screen.findByText('LIC1')
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByText(/Samples — LIC1/i)

    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
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
    render(<Schedule8 />)
    await screen.findByText('LIC1')
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByText(/Samples — LIC1/i)
    await userEvent.click(screen.getByRole('button', { name: /^edit$/i }))
    await userEvent.click(screen.getByRole('button', { name: /Additions \(1\):/i }))
    await screen.findByText(/Additions \/ Deductions — C-1/i)
  }

  test('the Additions link opens the rate tables with the existing rows', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openRates()
    expect(screen.getByText('Bridge build')).toBeInTheDocument()
    expect(screen.getByText('Road credit')).toBeInTheDocument()
  })

  test('add an addition POSTs the rate sub-resource and shows the success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(RATES_8101, () => HttpResponse.json(doc({ message: savedMsg }))),
    )
    await openRates()

    await userEvent.type(screen.getByLabelText('Additions — Cost Item'), '82')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await userEvent.type(screen.getByLabelText('Additions — Cost Type'), 'CT1')
    await userEvent.click(screen.getByRole('button', { name: /add additions/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
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

    await userEvent.type(screen.getByLabelText('Deductions — Cost Item'), '101')
    await userEvent.type(screen.getByLabelText('Deductions — $/m³'), '3')
    await userEvent.type(screen.getByLabelText('Deductions — Cost Type'), 'CT2')
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

    await userEvent.type(screen.getByLabelText('Additions — Cost Item'), '82')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '99999999')
    await userEvent.type(screen.getByLabelText('Additions — Cost Type'), 'CT1')
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

    await userEvent.type(screen.getByLabelText('Additions — Cost Item'), '82')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await userEvent.type(screen.getByLabelText('Additions — Cost Type'), 'CT1')
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

  test('Back from the rates screen confirms and returns to the sample list', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openRates()

    await userEvent.click(screen.getByRole('button', { name: /back to sample/i }))
    expect(
      screen.getByText('Unsaved data will be lost. Are you sure to continue?'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))

    expect(await screen.findByText(/Samples — LIC1/i)).toBeInTheDocument()
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

  test('Cancel from the rates Back confirm stays on the rates screen', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openRates()

    await userEvent.click(screen.getByRole('button', { name: /back to sample/i }))
    const cancels = screen.getAllByRole('button', { name: /^cancel$/i })
    await userEvent.click(cancels[cancels.length - 1])

    expect(screen.getByText(/Additions \/ Deductions — C-1/i)).toBeInTheDocument()
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

    await userEvent.type(screen.getByLabelText('Additions — Cost Item'), '82')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await userEvent.type(screen.getByLabelText('Additions — Cost Type'), 'CT1')
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

    await userEvent.type(screen.getByLabelText('Additions — Cost Item'), '82')
    await userEvent.type(screen.getByLabelText('Additions — $/m³'), '7')
    await userEvent.type(screen.getByLabelText('Additions — Cost Type'), 'CT1')
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
    render(<Schedule8 />)
    await screen.findByText('LIC1')
    await userEvent.click(screen.getByRole('button', { name: /TtT Samples \(1\)/i }))
    await screen.findByText(/Samples — LIC1/i)
    await userEvent.click(screen.getByRole('button', { name: /^view$/i }))
    await userEvent.click(screen.getByRole('button', { name: /Additions \(1\):/i }))
    await screen.findByText(/Additions \/ Deductions — C-1/i)

    expect(screen.queryByLabelText('Additions — Cost Item')).not.toBeInTheDocument()
    // Read-only back skips the confirm modal and returns immediately.
    await userEvent.click(screen.getByRole('button', { name: /back to sample/i }))
    expect(await screen.findByText(/Samples — LIC1/i)).toBeInTheDocument()
  })
})
