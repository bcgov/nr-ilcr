import type { ReactNode } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import { getDefaultNormalizer, render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule7a from '@/components/schedule7a'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import type BridgeRequest from '@/interfaces/Schedule7aRequest'
import type { Bridge } from '@/interfaces/Schedule7aResponse'

const URL = 'http://localhost:3000/api/v1/schedule7a'
const BRIDGES_URL = `${URL}/bridges`
const CHECK_URL = `${URL}/check-status`

const CODE_LISTS = {
  constructionTypes: [
    { code: 'N', description: 'New' },
    { code: 'U', description: 'Used' },
  ],
  superstructureTypes: [{ code: 'STL', description: 'Steel' }],
  deckTypes: [{ code: 'WD', description: 'Wood' }],
  abutmentTypes: [{ code: 'CONC', description: 'Concrete' }],
  loadRatings: [{ code: 'L100', description: 'L-100' }],
}

const northFork: Bridge = {
  bridgeReportId: 7001,
  rowCounter: 1,
  locationName: 'North Fork Bridge',
  builtDate: '2020-06',
  constructionTypeCode: 'N',
  superstructureTypeCode: 'STL',
  deckTypeCode: 'WD',
  abutmentTypeCode: 'CONC',
  loadRatingCode: 'L100',
  lifeSpan: 50,
  abutmentHeight: 5.0,
  length: 20.0,
  width: 4.0,
  distance: 12,
  sitePlanCost: 1000,
  superstructureMaterialCost: 5000,
  superstructureDeliverCost: 500,
  superstructureInstallCost: 800,
  abutmentMaterialCost: 3000,
  abutmentDeliverCost: 300,
  abutmentInstallCost: 400,
  approachCost: 700,
  afterInstallCost: 200,
  otherCost: 100,
  comments: 'Spans the north fork',
  totalMaterial: 8000,
  totalDeliver: 800,
  totalInstall: 1200,
  grandTotal: 12000,
  revisionCount: 3,
}

const bridgeAt = (id: number, rowCounter: number, overrides: Partial<Bridge> = {}): Bridge => ({
  ...northFork,
  bridgeReportId: id,
  rowCounter,
  locationName: `Bridge ${String(rowCounter)}`,
  ...overrides,
})

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  bridges: [northFork],
  codeLists: CODE_LISTS,
  ...overrides,
})

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

// Preserves the literal whitespace the backend sends (the check-status double space, the ERR-001
// trailing space) so verbatim-rendering assertions are not defeated by the default collapse/trim.
const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

// The accordion body for one bridge, opened so its fields are reachable.
async function openBridge(user: ReturnType<typeof userEvent.setup>, rowCounter: number) {
  await user.click(
    await screen.findByRole('button', { name: `Bridge report Id: ${String(rowCounter)}` }),
  )
}

const field = (label: RegExp | string) => screen.getByLabelText(label)

// Scope to one bridge's accordion panel. The delete-confirm Modal is always mounted (Carbon renders
// it closed) and carries its own Delete/Cancel buttons, so an unscoped query matches both surfaces.
const bridgePanel = (bridgeReportId: number) =>
  within(
    document
      .getElementById(`bridge-${String(bridgeReportId)}-locationName`)
      ?.closest('.cds--accordion__item') as HTMLElement,
  )

// The Carbon Modal root, so its confirm button is distinguishable from the row action that opened it.
const deleteModal = async () => within(await screen.findByRole('presentation'))

// Drives a mill/year change mid-flight so the stale-response guard can be exercised. Module-level so
// it is not re-created per render (an @eslint-react rule forbids nested component definitions).
const ContextSwitchHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(600, 2019)}>
        switch context
      </button>
      <Schedule7a />
    </>
  )
}

// Fill the Add panel with a complete, valid bridge.
async function fillAddForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(field('Name/Location of Bridge'), 'South Creek Bridge')
  await user.type(field('Date'), '2021-03')
  await user.type(field('Expected Life Span'), '40')
  await user.type(field('Abutments Ht.(m)'), '3.5')
  await user.type(field('Length (m)'), '15.0')
  await user.type(field('Width (m)'), '4.0')
  await user.type(field('Distance (km)'), '8')
  for (const [name, option] of [
    ['New/Used', 'New'],
    ['Superstructure Type', 'Steel'],
    ['Decking Type', 'Wood'],
    ['Abutments Type', 'Concrete'],
    ['Load Rating', 'L-100'],
  ] as const) {
    await user.click(screen.getByRole('combobox', { name: new RegExp(name, 'i') }))
    await user.click(await screen.findByRole('option', { name: option }))
  }
}

describe('Schedule 7A page', () => {
  test('renders each bridge as an accordion row with legacy labels and server totals (AC1, AC2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)

    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    await openBridge(user, 1)

    expect(field('Name/Location of Bridge')).toHaveValue('North Fork Bridge')
    expect(field('Date')).toHaveValue('2020-06')
    expect(field('Expected Life Span')).toHaveValue('50')
    expect(field('Distance (km)')).toHaveValue('12')
    expect(field('Site Plan / Gen. Arr. ($)')).toHaveValue('1000')
    expect(field('Certification After install ($)')).toHaveValue('200')

    // Server-computed totals render as read-only text, never as inputs.
    expect(screen.getByText('12,000')).toBeInTheDocument()
    expect(screen.getByText('8,000')).toBeInTheDocument()
    expect(screen.queryByLabelText('Grand Total ($)')).not.toBeInTheDocument()
  })

  test('a null total renders blank, never 0 (AC2)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({
            bridges: [
              {
                ...northFork,
                totalMaterial: null,
                totalDeliver: null,
                totalInstall: null,
                grandTotal: null,
              },
            ],
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Assert every total's value node is genuinely EMPTY. Merely excluding the literal "0" would
    // also pass on "12,000", on an em dash, or on a component that failed to render at all.
    const values = document.querySelectorAll('.schedule-7a__total-value')
    expect(values).toHaveLength(4)
    for (const value of values) {
      expect(value).toHaveTextContent('')
    }
    expect(screen.queryByText('12,000')).not.toBeInTheDocument()
  })

  test('an empty bridge list is a valid document, not an error (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))))
    render(<Schedule7a />)

    expect(await screen.findByText(/no bridge reports have been added/i)).toBeInTheDocument()
    expect(screen.queryByText(/unable to load/i)).not.toBeInTheDocument()
  })

  test('Add/Close toggles the add panel and Add Report POSTs, showing the verbatim success (AC3)', async () => {
    let captured: BridgeRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, async ({ request }) => {
        captured = (await request.json()) as BridgeRequest
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    expect(screen.getByText('Add a Bridge report')).toBeInTheDocument()

    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toMatchObject({
      locationName: 'South Creek Bridge',
      builtDate: '2021-03',
      constructionTypeCode: 'N',
      loadRatingCode: 'L100',
      lifeSpan: 40,
      abutmentHeight: 3.5,
      distance: 8,
    })
    // Derived figures are server-owned and must never be sent.
    expect(captured).not.toHaveProperty('grandTotal')
    expect(captured).not.toHaveProperty('rowCounter')
  })

  test('a rejected add keeps entered values and shows the verbatim server detail (AC3)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, () =>
        problemBody(400, 'The date is not valid. Enter date in format: YYYY-MM.'),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    expect(
      await screen.findByText('The date is not valid. Enter date in format: YYYY-MM.'),
    ).toBeInTheDocument()
    expect(field('Name/Location of Bridge')).toHaveValue('South Creek Bridge')
  })

  test('advisory validation blocks a doomed add and never fires the request (AC8)', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await user.type(field('Name/Location of Bridge'), 'Incomplete Bridge')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    // Twelve fields are required and only the name was filled, so eleven must be flagged. Asserting
    // the exact count is what stops validateBridge from short-circuiting after the first failure.
    expect(await screen.findAllByText('Value Required')).toHaveLength(11)
    expect(posted).toBe(false)
  })

  test('an inline correction PUTs with the row revisionCount read from the document (AC4)', async () => {
    let captured: BridgeRequest | null = null
    let putUrl = ''
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${BRIDGES_URL}/7001`, async ({ request }) => {
        captured = (await request.json()) as BridgeRequest
        putUrl = request.url
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Site Plan / Gen. Arr. ($)'))
    await user.type(field('Site Plan / Gen. Arr. ($)'), '2000')
    await user.click(bridgePanel(7001).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toMatchObject({ sitePlanCost: 2000, revisionCount: 3 })
    // The write is scoped to the working context, not to whatever the document body echoed.
    expect(putUrl).toContain(`millId=${String(DEFAULT_MILL_ID)}`)
    expect(putUrl).toContain(`year=${String(DEFAULT_YEAR)}`)
  })

  test('editing one field on an untouched row preserves the other served values (AC4)', async () => {
    let captured: BridgeRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${BRIDGES_URL}/7001`, async ({ request }) => {
        captured = (await request.json()) as BridgeRequest
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Width (m)'))
    await user.type(field('Width (m)'), '6.5')
    await user.click(bridgePanel(7001).getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(captured).not.toBeNull()
    })
    // The edited field changes; every other field still carries what the document served.
    expect(captured).toMatchObject({
      width: 6.5,
      locationName: 'North Fork Bridge',
      builtDate: '2020-06',
      superstructureTypeCode: 'STL',
      lifeSpan: 50,
      length: 20,
      distance: 12,
      abutmentMaterialCost: 3000,
      otherCost: 100,
      comments: 'Spans the north fork',
    })
  })

  test('Cancel reverts an edited row to the served values (AC4)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Name/Location of Bridge'))
    await user.type(field('Name/Location of Bridge'), 'Renamed')
    expect(field('Name/Location of Bridge')).toHaveValue('Renamed')

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Cancel' }))
    expect(field('Name/Location of Bridge')).toHaveValue('North Fork Bridge')
  })

  test('a stale PUT surfaces the verbatim 409 conflict message (AC4)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${BRIDGES_URL}/7001`, () =>
        problemBody(409, 'This schedule was changed by another user. Please reload and try again.'),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByText(
        'This schedule was changed by another user. Please reload and try again.',
      ),
    ).toBeInTheDocument()
  })

  test('delete confirms then reports the remaining-bridges message (AC5)', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.delete(`${BRIDGES_URL}/7001`, () => {
        deleted = true
        return HttpResponse.json(
          doc({
            bridges: [bridgeAt(7002, 1)],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    expect(
      await screen.findByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()

    await user.click((await deleteModal()).getByRole('button', { name: 'Delete' }))
    await waitFor(() => {
      expect(deleted).toBe(true)
    })
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('deleting the last bridge reports the empty-schedule message (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete(`${BRIDGES_URL}/7001`, () =>
        HttpResponse.json(
          doc({
            bridges: [],
            message: {
              key: 'anyDataToSaveInfoMsg',
              text: 'Any data was saved. The Schedule is empty.',
            },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Delete' }))
    await user.click((await deleteModal()).getByRole('button', { name: 'Delete' }))

    expect(
      await screen.findByText('Any data was saved. The Schedule is empty.'),
    ).toBeInTheDocument()
  })

  test('Check Status renders per-bridge failures and no schedule banner on mixed results (AC6)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            {
              key: 'missingRequiredFieldMsg',
              text: 'Bridge Report Id : 2 - Site Plan / Gen. Arr.  Cost Value Required',
            },
          ],
          bridgeMessages: [
            { key: 'bridgeRequirementsMetMsg', text: 'All requirements for 1 have been met.' },
          ],
          requirementsMetMessage: null,
        }),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click((await screen.findAllByRole('button', { name: 'Check Status' }))[0])

    // The legacy label carries a double space before "Cost" — it must survive verbatim.
    expect(
      await screen.findByText('Bridge Report Id : 2 - Site Plan / Gen. Arr.  Cost Value Required', {
        normalizer: verbatim,
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('All requirements for 1 have been met.')).toBeInTheDocument()
    expect(
      screen.queryByText('All requirements for this schedule have been met'),
    ).not.toBeInTheDocument()
  })

  test('Check Status shows the schedule-wide banner only when every bridge passes (AC6)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: true,
          errors: [],
          bridgeMessages: [
            { key: 'bridgeRequirementsMetMsg', text: 'All requirements for 1 have been met.' },
          ],
          requirementsMetMessage: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        }),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click((await screen.findAllByRole('button', { name: 'Check Status' }))[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
  })

  test('Check Status is offered both above and below the list (AC6)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    expect(await screen.findAllByRole('button', { name: 'Check Status' })).toHaveLength(2)
  })

  test('no GET fires and ERR-001 renders when mill/year is unset (AC7)', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule7a />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
  })

  test.each([
    [
      409,
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
    ],
    [404, 'Schedule not found.'],
  ])(
    'a %i load guard renders its verbatim detail and suppresses the work area (AC7)',
    async (status, detail) => {
      server.use(http.get(URL, () => problemBody(status, detail)))
      render(<Schedule7a />)

      expect(await screen.findByText(detail)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Check Status' })).not.toBeInTheDocument()
    },
  )

  test('read-only (editable:false) disables every write control AND Check Status (AC7)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Bridges still display, and every control stays PRESENT but disabled — legacy bound `disabled`
    // and never removed a control, so asserting absence here would hide a regression that re-enabled
    // the whole write surface.
    expect(field('Name/Location of Bridge')).toHaveValue('North Fork Bridge')
    expect(field('Name/Location of Bridge')).toBeDisabled()
    expect(field('Site Plan / Gen. Arr. ($)')).toBeDisabled()
    expect(screen.getByRole('combobox', { name: /New\/Used/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    for (const name of ['Save', 'Cancel', 'Delete'] as const) {
      expect(bridgePanel(7001).getByRole('button', { name })).toBeDisabled()
    }
    // Legacy disabled Check Status outside Draft too, even though the endpoint permits it.
    for (const button of screen.getAllByRole('button', { name: 'Check Status' })) {
      expect(button).toBeDisabled()
    }
  })

  test('a read-only page fires no write when a disabled control is clicked (AC7)', async () => {
    let wrote = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))),
      http.put(`${BRIDGES_URL}/7001`, () => {
        wrote = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Save' }))
    expect(wrote).toBe(false)
  })

  test('the list paginates five bridges per page and page 2 is reachable (AC1)', async () => {
    const bridges = Array.from({ length: 7 }, (_, index) => bridgeAt(7001 + index, index + 1))
    server.use(http.get(URL, () => HttpResponse.json(doc({ bridges }))))
    const user = userEvent.setup()
    render(<Schedule7a />)

    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bridge report Id: 5' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bridge report Id: 6' })).not.toBeInTheDocument()

    // Without this the whole Pagination control could be removed and the suite would not notice.
    await user.click(screen.getByRole('button', { name: /next page/i }))
    expect(await screen.findByRole('button', { name: 'Bridge report Id: 6' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bridge report Id: 7' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bridge report Id: 1' })).not.toBeInTheDocument()
  })

  test('five or fewer bridges render no pagination control (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule7a />)
    await screen.findByRole('button', { name: 'Bridge report Id: 1' })
    expect(screen.queryByRole('button', { name: /next page/i })).not.toBeInTheDocument()
  })

  test('a bridge stored with null attributes renders blanks and saves without throwing (AC2, AC4)', async () => {
    // Legacy rows predate the required-field validation, so Check Status exists to flag them. Jackson
    // omits nulls, so these keys arrive ABSENT — the page must not assume a string.
    const bare = {
      bridgeReportId: 7009,
      rowCounter: 1,
      revisionCount: 0,
    } as unknown as Bridge
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [bare] }))),
      http.put(`${BRIDGES_URL}/7009`, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    expect(field('Name/Location of Bridge')).toHaveValue('')
    expect(field('Distance (km)')).toHaveValue('')

    // Advisory validation must reject rather than crash on the absent values.
    await user.click(bridgePanel(7009).getByRole('button', { name: 'Save' }))
    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)
    expect(posted).toBe(false)
  })

  test('saving one row preserves unsaved edits on the other open rows (AC4)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [northFork, bridgeAt(7002, 2)] }))),
      http.put(`${BRIDGES_URL}/7002`, () =>
        HttpResponse.json(
          doc({
            bridges: [northFork, bridgeAt(7002, 2)],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)
    await openBridge(user, 2)

    const rowOneName = document.getElementById('bridge-7001-locationName') as HTMLInputElement
    await user.clear(rowOneName)
    await user.type(rowOneName, 'UNSAVED WORK')

    await user.click(bridgePanel(7002).getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    // Row 1 was never sent, so its typing must survive the echo that refreshed row 2.
    expect(document.getElementById('bridge-7001-locationName')).toHaveValue('UNSAVED WORK')
  })

  test('a dropdown shows the served code description, not a blank placeholder (AC2)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    // Reading selectedItem back is what a hardcoded `selectedItem={null}` would break.
    expect(screen.getByRole('combobox', { name: /New\/Used/i })).toHaveTextContent('New')
    expect(screen.getByRole('combobox', { name: /Superstructure Type/i })).toHaveTextContent(
      'Steel',
    )
    expect(screen.getByRole('combobox', { name: /Decking Type/i })).toHaveTextContent('Wood')
    expect(screen.getByRole('combobox', { name: /Abutments Type/i })).toHaveTextContent('Concrete')
    expect(screen.getByRole('combobox', { name: /Load Rating/i })).toHaveTextContent('L-100')
  })

  test('a fractional cost is rounded and a blank cost is sent as null, not zero (AC3)', async () => {
    let captured: BridgeRequest | null = null
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))),
      http.post(BRIDGES_URL, async ({ request }) => {
        captured = (await request.json()) as BridgeRequest
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    // Half-away-from-zero, matching Oracle — the Integer wire would otherwise truncate to 1.
    await user.type(field('Other Costs ($)'), '1.5')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))

    await waitFor(() => {
      expect(captured).not.toBeNull()
    })
    expect(captured).toMatchObject({ otherCost: 2 })
    // "Not entered" must stay distinguishable from zero.
    expect((captured as unknown as BridgeRequest).sitePlanCost).toBeNull()
    expect((captured as unknown as BridgeRequest).approachCost).toBeNull()
    expect((captured as unknown as BridgeRequest).comments).toBeNull()
  })

  test('an invalid inline edit is blocked before the PUT (AC4, AC8)', async () => {
    let put = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(`${BRIDGES_URL}/7001`, () => {
        put = true
        return HttpResponse.json(doc())
      }),
    )
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Length (m)'))
    await user.type(field('Length (m)'), '99999')
    await user.click(bridgePanel(7001).getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByText('Entered bridge length must be between 0.0 and 9,999.9'),
    ).toBeInTheDocument()
    expect(put).toBe(false)
  })

  test('an inline error clears as soon as the user corrects that field (AC8)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    render(<Schedule7a />)
    await openBridge(user, 1)

    await user.clear(field('Length (m)'))
    await user.click(bridgePanel(7001).getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Value Required')).toBeInTheDocument()

    await user.type(field('Length (m)'), '15.0')
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('closing and reopening the add panel discards the draft and its errors (AC3)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ bridges: [] }))))
    const user = userEvent.setup()
    render(<Schedule7a />)

    await user.click(await screen.findByRole('button', { name: 'Add' }))
    await user.type(field('Name/Location of Bridge'), 'Half typed')
    await user.click(screen.getByRole('button', { name: 'Add Report' }))
    expect(await screen.findAllByText('Value Required')).not.toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Close' }))
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(field('Name/Location of Bridge')).toHaveValue('')
    expect(screen.queryByText('Value Required')).not.toBeInTheDocument()
  })

  test('a write echo dispatched under a superseded mill/year is discarded (AC4)', async () => {
    server.use(
      // The module-level `URL` const shadows the global, so match on the raw string.
      http.get(URL, ({ request }) =>
        HttpResponse.json(
          request.url.includes(`millId=${String(DEFAULT_MILL_ID)}`)
            ? doc()
            : doc({ bridges: [bridgeAt(8001, 1, { locationName: 'Other Mill Bridge' })] }),
        ),
      ),
      http.put(`${BRIDGES_URL}/7001`, async () => {
        // Land after the context has already moved on.
        await delay(60)
        return HttpResponse.json(
          doc({
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<ContextSwitchHarness />)
    await openBridge(user, 1)

    await user.click(bridgePanel(7001).getByRole('button', { name: 'Save' }))
    await user.click(screen.getByRole('button', { name: 'switch context' }))

    // The stale echo must not repaint the new context's document or its banner.
    expect(await screen.findByRole('button', { name: 'Bridge report Id: 1' })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    })
  })
})
