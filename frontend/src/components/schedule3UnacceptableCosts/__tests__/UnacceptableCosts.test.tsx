import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }))
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
  Link: ({ children }: { children: ReactNode }) => children,
}))

import UnacceptableCostsPage from '@/components/schedule3UnacceptableCosts'

const URL = 'http://localhost:3000/api/v1/schedule3/included-unacceptable-costs'

const doc = {
  editable: true,
  count: 1,
  subtotalTotal: 250,
  annualRentsTotal: 777,
  rows: [{ id: 5505, description: 'Penalty', total: 250 }],
}

const rowOf = (displayValue: string) =>
  screen.getByDisplayValue(displayValue).closest('tr') as HTMLElement

// Read the ?intent= query param from a request URL (avoids  in the test env).
const intentOf = (url: string): string | null => /[?&]intent=([^&]+)/.exec(url)?.[1] ?? null

describe('Included Unacceptable Costs sub-page (Story 4.4) — edit-in-place + batch Save', () => {
  test('lists rows as editable inputs, the subtotal, and the read-only Annual Rents (S111)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<UnacceptableCostsPage />)

    // Every row is a live input (legacy edit-in-place), seeded from the loaded doc.
    expect(await screen.findByDisplayValue('Penalty')).toBeInTheDocument()
    const row = rowOf('Penalty')
    expect(within(row).getByLabelText('Edit total')).toHaveValue('250')
    // Annual Rents (S111) is read-only, from the item-29 Harvest.
    const annual = screen.getByLabelText('Annual Rents (Forest Act, S111)')
    expect(annual).toBeDisabled()
    expect(annual).toHaveValue('777')
    // Subtotal (last-saved) shown in the Totals row.
    expect(screen.getByText('250')).toBeInTheDocument()
    // Intro + add form present.
    expect(
      screen.getByText(/Unacceptable costs include income and logging taxes/),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
  })

  test('Add persists the whole set immediately (legacy), sending the new row with a null id', async () => {
    let captured: unknown = null
    let intent: string | null = null
    server.use(
      http.get(URL, () => HttpResponse.json({ ...doc, rows: [], count: 0, subtotalTotal: 0 })),
      http.put(URL, async ({ request }) => {
        intent = intentOf(request.url)
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          rows: [{ id: 6001, description: 'Fine', total: 500 }],
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Description'), 'Fine')
    await user.type(screen.getByLabelText('Total $'), '500')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    // Add immediately persists (legacy addOtherCost → save); the row is sent with a null id (insert).
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(intent).toBe('save')
    expect(captured).toEqual({ rows: [{ id: null, description: 'Fine', total: 500 }] })
    expect(screen.getByLabelText('Description')).toHaveValue('')
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
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Penalty')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toEqual({ rows: [{ id: 5505, description: 'Penalty', total: 250 }] })
  })

  test('edits to a row are sent by Save (id preserved for the update)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({ ...doc, message: { key: 'k', text: 'Data saved successfully' } })
      }),
    )
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Penalty')
    const total = within(rowOf('Penalty')).getByLabelText('Edit total')
    await user.clear(total)
    await user.type(total, '300')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toEqual({ rows: [{ id: 5505, description: 'Penalty', total: 300 }] })
  })

  test('Remove deletes immediately (legacy): PUT with intent=delete + the deleted message', async () => {
    let captured: unknown = null
    let intent: string | null = null
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...doc,
          count: 2,
          rows: [
            { id: 5505, description: 'Penalty', total: 250 },
            { id: 5506, description: 'Interest', total: 40 },
          ],
        }),
      ),
      http.put(URL, async ({ request }) => {
        intent = intentOf(request.url)
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          count: 1,
          rows: [{ id: 5506, description: 'Interest', total: 40 }],
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Penalty')
    await user.click(within(rowOf('Penalty')).getByRole('button', { name: /^remove$/i }))

    // Remove persists immediately with the remaining rows and the delete intent (legacy delete()).
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(intent).toBe('delete')
    expect(captured).toEqual({ rows: [{ id: 5506, description: 'Interest', total: 40 }] })
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
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Penalty')
    const desc = within(rowOf('Penalty')).getByLabelText('Edit description')
    await user.clear(desc)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Description: Value is required.')).toBeInTheDocument()
    expect(putCalled).toBe(false)
  })

  test('read-only schedule shows rows as text with no add/save/remove controls', async () => {
    server.use(http.get(URL, () => HttpResponse.json({ ...doc, editable: false })))
    render(<UnacceptableCostsPage />)

    expect(await screen.findByText('Penalty')).toBeInTheDocument()
    expect(screen.queryByLabelText('Edit description')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Description')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^remove$/i })).not.toBeInTheDocument()
  })

  test('Save is greyed out when there are no rows to save', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json({ ...doc, rows: [], count: 0, subtotalTotal: 0 })),
    )
    render(<UnacceptableCostsPage />)
    await screen.findByText('No records found.')
    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled()
  })

  test('Back navigates to Schedule 3 when there are no unsaved changes', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /^back$/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3' })
  })

  test('Back confirms first when there are unsaved changes', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    // Make an edit so the page is dirty.
    await screen.findByDisplayValue('Penalty')
    const total = within(rowOf('Penalty')).getByLabelText('Edit total')
    await user.clear(total)
    await user.type(total, '9')
    await user.click(screen.getByRole('button', { name: /^back$/i }))

    // A confirm dialog appears; navigation only happens on Continue.
    const dialog = await screen.findByRole('dialog')
    expect(mockNavigate).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: /continue/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3' })
  })
})
