import type { ReactNode } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getDefaultNormalizer, render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like the sibling suites.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule9 from '@/components/schedule9'
import MillYearProvider from '@/context/millYear/MillYearProvider'
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
    <MillYearProvider>
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
