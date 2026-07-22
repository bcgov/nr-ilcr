import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like Schedule2.test.tsx.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule4 from '@/components/schedule4'

const URL = 'http://localhost:3000/api/v1/schedule4'
const LOCATIONS_URL = 'http://localhost:3000/api/v1/schedule4/locations'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule4/check-status'

const harbour = {
  id: 7001,
  revisionCount: 0,
  name: 'Harbour Dump',
  categories: [
    { code: 40, kind: 'FIXED', volume: 2000, cost: 100000, distance: null, perUnit: 50.0 },
    { code: 47, kind: 'DISTANCE', volume: 500, cost: 25000, distance: 120.5, perUnit: 50.0 },
  ],
  subPageRows: [
    {
      id: 7013,
      code: 43,
      description: 'Deferred towing row',
      distance: 50,
      volume: 999,
      cost: 99999,
      cycle: null,
      perUnit: null,
    },
  ],
}
const emptyLanding = {
  id: 7002,
  revisionCount: 0,
  name: 'Empty Landing',
  categories: [],
  subPageRows: [],
}

const doc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  locations: [harbour, emptyLanding],
  ...overrides,
})

describe('Schedule4 page', () => {
  test('lists existing locations with sub-page counts; Add New Location enabled (editable)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule4 />)

    expect(await screen.findByText('Harbour Dump')).toBeInTheDocument()
    expect(screen.getByText('Empty Landing')).toBeInTheDocument()
    // Add New Location enabled in an editable (Draft) context.
    expect(screen.getByRole('button', { name: /add new location/i })).toBeEnabled()
    // Row actions include Edit (not View) when editable.
    expect(screen.getAllByRole('button', { name: /^edit$/i }).length).toBeGreaterThan(0)
  })

  test('Add New Location opens the category-grid panel with editable inputs', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule4 />)
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))

    expect(screen.getByText('New Location')).toBeInTheDocument()
    expect(screen.getByLabelText('Location Name')).toHaveValue('')
    // The fixed + distance category inputs are present.
    expect(screen.getByLabelText('Lakeside Dry Dump volume')).toBeInTheDocument()
    expect(screen.getByLabelText('Truck Barge/Ferry distance')).toBeInTheDocument()
  })

  test('save a new location PUTs and shows the API success message', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () =>
        HttpResponse.json(
          doc({ message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' } }),
        ),
      ),
    )
    render(<Schedule4 />)
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await userEvent.type(screen.getByLabelText('Location Name'), 'New Dump')
    await userEvent.type(screen.getByLabelText('Lakeside Dry Dump cost'), '5000')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
  })

  test('blank name blocks save with the verbatim ERR-001 (no PUT fired)', async () => {
    const put = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(LOCATIONS_URL, () => {
        put()
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule4 />)
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /add new location/i }))
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      await screen.findByText('Location Name can not be empty. Please enter a description.'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('Copy opens a prefilled panel with a cleared name and the WRN-001 nudge', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule4 />)
    await screen.findByText('Harbour Dump')

    // The first Copy button is Harbour Dump's row.
    await userEvent.click(screen.getAllByRole('button', { name: /^copy$/i })[0])

    expect(screen.getByText('Copy Location')).toBeInTheDocument()
    expect(screen.getByLabelText('Location Name')).toHaveValue('')
    expect(
      screen.getByText(
        'To complete copy of Location: Harbour Dump, provide a new Location Name and invoke save.',
      ),
    ).toBeInTheDocument()
    // Amounts cloned from the source.
    expect(screen.getByLabelText('Lakeside Dry Dump cost')).toHaveValue('100000')
  })

  test('Check Status renders the per-location results', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [],
          locations: [
            {
              id: 7001,
              name: 'Harbour Dump',
              met: false,
              messages: [],
              issues: [
                { code: 52, message: { key: 'missingRequiredFieldMsg', text: 'Value Required' } },
              ],
            },
            {
              id: 7002,
              name: 'Empty Landing',
              met: true,
              messages: [
                {
                  key: 'locationRequirementsMetMsg',
                  text: 'All requirements for Empty Landing have been met.',
                },
              ],
              issues: [],
            },
          ],
        }),
      ),
    )
    render(<Schedule4 />)
    await screen.findByText('Harbour Dump')

    await userEvent.click(screen.getByRole('button', { name: /check status/i }))

    expect(await screen.findByText('Value Required')).toBeInTheDocument()
    expect(
      screen.getByText('All requirements for Empty Landing have been met.'),
    ).toBeInTheDocument()
  })

  test('editable:false renders View actions and disables Add/Copy/Delete (STA-001)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ trackStatus: 'S', editable: false }))))
    render(<Schedule4 />)
    await screen.findByText('Harbour Dump')

    expect(screen.getByRole('button', { name: /add new location/i })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /^view$/i }).length).toBeGreaterThan(0)
    screen.getAllByRole('button', { name: /^copy$/i }).forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /^delete$/i }).forEach((b) => expect(b).toBeDisabled())
  })
})
