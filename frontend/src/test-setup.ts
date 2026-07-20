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

// Canonical Schedule 1 document (matches the Story 1.2 backend doc / V3 seed) for the default handler.
const schedule1Doc = {
  millId: 514,
  year: 2021,
  trackStatus: 'D',
  editable: true,
  crownVolume: 12345,
  revisionCount: 3,
  comments: 'Seed comment for 514/2021',
  lineItems: [{ costItemCode: 12, volume: 1000, cost: 50000, perUnit: 50.0 }],
  silviculture: {
    actualSpent: { costItemCode: 1, volume: 500, cost: 20000, perUnit: 40.0 },
    accruedLessActual: null,
    lessAdmin: null,
    total: null,
  },
  forestMgmtAdminCost: null,
  lessSilvAdminCost: null,
  otherCosts: { volume: 8000, costSubtotal: 24000, perUnit: 3.0, count: 2 },
}

export const restHandlers = [
  http.get('http://localhost:3000/api/v1/users', () => {
    return new HttpResponse(JSON.stringify(users), {
      status: 200,
    })
  }),
  http.get('http://localhost:3000/api/v1/schedule1', () => HttpResponse.json(schedule1Doc)),
]

// Exported so tests can register per-scenario handlers with server.use(...).
export const server = setupServer(...restHandlers)

// Start server before all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

//  Close server after all tests
afterAll(() => server.close())

// Reset handlers after each test `important for test isolation`
afterEach(() => server.resetHandlers())
