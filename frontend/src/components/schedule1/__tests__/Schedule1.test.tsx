import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import {
  declaredRole,
  render,
  renderAsAdmin,
  renderAsSubmitter,
  screen,
  waitFor,
  within,
} from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import { ILCR_ROLES, type IlcrRole } from '@/context/auth/mockUsers'

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

  test('Delete is disabled when the served document carries no revisionCount (defect #292)', async () => {
    // Legacy gated Delete on isScheduleOpen() — a persisted summary — as well as on edit rights
    // (schedule1.xhtml:803-804), and the shared bar now carries that rule via `scheduleSaved`.
    // This used to be unreachable through this page ("getSchedule1 404s when unsaved") and pinned
    // the rule rather than a user-visible state — defect #296 made it a REAL state, because the GET
    // now serves the empty editable document. An absent `revisionCount` (Jackson `non_null` omits
    // nulls) must NOT read as "saved". Schedule 2, whose GET always served an empty editable
    // document, is where the missing rule first became defect #292.
    const { revisionCount, ...unsavedDoc } = schedule1Doc
    expect(revisionCount).toBe(3) // guard: the fixture really did carry one to strip
    server.use(http.get(URL, () => HttpResponse.json(unsavedDoc)))
    render(<Schedule1 />)

    await screen.findByText('Standing Tree to Loaded Truck')
    const bars = document.querySelectorAll<HTMLElement>('.schedule-1__actions')
    expect(bars).toHaveLength(2)
    const bottom = within(bars[1])
    expect(bottom.getByRole('button', { name: 'Delete' })).toBeDisabled()
    // Entry is untouched — only the destructive action is withheld.
    expect(bottom.getByRole('button', { name: 'Save' })).toBeEnabled()
    expect(bottom.getByRole('button', { name: 'Check Status' })).toBeEnabled()
  })

  test('Other Costs on a never-saved schedule shows ALT-001 and does not navigate (#296)', async () => {
    // The gate this replaced tested `!data`, which was only ever falsy while the GET 404'd on an
    // unsaved schedule. Defect #296 removed that 404, so `data` is now truthy here and the gate has
    // to test SAVED instead — otherwise the click reaches a sub-page whose controller still requires
    // a summary (validateScheduleViewable, kept deliberately) and 404s. This is that gate.
    const { revisionCount, ...unsavedDoc } = schedule1Doc
    expect(revisionCount).toBe(3) // guard: the fixture really did carry one to strip
    server.use(http.get(URL, () => HttpResponse.json(unsavedDoc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    await screen.findByText('Standing Tree to Loaded Truck')
    mockNavigate.mockClear()
    await user.click(screen.getByRole('button', { name: /^Subtotal Other Costs\(\d+\):$/ }))

    // Legacy ALT-001, verbatim — and crucially NO navigation to the sub-page.
    expect(
      await screen.findByText('The schedule has to be saved before opening other costs'),
    ).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  test('a load error with no ProblemDetail detail falls back to the generic text (#296)', async () => {
    // `mapLoadErrorDetail` is `detail || 'Unable to load Schedule 1.'` since #296 removed the
    // client-composed sentence. The fallback arm had no coverage: every error fixture carried a
    // detail. A body-less failure (502/504 from the gateway, a dropped connection) takes this path.
    server.use(http.get(URL, () => new HttpResponse(null, { status: 502 })))
    render(<Schedule1 />)

    expect(await screen.findByText('Unable to load Schedule 1.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('a never-saved schedule issues NO DELETE request even if the confirm is reached (#296)', async () => {
    // The Schedule 3 twin of this was asked for by the PR #361 review; Schedule 1 had the guard but
    // no test either. It has to target `handleDelete`: the Delete BUTTON only opens the modal
    // (`onDelete={() => setConfirmDeleteOpen(true)}`) — `handleDelete` is the modal's
    // `onRequestSubmit` — so the guard is proven by reaching the confirm and asserting nothing goes
    // to the network. Since #296 the endpoint is idempotent and answers 200, so a stray delete would
    // announce success for a record that never existed.
    const { revisionCount, ...unsavedDoc } = schedule1Doc
    expect(revisionCount).toBe(3) // guard: the fixture really did carry one to strip
    let deleteCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(unsavedDoc)),
      http.delete(URL, () => {
        deleteCalled = true
        return HttpResponse.json({ message: { key: 'x', text: 'x' } })
      }),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    await screen.findByText('Standing Tree to Loaded Truck')
    const bars = document.querySelectorAll<HTMLElement>('.schedule-1__actions')
    expect(bars).toHaveLength(2)
    // Disabled for a real user; click it anyway to reach the modal — exactly the "any other route
    // into this handler" the guard exists for.
    await user.click(within(bars[1]).getByRole('button', { name: /^delete$/i }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete schedule' })
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(deleteCalled).toBe(false)
    expect(screen.queryByText(/deleted successfully/i)).not.toBeInTheDocument()
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

  // Defect #296 inverted this case. It used to assert that a 404 rendered a sentence composed in the
  // CLIENT ('No Schedule 1 exists for Mill 514 ...'), which broke AD-8, leaked the internal mill id,
  // and told the user to do something the app could not do. The backend no longer 404s for an unsaved
  // schedule at all, so what remains is the plain rule: whatever ProblemDetail text the API sends is
  // shown verbatim.
  test('a load error shows the API detail verbatim (AD-8), never a client-composed sentence', async () => {
    server.use(problemHandler(404, 'Schedule not found.'))
    // Explicit context so the render is deterministic regardless of the dev default mill/year.
    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        <Schedule1 />
      </MillYearProvider>,
    )

    expect(await screen.findByText('Schedule not found.')).toBeInTheDocument()
    expect(screen.queryByText(/No Schedule 1 exists for Mill/)).not.toBeInTheDocument()
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

// -------------------------------------------------------------------------------------------------
// Story 16.3 — the ministry correction journey. Schedule 1 is the representative page: it is the
// only one carrying the Subtotal-Other-Costs cross-field rule AND the non-blocking empty-cost
// warning, so all four Check Status result shapes are reachable here and nowhere else.
//
// Three things make this arm different from every other test in this file.
//
// 1. EVERY test declares WHO is acting, and then ASSERTS what identity the request carried.
//    `findMockUser(null)` falls through to `MOCK_USERS[0]` — the admin (mockUsers.ts:38) — so a
//    plain `render()` acts as a Ministry Administrator and says nothing about it. The 30
//    pre-existing `trackStatus: 'S'` tests all pair it with `editable: false`, which is the
//    SUBMITTER's answer at Submitted: the admin capability shipped with the matrix (Story 16.1) and
//    had no test at all.
// 2. Both the GET and the PUT echo compute `editable` from the 16.1 matrix over the CALLER'S roles,
//    instead of hardcoding a boolean. That makes the role declaration load-bearing —
//    `renderAsSubmitter` genuinely changes what the server answers — and stops a save echo
//    smuggling back an editability the matrix would not grant.
// 3. Entry is click+clear+paste, never `user.type`: this page mounts an editor per writable cell,
//    so per-character typing is O(fields x chars) and has timed the schedule suites out on CI.
//
// Editability stays server-authoritative throughout (AD-9) — the page reads `data.editable` and
// never the role or the status. Every user-facing string is asserted VERBATIM against
// `backend/src/main/resources/messages.properties` (AD-8).
// -------------------------------------------------------------------------------------------------

describe('Schedule1 ministry correction at Submitted (Story 16.3)', () => {
  const CHECK_URL = 'http://localhost:3000/api/v1/schedule1/check-status'

  // The PINNED 16.1 matrix (`ScheduleEditability`), per track status. Submitter edits at Draft only;
  // admin edits at Submitted and Verified and is DELIBERATELY read-only at Draft while the mill
  // still owns the data. Everything else is read-only, fail-closed: `O` (a declared status with no
  // branch in either legacy gate), a missing/null status row, and an unrecognised role.
  //
  // Reproduced here rather than imported because the real rule lives in Java: this is the wire
  // contract the frontend is entitled to assume, and stating it makes falsification trivial — flip
  // the admin entry to ['D'] and every admin-at-Submitted arm fails; narrow it to ['S'] and the
  // Verified arm fails.
  const EDITABLE_STATUSES: Record<string, readonly string[]> = {
    ILCR_ADMIN: ['S', 'V'],
    ILCR_SUBMITTER: ['D'],
  }

  /**
   * The acting role as the request actually carried it.
   *
   * The throw below is a HARNESS check and can never fire in a working harness — it is emphatically
   * NOT the guard against a forgotten `renderAsAdmin`/`renderAsSubmitter`. `api-service` resolves
   * identity through `findMockUser(localStorage…)`, which falls back to `MOCK_USERS[0]` (the ADMIN)
   * and joins its roles, so a header is ALWAYS sent (api-service.ts:11-17); its absence would mean
   * only that `isMockAuth()` is false, i.e. the whole harness is misconfigured.
   *
   * What catches a lost declaration is `expectActingAs()` below, and it needs BOTH halves. The wire
   * half alone cannot close the ADMIN direction: a declared admin and an undeclared one are
   * byte-identical on `X-Mock-Groups`, because the fallback IS the admin. `declaredRole()`
   * (test-utils.tsx) is what distinguishes them — it reports what THIS test seeded, and null when
   * nothing did, which is the one thing the two cases do not share.
   *
   * That is measured across the epic, not argued: with the wire assertion alone, dropping every
   * declaration broke 2 of 10 arms on Schedule 10, 2 of 11 on Schedule 4 and 0 of 13 on Schedule 11
   * — in each case only the SUBMITTER arms. With both halves it breaks all of them, and all 22 here.
   *
   * Both are asserted in the TEST BODY, never in a resolver: an `expect` that throws inside an MSW
   * resolver surfaces as a failed request, the page renders that as a load error, and the failure is
   * misattributed to the component.
   */
  const actingRole = (request: Request): string => {
    const header = request.headers.get('X-Mock-Groups')
    if (!header) {
      throw new Error(
        'no X-Mock-Groups header — isMockAuth() is false, the harness is misconfigured',
      )
    }
    return header
  }

  /**
   * `editable` as the SERVER computes it: the UNION of permitted statuses across every role the
   * caller holds (`ScheduleEditability.forCaller`), not a lookup on one role. api-service sends
   * `roles.join(',')` (api-service.ts:13), so the header is a comma list even though a mock user
   * holds exactly one role today — encoding the single-key lookup would double the wrong rule.
   *
   * An unrecognised token contributes nothing (fail closed, matching `Role.fromValue` returning
   * null). That is deliberately silent here, because it must NOT be the reason a read-only arm
   * passes: each arm pins the exact identity that was sent, so a typo'd or drifted role name fails
   * on the identity assertion rather than sliding through as a read-only document.
   */
  const matrixEditable = (roleHeader: string, trackStatus: unknown): boolean => {
    const permitted = new Set(
      roleHeader.split(',').flatMap((role) => EDITABLE_STATUSES[role.trim()] ?? []),
    )
    return permitted.has(String(trackStatus))
  }

  /** A Submitted Schedule 1, with per-test overrides. */
  const doc = (over: Record<string, unknown> = {}) => ({
    ...schedule1Doc,
    trackStatus: 'S',
    ...over,
  })

  // The identity the GET actually carried, captured per test and asserted in the body. Reset between
  // tests so a null here means "the page never loaded", not "a previous test's value lingered".
  let sentRole: string | null = null
  beforeEach(() => {
    sentRole = null
  })

  /**
   * The two-part identity assertion every role-declaring arm makes: "this test DECLARED this role"
   * AND "the request CARRIED it". `carried` is the capture for the verb under test — `sentRole` for
   * the load, or the arm's own `putRole` / `deleteRole` / `checkRole` — so a write can assert the
   * identity it was ISSUED under, not merely the one the page loaded under.
   *
   * Neither half is sufficient alone, and which half is weak depends on the direction:
   *   - the WIRE half misses a dropped `renderAsAdmin`, because the unseeded fallback IS the admin;
   *   - the DECLARATION half would miss a helper that seeded storage without api-service reading it.
   * See the note on `actingRole`.
   */
  const expectActingAs = (role: IlcrRole, carried: string | null) => {
    expect(declaredRole()).toBe(role)
    expect(carried).toBe(role)
  }

  /** GET that answers `editable` per the matrix for whoever is asking. */
  const matrixGet = (over: Record<string, unknown> = {}) =>
    http.get(URL, ({ request }) => {
      sentRole = actingRole(request)
      const body = doc(over)
      return HttpResponse.json({ ...body, editable: matrixEditable(sentRole, body.trackStatus) })
    })

  const COST_FIELD = 'Standing Tree to Loaded Truck cost'
  const VOLUME_FIELD = 'Standing Tree to Loaded Truck volume'
  const COMMENTS_FIELD = 'If you have any additional comments, please enter them here:'

  /**
   * Enter a value the way the sibling suites do — click, clear, paste. One input event instead of
   * one per character; see the header note on the CI timeout this avoids.
   */
  const enter = async (user: ReturnType<typeof userEvent.setup>, label: string, value: string) => {
    const field = screen.getByLabelText(label)
    await user.click(field)
    await user.clear(field)
    await user.paste(value)
  }

  /** Both action bars' buttons — the page renders the bar twice (Delete only on the bottom one). */
  const actionButtons = () => {
    const bars = document.querySelectorAll<HTMLElement>('.schedule-1__actions')
    expect(bars).toHaveLength(2)
    return [...bars].flatMap((bar) => within(bar).getAllByRole('button'))
  }

  /** The editable shape: live inputs and every action available. */
  const expectEditable = () => {
    expect(screen.getByLabelText(VOLUME_FIELD)).toBeEnabled()
    expect(screen.getByLabelText(COST_FIELD)).toBeEnabled()
    expect(screen.getByLabelText(COMMENTS_FIELD)).toBeEnabled()
    actionButtons().forEach((button) => expect(button).toBeEnabled())
  }

  /** The read-only shape: no inputs at all, and every action withheld. */
  const expectReadOnly = () => {
    expect(screen.queryByLabelText(VOLUME_FIELD)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(COST_FIELD)).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Subtotal Other Costs volume')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(COMMENTS_FIELD)).not.toBeInTheDocument()
    actionButtons().forEach((button) => expect(button).toBeDisabled())
    // The confirm modal is rendered only for an editable schedule, so there is no route to a DELETE
    // even by reaching past the disabled button.
    expect(
      screen.queryByText('This will delete the current record. Do you want to continue?'),
    ).not.toBeInTheDocument()
  }

  // ---- The capability itself ---------------------------------------------------------------------

  test('ADMIN at Submitted corrects and saves: SUC-001 verbatim, and the save does NOT move status', async () => {
    let putBody: Record<string, unknown> | null = null
    let putRole: string | null = null
    let putCount = 0
    server.use(
      matrixGet(),
      http.put(URL, async ({ request }) => {
        putCount += 1
        putRole = actingRole(request)
        putBody = (await request.json()) as Record<string, unknown>
        // The echo is STILL Submitted. There is exactly one status writer in the whole backend (the
        // year-open INSERT, ReportingYearRepository:139) and no transition endpoint at all, so the
        // save path cannot move a track — this echo is that contract, and the page must render the
        // corrected document as still-Submitted-and-still-correctable.
        //
        // `editable` comes from the SAME matrix computation as the GET rather than a hardcoded
        // `true`: an echo that asserts its own editability would make every post-save assertion
        // below self-fulfilling, and would let a real echo hand back rights the matrix denies.
        const echo = {
          ...schedule1Doc,
          trackStatus: 'S',
          revisionCount: 4,
          lineItems: [{ costItemCode: 12, volume: 1000, cost: 61000, perUnit: 61.0 }],
          comments: 'Corrected by the ministry',
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }
        return HttpResponse.json({
          ...echo,
          editable: matrixEditable(putRole, echo.trackStatus),
        })
      }),
    )
    renderAsAdmin(<Schedule1 />)
    const user = userEvent.setup()

    // The whole point of 16.1's admin row: at Submitted the fields are LIVE for this actor.
    await screen.findByLabelText(COST_FIELD)
    expectEditable()
    // Loaded AS the admin — not as whoever MOCK_USERS[0] happens to be.
    expectActingAs(ILCR_ROLES.admin, sentRole)

    await enter(user, COST_FIELD, '61000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    // SUC-001, verbatim from the API `message.text` (AD-8) — `dataSavedSuccesfullyInfoMsg`.
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(putCount).toBe(1)
    // The correction was ISSUED as the admin, not merely loaded as one.
    expectActingAs(ILCR_ROLES.admin, putRole)
    expect(putBody).not.toBeNull()
    // The optimistic-lock token the admin last read, and the corrected figure.
    expect(putBody).toMatchObject({ revisionCount: 3 })
    expect(
      (putBody as { lineItems: { costItemCode: number; cost: number | null }[] }).lineItems,
    ).toEqual(expect.arrayContaining([expect.objectContaining({ costItemCode: 12, cost: 61000 })]))
    // The client cannot ASK for a transition: there is no status field on the write contract at all.
    // Without this, a future request DTO could grow one and nothing would notice.
    expect(Object.keys(putBody as object)).not.toContain('trackStatus')
    expect(Object.keys(putBody as object)).not.toContain('status')

    // The echo was applied, and the page is still the editable Submitted document afterwards — the
    // save did not strand the admin on a read-only screen, and did not re-open the track as a Draft.
    expect(screen.getByLabelText(COMMENTS_FIELD)).toHaveValue('Corrected by the ministry')
    expect(screen.getByLabelText(COST_FIELD)).toHaveValue('61,000')
    expectEditable()
  })

  test('SUBMITTER at Submitted is fully read-only — inputs, Save, Check Status and Delete (negative arm)', async () => {
    // The arm that stops a WIDENED gate passing unnoticed: if `editable` ever became "anyone at
    // Submitted", every admin test above would still be green and only this one would fail.
    server.use(matrixGet())
    renderAsSubmitter(<Schedule1 />)

    expect(await screen.findByText('Standing Tree to Loaded Truck')).toBeInTheDocument()
    // Acting as the submitter — the assertion that makes this a real negative arm rather than an
    // admin arm with a read-only fixture.
    expectActingAs(ILCR_ROLES.submitter, sentRole)
    expectReadOnly()
  })

  test('ADMIN at Draft is read-only — the capability 16.1 deliberately REMOVED (bidirectional)', async () => {
    // Completing the matrix was two-directional: it ADDED admin@Submitted and REMOVED the
    // admin@Draft edit the pre-epic blanket gate allowed. The mill still owns its Draft data.
    server.use(matrixGet({ trackStatus: 'D' }))
    renderAsAdmin(<Schedule1 />)

    expect(await screen.findByText('Standing Tree to Loaded Truck')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, sentRole)
    expectReadOnly()
  })

  test('SUBMITTER at Draft still edits — the matrix discriminates, it is not uniformly closed', async () => {
    // Guards the two tests above against a degenerate handler (or a broken identity helper) that
    // simply answered `editable: false` to everything.
    server.use(matrixGet({ trackStatus: 'D' }))
    renderAsSubmitter(<Schedule1 />)

    await screen.findByLabelText(COST_FIELD)
    expectActingAs(ILCR_ROLES.submitter, sentRole)
    expectEditable()
  })

  // ---- The rest of the administrator row, and the fail-closed cells -----------------------------
  //
  // `trackStatus: 'V'` is served NOWHERE else in the repo, so without this the second half of the
  // administrator row has no frontend evidence at all: narrowing the backend matrix to `['S']` would
  // leave every suite green. `O` and a null/missing status row are the two cells legacy left
  // implicit and 16.1 pinned as read-only (an `O` with no branch in either legacy gate; a null that
  // `valueOfByValue` swallows to INVALID).

  test('ADMIN at Verified can still correct — the other half of the administrator row', async () => {
    server.use(matrixGet({ trackStatus: 'V' }))
    renderAsAdmin(<Schedule1 />)

    await screen.findByLabelText(COST_FIELD)
    expectActingAs(ILCR_ROLES.admin, sentRole)
    expectEditable()
  })

  test.each([
    ['O (Opened) — a declared status with no branch in either legacy gate', 'O'],
    ['a missing/null status row — legacy swallowed it to INVALID', null],
  ])('ADMIN is read-only at %s, fail-closed', async (_name, trackStatus) => {
    server.use(matrixGet({ trackStatus }))
    renderAsAdmin(<Schedule1 />)

    expect(await screen.findByText('Standing Tree to Loaded Truck')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, sentRole)
    expectReadOnly()
  })

  test('SUBMITTER at Verified is read-only too — the admin row is not a general widening', async () => {
    server.use(matrixGet({ trackStatus: 'V' }))
    renderAsSubmitter(<Schedule1 />)

    expect(await screen.findByText('Standing Tree to Loaded Truck')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.submitter, sentRole)
    expectReadOnly()
  })

  // ---- Validation parity: the five FLD classes, each refusing the correction ---------------------
  //
  // Legacy has ONE validator class (`ILCRCostValidator`); the 7- and 8-digit volume bounds are stock
  // `f:validateDoubleRange` with a `validatorMessage` override. The RANGES and the MESSAGES are what
  // matter, and all five strings below are the bundle's, verbatim:
  //   costValidatorErrorMsg        :37
  //   volume7DigitValidatorErrorMsg:40
  //   volume8DigitValidatorErrorMsg:41
  // plus the two converter messages the frontend mirrors for a non-numeric entry.

  test.each([
    [
      'a cost beyond the 8-digit band',
      COST_FIELD,
      '100000000',
      'Entered cost must be between -99,999,999 and 99,999,999.',
      '100,000,000',
    ],
    [
      'a 7-digit volume beyond ±9,999,999',
      VOLUME_FIELD,
      '10000000',
      'Entered volume must be between -9,999,999 and 9,999,999.',
      '10,000,000',
    ],
    [
      'an 8-digit volume beyond ±99,999,999',
      'Subtotal Other Costs volume',
      '100000000',
      'Entered volume must be between -99,999,999 and 99,999,999.',
      '100,000,000',
    ],
    ['a non-numeric cost', COST_FIELD, 'abc', 'Entered cost is invalid.', 'abc'],
    ['a non-numeric volume', VOLUME_FIELD, 'abc', 'Entered volume entry is invalid.', 'abc'],
  ])(
    'ADMIN at Submitted: %s is refused with its verbatim message and NOTHING is persisted',
    async (_name, label, value, message, displayed) => {
      let putCalled = false
      server.use(
        matrixGet(),
        http.put(URL, () => {
          putCalled = true
          return problemBody(400, 'the save gate should have blocked this')
        }),
      )
      renderAsAdmin(<Schedule1 />)
      const user = userEvent.setup()

      await screen.findByLabelText(label)
      await enter(user, label, value)
      await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

      expect(await screen.findByText(message)).toBeInTheDocument()
      expectActingAs(ILCR_ROLES.admin, sentRole)
      expect(putCalled).toBe(false)
      // A refused save must not discard what the operator entered (epic constraint). A numeric
      // entry is re-grouped by the blur the Save click caused; invalid text is left exactly as
      // typed, so the inline error still points at what the operator actually wrote.
      expect(screen.getByLabelText(label)).toHaveValue(displayed)
    },
  )

  test('ADMIN at Submitted: a MIXED valid-and-invalid correction issues NO PUT — the valid edit is not persisted either', async () => {
    // CHK-010 S15. The gate is all-or-nothing on purpose: the backend's own save is
    // `@Transactional`, so a partial persist would be a divergence in both directions. Without this
    // test, "block the doomed round-trip" could be implemented per-field and look fine.
    let putCalled = false
    server.use(
      matrixGet(),
      http.put(URL, () => {
        putCalled = true
        return problemBody(400, 'no PUT may be issued when any field is invalid')
      }),
    )
    renderAsAdmin(<Schedule1 />)
    const user = userEvent.setup()

    await screen.findByLabelText(COST_FIELD)
    // One perfectly good correction, beside one that cannot be parsed.
    await enter(user, COST_FIELD, '61000')
    await enter(user, VOLUME_FIELD, 'abc')

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(await screen.findByText('Entered volume entry is invalid.')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, sentRole)
    // THE assertion: not "the invalid field was rejected" but "no request was made at all".
    expect(putCalled).toBe(false)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    // Both entries survive on screen for the operator to fix — nothing is discarded, nothing is saved.
    expect(screen.getByLabelText(COST_FIELD)).toHaveValue('61,000')
    expect(screen.getByLabelText(VOLUME_FIELD)).toHaveValue('abc')
  })

  // ---- Delete at Submitted, behind the verbatim confirm ------------------------------------------

  test('ADMIN at Submitted: Delete confirms with confirmDeleteMsg verbatim, then DELETEs', async () => {
    let deleteRole: string | null = null
    let deleteCount = 0
    server.use(
      matrixGet(),
      http.delete(URL, ({ request }) => {
        deleteCount += 1
        deleteRole = actingRole(request)
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    renderAsAdmin(<Schedule1 />)
    const user = userEvent.setup()

    await screen.findByLabelText(VOLUME_FIELD)
    const bars = document.querySelectorAll<HTMLElement>('.schedule-1__actions')
    await user.click(within(bars[1]).getByRole('button', { name: /^delete$/i }))

    // confirmDeleteMsg (messages.properties:202), verbatim. The modal's heading and button labels
    // are NOT asserted as legacy's — this page renders "Delete schedule" / Delete / Cancel where
    // legacy rendered "Confirmation" / Yes / No, a recorded divergence left unfixed by user ruling.
    const dialog = await screen.findByRole('dialog', { name: 'Delete schedule' })
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(deleteCount).toBe(1)
    expectActingAs(ILCR_ROLES.admin, sentRole)
    expectActingAs(ILCR_ROLES.admin, deleteRole)
  })

  test('ADMIN at Submitted: CANCELLING the confirm issues NO DELETE and leaves the document intact', async () => {
    let deleteCalled = false
    server.use(
      matrixGet(),
      http.delete(URL, () => {
        deleteCalled = true
        return HttpResponse.json({ message: { key: 'x', text: 'x' } })
      }),
    )
    renderAsAdmin(<Schedule1 />)
    const user = userEvent.setup()

    await screen.findByLabelText(VOLUME_FIELD)
    const bars = document.querySelectorAll<HTMLElement>('.schedule-1__actions')
    await user.click(within(bars[1]).getByRole('button', { name: /^delete$/i }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete schedule' })
    await user.click(within(dialog).getByRole('button', { name: /^cancel$/i }))

    expect(deleteCalled).toBe(false)
    expect(screen.queryByText('Data deleted successfully')).not.toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, sentRole)
    // The schedule is still there, unchanged and still editable.
    expect(screen.getByLabelText(COST_FIELD)).toHaveValue('50,000')
    expect(screen.getByLabelText(VOLUME_FIELD)).toHaveValue('1,000')
  })

  // ---- Check Status at Submitted: all four result shapes, read-only ------------------------------

  /** Click the first Check Status button and return the user-event session. */
  const runCheck = async () => {
    const user = userEvent.setup()
    const buttons = await screen.findAllByRole('button', { name: /check status/i })
    buttons.forEach((button) => expect(button).toBeEnabled())
    await user.click(buttons[0])
    return user
  }

  test('ADMIN at Submitted: an all-met check renders scheduleRequirementsMetMsg verbatim', async () => {
    let checkRole: string | null = null
    let writes = 0
    server.use(
      matrixGet(),
      http.put(URL, () => {
        writes += 1
        return problemBody(400, 'a check must not write')
      }),
      http.delete(URL, () => {
        writes += 1
        return problemBody(400, 'a check must not write')
      }),
      http.post(CHECK_URL, ({ request }) => {
        checkRole = actingRole(request)
        return HttpResponse.json({
          requirementsMet: true,
          errors: [],
          warnings: [],
          message: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        })
      }),
    )
    renderAsAdmin(<Schedule1 />)

    // The served figures BEFORE the check, so "unchanged" is a comparison and not an assumption.
    expect(await screen.findByLabelText(COST_FIELD)).toHaveValue('50,000')
    await runCheck()

    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, sentRole)
    expectActingAs(ILCR_ROLES.admin, checkRole)
    // The document is untouched by a check: same values, no write of any kind, still editable.
    expect(screen.getByLabelText(COST_FIELD)).toHaveValue('50,000')
    expect(screen.getByLabelText(VOLUME_FIELD)).toHaveValue('1,000')
    expect(screen.getByLabelText(COMMENTS_FIELD)).toHaveValue('Seed comment for 514/2021')
    expect(writes).toBe(0)
    expect(screen.getAllByRole('button', { name: /^save$/i })[0]).toBeEnabled()
  })

  test('ADMIN at Submitted: per-field misses render "{label}: Value Required" verbatim', async () => {
    // `missingRequiredFieldMsg` (:54) is the bare "Value Required"; the server composes
    // `label + ": " + text` (Schedule1Service.valueRequired), so the LABEL prefix is part of the
    // verbatim string the page must not rewrite.
    server.use(
      matrixGet(),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            { key: 'missingRequiredFieldMsg', text: 'Log Transportation - Volume: Value Required' },
            { key: 'missingRequiredFieldMsg', text: 'Log Transportation - Cost: Value Required' },
          ],
          warnings: [],
          message: null,
        }),
      ),
    )
    renderAsAdmin(<Schedule1 />)
    await screen.findByLabelText(COST_FIELD)
    await runCheck()

    expect(
      await screen.findByText('Log Transportation - Volume: Value Required'),
    ).toBeInTheDocument()
    expect(screen.getByText('Log Transportation - Cost: Value Required')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, sentRole)
    // A failed check never blocks the correction it is reporting on.
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test.each([
    [
      'the cost side',
      'sch1.subtotal.other.costs.costs.grearter.than.zero',
      'Subtotal Other Costs (2): Cost: must be greater than 0 when Volume is greater than 0',
    ],
    [
      'the volume side',
      'sch1.subtotal.other.costs.volume.grearter.than.zero',
      'Subtotal Other Costs (2): Volume: must be greater than 0 when Cost is greater than 0',
    ],
  ])(
    'ADMIN at Submitted: the Subtotal-Other-Costs cross-field error renders verbatim — %s',
    async (_side, key, text) => {
      // The one cross-field rule in the twelve schedules, and the reason Schedule 1 is the
      // representative page. The backend emits ONE direction per check (it is an if/else over the
      // whole-number volume vs the itemized cost sum), so each direction is its own case. Note the
      // legacy misspelling in the KEY ("grearter") — kept verbatim, as the bundle header instructs.
      server.use(
        matrixGet(),
        http.post(CHECK_URL, () =>
          HttpResponse.json({
            requirementsMet: false,
            errors: [{ key, text }],
            warnings: [],
            message: null,
          }),
        ),
      )
      renderAsAdmin(<Schedule1 />)
      await screen.findByLabelText(COST_FIELD)
      await runCheck()

      expect(await screen.findByText(text)).toBeInTheDocument()
      expectActingAs(ILCR_ROLES.admin, sentRole)
    },
  )

  test('ADMIN at Submitted: the empty-cost WARNING renders verbatim with {0} substituted, and blocks nothing', async () => {
    // `warning.schedule1.checkstatus.subtotalother.costEmpty` (:198) is a MessageFormat template;
    // the server substitutes the itemized-row count for {0} before serving it, so the page must
    // render the resolved sentence and never the template or the key.
    const warning =
      'Subtotal Other Costs (2) - Cost: One or more entries contain an empty Cost value. ' +
      'Please verify there are no Other Costs to be entered.'
    server.use(
      matrixGet(),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          // Non-blocking: `requirementsMet` is computed from the ERRORS alone, so a warning still
          // reports the schedule as met.
          requirementsMet: true,
          errors: [],
          warnings: [
            { key: 'warning.schedule1.checkstatus.subtotalother.costEmpty', text: warning },
          ],
          message: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        }),
      ),
    )
    renderAsAdmin(<Schedule1 />)
    await screen.findByLabelText(COST_FIELD)
    await runCheck()

    expect(await screen.findByText(warning)).toBeInTheDocument()
    expect(warning).toContain('(2)')
    expect(warning).not.toContain('{0}')
    expectActingAs(ILCR_ROLES.admin, sentRole)
    // Met despite the warning, and the correction surface stays open.
    expect(screen.getByText('All requirements for this schedule have been met')).toBeInTheDocument()
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test('SUBMITTER at Submitted cannot run a check at all — the button is disabled', async () => {
    // Legacy disabled Check Status whenever the report was not editable by the caller (26 of 26
    // buttons); the UI matches, even though the endpoint itself is deliberately VIEW-gated.
    server.use(matrixGet())
    renderAsSubmitter(<Schedule1 />)

    await screen.findByText('Standing Tree to Loaded Truck')
    expectActingAs(ILCR_ROLES.submitter, sentRole)
    screen
      .getAllByRole('button', { name: /check status/i })
      .forEach((button) => expect(button).toBeDisabled())
  })
})
