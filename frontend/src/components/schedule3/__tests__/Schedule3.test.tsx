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
  test('editable:true renders the three-column layout; crown/perUnit stay read-only', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)

    // Both-columns line (27) has Harvest AND PO&P inputs seeded from the doc.
    const harvest = await screen.findByLabelText('Licenses, Fees, Insurance Harvest')
    expect(harvest).toHaveValue('1000')
    expect(screen.getByLabelText('Licenses, Fees, Insurance PO&P')).toHaveValue('400')
    // Crown is server-derived, read-only text (not an input) — scoped to the line's row.
    const row = screen.getByText('Licenses, Fees, Insurance').closest('tr')
    expect(within(row as HTMLElement).getByText('600')).toBeInTheDocument()
    // Timber volume entry + read-only derived cost/perUnit.
    expect(screen.getByLabelText('Crown Timber Harvest Volume')).toHaveValue('7000')
    // Override menu defaults to No; Comments editable; Save enabled (top + bottom).
    expect(screen.getByLabelText('Override Harvest ⁄ Total PO&P $')).toHaveValue('N')
    expect(screen.getByLabelText('Comments')).toHaveValue('Seed comment for 514/2021')
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
    expect(screen.getByLabelText('Licenses, Fees, Insurance Harvest')).toHaveValue('100000000')
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
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByLabelText('Licenses, Fees, Insurance Harvest')).not.toBeInTheDocument(),
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
  test('clicking a sub-page link confirms then navigates to the Story 4.4 route', async () => {
    mockNavigate.mockClear()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs \(2\):/ }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3/other-acceptable-costs' })

    await user.click(screen.getByRole('button', { name: /Included Unacceptable Costs \(1\):/ }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3/included-unacceptable-costs' })
    confirmSpy.mockRestore()
  })

  test('cancelling the confirm does NOT navigate (editable)', async () => {
    mockNavigate.mockClear()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    server.use(http.get(URL, () => HttpResponse.json(schedule3Doc)))
    render(<Schedule3 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs \(2\):/ }))
    expect(mockNavigate).not.toHaveBeenCalled()
    confirmSpy.mockRestore()
  })
})
