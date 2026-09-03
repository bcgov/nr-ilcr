import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import MillInformationReport from '@/components/millInformationReport'
import { triggerDownload } from '@/utils/download'
import type * as DownloadUtil from '@/utils/download'

// Mock only the download side effect, which cannot run in jsdom. extractBlobDetail is left real,
// though the transport-error path below reaches its non-blob branch rather than its blob branch.
vi.mock('@/utils/download', async (importOriginal) => ({
  ...(await importOriginal<typeof DownloadUtil>()),
  triggerDownload: vi.fn(),
}))

const downloaded = vi.mocked(triggerDownload)

const YEARS_ENDPOINT = 'http://localhost:3000/api/v1/reporting-years'
const REPORT_ENDPOINT = 'http://localhost:3000/api/v1/reports/mill-information'
const YEAR_REQUIRED = 'Report Year: Value is required.'

// The API returns opened years newest-first.
const OPEN_YEARS = [{ reportYear: 2021 }, { reportYear: 2020 }, { reportYear: 2019 }]

const yearsRespond = (years: { reportYear: number }[]) =>
  server.use(http.get(YEARS_ENDPOINT, () => HttpResponse.json(years)))

const reportRespondsWithPdf = (capture?: (year: string | null) => void) =>
  server.use(
    http.get(REPORT_ENDPOINT, ({ request }) => {
      capture?.(new URL(request.url).searchParams.get('year'))
      return HttpResponse.arrayBuffer(new TextEncoder().encode('%PDF-1.4 mock\n%%EOF\n').buffer, {
        headers: { 'Content-Type': 'application/pdf' },
      })
    }),
  )

describe('Mill Information Report', () => {
  beforeEach(() => downloaded.mockClear())

  test('pre-selects the most recent opened year', async () => {
    yearsRespond(OPEN_YEARS)
    render(<MillInformationReport />)

    const select = await screen.findByLabelText('Report Year:')
    await waitFor(() => expect(select).toHaveValue('2021'))
    expect(screen.getByRole('option', { name: '2019' })).toBeInTheDocument()
  })

  test('generates the report for the defaulted year and downloads mills_print.pdf', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    reportRespondsWithPdf(requested)
    render(<MillInformationReport />)

    await waitFor(() => expect(screen.getByLabelText('Report Year:')).toHaveValue('2021'))
    await userEvent.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    // NOTE: this assertion fails LOCALLY and passes in CI. Under responseType:'blob' this MSW/undici
    // build cannot construct a Response from a body ("object.stream is not a function"), which also
    // reddens three PrintSchedules tests on untouched code. It is kept because it is correct and is
    // the only frontend check on the mills_print.pdf filename; do not delete it to make a local run
    // green. Not marked test.fails, which would invert the problem and break CI, where it passes.
    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls[0][1]).toBe('mills_print.pdf')
    expect(screen.queryByText(YEAR_REQUIRED)).not.toBeInTheDocument()
  })

  test('changing the year generates for the newly selected year', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    reportRespondsWithPdf(requested)
    render(<MillInformationReport />)

    const select = await screen.findByLabelText('Report Year:')
    await waitFor(() => expect(select).toHaveValue('2021'))
    await userEvent.selectOptions(select, '2019')
    await userEvent.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => expect(requested).toHaveBeenCalledWith('2019'))
  })

  test('no opened year: says nothing was opened, rather than blaming the user, and makes no request', async () => {
    const requested = vi.fn()
    yearsRespond([])
    reportRespondsWithPdf(requested)
    render(<MillInformationReport />)

    await waitFor(() => expect(screen.getByLabelText('Report Year:')).toBeDisabled())
    await userEvent.click(screen.getByRole('button', { name: 'Generate Report' }))

    // Not "Report Year: Value is required." — nothing was ever offered to select.
    expect(await screen.findByText('No reporting period has been opened.')).toBeInTheDocument()
    expect(screen.queryByText(YEAR_REQUIRED)).not.toBeInTheDocument()
    expect(requested).not.toHaveBeenCalled()
  })

  test('a generation failure shows the error and the held year survives for the retry', async () => {
    yearsRespond(OPEN_YEARS)
    // A transport-level failure rather than a problem+json body. Under responseType:'blob' this
    // MSW/undici build fails to construct a Response from a body, which is the same defect that
    // currently reddens three PrintSchedules tests on untouched code; the catch path exercised is
    // the same either way, and the verbatim wire text is pinned in MillInformationReportIT.
    server.use(http.get(REPORT_ENDPOINT, () => HttpResponse.error()))
    render(<MillInformationReport />)

    await waitFor(() => expect(screen.getByLabelText('Report Year:')).toHaveValue('2021'))
    await userEvent.click(screen.getByRole('button', { name: 'Generate Report' }))

    // An error banner appears and no file is produced. The VERBATIM undefinedError text is a wire
    // contract pinned where it is observable — MillInformationReportIT — not here.
    expect(await screen.findByText('Unable to generate the report.')).toBeInTheDocument()
    expect(downloaded).not.toHaveBeenCalled()
    // The selection is untouched, so pressing Generate again retries the year that was held.
    expect(screen.getByLabelText('Report Year:')).toHaveValue('2021')

    const retried = vi.fn()
    reportRespondsWithPdf(retried)
    await userEvent.click(screen.getByRole('button', { name: 'Generate Report' }))
    await waitFor(() => expect(retried).toHaveBeenCalledWith('2021'))
  })

  test('the year cannot be changed while a report is generating', async () => {
    yearsRespond(OPEN_YEARS)
    let release: (() => void) | undefined
    server.use(
      http.get(REPORT_ENDPOINT, async () => {
        await new Promise<void>((resolve) => {
          release = resolve
        })
        return HttpResponse.arrayBuffer(new TextEncoder().encode('%PDF-1.4 mock\n%%EOF\n').buffer, {
          headers: { 'Content-Type': 'application/pdf' },
        })
      }),
    )
    render(<MillInformationReport />)

    const select = await screen.findByLabelText('Report Year:')
    await waitFor(() => expect(select).toHaveValue('2021'))
    await userEvent.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => expect(select).toBeDisabled())
    release?.()
  })

  test('a failed year load surfaces an error', async () => {
    server.use(http.get(YEARS_ENDPOINT, () => HttpResponse.error()))
    render(<MillInformationReport />)

    expect(await screen.findByText('Unable to load the reporting years.')).toBeInTheDocument()
  })
})
