import type { ReactNode } from 'react'
import type Schedule2Response from '@/interfaces/Schedule2Response'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import type { IlcrRole } from '@/context/auth/mockUsers'
import { ILCR_ROLES } from '@/context/auth/mockUsers'
import {
  declaredRole,
  fireEvent,
  render,
  renderAsAdmin,
  renderAsSubmitter,
  screen,
  waitFor,
  within,
} from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider (AppProviders has none). Mock the router
// exactly like Schedule1.test.tsx; stub Link as a passthrough in case it renders.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule2 from '@/components/schedule2'
import MillYearProvider from '@/context/millYear/MillYearProvider'

const URL = 'http://localhost:3000/api/v1/schedule2'
const CHECK_URL = 'http://localhost:3000/api/v1/schedule2/check-status'

const block = (volume: number | null, cost: number | null, perUnit: number | null) => ({
  volume,
  cost,
  perUnit,
})

const schedule2Doc = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  revisionCount: 3,
  comments: 'Seed comment for 514/2021',
  purchasedLogCost: block(1000, 50000, 50.0),
  purchasedWoodOverhead: block(1000, 2000, 2.0),
  subtotal: block(1000, 52000, 52.0),
  lessLogSales: block(200, 8000, 40.0),
  netPurchased: block(800, 44000, 55.0),
  totalCompanyLogging: block(2000, 90000, 45.0),
  totalAverage: block(2800, 134000, 47.86),
}

// The SERVED body of an unsaved (or just-deleted) Schedule 2 — copied from the wire, not imagined.
// Captured 2026-08-24 from the running backend for mill 514 / 2021, a mill-year that has never had a
// Schedule 2 saved:
//
//   {"millId":16050,"year":2021,"trackStatus":"D","editable":true,"purchasedLogCost":{},
//    "purchasedWoodOverhead":{},"subtotal":{},"lessLogSales":{},"netPurchased":{},
//    "totalCompanyLogging":{},"totalAverage":{}}
//
// Under the app-wide Jackson `default-property-inclusion: non_null` EVERY null is dropped, so there
// is no `revisionCount` key, no `comments` key, and each CostBlock arrives as `{}` rather than as
// three nulls. Defect #292 was exactly this gap between fixture and wire: the delete test asserted
// the right thing ("Delete is disabled") and passed from the day it was written, because its fixture
// set `revisionCount: null` — a value the API cannot emit — while the real gate
// (`revisionCount !== null`) was inert against the `undefined` the app actually receives.
//
// So: do not add keys back to make a test convenient. If a reader needs a value here that the server
// would omit, the reader is what needs fixing. Typed as `Schedule2Response` so the interface
// constrains the fixture instead of merely describing it.
const unsavedDoc: Schedule2Response = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  purchasedLogCost: {},
  purchasedWoodOverhead: {},
  subtotal: {},
  lessLogSales: {},
  netPurchased: {},
  totalCompanyLogging: {},
  totalAverage: {},
}

// The action-bar buttons only (the confirm modal renders a "Delete" button of its own, and the bar
// renders twice — above and below the table). `expected` is required, not optional: `getAllByRole`
// throws on zero matches but this `.filter` returns [] silently, so a bare `forEach(expect(...))` over
// the result would pass vacuously if the modifier class were ever renamed — which `index.scss` and the
// e2e page object both key off, so it is a live possibility (code-review finding).
const actionBarButtons = (name: RegExp, expected: number) => {
  const found = screen
    .getAllByRole('button', { name })
    .filter((b) => b.closest('.schedule-2__actions'))
  expect(found).toHaveLength(expected)
  return found
}

// Carbon's Modal stays mounted and toggles `is-visible`, so a closed confirm dialog is still in the
// DOM with role="dialog" — assert on openness, never on absence.
const confirmModalOpen = () =>
  Boolean(document.querySelector('.cds--modal')?.classList.contains('is-visible'))

const problemHandler = (status: number, detail: string) =>
  http.get(
    URL,
    () =>
      new HttpResponse(JSON.stringify({ detail }), {
        status,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
  )

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

// The failure shapes that reach the page's OWN fallback strings rather than a server-supplied
// message. Both sites read `extractDetail(err) || fallback` (`index.tsx:60`,
// `useScheduleBanners.ts:78`) and `extractDetail` returns `response?.data?.detail` or `undefined`
// (`utils/error.ts`), so the fallback is what the user sees whenever that expression is falsy.
//
// FOUR shapes, and the reason is NOT that they take different code paths — an earlier version of
// this comment claimed that and was wrong (defect #298 code review). `extractDetail` has exactly two
// paths: the network shape fails the `'response' in error` guard and returns `undefined`; EVERY other
// shape here passes that guard and returns `undefined` (or `''`) from the same expression. They are
// listed separately because they are different things a REAL deployment does — a dropped connection,
// an empty-bodied 500, a gateway that answers in its own JSON dialect, and a backend that sets the
// key but leaves it blank — and each is a shape a future refactor could break independently.
//
// The blank-detail shape is the load-bearing one. Without it the whole set produces only
// `undefined`, which is the half of `||` that `??` ALSO satisfies — so the suite went green with
// both sites swapped to `??`, and a present-but-empty `detail` would have rendered a blank subtitle:
// the exact defect this file exists to prevent. Note `schedule6/index.tsx:163` already uses `??` for
// the identical concept, so "harmonise Schedule 2 with the reference implementation" is a realistic
// edit, and `@typescript-eslint/prefer-nullish-coalescing` recommends it. This shape is what makes
// that edit fail loudly. Do not remove it.
//
// The two helpers above cannot express any of these — both REQUIRE a non-empty detail — which is
// part of why these branches went unexercised through 25 tests.
const NETWORK_FAILURE = 'a dropped connection' as const
const EMPTY_500 = 'an empty-bodied 500' as const
const GATEWAY_NO_DETAIL = 'a gateway problem+json with no detail key' as const
const BLANK_DETAIL = 'a problem+json whose detail is blank' as const

const detailLessFailures: [string, () => Response][] = [
  [NETWORK_FAILURE, () => HttpResponse.error()],
  [EMPTY_500, () => new HttpResponse(null, { status: 500 })],
  [
    GATEWAY_NO_DETAIL,
    () =>
      new HttpResponse(JSON.stringify({ title: 'Bad Gateway' }), {
        status: 502,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
  ],
  [BLANK_DETAIL, () => problemBody(500, '')],
]

// Delete runs the SAME four shapes as load. It used to run a subset — first `slice(0, 2)`, which tied
// delete's coverage to this array's order, then a filter excluding the gateway body on the grounds
// that it reaches `extractDetail` identically (measured, and true). PR #364 review (SScholefield)
// asked for the 502 on the write path anyway, and that is the right call for a reason the "no distinct
// path" argument missed: the equivalence holds for `extractDetail`, but a write reaches it through
// `useScheduleMutations.remove` → `useScheduleBanners.run`, so the claim being leaned on was about a
// function two layers below the one under test. Running all four costs one extra modal round-trip and
// retires both the subset and the argument.
const detailLessDeleteFailures = detailLessFailures

// Every notification currently rendered, as {kind, title, subtitle}. Asserting the whole set rather
// than `findByText` on a string is deliberate, for three reasons the #298 review found the hard way:
//
//   1. The load panel's TITLE is `Unable to load Schedule 2` and its subtitle is the fallback
//      `Unable to load Schedule 2.` — the same words, one full stop apart (`index.tsx:248`). A text
//      query cannot tell them apart reliably.
//   2. `kind` carries severity, and `NotificationColumn` documents severity as a WCAG 2.1 AA
//      contract borne by BOTH the kind and an explicit title word. Flipping both fallbacks to
//      `kind="success"` left the suite green, so nothing pinned the one thing these banners exist
//      to get right in the worst case.
//   3. Asserting the COUNT distinguishes "the error state rendered" from "nothing rendered at all".
//      An emptied fallback makes `errorDetail` falsy, so `if (errorDetail)` is skipped and the page
//      returns `null` from `if (!data)` — a blank white page, not an empty subtitle. Negative
//      assertions ("no Save button") pass against that blank page and cannot see the regression.
const notifications = () =>
  Array.from(document.querySelectorAll('.cds--inline-notification')).map((el) => ({
    // Carbon also emits `--low-contrast` on these, so match the kind explicitly rather than
    // globbing the modifier (a `(\w+)` glob picks up "low" from "low-contrast").
    kind: (['error', 'warning', 'info', 'success'] as const).find((k) =>
      el.classList.contains(`cds--inline-notification--${k}`),
    ),
    // Trimmed (PR #364 review, SScholefield): Carbon wraps these in their own elements, so
    // `textContent` can pick up whitespace from JSX formatting. Nothing carries whitespace today, so
    // this guards against a template edit breaking the test rather than the page — a false NEGATIVE.
    // It cannot mask a real failure: trimming never turns wrong text into right text, and a
    // whitespace-only subtitle is caught by the count/kind assertions regardless.
    title: el.querySelector('.cds--inline-notification__title')?.textContent?.trim() ?? '',
    subtitle: el.querySelector('.cds--inline-notification__subtitle')?.textContent?.trim() ?? '',
  }))

describe('Schedule2 page', () => {
  test('editable:true renders editable inputs for 25/26 + comments; derived blocks read-only (AC1)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)

    // The three editable fields are inputs seeded from the document.
    const item25Cost = await screen.findByLabelText('Purchased Log Cost cost')
    // Editable numbers display thousands-grouped (commas).
    expect(item25Cost).toHaveValue('50,000')
    expect(screen.getByLabelText('Less Log Sales volume')).toHaveValue('200')
    expect(screen.getByLabelText('Less Log Sales cost')).toHaveValue('8,000')
    expect(
      screen.getByLabelText('If you have any additional comments, please enter them here:'),
    ).toHaveValue('Seed comment for 514/2021')

    // Carried purchasedLogCost.volume is read-only (never an input).
    expect(screen.queryByLabelText('Purchased Log Cost volume')).not.toBeInTheDocument()
    // Derived blocks are read-only display (no inputs).
    expect(screen.queryByLabelText(/Subtotal/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Net Purchased/i)).not.toBeInTheDocument()

    // Actions enabled.
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
    screen.getAllByRole('button', { name: /check status/i }).forEach((b) => expect(b).toBeEnabled())
    screen.getAllByRole('button', { name: /delete/i }).forEach((b) => expect(b).toBeEnabled())
  })

  // ---- Defect #291: derived figures track data entry, on blur, before Save. -----------------------
  // The fixture's carried figures: Sch3 PO&P volume 1000 / PO&P actual cost 2000 (purchasedWoodOverhead),
  // Sch3 Crown volume 2000 / total logging cost 90000 (totalCompanyLogging). Entered: 50000 / 200 / 8000.

  /** A value row's cells as text: [label, volume, cost, $/m³]. */
  const rowCells = (label: string) => {
    const tr = screen.getByText(label).closest('tr')
    if (!tr) throw new Error(`no row for "${label}"`)
    return within(tr)
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
  }

  test('typing alone moves nothing; blurring the field recalculates every dependent figure (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '52,000', '52.00'])

    // Typing must NOT recalculate — legacy recalculated on the field's change/blur, not per keystroke,
    // so a half-typed number never drives the totals.
    await user.clear(cost)
    await user.type(cost, '60000')
    expect(cost).toHaveValue('60,000')
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '52,000', '52.00'])

    // Blur commits the field, and every dependent figure moves at once — with no Save.
    await user.tab()
    expect(rowCells('Purchased/Private Log Costs:')).toEqual([
      'Purchased/Private Log Costs:',
      '1,000',
      'Purchased Log Cost cost', // the entry cell holds the input; its text is the hidden a11y label
      '60.00', // 60000/1000
    ])
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '62,000', '62.00'])
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual([
      'Net Purchased/Private Log Cost:',
      '800', // 1000 - 200
      '54,000', // 62000 - 8000
      '67.50',
    ])
    expect(rowCells('Total Average Logging Costs:')).toEqual([
      'Total Average Logging Costs:',
      '2,800', // 800 + 2000 crown
      '144,000', // 54000 + 90000
      '51.43', // 144000/2800 = 51.4286
    ])
  })

  test('the wholly-carried rows never move during entry (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '999999')
    await user.tab()

    // Both are carried from Schedules 3 and 1 — Schedule 2 entry cannot affect them.
    expect(rowCells('Purchased/Private Wood Overhead:')).toEqual([
      'Purchased/Private Wood Overhead:',
      '1,000',
      '2,000',
      '2.00',
    ])
    expect(rowCells('Total Company Logging Costs(Sch 1):')).toEqual([
      'Total Company Logging Costs(Sch 1):',
      '2,000',
      '90,000',
      '45.00',
    ])
  })

  test('editing the sales pair recalculates net and total average (#291)', async () => {
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    const user = userEvent.setup()

    const volume = await screen.findByLabelText('Less Log Sales volume')
    await user.clear(volume)
    await user.type(volume, '400')
    await user.tab()

    // 8000/400 = 20.00; net volume 1000-400 = 600; net cost unchanged at 52000-8000 = 44000;
    // 44000/600 = 73.3333; total average volume 600+2000 = 2600, cost 44000+90000 = 134000.
    expect(rowCells('(less) Log Sales:')).toEqual([
      '(less) Log Sales:',
      'Less Log Sales volume', // both cells hold inputs; their text is the hidden a11y label
      'Less Log Sales cost',
      '20.00', // 8000/400
    ])
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual([
      'Net Purchased/Private Log Cost:',
      '600',
      '44,000',
      '73.33',
    ])
    expect(rowCells('Total Average Logging Costs:')).toEqual([
      'Total Average Logging Costs:',
      '2,600',
      '134,000',
      '51.54', // 134000/2600 = 51.5385
    ])
  })

  test('the mirror equals the SERVER figures, before and after Save (#291 AC5)', async () => {
    // What Schedule2Service actually computes for cost 60000 against this document's carried figures.
    // Asserting the rendered cells against THESE values — not against a snapshot of the render — is
    // what makes this a mirror-vs-server comparison. The earlier version snapshotted the pre-Save
    // render and compared it to the post-Save render; because an editable page always renders the
    // mirror, that compared the mirror to itself and would have passed with 999999 in the echo
    // (code review 2026-08-21).
    const SERVER = {
      subtotal: block(1000, 62000, 62.0),
      netPurchased: block(800, 54000, 67.5),
      totalAverage: block(2800, 144000, 51.4286),
    }
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, () =>
        HttpResponse.json({
          ...schedule2Doc,
          revisionCount: 4,
          purchasedLogCost: block(1000, 60000, 60.0),
          ...SERVER,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        }),
      ),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '60000')
    await user.tab()

    // The mirror must already agree with what the server will send.
    const expected = {
      subtotal: ['Subtotal:', '1,000', '62,000', '62.00'],
      net: ['Net Purchased/Private Log Cost:', '800', '54,000', '67.50'],
      average: ['Total Average Logging Costs:', '2,800', '144,000', '51.43'],
    }
    expect(rowCells('Subtotal:')).toEqual(expected.subtotal)
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual(expected.net)
    expect(rowCells('Total Average Logging Costs:')).toEqual(expected.average)

    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    // ...and still agree once the echo has replaced the document.
    expect(rowCells('Subtotal:')).toEqual(expected.subtotal)
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual(expected.net)
    expect(rowCells('Total Average Logging Costs:')).toEqual(expected.average)
  })

  test('on load the mirror reproduces the served figures exactly (#291 AC5)', async () => {
    // The fixture is self-consistent (its stored derived values satisfy Schedule2Service's formulas),
    // so this is a direct mirror-vs-server comparison with no edit involved: a mirror that rounds or
    // propagates nulls differently from the service fails here.
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)
    await screen.findByText('Purchased/Private Log Costs:')

    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '52,000', '52.00'])
    expect(rowCells('Net Purchased/Private Log Cost:')).toEqual([
      'Net Purchased/Private Log Cost:',
      '800',
      '44,000',
      '55.00',
    ])
    expect(rowCells('Total Average Logging Costs:')).toEqual([
      'Total Average Logging Costs:',
      '2,800',
      '134,000',
      '47.86',
    ])
  })

  test('view mode renders the document figures as-is — no client recomputation (#291 AC7)', async () => {
    // A non-editable document whose stored Subtotal deliberately disagrees with its own line items: if
    // the page recomputed outside Draft it would show 52,000 instead of the server's figure.
    server.use(
      http.get(URL, () =>
        HttpResponse.json({
          ...schedule2Doc,
          trackStatus: 'S',
          editable: false,
          subtotal: block(1000, 999999, 999.99),
        }),
      ),
    )
    render(<Schedule2 />)

    expect(await screen.findByText('Purchased/Private Log Costs:')).toBeInTheDocument()
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '999,999', '999.99'])
  })

  test('editable:false renders read-only + disables actions (AC1)', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json({ ...schedule2Doc, trackStatus: 'S', editable: false }),
      ),
    )
    render(<Schedule2 />)

    expect(await screen.findByText('Purchased/Private Log Costs:')).toBeInTheDocument()
    expect(screen.queryByLabelText('Purchased Log Cost cost')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Less Log Sales volume')).not.toBeInTheDocument()
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeDisabled())
    screen
      .getAllByRole('button', { name: /check status/i })
      .forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /delete/i }).forEach((b) => expect(b).toBeDisabled())
  })

  test('unsaved editable doc Saves with revisionCount 0 (AC2)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(unsavedDoc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...schedule2Doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const item25Cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.type(item25Cost, '12345')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    const body = captured as {
      revisionCount: number
      purchasedLogCostCost: number | null
      lessLogSalesVolume: number | null
      lessLogSalesCost: number | null
    }
    expect(body.revisionCount).toBe(0)
    expect(body.purchasedLogCostCost).toBe(12345)
    expect(body.lessLogSalesVolume).toBeNull()
    expect(body.lessLogSalesCost).toBeNull()
    // Contract lock: the PUT carries ONLY the five entered/meta fields — never a derived/carried
    // figure (subtotal, netPurchased, perUnit, purchasedLogCost.volume, ...). A stray field would
    // otherwise slip through unnoticed.
    expect(Object.keys(body as Record<string, unknown>).sort()).toEqual([
      'comments',
      'lessLogSalesCost',
      'lessLogSalesVolume',
      'purchasedLogCostCost',
      'revisionCount',
    ])
  })

  test('valid Save PUTs the flat request and shows the API success message (AC2)', async () => {
    let captured: unknown = null
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json({
          ...schedule2Doc,
          revisionCount: 4,
          purchasedLogCost: block(1000, 60000, 60.0),
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '60000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    const body = captured as {
      revisionCount: number
      purchasedLogCostCost: number
      lessLogSalesVolume: number
      lessLogSalesCost: number
    }
    expect(body.revisionCount).toBe(3)
    expect(body.purchasedLogCostCost).toBe(60000)
    expect(body.lessLogSalesVolume).toBe(200)
    expect(body.lessLogSalesCost).toBe(8000)
    // Reseeded from the echo (60000/1000 = 60 read-only display), shown in the currency style ($/m³).
    expect(screen.getByText('60.00')).toBeInTheDocument()
  })

  test('out-of-range value is blocked client-side — inline error, no PUT (AC3)', async () => {
    let putCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, () => {
        putCalled = true
        return problemBody(400, 'server should not be reached')
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    await user.clear(cost)
    await user.type(cost, '100000000')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(
      await screen.findByText('Entered cost must be between -99,999,999 and 99,999,999.'),
    ).toBeInTheDocument()
    expect(putCalled).toBe(false)
    expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue('100,000,000')
  })

  test('backend 4xx save failure shows verbatim detail (AC3)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.put(URL, () => problemBody(409, 'The record has been changed by another user.')),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])

    expect(
      await screen.findByText('The record has been changed by another user.'),
    ).toBeInTheDocument()
  })

  test('a never-saved Schedule 2 disables Delete and leaves Save / Check Status usable (defect #292)', async () => {
    // The served body of a mill/year that has never had a Schedule 2 saved: 200, EDITABLE, every
    // figure blank, and NO `revisionCount` key. There is nothing to delete, so Delete must be
    // greyed out — but data entry must still be possible (legacy AF1).
    server.use(http.get(URL, () => HttpResponse.json(unsavedDoc)))
    render(<Schedule2 />)

    await screen.findByLabelText('Purchased Log Cost cost')
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/check status/i, 2).forEach((b) => expect(b).toBeEnabled())
    // The greyed button must say why (defect #292 decision 3) — to assistive technology, not on the
    // page: the reason is `cds--visually-hidden`, since a disabled button is not focusable but the
    // visible text read as clutter (product call 2026-08-24).
    const hint = screen.getByText('Available once the schedule is saved')
    expect(hint).toHaveClass('cds--visually-hidden')
    expect(actionBarButtons(/delete/i, 1)[0]).toHaveAttribute('aria-describedby', hint.id)
  })

  test('Delete renders on the bottom action bar only (defect #292)', async () => {
    // Legacy carried Save + Check Status above the schedule and Save + Check Status + Delete below
    // it (schedule2.xhtml:35-36 vs :172-178) — the asymmetry Schedules 1 and 3 already honour.
    server.use(http.get(URL, () => HttpResponse.json(schedule2Doc)))
    render(<Schedule2 />)

    await screen.findByLabelText('Purchased Log Cost cost')
    actionBarButtons(/^save$/i, 2)
    actionBarButtons(/check status/i, 2)
    const deletes = actionBarButtons(/delete/i, 1)
    // …and it is the LAST action bar in the document that carries it.
    const bars = Array.from(document.querySelectorAll('.schedule-2__actions'))
    expect(bars).toHaveLength(2)
    expect(bars[1]?.contains(deletes[0] ?? null)).toBe(true)
    // A saved schedule (revisionCount 3) can still be deleted.
    expect(deletes[0]).toBeEnabled()
  })

  test('Delete confirms, shows the API message, then re-GETs the empty editable schedule (AC4)', async () => {
    let deleted = false
    server.use(
      http.get(URL, () => HttpResponse.json(deleted ? unsavedDoc : schedule2Doc)),
      http.delete(URL, () => {
        deleted = true
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /delete/i })[0])
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // Schedule 2 never 404s: the re-GET returns the empty EDITABLE document. Inputs remain (now
    // empty), Delete is disabled (nothing to delete), and Save/Check Status stay enabled so the
    // Licensee can immediately re-enter data (legacy AF1).
    await waitFor(() => expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue(''))
    // Delete greys out again: the re-GET carries no `revisionCount`, so there is nothing to delete
    // and the same schedule cannot be "deleted" repeatedly (defect #292, second face).
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    screen.getAllByRole('button', { name: /^save$/i }).forEach((b) => expect(b).toBeEnabled())
  })

  test('an unsaved schedule with no carried Schedule 3 figures renders blanks, never NaN', async () => {
    // Regression for the NaN that reached main on 2026-08-24. `unsavedDoc` is the real served body, so
    // its CostBlocks are `{}` — the carried Schedule 3 figures are ABSENT, not null. The derived mirror
    // runs on any editable document, and an unsaved schedule is editable, so those `undefined`s reached
    // the arithmetic: `12345 + undefined` is NaN, and the user saw "NaN" in Subtotal, Net Purchased and
    // Total Average — the last one before typing anything at all.
    server.use(http.get(URL, () => HttpResponse.json(unsavedDoc)))
    render(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    expect(document.body.textContent).not.toContain('NaN')

    // Commit a cost the way legacy did — on blur, which is what feeds the mirror.
    await user.type(cost, '12345')
    await user.tab()

    await waitFor(() => expect(cost).toHaveValue('12,345'))
    expect(document.body.textContent).not.toContain('NaN')
    // …and the figures that DO derive from the entry are still right: with nothing carried, the
    // subtotal is the entered cost itself (absent behaves as "nothing to add", not as poison).
    const subtotalRow = screen.getByText('Subtotal:').closest('tr')
    expect(subtotalRow?.textContent).toContain('12,345')
  })

  test('Delete stays disabled while the post-delete reload is in flight, so no second DELETE can fire (defect #292 face 2)', async () => {
    // The window that survived the first fix: `run`'s `.finally` releases `saving` when the DELETE
    // settles, but the reload GET it dispatched is still outstanding — and until that reload lands,
    // `data` still carries the pre-delete `revisionCount`. Delete therefore re-enabled on a record
    // that was already gone, and a second click sent a second DELETE which the idempotent backend
    // answered with another success message. That is the reported symptom, verbatim.
    let deleted = false
    let deleteCount = 0
    let releaseReload: () => void = () => undefined
    const reloadBlocked = new Promise<void>((resolve) => {
      releaseReload = resolve
    })
    server.use(
      http.get(URL, async () => {
        if (deleted) {
          await reloadBlocked // hold the reload open to keep the window measurable
          return HttpResponse.json(unsavedDoc)
        }
        return HttpResponse.json(schedule2Doc)
      }),
      http.delete(URL, () => {
        deleted = true
        deleteCount += 1
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    // The DELETE has landed and the reload has NOT. Delete must already be shut.
    await waitFor(() => expect(deleteCount).toBe(1))
    await waitFor(() => expect(actionBarButtons(/delete/i, 1)[0]).toBeDisabled())

    // Prove it, rather than trusting the attribute: clicking cannot produce a second request.
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    expect(confirmModalOpen()).toBe(false)
    expect(deleteCount).toBe(1)

    releaseReload()
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue(''))
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    expect(deleteCount).toBe(1)
  })

  test('the whole delete → reload sequence holds the in-flight lock (PR #351 review)', async () => {
    // Clearing the optimistic-lock token shut the DELETE gate, but left the WINDOW open: `run`'s
    // `.finally` released `saving` when the DELETE settled while the reload GET was still out. Save is
    // gated on `saving`, NOT on the persisted-record check, so a click in that window PUT
    // `revisionCount: 0` and re-created the schedule with the pre-delete figures — then the reload
    // painted an empty document over a row that now existed. This asserts the LOCK; the gate tests
    // above pass on the cleared token alone and cannot see this.
    let deleted = false
    let putCount = 0
    let releaseReload: () => void = () => undefined
    const reloadBlocked = new Promise<void>((resolve) => {
      releaseReload = resolve
    })
    server.use(
      http.get(URL, async () => {
        if (deleted) {
          await reloadBlocked // hold the reload open to keep the window measurable
          return HttpResponse.json(unsavedDoc)
        }
        return HttpResponse.json(schedule2Doc)
      }),
      http.delete(URL, () => {
        deleted = true
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
      http.put(URL, () => {
        putCount += 1
        return HttpResponse.json(schedule2Doc)
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    // Mid-window: the DELETE has settled, the reload has not. EVERY write control stays locked,
    // because delete→reload is one operation rather than two.
    await waitFor(() => expect(actionBarButtons(/delete/i, 1)[0]).toBeDisabled())
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeDisabled())
    actionBarButtons(/check status/i, 2).forEach((b) => expect(b).toBeDisabled())

    // Prove it rather than trusting the attribute: a Save here cannot resurrect the deleted record.
    await user.click(actionBarButtons(/^save$/i, 2)[0]!)
    expect(putCount).toBe(0)

    releaseReload()
    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    // Lock released only once the whole sequence is done, and the form is re-seeded empty by then.
    await waitFor(() => expect(actionBarButtons(/^save$/i, 2)[0]).toBeEnabled())
    expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue('')
    expect(putCount).toBe(0)
  })

  test('a FAILED post-delete reload still closes the Delete gate (defect #292 face 2)', async () => {
    // The permanent version of the same hole: if the reload never succeeds, nothing else ever tells
    // the page its record is gone. The banner says the refresh failed; Delete must still be shut,
    // because the DELETE itself succeeded.
    let deleted = false
    let deleteCount = 0
    server.use(
      http.get(URL, () =>
        deleted ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(schedule2Doc),
      ),
      http.delete(URL, () => {
        deleted = true
        deleteCount += 1
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(
      await screen.findByText('Deleted, but the list could not be refreshed.'),
    ).toBeInTheDocument()
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    expect(deleteCount).toBe(1)
  })

  test('a failed DELETE renders the API detail verbatim, not the fallback (AC5, defect #298)', async () => {
    // The OTHER half of the delete branch, and the half that can actually happen here: the backend
    // runs `validateMillYearActive` before deleting, so a 409 mill-closed on DELETE is a live path.
    // Until the #298 code review, no delete fixture in this file carried a `detail` at all — every
    // one was a success or a detail-less failure — so a regression that dropped `extractDetail` from
    // the delete path would have shown the generic string for this 409 and nothing would have failed.
    // The load branch has had this pair since the start (see the 409 mill-closed load test).
    // `millNotActiveForCurrentYearMsg` in FULL (`messages.properties:10`). This read
    // 'This Mill is not active for the current Reporting Year.' — a TRUNCATED invention that stops
    // at the first full stop and drops 'Please select another mill from the Home Page.' The API
    // cannot emit the short form, so the verbatim rule (AD-8) was being asserted against a string
    // that does not exist; the load-path test further down has always carried the whole sentence.
    const detail =
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.'
    let getCount = 0
    server.use(
      http.get(URL, () => {
        getCount += 1
        return HttpResponse.json(schedule2Doc)
      }),
      http.delete(URL, () => problemBody(409, detail)),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    // Verbatim (AD-8) — the API's own wording, never the client's fallback.
    await waitFor(() =>
      expect(notifications()).toEqual([
        { kind: 'error', title: 'Action failed', subtitle: detail },
      ]),
    )
    // Same post-failure invariants as the detail-less case: nothing deleted, nothing reloaded.
    expect(getCount).toBe(1)
    expect(confirmModalOpen()).toBe(false)
    await waitFor(() => expect(actionBarButtons(/delete/i, 1)[0]).toBeEnabled())
  })

  test.each(detailLessDeleteFailures)(
    'a DELETE failure carrying no detail falls back to the generic delete message and leaves the record intact — %s (AC5, defect #298)',
    async (_shape, respond) => {
      // Distinct from the test above: there the DELETE SUCCEEDED and the reload failed (the
      // `Deleted, but…` string). Here the DELETE itself fails, which is the other fallback entirely
      // — and the record therefore still exists, so every post-delete consequence must NOT happen.
      let getCount = 0
      server.use(
        http.get(URL, () => {
          getCount += 1
          return HttpResponse.json(schedule2Doc)
        }),
        http.delete(URL, respond),
      )
      render(<Schedule2 />)
      const user = userEvent.setup()

      const cost = await screen.findByLabelText('Purchased Log Cost cost')
      expect(getCount).toBe(1)
      // Enter a value that differs from the fixture, so the "entries survive" assertion below has
      // something a re-seed would destroy.
      await user.clear(cost)
      await user.type(cost, '61000')
      await user.click(actionBarButtons(/delete/i, 1)[0]!)
      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

      // The banner, with its severity — not a bare text match. `kind` and the explicit "Action
      // failed" title are what carry severity to a screen reader (`NotificationColumn`'s WCAG note),
      // and flipping this to kind="success" used to leave the suite green.
      await waitFor(() =>
        expect(notifications()).toEqual([
          { kind: 'error', title: 'Action failed', subtitle: 'Unable to delete Schedule 2.' },
        ]),
      )
      // `onSuccess` never ran, so the delete→reload chain never started: still the initial GET only.
      expect(getCount).toBe(1)
      // The confirm dialog is dismissed even though the delete failed — otherwise the user is left
      // staring at the confirmation with the error banner hidden behind it. `setConfirmDeleteOpen`
      // fires before dispatch, and moving it into `onSuccess` used to go unnoticed.
      expect(confirmModalOpen()).toBe(false)
      // Nothing was deleted, so Delete must still be OFFERED — the inverse of defect #292's
      // post-delete gate, which greys it out only once the record is actually gone. This also
      // proves the in-flight lock released on the error path; a leaked lock leaves every action
      // dead until reload. Both bars are counted (`actionBarButtons`, not a bare `getAllByRole`) so
      // a page rendering one bar instead of two cannot pass silently.
      await waitFor(() => expect(actionBarButtons(/delete/i, 1)[0]).toBeEnabled())
      actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
      // The value the user CHANGED survives, so the action can be retried and not retyped. It must
      // be a typed value: asserting the fixture's own 50,000 here proved nothing, because a
      // regression that re-seeded the form from `data` would reproduce it exactly.
      expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue('61,000')
      expect(screen.getByLabelText('Less Log Sales volume')).toHaveValue('200')
    },
  )

  test('a never-saved schedule issues NO DELETE request, not merely a disabled button (defect #292)', async () => {
    // The gate has to hold at the request level, the way Save's does (`putCalled === false` above):
    // `disabled` is presentation, and `handleDelete` is what actually must refuse.
    let deleteCalled = false
    server.use(
      http.get(URL, () => HttpResponse.json(unsavedDoc)),
      http.delete(URL, () => {
        deleteCalled = true
        return HttpResponse.json({ message: { key: 'x', text: 'x' } })
      }),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)

    // No confirm dialog opened, and nothing reached the network.
    expect(confirmModalOpen()).toBe(false)
    expect(deleteCalled).toBe(false)
  })

  test('Check Status MET renders a success notification with the returned text (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'MET',
          // Verbatim `scheduleRequirementsMetMsg` (`messages.properties:184`) — note there is NO
          // trailing full stop in the bundle. This fixture previously invented
          // "Schedule requirements met.", a string the API cannot emit; the test still passed
          // (the page renders the API's `text` as given), but it pinned text that does not exist.
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
          ],
        }),
      ),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /check status/i })[0])

    expect(
      await screen.findByText('All requirements for this schedule have been met'),
    ).toBeInTheDocument()
  })

  test('Check Status ISSUES renders a warning notification with the returned text (AC5)', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(schedule2Doc)),
      http.post(CHECK_URL, () =>
        HttpResponse.json({
          outcome: 'ISSUES',
          messages: [
            {
              key: 'missingRequiredFieldMsg',
              text: 'Purchased/Private Log Costs - Cost: Value Required',
            },
          ],
        }),
      ),
    )
    render(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    await user.click(screen.getAllByRole('button', { name: /check status/i })[0])

    expect(
      await screen.findByText('Purchased/Private Log Costs - Cost: Value Required'),
    ).toBeInTheDocument()
  })

  test('409 mill-closed shows verbatim detail, form suppressed', async () => {
    const detail =
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.'
    server.use(problemHandler(409, detail))
    render(<Schedule2 />)

    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test.each(detailLessFailures)(
    'a load failure carrying no detail falls back to the generic load message — %s (AC7, defect #298)',
    async (_shape, respond) => {
      // The test above proves the verbatim half of this branch; this proves the other half. What a
      // missing fallback actually costs the user is the WHOLE PAGE: `mapLoadError` returning ''
      // makes `errorDetail` falsy, so `if (errorDetail)` (index.tsx:244) never fires and the page
      // returns null at `if (!data)` (:253) — no panel, no header, a blank white screen. Asserting
      // the notification SET, kind included, is what distinguishes that from the error state.
      server.use(http.get(URL, respond))
      render(<Schedule2 />)

      await waitFor(() =>
        expect(notifications()).toEqual([
          {
            kind: 'error',
            title: 'Unable to load Schedule 2',
            subtitle: 'Unable to load Schedule 2.',
          },
        ]),
      )
      // ...and the document is genuinely suppressed rather than rendered beside the error. These are
      // guaranteed by the early return, so they are a regression tripwire on that structure, not
      // independent evidence — the assertion above is what carries this test.
      expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Purchased Log Cost cost')).not.toBeInTheDocument()
    },
  )

  test('empty context shows verbatim ERR-001 and fires NO request', async () => {
    server.use(
      http.get(URL, () => {
        throw new Error('GET must not fire when mill/year context is null')
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Schedule2 />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  })

  test('stale PUT is ignored when context changes before it settles (Story 29.6)', async () => {
    let releasePut = () => {}
    const releasePromise = new Promise<void>((resolve) => {
      releasePut = resolve
    })

    server.use(
      http.get(URL, ({ request }) =>
        new window.URL(request.url).searchParams.get('millId') === '999'
          ? HttpResponse.json({
              ...schedule2Doc,
              millId: 999,
              year: 2020,
              editable: false,
              comments: 'Context 999/2020 loaded',
            })
          : HttpResponse.json(schedule2Doc),
      ),
      http.put(URL, async () => {
        await releasePromise
        return HttpResponse.json({
          ...schedule2Doc,
          message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
        })
      }),
    )

    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        {/* eslint-disable-next-line @typescript-eslint/no-use-before-define */}
        <StaleRaceHarness />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await screen.findAllByRole('button', { name: /^save$/i })
    await user.click(screen.getAllByRole('button', { name: /^save$/i })[0])
    await user.click(screen.getByRole('button', { name: /change/i }))

    expect(await screen.findByText('Context 999/2020 loaded')).toBeInTheDocument()

    releasePut()
    await waitFor(() => {
      expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument()
    })
  })
})

describe('Schedule2 ministry correction at Submitted (Story 16.3)', () => {
  // ---- Story 16.3: the ministry-correction journey at Submitted -----------------------------------
  //
  // Story 16.1 shipped a server-side role x status editability matrix — ILCR_SUBMITTER may edit only
  // at Draft, ILCR_ADMIN may edit at Submitted and Verified and is deliberately READ-ONLY at Draft —
  // and every page learns it from the one server-computed `editable` boolean (AD-9). Nothing in this
  // file proved the admin half: every `'S'` fixture above pairs it with `editable: false`, which is
  // the submitter read-only case, so a widened gate (say, `editable` derived from `trackStatus`, or
  // from the role) would have passed the whole suite.
  //
  // Two things make these arms evidence rather than decoration:
  //
  //   1. The acting role is DECLARED (`renderAsAdmin` / `renderAsSubmitter`). `findMockUser(null)`
  //      falls through to `MOCK_USERS[0]` — the admin — so every test above silently acts as a
  //      Ministry Administrator without saying so; that exact fallback ran the e2e suite as the wrong
  //      role for a month. Declaring it seeds the storage key, so the browser identity
  //      (`MockAuthProvider`) and the wire identity (`X-Mock-Groups`) agree, as they do in the app.
  //
  //   2. The declaration is LOAD-BEARING, not documentation. `matrixGet` below answers `editable` by
  //      applying the 16.1 matrix to the `X-Mock-Groups` header the request actually carried, the way
  //      the mock backend does — so the admin arm and the submitter arm share one handler and one
  //      document, and differ only in which render helper they call. Hardcoding `editable: true` in
  //      the fixture would have proved something much weaker ("a page given editable:true allows
  //      correction"), and an identity helper that silently seeded nothing would still have passed.
  //
  // Editability stays server-authoritative throughout: the page reads `data.editable` and never the
  // role or the status (AD-9). Every user-facing string is asserted VERBATIM against
  // `backend/src/main/resources/messages.properties` (AD-8).

  // The PINNED 16.1 matrix (`ScheduleEditability`), per track status. Reproduced here rather than
  // imported because the real rule lives in Java: this is the wire contract the frontend is entitled
  // to assume, and stating it makes falsification trivial — flip the admin entry to ['D'] and every
  // admin-at-Submitted arm below must fail. Anything else — 'O', a missing row, an unknown role — is
  // read-only.
  const EDITABLE_STATUSES: Record<string, readonly string[]> = {
    ILCR_ADMIN: ['S', 'V'],
    ILCR_SUBMITTER: ['D'],
  }

  /**
   * The acting role as the request actually carried it. `api-service` mirrors the selected mock
   * user's roles onto `X-Mock-Groups` (api-service.ts:11-17), so this is the same signal the mock
   * backend gates on — not something the test asserts about itself.
   *
   * The throw is a tripwire for a future `api-service` that stops sending the header at all; it is
   * NOT the identity guard, because it can never fire today: `mockUserGroups()` calls
   * `findMockUser(localStorage…)`, which falls back to `MOCK_USERS[0]` — the ADMIN — so a header is
   * always sent, even by a test that declared no role. The guard is `expectActingAs` below, which
   * pairs this header with `declaredRole()`.
   */
  const actingRole = (request: Request): string => {
    const header = request.headers.get('X-Mock-Groups')
    if (!header) {
      throw new Error('request carried no X-Mock-Groups header — the acting identity was not sent')
    }
    return header
  }

  // The identity the LAST matrix-answered request actually carried. Captured in the resolver and
  // asserted in the test body, never inside the resolver: an expectation that throws there is
  // reported by MSW as a failed request, so the failure would be misattributed to the page.
  let sentRole: string | null = null

  beforeEach(() => {
    sentRole = null
  })

  /** A Submitted Schedule 2, with per-test overrides. */
  const doc = (over: Partial<Schedule2Response> = {}): Schedule2Response => ({
    ...schedule2Doc,
    message: null,
    trackStatus: 'S',
    ...over,
  })

  /**
   * Stamp `editable` per the matrix for whoever is asking. Deliberately applied AFTER the document's
   * own flag, so the computed value always wins: the fixtures carry `editable: true` inherited from
   * `schedule2Doc`, and the read-only arms below pass only because the matrix overrides it. Dropping
   * this call therefore breaks those arms rather than quietly loosening them.
   */
  const withMatrixEditable = <T extends { trackStatus: string | null }>(
    body: T,
    request: Request,
  ) => {
    sentRole = actingRole(request)
    // UNION across every role the caller holds, because that is what the server does
    // (`ScheduleEditability.forCaller`) and because `X-Mock-Groups` is `roles.join(',')` — a raw
    // lookup on the whole header would miss a multi-role caller and drop it into read-only via
    // `?? []`, the one outcome that makes a read-only arm pass for the wrong reason. Unreachable
    // with today's one-role-per-mock-user, and that is exactly why the double must not encode the
    // narrower rule.
    const permitted = new Set(
      sentRole.split(',').flatMap((role) => EDITABLE_STATUSES[role.trim()] ?? []),
    )
    return { ...body, editable: permitted.has(String(body.trackStatus)) }
  }

  /**
   * Both halves of the identity claim every arm's NAME makes, asserted in the test body rather than
   * inside an MSW resolver (a failed expectation there is reported as a failed REQUEST and
   * misattributed to the page).
   *
   *   - `declaredRole()` — the role THIS test seeded, and `null` when nothing declared one.
   *   - `sentRole` — the `X-Mock-Groups` identity the page's request actually carried.
   *
   * Neither half is sufficient, and the measurements are worth keeping. The WIRE half catches a
   * lost or mis-seeded `renderAsSubmitter` (the submitter arm then reports `expected 'ILCR_ADMIN'
   * to be 'ILCR_SUBMITTER'`), an identity leaking in from an earlier test, and an `api-service`
   * that stops mirroring roles onto the header. It CANNOT catch a lost `renderAsAdmin`:
   * `findMockUser(null)` falls back to `MOCK_USERS[0]`, which IS the admin, so a fallback admin and
   * a declared admin are indistinguishable on the wire — measured, and the whole suite stayed green
   * with the declaration deleted. The DECLARATION half closes exactly that: a fallback admin
   * declared nothing, so `declaredRole()` is null where a real `renderAsAdmin` returns the role.
   */
  const expectActingAs = (role: IlcrRole, header: string) => {
    expect(declaredRole()).toBe(role)
    expect(sentRole).toBe(header)
  }

  /** GET that answers `editable` per the matrix for whoever is asking. */
  const matrixGet = (over: Partial<Schedule2Response> = {}) =>
    http.get(URL, ({ request }) => HttpResponse.json(withMatrixEditable(doc(over), request)))

  /**
   * A value row's cells as text: [label, volume, cost, $/m3]. A local copy of the helper in the
   * `Schedule2 page` describe above, which is scoped to that block — duplicated rather than hoisted
   * so this describe adds nothing to the pre-existing tests.
   */
  const rowCells = (label: string) => {
    const tr = screen.getByText(label).closest('tr')
    if (!tr) throw new Error(`no row for "${label}"`)
    return within(tr)
      .getAllByRole('cell')
      .map((cell) => cell.textContent)
  }

  const COMMENTS_FIELD = 'If you have any additional comments, please enter them here:'

  /**
   * The read-only shape. Schedule 2 swaps its inputs for text cells when the document is not
   * editable, so ABSENCE is the assertion — a `toBeDisabled` on a missing input passes vacuously —
   * and every action is withheld.
   */
  const expectReadOnlySurface = () => {
    expect(screen.queryByLabelText('Purchased Log Cost cost')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Less Log Sales volume')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Less Log Sales cost')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(COMMENTS_FIELD)).not.toBeInTheDocument()
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeDisabled())
    actionBarButtons(/check status/i, 2).forEach((b) => expect(b).toBeDisabled())
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    // The confirm modal is rendered only for an editable schedule (`index.tsx`: `{editable && …}`),
    // so there is no route to a DELETE even by reaching past the disabled button.
    expect(
      screen.queryByText('This will delete the current record. Do you want to continue?'),
    ).not.toBeInTheDocument()
  }

  /** The correction shape: real inputs seeded from the document, and every action offered. */
  const expectEditableSurface = () => {
    expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue('50,000')
    expect(screen.getByLabelText('Less Log Sales volume')).toHaveValue('200')
    expect(screen.getByLabelText('Less Log Sales cost')).toHaveValue('8,000')
    expect(screen.getByLabelText(COMMENTS_FIELD)).toBeInTheDocument()
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/check status/i, 2).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeEnabled())
  }

  // Enter a value the cheap way. These pages mount an editor per cell and `user.type` is
  // O(cells x chars) — the cause of the CI timeouts recorded against the schedule suites — so drive
  // the field with one change event and commit it with an explicit blur, which is the event the
  // derived mirror actually listens to (defect #291).
  const enterValue = (input: HTMLElement, grouped: string) => {
    fireEvent.change(input, { target: { value: grouped } })
    fireEvent.blur(input)
  }

  test('ADMIN at Submitted corrects a figure and saves: SUC-001 verbatim, and the save cannot move the track (16.3)', async () => {
    let captured: unknown = null
    let getCount = 0
    server.use(
      http.get(URL, ({ request }) => {
        getCount += 1
        return HttpResponse.json(withMatrixEditable(doc(), request))
      }),
      http.put(URL, async ({ request }) => {
        captured = await request.json()
        return HttpResponse.json(
          withMatrixEditable(
            doc({
              // The server echoes the SAME status back — a correction is not a transition.
              trackStatus: 'S',
              revisionCount: 4,
              purchasedLogCost: block(1000, 60000, 60.0),
              subtotal: block(1000, 62000, 62.0),
              netPurchased: block(800, 54000, 67.5),
              totalAverage: block(2800, 144000, 51.4286),
              message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
            }),
            request,
          ),
        )
      }),
    )
    renderAsAdmin(<Schedule2 />)
    const user = userEvent.setup()

    // The matrix makes this document editable FOR THIS ROLE, so the ministry gets real inputs — not
    // the read-only cells a submitter is served for the same document (the arm below).
    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expect(cost).toHaveValue('50,000')
    expect(screen.getByLabelText('Less Log Sales volume')).toBeEnabled()
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/check status/i, 2).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeEnabled())

    enterValue(cost, '60,000')
    await user.click(actionBarButtons(/^save$/i, 2)[0]!)

    // SUC-001 exactly as `messages.properties:173` has it, carried on the response and rendered
    // verbatim (AD-8) — with its severity, since `kind` is half of what a screen reader gets.
    await waitFor(() =>
      expect(notifications()).toEqual([
        { kind: 'success', title: 'Success', subtitle: 'Data saved successfully' },
      ]),
    )

    // Saving must not move status. The page's only lever on the track would be the write body, and
    // the body carries the five entered/meta fields and nothing else — no `trackStatus`, so a
    // correction cannot ask for a transition (there is no transition endpoint at all; MSW is strict,
    // so a call to one would have failed this test as an unhandled request).
    const body = captured as Record<string, unknown>
    expect(Object.keys(body).sort()).toEqual([
      'comments',
      'lessLogSalesCost',
      'lessLogSalesVolume',
      'purchasedLogCostCost',
      'revisionCount',
    ])
    expect(body.purchasedLogCostCost).toBe(60000)
    expect(body.revisionCount).toBe(3)

    // ...and the echo — still `'S'`, so still editable for an admin — leaves the page in edit mode,
    // so the ministry can make a second correction without reloading. A gate that keyed off
    // `trackStatus` rather than the matrix would have dropped to read-only here.
    expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue('60,000')
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
    // The correction was one PUT against one loaded document: no silent reload smuggled a new
    // status in behind it.
    expect(getCount).toBe(1)
  })

  test('SUBMITTER at Submitted is read-only: no inputs, every action disabled, no request possible (16.3)', async () => {
    // The negative arm, and the same `matrixGet()` document as the admin arm above — the ONLY
    // difference between the two tests is which role is declared. Without this, an `editable` the
    // page ignored (or derived from the role) would pass the admin arm and nothing would notice.
    let writes = 0
    server.use(
      matrixGet(),
      http.put(URL, () => {
        writes += 1
        return problemBody(403, 'the submitter must not be able to write at Submitted')
      }),
      http.delete(URL, () => {
        writes += 1
        return problemBody(403, 'the submitter must not be able to delete at Submitted')
      }),
      http.post(CHECK_URL, () => {
        writes += 1
        return problemBody(403, 'check status is disabled when not editable')
      }),
    )
    renderAsSubmitter(<Schedule2 />)
    const user = userEvent.setup()

    expect(await screen.findByText('Purchased/Private Log Costs:')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
    expectReadOnlySurface()

    // Nothing is dispatched from the read-only surface: clicking where Save, Check Status and
    // Delete sit produces no request of any kind.
    //
    // Read this for what it is. It holds BECAUSE the disabled buttons swallow the clicks —
    // `handleSave` and `handleCheckStatus` guard only `!data || saving` and never look at
    // `editable` (`schedule2/index.tsx`), so this is evidence about the rendered surface, not about
    // a second check inside the handlers. Only `handleDelete` re-checks, and it re-checks
    // `isScheduleSaved`, not the role×status gate. Adding those guards would be app code and is
    // deliberately out of scope here; the backend refuses the write either way, which is where the
    // authority lives (AD-9).
    await user.click(actionBarButtons(/^save$/i, 2)[0]!)
    await user.click(actionBarButtons(/check status/i, 2)[0]!)
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    expect(writes).toBe(0)
    expect(confirmModalOpen()).toBe(false)
  })

  test('ADMIN at Draft is read-only — the capability 16.1 deliberately removed (16.3)', async () => {
    // The other direction of the same matrix: the ministry does NOT edit a Licensee's Draft. This
    // arm exists because "admin can edit" is the intuitive reading of the story and is wrong; a gate
    // widened to "admin may always edit" passes every other test in this file.
    let writes = 0
    server.use(
      matrixGet({ trackStatus: 'D' }),
      http.put(URL, () => {
        writes += 1
        return problemBody(403, 'the administrator must not be able to write at Draft')
      }),
      http.delete(URL, () => {
        writes += 1
        return problemBody(403, 'the administrator must not be able to delete at Draft')
      }),
      http.post(CHECK_URL, () => {
        writes += 1
        return problemBody(403, 'check status is disabled when not editable')
      }),
    )
    renderAsAdmin(<Schedule2 />)
    const user = userEvent.setup()

    expect(await screen.findByText('Purchased/Private Log Costs:')).toBeInTheDocument()
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    expectReadOnlySurface()

    // Same reading as the arm above: no request leaves the read-only surface.
    await user.click(actionBarButtons(/^save$/i, 2)[0]!)
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    expect(writes).toBe(0)
  })

  test('SUBMITTER at Draft still edits — the discriminator against an always-false handler (16.3)', async () => {
    // The fourth cell of the matrix, and the arm that stops the two read-only arms above from
    // passing vacuously: a `matrixGet` that answered `editable: false` to everyone would satisfy
    // both of them, and this one fails. Same Draft document as the admin-at-Draft arm, opposite
    // outcome, one changed word in the test.
    server.use(matrixGet({ trackStatus: 'D' }))
    renderAsSubmitter(<Schedule2 />)

    await screen.findByLabelText('Purchased Log Cost cost')
    expectActingAs(ILCR_ROLES.submitter, 'ILCR_SUBMITTER')
    expectEditableSurface()
  })

  // ---- C: the admin cells nobody serves ----------------------------------------------------------
  //
  // `ILCR_ADMIN: ['S', 'V']` is pinned in all twelve suites, but `trackStatus: 'V'` is served ZERO
  // times repo-wide — so narrowing the server matrix to `['S']` alone left every suite green and the
  // VERIFIED half of the admin row had no evidence at all. The other two rows are the fail-CLOSED
  // cells 16.1 pinned: a status outside the row, and a document carrying no status at all (the wire
  // omits nulls, so `trackStatus` genuinely can arrive absent).
  test.each([
    ['V', 'Verified is correctable too — the untested half of the admin row', true],
    ['O', 'an unrecognised status fails closed', false],
    [null, 'no track status at all fails closed', false],
  ] as const)('ADMIN at trackStatus %s: %s (16.3)', async (trackStatus, _why, editable) => {
    let writes = 0
    server.use(
      matrixGet({ trackStatus }),
      http.put(URL, () => {
        writes += 1
        return problemBody(403, 'not editable at this status')
      }),
      http.delete(URL, () => {
        writes += 1
        return problemBody(403, 'not deletable at this status')
      }),
    )
    renderAsAdmin(<Schedule2 />)

    await screen.findByText('Purchased/Private Log Costs:')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    if (editable) {
      expectEditableSurface()
    } else {
      expectReadOnlySurface()
    }
    expect(writes).toBe(0)
  })

  test('ADMIN at Submitted deletes behind the verbatim confirm (16.3)', async () => {
    let deleted = false
    let deleteCount = 0
    server.use(
      http.get(URL, ({ request }) =>
        HttpResponse.json(
          withMatrixEditable(
            deleted
              ? // Post-delete the mill-year has no Schedule 2 row, so the re-GET serves the empty
                // document — still on the Submitted track, so still the ministry's to re-enter.
                // Built from `unsavedDoc` so it keeps the real wire shape (no `revisionCount` key).
                { ...unsavedDoc, trackStatus: 'S' }
              : doc(),
            request,
          ),
        ),
      ),
      http.delete(URL, () => {
        deleted = true
        deleteCount += 1
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    renderAsAdmin(<Schedule2 />)
    const user = userEvent.setup()

    await screen.findByLabelText('Purchased Log Cost cost')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)

    // `confirmDeleteMsg`, `messages.properties:202`, to the character.
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText('Data deleted successfully')).toBeInTheDocument()
    expect(deleteCount).toBe(1)
    // The row is gone, so Delete greys out while the form stays open for re-entry (legacy AF1) —
    // and the track is still Submitted, so the ministry keeps its inputs.
    await waitFor(() => expect(screen.getByLabelText('Purchased Log Cost cost')).toHaveValue(''))
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeDisabled())
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
  })

  test('ADMIN at Submitted cancels the confirm: NO DELETE, and the document is untouched (16.3)', async () => {
    let deleteCount = 0
    let getCount = 0
    server.use(
      http.get(URL, ({ request }) => {
        getCount += 1
        return HttpResponse.json(withMatrixEditable(doc(), request))
      }),
      // Registered so a stray request is COUNTED rather than merely rejected by strict MSW: this
      // asserts "no DELETE was issued", which needs the handler to exist to be a real observation.
      http.delete(URL, () => {
        deleteCount += 1
        return HttpResponse.json({
          message: { key: 'dataDeletedSuccesfullyInfoMsg', text: 'Data deleted successfully' },
        })
      }),
    )
    renderAsAdmin(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    await user.click(actionBarButtons(/delete/i, 1)[0]!)
    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('This will delete the current record. Do you want to continue?'),
    ).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: /^cancel$/i }))

    // The modal closes and nothing at all happened: no DELETE, no reload, no banner, and the record
    // is still there to delete (Delete still offered).
    await waitFor(() => expect(confirmModalOpen()).toBe(false))
    expect(deleteCount).toBe(0)
    expect(getCount).toBe(1)
    expect(notifications()).toEqual([])
    expect(cost).toHaveValue('50,000')
    expect(screen.getByLabelText('Less Log Sales volume')).toHaveValue('200')
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeEnabled())
  })

  test('ADMIN at Submitted runs Check Status: available, verbatim, and it mutates nothing (16.3)', async () => {
    let getCount = 0
    let checkCount = 0
    let writes = 0
    server.use(
      http.get(URL, ({ request }) => {
        getCount += 1
        return HttpResponse.json(withMatrixEditable(doc(), request))
      }),
      http.post(CHECK_URL, () => {
        checkCount += 1
        return HttpResponse.json({
          outcome: 'MET',
          // `scheduleRequirementsMetMsg`, `messages.properties:184` — no trailing full stop.
          messages: [
            {
              key: 'scheduleRequirementsMetMsg',
              text: 'All requirements for this schedule have been met',
            },
          ],
        })
      }),
      http.put(URL, () => {
        writes += 1
        return problemBody(500, 'check status must not write')
      }),
      http.delete(URL, () => {
        writes += 1
        return problemBody(500, 'check status must not delete')
      }),
    )
    renderAsAdmin(<Schedule2 />)
    const user = userEvent.setup()

    const cost = await screen.findByLabelText('Purchased Log Cost cost')
    expectActingAs(ILCR_ROLES.admin, 'ILCR_ADMIN')
    actionBarButtons(/check status/i, 2).forEach((b) => expect(b).toBeEnabled())
    await user.click(actionBarButtons(/check status/i, 2)[0]!)

    await waitFor(() =>
      expect(notifications()).toEqual([
        {
          kind: 'success',
          title: 'Check Status',
          subtitle: 'All requirements for this schedule have been met',
        },
      ]),
    )
    expect(checkCount).toBe(1)
    // Read-only: no write of any kind, no reload, and the form still holds exactly what was loaded.
    expect(writes).toBe(0)
    expect(getCount).toBe(1)
    expect(cost).toHaveValue('50,000')
    expect(screen.getByLabelText('Less Log Sales volume')).toHaveValue('200')
    expect(screen.getByLabelText('Less Log Sales cost')).toHaveValue('8,000')
    expect(rowCells('Subtotal:')).toEqual(['Subtotal:', '1,000', '52,000', '52.00'])
    // The schedule is still editable and still deletable afterwards — checking status is not a
    // transition either.
    actionBarButtons(/^save$/i, 2).forEach((b) => expect(b).toBeEnabled())
    actionBarButtons(/delete/i, 1).forEach((b) => expect(b).toBeEnabled())
  })
})

import useMillYear from '@/context/millYear/useMillYear'

const StaleRaceHarness = () => {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(999, 2020)}>
        change
      </button>
      <Schedule2 />
    </>
  )
}
