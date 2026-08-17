import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { fetchAuthSession, signInWithRedirect } from 'aws-amplify/auth'
import { isMockAuth } from '@/env'
import { MOCK_USER_STORAGE_KEY, findMockUser } from '@/context/auth/mockUsers'

// Local mock-auth only: mirror the selected mock user's roles to the backend so its (security-off)
// mock principal matches the picker — switching to admin grants ILCR_ADMIN, which admin-only
// endpoints (e.g. MAINTAIN_CODE_TABLES) require. The real security chain ignores this header
// (MockPrincipalFilter); real builds send a Bearer token instead.
function mockUserGroups(): string {
  try {
    return findMockUser(localStorage.getItem(MOCK_USER_STORAGE_KEY)).roles.join(',')
  } catch {
    return ''
  }
}

type RetriableConfig = InternalAxiosRequestConfig & { retriedAfterRefresh?: boolean }

class APIService {
  private readonly client: AxiosInstance

  constructor() {
    this.client = axios.create({
      baseURL: '/api',
      xsrfCookieName: 'XSRF-TOKEN',
      xsrfHeaderName: 'X-XSRF-TOKEN',
      headers: {
        'Content-Type': 'application/json',
      },
    })

    this.client.interceptors.request.use(async (config) => {
      if (isMockAuth()) {
        const groups = mockUserGroups()
        if (groups) {
          config.headers.set('X-Mock-Groups', groups)
        }
        return config
      }
      // Real FAM/Cognito: attach the ID token (Story 1.0). fetchAuthSession refreshes silently, so
      // each request carries a fresh token without a login bounce. Unauthenticated → no header, the
      // backend answers 401 and the response interceptor handles it.
      try {
        const session = await fetchAuthSession()
        const token = session.tokens?.idToken?.toString()
        if (token) {
          config.headers.set('Authorization', `Bearer ${token}`)
        }
      } catch {
        // No session yet — let the request go and 401 drive sign-in.
      }
      return config
    })

    this.client.interceptors.response.use(
      (response: AxiosResponse) => response,
      async (error: unknown) => {
        if (!axios.isAxiosError(error)) {
          console.error('API response error')
          return Promise.reject(error)
        }
        console.error(`API response error status: ${error.response?.status ?? 'unknown'}`)

        const original = error.config as RetriableConfig | undefined
        if (
          isMockAuth() ||
          error.response?.status !== 401 ||
          !original ||
          original.retriedAfterRefresh
        ) {
          return Promise.reject(error)
        }

        // 401 with real auth: try a forced refresh once, retry the request, and only bounce to the
        // Hosted UI if the refresh token itself is gone — so a submitter mid-form is not evicted (O7).
        original.retriedAfterRefresh = true
        try {
          const session = await fetchAuthSession({ forceRefresh: true })
          if (session.tokens?.idToken) {
            return await this.client.request(original)
          }
        } catch {
          // refresh failed — fall through to sign-in
        }
        await signInWithRedirect({
          customState: `${window.location.pathname}${window.location.search}`,
        })
        return Promise.reject(error)
      },
    )
  }

  public getAxiosInstance(): AxiosInstance {
    return this.client
  }
}

export default new APIService()
