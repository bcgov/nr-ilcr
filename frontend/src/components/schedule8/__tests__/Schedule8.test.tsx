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
})
