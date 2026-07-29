import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor, userEvent } from '@/test-utils'
import { server } from '@/test-setup'
import apiService from '@/service/api-service'
import ContextBanner from '@/components/Layout/ContextBanner'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'

// Banner unit tests render ContextBanner DIRECTLY (not Layout — avoids Carbon Header/router weight;
// integration visibility is Story 1.5 Playwright). The shared MSW server errors on unhandled requests
// (test-setup.ts:43), so every test either registers a /mill-context handler or asserts no-fetch with
// a throw-if-called handler plus an axios spy (the AC5 test). Fixtures mirror the V8/V9 backend seeds
// so Playwright (1.5) meets the same shapes. `message` is deliberately present on the success
// fixtures — the AC6 trap test relies on it.
const CONTEXT = 'http://localhost:3000/api/v1/mill-context'
const SUC_001 = { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' }

// V9 514/2020: both tracks with dates (AC1).
const CTX_514_2020 = {
  millId: 514,
  millNumber: '514',
  millName: 'AAA Milling',
  reportYear: 2020,
  schedules1To10Status: { code: 'S', description: 'Submitted', date: '2020-11-30' },
  schedule11Status: { code: 'D', description: 'Draft', date: '2020-08-01' },
  millViewable: true,
  message: SUC_001,
}

// V9 514/2021: Schedule 1-10 only, silviculture (Sch 11) omitted (AC2 per-track independence).
const CTX_514_2021_ONE_TRACK = {
  millId: 514,
  millNumber: '514',
  millName: 'AAA Milling',
  reportYear: 2021,
  schedules1To10Status: { code: 'D', description: 'Draft', date: '2021-05-01' },
  millViewable: true,
  message: SUC_001,
}

// V9 516/2021: closed mill (CLS), Sch 1-10 present but date omitted (AC4 + Date: Not Initiated).
const CTX_516_2021_CLOSED_NO_DATE = {
  millId: 516,
  millNumber: '516',
  millName: 'Riverside Mill',
  reportYear: 2021,
  schedules1To10Status: { code: 'D', description: 'Draft' },
  millViewable: false,
  message: SUC_001,
}

// V8 515/2020: saved (mill, year) with no ILCR_MILL_REPORT_STATUS row — both statuses omitted (S07).
const CTX_515_2020_NO_STATUS = {
  millId: 515,
  millNumber: '515',
  millName: 'Cedar Mill',
  reportYear: 2020,
  millViewable: true,
  message: SUC_001,
}

const problem500 = () =>
  new HttpResponse(JSON.stringify({ status: 500, detail: 'boom' }), {
    status: 500,
    headers: { 'Content-Type': 'application/problem+json' },
  })

// Harness for the AC7 context-change test: a button that switches the working context to 516/2021,
// alongside the banner. Declared at module scope (components must not be defined during render).
function SwitchHarness() {
  const { setContext } = useMillYear()
  return (
    <>
      <button type="button" onClick={() => setContext(516, 2021)}>
        switch
      </button>
      <ContextBanner />
    </>
  )
}

describe('ContextBanner — working context + both track statuses (Story 1.4)', () => {
  test('renders the three legacy lines in a labelled landmark and ignores message (AC1/AC6/AC8)', async () => {
    server.use(http.get(CONTEXT, () => HttpResponse.json(CTX_514_2020)))
    render(
      <MillYearProvider initial={{ millId: 514, year: 2020 }}>
        <ContextBanner />
      </MillYearProvider>,
    )

    // Labelled landmark (WCAG region), not color-only chrome (AC8).
    const banner = await screen.findByRole('region', { name: 'Working context' })
    expect(banner).toBeInTheDocument()

    // Three verbatim legacy lines (AC1).
    expect(screen.getByText('Mill: 514 AAA Milling - Year: 2020')).toBeInTheDocument()
    expect(screen.getByText('Sch 1-10 - Status: Submitted - Date: 2020-11-30')).toBeInTheDocument()
    expect(screen.getByText('Sch 11 - Status: Draft - Date: 2020-08-01')).toBeInTheDocument()

    // AC6 trap: the SUC-001 message rides EVERY 200 but the banner load must never display it.
    expect(screen.queryByText(/Data saved successfully/)).not.toBeInTheDocument()
  })

  test('S07 — no report-status row: mill line only, no status lines, no error (AC3)', async () => {
    server.use(http.get(CONTEXT, () => HttpResponse.json(CTX_515_2020_NO_STATUS)))
    render(
      <MillYearProvider initial={{ millId: 515, year: 2020 }}>
        <ContextBanner />
      </MillYearProvider>,
    )

    expect(await screen.findByText('Mill: 515 Cedar Mill - Year: 2020')).toBeInTheDocument()
    expect(screen.queryByText(/Sch 1-10/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Sch 11/)).not.toBeInTheDocument()
    // Passive chrome: no error surface (AC3/AC8).
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('per-track independence — Sch 1-10 present, Sch 11 omitted → only the 1-10 line (AC2)', async () => {
    server.use(http.get(CONTEXT, () => HttpResponse.json(CTX_514_2021_ONE_TRACK)))
    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        <ContextBanner />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Sch 1-10 - Status: Draft - Date: 2021-05-01'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Sch 11/)).not.toBeInTheDocument()
  })

  test('missing date → "Not Initiated"; closed mill renders like an open mill (AC2/AC4)', async () => {
    server.use(http.get(CONTEXT, () => HttpResponse.json(CTX_516_2021_CLOSED_NO_DATE)))
    render(
      <MillYearProvider initial={{ millId: 516, year: 2021 }}>
        <ContextBanner />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Sch 1-10 - Status: Draft - Date: Not Initiated'),
    ).toBeInTheDocument()
    // AC4: same banner lines as an open mill — no distinct closed-mill text, no millViewable leak.
    expect(screen.getByText('Mill: 516 Riverside Mill - Year: 2021')).toBeInTheDocument()
    expect(screen.queryByText(/closed|not viewable|CLS/i)).not.toBeInTheDocument()
  })

  test('no working context → renders nothing and fires no request (AC5)', () => {
    // Throw-if-called handler (spec-mandated mechanic) PLUS a spy on the axios instance: the banner
    // issues its GET synchronously inside the effect, and RTL's render flushes effects before
    // returning, so the negative assertion is deterministic — a regressed guard fails immediately.
    server.use(
      http.get(CONTEXT, () => {
        throw new Error('ContextBanner must not fetch /mill-context with a null context (AC5)')
      }),
    )
    const getSpy = vi.spyOn(apiService.getAxiosInstance(), 'get')
    try {
      render(
        <MillYearProvider initial={{ millId: null, year: null }}>
          <ContextBanner />
        </MillYearProvider>,
      )

      expect(getSpy).not.toHaveBeenCalled()
      expect(screen.queryByRole('region', { name: 'Working context' })).not.toBeInTheDocument()
    } finally {
      getSpy.mockRestore()
    }
  })

  test('context change refetches and fully replaces the previous context (AC7)', async () => {
    server.use(
      http.get(CONTEXT, ({ request }) => {
        const millId = new URL(request.url).searchParams.get('millId')
        return HttpResponse.json(millId === '514' ? CTX_514_2020 : CTX_516_2021_CLOSED_NO_DATE)
      }),
    )

    render(
      <MillYearProvider initial={{ millId: 514, year: 2020 }}>
        <SwitchHarness />
      </MillYearProvider>,
    )

    expect(await screen.findByText('Mill: 514 AAA Milling - Year: 2020')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'switch' }))

    expect(await screen.findByText('Mill: 516 Riverside Mill - Year: 2021')).toBeInTheDocument()
    // No data from the previous context remains (AC7).
    expect(screen.queryByText('Mill: 514 AAA Milling - Year: 2020')).not.toBeInTheDocument()
    expect(screen.queryByText(/Sch 11/)).not.toBeInTheDocument()
  })

  test('previous context is suppressed WHILE the refetch is in flight — no stale window (AC7)', async () => {
    // Review-finding regression test: gate the new context's response behind a promise so the
    // in-flight window is held open deterministically, then assert the old banner is already gone
    // BEFORE the new data can possibly land.
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get(CONTEXT, async ({ request }) => {
        if (new URL(request.url).searchParams.get('millId') === '516') {
          await gate
          return HttpResponse.json(CTX_516_2021_CLOSED_NO_DATE)
        }
        return HttpResponse.json(CTX_514_2020)
      }),
    )

    render(
      <MillYearProvider initial={{ millId: 514, year: 2020 }}>
        <SwitchHarness />
      </MillYearProvider>,
    )
    expect(await screen.findByText('Mill: 514 AAA Milling - Year: 2020')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'switch' }))

    // The 516 response is still gated: the 514 banner must already be suppressed, not lingering.
    await waitFor(() =>
      expect(screen.queryByText('Mill: 514 AAA Milling - Year: 2020')).not.toBeInTheDocument(),
    )
    expect(screen.queryByText('Mill: 516 Riverside Mill - Year: 2021')).not.toBeInTheDocument()

    release()
    expect(await screen.findByText('Mill: 516 Riverside Mill - Year: 2021')).toBeInTheDocument()
  })

  test('blank-string date and missing description fall back to Not Initiated / code (defensive)', async () => {
    // Out-of-contract shapes (the 1.2 backend collapses blank→null) — proves the || fallbacks:
    // blank date → "Not Initiated" (legacy UserSessionMB.java:374), absent description → code.
    server.use(
      http.get(CONTEXT, () =>
        HttpResponse.json({
          ...CTX_514_2020,
          schedules1To10Status: { code: 'S', description: '', date: '' },
          schedule11Status: { code: 'D' },
        }),
      ),
    )
    render(
      <MillYearProvider initial={{ millId: 514, year: 2020 }}>
        <ContextBanner />
      </MillYearProvider>,
    )

    expect(
      await screen.findByText('Sch 1-10 - Status: S - Date: Not Initiated'),
    ).toBeInTheDocument()
    expect(screen.getByText('Sch 11 - Status: D - Date: Not Initiated')).toBeInTheDocument()
  })

  test('failed fetch suppresses the banner silently, no crash (AC8)', async () => {
    const hit = vi.fn()
    server.use(
      http.get(CONTEXT, () => {
        hit()
        return problem500()
      }),
    )
    render(
      <MillYearProvider initial={{ millId: 514, year: 2020 }}>
        <ContextBanner />
      </MillYearProvider>,
    )

    await waitFor(() => expect(hit).toHaveBeenCalled())
    expect(screen.queryByRole('region', { name: 'Working context' })).not.toBeInTheDocument()
  })
})
