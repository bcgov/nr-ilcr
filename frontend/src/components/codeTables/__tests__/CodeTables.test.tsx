import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { fireEvent, render, screen } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import CodeTables from '@/components/codeTables'
import type { CodeTableEntry } from '@/interfaces/CodeTable'

const BASE = 'http://localhost:3000/api/v1/code-tables'
const UNIT_ENTRIES = `${BASE}/UNIT_CODE/entries`

const TABLES = [
  { key: 'UNIT_CODE', label: 'Unit Codes', codeMaxLength: 10, descriptionMaxLength: 120 },
  { key: 'SKID_TYPE_CODE', label: 'Skid Type Codes', codeMaxLength: 3, descriptionMaxLength: 120 },
]

const SEED: CodeTableEntry[] = [
  { code: 'M3', description: 'Cubic Metres', effectiveDate: '2000-01-01', expiryDate: null },
  { code: 'TON', description: 'Tonnes', effectiveDate: '2000-01-01', expiryDate: '2020-12-31' },
]

const listHandlers = (entries: CodeTableEntry[] = SEED) => [
  http.get(BASE, () => HttpResponse.json(TABLES)),
  http.get(UNIT_ENTRIES, () => HttpResponse.json(entries)),
]

const selectUnitCodes = async () => {
  await userEvent.click(await screen.findByRole('combobox', { name: 'Code List' }))
  await userEvent.click(await screen.findByRole('option', { name: 'Unit Codes' }))
  await screen.findByText('M3') // grid loaded
}

describe('Table Maintenance (Story 24.3)', () => {
  test('selecting a table loads its entries into the grid', async () => {
    server.use(...listHandlers())
    render(<CodeTables />)
    await selectUnitCodes()
    expect(screen.getByText('Cubic Metres')).toBeInTheDocument()
    expect(screen.getByText('Tonnes')).toBeInTheDocument()
    expect(screen.getByText('2020-12-31')).toBeInTheDocument() // TON expiry
  })

  test('adding a valid entry PUTs it and shows the verbatim success + reloaded grid', async () => {
    const put = vi.fn()
    server.use(
      ...listHandlers(),
      http.put(UNIT_ENTRIES, async ({ request }) => {
        put(await request.json())
        return HttpResponse.json({
          outcome: 'INSERTED',
          messageKey: 'dataSavedSuccesfullyInfoMsg',
          message: 'Data saved successfully',
          entries: [
            ...SEED,
            {
              code: 'BF',
              description: 'Board Feet',
              effectiveDate: '2020-01-01',
              expiryDate: '2030-12-31',
            },
          ],
        })
      }),
    )
    render(<CodeTables />)
    await selectUnitCodes()

    await userEvent.type(screen.getByLabelText('Code'), 'BF')
    await userEvent.type(screen.getByLabelText('Description'), 'Board Feet')
    fireEvent.change(screen.getByLabelText('Effective Date'), { target: { value: '2020-01-01' } })
    fireEvent.change(screen.getByLabelText('Expiry Date'), { target: { value: '2030-12-31' } })
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledWith({
      code: 'BF',
      description: 'Board Feet',
      effectiveDate: '2020-01-01',
      expiryDate: '2030-12-31',
    })
    expect(screen.getByText('Board Feet')).toBeInTheDocument() // grid reloaded from response
  })

  test('an entry with no expiry ("never expires") sends a null expiry', async () => {
    const put = vi.fn()
    server.use(
      ...listHandlers(),
      http.put(UNIT_ENTRIES, async ({ request }) => {
        put(await request.json())
        return HttpResponse.json({
          outcome: 'INSERTED',
          messageKey: 'dataSavedSuccesfullyInfoMsg',
          message: 'Data saved successfully',
          entries: SEED,
        })
      }),
    )
    render(<CodeTables />)
    await selectUnitCodes()

    await userEvent.type(screen.getByLabelText('Code'), 'NE')
    await userEvent.type(screen.getByLabelText('Description'), 'No Expiry')
    fireEvent.change(screen.getByLabelText('Effective Date'), { target: { value: '2020-01-01' } })
    // Leave Expiry Date empty — optional.
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledWith({
      code: 'NE',
      description: 'No Expiry',
      effectiveDate: '2020-01-01',
      expiryDate: null,
    })
  })

  test('a date range with expiry before effective is blocked client-side (no PUT)', async () => {
    const put = vi.fn()
    server.use(
      ...listHandlers(),
      http.put(UNIT_ENTRIES, () => {
        put()
        return HttpResponse.json({
          outcome: 'INSERTED',
          messageKey: '',
          message: 'ok',
          entries: SEED,
        })
      }),
    )
    render(<CodeTables />)
    await selectUnitCodes()

    await userEvent.type(screen.getByLabelText('Code'), 'BAD')
    await userEvent.type(screen.getByLabelText('Description'), 'Bad Dates')
    fireEvent.change(screen.getByLabelText('Effective Date'), { target: { value: '2030-01-01' } })
    fireEvent.change(screen.getByLabelText('Expiry Date'), { target: { value: '2020-01-01' } })
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(
      await screen.findByText('Expiry Date must be greater than or equal to Effective Date.'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('editing a row prefills it and PUTs the updated description', async () => {
    const put = vi.fn()
    server.use(
      ...listHandlers(),
      http.put(UNIT_ENTRIES, async ({ request }) => {
        put(await request.json())
        return HttpResponse.json({
          outcome: 'UPDATED',
          messageKey: 'dataSavedSuccesfullyInfoMsg',
          message: 'Data saved successfully',
          entries: SEED,
        })
      }),
    )
    render(<CodeTables />)
    await selectUnitCodes()

    // Edit TON (it has effective + expiry set; M3 never expires, so editing it would require an
    // expiry per FLD-004 — a separate case).
    await userEvent.click(screen.getAllByRole('button', { name: 'Edit' })[1]) // TON row
    const descField = screen.getByLabelText('Description (TON)')
    expect(descField).toHaveValue('Tonnes')
    await userEvent.clear(descField)
    await userEvent.type(descField, 'Metric Tonnes')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'TON', description: 'Metric Tonnes' }),
    )
  })

  test('cancelling an edit restores the row and issues no PUT', async () => {
    const put = vi.fn()
    server.use(
      ...listHandlers(),
      http.put(UNIT_ENTRIES, () => {
        put()
        return HttpResponse.json({
          outcome: 'UPDATED',
          messageKey: '',
          message: 'ok',
          entries: SEED,
        })
      }),
    )
    render(<CodeTables />)
    await selectUnitCodes()

    await userEvent.click(screen.getAllByRole('button', { name: 'Edit' })[1]) // TON
    const descField = screen.getByLabelText('Description (TON)')
    await userEvent.clear(descField)
    await userEvent.type(descField, 'Discarded')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    // Back to the read-only row with the original description; nothing saved.
    expect(screen.getByText('Tonnes')).toBeInTheDocument()
    expect(screen.queryByLabelText('Description (TON)')).not.toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('a save failure surfaces the server error and keeps nothing saved', async () => {
    server.use(
      ...listHandlers(),
      http.put(UNIT_ENTRIES, () =>
        HttpResponse.json({ detail: 'Code table not found.' }, { status: 404 }),
      ),
    )
    render(<CodeTables />)
    await selectUnitCodes()

    await userEvent.type(screen.getByLabelText('Code'), 'ZZ')
    await userEvent.type(screen.getByLabelText('Description'), 'Zed')
    fireEvent.change(screen.getByLabelText('Effective Date'), { target: { value: '2020-01-01' } })
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Code table not found.')).toBeInTheDocument()
  })

  test('a null expiry renders as an em dash in the grid', async () => {
    server.use(...listHandlers())
    render(<CodeTables />)
    await selectUnitCodes()
    // M3 never expires (expiryDate null) → em dash.
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })
})
