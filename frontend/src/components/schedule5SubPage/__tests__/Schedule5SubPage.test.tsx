import type { ReactNode } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  getRouteApi: () => ({ useSearch: () => ({}), useNavigate: () => vi.fn() }),
  Link: ({ children }: { children: ReactNode }) => children,
}))

window.HTMLElement.prototype.scrollIntoView = vi.fn()

import Schedule5SubPage from '@/components/schedule5SubPage'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import type { SubPageDocument } from '@/interfaces/Schedule5SubPage'

const CAMP_URL = 'http://localhost:3000/api/v1/schedule5/camps/8700/other-camp-expenses'
const ACCESS_URL = 'http://localhost:3000/api/v1/schedule5/camps/8700/other-access-expenses'

/**
 * The CAMP document, built from § PINNED SUB-PAGE CONTRACT. Its footer volume is the SUM of the row
 * volumes (3 × 120000), which is what makes it different from the access twin below.
 */
const campDoc = (overrides: Partial<SubPageDocument> = {}): SubPageDocument => ({
  campId: 8700,
  campName: 'Reconcile Camp',
  associatedCampVolume: 120000,
  editable: true,
  rows: [
    {
      rowId: 8722,
      description: 'Generator Fuel',
      volume: 120000,
      cost: 10000,
      costPerVolume: 0.08,
    },
    { rowId: 8723, description: 'Propane', volume: 120000, cost: 2500, costPerVolume: 0.02 },
    { rowId: 8724, volume: 120000, cost: 500, costPerVolume: 0 },
  ],
  totals: { volume: 360000, cost: 13000, costPerVolume: 0.04 },
  ...overrides,
})

/** The ACCESS twin: two rows, and a footer volume that is the SINGLE camp volume (deviation (C)). */
const accessDoc = (overrides: Partial<SubPageDocument> = {}): SubPageDocument => ({
  campId: 8700,
  campName: 'Reconcile Camp',
  associatedCampVolume: 120000,
  editable: true,
  rows: [
    { rowId: 8725, description: 'Bridge Rental', volume: 120000, cost: 7000, costPerVolume: 0.06 },
    { rowId: 8726, description: 'Culvert Hire', volume: 120000, cost: 3000, costPerVolume: 0.03 },
  ],
  totals: { volume: 120000, cost: 10000, costPerVolume: 0.08 },
  ...overrides,
})

/**
 * A CAMP document whose rows ALL carry a description.
 *
 * `campDoc` deliberately includes a null-description row, because that is a legal stored state the
 * read path must render. But the grid input is `required="true"` on both pages
 * (`schedule5CampExpenses.xhtml:66`), so Save legitimately BLOCKS while such a row is on screen —
 * legacy behaves the same way, and a licensee must fill the description in before saving. Tests that
 * need a save to reach the network therefore start from a fully-described list.
 */
const describedCampDoc = (overrides: Partial<SubPageDocument> = {}): SubPageDocument =>
  campDoc({
    rows: [
      {
        rowId: 8722,
        description: 'Generator Fuel',
        volume: 120000,
        cost: 10000,
        costPerVolume: 0.08,
      },
      { rowId: 8723, description: 'Propane', volume: 120000, cost: 2500, costPerVolume: 0.02 },
    ],
    totals: { volume: 240000, cost: 12500, costPerVolume: 0.05 },
    ...overrides,
  })

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

/**
 * Deterministically drain the event loop so an already-settled request finishes its whole
 * then/catch chain before a negative assertion runs. Turn-based, never wall-clock: a `delay(50)`
 * proves only "nothing happened within 50ms on this machine" and inverts under CI load (the 7.3
 * review finding).
 */
const flushAsync = async () => {
  for (let i = 0; i < 4; i += 1) {
    await new Promise((resolve) => {
      setTimeout(resolve, 0)
    })
  }
}

// Drives a mid-flight mill/year change so the stale-response guards can be exercised. Module-level
// so it is not re-created per render (an @eslint-react rule forbids nested component definitions).
// NOTE: unlike the real app, this harness does NOT remount the sub-page on a context change (the
// parent's key does that), so what it isolates is the component's OWN `isCurrent()` mutation guards.
const SubPageRaceHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <Schedule5SubPage campId={8700} kind="CAMP" onBack={vi.fn()} />
    </>
  )
}

const renderSubPage = (kind: 'CAMP' | 'ACCESS' = 'CAMP', onBack = vi.fn()) =>
  render(
    <MillYearProvider>
      <Schedule5SubPage campId={8700} kind={kind} onBack={onBack} />
    </MillYearProvider>,
  )

const seedCamp = (doc: SubPageDocument = campDoc()) => {
  server.use(http.get(CAMP_URL, () => HttpResponse.json(doc)))
}

const rowInputs = async (name: RegExp) => {
  const inputs = await screen.findAllByLabelText(name)
  return inputs
}

/**
 * The Carbon modal carrying this verbatim confirm text. Both confirms are always mounted, so their
 * Yes/No buttons must be reached THROUGH the dialog that owns them — a bare
 * `getByRole('button', { name: 'Yes' })` is ambiguous and would click whichever happened to render
 * first.
 */
const confirmDialog = (text: string): HTMLElement =>
  screen.getByText(text).closest('.cds--modal') as HTMLElement

// ---- Defect #291: the footer and the row rates track entry, on blur. ----------------------------
//
// This page had NO test for its mirror: the code review proved both mirrors could be deleted with
// 56/56 still passing, because the 12 unit tests exercise `deriveSubPageTotals` in isolation and never
// observe the page calling it. Every test below fails if the mirror is disabled.

describe('#291 the sub-page mirror', () => {
  /** The Totals footer's three figures. */
  const footerCells = (): (string | null)[] => {
    const tr = document.querySelector('.schedule-5-sub-page__totals') as HTMLElement
    return [...tr.querySelectorAll('td')].slice(1, 4).map((td) => td.textContent)
  }
  /** The `$/m³` cell of the row whose cost input currently holds `value`. */
  const rateOfRowWithCost = (value: string): string | null | undefined => {
    const tr = screen.getByDisplayValue(value).closest('tr') as HTMLElement
    return [...tr.querySelectorAll('td')][3]?.textContent
  }

  test('on load the mirror reproduces the served CAMP footer (AC5)', async () => {
    // campDoc is self-consistent: 10000+2500+500 = 13,000 over 3 x 120,000 = 360,000 -> 0.04.
    seedCamp()
    renderSubPage('CAMP')
    await screen.findByDisplayValue('10,000')
    expect(footerCells()).toEqual(['360,000', '13,000', '0.04'])
  })

  test('typing alone moves nothing; blurring a cost moves the footer AND that row rate', async () => {
    seedCamp()
    renderSubPage('CAMP')
    const user = userEvent.setup()
    const cost = await screen.findByDisplayValue('10,000')

    await user.clear(cost)
    await user.type(cost, '20000')
    expect(footerCells()).toEqual(['360,000', '13,000', '0.04']) // not per keystroke

    await user.tab()
    // 20000+2500+500 = 23,000 over 360,000 -> 0.06; the row's own rate 20000/120000 -> 0.17.
    expect(footerCells()).toEqual(['360,000', '23,000', '0.06'])
    expect(rateOfRowWithCost('20000')).toBe('0.17')
  })

  test('the ACCESS footer uses the SINGLE camp volume, not n x it', async () => {
    server.use(http.get(ACCESS_URL, () => HttpResponse.json(accessDoc())))
    renderSubPage('ACCESS')
    const user = userEvent.setup()
    const cost = await screen.findByDisplayValue('7,000')

    await user.clear(cost)
    await user.type(cost, '9000')
    await user.tab()
    // 9000+3000 = 12,000 over the single 120,000 -> 0.10. A CAMP-shaped footer would show 240,000.
    expect(footerCells()).toEqual(['120,000', '12,000', '0.10'])
  })

  test('an unusable entry holds the last valid figures', async () => {
    seedCamp()
    renderSubPage('CAMP')
    const user = userEvent.setup()
    const cost = await screen.findByDisplayValue('10,000')

    await user.clear(cost)
    await user.type(cost, '1e3') // parses under toNum, rejected by the wire parser
    await user.tab()
    expect(footerCells()).toEqual(['360,000', '13,000', '0.04'])
  })

  test('read-only renders the served figures untouched (AC7)', async () => {
    // A totals block that disagrees with its own rows: a recomputing view would show 13,000.
    seedCamp(
      campDoc({ editable: false, totals: { volume: 999, cost: 999999, costPerVolume: 9.99 } }),
    )
    renderSubPage('CAMP')
    await screen.findByText('Totals:')
    expect(footerCells()).toEqual(['999', '999,999', '9.99'])
  })
})

describe('rendering (AC9)', () => {
  test('the CAMP page renders its legacy headers, columns and Totals footer', async () => {
    seedCamp()
    renderSubPage('CAMP')

    expect(await screen.findByText('Add Other Camp Expense')).toBeInTheDocument()
    expect(screen.getByText('Other Camp Expenses')).toBeInTheDocument()
    // By ROLE, not text: every editable cell carries a visually-hidden label with the same word, so
    // a text query matches the header AND one label per row.
    expect(screen.getByRole('columnheader', { name: 'Description' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Cost $' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Action' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /Volume m/ })).toBeInTheDocument()
    expect(screen.getByText('Totals:')).toBeInTheDocument()
  })

  test('the ACCESS page renders ITS headers', async () => {
    server.use(http.get(ACCESS_URL, () => HttpResponse.json(accessDoc())))
    renderSubPage('ACCESS')

    expect(await screen.findByText('Add Other Access Expense')).toBeInTheDocument()
    expect(screen.getByText('Other Access Expenses')).toBeInTheDocument()
  })

  test('the two footers differ: CAMP sums the row volumes, ACCESS uses the camp volume', async () => {
    // Both documents carry a 120,000 camp volume. The camp footer reports 360,000 (3 × 120,000) and
    // the access footer reports 120,000 for two rows — deviation (C), rendered verbatim from the
    // server. Nothing is summed on the client.
    seedCamp()
    const { unmount } = renderSubPage('CAMP')
    const campTotals = (await screen.findByText('Totals:')).closest('tr') as HTMLElement
    expect(within(campTotals).getByText('360,000')).toBeInTheDocument()
    expect(within(campTotals).getByText('13,000')).toBeInTheDocument()
    unmount()

    server.use(http.get(ACCESS_URL, () => HttpResponse.json(accessDoc())))
    renderSubPage('ACCESS')
    const accessTotals = (await screen.findByText('Totals:')).closest('tr') as HTMLElement
    expect(within(accessTotals).getByText('120,000')).toBeInTheDocument()
    expect(within(accessTotals).getByText('10,000')).toBeInTheDocument()
  })

  test('the add-form Volume is ALWAYS disabled, even on an editable schedule', async () => {
    seedCamp()
    renderSubPage('CAMP')
    const volume = await screen.findByLabelText('Volume:')
    expect(volume).toBeDisabled()
    expect(volume).toHaveValue('120,000')
  })

  test('an empty list renders the legacy empty-state text and a null total', async () => {
    seedCamp(campDoc({ rows: [], totals: {} }))
    renderSubPage('CAMP')
    expect(await screen.findByText('No records found.')).toBeInTheDocument()
  })

  test('a null description renders blank rather than "null"', async () => {
    seedCamp()
    renderSubPage('CAMP')
    const descriptions = await rowInputs(/^Description$/)
    expect(descriptions[2]).toHaveValue('')
  })
})

describe('add and save (AC11)', () => {
  test('Add PUTs the full list INCLUDING an edited grid row', async () => {
    // Legacy dropped un-submitted grid edits at Add and then overwrote them from the reload
    // (deviation (E)); that discard is a defect and is not ported.
    seedCamp(describedCampDoc())
    let body: unknown = null
    server.use(
      http.put(CAMP_URL, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(
          describedCampDoc({ message: { key: 'k', text: 'Data saved successfully' } }),
        )
      }),
    )
    const user = userEvent.setup()
    renderSubPage('CAMP')

    const descriptions = await rowInputs(/^Description$/)
    await user.clear(descriptions[0])
    await user.type(descriptions[0], 'Generator Diesel')

    await user.type(screen.getByLabelText('Description:'), 'Chainsaw Fuel')
    await user.type(screen.getByLabelText('Cost $:'), '750')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => {
      expect(body).not.toBeNull()
    })
    // The edited grid row travels WITH the added one. Legacy dropped it (deviation (E)).
    expect(body).toEqual({
      rows: [
        { rowId: 8722, description: 'Generator Diesel', cost: 10000 },
        { rowId: 8723, description: 'Propane', cost: 2500 },
        { rowId: null, description: 'Chainsaw Fuel', cost: 750 },
      ],
    })
  })

  test('every mutation URL carries millId and year', async () => {
    // No test catches their absence unless a handler inspects request.url.
    seedCamp(describedCampDoc())
    let url = ''
    server.use(
      http.put(CAMP_URL, ({ request }) => {
        url = request.url
        return HttpResponse.json(describedCampDoc())
      }),
    )
    const user = userEvent.setup()
    renderSubPage('CAMP')

    await screen.findByText('Other Camp Expenses')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(url).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    })
    expect(url).toContain(`year=${String(DEFAULT_YEAR)}`)
  })

  test('the success message renders verbatim from the server', async () => {
    seedCamp(describedCampDoc())
    server.use(
      http.put(CAMP_URL, () =>
        HttpResponse.json(
          describedCampDoc({ message: { key: 'k', text: 'Data saved successfully' } }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderSubPage('CAMP')
    await screen.findByText('Other Camp Expenses')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('a 400 renders the verbatim detail and leaves entered values in place', async () => {
    seedCamp(describedCampDoc())
    server.use(
      http.put(CAMP_URL, () =>
        problemBody(400, 'Entered cost must be between -9,999,999 and 9,999,999.'),
      ),
    )
    const user = userEvent.setup()
    renderSubPage('CAMP')

    const descriptions = await rowInputs(/^Description$/)
    await user.clear(descriptions[0])
    await user.type(descriptions[0], 'Kept On Failure')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByText('Entered cost must be between -9,999,999 and 9,999,999.'),
    ).toBeInTheDocument()
    expect((await rowInputs(/^Description$/))[0]).toHaveValue('Kept On Failure')
  })
})

describe('the required-description timing (AC10)', () => {
  test('ACCESS rejects a cleared grid description ON CHANGE', async () => {
    server.use(http.get(ACCESS_URL, () => HttpResponse.json(accessDoc())))
    const user = userEvent.setup()
    renderSubPage('ACCESS')

    const descriptions = await rowInputs(/^Description$/)
    await user.clear(descriptions[0])

    // schedule5AccessExpenses.xhtml:63 carries <f:ajax event="change">.
    expect(await screen.findByText('Value Required')).toBeInTheDocument()
  })

  test('CAMP does NOT — it defers to Save, and that is the whole of S22', async () => {
    seedCamp()
    const user = userEvent.setup()
    renderSubPage('CAMP')

    const descriptions = await rowInputs(/^Description$/)
    await user.clear(descriptions[0])
    await flushAsync()

    // schedule5CampExpenses.xhtml:64-67 has NO f:ajax. Making both pages validate on change would
    // erase the distinction S21 and S22 exist to describe.
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()

    let called = false
    server.use(
      http.put(CAMP_URL, () => {
        called = true
        return HttpResponse.json(campDoc())
      }),
    )
    await user.click(screen.getByRole('button', { name: 'Save' }))
    // Scoped to row 0 — the seeded row 8724 also has no description, so Save legitimately flags it
    // at the same moment.
    expect(
      await screen.findByText('Value Required', {
        selector: '#sub-page-row-description-0-error-msg',
      }),
    ).toBeInTheDocument()
    await flushAsync()
    // A client rejection issues NO request.
    expect(called).toBe(false)
  })

  test('ACCESS on-change validates ONLY the changed row — untouched blank rows stay unflagged', async () => {
    // Row 8726 is a legally-stored blank description (deviation (F)) the licensee never touched.
    // Legacy's <f:ajax event="change"> processes only the input that changed, so editing another
    // row must not flag it — whole-grid change validation was a review finding (2026-08-12).
    server.use(
      http.get(ACCESS_URL, () =>
        HttpResponse.json(
          accessDoc({
            rows: [
              {
                rowId: 8725,
                description: 'Bridge Rental',
                volume: 120000,
                cost: 7000,
                costPerVolume: 0.06,
              },
              { rowId: 8726, volume: 120000, cost: 3000, costPerVolume: 0.03 },
            ],
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderSubPage('ACCESS')

    const costs = await rowInputs(/^Cost \$$/)
    await user.type(costs[0], '1')
    await flushAsync()

    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('the COST band does not fire on change on either page — it belongs to Add/Save', async () => {
    // Neither page's cost input carries f:ajax, so legacy surfaces the band only at submit. A
    // change-time band error on the Camp grid was an unrecorded deviation (review, 2026-08-12).
    seedCamp(describedCampDoc())
    const user = userEvent.setup()
    renderSubPage('CAMP')

    const costs = await rowInputs(/^Cost \$$/)
    await user.clear(costs[0])
    await user.type(costs[0], '10000000')
    await flushAsync()
    expect(
      screen.queryByText('Entered cost must be between -9,999,999 and 9,999,999.'),
    ).not.toBeInTheDocument()

    // …and Save is where it lands, with no request issued.
    let called = false
    server.use(
      http.put(CAMP_URL, () => {
        called = true
        return HttpResponse.json(describedCampDoc())
      }),
    )
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(
      await screen.findByText('Entered cost must be between -9,999,999 and 9,999,999.'),
    ).toBeInTheDocument()
    await flushAsync()
    expect(called).toBe(false)
  })

  test('both ADD forms require a description (deviation (A))', async () => {
    seedCamp()
    const user = userEvent.setup()
    renderSubPage('CAMP')

    await screen.findByText('Add Other Camp Expense')
    await user.type(screen.getByLabelText('Cost $:'), '100')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    // Scoped to the ADD form's own error node: Add commits the WHOLE list (AC11), so it also
    // validates the grid — and the seeded row 8724 has no description, which legitimately raises a
    // second 'Value Required' at the same moment.
    expect(
      await screen.findByText('Value Required', {
        selector: '#sub-page-add-description-error-msg',
      }),
    ).toBeInTheDocument()
  })
})

describe('cost bands (AC5)', () => {
  test('CAMP rejects 10,000,000 with its own message', async () => {
    seedCamp()
    const user = userEvent.setup()
    renderSubPage('CAMP')

    await user.type(await screen.findByLabelText('Description:'), 'Too Big')
    await user.type(screen.getByLabelText('Cost $:'), '10000000')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(
      await screen.findByText('Entered cost must be between -9,999,999 and 9,999,999.'),
    ).toBeInTheDocument()
  })

  test('ACCESS ACCEPTS 10,000,000 — the same value, the other page', async () => {
    server.use(http.get(ACCESS_URL, () => HttpResponse.json(accessDoc())))
    let called = false
    server.use(
      http.put(ACCESS_URL, () => {
        called = true
        return HttpResponse.json(accessDoc())
      }),
    )
    const user = userEvent.setup()
    renderSubPage('ACCESS')

    await user.type(await screen.findByLabelText('Description:'), 'Wide')
    await user.type(screen.getByLabelText('Cost $:'), '10000000')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    await waitFor(() => {
      expect(called).toBe(true)
    })
    expect(
      screen.queryByText('Entered cost must be between -99,999,999 and 99,999,999.'),
    ).not.toBeInTheDocument()
  })
})

describe('row delete and Back (AC12)', () => {
  test('Delete confirms with CFM-001, then DELETEs that row', async () => {
    seedCamp()
    let deletedUrl = ''
    server.use(
      http.delete(`${CAMP_URL}/:rowId`, ({ request }) => {
        deletedUrl = request.url
        return HttpResponse.json(
          campDoc({ message: { key: 'k', text: 'Data deleted successfully' } }),
        )
      }),
    )
    const user = userEvent.setup()
    renderSubPage('CAMP')

    const deletes = await screen.findAllByRole('button', { name: 'Delete' })
    await user.click(deletes[0])

    const dialog = await screen.findByText(
      'This will delete the current record. Do you want to continue?',
    )
    expect(dialog).toBeInTheDocument()
    await user.click(
      within(
        confirmDialog('This will delete the current record. Do you want to continue?'),
      ).getByRole('button', { name: 'Yes' }),
    )

    await waitFor(() => {
      expect(deletedUrl).toContain('/8722')
    })
    expect(deletedUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
  })

  test('a delete PRESERVES in-flight edits typed into other rows across the echo reseed', async () => {
    // Deviation (E)'s principle applied to Delete (review decision, 2026-08-12): the echo reseeds
    // the grid, and without the draft re-apply an edit typed into a SURVIVING row would be
    // silently discarded — the exact legacy discard the story refuses to port on Add.
    seedCamp(describedCampDoc())
    server.use(
      http.delete(`${CAMP_URL}/:rowId`, () =>
        HttpResponse.json(
          describedCampDoc({
            rows: [
              // The echo serves the SURVIVOR with its stored (un-edited) description.
              {
                rowId: 8723,
                description: 'Propane',
                volume: 120000,
                cost: 2500,
                costPerVolume: 0.02,
              },
            ],
            totals: { volume: 120000, cost: 2500, costPerVolume: 0.02 },
            message: { key: 'k', text: 'Data deleted successfully' },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderSubPage('CAMP')

    // Edit row 1 (8723), then delete row 0 (8722).
    const descriptions = await rowInputs(/^Description$/)
    await user.clear(descriptions[1])
    await user.type(descriptions[1], 'Propane Deluxe')
    const deletes = screen.getAllByRole('button', { name: 'Delete' })
    await user.click(deletes[0])
    await user.click(
      within(
        confirmDialog('This will delete the current record. Do you want to continue?'),
      ).getByRole('button', { name: 'Yes' }),
    )

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // The surviving row still shows the in-flight edit, not the echoed stored value.
    expect((await rowInputs(/^Description$/))[0]).toHaveValue('Propane Deluxe')
  })

  test('declining the confirm issues NO request', async () => {
    seedCamp()
    let called = false
    server.use(
      http.delete(`${CAMP_URL}/:rowId`, () => {
        called = true
        return HttpResponse.json(campDoc())
      }),
    )
    const user = userEvent.setup()
    renderSubPage('CAMP')

    const deletes = await screen.findAllByRole('button', { name: 'Delete' })
    await user.click(deletes[0])
    await user.click(
      within(
        confirmDialog('This will delete the current record. Do you want to continue?'),
      ).getByRole('button', { name: 'No' }),
    )
    await flushAsync()

    expect(called).toBe(false)
  })

  test('Back confirms with CFM-002 when editable', async () => {
    seedCamp()
    const onBack = vi.fn()
    const user = userEvent.setup()
    renderSubPage('CAMP', onBack)

    await user.click(await screen.findByRole('button', { name: 'Back' }))
    expect(
      await screen.findByText(
        'Any unsaved data will be lost. Are you sure you would like to continue?',
      ),
    ).toBeInTheDocument()
    expect(onBack).not.toHaveBeenCalled()

    await user.click(
      within(
        confirmDialog('Any unsaved data will be lost. Are you sure you would like to continue?'),
      ).getByRole('button', { name: 'Yes' }),
    )
    expect(onBack).toHaveBeenCalled()
  })

  test('Back in READ-ONLY navigates with no confirm — legacy renders a bare Back', async () => {
    seedCamp(campDoc({ editable: false }))
    const onBack = vi.fn()
    const user = userEvent.setup()
    renderSubPage('CAMP', onBack)

    await user.click(await screen.findByRole('button', { name: 'Back' }))
    expect(onBack).toHaveBeenCalled()
    expect(
      screen.queryByText('Any unsaved data will be lost. Are you sure you would like to continue?'),
    ).not.toBeInTheDocument()
  })
})

describe('editability (AC15)', () => {
  test('editable:false disables Add, the row inputs, Delete and Save — but NOT Back', async () => {
    seedCamp(campDoc({ editable: false }))
    renderSubPage('CAMP')

    expect(await screen.findByRole('button', { name: 'Add' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    screen.getAllByRole('button', { name: 'Delete' }).forEach((button) => {
      expect(button).toBeDisabled()
    })
    const descriptions = await rowInputs(/^Description$/)
    descriptions.forEach((input) => {
      expect(input).toBeDisabled()
    })
    // Back stays enabled: a read-only licensee must still be able to leave the page.
    expect(screen.getByRole('button', { name: 'Back' })).toBeEnabled()
  })

  test('a read-only page renders ONE Save, not legacy’s duplicate (deviation (K))', async () => {
    seedCamp(campDoc({ editable: false }))
    renderSubPage('CAMP')
    await screen.findByText('Other Camp Expenses')
    expect(screen.getAllByRole('button', { name: 'Save' })).toHaveLength(1)
  })
})

describe('load failures', () => {
  test('a 404 renders the verbatim detail instead of the grid', async () => {
    server.use(http.get(CAMP_URL, () => problemBody(404, 'Camp not found.')))
    renderSubPage('CAMP')
    expect(await screen.findByText('Camp not found.')).toBeInTheDocument()
    expect(screen.queryByText('Totals:')).not.toBeInTheDocument()
  })
})

describe('stale-context safety', () => {
  test('a mill/year change mid-save does not apply the stale response (Task 10)', async () => {
    // The PUT is gated on an explicit release, never a wall-clock delay (the 7.3 review finding).
    // In the real app the parent REMOUNTS the sub-page on a context change; this harness does not,
    // which is exactly what isolates the component's own `isCurrent()` guards on the mutation tail.
    let releasePut = () => {}
    const putGate = new Promise<void>((resolve) => {
      releasePut = resolve
    })
    server.use(
      http.get(CAMP_URL, ({ request }) =>
        HttpResponse.json(
          request.url.includes('millId=999')
            ? describedCampDoc({
                rows: [
                  {
                    rowId: 9101,
                    description: 'New Context Row',
                    volume: 50000,
                    cost: 100,
                    costPerVolume: 0,
                  },
                ],
              })
            : describedCampDoc(),
        ),
      ),
      http.put(CAMP_URL, async () => {
        await putGate
        return HttpResponse.json(
          describedCampDoc({ message: { key: 'k', text: 'Data saved successfully' } }),
        )
      }),
    )
    render(
      <MillYearProvider initial={{ millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }}>
        <SubPageRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await screen.findByDisplayValue('Generator Fuel')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await user.click(screen.getByRole('button', { name: 'change' }))

    // The new context's document loads…
    expect(await screen.findByDisplayValue('New Context Row')).toBeInTheDocument()
    // …then the stale PUT settles, and nothing from it may land: no success banner, no old rows.
    releasePut()
    await flushAsync()
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(screen.queryByDisplayValue('Generator Fuel')).not.toBeInTheDocument()
  })
})
