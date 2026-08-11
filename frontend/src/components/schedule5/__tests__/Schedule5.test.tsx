import type { ReactNode } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

// jsdom lacks scrollIntoView; Carbon calls it on a highlighted option.
window.HTMLElement.prototype.scrollIntoView = vi.fn()

import Schedule5 from '@/components/schedule5'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import type CampRequest from '@/interfaces/Schedule5Request'
import type { Camp } from '@/interfaces/Schedule5Response'

const URL = 'http://localhost:3000/api/v1/schedule5'
const CAMPS_URL = `${URL}/camps`
const CAMP_URL = `${URL}/camps/:campId`
const CHECK_URL = `${URL}/check-status`
const MESSAGES_URL = 'http://localhost:3000/api/v1/messages'

// Camp 8401 exactly as Story 7.1 pinned it (its literals are the shipped seed's), so a mock built
// from this block and the real API agree.
const cedarFlats: Camp = {
  campId: 8401,
  revisionCount: 0, // a FALSY but entirely valid optimistic-lock token
  campName: 'Cedar Flats Camp',
  roadDistanceToOperatingArea: 42.5,
  sizeOfCamp: 60,
  associatedCampVolume: 120000,
  isolatedCamp: true,
  comments: 'Seasonal camp, spring only.',
  cateringAndFood: { volume: 96000, cost: 480000, costPerVolume: 5.0 },
  wagesAndBenefits: { volume: 120000, cost: 960000, costPerVolume: 8.0 },
  depreciationLease: { volume: 120000, cost: 120000, costPerVolume: 1.0 },
  generalCampExpenses: { volume: 120000, cost: 60000, costPerVolume: 0.5 },
  otherCampExpenses: { volume: 80000, cost: 24000, costPerVolume: 0.31 },
  campSubTotal: { volume: 120000, cost: 1644000, costPerVolume: 13.7 },
  recoveries: { cost: 44000 },
  campTotal: { volume: 120000, cost: 1600000, costPerVolume: 13.33 },
  crewTransportation: { volume: 90000, cost: 180000, costPerVolume: 2.0 },
  equipAndSuppliesLand: { volume: 120000, cost: 90000, costPerVolume: 0.75 },
  equipAndSuppliesRail: { volume: 120000, cost: 15000, costPerVolume: 0.13 },
  equipAndSuppliesAir: { volume: 120000, cost: 12000, costPerVolume: 0.1 },
  equipAndSuppliesWater: { volume: 120000, cost: 6000, costPerVolume: 0.05 },
  otherAccessExpenses: { volume: 60000, cost: 3000, costPerVolume: 0.05 },
  accessExpenseTotal: { volume: 120000, cost: 306000, costPerVolume: 2.55 },
  campAndAccessTotal: { volume: 120000, cost: 1906000, costPerVolume: 15.88 },
  otherCampExpenseCount: 3,
  otherAccessExpenseCount: 1,
}

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  camps: [cedarFlats],
  ...overrides,
})

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

/** The Carbon modal carrying this verbatim confirm text (all three are always in the DOM). */
const confirmDialog = (text: string): HTMLElement =>
  screen.getByText(text).closest('.cds--modal') as HTMLElement

/** Open the camp panel for editing and wait for it to seat. */
const openEditor = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(await screen.findByRole('button', { name: /^edit$/i }))
  return screen.findByLabelText('Camp Name')
}

/**
 * A button inside the camp panel. Scoped because Carbon's always-mounted modals each contribute a
 * "Close" icon button, so an unscoped /^close$/i matches four things.
 */
const panelButton = (name: RegExp): HTMLElement =>
  within(document.querySelector('.schedule-5__panel') as HTMLElement).getByRole('button', { name })

// Drives a mid-flight mill/year change so the stale-response guard can be exercised. Module-level so
// it is not re-created per render (an @eslint-react rule forbids nested component definitions).
const StaleRaceHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <Schedule5 />
    </>
  )
}

describe('Schedule 5 camps table (AC1, AC2)', () => {
  test('lists camps in served order under Existing Camps, with exactly two columns', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)

    expect(await screen.findByText('Cedar Flats Camp')).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Existing Camps' })
    const headers = within(table).getAllByRole('columnheader')
    expect(headers).toHaveLength(2)
    expect(headers[0]).toHaveTextContent('Camp Name')
    expect(headers[1]).toHaveTextContent('Action')
    // No descriptor columns: legacy's table shows the name alone.
    expect(within(table).queryByText('42.5')).not.toBeInTheDocument()
  })

  test('zero camps render the legacy empty message, Add New Camp still enabled', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ camps: [] }))))
    render(<Schedule5 />)

    expect(await screen.findByText('No records found.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add new camp/i })).toBeEnabled()
  })

  test('editable with NO panel open: Edit fires no confirm, it opens the panel directly', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()

    await openEditor(user)
    expect(screen.getByLabelText('Camp Name')).toHaveValue('Cedar Flats Camp')
  })

  test('editable WITH a panel open: Edit fires CFM-003 before switching', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()

    await openEditor(user)
    await user.clear(screen.getByLabelText('Camp Name'))
    await user.type(screen.getByLabelText('Camp Name'), 'Edited Name')

    await user.click(screen.getByRole('button', { name: /^edit$/i }))
    const dialog = confirmDialog(
      'Any unsaved changes to the current camp report will be lost. Are you sure you would like to continue?',
    )
    await user.click(within(dialog).getByRole('button', { name: /^yes$/i }))

    // The draft is discarded and the panel re-seats on the stored camp.
    await waitFor(() => expect(screen.getByLabelText('Camp Name')).toHaveValue('Cedar Flats Camp'))
  })

  test('Add New Camp fires CFM-003 when a panel is already open', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()

    await openEditor(user)
    await user.click(screen.getByRole('button', { name: /add new camp/i }))
    const dialog = confirmDialog(
      'Any unsaved changes to the current camp report will be lost. Are you sure you would like to continue?',
    )
    await user.click(within(dialog).getByRole('button', { name: /^yes$/i }))

    expect(await screen.findByText('New Camp Details')).toBeInTheDocument()
    expect(screen.getByLabelText('Camp Name')).toHaveValue('')
  })

  test('read-only collapses the row actions to a single View (deviation (B))', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule5 />)

    expect(await screen.findByRole('button', { name: /^view$/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^edit$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^copy$/i })).not.toBeInTheDocument()
  })
})

describe('Schedule 5 camp panel (AC4, AC5, AC7)', () => {
  test('the existing-camp panel is headed by the camp name; the new panel by the literal', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()

    await openEditor(user)
    expect(screen.getByRole('heading', { name: 'Cedar Flats Camp' })).toBeInTheDocument()

    await user.click(panelButton(/^close$/i))
    const dialog = confirmDialog(
      'Any unsaved data will be lost. Are you sure you would like to continue?',
    )
    await user.click(within(dialog).getByRole('button', { name: /^yes$/i }))

    await user.click(screen.getByRole('button', { name: /add new camp/i }))
    expect(await screen.findByRole('heading', { name: 'New Camp Details' })).toBeInTheDocument()
  })

  test('the grid renders every legacy row in order with its legacy label', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const grid = screen.getByRole('table', { name: 'Camp and access expenses' })
    const labels = within(grid)
      .getAllByRole('row')
      .slice(1)
      .map((row) => row.querySelector('td')?.textContent ?? '')

    expect(labels).toEqual([
      'Camp Expenses',
      'Catering and Food: ',
      'Wages and Benefits: ',
      'Depreciation/Lease: ',
      'General Camp Expenses: ',
      'Other Camp Expenses (3): ',
      'Camp Sub-Total: ',
      'Recoveries: ',
      'Camp Total: ',
      'Access Expenses',
      'Crew Transportation: ',
      'Equipment and Supplies Transportation',
      'Land: ',
      'Rail: ',
      'Air: ',
      'Water: ',
      'Other Access Expenses (1): ',
      'Access Expense Total: ',
      'Total Expense',
      'Camp and Access: ',
    ])
  })

  test('derived rows and $/m³ render server values with the legacy masks, never recomputed', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const grid = screen.getByRole('table', { name: 'Camp and access expenses' })
    const subTotal = within(grid).getByText('Camp Sub-Total:').closest('tr') as HTMLElement
    expect(within(subTotal).getByText('1,644,000')).toBeInTheDocument()
    expect(within(subTotal).getByText('13.70')).toBeInTheDocument()
    expect(within(subTotal).queryByRole('textbox')).not.toBeInTheDocument()

    const total = within(grid).getByText('Camp and Access:').closest('tr') as HTMLElement
    expect(within(total).getByText('1,906,000')).toBeInTheDocument()
    expect(within(total).getByText('15.88')).toBeInTheDocument()
  })

  test('Recoveries has a cost but NO volume and NO $/m³ cell', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    expect(screen.getByLabelText('Recoveries cost')).toHaveValue('44,000')
    expect(screen.queryByLabelText('Recoveries volume')).not.toBeInTheDocument()
  })

  test('the two Other … rows have an editable volume but a read-only server cost', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    expect(screen.getByLabelText('Other Camp Expenses volume')).toHaveValue('80,000')
    expect(screen.queryByLabelText('Other Camp Expenses cost')).not.toBeInTheDocument()
    const grid = screen.getByRole('table', { name: 'Camp and access expenses' })
    const row = within(grid).getByText('Other Camp Expenses (3):').closest('tr') as HTMLElement
    expect(within(row).getByText('24,000')).toBeInTheDocument()
  })

  test('a null amount renders BLANK, never 0', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ camps: [{ ...cedarFlats, recoveries: undefined }] })),
      ),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    expect(screen.getByLabelText('Recoveries cost')).toHaveValue('')
  })

  test('a null isolatedCamp renders as nothing selected and blocks the save (FLD-001)', async () => {
    let posted = false
    server.use(
      http.get(URL, () =>
        HttpResponse.json(doc({ camps: [{ ...cedarFlats, isolatedCamp: null }] })),
      ),
      http.put(CAMP_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    expect(screen.getByLabelText('Isolated Camp')).toHaveValue('')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Isolated Camp is required.')).toBeInTheDocument()
    await delay(50)
    expect(posted).toBe(false)
  })
})

describe('Schedule 5 writes (AC6, AC8)', () => {
  test('a PUT carries ALL TWELVE categories and the row’s own revisionCount', async () => {
    let captured: CampRequest | null = null
    let putUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, async ({ request }) => {
        captured = (await request.json()) as CampRequest
        putUrl = request.url
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    const body = captured as unknown as CampRequest

    // All twelve present — an omitted entry would CLEAR both halves server-side.
    for (const key of [
      'cateringAndFood',
      'wagesAndBenefits',
      'depreciationLease',
      'generalCampExpenses',
      'otherCampExpenses',
      'recoveries',
      'crewTransportation',
      'equipAndSuppliesLand',
      'equipAndSuppliesRail',
      'equipAndSuppliesAir',
      'equipAndSuppliesWater',
      'otherAccessExpenses',
    ] as const) {
      expect(body[key], `${key} missing from the write body`).toBeDefined()
    }

    // Only the half each category actually stores.
    expect(body.otherCampExpenses).toEqual({ volume: 80000 })
    expect(body.otherAccessExpenses).toEqual({ volume: 60000 })
    expect(body.recoveries).toEqual({ cost: 44000 })
    expect(body.cateringAndFood).toEqual({ volume: 96000, cost: 480000 })

    // No derived figure is ever sent.
    expect(body).not.toHaveProperty('campSubTotal')
    expect(body).not.toHaveProperty('campAndAccessTotal')
    expect(body).not.toHaveProperty('otherCampExpenseCount')

    // The FALSY 0 token is sent as 0, not dropped and not coerced.
    expect(body.revisionCount).toBe(0)

    expect(putUrl).toContain('/camps/8401')
    expect(putUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(putUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('BR-03: changing the camp volume clobbers all eleven category volumes IN THE BODY', async () => {
    let captured: CampRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, async ({ request }) => {
        captured = (await request.json()) as CampRequest
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const campVolume = screen.getByLabelText('Associated Camp Volume (m³)')
    await user.clear(campVolume)
    await user.type(campVolume, '55000')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    const body = captured as unknown as CampRequest
    // Catering was 96,000 — a per-category value the propagation must overwrite unconditionally.
    expect(body.cateringAndFood.volume).toBe(55000)
    expect(body.crewTransportation.volume).toBe(55000)
    expect(body.otherAccessExpenses.volume).toBe(55000)
    // Recoveries is the volume-less twelfth and must NOT gain one.
    expect(body.recoveries).toEqual({ cost: 44000 })
  })

  test('BR-03 does NOT re-run at save: a volume edited afterwards survives', async () => {
    let captured: CampRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, async ({ request }) => {
        captured = (await request.json()) as CampRequest
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const campVolume = screen.getByLabelText('Associated Camp Volume (m³)')
    await user.clear(campVolume)
    await user.type(campVolume, '55000')
    // Edit ONE category volume after the propagation.
    const catering = screen.getByLabelText('Catering and Food volume')
    await user.clear(catering)
    await user.type(catering, '77000')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    const body = captured as unknown as CampRequest
    expect(body.cateringAndFood.volume).toBe(77000)
    // Its siblings still carry the propagated value.
    expect(body.wagesAndBenefits.volume).toBe(55000)
  })

  test('a new camp POSTs without a revisionCount', async () => {
    let captured: CampRequest | null = null
    let postUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ camps: [] }))),
      http.post(CAMPS_URL, async ({ request }) => {
        captured = (await request.json()) as CampRequest
        postUrl = request.url
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /add new camp/i }))
    await user.type(screen.getByLabelText('Camp Name'), 'Cedar Flats Camp')
    await user.selectOptions(screen.getByLabelText('Isolated Camp'), 'true')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    const body = captured as unknown as CampRequest
    expect(body.revisionCount).toBeUndefined()
    expect(body.campName).toBe('Cedar Flats Camp')
    // Untouched categories still travel, both halves null — null CLEARS, and that is the intent.
    expect(body.cateringAndFood).toEqual({ volume: null, cost: null })
    expect(postUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(postUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
  })

  test('a 400 renders the verbatim detail and leaves every entered value in place', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, () =>
        problemBody(400, 'Entered cost must be between -9,999,999 and 9,999,999.'),
      ),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const name = screen.getByLabelText('Camp Name')
    await user.clear(name)
    await user.type(name, 'Renamed Camp')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      await screen.findByText('Entered cost must be between -9,999,999 and 9,999,999.'),
    ).toBeInTheDocument()
    // The panel stays open with the entered value so a corrected save can retry.
    expect(screen.getByLabelText('Camp Name')).toHaveValue('Renamed Camp')
  })

  test('Delete confirms with CFM-001, then DELETEs with the mill/year query', async () => {
    let deleteUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(CAMP_URL, ({ request }) => {
        deleteUrl = request.url
        return HttpResponse.json(
          doc({
            camps: [],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        )
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^delete$/i }))
    const dialog = confirmDialog('This will delete the current record. Do you want to continue?')
    await user.click(within(dialog).getByRole('button', { name: /^yes$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(deleteUrl).toContain('/camps/8401')
    expect(deleteUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(deleteUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
  })
})

describe('Schedule 5 copy (AC9)', () => {
  test('prefills the amounts, blanks the name, zeroes the Other counts, and writes nothing', async () => {
    let wrote = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.get(MESSAGES_URL, ({ request }) => {
        const url = new global.URL(request.url)
        return HttpResponse.json({
          key: url.searchParams.get('key'),
          text: `To complete copy of Camp: ${url.searchParams.get('arg') ?? ''}, provide a new Camp Name and invoke save.`,
        })
      }),
      http.post(CAMPS_URL, () => {
        wrote = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^copy$/i }))

    // Name blanked; amounts and descriptors carried over.
    expect(await screen.findByRole('heading', { name: 'New Camp Details' })).toBeInTheDocument()
    expect(screen.getByLabelText('Camp Name')).toHaveValue('')
    expect(screen.getByLabelText('Catering and Food cost')).toHaveValue('480,000')
    expect(screen.getByLabelText('Associated Camp Volume (m³)')).toHaveValue('120,000')

    // Both sub-page counts reset to zero — a copy carries no sub-page rows.
    const grid = screen.getByRole('table', { name: 'Camp and access expenses' })
    expect(within(grid).getByText('Other Camp Expenses (0):')).toBeInTheDocument()
    expect(within(grid).getByText('Other Access Expenses (0):')).toBeInTheDocument()

    // WRN-001, resolved from the bundle rather than hardcoded.
    expect(
      await screen.findByText(
        'To complete copy of Camp: Cedar Flats Camp, provide a new Camp Name and invoke save.',
      ),
    ).toBeInTheDocument()

    // Copy itself never writes.
    await delay(50)
    expect(wrote).toBe(false)
  })

  test('a failed message resolve leaves the banner absent rather than inventing text', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.get(MESSAGES_URL, () => problemBody(404, 'Message not found.')),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^copy$/i }))

    expect(await screen.findByRole('heading', { name: 'New Camp Details' })).toBeInTheDocument()
    await delay(50)
    expect(screen.queryByText(/to complete copy of camp/i)).not.toBeInTheDocument()
  })

  test('an unrenamed copy is an ordinary POST and its 409 renders verbatim', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.get(MESSAGES_URL, () => HttpResponse.json({ key: 'sch5.copy.msg', text: 'warn' })),
      http.post(CAMPS_URL, () => problemBody(409, 'Camp name already exists.')),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^copy$/i }))
    await user.type(await screen.findByLabelText('Camp Name'), 'Cedar Flats Camp')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Camp name already exists.')).toBeInTheDocument()
  })
})

describe('Schedule 5 advisory validation (AC13)', () => {
  test('wages accepts 10,000,000 while its eight siblings reject it', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    // A sibling at 10,000,000 is rejected client-side, with no request issued.
    const catering = screen.getByLabelText('Catering and Food cost')
    await user.clear(catering)
    await user.type(catering, '10000000')
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    expect(
      await screen.findByText('Entered cost must be between -9,999,999 and 9,999,999.'),
    ).toBeInTheDocument()
    await delay(50)
    expect(posted).toBe(false)

    // The SAME value on wages is accepted and the write goes out.
    await user.clear(catering)
    await user.type(catering, '480000')
    const wages = screen.getByLabelText('Wages and Benefits cost')
    await user.clear(wages)
    await user.type(wages, '10000000')
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    await waitFor(() => expect(posted).toBe(true))
  })

  test('Recoveries rejects -1 with its own 0-floor message', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const recoveries = screen.getByLabelText('Recoveries cost')
    await user.clear(recoveries)
    await user.type(recoveries, '-1')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      await screen.findByText('Entered cost must be between 0 and 9,999,999.'),
    ).toBeInTheDocument()
  })
})

describe('Schedule 5 guards (AC10, AC11)', () => {
  test('no mill/year in context renders the notice and issues NO request', async () => {
    let requested = false
    server.use(
      http.get(URL, () => {
        requested = true
        return HttpResponse.json(doc())
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule5 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    await delay(50)
    expect(requested).toBe(false)
    expect(screen.queryByRole('button', { name: /add new camp/i })).not.toBeInTheDocument()
  })

  test('a 409 mill-not-active renders the ProblemDetail verbatim with no page content', async () => {
    server.use(
      http.get(URL, () =>
        problemBody(409, 'The Mill is not active for the current reporting year.'),
      ),
    )
    render(<Schedule5 />)

    expect(
      await screen.findByText('The Mill is not active for the current reporting year.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Existing Camps' })).not.toBeInTheDocument()
  })

  test('a 404 renders Schedule not found. verbatim', async () => {
    server.use(http.get(URL, () => problemBody(404, 'Schedule not found.')))
    render(<Schedule5 />)

    expect(await screen.findByText('Schedule not found.')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Existing Camps' })).not.toBeInTheDocument()
  })

  test('editable:false disables the whole write surface AND Check Status, values stay visible', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule5 />)
    const user = userEvent.setup()

    expect(await screen.findByText('Cedar Flats Camp')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add new camp/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /check status/i })).toBeDisabled()

    // The View panel shows the grid values but no inputs and no Save.
    await user.click(screen.getByRole('button', { name: /^view$/i }))
    const grid = await screen.findByRole('table', { name: 'Camp and access expenses' })
    expect(within(grid).getByText('480,000')).toBeInTheDocument()
    expect(within(grid).queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })
})

describe('Schedule 5 Check Status (AC12)', () => {
  test('MET renders the single schedule banner ALONE — no per-camp lines are synthesised', async () => {
    let checkUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, ({ request }) => {
        checkUrl = request.url
        return HttpResponse.json({
          outcome: 'MET',
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
          ],
          camps: [],
        })
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /check status/i }))

    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/have been met\./i)).not.toBeInTheDocument()
    expect(checkUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(checkUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
  })

  test('ISSUES renders one line per camp message, with severity following requirementsMet', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          camps: [
            {
              campId: 8401,
              campName: 'Cedar Flats Camp',
              requirementsMet: true,
              messages: [
                {
                  key: 'campRequirementsMetMsg',
                  text: 'All requirements for Cedar Flats Camp have been met.',
                },
              ],
            },
            {
              campId: 8402,
              campName: 'Bare Ridge Camp',
              requirementsMet: false,
              messages: [
                {
                  key: 'missingRequiredFieldMsg',
                  field: 'roadDistanceToOperatingArea',
                  text: 'Camp Report Name : Bare Ridge Camp - Road Distance to Operating Area: Value Required',
                },
              ],
            },
          ],
        }),
      ),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /check status/i }))

    expect(
      await screen.findByText('All requirements for Cedar Flats Camp have been met.'),
    ).toBeInTheDocument()
    // The composed text arrives whole from the server and renders verbatim.
    expect(
      screen.getByText(
        'Camp Report Name : Bare Ridge Camp - Road Distance to Operating Area: Value Required',
      ),
    ).toBeInTheDocument()
    // Severity is carried by a title word, never colour alone.
    expect(screen.getByText('Check Status — value required')).toBeInTheDocument()
  })

  test('Check Status is disabled while a panel holds unsaved entries (deviation (I))', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()

    expect(await screen.findByRole('button', { name: /check status/i })).toBeEnabled()
    await openEditor(user)
    expect(screen.getByRole('button', { name: /check status/i })).toBeDisabled()
  })
})

describe('Schedule 5 stale-context safety (AC14)', () => {
  test('a mill/year change mid-save does not apply the stale response', async () => {
    server.use(
      http.get(URL, ({ request }) =>
        HttpResponse.json(
          request.url.includes('millId=999')
            ? doc({ camps: [{ ...cedarFlats, campId: 9001, campName: 'Bare Ridge Camp' }] })
            : doc(),
        ),
      ),
      http.put(CAMP_URL, async () => {
        await delay(300)
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    render(
      <MillYearProvider initial={{ millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }}>
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await openEditor(user)
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Bare Ridge Camp')).toBeInTheDocument()
    // Let the stale PUT resolve, then confirm nothing from it landed.
    await delay(400)
    expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    expect(screen.queryByText('Cedar Flats Camp')).not.toBeInTheDocument()
  })
})
