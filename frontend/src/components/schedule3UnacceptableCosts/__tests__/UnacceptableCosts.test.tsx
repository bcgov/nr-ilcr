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

describe('Included Unacceptable Costs sub-page (Story 4.4)', () => {
  test('lists rows, the subtotal, and the read-only Annual Rents (S111) figure', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<UnacceptableCostsPage />)

    expect(await screen.findByText('Penalty')).toBeInTheDocument()
    // Annual Rents (S111) is read-only, from the item-29 Harvest.
    const annual = screen.getByLabelText('Annual Rents (Forest Act, S111)')
    expect(annual).toBeDisabled()
    expect(annual).toHaveValue('777')
    // Intro sentence renders.
    expect(
      screen.getByText(/Unacceptable costs include income and logging taxes/),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
  })

  test('add valid row POSTs description/total and shows verbatim success', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json({ ...doc, rows: [], count: 0, subtotalTotal: 0 })),
      http.post(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          rows: [{ id: 6001, description: 'Fine', total: 500 }],
          count: 1,
          subtotalTotal: 500,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Description'), 'Fine')
    await user.type(screen.getByLabelText('Total $'), '500')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toEqual({ description: 'Fine', total: 500 })
  })

  test('blank description blocks the add (advisory), no POST', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.post(URL, () => {
        posted = true
        return HttpResponse.json(doc)
      }),
    )
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Total $'), '10')
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    expect(await screen.findByText('Description: Value is required.')).toBeInTheDocument()
    expect(posted).toBe(false)
  })

  test('delete confirms then DELETEs and shows the API message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.delete(`${URL}/5505`, () =>
        HttpResponse.json({
          ...doc,
          rows: [],
          count: 0,
          subtotalTotal: 0,
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        }),
      ),
    )
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()

    const row = (await screen.findByText('Penalty')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: /delete/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('read-only schedule hides the add form and row actions', async () => {
    server.use(http.get(URL, () => HttpResponse.json({ ...doc, editable: false })))
    render(<UnacceptableCostsPage />)
    expect(await screen.findByText('Penalty')).toBeInTheDocument()
    expect(screen.queryByLabelText('Description')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
  })

  test('Back navigates to Schedule 3', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<UnacceptableCostsPage />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /back to schedule 3/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3' })
  })
})
