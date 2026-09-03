import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/test-setup'
import type * as DownloadUtil from '@/utils/download'
import { triggerDownload } from '@/utils/download'
import PrintSchedules from '../index'

const PRINT_URL = 'http://localhost:3000/api/v1/reports/print'

// Drive the mill/year context guard directly so the page under test does not depend on the provider or
// a mill-context fetch — and so `isCurrent` / `contextMissing` can be controlled per test.
const guard = vi.hoisted(() => ({
  millId: 514 as number | null,
  year: 2021 as number | null,
  contextMissing: false,
  isCurrent: () => true,
}))
vi.mock('@/hooks/useScheduleContextGuard', () => ({
  useScheduleContextGuard: () => guard,
}))

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
  guard.millId = 514
  guard.year = 2021
  guard.contextMissing = false
  guard.isCurrent = () => true
  vi.mocked(triggerDownload).mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('PrintSchedules', () => {
  it('renders schedules/options (deferred disabled, Schedule information default-checked), Generate disabled', () => {
    render(<PrintSchedules />)
    expect(screen.getByRole('checkbox', { name: 'Select all schedules' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Schedule 7A' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'Schedule 11' })).toBeEnabled()
    // S06 default: Schedule Information is pre-checked.
    expect(screen.getByRole('checkbox', { name: 'Schedule information' })).toBeChecked()
    // Schedule 1 through 4 are enabled and available.
    expect(screen.getByRole('checkbox', { name: 'Schedule 1' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'Schedule 2' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'Schedule 3' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'Schedule 4' })).toBeEnabled()
    expect(
      screen.getByRole('checkbox', { name: 'Mill information report (coming soon)' }),
    ).toBeDisabled()
    // No schedule selected yet → Generate is disabled; Clear is available.
    expect(screen.getByRole('button', { name: /Generate PDF/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Clear' })).toBeInTheDocument()
  })

  it('"Select all schedules" checks only the renderable schedules', async () => {
    render(<PrintSchedules />)
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all schedules' }))
    for (const label of [
      'Schedule 1',
      'Schedule 2',
      'Schedule 3',
      'Schedule 4',
      'Schedule 5',
      'Schedule 7B',
      'Schedule 8',
      'Schedule 9',
      'Schedule 11',
    ]) {
      expect(screen.getByRole('checkbox', { name: label })).toBeChecked()
    }
    expect(screen.getByRole('checkbox', { name: 'Schedule 4' })).toBeChecked()
  })

  it('posts the selection and downloads the PDF on success', async () => {
    let sentBody: Record<string, boolean> | null = null
    server.use(
      http.post(PRINT_URL, async ({ request }) => {
        sentBody = (await request.json()) as Record<string, boolean>
        // arrayBuffer, NOT new HttpResponse(new Blob(...)): this MSW build coerces a Blob body to
        // its string form, so the old fixture delivered the 13 bytes "[object Blob]" rather than a
        // PDF. The download assertions below were passing on that, which is what the "MSW/undici
        // blob defect" notes in this suite were actually seeing.
        return HttpResponse.arrayBuffer(new TextEncoder().encode('%PDF-1.4 mock\n%%EOF\n').buffer, {
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
    expect(sentBody).toMatchObject({
      schedule5: true,
      printComments: true,
      printScheduleInformation: true,
      allSchedules: false,
    })
    expect(vi.mocked(triggerDownload).mock.calls[0][1]).toBe('schedules_print.pdf')
  })

  it('passes through the verbatim server detail for a 400 rejection', async () => {
    server.use(
      http.post(PRINT_URL, () =>
        HttpResponse.json({ detail: 'Select at least one print option.' }, { status: 400 }),
      ),
    )
    render(<PrintSchedules />)

    // Schedule Information is on by default, so ticking a schedule is enough to enable Generate.
    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule 5' }))
    await userEvent.click(screen.getByRole('button', { name: /Generate PDF/ }))

    expect(await screen.findByText('Select at least one print option.')).toBeInTheDocument()
    expect(vi.mocked(triggerDownload)).not.toHaveBeenCalled()
  })

  it('shows a friendly message (not "Schedule not found") when the selection has no data (404)', async () => {
    server.use(
      http.post(PRINT_URL, () =>
        HttpResponse.json({ detail: 'Schedule not found.' }, { status: 404 }),
      ),
    )
    render(<PrintSchedules />)

    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule 5' }))
    await userEvent.click(screen.getByRole('button', { name: /Generate PDF/ }))

    expect(
      await screen.findByText('No data to print for the selected schedules.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Schedule not found.')).toBeNull()
    expect(vi.mocked(triggerDownload)).not.toHaveBeenCalled()
  })

  it('ignores a stale response when the mill/year context changed mid-render (no download)', async () => {
    let handled = false
    server.use(
      http.post(PRINT_URL, () => {
        handled = true
        return HttpResponse.arrayBuffer(new TextEncoder().encode('%PDF-1.4 mock\n%%EOF\n').buffer, {
          headers: { 'Content-Type': 'application/pdf' },
        })
      }),
    )
    // The dispatch-time guard reports the context is no longer current when the response comes back.
    guard.isCurrent = () => false
    render(<PrintSchedules />)

    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule 5' }))
    await userEvent.click(screen.getByRole('button', { name: /Generate PDF/ }))

    await waitFor(() => expect(handled).toBe(true))
    // The stale PDF must not download, and no "done" banner appears under the new context.
    expect(vi.mocked(triggerDownload)).not.toHaveBeenCalled()
    expect(screen.queryByText(/generated and downloaded/i)).toBeNull()
  })

  it('Clear resets to the default (schedules cleared, Schedule Information re-checked)', async () => {
    render(<PrintSchedules />)

    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule 5' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Comments' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Schedule information' })) // uncheck default
    expect(screen.getByRole('checkbox', { name: 'Schedule information' })).not.toBeChecked()

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }))

    expect(screen.getByRole('checkbox', { name: 'Schedule 5' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Comments' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Schedule information' })).toBeChecked()
  })

  it('gates on a missing mill/year context instead of showing the form', () => {
    guard.contextMissing = true
    render(<PrintSchedules />)
    expect(screen.getByText('Select a mill and reporting year')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Generate PDF/ })).toBeNull()
  })
})
