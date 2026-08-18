import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/test-setup'
import type * as DownloadUtil from '@/utils/download'
import { triggerDownload } from '@/utils/download'
import PrintSchedules from '../index'

const PRINT_URL = 'http://localhost:3000/api/v1/reports/print'

// Working mill/year comes from the mill/year context; drive it directly so the page under test does
// not depend on the provider or a mill-context fetch.
const ctx = vi.hoisted(() => ({ millId: 514 as number | null, year: 2021 as number | null }))
vi.mock('@/context/millYear/useMillYear', () => ({ default: () => ({ ...ctx }) }))

// The tombstone self-sources the working context (and would fetch /v1/mill-context); stub it — this
// suite is about the selection form + download, not the header.
vi.mock('@/components/core/ScheduleTombstone', () => ({
  default: ({ title }: { title: string }) => <h1>{title}</h1>,
}))

// Keep the real extractBlobDetail (exercised on the error path); spy only on the download side effect.
vi.mock('@/utils/download', async (importOriginal) => ({
  ...(await importOriginal<typeof DownloadUtil>()),
  triggerDownload: vi.fn(),
}))

beforeEach(() => {
  ctx.millId = 514
  ctx.year = 2021
  vi.mocked(triggerDownload).mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('PrintSchedules', () => {
  it('renders schedules/options with the deferred ones disabled ("coming soon"), Generate disabled', () => {
    render(<PrintSchedules />)
    expect(screen.getByRole('checkbox', { name: 'Select all schedules' })).toBeInTheDocument()
    // Renderable schedules + content options are enabled.
    expect(screen.getByRole('checkbox', { name: 'Schedule 7A' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'Schedule 11' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'Comments' })).toBeEnabled()
    // Deferred schedules + the Mill info report are shown but disabled with a coming-soon note.
    expect(screen.getByRole('checkbox', { name: 'Schedule 1 (coming soon)' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Schedule 8 (coming soon)' })).toBeDisabled()
    expect(
      screen.getByRole('checkbox', { name: 'Mill information report (coming soon)' }),
    ).toBeDisabled()
    // Nothing selected yet → Generate is disabled.
    expect(screen.getByRole('button', { name: /Generate PDF/ })).toBeDisabled()
  })

  it('"Select all schedules" checks only the renderable schedules', async () => {
    render(<PrintSchedules />)
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all schedules' }))
    for (const label of ['Schedule 5', 'Schedule 7B', 'Schedule 9', 'Schedule 11']) {
      expect(screen.getByRole('checkbox', { name: label })).toBeChecked()
    }
    // A deferred schedule stays disabled and unchecked.
    expect(screen.getByRole('checkbox', { name: 'Schedule 1 (coming soon)' })).not.toBeChecked()
  })

  it('posts the selection and downloads the PDF on success', async () => {
    let sentBody: Record<string, boolean> | null = null
    server.use(
      http.post(PRINT_URL, async ({ request }) => {
        sentBody = (await request.json()) as Record<string, boolean>
        return new HttpResponse(new Blob(['%PDF-1.4 mock']), {
          headers: { 'Content-Type': 'application/pdf' },
        })
      }),
    )
    render(<PrintSchedules />)

    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule 5' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Comments' }))
    await userEvent.click(screen.getByRole('button', { name: /Generate PDF/ }))

    await waitFor(() => expect(vi.mocked(triggerDownload)).toHaveBeenCalledTimes(1))
    expect(screen.getByText(/generated and downloaded/i)).toBeInTheDocument()
    expect(sentBody).toMatchObject({ schedule5: true, printComments: true, allSchedules: false })
    expect(vi.mocked(triggerDownload).mock.calls[0][1]).toBe('schedules_print.pdf')
  })

  it('shows the server error detail when the selection is rejected', async () => {
    server.use(
      http.post(PRINT_URL, () =>
        HttpResponse.json({ detail: 'Select at least one print option.' }, { status: 400 }),
      ),
    )
    render(<PrintSchedules />)

    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule 5' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule information' }))
    await userEvent.click(screen.getByRole('button', { name: /Generate PDF/ }))

    expect(await screen.findByText('Select at least one print option.')).toBeInTheDocument()
    expect(vi.mocked(triggerDownload)).not.toHaveBeenCalled()
  })

  it('gates on a missing mill/year context instead of showing the form', () => {
    ctx.millId = null
    ctx.year = null
    render(<PrintSchedules />)
    expect(screen.getByText('Select a mill and reporting year')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Generate PDF/ })).toBeNull()
  })
})
