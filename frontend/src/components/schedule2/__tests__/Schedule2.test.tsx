import type { ReactNode } from 'react'
import type Schedule2Response from '@/interfaces/Schedule2Response'
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

// The SERVED body of an unsaved (or just-deleted) Schedule 2 — copied from the wire, not imagined.
// Captured 2026-08-24 from the running backend for mill 514 / 2021, a mill-year that has never had a
// Schedule 2 saved:
//
//   {"millId":16050,"year":2021,"trackStatus":"D","editable":true,"purchasedLogCost":{},
//    "purchasedWoodOverhead":{},"subtotal":{},"lessLogSales":{},"netPurchased":{},
//    "totalCompanyLogging":{},"totalAverage":{}}
//
// Under the app-wide Jackson `default-property-inclusion: non_null` EVERY null is dropped, so there
// is no `revisionCount` key, no `comments` key, and each CostBlock arrives as `{}` rather than as
// three nulls. Defect #292 was exactly this gap between fixture and wire: the delete test asserted
// the right thing ("Delete is disabled") and passed from the day it was written, because its fixture
// set `revisionCount: null` — a value the API cannot emit — while the real gate
// (`revisionCount !== null`) was inert against the `undefined` the app actually receives.
//
// So: do not add keys back to make a test convenient. If a reader needs a value here that the server
// would omit, the reader is what needs fixing. Typed as `Schedule2Response` so the interface
// constrains the fixture instead of merely describing it.
const unsavedDoc: Schedule2Response = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  purchasedLogCost: {},
  purchasedWoodOverhead: {},
  subtotal: {},
  lessLogSales: {},
  netPurchased: {},
  totalCompanyLogging: {},
  totalAverage: {},
}

// The action-bar buttons only (the confirm modal renders a "Delete" button of its own, and the bar
// renders twice — above and below the table). `expected` is required, not optional: `getAllByRole`
// throws on zero matches but this `.filter` returns [] silently, so a bare `forEach(expect(...))` over
// the result would pass vacuously if the modifier class were ever renamed — which `index.scss` and the
// e2e page object both key off, so it is a live possibility (code-review finding).
const actionBarButtons = (name: RegExp, expected: number) => {
  const found = screen
    .getAllByRole('button', { name })
    .filter((b) => b.closest('.schedule-2__actions'))
  expect(found).toHaveLength(expected)
  return found
}

// Carbon's Modal stays mounted and toggles `is-visible`, so a closed confirm dialog is still in the
// DOM with role="dialog" — assert on openness, never on absence.
const confirmModalOpen = () =>
  Boolean(document.querySelector('.cds--modal')?.classList.contains('is-visible'))

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
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/check status/i, 2).forEach((b) => expect(b).toBeEnabled())
    // The greyed button must say why (defect #292 decision 3) and be described by it.
    const hint = screen.getByText('Available once the schedule is saved')
    expect(actionBarButtons(/delete/i, 1)[0]).toHaveAttribute('aria-describedby', hint.id)
  })

  test('Delete renders on the bottom action bar only (defect #292)', async () => {
    // Legacy carried Save + Check Status above the schedule and Save + Check Status + Delete below
    // it (schedule2.xhtml:35-36 vs :172-178) — the asymmetry Schedules 1 and 3 already honour.
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)

    await screen.findByLabelText('Purchased Log Cost cost')
    actionBarButtons(/^save$/i, 2)
    actionBarButtons(/check status/i, 2)
    const deletes = actionBarButtons(/delete/i, 1)
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
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test('Delete stays disabled while the post-delete reload is in flight, so no second DELETE can fire (defect #292 face 2)', async () => {
    // The window that survived the first fix: `run`'s `.finally` releases `saving` when the DELETE
    // settles, but the reload GET it dispatched is still outstanding — and until that reload lands,
    // `data` still carries the pre-delete `revisionCount`. Delete therefore re-enabled on a record
    // that was already gone, and a second click sent a second DELETE which the idempotent backend
    // answered with another success message. That is the reported symptom, verbatim.
    let deleted = false
    let deleteCount = 0
    let releaseReload: () => void = () => undefined
    const reloadBlocked = new Promise<void>((resolve) => {
      releaseReload = resolve
    })
    server.use(
      http.get(URL, async () => {
        if (deleted) {
          await reloadBlocked // hold the reload open to keep the window measurable
          return HttpResponse.json(unsavedDoc)
        }
        return HttpResponse.json(schedule2Doc)
      }),
      http.delete(URL, () => {
        deleted = true
        deleteCount += 1
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    // The DELETE has landed and the reload has NOT. Delete must already be shut.
    await waitFor(() => expect(deleteCount).toBe(1))
    await waitFor(() => expect(actionBarButtons(/delete/i, 1)[0]).toBeDisabled())

    // Prove it, rather than trusting the attribute: clicking cannot produce a second request.
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    expect(confirmModalOpen()).toBe(false)
    expect(deleteCount).toBe(1)

    releaseReload()
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue(''))
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    expect(deleteCount).toBe(1)
  })

  test('a FAILED post-delete reload still closes the Delete gate (defect #292 face 2)', async () => {
    // The permanent version of the same hole: if the reload never succeeds, nothing else ever tells
    // the page its record is gone. The banner says the refresh failed; Delete must still be shut,
    // because the DELETE itself succeeded.
    let deleted = false
    let deleteCount = 0
    server.use(
      http.get(URL, () =>
        deleted ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(schedule2Doc),
      ),
      http.delete(URL, () => {
        deleted = true
        deleteCount += 1
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(
      await screen.findByText('Deleted, but the list could not be refreshed.'),
    ).toBeInTheDocument()
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    expect(deleteCount).toBe(1)
  })

  test('a never-saved schedule issues NO DELETE request, not merely a disabled button (defect #292)', async () => {
    // The gate has to hold at the request level, the way Save's does (`putCalled === false` above):
    // `disabled` is presentation, and `handleDelete` is what actually must refuse.
    let deleteCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(unsavedDoc)),
      http.delete(URL, () => {
        deleteCalled = true
        return HttpResponse.json({ message: { key: 'x', text: 'x' } })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)

    // No confirm dialog opened, and nothing reached the network.
    expect(confirmModalOpen()).toBe(false)
    expect(deleteCalled).toBe(false)
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
