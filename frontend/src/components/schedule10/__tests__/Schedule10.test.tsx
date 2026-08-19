import type { FC } from 'react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { getDefaultNormalizer, render, screen, waitFor } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import Schedule10 from '@/components/schedule10'
import type Schedule10Response from '@/interfaces/Schedule10Response'
import type { ConstructionPage, RoadDetail } from '@/interfaces/Schedule10Response'

// The road level is URL-driven, so this page renders inside a REAL memory router — the search hooks,
// navigation and the browser Back button all need router context.
function makeRouter(initialUrl = '/schedule-10') {
  const rootRoute = createRootRoute()
  const scheduleRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/schedule-10',
    validateSearch: (s: Record<string, unknown>) => {
      const raw = s.pageId
      const pageId = raw == null || raw === '' ? undefined : Number(raw)
      return {
        pageId: pageId != null && Number.isInteger(pageId) && pageId > 0 ? pageId : undefined,
      }
    },
    component: Schedule10,
  })
  return createRouter({
    routeTree: rootRoute.addChildren([scheduleRoute]),
    history: createMemoryHistory({ initialEntries: [initialUrl] }),
  })
}

const renderSchedule10 = (initialUrl = '/schedule-10') => {
  const router = makeRouter(initialUrl)
  return { router, ...render(<RouterProvider router={router} />) }
}

const URL = 'http://localhost:3000/api/v1/schedule10'
const PAGES_URL = `${URL}/pages`
const CHECK_URL = `${URL}/check-status`

const verbatim = { normalizer: getDefaultNormalizer({ collapseWhitespace: false, trim: false }) }

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

const roadDetail = (overrides: Partial<RoadDetail> = {}): RoadDetail => ({
  roadDetailId: 8910,
  rowNumber: 1,
  roadDetailLabel: 'Road #1, Mainline A',
  roadName: 'Mainline A',
  roadLifetimeCode: 'P',
  becClassification: {
    biogeoclimaticCatalogueId: 8801,
    becZoneCode: 'ICH',
    subzone: 'dw',
    variant: '1',
    phase: null,
    label: 'ICHdw1',
  },
  relSoilMoistRgmClsCode: '1',
  sideSlopePct: 25,
  subGrade: {
    length: 12.5,
    surfaceWidth: 6.5,
    actualCost: 150000,
    ttTransfer: null,
    otherTransfer: null,
    lessBridges: null,
    lessCulverts: null,
    lessLandings: null,
    lessOverland: null,
    lessOtherEng: null,
    lessEndHaul: null,
    totalCosts: 150000,
    totalDeductions: 0,
    total: 150000,
    costPerLength: 12000,
  },
  stabilizing: {
    ballastMethodCode: 'C',
    ballastMaterialCode: 'GR',
    length: 3,
    surfaceWidth: 6.5,
    depth: 0.3,
    distanceToSource: 12.4,
    actualCost: null,
    ttTransfer: null,
    otherTransfer: null,
    total: 0,
    costPerLength: null,
  },
  materialComposition: {
    solidRockPct: 10,
    rippableRockPct: 20,
    coarsePct: 40,
    finePct: 20,
    organicPct: 10,
    totalPct: 100,
  },
  detailedEngineeringCostInd: 'N',
  endHaulDistance: null,
  endHaulVolume: null,
  overlandDistance: null,
  overlandVolume: null,
  comments: null,
  revisionCount: 3,
  ...overrides,
})

const page = (overrides: Partial<ConstructionPage> = {}): ConstructionPage => ({
  pageId: 8900,
  pageNumber: 1,
  pageLabel: 'Page 1, Period: 2021-06, TSA: 01, SB: 01A, TFL:-',
  forestRegionCode: 'RNI',
  tsaNumber: '01',
  tsbNumberCode: '01A',
  tflNumberCode: null,
  roadGroup: '11',
  divisionName: 'North Division',
  constructionPeriod: '2021-06',
  roadDetailCount: 1,
  revisionCount: 2,
  roadDetails: [roadDetail()],
  ...overrides,
})

const doc = (overrides: Partial<Schedule10Response> = {}): Schedule10Response => ({
  millId: DEFAULT_MILL_ID,
  year: DEFAULT_YEAR,
  trackStatus: 'D',
  editable: true,
  pages: [page()],
  codeLists: {
    forestRegions: [{ code: 'RNI', description: 'Northern Interior' }],
    tsaNumbers: [
      { code: '01', description: 'Arrow TSA' },
      { code: '16', description: 'Lakes TSA' },
    ],
    supplyBlocks: [
      { code: '01A', description: 'Arrow TSA Block A' },
      { code: '16G', description: 'Lakes TSA Block G' },
    ],
    roadLifetimes: [{ code: 'P', description: 'Permanent' }],
    ballastMethods: [
      { code: 'C', description: 'Crushed' },
      { code: 'N', description: 'None' },
    ],
    ballastMaterials: [{ code: 'GR', description: 'Gravel' }],
    rsmrClasses: [{ code: '1', description: '1 - Very Dry' }],
    becClassifications: [
      {
        biogeoclimaticCatalogueId: 8801,
        becZoneCode: 'ICH',
        subzone: 'dw',
        variant: '1',
        phase: null,
        label: 'ICHdw1',
      },
    ],
  },
  ...overrides,
})

const getHandler = (body: Schedule10Response = doc()) =>
  http.get(URL, () => HttpResponse.json(body))

beforeEach(() => {
  window.localStorage.clear()
  server.use(getHandler())
})

const openPagePanel = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
}

describe('rendering the document', () => {
  test('lists construction pages with their labels verbatim', async () => {
    renderSchedule10()
    expect(await screen.findByText(page().pageLabel)).toBeInTheDocument()
    expect(screen.getByText('Page Summary')).toBeInTheDocument()
  })

  test('preserves the legacy page-label quirks exactly', async () => {
    // A TFL page renders the literal text "TSA: null" and has no space after "TFL:". Normalising
    // either would break parity with the server and the acceptance suite.
    const label = 'Page 2, Period: 2021-05, TSA: null, SB: -, TFL:08'
    server.use(getHandler(doc({ pages: [page({ pageId: 8902, pageLabel: label })] })))
    renderSchedule10()
    expect(await screen.findByText(label, verbatim)).toBeInTheDocument()
  })

  test('renders the empty state rather than an error when there are no pages', async () => {
    server.use(getHandler(doc({ pages: [] })))
    renderSchedule10()
    expect(await screen.findByText('No records found.')).toBeInTheDocument()
  })

  test('fires no request when the working context is missing', async () => {
    const spy = vi.fn()
    server.use(
      http.get(URL, () => {
        spy()
        return HttpResponse.json(doc())
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <RouterProvider router={makeRouter()} />
      </MillYearProvider>,
    )
    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()
  })

  test('sends the working mill and year on the load', async () => {
    let requested = ''
    server.use(
      http.get(URL, ({ request }) => {
        requested = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await screen.findByText(page().pageLabel)
    expect(requested).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(requested).toContain(`year=${String(DEFAULT_YEAR)}`)
  })
})

describe('guard states', () => {
  test.each([
    [
      409,
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
    ],
    [404, 'Schedule not found.'],
    [400, 'Please Select Mill and Reporting Year in the Home Page. '],
  ])('renders the %s detail verbatim and suppresses the content', async (status, detail) => {
    server.use(http.get(URL, () => problemBody(status, detail)))
    renderSchedule10()
    expect(await screen.findByText(detail, verbatim)).toBeInTheDocument()
    expect(screen.queryByText('Page Summary')).not.toBeInTheDocument()
  })

  test('falls back to the generic message when a failure carries no detail', async () => {
    server.use(http.get(URL, () => new HttpResponse(null, { status: 500 })))
    renderSchedule10()
    expect(await screen.findByText('Unable to load Schedule 10.')).toBeInTheDocument()
  })
})

describe('the page panel', () => {
  test('opens on Edit and seeds the stored values', async () => {
    renderSchedule10()
    await openPagePanel()
    expect(await screen.findByDisplayValue('North Division')).toBeInTheDocument()
    expect(screen.getByDisplayValue('2021-06')).toBeInTheDocument()
  })

  test('disables the open page own row actions', async () => {
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('North Division')
    // Edit/Delete/Copy for the row now being edited are greyed, which is how the legacy screen
    // marks which page is open.
    expect(screen.getByRole('button', { name: 'Edit' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Copy' })).toBeDisabled()
  })

  test('renders a derived road group read-only, and blank when it is absent', async () => {
    server.use(getHandler(doc({ pages: [page({ roadGroup: null })] })))
    renderSchedule10()
    await openPagePanel()
    expect(await screen.findByText('Road Group:')).toBeInTheDocument()
    // An unmapped location is a saved state, not a failure.
    expect(screen.queryByText(/road group.*(error|invalid)/i)).not.toBeInTheDocument()
  })

  test('creates a page and sends the body with mill and year', async () => {
    let body: Record<string, unknown> | null = null
    let requested = ''
    server.use(
      http.post(PAGES_URL, async ({ request }) => {
        requested = request.url
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({
          ...doc(),
          message: { key: 'k', text: 'Data saved successfully' },
        })
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }))

    await userEvent.type(await screen.findByLabelText('Division'), 'New Division')
    await userEvent.click(screen.getByRole('combobox', { name: 'Region' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Northern Interior' }))
    await userEvent.click(screen.getByRole('combobox', { name: 'TSA or TFL' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Arrow TSA' }))

    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])

    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    expect(requested).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(body).toMatchObject({
      forestRegionCode: 'RNI',
      tsaOrTfl: '01',
      divisionName: 'New Division',
      tflNumberCode: null,
    })
    expect(body).not.toHaveProperty('revisionCount')
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('updates a page carrying its own revision count', async () => {
    let body: Record<string, unknown> | null = null
    server.use(
      http.put(`${PAGES_URL}/8900`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({
          ...doc(),
          message: { key: 'k', text: 'Data saved successfully' },
        })
      }),
    )
    renderSchedule10()
    await openPagePanel()
    const division = await screen.findByDisplayValue('North Division')
    await userEvent.clear(division)
    await userEvent.type(division, 'Edited Division')
    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])

    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    expect(body).toMatchObject({ divisionName: 'Edited Division', revisionCount: 2 })
  })

  test('copies a page without a confirmation', async () => {
    const spy = vi.fn()
    server.use(
      http.post(`${PAGES_URL}/8900/copy`, () => {
        spy()
        return HttpResponse.json({
          ...doc(),
          message: { key: 'k', text: 'Data saved successfully' },
        })
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Copy' }))
    await waitFor(() => {
      expect(spy).toHaveBeenCalledTimes(1)
    })
  })

  test('blocks a save that fails advisory validation, issuing no request', async () => {
    const spy = vi.fn()
    server.use(
      http.post(PAGES_URL, () => {
        spy()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }))
    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])

    expect(await screen.findByText('Region is required.')).toBeInTheDocument()
    expect(screen.getByText('TSA or TFL is required.')).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()
  })

  test('keeps entered values and shows the verbatim detail when the server rejects', async () => {
    server.use(
      http.put(`${PAGES_URL}/8900`, () =>
        problemBody(400, 'Division must be 20 characters or fewer.'),
      ),
    )
    renderSchedule10()
    await openPagePanel()
    const division = await screen.findByDisplayValue('North Division')
    await userEvent.clear(division)
    await userEvent.type(division, 'Kept Division')
    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])

    expect(await screen.findByText('Division must be 20 characters or fewer.')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Kept Division')).toBeInTheDocument()
  })
})

describe('the TSA and TFL branches', () => {
  test('choosing TFL disables the supply block and enables the TFL number', async () => {
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('North Division')

    expect(screen.getByLabelText('TFL')).toBeDisabled()

    await userEvent.click(screen.getByRole('combobox', { name: 'TSA or TFL' }))
    await userEvent.click(await screen.findByRole('option', { name: 'TFL' }))

    expect(screen.getByLabelText('TFL')).toBeEnabled()
    expect(screen.getByRole('combobox', { name: 'Supply Block' })).toBeDisabled()
  })

  test('sends only the branch in use', async () => {
    let body: Record<string, unknown> | null = null
    server.use(
      http.put(`${PAGES_URL}/8900`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('North Division')

    await userEvent.click(screen.getByRole('combobox', { name: 'TSA or TFL' }))
    await userEvent.click(await screen.findByRole('option', { name: 'TFL' }))
    await userEvent.type(screen.getByLabelText('TFL'), '08')
    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])

    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    expect(body).toMatchObject({ tsaOrTfl: 'TFL', tflNumberCode: '08', supplyBlock: null })
  })

  test('narrows the supply blocks to the chosen TSA', async () => {
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('North Division')
    await userEvent.click(screen.getByRole('combobox', { name: 'Supply Block' }))
    expect(await screen.findByRole('option', { name: 'Arrow TSA Block A' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Lakes TSA Block G' })).not.toBeInTheDocument()
  })
})

describe('deleting', () => {
  test('deletes a page after the confirmation', async () => {
    const spy = vi.fn()
    server.use(
      http.delete(`${PAGES_URL}/8900`, () => {
        spy()
        return HttpResponse.json({
          ...doc({ pages: [] }),
          message: { key: 'k', text: 'Data deleted successfully' },
        })
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(
      await screen.findByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => {
      expect(spy).toHaveBeenCalledTimes(1)
    })
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('cancelling the confirmation issues no request and leaves the row', async () => {
    const spy = vi.fn()
    server.use(
      http.delete(`${PAGES_URL}/8900`, () => {
        spy()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    await userEvent.click(await screen.findByRole('button', { name: 'No' }))

    expect(spy).not.toHaveBeenCalled()
    expect(screen.getByText(page().pageLabel)).toBeInTheDocument()
  })
})

describe('the road level', () => {
  test('confirms before leaving the open page panel, then navigates', async () => {
    const { router } = renderSchedule10()
    await openPagePanel()
    await userEvent.click(await screen.findByRole('button', { name: 'Enter Road Data (1)' }))

    expect(
      await screen.findByText(
        'Any unsaved data will be lost. Are you sure you would like to continue?',
      ),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => {
      expect(router.state.location.search).toMatchObject({ pageId: 8900 })
    })
    expect(await screen.findByText('Road #1, Mainline A')).toBeInTheDocument()
  })

  test('cancelling the confirmation keeps the form open with its values', async () => {
    const { router } = renderSchedule10()
    await openPagePanel()
    const division = await screen.findByDisplayValue('North Division')
    await userEvent.clear(division)
    await userEvent.type(division, 'Unsaved edit')
    await userEvent.click(screen.getByRole('button', { name: 'Enter Road Data (1)' }))
    await userEvent.click(await screen.findByRole('button', { name: 'No' }))

    expect(router.state.location.search).toEqual({})
    expect(screen.getByDisplayValue('Unsaved edit')).toBeInTheDocument()
  })

  test('an unknown page id falls back to the page list', async () => {
    renderSchedule10('/schedule-10?pageId=999999')
    expect(await screen.findByText('Page Summary')).toBeInTheDocument()
  })

  test('creates a road detail against its page', async () => {
    let body: Record<string, unknown> | null = null
    let requested = ''
    server.use(
      http.post(`${PAGES_URL}/8900/road-details`, async ({ request }) => {
        requested = request.url
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({
          ...doc(),
          message: { key: 'k', text: 'Data saved successfully' },
        })
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }))

    await userEvent.type(await screen.findByLabelText('Road Name'), 'New Road')
    await userEvent.click(screen.getByRole('combobox', { name: 'Road Type' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Permanent' }))
    await userEvent.click(screen.getByRole('combobox', { name: 'BEC Zone' }))
    await userEvent.click(await screen.findByRole('option', { name: 'ICHdw1' }))
    await userEvent.click(screen.getByRole('combobox', { name: 'RSMR Class' }))
    await userEvent.click(await screen.findByRole('option', { name: '1 - Very Dry' }))
    await userEvent.click(screen.getByRole('combobox', { name: 'Ballast Method Code' }))
    await userEvent.click(await screen.findByRole('option', { name: 'None' }))

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    expect(requested).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(body).toMatchObject({
      roadName: 'New Road',
      roadLifetimeCode: 'P',
      becbiogeoCatalogueId: 8801,
      relSoilMoistRgmClsCode: '1',
    })
  })

  test('updates a road detail with its own revision count, not its page one', async () => {
    let body: Record<string, unknown> | null = null
    server.use(
      http.put(`${PAGES_URL}/8900/road-details/8910`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const roadName = await screen.findByDisplayValue('Mainline A')
    await userEvent.clear(roadName)
    await userEvent.type(roadName, 'Renamed Road')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    // The page's own token is 2; the detail carries 3.
    expect(body).toMatchObject({ roadName: 'Renamed Road', revisionCount: 3 })
  })

  test('copies the sub-grade surface width into the stabilizing width', async () => {
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const subGradeWidth = await screen.findByLabelText('Sub-Grade Surface Width')
    await userEvent.clear(subGradeWidth)
    await userEvent.type(subGradeWidth, '7.5')

    expect(screen.getByLabelText('Additional Stabilizing Surface Width')).toHaveValue('7.5')
  })

  test('seeds numeric fields through their legacy display mask', async () => {
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    // Served as 12.5 and 3 after JSON.parse; the mask restores the decimals legacy showed.
    expect(await screen.findByLabelText('Sub-Grade Length')).toHaveValue('12.500')
    expect(screen.getByLabelText('Additional Stabilizing Length')).toHaveValue('3.000')
  })

  test('blocks an invalid road save and issues no request', async () => {
    const spy = vi.fn()
    server.use(
      http.post(`${PAGES_URL}/8900/road-details`, () => {
        spy()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Road Name is required.')).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()
  })

  test('Back returns to the page list', async () => {
    const { router } = renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Back' }))
    await waitFor(() => {
      expect(router.state.location.search).toEqual({})
    })
    expect(await screen.findByText('Page Summary')).toBeInTheDocument()
  })
})

describe('check status', () => {
  test('shows the all-met message', async () => {
    server.use(
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
          ],
          pages: [],
        }),
      ),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Check Status' }))
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
  })

  test('lists outstanding lines verbatim, and only from nodes that failed', async () => {
    server.use(
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          pages: [
            {
              pageId: 8900,
              pageNumber: 1,
              pageLabel: page().pageLabel,
              met: false,
              issues: [
                {
                  field: 'divisionName',
                  message: {
                    key: 'missingRequiredFieldMsg',
                    text: `${page().pageLabel} Division: Value Required`,
                  },
                },
              ],
              roadDetails: [
                // A passing road detail is still listed on an issues outcome and must contribute
                // no line.
                {
                  roadDetailId: 8910,
                  rowNumber: 1,
                  roadDetailLabel: 'Road #1, Mainline A',
                  met: true,
                  issues: [],
                },
              ],
            },
          ],
        }),
      ),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Check Status' }))
    expect(
      await screen.findByText(`${page().pageLabel} Division: Value Required`, verbatim),
    ).toBeInTheDocument()
    expect(
      screen.queryByText('All requirements for this schedule have been met'),
    ).not.toBeInTheDocument()
  })

  test('issues one request for a double click', async () => {
    const spy = vi.fn()
    let release: (() => void) | null = null
    server.use(
      http.post(CHECK_URL, async () => {
        spy()
        // Held open explicitly rather than on a timer, so the second click is guaranteed to land
        // while the first request is still in flight however loaded the run is.
        await new Promise<void>((resolve) => {
          release = resolve
        })
        return HttpResponse.json({ outcome: 'MET', messages: [], pages: [] })
      }),
    )
    renderSchedule10()
    const button = await screen.findByRole('button', { name: 'Check Status' })
    await userEvent.click(button)
    await waitFor(() => {
      expect(spy).toHaveBeenCalledTimes(1)
    })
    await userEvent.click(button)
    expect(spy).toHaveBeenCalledTimes(1)

    release?.()
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
  })
})

describe('read-only rendering outside Draft', () => {
  test('keeps every write control rendered but disabled, and offers View', async () => {
    server.use(getHandler(doc({ editable: false, trackStatus: 'S' })))
    renderSchedule10()

    expect(await screen.findByText(page().pageLabel)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Copy' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Check Status' })).toBeDisabled()
  })

  test('View renders values as text with no Save', async () => {
    server.use(getHandler(doc({ editable: false, trackStatus: 'S' })))
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'View' }))

    expect(await screen.findByText('North Division')).toBeInTheDocument()
    expect(screen.queryByLabelText('Division')).not.toBeInTheDocument()
    // The panel drops its own Save; the page-level Save stays rendered and disabled, so a read-only
    // reporter can still see which actions exist.
    const saves = screen.getAllByRole('button', { name: 'Save' })
    expect(saves).toHaveLength(1)
    expect(saves[0]).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Close' })).toBeEnabled()
  })

  test('leaves Back enabled at the road level', async () => {
    server.use(getHandler(doc({ editable: false, trackStatus: 'S' })))
    renderSchedule10('/schedule-10?pageId=8900')
    expect(await screen.findByRole('button', { name: 'Back' })).toBeEnabled()
  })
})

describe('stale context', () => {
  // Switches the working context from inside the tree, the way the Home page does, so the page sees
  // a real mill/year change rather than a remount.
  const ContextSwitch: FC = () => {
    const { setContext } = useMillYear()
    return (
      <button type="button" onClick={() => setContext(999, 2020)}>
        switch context
      </button>
    )
  }

  test('ignores a load that resolves after the mill and year changed', async () => {
    server.use(
      http.get(URL, async ({ request }) => {
        const url = new global.URL(request.url)
        if (url.searchParams.get('millId') === String(DEFAULT_MILL_ID)) {
          // The first context's response lands after the second has been asked for.
          await new Promise((resolve) => setTimeout(resolve, 80))
          return HttpResponse.json(doc({ pages: [page({ pageLabel: 'STALE PAGE' })] }))
        }
        return HttpResponse.json(doc({ pages: [page({ pageLabel: 'FRESH PAGE' })] }))
      }),
    )
    render(
      <MillYearProvider initial={{ millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }}>
        <ContextSwitch />
        <RouterProvider router={makeRouter()} />
      </MillYearProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'switch context' }))

    expect(await screen.findByText('FRESH PAGE')).toBeInTheDocument()
    // The slower first response must never replace the newer context's document.
    await new Promise((resolve) => setTimeout(resolve, 120))
    expect(screen.queryByText('STALE PAGE')).not.toBeInTheDocument()
    expect(screen.getByText('FRESH PAGE')).toBeInTheDocument()
  })
})
