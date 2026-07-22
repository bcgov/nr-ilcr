import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider; mock the router like Schedule2.test.tsx.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule4 from '@/components/schedule4'
import type { Location } from '@/interfaces/Schedule4Response'

const URL = 'http://localhost:3000/api/v1/schedule4'
const LOCATIONS_URL = 'http://localhost:3000/api/v1/schedule4/locations'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule4/check-status'

const harbour: Location = {
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
const emptyLanding: Location = {
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

// Harbour Dump's id is 7001, so its sub-page rows POST/DELETE target .../locations/7001/rows.
const ROWS_7001 = 'http://localhost:3000/api/v1/schedule4/locations/7001/rows'

describe('Schedule4 sub-pages (Story 10.6)', () => {
  // Open Harbour Dump's Towing sub-page: Edit → panel → "Towing Total (1)" → NAV-002 → Continue.
  const openTowing = async () => {
    render(<Schedule4 />)
    await screen.findByText('Harbour Dump')
    await userEvent.click(screen.getAllByRole('button', { name: /^edit$/i })[0])
    await userEvent.click(screen.getByRole('button', { name: /Towing Total \(1\)/i }))
    // NAV-002 unsaved-changes confirm.
    expect(
      screen.getByText('Any unsaved data will be lost. Are you sure you would like to continue?'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^continue$/i }))
    await screen.findByText('Towing Total — Harbour Dump')
  }

  test('open a sub-page from a saved location (NAV-002) shows its rows', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    await openTowing()
    expect(screen.getByText('Deferred towing row')).toBeInTheDocument()
  })

  test('add a row PUTs the sub-resource and shows the API success message', async () => {
    const withRow = () => {
      const clone = doc()
      clone.locations = [
        {
          ...harbour,
          subPageRows: [
            harbour.subPageRows[0],
            {
              id: 9100,
              code: 43,
              description: 'Added Towing',
              distance: 12,
              volume: 5,
              cost: 300,
              cycle: null,
              perUnit: 60,
            },
          ],
        },
        emptyLanding,
      ]
      return clone
    }
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(ROWS_7001, () =>
        HttpResponse.json({
          ...withRow(),
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    await openTowing()

    await userEvent.type(screen.getByLabelText('Description'), 'Added Towing')
    await userEvent.type(screen.getByLabelText('Volume'), '5')
    await userEvent.click(screen.getByRole('button', { name: /add row/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(screen.getByText('Added Towing')).toBeInTheDocument()
  })

  test('blank description blocks Add with Value Required (no POST)', async () => {
    const post = vi.fn()
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(ROWS_7001, () => {
        post()
        return HttpResponse.json(doc())
      }),
    )
    await openTowing()

    await userEvent.click(screen.getByRole('button', { name: /add row/i }))

    expect(await screen.findByText('Value Required')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  test('delete a row (NAV-005) DELETEs the sub-resource', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.delete('http://localhost:3000/api/v1/schedule4/locations/7001/rows/7013', () => {
        deleted = true
        const clone = doc()
        clone.locations = [{ ...harbour, subPageRows: [] }, emptyLanding]
        return HttpResponse.json({
          ...clone,
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    await openTowing()

    // Two "Delete" buttons exist (the row's + the always-rendered NAV-005 modal primary). Click the
    // row's [0] to open the confirm, then the modal's primary [last] to submit.
    await userEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const deleteButtons = screen.getAllByRole('button', { name: /^delete$/i })
    await userEvent.click(deleteButtons[deleteButtons.length - 1])

    await waitFor(() => expect(deleted).toBe(true))
  })
})
