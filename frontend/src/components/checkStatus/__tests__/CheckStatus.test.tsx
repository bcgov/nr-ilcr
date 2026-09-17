import { afterEach, describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getDefaultNormalizer } from '@testing-library/react'
import {
  declaredRole,
  render,
  renderAsAdmin,
  renderAsSubmitter,
  screen,
  userEvent,
  waitFor,
  within,
} from '@/test-utils'
import { server } from '@/test-setup'
import apiService from '@/service/api-service'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'
import { ILCR_ROLES, MOCK_USER_STORAGE_KEY } from '@/context/auth/mockUsers'
import { ERR_MILL_YEAR_NOT_SELECTED } from '@/components/core/ScheduleLoadState'
import CheckStatus, {
  CONFIRM_SUBMIT_1_TO_10,
  HINT_NOT_ADMIN,
  HINT_NOT_DRAFT,
  HINT_NOT_DRAFT_11,
  HINT_NOT_SUBMITTED,
  HINT_NOT_SUBMITTED_11,
  HINT_NOT_SUBMITTER,
  SUBMIT_FAILED,
} from '../index'
import { LOAD_FAILED } from '../useCheckStatusSweep'
import { SCHEDULE_TITLES } from '../verdicts'
import {
  MET_TEXT,
  SCH1_CROSS_CHECK_TEXT,
  SCH1_WARNING_TEXT,
  SCH11_CHECKED_TEXT,
  SCH11_ERROR_TEXT,
  SCH4_EMPTY_LANDING_MET_TEXT,
  SCH5_BLANK_NAME_TEXT,
  SCH5_MET_CAMP_TEXT,
  SCH6_ERROR_TEXT,
  SCH7A_BRIDGE_MET_TEXT,
  SCH7B_ERROR_TEXT,
  SCH8_HARVESTED_TEXT,
  SCH8_NO_SAMPLE_TEXT,
  SCH8_SKIDDING_TEXT,
  SCH10_ROAD_ISSUE_TEXT,
  schedule1Fail,
  schedule1FailWithWarning,
  schedule10Issues,
  schedule11Fail,
  schedule2Issues,
  schedule2Met,
  schedule4Issues,
  schedule5Issues,
  schedule6Issues,
  schedule7aFail,
  schedule7aMet,
  schedule7bFail,
  schedule8Issues,
  schedule8NoSamples,
  sweep,
} from './fixtures'

const API = 'http://localhost:3000/api'
const SWEEP_URL = `${API}/v1/check-status`
const MILL_CONTEXT = `${API}/v1/mill-context`

const NOT_FOUND = 'One or more of the schedules for the report have not been found.'
const NOT_ACTIVE =
  'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.'
const FORBIDDEN = 'You do not have permission to perform this action.'

const TITLES_1_TO_10 = [
  SCHEDULE_TITLES['1'],
  SCHEDULE_TITLES['2'],
  SCHEDULE_TITLES['3'],
  SCHEDULE_TITLES['4'],
  SCHEDULE_TITLES['5'],
  SCHEDULE_TITLES['6'],
  SCHEDULE_TITLES['7A'],
  SCHEDULE_TITLES['7B'],
  SCHEDULE_TITLES['8'],
  SCHEDULE_TITLES['9'],
  SCHEDULE_TITLES['10'],
]

const problem = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

/** A `/mill-context` body carrying BOTH track statuses (the default handler omits Schedule 11's). */
const millContextWithBothTracks = (code1To10: string, code11: string) =>
  http.get(MILL_CONTEXT, ({ request }) => {
    const params = new URL(request.url).searchParams
    const millId = Number(params.get('millId'))
    const reportYear = Number(params.get('year'))
    const describe = (code: string) =>
      code === 'D' ? 'Draft' : code === 'S' ? 'Submitted' : 'Verified'
    return HttpResponse.json({
      millId,
      millNumber: String(millId),
      millName: 'Test Mill',
      reportYear,
      schedules1To10Status: {
        code: code1To10,
        description: describe(code1To10),
        date: '2017-01-01',
      },
      schedule11Status: { code: code11, description: describe(code11), date: '2017-02-02' },
      millViewable: true,
    })
  })

/** Keeps the server's runs of spaces intact, so a multi-space text is matched byte for byte. */
const verbatim = { normalizer: getDefaultNormalizer({ collapseWhitespace: false }) }

const asSubmitter = () => window.localStorage.setItem(MOCK_USER_STORAGE_KEY, 'submitter')

const region1To10 = () => screen.getByRole('region', { name: 'Schedules 1–10' })
const region11 = () => screen.getByRole('region', { name: 'Schedule 11' })
const submitButtons = () => screen.getAllByRole('button', { name: 'Submit' })
const verifiedButtons = () => screen.getAllByRole('button', { name: 'Verified' })
/** The two Schedules 1–10 bars (above and below that region) — everything outside the Schedule 11 region. */
const submitButtons1To10 = () => submitButtons().filter((b) => !region11().contains(b))
const verifiedButtons1To10 = () => verifiedButtons().filter((b) => !region11().contains(b))

/** The accordion item (its `<li>`) whose heading button carries the given legacy title. */
const item = (title: string): HTMLElement => {
  const li = screen.getByRole('button', { name: title }).closest('li')
  if (!li) throw new Error(`no accordion item for ${title}`)
  return li
}

/** The result lines inside one item — every InlineNotification renders with role=status. */
const linesIn = (element: HTMLElement) => within(element).queryAllByRole('status')

/** The sweep requests seen by a spy on the shared axios instance (the tombstone's /mill-context read shares it). */
const sweepCalls = (spy: { mock: { calls: unknown[][] } }) =>
  spy.mock.calls.filter((call) => String(call[0]).startsWith('/v1/check-status'))

const hintFor = (button: HTMLElement): HTMLElement | null => {
  const id = button.getAttribute('aria-describedby')
  return id ? document.getElementById(id) : null
}

/** The page plus a button that flips the working context to mill 999 / 2020 (the Schedule 6 suite's mechanic). */
const ContextSwitchHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <CheckStatus />
    </>
  )
}

// The submitter tests seed the mock user; nothing in the harness clears it.
afterEach(() => {
  window.localStorage.clear()
  vi.restoreAllMocks()
})

describe('Check Status page (Story 15.2)', () => {
  // ---- UC-CHK-001 S01 / UC-CHK-009 S01 ---------------------------------------------------------

  test('S01 (submitter): all twelve met — twelve verbatim met lines in legacy order, both track lines, Submit enabled', async () => {
    asSubmitter()
    let calls = 0
    server.use(
      millContextWithBothTracks('D', 'D'),
      http.get(SWEEP_URL, ({ request }) => {
        calls += 1
        const params = new URL(request.url).searchParams
        expect(params.get('millId')).toBe('13050')
        expect(params.get('year')).toBe('2017')
        return HttpResponse.json(sweep({ statusCode11: 'D', canSubmit1To10: true }))
      }),
    )
    render(<CheckStatus />)

    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    expect(calls).toBe(1)

    // One heading, the tombstone's; both track lines from /mill-context.
    expect(screen.getByRole('heading', { level: 1, name: 'Check Status' })).toBeInTheDocument()
    expect(
      await screen.findByText('Sch 1-10 - Status: Draft - Date: 2017-01-01'),
    ).toBeInTheDocument()
    expect(await screen.findByText('Sch 11 - Status: Draft - Date: 2017-02-02')).toBeInTheDocument()

    // Sections in the order received, titled verbatim; Schedule 11 in its own region.
    const first = within(region1To10())
      .getAllByRole('button')
      .filter((b) => b.getAttribute('aria-expanded') !== null)
      .map((b) => b.textContent)
    expect(first).toEqual(TITLES_1_TO_10)
    const eleven = within(region11())
      .getAllByRole('button')
      .filter((b) => b.getAttribute('aria-expanded') !== null)
      .map((b) => b.textContent)
    expect(eleven).toEqual([SCHEDULE_TITLES['11']])

    // Every one of the twelve is open (D3: legacy Schedule 11 was open too).
    for (const title of [...TITLES_1_TO_10, SCHEDULE_TITLES['11']]) {
      expect(screen.getByRole('button', { name: title })).toHaveAttribute('aria-expanded', 'true')
    }

    // Schedule 11's "Status has been checked" is not this page's to show (deviation L).
    expect(screen.queryByText(SCH11_CHECKED_TEXT)).not.toBeInTheDocument()

    // D1: submitter with both tracks in Draft → all three Submits enabled, no hint wired. Legacy's
    // three rows: two for Schedules 1–10 (above/below that region), one inside the Schedule 11 tab.
    const buttons = submitButtons()
    expect(buttons).toHaveLength(3)
    for (const button of buttons) {
      expect(button).toBeEnabled()
      expect(button).not.toHaveAttribute('aria-describedby')
    }
    expect(submitButtons1To10()).toHaveLength(2)
    expect(within(region1To10()).queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument()
    expect(
      within(item(SCHEDULE_TITLES['11'])).getByRole('button', { name: 'Submit' }),
    ).toBeEnabled()
    // Verified beside every Submit, greyed for the licensee regardless of status (legacy
    // canUserVerifyReport: Submitted AND not the licensee).
    const verified = verifiedButtons()
    expect(verified).toHaveLength(3)
    for (const button of verified) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_ADMIN)
    }
  })

  test('CHK-009 S01 (admin, the default mock user): the same twelve lines render; Submit is disabled with a hint', async () => {
    server.use(http.get(SWEEP_URL, () => HttpResponse.json(sweep())))
    render(<CheckStatus />)

    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    expect(submitButtons()).toHaveLength(3)
    for (const button of submitButtons()) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_SUBMITTER)
    }
    // Admin, but nothing is Submitted → Verified greyed with the status hint, per track.
    for (const button of verifiedButtons1To10()) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_SUBMITTED)
    }
    const verified11 = within(item(SCHEDULE_TITLES['11'])).getByRole('button', { name: 'Verified' })
    expect(verified11).toBeDisabled()
    expect(hintFor(verified11)).toHaveTextContent(HINT_NOT_SUBMITTED_11)
  })

  // ---- S02: the correct-and-re-check loop is a remount ----------------------------------------

  test('S02 (submitter) / CHK-009 S02: a second mount re-issues the sweep and renders the changed verdict', async () => {
    asSubmitter()
    let calls = 0
    server.use(
      http.get(SWEEP_URL, () => {
        calls += 1
        return HttpResponse.json(
          sweep({
            overrides: [{ schedule: '7B', requirementsMet: false, verdict: schedule7bFail }],
          }),
        )
      }),
    )
    const first = render(<CheckStatus />)
    await screen.findByText(SCH7B_ERROR_TEXT)
    expect(calls).toBe(1)
    first.unmount()

    let secondCalls = 0
    server.use(
      http.get(SWEEP_URL, () => {
        secondCalls += 1
        return HttpResponse.json(sweep())
      }),
    )
    render(<CheckStatus />)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    expect(calls).toBe(1)
    expect(secondCalls).toBe(1) // the second mount issued its own GET
    expect(screen.queryByText(SCH7B_ERROR_TEXT)).not.toBeInTheDocument()
  })

  // ---- D7: nothing on this page navigates ------------------------------------------------------

  test('D7 no-link pin: the results regions contain no links; the accordion buttons are the positive control', async () => {
    server.use(http.get(SWEEP_URL, () => HttpResponse.json(sweep())))
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    expect(within(region1To10()).queryAllByRole('link')).toEqual([])
    expect(within(region11()).queryAllByRole('link')).toEqual([])
    expect(within(region1To10()).getAllByRole('button').length).toBeGreaterThanOrEqual(11)
  })

  // ---- S05: no context → no request -----------------------------------------------------------

  test('S05: with no mill/year the page fires no request and renders the context guard without Submit', async () => {
    let calls = 0
    server.use(
      http.get(SWEEP_URL, () => {
        calls += 1
        return HttpResponse.json(sweep())
      }),
    )
    const getSpy = vi.spyOn(apiService.getAxiosInstance(), 'get')
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <CheckStatus />
      </MillYearProvider>,
    )

    expect(await screen.findByText(ERR_MILL_YEAR_NOT_SELECTED)).toBeInTheDocument()
    expect(calls).toBe(0)
    expect(getSpy).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Schedules 1–10' })).not.toBeInTheDocument()
  })

  // ---- S06 and the other error states: verbatim detail, nothing else ----------------------------

  test.each([
    ['S06 404', 404, NOT_FOUND, 'Unable to load Check Status'],
    ['409 closed mill', 409, NOT_ACTIVE, 'Mill not active for Reporting Year'],
    ['403 out of scope', 403, FORBIDDEN, 'Unable to load Check Status'],
    ['500 with a detail', 500, 'Schedule 5 could not be evaluated.', 'Unable to load Check Status'],
  ])(
    '%s: the ProblemDetail detail renders verbatim; no sections and no action bar',
    async (_label, status, detail, title) => {
      server.use(http.get(SWEEP_URL, () => problem(status, detail)))
      render(<CheckStatus />)

      expect(await screen.findByText(detail)).toBeInTheDocument()
      // The closed-mill 409 takes the shared renderer's own titled state (PR #464); the rest are load failures.
      expect(screen.getByText(title)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Schedules 1–10' })).not.toBeInTheDocument()
      expect(screen.queryByText(MET_TEXT)).not.toBeInTheDocument()
    },
  )

  test('network failure: the client fallback renders; no sections and no action bar', async () => {
    server.use(http.get(SWEEP_URL, () => HttpResponse.error()))
    render(<CheckStatus />)

    expect(await screen.findByText(LOAD_FAILED)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument()
    expect(screen.queryByText(MET_TEXT)).not.toBeInTheDocument()
  })

  test('a successful response for a different mill/year is rejected as a controlled load error', async () => {
    server.use(http.get(SWEEP_URL, () => HttpResponse.json(sweep({ millId: 999, year: 2020 }))))
    render(<CheckStatus />)

    expect(await screen.findByText(LOAD_FAILED)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Schedules 1–10' })).not.toBeInTheDocument()
    expect(screen.queryByText(MET_TEXT)).not.toBeInTheDocument()
  })

  test('loading: the shared loading state shows until the sweep resolves', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get(SWEEP_URL, async () => {
        await held
        return HttpResponse.json(sweep())
      }),
    )
    render(<CheckStatus />)
    expect(await screen.findByRole('status', { name: 'Loading Check Status' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument()
    release()
    await screen.findAllByText(MET_TEXT)
    expect(screen.queryByRole('status', { name: 'Loading Check Status' })).not.toBeInTheDocument()
  })

  // ---- S09 / S19: Schedule 1's cross-check errors and warnings ---------------------------------

  test('S09 (submitter) / CHK-009 S09 / S19: Schedule 1 renders its cross-check error under its own section, no met line, and a warning as kind=warning', async () => {
    asSubmitter()
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [
              { schedule: '1', requirementsMet: false, verdict: schedule1FailWithWarning },
            ],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(SCH1_CROSS_CHECK_TEXT)

    const schedule1 = item(SCHEDULE_TITLES['1'])
    expect(within(schedule1).getByText(SCH1_CROSS_CHECK_TEXT)).toBeInTheDocument()
    expect(
      within(schedule1).getByText('Standing Tree to Loaded Truck - Volume m³: Value Required'),
    ).toBeInTheDocument()
    expect(within(schedule1).queryByText(MET_TEXT)).not.toBeInTheDocument()
    expect(linesIn(schedule1)).toHaveLength(24) // 23 errors + 1 warning
    const warning = within(schedule1)
      .getByText(SCH1_WARNING_TEXT)
      .closest('.cds--inline-notification')
    expect(warning).toHaveClass('cds--inline-notification--warning')
    const error = within(schedule1)
      .getByText(SCH1_CROSS_CHECK_TEXT)
      .closest('.cds--inline-notification')
    expect(error).toHaveClass('cds--inline-notification--error')
    if (!error || !warning) throw new Error('expected both an error and a warning notification')
    expect(error.compareDocumentPosition(warning)).toBe(window.Node.DOCUMENT_POSITION_FOLLOWING)
    // The other eleven still show their met line — the positive control for "under its own section".
    expect(screen.getAllByText(MET_TEXT)).toHaveLength(11)
  })

  // ---- S10 / S18: Schedule 8 pages and samples ------------------------------------------------

  test('S10 (submitter) / CHK-009 S10 / S18: Schedule 8 renders the page issues and both samples’ issues, attributed positionally, verbatim', async () => {
    asSubmitter()
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [{ schedule: '8', requirementsMet: false, verdict: schedule8Issues }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(`Page # 1 - Sample # 2 - Actual Harvested: ${SCH8_HARVESTED_TEXT}`)

    const schedule8 = item(SCHEDULE_TITLES['8'])
    expect(linesIn(schedule8)).toHaveLength(13)
    expect(within(schedule8).getByText('Page # 1 - Division: Value Required')).toBeInTheDocument()
    expect(
      within(schedule8).getByText('Page # 1 - Sample # 1 - Cut Block: Value Required'),
    ).toBeInTheDocument()
    expect(
      within(schedule8).getByText(
        `Page # 1 - Sample # 1 - Skidding/Yarding: ${SCH8_SKIDDING_TEXT}`,
      ),
    ).toBeInTheDocument()
    expect(within(schedule8).queryByText(MET_TEXT)).not.toBeInTheDocument()
    expect(schedule8.textContent).not.toMatch(/897[234]/)
  })

  test('S18: a Schedule 8 page with no samples renders its page-level line only', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [{ schedule: '8', requirementsMet: false, verdict: schedule8NoSamples }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(`Page # 1 - Sample: ${SCH8_NO_SAMPLE_TEXT}`)
    expect(linesIn(item(SCHEDULE_TITLES['8']))).toHaveLength(1)
  })

  // ---- S17: 7B renders its OWN verdict --------------------------------------------------------

  test('S17: 7A met and 7B failing — the 7B section shows its error and no met line; 7A shows met', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [
              { schedule: '7A', requirementsMet: true, verdict: schedule7aMet },
              { schedule: '7B', requirementsMet: false, verdict: schedule7bFail },
            ],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(SCH7B_ERROR_TEXT)

    const sevenB = item(SCHEDULE_TITLES['7B'])
    expect(within(sevenB).getByText(SCH7B_ERROR_TEXT)).toBeInTheDocument()
    expect(within(sevenB).queryByText(MET_TEXT)).not.toBeInTheDocument()
    expect(linesIn(sevenB)).toHaveLength(3)
    const sevenA = item(SCHEDULE_TITLES['7A'])
    expect(within(sevenA).getByText(MET_TEXT)).toBeInTheDocument()
    expect(within(sevenA).queryByText(SCH7B_ERROR_TEXT)).not.toBeInTheDocument()
  })

  test('7A failing: its errors render; its bridgeMessages do not (D8)', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [{ schedule: '7A', requirementsMet: false, verdict: schedule7aFail }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText('Bridge Report Id : 3 - Other Costs : Value Required')
    expect(screen.queryByText(SCH7A_BRIDGE_MET_TEXT)).not.toBeInTheDocument()
    expect(linesIn(item(SCHEDULE_TITLES['7A']))).toHaveLength(2)
  })

  // ---- The other family-B verdicts, one test each ---------------------------------------------

  test('Schedule 5: eight failing-camp lines verbatim (the three-space camp included); the met camp’s line is absent', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [{ schedule: '5', requirementsMet: false, verdict: schedule5Issues }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(SCH5_BLANK_NAME_TEXT, verbatim)

    const schedule5 = item(SCHEDULE_TITLES['5'])
    expect(linesIn(schedule5)).toHaveLength(8)
    expect(within(schedule5).queryByText(SCH5_MET_CAMP_TEXT)).not.toBeInTheDocument()
    expect(within(schedule5).queryByText(MET_TEXT)).not.toBeInTheDocument()
  })

  test('Schedule 4: the failing location names its field from the label table; the met location renders nothing', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [{ schedule: '4', requirementsMet: false, verdict: schedule4Issues }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText('Harbour Dump - Rail Haul - Cost $: Value Required')

    const schedule4 = item(SCHEDULE_TITLES['4'])
    expect(linesIn(schedule4)).toHaveLength(1)
    expect(within(schedule4).queryByText(SCH4_EMPTY_LANDING_MET_TEXT)).not.toBeInTheDocument()
  })

  test('Schedules 6, 10, 2 (MET and hand-composed ISSUES) render their lines verbatim', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [
              { schedule: '2', requirementsMet: false, verdict: schedule2Issues },
              { schedule: '6', requirementsMet: false, verdict: schedule6Issues },
              { schedule: '10', requirementsMet: false, verdict: schedule10Issues },
            ],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(SCH6_ERROR_TEXT)

    expect(linesIn(item(SCHEDULE_TITLES['6']))).toHaveLength(1)
    expect(
      within(item(SCHEDULE_TITLES['10'])).getByText(
        `Road #1, Blank Material Road: ${SCH10_ROAD_ISSUE_TEXT}`,
      ),
    ).toBeInTheDocument()
    expect(linesIn(item(SCHEDULE_TITLES['10']))).toHaveLength(4)
    const schedule2 = item(SCHEDULE_TITLES['2'])
    expect(
      within(schedule2).getByText('Purchased/Private Log Costs - Cost $: Value Required'),
    ).toBeInTheDocument()
    expect(within(schedule2).queryByText(MET_TEXT)).not.toBeInTheDocument()
    // Nine still met: the positive control.
    expect(screen.getAllByText(MET_TEXT)).toHaveLength(9)
  })

  test('Schedule 2 MET renders the single met line from messages[0]', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({ overrides: [{ schedule: '2', requirementsMet: true, verdict: schedule2Met }] }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)
    expect(linesIn(item(SCHEDULE_TITLES['2']))).toHaveLength(1)
  })

  test('Schedule 11 failing: its two lines render in its own region', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [{ schedule: '11', requirementsMet: false, verdict: schedule11Fail }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(SCH11_ERROR_TEXT, verbatim)
    expect(linesIn(item(SCHEDULE_TITLES['11']))).toHaveLength(2)
    expect(within(region11()).queryByText(SCH11_CHECKED_TEXT)).not.toBeInTheDocument()
  })

  // ---- Degenerate payloads: nothing invented -----------------------------------------------------

  test('degenerate payloads: a verdict with nothing to say falls through to the shared "Status checked" line, as on every schedule page', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            overrides: [
              {
                schedule: '9',
                requirementsMet: false,
                verdict: { requirementsMet: false, errors: [], requirementsMetMessage: null },
              },
              {
                schedule: '6',
                requirementsMet: false,
                verdict: {
                  outcome: 'ISSUES',
                  messages: [],
                  records: [{ recordId: 1, rowCounter: 1, met: true, issues: [] }],
                },
              },
            ],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    for (const code of ['9', '6'] as const) {
      const lines = linesIn(item(SCHEDULE_TITLES[code]))
      expect(lines).toHaveLength(1)
      expect(lines[0]).toHaveTextContent('Status checked')
      expect(lines[0]).toHaveTextContent('This schedule has outstanding requirements.')
      expect(lines[0]).toHaveClass('cds--inline-notification--warning')
    }
    expect(screen.getAllByText(MET_TEXT)).toHaveLength(10) // positive control
  })

  // ---- S11 / S14 / S15: the Submit gate --------------------------------------------------------

  test('S11: submitter at Submitted → Submit disabled in both 1–10 bars, hint in the DOM and resolvable', async () => {
    asSubmitter()
    server.use(http.get(SWEEP_URL, () => HttpResponse.json(sweep({ statusCode1To10: 'S' }))))
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    const buttons = submitButtons1To10()
    expect(buttons).toHaveLength(2)
    for (const button of buttons) {
      expect(button).toBeDisabled()
      const hint = hintFor(button)
      expect(hint).toHaveTextContent(HINT_NOT_DRAFT)
      expect(hint).toHaveClass('cds--visually-hidden')
    }
    // The two hints are distinct elements — no duplicate id.
    expect(hintFor(buttons[0])).not.toBe(hintFor(buttons[1]))
    // Schedule 11's own Submit reads ITS track (status absent here) and its own hint.
    const submit11 = within(item(SCHEDULE_TITLES['11'])).getByRole('button', { name: 'Submit' })
    expect(submit11).toBeDisabled()
    expect(hintFor(submit11)).toHaveTextContent(HINT_NOT_DRAFT_11)
  })

  test('S11: submitter with the 1–10 statusCode absent → Submit disabled', async () => {
    asSubmitter()
    server.use(http.get(SWEEP_URL, () => HttpResponse.json(sweep({ statusCode1To10: null }))))
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)
    for (const button of submitButtons1To10()) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_DRAFT)
    }
  })

  test('S14: admin at Draft → Submit disabled in both bars with the role hint; validity plays no part', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            statusCode1To10: 'D',
            overrides: [{ schedule: '1', requirementsMet: false, verdict: schedule1Fail }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(SCH1_CROSS_CHECK_TEXT)
    const buttons = submitButtons()
    expect(buttons).toHaveLength(3)
    for (const button of buttons) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_SUBMITTER)
    }
  })

  test('S01 rider: a submitter at Draft is enabled even when schedules fail — the ten-schedule gate is the click (15.3)', async () => {
    asSubmitter()
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(
          sweep({
            canSubmit1To10: true,
            overrides: [{ schedule: '1', requirementsMet: false, verdict: schedule1Fail }],
          }),
        ),
      ),
    )
    render(<CheckStatus />)
    await screen.findByText(SCH1_CROSS_CHECK_TEXT)
    expect(submitButtons1To10()).toHaveLength(2)
    for (const button of submitButtons1To10()) {
      expect(button).toBeEnabled()
    }
  })

  test('S15: 1–10 Submitted and Schedule 11 Draft → the 1–10 Submits are disabled while Schedule 11’s own Submit is enabled; each track reads only its own status', async () => {
    asSubmitter()
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' })),
      ),
    )
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    expect(
      await screen.findByText('Sch 1-10 - Status: Submitted - Date: 2017-01-01'),
    ).toBeInTheDocument()
    expect(await screen.findByText('Sch 11 - Status: Draft - Date: 2017-02-02')).toBeInTheDocument()
    for (const button of submitButtons1To10()) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_DRAFT)
    }
    const eleven = item(SCHEDULE_TITLES['11'])
    expect(within(eleven).getByText(MET_TEXT)).toBeInTheDocument()
    // Legacy placed the Schedule 11 row INSIDE its tab, under the result (checkStatus.xhtml:152-183).
    const submit11 = within(eleven).getByRole('button', { name: 'Submit' })
    expect(submit11).toBeEnabled()
    expect(submit11).not.toHaveAttribute('aria-describedby')
    // Verified is greyed for the licensee on both tracks.
    for (const button of verifiedButtons()) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_ADMIN)
    }
    // The admin reversals are not rendered for the licensee at any status (legacy `rendered=`).
    for (const label of ['Set to Draft', 'Set to Submit']) {
      expect(screen.queryByRole('button', { name: label })).not.toBeInTheDocument()
    }
    expect(submitButtons()).toHaveLength(3) // positive control
  })

  test('Verified gate: admin with Schedules 1–10 Submitted and Schedule 11 Draft → only the 1–10 Verified pair is enabled', async () => {
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' })),
      ),
    )
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    // 1–10 Submitted → its two Verified enabled, no hint.
    const verified1To10 = verifiedButtons1To10()
    expect(verified1To10).toHaveLength(2)
    for (const button of verified1To10) {
      expect(button).toBeEnabled()
      expect(button).not.toHaveAttribute('aria-describedby')
    }
    // Schedule 11 still Draft → its Verified greyed with its own status hint.
    const verified11 = within(item(SCHEDULE_TITLES['11'])).getByRole('button', { name: 'Verified' })
    expect(verified11).toBeDisabled()
    expect(hintFor(verified11)).toHaveTextContent(HINT_NOT_SUBMITTED_11)
    // An admin never submits, whatever the status.
    for (const button of submitButtons()) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_SUBMITTER)
    }
    // Every hint id is unique across the six buttons.
    const ids = [...submitButtons(), ...verifiedButtons()]
      .map((b) => b.getAttribute('aria-describedby'))
      .filter((id): id is string => id !== null)
    expect(new Set(ids).size).toBe(ids.length)
  })

  test('admin reversals: Set to Draft renders only while a track is Submitted, Set to Submit only while Verified, each on its own track, in legacy button order', async () => {
    server.use(
      millContextWithBothTracks('S', 'V'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'V' })),
      ),
    )
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    // 1–10 Submitted → both 1–10 bars: Set to Draft (enabled), Submit (greyed), Verified (enabled).
    const setToDraft = screen.getAllByRole('button', { name: 'Set to Draft' })
    expect(setToDraft).toHaveLength(2)
    for (const button of setToDraft) {
      expect(button).toBeEnabled()
      expect(region11().contains(button)).toBe(false)
    }
    expect(within(region1To10()).queryByRole('button', { name: 'Set to Submit' })).toBeNull()
    // Schedule 11 Verified → its bar: Set to Submit (enabled), Submit (greyed), Verified (greyed).
    const bar11 = item(SCHEDULE_TITLES['11'])
    const setToSubmit = within(bar11).getByRole('button', { name: 'Set to Submit' })
    expect(setToSubmit).toBeEnabled()
    expect(within(bar11).queryByRole('button', { name: 'Set to Draft' })).toBeNull()
    expect(screen.getAllByRole('button', { name: 'Set to Submit' })).toHaveLength(1)
    expect(within(bar11).getByRole('button', { name: 'Submit' })).toBeDisabled()
    expect(within(bar11).getByRole('button', { name: 'Verified' })).toBeDisabled()
    // Legacy order inside a row: the reversal, then Submit, then Verified (checkStatus.xhtml:37-62).
    const rowButtons = within(bar11)
      .getAllByRole('button')
      .filter((b) => b.closest('.check-status__actions') !== null)
      .map((b) => b.textContent)
    expect(rowButtons).toEqual(['Set to Submit', 'Submit', 'Verified'])
  })

  test('admin reversals: neither renders for an admin while a track is Draft, nor for the licensee at any status', async () => {
    server.use(
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'D', statusCode11: 'D' })),
      ),
    )
    render(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)
    for (const label of ['Set to Draft', 'Set to Submit']) {
      expect(screen.queryByRole('button', { name: label })).not.toBeInTheDocument()
    }
    expect(submitButtons()).toHaveLength(3) // positive control: the bars are there
  })

  // ---- Stale context ---------------------------------------------------------------------------

  test('stale-context guard: switching mill/year aborts the in-flight sweep, shows loading on the same render, and nothing of the old request renders', async () => {
    let releaseA!: () => void
    const heldA = new Promise<void>((resolve) => {
      releaseA = resolve
    })
    let releaseB!: () => void
    const heldB = new Promise<void>((resolve) => {
      releaseB = resolve
    })
    server.use(
      http.get(SWEEP_URL, async ({ request }) => {
        const params = new URL(request.url).searchParams
        if (params.get('millId') === '13050') {
          await heldA
          return HttpResponse.json(
            sweep({
              millId: 13050,
              year: 2017,
              overrides: [{ schedule: '1', requirementsMet: false, verdict: schedule1Fail }],
            }),
          )
        }
        await heldB
        return HttpResponse.json(sweep({ millId: 999, year: 2020 }))
      }),
    )
    const getSpy = vi.spyOn(apiService.getAxiosInstance(), 'get')
    const user = userEvent.setup()
    render(<ContextSwitchHarness />)
    expect(await screen.findByRole('status', { name: 'Loading Check Status' })).toBeInTheDocument()
    await waitFor(() => expect(sweepCalls(getSpy)).toHaveLength(1))
    const signalA = (sweepCalls(getSpy)[0][1] as { signal: AbortSignal }).signal
    expect(signalA.aborted).toBe(false)

    await user.click(screen.getByRole('button', { name: 'change' }))
    // Both requests are held, so this IS the frame right after the switch: loading, not blank.
    expect(screen.getByRole('status', { name: 'Loading Check Status' })).toBeInTheDocument()
    // The request for mill A was aborted by the effect cleanup the moment the context changed.
    expect(signalA.aborted).toBe(true)
    await waitFor(() => expect(sweepCalls(getSpy)).toHaveLength(2))

    releaseA()
    releaseB()
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    expect(screen.queryByText(SCH1_CROSS_CHECK_TEXT)).not.toBeInTheDocument()
  })

  test("stale-context guard: the aborted request's rejection is never shown as a load failure", async () => {
    let releaseA!: () => void
    const heldA = new Promise<void>((resolve) => {
      releaseA = resolve
    })
    server.use(
      http.get(SWEEP_URL, async ({ request }) => {
        const params = new URL(request.url).searchParams
        if (params.get('millId') === '13050') {
          await heldA
          return HttpResponse.error()
        }
        return HttpResponse.json(sweep({ millId: 999, year: 2020 }))
      }),
    )
    const getSpy = vi.spyOn(apiService.getAxiosInstance(), 'get')
    const user = userEvent.setup()
    render(<ContextSwitchHarness />)
    expect(await screen.findByRole('status', { name: 'Loading Check Status' })).toBeInTheDocument()
    await waitFor(() => expect(sweepCalls(getSpy)).toHaveLength(1))

    await user.click(screen.getByRole('button', { name: 'change' }))
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    // Axios rejects an aborted request with a CanceledError; that rejection is not this page's error.
    expect((sweepCalls(getSpy)[0][1] as { signal: AbortSignal }).signal.aborted).toBe(true)
    releaseA()
    await waitFor(() => expect(screen.getAllByText(MET_TEXT)).toHaveLength(12))
    expect(screen.queryByText(LOAD_FAILED)).not.toBeInTheDocument()
    expect(screen.queryByText('Unable to load Check Status')).not.toBeInTheDocument()
  })

  test('a mill/year change re-issues the sweep for the new context', async () => {
    const seen: string[] = []
    server.use(
      http.get(SWEEP_URL, ({ request }) => {
        const params = new URL(request.url).searchParams
        seen.push(`${params.get('millId')}/${params.get('year')}`)
        return HttpResponse.json(
          sweep({ millId: Number(params.get('millId')), year: Number(params.get('year')) }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<ContextSwitchHarness />)
    await screen.findAllByText(MET_TEXT)
    await user.click(screen.getByRole('button', { name: 'change' }))
    await waitFor(() => expect(seen).toEqual(['13050/2017', '999/2020']))
  })
})

// =================================================================================================
// Story 15.4 — Submit Schedules 1–10 behind the confirmation.
// =================================================================================================

const SUBMIT_URL = `${SWEEP_URL}/submit`

// The wire texts, copied from the backend's own constants (CheckStatusSubmitIT.java:48-54,
// CheckStatusContextGuardIT.java:37-45) — not from the story file. ERR_001 keeps its trailing space.
const SUBMITTED = 'Schedules 1-10 are successfully submitted.'
const NOT_SUBMITTED =
  'The report cannot be submitted. One or more of the Schedules have not passed validation. Please review and correct any errors.'
const SUBMISSION_ERROR =
  'An error has been found submitting schedules. The error details have been logged. Please contact ILCR application support.'
const ERR_001 = 'Please Select Mill and Reporting Year in the Home Page. '

const LOADING = { name: 'Loading Check Status' }
const LOAD_TITLE = 'Unable to load Check Status'
const DRAFT_LINE = 'Sch 1-10 - Status: Draft - Date: 2017-01-01'
const SUBMITTED_LINE = 'Sch 1-10 - Status: Submitted - Date: 2017-01-01'
const MILL_LINE = 'Mill: 13050 Test Mill - Year: 2017'
const SCH11_LINE = 'Sch 11 - Status: Draft - Date: 2017-02-02'

/** Neither trims nor collapses, so a trailing space is matched byte for byte. */
const byteExact = { normalizer: getDefaultNormalizer({ trim: false, collapseWhitespace: false }) }

type Answer = () => Response
const json =
  (body: unknown): Answer =>
  () =>
    HttpResponse.json(body)

const submitOk: Answer = () =>
  HttpResponse.json({ message: { key: 'sch1-10SubmittedMsg', text: SUBMITTED } })

/** Boot's container-error body — a 401 is NOT problem+json (SecurityConfiguration.java:68-72). */
const unauthorized: Answer = () =>
  new HttpResponse(
    JSON.stringify({
      timestamp: '2026-09-17T00:00:00Z',
      status: 401,
      error: 'Unauthorized',
      path: '/api/v1/check-status/submit',
    }),
    { status: 401, headers: { 'Content-Type': 'application/json' } },
  )

const millContextBody = (code1To10: string, code11 = 'D') => {
  const describe = (code: string) =>
    code === 'D' ? 'Draft' : code === 'S' ? 'Submitted' : 'Verified'
  return {
    millId: 13050,
    millNumber: '13050',
    millName: 'Test Mill',
    reportYear: 2017,
    schedules1To10Status: { code: code1To10, description: describe(code1To10), date: '2017-01-01' },
    schedule11Status: { code: code11, description: describe(code11), date: '2017-02-02' },
    millViewable: true,
  }
}

const SUBMITTED_SWEEP = sweep({ statusCode1To10: 'S', canSubmit1To10: false })

/**
 * A stateful fake of the two reads this page issues. Each GET answers with whatever `state` holds at
 * that moment, so a submit handler can flip the "server" to Submitted and the refetches see it — the
 * global `/mill-context` default hardcodes Draft on every call, so a correct refetch would look broken
 * against it (test-setup.ts:176-191). Call counts are recorded; note the tombstone fetches
 * `/mill-context` TWICE on mount (once from the loading tree, once after the data tree remounts it),
 * so context counts are asserted as deltas from the settled mount, never as absolutes.
 */
const fakeReads = (
  sweepBody: unknown = sweep({ canSubmit1To10: true }),
  context: Answer = json(millContextBody('D')),
) => {
  const state = { sweep: json(sweepBody), context }
  const calls = { sweep: 0, context: 0 }
  server.use(
    http.get(SWEEP_URL, () => {
      calls.sweep += 1
      return state.sweep()
    }),
    http.get(MILL_CONTEXT, () => {
      calls.context += 1
      return state.context()
    }),
  )
  return { state, calls }
}

/** The submit POST, answered by `answer` and recorded (count + URLs) for the request-counter proofs. */
const submitHandler = (answer: Answer) => {
  const calls = { count: 0, urls: [] as string[] }
  server.use(
    http.post(SUBMIT_URL, ({ request }) => {
      calls.count += 1
      calls.urls.push(request.url)
      return answer()
    }),
  )
  return calls
}

/** A submit that succeeds AND moves the fake server's reads to Submitted, as the real one would. */
const submitAndFlip = (reads: ReturnType<typeof fakeReads>): Answer => {
  return () => {
    reads.state.sweep = json(SUBMITTED_SWEEP)
    reads.state.context = json(millContextBody('S'))
    return submitOk()
  }
}

const drainEventLoop = async (turns = 20) => {
  for (let i = 0; i < turns; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

const dialog = () => screen.findByRole('dialog', { name: 'Confirmation Required' })
const openDialog = () => screen.queryByRole('dialog', { name: 'Confirmation Required' })

/** Press one of the two Schedules 1–10 Submits (0 = above the region, 1 = below) and return the dialog. */
const pressSubmit = async (user: ReturnType<typeof userEvent.setup>, index: 0 | 1 = 0) => {
  await user.click(submitButtons1To10()[index])
  return within(await dialog())
}
const confirmSubmit = async (user: ReturnType<typeof userEvent.setup>, index: 0 | 1 = 0) => {
  const prompt = await pressSubmit(user, index)
  await user.click(prompt.getByRole('button', { name: 'Yes' }))
}

/** The focusable Column wrapping the banner that carries `text` (D5(b): `tabIndex={-1}` on the Column). */
const bannerColumn = (text: string) => screen.getByText(text).closest('[tabindex="-1"]')

/** Mount as the submitter and wait for the data tree AND the tombstone's second fetch to settle. */
const mountSettled = async (line = DRAFT_LINE) => {
  renderAsSubmitter(<CheckStatus />)
  expect(declaredRole()).toBe(ILCR_ROLES.submitter)
  expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
  expect(await screen.findByText(line)).toBeInTheDocument()
}

const expectVerdictsAndBarsPresent = () => {
  expect(screen.getAllByText(MET_TEXT)).toHaveLength(12)
  expect(region1To10()).toBeInTheDocument()
  expect(region11()).toBeInTheDocument()
  expect(submitButtons()).toHaveLength(3)
  expect(screen.queryByText(LOAD_TITLE)).not.toBeInTheDocument()
  expect(screen.queryByText(LOAD_FAILED)).not.toBeInTheDocument()
  expect(screen.queryByRole('status', LOADING)).not.toBeInTheDocument()
}

const expectSubmits1To10 = async (state: 'enabled' | 'disabled', hint?: string) => {
  await waitFor(() => {
    const buttons = submitButtons1To10()
    expect(buttons).toHaveLength(2)
    for (const button of buttons) {
      if (state === 'enabled') {
        expect(button).toBeEnabled()
        expect(button).not.toHaveAttribute('aria-describedby')
      } else {
        expect(button).toBeDisabled()
        if (hint) expect(hintFor(button)).toHaveTextContent(hint)
      }
    }
  })
}

describe('Submit Schedules 1–10 (Story 15.4)', () => {
  // ---- AC 1 / AC 2 / AC 5: the happy path from either bar -------------------------------------

  test.each([
    ['top', 0],
    ['bottom', 1],
  ] as const)(
    'AC 1 (%s Submit): the dialog carries the four legacy literals; Yes issues ONE POST; the server text renders; both refetches land and the page reads Submitted',
    async (_label, index) => {
      const reads = fakeReads()
      const submit = submitHandler(submitAndFlip(reads))
      const user = userEvent.setup()
      await mountSettled()
      const contextCallsAtMount = reads.calls.context
      await expectSubmits1To10('enabled')

      const prompt = await pressSubmit(user, index)
      // AC 2 — legacy's dialog, verbatim (checkStatus.xhtml:197-200; messages.properties:102).
      expect(screen.getByRole('dialog', { name: 'Confirmation Required' })).toBeInTheDocument()
      expect(prompt.getByText(CONFIRM_SUBMIT_1_TO_10)).toBeInTheDocument()
      expect(prompt.getByRole('button', { name: 'Yes' })).toBeInTheDocument()
      expect(prompt.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
      expect(prompt.queryByRole('button', { name: 'No' })).not.toBeInTheDocument()
      expect(document.querySelector('.cds--modal--danger')).toBeNull()
      expect(document.querySelector('.cds--modal')).not.toBeNull() // positive control for the selector
      expect(submit.count).toBe(0)

      await user.click(prompt.getByRole('button', { name: 'Yes' }))
      expect(await screen.findByText(SUBMITTED)).toBeInTheDocument()
      expect(screen.getByText('Success')).toBeInTheDocument()
      expect(submit.count).toBe(1)
      const params = new URL(submit.urls[0]).searchParams
      expect(params.get('millId')).toBe('13050')
      expect(params.get('year')).toBe('2017')

      // AC 5 — both reads re-issued; everything shown afterwards came off the second responses.
      await waitFor(() => expect(reads.calls.sweep).toBe(2))
      await waitFor(() => expect(reads.calls.context).toBe(contextCallsAtMount + 1))
      expect(await screen.findByText(SUBMITTED_LINE)).toBeInTheDocument()
      expect(screen.queryByText(DRAFT_LINE)).not.toBeInTheDocument()
      await expectSubmits1To10('disabled', HINT_NOT_DRAFT)
      // A banner, never the load state: the verdicts and all three bars are still on the page.
      expectVerdictsAndBarsPresent()
      expect(screen.getByText(SUBMITTED)).toBeInTheDocument()
      expect(openDialog()).toBeNull()
    },
  )

  test('AC 5: the values rendered after a 200 are the second sweep’s — a changed verdict shows, nothing is flipped client-side', async () => {
    const reads = fakeReads()
    submitHandler(() => {
      reads.state.sweep = json(
        sweep({
          statusCode1To10: 'S',
          canSubmit1To10: false,
          overrides: [{ schedule: '7B', requirementsMet: false, verdict: schedule7bFail }],
        }),
      )
      reads.state.context = json(millContextBody('S'))
      return submitOk()
    })
    const user = userEvent.setup()
    await mountSettled()
    const contextCallsAtMount = reads.calls.context
    expect(screen.queryByText(SCH7B_ERROR_TEXT)).not.toBeInTheDocument()

    await confirmSubmit(user)
    await screen.findByText(SUBMITTED)
    expect(await screen.findByText(SCH7B_ERROR_TEXT)).toBeInTheDocument()
    expect(screen.getAllByText(MET_TEXT)).toHaveLength(11)
    expect(await screen.findByText(SUBMITTED_LINE)).toBeInTheDocument()
    await expectSubmits1To10('disabled', HINT_NOT_DRAFT)
    expect(reads.calls.sweep).toBe(2)
    expect(reads.calls.context).toBe(contextCallsAtMount + 1)
  })

  // ---- AC 3: Cancel is purely client-side -----------------------------------------------------

  test('AC 3: Cancel, the close control and Escape each close the dialog with NO request and nothing else changed; Yes is the positive control', async () => {
    const reads = fakeReads()
    const submit = submitHandler(submitAndFlip(reads))
    const user = userEvent.setup()
    await mountSettled()
    const contextCallsAtMount = reads.calls.context

    // Cancel — legacy's `type="button"` with no action (UC-CHK-002 S02).
    let prompt = await pressSubmit(user, 0)
    await user.click(prompt.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(openDialog()).toBeNull())
    // The close control and Escape both run through onRequestClose.
    prompt = await pressSubmit(user, 1)
    await user.click(prompt.getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(openDialog()).toBeNull())
    await pressSubmit(user, 0)
    await user.keyboard('{Escape}')
    await waitFor(() => expect(openDialog()).toBeNull())

    // Proven by the counters, not by the prompt text disappearing (Carbon keeps a closed Modal mounted).
    await drainEventLoop()
    expect(submit.count).toBe(0)
    expect(reads.calls.sweep).toBe(1)
    expect(reads.calls.context).toBe(contextCallsAtMount)
    expect(screen.queryByText(SUBMITTED)).not.toBeInTheDocument()
    expect(screen.queryByText('Success')).not.toBeInTheDocument()
    expect(screen.queryByText('Action failed')).not.toBeInTheDocument()
    expect(screen.getByText(DRAFT_LINE)).toBeInTheDocument()
    await expectSubmits1To10('enabled')
    expectVerdictsAndBarsPresent()

    // Positive control: the same dialog, answered Yes, issues the POST.
    await confirmSubmit(user, 0)
    expect(await screen.findByText(SUBMITTED)).toBeInTheDocument()
    expect(submit.count).toBe(1)
  })

  // ---- AC 4 / AC 5b: the gate 409 re-syncs the panels (D8) ------------------------------------

  test('AC 4 / AC 5b: the gate 409 renders verbatim, the verdicts and bars survive, and the re-sweep puts the newly-failing schedule on screen', async () => {
    const reads = fakeReads()
    const submit = submitHandler(() => {
      // The gate found a schedule that fell out of compliance since the page was opened.
      reads.state.sweep = json(
        sweep({
          canSubmit1To10: true,
          overrides: [{ schedule: '7B', requirementsMet: false, verdict: schedule7bFail }],
        }),
      )
      return problem(409, NOT_SUBMITTED)
    })
    const user = userEvent.setup()
    await mountSettled()
    expect(screen.queryByText(SCH7B_ERROR_TEXT)).not.toBeInTheDocument() // the "changed" control

    await confirmSubmit(user)
    expect(await screen.findByText(NOT_SUBMITTED)).toBeInTheDocument()
    expect(screen.getByText('Action failed')).toBeInTheDocument()
    expect(submit.count).toBe(1)
    // D8: the panels CHANGED — the second sweep's failing Schedule 7B is now on the page.
    expect(await screen.findByText(SCH7B_ERROR_TEXT)).toBeInTheDocument()
    expect(reads.calls.sweep).toBe(2)
    expect(screen.getAllByText(MET_TEXT)).toHaveLength(11)
    expect(within(item(SCHEDULE_TITLES['7B'])).getByText(SCH7B_ERROR_TEXT)).toBeInTheDocument()
    // Still a banner, not the load state; the bars are back to live once the request settled.
    expect(screen.queryByText(LOAD_TITLE)).not.toBeInTheDocument()
    expect(region1To10()).toBeInTheDocument()
    expect(region11()).toBeInTheDocument()
    expect(submitButtons()).toHaveLength(3)
    await expectSubmits1To10('enabled')
    expect(screen.getByText(NOT_SUBMITTED)).toBeInTheDocument() // the banner survived the re-sweep
  })

  test('AC 5b: the not-Draft 409 re-syncs — the track reads Submitted, canSubmit is false, both Submits grey out', async () => {
    const reads = fakeReads()
    submitHandler(() => {
      // Someone else (or this user's other tab) already submitted: the server is at S.
      reads.state.sweep = json(SUBMITTED_SWEEP)
      reads.state.context = json(millContextBody('S'))
      return problem(409, SUBMISSION_ERROR)
    })
    const user = userEvent.setup()
    await mountSettled()
    const contextCallsAtMount = reads.calls.context

    await confirmSubmit(user)
    expect(await screen.findByText(SUBMISSION_ERROR)).toBeInTheDocument()
    expect(screen.getByText('Action failed')).toBeInTheDocument()
    await waitFor(() => expect(reads.calls.sweep).toBe(2))
    await waitFor(() => expect(reads.calls.context).toBe(contextCallsAtMount + 1))
    await expectSubmits1To10('disabled', HINT_NOT_DRAFT)
    expect(await screen.findByText(SUBMITTED_LINE)).toBeInTheDocument()
    expectVerdictsAndBarsPresent()
    expect(screen.queryByText(SUBMITTED)).not.toBeInTheDocument() // no success text on a failure
  })

  // ---- AC 4 / AC 5b negative half: verbatim detail, NO re-fetch -------------------------------

  test.each([
    ['400 (trailing space, byte-exact)', () => problem(400, ERR_001), ERR_001],
    ['403', () => problem(403, FORBIDDEN), FORBIDDEN],
    ['404', () => problem(404, NOT_FOUND), NOT_FOUND],
    ['500', () => problem(500, SUBMISSION_ERROR), SUBMISSION_ERROR],
    ['401 (no problem+json body)', unauthorized, SUBMIT_FAILED],
    ['network failure', () => HttpResponse.error(), SUBMIT_FAILED],
  ])(
    'AC 4 / AC 5b: %s → the detail (or the client fallback) renders verbatim, the verdicts survive, and neither read is re-issued',
    async (_label, answer, expected) => {
      const reads = fakeReads()
      const submit = submitHandler(answer)
      const user = userEvent.setup()
      await mountSettled()
      const contextCallsAtMount = reads.calls.context

      await confirmSubmit(user)
      expect(await screen.findByText(expected, byteExact)).toBeInTheDocument()
      expect(screen.getByText('Action failed')).toBeInTheDocument()
      expect(submit.count).toBe(1)
      // The lock has released, so the request has fully settled before the counters are read.
      await expectSubmits1To10('enabled')
      await drainEventLoop()
      expect(reads.calls.sweep).toBe(1)
      expect(reads.calls.context).toBe(contextCallsAtMount)
      expectVerdictsAndBarsPresent()
      expect(screen.getByText(DRAFT_LINE)).toBeInTheDocument()
      // A fallback keyed on "network error" would render the string "undefined" for the 401 (M10).
      expect(screen.queryByText('undefined')).not.toBeInTheDocument()
      expect(screen.queryByText(SUBMITTED)).not.toBeInTheDocument()
    },
  )

  // ---- AC 4 / AC 6: the closed-mill 409, whose re-sweep itself 409s ----------------------------

  test('AC 4 / AC 6: the closed-mill 409 renders verbatim and triggers a re-sweep that also 409s — the panels, bars, tombstone and banner all survive', async () => {
    const reads = fakeReads()
    submitHandler(() => {
      // The mill was closed under the open page: every read now refuses too (AD-4).
      reads.state.sweep = () => problem(409, NOT_ACTIVE)
      reads.state.context = () => problem(409, NOT_ACTIVE)
      return problem(409, NOT_ACTIVE)
    })
    const user = userEvent.setup()
    await mountSettled()
    const contextCallsAtMount = reads.calls.context

    await confirmSubmit(user)
    expect(await screen.findByText(NOT_ACTIVE)).toBeInTheDocument()
    await waitFor(() => expect(reads.calls.sweep).toBe(2))
    await waitFor(() => expect(reads.calls.context).toBe(contextCallsAtMount + 1))
    await expectSubmits1To10('enabled')
    await drainEventLoop()
    // Once, in the banner — never a second time in the shared renderer's titled 409 state.
    expect(screen.getAllByText(NOT_ACTIVE)).toHaveLength(1)
    expect(screen.queryByText('Mill not active for Reporting Year')).not.toBeInTheDocument()
    expect(screen.getByText('Action failed')).toBeInTheDocument()
    expectVerdictsAndBarsPresent()
    expect(screen.getByText(MILL_LINE)).toBeInTheDocument()
    expect(screen.getByText(DRAFT_LINE)).toBeInTheDocument()
  })

  // ---- AC 6: a failed re-fetch must not destroy the outcome ------------------------------------

  test.each([
    ['500', () => problem(500, 'Schedule 5 could not be evaluated.')],
    ['network failure', () => HttpResponse.error()],
  ])(
    'AC 6: a %s on the post-200 re-sweep keeps the success banner, the verdicts and the bars, and never flashes the spinner over the page',
    async (_label, failure) => {
      let release!: () => void
      const held = new Promise<void>((resolve) => {
        release = resolve
      })
      const reads = fakeReads()
      submitHandler(() => {
        reads.state.sweep = async () => {
          await held
          return failure()
        }
        return submitOk()
      })
      const user = userEvent.setup()
      await mountSettled()

      await confirmSubmit(user)
      expect(await screen.findByText(SUBMITTED)).toBeInTheDocument()
      await waitFor(() => expect(reads.calls.sweep).toBe(2))
      // The re-fetch is in flight and held: this IS the frame D4 must keep flicker-free.
      expect(screen.queryByRole('status', LOADING)).not.toBeInTheDocument()
      expect(screen.getAllByText(MET_TEXT)).toHaveLength(12)
      expect(screen.getByText(SUBMITTED)).toBeInTheDocument()

      release()
      await drainEventLoop()
      expectVerdictsAndBarsPresent()
      expect(screen.getByText(SUBMITTED)).toBeInTheDocument()
      expect(screen.queryByText('Schedule 5 could not be evaluated.')).not.toBeInTheDocument()
    },
  )

  test.each([
    ['500', () => problem(500, 'boom')],
    ['network failure', () => HttpResponse.error()],
  ])(
    'AC 6 (the second hook): a %s on the post-200 /mill-context re-fetch leaves the Mill/Year line and both status lines standing',
    async (_label, failure) => {
      const reads = fakeReads()
      submitHandler(() => {
        reads.state.sweep = json(SUBMITTED_SWEEP)
        reads.state.context = failure
        return submitOk()
      })
      const user = userEvent.setup()
      await mountSettled()
      const contextCallsAtMount = reads.calls.context

      await confirmSubmit(user)
      expect(await screen.findByText(SUBMITTED)).toBeInTheDocument()
      await waitFor(() => expect(reads.calls.context).toBe(contextCallsAtMount + 1))
      await expectSubmits1To10('disabled', HINT_NOT_DRAFT) // the second sweep landed
      await drainEventLoop()
      // The last good context stands — stale, but standing — rather than the block unmounting.
      expect(screen.getByRole('region', { name: 'Working context' })).toBeInTheDocument()
      expect(screen.getByText(MILL_LINE)).toBeInTheDocument()
      expect(screen.getByText(DRAFT_LINE)).toBeInTheDocument()
      expect(screen.getByText(SCH11_LINE)).toBeInTheDocument()
      expect(screen.getByText(SUBMITTED)).toBeInTheDocument()
      expectVerdictsAndBarsPresent()
    },
  )

  // ---- AC 1 / D1: the gate reads the wire ------------------------------------------------------

  test.each([
    [
      'submitter, canSubmit true at Submitted → enabled (the gate reads the wire, not the status)',
      'submitter',
      { statusCode1To10: 'S', canSubmit1To10: true },
      'enabled',
      undefined,
    ],
    [
      'submitter, canSubmit false at Draft → disabled with the status hint (the hint still selects on role/status)',
      'submitter',
      { statusCode1To10: 'D', canSubmit1To10: false },
      'disabled',
      HINT_NOT_DRAFT,
    ],
    [
      'submitter, canSubmit absent at Draft → disabled',
      'submitter',
      { statusCode1To10: 'D' },
      'disabled',
      HINT_NOT_DRAFT,
    ],
    [
      'admin, canSubmit false at Draft → disabled with the role hint',
      'admin',
      { statusCode1To10: 'D', canSubmit1To10: false },
      'disabled',
      HINT_NOT_SUBMITTER,
    ],
  ] as const)('D1: %s', async (_label, role, options, state, hint) => {
    server.use(http.get(SWEEP_URL, () => HttpResponse.json(sweep(options))))
    if (role === 'admin') {
      renderAsAdmin(<CheckStatus />)
      expect(declaredRole()).toBe(ILCR_ROLES.admin)
    } else {
      renderAsSubmitter(<CheckStatus />)
      expect(declaredRole()).toBe(ILCR_ROLES.submitter)
    }
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    await expectSubmits1To10(state, hint)
  })

  // ---- AC 7: the outcome is put in front of the user (D6) --------------------------------------

  test.each([
    ['success', submitOk, SUBMITTED],
    ['failure', () => problem(409, NOT_SUBMITTED), NOT_SUBMITTED],
  ])('AC 7: after a %s the banner has focus', async (_label, answer, text) => {
    fakeReads()
    submitHandler(answer)
    const user = userEvent.setup()
    await mountSettled()

    await confirmSubmit(user, 1) // the BOTTOM bar — the one legacy's scroll anchor existed for
    expect(await screen.findByText(text)).toBeInTheDocument()
    const column = bannerColumn(text)
    expect(column).not.toBeNull()
    await waitFor(() => expect(column).toHaveFocus())
    // Programmatic target only — never in the tab order.
    expect(column).toHaveAttribute('tabindex', '-1')
  })

  // ---- D7: the in-flight lock ------------------------------------------------------------------

  test('D7: while the POST is in flight both 1–10 Submits are disabled and further clicks issue nothing; Schedule 11’s own Submit is untouched', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    fakeReads(sweep({ canSubmit1To10: true, statusCode11: 'D' }))
    const submit = submitHandler(async () => {
      await held
      return submitOk()
    })
    const user = userEvent.setup()
    await mountSettled()
    const submit11 = () =>
      within(item(SCHEDULE_TITLES['11'])).getByRole('button', { name: 'Submit' })
    expect(submit11()).toBeEnabled()

    await confirmSubmit(user)
    await waitFor(() => expect(submit.count).toBe(1))
    const buttons = submitButtons1To10()
    expect(buttons).toHaveLength(2)
    for (const button of buttons) {
      expect(button).toBeDisabled()
    }
    expect(submit11()).toBeEnabled() // the lock is the track's, not the page's
    await user.click(buttons[0])
    await user.click(buttons[1])
    expect(openDialog()).toBeNull()
    expect(submit.count).toBe(1)

    release()
    expect(await screen.findByText(SUBMITTED)).toBeInTheDocument()
    expect(submit.count).toBe(1)
    await expectSubmits1To10('enabled') // the lock releases once the request settles
  })

  // ---- Stale context -------------------------------------------------------------------------

  test('stale context: a submit whose response lands after the mill/year flips renders no banner', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get(SWEEP_URL, ({ request }) => {
        const params = new URL(request.url).searchParams
        return HttpResponse.json(
          sweep({
            millId: Number(params.get('millId')),
            year: Number(params.get('year')),
            canSubmit1To10: true,
          }),
        )
      }),
    )
    submitHandler(async () => {
      await held
      return submitOk()
    })
    const getSpy = vi.spyOn(apiService.getAxiosInstance(), 'get')
    const user = userEvent.setup()
    renderAsSubmitter(<ContextSwitchHarness />)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

    await confirmSubmit(user)
    await user.click(screen.getByRole('button', { name: 'change' }))
    await waitFor(() => expect(sweepCalls(getSpy)).toHaveLength(2))
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

    release()
    await drainEventLoop()
    expect(screen.queryByText(SUBMITTED)).not.toBeInTheDocument()
    expect(screen.queryByText('Success')).not.toBeInTheDocument()
    // Positive control: the un-flipped flow renders the banner (the AC 1 arms); here the second sweep
    // was for the NEW context, so the page is live for mill 999 with no message from mill 13050.
    expect(String(sweepCalls(getSpy)[1][0])).toContain('millId=999')
  })

  // ---- D10 / regression: nothing else on the three bars gains a click -----------------------

  test('D10: Schedule 11’s Submit keeps the client gate and stays inert — enabled at Draft, but a click opens no dialog and issues no request', async () => {
    server.use(http.get(SWEEP_URL, () => HttpResponse.json(sweep({ statusCode11: 'D' }))))
    const postSpy = vi.spyOn(apiService.getAxiosInstance(), 'post')
    const user = userEvent.setup()
    renderAsSubmitter(<CheckStatus />)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

    const submit11 = within(item(SCHEDULE_TITLES['11'])).getByRole('button', { name: 'Submit' })
    expect(submit11).toBeEnabled()
    await user.click(submit11)
    expect(openDialog()).toBeNull()
    expect(postSpy).not.toHaveBeenCalled()
    // The 1–10 pair reads the wire: canSubmit absent → greyed, whatever Schedule 11 says.
    await expectSubmits1To10('disabled', HINT_NOT_DRAFT)
  })

  test('regression: Verified, Set to Draft and Set to Submit remain inert on all three bars', async () => {
    server.use(
      millContextWithBothTracks('S', 'V'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'V' })),
      ),
    )
    const getSpy = vi.spyOn(apiService.getAxiosInstance(), 'get')
    const postSpy = vi.spyOn(apiService.getAxiosInstance(), 'post')
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    expect(getSpy).toHaveBeenCalled() // the spies see this instance's traffic (positive control)

    const enabled = [
      ...screen.getAllByRole('button', { name: 'Verified' }),
      ...screen.getAllByRole('button', { name: 'Set to Draft' }),
      ...screen.getAllByRole('button', { name: 'Set to Submit' }),
    ].filter((button) => !(button as HTMLButtonElement).disabled)
    // 2 Verified + 2 Set to Draft (1–10 Submitted), 1 Set to Submit (Schedule 11 Verified).
    expect(enabled).toHaveLength(5)
    for (const button of enabled) {
      await user.click(button)
    }
    expect(openDialog()).toBeNull()
    expect(postSpy).not.toHaveBeenCalled()
    expect(screen.queryByText('Success')).not.toBeInTheDocument()
    expect(screen.queryByText('Action failed')).not.toBeInTheDocument()
  })
})
