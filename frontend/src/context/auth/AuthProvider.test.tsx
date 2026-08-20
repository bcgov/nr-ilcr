import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

const isMockAuth = vi.hoisted(() => vi.fn())
vi.mock('@/env', () => ({ isMockAuth }))
vi.mock('./RealAuthProvider', () => ({
  default: ({ children }: { children: ReactNode }) => <div data-testid="real">{children}</div>,
}))
vi.mock('./MockAuthProvider', () => ({
  default: ({ children }: { children: ReactNode }) => <div data-testid="mock">{children}</div>,
}))

import AuthProvider from './AuthProvider'

describe('AuthProvider seam', () => {
  it('selects the mock provider when isMockAuth() is true', () => {
    isMockAuth.mockReturnValue(true)
    render(
      <AuthProvider>
        <span>child</span>
      </AuthProvider>,
    )
    expect(screen.getByTestId('mock')).toBeInTheDocument()
  })

  it('selects the real (Amplify) provider otherwise', () => {
    isMockAuth.mockReturnValue(false)
    render(
      <AuthProvider>
        <span>child</span>
      </AuthProvider>,
    )
    expect(screen.getByTestId('real')).toBeInTheDocument()
  })
})
