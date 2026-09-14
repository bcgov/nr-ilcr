import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, within } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import DataExtract from '@/components/dataExtract'
import { triggerDownload } from '@/utils/download'
import type * as DownloadUtil from '@/utils/download'

// Keep the real extractBlobMessages — the blob error path is one of the things under test here, and
// mocking it would make the accumulating-400-through-a-Blob assertion vacuous. Spy only on the
// download side effect, which jsdom cannot perform.
vi.mock('@/utils/download', async (importOriginal) => ({
  ...(await importOriginal<typeof DownloadUtil>()),
  triggerDownload: vi.fn(),
}))

beforeEach(() => {
  vi.mocked(triggerDownload).mockReset()
})

// jsdom lacks scrollIntoView; Carbon's list-box components call it on the highlighted option
// whenever a menu opens. Same shim as the Home suite.
window.HTMLElement.prototype.scrollIntoView = () => {}

const MILLS = 'http://localhost:3000/api/v1/mills'
const YEARS = 'http://localhost:3000/api/v1/reporting-years'
const EXTRACT = 'http://localhost:3000/api/v1/reports/data-extract'
const MESSAGES = 'http://localhost:3000/api/v1/messages'

// The ids are deliberately NOT in the same order as the mill numbers, so an assertion on the
// rendered order proves the page preserves the SERVER's ordering rather than accidentally agreeing
// with the ids. The third mill has neither number nor name: both columns are nullable and Jackson
// omits a null, so the label has to stay renderable without them.
const MILLS_THREE = [
  { millId: 9002, millNumber: '514', millName: 'AAA Milling', millStatusCode: 'ACT' },
  { millId: 9001, millNumber: '516', millName: 'Closed Milling', millStatusCode: 'CLS' },
  { millId: 9003, millStatusCode: 'ACT' },
]

// The API serves opened years newest-first (BR-03), so the latest is the first element.
const YEARS_THREE = [{ reportYear: 2021 }, { reportYear: 2020 }, { reportYear: 2019 }]

const START_REQUIRED = 'Start Year: Value is required.'
const END_REQUIRED = 'End Year: Value is required.'
const MILLS_NOT_SELECTED = 'Please select at least one Mill for extracting.'
const SCHEDULES_NOT_SELECTED = 'Please select at least one Schedule for extracting.'

const problem400 = (messages: { key: string; text: string }[]) =>
  new HttpResponse(
    JSON.stringify({ status: 400, detail: messages.map((m) => m.text).join('; '), messages }),
    { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
  )

/**
 * A successful extract: the CSV file itself.
 *
 * `arrayBuffer`, not a `Blob` body — this MSW build coerces a Blob to its string form, so a Blob
 * fixture would deliver the twelve bytes of "[object Blob]" and every download assertion would pass
 * on nothing (the same trap the Print Schedules suite documents).
 */
const csvResponse = (body = '"ILCR"\n"*** END OF REPORT ***"\n') =>
  HttpResponse.arrayBuffer(new TextEncoder().encode(body).buffer as ArrayBuffer, {
    headers: { 'Content-Type': 'application/csv; charset=UTF-8' },
  })

/** The allowlisted SUC-001 lookup. Deliberately NOT the bundle's real text — see the test below. */
const SERVER_SUCCESS_TEXT = 'Server-owned extraction confirmation.'

const messageStub = (text: string | null = SERVER_SUCCESS_TEXT) =>
  http.get(MESSAGES, ({ request }) => {
    const key = new URL(request.url).searchParams.get('key')
    if (key !== 'dataExtractedSuccesfullyInfoMsg') {
      return new HttpResponse(null, { status: 404 })
    }
    return text === null
      ? new HttpResponse(null, { status: 500 })
      : HttpResponse.json({ key, text })
  })

const lists = (mills: unknown[] = MILLS_THREE, years: unknown[] = YEARS_THREE) => [
  http.get(MILLS, () => HttpResponse.json(mills)),
  http.get(YEARS, () => HttpResponse.json(years)),
  messageStub(),
]

/** The Selected Report Data Summary row for a label, as rendered text. */
const summaryValue = (label: string) =>
  screen.getByTestId(`data-extract-summary-${label}`).textContent

/**
 * ONE picker's listbox, found through its own wrapper.
 *
 * Scoped deliberately, twice over. Carbon renders a `role="listbox"` menu for EVERY
 * FilterableMultiSelect whether or not it is open — only the options inside are conditional — so an
 * unscoped `getByRole('listbox')` finds two elements and throws. And a native `<select>` exposes its
 * children as `option`, so the two year pickers put eight more options on the page that an unscoped
 * option query would mix into every assertion below.
 */
const listboxFor = (name: RegExp): HTMLElement => {
  const input = screen.getByRole('combobox', { name })
  const wrapper = input.closest('.cds--multi-select__wrapper')
  if (wrapper === null) {
    throw new Error(`no multi-select wrapper around the control named ${String(name)}`)
  }
  return within(wrapper as HTMLElement).getByRole('listbox')
}

async function openPicker(user: ReturnType<typeof userEvent.setup>, name: RegExp) {
  await user.click(await screen.findByRole('combobox', { name }))
  return listboxFor(name)
}

// queryAll, not getAll: a filter that matches nothing is a legitimate state this suite asserts, and
// getAllByRole throws on an empty result rather than returning one.
const optionNames = (listbox: HTMLElement) =>
  within(listbox)
    .queryAllByRole('option')
    .map((option) => option.textContent?.trim() ?? '')

const realOptions = (listbox: HTMLElement) =>
  optionNames(listbox).filter((text) => text !== 'Select all')

const pick = async (user: ReturnType<typeof userEvent.setup>, listbox: HTMLElement, name: string) =>
  user.click(within(listbox).getByRole('option', { name }))

async function renderPage() {
  render(<DataExtract />)
  // The year pickers are populated from the list load, so waiting for the default proves the page
  // has settled before any interaction.
  await waitFor(() => {
    expect(screen.getByLabelText('Start Year:')).toHaveValue('2021')
  })
}

describe('Data Extract — page shell and context independence (AC1)', () => {
  test('renders its own title and takes NO Home mill/year context', async () => {
    server.use(...lists())
    await renderPage()

    expect(screen.getByRole('heading', { name: 'Data Extract' })).toBeInTheDocument()
    // The page never renders a context guard: there is no mill/year to be missing, because the
    // extract selects its own. The tombstone above IS chrome and carries the working-context lines,
    // which is legacy's shared submenu behaviour — but it makes no claim about what the extract
    // covers, and nothing here gates on it.
    expect(
      screen.queryByText(/Choose a mill and reporting year on the Home page/i),
    ).not.toBeInTheDocument()
    expect(screen.queryByText(/Please Select Mill and Reporting Year/i)).not.toBeInTheDocument()
  })
})

describe('Data Extract — the four selection inputs (AC2)', () => {
  test('both year pickers default to the LATEST reporting year', async () => {
    server.use(...lists())
    await renderPage()

    expect(screen.getByLabelText('Start Year:')).toHaveValue('2021')
    expect(screen.getByLabelText('End Year:')).toHaveValue('2021')
  })

  test('year options are year-descending, each keeping a selectable no-selection placeholder', async () => {
    server.use(...lists())
    await renderPage()

    const start = screen.getByLabelText('Start Year:')
    const texts = within(start)
      .getAllByRole('option')
      .map((option) => option.textContent)
    // The placeholder is legacy's own no-selection item (extractData.xhtml:43), and it stays
    // selectable — which is what lets Clear put the picker back to empty.
    expect(texts).toEqual(['Start Year', '2021', '2020', '2019'])
  })

  test('mills and schedules start EMPTY — nothing is pre-selected', async () => {
    server.use(...lists())
    await renderPage()

    expect(summaryValue('Mills')).toBe('')
    expect(summaryValue('Schedules')).toBe('')
  })

  test('mill options are labelled "<number> - <name>" in the served order', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Mills/i)

    // Select all leads the list, then the mills in the order the API served them (mill-number
    // ascending in production). The mill with neither column still renders an activatable label.
    expect(optionNames(listbox)).toEqual([
      'Select all',
      '514 - AAA Milling',
      '516 - Closed Milling',
      'Mill 9003',
    ])
  })

  test('closed mills are offered — you extract history for a mill that has closed', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Mills/i)

    // Legacy uses the non-active-filtered mill selection here, unlike the Mill Information report.
    expect(
      within(listbox).getByRole('option', { name: '516 - Closed Milling' }),
    ).toBeInTheDocument()
  })

  test('there are ELEVEN schedule options, not twelve — Schedule 7 is one choice', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Schedules/i)

    expect(realOptions(listbox)).toEqual([
      'Schedule 1',
      'Schedule 2',
      'Schedule 3',
      'Schedule 4',
      'Schedule 5',
      'Schedule 6',
      'Schedule 7',
      'Schedule 8',
      'Schedule 9',
      'Schedule 10',
      'Schedule 11',
    ])
    expect(realOptions(listbox)).toHaveLength(11)
    // The positive control for the count: 7A and 7B are covered BY "Schedule 7" and are not
    // separate options, which is the whole reason the count is eleven.
    expect(within(listbox).queryByRole('option', { name: 'Schedule 7A' })).not.toBeInTheDocument()
    expect(within(listbox).queryByRole('option', { name: 'Schedule 7B' })).not.toBeInTheDocument()
  })

  test('schedule options keep 1..11 order and are NOT hoisted when selected', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Schedules/i)

    // Pick a late option, then reopen. Carbon's default selectionFeedback would move it to the top
    // of the list; `selectionFeedback="fixed"` plus an identity sort keeps the server's order.
    await pick(user, listbox, 'Schedule 11')
    await waitFor(() => {
      expect(summaryValue('Schedules')).toBe('Schedule 11')
    })
    await user.keyboard('{Escape}')
    const reopened = await openPicker(user, /Schedules/i)

    const options = realOptions(reopened)
    expect(options[0]).toBe('Schedule 1')
    expect(options[10]).toBe('Schedule 11')
  })

  test('the schedule filter is PREFIX-first: "Schedule 1" matches 1, 10 and 11', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const input = await screen.findByRole('combobox', { name: /Schedules/i })
    await user.click(input)
    await user.type(input, 'Schedule 1')

    // Legacy's filterMatchMode="startsWith" filters on the option LABEL, and "Schedule 1",
    // "Schedule 10" and "Schedule 11" all begin with "Schedule 1" — so a prefix match returns three
    // options where an exact match would return one. That is the behaviour being pinned.
    const listbox = listboxFor(/Schedules/i)
    expect(realOptions(listbox)).toEqual(['Schedule 1', 'Schedule 10', 'Schedule 11'])
    expect(realOptions(listbox)).not.toContain('Schedule 2')
  })

  test('a bare "1" matches nothing in the schedule picker — prefix, not substring', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const input = await screen.findByRole('combobox', { name: /Schedules/i })
    await user.click(input)
    await user.type(input, '1')

    // Every schedule label begins with the word "Schedule", so a lone digit is a prefix of none of
    // them. Legacy behaved the same way: startsWith is evaluated against the whole label, and its
    // picker offered no way to search by the number alone. Asserted rather than left implicit,
    // because the obvious "improvement" — switching to a substring match — would be a silent parity
    // change, and because the AC illustrating this rule quotes a bare "1" as the query.
    expect(realOptions(listboxFor(/Schedules/i))).toEqual([])
  })

  test('the mill filter matches on the number prefix, not a substring', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const input = await screen.findByRole('combobox', { name: /Mills/i })
    await user.click(input)
    await user.type(input, '51')

    expect(realOptions(listboxFor(/Mills/i))).toEqual(['514 - AAA Milling', '516 - Closed Milling'])

    // Positive control for "prefix, not substring": "Milling" appears inside two labels but starts
    // neither, so a substring filter would return two options where a prefix filter returns none.
    await user.clear(input)
    await user.type(input, 'Milling')
    expect(realOptions(listboxFor(/Mills/i))).toEqual([])
  })

  test('select-all checks EVERY option in the picker', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Schedules/i)
    await pick(user, listbox, 'Select all')

    await waitFor(() => {
      expect(summaryValue('Schedules')).toBe(
        'Schedule 1, Schedule 2, Schedule 3, Schedule 4, Schedule 5, Schedule 6, Schedule 7, ' +
          'Schedule 8, Schedule 9, Schedule 10, Schedule 11',
      )
    })
  })

  test('no picker passes a size prop — control height is owned centrally', async () => {
    server.use(...lists())
    await renderPage()

    // The 48px height comes from one app-wide token on `.cds--list-box`, so the assertion is that
    // each control carries that class and NO size modifier of its own.
    for (const name of [/Mills/i, /Schedules/i]) {
      const control = (await screen.findByRole('combobox', { name })).closest('.cds--list-box')
      expect(control).not.toBeNull()
      expect(control?.className).not.toMatch(/--(sm|md|lg|xl)(\s|$)/)
    }
  })
})

describe('Data Extract — Selected Report Data Summary (AC3)', () => {
  test('echoes mill NUMBERS, not ids and not the option labels', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Mills/i)
    await pick(user, listbox, '514 - AAA Milling')
    await pick(user, listbox, '516 - Closed Milling')

    await waitFor(() => {
      expect(summaryValue('Mills')).toBe('514, 516')
    })
    // The ids are 9002/9001 and the labels carry the names — neither may leak into the echo.
    expect(summaryValue('Mills')).not.toContain('9002')
    expect(summaryValue('Mills')).not.toContain('AAA Milling')
  })

  test('a mill with no number echoes under its option name, so the summary matches the request', async () => {
    // Recorded deviation (J): legacy called getMillNumber().toString() on this row and would have
    // thrown. Dropping the row from the echo instead — the first cut — made a selection of only this
    // mill read as "nothing selected" while the request carried its id (21.1 review P3).
    const bodies: unknown[] = []
    server.use(
      ...lists(),
      http.post(EXTRACT, async ({ request }) => {
        bodies.push(await request.json())
        return csvResponse()
      }),
    )
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Mills/i)
    await pick(user, listbox, 'Mill 9003')
    await user.keyboard('{Escape}')

    await waitFor(() => {
      expect(summaryValue('Mills')).toBe('Mill 9003')
    })
    await user.click(screen.getByRole('button', { name: 'Generate Report' }))
    await waitFor(() => {
      expect(bodies).toHaveLength(1)
    })
    expect((bodies[0] as { millIds: number[] }).millIds).toEqual([9003])
  })

  test('two mills sharing a label are selected by IDENTITY — ticking one does not tick both', async () => {
    // MILL_NUMBER and MILL_NAME are nullable with no uniqueness constraint, so two rows can render
    // the same "<number> - <name>". The round trip goes by millId, not by display string
    // (21.1 review P2).
    const twins = [
      { millId: 7001, millNumber: '700', millName: 'Twin Milling', millStatusCode: 'ACT' },
      { millId: 7002, millNumber: '700', millName: 'Twin Milling', millStatusCode: 'ACT' },
    ]
    const bodies: unknown[] = []
    server.use(
      ...lists(twins),
      http.post(EXTRACT, async ({ request }) => {
        bodies.push(await request.json())
        return csvResponse()
      }),
    )
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Mills/i)
    const [first] = within(listbox).getAllByRole('option', { name: '700 - Twin Milling' })
    await user.click(first)
    await user.keyboard('{Escape}')

    await waitFor(() => {
      expect(summaryValue('Mills')).toBe('700')
    })
    await user.click(screen.getByRole('button', { name: 'Generate Report' }))
    await waitFor(() => {
      expect(bodies).toHaveLength(1)
    })
    expect((bodies[0] as { millIds: number[] }).millIds).toEqual([7001])
  })

  test('echoes the RAW checked schedule names, never the detail-expanded list', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()
    const listbox = await openPicker(user, /Schedules/i)
    await pick(user, listbox, 'Schedule 7')
    await pick(user, listbox, 'Schedule 1')

    await waitFor(() => {
      // Option order, not click order — legacy submitted the checkbox values in component order.
      expect(summaryValue('Schedules')).toBe('Schedule 1, Schedule 7')
    })
    // The sub-table expansion ("Schedule 7 A"/"Schedule 1 Other") belongs to the CSV, not here.
    expect(summaryValue('Schedules')).not.toContain('Schedule 7 A')
    expect(summaryValue('Schedules')).not.toContain('Other')
  })

  test('echoes the year selections live, and an empty selection as empty', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()

    expect(summaryValue('Start Year')).toBe('2021')
    await user.selectOptions(screen.getByLabelText('Start Year:'), '2019')
    await waitFor(() => {
      expect(summaryValue('Start Year')).toBe('2019')
    })

    // Empty is empty — not a placeholder and not a count. CNT-001 is not a counter.
    await user.selectOptions(screen.getByLabelText('Start Year:'), '')
    await waitFor(() => {
      expect(summaryValue('Start Year')).toBe('')
    })
    expect(summaryValue('Mills')).not.toMatch(/\d+\s+selected/i)
  })
})

describe('Data Extract — Clear (AC4)', () => {
  test('empties all four pickers and blanks all four summary rows', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()

    const mills = await openPicker(user, /Mills/i)
    await pick(user, mills, '514 - AAA Milling')
    await user.keyboard('{Escape}')
    const schedules = await openPicker(user, /Schedules/i)
    await pick(user, schedules, 'Schedule 1')
    await user.keyboard('{Escape}')
    await waitFor(() => {
      expect(summaryValue('Mills')).toBe('514')
    })

    await user.click(screen.getByRole('button', { name: 'Clear' }))

    await waitFor(() => {
      expect(summaryValue('Mills')).toBe('')
    })
    expect(summaryValue('Schedules')).toBe('')
    expect(summaryValue('Start Year')).toBe('')
    expect(summaryValue('End Year')).toBe('')
  })

  test('does NOT restore the latest-year defaults — the years are emptied', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Clear' }))

    // legacy clear() nulls all four fields and does not re-run initPeriods(). Print Schedules'
    // Clear restores its defaults; this one must not be mistaken for that.
    await waitFor(() => {
      expect(screen.getByLabelText('Start Year:')).toHaveValue('')
    })
    expect(screen.getByLabelText('End Year:')).toHaveValue('')
  })

  test('the pickers are genuinely selectable again after Clear, not stuck empty', async () => {
    server.use(...lists())
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Clear' }))
    await waitFor(() => {
      expect(screen.getByLabelText('Start Year:')).toHaveValue('')
    })

    // Without this, AC6 could never be exercised in a browser: a picker that cannot return to a
    // placeholder can never be submitted blank.
    await user.selectOptions(screen.getByLabelText('Start Year:'), '2020')
    expect(screen.getByLabelText('Start Year:')).toHaveValue('2020')
  })

  test('dismisses any validation messages on screen', async () => {
    server.use(
      ...lists(),
      http.post(EXTRACT, () =>
        problem400([{ key: 'extractMillsNotSelectedMsg', text: MILLS_NOT_SELECTED }]),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))
    expect(await screen.findByText(MILLS_NOT_SELECTED)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Clear' }))
    await waitFor(() => {
      expect(screen.queryByText(MILLS_NOT_SELECTED)).not.toBeInTheDocument()
    })
  })
})

describe('Data Extract — accumulating validation (AC5, AC6)', () => {
  test('renders ALL returned messages together, one banner each, in server order', async () => {
    server.use(
      ...lists(),
      http.post(EXTRACT, () =>
        problem400([
          { key: 'javax.faces.component.UIInput.REQUIRED', text: START_REQUIRED },
          { key: 'javax.faces.component.UIInput.REQUIRED', text: END_REQUIRED },
          { key: 'extractMillsNotSelectedMsg', text: MILLS_NOT_SELECTED },
          { key: 'extractSchedulesNotSelectedMsg', text: SCHEDULES_NOT_SELECTED },
        ]),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    // All four, not the first one: this is the assertion the whole story exists for.
    expect(await screen.findByText(START_REQUIRED)).toBeInTheDocument()
    expect(screen.getByText(END_REQUIRED)).toBeInTheDocument()
    expect(screen.getByText(MILLS_NOT_SELECTED)).toBeInTheDocument()
    expect(screen.getByText(SCHEDULES_NOT_SELECTED)).toBeInTheDocument()

    const rendered = screen
      .getAllByTestId('data-extract-message')
      .map((node) => node.textContent ?? '')
    expect(rendered).toHaveLength(4)
    expect(rendered[0]).toContain(START_REQUIRED)
    expect(rendered[3]).toContain(SCHEDULES_NOT_SELECTED)
  })

  test('the message text comes from the server, never a client literal', async () => {
    // The server is free to word these differently; the page must show whatever it sent. A page
    // holding its own copy of "Start Year: Value is required." would pass the test above while
    // ignoring the response entirely, which is the defect recorded against a shipped sibling page.
    server.use(
      ...lists(),
      http.post(EXTRACT, () =>
        problem400([
          { key: 'javax.faces.component.UIInput.REQUIRED', text: 'Commencement Year: absent.' },
        ]),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    expect(await screen.findByText('Commencement Year: absent.')).toBeInTheDocument()
    expect(screen.queryByText(START_REQUIRED)).not.toBeInTheDocument()
  })

  test('falls back to `detail` when a 400 carries no messages array', async () => {
    server.use(
      ...lists(),
      http.post(
        EXTRACT,
        () =>
          new HttpResponse(JSON.stringify({ status: 400, detail: 'Something was rejected.' }), {
            status: 400,
            headers: { 'Content-Type': 'application/problem+json' },
          }),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    expect(await screen.findByText('Something was rejected.')).toBeInTheDocument()
  })

  test('messages clear on ANY selection change', async () => {
    server.use(
      ...lists(),
      http.post(EXTRACT, () =>
        problem400([{ key: 'extractMillsNotSelectedMsg', text: MILLS_NOT_SELECTED }]),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))
    expect(await screen.findByText(MILLS_NOT_SELECTED)).toBeInTheDocument()

    // A year change is a selection change. The banners have no dismiss control, so without this
    // they would describe a selection that is no longer on screen.
    await user.selectOptions(screen.getByLabelText('End Year:'), '2020')
    await waitFor(() => {
      expect(screen.queryByText(MILLS_NOT_SELECTED)).not.toBeInTheDocument()
    })
  })

  test('a blank submit is sent to the server rather than blocked on the client', async () => {
    // AC6 is only reachable if the page actually submits an empty selection. A client-side gate
    // here would make the server's accumulated messages unreachable in a browser.
    const bodies: unknown[] = []
    server.use(
      ...lists(),
      http.post(EXTRACT, async ({ request }) => {
        bodies.push(await request.json())
        return problem400([
          { key: 'javax.faces.component.UIInput.REQUIRED', text: START_REQUIRED },
          { key: 'extractMillsNotSelectedMsg', text: MILLS_NOT_SELECTED },
        ])
      }),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Clear' }))
    await waitFor(() => {
      expect(screen.getByLabelText('Start Year:')).toHaveValue('')
    })
    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => {
      expect(bodies).toHaveLength(1)
    })
    expect(bodies[0]).toEqual({ startYear: '', endYear: '', millIds: [], schedules: [] })
    expect(await screen.findByText(START_REQUIRED)).toBeInTheDocument()
  })

  test('sends mill IDS and raw schedule names, matching the pinned request shape', async () => {
    const bodies: unknown[] = []
    server.use(
      ...lists(),
      http.post(EXTRACT, async ({ request }) => {
        bodies.push(await request.json())
        return csvResponse()
      }),
    )
    await renderPage()
    const user = userEvent.setup()

    const mills = await openPicker(user, /Mills/i)
    await pick(user, mills, '516 - Closed Milling')
    await user.keyboard('{Escape}')
    const schedules = await openPicker(user, /Schedules/i)
    await pick(user, schedules, 'Schedule 7')
    await user.keyboard('{Escape}')
    await waitFor(() => {
      expect(summaryValue('Mills')).toBe('516')
    })
    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => {
      expect(bodies).toHaveLength(1)
    })
    // Ids on the wire, numbers on the screen — the summary echoes 516 while the request carries
    // 9001. Years are raw strings, never numbers, so a blank stays distinguishable from a zero.
    expect(bodies[0]).toEqual({
      startYear: '2021',
      endYear: '2021',
      millIds: [9001],
      schedules: ['Schedule 7'],
    })
  })

  test('downloads the body as dataExtract<yyyyMMdd>.csv and confirms with the server sentence', async () => {
    server.use(
      ...lists(),
      http.post(EXTRACT, () => csvResponse()),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => {
      expect(vi.mocked(triggerDownload)).toHaveBeenCalledTimes(1)
    })
    const [blob, filename] = vi.mocked(triggerDownload).mock.calls[0]
    expect(blob).toBeInstanceOf(Blob)
    // Matched by shape, not against a frozen literal: the name carries today's date, and fake timers
    // were abandoned on this project. The eight digits are what the contract actually pins.
    expect(filename).toMatch(/^dataExtract\d{8}\.csv$/)
    // And it agrees with the browser clock the page read it from.
    const today = new Date()
    const expected = `${String(today.getFullYear())}${String(today.getMonth() + 1).padStart(2, '0')}${String(today.getDate()).padStart(2, '0')}`
    expect(filename).toBe(`dataExtract${expected}.csv`)

    const banner = await screen.findByTestId('data-extract-success')
    expect(banner).toHaveTextContent('Generated')
    expect(banner).toHaveTextContent(SERVER_SUCCESS_TEXT)
    expect(screen.getByRole('status', { name: 'Data extract status' })).toHaveTextContent(
      `The data extract has been generated. ${SERVER_SUCCESS_TEXT}`,
    )
  })

  test('renders SUC-001 from the server bundle, never a client literal', async () => {
    // The proof, not a tautology: the stub serves text that is NOT the bundle's real sentence, so a
    // page that hardcoded "Data extraction successfully." would render the wrong string and fail.
    server.use(
      ...lists(),
      http.post(EXTRACT, () => csvResponse()),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    expect(await screen.findByText(SERVER_SUCCESS_TEXT)).toBeInTheDocument()
    expect(screen.queryByText('Data extraction successfully.')).not.toBeInTheDocument()
  })

  test('falls back to the mirrored literal only when the message lookup itself fails', async () => {
    server.use(
      http.get(MILLS, () => HttpResponse.json(MILLS_THREE)),
      http.get(YEARS, () => HttpResponse.json(YEARS_THREE)),
      messageStub(null),
      http.post(EXTRACT, () => csvResponse()),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    expect(await screen.findByText('Data extraction successfully.')).toBeInTheDocument()
    // A failed lookup for a not-yet-earned confirmation is not itself an error worth a banner.
    expect(screen.queryByText(/Unable to load/)).not.toBeInTheDocument()
  })

  test('the success banner clears on any selection change', async () => {
    server.use(
      ...lists(),
      http.post(EXTRACT, () => csvResponse()),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))
    expect(await screen.findByTestId('data-extract-success')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Start Year:'), '2020')

    await waitFor(() => {
      expect(screen.queryByTestId('data-extract-success')).not.toBeInTheDocument()
    })
    // The live region is cleared too — a stale announcement outlives a stale banner otherwise.
    expect(screen.getByRole('status', { name: 'Data extract status' })).toBeEmptyDOMElement()
  })

  test('a 500 blob body renders its detail and saves no file', async () => {
    server.use(
      ...lists(),
      http.post(
        EXTRACT,
        () =>
          new HttpResponse(
            JSON.stringify({
              status: 500,
              detail:
                'ILCR has found an unhandled error/exception. Please refer to application log files.',
            }),
            { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
          ),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    const banner = await screen.findByTestId('data-extract-message')
    expect(banner).toHaveTextContent('Cannot generate')
    expect(banner).toHaveTextContent(
      'ILCR has found an unhandled error/exception. Please refer to application log files.',
    )
    expect(vi.mocked(triggerDownload)).not.toHaveBeenCalled()
    expect(screen.queryByTestId('data-extract-success')).not.toBeInTheDocument()
    // Server text, not the client's last-resort fallback.
    expect(screen.queryByText('Unable to generate the data extract.')).not.toBeInTheDocument()
  })

  test('a 400 blob body still renders EVERY message, not just its detail', async () => {
    // The extractBlobMessages proof. extractBlobDetail would have collapsed these four refusals to
    // the single joined `detail`, silently undoing the whole point of the accumulating gate.
    server.use(
      ...lists(),
      http.post(EXTRACT, () =>
        problem400([
          { key: 'startYear', text: START_REQUIRED },
          { key: 'endYear', text: END_REQUIRED },
          { key: 'mills', text: MILLS_NOT_SELECTED },
          { key: 'schedules', text: SCHEDULES_NOT_SELECTED },
        ]),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => {
      expect(screen.getAllByTestId('data-extract-message')).toHaveLength(4)
    })
    for (const text of [START_REQUIRED, END_REQUIRED, MILLS_NOT_SELECTED, SCHEDULES_NOT_SELECTED]) {
      expect(screen.getByText(text)).toBeInTheDocument()
    }
    expect(vi.mocked(triggerDownload)).not.toHaveBeenCalled()
  })

  test('the busy lock holds until the download has been handed over', async () => {
    let release: (() => void) | undefined
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      ...lists(),
      http.post(EXTRACT, async () => {
        await held
        return csvResponse()
      }),
    )
    await renderPage()
    const user = userEvent.setup()
    const generate = screen.getByRole('button', { name: 'Generate Report' })

    await user.click(generate)

    // Every control is disabled while the request is in flight — legacy blocked the whole panel.
    await waitFor(() => {
      expect(generate).toBeDisabled()
    })
    expect(screen.getByRole('button', { name: /Clear/ })).toBeDisabled()
    expect(vi.mocked(triggerDownload)).not.toHaveBeenCalled()

    release?.()

    await waitFor(() => {
      expect(generate).toBeEnabled()
    })
    // The lock outlived the save, rather than the save outliving the lock.
    expect(vi.mocked(triggerDownload)).toHaveBeenCalledTimes(1)
  })

  test('a rejection with no problem body falls back to the client generic', async () => {
    server.use(
      ...lists(),
      http.post(EXTRACT, () => new HttpResponse(null, { status: 502 })),
    )
    await renderPage()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    expect(await screen.findByText('Unable to generate the data extract.')).toBeInTheDocument()
  })

  test('every control is locked while a submit is in flight, and released after', async () => {
    // Legacy blocked the whole panel with <p:blockUI trigger="generateBtn"> (extractData.xhtml:160).
    // Without the lock, a picker changed mid-request would clear the messages and the late 400
    // would then describe a selection no longer on screen (21.1 review P1). The `.finally` release
    // is asserted too — a stranded lock was the HIGH class of the 7.3 review.
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      ...lists(),
      http.post(EXTRACT, async () => {
        await gate
        return problem400([{ key: 'extractMillsNotSelectedMsg', text: MILLS_NOT_SELECTED }])
      }),
    )
    await renderPage()
    const user = userEvent.setup()

    const generate = screen.getByRole('button', { name: 'Generate Report' })
    const clearButton = screen.getByRole('button', { name: 'Clear' })
    const controls = () => [
      screen.getByLabelText('Start Year:'),
      screen.getByLabelText('End Year:'),
      screen.getByRole('combobox', { name: /Mills/i }),
      screen.getByRole('combobox', { name: /Schedules/i }),
    ]
    // Positive control: everything is live before the click.
    for (const control of [...controls(), generate, clearButton]) {
      expect(control).toBeEnabled()
    }

    await user.click(generate)

    await waitFor(() => {
      expect(generate).toBeDisabled()
    })
    for (const control of [...controls(), clearButton]) {
      expect(control).toBeDisabled()
    }
    expect(screen.getByRole('status', { name: 'Data extract status' })).toHaveTextContent(
      'Generating the data extract.',
    )

    release()

    expect(await screen.findByText(MILLS_NOT_SELECTED)).toBeInTheDocument()
    await waitFor(() => {
      expect(generate).toBeEnabled()
    })
    for (const control of [...controls(), clearButton]) {
      expect(control).toBeEnabled()
    }
  })

  test('a page-lifetime live region announces the submit outcome', async () => {
    server.use(
      ...lists(),
      http.post(EXTRACT, () =>
        problem400([{ key: 'extractMillsNotSelectedMsg', text: MILLS_NOT_SELECTED }]),
      ),
    )
    await renderPage()
    const user = userEvent.setup()

    // Mounted and empty BEFORE the submit — a live region has to already be in the accessibility
    // tree for a change inside it to be announced.
    const region = screen.getByRole('status', { name: 'Data extract status' })
    expect(region.textContent).toBe('')

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    await waitFor(() => {
      expect(region).toHaveTextContent(/could not be generated/i)
    })
  })
})

describe('Data Extract — list load failures', () => {
  test('a failed mill list surfaces the server detail and leaves the page usable', async () => {
    server.use(
      messageStub(),
      http.get(YEARS, () => HttpResponse.json(YEARS_THREE)),
      http.get(
        MILLS,
        () =>
          new HttpResponse(JSON.stringify({ status: 500, detail: 'Mills unavailable.' }), {
            status: 500,
            headers: { 'Content-Type': 'application/problem+json' },
          }),
      ),
    )
    render(<DataExtract />)

    expect(await screen.findByText('Mills unavailable.')).toBeInTheDocument()
    // The year pickers still loaded, so the page is not blanked by one failed list.
    await waitFor(() => {
      expect(screen.getByLabelText('Start Year:')).toHaveValue('2021')
    })
  })

  test('a failed reporting-years list surfaces the server detail and leaves the mills usable', async () => {
    // The twin of the mills case above; until this test only one of the two load paths was covered
    // (21.1 review P9).
    server.use(
      messageStub(),
      http.get(MILLS, () => HttpResponse.json(MILLS_THREE)),
      http.get(
        YEARS,
        () =>
          new HttpResponse(JSON.stringify({ status: 500, detail: 'Years unavailable.' }), {
            status: 500,
            headers: { 'Content-Type': 'application/problem+json' },
          }),
      ),
    )
    render(<DataExtract />)
    const user = userEvent.setup()

    expect(await screen.findByText('Years unavailable.')).toBeInTheDocument()
    // Both year pickers stay on their placeholder — no year is invented.
    expect(screen.getByLabelText('Start Year:')).toHaveValue('')
    expect(screen.getByLabelText('End Year:')).toHaveValue('')
    // The other list still loaded.
    const listbox = await openPicker(user, /Mills/i)
    expect(within(listbox).getByRole('option', { name: '514 - AAA Milling' })).toBeInTheDocument()
  })

  test('a failure with no problem body falls back to the client generic for that list', async () => {
    server.use(
      messageStub(),
      http.get(MILLS, () => HttpResponse.json(MILLS_THREE)),
      http.get(YEARS, () => new HttpResponse(null, { status: 503 })),
    )
    render(<DataExtract />)

    expect(await screen.findByText('Unable to load the reporting years.')).toBeInTheDocument()
  })

  test('both lists failing with the SAME detail render ONE banner, not two', async () => {
    // One gateway answering for both lookups produces identical problem bodies; the banners are
    // keyed by their text, so the second must not duplicate the first (21.1 review P4).
    const outage = () =>
      new HttpResponse(JSON.stringify({ status: 502, detail: 'Upstream unavailable.' }), {
        status: 502,
        headers: { 'Content-Type': 'application/problem+json' },
      })
    server.use(messageStub(), http.get(MILLS, outage), http.get(YEARS, outage))
    render(<DataExtract />)

    expect(await screen.findByText('Upstream unavailable.')).toBeInTheDocument()
    // Settle: give the second failure every chance to land before counting.
    await waitFor(() => {
      expect(screen.getAllByText('Upstream unavailable.')).toHaveLength(1)
    })
    expect(screen.getAllByText('Upstream unavailable.')).toHaveLength(1)
  })

  test('no opened reporting year leaves both year pickers empty rather than defaulted', async () => {
    server.use(...lists(MILLS_THREE, []))
    render(<DataExtract />)

    const start = await screen.findByLabelText('Start Year:')
    await waitFor(() => {
      expect(start).toHaveValue('')
    })
    expect(within(start).getAllByRole('option')).toHaveLength(1)
  })
})
