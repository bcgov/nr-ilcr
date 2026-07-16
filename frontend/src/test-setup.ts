import '@testing-library/jest-dom'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'

class ResizeObserverMock implements ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

globalThis.ResizeObserver = ResizeObserverMock

const users = [
  {
    id: 1,
    name: 'ILCR Developer',
    email: 'ilcr.dev@gov.bc.ca',
  },
]

export const restHandlers = [
  http.get('http://localhost:3000/api/v1/users', () => {
    return new HttpResponse(JSON.stringify(users), {
      status: 200,
    })
  }),
]

const server = setupServer(...restHandlers)

// Start server before all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

//  Close server after all tests
afterAll(() => server.close())

// Reset handlers after each test `important for test isolation`
afterEach(() => server.resetHandlers())
