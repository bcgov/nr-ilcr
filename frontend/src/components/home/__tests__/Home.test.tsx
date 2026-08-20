import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider (AppProviders has none). Mock the router
// exactly like Schedule1.test.tsx; stub Link as a passthrough in case it renders.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

// jsdom lacks scrollIntoView; Carbon's Dropdown calls it on the highlighted option whenever the
// menu opens with a selectedItem (the context-reflection/pre-select paths). Same shim class as the
// ResizeObserver mock in test-setup.ts, kept local to the Home suite.
window.HTMLElement.prototype.scrollIntoView = vi.fn()

import Home from '@/components/home'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import useMillYear from '@/context/millYear/useMillYear'

const MILLS = 'http://localhost:3000/api/v1/mills'
const YEARS = 'http://localhost:3000/api/v1/reporting-years'
const CONTEXT = 'http://localhost:3000/api/v1/mill-context'
const MINE = 'http://localhost:3000/api/v1/home-content/mine'

const MILLS_TWO = [
  { millId: 514, millNumber: '514', millName: 'AAA Milling', millStatusCode: 'ACT' },
  { millId: 516, millNumber: '516', millName: 'Closed Milling', millStatusCode: 'CLS' },
]
const YEARS_TWO = [{ reportYear: 2021 }, { reportYear: 2020 }]

const SUC_001 = { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' }

const problem400 = (messages: { key: string; text: string }[]) =>
  new HttpResponse(
    JSON.stringify({ status: 400, detail: messages.map((m) => m.text).join('; '), messages }),
    { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
  )

// Renders the live MillYearContext so tests can assert setContext was / was not invoked.
function ContextProbe() {
  const { millId, year } = useMillYear()
  return <div data-testid="ctx">{`${String(millId)}/${String(year)}`}</div>
}

const listHandlers = (mills = MILLS_TWO, years = YEARS_TWO) => [
  http.get(MILLS, () => HttpResponse.json(mills)),
  http.get(YEARS, () => HttpResponse.json(years)),
]

// Open a Carbon Dropdown by its visible label and pick an option by its text. Uses findByRole for
// the toggle so it waits out the on-mount list load (the page shows a LoadingScreen until then).
async function selectFromDropdown(
  user: ReturnType<typeof userEvent.setup>,
  label: RegExp,
  option: RegExp,
) {
  await user.click(await screen.findByRole('combobox', { name: label }))
  await user.click(await screen.findByRole('option', { name: option }))
}

describe('Home — select mill and year (Story 1.3)', () => {
  test('renders both dropdowns populated from the list endpoints (AC1)', async () => {
    server.use(...listHandlers())
    render(<Home />)

    // Both dropdowns are labelled and keyboard-operable (AC1/AC6).
    expect(await screen.findByRole('combobox', { name: /Mill/i })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /Reporting Year/i })).toBeInTheDocument()

    // Options come from the handlers, labelled `{millNumber} - {millName}` and by reportYear.
    const user = userEvent.setup()
    await user.click(screen.getByRole('combobox', { name: /Mill/i }))
    expect(await screen.findByRole('option', { name: '514 - AAA Milling' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '516 - Closed Milling' })).toBeInTheDocument()
  })

  test('successful Save posts the chosen params and shows the SUC-001 message from the API (AC2)', async () => {
    let requestedUrl = ''
    server.use(
      ...listHandlers(),
      http.get(CONTEXT, ({ request }) => {
        requestedUrl = request.url
        return HttpResponse.json({
          millId: 514,
          millNumber: '514',
          millName: 'AAA Milling',
          reportYear: 2021,
          millViewable: true,
          message: SUC_001,
        })
      }),
    )
    render(
      <MillYearProvider initial={{ millId: 999, year: 1999 }}>
        <Home />
        <ContextProbe />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await selectFromDropdown(user, /Mill/i, /514 - AAA Milling/)
    await selectFromDropdown(user, /Reporting Year/i, /^2021$/)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    // SUC-001 text is rendered verbatim from message.text (AD-8), never hardcoded.
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    // Request carried the chosen mill + year.
    expect(requestedUrl).toContain('millId=514')
    expect(requestedUrl).toContain('year=2021')
    // Context established from the 200 (AR11).
    await waitFor(() => expect(screen.getByTestId('ctx')).toHaveTextContent('514/2021'))
  })

  test('single-mill list pre-selects that mill (S02)', async () => {
    // Mill 777 deliberately does NOT match the provider's default context (514/2021), so this
    // exercises the S02 single-mill fallback rather than the context-reflection path.
    const one = [
      { millId: 777, millNumber: '777', millName: 'Solo Milling', millStatusCode: 'ACT' },
    ]
    server.use(...listHandlers(one))
    render(<Home />)

    // The sole mill is pre-selected — its label shows on the Mill dropdown without user action.
    expect(await screen.findByRole('combobox', { name: /Mill/i })).toHaveTextContent(
      '777 - Solo Milling',
    )
  })

  test('reflects the saved working context and allows a year-only change (AC4/S03, legacy parity)', async () => {
    let requestedUrl = ''
    server.use(
      ...listHandlers(),
      http.get(CONTEXT, ({ request }) => {
        requestedUrl = request.url
        return HttpResponse.json({
          millId: 514,
          millNumber: '514',
          millName: 'AAA Milling',
          reportYear: 2020,
          millViewable: true,
          message: SUC_001,
        })
      }),
    )
    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        <Home />
        <ContextProbe />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    // The current context renders in the dropdowns (legacy home.xhtml binds them to the session).
    expect(await screen.findByRole('combobox', { name: /Mill/i })).toHaveTextContent(
      '514 - AAA Milling',
    )
    expect(screen.getByRole('combobox', { name: /Reporting Year/i })).toHaveTextContent('2021')

    // Change ONLY the year and Save — the retained mill rides along (AC4 "and/or").
    await selectFromDropdown(user, /Reporting Year/i, /^2020$/)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(requestedUrl).toContain('millId=514')
    expect(requestedUrl).toContain('year=2020')
    await waitFor(() => expect(screen.getByTestId('ctx')).toHaveTextContent('514/2020'))
  })

  test('change-context replaces the previous working context and re-shows SUC-001 (S03)', async () => {
    server.use(
      ...listHandlers(),
      http.get(CONTEXT, () =>
        HttpResponse.json({
          millId: 516,
          millNumber: '516',
          millName: 'Closed Milling',
          reportYear: 2020,
          millViewable: false,
          message: SUC_001,
        }),
      ),
    )
    render(
      <MillYearProvider initial={{ millId: 514, year: 2021 }}>
        <Home />
        <ContextProbe />
      </MillYearProvider>,
    )
    const user = userEvent.setup()
    expect(screen.getByTestId('ctx')).toHaveTextContent('514/2021')

    await selectFromDropdown(user, /Mill/i, /516 - Closed Milling/)
    await selectFromDropdown(user, /Reporting Year/i, /^2020$/)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('ctx')).toHaveTextContent('516/2020'))

    // Changing a selection after a Save clears the stale confirmation — the new selection is
    // unsaved and must not look saved.
    await selectFromDropdown(user, /Reporting Year/i, /^2021$/)
    await waitFor(() =>
      expect(screen.queryByText('Data saved successfully')).not.toBeInTheDocument(),
    )
  })

  test('Save on placeholders shows the verbatim 400 field messages and leaves context unchanged (S04/S05/S08)', async () => {
    server.use(
      ...listHandlers(),
      http.get(CONTEXT, () =>
        problem400([
          { key: 'javax.faces.component.UIInput.REQUIRED', text: 'Mill: Value is required.' },
          {
            key: 'javax.faces.component.UIInput.REQUIRED',
            text: 'Reporting Year: Value is required.',
          },
        ]),
      ),
    )
    render(
      <MillYearProvider initial={{ millId: 999, year: 1999 }}>
        <Home />
        <ContextProbe />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await screen.findByRole('combobox', { name: /Mill/i })
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    // Both verbatim backend messages render together (S08).
    expect(await screen.findByText('Mill: Value is required.')).toBeInTheDocument()
    expect(screen.getByText('Reporting Year: Value is required.')).toBeInTheDocument()
    // setContext was NOT called on the 400 — the existing context is untouched (S04/S05).
    expect(screen.getByTestId('ctx')).toHaveTextContent('999/1999')
  })

  test('missing mill only (S04) — its verbatim message renders alone, context unchanged', async () => {
    server.use(
      ...listHandlers(),
      http.get(CONTEXT, () =>
        problem400([
          { key: 'javax.faces.component.UIInput.REQUIRED', text: 'Mill: Value is required.' },
        ]),
      ),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Home />
        <ContextProbe />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    // Year chosen, mill left on its placeholder.
    await selectFromDropdown(user, /Reporting Year/i, /^2021$/)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Mill: Value is required.')).toBeInTheDocument()
    expect(screen.queryByText('Reporting Year: Value is required.')).not.toBeInTheDocument()
    expect(screen.getByTestId('ctx')).toHaveTextContent('null/null')
  })

  test('missing year only (S05) — its verbatim message renders alone; the mill was sent', async () => {
    let requestedUrl = ''
    server.use(
      ...listHandlers(),
      http.get(CONTEXT, ({ request }) => {
        requestedUrl = request.url
        return problem400([
          {
            key: 'javax.faces.component.UIInput.REQUIRED',
            text: 'Reporting Year: Value is required.',
          },
        ])
      }),
    )
    render(
      <MillYearProvider initial={{ millId: null, year: null }}>
        <Home />
        <ContextProbe />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    // Mill chosen, year left on its placeholder.
    await selectFromDropdown(user, /Mill/i, /514 - AAA Milling/)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Reporting Year: Value is required.')).toBeInTheDocument()
    expect(screen.queryByText('Mill: Value is required.')).not.toBeInTheDocument()
    // The chosen mill was sent; the empty year went through verbatim (backend-authoritative).
    expect(requestedUrl).toContain('millId=514')
    expect(requestedUrl).toContain('year=')
    expect(screen.getByTestId('ctx')).toHaveTextContent('null/null')
  })

  test('renders the sanitized role welcome message when one is returned (Story 24.2)', async () => {
    server.use(
      ...listHandlers(),
      http.get(MINE, () =>
        HttpResponse.json({
          role: 'LICENSEE',
          messageText: '<p>Welcome, <strong>licensee</strong></p><script>alert(1)</script>',
        }),
      ),
    )
    render(<Home />)

    // The formatting survives; the <script> is stripped by DOMPurify (sanitizeHtml) before render.
    expect(await screen.findByText('licensee')).toBeInTheDocument()
    expect(screen.queryByText('alert(1)')).not.toBeInTheDocument()
  })

  test('list-load failure renders the problem detail in an error notification', async () => {
    server.use(
      http.get(
        MILLS,
        () =>
          new HttpResponse(JSON.stringify({ detail: 'Mills are unavailable.' }), {
            status: 500,
            headers: { 'Content-Type': 'application/problem+json' },
          }),
      ),
      http.get(YEARS, () => HttpResponse.json(YEARS_TWO)),
    )
    render(<Home />)

    expect(await screen.findByText('Mills are unavailable.')).toBeInTheDocument()
  })

  test('404 save (pinned contract: unknown mill / unopened year) — detail renders, context unchanged', async () => {
    server.use(
      ...listHandlers(),
      http.get(
        CONTEXT,
        () =>
          new HttpResponse(JSON.stringify({ detail: 'Mill or Reporting Year not found.' }), {
            status: 404,
            headers: { 'Content-Type': 'application/problem+json' },
          }),
      ),
    )
    render(
      <MillYearProvider initial={{ millId: 999, year: 1999 }}>
        <Home />
        <ContextProbe />
      </MillYearProvider>,
    )
    const user = userEvent.setup()

    await selectFromDropdown(user, /Mill/i, /514 - AAA Milling/)
    await selectFromDropdown(user, /Reporting Year/i, /^2021$/)
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    // No `messages` array on the 404 — extractSaveErrors falls back to the verbatim detail.
    expect(await screen.findByText('Mill or Reporting Year not found.')).toBeInTheDocument()
    expect(screen.getByTestId('ctx')).toHaveTextContent('999/1999')
  })
})
