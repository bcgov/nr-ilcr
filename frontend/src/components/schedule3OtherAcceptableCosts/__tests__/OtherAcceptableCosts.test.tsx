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
import MillYearProvider from '@/context/millYear/MillYearProvider'

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

const rowOf = (displayValue: string) =>
  screen.getByDisplayValue(displayValue).closest('tr') as HTMLElement

// Read the ?intent= query param from a request URL (avoids  in the test env).
const intentOf = (url: string): string | null => /[?&]intent=([^&]+)/.exec(url)?.[1] ?? null

describe('Other Acceptable Costs sub-page (Story 4.4) — edit-in-place + batch Save', () => {
  test('lists groups as editable inputs with live-derived crown + subtotal + add form', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)

    await screen.findByDisplayValue('Consulting')
    const row = rowOf('Consulting')
    expect(within(row).getByLabelText('Edit total')).toHaveValue('800')
    expect(within(row).getByLabelText('Edit PO&P')).toHaveValue('300')
    expect(within(row).getByText('500')).toBeInTheDocument() // crown 800−300, derived live
    expect(screen.getByDisplayValue('Travel')).toBeInTheDocument()
    // Add form present (editable).
    expect(screen.getByLabelText('Description')).toBeInTheDocument()
    expect(screen.getByLabelText('PO&P $')).toBeInTheDocument()
  })

  test('crown recomputes live as the row is edited (before Save)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Consulting')
    const row = rowOf('Consulting')
    const total = within(row).getByLabelText('Edit total')
    await user.clear(total)
    await user.type(total, '1000')
    expect(within(row).getByText('700')).toBeInTheDocument() // 1000 − 300
  })

  test('Add persists the whole set immediately (legacy), sending the new group with a null id', async () => {
    let captured: unknown = null
    let intent: string | null = null
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...doc,
          rows: [],
          count: 0,
          subtotal: { harvest: 0, pop: 0, crown: 0 },
        }),
      ),
      http.put(URL, async ({ request }) => {
        intent = intentOf(request.url)
        captured = await request.json()
        return HttpResponse.json({
          ...doc,
          rows: [{ id: 7001, description: 'New', total: 900, pop: 100, crown: 800 }],
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
    expect(intent).toBe('save')
    expect(captured).toEqual({ rows: [{ id: null, description: 'New', total: 900, pop: 100 }] })
  })

  test('Save persists the whole set (total/pop, not derived crown) and shows success', async () => {
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
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Consulting')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toEqual({
      rows: [
        { id: 5501, description: 'Consulting', total: 800, pop: 300 },
        { id: 5503, description: 'Travel', total: 600, pop: 200 },
      ],
    })
  })

  test('an edited PO&P is sent by Save', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({ ...doc, message: { key: 'k', text: 'Data saved successfully' } })
      }),
    )
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Consulting')
    const pop = within(rowOf('Consulting')).getByLabelText('Edit PO&P')
    await user.clear(pop)
    await user.type(pop, '350')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect((captured as { rows: { id: number; pop: number }[] }).rows[0]).toEqual({
      id: 5501,
      description: 'Consulting',
      total: 800,
      pop: 350,
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
          rows: [{ id: 5503, description: 'Travel', total: 600, pop: 200, crown: 400 }],
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Consulting')
    await user.click(within(rowOf('Consulting')).getByRole('button', { name: /^remove$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(intent).toBe('delete')
    expect(captured).toEqual({ rows: [{ id: 5503, description: 'Travel', total: 600, pop: 200 }] })
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
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Consulting')
    const desc = within(rowOf('Consulting')).getByLabelText('Edit description')
    await user.clear(desc)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Description: Value is required.')).toBeInTheDocument()
    expect(putCalled).toBe(false)
  })

  test('a save failure surfaces the ProblemDetail as an action error', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, () => problemBody(500, 'Other cost could not be saved.')),
    )
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Consulting')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Action failed')).toBeInTheDocument()
    expect(screen.getByText('Other cost could not be saved.')).toBeInTheDocument()
  })

  test('read-only schedule shows rows as text with derived crown and no controls', async () => {
    server.use(http.get(URL, () => HttpResponse.json({ ...doc, editable: false })))
    render(<OtherAcceptableCostsPage />)

    expect(await screen.findByText('Consulting')).toBeInTheDocument()
    const row = screen.getByText('Consulting').closest('tr') as HTMLElement
    expect(within(row).getByText('500')).toBeInTheDocument() // derived crown
    expect(screen.queryByLabelText('Edit description')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^add$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^remove$/i })).not.toBeInTheDocument()
  })

  test('Save is greyed out when there are no groups to save', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...doc,
          rows: [],
          count: 0,
          subtotal: { harvest: 0, pop: 0, crown: 0 },
        }),
      ),
    )
    render(<OtherAcceptableCostsPage />)
    await screen.findByText('No records found.')
    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled()
  })

  test('no mill/year context shows the required notice and fires no request', async () => {
    let fetched = false
    server.use(
      http.get(URL, () => {
        fetched = true
        return HttpResponse.json(doc)
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <OtherAcceptableCostsPage />
      </MillYearProvider>,
    )
    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(fetched).toBe(false)
  })
})
