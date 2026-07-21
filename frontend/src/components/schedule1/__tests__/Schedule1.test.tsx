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
  test('editable:true renders an editable form; perUnit stays read-only (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)

    // Writable fields are inputs seeded from the document.
    const volume = await screen.findByLabelText('Standing Tree to Loaded Truck volume')
    expect(volume).toHaveValue('1000')
    expect(screen.getByLabelText('Standing Tree to Loaded Truck cost')).toHaveValue('50000')
    // perUnit is server-computed, read-only text (not an input).
    expect(screen.getByText('50')).toBeInTheDocument()
    // Comments is editable.
    expect(screen.getByLabelText('Comments')).toHaveValue('Seed comment for 514/2021')
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
    const body = captured as { revisionCount: number; lineItems: { costItemCode: number }[] }
    expect(body.revisionCount).toBe(3)
    expect(body.lineItems.map((li) => li.costItemCode)).toEqual([12, 13, 14, 15, 16, 17, 18])
    // Recomputed perUnit from the echo is displayed (server-computed, 60000/2000 = 30).
    expect(screen.getByText('30')).toBeInTheDocument()
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
    expect(screen.getByLabelText('Standing Tree to Loaded Truck cost')).toHaveValue('100000000')
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
    const dialog = await screen.findByRole('dialog')
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
    render(<Schedule1 />)

    expect(await screen.findByText('Schedule not found.')).toBeInTheDocument()
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
    expect(screen.getByLabelText('Standing Tree to Loaded Truck volume')).toHaveValue('7777')
    expect(screen.getByLabelText('Depletion and Amortization volume')).toHaveValue('7777')
    expect(screen.getByLabelText('Actual $ Spent volume')).toHaveValue('7777')
    expect(screen.getByLabelText('Accrued less Actual $ Spent volume')).toHaveValue('7777')
    expect(screen.getByLabelText('Forest Management Administration volume')).toHaveValue('7777')
    expect(screen.getByLabelText('Subtotal Company Logging volume')).toHaveValue('7777')
    expect(screen.getByLabelText('Less Silviculture Admin Costs volume')).toHaveValue('7777')
    expect(screen.getByLabelText('Total Silviculture volume')).toHaveValue('7777')
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
    expect(screen.getByText('600000')).toBeInTheDocument()
    expect(
      screen.queryByLabelText('Forest Management Administration Costs (Sch 3) cost'),
    ).not.toBeInTheDocument()
    // Less Silviculture Admin (139) shows the PULLED cost (150000), not Schedule 1's own 9999.
    expect(screen.getByText('150000')).toBeInTheDocument()
    expect(screen.queryByText('9999')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Less Silviculture Admin Costs cost')).not.toBeInTheDocument()
  })

  test('crown-timber source field displays disabled with the Schedule 3 value (AC3)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const crown = await screen.findByLabelText('Crown Timber Volume for all fields (Sch 3)')
    expect(crown).toBeDisabled()
    expect(crown).toHaveValue('54321')
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
    const total = screen.getByLabelText('Total Silviculture volume')
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
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-1/other-costs' })
    confirmSpy.mockRestore()
  })

  test('cancelling the confirm does NOT navigate (editable)', async () => {
    mockNavigate.mockClear()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    server.use(http.get(URL, () => HttpResponse.json(schedule1Doc)))
    render(<Schedule1 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs/i }))
    expect(mockNavigate).not.toHaveBeenCalled()
    confirmSpy.mockRestore()
  })

  test('read-only schedule opens the sub-page without a confirm', async () => {
    mockNavigate.mockClear()
    const confirmSpy = vi.spyOn(window, 'confirm')
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ ...schedule1Doc, trackStatus: 'S', editable: false }),
      ),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Subtotal Other Costs/i }))
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-1/other-costs' })
    confirmSpy.mockRestore()
  })
})
