import type { ReactNode } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import {
  declaredRole,
  fireEvent,
  getDefaultNormalizer,
  render,
  renderAsAdmin,
  renderAsSubmitter,
  screen,
  waitFor,
  within,
} from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule9 from '@/components/schedule9'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'
import type { IlcrRole } from '@/context/auth/mockUsers'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import type ContractualWorkRecordRequest from '@/interfaces/Schedule9Request'
import type { ContractualWorkRecord } from '@/interfaces/Schedule9Response'

const URL = 'http://localhost:3000/api/v1/schedule9'
const RECORDS_URL = `${URL}/records`
const CHECK_URL = `${URL}/check-status`

const CODE_LISTS = {
  contractualItems: [
    { code: '108', description: 'Cattleguard' },
    { code: '111', description: 'Semi-permanent Road Deactivation' },
    { code: '114', description: 'Other' },
  ],
  unitTypes: [
    { code: 'M3', description: 'Cubic Metres' },
    { code: 'O', description: 'Other' },
  ],
  biogeoclimaticZones: [{ code: 'BZ1', description: 'BEC Zone One' }],
  sources: [
    { code: 'A', description: 'Actual Cost' },
    { code: 'S', description: 'Subcontract' },
  ],
}

const record108: ContractualWorkRecord = {
  id: 9101,
  revisionCount: 0,
  contractorId: 'CTR-001',
  contractualItem: { code: '108', description: 'Cattleguard' },
  itemDescription: null,
  unitType: { code: 'M3', description: 'Cubic Metres' },
  unitDescription: null,
  numberOfUnits: 12.5,
  biogeoclimaticZone: { code: 'BZ1', description: 'BEC Zone One' },
  cost: 5000,
  costPerUnit: 400,
  sideSlopePct: null,
  source: { code: 'A', description: 'Actual Cost' },
  sourceDescription: null,
  comments: 'Cattleguard install.',
}

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  records: [record108],
  codeLists: CODE_LISTS,
  ...overrides,
})

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

const verbatim = getDefaultNormalizer({ collapseWhitespace: false, trim: false })

const renderPage = () =>
  render(
    // Seeded explicitly: the provider no longer supplies a default context, and every scenario here
    // needs a working context for the page to render content rather than the mill/year guard.
    <MillYearProvider initial={{ millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }}>
      <Schedule9 />
    </MillYearProvider>,
  )

const settle = async () =>
  waitFor(() => {
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

const addPanel = () =>
  within(
    document.getElementById('add-contractorId')?.closest('.schedule-9__section') as HTMLElement,
  )

const recordPanel = (id: number) =>
  within(
    document
      .getElementById(`record-${String(id)}-contractorId`)
      ?.closest('.cds--accordion__item') as HTMLElement,
  )

const openRecord = async (user: ReturnType<typeof userEvent.setup>, id: number) =>
  user.click(
    await screen.findByRole('button', { name: `Contractual Work Report Id: ${String(id)}` }),
  )

async function fillAddForm(user: ReturnType<typeof userEvent.setup>) {
  const panel = addPanel()
  await user.type(panel.getByLabelText('Company ID'), 'CTR-NEW')
  await user.click(panel.getByRole('combobox', { name: /Contractual Item/i }))
  await user.click(await panel.findByRole('option', { name: 'Cattleguard' }))
  await user.click(panel.getByRole('combobox', { name: /Unit Type/i }))
  await user.click(await panel.findByRole('option', { name: 'Cubic Metres' }))
  await user.click(panel.getByRole('combobox', { name: /Biogeoclimatic Zone/i }))
  await user.click(await panel.findByRole('option', { name: 'BEC Zone One' }))
  await user.click(panel.getByRole('combobox', { name: /Source/i }))
  await user.click(await panel.findByRole('option', { name: 'Actual Cost' }))
}

beforeEach(() => {
  server.use(http.get(URL, () => HttpResponse.json(doc())))
})

describe('Schedule9 — render + add panel', () => {
  test('renders the record list and toggles the add panel', async () => {
    const user = userEvent.setup()
    renderPage()
    await settle()
    expect(
      await screen.findByRole('button', { name: 'Contractual Work Report Id: 9101' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(screen.getByText('Add a contractual work record')).toBeInTheDocument()
  })

  test('required selects are flagged on add (FLD-001)', async () => {
    const user = userEvent.setup()
    renderPage()
    await settle()
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await user.click(screen.getByRole('button', { name: 'Add Record' }))
    // Company ID, Contractual Item, Unit Type, Biogeoclimatic Zone, Source.
    expect(await screen.findAllByText('Value Required')).toHaveLength(5)
  })
})

describe('Schedule9 — conditional fields (BR-04)', () => {
  test('item 108 disables Item Other Description; item 114 enables it', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({
            records: [
              record108,
              {
                ...record108,
                id: 9114,
                contractualItem: { code: '114', description: 'Other' },
                itemDescription: 'Custom gate',
              },
            ],
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()

    await openRecord(user, 9101)
    expect(recordPanel(9101).getByLabelText('Item Other Description')).toBeDisabled()

    await openRecord(user, 9114)
    expect(recordPanel(9114).getByLabelText('Item Other Description')).toBeEnabled()
  })

  test('$/Unit renders the served derived value (read-only), null when units blank', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({
            records: [
              record108,
              { ...record108, id: 9102, numberOfUnits: null, costPerUnit: null },
            ],
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    // 5000 / 12.5 = 400.00.
    expect(recordPanel(9101).getByText('400.00')).toBeInTheDocument()
    await openRecord(user, 9102)
    // No units -> no $/Unit: the derived-value cell renders blank (never "0").
    const panel9102 = document
      .getElementById('record-9102-contractorId')
      ?.closest('.cds--accordion__item') as HTMLElement
    expect(panel9102.querySelector('.schedule-9__derived-value')?.textContent).toBe('')
  })
})

describe('Schedule9 — write flows', () => {
  test('add posts the record and shows the success banner (SUC-001)', async () => {
    let captured: ContractualWorkRecordRequest | null = null
    server.use(
      http.post(RECORDS_URL, async ({ request }) => {
        captured = (await request.json()) as ContractualWorkRecordRequest
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.click(screen.getByRole('button', { name: 'Add Record' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(captured).toMatchObject({
      contractorId: 'CTR-NEW',
      contractualItemCode: 108,
      sourceCode: 'A',
    })
  })

  test('per-record Save PUTs that record with its revisionCount', async () => {
    let putUrl = ''
    let body: ContractualWorkRecordRequest | null = null
    server.use(
      http.put(`${RECORDS_URL}/:id`, async ({ request, params }) => {
        putUrl = String(params.id)
        body = (await request.json()) as ContractualWorkRecordRequest
        return HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        )
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    await user.clear(recordPanel(9101).getByLabelText('Cost'))
    await user.type(recordPanel(9101).getByLabelText('Cost'), '7777')
    await user.click(recordPanel(9101).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(putUrl).toBe('9101')
    expect(body).toMatchObject({ cost: 7777, revisionCount: 0 })
  })

  test('delete confirms then DELETEs (DEL-001)', async () => {
    server.use(
      http.delete(`${RECORDS_URL}/:id`, () =>
        HttpResponse.json(
          doc({
            records: [],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    await user.click(recordPanel(9101).getByRole('button', { name: 'Delete' }))
    // The CFM-001 confirm.
    expect(
      screen.getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(
      within(await screen.findByRole('presentation')).getByRole('button', { name: 'Yes' }),
    )

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
  })

  test('entered values are retained when the add save fails (S12 retry)', async () => {
    server.use(http.post(RECORDS_URL, () => problemBody(400, 'Company ID: Value is required.')))
    const user = userEvent.setup()
    renderPage()
    await settle()
    await user.click(screen.getByRole('button', { name: 'Add' }))
    await fillAddForm(user)
    await user.type(addPanel().getByLabelText('Cost'), '5000')
    await user.click(screen.getByRole('button', { name: 'Add Record' }))

    // The verbatim server message renders, and the entered cost survives for correction.
    expect(await screen.findByText('Company ID: Value is required.')).toBeInTheDocument()
    expect(addPanel().getByLabelText('Cost')).toHaveValue('5,000')
  })
})

describe('Schedule9 — check status + guards', () => {
  test('check status renders the outstanding lines verbatim (S09)', async () => {
    server.use(
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: false,
          errors: [
            {
              key: 'missingRequiredFieldMsg',
              text: 'Contractual Work Report Id : 1 Cost$: Value Required',
            },
          ],
          requirementsMetMessage: null,
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await user.click(screen.getAllByRole('button', { name: 'Check Status' })[0])
    expect(
      await screen.findByText('Contractual Work Report Id : 1 Cost$: Value Required', {
        normalizer: verbatim,
      }),
    ).toBeInTheDocument()
  })

  test('editable:false disables the entry controls (S30)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ editable: false, trackStatus: 'S' }))))
    renderPage()
    await settle()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: 'Check Status' })[0]).toBeDisabled()
  })

  test('a context guard renders the API verbatim detail (EF3)', async () => {
    server.use(http.get(URL, () => problemBody(404, 'Schedule not found.')))
    renderPage()
    await settle()
    expect(await screen.findByText('Schedule not found.')).toBeInTheDocument()
  })
})

describe('Schedule9 — code-review coverage additions', () => {
  test('changing the item off "Other" clears Item Other Description (BR-04 clear-on-change)', async () => {
    const user = userEvent.setup()
    renderPage()
    await settle()
    await user.click(screen.getByRole('button', { name: 'Add' }))
    const panel = addPanel()
    await user.click(panel.getByRole('combobox', { name: /Contractual Item/i }))
    await user.click(await panel.findByRole('option', { name: 'Other' }))
    const itemDesc = panel.getByLabelText('Item Other Description')
    expect(itemDesc).toBeEnabled()
    await user.type(itemDesc, 'Custom gate')
    expect(itemDesc).toHaveValue('Custom gate')

    await user.click(panel.getByRole('combobox', { name: /Contractual Item/i }))
    await user.click(await panel.findByRole('option', { name: 'Cattleguard' }))
    expect(panel.getByLabelText('Item Other Description')).toHaveValue('')
    expect(panel.getByLabelText('Item Other Description')).toBeDisabled()
  })

  test('editing a row Cost updates the live $/Unit preview', async () => {
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    expect(recordPanel(9101).getByText('400.00')).toBeInTheDocument()
    await user.clear(recordPanel(9101).getByLabelText('Cost'))
    await user.type(recordPanel(9101).getByLabelText('Cost'), '10000')
    // 10000 / 12.5 = 800.00 (units unchanged); the row now previews live, not the served 400.00.
    expect(recordPanel(9101).getByText('800.00')).toBeInTheDocument()
    expect(recordPanel(9101).queryByText('400.00')).not.toBeInTheDocument()
  })

  test('a successful per-record Save applies the re-served row', async () => {
    server.use(
      http.put(`${RECORDS_URL}/:id`, () =>
        HttpResponse.json(
          doc({
            records: [{ ...record108, cost: 7777, costPerUnit: 622.16, revisionCount: 1 }],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    await user.clear(recordPanel(9101).getByLabelText('Cost'))
    await user.type(recordPanel(9101).getByLabelText('Cost'), '7777')
    await user.click(recordPanel(9101).getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // The row re-derives from the echo (rowForms dropped), so Cost shows the masked served value.
    expect(recordPanel(9101).getByLabelText('Cost')).toHaveValue('7,777')
  })

  test('entered values are retained when a per-record Save fails (S12 retry)', async () => {
    server.use(
      http.put(`${RECORDS_URL}/:id`, () =>
        problemBody(409, 'This schedule was changed by another user. Please reload and try again.'),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    await user.clear(recordPanel(9101).getByLabelText('Cost'))
    await user.type(recordPanel(9101).getByLabelText('Cost'), '7777')
    await user.click(recordPanel(9101).getByRole('button', { name: 'Save' }))
    expect(
      await screen.findByText(
        'This schedule was changed by another user. Please reload and try again.',
      ),
    ).toBeInTheDocument()
    // The edited value survives the failure for retry (masked to "7,777" on the blur that Save
    // triggered); it is NOT cleared or re-derived, so the reporter can correct and resubmit.
    expect(recordPanel(9101).getByLabelText('Cost')).toHaveValue('7,777')
  })

  test('cancelling the delete confirm sends no request and keeps the record', async () => {
    let deleteCalled = false
    server.use(
      http.delete(`${RECORDS_URL}/:id`, () => {
        deleteCalled = true
        return HttpResponse.json(doc({ records: [] }))
      }),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    await user.click(recordPanel(9101).getByRole('button', { name: 'Delete' }))
    await user.click(
      within(await screen.findByRole('presentation')).getByRole('button', { name: 'No' }),
    )
    expect(deleteCalled).toBe(false)
    expect(
      screen.getByRole('button', { name: 'Contractual Work Report Id: 9101' }),
    ).toBeInTheDocument()
  })

  test('check status all-met renders the SUC-002 banner', async () => {
    server.use(
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          requirementsMet: true,
          errors: [],
          requirementsMetMessage: {
            key: 'scheduleRequirementsMetMsg',
            text: 'All requirements for this schedule have been met',
          },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await settle()
    await user.click(screen.getAllByRole('button', { name: 'Check Status' })[0])
    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
  })

  test('editable:false disables the per-row Save/Delete and fields', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ editable: false, trackStatus: 'S' }))))
    const user = userEvent.setup()
    renderPage()
    await settle()
    await openRecord(user, 9101)
    expect(recordPanel(9101).getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(recordPanel(9101).getByRole('button', { name: 'Delete' })).toBeDisabled()
    expect(recordPanel(9101).getByLabelText('Company ID')).toBeDisabled()
  })
})

// Story 30.3 / #312 Overall 6. `renderIcon` puts an <svg> inside the button and leaves the accessible
// name as the label text, so a by-name lookup still finds the button AND proves the decorative icon is
// there — a later edit that drops an icon fails here. Added for the #381 review (paulushcgcj): this
// page's action bar and add-new trigger were still text-only after 30.3 reached the shared bars.
describe('Schedule 9 action icons (Story 30.3 / #312 Overall 6)', () => {
  test('Check Status, the Add toggle and Add Record carry their icon', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    const user = userEvent.setup()
    renderPage()
    await settle()

    // Check Status is Schedule 9's only page-level action and renders above AND below the list.
    for (const button of screen.getAllByRole('button', { name: /check status/i })) {
      expect(button.querySelector('svg')).not.toBeNull()
    }

    // The add trigger is one control whose label toggles, so its icon has to toggle with it, and
    // Add Record exists only once the panel it opens is on screen.
    const toggle = screen.getByRole('button', { name: 'Add' })
    expect(toggle.querySelector('svg')).not.toBeNull()
    await user.click(toggle)
    expect(screen.getByRole('button', { name: 'Close' }).querySelector('svg')).not.toBeNull()
    expect(screen.getByRole('button', { name: 'Add Record' }).querySelector('svg')).not.toBeNull()
  })
})

// -------------------------------------------------------------------------------------------------
// Story 16.3 — the ministry-correction journey on Schedule 9.
//
// Two things make these arms load-bearing rather than decorative:
//
// 1. Every arm DECLARES its acting role (`renderAsAdmin` / `renderAsSubmitter`). The suite's own
//    `renderPage` goes through `render`, which leaves the identity unseeded — and `findMockUser(null)`
//    falls through to `MOCK_USERS[0]`, the admin. That silent fallback ran the e2e suite as the wrong
//    role for a month, which is the reason this story exists.
//
// 2. The MSW handlers COMPUTE `editable` from the 16.1 matrix over the request's own
//    `X-Mock-Groups` header, instead of hardcoding it. So the role declaration actually drives the
//    outcome: swap `renderAsAdmin` for `renderAsSubmitter` in the save arm and the server answers
//    read-only and the arm fails. With a hardcoded flag it would have passed either way, proving
//    only "a page given editable:true allows correction" — never "an admin at Submitted may correct".
//
// Editability stays server-authoritative throughout (AD-9): the page reads `data.editable` and never
// the role or the track. Every user-facing string is asserted VERBATIM against
// `backend/src/main/resources/messages.properties` (AD-8).
// -------------------------------------------------------------------------------------------------

describe('Schedule9 — Story 16.3: correcting a Submitted track', () => {
  // The PINNED 16.1 matrix (`ScheduleEditability`), per track status. Submitter edits at Draft only;
  // admin edits at Submitted and Verified and is DELIBERATELY read-only at Draft while the mill
  // still owns the data. Anything else — a missing row, an unknown role — is read-only.
  //
  // Reproduced here rather than imported because the real rule lives in Java: this is the wire
  // contract the frontend is entitled to assume, and stating it makes the falsification check
  // trivial (flip the admin entry to ['D'] and every admin-at-Submitted arm below must fail).
  const EDITABLE_STATUSES: Record<string, readonly string[]> = {
    ILCR_ADMIN: ['S', 'V'],
    ILCR_SUBMITTER: ['D'],
  }

  /**
   * The acting role as the request actually carried it. `api-service` mirrors the selected mock
   * user's roles onto `X-Mock-Groups` (api-service.ts:11-17), so this is the same signal the real
   * mock backend gates on — not something the test asserts about itself.
   *
   * The throw is a tripwire for a future api-service that stops sending the header; it is NOT the
   * identity guard. It cannot fire today, because `findMockUser(null)` falls back to `MOCK_USERS[0]`
   * and the header is always populated — which is precisely why an arm that lost its `renderAsAdmin`
   * would still be served as an admin. That is what `sentRole` below exists to catch.
   */
  const actingRole = (request: Request): string => {
    const header = request.headers.get('X-Mock-Groups')
    if (!header) {
      throw new Error('request carried no X-Mock-Groups header — the acting identity was not sent')
    }
    return header
  }

  /**
   * The identity the LAST request of the arm actually carried, recorded by every handler below and
   * asserted IN THE TEST BODY. Deliberately not an `expect` inside an MSW resolver: a throw there
   * surfaces as a request failure and gets misattributed to the component.
   *
   * This is HALF the identity claim. On its own it cannot catch a dropped `renderAsAdmin`, because
   * the fallback identity IS the admin — `render()` and `renderAsAdmin()` put the same header on the
   * wire, so the wire cannot tell them apart. `declaredRole()` supplies the other half: it reports
   * what THIS test seeded, and is null when nothing did. `expectActingAs` asserts both together.
   */
  let sentRole: string | null = null
  beforeEach(() => {
    sentRole = null
  })

  /**
   * A Schedule 9 document — Submitted unless overridden — whose `editable` is answered per the
   * matrix for whoever is asking. Used for the read AND for every write echo, so an echo cannot
   * quietly hand back a wider flag than the matrix allows.
   *
   * The header is a COMMA-JOINED role list (`roles.join(',')`), and the server unions the permitted
   * statuses across every role the caller holds (`ScheduleEditability.forCaller`). Unreachable today
   * — a mock user holds exactly one ILCR role — but keying on the raw header would encode the wrong
   * rule, and an unrecognised key collapsing to read-only is the one outcome that lets a read-only
   * arm pass for the wrong reason.
   */
  const matrixDoc = (request: Request, over: Record<string, unknown> = {}) => {
    const body = doc({ trackStatus: 'S', ...over })
    sentRole = actingRole(request)
    const permitted = new Set(sentRole.split(',').flatMap((role) => EDITABLE_STATUSES[role] ?? []))
    return { ...body, editable: permitted.has(String(body.trackStatus)) }
  }

  /** GET that answers `editable` per the matrix for whoever is asking. */
  const matrixGet = (over: Record<string, unknown> = {}) =>
    http.get(URL, ({ request }) => HttpResponse.json(matrixDoc(request, over)))

  // Rendered through the role-declaring helpers, otherwise identical to `renderPage`: the same
  // explicit working context, because these scenarios assert page content, not the mill/year guard.
  const schedule9Page = () => (
    <MillYearProvider initial={{ millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }}>
      <Schedule9 />
    </MillYearProvider>
  )
  const renderPageAsAdmin = () => renderAsAdmin(schedule9Page())
  const renderPageAsSubmitter = () => renderAsSubmitter(schedule9Page())

  const SAVED = 'Data saved successfully'
  const DELETED = 'Data deleted successfully'
  const CONFIRM_DELETE_MSG = 'This will delete the current record. Do you want to continue?'
  const REQUIREMENTS_MET = 'All requirements for this schedule have been met'

  // The exact top-level key set `buildBody` puts on the wire for a per-record Save. Asserted as a set
  // (not a subset) because that is what makes the "a correction cannot move the status" claim
  // checkable from the client side: `trackStatus` is rendered NOWHERE on any schedule page (the
  // tombstone shows the working-context mill status, not the document's track), so there is no DOM
  // proxy for it. What IS provable here is structural — the request carries no status field at all,
  // and no transition endpoint is even registered, so strict MSW would fail the test if one were
  // called. Epics 17-18 own transitions; none exists today.
  const SAVE_BODY_KEYS = [
    'biogeoclimaticZone',
    'comments',
    'contractorId',
    'contractualItemCode',
    'cost',
    'itemDescription',
    'numberOfUnits',
    'revisionCount',
    'sideSlopePct',
    'sourceCode',
    'sourceDescription',
    'unitCode',
    'unitDescription',
  ]

  // Cost is masked on blur and is one of ~13 inputs mounted PER ROW, so `user.type` here is
  // O(rows x chars) and has timed out CI. A single change event is equivalent for these arms.
  const setCost = (id: number, value: string) =>
    fireEvent.change(recordPanel(id).getByLabelText('Cost'), { target: { value } })

  /** The read-only shape for one record row, plus the page-level actions. */
  const expectReadOnly = (id: number) => {
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Check Status' })) {
      expect(button).toBeDisabled()
    }
    const panel = recordPanel(id)
    expect(panel.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(panel.getByRole('button', { name: 'Delete' })).toBeDisabled()
    expect(panel.getByLabelText('Company ID')).toBeDisabled()
    expect(panel.getByLabelText('Cost')).toBeDisabled()
    expect(panel.getByLabelText('Comments')).toBeDisabled()
  }

  /**
   * Both halves of an arm's identity claim: what this test DECLARED (`renderAs*` seeded it) and what
   * the request CARRIED (`X-Mock-Groups`). Together they say "this arm acts as X" in a way that a
   * forgotten role declaration cannot satisfy — the declaration is observable even where the wire is
   * ambiguous.
   */
  const expectActingAs = (role: IlcrRole, header: string) => {
    expect(declaredRole()).toBe(role)
    expect(sentRole).toBe(header)
  }

  /** The correctable shape: the same controls, live. */
  const expectEditable = (id: number) => {
    expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()
    for (const button of screen.getAllByRole('button', { name: 'Check Status' })) {
      expect(button).toBeEnabled()
    }
    const panel = recordPanel(id)
    expect(panel.getByRole('button', { name: 'Save' })).toBeEnabled()
    expect(panel.getByRole('button', { name: 'Delete' })).toBeEnabled()
    expect(panel.getByLabelText('Company ID')).toBeEnabled()
    expect(panel.getByLabelText('Cost')).toBeEnabled()
    expect(panel.getByLabelText('Comments')).toBeEnabled()
  }

  test('admin at Submitted corrects a record and saves (CHK-010 S01)', async () => {
    let putId = ''
    let body: Record<string, unknown> | null = null
    server.use(
      matrixGet(),
      http.put(`${RECORDS_URL}/:id`, async ({ request, params }) => {
        putId = String(params.id)
        body = (await request.json()) as Record<string, unknown>
        // The echo is STILL Submitted, and still editable for this actor per the matrix — a
        // correction is not a transition. There is exactly one status writer in the whole backend
        // (the year-open INSERT, ReportingYearRepository:139) and no transition endpoint at all.
        return HttpResponse.json(
          matrixDoc(request, {
            records: [{ ...record108, cost: 7777, costPerUnit: 622.16, revisionCount: 1 }],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: SAVED },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    renderPageAsAdmin()
    await settle()
    await openRecord(user, 9101)

    // The controls are LIVE at Submitted for this actor — the capability Story 16.1 granted.
    expect(recordPanel(9101).getByLabelText('Cost')).toBeEnabled()
    expect(recordPanel(9101).getByRole('button', { name: 'Save' })).toBeEnabled()
    expect(recordPanel(9101).getByRole('button', { name: 'Delete' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled()

    setCost(9101, '7777')
    await user.click(recordPanel(9101).getByRole('button', { name: 'Save' }))

    // SUC-001, verbatim from the API — never composed client-side (AD-8).
    expect(await screen.findByText(SAVED, { normalizer: verbatim })).toBeInTheDocument()
    // The correction was issued AS the admin — not as whoever MOCK_USERS[0] happens to be. This is
    // the PUT's own header (its resolver recorded last), so the write, not just the read, was the
    // administrator's.
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expect(putId).toBe('9101')
    expect(body).toMatchObject({ cost: 7777, revisionCount: 0 })
    // No status field on the request: the client structurally cannot ask for a transition.
    expect(Object.keys(body ?? {}).sort()).toEqual(SAVE_BODY_KEYS)

    // Still editable after the 'S' echo — the page reads the server's flag, not the track (AD-9).
    expect(recordPanel(9101).getByLabelText('Cost')).toHaveValue('7,777')
    expect(recordPanel(9101).getByRole('button', { name: 'Save' })).toBeEnabled()
  })

  test('submitter at Submitted has every control disabled (S13/S17)', async () => {
    // Same handler as the admin arm above — only the declared identity differs, and the server
    // answers differently because of it.
    server.use(matrixGet())
    const user = userEvent.setup()
    renderPageAsSubmitter()
    await settle()
    await openRecord(user, 9101)
    // The read-only answer was served to a SUBMITTER: without this the arm would pass just as well
    // under the admin fallback, and the negative case would be asserting nothing about identity.
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
    expectReadOnly(9101)
  })

  test('admin at Draft is read-only — the capability 16.1 deliberately removed', async () => {
    // ADMIN edits at Submitted and Verified and is read-only at DRAFT: the matrix is bidirectional,
    // and without this arm a gate widened to "admin edits everywhere" would pass unnoticed.
    server.use(matrixGet({ trackStatus: 'D' }))
    const user = userEvent.setup()
    renderPageAsAdmin()
    await settle()
    await openRecord(user, 9101)
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expectReadOnly(9101)
  })

  test('submitter at Draft CAN still edit — the discriminator for the two arms above', async () => {
    // Without this, both read-only arms would also pass against a degenerate always-false handler
    // (or a matrix helper that silently resolved no role at all).
    server.use(matrixGet({ trackStatus: 'D' }))
    const user = userEvent.setup()
    renderPageAsSubmitter()
    await settle()
    await openRecord(user, 9101)
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
    expectEditable(9101)
  })

  // The rest of the admin row, and the cells 16.1 pinned as fail-closed. `trackStatus: 'V'` is served
  // ZERO times anywhere in the repo, so narrowing the backend matrix from ['S','V'] to ['S'] would
  // leave every one of the twelve suites green — half the admin capability with no evidence at all.
  test.each([
    ['V', true, 'the Verified half of the admin row'],
    ['O', false, 'an unknown status fails closed'],
    [null, false, 'an absent track fails closed'],
  ])('admin at trackStatus %s is editable=%s — %s', async (trackStatus, expectedEditable) => {
    server.use(matrixGet({ trackStatus }))
    const user = userEvent.setup()
    renderPageAsAdmin()
    await settle()
    await openRecord(user, 9101)
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    if (expectedEditable) {
      expectEditable(9101)
    } else {
      expectReadOnly(9101)
    }
  })

  test('admin at Submitted deletes behind the verbatim confirm (S06)', async () => {
    let deletedId = ''
    server.use(
      matrixGet(),
      http.delete(`${RECORDS_URL}/:id`, ({ request, params }) => {
        deletedId = String(params.id)
        return HttpResponse.json(
          matrixDoc(request, {
            records: [],
            message: { key: 'dataDeletedSuccesfullyInfoMsg', text: DELETED },
          }),
        )
      }),
    )
    const user = userEvent.setup()
    renderPageAsAdmin()
    await settle()
    await openRecord(user, 9101)
    await user.click(recordPanel(9101).getByRole('button', { name: 'Delete' }))

    // Asserted through the SHARED core/ConfirmDeleteModal as it stands (user ruling 2026-09-11):
    // header "Confirmation", answers Yes/No, `confirmDeleteMsg` verbatim.
    const modal = within(await screen.findByRole('presentation'))
    expect(screen.getByText('Confirmation')).toBeInTheDocument()
    expect(screen.getByText(CONFIRM_DELETE_MSG, { normalizer: verbatim })).toBeInTheDocument()
    await user.click(modal.getByRole('button', { name: 'Yes' }))

    expect(await screen.findByText(DELETED, { normalizer: verbatim })).toBeInTheDocument()
    // The DELETE's own header (its resolver recorded last): the removal was the administrator's.
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expect(deletedId).toBe('9101')
  })

  test('admin at Submitted cancels the confirm and NO delete is issued (S07)', async () => {
    let deleteCalled = false
    server.use(
      matrixGet(),
      http.delete(`${RECORDS_URL}/:id`, ({ request }) => {
        deleteCalled = true
        return HttpResponse.json(matrixDoc(request, { records: [] }))
      }),
    )
    const user = userEvent.setup()
    renderPageAsAdmin()
    await settle()
    await openRecord(user, 9101)
    await user.click(recordPanel(9101).getByRole('button', { name: 'Delete' }))
    await user.click(
      within(await screen.findByRole('presentation')).getByRole('button', { name: 'No' }),
    )

    expect(deleteCalled).toBe(false)
    // Only the GET ran, and it ran as the admin — the document WAS correctable, so the cancel is
    // what stopped the delete, not a read-only gate.
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    // The record survives untouched and the row is still editable: cancel is a no-op, not a reload.
    expect(
      screen.getByRole('button', { name: 'Contractual Work Report Id: 9101' }),
    ).toBeInTheDocument()
    expect(recordPanel(9101).getByLabelText('Cost')).toHaveValue('5,000')
    expect(screen.queryByText(DELETED)).not.toBeInTheDocument()
  })

  test('Check Status runs at Submitted and mutates nothing (S02)', async () => {
    // Read-only is asserted as "one POST, and the document was never re-fetched or written": a
    // stable GET count is a real signal here because the write paths DO re-GET/echo, and no PUT or
    // DELETE handler is registered, so strict MSW (onUnhandledRequest: 'error') fails the test if
    // Check Status touched either.
    let getCount = 0
    let postCount = 0
    server.use(
      http.get(URL, ({ request }) => {
        getCount += 1
        return HttpResponse.json(matrixDoc(request))
      }),
      http.post(CHECK_URL, ({ request }) => {
        postCount += 1
        // Recorded the same way as every other handler: Check Status returns no document, so it
        // cannot go through `matrixDoc`.
        sentRole = actingRole(request)
        return HttpResponse.json({
          requirementsMet: true,
          errors: [],
          requirementsMetMessage: { key: 'scheduleRequirementsMetMsg', text: REQUIREMENTS_MET },
        })
      }),
    )
    const user = userEvent.setup()
    renderPageAsAdmin()
    await settle()
    await openRecord(user, 9101)

    // Enabled here BECAUSE the matrix makes this actor editable at 'S'. Check Status is read-only at
    // any status, but the button follows legacy in disabling alongside the write controls when the
    // caller cannot edit (index.tsx:296-298) — legacy did the same on 26 of 26 buttons across 15
    // pages. Parity, not a bug; the two read-only arms above assert the disabled side of it.
    const checkStatus = screen.getAllByRole('button', { name: 'Check Status' })
    expect(checkStatus[0]).toBeEnabled()
    await user.click(checkStatus[0])

    expect(await screen.findByText(REQUIREMENTS_MET, { normalizer: verbatim })).toBeInTheDocument()
    // The POST's own header (its resolver recorded last): Check Status ran as the administrator.
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expect(postCount).toBe(1)
    expect(getCount).toBe(1)
    // The document on screen is untouched and still editable.
    expect(recordPanel(9101).getByLabelText('Cost')).toHaveValue('5,000')
    expect(recordPanel(9101).getByRole('button', { name: 'Save' })).toBeEnabled()
  })
})
