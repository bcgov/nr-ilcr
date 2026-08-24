import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider (AppProviders has none). Mock the router
// exactly like Schedule1.test.tsx; stub Link as a passthrough in case it renders.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule2 from '@/components/schedule2'
import MillYearProvider from '@/context/millYear/MillYearProvider'

const URL = 'http://localhost:3000/api/v1/schedule2'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule2/check-status'

const block = (volume: number | null, cost: number | null, perUnit: number | null) => ({
  volume,
  cost,
  perUnit,
})

const schedule2Doc = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  revisionCount: 3,
  comments: 'Seed comment for 514/2021',
  purchasedLogCost: block(1000, 50000, 50.0),
  purchasedWoodOverhead: block(1000, 2000, 2.0),
  subtotal: block(1000, 52000, 52.0),
  lessLogSales: block(200, 8000, 40.0),
  netPurchased: block(800, 44000, 55.0),
  totalCompanyLogging: block(2000, 90000, 45.0),
  totalAverage: block(2800, 134000, 47.86),
}

// The SERVED shape of an unsaved (or just-deleted) Schedule 2: every figure null and — critically —
// NO `revisionCount` key at all. The backend leaves it null and the app-wide Jackson
// `default-property-inclusion: non_null` (application.yml) then drops it from the body, so a fixture
// that sets `revisionCount: null` describes a response the API cannot emit. Defect #292 shipped
// behind exactly that: the delete test below asserted the right thing ("Delete is disabled") and
// passed from the day it was written while the real gate (`revisionCount !== null`) was inert
// against `undefined`.
// Never add a `revisionCount` key here.
const unsavedDoc = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  comments: null,
  purchasedLogCost: block(null, null, null),
  purchasedWoodOverhead: block(null, null, null),
  subtotal: block(null, null, null),
  lessLogSales: block(null, null, null),
  netPurchased: block(null, null, null),
  totalCompanyLogging: block(null, null, null),
  totalAverage: block(null, null, null),
}

// The action-bar buttons only (the confirm modal renders a "Delete" button of its own, and the bar
// renders twice — above and below the table).
const actionBarButtons = (name: RegExp) =>
  screen.getAllByRole('button', { name }).filter((b) => b.closest('.schedule-2__actions'))

const problemHandler = (status: number, detail: string) =>
  http.get(
    URL,
    () =>
      new HttpResponse(JSON.stringify({ detail }), {
        status,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
  )

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

describe('Schedule2 page', () => {
  test('editable:true renders editable inputs for 25/26 + comments; derived blocks read-only (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)

    // The three editable fields are inputs seeded from the document.
    const item25Cost = await screen.findByLabelText('Purchased Log Cost cost')
    // Editable numbers display thousands-grouped (commas).
    expect(item25Cost).toHaveValue('50,000')
    expect(screen.getByLabelText('Less Log Sales volume')).toHaveValue('200')
    expect(screen.getByLabelText('Less Log Sales cost')).toHaveValue('8,000')
    expect(
      screen.getByLabelText('If you have any additional comments, please enter them here:'),
    ).toHaveValue('Seed comment for 514/2021')

    // Carried purchasedLogCost.volume is read-only (never an input).
    expect(screen.queryByLabelText('Purchased Log Cost volume')).not.toBeInTheDocument()
    // Derived blocks are read-only display (no inputs).
    expect(screen.queryByLabelText(/Subtotal/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Net Purchased/i)).not.toBeInTheDocument()

    // Actions enabled.
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
    screen.getAllByRole('button', { name: /check status/i }).forEach((b) => expect(b).toBeEnabled())
    screen.getAllByRole('button', { name: /delete/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test('editable:false renders read-only + disables actions (AC1)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ ...schedule2Doc, trackStatus: 'S', editable: false }),
      ),
    )
    render(<Schedule2 />)

    expect(await screen.findByText('Purchased/Private Log Costs:')).toBeInTheDocument()
    expect(screen.queryByLabelText('Purchased Log Cost cost')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Less Log Sales volume')).not.toBeInTheDocument()
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeDisabled())
    screen
      .getAllByRole('button', { name: /check status/i })
      .forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /delete/i }).forEach((b) => expect(b).toBeDisabled())
  })

  test('unsaved editable doc Saves with revisionCount 0 (AC2)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(unsavedDoc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...schedule2Doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const item25Cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.type(item25Cost, '12345')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    const body = captured as {
      revisionCount: number
      purchasedLogCostCost: number | null
      lessLogSalesVolume: number | null
      lessLogSalesCost: number | null
    }
    expect(body.revisionCount).toBe(0)
    expect(body.purchasedLogCostCost).toBe(12345)
    expect(body.lessLogSalesVolume).toBeNull()
    expect(body.lessLogSalesCost).toBeNull()
    // Contract lock: the PUT carries ONLY the five entered/meta fields — never a derived/carried
    // figure (subtotal, netPurchased, perUnit, purchasedLogCost.volume, ...). A stray field would
    // otherwise slip through unnoticed.
    expect(Object.keys(body as Record<string, unknown>).sort()).toEqual([
      'comments',
      'lessLogSalesCost',
      'lessLogSalesVolume',
      'purchasedLogCostCost',
      'revisionCount',
    ])
  })

  test('valid Save PUTs the flat request and shows the API success message (AC2)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...schedule2Doc,
          revisionCount: 4,
          purchasedLogCost: block(1000, 60000, 60.0),
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '60000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    const body = captured as {
      revisionCount: number
      purchasedLogCostCost: number
      lessLogSalesVolume: number
      lessLogSalesCost: number
    }
    expect(body.revisionCount).toBe(3)
    expect(body.purchasedLogCostCost).toBe(60000)
    expect(body.lessLogSalesVolume).toBe(200)
    expect(body.lessLogSalesCost).toBe(8000)
    // Reseeded from the echo (60000/1000 = 60 read-only display), shown in the currency style ($/m³).
    expect(screen.getByText('60.00')).toBeInTheDocument()
  })

  test('out-of-range value is blocked client-side — inline error, no PUT (AC3)', async () => {
    let putCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, () => {
        putCalled = true
        return problemBody(400, 'server should not be reached')
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '100000000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(
      await screen.findByText('Entered cost must be between -99,999,999 and 99,999,999.'),
    ).toBeInTheDocument()
    expect(putCalled).toBe(false)
    expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue('100,000,000')
  })

  test('backend 4xx save failure shows verbatim detail (AC3)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, () => problemBody(409, 'The record has been changed by another user.')),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(
      await screen.findByText('The record has been changed by another user.'),
    ).toBeInTheDocument()
  })

  test('a never-saved Schedule 2 disables Delete and leaves Save / Check Status usable (defect #292)', async () => {
    // The served body of a mill/year that has never had a Schedule 2 saved: 200, EDITABLE, every
    // figure blank, and NO `revisionCount` key. There is nothing to delete, so Delete must be
    // greyed out — but data entry must still be possible (legacy AF1).
    server.use(http.get(URL, () => HttpResponse.json(unsavedDoc)))
    render(<Schedule2 />)

    await screen.findByLabelText('Purchased Log Cost cost')
    const deletes = actionBarButtons(/delete/i)
    expect(deletes).toHaveLength(1)
    deletes.forEach((b) => expect(b).toBeDisabled())
    actionBarButtons(/^save$/i).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/check status/i).forEach((b) => expect(b).toBeEnabled())
  })

  test('Delete renders on the bottom action bar only (defect #292)', async () => {
    // Legacy carried Save + Check Status above the schedule and Save + Check Status + Delete below
    // it (schedule2.xhtml:35-36 vs :172-178) — the asymmetry Schedules 1 and 3 already honour.
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)

    await screen.findByLabelText('Purchased Log Cost cost')
    expect(actionBarButtons(/^save$/i)).toHaveLength(2)
    expect(actionBarButtons(/check status/i)).toHaveLength(2)
    const deletes = actionBarButtons(/delete/i)
    expect(deletes).toHaveLength(1)
    // …and it is the LAST action bar in the document that carries it.
    const bars = Array.from(document.querySelectorAll('.schedule-2__actions'))
    expect(bars).toHaveLength(2)
    expect(bars[1]?.contains(deletes[0] ?? null)).toBe(true)
    // A saved schedule (revisionCount 3) can still be deleted.
    expect(deletes[0]).toBeEnabled()
  })

  test('Delete confirms, shows the API message, then re-GETs the empty editable schedule (AC4)', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(deleted ? unsavedDoc : schedule2Doc)),
      http.delete(URL, () => {
        deleted = true
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /delete/i })[0])
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // Schedule 2 never 404s: the re-GET returns the empty EDITABLE document. Inputs remain (now
    // empty), Delete is disabled (nothing to delete), and Save/Check Status stay enabled so the
    // Licensee can immediately re-enter data (legacy AF1).
    await waitFor(() => expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue(''))
    // Delete greys out again: the re-GET carries no `revisionCount`, so there is nothing to delete
    // and the same schedule cannot be "deleted" repeatedly (defect #292, second face).
    const actionDeletes = actionBarButtons(/delete/i)
    expect(actionDeletes).toHaveLength(1)
    actionDeletes.forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test('Check Status MET renders a success notification with the returned text (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          messages: [{ key: 'scheduleRequirementsMetMsg', text: 'Schedule requirements met.' }],
        }),
      ),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /check status/i })[0])

    expect(await screen.findByText('Schedule requirements met.')).toBeInTheDocument()
  })

  test('Check Status ISSUES renders a warning notification with the returned text (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [{ key: 'missingRequiredFieldMsg', text: 'A required field is missing.' }],
        }),
      ),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /check status/i })[0])

    expect(await screen.findByText('A required field is missing.')).toBeInTheDocument()
  })

  test('409 mill-closed shows verbatim detail, form suppressed', async () => {
    const detail =
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.'
    server.use(problemHandler(409, detail))
    render(<Schedule2 />)

    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('empty context shows verbatim ERR-001 and fires NO request', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule2 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('stale PUT is ignored when context changes before it settles (Story 29.6)', async () => {
    let releasePut = () => {}
    const releasePromise = new Promise<void>((resolve) => {
      releasePut = resolve
    })

    server.use(
      http.get(URL, ({ request }) =>
        new window.URL(request.url).searchParams.get('millId') === '999'
          ? HttpResponse.json({
              ...schedule2Doc,
              millId: 999,
              year: 2020,
              editable: false,
              comments: 'Context 999/2020 loaded',
            })
          : HttpResponse.json(schedule2Doc),
      ),
      http.put(URL, async () => {
        await releasePromise
        return HttpResponse.json({
          ...schedule2Doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )

    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        {/* eslint-disable-next-line @typescript-eslint/no-use-before-define */}
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await screen.findAllByRole('button', { name: /^save$/i })
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
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
      <Schedule2 />
    </>
  )
}
