import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test-setup'

const mocks = vi.hoisted(() => ({
  fetchAuthSession: vi.fn(),
  signInWithRedirect: vi.fn(),
}))

// Real-auth branch under test: force isMockAuth() false so the interceptors attach a Bearer and run
// the 401 refresh/bounce path (the mock branch is covered by the existing mock-mode suites).
vi.mock('@/env', () => ({
  isMockAuth: () => false,
  getAmplifyRuntimeConfig: () => ({}),
}))
vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: mocks.fetchAuthSession,
  signInWithRedirect: mocks.signInWithRedirect,
}))

const PROBE = 'http://localhost:3000/api/v1/probe'

async function importClient() {
  const mod = await import('./api-service')
  return mod.default.getAxiosInstance()
}

beforeEach(() => {
  mocks.fetchAuthSession.mockReset()
  mocks.signInWithRedirect.mockReset().mockResolvedValue(undefined)
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('APIService real-auth interceptors', () => {
  it('attaches the ID token as a Bearer on each request', async () => {
    mocks.fetchAuthSession.mockResolvedValue({
      tokens: { idToken: { toString: () => 'ID-TOKEN' } },
    })
    let seenAuth: string | null = null
    server.use(
      http.get(PROBE, ({ request }) => {
        seenAuth = request.headers.get('authorization')
        return HttpResponse.json({ ok: true })
      }),
    )

    const client = await importClient()
    await client.get('/v1/probe')

    expect(seenAuth).toBe('Bearer ID-TOKEN')
  })

  it('on 401 refreshes the token and retries the request once', async () => {
    mocks.fetchAuthSession.mockResolvedValue({
      tokens: { idToken: { toString: () => 'ID-TOKEN' } },
    })
    let calls = 0
    server.use(
      http.get(PROBE, () => {
        calls += 1
        return calls === 1
          ? new HttpResponse(null, { status: 401 })
          : HttpResponse.json({ ok: true })
      }),
    )

    const client = await importClient()
    const res = await client.get('/v1/probe')

    expect(calls).toBe(2)
    expect(res.status).toBe(200)
    expect(mocks.signInWithRedirect).not.toHaveBeenCalled()
  })

  it('on 401 bounces to sign-in when the refresh token is gone', async () => {
    // Request interceptor gets a token; the forced refresh returns none → session is dead → bounce.
    mocks.fetchAuthSession.mockImplementation((opts?: { forceRefresh?: boolean }) =>
      Promise.resolve(
        opts?.forceRefresh
          ? { tokens: undefined }
          : { tokens: { idToken: { toString: () => 'ID-TOKEN' } } },
      ),
    )
    server.use(http.get(PROBE, () => new HttpResponse(null, { status: 401 })))

    const client = await importClient()
    await expect(client.get('/v1/probe')).rejects.toBeDefined()

    expect(mocks.signInWithRedirect).toHaveBeenCalledTimes(1)
  })

  it('on a transient forced-refresh failure it surfaces the 401 without bouncing (session preserved)', async () => {
    // forceRefresh rejects (network/provider outage) but a non-forced session still holds a token,
    // so the route must be preserved — do NOT discard it into a fresh Hosted UI flow (O7).
    mocks.fetchAuthSession.mockImplementation((opts?: { forceRefresh?: boolean }) =>
      opts?.forceRefresh
        ? Promise.reject(new Error('network outage'))
        : Promise.resolve({ tokens: { idToken: { toString: () => 'ID-TOKEN' } } }),
    )
    server.use(http.get(PROBE, () => new HttpResponse(null, { status: 401 })))

    const client = await importClient()
    await expect(client.get('/v1/probe')).rejects.toBeDefined()

    expect(mocks.signInWithRedirect).not.toHaveBeenCalled()
  })

  it('on 401 bounces when a forced-refresh failure leaves no usable session', async () => {
    // forceRefresh rejects AND no session remains (refresh token truly gone) → Amplify confirms no
    // usable session → bounce.
    mocks.fetchAuthSession.mockImplementation((opts?: { forceRefresh?: boolean }) =>
      opts?.forceRefresh
        ? Promise.reject(new Error('no refresh token'))
        : Promise.resolve({ tokens: undefined }),
    )
    server.use(http.get(PROBE, () => new HttpResponse(null, { status: 401 })))

    const client = await importClient()
    await expect(client.get('/v1/probe')).rejects.toBeDefined()

    expect(mocks.signInWithRedirect).toHaveBeenCalledTimes(1)
  })
})
