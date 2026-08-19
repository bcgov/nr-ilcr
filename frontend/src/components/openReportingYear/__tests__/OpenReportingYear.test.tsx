import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import OpenReportingYear from '@/components/openReportingYear'
import type { ReportingYearAdminView } from '@/interfaces/ReportingYearAdmin'

const ENDPOINT = 'http://localhost:3000/api/v1/admin/reporting-years'
const CONFIRM_PROMPT = 'Please confirm you would like to create a new reporting year?'

const RECURRING: ReportingYearAdminView = {
  openYears: [2024, 2023],
  nextYear: 2025,
  firstTime: false,
  selectableStartYears: [],
}

const FIRST_TIME: ReportingYearAdminView = {
  openYears: [],
  nextYear: null,
  firstTime: true,
  selectableStartYears: [2024, 2025, 2026, 2027],
}

describe('Open Reporting Year (Story 24.1)', () => {
  test('recurring: shows the next year and opens it after confirmation', async () => {
    const post = vi.fn()
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(RECURRING)),
      http.post(ENDPOINT, async ({ request }) => {
        post(await request.json())
        return HttpResponse.json({
          year: 2025,
          millsInitialized: 3,
          messageKey: 'successNewReportingYearMsg',
          message: 'The new reporting year 2025 has been successfully created.',
        })
      }),
    )
    render(<OpenReportingYear />)

    expect(await screen.findByText('2025')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Open Reporting Year' }))
    expect(await screen.findByText(CONFIRM_PROMPT)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Yes' }))

    expect(
      await screen.findByText('The new reporting year 2025 has been successfully created.'),
    ).toBeInTheDocument()
    expect(post).toHaveBeenCalledTimes(1)
  })

  test('declining the confirmation makes no server call (S05)', async () => {
    const post = vi.fn()
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(RECURRING)),
      http.post(ENDPOINT, async ({ request }) => {
        post(await request.json())
        return HttpResponse.json({ year: 2025, millsInitialized: 0, messageKey: '', message: '' })
      }),
    )
    render(<OpenReportingYear />)

    await userEvent.click(await screen.findByRole('button', { name: 'Open Reporting Year' }))
    expect(await screen.findByText(CONFIRM_PROMPT)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'No' }))

    expect(post).not.toHaveBeenCalled()
  })

  test('first-time: opening without a selection rejects FLD-001 and calls nothing (S04)', async () => {
    const post = vi.fn()
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(FIRST_TIME)),
      http.post(ENDPOINT, async ({ request }) => {
        post(await request.json())
        return HttpResponse.json({ year: 2026, millsInitialized: 0, messageKey: '', message: '' })
      }),
    )
    render(<OpenReportingYear />)

    await userEvent.click(await screen.findByRole('button', { name: 'Open Reporting Year' }))

    expect(
      await screen.findByText('Please select the reporting year to setup ILCR.'),
    ).toBeInTheDocument()
    // Carbon keeps a closed Modal's content mounted, so the guard is proven by the absence of any
    // POST rather than by the prompt text being absent from the DOM.
    expect(post).not.toHaveBeenCalled()
  })
})
