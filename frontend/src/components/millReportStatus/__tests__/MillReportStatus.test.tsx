import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import MillReportStatus from '@/components/millReportStatus'
import { triggerDownload } from '@/utils/download'
import type * as DownloadUtil from '@/utils/download'
import type MillReportStatusRow from '@/interfaces/MillReportStatusRow'

// Mock only the download side effect, which cannot run in jsdom (no object URLs, no anchor
// navigation). extractBlobDetail is left REAL, because parsing the problem+json out of an error Blob
// is behaviour these tests assert rather than something to stub away.
vi.mock('@/utils/download', async (importOriginal) => ({
  ...(await importOriginal<typeof DownloadUtil>()),
  triggerDownload: vi.fn(),
}))

const downloaded = vi.mocked(triggerDownload)

const YEARS_ENDPOINT = 'http://localhost:3000/api/v1/reporting-years'
const STATUS_ENDPOINT = 'http://localhost:3000/api/v1/reports/mill-status'
/** The per-mill drill-down: the mill id rides the PATH, the year the query string. */
const DRILL_DOWN = (millId: number) =>
  `http://localhost:3000/api/v1/reports/mill-information/${millId}`

const YEAR_LABEL = 'Report Year:'
const noMills = (year: string) => `No mill has a report status for ${year}.`
const PROMPT = 'Select a Report Year and choose Apply to list the mills.'

// The API returns opened years newest-first.
const OPEN_YEARS = [{ reportYear: 2021 }, { reportYear: 2020 }, { reportYear: 2019 }]

const row = (overrides: Partial<MillReportStatusRow>): MillReportStatusRow => ({
  millId: 730,
  millNumber: '7300',
  millName: 'MILL INFO FULL',
  region: 'Kootenay Selling Price Zone',
  active: true,
  openDate: 'O: 2021-01-05',
  draftDate: 'D: 2021-03-10',
  submitDate: 'S: 2021-05-20',
  verifyDate: 'V: 2021-07-01',
  silviDraftDate: 'D: 2021-04-12',
  silviSubmitDate: 'S: 2021-06-15',
  silviVerifyDate: 'V: 2021-08-20',
  ...overrides,
})

/** Server order is mill-id ascending, which is deliberately NOT mill-number ascending here. */
const ROWS_2021: MillReportStatusRow[] = [
  row({ millId: 514, millNumber: '9000', millName: 'AAA Milling', region: null, active: false }),
  row({ millId: 730 }),
  row({
    millId: 731,
    millNumber: '7310',
    millName: 'MILL INFO SPARSE',
    region: null,
    draftDate: 'D: ',
    submitDate: 'S: ',
    verifyDate: 'V: ',
    silviDraftDate: 'D: ',
    silviSubmitDate: 'S: ',
    silviVerifyDate: 'V: ',
  }),
  // The milestone and region fields are OMITTED here, not sent as null — that is the real wire
  // shape, because the backend sets Jackson `default-property-inclusion: non_null`. The explicit
  // nulls on the rows above cover the defensive case, so both shapes are exercised.
  {
    millId: 732,
    millNumber: '0100',
    millName: 'MILL INFO NO CLIENT',
    active: false,
  },
]

const ROWS_2019: MillReportStatusRow[] = [
  row({ millId: 900, millNumber: '9001', millName: 'NINETEEN MILLING' }),
]

const yearsRespond = (years: { reportYear: number }[]) =>
  server.use(http.get(YEARS_ENDPOINT, () => HttpResponse.json(years)))

/** Answers the status endpoint per year, recording each requested year. */
const statusResponds = (
  byYear: Record<string, MillReportStatusRow[]>,
  requested?: (year: string | null) => void,
) =>
  server.use(
    http.get(STATUS_ENDPOINT, ({ request }) => {
      const year = new URL(request.url).searchParams.get('year')
      requested?.(year)
      return HttpResponse.json(byYear[year ?? ''] ?? [])
    }),
  )

/**
 * Answers the drill-down endpoint for ONE mill with a PDF, recording the year it was asked for.
 *
 * Registered per mill rather than as a wildcard so a request for the wrong mill 404s loudly instead
 * of being answered by a handler that ignores which mill was asked for — the sort-order case below
 * depends on exactly that distinction.
 */
const drillDownResponds = (millId: number, capture?: (year: string | null) => void) =>
  server.use(
    http.get(DRILL_DOWN(millId), ({ request }) => {
      capture?.(new URL(request.url).searchParams.get('year'))
      return HttpResponse.arrayBuffer(new TextEncoder().encode('%PDF-1.4 mock\n%%EOF\n').buffer, {
        headers: { 'Content-Type': 'application/pdf' },
      })
    }),
  )

const applyButton = () => screen.getByRole('button', { name: 'Apply' })

// Resolved via the label element, not getByRole('columnheader', { name }): Carbon renders its
// screen-reader sort hint INSIDE the th, so a sortable header's accessible name is really
// "Click to sort rows by Mill Number header in ascending order Mill Number" — and it changes as the
// sort state changes. The label div is stable and present in both branches of TableHeader. (Same
// helper as the Schedule 11 locations table's tests.)
const header = (name: string) =>
  screen.getByText(name, { selector: '.cds--table-header-label' }).closest('th') as HTMLElement

const clickHeader = (name: string) => userEvent.click(within(header(name)).getByRole('button'))

/** Every row in the table body. */
const bodyRows = () => {
  const body = screen
    .getByRole('table', { name: 'Mill Status Report' })
    .querySelector('tbody') as HTMLElement
  // queryAll, not getAll: an empty tbody is a legitimate state now (an error banner suppresses the
  // empty message), and getAllByRole throws on no match.
  return within(body).queryAllByRole('row') as HTMLTableRowElement[]
}

/**
 * The Mill column of each DATA row, in display order. The empty-state row spans all six columns
 * with a single cell, so filtering on the cell count leaves only real mills — which is what makes
 * `toEqual([])` an assertion that the table holds no mills.
 */
const renderedMills = () =>
  bodyRows()
    .filter((row) => row.cells.length === 6)
    .map((row) => row.cells[1].textContent)

/**
 * The four VISIBLE milestone values of a track cell, in order. Scoped to the value span because each
 * line also carries a visually-hidden milestone name (Opened / Draft / Submitted / Verified), so the
 * cell's raw textContent interleaves label and value.
 */
const trackLines = (cell: HTMLTableCellElement) =>
  Array.from(cell.querySelectorAll('.mill-report-status__line-value')).map((n) => n.textContent)

/** The six cells of one data row, by mill name. */
const cellsFor = (millName: string) =>
  bodyRows().find((row) => row.cells.length === 6 && row.cells[1].textContent === millName)!.cells

describe('Mill Status Report', () => {
  beforeEach(() => downloaded.mockClear())

  test('loads at the no-selection year with Apply disabled, an empty table and the legend', async () => {
    yearsRespond(OPEN_YEARS)
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    // The no-selection item is the initial value and stays available — nothing is pre-selected here,
    // unlike the Mill Information Report.
    expect(select).toHaveValue('')
    expect(screen.getByRole('option', { name: 'Select Reporting Year' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('option', { name: '2019' })).toBeInTheDocument())

    expect(applyButton()).toBeDisabled()

    // The table holds NO mills; the prompt row stands where data will go.
    expect(renderedMills()).toEqual([])
    expect(screen.getByText(PROMPT)).toBeInTheDocument()

    // The O/D/S/V legend is visible from first paint; it is what decodes the raw prefixes below.
    for (const entry of ['O - Open', 'D - Draft', 'S - Submitted', 'V - Verified']) {
      expect(screen.getByText(entry)).toBeInTheDocument()
    }
  })

  test('applying a year requests it and renders the rows in the served order', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 }, requested)
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    expect(applyButton()).toBeEnabled()
    await userEvent.click(applyButton())

    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    await waitFor(() =>
      expect(renderedMills()).toEqual([
        'AAA Milling',
        'MILL INFO FULL',
        'MILL INFO SPARSE',
        'MILL INFO NO CLIENT',
      ]),
    )
    // Unscoped: only the year is ever sent — no mill parameter exists on this endpoint.
    expect(requested).toHaveBeenCalledTimes(1)
  })

  test('the six columns carry the legacy header text, and only the four scalars sort', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    const headers = await screen.findAllByRole('columnheader')
    expect(headers.map((h) => h.querySelector('.cds--table-header-label')?.textContent)).toEqual([
      'Mill Number',
      'Mill',
      'Region',
      'Active',
      // Verbatim from legacy, quirks included: a hyphen in "1-10" and a plural "Schedules 11".
      'Report Info for Current Year Schedules 1-10',
      'Report Info for Current Year Schedules 11',
    ])

    // Legacy puts sortBy on exactly the four scalar columns; a stacked four-line cell has no single
    // value to order by, so the two track headers carry no sort control and no aria-sort at all.
    for (const label of ['Mill Number', 'Mill', 'Region', 'Active']) {
      expect(within(header(label)).getByRole('button')).toBeInTheDocument()
      expect(header(label)).toHaveAttribute('aria-sort', 'none')
    }
    for (const label of [
      'Report Info for Current Year Schedules 1-10',
      'Report Info for Current Year Schedules 11',
    ]) {
      expect(within(header(label)).queryByRole('button')).not.toBeInTheDocument()
      expect(header(label)).not.toHaveAttribute('aria-sort')
    }
  })

  test('each track cell stacks four raw milestone strings, sharing one Opened value', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    const cells = cellsFor('MILL INFO FULL')
    // Prefixes are NOT stripped: the legend is what decodes the letter, so the value reaches the
    // screen exactly as the reporting view holds it.
    expect(trackLines(cells[4])).toEqual([
      'O: 2021-01-05',
      'D: 2021-03-10',
      'S: 2021-05-20',
      'V: 2021-07-01',
    ])
    // The Schedule 11 group repeats the SAME Opened value — there is no independent opened date —
    // then carries its own three milestones.
    expect(trackLines(cells[5])).toEqual([
      'O: 2021-01-05',
      'D: 2021-04-12',
      'S: 2021-06-15',
      'V: 2021-08-20',
    ])
  })

  test('a null milestone renders an empty line and a prefix-only one keeps its prefix', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // MILL INFO SPARSE: prefix-only on both tracks — the prefix survives.
    const sparse = cellsFor('MILL INFO SPARSE')
    expect(trackLines(sparse[4])).toEqual(['O: 2021-01-05', 'D: ', 'S: ', 'V: '])
    expect(trackLines(sparse[5])).toEqual(['O: 2021-01-05', 'D: ', 'S: ', 'V: '])

    // MILL INFO NO CLIENT: every milestone absent. Both cells render four EMPTY lines — still four,
    // so the row stays aligned with its neighbours — and, critically, NOT the text "null".
    const absent = cellsFor('MILL INFO NO CLIENT')
    expect(trackLines(absent[4])).toEqual(['', '', '', ''])
    expect(trackLines(absent[5])).toEqual(['', '', '', ''])
    expect(absent[4].querySelectorAll('.mill-report-status__line')).toHaveLength(4)
    expect(screen.queryByText('null')).not.toBeInTheDocument()
  })

  test('Active renders Yes/No and a missing Region renders a dash', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // AAA Milling: active false, no region.
    expect(cellsFor('AAA Milling')[2].textContent).toBe('-')
    expect(cellsFor('AAA Milling')[3].textContent).toBe('No')
    // MILL INFO NO CLIENT: the region field is absent from the body entirely, not null — same dash.
    expect(cellsFor('MILL INFO NO CLIENT')[2].textContent).toBe('-')
    // MILL INFO FULL: active true, region resolved.
    expect(cellsFor('MILL INFO FULL')[2].textContent).toBe('Kootenay Selling Price Zone')
    expect(cellsFor('MILL INFO FULL')[3].textContent).toBe('Yes')
  })

  test('the Mill cell is an activatable, labelled drill-down button — not a link', async () => {
    // 19.2 shipped this cell as plain text behind an explicit fence, which THIS test inverts: the
    // drill-down is now the point of the cell. A BUTTON and not an anchor, though legacy's was a
    // p:commandLink — activating it fetches a blob for the browser to save, so there is no URL to
    // follow, open in a tab or copy, and role=link would misdescribe it.
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    const millCell = cellsFor('MILL INFO FULL')[1]
    expect(within(millCell).queryByRole('link')).not.toBeInTheDocument()
    const drillDown = within(millCell).getByRole('button')
    // The visible label stays the mill name — the column reads as it did.
    expect(drillDown).toHaveTextContent('MILL INFO FULL')
    // ...but the accessible name says what activating it DOES, and which mill it does it for. There
    // is one such button per row, so a name of "MILL INFO FULL" alone would leave a screen-reader
    // user with a column of unexplained controls.
    expect(drillDown).toHaveAccessibleName(
      'Generate the mill information report for MILL INFO FULL',
    )
    // Every rendered row carries one, not just the first.
    for (const name of ['AAA Milling', 'MILL INFO SPARSE', 'MILL INFO NO CLIENT']) {
      expect(within(cellsFor(name)[1]).getByRole('button')).toBeInTheDocument()
    }
  })

  test('clicking a scalar header cycles NONE -> ASC -> DESC -> NONE, blanks last', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // ASC — numerically, not as text: 0100, 7300, 7310, 9000.
    await clickHeader('Mill Number')
    expect(header('Mill Number')).toHaveAttribute('aria-sort', 'ascending')
    expect(renderedMills()).toEqual([
      'MILL INFO NO CLIENT',
      'MILL INFO FULL',
      'MILL INFO SPARSE',
      'AAA Milling',
    ])
    // An inactive column still reports none, never the active column's direction.
    expect(header('Mill')).toHaveAttribute('aria-sort', 'none')

    await clickHeader('Mill Number')
    expect(header('Mill Number')).toHaveAttribute('aria-sort', 'descending')
    expect(renderedMills()).toEqual([
      'AAA Milling',
      'MILL INFO SPARSE',
      'MILL INFO FULL',
      'MILL INFO NO CLIENT',
    ])

    // Third click restores the server's mill-id order.
    await clickHeader('Mill Number')
    expect(header('Mill Number')).toHaveAttribute('aria-sort', 'none')
    expect(renderedMills()).toEqual([
      'AAA Milling',
      'MILL INFO FULL',
      'MILL INFO SPARSE',
      'MILL INFO NO CLIENT',
    ])
  })

  test('Region sorts on the rendered dash, and Active sorts on the rendered Yes/No', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // Only MILL INFO FULL has a region; the other three render "-". Legacy stored that literal dash
    // in the sorted field (MillReportStatusDAO.java:120) and ordered the string, so ascending puts
    // the dashes FIRST and descending puts them last. Sorting the raw null instead would park them
    // last in both directions, disagreeing with legacy and with the screen.
    await clickHeader('Region')
    expect(renderedMills().at(-1)).toBe('MILL INFO FULL')
    await clickHeader('Region')
    expect(renderedMills()[0]).toBe('MILL INFO FULL')

    // Active ascending sorts the rendered text, so "No" precedes "Yes" — legacy sorted its Yes/No
    // string, not the underlying code.
    await clickHeader('Active')
    expect(renderedMills().slice(0, 2)).toEqual(['AAA Milling', 'MILL INFO NO CLIENT'])
  })

  test('the two track headers are inert: clicking one sorts nothing and sets no sort state', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    const track = header('Report Info for Current Year Schedules 1-10')
    await userEvent.click(track)
    expect(renderedMills()).toEqual([
      'AAA Milling',
      'MILL INFO FULL',
      'MILL INFO SPARSE',
      'MILL INFO NO CLIENT',
    ])
    expect(track).not.toHaveAttribute('aria-sort')
  })

  test('re-applying the SAME year does not refetch', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 }, requested)
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(requested).toHaveBeenCalledTimes(1))

    // Legacy: "avoid loading the list in case of report year does not change"
    // (MillReportStatusMB.applyYearChanged).
    await userEvent.click(applyButton())
    await userEvent.click(applyButton())
    expect(requested).toHaveBeenCalledTimes(1)
    expect(renderedMills()).toHaveLength(4)
  })

  test('changing the year and applying replaces the table with that year’s rows', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021, '2019': ROWS_2019 }, requested)
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    await userEvent.selectOptions(select, '2019')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toEqual(['NINETEEN MILLING']))
    expect(requested).toHaveBeenNthCalledWith(2, '2019')
  })

  test('a sort does not survive into a newly applied year', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021, '2019': ROWS_2019 })
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))
    await clickHeader('Mill Number')
    expect(header('Mill Number')).toHaveAttribute('aria-sort', 'ascending')

    await userEvent.selectOptions(select, '2019')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toEqual(['NINETEEN MILLING']))

    // Legacy's Apply was a full page submit (ajax="false"), so the table came back unsorted. A
    // surviving sort would also leave the header claiming an order the new rows are not in.
    expect(header('Mill Number')).toHaveAttribute('aria-sort', 'none')
  })

  test('clearing the year disables Apply and empties the table', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    await userEvent.selectOptions(select, '')
    expect(applyButton()).toBeDisabled()
    expect(renderedMills()).toEqual([])
    expect(screen.getByText(PROMPT)).toBeInTheDocument()
  })

  test('clearing and re-selecting the year repeatedly, before Apply is ever pressed, never throws', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 }, requested)
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)

    // The recorded S09 fix: legacy called setMillReportStatusReports(...) on a DO that is null until
    // the first Apply (MillReportStatusMB:77), so this exact sequence was an NPE.
    for (const year of ['2021', '', '2020', '', '2019', '']) {
      await userEvent.selectOptions(select, year)
      if (year === '') {
        expect(applyButton()).toBeDisabled()
        expect(renderedMills()).toEqual([])
        expect(screen.getByText(PROMPT)).toBeInTheDocument()
      } else {
        expect(applyButton()).toBeEnabled()
      }
    }
    // Nothing was ever fetched — Apply was never pressed.
    expect(requested).not.toHaveBeenCalled()
  })

  test('a year with no mills renders an empty table and no error banner', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021, '2019': [] })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2019')
    await userEvent.click(applyButton())

    expect(await screen.findByText(noMills('2019'))).toBeInTheDocument()
    // An empty result is a correct render, not a failure.
    expect(screen.queryByText('Error')).not.toBeInTheDocument()
  })

  test('a rows failure shows the problem detail, keeps the table, and retries the held year', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // Now a different year fails with problem+json.
    server.use(
      http.get(STATUS_ENDPOINT, () =>
        HttpResponse.json(
          { detail: 'Report Year is not an open reporting period.' },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    await userEvent.selectOptions(select, '2020')
    await userEvent.click(applyButton())

    // The verbatim `detail` is shown...
    expect(
      await screen.findByText('Report Year is not an open reporting period.'),
    ).toBeInTheDocument()
    // ...the table is left intact...
    expect(renderedMills()).toHaveLength(4)
    // ...and the selection is retained so Apply retries the year that was held.
    expect(select).toHaveValue('2020')

    const requested = vi.fn()
    statusResponds({ '2020': ROWS_2019 }, requested)
    await userEvent.click(applyButton())
    await waitFor(() => expect(requested).toHaveBeenCalledWith('2020'))
  })

  test('a transport failure falls back to the page’s own message', async () => {
    yearsRespond(OPEN_YEARS)
    server.use(http.get(STATUS_ENDPOINT, () => HttpResponse.error()))
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())

    expect(await screen.findByText('Unable to load the mill status report.')).toBeInTheDocument()
  })

  test('a no-op Apply still clears a standing error banner (P2)', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // 2020 fails.
    server.use(
      http.get(STATUS_ENDPOINT, () =>
        HttpResponse.json(
          { detail: '2020 exploded.' },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    await userEvent.selectOptions(select, '2020')
    await userEvent.click(applyButton())
    expect(await screen.findByText('2020 exploded.')).toBeInTheDocument()

    // Back to 2021 and Apply. That is a NO-OP fetch (2021 is still the applied year), and the early
    // return used to skip the error reset — leaving the 2020 banner above correct 2021 rows with no
    // dismiss control anywhere on the page.
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())

    await waitFor(() => expect(screen.queryByText('2020 exploded.')).not.toBeInTheDocument())
    expect(renderedMills()).toHaveLength(4)
  })

  test('changing the year alone clears a standing error banner (P2)', async () => {
    yearsRespond(OPEN_YEARS)
    server.use(
      http.get(STATUS_ENDPOINT, () =>
        HttpResponse.json(
          { detail: 'It broke.' },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())
    expect(await screen.findByText('It broke.')).toBeInTheDocument()

    // Selecting a different year is enough — the banner described a year no longer selected.
    await userEvent.selectOptions(select, '2020')
    await waitFor(() => expect(screen.queryByText('It broke.')).not.toBeInTheDocument())
  })

  test('a failed Apply suppresses the no-mills claim, which was never established (P4)', async () => {
    yearsRespond(OPEN_YEARS)
    server.use(
      http.get(STATUS_ENDPOINT, () =>
        HttpResponse.json(
          { detail: 'Nope.' },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    expect(await screen.findByText('Nope.')).toBeInTheDocument()

    // Neither message may appear beside the error: "No mill has a report status" would assert the
    // opposite of what happened, and the prompt would invite an action just attempted.
    expect(screen.queryByText(/No mill has a report status/)).not.toBeInTheDocument()
    expect(screen.queryByText(PROMPT)).not.toBeInTheDocument()
    expect(renderedMills()).toEqual([])
  })

  test('the no-mills message names the year actually APPLIED, not the current selection (P4)', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021, '2019': [] })
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2019')
    await userEvent.click(applyButton())
    expect(await screen.findByText('No mill has a report status for 2019.')).toBeInTheDocument()

    // Move the selection WITHOUT applying: the message must keep naming 2019, whose emptiness is the
    // only fact established.
    await userEvent.selectOptions(select, '2021')
    expect(screen.getByText('No mill has a report status for 2019.')).toBeInTheDocument()
  })

  test('no opened reporting period: says so instead of instructing an impossible Apply (P3)', async () => {
    yearsRespond([])
    render(<MillReportStatus />)

    // Apply can never enable, so "Select a Report Year and choose Apply" would instruct an action the
    // page has not offered. Same wording the sibling Mill Information Report uses.
    expect(await screen.findByText('No reporting period has been opened.')).toBeInTheDocument()
    expect(screen.queryByText(PROMPT)).not.toBeInTheDocument()
    expect(applyButton()).toBeDisabled()
    expect(screen.getByLabelText(YEAR_LABEL)).toHaveValue('')
  })

  test('both controls are locked while a fetch is in flight, and only one request goes out (P6)', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    let release: (() => void) | undefined
    server.use(
      http.get(STATUS_ENDPOINT, async ({ request }) => {
        requested(new URL(request.url).searchParams.get('year'))
        await new Promise<void>((resolve) => {
          release = resolve
        })
        return HttpResponse.json(ROWS_2021)
      }),
    )
    render(<MillReportStatus />)

    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())

    // `busy` is the ONLY thing preventing a mid-flight year change — `apply` has no cancellation and
    // no last-write-wins guard, so removing it from either control would let a second request race
    // the first and settle in arrival order. Without this test both removals stay green.
    await waitFor(() => expect(select).toBeDisabled())
    expect(applyButton()).toBeDisabled()
    await userEvent.click(applyButton())
    expect(requested).toHaveBeenCalledTimes(1)

    release?.()
    await waitFor(() => expect(renderedMills()).toHaveLength(4))
    expect(select).toBeEnabled()
    expect(requested).toHaveBeenCalledTimes(1)
  })

  test('each stacked milestone is labelled for assistive tech, and the legend describes the table (P7)', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // Four named milestones per track cell, so a screen reader announces "Submitted 2021-05-20"
    // rather than a bare date — and announces all four NAMES even on a row that has reached none.
    const labelsOf = (cell: HTMLTableCellElement) =>
      Array.from(cell.querySelectorAll('.mill-report-status__line-label')).map((n) => n.textContent)
    const full = cellsFor('MILL INFO FULL')
    expect(labelsOf(full[4])).toEqual(['Opened', 'Draft', 'Submitted', 'Verified'])
    expect(labelsOf(full[5])).toEqual(['Opened', 'Draft', 'Submitted', 'Verified'])
    expect(labelsOf(cellsFor('MILL INFO NO CLIENT')[4])).toEqual([
      'Opened',
      'Draft',
      'Submitted',
      'Verified',
    ])

    // The O/D/S/V legend is what decodes the prefixes, so it must be programmatically attached to
    // the table rather than merely sitting above it.
    const table = screen.getByRole('table', { name: 'Mill Status Report' })
    const legendId = table.getAttribute('aria-describedby')
    expect(legendId).toBeTruthy()
    const legend = document.getElementById(legendId as string)
    expect(legend).not.toBeNull()
    expect(legend?.textContent).toContain('O - Open')
    expect(legend?.textContent).toContain('V - Verified')
  })

  test('a failed year load surfaces its own error and leaves Apply disabled', async () => {
    server.use(http.get(YEARS_ENDPOINT, () => HttpResponse.error()))
    render(<MillReportStatus />)

    expect(await screen.findByText('Unable to load the reporting years.')).toBeInTheDocument()
    expect(applyButton()).toBeDisabled()
  })

  /**
   * Applies 2021 and returns the rendered table, so each drill-down test starts from the state the
   * drill-down only exists in — rows on screen for an applied year.
   */
  const applied2021 = async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021, '2019': ROWS_2019 })
    render(<MillReportStatus />)
    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))
  }

  /**
   * The page's polite live region. Queried BY NAME because Carbon's InlineNotification also renders
   * role="status", so a bare getByRole('status') is ambiguous the moment a banner is up.
   */
  const statusRegion = () => screen.getByRole('status', { name: 'Report generation status' })

  /** The drill-down control in the Mill cell of the row naming `millName`. */
  const millButton = (millName: string) =>
    within(cellsFor(millName)[1]).getByRole('button') as HTMLButtonElement

  /**
   * The drill-down advertises unavailability with aria-disabled, NOT the real `disabled` attribute,
   * so it stays focusable and activating it never blurs a keyboard user to <body>. `toBeDisabled()`
   * would therefore be wrong here in both directions — it reports false while the row is fetching,
   * and it cannot distinguish "advertised as busy" from "unavailable".
   */
  const expectAdvertisedDisabled = (millName: string, expected: boolean) => {
    const button = millButton(millName)
    if (expected) {
      expect(button).toHaveAttribute('aria-disabled', 'true')
    } else {
      expect(button).not.toHaveAttribute('aria-disabled')
    }
    // Never the real attribute, whatever the state: that is what keeps focus where the user put it.
    expect(button).not.toBeDisabled()
  }

  /**
   * Registers a drill-down handler for one mill that hangs until released, so an in-flight window
   * can be inspected. Returns the recorder and a releaser that settles it as a PDF or an error.
   */
  const heldDrillDown = (millId: number) => {
    const requested = vi.fn()
    let settle: ((asError: boolean) => void) | undefined
    server.use(
      http.get(DRILL_DOWN(millId), async ({ request }) => {
        requested(new URL(request.url).searchParams.get('year'))
        const asError = await new Promise<boolean>((resolve) => {
          settle = resolve
        })
        if (asError) {
          return HttpResponse.json(
            { detail: `mill ${millId} exploded.` },
            { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
          )
        }
        return HttpResponse.arrayBuffer(new TextEncoder().encode('%PDF-1.4 mock\n%%EOF\n').buffer, {
          headers: { 'Content-Type': 'application/pdf' },
        })
      }),
    )
    return {
      requested,
      releaseWithPdf: () => settle?.(false),
      releaseWithError: () => settle?.(true),
      started: () => waitFor(() => expect(requested).toHaveBeenCalled()),
    }
  }

  test('clicking a mill requests that mill and downloads mill_<millNumber>_print.pdf', async () => {
    const requested = vi.fn()
    await applied2021()
    // Mill ID 730 carries mill NUMBER 7300, and the two differ ON PURPOSE: the URL is keyed by the
    // id while the filename is keyed by the number (PrintSchedulesMB.java:332), so a filename built
    // from the id — or a URL built from the number — fails here rather than looking plausible.
    drillDownResponds(730, requested)

    await userEvent.click(millButton('MILL INFO FULL'))

    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    // The only frontend check on the parity filename. 19.1 recorded a local-only red here — under
    // responseType:'blob' an older MSW/undici build could not construct a Response from the body
    // ("object.stream is not a function"), so its equivalent assertion passed only in CI
    // (MillInformationReport.test.tsx:61-66). That no longer reproduces: verified 2026-09-02, both
    // this suite and MillInformationReport's are green locally. If it returns, the fix is the
    // toolchain, never weakening this assertion.
    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls[0][1]).toBe('mill_7300_print.pdf')
  })

  test('the drill-down sends the APPLIED year, not a newer unapplied selection', async () => {
    const requested = vi.fn()
    await applied2021()
    drillDownResponds(730, requested)

    // Move the selection to 2019 WITHOUT applying. The rows on screen still belong to 2021, so the
    // mill the user is clicking is a 2021 row and 2021 is the year its PDF must cover. Sending the
    // selection would silently produce a report for a table that is not on screen.
    await userEvent.selectOptions(screen.getByLabelText(YEAR_LABEL), '2019')
    await userEvent.click(millButton('MILL INFO FULL'))

    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
  })

  test('after sorting, clicking a row drills into THAT mill, not its pre-sort neighbour (S10)', async () => {
    const requested = vi.fn()
    await applied2021()
    // Only mill 731 answers. Sorted by Region ascending, MILL INFO SPARSE moves out of its served
    // position — so if the click resolved by row INDEX rather than by row identity, the request
    // would go to another mill's URL and 404 instead of downloading.
    drillDownResponds(731, requested)

    await clickHeader('Region')
    await userEvent.click(millButton('MILL INFO SPARSE'))

    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    expect(screen.queryByText(/no report status/i)).not.toBeInTheDocument()
  })

  test('the drill-down is reachable and activatable by keyboard alone', async () => {
    const requested = vi.fn()
    await applied2021()
    drillDownResponds(730, requested)

    // Focus via the keyboard, then activate with Enter — the WCAG 2.1 AA acceptance criterion. A
    // div with an onClick would satisfy the mouse test above and fail this one.
    const drillDown = millButton('MILL INFO FULL')
    drillDown.focus()
    expect(drillDown).toHaveFocus()
    await userEvent.keyboard('{Enter}')

    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    // And focus STAYS on the control it was on (review round 1, P8). A real `disabled` attribute
    // set on the focused element makes the browser blur it to <body>, so a keyboard user who
    // pressed Enter would silently lose their place in the table and have to tab back from the top.
    expect(millButton('MILL INFO FULL')).toHaveFocus()
    expect(millButton('MILL INFO FULL')).not.toBeDisabled()
  })

  test('start and outcome are announced through a polite live region (P8, SC 4.1.3)', async () => {
    // Review round 1, P8. Disabling the control said nothing to a screen reader: no aria-busy, no
    // live region, no announcement of either start or outcome. The region is mounted for the life
    // of the page — a live region has to already exist for a change inside it to be announced, so
    // rendering it only alongside the banner would announce nothing.
    await applied2021()
    const region = statusRegion()
    expect(region).toBeInTheDocument()
    expect(region).toHaveAttribute('aria-live', 'polite')
    expect(region).toHaveTextContent('')

    const full = heldDrillDown(730)
    await userEvent.click(millButton('MILL INFO FULL'))
    await full.started()

    // Start names the action and the mill.
    await waitFor(() =>
      expect(region).toHaveTextContent('Generating the mill information report for MILL INFO FULL'),
    )

    full.releaseWithPdf()
    // ...and so does the outcome, which is the half SC 4.1.3 is actually about.
    await waitFor(() =>
      expect(region).toHaveTextContent(
        'The mill information report for MILL INFO FULL has downloaded.',
      ),
    )
  })

  test('a failure is announced too, not just painted into the banner (P8)', async () => {
    await applied2021()
    server.use(
      http.get(DRILL_DOWN(730), () =>
        HttpResponse.json(
          { detail: 'Nope.' },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    await userEvent.click(millButton('MILL INFO FULL'))

    // The banner is a freshly mounted node, which is not reliably announced; the region carries the
    // same outcome through a channel that is.
    expect(await screen.findByText('Nope.')).toBeInTheDocument()
    await waitFor(() =>
      expect(statusRegion()).toHaveTextContent(
        'The mill information report for MILL INFO FULL failed. Nope.',
      ),
    )
  })

  test('a failed PDF shows the problem detail, keeps the table, and retries on a second click', async () => {
    await applied2021()
    server.use(
      http.get(DRILL_DOWN(730), () =>
        HttpResponse.json(
          { detail: 'The selected mill has no report status for the selected Report Year.' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    await userEvent.click(millButton('MILL INFO FULL'))

    // The verbatim `detail`, read out of the error BLOB (responseType 'blob' delivers the
    // problem+json body as a Blob too, so extractBlobDetail is what recovers it)...
    expect(
      await screen.findByText(
        'The selected mill has no report status for the selected Report Year.',
      ),
    ).toBeInTheDocument()
    // ...the table is untouched — all four mills, in the served order...
    expect(renderedMills()).toEqual([
      'AAA Milling',
      'MILL INFO FULL',
      'MILL INFO SPARSE',
      'MILL INFO NO CLIENT',
    ])
    // ...the year selection is retained...
    expect(screen.getByLabelText(YEAR_LABEL)).toHaveValue('2021')
    // ...no file was produced...
    expect(downloaded).not.toHaveBeenCalled()
    // ...and the empty-table message is NOT shown, because the table is not empty. This is the
    // reason a PDF failure has its own state instead of riding `error`: routing it through `error`
    // would make emptyMessage() go silent, which matters the moment the table IS empty.
    expect(screen.queryByText(PROMPT)).not.toBeInTheDocument()

    // Retryable by clicking the same mill again — nothing advertises the row as unavailable, and
    // the request goes out.
    const requested = vi.fn()
    drillDownResponds(730, requested)
    expectAdvertisedDisabled('MILL INFO FULL', false)
    await userEvent.click(millButton('MILL INFO FULL'))
    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
  })

  // Verbatim from utils/download.ts — the one message the guard raises.
  const TRUNCATED_MESSAGE =
    'The report did not download completely. Please try again \u2014 if it keeps failing, contact ' +
    'the ILCR administrator.'

  test('a TRUNCATED 200 application/pdf is refused: banner up, table intact, nothing saved', async () => {
    await applied2021()
    // The failure the streaming endpoint actually produces. ReportController commits 200 and the
    // application/pdf headers BEFORE exporting, so an export-time failure or an async timeout
    // cannot come back as problem+json — it comes back as a short body under a success status.
    // Without the blob guard the browser saves that as mill_7300_print.pdf and the administrator
    // has a file that will not open, with nothing on the page saying so (MRPT-002 S07 /
    // MRPT-004 S05).
    server.use(
      http.get(DRILL_DOWN(730), () =>
        HttpResponse.arrayBuffer(new TextEncoder().encode('%PDF-1.4 mock\n').buffer, {
          headers: { 'Content-Type': 'application/pdf' },
        }),
      ),
    )

    await userEvent.click(millButton('MILL INFO FULL'))

    // Exact text, not a regex: the same sentence also lands in the aria-live status region, and a
    // substring match would find both.
    expect(await screen.findByText(TRUNCATED_MESSAGE)).toBeInTheDocument()
    expect(
      screen.getByText(
        `The mill information report for MILL INFO FULL failed. ${TRUNCATED_MESSAGE}`,
      ),
    ).toBeInTheDocument()
    // Nothing was handed to the browser...
    expect(downloaded).not.toHaveBeenCalled()
    // ...the table survives, so the drill-down can be retried...
    expect(renderedMills()).toEqual([
      'AAA Milling',
      'MILL INFO FULL',
      'MILL INFO SPARSE',
      'MILL INFO NO CLIENT',
    ])
    expect(screen.getByLabelText(YEAR_LABEL)).toHaveValue('2021')

    // ...and a retry that returns a WHOLE PDF downloads normally, which is what proves the guard
    // rejects the body rather than the row.
    const requested = vi.fn()
    drillDownResponds(730, requested)
    await userEvent.click(millButton('MILL INFO FULL'))
    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls[0][1]).toBe('mill_7300_print.pdf')
  })

  test('a transport failure falls back to the page’s own PDF message', async () => {
    await applied2021()
    server.use(http.get(DRILL_DOWN(730), () => HttpResponse.error()))

    await userEvent.click(millButton('MILL INFO FULL'))

    expect(
      await screen.findByText('Unable to generate the mill information report.'),
    ).toBeInTheDocument()
    expect(renderedMills()).toHaveLength(4)
  })

  test('a PDF failure banner clears when the year selection moves on', async () => {
    await applied2021()
    server.use(http.get(DRILL_DOWN(730), () => HttpResponse.error()))
    await userEvent.click(millButton('MILL INFO FULL'))
    expect(
      await screen.findByText('Unable to generate the mill information report.'),
    ).toBeInTheDocument()

    // NotificationColumn has no dismiss control, so without this the banner would describe a mill
    // from a year no longer selected, permanently.
    await userEvent.selectOptions(screen.getByLabelText(YEAR_LABEL), '2019')
    await waitFor(() =>
      expect(
        screen.queryByText('Unable to generate the mill information report.'),
      ).not.toBeInTheDocument(),
    )
  })

  test('only the clicked row advertises busy while its PDF is in flight, and it fires once', async () => {
    await applied2021()
    const full = heldDrillDown(730)

    await userEvent.click(millButton('MILL INFO FULL'))
    await full.started()

    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', true))
    expect(millButton('MILL INFO FULL')).toHaveAttribute('aria-busy', 'true')
    // A second activation of the in-flight row cannot queue another request. The control is still a
    // real enabled button, so this is the HANDLER's early return doing the work, not the DOM.
    await userEvent.click(millButton('MILL INFO FULL'))
    expect(full.requested).toHaveBeenCalledTimes(1)
    // Every OTHER mill stays available — one download does not lock the table, and the year control
    // and Apply are untouched too, because neither is what the request depends on.
    expectAdvertisedDisabled('MILL INFO SPARSE', false)
    expectAdvertisedDisabled('AAA Milling', false)
    expect(screen.getByLabelText(YEAR_LABEL)).toBeEnabled()

    full.releaseWithPdf()
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', false))
    expect(full.requested).toHaveBeenCalledTimes(1)
  })

  test('two overlapping downloads keep independent state; neither settling clears the other (P1)', async () => {
    // Review round 1, P1. `downloadingMillId` was a single scalar with an unconditional `.finally`,
    // so this exact sequence broke three ways at once: clicking 731 re-enabled 730 while 730's
    // request was still open, 731 settling then nulled the state so NO row was busy with 730 still
    // outstanding, and a second 730 click fired a duplicate request that saved the same file twice.
    await applied2021()
    const full = heldDrillDown(730)
    const sparse = heldDrillDown(731)

    await userEvent.click(millButton('MILL INFO FULL'))
    await full.started()
    await userEvent.click(millButton('MILL INFO SPARSE'))
    await sparse.started()

    // BOTH rows are busy at once — the state is per-mill, not one slot.
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', true))
    expectAdvertisedDisabled('MILL INFO SPARSE', true)

    // 731 finishes first. It must clear ONLY itself.
    sparse.releaseWithPdf()
    await waitFor(() => expectAdvertisedDisabled('MILL INFO SPARSE', false))
    expect(downloaded.mock.calls.map((call) => call[1])).toEqual(['mill_7310_print.pdf'])
    // 730 is STILL outstanding, so it must still refuse a second activation.
    expectAdvertisedDisabled('MILL INFO FULL', true)
    await userEvent.click(millButton('MILL INFO FULL'))
    expect(full.requested).toHaveBeenCalledTimes(1)

    // And 730 failing LATE must not raise a banner over the download that already succeeded — the
    // scalar version wrote pdfError after 731's click had cleared it.
    full.releaseWithError()
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', false))
    expect(await screen.findByText('mill 730 exploded.')).toBeInTheDocument()
    // Exactly one file, from the request that actually succeeded.
    expect(downloaded).toHaveBeenCalledTimes(1)
  })

  test('a drill-down that outlives its year is abandoned — no banner, no file (P2)', async () => {
    // Review round 1, P2. `changeYear` cleared `pdfError` but cancelled nothing, so a late FAILURE
    // re-armed the very banner the clear exists to remove — describing a mill from a year no longer
    // on screen, with no dismiss control anywhere to remove it.
    await applied2021()
    const full = heldDrillDown(730)

    await userEvent.click(millButton('MILL INFO FULL'))
    await full.started()
    // Move the year WITHOUT waiting for the request. The existing sibling test awaits the banner
    // first, which is why it could never see this.
    await userEvent.selectOptions(screen.getByLabelText(YEAR_LABEL), '2019')
    full.releaseWithError()

    // Nothing appears. Asserted by settling a SECOND, current request afterwards, so the test
    // proves the stale result was dropped rather than merely not having arrived yet.
    const sparse = heldDrillDown(731)
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toEqual(['NINETEEN MILLING']))
    sparse.releaseWithPdf()

    expect(screen.queryByText('mill 730 exploded.')).not.toBeInTheDocument()
    expect(screen.queryByText('Report failed')).not.toBeInTheDocument()
  })

  test('a drill-down that outlives its year does not save a PDF for the old year (P2)', async () => {
    // The mirror case, and the worse one: a late SUCCESS used to save a file for the previous year,
    // and the parity filename carries no year, so nothing on disk distinguished it from the right
    // one.
    await applied2021()
    const full = heldDrillDown(730)

    await userEvent.click(millButton('MILL INFO FULL'))
    await full.started()
    await userEvent.selectOptions(screen.getByLabelText(YEAR_LABEL), '2019')
    full.releaseWithPdf()

    // Settle a current request afterwards, so "no download" is a fact about the stale one rather
    // than about timing.
    const nineteen = heldDrillDown(900)
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toEqual(['NINETEEN MILLING']))
    await userEvent.click(millButton('NINETEEN MILLING'))
    await nineteen.started()
    nineteen.releaseWithPdf()

    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls.map((call) => call[1])).toEqual(['mill_9001_print.pdf'])
  })

  test('a stale drill-down settling does not re-enable a row the CURRENT one is still fetching', async () => {
    // Review round 2. `.finally` deleted its mill from the in-flight set unconditionally, justified
    // as "the abandon already emptied the set, so this is a no-op". That holds only while the mill
    // stays out of the set — and this sequence puts it back.
    //
    // Changing the year abandons in-flight downloads but does NOT clear the rows (they still belong
    // to the applied year until Apply replaces them), so the same mill is still on screen and still
    // clickable. Click it again and it re-enters the set under the NEW generation; the old request
    // then settles and deletes an entry it no longer owns.
    await applied2021()
    const stale = heldDrillDown(730)

    await userEvent.click(millButton('MILL INFO FULL'))
    await stale.started()
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', true))

    // Bumps the generation, empties the set, leaves the 2021 rows up.
    await userEvent.selectOptions(screen.getByLabelText(YEAR_LABEL), '2019')
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', false))

    const current = heldDrillDown(730)
    await userEvent.click(millButton('MILL INFO FULL'))
    await current.started()
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', true))

    // The stale request lands while the current one is still open. Before the fix this re-enabled
    // the row, so the administrator could fire a duplicate request for a mill already downloading.
    stale.releaseWithPdf()
    await waitFor(() => expect(stale.requested).toHaveBeenCalledTimes(1))
    expectAdvertisedDisabled('MILL INFO FULL', true)
    await userEvent.click(millButton('MILL INFO FULL'))
    expect(current.requested).toHaveBeenCalledTimes(1)

    // The current request still owns its own removal, so the row recovers normally — and only it
    // saves a file, because the stale one is dropped by the generation check in `.then`.
    current.releaseWithPdf()
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', false))
    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls[0][1]).toBe('mill_7300_print.pdf')
  })

  test('re-applying the SAME year does NOT abandon an in-flight drill-down', async () => {
    // The other side of the P2 guard, and the reason it hangs off a real load rather than off every
    // Apply press: re-applying the same year does not refetch and does not replace the rows, so a
    // download in flight is still answering for exactly what is on screen. Cancelling it would make
    // a no-op button press destroy work.
    await applied2021()
    const full = heldDrillDown(730)

    await userEvent.click(millButton('MILL INFO FULL'))
    await full.started()
    await userEvent.click(applyButton())
    full.releaseWithPdf()

    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls[0][1]).toBe('mill_7300_print.pdf')
  })

  test('drill-downs are unavailable while an Apply is in flight (P3)', async () => {
    // Review round 1, P3. `busy` was never passed to the table, so during an Apply a click still
    // fired against the OUTGOING year — landing a 2021 PDF, or a 404 naming a 2021 mill, on a 2019
    // table.
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    let releaseRows: (() => void) | undefined
    server.use(
      http.get(STATUS_ENDPOINT, async ({ request }) => {
        const year = new URL(request.url).searchParams.get('year')
        if (year === '2019') {
          await new Promise<void>((resolve) => {
            releaseRows = resolve
          })
          return HttpResponse.json(ROWS_2019)
        }
        return HttpResponse.json(ROWS_2021)
      }),
    )
    server.use(http.get(DRILL_DOWN(730), () => requested()))
    render(<MillReportStatus />)
    const select = await screen.findByLabelText(YEAR_LABEL)
    await userEvent.selectOptions(select, '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    // Apply 2019 and click a 2021 row before it lands.
    await userEvent.selectOptions(select, '2019')
    await userEvent.click(applyButton())
    await waitFor(() => expectAdvertisedDisabled('MILL INFO FULL', true))
    await userEvent.click(millButton('MILL INFO FULL'))
    expect(requested).not.toHaveBeenCalled()

    releaseRows?.()
    await waitFor(() => expect(renderedMills()).toEqual(['NINETEEN MILLING']))
    // Available again once the table is settled.
    expectAdvertisedDisabled('NINETEEN MILLING', false)
  })

  /** Rows whose Mill cells exercise the fallback chain: a name, a blank name, none, neither. */
  const FALLBACK_ROWS = [
    { millId: 801, millNumber: '8010', active: true },
    { millId: 802, active: false },
    { millId: 803, millNumber: '8030', millName: '   ', active: true },
    { millId: 804, millNumber: '8040', millName: 'ZED MILLING', active: true },
  ] as MillReportStatusRow[]

  const applyFallbackRows = async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': FALLBACK_ROWS })
    render(<MillReportStatus />)
    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))
  }

  test('a whitespace-only mill name falls back like an absent one, label and filename (P5)', async () => {
    // Review round 1, P5. Both fallback chains used `??`, which passes '' and '   ' straight
    // through, while the BACKEND used isBlank for its half of the same filename contract
    // (ReportController.drillDownFilename). MILL.MILL_NAME is a nullable VARCHAR2(100) with no
    // non-blank constraint, so this row is a reachable delivery state — and it rendered a ghost
    // Button with an invisible label and an accessible name of
    // "Generate the mill information report for    ", which is exactly what the chain prevents.
    const requested = vi.fn()
    await applyFallbackRows()

    // Falls through to the mill NUMBER, same as the row with no name at all.
    expect(millButton('8030')).toHaveAccessibleName('Generate the mill information report for 8030')
    expect(millButton('8030')).toHaveTextContent('8030')

    // And the filename agrees with the backend, which derives the identical name from the same mill.
    drillDownResponds(803, requested)
    await userEvent.click(millButton('8030'))
    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls[0][1]).toBe('mill_8030_print.pdf')
  })

  test('the Mill column sorts the label the user SEES, including the fallbacks (P4)', async () => {
    // Review round 1, P4. The column displayed millLabel but sorted row.millName, breaking the
    // table's own stated invariant ("every extractor sorts the value the user can SEE"). A row
    // visibly labelled "8010" sorted as if blank and landed among the nulls, while aria-sort
    // claimed an ordering the column did not have.
    await applyFallbackRows()

    // Server order is mill-id ascending: 8010, 802, 8030, ZED MILLING.
    expect(renderedMills()).toEqual(['8010', '802', '8030', 'ZED MILLING'])

    await clickHeader('Mill')
    expect(header('Mill')).toHaveAttribute('aria-sort', 'ascending')
    // Ascending over the RENDERED strings: '802' < '8010' < '8030' < 'ZED MILLING' (string order,
    // which is what legacy's display-string sort did). Nothing is parked with the nulls, because
    // after the fallback there ARE no nulls.
    expect(renderedMills()).toEqual(['802', '8010', '8030', 'ZED MILLING'])

    await clickHeader('Mill')
    expect(header('Mill')).toHaveAttribute('aria-sort', 'descending')
    expect(renderedMills()).toEqual(['ZED MILLING', '8030', '8010', '802'])

    // Third click restores the server's mill-id order.
    await clickHeader('Mill')
    expect(header('Mill')).toHaveAttribute('aria-sort', 'none')
    expect(renderedMills()).toEqual(['8010', '802', '8030', 'ZED MILLING'])
  })

  test('a whitespace-only mill NUMBER sorts last rather than as zero (P5)', async () => {
    // The same blank-awareness in the Mill Number extractor, which already treated '' as absent but
    // not '   ' — and `Number('   ')` is 0, not NaN, so a padded value sorted ahead of every real
    // mill instead of last.
    yearsRespond(OPEN_YEARS)
    statusResponds({
      '2021': [
        { millId: 901, millNumber: '   ', millName: 'PADDED', active: true },
        { millId: 902, millNumber: '5000', millName: 'REAL', active: true },
      ] as MillReportStatusRow[],
    })
    render(<MillReportStatus />)
    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(2))

    await clickHeader('Mill Number')
    expect(renderedMills()).toEqual(['REAL', 'PADDED'])
  })

  test('a mill with no name is labelled by its number, and a nameless numberless one by its id', async () => {
    const requested = vi.fn()
    yearsRespond(OPEN_YEARS)
    statusResponds({
      '2021': [
        // No millName: the label and the accessible name fall back to the mill NUMBER, and the
        // filename still uses the number.
        { millId: 801, millNumber: '8010', active: true },
        // Neither name nor number — referential corruption, but the control must still be visible
        // and activatable, so both the label and the filename fall back to the mill ID. This is the
        // same fallback the BACKEND applies when naming the file, and the two have to agree.
        { millId: 802, active: false },
      ] as MillReportStatusRow[],
    })
    render(<MillReportStatus />)
    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toEqual(['8010', '802']))

    expect(millButton('8010')).toHaveAccessibleName('Generate the mill information report for 8010')
    expect(millButton('802')).toHaveAccessibleName('Generate the mill information report for 802')

    drillDownResponds(802, requested)
    await userEvent.click(millButton('802'))
    await waitFor(() => expect(requested).toHaveBeenCalledWith('2021'))
    // The backend applies the SAME id fallback when naming the file (ReportController
    // .drillDownFilename), so this assertion is half of a two-sided contract.
    await waitFor(() => expect(downloaded).toHaveBeenCalledTimes(1))
    expect(downloaded.mock.calls[0][1]).toBe('mill_802_print.pdf')
  })
})
