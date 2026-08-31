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
import { getDefaultNormalizer, render, screen, waitFor, within } from '@/test-utils'
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

const fillMinimalRoad = async () => {
  await userEvent.type(await screen.findByLabelText('Road Name'), 'Mainline C')
  await userEvent.click(screen.getByRole('combobox', { name: 'Road Type' }))
  await userEvent.click(await screen.findByRole('option', { name: 'Permanent' }))
  await userEvent.click(screen.getByRole('combobox', { name: 'BEC Zone' }))
  await userEvent.click(await screen.findByRole('option', { name: 'ICHdw1' }))
  await userEvent.click(screen.getByRole('combobox', { name: 'RSMR Class' }))
  await userEvent.click(await screen.findByRole('option', { name: '1 - Very Dry' }))
  await userEvent.click(screen.getByRole('combobox', { name: 'Ballast Method Code' }))
  await userEvent.click(await screen.findByRole('option', { name: 'None' }))
}

describe('rendering the document', () => {
  test('lists construction pages with their labels verbatim', async () => {
    renderSchedule10()
    expect(await screen.findByText(page().pageLabel)).toBeInTheDocument()
    expect(screen.getByText('Page Summary')).toBeInTheDocument()
  })

  // Story 30.3 / #312 Overall 6. `renderIcon` puts an <svg> inside the button and leaves the
  // accessible name as the label text, so a by-name lookup still finds the button AND proves the
  // decorative icon is there — a later edit that drops an icon fails here.
  test('every primary and row action button carries its decorative icon', async () => {
    renderSchedule10()

    // Row-scoped on purpose: the delete-confirm Modal stays mounted while the page is editable,
    // so the document also holds its closed footer's "Delete", which is deliberately icon-free.
    const iconRow = (await screen.findByText(page().pageLabel)).closest('tr') as HTMLElement
    for (const name of [/^edit$/i, /^copy$/i, /^delete$/i]) {
      expect(within(iconRow).getByRole('button', { name }).querySelector('svg')).not.toBeNull()
    }
    for (const name of [/add new page/i, /check status/i]) {
      for (const button of screen.getAllByRole('button', { name })) {
        expect(button.querySelector('svg')).not.toBeNull()
      }
    }
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

  test('renders the derived road group read-only, not as an input', async () => {
    renderSchedule10()
    await openPagePanel()
    // The class is asserted, not just the text. It is what carries the typography that makes this
    // <span> read like the six Carbon labels beside it, and that typography comes from TWO
    // stylesheets (#366): `label-01` metrics in schedule10/index.scss, and the size from the
    // `.schedule-page` rule in styles/index.scss, which sets every label on the page to 0.875rem
    // and which a hand-rolled <span> only picks up by being named in it.
    //
    // This one line is ALL the automated protection that fix has, deliberately. A source-level SCSS
    // tripwire was written for it and then deleted: it could not have caught #366 (the original rule
    // existed and looked fine — its number was wrong relative to another file), and the review of it
    // found three of its assertions could not fail at all. A 2px label is caught by the next person
    // who opens the page, which is how this was found and how the fix was signed off. What no eye
    // catches is a rename here, silently detaching the span from both rules — so that is what this
    // pins, and it is the half of the contract jsdom can actually see.
    expect(await screen.findByText('Road Group')).toHaveClass('schedule-10__field-label')
    expect(screen.getByText('11')).toBeInTheDocument()
    // Derived values render as TEXT so a screen reader announces a value, not a dead control.
    expect(screen.queryByLabelText('Road Group')).not.toBeInTheDocument()
  })

  test('renders an absent road group as blank, never as an error', async () => {
    server.use(getHandler(doc({ pages: [page({ roadGroup: null })] })))
    renderSchedule10()
    await openPagePanel()
    // An unmapped location is a saved state, not a failure: the em dash placeholder, no value.
    expect(await screen.findByText('Road Group')).toBeInTheDocument()
    expect(screen.queryByText('11')).not.toBeInTheDocument()
  })

  test('blanks the road group as soon as the location is edited', async () => {
    // The group on screen was derived by the server from the location as STORED; editing the
    // location makes it stale, and a stale group is worse than none (review M4).
    renderSchedule10()
    await openPagePanel()
    expect(await screen.findByText('11')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox', { name: 'TSA or TFL' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Lakes TSA' }))

    await waitFor(() => {
      expect(screen.queryByText('11')).not.toBeInTheDocument()
    })
    expect(screen.getByText('Road Group')).toBeInTheDocument()
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
    await userEvent.click(await screen.findByRole('button', { name: 'Add New Page' }))

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
    await userEvent.click(await screen.findByRole('button', { name: 'Add New Page' }))
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

  test('still shows a stored supply block that does not belong to its TSA', async () => {
    // Real pages carry such pairs; the narrowing must not blank a field that holds a value.
    server.use(getHandler(doc({ pages: [page({ tsaNumber: '16', tsbNumberCode: '01A' })] })))
    renderSchedule10()
    await openPagePanel()
    expect(await screen.findByDisplayValue('Arrow TSA Block A')).toBeInTheDocument()
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
    await userEvent.click(await screen.findByRole('button', { name: 'Add Road' }))

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
    const subGradeWidth = await screen.findByLabelText('Sub-Grade Surface Width (m)')
    await userEvent.clear(subGradeWidth)
    await userEvent.type(subGradeWidth, '7.5')

    expect(screen.getByLabelText('Additional Stabilizing Surface Width (m)')).toHaveValue('7.5')
  })

  test('seeds numeric fields through their legacy display mask', async () => {
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    // Served as 12.5 and 3 after JSON.parse; the mask restores the decimals legacy showed.
    expect(await screen.findByLabelText('Sub-Grade Length (km)')).toHaveValue('12.500')
    expect(screen.getByLabelText('Additional Stabilizing Length (km)')).toHaveValue('3.000')
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
    await userEvent.click(await screen.findByRole('button', { name: 'Add Road' }))
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
    expect(screen.getByRole('button', { name: 'Add New Page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Copy' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Check Status' })).toBeDisabled()
  })

  test('View renders values as text and keeps Save rendered but disabled', async () => {
    server.use(getHandler(doc({ editable: false, trackStatus: 'S' })))
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'View' }))

    expect(await screen.findByText('North Division')).toBeInTheDocument()
    expect(screen.queryByLabelText('Division')).not.toBeInTheDocument()
    // AC11 and deviation 7: DISABLED, never removed. Removing it left a screen reader with no
    // evidence the action exists, and contradicted the AC this page inherited from Story 12.3.
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Close' })).toBeEnabled()
  })

  test('keeps the road-level Save rendered but disabled', async () => {
    server.use(getHandler(doc({ editable: false, trackStatus: 'S' })))
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'View' }))

    expect(await screen.findByText('Mainline A')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Add Road' })).toBeDisabled()
  })

  test('leaves Back enabled at the road level', async () => {
    server.use(getHandler(doc({ editable: false, trackStatus: 'S' })))
    renderSchedule10('/schedule-10?pageId=8900')
    expect(await screen.findByRole('button', { name: 'Back' })).toBeEnabled()
  })
})

describe('every endpoint carries the working context (guardrail R12)', () => {
  // Guardrail R12: MSW matches a path regardless of its query string, so a handler that does not
  // INSPECT request.url passes while the real backend 400s the write. Only 2 of the 9 endpoints were
  // asserted; this pins all of them in one place so a dropped `&year=` cannot pass again.
  const expectContext = (url: string | null) => {
    expect(url).not.toBeNull()
    const params = new global.URL(url as string).searchParams
    expect(params.get('millId')).toBe(String(DEFAULT_MILL_ID))
    expect(params.get('year')).toBe(String(DEFAULT_YEAR))
  }

  test('GET the document', async () => {
    let seen: string | null = null
    server.use(
      http.get(URL, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await screen.findByText(page().pageLabel)
    expectContext(seen)
  })

  test('POST a page', async () => {
    let seen: string | null = null
    server.use(
      http.post(PAGES_URL, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Add New Page' }))
    await userEvent.click(screen.getByRole('combobox', { name: 'Region' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Northern Interior' }))
    await userEvent.click(screen.getByRole('combobox', { name: 'TSA or TFL' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Arrow TSA' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => {
      expectContext(seen)
    })
  })

  test('PUT a page', async () => {
    let seen: string | null = null
    server.use(
      http.put(`${PAGES_URL}/8900`, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('North Division')
    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])
    await waitFor(() => {
      expectContext(seen)
    })
  })

  test('POST a page copy', async () => {
    let seen: string | null = null
    server.use(
      http.post(`${PAGES_URL}/8900/copy`, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Copy' }))
    await waitFor(() => {
      expectContext(seen)
    })
  })

  test('DELETE a page', async () => {
    let seen: string | null = null
    server.use(
      http.delete(`${PAGES_URL}/8900`, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc({ pages: [] }))
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Yes' }))
    await waitFor(() => {
      expectContext(seen)
    })
  })

  test('POST a road detail', async () => {
    let seen: string | null = null
    server.use(
      http.post(`${PAGES_URL}/8900/road-details`, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Add Road' }))
    await fillMinimalRoad()
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => {
      expectContext(seen)
    })
  })

  test('PUT a road detail', async () => {
    let seen: string | null = null
    server.use(
      http.put(`${PAGES_URL}/8900/road-details/8910`, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    await screen.findByDisplayValue('Mainline A')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => {
      expectContext(seen)
    })
  })

  test('DELETE a road detail', async () => {
    let seen: string | null = null
    server.use(
      http.delete(`${PAGES_URL}/8900/road-details/8910`, ({ request }) => {
        seen = request.url
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Yes' }))
    await waitFor(() => {
      expectContext(seen)
    })
  })

  test('POST check status', async () => {
    let seen: string | null = null
    server.use(
      http.post(CHECK_URL, ({ request }) => {
        seen = request.url
        return HttpResponse.json({ outcome: 'MET', messages: [], pages: [] })
      }),
    )
    renderSchedule10()
    await userEvent.click(await screen.findByRole('button', { name: 'Check Status' }))
    await waitFor(() => {
      expectContext(seen)
    })
  })
})

describe('regressions from the 2026-08-19 code review', () => {
  test('H1 — the unsaved-changes confirmation actually discards the page edit', async () => {
    // Confirming "Any unsaved data will be lost" then coming back must NOT find the edit still in
    // the panel: Save would then write it against a freshly re-read revisionCount, so the lock
    // passes and data the user was told was discarded is persisted.
    renderSchedule10()
    await openPagePanel()
    const division = await screen.findByDisplayValue('North Division')
    await userEvent.clear(division)
    await userEvent.type(division, 'DISCARDED')

    await userEvent.click(screen.getByRole('button', { name: 'Enter Road Data (1)' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Yes' }))
    await screen.findByRole('button', { name: 'Add Road' })

    await userEvent.click(screen.getByRole('button', { name: 'Back' }))
    await screen.findByText('Page Summary')
    expect(screen.queryByDisplayValue('DISCARDED')).not.toBeInTheDocument()
  })

  test('H2 — a stored supply block absent from the catalogue still renders', async () => {
    // Page 8904 of delivery stores TSB `16Z`, which the code table no longer serves. Filtering the
    // SERVED list for it yielded [] and the field rendered blank over a real value.
    server.use(getHandler(doc({ pages: [page({ tsaNumber: '16', tsbNumberCode: '16Z' })] })))
    renderSchedule10()
    await openPagePanel()
    expect(await screen.findByDisplayValue('16Z')).toBeInTheDocument()
  })

  test('H3 — re-selecting the SAME TSA keeps a cross-TSA block', async () => {
    // The clear was gated on the block not matching the TSA prefix, with no check that the TSA had
    // changed, so merely touching the control dropped a stored cross-TSA pair.
    let body: unknown = null
    server.use(
      getHandler(doc({ pages: [page({ tsaNumber: '16', tsbNumberCode: '01A' })] })),
      http.put(`${PAGES_URL}/8900`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('Arrow TSA Block A')

    await userEvent.click(screen.getByRole('combobox', { name: 'TSA or TFL' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Lakes TSA' }))
    expect(screen.getByDisplayValue('Arrow TSA Block A')).toBeInTheDocument()

    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])
    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    expect(body).toMatchObject({ tsaOrTfl: '16', supplyBlock: '01A' })
  })

  test('H3 — clearing the TSA control orphans no supply block', async () => {
    // `''.startsWith` is always true, so clearing the combo left a block with no TSA behind it.
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('Arrow TSA Block A')

    // Carbon's own clear affordance, which is the real path to `onSelect('')`; clearing the text
    // alone leaves the selection intact and fires no change. Scoped to THIS combo — Region and
    // Supply Block render an identical button.
    const tsaCombo = screen.getByRole('combobox', { name: 'TSA or TFL' })
    const clear = within(tsaCombo.closest('.cds--list-box__wrapper') as HTMLElement).getByRole(
      'button',
      { name: /clear selected item/i },
    )
    await userEvent.click(clear)
    await waitFor(() => {
      expect(screen.queryByDisplayValue('Arrow TSA Block A')).not.toBeInTheDocument()
    })
  })

  test('M1 — a page with no lock token says so instead of doing nothing', async () => {
    const spy = vi.fn()
    server.use(
      getHandler(doc({ pages: [page({ revisionCount: null as unknown as number })] })),
      http.put(`${PAGES_URL}/8900`, () => {
        spy()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10()
    await openPagePanel()
    await screen.findByDisplayValue('North Division')
    await userEvent.click(screen.getAllByRole('button', { name: 'Save' })[0])

    expect(
      await screen.findByText(
        'This schedule was changed by another user. Please reload and try again.',
      ),
    ).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()
  })

  test('M11 — editing a page field clears a stale check-status result', async () => {
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
    await screen.findByText('All requirements for this schedule have been met')

    await openPagePanel()
    await userEvent.type(await screen.findByDisplayValue('North Division'), 'x')
    await waitFor(() => {
      expect(
        screen.queryByText('All requirements for this schedule have been met'),
      ).not.toBeInTheDocument()
    })
  })

  test('M13 — a road detail is deleted after the confirmation', async () => {
    const spy = vi.fn()
    server.use(
      http.delete(`${PAGES_URL}/8900/road-details/8910`, () => {
        spy()
        return HttpResponse.json({
          ...doc({ pages: [page({ roadDetails: [], roadDetailCount: 0 })] }),
          message: { key: 'k', text: 'Data deleted successfully' },
        })
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
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

  test('M13 — cancelling a road-detail delete issues no request', async () => {
    const spy = vi.fn()
    server.use(
      http.delete(`${PAGES_URL}/8900/road-details/8910`, () => {
        spy()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    await userEvent.click(await screen.findByRole('button', { name: 'No' }))

    expect(spy).not.toHaveBeenCalled()
    expect(screen.getByText('Road #1, Mainline A')).toBeInTheDocument()
  })

  test('L6 — leaving the road level dismisses an open delete confirmation', async () => {
    // A confirmation left mounted across a level change sat over the page list, and its Yes did
    // nothing at all: confirmDelete needs the road level's pageId.
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    await screen.findByText('This will delete the current record. Do you want to continue?')

    await userEvent.click(screen.getByRole('button', { name: 'Back' }))
    await screen.findByText('Page Summary')
    expect(
      screen.queryByText('This will delete the current record. Do you want to continue?'),
    ).not.toBeInTheDocument()
  })

  test('P1 — a road-detail issue names the road it belongs to', async () => {
    // Trap 10: the backend prefixes roadName/subzone with the PAGE label only, so on a multi-road
    // page the flattened line could not say which road was at fault.
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
              issues: [],
              roadDetails: [
                {
                  roadDetailId: 8910,
                  rowNumber: 1,
                  roadDetailLabel: 'Road #1, Mainline A',
                  met: false,
                  issues: [
                    {
                      field: 'roadName',
                      message: {
                        key: 'missingRequiredFieldMsg',
                        text: 'Road Name: Value Required',
                      },
                    },
                  ],
                },
                {
                  roadDetailId: 8911,
                  rowNumber: 2,
                  roadDetailLabel: 'Road #2, Spur B',
                  met: false,
                  issues: [
                    {
                      field: 'subzone',
                      message: { key: 'missingRequiredFieldMsg', text: 'Subzone: Value Required' },
                    },
                  ],
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
      await screen.findByText('Road #1, Mainline A: Road Name: Value Required'),
    ).toBeInTheDocument()
    expect(screen.getByText('Road #2, Spur B: Subzone: Value Required')).toBeInTheDocument()
  })

  test('P4 — the BEC autocomplete matches on a prefix, not a substring (AC5)', async () => {
    server.use(
      getHandler(
        doc({
          codeLists: {
            ...doc().codeLists,
            becClassifications: [
              {
                biogeoclimaticCatalogueId: 8801,
                becZoneCode: 'ICH',
                subzone: 'dw',
                variant: '1',
                phase: null,
                label: 'ICHdw1',
              },
              {
                biogeoclimaticCatalogueId: 8802,
                becZoneCode: 'SBS',
                subzone: 'mk',
                variant: '1',
                phase: null,
                label: 'SBSmk1',
              },
              {
                biogeoclimaticCatalogueId: 8803,
                becZoneCode: 'ESSF',
                subzone: 'wc',
                variant: '3',
                phase: null,
                label: 'ESSFwcICH',
              },
            ],
          },
        }),
      ),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const bec = await screen.findByRole('combobox', { name: 'BEC Zone' })
    await userEvent.clear(bec)
    await userEvent.type(bec, 'ICH')

    expect(await screen.findByRole('option', { name: 'ICHdw1' })).toBeInTheDocument()
    // A substring match would offer this too — the zone merely CONTAINS `ICH`.
    expect(screen.queryByRole('option', { name: 'ESSFwcICH' })).not.toBeInTheDocument()
  })

  test('P2 — ballast method N disables Material Type and sends the coerced figures', async () => {
    let body: unknown = null
    server.use(
      http.put(`${PAGES_URL}/8900/road-details/8910`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    await screen.findByDisplayValue('Mainline A')

    await userEvent.click(screen.getByRole('combobox', { name: 'Ballast Method Code' }))
    await userEvent.click(await screen.findByRole('option', { name: 'None' }))
    expect(screen.getByRole('combobox', { name: 'Type' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    // `ttTransfer` is deliberately NOT zeroed — the server keeps it on the `N` branch (Trap 8).
    expect(body).toMatchObject({
      stabilizing: {
        ballastMethodCode: 'N',
        ballastMaterialCode: 'NA',
        length: 0,
        surfaceWidth: 0,
        depth: 0,
        distanceToSource: 0,
        actualCost: 0,
        otherTransfer: 0,
      },
    })
  })

  test('review #325 — method N disables the figures it discards, but not TtT Transfer', async () => {
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    await screen.findByDisplayValue('Mainline A')

    // Before choosing N every figure is editable.
    expect(screen.getByLabelText('Additional Stabilizing Length (km)')).toBeEnabled()

    await userEvent.click(screen.getByRole('combobox', { name: 'Ballast Method Code' }))
    await userEvent.click(await screen.findByRole('option', { name: 'None' }))

    for (const label of [
      'Additional Stabilizing Length (km)',
      'Additional Stabilizing Surface Width (m)',
      'Depth (m)',
      'Distance to Source (km)',
      'Additional Stabilizing Actual Costs ($)',
      'Additional Stabilizing Other Transfer ($)',
    ]) {
      expect(screen.getByLabelText(label)).toBeDisabled()
    }
    // The server keeps this one on the N branch, so entry here is still recorded.
    expect(screen.getByLabelText('Additional Stabilizing TtT Transfer ($)')).toBeEnabled()
  })

  test('review #325 — method D disables only the material, leaving the figures editable', async () => {
    server.use(
      getHandler(
        doc({
          codeLists: {
            ...doc().codeLists,
            ballastMethods: [
              { code: 'C', description: 'Crushed' },
              { code: 'N', description: 'None' },
              { code: 'D', description: 'Dirt' },
            ],
          },
        }),
      ),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    await screen.findByDisplayValue('Mainline A')

    await userEvent.click(screen.getByRole('combobox', { name: 'Ballast Method Code' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Dirt' }))

    expect(screen.getByRole('combobox', { name: 'Type' })).toBeDisabled()
    // D stores its figures as submitted, so they must stay editable.
    expect(screen.getByLabelText('Additional Stabilizing Length (km)')).toBeEnabled()
    expect(screen.getByLabelText('Additional Stabilizing Actual Costs ($)')).toBeEnabled()
  })

  test('M7 — a de-listed BEC classification renders its label, not its catalogue id', async () => {
    server.use(
      getHandler(
        doc({
          codeLists: { ...doc().codeLists, becClassifications: [] },
        }),
      ),
    )
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    // The row's own becClassification carries the label even when the offerable list does not.
    expect(await screen.findByDisplayValue('ICHdw1')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('8801')).not.toBeInTheDocument()
  })

  test('M12 — derived money cells follow the legacy masks', async () => {
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    await screen.findByDisplayValue('Mainline A')
    // mask.int.7digits (#,###,##0) is a WHOLE-dollar mask: the integer totals carry no decimals.
    expect(screen.getAllByText('150,000').length).toBeGreaterThan(0)
    expect(screen.queryByText('150,000.00')).not.toBeInTheDocument()
  })

  test('L5 — the material hint stays quiet until a ballast method is chosen', async () => {
    const hint = 'A material Type is required for this Additional Stabilizing code.'
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Add Road' }))
    await screen.findByRole('combobox', { name: 'Ballast Method Code' })
    // A BLANK code lands in the `C` branch server-side, so the predicate is true for it — but the
    // reporter has chosen nothing yet and has nothing to correct.
    expect(screen.queryByText(hint)).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox', { name: 'Ballast Method Code' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Crushed' }))
    expect(await screen.findByText(hint)).toBeInTheDocument()
  })

  test('L9 — the road form meets the accessibility floor (AC15)', async () => {
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    await screen.findByDisplayValue('Mainline A')

    // Every input is reachable by its programmatic label, never by placeholder or position.
    expect(screen.getByLabelText('Road Name')).toBeInTheDocument()
    expect(screen.getByLabelText('Sub-Grade Length (km)')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'BEC Zone' })).toBeInTheDocument()
    // Both tables carry real header cells so a screen reader can navigate them.
    expect(screen.getByRole('columnheader', { name: 'Roads' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Action' })).toBeInTheDocument()
    // Derived totals are text, not disabled inputs, so they are announced as values.
    expect(screen.queryByLabelText('Total ($)')).not.toBeInTheDocument()
  })

  test('L9 — an advisory error is bound to its field and clears as it is fixed', async () => {
    renderSchedule10('/schedule-10?pageId=8900')
    await userEvent.click(await screen.findByRole('button', { name: 'Add Road' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    const roadName = await screen.findByLabelText('Road Name')
    expect(await screen.findByText('Road Name is required.')).toBeInTheDocument()
    expect(roadName).toHaveAttribute('aria-invalid', 'true')

    await userEvent.type(roadName, 'Mainline C')
    await waitFor(() => {
      expect(screen.queryByText('Road Name is required.')).not.toBeInTheDocument()
    })
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
