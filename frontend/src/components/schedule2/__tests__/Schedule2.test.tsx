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

  // ---- Defect #291: derived figures track data entry, on blur, before Save. -----------------------
  // The fixture's carried figures: Sch3 PO&P volume 1000 / PO&P actual cost 2000 (purchasedWoodOverhead),
  // Sch3 Crown volume 2000 / total logging cost 90000 (totalCompanyLogging). Entered: 50000 / 200 / 8000.

  /** A value row's cells as text: [label, volume, cost, $/m³]. */
  const rowCells = (label: string) => {
    const tr = screen.getByText(label).closest('tr')
    if (!tr) throw new Error(`no row for "${label}"`)
    return within(tr)
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
  }

  test('typing alone moves nothing; blurring the field recalculates every dependent figure (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '52,000', '52.00'])

    // Typing must NOT recalculate — legacy recalculated on the field's change/blur, not per keystroke,
    // so a half-typed number never drives the totals.
    await user.clear(cost)
    await user.type(cost, '60000')
    expect(cost).toHaveValue('60,000')
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '52,000', '52.00'])

    // Blur commits the field, and every dependent figure moves at once — with no Save.
    await user.tab()
    expect(rowCells('Purchased/Private Log Costs:')).toEqual([
      'Purchased/Private Log Costs:',
      '1,000',
      'Purchased Log Cost cost', // the entry cell holds the input; its text is the hidden a11y label
      '60.00', // 60000/1000
    ])
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '62,000', '62.00'])
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual([
      'Net Purchased/Private Log Cost:',
      '800', // 1000 - 200
      '54,000', // 62000 - 8000
      '67.50',
    ])
    expect(rowCells('Total Average Logging Costs:')).toEqual([
      'Total Average Logging Costs:',
      '2,800', // 800 + 2000 crown
      '144,000', // 54000 + 90000
      '51.43', // 144000/2800 = 51.4286
    ])
  })

  test('the wholly-carried rows never move during entry (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '999999')
    await user.tab()

    // Both are carried from Schedules 3 and 1 — Schedule 2 entry cannot affect them.
    expect(rowCells('Purchased/Private Wood Overhead:')).toEqual([
      'Purchased/Private Wood Overhead:',
      '1,000',
      '2,000',
      '2.00',
    ])
    expect(rowCells('Total Company Logging Costs(Sch 1):')).toEqual([
      'Total Company Logging Costs(Sch 1):',
      '2,000',
      '90,000',
      '45.00',
    ])
  })

  test('editing the sales pair recalculates net and total average (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    const user = userEvent.setup()

    const volume = await screen.findByLabelText('Less Log Sales volume')
    await user.clear(volume)
    await user.type(volume, '400')
    await user.tab()

    // 8000/400 = 20.00; net volume 1000-400 = 600; net cost unchanged at 52000-8000 = 44000;
    // 44000/600 = 73.3333; total average volume 600+2000 = 2600, cost 44000+90000 = 134000.
    expect(rowCells('(less) Log Sales:')).toEqual([
      '(less) Log Sales:',
      'Less Log Sales volume', // both cells hold inputs; their text is the hidden a11y label
      'Less Log Sales cost',
      '20.00', // 8000/400
    ])
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual([
      'Net Purchased/Private Log Cost:',
      '600',
      '44,000',
      '73.33',
    ])
    expect(rowCells('Total Average Logging Costs:')).toEqual([
      'Total Average Logging Costs:',
      '2,600',
      '134,000',
      '51.54', // 134000/2600 = 51.5385
    ])
  })

  test('the mirror equals the SERVER figures, before and after Save (#291 AC5)', async () => {
    // What Schedule2Service actually computes for cost 60000 against this document's carried figures.
    // Asserting the rendered cells against THESE values — not against a snapshot of the render — is
    // what makes this a mirror-vs-server comparison. The earlier version snapshotted the pre-Save
    // render and compared it to the post-Save render; because an editable page always renders the
    // mirror, that compared the mirror to itself and would have passed with 999999 in the echo
    // (code review 2026-08-21).
    const SERVER = {
      subtotal: block(1000, 62000, 62.0),
      netPurchased: block(800, 54000, 67.5),
      totalAverage: block(2800, 144000, 51.4286),
    }
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, () =>
        HttpResponse.json({
          ...schedule2Doc,
          revisionCount: 4,
          purchasedLogCost: block(1000, 60000, 60.0),
          ...SERVER,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '60000')
    await user.tab()

    // The mirror must already agree with what the server will send.
    const expected = {
      subtotal: ['Subtotal:', '1,000', '62,000', '62.00'],
      net: ['Net Purchased/Private Log Cost:', '800', '54,000', '67.50'],
      average: ['Total Average Logging Costs:', '2,800', '144,000', '51.43'],
    }
    expect(rowCells('Subtotal:')).toEqual(expected.subtotal)
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual(expected.net)
    expect(rowCells('Total Average Logging Costs:')).toEqual(expected.average)

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    // ...and still agree once the echo has replaced the document.
    expect(rowCells('Subtotal:')).toEqual(expected.subtotal)
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual(expected.net)
    expect(rowCells('Total Average Logging Costs:')).toEqual(expected.average)
  })

  test('on load the mirror reproduces the served figures exactly (#291 AC5)', async () => {
    // The fixture is self-consistent (its stored derived values satisfy Schedule2Service's formulas),
    // so this is a direct mirror-vs-server comparison with no edit involved: a mirror that rounds or
    // propagates nulls differently from the service fails here.
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    await screen.findByText('Purchased/Private Log Costs:')

    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '52,000', '52.00'])
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual([
      'Net Purchased/Private Log Cost:',
      '800',
      '44,000',
      '55.00',
    ])
    expect(rowCells('Total Average Logging Costs:')).toEqual([
      'Total Average Logging Costs:',
      '2,800',
      '134,000',
      '47.86',
    ])
  })

  test('view mode renders the document figures as-is — no client recomputation (#291 AC7)', async () => {
    // A non-editable document whose stored Subtotal deliberately disagrees with its own line items: if
    // the page recomputed outside Draft it would show 52,000 instead of the server's figure.
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...schedule2Doc,
          trackStatus: 'S',
          editable: false,
          subtotal: block(1000, 999999, 999.99),
        }),
      ),
    )
    render(<Schedule2 />)

    expect(await screen.findByText('Purchased/Private Log Costs:')).toBeInTheDocument()
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '999,999', '999.99'])
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
    const emptyDoc = {
      millId: 514,
      year: 2021,
      trackStatus: 'D',
      editable: true,
      revisionCount: null,
      comments: null,
      purchasedLogCost: block(null, null, null),
      purchasedWoodOverhead: block(null, null, null),
      subtotal: block(null, null, null),
      lessLogSales: block(null, null, null),
      netPurchased: block(null, null, null),
      totalCompanyLogging: block(null, null, null),
      totalAverage: block(null, null, null),
    }
    server.use(
      http.get(URL, () => HttpResponse.json(emptyDoc)),
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

  test('Delete confirms, shows the API message, then re-GETs the empty editable schedule (AC4)', async () => {
    let deleted = false
    const emptyDoc = {
      ...schedule2Doc,
      revisionCount: null,
      comments: null,
      purchasedLogCost: block(null, null, null),
      purchasedWoodOverhead: block(null, null, null),
      subtotal: block(null, null, null),
      lessLogSales: block(null, null, null),
      netPurchased: block(null, null, null),
      totalCompanyLogging: block(null, null, null),
      totalAverage: block(null, null, null),
    }
    server.use(
      http.get(URL, () => HttpResponse.json(deleted ? emptyDoc : schedule2Doc)),
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
    // Scope to the action-bar Delete buttons (the closed confirm modal also has a "Delete" button).
    const actionDeletes = screen
      .getAllByRole('button', { name: /delete/i })
      .filter((b) => b.closest('.schedule-2__actions'))
    expect(actionDeletes.length).toBeGreaterThan(0)
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
