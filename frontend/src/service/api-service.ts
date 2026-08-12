import type { AxiosInstance, AxiosResponse } from 'axios'
import axios from 'axios'
import { MOCK_USER_STORAGE_KEY, findMockUser } from '@/context/auth/mockUsers'

// Dev/mock-auth only: the SPA's selected mock user roles, mirrored to the backend so its (security-off)
// mock principal matches the user picker — switching to an admin user grants ILCR_ADMIN, which the
// admin-only endpoints (e.g. MAINTAIN_CODE_TABLES) require. The real security chain (prod) ignores this
// header (see MockPrincipalFilter); remove this interceptor when real FAM auth lands.
function mockUserGroups(): string {
  try {
    return findMockUser(localStorage.getItem(MOCK_USER_STORAGE_KEY)).roles.join(',')
  } catch {
    return ''
  }
}

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
    // Only in a dev build — a production bundle must not send the mock-role header (it would be
    // meaningless to the real security chain and needlessly leak the mock role names).
    if (import.meta.env.DEV) {
      this.client.interceptors.request.use((config) => {
        const groups = mockUserGroups()
        if (groups) {
          config.headers.set('X-Mock-Groups', groups)
        }
        return config
      })
    }
    this.client.interceptors.response.use(
      (response: AxiosResponse) => {
        console.info(`received response status: ${response.status}`)
        return response
      },
      (error: unknown) => {
        if (axios.isAxiosError(error)) {
          console.error(`API response error status: ${error.response?.status ?? 'unknown'}`)
        } else {
          console.error('API response error')
        }
        return Promise.reject(error)
      },
    )
  }

  public getAxiosInstance(): AxiosInstance {
    return this.client
  }
}

export default new APIService()
