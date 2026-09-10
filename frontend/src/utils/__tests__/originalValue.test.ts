import { describe, expect, it } from 'vitest'
import type { OriginalValues } from '@/interfaces/OriginalValue'
import { originalValueState } from '@/utils/originalValue'

/**
 * The client half of the original-value rule (Story 16.2) — the mirror of legacy's
 * `CoreUtil.isOriginalVal` family. Its three branches decide every indicator on every schedule page,
 * so they are asserted here once rather than re-proved per page.
 */
describe('originalValueState', () => {
  const submitted: OriginalValues = {
    volume: { value: '60000', tooltip: 'Original Submission Value: 60,000' },
    comments: {
      value: 'as the mill reported it',
      tooltip: 'Original Submission Value: as the mill reported it',
    },
  }

  describe('branch 1 — the Draft gate', () => {
    it.each([null, undefined])('flags nothing when the map is %s', (originals) => {
      // At Draft the server sends no map at all, and that absence — not a per-field decision — is
      // what suppresses every indicator on the page, for every role.
      expect(originalValueState(originals, 'volume', '99999')).toEqual({
        changed: false,
        tooltip: null,
      })
    })
  })

  describe('branch 2 — a submitted value is on file', () => {
    it('flags a value that differs, and carries the API tooltip verbatim', () => {
      expect(originalValueState(submitted, 'volume', '60002')).toEqual({
        changed: true,
        tooltip: 'Original Submission Value: 60,000',
      })
    })

    it('does not flag a value equal to the original — the no-indicator-when-unchanged case', () => {
      // CHK-005 S09: the Auditor saves without changing anything and no indicator appears.
      expect(originalValueState(submitted, 'volume', '60000').changed).toBe(false)
    })

    it('clears once a correction is edited back to the original — CHK-010 S05', () => {
      const corrected = originalValueState(submitted, 'volume', '60002')
      const reverted = originalValueState(submitted, 'volume', '60000')

      expect(corrected.changed).toBe(true)
      expect(reverted.changed).toBe(false)
    })

    it('compares numbers by value, not by text', () => {
      // Legacy compared rounded BigDecimals (isBigDecimalOriginalVal), so none of these is a change.
      // Comparing display strings would have flagged all three and put an indicator on every cell a
      // reporter merely retyped.
      expect(originalValueState(submitted, 'volume', '60000.0').changed).toBe(false)
      expect(originalValueState(submitted, 'volume', '60,000').changed).toBe(false)
      expect(originalValueState(submitted, 'volume', ' 60000 ').changed).toBe(false)
    })

    it('compares parsed numbers EXACTLY — no epsilon tolerance', () => {
      // Deliberate, and asserted so it is not "tidied" into an approximate compare later. Both
      // sides are parsed straight from decimal strings by `parseDecimalInput` (a bare `Number()`
      // with the grouping stripped) and no arithmetic happens in between, so two strings denoting
      // the same decimal always yield bit-identical doubles — there is no drift for a tolerance to
      // absorb. A tolerance would instead LOSE real differences: these two cost figures are 1 cent
      // apart, a genuine ministry correction, and any epsilon wide enough to matter hides it.
      const cents: OriginalValues = {
        cost: { value: '0.1', tooltip: 'Original Submission Value: 0.10' },
      }
      expect(originalValueState(cents, 'cost', '0.10').changed).toBe(false)
      expect(originalValueState(cents, 'cost', '0.11').changed).toBe(true)
      // The classic 0.1 + 0.2 case cannot arise here, because nothing on this path adds: the
      // string "0.30000000000000004" is simply a different value from "0.3", and is flagged.
      const third: OriginalValues = {
        cost: { value: '0.3', tooltip: 'Original Submission Value: 0.30' },
      }
      expect(originalValueState(third, 'cost', '0.3').changed).toBe(false)
      expect(originalValueState(third, 'cost', String(0.1 + 0.2)).changed).toBe(true)
    })

    it('compares text exactly when told the field is not numeric', () => {
      expect(
        originalValueState(submitted, 'comments', 'as the mill reported it', false).changed,
      ).toBe(false)
      expect(
        originalValueState(submitted, 'comments', 'as the ministry corrected it', false).changed,
      ).toBe(true)
    })

    it('flags a cleared field — the licensee submitted a value and it has been emptied', () => {
      expect(originalValueState(submitted, 'volume', '').changed).toBe(true)
    })
  })

  describe('branch 3 — nothing on file for the field', () => {
    it('flags any non-empty value as added since submission', () => {
      // Roughly half the live rows are in this state, so this is the common path and not an edge
      // case. Legacy's isOriginalVal flagged exactly this once the track had left Draft.
      const state = originalValueState(submitted, 'cost', '600')

      expect(state.changed).toBe(true)
      // No tooltip: there is no submitted value to show. The indicator says so in its own words.
      expect(state.tooltip).toBeNull()
    })

    it('does not flag an empty value', () => {
      expect(originalValueState(submitted, 'cost', '').changed).toBe(false)
      expect(originalValueState(submitted, 'cost', null).changed).toBe(false)
      expect(originalValueState(submitted, 'cost', undefined).changed).toBe(false)
    })
  })

  it('accepts a number as the current value, not only a form string', () => {
    // Read-only cells pass the served figure straight through rather than a typed string.
    expect(originalValueState(submitted, 'volume', 60002).changed).toBe(true)
    expect(originalValueState(submitted, 'volume', 60000).changed).toBe(false)
  })
})
