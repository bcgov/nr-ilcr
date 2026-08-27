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

/**
 * Deterministic settle: a fixed number of macrotask turns, not a wall-clock sleep — enough for a
 * released response's promise chain to run to completion without racing a loaded runner (the
 * PR #276 settle-sleep lesson).
 */
const drainEventLoop = async (turns = 20) => {
  for (let i = 0; i < turns; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

const renderPicker = (overrides: Partial<Parameters<typeof DirectoryPicker>[0]> = {}) => {
  const onSelect = vi.fn()
  const onError = vi.fn()
  render(<DirectoryPicker selected={null} onSelect={onSelect} onError={onError} {...overrides} />)
  return { onSelect, onError }
}

const combo = () => screen.getByRole('combobox', { name: /user id/i })

/**
 * Outlasts the picker's debounce by timer ordering, not by hope: this timer is scheduled AFTER any
 * debounce timer the preceding interaction could have scheduled and carries a longer delay, so
 * when it fires that debounce has already fired — or was never scheduled at all. The follow-up
 * drain then settles whatever the fired debounce dispatched.
 */
const outlastDebounce = async () => {
  await new Promise((resolve) => setTimeout(resolve, 400))
  await drainEventLoop()
}

describe('DirectoryPicker', () => {
  test('an IDIR term shorter than two characters is never sent (the server would 400 it)', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => HttpResponse.json([IDIR_USER]))
    renderPicker()

    await user.type(combo(), 'A')
    await outlastDebounce()
    expect(seen).toHaveLength(0)

    // Positive control: the same pipeline delivers a request the moment the floor is met, so the
    // absence above cannot be the pipeline simply never running. Interception order is dispatch
    // order, so a late 'A' request could not hide behind this one either.
    await user.type(combo(), 'L')
    await waitFor(() => expect(seen.length).toBeGreaterThan(0))
    expect(seen.map((params) => params.get('userId'))).toEqual(['AL'])
  })

  test('a two-character IDIR term searches by userId, and never by name or guid', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => HttpResponse.json([IDIR_USER]))
    renderPicker()

    await user.type(combo(), 'AL')

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0].get('idp')).toBe('IDIR')
    expect(seen[0].get('userId')).toBe('AL')
    // Sending guid under IDIR is a 400 `error.user.lookup.parameter` by design; the name criteria
    // travel only when the Search-by selector chooses them.
    expect(seen[0].get('userGuid')).toBeNull()
    expect(seen[0].get('firstName')).toBeNull()
    expect(seen[0].get('lastName')).toBeNull()
  })

  test('IDIR can search by last name — the Search-by criterion drives which parameter is sent', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => HttpResponse.json([IDIR_USER]))
    renderPicker()

    await user.click(screen.getByRole('combobox', { name: /search by/i }))
    await user.click(await screen.findByRole('option', { name: 'Last name' }))
    await user.type(screen.getByRole('combobox', { name: /last name/i }), 'Lo')

    await waitFor(() => expect(seen).toHaveLength(1))
    expect(seen[0].get('idp')).toBe('IDIR')
    expect(seen[0].get('lastName')).toBe('Lo')
    // Exactly one criterion travels: a last-name search must not also carry a userId.
    expect(seen[0].get('userId')).toBeNull()
    expect(seen[0].get('firstName')).toBeNull()
  })

  test('choosing a candidate hands the whole directory record back, not just its label', async () => {
    const user = userEvent.setup()
    captureLookups(() => HttpResponse.json([IDIR_USER]))
    const { onSelect } = renderPicker()

    await user.type(combo(), 'AL')
    await user.click(await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }))

    expect(onSelect).toHaveBeenCalledWith(IDIR_USER)
  })

  test('choosing a candidate does not search for its own label or flash a no-match (echo guard)', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => HttpResponse.json([IDIR_USER]))
    const { onSelect } = renderPicker()

    await user.type(combo(), 'AL')
    await user.click(await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }))
    expect(onSelect).toHaveBeenCalledWith(IDIR_USER)

    // Carbon echoes the chosen label into onInputChange; a search for "Ada Lovelace (ALOVELAC)"
    // is a guaranteed miss that would render NO_DIRECTORY_MATCH under a successful pick. Any
    // debounce the echo scheduled has fired and settled by now.
    await outlastDebounce()
    expect(seen.map((params) => params.get('userId'))).toEqual(['AL'])
    expect(screen.queryByText(NO_DIRECTORY_MATCH)).not.toBeInTheDocument()
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

  test('a search pending under the old provider never fires after a provider switch', async () => {
    const user = userEvent.setup()
    // The marker makes the assertion load-tolerant: a debounce that legitimately elapsed BEFORE
    // the switch (a slow runner) is intercepted before the marker, while the defect — a pending
    // timer claiming the newest sequence token at fire time and dispatching idp=IDIR from its
    // stale closure — can only land AFTER it.
    const seen: Array<URLSearchParams | 'SWITCHED'> = []
    server.use(
      http.get(LOOKUP, ({ request }) => {
        seen.push(new URL(request.url).searchParams)
        return HttpResponse.json([])
      }),
    )
    renderPicker()

    await user.type(combo(), 'AL')
    await user.click(screen.getByRole('combobox', { name: /identity provider/i }))
    await user.click(await screen.findByRole('option', { name: 'BCeID Business' }))
    seen.push('SWITCHED')

    await outlastDebounce()
    expect(seen.slice(seen.indexOf('SWITCHED') + 1)).toEqual([])

    // Positive control: the next search goes out under the new provider only.
    await user.type(combo(), 'B')
    await waitFor(() => expect(seen.length).toBeGreaterThan(seen.indexOf('SWITCHED') + 1))
    const afterSwitch = seen.slice(seen.indexOf('SWITCHED') + 1) as URLSearchParams[]
    expect(afterSwitch.map((params) => params.get('idp'))).toEqual(['BCEIDBUSINESS'])
  })

  test('switching provider clears the typed criterion, and BCeID offers no Search-by', async () => {
    const user = userEvent.setup()
    captureLookups(() => HttpResponse.json([IDIR_USER]))
    renderPicker()

    await user.type(combo(), 'ALOVELAC')
    await user.click(screen.getByRole('combobox', { name: /identity provider/i }))
    await user.click(await screen.findByRole('option', { name: 'BCeID Business' }))

    // Stale text over an emptied result list would read as "searched, nothing found".
    expect(combo()).toHaveValue('')
    // BCeID is an exact lookup by user ID only, so the criterion selector renders for IDIR alone.
    expect(screen.queryByRole('combobox', { name: /search by/i })).not.toBeInTheDocument()
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

  test('a search matching nobody says so, in a live region, rather than showing an empty list (AC8)', async () => {
    const user = userEvent.setup()
    captureLookups(() => HttpResponse.json([]))
    renderPicker()

    await user.type(combo(), 'ZZ')

    const note = await screen.findByText(NO_DIRECTORY_MATCH)
    // role="status": the note appears after the fact, and silence here is the recorded app-wide
    // announce-nothing defect class this screen must not extend (NFR1).
    expect(note).toHaveAttribute('role', 'status')
  })

  test('the flag-off 404 reads as not-enabled, not as a failure, and stops further searching (AC7)', async () => {
    const user = userEvent.setup()
    const seen = captureLookups(() => new HttpResponse(null, { status: 404 }))
    const { onError } = renderPicker()

    await user.type(combo(), 'AL')

    const note = await screen.findByText(DIRECTORY_DISABLED)
    expect(note).toHaveAttribute('role', 'status')
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
    let releaseFirst!: () => void
    const firstBlocked = new Promise<void>((resolve) => {
      releaseFirst = resolve
    })
    let calls = 0
    server.use(
      http.get(LOOKUP, async () => {
        calls += 1
        if (calls === 1) {
          // Held, not slept: the race is guaranteed rather than hoped for — this response can only
          // land after the newer one has already rendered.
          await firstBlocked
          return HttpResponse.json([slow])
        }
        return HttpResponse.json([IDIR_USER])
      }),
    )
    renderPicker()

    await user.type(combo(), 'AL')
    await waitFor(() => expect(calls).toBe(1))
    await user.type(combo(), 'OV')
    expect(
      await screen.findByRole('option', { name: 'Ada Lovelace (ALOVELAC)' }),
    ).toBeInTheDocument()

    releaseFirst()
    await drainEventLoop()
    expect(screen.queryByRole('option', { name: /Stale Result/ })).not.toBeInTheDocument()
  })
})
