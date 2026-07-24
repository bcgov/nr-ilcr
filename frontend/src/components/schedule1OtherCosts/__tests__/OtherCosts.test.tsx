import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like Schedule1.test.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import OtherCostsPage from '@/components/schedule1OtherCosts'

const URL = 'http://localhost:3000/api/v1/schedule1/other-costs'

const doc = {
  volume: 5000,
  costSubtotal: 3000,
  perUnit: 0.6,
  count: 2,
  rows: [
    { id: 5051, description: 'Existing Row A', cost: 3000, perUnit: 0.6 },
    { id: 5052, description: 'Existing Row B', cost: null, perUnit: null },
  ],
  editable: true,
}

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

describe('Other Costs sub-page (Story 2.5)', () => {
  test('lists rows with server-computed totals and a disabled shared-volume field (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherCostsPage />)

    expect(await screen.findByText('Existing Row A')).toBeInTheDocument()
    expect(screen.getByText('Existing Row B')).toBeInTheDocument()
    // Shared volume is disabled (read-only, from Schedule 1).
    const shared = screen.getByLabelText('Volume m³ (shared, from Schedule 1)')
    expect(shared).toBeDisabled()
    expect(shared).toHaveValue('5000')
    // Editable schedule shows the add form.
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
  })

  test('add valid row POSTs and shows verbatim success (AC2)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json({ ...doc, rows: [], count: 0, costSubtotal: 0 })),
      http.post(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          rows: [{ id: 6001, description: 'New Row', cost: 1200, perUnit: 0.2 }],
          count: 1,
          costSubtotal: 1200,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Description'), 'New Row')
    await user.type(screen.getByLabelText('Cost'), '1200')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(screen.getByText('New Row')).toBeInTheDocument()
    expect(captured).toEqual({ description: 'New Row', cost: 1200 })
  })

  test('blank description blocks the add (advisory), no POST (AC5/S10)', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.post(URL, () => {
        posted = true
        return HttpResponse.json(doc)
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^add$/i }))
    expect(screen.getByText('Description: Value is required.')).toBeInTheDocument()
    expect(posted).toBe(false)
  })

  test('over-range cost blocks the add (advisory), no POST (AC5/S11)', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.post(URL, () => {
        posted = true
        return HttpResponse.json(doc)
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Description'), 'X')
    await user.type(screen.getByLabelText('Cost'), '100000000')
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    expect(
      screen.getByText('Entered cost must be between -99,999,999 and 99,999,999.'),
    ).toBeInTheDocument()
    expect(posted).toBe(false)
  })

  test('inline edit PUTs the row and shows verbatim success (AC3)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(`${URL}/5051`, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          rows: [
            { id: 5051, description: 'Edited A', cost: 4000, perUnit: 0.8 },
            { id: 5052, description: 'Existing Row B', cost: null, perUnit: null },
          ],
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByText('Existing Row A')
    await user.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    const desc = screen.getByLabelText('Edit description')
    await user.clear(desc)
    await user.type(desc, 'Edited A')
    const cost = screen.getByLabelText('Edit cost')
    await user.clear(cost)
    await user.type(cost, '4000')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(screen.getByText('Edited A')).toBeInTheDocument()
    expect(captured).toEqual({ description: 'Edited A', cost: 4000 })
  })

  test('inline edit backend 400 renders verbatim detail, row unchanged (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(`${URL}/5051`, () => problemBody(400, 'Entered cost is invalid.')),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByText('Existing Row A')
    await user.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    const cost = screen.getByLabelText('Edit cost')
    await user.clear(cost)
    await user.type(cost, '4000') // passes client validation; backend rejects
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Entered cost is invalid.')).toBeInTheDocument()
    // Edit stays open (PUT rejected); the original description is still in the edit field, unchanged.
    expect(screen.getByLabelText('Edit description')).toHaveValue('Existing Row A')
  })

  test('delete confirms then DELETEs and shows verbatim success (AC4/S12)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.delete(`${URL}/5051`, () =>
        HttpResponse.json({
          ...doc,
          rows: [{ id: 5052, description: 'Existing Row B', cost: null, perUnit: null }],
          count: 1,
          costSubtotal: 0,
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        }),
      ),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByText('Existing Row A')
    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Existing Row A')).not.toBeInTheDocument())
  })

  test('read-only schedule shows rows but no add/edit/delete controls (AC6/S22)', async () => {
    server.use(http.get(URL, () => HttpResponse.json({ ...doc, editable: false })))
    render(<OtherCostsPage />)

    expect(await screen.findByText('Existing Row A')).toBeInTheDocument()
    expect(screen.queryByLabelText('Description')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^edit$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument()
  })

  test('backend 400 renders the verbatim ProblemDetail (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json({ ...doc, rows: [], count: 0 })),
      http.post(URL, () => problemBody(400, 'Description: Value is required.')),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    // Pass client validation (non-empty description) so the POST is actually attempted.
    await user.type(await screen.findByLabelText('Description'), 'Valid text')
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    expect(await screen.findByText('Description: Value is required.')).toBeInTheDocument()
  })
})
