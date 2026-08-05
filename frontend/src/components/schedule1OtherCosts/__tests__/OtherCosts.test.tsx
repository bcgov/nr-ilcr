import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like Schedule1.test.
const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
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

const rowOf = (displayValue: string) =>
  screen.getByDisplayValue(displayValue).closest('tr') as HTMLElement

// Read the ?intent= query param from a request URL (avoids  in the test env).
const intentOf = (url: string): string | null => /[?&]intent=([^&]+)/.exec(url)?.[1] ?? null

describe('Other Costs sub-page (Story 2.5) — edit-in-place + batch Save', () => {
  test('lists rows as editable inputs with shared volume + live $/m³ and the add form', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherCostsPage />)

    await screen.findByDisplayValue('Existing Row A')
    const rowA = rowOf('Existing Row A')
    expect(within(rowA).getByLabelText('Edit cost')).toHaveValue('3000')
    expect(within(rowA).getByText('5000')).toBeInTheDocument() // shared volume
    expect(within(rowA).getByText('0.6')).toBeInTheDocument() // 3000 / 5000, derived live
    // Row B has no cost → $/m³ blank (em dash).
    expect(screen.getByDisplayValue('Existing Row B')).toBeInTheDocument()
    // Add form present; shared volume is read-only.
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
    expect(screen.getByLabelText('Volume')).toBeDisabled()
  })

  test('$/m³ recomputes live as the cost is edited (before Save)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Existing Row A')
    const cost = within(rowOf('Existing Row A')).getByLabelText('Edit cost')
    await user.clear(cost)
    await user.type(cost, '5000')
    expect(within(rowOf('Existing Row A')).getByText('1')).toBeInTheDocument() // 5000 / 5000
  })

  test('Add persists the whole set immediately (legacy), sending the new row with a null id', async () => {
    let captured: unknown = null
    let intent: string | null = null
    server.use(
      http.get(URL, () => HttpResponse.json({ ...doc, rows: [], count: 0, costSubtotal: 0 })),
      http.put(URL, async ({ request }) => {
        intent = intentOf(request.url)
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          rows: [{ id: 6001, description: 'New', cost: 1000, perUnit: 0.2 }],
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Description'), 'New')
    await user.type(screen.getByLabelText('Cost'), '1000')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(intent).toBe('save')
    expect(captured).toEqual({ rows: [{ id: null, description: 'New', cost: 1000 }] })
  })

  test('Save persists the whole set via one PUT and shows verbatim success', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Existing Row A')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // Volume / $-per-m³ are server-derived and excluded; a blank cost sends null.
    expect(captured).toEqual({
      rows: [
        { id: 5051, description: 'Existing Row A', cost: 3000 },
        { id: 5052, description: 'Existing Row B', cost: null },
      ],
    })
  })

  test('an edited cost is sent by Save', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({ ...doc, message: { key: 'k', text: 'Data saved successfully' } })
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Existing Row A')
    const cost = within(rowOf('Existing Row A')).getByLabelText('Edit cost')
    await user.clear(cost)
    await user.type(cost, '4000')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect((captured as { rows: { id: number; cost: number }[] }).rows[0]).toEqual({
      id: 5051,
      description: 'Existing Row A',
      cost: 4000,
    })
  })

  test('Remove deletes immediately (legacy): PUT with intent=delete + the deleted message', async () => {
    let captured: unknown = null
    let intent: string | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, async ({ request }) => {
        intent = intentOf(request.url)
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          count: 1,
          rows: [{ id: 5052, description: 'Existing Row B', cost: null, perUnit: null }],
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Existing Row A')
    await user.click(within(rowOf('Existing Row A')).getByRole('button', { name: /^remove$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(intent).toBe('delete')
    expect(captured).toEqual({ rows: [{ id: 5052, description: 'Existing Row B', cost: null }] })
  })

  test('blank description blocks Save (advisory), no PUT', async () => {
    let putCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, () => {
        putCalled = true
        return HttpResponse.json(doc)
      }),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Existing Row A')
    const desc = within(rowOf('Existing Row A')).getByLabelText('Edit description')
    await user.clear(desc)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Description: Value is required.')).toBeInTheDocument()
    expect(putCalled).toBe(false)
  })

  test('a save failure renders the verbatim ProblemDetail (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, () => problemBody(400, 'Entered cost is invalid.')),
    )
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Existing Row A')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Entered cost is invalid.')).toBeInTheDocument()
  })

  test('read-only schedule shows rows as text with no add/save/remove controls (AC6/S22)', async () => {
    server.use(http.get(URL, () => HttpResponse.json({ ...doc, editable: false })))
    render(<OtherCostsPage />)

    expect(await screen.findByText('Existing Row A')).toBeInTheDocument()
    expect(screen.queryByLabelText('Edit description')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Description')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^remove$/i })).not.toBeInTheDocument()
  })

  test('Save is greyed out when there are no rows to save', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json({ ...doc, rows: [], count: 0, costSubtotal: 0 })),
    )
    render(<OtherCostsPage />)
    await screen.findByText('No records found.')
    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled()
  })

  test('Back navigates to Schedule 1 when there are no unsaved changes', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherCostsPage />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /back to schedule 1/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-1' })
  })

  test('Back confirms first when there are unsaved changes', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Existing Row A')
    const cost = within(rowOf('Existing Row A')).getByLabelText('Edit cost')
    await user.clear(cost)
    await user.type(cost, '9')
    await user.click(screen.getByRole('button', { name: /back to schedule 1/i }))

    const dialog = await screen.findByRole('dialog')
    expect(mockNavigate).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: /continue/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-1' })
  })
})
