import { cleanup, render } from '@testing-library/react'
import { afterEach } from 'vitest'
import AppProviders from '@/app/AppProviders'

afterEach(() => {
  cleanup()
})

function customRender(ui: React.ReactElement, options = {}) {
  return render(ui, {
    wrapper: ({ children }) => <AppProviders>{children}</AppProviders>,
    ...options,
  })
}

export * from '@testing-library/react'
export { default as userEvent } from '@testing-library/user-event'
// override render export
export { customRender as render }
