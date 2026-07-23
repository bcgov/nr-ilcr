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

import OtherAcceptableCostsPage from '@/components/schedule3OtherAcceptableCosts'

const URL = 'http://localhost:3000/api/v1/schedule3/other-acceptable-costs'

const doc = {
  editable: true,
  count: 2,
  subtotal: { harvest: 1400, pop: 500, crown: 900 },
  rows: [
    { id: 5501, description: 'Consulting', total: 800, pop: 300, crown: 500 },
    { id: 5503, description: 'Travel', total: 600, pop: 200, crown: 400 },
  ],
}

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

describe('Other Acceptable Costs sub-page (Story 4.4)', () => {
  test('lists groups with derived crown + subtotal and the add form', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)

    const row = (await screen.findByText('Consulting')).closest('tr') as HTMLElement
    expect(within(row).getByText('800')).toBeInTheDocument()
    expect(within(row).getByText('300')).toBeInTheDocument()
    expect(within(row).getByText('500')).toBeInTheDocument() // crown 800−300
    expect(screen.getByText('Travel')).toBeInTheDocument()
    // Add form present (editable).
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
    expect(screen.getByLabelText('PO&P $')).toBeInTheDocument()
  })

  test('add valid group POSTs description/total/pop and shows verbatim success', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...doc,
          rows: [],
          count: 0,
          subtotal: { harvest: 0, pop: 0, crown: 0 },
        }),
      ),
      http.post(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          rows: [{ id: 6001, description: 'New', total: 900, pop: 100, crown: 800 }],
          count: 1,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Description'), 'New')
    await user.type(screen.getByLabelText('Total $'), '900')
    await user.type(screen.getByLabelText('PO&P $'), '100')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toEqual({ description: 'New', total: 900, pop: 100 })
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
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Total $'), '10')
    await user.click(screen.getByRole('button', { name: /^add$/i }))
    expect(await screen.findByText('Description: Value is required.')).toBeInTheDocument()
    expect(posted).toBe(false)
  })

  test('delete confirms then DELETEs and shows the API message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.delete(`${URL}/5501`, () =>
        HttpResponse.json({
          ...doc,
          rows: [{ id: 5503, description: 'Travel', total: 600, pop: 200, crown: 400 }],
          count: 1,
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        }),
      ),
    )
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    const row = (await screen.findByText('Consulting')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: /delete/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('read-only schedule hides the add form and row actions', async () => {
    server.use(http.get(URL, () => HttpResponse.json({ ...doc, editable: false })))
    render(<OtherAcceptableCostsPage />)

    expect(await screen.findByText('Consulting')).toBeInTheDocument()
    expect(screen.queryByLabelText('Description')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
  })

  test('load error shows the verbatim ProblemDetail and a Back button', async () => {
    server.use(http.get(URL, () => problemBody(404, 'Schedule not found.')))
    render(<OtherAcceptableCostsPage />)
    expect(await screen.findByText('Schedule not found.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /back to schedule 3/i })).toBeInTheDocument()
  })

  test('Back navigates to Schedule 3', async () => {
    mockNavigate.mockClear()
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /back to schedule 3/i }))
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/schedule-3' })
  })
})
