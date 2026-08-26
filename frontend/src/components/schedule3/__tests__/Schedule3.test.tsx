import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider (AppProviders has none). Mock the router
// like Schedule1.test.tsx; a hoisted navigate spy lets the sub-page navigation test assert the target.
const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule3 from '@/components/schedule3'
import MillYearProvider from '@/context/millYear/MillYearProvider'

const URL = 'http://localhost:3000/api/v1/schedule3'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule3/check-status'

// Canonical Schedule 3 document (matches the pinned 4.1/4.2 wire contract). Both-columns lines carry
// harvest+pop+crown; Annual Rents (29) & Silviculture Admin (37) are Harvest-only (pop null); Scaling
// (33) carries a server-derived pop.
const schedule3Doc = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  revisionCount: 3,
  overrideHarvestTotalPop: 'N',
  comments: 'Seed comment for 514/2021',
  lineItems: [
    { costItemCode: 27, harvest: 1000, pop: 400, crown: 600 },
    { costItemCode: 28, harvest: 800, pop: 300, crown: 500 },
    { costItemCode: 29, harvest: 250, pop: 0, crown: 250 },
    { costItemCode: 30, harvest: 1200, pop: 500, crown: 700 },
    { costItemCode: 31, harvest: 600, pop: 200, crown: 400 },
    { costItemCode: 32, harvest: 700, pop: 250, crown: 450 },
    { costItemCode: 33, harvest: 900, pop: 100, crown: 800 },
    { costItemCode: 34, harvest: 400, pop: 150, crown: 250 },
    { costItemCode: 35, harvest: 350, pop: 120, crown: 230 },
    { costItemCode: 36, harvest: 500, pop: 180, crown: 320 },
    { costItemCode: 37, harvest: 150, pop: null, crown: 150 },
  ],
  popTimber: { volume: 5000, cost: 20000, perUnit: 4.0 },
  crownTimber: { volume: 7000, cost: 28000, perUnit: 4.0 },
  totalOverhead: { volume: 12000, cost: 48000, perUnit: 4.0 },
  subtotalOtherCosts: { harvest: 1500, pop: 600, crown: 900 },
  subtotalActualCosts: { harvest: 8850, pop: 3100, crown: 5750 },
  includedUnacceptableCosts: { harvest: 250, pop: 0, crown: 250 },
  totalCosts: { harvest: 8600, pop: 3100, crown: 5500 },
  otherAcceptableCount: 2,
  unacceptableCount: 1,
  warnings: [],
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

describe('Schedule3 editable page (AC1/AC2)', () => {
  // ---- Defect #291: derived figures track data entry, on blur, before Save. -----------------------
  //
  // A SELF-CONSISTENT fixture is used here rather than `schedule3Doc`, whose stored derived values do
  // not satisfy the server's own formulas (its Scaling PO&P is 100 where the ratio gives 375, and its
  // Subtotal Actual harvest is 8850 where the columns sum to 8350). Asserting a mirror against numbers
  // the server would never produce would prove nothing.
  //
  // Entered: line 27 = 870000/300000, Annual Rents (29) = 30000, PO&P Timber volume 54321, Crown
  // Timber volume 54321 -> overhead 108642. That reproduces
  // Schedule3ServiceTest.fullDocument_derivedCascadeMatchesLegacy.
  const consistentDoc = {
    ...schedule3Doc,
    lineItems: [
      { costItemCode: 27, harvest: 870000, pop: 300000, crown: 570000 },
      { costItemCode: 29, harvest: 30000, pop: 0, crown: 30000 },
    ],
    popTimber: { volume: 54321, cost: 300000, perUnit: 5.52 },
    crownTimber: { volume: 54321, cost: 570000, perUnit: 10.49 },
    totalOverhead: { volume: 108642, cost: 870000, perUnit: 8.01 },
    subtotalOtherCosts: { harvest: 0, pop: 0, crown: 0 },
    subtotalActualCosts: { harvest: 900000, pop: 300000, crown: 600000 },
    includedUnacceptableCosts: { harvest: 30000, pop: 0, crown: 30000 },
    totalCosts: { harvest: 870000, pop: 300000, crown: 570000 },
    otherAcceptableCount: 0,
    unacceptableCount: 1,
  }

  /** A three-column row's cells as text: [label, harvest, PO&P, crown]. */
  const rowCells = (label: string | RegExp) => {
    const tr = screen.getByText(label).closest('tr')
    if (!tr) throw new Error(`no row for "${String(label)}"`)
    return within(tr)
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
  }
  const crownOf = (label: string | RegExp) => rowCells(label)[3]

  test('the served figures are reproduced exactly on load, before any edit (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(consistentDoc)))
    render(<Schedule3 />)
    await screen.findByText('Licenses, Fees, Insurance')

    // The mirror is already driving these cells; they must equal what the server sent.
    expect(rowCells('Subtotal (Actual Costs)')).toEqual([
      'Subtotal (Actual Costs)',
      '900,000',
      '300,000',
      '600,000',
    ])
    expect(rowCells('Total Costs')).toEqual(['Total Costs', '870,000', '300,000', '570,000'])
    expect(rowCells('Total Overhead')).toEqual(['Total Overhead', '108,642', '870,000', '8.01'])
  })

  test('typing alone moves nothing; blurring a harvest recalculates the cascade (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(consistentDoc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    const harvest = await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    await user.clear(harvest)
    await user.type(harvest, '900000')
    expect(crownOf('Licenses, Fees, Insurance')).toBe('570,000') // not per keystroke

    await user.tab()
    expect(crownOf('Licenses, Fees, Insurance')).toBe('600,000') // 900000 − 300000
    // Subtotal harvest 930000; Total Costs harvest 930000 − 30000 = 900000.
    expect(rowCells('Subtotal (Actual Costs)')[1]).toBe('930,000')
    expect(rowCells('Total Costs')[1]).toBe('900,000')
    // The PO&P column is untouched, so the timber costs hold.
    expect(rowCells('Total Costs')[2]).toBe('300,000')
  })

  test("Scaling's derived PO&P tracks BOTH timber volumes and its own harvest (#291)", async () => {
    server.use(http.get(URL, () => HttpResponse.json(consistentDoc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    // No Scaling harvest yet -> its PO&P and crown are blank.
    const scaling = await screen.findByLabelText('Scaling Expense Harvest')
    expect(rowCells('Scaling Expense')[2]).toBe('—')

    // ratio = 54321/108642 = 0.5; 0.5 * 60000 = 30000.
    await user.type(scaling, '60000')
    await user.tab()
    expect(rowCells('Scaling Expense')[2]).toBe('30,000')
    expect(crownOf('Scaling Expense')).toBe('30,000')
    // ...and it feeds the PO&P subtotal: 300000 + 30000.
    expect(rowCells('Subtotal (Actual Costs)')[2]).toBe('330,000')

    // Changing the CROWN timber volume moves the ratio, so Scaling's PO&P moves with it:
    // 54321/(54321 + 162963) = 0.25 -> 15000.
    const crownVolume = screen.getByLabelText('Crown Timber Harvest Volume')
    await user.clear(crownVolume)
    await user.type(crownVolume, '162963')
    await user.tab()
    expect(rowCells('Scaling Expense')[2]).toBe('15,000')
    expect(rowCells('Subtotal (Actual Costs)')[2]).toBe('315,000')
  })

  test('Included Unacceptable Costs tracks the Annual Rents harvest, count included (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(consistentDoc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    const annualRents = await screen.findByLabelText('Annual Rents Harvest')
    expect(rowCells(/^Included Unacceptable Costs \(1\):$/)[1]).toBe('30,000')

    // The S111 alert fires on this field's blur (legacy onchange) — swallow it.
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
    try {
      await user.clear(annualRents)
      await user.type(annualRents, '50000')
      await user.tab()
      // The item-38 sub-page rows contribute 0 here, so the total is the entered value.
      expect(rowCells(/^Included Unacceptable Costs \(1\):$/)[1]).toBe('50,000')
      // Total Costs = 900000 subtotal (harvest 870000 + 50000 rents = 920000) − 50000 = 870000.
      expect(rowCells('Total Costs')[1]).toBe('870,000')

      // Clearing it drops the +1 from the sub-page link count as well.
      await user.clear(screen.getByLabelText('Annual Rents Harvest'))
      await user.tab()
      expect(rowCells(/^Included Unacceptable Costs \(0\):$/)[1]).toBe('0')
    } finally {
      alertSpy.mockRestore()
    }
  })

  test('a timber volume blur recalculates its cost per unit (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(consistentDoc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    const popVolume = await screen.findByLabelText(
      'Privately Owned & Purchased (PO&P) Timber Harvest Volume',
    )
    await user.clear(popVolume)
    await user.type(popVolume, '100000')
    await user.tab()

    // PO&P timber cost is still 300000 (the PO&P column did not change), over the new volume.
    expect(rowCells('Privately Owned & Purchased (PO&P) Timber')[3]).toBe('3.00')
    // Total Overhead volume = 100000 + 54321 = 154321; 870000/154321 = 5.6376… -> 5.64.
    expect(rowCells('Total Overhead')[1]).toBe('154,321')
    expect(rowCells('Total Overhead')[3]).toBe('5.64')
  })

  test('the mirror equals the SERVER figures, before and after Save (#291 AC5)', async () => {
    // Asserted against the echo's own derived fields rather than a snapshot of the pre-Save render —
    // an editable page always renders the mirror, so render-to-render compared the mirror with itself
    // (code review 2026-08-21).
    server.use(
      http.get(URL, () => HttpResponse.json(consistentDoc)),
      http.put(URL, () =>
        HttpResponse.json({
          ...consistentDoc,
          revisionCount: 4,
          lineItems: [
            { costItemCode: 27, harvest: 900000, pop: 300000, crown: 600000 },
            { costItemCode: 29, harvest: 30000, pop: 0, crown: 30000 },
          ],
          subtotalActualCosts: { harvest: 930000, pop: 300000, crown: 630000 },
          totalCosts: { harvest: 900000, pop: 300000, crown: 600000 },
          crownTimber: { volume: 54321, cost: 600000, perUnit: 11.05 },
          totalOverhead: { volume: 108642, cost: 900000, perUnit: 8.28 },
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    render(<Schedule3 />)
    const user = userEvent.setup()

    const harvest = await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    await user.clear(harvest)
    await user.type(harvest, '900000')
    await user.tab()

    // The mirror must already agree with the figures the echo will carry.
    const expected = {
      subtotal: ['Subtotal (Actual Costs)', '930,000', '300,000', '630,000'],
      totals: ['Total Costs', '900,000', '300,000', '600,000'],
      crownTimber: ['Crown Timber', 'Crown Timber Harvest Volume', '600,000', '11.05'],
      overhead: ['Total Overhead', '108,642', '900,000', '8.28'],
    }
    expect(rowCells('Subtotal (Actual Costs)')).toEqual(expected.subtotal)
    expect(rowCells('Total Costs')).toEqual(expected.totals)
    expect(rowCells('Crown Timber')).toEqual(expected.crownTimber)
    expect(rowCells('Total Overhead')).toEqual(expected.overhead)

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    expect(rowCells('Subtotal (Actual Costs)')).toEqual(expected.subtotal)
    expect(rowCells('Total Costs')).toEqual(expected.totals)
    expect(rowCells('Crown Timber')).toEqual(expected.crownTimber)
    expect(rowCells('Total Overhead')).toEqual(expected.overhead)
  })

  test('view mode renders the document figures as-is — no client recomputation (#291 AC7)', async () => {
    // Stored totals that deliberately disagree with the line items: a recomputing view would show
    // 900,000 rather than the server's figure.
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...consistentDoc,
          trackStatus: 'S',
          editable: false,
          subtotalActualCosts: { harvest: 111111, pop: 222222, crown: -111111 },
        }),
      ),
    )
    render(<Schedule3 />)

    expect(await screen.findByText('Subtotal (Actual Costs)')).toBeInTheDocument()
    expect(rowCells('Subtotal (Actual Costs)')).toEqual([
      'Subtotal (Actual Costs)',
      '111,111',
      '222,222',
      '-111,111',
    ])
  })

  test('editable:true renders the three-column layout; crown/perUnit stay read-only', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)

    // Both-columns line (27) has Harvest AND PO&P inputs seeded from the doc.
    const harvest = await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    // Editable numeric fields display grouped values, like the read-only cells beside them.
    expect(harvest).toHaveValue('1,000')
    expect(screen.getByLabelText('Licenses, Fees, Insurance PO&P')).toHaveValue('400')
    // Crown is server-derived, read-only text (not an input) — scoped to the line's row.
    const row = screen.getByText('Licenses, Fees, Insurance').closest('tr')
    expect(within(row as HTMLElement).getByText('600')).toBeInTheDocument()
    // Timber volume entry + read-only derived cost/perUnit.
    expect(screen.getByLabelText('Crown Timber Harvest Volume')).toHaveValue('7,000')
    // Override menu defaults to No; Comments editable; Save enabled (top + bottom).
    expect(screen.getByLabelText('Override Harvest ⁄ Total PO&P $')).toHaveValue('N')
    expect(
      screen.getByLabelText('If you have any additional comments, please enter them here:'),
    ).toHaveValue('Seed comment for 514/2021')
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
    // Sub-page links show their counts.
    expect(screen.getByRole('button', { name: /Subtotal Other Costs \(2\):/ })).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /Included Unacceptable Costs \(1\):/ }),
    ).toBeInTheDocument()
  })

  test('Harvest-only lines (29/37) and Scaling (33) expose no PO&P input', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)
    await screen.findByLabelText('Annual Rents Harvest')

    // Annual Rents & Silviculture Admin: Harvest entry only, no PO&P input.
    expect(screen.getByLabelText('Annual Rents Harvest')).toBeInTheDocument()
    expect(screen.queryByLabelText('Annual Rents PO&P')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Silviculture Admin Costs Harvest')).toBeInTheDocument()
    expect(screen.queryByLabelText('Silviculture Admin Costs PO&P')).not.toBeInTheDocument()
    // Scaling: Harvest entry, PO&P is server-derived (read-only text, no input).
    expect(screen.getByLabelText('Scaling Expense Harvest')).toBeInTheDocument()
    expect(screen.queryByLabelText('Scaling Expense PO&P')).not.toBeInTheDocument()
    // AC2: Annual Rents PO&P renders blank (—), NOT the backend's 0.
    const annualRow = screen.getByText('Annual Rents').closest('tr') as HTMLElement
    expect(within(annualRow).getByText('—')).toBeInTheDocument()
    expect(within(annualRow).queryByText('0')).not.toBeInTheDocument()
  })

  test('editing Annual Rents Harvest raises the S111 alert (ALT-001)', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined)
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    const annual = await screen.findByLabelText('Annual Rents Harvest')
    await user.clear(annual)
    await user.type(annual, '999')
    await user.tab() // blur → legacy onchange
    expect(alertSpy).toHaveBeenCalledWith(
      'Annual Rent (Forest Act, S111) is recorded as an Unacceptable Cost.',
    )
    alertSpy.mockRestore()
  })

  test('editable:false renders read-only + disables actions (S15/STA-001)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ ...schedule3Doc, trackStatus: 'S', editable: false }),
      ),
    )
    render(<Schedule3 />)

    expect(await screen.findByText('Licenses, Fees, Insurance')).toBeInTheDocument()
    expect(screen.queryByLabelText('Licenses, Fees, Insurance Harvest')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Override Harvest ⁄ Total PO&P $')).toBeDisabled()
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /delete/i }).forEach((b) => expect(b).toBeDisabled())
  })
})

describe('Schedule3 Save / Delete (AC4/AC5)', () => {
  test('valid Save PUTs entered-fields-only + revisionCount and shows the API message', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(schedule3Doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...schedule3Doc,
          revisionCount: 4,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          warnings: [
            { key: 'crownVolumeChangeSchedule1', text: 'Crown volume applied to Schedule 1.' },
          ],
        })
      }),
    )
    render(<Schedule3 />)
    const user = userEvent.setup()

    const pop = await screen.findByLabelText('Licenses, Fees, Insurance PO&P')
    await user.clear(pop)
    await user.type(pop, '450')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    // SUC-001 verbatim from the API message.text (AD-8).
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // WRN-001 crown-push outcome rides the echo warnings channel.
    expect(screen.getByText('Crown volume applied to Schedule 1.')).toBeInTheDocument()
    // Request carried the optimistic-lock token + all 11 harvest codes + override + timber volumes.
    const body = captured as {
      revisionCount: number
      overrideHarvestTotalPop: string
      lineItems: { costItemCode: number; harvest: number | null; pop: number | null }[]
      crownTimberVolume: number | null
    }
    expect(body.revisionCount).toBe(3)
    expect(body.overrideHarvestTotalPop).toBe('N')
    expect(body.lineItems.map((li) => li.costItemCode)).toEqual([
      27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
    ])
    // Harvest-only lines carry a null pop; the edited both-columns line carries the entered pop.
    expect(body.lineItems.find((li) => li.costItemCode === 29)?.pop).toBeNull()
    expect(body.lineItems.find((li) => li.costItemCode === 27)?.pop).toBe(450)
    expect(body.crownTimberVolume).toBe(7000)
  })

  test('out-of-range value is blocked client-side (advisory) — inline error, no PUT', async () => {
    let putCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(schedule3Doc)),
      http.put(URL, () => {
        putCalled = true
        return problemBody(400, 'server should not be reached')
      }),
    )
    render(<Schedule3 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    await user.clear(cost)
    await user.type(cost, '100000000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(
      await screen.findByText('Entered cost must be between -99,999,999 and 99,999,999.'),
    ).toBeInTheDocument()
    expect(putCalled).toBe(false)
    // Clicking Save blurred the field, which re-grouped it — and the advisory range check above
    // still fired, proving validation parses the grouped string rather than choking on the commas.
    expect(screen.getByLabelText('Licenses, Fees, Insurance Harvest')).toHaveValue('100,000,000')
  })

  test('500 save failure shows ERR-001 and a retry re-submits (S17)', async () => {
    let attempts = 0
    server.use(
      http.get(URL, () => HttpResponse.json(schedule3Doc)),
      http.put(URL, () => {
        attempts += 1
        return attempts === 1
          ? problemBody(500, 'Schedule could not be saved.')
          : HttpResponse.json({
              ...schedule3Doc,
              message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
            })
      }),
    )
    render(<Schedule3 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Schedule could not be saved.')).toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(attempts).toBe(2)
  })

  test('Save and Check Status sit above AND below; Delete only below (schedule3.xhtml:37-38 vs :420-426)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)

    await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    // Scoped to the two action bars: this page keeps a delete-confirm AND a "Leave Schedule 3" modal in
    // the DOM while closed, so an unscoped name query cannot tell the bars apart from the dialogs.
    const bars = document.querySelectorAll<HTMLElement>('.schedule-3__actions')
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

  test('Delete is disabled when the served document carries no revisionCount (defect #292)', async () => {
    // Legacy gated Delete on isScheduleOpen() — a persisted summary — as well as on edit rights, and
    // the shared bar now carries that rule via `scheduleSaved`. This used to be unreachable through
    // this page ("getSchedule3 404s when unsaved") and pinned the rule rather than a user-visible
    // state — defect #296 made it a REAL state, because the GET now serves the empty editable
    // document. An absent `revisionCount` (Jackson `non_null` omits nulls) must NOT read as "saved".
    const { revisionCount, ...unsavedDoc } = schedule3Doc
    expect(revisionCount).toBe(3) // guard: the fixture really did carry one to strip
    server.use(http.get(URL, () => HttpResponse.json(unsavedDoc)))
    render(<Schedule3 />)

    await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    const bars = document.querySelectorAll<HTMLElement>('.schedule-3__actions')
    // Pin the count before indexing: without it a regression to a single bar throws inside
    // `within(undefined)` instead of failing legibly (code-review finding; the Schedule 1 twin
    // already asserted this).
    expect(bars).toHaveLength(2)
    const bottom = within(bars[1])
    expect(bottom.getByRole('button', { name: 'Delete' })).toBeDisabled()
    // Entry is untouched — only the destructive action is withheld.
    expect(bottom.getByRole('button', { name: 'Save' })).toBeEnabled()
    expect(bottom.getByRole('button', { name: 'Check Status' })).toBeEnabled()
  })

  test('a sub-page on a never-saved schedule shows ALT-001 and does not navigate (#296)', async () => {
    // Schedule 3 had NO save-required gate on its sub-pages at all — before defect #296 the parent
    // page itself 404'd when unsaved, so the case could not arise. It can now: the GET serves an
    // empty editable document while both sub-page controllers still require a summary
    // (validateScheduleViewable, kept deliberately by #296 D1), so without this gate the click lands
    // on a 404 dead-end instead of the legacy message.
    const { revisionCount, ...unsavedDoc } = schedule3Doc
    expect(revisionCount).toBe(3) // guard: the fixture really did carry one to strip
    server.use(http.get(URL, () => HttpResponse.json(unsavedDoc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    mockNavigate.mockClear()
    await user.click(screen.getByRole('button', { name: /^Subtotal Other Costs \(\d+\):$/ }))

    // Legacy ALT-001, verbatim (the same string Schedule 1 uses) — and no navigation.
    expect(
      await screen.findByText('The schedule has to be saved before opening other costs'),
    ).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()

    // The same gate covers the second sub-page.
    await user.click(screen.getByRole('button', { name: /^Included Unacceptable Costs \(\d+\):$/ }))
    expect(mockNavigate).not.toHaveBeenCalled()

    // It is a passive modal, so dismissing it must return the user to the schedule rather than
    // leaving the page blocked behind it.
    const blocked = screen.getByRole('dialog', { name: 'Save required' })
    await user.click(within(blocked).getByRole('button', { name: /close/i }))
    await waitFor(() =>
      expect(
        screen.queryByText('The schedule has to be saved before opening other costs'),
      ).not.toBeInTheDocument(),
    )
    expect(screen.getByLabelText('Licenses, Fees, Insurance Harvest')).toBeInTheDocument()
  })

  test('a never-saved schedule issues NO DELETE request even if the confirm is reached (#296)', async () => {
    // Asked for by the PR #361 review (paulushcgcj, seconded by SScholefield), and it has to target
    // `handleDelete` specifically: on this page the Delete BUTTON only opens the modal
    // (`onDelete={() => setConfirmDeleteOpen(true)}`) — `handleDelete` is the modal's
    // `onRequestSubmit`. So the guard is proven by reaching the confirm and asserting nothing goes to
    // the network, not by finding the button unclickable.
    //
    // This matters more since #296 than before: the endpoint is idempotent now and answers 200, so a
    // stray delete on a never-saved schedule would show "Data deleted successfully" for a record that
    // never existed. Schedule 2 has had the equivalent guard since #292.
    const { revisionCount, ...unsavedDoc } = schedule3Doc
    expect(revisionCount).toBe(3) // guard: the fixture really did carry one to strip
    let deleteCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(unsavedDoc)),
      http.delete(URL, () => {
        deleteCalled = true
        return HttpResponse.json({ message: { key: 'x', text: 'x' } })
      }),
    )
    render(<Schedule3 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    const bars = document.querySelectorAll<HTMLElement>('.schedule-3__actions')
    expect(bars).toHaveLength(2)
    // The button is disabled for a real user; click it anyway to reach the modal, which is exactly
    // the "any other route into this handler" the guard exists for.
    await user.click(within(bars[1]).getByRole('button', { name: /^delete$/i }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete schedule' })
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    // handleDelete refused: nothing reached the network, and no success banner was shown.
    expect(deleteCalled).toBe(false)
    expect(screen.queryByText(/deleted successfully/i)).not.toBeInTheDocument()
  })

  test('Delete confirms then shows the API message and empties the schedule (SUC-002)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule3Doc)),
      http.delete(URL, () =>
        HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        }),
      ),
    )
    render(<Schedule3 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    await user.click(screen.getAllByRole('button', { name: /delete/i })[0])
    // Scope to the delete-confirm modal by heading — the page also renders the "Leave Schedule 3"
    // navigation modal, so a bare dialog role is ambiguous.
    const dialog = await screen.findByRole('dialog', { name: 'Delete schedule' })
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // The values are gone but the FORM STAYS EDITABLE, so a first entry can be re-made immediately
    // (legacy AF1). This used to assert the input was removed, because the page pinned
    // `editable: false` on the premise that a re-GET would 404 and the record could not be
    // re-created — defect #296 made both false, and the #296 code review caught this test still
    // encoding the old premise.
    const harvest = await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    await waitFor(() => expect(harvest).toHaveValue(''))
    expect(harvest).toBeEnabled()
    // Delete is closed the instant the record is gone — the revisionCount gate (defect #292).
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: /^delete$/i })[0]).toBeDisabled(),
    )
  })
})

describe('Schedule3 context / load guards (AC3)', () => {
  test('409 mill-closed shows verbatim ERR-003, form suppressed', async () => {
    const detail =
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.'
    server.use(problemHandler(409, detail))
    render(<Schedule3 />)

    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('404 not-found shows verbatim ERR-004', async () => {
    server.use(problemHandler(404, 'Schedule not found.'))
    render(<Schedule3 />)

    expect(await screen.findByText('Schedule not found.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('empty context shows verbatim ERR-002 and fires NO request', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule3 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })
})

describe('Schedule3 Check Status (AC5)', () => {
  test('requirementsMet renders the verbatim SUC-003 success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule3Doc)),
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
    render(<Schedule3 />)
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: /check status/i }))[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
  })

  test('missing-field / Harvest<PO&P errors render verbatim; Save stays enabled', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule3Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            {
              key: 'missingRequiredFieldMsg',
              text: 'Annual Rents (Harvest Total $): Value Required',
            },
            {
              key: 'harvestNotGreaterThanPopErrorMsg',
              text: 'Licence, Fees, Insurance (Harvest Total $) must be greater than or equal to PO&P.',
            },
          ],
          warnings: [],
          message: null,
        }),
      ),
    )
    render(<Schedule3 />)
    const user = userEvent.setup()
    await user.click((await screen.findAllByRole('button', { name: /check status/i }))[0])
    expect(
      await screen.findByText('Annual Rents (Harvest Total $): Value Required'),
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        'Licence, Fees, Insurance (Harvest Total $) must be greater than or equal to PO&P.',
      ),
    ).toBeInTheDocument()
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })
})

describe('Schedule3 sub-page navigation (AC6)', () => {
  test('clicking a sub-page link confirms via the Modal then navigates to the Story 4.4 route', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    // A Carbon Modal (not a native window.confirm) gates the navigation.
    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs \(2\):/ }))
    const otherDialog = await screen.findByRole('dialog', { name: 'Leave Schedule 3' })
    expect(
      within(otherDialog).getByText(
        'Any unsaved data will be lost. Are you sure you would like to continue?',
      ),
    ).toBeInTheDocument()
    await user.click(within(otherDialog).getByRole('button', { name: /continue/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3/other-acceptable-costs' })

    await user.click(screen.getByRole('button', { name: /Included Unacceptable Costs \(1\):/ }))
    const unacceptableDialog = await screen.findByRole('dialog', { name: 'Leave Schedule 3' })
    await user.click(within(unacceptableDialog).getByRole('button', { name: /continue/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3/included-unacceptable-costs' })
  })

  test('cancelling the Modal does NOT navigate (editable)', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs \(2\):/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Leave Schedule 3' })
    await user.click(within(dialog).getByRole('button', { name: /cancel/i }))
    expect(mockNavigate).not.toHaveBeenCalled()
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
              ...schedule3Doc,
              millId: 999,
              year: 2020,
              editable: false,
              comments: 'Context 999/2020 loaded',
            })
          : HttpResponse.json(schedule3Doc),
      ),
      http.put(URL, async () => {
        await releasePromise
        return HttpResponse.json({
          ...schedule3Doc,
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
      <Schedule3 />
    </>
  )
}
