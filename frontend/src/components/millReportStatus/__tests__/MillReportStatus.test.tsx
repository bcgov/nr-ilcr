import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import MillReportStatus from '@/components/millReportStatus'
import type MillReportStatusRow from '@/interfaces/MillReportStatusRow'

const YEARS_ENDPOINT = 'http://localhost:3000/api/v1/reporting-years'
const STATUS_ENDPOINT = 'http://localhost:3000/api/v1/reports/mill-status'

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

  test('the Mill cell is plain text — no drill-down link (Story 19.3 fence)', async () => {
    yearsRespond(OPEN_YEARS)
    statusResponds({ '2021': ROWS_2021 })
    render(<MillReportStatus />)

    await userEvent.selectOptions(await screen.findByLabelText(YEAR_LABEL), '2021')
    await userEvent.click(applyButton())
    await waitFor(() => expect(renderedMills()).toHaveLength(4))

    const millCell = cellsFor('MILL INFO FULL')[1]
    expect(within(millCell).queryByRole('link')).not.toBeInTheDocument()
    expect(within(millCell).queryByRole('button')).not.toBeInTheDocument()
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
})
