import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import { render, screen, within, waitFor } from '@/test-utils'
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
  // ---- Defect #291: the Totals footer tracks entry, on blur. -------------------------------------
  //
  // The fixture is self-consistent (rows 800+600 = 1400 harvest, 300+200 = 500 pop, crown 900), so
  // the load assertion is a genuine mirror-vs-server comparison.

  /** The Totals footer's three figures. */
  const footerCells = (): (string | null)[] => {
    const tr = document.querySelector('.schedule-3-sub__totals') as HTMLElement
    return [...tr.querySelectorAll('td')].slice(1, 4).map((td) => td.textContent)
  }

  test('on load the footer mirror reproduces the served subtotal (#291 AC5)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    await screen.findByDisplayValue('Consulting')

    expect(footerCells()).toEqual(['1,400', '500', '900'])
  })

  test('typing alone leaves the footer alone; blurring a total recalculates it (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await screen.findByDisplayValue('Consulting')

    const total = within(rowOf('Consulting')).getByLabelText('Edit total')
    await user.clear(total)
    await user.type(total, '1000')
    expect(footerCells()).toEqual(['1,400', '500', '900']) // not per keystroke

    await user.tab()
    // Harvest 1000 + 600 = 1,600; PO&P unchanged at 500; Crown the difference.
    expect(footerCells()).toEqual(['1,600', '500', '1,100'])
  })

  test('a PO&P blur moves the PO&P and Crown columns only (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await screen.findByDisplayValue('Consulting')

    const pop = within(rowOf('Consulting')).getByLabelText('Edit PO&P')
    await user.clear(pop)
    await user.type(pop, '100')
    await user.tab()

    // Harvest unchanged at 1,400; PO&P 100 + 200 = 300; Crown 1,400 - 300.
    expect(footerCells()).toEqual(['1,400', '300', '1,100'])
  })

  test('clearing a row treats its halves as 0, matching the server (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await screen.findByDisplayValue('Consulting')

    await user.clear(within(rowOf('Consulting')).getByLabelText('Edit total'))
    await user.tab()
    // subtotalOtherCosts sums with nulls as 0, so the footer is 600 / 500 / 100 -- not blank.
    expect(footerCells()).toEqual(['600', '500', '100'])
  })

  test('a lax-only entry moves NEITHER the row Crown nor the footer (#344 review)', async () => {
    // `1e3` is the case where the page's two parsers disagreed: `validateOtherAcceptable` accepts it
    // (`Number('1e3')` is 1000, in range, so no error is shown), the row's derived Crown used lax
    // `toNum` and read 1000, and the footer mirror used strict `committedNum` and read null. The page
    // then contradicted itself — Crown 700 in the row, Crown 100 in the footer, over a Total $ field
    // displaying `1e3`. Two fixes meet here: the derived columns now parse with `committedNum` like the
    // footer, and a strictly-unparseable entry no longer commits at all. `0x10` and `12,34` are the
    // same class.
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await screen.findByDisplayValue('Consulting')

    const total = within(rowOf('Consulting')).getByLabelText('Edit total')
    await user.clear(total)
    await user.type(total, '1e3')
    await user.tab()

    // Both derived surfaces HOLD the last committed figures, and agree with each other.
    expect(within(rowOf('1e3')).getByText('500')).toBeInTheDocument() // 800 − 300, as served
    expect(footerCells()).toEqual(['1,400', '500', '900'])
  })

  test('an out-of-range entry holds the footer while the field sits invalid (#344 review)', async () => {
    // The rule `useCommittedValues` documents and every other surface followed: an invalid entry holds
    // its previous committed value, because legacy's failed round-trip left the last valid figures on
    // screen. This sub-page committed unconditionally, so 999,999,999 (over the ±99,999,999 cost range)
    // drove the footer to a figure the server can never produce while the field sat red beside it.
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await screen.findByDisplayValue('Consulting')

    const total = within(rowOf('Consulting')).getByLabelText('Edit total')
    await user.clear(total)
    await user.type(total, '999999999')
    await user.tab()

    // The entered text stays on screen for correction...
    expect(within(rowOf('999,999,999')).getByLabelText('Edit total')).toHaveValue('999,999,999')
    // ...but the footer holds the last figures the server could actually produce.
    //
    // NOTE this page does not mark the field invalid until a Save is attempted (`rowErrors` is filled
    // by `persist` alone), so there is no red state to assert here yet. That is why the commit gate
    // runs `config.validate` itself instead of reading `rowErrors`.
    expect(footerCells()).toEqual(['1,400', '500', '900'])
  })

  test('a locally added row counts toward the footer while its PUT is in flight (#291)', async () => {
    // PROVEN by the code review: the footer fell back to `{}` for a row appended before its PUT
    // landed, so it silently omitted a row visibly sitting in the grid. It now falls back to the
    // row's live values.
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, async () => {
        await delay(50)
        return HttpResponse.json(doc)
      }),
    )
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await screen.findByDisplayValue('Consulting')

    await user.type(screen.getByLabelText('Description'), 'Consulting 2')
    await user.type(screen.getByLabelText('Total $'), '900')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    // While the request is in flight the appended row is on screen, so the footer must count it.
    await waitFor(() => {
      expect(footerCells()).toEqual(['2,300', '500', '1,800'])
    })
  })

  test("an in-flight added row derives its Crown with the FOOTER's parser (#344 review)", async () => {
    // The one path where the row/footer parser split is still observable. A row appended before its
    // PUT lands is absent from `committed`, so `committedFor` falls back to its LIVE values — and
    // `1e3` clears `validateOtherAcceptable` (`Number('1e3')` is 1000, in range), so it can be added.
    // With the row cells on lax `toNum` and the footer on strict `committedNum`, the row read Crown
    // 1,000 while the footer excluded it from Harvest entirely: 1,400 under a visible row of 1e3.
    server.use(
      http.get(URL, () => HttpResponse.json(doc)),
      http.put(URL, async () => {
        await delay(50)
        return HttpResponse.json(doc)
      }),
    )
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()
    await screen.findByDisplayValue('Consulting')

    await user.type(screen.getByLabelText('Description'), 'Exponential')
    await user.type(screen.getByLabelText('Total $'), '1e3')
    await user.click(screen.getByRole('button', { name: /^add$/i }))

    const added = await screen.findByDisplayValue('1e3')
    const row = added.closest('tr') as HTMLElement
    // One parser, so the row agrees with the footer: neither counts the unparseable entry.
    expect(within(row).queryByText('1,000')).not.toBeInTheDocument()
    expect(footerCells()).toEqual(['1,400', '500', '900'])
  })

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

  test('crown recomputes on BLUR as the row is edited (before Save)', async () => {
    // UPDATED 2026-08-21 (ruled after code review): Crown used to recompute per keystroke while the
    // Totals footer moved on blur, so the page contradicted itself mid-typing — row Crowns of 700 and
    // 400 under a Subtotal Crown of 900. Legacy drove both from ONE handler
    // (`update="otherCrownTabel footerValues"`), so every derived cell here now settles on the same
    // event. The pre-Save recomputation this test exists to pin is intact; only its timing moved.
    server.use(http.get(URL, () => HttpResponse.json(doc)))
    render(<OtherAcceptableCostsPage />)
    const user = userEvent.setup()

    await screen.findByDisplayValue('Consulting')
    const total = within(rowOf('Consulting')).getByLabelText('Edit total')
    await user.clear(total)
    await user.type(total, '1000')
    // Not yet — the row is mid-edit and uncommitted.
    expect(within(rowOf('Consulting')).getByText('500')).toBeInTheDocument()

    await user.tab()
    expect(within(rowOf('Consulting')).getByText('700')).toBeInTheDocument() // 1000 − 300
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
