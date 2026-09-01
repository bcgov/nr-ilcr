import { cleanup, render } from '@testing-library/react'
import { afterEach } from 'vitest'
import AppProviders from '@/app/AppProviders'
import MillYearProvider from '@/context/millYear/MillYearProvider'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from '@/context/millYear/millYearDefaults'

afterEach(() => {
  cleanup()
})

// Every suite rendered through here expects a WORKING CONTEXT — schedule pages assert content, not
// the "Please Select Mill and Reporting Year" guard. That used to come from MillYearProvider's own
// 13050/2017 dev default inside AppProviders; now that the app starts with no context (so Home can
// show its Select Mill / Select Reporting Year placeholders), the test harness seeds it explicitly.
// Nested INSIDE AppProviders so it overrides the empty context for the tree under test, and a suite
// that needs a different (or deliberately empty) context still wins by rendering its own provider.
function customRender(ui: React.ReactElement, options = {}) {
  return render(ui, {
    wrapper: ({ children }) => (
      <AppProviders>
        <MillYearProvider initial={{ millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }}>
          {children}
        </MillYearProvider>
      </AppProviders>
    ),
    ...options,
  })
}

export * from '@testing-library/react'
export { default as userEvent } from '@testing-library/user-event'
// override render export
export { customRender as render }
