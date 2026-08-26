import { describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, userEvent, waitFor } from '@/test-utils'
import { server } from '@/test-setup'
import DirectoryPicker, { DIRECTORY_DISABLED, NO_DIRECTORY_MATCH } from '../DirectoryPicker'

const LOOKUP = 'http://localhost:3000/api/v1/users/lookup'

const IDIR_USER = {
  userGuid: 'A'.repeat(32),
  displayName: 'Ada Lovelace',
  idpUsername: 'ALOVELAC',
  identityProvider: 'IDIR',
}

const problemBody = (status: number, detail: string) =>
  new HttpResponse(JSON.stringify({ detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

/** Records every lookup request so the tests can assert what was — and was not — sent. */
const captureLookups = (respond: () => HttpResponse) => {
  const seen: URLSearchParams[] = []
  server.use(
    http.get(LOOKUP, ({ request }) => {
      seen.push(new URL(request.url).searchParams)
      return respond()
    }),
  )
  return seen
}

const renderPicker = (overrides: Partial<Parameters<typeof DirectoryPicker>[0]> = {}) => {
  const onSelect = vi.fn()
  const onError = vi.fn()
  render(<DirectoryPicker selected={null} onSelect={onSelect} onError={onError} {...overrides} />)
  return { onSelect, onError }
}

const combo = () => screen.getByRole('combobox', { name: /user id/i })

describe('DirectoryPicker', () => {
  test('an IDIR term shorter than two characters is never sent (the server would 400 it)', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => HttpResponse.json([IDIR_USER]))
    renderPicker()

    await user.type(combo(), 'A')

    // The debounce has to be given a chance to fire before "no request" means anything.
    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(seen).toHaveLength(0)
  })

  test('a two-character IDIR term searches by userId, and never by name or guid', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => HttpResponse.json([IDIR_USER]))
    renderPicker()

    await user.type(combo(), 'AL')

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0].get('idp')).toBe('IDIR')
    expect(seen[0].get('userId')).toBe('AL')
    // Sending either under IDIR is a 400 `error.user.lookup.parameter` by design.
    expect(seen[0].get('userGuid')).toBeNull()
    expect(seen[0].get('firstName')).toBeNull()
    expect(seen[0].get('lastName')).toBeNull()
  })

  test('choosing a candidate hands the whole directory record back, not just its label', async () => {
    const user = userEvent.setup()
    captureLookups(() => HttpResponse.json([IDIR_USER]))
    const { onSelect } = renderPicker()

    await user.type(combo(), 'AL')
    await user.click(await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }))

    expect(onSelect).toHaveBeenCalledWith(IDIR_USER)
  })

  test('a candidate with no directory name still lists, under its username', async () => {
    const user = userEvent.setup()
    captureLookups(() => HttpResponse.json([{ ...IDIR_USER, displayName: null }]))
    renderPicker()

    await user.type(combo(), 'AL')

    expect(await screen.findByRole('option', { name: 'ALOVELAC' })).toBeInTheDocument()
  })

  test('switching provider drops the carried selection and clears the previous results', async () => {
    const user = userEvent.setup()
    captureLookups(() => HttpResponse.json([IDIR_USER]))
    const { onSelect } = renderPicker({ selected: IDIR_USER })

    await user.type(combo(), 'AL')
    expect(
      await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: /identity provider/i }))
    await user.click(await screen.findByRole('option', { name: 'BCeID Business' }))

    expect(onSelect).toHaveBeenCalledWith(null)
    expect(
      screen.queryByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }),
    ).not.toBeInTheDocument()
  })

  test('a BCeID term of one character IS sent — only IDIR carries the two-character floor', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => HttpResponse.json([]))
    renderPicker()

    await user.click(screen.getByRole('combobox', { name: /identity provider/i }))
    await user.click(await screen.findByRole('option', { name: 'BCeID Business' }))
    await user.type(combo(), 'B')

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0].get('idp')).toBe('BCEIDBUSINESS')
    expect(seen[0].get('userId')).toBe('B')
    // firstName/lastName under BCeID are a 400 `error.user.lookup.parameter`.
    expect(seen[0].get('firstName')).toBeNull()
    expect(seen[0].get('lastName')).toBeNull()
  })

  test('a search matching nobody says so, rather than showing an empty list (AC8)', async () => {
    const user = userEvent.setup()
    captureLookups(() => HttpResponse.json([]))
    renderPicker()

    await user.type(combo(), 'ZZ')

    expect(await screen.findByText(NO_DIRECTORY_MATCH)).toBeInTheDocument()
  })

  test('the flag-off 404 reads as not-enabled, not as a failure, and stops further searching (AC7)', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => new HttpResponse(null, { status: 404 }))
    const { onError } = renderPicker()

    await user.type(combo(), 'AL')

    expect(await screen.findByText(DIRECTORY_DISABLED)).toBeInTheDocument()
    // Not an error banner: this is the shipped default in every environment today.
    expect(onError).not.toHaveBeenCalledWith(expect.any(String))
    expect(screen.queryByText(NO_DIRECTORY_MATCH)).not.toBeInTheDocument()

    // Latched — the control is disabled and no further keystroke can fire another doomed search.
    expect(combo()).toBeDisabled()
    await waitFor(() => expect(seen).toHaveLength(1))
  })

  test('a 502 surfaces the directory verbatim page-level, and shows no no-match text (AC6)', async () => {
    const user = userEvent.setup()
    const detail = 'The user directory is currently unavailable. Please try again later.'
    captureLookups(() => problemBody(502, detail))
    const { onError } = renderPicker()

    await user.type(combo(), 'AL')

    await waitFor(() => expect(onError).toHaveBeenCalledWith(detail))
    // A failed search is not a no-match; conflating them would tell the admin the person does not
    // exist when the directory simply could not answer.
    expect(screen.queryByText(NO_DIRECTORY_MATCH)).not.toBeInTheDocument()
  })

  test('a rejected search surfaces the backend criteria text verbatim, not a rewritten one', async () => {
    const user = userEvent.setup()
    const detail = 'Each search criterion must be at least 2 characters.'
    captureLookups(() => problemBody(400, detail))
    const { onError } = renderPicker()

    await user.type(combo(), 'AL')

    await waitFor(() => expect(onError).toHaveBeenCalledWith(detail))
  })

  test('a slow earlier response cannot overwrite the newer suggestions', async () => {
    const user = userEvent.setup()
    const slow = { ...IDIR_USER, displayName: 'Stale Result', idpUsername: 'STALE' }
    let call = 0
    server.use(
      http.get(LOOKUP, async () => {
        call += 1
        if (call === 1) {
          await new Promise((resolve) => setTimeout(resolve, 300))
          return HttpResponse.json([slow])
        }
        return HttpResponse.json([IDIR_USER])
      }),
    )
    renderPicker()

    await user.type(combo(), 'AL')
    // Past the debounce, so the first (slow) search is genuinely in flight before the second starts.
    await new Promise((resolve) => setTimeout(resolve, 300))
    await user.type(combo(), 'OV')

    expect(
      await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }),
    ).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(screen.queryByRole('option', { name: /Stale Result/ })).not.toBeInTheDocument()
  })
})
