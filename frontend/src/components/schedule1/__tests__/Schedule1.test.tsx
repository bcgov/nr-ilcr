import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider (AppProviders has none). Mock the router
// exactly like Dashboard.test.tsx; stub Link as a passthrough in case it renders.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
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
  revisionCount: 3,
  comments: 'Seed comment for 514/2021',
  lineItems: [{ costItemCode: 12, volume: 1000, cost: 50000, perUnit: 50.0 }],
  silviculture: {
    actualSpent: { costItemCode: 1, volume: 500, cost: 20000, perUnit: 40.0 },
    accruedLessActual: null,
    lessAdmin: null,
    total: null,
  },
  forestMgmtAdminCost: null,
  lessSilvAdminCost: null,
  otherCosts: { volume: 8000, costSubtotal: 24000, perUnit: 3.0, count: 2 },
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

  test('400 field rejection shows verbatim detail and keeps entered values (AC3 / S03)', async () => {
    const detail = 'Entered cost must be between -99,999,999 and 99,999,999.'
    server.use(
      http.get(URL, () => HttpResponse.json(schedule1Doc)),
      http.put(URL, () => problemBody(400, detail)),
    )
    render(<Schedule1 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Standing Tree to Loaded Truck cost')
    await user.clear(cost)
    await user.type(cost, '100000000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(await screen.findByText(detail)).toBeInTheDocument()
    // Entered value is retained (not cleared) so the user can correct + retry.
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
