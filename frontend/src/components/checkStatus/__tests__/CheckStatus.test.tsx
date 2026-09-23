import { afterEach, describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getDefaultNormalizer } from '@testing-library/react'
import {
  declaredRole,
  fireEvent,
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
  CONFIRM_SET_TO_DRAFT_1_TO_10,
  CONFIRM_SET_TO_SUBMIT_1_TO_10,
  CONFIRM_SUBMIT_1_TO_10,
  HINT_NOT_ADMIN,
  HINT_NOT_DRAFT,
  HINT_NOT_DRAFT_11,
  HINT_NOT_SUBMITTED,
  HINT_NOT_SUBMITTED_11,
  HINT_NOT_SUBMITTER,
  HINT_NOT_WIRED,
  SET_TO_DRAFT_FAILED,
  SET_TO_SUBMIT_FAILED,
  SUBMIT_FAILED,
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
    await screen.findByText('Location 7001 - Description: Value Required')

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
    // Declared, not inherited. The default mock user IS the admin, so a bare `render()` would pass
    // this arm whether or not the admin half of the gate works — and since Story 18.2 the arm
    // asserts a LIVE admin-only control, which is the polarity that trap hides best.
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    await screen.findAllByText(MET_TEXT)

    // 1–10 Submitted → both 1–10 bars: Set to Draft, Submit (greyed), Verified (enabled).
    // AMENDED by Story 18.2, which supplied the transition: the 1–10 reversal is now LIVE, as
    // legacy's was, and carries no hint. Schedule 11's stays greyed — Epic 26 owns that track.
    const setToDraft = screen.getAllByRole('button', { name: 'Set to Draft' })
    expect(setToDraft).toHaveLength(2)
    for (const button of setToDraft) {
      expect(button).toBeEnabled()
      expect(button).not.toHaveAttribute('aria-describedby')
      expect(region11().contains(button)).toBe(false)
    }
    expect(within(region1To10()).queryByRole('button', { name: 'Set to Submit' })).toBeNull()
    // Schedule 11 Verified → its bar: Set to Submit (greyed, still unwired), Submit and Verified greyed.
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
    expect(dialog).toHaveAccessibleName('Confirmation Required')

    // Escape is handled by the dialog, so it must hold focus — as it does for a user once it opens.
    within(dialog).getByRole('button', { name: 'Yes' }).focus()
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

  // ---- The refused verify re-reads, exactly as the refused submit does -------------------------

  test('CHK-012 S03: a gate 409 re-sweeps, so the newly-failing schedule is on screen behind the verbatim banner', async () => {
    const posted = vi.fn()
    let sweeps = 0
    let failing = false
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () => {
        sweeps += 1
        return HttpResponse.json(
          sweep({
            statusCode1To10: 'S',
            statusCode11: 'D',
            overrides: failing
              ? [{ schedule: '7B', requirementsMet: false, verdict: schedule7bFail }]
              : [],
          }),
        )
      }),
      verifyHandler(posted, () => {
        // A schedule fell out of compliance since the page was read. The gate runs before the
        // transition, so this is the 409 the user gets, and the verdict on screen is now stale.
        failing = true
        return problem(409, NOT_SUBMITTED_MSG)
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<CheckStatus />)
    await screen.findAllByText(MET_TEXT)
    expect(screen.queryByText(SCH7B_ERROR_TEXT)).not.toBeInTheDocument() // the "changed" control

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))

    expect(await screen.findByText(NOT_SUBMITTED_MSG, verbatimUntrimmed)).toBeInTheDocument()
    expect(posted).toHaveBeenCalledTimes(1)
    // The panels now agree with the banner's reason: the refusal re-read, as submit's 409 does.
    // Without the re-read the user reads why it was refused while the page still shows all-met.
    expect(await screen.findByText(SCH7B_ERROR_TEXT)).toBeInTheDocument()
    await waitFor(() => expect(sweeps).toBe(2))
    expect(screen.getByText(NOT_SUBMITTED_MSG, verbatimUntrimmed)).toBeInTheDocument()
  })

  // ---- Stale context ---------------------------------------------------------------------------

  test('stale context: a settled verify outcome does not follow the mill/year change — the banner goes, the prompt goes, and the new report is gated on its own status', async () => {
    const posted = vi.fn()
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, ({ request }) => {
        const params = new URL(request.url).searchParams
        return HttpResponse.json(
          sweep({
            millId: Number(params.get('millId')),
            year: Number(params.get('year')),
            statusCode1To10: 'S',
            statusCode11: 'D',
          }),
        )
      }),
      verifyHandler(posted, ok),
    )
    const user = userEvent.setup()
    renderAsAdmin(<ContextSwitchHarness />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    expect(await screen.findByText(VERIFIED_MSG)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'change' }))
    await screen.findAllByText(MET_TEXT)
    // Mill 999 is Submitted as well, so the pair is live again — on ITS status, not left greyed by a
    // success that belongs to the report the user moved away from, and with no banner from it either.
    expect(screen.queryByText(VERIFIED_MSG)).not.toBeInTheDocument()
    expect(screen.queryByText('Success')).not.toBeInTheDocument()
    await waitFor(() => {
      const buttons = verifiedButtons1To10()
      expect(buttons).toHaveLength(2)
      for (const button of buttons) {
        expect(button).toBeEnabled()
      }
    })
    expect(posted).toHaveBeenCalledTimes(1)
  })

  test('stale context: a prompt left pending across a mill/year change is dismissed, not re-shown over the new report', async () => {
    const posted = vi.fn()
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, ({ request }) => {
        const params = new URL(request.url).searchParams
        return HttpResponse.json(
          sweep({
            millId: Number(params.get('millId')),
            year: Number(params.get('year')),
            statusCode1To10: 'S',
            statusCode11: 'D',
          }),
        )
      }),
      verifyHandler(posted, ok),
    )
    const user = userEvent.setup()
    renderAsAdmin(<ContextSwitchHarness />)
    await screen.findAllByText(MET_TEXT)

    await user.click(verifiedButtons1To10()[0])
    expect(await screen.findByRole('dialog')).toBeInTheDocument()

    // The change is unreachable behind Carbon's overlay for a real user; the guarantee is that a
    // question asked about mill 13050 can never be answered `Yes` against mill 999.
    await user.click(screen.getByRole('button', { name: 'change' }))
    await screen.findAllByText(MET_TEXT)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(posted).not.toHaveBeenCalled()

    // Positive control: asking again in the new context still works.
    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes' }))
    await waitFor(() => expect(posted).toHaveBeenCalledTimes(1))
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
const NOT_DRAFT = 'Schedules 1-10 are no longer in Draft and cannot be submitted.'
// ERR_001 is declared once for both suites, with 17.2's block above (its trailing space is real).

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
    prompt = await pressSubmit(user, 0)
    // Escape is handled by the dialog, so it must hold focus — as it does for a user once it opens.
    prompt.getByRole('button', { name: 'Yes' }).focus()
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

  test('AC 3: opening and cancelling a retry preserves the previous outcome banner', async () => {
    fakeReads()
    const submit = submitHandler(() => problem(409, NOT_SUBMITTED))
    const user = userEvent.setup()
    await mountSettled()

    await confirmSubmit(user)
    expect(await screen.findByText(NOT_SUBMITTED)).toBeInTheDocument()
    await expectSubmits1To10('enabled')
    expect(submit.count).toBe(1)

    const prompt = await pressSubmit(user)
    expect(screen.getByText(NOT_SUBMITTED)).toBeInTheDocument()
    await user.click(prompt.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(openDialog()).toBeNull())
    expect(screen.getByText(NOT_SUBMITTED)).toBeInTheDocument()
    expect(screen.getByText('Action failed')).toBeInTheDocument()
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
      return problem(409, NOT_DRAFT)
    })
    const user = userEvent.setup()
    await mountSettled()
    const contextCallsAtMount = reads.calls.context

    await confirmSubmit(user)
    expect(await screen.findByText(NOT_DRAFT)).toBeInTheDocument()
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
    ['mismatched mill/year 200', () => HttpResponse.json(sweep({ millId: 999, year: 2017 }))],
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
      await expectSubmits1To10('disabled')

      release()
      await drainEventLoop()
      expectVerdictsAndBarsPresent()
      expect(screen.getByText(SUBMITTED)).toBeInTheDocument()
      expect(screen.queryByText('Schedule 5 could not be evaluated.')).not.toBeInTheDocument()
      expect(screen.queryByText(LOAD_FAILED)).not.toBeInTheDocument()
      await expectSubmits1To10('disabled')
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
    // A 200 completes the transition. Even if this deliberately stale fake still says Draft, the
    // successful action stays locked while the server-sourced refresh catches up (or fails).
    await expectSubmits1To10('disabled')
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
    // And the in-flight lock came off with it. Released only for the dispatching context it would
    // strand mill 999's pair greyed for the life of the mount, with no banner to explain why.
    await expectSubmits1To10('enabled')
  })

  test('stale context: a SETTLED submit outcome does not follow the mill/year change — the banner goes and the new report’s Submit is live', async () => {
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
    const submit = submitHandler(submitOk)
    const user = userEvent.setup()
    renderAsSubmitter(<ContextSwitchHarness />)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

    await confirmSubmit(user)
    expect(await screen.findByText(SUBMITTED)).toBeInTheDocument()
    // The success locks THIS context's pair (D7) — the state that must not be carried over.
    await expectSubmits1To10('disabled')

    await user.click(screen.getByRole('button', { name: 'change' }))
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    expect(screen.queryByText(SUBMITTED)).not.toBeInTheDocument()
    expect(screen.queryByText('Success')).not.toBeInTheDocument()
    // Mill 999 is Draft with `canSubmit` true: its Submit is offered on its own verdict.
    await expectSubmits1To10('enabled')
    expect(submit.count).toBe(1)
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

    // AMENDED TWICE. Story 17.2 made the 1–10 Verified pair a live transition; Story 18.2 made the
    // 1–10 reversals live too. What is left without a transition behind it is Schedule 11's bar, and
    // that is now all this regression owns: everything unwired stays unclickable and sends nothing.
    const bar11 = within(item(SCHEDULE_TITLES['11']))
    const inert = [
      bar11.getByRole('button', { name: 'Verified' }),
      bar11.getByRole('button', { name: 'Set to Submit' }),
    ]
    for (const button of inert) {
      expect(button).toBeDisabled()
      await user.click(button)
    }
    // Positive control: every pair that IS wired is enabled, so "disabled" above is not vacuous —
    // and all four live buttons sit outside the Schedule 11 bar.
    const live = [
      ...verifiedButtons1To10(),
      ...screen.getAllByRole('button', { name: 'Set to Draft' }),
    ]
    expect(live).toHaveLength(4)
    for (const button of live) {
      expect(button).toBeEnabled()
      expect(region11().contains(button)).toBe(false)
    }
    expect(openDialog()).toBeNull()
    expect(postSpy).not.toHaveBeenCalled()
    expect(screen.queryByText('Success')).not.toBeInTheDocument()
    expect(screen.queryByText('Action failed')).not.toBeInTheDocument()
  })
})

// ================================================================================================
// Reversal actions on the Schedules 1–10 track (Story 18.2 / UC-CHK-016, UC-CHK-018)
//
// Legacy put both buttons in all three action rows but `rendered=` them by track status, and hung the
// transition on the confirm dialog's Yes, never on the button itself (checkStatus.xhtml:37-50 and
// :189-196; CheckStatusMB.java:162-176). These arms hold that shape: the control is ABSENT outside its
// one legal state rather than greyed, and Cancel cannot reach the server because nothing is wired to it.
//
// Every arm declares its role. The default mock user IS the administrator (`findMockUser(null)` falls
// back to `MOCK_USERS[0]`), so an admin arm that does not say so passes whether or not the gate works —
// and this is an admin-only story, which is exactly where that trap costs the most.
// ================================================================================================

const SET_TO_DRAFT_URL = `${API}/v1/check-status/set-to-draft`
const SET_TO_SUBMIT_URL = `${API}/v1/check-status/set-to-submit`

const DRAFT_MSG = 'Schedules 1-10 have been set back to draft.'
/**
 * A successful Set to Submit renders legacy's SUBMIT success text. Legacy reused that key and minted
 * no "verification reversed" message of its own (UC-CHK-018.md:135), so this is parity, not a slip.
 */
const RESUBMITTED_MSG = 'Schedules 1-10 are successfully submitted.'

const GATE_DRAFT_MSG =
  'Schedules 1-10 cannot be set to Draft while one or more Schedules have errors. Please correct them and try again.'
const GATE_SUBMIT_MSG =
  'Schedules 1-10 cannot be set to Submit while one or more Schedules have errors. Please correct them and try again.'
const NOT_SUBMITTED_ANYMORE_MSG =
  'Schedules 1-10 are no longer in Submitted and cannot be set to Draft.'
const NOT_VERIFIED_ANYMORE_MSG =
  'Schedules 1-10 are no longer in Verified and cannot be set to Submit.'
const REVERSAL_ERR_001 = 'Please Select Mill and Reporting Year in the Home Page. '
const PERSISTENCE_MSG =
  'An error has been found submitting schedules. The error details have been logged. Please contact ILCR application support.'

/**
 * The two reversals as one table. The arms that could plausibly differ per label run against BOTH:
 * the render gates, the prompt, Cancel, the transition itself, the gate/wrong-status refusals, the
 * missing-`detail` fallback and the in-flight lock. The arms that hard-code Set to Draft — the
 * 400/403/404/409/500 status table, retry-after-failure, the 409 refresh window, the stale-response
 * guard and the context switch — exercise ONE code path each: both labels run through the same
 * `confirmReversal` factory, and the only per-label values it reads (`path`, `fallback`) are pinned
 * by the arms above. Running them twice would buy duplicate coverage, not a second risk.
 */
const REVERSALS = [
  {
    label: 'Set to Draft',
    url: SET_TO_DRAFT_URL,
    otherUrl: SET_TO_SUBMIT_URL,
    other: 'Set to Submit',
    from: 'S',
    to: 'D',
    prompt: CONFIRM_SET_TO_DRAFT_1_TO_10,
    success: DRAFT_MSG,
    successKey: 'sch1-10DraftMsg',
    fallback: SET_TO_DRAFT_FAILED,
    gate: GATE_DRAFT_MSG,
    stale: NOT_SUBMITTED_ANYMORE_MSG,
    /** What the bar offers once the transition lands — the visible consequence of the re-sweep. */
    becomes: null,
  },
  {
    label: 'Set to Submit',
    url: SET_TO_SUBMIT_URL,
    otherUrl: SET_TO_DRAFT_URL,
    other: 'Set to Draft',
    from: 'V',
    to: 'S',
    prompt: CONFIRM_SET_TO_SUBMIT_1_TO_10,
    success: RESUBMITTED_MSG,
    successKey: 'sch1-10SubmittedMsg',
    fallback: SET_TO_SUBMIT_FAILED,
    gate: GATE_SUBMIT_MSG,
    stale: NOT_VERIFIED_ANYMORE_MSG,
    becomes: 'Set to Draft',
  },
] as const

/** How `millContextWithBothTracks` words each code, so a status line can be asserted from a code. */
const STATUS_WORD: Record<string, string> = { D: 'Draft', S: 'Submitted', V: 'Verified' }

/** The 1–10 bars' copies of one reversal; Schedule 11's own button is never in this list. */
const reversalButtons = (label: string) =>
  screen.queryAllByRole('button', { name: label }).filter((b) => !region11().contains(b))

/** A sweep fixed at one 1–10 status, Schedule 11 left in Draft so its own bar offers no reversal. */
const reversalSweep = (code1To10: string | null) => {
  const contextCode = code1To10 === 'S' || code1To10 === 'V' ? code1To10 : 'D'
  server.use(
    millContextWithBothTracks(contextCode, 'D'),
    http.get(SWEEP_URL, () =>
      HttpResponse.json(sweep({ statusCode1To10: code1To10, statusCode11: 'D' })),
    ),
  )
}

const mountAsAdmin = async (code1To10: string | null) => {
  reversalSweep(code1To10)
  renderAsAdmin(<CheckStatus />)
  expect(declaredRole()).toBe(ILCR_ROLES.admin)
  expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
}

/** Press one of the two 1–10 copies of a reversal (0 = above the region, 1 = below) and open its prompt. */
const pressReversal = async (
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  index: 0 | 1 = 0,
) => {
  await user.click(reversalButtons(label)[index])
  return within(await dialog())
}

const confirmReversal = async (
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  index: 0 | 1 = 0,
) => {
  const prompt = await pressReversal(user, label, index)
  await user.click(prompt.getByRole('button', { name: 'Yes' }))
}

/** A 200 body for one reversal, shaped as `SetTrackStatusResponse`. */
const reversalOk = (to: string, key: string, text: string) =>
  HttpResponse.json({ trackStatus: to, message: { key, text } })

describe('Reversal actions on the Schedules 1–10 track (Story 18.2)', () => {
  // ---- AC 1 / AC 2: the render gates -----------------------------------------------------------

  test.each(REVERSALS)(
    'CHK-016/018 S07: at $from an admin gets $label live in BOTH 1–10 bars, and never the other one',
    async ({ label, from, other }) => {
      await mountAsAdmin(from)

      const buttons = reversalButtons(label)
      expect(buttons).toHaveLength(2)
      for (const button of buttons) {
        // Live, with no hint: a wired reversal is an ordinary enabled control, as legacy's was.
        expect(button).toBeEnabled()
        expect(button).not.toHaveAttribute('aria-describedby')
      }
      expect(reversalButtons(other)).toHaveLength(0)
    },
  )

  test.each([
    ['D', 'the track is in Draft'],
    ['O', 'the track carries the dead O code'],
    [null, 'the wire omits statusCode entirely'],
  ])(
    'CHK-016/018 S07: neither reversal is in the DOM when %s (%s) — ABSENT, not disabled',
    async (code) => {
      await mountAsAdmin(code)

      for (const label of ['Set to Draft', 'Set to Submit']) {
        expect(screen.queryByRole('button', { name: label })).not.toBeInTheDocument()
      }
      // Positive control: the bars themselves are on the page, so "absent" is not "nothing rendered".
      expect(submitButtons()).toHaveLength(3)
      expect(verifiedButtons1To10()).toHaveLength(2)
    },
  )

  test.each(REVERSALS)(
    'CHK-016/018 S08: at $from the licensee gets no $label at all — admin-only, and hidden rather than greyed',
    async ({ from, label }) => {
      reversalSweep(from)
      renderAsSubmitter(<CheckStatus />)
      expect(declaredRole()).toBe(ILCR_ROLES.submitter)
      expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

      expect(screen.queryByRole('button', { name: label })).not.toBeInTheDocument()
      expect(submitButtons()).toHaveLength(3)
    },
  )

  test.each(REVERSALS)(
    'CHK-016/018 S07 (control): the SAME fixture that hides $label from the licensee shows it to an admin',
    async ({ from, label }) => {
      await mountAsAdmin(from)
      expect(reversalButtons(label)).toHaveLength(2)
    },
  )

  // ---- AC 3: the confirmation, verbatim --------------------------------------------------------

  test.each(REVERSALS)(
    'CHK-016/018 BR-06: $label opens its own prompt with legacy’s four literals and sends nothing yet',
    async ({ from, label, prompt, url, otherUrl }) => {
      const posted = vi.fn()
      await mountAsAdmin(from)
      server.use(
        http.post(url, () => {
          posted()
          return reversalOk('X', 'k', 'unused')
        }),
        http.post(otherUrl, () => {
          posted()
          return reversalOk('X', 'k', 'unused')
        }),
      )
      const user = userEvent.setup()

      const shown = await pressReversal(user, label)

      // The apostrophe is ASCII and undoubled, and Set to Submit says SUBMIT, not SUBMITTED.
      expect(shown.getByText(prompt, verbatim)).toBeInTheDocument()
      expect(shown.getByText('Confirmation Required')).toBeInTheDocument()
      expect(shown.getByRole('button', { name: 'Yes' })).toBeInTheDocument()
      expect(shown.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
      // Opening asks the server nothing: legacy's button was type="button" whose whole behaviour was
      // `confirmBackToDraft.show()`, and the action hung off the dialog's Yes.
      expect(posted).not.toHaveBeenCalled()
    },
  )

  test.each(REVERSALS)(
    'AC 3: the other transitions’ prompts are never the one $label shows',
    async ({ from, label, prompt }) => {
      await mountAsAdmin(from)
      const user = userEvent.setup()

      const shown = await pressReversal(user, label)

      const wrong = [
        CONFIRM_SUBMIT_1_TO_10,
        CONFIRM_SET_TO_DRAFT_1_TO_10,
        CONFIRM_SET_TO_SUBMIT_1_TO_10,
      ].filter((text) => text !== prompt)
      for (const text of wrong) {
        expect(shown.queryByText(text, verbatim)).toBeNull()
      }
      // Positive control: the right one IS there, so the absences are not a dead query.
      expect(shown.getByText(prompt, verbatim)).toBeInTheDocument()
    },
  )

  // ---- AC 4: Cancel ----------------------------------------------------------------------------

  test.each(REVERSALS)(
    'CHK-016/018 S02: Cancel, the close control and Escape each dismiss $label with NO request, and focus returns to the button',
    async ({ from, label, url }) => {
      const posted = vi.fn()
      await mountAsAdmin(from)
      server.use(
        http.post(url, () => {
          posted()
          return reversalOk('X', 'k', 'unused')
        }),
      )
      const user = userEvent.setup()

      const trigger = () => reversalButtons(label)[0]

      // All FOUR dismissals AC 4 names, and each one puts focus back on the button that opened the
      // prompt — the modal is unmounted on close, so without that the user lands on <body>.
      await user.click(trigger())
      await user.click(within(await dialog()).getByRole('button', { name: 'Cancel' }))
      await waitFor(() => expect(openDialog()).toBeNull())
      expect(trigger()).toHaveFocus()

      await user.click(trigger())
      await user.click(within(await dialog()).getByRole('button', { name: 'Close' }))
      await waitFor(() => expect(openDialog()).toBeNull())
      expect(trigger()).toHaveFocus()

      await user.click(trigger())
      within(await dialog())
        .getByRole('button', { name: 'Yes' })
        .focus()
      await user.keyboard('{Escape}')
      await waitFor(() => expect(openDialog()).toBeNull())
      expect(trigger()).toHaveFocus()

      // ⚠️ The backdrop does NOT dismiss, and that is correct. AC 4 lists it with the other three,
      // but Carbon refuses outside-click on a TRANSACTIONAL modal — one with a `ModalFooter` — and
      // warns if you force it (`ComposedModal.js:173` gates on `isPassive`; `:219` warns that
      // `preventCloseOnClickOutside` "should not be `false` when `<ModalFooter>` is present.
      // Transactional, non-passive Modals should not be dissmissable by clicking outside"). A
      // confirmation whose answer commits a status change is exactly that. Pinned as a refusal so a
      // future reading of AC 4 does not "fix" it into an accidental dismissal.
      await user.click(trigger())
      await dialog()
      fireEvent.click(screen.getByRole('presentation'))
      await drainEventLoop()
      expect(openDialog()).not.toBeNull()
      await user.click(within(await dialog()).getByRole('button', { name: 'Cancel' }))
      await waitFor(() => expect(openDialog()).toBeNull())
      expect(trigger()).toHaveFocus()

      // Proved by the absence of the POST, never by the prompt text disappearing: Carbon keeps a
      // closed Modal's content mounted, so a text assertion would pass whatever Cancel did.
      await drainEventLoop()
      expect(posted).not.toHaveBeenCalled()
      expect(screen.queryByText('Success')).not.toBeInTheDocument()
      expect(screen.queryByText('Action failed')).not.toBeInTheDocument()
      // Nothing moved: the reversal is still offered at its own status.
      expect(reversalButtons(label)).toHaveLength(2)
      expect(trigger()).toBeEnabled()

      // Positive control: the same handler DOES fire on Yes.
      await confirmReversal(user, label)
      await waitFor(() => expect(posted).toHaveBeenCalledTimes(1))
    },
  )

  // ---- AC 5: the transition --------------------------------------------------------------------

  test.each(REVERSALS)(
    'CHK-016/018 S01: Yes issues ONE POST to the right path with no body, renders the server’s own text, and the bar swaps over',
    async ({ from, to, label, url, otherUrl, success, successKey, becomes }) => {
      const seen: string[] = []
      const bodies: string[] = []
      const wrongPath = vi.fn()
      let moved = false
      let sweeps = 0
      server.use(
        // STATEFUL, both reads. A static `/mill-context` would leave the tombstone saying "Submitted"
        // after a Set to Draft and no assertion here would notice — which is exactly what Task 9's
        // warning is about.
        http.get(MILL_CONTEXT, ({ request }) => {
          const params = new URL(request.url).searchParams
          const code = moved ? to : from
          return HttpResponse.json({
            millId: Number(params.get('millId')),
            millNumber: String(params.get('millId')),
            millName: 'Test Mill',
            reportYear: Number(params.get('year')),
            schedules1To10Status: { code, description: STATUS_WORD[code], date: '2017-01-01' },
            schedule11Status: { code: 'D', description: 'Draft', date: '2017-02-02' },
            millViewable: true,
          })
        }),
        http.get(SWEEP_URL, () => {
          sweeps += 1
          return HttpResponse.json(sweep({ statusCode1To10: moved ? to : from, statusCode11: 'D' }))
        }),
        http.post(otherUrl, () => {
          wrongPath()
          return reversalOk('X', 'k', 'wrong path')
        }),
        http.post(url, async ({ request }) => {
          const params = new URL(request.url).searchParams
          seen.push(`${params.get('millId')}/${params.get('year')}`)
          bodies.push(await request.text())
          moved = true
          return reversalOk(to, successKey, success)
        }),
      )
      renderAsAdmin(<CheckStatus />)
      expect(declaredRole()).toBe(ILCR_ROLES.admin)
      expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
      expect(
        await screen.findByText(`Sch 1-10 - Status: ${STATUS_WORD[from]} - Date: 2017-01-01`),
      ).toBeInTheDocument()
      const settled = sweeps
      const user = userEvent.setup()

      await confirmReversal(user, label, 1) // the BOTTOM bar — legacy's scroll anchor existed for it

      // The server's words, verbatim — including Set to Submit's reuse of the SUBMIT success text.
      expect(await screen.findByText(success, verbatim)).toBeInTheDocument()
      expect(screen.getByText('Success')).toBeInTheDocument()
      // Exactly one call, to THIS path, carrying the working context as query params and no body.
      expect(seen).toEqual(['13050/2017'])
      expect(bodies).toEqual([''])
      expect(wrongPath).not.toHaveBeenCalled()
      // Focus moved to the banner, which is a programmatic target only.
      const column = bannerColumn(success)
      expect(column).not.toBeNull()
      await waitFor(() => expect(column).toHaveFocus())
      expect(column).toHaveAttribute('tabindex', '-1')

      // The tombstone's own line moved — the visible half of the transition, and the one a
      // button-only assertion cannot see.
      expect(
        await screen.findByText(`Sch 1-10 - Status: ${STATUS_WORD[to]} - Date: 2017-01-01`),
      ).toBeInTheDocument()
      expect(
        screen.queryByText(`Sch 1-10 - Status: ${STATUS_WORD[from]} - Date: 2017-01-01`),
      ).toBeNull()

      // The re-sweep landed and the gates re-evaluated against the SERVER's new status, not a local
      // copy of it: the button that was pressed is gone, and the one the new status offers is there.
      await waitFor(() => expect(reversalButtons(label)).toHaveLength(0))
      // EXACTLY one re-read (AC 5 says "bumps `reloadToken` once"). A double bump also swaps the
      // buttons over, so counting is the only thing that tells the two apart.
      await drainEventLoop()
      expect(sweeps - settled).toBe(1)
      if (becomes) {
        await waitFor(() => expect(reversalButtons(becomes)).toHaveLength(2))
      } else {
        expect(reversalButtons('Set to Draft')).toHaveLength(0)
        expect(reversalButtons('Set to Submit')).toHaveLength(0)
      }
    },
  )

  // ---- AC 6 / AC 7 / AC 8: refusals ------------------------------------------------------------

  test.each([
    [400, REVERSAL_ERR_001, false],
    [403, FORBIDDEN, false],
    [404, NOT_FOUND, false],
    [409, NOT_ACTIVE, true],
    [500, PERSISTENCE_MSG, false],
  ] as const)(
    'CHK-016/018 S03: a %s renders the server’s detail verbatim in the banner, leaves the verdicts standing, and re-sweeps only on a conflict',
    async (status, detail, resyncs) => {
      let sweeps = 0
      server.use(
        millContextWithBothTracks('S', 'D'),
        http.get(SWEEP_URL, () => {
          sweeps += 1
          return HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' }))
        }),
        http.post(SET_TO_DRAFT_URL, () => problem(status, detail)),
      )
      renderAsAdmin(<CheckStatus />)
      expect(declaredRole()).toBe(ILCR_ROLES.admin)
      expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
      const settled = sweeps
      const user = userEvent.setup()

      await confirmReversal(user, 'Set to Draft')

      // ERR-001's trailing space is real and must survive to the screen, so this one is untrimmed.
      expect(await screen.findByText(detail, verbatimUntrimmed)).toBeInTheDocument()
      expect(screen.getByText('Action failed')).toBeInTheDocument()
      // A refusal is a BANNER. The page is not blanked and the good sweep result is still on screen.
      expect(screen.getAllByText(MET_TEXT)).toHaveLength(12)
      expect(screen.queryByText(LOAD_FAILED)).not.toBeInTheDocument()
      expect(region1To10()).toBeInTheDocument()
      // Exactly once, in the banner. `renderScheduleLoadState` owns the no-context page and shows
      // this very ERR-001 text, so a second copy would mean the load state had taken the page over
      // — which is the failure this arm exists to catch, and the reason it counts rather than
      // asserting the text is absent.
      expect(screen.getAllByText(detail, verbatimUntrimmed)).toHaveLength(1)

      // Only a 409 says "your picture of the state is stale", so only a 409 re-reads.
      await drainEventLoop()
      expect(sweeps - settled).toBe(resyncs ? 1 : 0)
    },
  )

  test.each(REVERSALS)(
    'CHK-016/018 S03: $label renders its own gate refusal and its own wrong-status refusal, each verbatim',
    async ({ from, label, url, gate, stale }) => {
      for (const detail of [gate, stale]) {
        reversalSweep(from)
        server.use(http.post(url, () => problem(409, detail)))
        const view = renderAsAdmin(<CheckStatus />)
        expect(declaredRole()).toBe(ILCR_ROLES.admin)
        expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
        const user = userEvent.setup()

        await confirmReversal(user, label)

        expect(await screen.findByText(detail, verbatim)).toBeInTheDocument()
        // NOTE: deviation (V) — that the refusal does not reuse legacy's "The report cannot be
        // submitted." wording — is pinned on the SERVER, at `SetToDraftIT.java:496` /
        // `SetToSubmitIT.java:447`. It cannot be pinned here: this page renders whatever `detail`
        // the fixture sends, so an assertion about the wording would only be testing the mock.
        view.unmount()
      }
    },
  )

  test.each(REVERSALS)(
    'AC 7: a failure carrying no ProblemDetail detail falls back to $label’s own client string',
    async ({ from, label, url, fallback }) => {
      await mountAsAdmin(from)
      // Boot's own 401 body — a JSON error with everything EXCEPT `detail`. Keying the fallback on
      // "was this a network error?" instead of on the missing field renders the literal `undefined`.
      server.use(
        http.post(url, () =>
          HttpResponse.json(
            { timestamp: '2026-09-23T00:00:00Z', status: 401, error: 'Unauthorized', path: '/x' },
            { status: 401 },
          ),
        ),
      )
      const user = userEvent.setup()

      await confirmReversal(user, label)

      expect(await screen.findByText(fallback, verbatim)).toBeInTheDocument()
      expect(screen.getByText('Action failed')).toBeInTheDocument()
      expect(screen.queryByText('undefined')).not.toBeInTheDocument()
    },
  )

  // ---- AC 9: the in-flight lock ----------------------------------------------------------------

  test.each(REVERSALS)(
    'AC 9: while $label is in flight BOTH 1–10 copies are greyed, a second click issues nothing, and the lock always comes off',
    async ({ from, to, label, url, success, successKey }) => {
      let release!: () => void
      const held = new Promise<void>((resolve) => {
        release = resolve
      })
      const posted = vi.fn()
      let moved = false
      server.use(
        millContextWithBothTracks(from, 'D'),
        http.get(SWEEP_URL, () =>
          HttpResponse.json(sweep({ statusCode1To10: moved ? to : from, statusCode11: 'D' })),
        ),
        http.post(url, async () => {
          posted()
          await held
          moved = true
          return reversalOk(to, successKey, success)
        }),
      )
      renderAsAdmin(<CheckStatus />)
      expect(declaredRole()).toBe(ILCR_ROLES.admin)
      expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
      const user = userEvent.setup()

      await confirmReversal(user, label)

      // Both bars grey together: they render from one action object, so one lock covers the pair.
      await waitFor(() => {
        const buttons = reversalButtons(label)
        expect(buttons).toHaveLength(2)
        for (const button of buttons) {
          expect(button).toBeDisabled()
        }
      })
      // Pressing either copy now does nothing at all: a disabled Carbon button swallows the click,
      // so no prompt opens. (`busyRef` guards the prompt's Yes, not the button — a live button would
      // have opened the prompt, not POSTed. The same-tick double-Yes arm below is what exercises it.)
      for (const button of reversalButtons(label)) {
        await user.click(button)
      }
      expect(openDialog()).toBeNull()
      expect(posted).toHaveBeenCalledTimes(1)

      release()
      expect(await screen.findByText(success, verbatim)).toBeInTheDocument()
      await waitFor(() => expect(reversalButtons(label)).toHaveLength(0))
      await drainEventLoop()
      expect(posted).toHaveBeenCalledTimes(1)
    },
  )

  test('AC 9: the lock is released even when the POST fails, so the reversal can be retried', async () => {
    let attempts = 0
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' })),
      ),
      http.post(SET_TO_DRAFT_URL, () => {
        attempts += 1
        return problem(500, PERSISTENCE_MSG)
      }),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    await confirmReversal(user, 'Set to Draft')
    expect(await screen.findByText(PERSISTENCE_MSG, verbatim)).toBeInTheDocument()

    await waitFor(() => {
      for (const button of reversalButtons('Set to Draft')) {
        expect(button).toBeEnabled()
      }
    })
    await confirmReversal(user, 'Set to Draft')
    await waitFor(() => expect(attempts).toBe(2))
  })

  // ---- AC 10 / AC 11: the working context ------------------------------------------------------

  test('AC 9 / AC 10: a reversal that lands after the mill/year flips writes nothing AND leaves the new context able to act', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    const seen: string[] = []
    let first = true
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, ({ request }) => {
        // Echo BOTH halves of the working context: the hook refuses a body that does not match the
        // request it answers, and a sweep echoing only the mill fails the new context's read.
        const params = new URL(request.url).searchParams
        return HttpResponse.json(
          sweep({
            millId: Number(params.get('millId')),
            year: Number(params.get('year')),
            statusCode1To10: 'S',
            statusCode11: 'D',
          }),
        )
      }),
      http.post(SET_TO_DRAFT_URL, async ({ request }) => {
        seen.push(String(new URL(request.url).searchParams.get('millId')))
        if (first) {
          first = false
          await held
        }
        return reversalOk('D', 'sch1-10DraftMsg', DRAFT_MSG)
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<ContextSwitchHarness />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

    await confirmReversal(user, 'Set to Draft')
    await user.click(screen.getByRole('button', { name: 'change' }))
    // Let the NEW context's sweep settle BEFORE the old response lands. Without this wait the page is
    // still on its loading state when the stale `.then` fires, and "no banner is on screen" would be
    // true whether or not the guard ran — the arm would pass against a page that had no banner
    // anywhere to show.
    await waitFor(() => expect(screen.getAllByText(MET_TEXT)).toHaveLength(12))

    // The new context's buttons stay GREYED while the old request is unresolved. `busyRef` is what
    // the confirm handler checks and no context change clears it, so a button re-enabled here would
    // be live with a Yes that silently does nothing — worse than the greying it replaced.
    expect(reversalButtons('Set to Draft')).toHaveLength(2)
    for (const button of reversalButtons('Set to Draft')) {
      expect(button).toBeDisabled()
    }

    release()
    await drainEventLoop()

    // The old context's success never reaches the new report's screen.
    expect(screen.queryByText(DRAFT_MSG, verbatim)).not.toBeInTheDocument()
    expect(screen.queryByText('Success')).not.toBeInTheDocument()

    // And the lock came off — the buttons are live again, and, the half no greying can prove, the
    // ref is clear too: gate the release on `isCurrent()` and this second POST is never sent.
    await waitFor(() => {
      for (const button of reversalButtons('Set to Draft')) {
        expect(button).toBeEnabled()
      }
    })
    await confirmReversal(user, 'Set to Draft')
    await waitFor(() => expect(seen).toEqual(['13050', '999']))
    expect(await screen.findByText(DRAFT_MSG, verbatim)).toBeInTheDocument()
  })

  test('AC 9: the reversal stays greyed through the 409 re-sweep, not merely until the POST answers', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    let sweeps = 0
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, async () => {
        sweeps += 1
        // The re-sweep the 409 triggers is held open; the first (mount) sweep answers at once.
        if (sweeps > 1) {
          await held
        }
        return HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' }))
      }),
      http.post(SET_TO_DRAFT_URL, () => problem(409, NOT_ACTIVE)),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    await confirmReversal(user, 'Set to Draft')

    // The POST has answered — its refusal is already on screen — but the re-read it triggered has
    // not. Through that window the status on screen is the one that has just been contradicted, so
    // the button must stay out of action: `busy` is `reversing || isReloading`, not `reversing`.
    expect(await screen.findByText(NOT_ACTIVE, verbatim)).toBeInTheDocument()
    await waitFor(() => expect(sweeps).toBe(2))
    for (const button of reversalButtons('Set to Draft')) {
      expect(button).toBeDisabled()
    }

    release()

    // Positive control: once the re-sweep lands the same button comes back, so "disabled" above was
    // the refresh window and not a lock that never releases.
    await waitFor(() => {
      const buttons = reversalButtons('Set to Draft')
      expect(buttons).toHaveLength(2)
      for (const button of buttons) {
        expect(button).toBeEnabled()
      }
    })
  })

  test('AC 11: switching mill/year abandons a pending reversal prompt and clears a settled outcome', async () => {
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' })),
      ),
      http.post(SET_TO_DRAFT_URL, () => problem(409, NOT_ACTIVE)),
    )
    const user = userEvent.setup()
    renderAsAdmin(<ContextSwitchHarness />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

    // A settled outcome first, then a prompt left open over it.
    await confirmReversal(user, 'Set to Draft')
    expect(await screen.findByText(NOT_ACTIVE, verbatim)).toBeInTheDocument()
    await pressReversal(user, 'Set to Draft')

    await user.click(screen.getByRole('button', { name: 'change' }))

    await waitFor(() => expect(screen.queryByText(NOT_ACTIVE, verbatim)).not.toBeInTheDocument())
    expect(screen.queryByText('Action failed')).not.toBeInTheDocument()
    // The prompt is gone rather than re-shown over a report the user never asked about. Carbon keeps
    // a closed Modal mounted, so this asserts the DIALOG ROLE is absent, not that the text is.
    await waitFor(() => expect(openDialog()).toBeNull())
  })

  test('AC 9: two Yes presses in ONE tick issue a single POST — the synchronous busyRef, not the disabled button', async () => {
    const posted = vi.fn()
    let moved = false
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: moved ? 'D' : 'S', statusCode11: 'D' })),
      ),
      http.post(SET_TO_DRAFT_URL, () => {
        posted()
        moved = true
        return reversalOk('D', 'sch1-10DraftMsg', DRAFT_MSG)
      }),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    const prompt = await pressReversal(user, 'Set to Draft')
    const yes = prompt.getByRole('button', { name: 'Yes' })

    // Both clicks land before React re-renders, so the modal is still mounted for the second and
    // `reversing` is still false when it runs — a state flag would let both through. `fireEvent`
    // rather than `userEvent` precisely because it does NOT await between the two.
    fireEvent.click(yes)
    fireEvent.click(yes)

    await waitFor(() => expect(posted).toHaveBeenCalledTimes(1))
    expect(await screen.findByText(DRAFT_MSG, verbatim)).toBeInTheDocument()
    await drainEventLoop()
    expect(posted).toHaveBeenCalledTimes(1)
    expect(screen.getAllByText(DRAFT_MSG, verbatim)).toHaveLength(1)
  })

  test('AC 9: a Yes refused by the in-flight lock still CLOSES its prompt rather than leaving a dead button', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    const verified = vi.fn()
    const reversed = vi.fn()
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' })),
      ),
      http.post(`${API}/v1/check-status/verify`, async () => {
        verified()
        await held
        return HttpResponse.json({
          trackStatus: 'V',
          message: {
            key: 'sch1-10VerifiedMsg',
            text: 'Schedules 1-10 status has been updated to verified.',
          },
        })
      }),
      http.post(SET_TO_DRAFT_URL, () => {
        reversed()
        return reversalOk('D', 'sch1-10DraftMsg', DRAFT_MSG)
      }),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    // At Submitted an admin is offered BOTH Verified and Set to Draft — the state Story 18.2 created.
    expect(reversalButtons('Set to Draft')).toHaveLength(2)
    await user.click(verifiedButtons1To10()[0])
    await user.click(within(await dialog()).getByRole('button', { name: 'Yes' }))
    await waitFor(() => expect(verified).toHaveBeenCalledTimes(1))

    // One in-flight term covers all three wired transitions, so the reversal greys with Verified
    // rather than sitting live over a transition that is about to move the track underneath it.
    await waitFor(() => {
      for (const button of reversalButtons('Set to Draft')) {
        expect(button).toBeDisabled()
      }
    })

    release()
    expect(
      await screen.findByText('Schedules 1-10 status has been updated to verified.', verbatim),
    ).toBeInTheDocument()
    // The reversal never fired: not silently swallowed at the busyRef guard, never offered at all.
    expect(reversed).not.toHaveBeenCalled()
  })

  test('AC 9 (mirror): while a reversal is in flight the Verified pair greys too — one lock, both directions', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    const verified = vi.fn()
    let moved = false
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: moved ? 'D' : 'S', statusCode11: 'D' })),
      ),
      http.post(`${API}/v1/check-status/verify`, () => {
        verified()
        return HttpResponse.json({
          trackStatus: 'V',
          message: { key: 'sch1-10VerifiedMsg', text: 'unused' },
        })
      }),
      http.post(SET_TO_DRAFT_URL, async () => {
        await held
        moved = true
        return reversalOk('D', 'sch1-10DraftMsg', DRAFT_MSG)
      }),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    // Both are live at Submitted (positive control for the greying that follows).
    for (const button of verifiedButtons1To10()) {
      expect(button).toBeEnabled()
    }

    await confirmReversal(user, 'Set to Draft')

    // Verified greys on the REVERSAL's flight. Without the shared term its Yes would reach the
    // busyRef guard and do nothing, and then — once the track had moved to Draft — send a verify the
    // server can only refuse, painting over the reversal's success.
    await waitFor(() => {
      const buttons = verifiedButtons1To10()
      expect(buttons).toHaveLength(2)
      for (const button of buttons) {
        expect(button).toBeDisabled()
      }
    })
    for (const button of verifiedButtons1To10()) {
      await user.click(button)
    }
    expect(openDialog()).toBeNull()
    expect(verified).not.toHaveBeenCalled()

    release()
    expect(await screen.findByText(DRAFT_MSG, verbatim)).toBeInTheDocument()
    await drainEventLoop()
    expect(verified).not.toHaveBeenCalled()
  })

  test('AC 6 / AC 10: a reversal REFUSAL that lands after the mill/year flips paints nothing and re-sweeps nothing', async () => {
    let release!: () => void
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    let sweeps = 0
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, ({ request }) => {
        sweeps += 1
        const params = new URL(request.url).searchParams
        return HttpResponse.json(
          sweep({
            millId: Number(params.get('millId')),
            year: Number(params.get('year')),
            statusCode1To10: 'S',
            statusCode11: 'D',
          }),
        )
      }),
      http.post(SET_TO_DRAFT_URL, async () => {
        await held
        return problem(409, NOT_ACTIVE)
      }),
    )
    const user = userEvent.setup()
    renderAsAdmin(<ContextSwitchHarness />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)

    await confirmReversal(user, 'Set to Draft')
    await user.click(screen.getByRole('button', { name: 'change' }))
    await waitFor(() => expect(screen.getAllByText(MET_TEXT)).toHaveLength(12))
    const settled = sweeps

    release()
    await drainEventLoop()

    // The `.catch` is guarded too, not just the `.then`: the old context's refusal never reaches the
    // new report's banner, and its 409 never triggers a re-sweep against a context it knows nothing
    // about. Every previous flip arm released a SUCCESS, so this path had no observer.
    expect(screen.queryByText(NOT_ACTIVE, verbatim)).not.toBeInTheDocument()
    expect(screen.queryByText('Action failed')).not.toBeInTheDocument()
    expect(sweeps).toBe(settled)
  })

  test('AC 5 / D3: opening a reversal prompt clears the standing banner, and Cancel leaves it cleared', async () => {
    server.use(
      millContextWithBothTracks('S', 'D'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'S', statusCode11: 'D' })),
      ),
      http.post(SET_TO_DRAFT_URL, () => problem(409, GATE_DRAFT_MSG)),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    await confirmReversal(user, 'Set to Draft')
    expect(await screen.findByText(GATE_DRAFT_MSG, verbatim)).toBeInTheDocument()

    // `requestReversal` follows verify, not submit: the outcome clears when the prompt OPENS, so a
    // settled banner never sits under a fresh question looking like its answer. Asserted while the
    // dialog is still open — after a Cancel the render-phase reset would confound it.
    await pressReversal(user, 'Set to Draft')
    expect(openDialog()).not.toBeNull()
    expect(screen.queryByText(GATE_DRAFT_MSG, verbatim)).not.toBeInTheDocument()
    expect(screen.queryByText('Action failed')).not.toBeInTheDocument()

    // ⚠️ And it stays cleared on Cancel — legacy's `p:messages` survived a cancelled dialog, ours
    // does not. Ratified as D3 (follow verify's shape); recorded here so the trade is visible: the
    // gate refusal naming what to fix is the one most worth keeping, and it is the one lost.
    await user.click(within(await dialog()).getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(openDialog()).toBeNull())
    expect(screen.queryByText(GATE_DRAFT_MSG, verbatim)).not.toBeInTheDocument()
  })

  test('D8 / BR-07: Schedule 11 at Submitted gets its own greyed Set to Draft, and it sends nothing', async () => {
    const postSpy = vi.spyOn(apiService.getAxiosInstance(), 'post')
    server.use(
      millContextWithBothTracks('D', 'S'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: 'D', statusCode11: 'S' })),
      ),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    // The OTHER half of D8 — every other HINT_NOT_WIRED arm is Schedule 11 at Verified, so the
    // `setToDraft` branch of the 11 track had no observer at all.
    const sch11 = within(item(SCHEDULE_TITLES['11']))
    const button = sch11.getByRole('button', { name: 'Set to Draft' })
    expect(button).toBeDisabled()
    expect(hintFor(button)).toHaveTextContent(HINT_NOT_WIRED)
    expect(sch11.queryByRole('button', { name: 'Set to Submit' })).toBeNull()

    await user.click(button)
    expect(openDialog()).toBeNull()
    expect(postSpy).not.toHaveBeenCalled()

    // Positive control: the 1–10 track is at Draft, so its bars offer no reversal to confuse this.
    expect(reversalButtons('Set to Draft')).toHaveLength(0)
  })

  // ---- AC 12: Schedule 11 ----------------------------------------------------------------------

  test('CHK-016/018 BR-07: a 1–10 reversal leaves Schedule 11’s bar and status line untouched', async () => {
    let moved = false
    server.use(
      millContextWithBothTracks('S', 'V'),
      http.get(SWEEP_URL, () =>
        HttpResponse.json(sweep({ statusCode1To10: moved ? 'D' : 'S', statusCode11: 'V' })),
      ),
      http.post(SET_TO_DRAFT_URL, () => {
        moved = true
        return reversalOk('D', 'sch1-10DraftMsg', DRAFT_MSG)
      }),
    )
    renderAsAdmin(<CheckStatus />)
    expect(declaredRole()).toBe(ILCR_ROLES.admin)
    expect((await screen.findAllByText(MET_TEXT)).length).toBe(12)
    const user = userEvent.setup()

    // Schedule 11 is at Verified, so legacy renders ITS Set to Submit inside the tab — greyed, since
    // Epic 26 owns that track's transition. It must not go live because the 1–10 pair did.
    const sch11SetToSubmit = () =>
      within(item(SCHEDULE_TITLES['11'])).getByRole('button', { name: 'Set to Submit' })
    expect(sch11SetToSubmit()).toBeDisabled()
    expect(hintFor(sch11SetToSubmit())).toHaveTextContent(HINT_NOT_WIRED)

    await confirmReversal(user, 'Set to Draft')
    expect(await screen.findByText(DRAFT_MSG, verbatim)).toBeInTheDocument()
    await waitFor(() => expect(reversalButtons('Set to Draft')).toHaveLength(0))

    // Schedule 11's own bar and status line are exactly as they were.
    expect(sch11SetToSubmit()).toBeDisabled()
    expect(
      within(item(SCHEDULE_TITLES['11'])).queryByRole('button', { name: 'Set to Draft' }),
    ).toBeNull()
    expect(screen.getByText('Sch 11 - Status: Verified - Date: 2017-02-02')).toBeInTheDocument()
  })
})
