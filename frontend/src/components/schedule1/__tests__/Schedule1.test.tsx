import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider (AppProviders has none). Mock the router
// exactly like Dashboard.test.tsx; stub Link as a passthrough in case it renders. A hoisted shared
// navigate spy lets the Story 2.5 navigation test assert the destination.
const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule1 from '@/components/schedule1'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'

// Drives a mid-save mill/year change so the shared run() stale-response guard can be exercised
// (Story 29.6). Module-level so it is not re-created per render (an @eslint-react rule forbids nested
// component definitions).
const StaleRaceHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <Schedule1 />
    </>
  )
}

const URL = 'http://localhost:3000/api/v1/schedule1'

const schedule1Doc = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  crownVolume: 12345,
  schedule3CrownVolume: 54321,
  revisionCount: 3,
  comments: 'Seed comment for 514/2021',
  lineItems: [{ costItemCode: 12, volume: 1000, cost: 50000, perUnit: 50.0 }],
  silviculture: {
    actualSpent: { costItemCode: 1, volume: 500, cost: 20000, perUnit: 40.0 },
    accruedLessActual: null,
    lessAdmin: { costItemCode: 139, volume: 55, cost: 9999, perUnit: null },
    total: null,
  },
  forestMgmtAdminCost: 600000,
  lessSilvAdminCost: 150000,
  otherCosts: { volume: 8000, costSubtotal: 24000, perUnit: 3.0, count: 2 },
  // The server's own derived figures for THIS document, so the mirror can be compared against them
  // rather than against hand arithmetic in this file (code review 2026-08-21). Computed from
  // Schedule1Service's formulas: subtotal = 50000 line-12 + 600000 FMA + 24000 other = 674000;
  // total silviculture = 20000 actual - 150000 Sch3 admin (no accrued) = -130000; grand total = 544000;
  // its rate = 544000 / 54321 crown = 10.01; less-silv-admin rate = 150000 / 55 = 2727.27. The 143/144
  // and 140 rates are null because those volumes are absent from the fixture.
  subtotalCompanyLoggingCost: 674000,
  subtotalCompanyLoggingPerUnit: null,
  totalSilvicultureCost: -130000,
  totalSilviculturePerUnit: null,
  totalCompanyLoggingCost: 544000,
  totalCompanyLoggingPerUnit: 10.01,
  forestMgmtAdminPerUnit: null,
  lessSilvAdminPerUnit: 2727.27,
  warnings: [],
}

// Story 2.3 BR-03 pre-fill fixture: first entry (all savable volumes = the Sch 3 crown value 7777),
// WRN-001 present, no Schedule 3 admin costs.
const WRN_001 =
  'The Crown Timber (Sch 3) volume has been set for volume fields. Please check and save schedule.'
const prefillDoc = {
  ...schedule1Doc,
  crownVolume: null,
  schedule3CrownVolume: 7777,
  // Full legacy 13-field copy: 12-18 + 143 + 144 (D2 reversal).
  lineItems: [12, 13, 14, 15, 16, 17, 18, 143, 144].map((code) => ({
    costItemCode: code,
    volume: 7777,
    cost: null,
    perUnit: null,
  })),
  silviculture: {
    actualSpent: { costItemCode: 1, volume: 7777, cost: null, perUnit: null },
    accruedLessActual: { costItemCode: 2, volume: 7777, cost: null, perUnit: null },
    lessAdmin: { costItemCode: 139, volume: 7777, cost: null, perUnit: null },
    total: { costItemCode: 140, volume: 7777, cost: null, perUnit: null },
  },
  forestMgmtAdminCost: null,
  lessSilvAdminCost: null,
  warnings: [{ key: 'crownVolumeSetForSchedule1', text: WRN_001 }],
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

describe('Schedule1 editable page', () => {
  // ---- Defect #291: derived figures track data entry, on blur, before Save. -----------------------

  /** A cost-table row's cells as text: [label, volume, cost, $/m³]. */
  const rowCells = (label: string | RegExp) => {
    const tr = screen.getByText(label).closest('tr')
    if (!tr) throw new Error(`no row for "${String(label)}"`)
    return within(tr)
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
  }
  /** The read-only $/m³ cell of a cost-table row (index 3). */
  const rate = (label: string | RegExp) => rowCells(label)[3]
  /** The read-only cost cell (index 2). */
  const costOf = (label: string | RegExp) => rowCells(label)[2]

  const SUBTOTAL = 'Subtotal Company Logging Cost (no Silviculture)'
  const GRAND_TOTAL = 'Total Company Logging Costs (Including total Silviculture Cost)'

  test('typing alone moves nothing; blurring a logging cost recalculates the whole chain (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    // Seeded: 50000/1000 = 50.00. Subtotal = 50000 + 600000 FMA + 24000 other = 674000.
    expect(rate('Standing Tree to Loaded Truck')).toBe('50.00')
    expect(costOf(SUBTOTAL)).toBe('674,000')

    await user.clear(cost)
    await user.type(cost, '100000')
    expect(rate('Standing Tree to Loaded Truck')).toBe('50.00') // not per keystroke
    expect(costOf(SUBTOTAL)).toBe('674,000')

    await user.tab()
    expect(rate('Standing Tree to Loaded Truck')).toBe('100.00') // 100000/1000
    expect(costOf(SUBTOTAL)).toBe('724,000') // 100000 + 600000 + 24000
    // Grand total = subtotal + total silviculture (20000 − 150000 = −130000) = 594000,
    // over the Sch 3 crown volume 54321 -> 10.93.
    expect(costOf(GRAND_TOTAL)).toBe('594,000')
    expect(rate(GRAND_TOTAL)).toBe('10.93')
  })

  test("a volume blur recalculates that row's $/m³ only (#291)", async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    const volume = await screen.findByLabelText('Standing Tree to Loaded Truck volume')
    await user.clear(volume)
    await user.type(volume, '2000')
    await user.tab()

    expect(rate('Standing Tree to Loaded Truck')).toBe('25.00') // 50000/2000
    // A volume is not part of any cost total, so the subtotal is unchanged.
    expect(costOf(SUBTOTAL)).toBe('674,000')
  })

  test("139's RATE is mirrored while its COST stays the Schedule 3 pull (#291)", async () => {
    // Row 139's two halves come from different places, and the page treats them differently on
    // purpose: the cost is pulled from Schedule 3 and nothing on this page feeds it, but the VOLUME is
    // user-entered -- so `deriveSchedule1` computes a rate for 139 (derived.ts:98) even though it
    // computes no cost for it. The mirror therefore supersedes 139's rate and must NOT touch its cost.
    //
    // Written after a SonarQube refactor of a three-deep ternary (2026-08-21) exposed that this
    // opposite-precedence rule was pinned by no test: inverting it left all 268 green.
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    const label = 'Less Silviculture Admin Costs'
    // Served state: 150,000 pulled from Schedule 3 over the fixture's volume of 55.
    expect(await screen.findByLabelText(`${label} volume`)).toHaveValue('55')
    expect(rate(label)).toBe('2,727.27') // 150,000 / 55

    const volume = screen.getByLabelText(`${label} volume`)
    await user.clear(volume)
    await user.type(volume, '60000')
    await user.tab()

    // The rate moved off the served figure -- the mirror owns it.
    expect(rate(label)).toBe('2.50') // 150,000 / 60,000
    // ...while the cost is still the Schedule 3 pull, untouched by the mirror.
    expect(costOf(label)).toBe('150,000')
  })

  test('the Other Costs $/m³ tracks the volume entered on this page (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    const volume = await screen.findByLabelText('Subtotal Other Costs volume')
    // Seeded: subtotal 24000 over volume 8000 = 3.00.
    expect(rate(/^Subtotal Other Costs\(2\):$/)).toBe('3.00')

    await user.clear(volume)
    await user.type(volume, '6000')
    await user.tab()
    expect(rate(/^Subtotal Other Costs\(2\):$/)).toBe('4.00') // 24000/6000

    // Clearing it blanks the rate rather than dividing by zero.
    await user.clear(screen.getByLabelText('Subtotal Other Costs volume'))
    await user.tab()
    expect(rate(/^Subtotal Other Costs\(2\):$/)).toBe('—')
  })

  test('Total Silviculture keeps legacy null semantics as costs are entered (#291)', async () => {
    // The pre-fill fixture has every volume set and every cost blank, with no Sch 3 admin pull, so
    // Total Silviculture must read blank — not a negative admin cost.
    server.use(http.get(URL, () => HttpResponse.json(prefillDoc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    const label = 'Total Silviculture (As per Financial Statements)'
    expect(await screen.findByText(label)).toBeInTheDocument()
    expect(costOf(label)).toBe('—')

    // Entering an Accrued cost alone is enough to produce a total (addition needs one operand).
    const accrued = screen.getByLabelText('Accrued less Actual $ Spent cost')
    await user.clear(accrued)
    await user.type(accrued, '50000')
    await user.tab()
    expect(costOf(label)).toBe('50,000')
  })

  test('on load the mirror reproduces the served figures exactly (#291 AC5)', async () => {
    // A direct mirror-vs-server comparison with no edit involved: the fixture now carries the figures
    // Schedule1Service computes for it, so a mirror that rounds or propagates nulls differently fails
    // here. Schedule 1 is the page with the two easiest-to-conflate rules, and before the code review
    // its fixture carried no derived fields at all — so every assertion was self-referential.
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    await screen.findByText('Standing Tree to Loaded Truck')

    expect(costOf(SUBTOTAL)).toBe('674,000')
    expect(costOf(GRAND_TOTAL)).toBe('544,000')
    expect(rate(GRAND_TOTAL)).toBe('10.01')
    expect(rate('Standing Tree to Loaded Truck')).toBe('50.00')
    expect(rate('Less Silviculture Admin Costs')).toBe('2,727.27') // 150000 / 55
    expect(costOf('Total Silviculture (As per Financial Statements)')).toBe('-130,000')
    expect(rate(/^Subtotal Other Costs\(2\):$/)).toBe('3.00')
  })

  test('the mirror equals the SERVER figures, before and after Save (#291 AC5)', async () => {
    // Asserted against the echo's own derived fields, not against a snapshot of the pre-Save render:
    // an editable page always renders the mirror, so comparing render-to-render compared the mirror
    // with itself and passed even with a wrong echo (code review 2026-08-21).
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.put(URL, () =>
        HttpResponse.json({
          ...schedule1Doc,
          revisionCount: 4,
          lineItems: [{ costItemCode: 12, volume: 1000, cost: 100000, perUnit: 100.0 }],
          subtotalCompanyLoggingCost: 724000,
          totalSilvicultureCost: -130000,
          totalCompanyLoggingCost: 594000,
          totalCompanyLoggingPerUnit: 10.93,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    await user.clear(cost)
    await user.type(cost, '100000')
    await user.tab()

    // The mirror must already agree with what the server will send: 100000 + 600000 + 24000 = 724000,
    // grand total 724000 - 130000 = 594000, rate 594000 / 54321 = 10.93.
    expect(costOf(SUBTOTAL)).toBe('724,000')
    expect(costOf(GRAND_TOTAL)).toBe('594,000')
    expect(rate(GRAND_TOTAL)).toBe('10.93')

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    expect(costOf(SUBTOTAL)).toBe('724,000')
    expect(costOf(GRAND_TOTAL)).toBe('594,000')
    expect(rate(GRAND_TOTAL)).toBe('10.93')
  })

  test('view mode renders the document figures as-is — no client recomputation (#291 AC7)', async () => {
    // A stored subtotal that deliberately disagrees with the line items: a recomputing view would show
    // 674,000 instead of the server's own figure.
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...schedule1Doc,
          trackStatus: 'S',
          editable: false,
          subtotalCompanyLoggingCost: 999999,
          subtotalCompanyLoggingPerUnit: 111.11,
        }),
      ),
    )
    render(<Schedule1 />)

    expect(await screen.findByText(SUBTOTAL)).toBeInTheDocument()
    expect(costOf(SUBTOTAL)).toBe('999,999')
    expect(rate(SUBTOTAL)).toBe('111.11')
  })

  test('editable:true renders an editable form; perUnit stays read-only (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)

    // Writable fields are inputs seeded from the document.
    const volume = await screen.findByLabelText('Standing Tree to Loaded Truck volume')
    // Editable numeric fields display grouped values, like the read-only cells beside them.
    expect(volume).toHaveValue('1,000')
    expect(screen.getByLabelText('Standing Tree to Loaded Truck cost')).toHaveValue('50,000')
    // perUnit is server-computed, read-only text (not an input); rendered in the shared currency
    // style (thousands-separated, two decimals).
    expect(screen.getByText('50.00')).toBeInTheDocument()
    // Comments is editable.
    expect(
      screen.getByLabelText('If you have any additional comments, please enter them here:'),
    ).toHaveValue('Seed comment for 514/2021')
    // Save renders (top + bottom) and is enabled.
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
    expect(screen.getByText(/Subtotal Other Costs\(2\)/)).toBeInTheDocument()
  })

  test('editable:false renders read-only + disables actions (AC1 / S22)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ ...schedule1Doc, trackStatus: 'S', editable: false }),
      ),
    )
    render(<Schedule1 />)

    expect(await screen.findByText('Standing Tree to Loaded Truck')).toBeInTheDocument()
    // No editable inputs in read-only mode.
    expect(screen.queryByLabelText('Standing Tree to Loaded Truck volume')).not.toBeInTheDocument()
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /delete/i }).forEach((b) => expect(b).toBeDisabled())
  })

  test('valid Save PUTs the pinned request and shows the API success message (AC2)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...schedule1Doc,
          revisionCount: 4,
          lineItems: [{ costItemCode: 12, volume: 2000, cost: 60000, perUnit: 30.0 }],
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    await user.clear(cost)
    await user.type(cost, '60000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    // SUC-001 comes from the API message.text (AD-8), not a hardcoded string.
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // Request carried the optimistic-lock token + entered-fields-only contract.
    const body = captured as {
      revisionCount: number
      lineItems: { costItemCode: number; volume: number | null; cost: number | null }[]
      otherCostsVolume: number | null
    }
    expect(body.revisionCount).toBe(3)
    expect(body.lineItems.map((li) => li.costItemCode)).toEqual([12, 13, 14, 15, 16, 17, 18])
    // The fields DISPLAY grouped values ("60,000"), so the wire must still carry clean numbers —
    // a separator leaking through would reach the server as a string or parse to null and blank the
    // column. Item 12's cost was typed here; otherCostsVolume was only ever seeded and blurred.
    const item12 = body.lineItems.find((li) => li.costItemCode === 12)
    expect(item12?.cost).toBe(60000)
    expect(item12?.volume).toBe(1000)
    expect(body.otherCostsVolume).toBe(8000)
    // Recomputed perUnit from the echo is displayed (server-computed, 60000/2000 = 30).
    expect(screen.getByText('30.00')).toBeInTheDocument()
  })

  test('load/save/delete carry selected millId/year in query params (regression guard)', async () => {
    const selected = { millId: 516, year: 2020 }
    const selectedDoc = {
      ...schedule1Doc,
      millId: selected.millId,
      year: selected.year,
      comments: 'Seed comment for 516/2020',
    }
    let getUrl = ''
    let putUrl = ''
    let deleteUrl = ''

    server.use(
      http.get(URL, ({ request }) => {
        getUrl = request.url
        return HttpResponse.json(selectedDoc)
      }),
      http.put(URL, ({ request }) => {
        putUrl = request.url
        return HttpResponse.json({
          ...selectedDoc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
      http.delete(URL, ({ request }) => {
        deleteUrl = request.url
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )

    render(
      <MillYearProvider initial={selected}>
        <Schedule1 />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: /delete/i })[0])
    // Scope to the delete-confirm modal by heading — the page also renders the Leave / Save-required
    // modals, so a bare dialog role is ambiguous.
    const dialog = await screen.findByRole('dialog', { name: /delete schedule/i })
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()

    const assertParams = (url: string) => {
      expect(url).toContain(`millId=${selected.millId}`)
      expect(url).toContain(`year=${selected.year}`)
    }

    assertParams(getUrl)
    assertParams(putUrl)
    assertParams(deleteUrl)
  })

  test('out-of-range value is blocked client-side (advisory) — inline error, no PUT (AC3 / S03)', async () => {
    let putCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.put(URL, () => {
        putCalled = true
        return problemBody(400, 'server should not be reached')
      }),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    await user.clear(cost)
    await user.type(cost, '100000000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    // Advisory client validation shows the verbatim range message inline and blocks the doomed PUT
    // (backend stays authoritative — see the 500 test for server-error rendering).
    expect(
      await screen.findByText('Entered cost must be between -99,999,999 and 99,999,999.'),
    ).toBeInTheDocument()
    expect(putCalled).toBe(false)
    // Clicking Save blurred the field, which re-grouped it — and the advisory range check above
    // still fired, proving validation parses the grouped string rather than choking on the commas.
    expect(screen.getByLabelText('Standing Tree to Loaded Truck cost')).toHaveValue('100,000,000')
  })

  test('500 save failure shows ERR-004 and retry re-submits (AC3 / S23-S24)', async () => {
    let attempts = 0
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.put(URL, () => {
        attempts += 1
        return attempts === 1
          ? problemBody(500, 'Schedule could not be saved.')
          : HttpResponse.json({
              ...schedule1Doc,
              message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
            })
      }),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()

    // Retry with the same payload → success.
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(attempts).toBe(2)
  })

  test('Save and Check Status sit above AND below; Delete only below (schedule1.xhtml:35-38 vs :796-803)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)

    await screen.findByLabelText('Standing Tree to Loaded Truck volume')
    // Scoped to the two action bars: the confirm Modal keeps its own "Delete" button in the DOM even
    // while closed, so an unscoped name query cannot tell the bars apart from the dialog.
    const bars = document.querySelectorAll<HTMLElement>('.schedule-1__actions')
    expect(bars).toHaveLength(2)
    const [top, bottom] = [within(bars[0]), within(bars[1])]

    expect(top.getByRole('button', { name: 'Save' })).toBeInTheDocument()
    expect(top.getByRole('button', { name: 'Check Status' })).toBeInTheDocument()
    // The whole point: legacy kept the destructive action off the bar a reporter meets first.
    expect(top.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()

    expect(bottom.getByRole('button', { name: 'Save' })).toBeInTheDocument()
    expect(bottom.getByRole('button', { name: 'Check Status' })).toBeInTheDocument()
    expect(bottom.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  test('Delete confirms then shows the API success message and empties the schedule (AC4 / S13)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.delete(URL, () =>
        HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Standing Tree to Loaded Truck volume')
    await user.click(screen.getAllByRole('button', { name: /delete/i })[0])
    // Confirm dialog shows the verbatim legacy text.
    const dialog = await screen.findByRole('dialog', { name: 'Delete schedule' })
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // Empty schedule: the code-12 row is gone.
    await waitFor(() =>
      expect(
        screen.queryByLabelText('Standing Tree to Loaded Truck volume'),
      ).not.toBeInTheDocument(),
    )
  })

  test('409 mill-closed shows verbatim ERR-002, form suppressed (AC / S20)', async () => {
    const detail =
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.'
    server.use(problemHandler(409, detail))
    render(<Schedule1 />)

    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('404 not-found shows verbatim ERR-003 (AC / S21)', async () => {
    server.use(problemHandler(404, 'Schedule not found.'))
    // Explicit context so the message is deterministic regardless of the dev default mill/year.
    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        <Schedule1 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText(
        'No Schedule 1 exists for Mill 514 in Reporting Year 2021. Select another mill/year from Home, or create Schedule 1 data for this context.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('S19 empty context shows verbatim ERR-001 and fires NO request', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule1 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })
})

describe('Schedule1 crown pre-fill & Schedule 3 pulls (Story 2.3)', () => {
  test('BR-03 pre-fill seeds savable volume fields and shows WRN-001 verbatim (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(prefillDoc)))
    render(<Schedule1 />)

    // WRN-001 renders verbatim from the API warnings channel (AD-8).
    expect(await screen.findByText(WRN_001)).toBeInTheDocument()
    // Every savable volume input carries the copied crown value — the full legacy 13-field set.
    expect(screen.getByLabelText('Standing Tree to Loaded Truck volume')).toHaveValue('7,777')
    expect(screen.getByLabelText('Depletion and Amortization volume')).toHaveValue('7,777')
    expect(screen.getByLabelText('Actual $ Spent volume')).toHaveValue('7,777')
    expect(screen.getByLabelText('Accrued less Actual $ Spent volume')).toHaveValue('7,777')
    expect(screen.getByLabelText('Forest Management Administration volume')).toHaveValue('7,777')
    expect(screen.getByLabelText('Subtotal Company Logging volume')).toHaveValue('7,777')
    expect(screen.getByLabelText('Less Silviculture Admin Costs volume')).toHaveValue('7,777')
    expect(
      screen.getByLabelText('Total Silviculture (As per Financial Statements) volume'),
    ).toHaveValue('7,777')
  })

  test('no warning banner when warnings are empty (AC2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    await screen.findByLabelText('Standing Tree to Loaded Truck volume')
    expect(screen.queryByText(WRN_001)).not.toBeInTheDocument()
  })

  test('BR-04 admin costs are pulled from Schedule 3 and shown read-only (AC3)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    await screen.findByLabelText('Standing Tree to Loaded Truck volume')

    // Forest Management Admin (143) row shows the pulled cost as read-only text (no input).
    expect(screen.getByText('Forest Management Administration Costs (Sch 3)')).toBeInTheDocument()
    expect(screen.getByText('600,000')).toBeInTheDocument()
    expect(
      screen.queryByLabelText('Forest Management Administration Costs (Sch 3) cost'),
    ).not.toBeInTheDocument()
    // Less Silviculture Admin (139) shows the PULLED cost (150000), not Schedule 1's own 9999.
    expect(screen.getByText('150,000')).toBeInTheDocument()
    expect(screen.queryByText('9,999')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Less Silviculture Admin Costs cost')).not.toBeInTheDocument()
  })

  test('crown-timber source field displays disabled with the Schedule 3 value (AC3)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const crown = await screen.findByLabelText('Crown Timber Volume for all fields (Sch 3)')
    expect(crown).toBeDisabled()
    expect(crown).toHaveValue('54,321')
  })
})

describe('Schedule1 editable pulled/derived volumes (Story 2.6 / D2 reversal)', () => {
  test('143/144/139/140 volumes are editable and sent on Save; their costs stay read-only', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...schedule1Doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    const fma = await screen.findByLabelText('Forest Management Administration volume')
    await user.clear(fma)
    await user.type(fma, '111')
    const scl = screen.getByLabelText('Subtotal Company Logging volume')
    await user.type(scl, '222')
    const lessAdmin = screen.getByLabelText('Less Silviculture Admin Costs volume')
    await user.clear(lessAdmin)
    await user.type(lessAdmin, '77')
    const total = screen.getByLabelText('Total Silviculture (As per Financial Statements) volume')
    await user.type(total, '88')
    // Their cost cells are read-only (no cost inputs).
    expect(
      screen.queryByLabelText('Forest Management Administration Costs (Sch 3) cost'),
    ).toBeNull()
    expect(screen.queryByLabelText('Less Silviculture Admin Costs cost')).toBeNull()

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    await screen.findByText('Data saved successfully')

    const body = captured as {
      forestMgmtAdminVolume: number
      subtotalCompanyLoggingVolume: number
      silviculture: { lessAdminVolume: number; totalVolume: number }
    }
    expect(body.forestMgmtAdminVolume).toBe(111)
    expect(body.subtotalCompanyLoggingVolume).toBe(222)
    expect(body.silviculture.lessAdminVolume).toBe(77)
    expect(body.silviculture.totalVolume).toBe(88)
  })
})

describe('Schedule1 Check Status (Story 2.7)', () => {
  const CHECK_URL = 'http://localhost:3000/api/v1/schedule1/check-status'

  test('requirementsMet renders the verbatim SUC-003 success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: true,
          errors: [],
          warnings: [],
          message: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: /check status/i }))[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
  })

  test('missing-field errors render verbatim; Save stays enabled', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            { key: 'missingRequiredFieldMsg', text: 'Log Transportation - Volume: Value Required' },
          ],
          warnings: [],
          message: null,
        }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: /check status/i }))[0])
    expect(
      await screen.findByText('Log Transportation - Volume: Value Required'),
    ).toBeInTheDocument()
    // A failed check never blocks editing/saving.
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test('warnings render and do not block Save (S18)', async () => {
    const warnText =
      'Subtotal Other Costs (2) - Cost: One or more entries contain an empty Cost value. ' +
      'Please verify there are no Other Costs to be entered.'
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: true,
          errors: [],
          warnings: [
            { key: 'warning.schedule1.checkstatus.subtotalother.costEmpty', text: warnText },
          ],
          message: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: /check status/i }))[0])
    expect(await screen.findByText(warnText)).toBeInTheDocument()
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test('Check Status is disabled on a read-only schedule (S22)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ ...schedule1Doc, trackStatus: 'S', editable: false }),
      ),
    )
    render(<Schedule1 />)
    await screen.findByText('Standing Tree to Loaded Truck')
    screen
      .getAllByRole('button', { name: /check status/i })
      .forEach((b) => expect(b).toBeDisabled())
  })

  test('a prior check result is cleared after a successful Save (Task 3)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            { key: 'missingRequiredFieldMsg', text: 'Log Transportation - Volume: Value Required' },
          ],
          warnings: [],
          message: null,
        }),
      ),
      http.put(URL, () =>
        HttpResponse.json({
          ...schedule1Doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: /check status/i }))[0])
    expect(
      await screen.findByText('Log Transportation - Volume: Value Required'),
    ).toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    await screen.findByText('Data saved successfully')
    await waitFor(() =>
      expect(
        screen.queryByText('Log Transportation - Volume: Value Required'),
      ).not.toBeInTheDocument(),
    )
  })

  test('a failed check renders the verbatim ProblemDetail', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.post(CHECK_URL, () =>
        problemBody(409, 'This schedule cannot be edited in its current status.'),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: /check status/i }))[0])
    expect(
      await screen.findByText('This schedule cannot be edited in its current status.'),
    ).toBeInTheDocument()
  })
})

describe('Schedule1 Other Costs navigation (Story 2.5)', () => {
  test('clicking Subtotal Other Costs confirms then navigates to the sub-page (AC1)', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs/i }))
    // A Carbon Modal (not window.confirm) shows the verbatim discard-unsaved-edits text.
    const dialog = await screen.findByRole('dialog', { name: 'Leave Schedule 1' })
    expect(
      within(dialog).getByText(
        'Any unsaved data will be lost. Are you sure you would like to continue?',
      ),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /continue/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-1/other-costs' })
  })

  test('cancelling the confirm Modal does NOT navigate (editable)', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs/i }))
    const dialog = await screen.findByRole('dialog', { name: 'Leave Schedule 1' })
    await user.click(within(dialog).getByRole('button', { name: /cancel/i }))
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  test('read-only schedule opens the sub-page without a confirm', async () => {
    mockNavigate.mockClear()
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ ...schedule1Doc, trackStatus: 'S', editable: false }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs/i }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-1/other-costs' })
  })
})

describe('Schedule1 stale-response guard (Story 29.6)', () => {
  test('a mill/year change mid-save does not apply the stale response (AC1)', async () => {
    // The PUT is gated on an explicit release, not a wall-clock delay, so the "stale response settles
    // after the context change" ordering holds under any CI load. Routing save through the shared
    // useScheduleMutations run() gives Schedule 1 the isCurrent() guard it previously lacked.
    let releasePut = () => {}
    const putGate = new Promise<void>((resolve) => {
      releasePut = resolve
    })
    server.use(
      http.get(URL, ({ request }) =>
        request.url.includes('millId=999')
          ? HttpResponse.json({
              ...schedule1Doc,
              millId: 999,
              year: 2020,
              editable: false,
              comments: 'Context 999/2020 loaded',
            })
          : HttpResponse.json(schedule1Doc),
      ),
      http.put(URL, async () => {
        await putGate
        return HttpResponse.json({
          ...schedule1Doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )

    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    // Editable 514 loaded → dispatch the save (PUT now in flight) → switch mill/year before it settles.
    await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    await user.click(screen.getByRole('button', { name: /change/i }))

    // The new context's document has rendered (read-only 999/2020).
    expect(await screen.findByText('Context 999/2020 loaded')).toBeInTheDocument()

    // Release the stale PUT, let its chain settle, then confirm nothing from it landed on 999/2020.
    releasePut()
    await waitFor(() => {
      expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Standing Tree to Loaded Truck cost')).not.toBeInTheDocument()
    })
  })
})
