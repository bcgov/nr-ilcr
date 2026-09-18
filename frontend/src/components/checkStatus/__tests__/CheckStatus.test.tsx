import { afterEach, describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getDefaultNormalizer } from '@testing-library/react'
import {
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
import { MOCK_USER_STORAGE_KEY } from '@/context/auth/mockUsers'
import { ERR_MILL_YEAR_NOT_SELECTED } from '@/components/core/ScheduleLoadState'
import CheckStatus, {
  HINT_NOT_ADMIN,
  HINT_NOT_DRAFT,
  HINT_NOT_DRAFT_11,
  HINT_NOT_SUBMITTED,
  HINT_NOT_SUBMITTED_11,
  HINT_NOT_SUBMITTER,
  HINT_NOT_WIRED,
  CONFIRM_HEADING,
  VERIFY_FAILED,
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

/**
 * As `verbatim`, but keeps a leading/trailing space as well. `verbatim` still trims — the default
 * normalizer's `trim` is independent of `collapseWhitespace` — which would silently pass an ERR-001
 * assertion whether or not the bundle's trailing space survived to the screen.
 */
const verbatimUntrimmed = {
  normalizer: getDefaultNormalizer({ collapseWhitespace: false, trim: false }),
}

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
        return HttpResponse.json(sweep({ statusCode11: 'D' }))
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
          sweep({ overrides: [{ schedule: '1', requirementsMet: false, verdict: schedule1Fail }] }),
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

    // 1–10 Submitted → both 1–10 bars: Set to Draft, Submit (greyed), Verified (enabled).
    // The reversal is RENDERED by legacy's rule but greyed until Epic 18 wires it (17.2 review):
    // legacy's was enabled and worked, and a live control that silently does nothing is worse than
    // a greyed one that says why.
    const setToDraft = screen.getAllByRole('button', { name: 'Set to Draft' })
    expect(setToDraft).toHaveLength(2)
    for (const button of setToDraft) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_WIRED)
      expect(region11().contains(button)).toBe(false)
    }
    expect(within(region1To10()).queryByRole('button', { name: 'Set to Submit' })).toBeNull()
    // Schedule 11 Verified → its bar: Set to Submit (greyed, same reason), Submit and Verified greyed.
    const bar11 = item(SCHEDULE_TITLES['11'])
    const setToSubmit = within(bar11).getByRole('button', { name: 'Set to Submit' })
    expect(setToSubmit).toBeDisabled()
    expect(hintFor(setToSubmit)).toHaveTextContent(HINT_NOT_WIRED)
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

// ================================================================================================
// Verify the Schedules 1–10 track (Story 17.2 / UC-CHK-012, UC-CHK-007, UC-CHK-009 S03/S16)
//
// Legacy hung the server call on the confirm dialog's Yes, never on the button (checkStatus.xhtml:57-62
// is type="button" whose only behaviour is confirmVerify.show(); the action is at :201-204). These
// tests hold that shape: Cancel cannot reach the server because nothing is wired to it.
// ================================================================================================

const VERIFY_URL = `${API}/v1/check-status/verify`

const VERIFIED_MSG = 'Schedules 1-10 status has been updated to verified.'
const NOT_SUBMITTED_MSG =
  'The report cannot be submitted. One or more of the Schedules have not passed validation. Please review and correct any errors.'
const SUBMISSION_ERROR_MSG =
  'An error has been found submitting schedules. The error details have been logged. Please contact ILCR application support.'
const ERR_001 = 'Please Select Mill and Reporting Year in the Home Page. '

/** A Submitted 1–10 track with Schedule 11 left in Draft, the state the Verified pair is enabled in. */
const submittedSweep = () =>
  server.use(
    millContextWithBothTracks('S', 'D'),
    http.get(SWEEP_URL, () =>
      HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' })),
    ),
  )

/** The verify POST, recorded by a spy so "no request was sent" is provable. */
const verifyHandler = (spy: () => void, reply: () => HttpResponse) =>
  http.post(VERIFY_URL, ({ request }) => {
    spy()
    void request
    return reply()
  })

const ok = () =>
  HttpResponse.json({
    trackStatus: 'V',
    message: { key: 'sch1-10VerifiedMsg', text: VERIFIED_MSG },
  })

describe('Verify the Schedules 1–10 track (Story 17.2)', () => {
  test('CHK-012 BR-09 / S01: clicking Verified opens the verbatim confirmation and sends nothing yet', async () => {
    const posted = vi.fn()
    submittedSweep()
    server.use(verifyHandler(posted, ok))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])

    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText(
        "Please confirm you'd like to set Schedules 1-10 to VERIFIED?",
        verbatim,
      ),
    ).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Yes' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    // Legacy's header, hard-coded in the XHTML rather than bundled (checkStatus.xhtml:201).
    expect(within(dialog).getByText('Confirmation Required')).toBeInTheDocument()
    // The transition hangs off Yes, so opening the prompt cannot have called the server.
    expect(posted).not.toHaveBeenCalled()
  })

  test('CHK-012 S02 / CHK-009 S16: Cancel closes the confirmation and makes no server call', async () => {
    const posted = vi.fn()
    submittedSweep()
    server.use(verifyHandler(posted, ok))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    )

    // Asserted by the absence of the POST, not the absence of the prompt: Carbon keeps a closed
    // Modal's content mounted (OpenReportingYear.test.tsx:88-90).
    expect(posted).not.toHaveBeenCalled()
    // Nothing changed: no outcome banner, and the pair is still enabled at Submitted.
    expect(screen.queryByText(VERIFIED_MSG)).not.toBeInTheDocument()
    for (const button of verifiedButtons1To10()) {
      expect(button).toBeEnabled()
    }

    // Positive control: the same handler DOES fire when Yes is clicked.
    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    await waitFor(() => expect(posted).toHaveBeenCalledTimes(1))
  })

  test('CHK-012 S01/S08: Yes verifies once, shows the server text, and the pair re-disables after the refresh', async () => {
    const seen: string[] = []
    // The sweep answers Submitted first and Verified afterwards, so a re-disabled button proves the
    // page re-read the server rather than flipping a local copy of the status.
    let verified = false
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: verified ? 'V' : 'S', statusCode11: 'D' })),
      ),
      http.post(VERIFY_URL, ({ request }) => {
        const params = new URL(request.url).searchParams
        seen.push(`${params.get('millId')}/${params.get('year')}`)
        verified = true
        return ok()
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))

    // The server's own words, verbatim.
    expect(await screen.findByText(VERIFIED_MSG, verbatim)).toBeInTheDocument()
    // Exactly one call, carrying the working context as query params (millId + year, not reportingYear).
    expect(seen).toEqual(['13050/2017'])
    // Both 1-10 bars re-disable, and the reversals swap over as legacy's `rendered=` rules do at V.
    await waitFor(() => {
      for (const button of verifiedButtons1To10()) {
        expect(button).toBeDisabled()
      }
    })
    expect(within(region1To10()).queryByRole('button', { name: 'Set to Draft' })).toBeNull()
    expect(screen.getAllByRole('button', { name: 'Set to Submit' }).length).toBeGreaterThan(0)
  })

  test('CHK-012 S09 / BR-08: verifying 1-10 leaves the Schedule 11 action row untouched', async () => {
    let verified = false
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: verified ? 'V' : 'S', statusCode11: 'D' })),
      ),
      http.post(VERIFY_URL, () => {
        verified = true
        return ok()
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    const before = item(SCHEDULE_TITLES['11']).innerHTML

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    await screen.findByText(VERIFIED_MSG, verbatim)
    await waitFor(() => expect(verifiedButtons1To10()[0]).toBeDisabled())

    // Schedule 11's own row is an independent track: same markup before and after (S09).
    expect(item(SCHEDULE_TITLES['11']).innerHTML).toBe(before)
  })

  test('AC10: a verify in flight cannot be sent twice', async () => {
    const posted = vi.fn()
    let release: () => void = () => undefined
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    submittedSweep()
    server.use(
      http.post(VERIFY_URL, async () => {
        posted()
        await held
        return ok()
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))

    // While the request is open both bars' buttons are out of action, so a second attempt is impossible.
    await waitFor(() => expect(posted).toHaveBeenCalledTimes(1))
    for (const button of verifiedButtons1To10()) {
      expect(button).toBeDisabled()
    }
    await user.click(verifiedButtons1To10()[1])
    expect(posted).toHaveBeenCalledTimes(1)

    release()
    expect(await screen.findByText(VERIFIED_MSG, verbatim)).toBeInTheDocument()
  })

  test('AC10 (17.2 review): the button stays out of action until the refresh lands, not just until the POST does', async () => {
    // Legacy re-gated the button in the SAME round trip that carried the message. We read the status
    // from a second request, so the window between the 200 and the reload landing is ours to close:
    // in it the sweep still answers Submitted, and a second verify would draw the server's
    // support-escalation 409 over the success the user just earned.
    const posted = vi.fn()
    let verified = false
    let sweeps = 0
    let releaseReload: () => void = () => undefined
    const reloadHeld = new Promise<void>((resolve) => {
      releaseReload = resolve
    })
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, async () => {
        sweeps += 1
        if (sweeps > 1) {
          await reloadHeld
        }
        return HttpResponse.json(
          sweep({ statusCode1To10: verified ? 'V' : 'S', statusCode11: 'D' }),
        )
      }),
      http.post(VERIFY_URL, () => {
        posted()
        verified = true
        return ok()
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    expect(await screen.findByText(VERIFIED_MSG, verbatim)).toBeInTheDocument()

    // The success is on screen and the reload is open, but the sweep still says Submitted.
    await waitFor(() => expect(sweeps).toBe(2))
    // `Set to Draft` renders only while the track is Submitted, so its presence IS the proof that
    // the gate is still reading the pre-transition status.
    expect(screen.getAllByRole('button', { name: 'Set to Draft' })).toHaveLength(2)
    expect(screen.queryByRole('button', { name: 'Set to Submit' })).toBeNull()
    for (const button of verifiedButtons1To10()) {
      expect(button).toBeDisabled()
    }
    // Clicking cannot even reach the prompt, so there is nothing to confirm.
    await user.click(verifiedButtons1To10()[0])
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(posted).toHaveBeenCalledTimes(1)

    // Positive control: once the refresh lands the button is still disabled, now because the server
    // says Verified — and the success message the first attempt earned is still the one on screen.
    releaseReload()
    await waitFor(() => expect(verifiedButtons1To10()[0]).toBeDisabled())
    expect(screen.getByText(VERIFIED_MSG, verbatim)).toBeInTheDocument()
    expect(screen.queryByText(SUBMISSION_ERROR_MSG, verbatim)).toBeNull()
    expect(posted).toHaveBeenCalledTimes(1)
  })

  test('AC10 (17.2 review): a refused verify releases the button once its own refresh-free arm settles', async () => {
    // The positive control for the test above: `busy` must not strand the button. An error bumps no
    // reload token, so the moment the request settles the gate is back on the server's status — still
    // Submitted — and a corrected report can be re-verified without leaving the page.
    const posted = vi.fn()
    submittedSweep()
    server.use(http.post(VERIFY_URL, () => problem(409, NOT_SUBMITTED_MSG)))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    expect(await screen.findByText(NOT_SUBMITTED_MSG, verbatim)).toBeInTheDocument()

    for (const button of verifiedButtons1To10()) {
      expect(button).toBeEnabled()
    }
    server.use(verifyHandler(posted, ok))
    await user.click(verifiedButtons1To10()[1])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    expect(await screen.findByText(VERIFIED_MSG, verbatim)).toBeInTheDocument()
    expect(posted).toHaveBeenCalledTimes(1)
  })

  test.each([
    ['success', () => ok(), VERIFIED_MSG],
    ['error', () => problem(409, NOT_SUBMITTED_MSG), NOT_SUBMITTED_MSG],
  ])(
    'AC11 (17.2 review): confirming moves focus to the outcome banner, not to <body> (%s)',
    async (_name, reply, text) => {
      // The prompt is mounted only while pending, so on confirm there is no dialog for Carbon to
      // restore focus from, and the launching button may now be greyed: focus fell to <body> and the
      // user was dropped to the top of the document with nothing announced.
      submittedSweep()
      server.use(http.post(VERIFY_URL, reply))
      const user = userEvent.setup()
      renderAsAdmin(<CheckStatus />)
      await screen.findAllByText(MET_TEXT)

      await user.click(verifiedButtons1To10()[1])
      await user.click(
        within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }),
      )
      const banner = await screen.findByText(text, verbatim)

      await waitFor(() => expect(document.activeElement).not.toBe(document.body))
      const focused = document.activeElement as HTMLElement
      expect(focused.contains(banner)).toBe(true)
      // A programmatic target only — never picked up by Tab.
      expect(focused).toHaveAttribute('tabindex', '-1')
    },
  )

  test('AC11 (17.2 review): declining still returns focus to the button, and moves it nowhere else', async () => {
    // The positive control for the pair above: the decline path predates this fix and must be intact.
    const posted = vi.fn()
    submittedSweep()
    server.use(verifyHandler(posted, ok))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    const launcher = verifiedButtons1To10()[1]
    await user.click(launcher)
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    )

    await waitFor(() => expect(document.activeElement).toBe(launcher))
    expect(posted).not.toHaveBeenCalled()
  })

  // ---- Error arms. Every one renders the server's verbatim `detail`; the page never branches on the
  // ---- text, because a 409 carries three different messages and the 500 duplicates one of them.
  test.each([
    ['CHK-012 S03 gate failure', 409, NOT_SUBMITTED_MSG],
    ['CHK-007 S06 refused transition', 409, SUBMISSION_ERROR_MSG],
    ['closed mill (ERR-002)', 409, NOT_ACTIVE],
    ['schedules not found (ERR-003)', 404, NOT_FOUND],
    ['missing context (ERR-001, trailing space)', 400, ERR_001],
    ['caller lacks the action', 403, FORBIDDEN],
  ])('%s: the verbatim detail renders and nothing changes', async (_name, status, detail) => {
    submittedSweep()
    server.use(http.post(VERIFY_URL, () => problem(status, detail)))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))

    expect(await screen.findByText(detail, verbatimUntrimmed)).toBeInTheDocument()
    // No success line anywhere (AC8), and the track is still Submitted so a retry stays possible.
    expect(screen.queryByText(VERIFIED_MSG)).not.toBeInTheDocument()
    await waitFor(() => {
      for (const button of verifiedButtons1To10()) {
        expect(button).toBeEnabled()
      }
    })
  })

  test('CHK-012 S03: a blocked verify can be retried, and the next outcome replaces the first', async () => {
    let attempt = 0
    submittedSweep()
    server.use(
      http.post(VERIFY_URL, () => {
        attempt += 1
        return attempt === 1 ? problem(409, NOT_SUBMITTED_MSG) : ok()
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    expect(await screen.findByText(NOT_SUBMITTED_MSG, verbatim)).toBeInTheDocument()

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    expect(await screen.findByText(VERIFIED_MSG, verbatim)).toBeInTheDocument()

    // AC8: exactly one outcome on screen. Legacy could show an error AND a success from one click
    // (CheckStatusMB.java:278-280 then :284); that defect is not reproduced.
    expect(screen.queryByText(NOT_SUBMITTED_MSG)).not.toBeInTheDocument()
  })

  test('a failure with no problem+json body falls back to the page-owned text', async () => {
    submittedSweep()
    server.use(http.post(VERIFY_URL, () => HttpResponse.error()))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))

    expect(await screen.findByText(VERIFY_FAILED)).toBeInTheDocument()
  })

  test('the licensee is never offered the action: no enabled Verified, and no dialog', async () => {
    submittedSweep()
    const posted = vi.fn()
    server.use(verifyHandler(posted, ok))
    renderAsSubmitter(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    // Rendered but greyed, exactly as legacy (no `rendered=` on the Verified button).
    for (const button of verifiedButtons1To10()) {
      expect(button).toBeDisabled()
      expect(hintFor(button)).toHaveTextContent(HINT_NOT_ADMIN)
    }
    expect(posted).not.toHaveBeenCalled()
  })

  test('AC11: the prompt is named by its heading, Escape declines it, and focus returns to the button', async () => {
    const posted = vi.fn()
    submittedSweep()
    server.use(verifyHandler(posted, ok))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    const trigger = verifiedButtons1To10()[0]
    await user.click(trigger)
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveAccessibleName(CONFIRM_HEADING)

    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(posted).not.toHaveBeenCalled()
    expect(trigger).toHaveFocus()
  })

  test('AC11: the outcome banner adds one page-level status line and leaves the per-schedule lines alone', async () => {
    submittedSweep()
    server.use(http.post(VERIFY_URL, () => problem(409, NOT_SUBMITTED_MSG)))
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)

    // Every InlineNotification renders role=status, so the new page-level banner must not leak into
    // the per-item queries the rest of this suite counts on.
    const linesBefore = linesIn(item(SCHEDULE_TITLES['1'])).length
    const pageBefore = screen.queryAllByRole('status').length

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    await screen.findByText(NOT_SUBMITTED_MSG, verbatimUntrimmed)

    expect(linesIn(item(SCHEDULE_TITLES['1']))).toHaveLength(linesBefore)
    expect(screen.queryAllByRole('status')).toHaveLength(pageBefore + 1)
  })
})
