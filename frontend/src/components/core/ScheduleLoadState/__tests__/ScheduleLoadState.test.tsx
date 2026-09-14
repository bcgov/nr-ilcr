import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { render, screen } from '@/test-utils'
import {
  ERR_MILL_CLOSED,
  ERR_MILL_YEAR_NOT_SELECTED,
  renderScheduleLoadState,
} from '@/components/core/ScheduleLoadState'

// The shared guard surface had no test of its own: the four states were exercised only incidentally
// through the four pages that use it (7A/7B/9/10), so the ORDER of the checks — the thing that
// decides whether a closed mill reads as its own state or as a generic load failure — could regress
// with every page suite green (Story 16.3 S12/S13).
//
// Every string here is the legacy bundle text VERBATIM (AD-8), sourced from
// `backend/src/main/resources/messages.properties`:
//   millYearNotSelectedErrorMsg   :9   (the client-only copy drops the bundle's trailing space —
//                                       a sibling convention documented on the export)
//   millNotActiveForCurrentYearMsg:10  ERR-002, served as the 409 `detail`
//   scheduleNotEditableErrorMsg   :25  the role×status denial, served as a 409 `detail`
const NOT_EDITABLE = 'This schedule cannot be edited in its current status.'

const HEADER = <h1>Schedule 9</h1>

const base = {
  header: HEADER,
  scheduleName: 'Schedule 9',
  contextMissing: false,
  isLoading: false,
}

/** Render whatever the guard returned; `null` (a document to show) is a test failure here. */
const renderState = (options: Parameters<typeof renderScheduleLoadState>[0]) => {
  const state = renderScheduleLoadState(options)
  if (!state) {
    throw new Error('expected a guard state, got null (the page would render its work area)')
  }
  return render(state)
}

/** No guard state may leave the work area reachable — legacy suppressed the form in all four. */
const expectFormSuppressed = () => {
  expect(screen.queryByRole('button', { name: /^save$/i })).not.toBeInTheDocument()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
}

describe('renderScheduleLoadState guard states', () => {
  test('S12 no mill/year context: ERR-001 verbatim, form suppressed', () => {
    renderState({ ...base, contextMissing: true })

    expect(screen.getByText('Mill and Reporting Year required')).toBeInTheDocument()
    expect(
      screen.getByText('Please Select Mill and Reporting Year in the Home Page.'),
    ).toBeInTheDocument()
    // The exported literal IS the asserted sentence — a drift in either fails here.
    expect(ERR_MILL_YEAR_NOT_SELECTED).toBe(
      'Please Select Mill and Reporting Year in the Home Page.',
    )
    expectFormSuppressed()
  })

  test('S13 closed mill: ERR-002 gets its OWN state, not the generic load failure', () => {
    renderState({ ...base, errorDetail: ERR_MILL_CLOSED })

    expect(
      screen.getByText(
        'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
      ),
    ).toBeInTheDocument()
    // Its own title, in the same family as the mill/year guard — and crucially NOT the generic
    // "Unable to load Schedule 9", which framed a context the operator must change on the Home Page
    // as a failure of the page.
    expect(screen.getByText('Mill not active for Reporting Year')).toBeInTheDocument()
    expect(screen.queryByText('Unable to load Schedule 9')).not.toBeInTheDocument()
    expectFormSuppressed()
  })

  // This used to assert `expect(ERR_MILL_CLOSED).toBe('<the same sentence typed again>')` — a
  // TAUTOLOGY. Both sides were frontend copies in the same commit, so the drift it claimed to catch
  // (a bundle reword leaving the branch keyed on dead text, and real 409s falling through to
  // "Unable to load <schedule>") would have passed. It now reads the real bundle file, the way
  // `schedule5/__tests__/validation.test.ts:334-375` already does for CAMP_MESSAGES.
  describe('bundle drift guard (review fix)', () => {
    // Deliberately does NOT trim the value: `millYearNotSelectedErrorMsg` ends in a real trailing
    // space, and that divergence from the client literal is the point of the assertions below.
    const bundle = (() => {
      const path = resolve(process.cwd(), '../backend/src/main/resources/messages.properties')
      const entries: Record<string, string> = {}
      for (const line of readFileSync(path, 'utf8').split('\n')) {
        if (line.trim() === '' || line.trim().startsWith('#')) {
          continue
        }
        const eq = line.indexOf('=')
        if (eq > 0 && !(line.slice(0, eq) in entries)) {
          entries[line.slice(0, eq)] = line.slice(eq + 1).replace(/\r$/, '')
        }
      }
      return entries
    })()

    test('ERR_MILL_CLOSED is byte-identical to millNotActiveForCurrentYearMsg', () => {
      expect(bundle['millNotActiveForCurrentYearMsg']).toBeDefined()
      // No trailing space on this key today, so byte-identity is the right assertion — and if the
      // bundle ever gains one, the trim-compare in the branch keeps working while THIS fails, which
      // is the correct place to find out.
      expect(ERR_MILL_CLOSED).toBe(bundle['millNotActiveForCurrentYearMsg'])
    })

    test('ERR_MILL_YEAR_NOT_SELECTED differs from its bundle entry by trailing whitespace ONLY', () => {
      const value = bundle['millYearNotSelectedErrorMsg']
      expect(value).toBeDefined()
      // Pins the documented divergence precisely rather than waving at it: the client literal is the
      // bundle value minus trailing whitespace, nothing else. A reword fails here; the deliberate
      // trailing space does not.
      expect(ERR_MILL_YEAR_NOT_SELECTED).toBe(value.replace(/\s+$/, ''))
      expect(value).toMatch(/\s$/)
    })
  })

  test('a SERVER-raised context error gets the paired titled state, not the generic failure', () => {
    // The bundle value carries a trailing space, so an exact `===` against the client literal missed
    // it and this fell through to "Unable to load Schedule 9" — an error framing for a context the
    // operator just has to set on the Home Page. Serve the server's exact text, space included.
    renderState({ ...base, errorDetail: `${ERR_MILL_YEAR_NOT_SELECTED} ` })

    expect(screen.getByText('Mill and Reporting Year required')).toBeInTheDocument()
    expect(screen.queryByText('Unable to load Schedule 9')).not.toBeInTheDocument()
    expectFormSuppressed()
  })

  test('closed mill still matches when the bundle text gains trailing whitespace', () => {
    renderState({ ...base, errorDetail: `${ERR_MILL_CLOSED}  ` })

    expect(screen.getByText('Mill not active for Reporting Year')).toBeInTheDocument()
    expect(screen.queryByText('Unable to load Schedule 9')).not.toBeInTheDocument()
  })

  test('track not editable for this role: the 409 detail verbatim, form suppressed', () => {
    // The role×status denial (Story 16.1) arrives as a 409 problem document and takes the generic
    // branch deliberately — the page cannot tell "read-only because Submitted" from "read-only
    // because your role cannot edit here", and 16.1 fenced out inventing a reason banner. What it
    // MUST do is show the server's sentence verbatim rather than composing one.
    renderState({ ...base, errorDetail: NOT_EDITABLE })

    expect(screen.getByText(NOT_EDITABLE)).toBeInTheDocument()
    expect(screen.getByText('Unable to load Schedule 9')).toBeInTheDocument()
    expectFormSuppressed()
  })

  test('a generic load failure shows the API detail verbatim under the page-named title', () => {
    renderState({ ...base, errorDetail: 'Schedule not found.' })

    expect(screen.getByText('Schedule not found.')).toBeInTheDocument()
    expect(screen.getByText('Unable to load Schedule 9')).toBeInTheDocument()
    expectFormSuppressed()
  })

  test('context-missing wins over an error detail (the guards are ordered, not independent)', () => {
    // A stale error from the previous context must not outrank "nothing is selected": the operator
    // has no mill, so the only actionable message is the Home Page one.
    renderState({ ...base, contextMissing: true, errorDetail: ERR_MILL_CLOSED })

    expect(screen.getByText('Mill and Reporting Year required')).toBeInTheDocument()
    expect(screen.queryByText('Mill not active for Reporting Year')).not.toBeInTheDocument()
  })

  test('loading shows the schedule-named loader and no error state', () => {
    renderState({ ...base, isLoading: true })

    // The loader carries its label as the status region's accessible name, not as visible text.
    expect(screen.getByRole('status', { name: 'Loading Schedule 9' })).toBeInTheDocument()
    expectFormSuppressed()
  })

  test('a loaded document returns null so the page renders its own work area', () => {
    expect(renderScheduleLoadState({ ...base, errorDetail: null })).toBeNull()
    expect(renderScheduleLoadState(base)).toBeNull()
  })
})
