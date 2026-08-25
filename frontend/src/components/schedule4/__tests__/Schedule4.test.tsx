import { vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
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
import Schedule4 from '@/components/schedule4'
import { Route as realScheduleRoute } from '@/routes/schedule-4'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import type { Location } from '@/interfaces/Schedule4Response'

// Schedule 4's sub-page level is URL-driven (search: loc + sub), so render it inside a REAL memory
// router — the component's route search hooks + navigation (and the browser Back button) need router
// context. `makeRouter` mirrors the app's /schedule-4 route (validateSearch); tests that need the
// browser Back button build the router directly to reach `router.history`.
function makeRouter(initialUrl = '/schedule-4') {
  const rootRoute = createRootRoute()
  const scheduleRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/schedule-4',
    validateSearch: realScheduleRoute.options.validateSearch,
    component: Schedule4,
  })
  return createRouter({
    routeTree: rootRoute.addChildren([scheduleRoute]),
    history: createMemoryHistory({ initialEntries: [initialUrl] }),
  })
}

const renderSchedule4 = (initialUrl = '/schedule-4') =>
  render(<RouterProvider router={makeRouter(initialUrl)} />)

// Schedule 4 renders its action bar twice (defect #293): Add New Location + Check Status at the top,
// Check Status alone at the foot. Both buttons share an accessible name, so address the bars by their
// marker rather than by document position — `getAllByRole(...)[index]` silently retargets if either bar
// moves or disappears, which a mutation run proved could hide the loss of the top button entirely
// (review 2026-08-24).
const topActions = () => within(screen.getByTestId('schedule-4-top-actions'))
const bottomActions = () => within(screen.getByTestId('schedule-4-bottom-actions'))
const bottomCheckStatus = () => bottomActions().getByRole('button', { name: /check status/i })
const checkStatusButtons = () => screen.getAllByRole('button', { name: /check status/i })

const URL = 'http://localhost:3000/api/v1/schedule4'
const LOCATIONS_URL = 'http://localhost:3000/api/v1/schedule4/locations'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule4/check-status'

const harbour: Location = {
  id: 7001,
  revisionCount: 0,
  name: 'Harbour Dump',
  comments: 'Harbour dock notes',
  categories: [
    { code: 40, kind: 'FIXED', volume: 2000, cost: 100000, distance: null, perUnit: 50.0 },
    { code: 47, kind: 'DISTANCE', volume: 500, cost: 25000, distance: 120.5, perUnit: 50.0 },
  ],
  subPageRows: [
    {
      id: 7013,
      code: 43,
      description: 'Deferred towing row',
      distance: 50,
      volume: 999,
      cost: 99999,
      cycle: null,
      perUnit: null,
    },
  ],
}
const emptyLanding: Location = {
  id: 7002,
  revisionCount: 0,
  name: 'Empty Landing',
  categories: [],
  subPageRows: [],
}

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  locations: [harbour, emptyLanding],
  ...overrides,
})

describe('Schedule4 page', () => {
  test('lists existing locations (name + actions); Add New Location enabled (editable)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()

    expect(await screen.findByText('Harbour Dump')).toBeInTheDocument()
    expect(screen.getByText('Empty Landing')).toBeInTheDocument()
    // Add New Location enabled in an editable (Draft) context.
    expect(screen.getByRole('button', { name: /add new location/i })).toBeEnabled()
    // Row actions include Edit (not View) when editable.
    expect(screen.getAllByRole('button', { name: /^edit$/i }).length).toBeGreaterThan(0)
  })

  test('Add New Location opens the category-grid panel with editable inputs', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))

    expect(screen.getByText('New Location')).toBeInTheDocument()
    expect(screen.getByLabelText('Location Name')).toHaveValue('')
    // The fixed + distance category inputs are present.
    expect(screen.getByLabelText('Lakeside Dry Dump volume')).toBeInTheDocument()
    expect(screen.getByLabelText('Truck Barge/Ferry distance')).toBeInTheDocument()
  })

  test('save a new location PUTs and shows the API success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        ),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await userEvent.type(screen.getByLabelText('Location Name'), 'New Dump')
    await userEvent.type(screen.getByLabelText('Lakeside Dry Dump cost'), '5000')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('after creating a location with category data, Edit shows the saved amounts (round-trip)', async () => {
    const created: Location = {
      id: 9001,
      revisionCount: 1,
      name: 'New Dump',
      comments: null,
      categories: [
        { code: 40, kind: 'FIXED', volume: 2000, cost: 5000, distance: null, perUnit: 2.5 },
      ],
      subPageRows: [],
    }
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({
            locations: [harbour, emptyLanding, created],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        ),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await userEvent.type(screen.getByLabelText('Location Name'), 'New Dump')
    await userEvent.type(screen.getByLabelText('Lakeside Dry Dump cost'), '5000')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))
    await screen.findByText('Data saved successfully')

    // Re-open the newly created location — its saved amounts must be seeded into the panel.
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[2])
    // Editable numbers display thousands-grouped (commas).
    expect(screen.getByLabelText('Lakeside Dry Dump cost')).toHaveValue('5,000')
    expect(screen.getByLabelText('Lakeside Dry Dump volume')).toHaveValue('2,000')
  })

  test('Edit seeds the location comments; Save sends the edited comments in the PUT', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    // Edit the first row (Harbour Dump) — the panel seeds its stored comments.
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    const comments = screen.getByLabelText(
      'If you have any additional comments, please enter them here:',
    )
    expect(comments).toHaveValue('Harbour dock notes')

    await userEvent.clear(comments)
    await userEvent.type(comments, 'Updated dock notes')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect((captured as { comments: string }).comments).toBe('Updated dock notes')
  })

  test('Save stays on the record — the edit panel stays open and its row is highlighted', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        ),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    expect(screen.getByText('Edit Location')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // Panel stays open on the same record (not closed) …
    expect(screen.getByText('Edit Location')).toBeInTheDocument()
    // … and that location's list row is highlighted.
    expect(screen.getByRole('cell', { name: 'Harbour Dump' }).closest('tr')).toHaveClass(
      'schedule-4__row--editing',
    )
  })

  test('blank name blocks save with the verbatim ERR-001 (no PUT fired)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      await screen.findByText('Location Name can not be empty. Please enter a description.'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('Copy opens a prefilled panel with a cleared name and the WRN-001 nudge', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    // The first Copy button is Harbour Dump's row.
    await userEvent.click(screen.getAllByRole('button', { name: /^copy$/i })[0])

    expect(screen.getByText('Copy Location')).toBeInTheDocument()
    expect(screen.getByLabelText('Location Name')).toHaveValue('')
    expect(
      screen.getByText(
        'To complete copy of Location: Harbour Dump, provide a new Location Name and invoke save.',
      ),
    ).toBeInTheDocument()
    // Amounts cloned from the source (thousands-grouped).
    expect(screen.getByLabelText('Lakeside Dry Dump cost')).toHaveValue('100,000')
  })

  // ---- Defect #291: the panel's $/m³ column tracks entry, on blur, before Save. -------------------

  /** A category row's cells as text: [label, dist, volume, cost, $/m³, cycle]. */
  const gridCells = (label: string) => {
    const tr = screen.getByText(`${label}:`).closest('tr')
    if (!tr) throw new Error(`no grid row for "${label}"`)
    return within(tr)
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
  }
  /** The read-only $/m³ cell of a category row (index 4). */
  const rate = (label: string) => gridCells(label)[4]

  test('typing alone leaves $/m³ alone; blurring the cost recalculates it (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])

    // Seeded from the saved amounts: 100000/2000 = 50.00.
    expect(rate('Lakeside Dry Dump')).toBe('50.00')

    const cost = screen.getByLabelText('Lakeside Dry Dump cost')
    await userEvent.clear(cost)
    await userEvent.type(cost, '150000')
    expect(rate('Lakeside Dry Dump')).toBe('50.00') // not per keystroke

    await userEvent.tab()
    expect(rate('Lakeside Dry Dump')).toBe('75.00') // 150000/2000
  })

  test('blurring the volume recalculates $/m³, and only that category (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])

    const volume = screen.getByLabelText('Lakeside Dry Dump volume')
    await userEvent.clear(volume)
    await userEvent.type(volume, '4000')
    await userEvent.tab()

    expect(rate('Lakeside Dry Dump')).toBe('25.00') // 100000/4000
    // The other saved category is untouched, and an empty one stays blank.
    expect(rate('Truck Barge/Ferry')).toBe('50.00') // 25000/500
    expect(rate('Water Dump')).toBe('—')
  })

  test('clearing the volume blanks $/m³ rather than showing Infinity (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])

    const volume = screen.getByLabelText('Lakeside Dry Dump volume')
    await userEvent.clear(volume)
    await userEvent.tab()
    expect(rate('Lakeside Dry Dump')).toBe('—')

    // A zero volume is the divide-by-zero case the server nulls out.
    await userEvent.type(screen.getByLabelText('Lakeside Dry Dump volume'), '0')
    await userEvent.tab()
    expect(rate('Lakeside Dry Dump')).toBe('—')
  })

  test("Copy shows the cloned amounts' $/m³ immediately, without a save (#291)", async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^copy$/i })[0])

    // A copy clones the amounts, so their rate is known — it used to render blank until the first save
    // because the panel captured $/m³ from the server and a copy has no server figure yet.
    expect(screen.getByLabelText('Lakeside Dry Dump cost')).toHaveValue('100,000')
    expect(rate('Lakeside Dry Dump')).toBe('50.00')
    expect(rate('Truck Barge/Ferry')).toBe('50.00')
  })

  test('the Save echo supersedes the mirror, and the rates agree (#291 AC5)', async () => {
    // Schedule 4 had no AC5 test at all, and its panel was never re-seeded from the echo — so
    // `deriveCategoryPerUnits(panelCommitted)` kept driving the column for the rest of the session
    // (code review 2026-08-21). The echo below carries the server's own perUnit for the saved amounts.
    const saved: Location = {
      ...harbour,
      revisionCount: 1,
      categories: [
        { code: 40, kind: 'FIXED', volume: 2000, cost: 150000, distance: null, perUnit: 75.0 },
        { code: 47, kind: 'DISTANCE', volume: 500, cost: 25000, distance: 120.5, perUnit: 50.0 },
      ],
    }
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () =>
        HttpResponse.json({
          ...doc({ locations: [saved, emptyLanding] }),
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])

    const cost = screen.getByLabelText('Lakeside Dry Dump cost')
    await userEvent.clear(cost)
    await userEvent.type(cost, '150000')
    await userEvent.tab()
    // The mirror must already agree with the rate the server will echo: 150000 / 2000 = 75.00.
    expect(rate('Lakeside Dry Dump')).toBe('75.00')

    await userEvent.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    // The panel stays open in edit mode; the column now reflects the echo and still reads 75.00.
    expect(screen.getByLabelText('Lakeside Dry Dump cost')).toHaveValue('150,000')
    expect(rate('Lakeside Dry Dump')).toBe('75.00')
    expect(rate('Truck Barge/Ferry')).toBe('50.00')
  })

  test('View mode renders the document $/m³ as-is — no client recomputation (#291 AC7)', async () => {
    // The stored perUnit deliberately disagrees with the stored pair: a recomputing view would show
    // 50.00 instead of the server's own figure.
    const skewed: Location = {
      ...harbour,
      categories: [
        { code: 40, kind: 'FIXED', volume: 2000, cost: 100000, distance: null, perUnit: 999.99 },
      ],
    }
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ editable: false, locations: [skewed], trackStatus: 'S' })),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^view$/i })[0])

    expect(rate('Lakeside Dry Dump')).toBe('999.99')
  })

  test('Check Status renders the per-location results', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          locations: [
            {
              id: 7001,
              name: 'Harbour Dump',
              met: false,
              messages: [],
              issues: [
                { code: 52, message: { key: 'missingRequiredFieldMsg', text: 'Value Required' } },
              ],
            },
            {
              id: 7002,
              name: 'Empty Landing',
              met: true,
              messages: [
                {
                  key: 'locationRequirementsMetMsg',
                  text: 'All requirements for Empty Landing have been met.',
                },
              ],
              issues: [],
            },
          ],
        }),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(topActions().getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Value Required')).toBeInTheDocument()
    expect(
      screen.getByText('All requirements for Empty Landing have been met.'),
    ).toBeInTheDocument()
  })

  test('editable:false renders View actions and disables Add/Copy/Delete (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    expect(screen.getByRole('button', { name: /add new location/i })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /^view$/i }).length).toBeGreaterThan(0)
    screen.getAllByRole('button', { name: /^copy$/i }).forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /^delete$/i }).forEach((b) => expect(b).toBeDisabled())
  })
})

// Harbour Dump's id is 7001, so its sub-page rows POST/DELETE target .../locations/7001/rows.
const ROWS_7001 = 'http://localhost:3000/api/v1/schedule4/locations/7001/rows'

describe('Schedule4 sub-pages (Story 10.6)', () => {
  // Open Harbour Dump's Towing sub-page: Edit → panel → "Towing Total (1)" → NAV-002 → Continue.
  const openTowing = async () => {
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /Towing Total \(1\)/i }))
    // NAV-002 unsaved-changes confirm.
    expect(
      screen.getByText('Any unsaved data will be lost. Are you sure you would like to continue?'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))
    await screen.findByRole('table', { name: /Towing Total/i })
  }

  test('open a sub-page from a saved location (NAV-002) shows its rows', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openTowing()
    // Rows are inline-editable, so the description is an input value (not static text).
    expect(screen.getByDisplayValue('Deferred towing row')).toBeInTheDocument()
  })

  test('clicking a sortable column header reorders the data rows', async () => {
    const twoRows = () => {
      const clone = doc()
      clone.locations = [
        {
          ...harbour,
          subPageRows: [
            {
              id: 8001,
              code: 43,
              description: 'Zebra row',
              distance: 10,
              volume: 100,
              cost: 900,
              cycle: null,
              perUnit: null,
            },
            {
              id: 8002,
              code: 43,
              description: 'Alpha row',
              distance: 20,
              volume: 200,
              cost: 100,
              cycle: null,
              perUnit: null,
            },
          ],
        },
        emptyLanding,
      ]
      return clone
    }
    server.use(http.get(URL, () => HttpResponse.json(twoRows())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /Towing Total \(2\)/i }))
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))
    await screen.findByRole('table', { name: /Towing Total/i })

    // Rows are inline-editable — read the description inputs' values in DOM order to check row order.
    const descOrder = () =>
      screen
        .getAllByRole('textbox', { name: /description \(row/i })
        .map((el) => (el as HTMLInputElement).value)

    // As-loaded order is Zebra then Alpha; sorting Description ascending flips them.
    expect(descOrder()).toEqual(['Zebra row', 'Alpha row'])
    await userEvent.click(screen.getByRole('button', { name: /^description$/i }))
    expect(descOrder()).toEqual(['Alpha row', 'Zebra row'])
  })

  test('add a row PUTs the sub-resource and shows the API success message', async () => {
    const withRow = () => {
      const clone = doc()
      clone.locations = [
        {
          ...harbour,
          subPageRows: [
            harbour.subPageRows[0],
            {
              id: 9100,
              code: 43,
              description: 'Added Towing',
              distance: 12,
              volume: 5,
              cost: 300,
              cycle: null,
              perUnit: 60,
            },
          ],
        },
        emptyLanding,
      ]
      return clone
    }
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(ROWS_7001, () =>
        HttpResponse.json({
          ...withRow(),
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    await openTowing()

    await userEvent.type(screen.getByLabelText('Description'), 'Added Towing')
    await userEvent.type(screen.getByLabelText('Volume (m³)'), '5')
    await userEvent.click(screen.getByRole('button', { name: /add row/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // The added row renders as an inline-editable input (value), not static text.
    expect(screen.getByDisplayValue('Added Towing')).toBeInTheDocument()
  })

  test('editing an existing row and clicking Save PUTs the changed row', async () => {
    let body: { cost?: unknown; description?: unknown } | undefined
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${ROWS_7001}/7013`, async ({ request }) => {
        body = (await request.json()) as typeof body
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    await openTowing()

    // Edit the existing row's Cost cell inline, then Save (row edits persist only on Save).
    const cost = screen.getByRole('textbox', { name: /cost \$ \(row 7013\)/i })
    await userEvent.clear(cost)
    await userEvent.type(cost, '12345')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() =>
      expect(body).toMatchObject({ cost: 12345, description: 'Deferred towing row' }),
    )
  })

  test('blank description blocks Add with Value Required (no POST)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(ROWS_7001, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    await openTowing()

    await userEvent.click(screen.getByRole('button', { name: /add row/i }))

    expect(await screen.findByText('Value Required')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('delete a row (NAV-005) DELETEs the sub-resource', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete('http://localhost:3000/api/v1/schedule4/locations/7001/rows/7013', () => {
        deleted = true
        const clone = doc()
        clone.locations = [{ ...harbour, subPageRows: [] }, emptyLanding]
        return HttpResponse.json({
          ...clone,
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    await openTowing()

    // Two "Delete" buttons exist (the row's + the always-rendered NAV-005 modal primary). Click the
    // row's [0] to open the confirm, then the modal's primary [last] to submit.
    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deleteButtons[deleteButtons.length - 1])

    await waitFor(() => expect(deleted).toBe(true))
  })

  test('Back returns from a sub-page to the location list', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openTowing()
    expect(screen.getByRole('table', { name: /Towing Total/i })).toBeInTheDocument()

    // The action-bar Back (scoped — the always-rendered delete-confirm modal has a "Cancel").
    const [back] = screen
      .getAllByRole('button', { name: /^back$/i })
      .filter((b) => b.closest('.schedule-4__panel-actions'))
    await userEvent.click(back)

    expect(screen.queryByRole('table', { name: /Towing Total/i })).not.toBeInTheDocument()
    // Back on the list.
    expect(screen.getByRole('button', { name: /add new location/i })).toBeInTheDocument()
  })

  test('the sub-page level is URL-driven; the browser Back button returns to the list', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    // Build the router directly so the test can reach router.history (the browser Back button).
    const router = makeRouter()
    render(<RouterProvider router={router} />)
    await screen.findByText('Harbour Dump')

    // Open Harbour Dump's Towing sub-page (Edit → Towing Total (1) → NAV-002 Continue).
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /Towing Total \(1\)/i }))
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))
    expect(await screen.findByRole('table', { name: /Towing Total/i })).toBeInTheDocument()
    // The level is reflected in the URL search (loc + sub) — refreshable / shareable.
    expect(router.state.location.search).toMatchObject({ loc: 7001, sub: 'TOWING' })

    // Browser Back pops the sub-page entry and returns to the location list.
    router.history.back()
    await waitFor(() =>
      expect(screen.queryByRole('table', { name: /Towing Total/i })).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: /add new location/i })).toBeInTheDocument()
    expect(router.state.location.search).toEqual({})
  })

  test('the in-app Back button replaces history so browser Back does not re-open the sub-page', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    // Build the router directly so the test can reach router.history (the browser Back button).
    const router = makeRouter()
    render(<RouterProvider router={router} />)
    await screen.findByText('Harbour Dump')

    // Open Harbour Dump's Towing sub-page (Edit → Towing Total (1) → NAV-002 Continue) — a history push.
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /Towing Total \(1\)/i }))
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))
    expect(await screen.findByRole('table', { name: /Towing Total/i })).toBeInTheDocument()

    // The in-app Back navigates with replace: true (index.tsx:529), collapsing the sub-page entry
    // into the list rather than pushing a new one.
    const [back] = screen
      .getAllByRole('button', { name: /^back$/i })
      .filter((b) => b.closest('.schedule-4__panel-actions'))
    await userEvent.click(back)
    await waitFor(() =>
      expect(screen.queryByRole('table', { name: /Towing Total/i })).not.toBeInTheDocument(),
    )
    expect(router.state.location.search).toEqual({})

    // Because the in-app Back REPLACED the sub-page entry (not pushed the list on top of it), browser
    // Back skips past the sub-page rather than re-opening it — the intended replace: true behavior.
    router.history.back()
    await waitFor(() => expect(router.state.location.search).toEqual({}))
    expect(screen.queryByRole('table', { name: /Towing Total/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add new location/i })).toBeInTheDocument()
  })
})

describe('Schedule4 context, load + write error, edit, delete and status paths', () => {
  test('missing mill/year shows verbatim ERR-001 and fires NO request (EF2-001)', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
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
    expect(screen.queryByRole('button', { name: /add new location/i })).not.toBeInTheDocument()
  })

  test('a load failure surfaces the default unable-to-load message', async () => {
    server.use(http.get(URL, () => HttpResponse.error()))
    renderSchedule4()

    expect(await screen.findByText('Unable to load Schedule 4.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /add new location/i })).not.toBeInTheDocument()
  })

  test('a save failure keeps the panel open and surfaces the API verbatim detail (ERR-002)', async () => {
    const detail = 'A location with that name already exists.'
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () => HttpResponse.json({ detail }, { status: 409 })),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await userEvent.type(screen.getByLabelText('Location Name'), 'Harbour Dump')
    await userEvent.type(screen.getByLabelText('Lakeside Dry Dump cost'), '5000')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText(detail)).toBeInTheDocument()
    // Panel stays open with entered values (edits not discarded).
    expect(screen.getByText('New Location')).toBeInTheDocument()
  })

  test('Edit an existing location PUTs its id + revisionCount (optimistic lock)', async () => {
    let body: Schedule4LocationRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, async ({ request }) => {
        body = (await request.json()) as Schedule4LocationRequest
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    expect(screen.getByText('Edit Location')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(body).not.toBeNull()
    expect(body!.id).toBe(7001)
    expect(body!.revisionCount).toBe(0)
  })

  test('Delete flow: row → confirm modal → DELETE + re-read drops the family', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(deleted ? doc({ locations: [emptyLanding] }) : doc())),
      http.delete(LOCATIONS_URL, () => {
        deleted = true
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    expect(
      screen.getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Harbour Dump')).not.toBeInTheDocument())
  })

  test('a delete failure surfaces the API verbatim detail', async () => {
    const detail = 'Unable to delete because the schedule is submitted.'
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(LOCATIONS_URL, () => HttpResponse.json({ detail }, { status: 409 })),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deletes = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deletes[deletes.length - 1])

    expect(await screen.findByText(detail)).toBeInTheDocument()
  })

  test('Check Status shows the whole-schedule SUC-006 banner when all pass', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All Schedule 4 requirements have been met.',
            },
          ],
          locations: [],
        }),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(topActions().getByRole('button', { name: /check status/i }))

    expect(
      await screen.findByText('All Schedule 4 requirements have been met.'),
    ).toBeInTheDocument()
  })

  test('a Check Status failure surfaces the API verbatim detail', async () => {
    const detail = 'Unable to evaluate the schedule right now.'
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () => HttpResponse.json({ detail }, { status: 500 })),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(topActions().getByRole('button', { name: /check status/i }))

    expect(await screen.findByText(detail)).toBeInTheDocument()
  })

  test('View opens a read-only panel (no Save) and sub-pages open directly (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getAllByRole('button', { name: /^view$/i })[0])
    expect(screen.getByText('View Location')).toBeInTheDocument()
    // Read-only: the name is plain text, no Save button, category values render as text.
    expect(screen.getByText('Location Name: Harbour Dump')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()

    // A View sub-page opens directly — no NAV-002/003 confirm.
    await userEvent.click(screen.getByRole('button', { name: /Towing Total \(1\)/i }))
    expect(await screen.findByRole('table', { name: /Towing Total/i })).toBeInTheDocument()
    expect(
      screen.queryByText('Any unsaved data will be lost. Are you sure you would like to continue?'),
    ).not.toBeInTheDocument()
  })

  // ---- Defect #293: the page's own bottom Check Status. ------------------------------------------
  // Legacy carried Check Status by itself on a row at the very bottom of the page (schedule4.xhtml:216-222).
  // The bottom bar is Check Status ALONE — no Add New Location — and it is NOT part of the location panel's
  // Save/Back row. Document order puts the top bar first, so the bottom instance is the LAST match.

  test('a bottom Check Status renders below the content and runs the same check (#293)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All Schedule 4 requirements have been met.',
            },
          ],
          locations: [],
        }),
      ),
    )
    renderSchedule4()
    const firstRow = await screen.findByText('Harbour Dump')

    expect(checkStatusButtons()).toHaveLength(2)
    // The bottom bar follows the locations table and is not inside it.
    const table = firstRow.closest('table') as HTMLElement
    expect(table).not.toContainElement(bottomCheckStatus())
    expect(firstRow.compareDocumentPosition(bottomCheckStatus())).toBe(
      window.Node.DOCUMENT_POSITION_FOLLOWING,
    )
    // Check Status alone — Add New Location rides the top bar only.
    expect(screen.getAllByRole('button', { name: /add new location/i })).toHaveLength(1)
    expect(bottomActions().queryByRole('button', { name: /add new location/i })).toBeNull()

    await userEvent.click(bottomCheckStatus())

    expect(
      await screen.findByText('All Schedule 4 requirements have been met.'),
    ).toBeInTheDocument()
  })

  test('a Check Status verdict takes focus, so the result is reached from either bar (#293)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All Schedule 4 requirements have been met.',
            },
          ],
          locations: [],
        }),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    // The verdict renders at the TOP of the page; the bottom button sits at the foot. Focus is what
    // carries the user (and a screen reader) to the result — it replaced a window.scrollTo that moved
    // the viewport but left focus stranded on the off-screen button (PR #353 review).
    await userEvent.click(bottomCheckStatus())

    const verdict = await screen.findByText('All Schedule 4 requirements have been met.')
    const region = verdict.closest('.schedule-4__check')
    expect(region).not.toBeNull()
    await waitFor(() => {
      expect(region).toHaveFocus()
    })
    // Programmatic target only — never in the tab order.
    expect(region).toHaveAttribute('tabindex', '-1')
  })

  test('a FAILED Check Status moves focus to the error banner too (#293)', async () => {
    const detail = 'Unable to evaluate the schedule right now.'
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () => HttpResponse.json({ detail }, { status: 500 })),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    // The failure banner renders in the same top-of-page region, so the failure path must reach it as
    // well — the success path alone would leave a failed check just as invisible as before.
    await userEvent.click(bottomCheckStatus())

    const banner = await screen.findByText(detail)
    await waitFor(() => {
      expect(banner.closest('[tabindex="-1"]')).toHaveFocus()
    })
  })

  test('a Save validation error does NOT steal focus from the field being corrected (#293)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await screen.findByText('New Location')

    // Focus-on-banner is armed by Check Status only. A Save that fails validation must leave focus
    // where the user is typing — yanking it to a banner would be worse than the bug it fixes.
    const nameField = screen.getByLabelText('Location Name')
    await userEvent.click(nameField)
    expect(nameField).toHaveFocus()

    await userEvent.click(
      within(
        screen.getByText('New Location').closest('.schedule-4__panel') as HTMLElement,
      ).getByRole('button', { name: /^save$/i }),
    )

    // The banner DOES render, and it renders in the very region Check Status focuses — so without the
    // `focusVerdict` guard this is exactly where focus would be stolen.
    const banner = await screen.findByText('Please correct the highlighted fields before saving.')
    expect(banner.closest('[tabindex="-1"]')).not.toBeNull()
    expect(document.activeElement).not.toHaveAttribute('tabindex', '-1')
  })

  test('the bottom Check Status is locked while a check is in flight — one POST per click (#293)', async () => {
    let posts = 0
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, async () => {
        posts += 1
        await delay(50)
        return HttpResponse.json({ outcome: 'MET', messages: [], locations: [] })
      }),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    const button = bottomCheckStatus()
    await userEvent.click(button)
    await userEvent.click(button)

    await waitFor(() => {
      expect(button).toBeEnabled()
    })
    expect(posts).toBe(1)
  })

  test('the location panel keeps Save/Back only — the bottom bar sits below it (#293)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    const heading = await screen.findByText('New Location')

    // Still exactly two: opening a panel adds no third Check Status to its own button row.
    expect(checkStatusButtons()).toHaveLength(2)

    // Containment, not document order: `compareDocumentPosition(...) === FOLLOWING` is also true for a
    // button nested INSIDE the panel, so folding the button into schedule-4__panel-actions — the design
    // this fix rejected — passed the old assertion (review 2026-08-24).
    const panel = heading.closest('.schedule-4__panel')
    expect(panel).not.toBeNull()
    expect(panel).not.toContainElement(bottomCheckStatus())
    const panelActions = panel?.querySelector('.schedule-4__panel-actions') as HTMLElement
    expect(within(panelActions).queryByRole('button', { name: /check status/i })).toBeNull()
    expect(within(panelActions).getByRole('button', { name: /^back$/i })).toBeInTheDocument()
    expect(within(panelActions).getByRole('button', { name: /^save$/i })).toBeInTheDocument()
  })

  test('both Check Status buttons are DISABLED outside Draft (DIV-1 / #322)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getAllByRole('button', { name: /^view$/i })[0])
    expect(await screen.findByText('View Location')).toBeInTheDocument()

    // Legacy bound EVERY Check Status instance to disableReportEdits() (schedule4.xhtml:43, :220-221;
    // schedule4NewLocation.xhtml:275; schedule4ExistingLocation.xhtml:1144), and the other seven
    // schedules already carry the `!editable` term. Ratified 2026-08-24: close it here for Schedule 4.
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
    expect(checkStatusButtons()).toHaveLength(2)
    expect(topActions().getByRole('button', { name: /check status/i })).toBeDisabled()
    expect(bottomCheckStatus()).toBeDisabled()
    expect(screen.getByRole('button', { name: /add new location/i })).toBeDisabled()
  })

  test('New location → sub-page link → NAV-003 save-first → opens the saved sub-page', async () => {
    const savedNew: Location = {
      id: 9200,
      revisionCount: 0,
      name: 'New Dump',
      categories: [
        { code: 40, kind: 'FIXED', volume: null, cost: 5000, distance: null, perUnit: null },
      ],
      subPageRows: [],
    }
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({
            locations: [harbour, emptyLanding, savedNew],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        ),
      ),
    )
    renderSchedule4()
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await userEvent.type(screen.getByLabelText('Location Name'), 'New Dump')
    await userEvent.type(screen.getByLabelText('Lakeside Dry Dump cost'), '5000')

    // Open a sub-page from the unsaved NEW panel → NAV-003 save-first confirm.
    await userEvent.click(screen.getByRole('button', { name: /Towing Total \(0\)/i }))
    expect(
      screen.getByText(
        'The information for the New Location must be saved before you can add other Transportation. Would you like to save the information now?',
      ),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /save and continue/i }))

    expect(await screen.findByRole('table', { name: /Towing Total/i })).toBeInTheDocument()
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
                locations: [
                  {
                    id: 999,
                    revisionCount: 1,
                    name: 'Context 999/2020 loaded',
                    comments: null,
                    categories: [],
                    subPageRows: [],
                  },
                ],
              }),
            )
          : HttpResponse.json(doc()),
      ),
      http.put(LOCATIONS_URL, async () => {
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
      path: '/schedule-4',
      validateSearch: realScheduleRoute.options.validateSearch,
      component: () => (
        <MillYearProvider initial={{ millId: 514, year: 2021 }}>
          {/* eslint-disable-next-line @typescript-eslint/no-use-before-define */}
          <StaleRaceHarness />
        </MillYearProvider>
      ),
    })
    const router = createRouter({
      routeTree: rootRoute.addChildren([scheduleRoute]),
      history: createMemoryHistory({ initialEntries: ['/schedule-4'] }),
    })
    render(<RouterProvider router={router} />)
    const user = userEvent.setup()

    await screen.findByText('Harbour Dump')
    await user.click(screen.getByRole('button', { name: /add new location/i }))
    await user.type(screen.getByLabelText('Location Name'), 'New Dump')
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Context 999/2020 loaded')).toBeInTheDocument()

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
      <Schedule4 />
    </>
  )
}
