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
    // Nothing on file for this one. The server still sends the key, carrying the bare separator
    // legacy's converters composed for a null original.
    cost: { value: '', tooltip: 'Original Submission Value: ' },
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

    it('flags a text edit that is only whitespace — "exactly" includes the spaces', () => {
      // Legacy compared with equals (`CoreUtil.java:988-992`), so a trailing space in a comment is
      // a change and the indicator has to show it. An earlier revision trimmed the current value
      // before comparing, which made all three of these read as unchanged and cleared the indicator
      // on a real correction; the trim now applies only to the emptiness test below.
      expect(
        originalValueState(submitted, 'comments', 'as the mill reported it ', false).changed,
      ).toBe(true)
      expect(
        originalValueState(submitted, 'comments', ' as the mill reported it', false).changed,
      ).toBe(true)
      expect(
        originalValueState(submitted, 'comments', 'as the  mill reported it', false).changed,
      ).toBe(true)
    })

    it('still ignores whitespace on a NUMERIC field, which compares by value', () => {
      // The two rules coexist: text is exact, figures are parsed. Re-asserted beside the text case
      // so neither can be "made consistent" with the other by mistake.
      expect(originalValueState(submitted, 'volume', ' 60000 ').changed).toBe(false)
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
      // Legacy's own text for a null original: the converters composed
      // `"Original Submission Value: " + ""`, so it ends after the separator
      // (`ILCROriginalValueStringConverter.java:23-26`). The client never substitutes its own.
      expect(state.tooltip).toBe('Original Submission Value: ')
    })

    it('does not flag an empty value', () => {
      expect(originalValueState(submitted, 'cost', '').changed).toBe(false)
      expect(originalValueState(submitted, 'cost', null).changed).toBe(false)
      expect(originalValueState(submitted, 'cost', undefined).changed).toBe(false)
    })

    it('treats a whitespace-only value as empty — legacy trimmed for exactly this question', () => {
      // `isNullOrEmpty(currentVal, true)` (`CoreUtil.java:994`) passed its own trim flag, so spaces
      // alone were never "a value added since submission". This is the ONE place a trim survives,
      // and it is asserted here so removing it would fail rather than quietly put an indicator on
      // every field a reporter tabbed through.
      expect(originalValueState(submitted, 'cost', '   ', false).changed).toBe(false)
      expect(originalValueState(submitted, 'cost', '   ').changed).toBe(false)
    })
  })

  describe('a field with no entry at all — no original-value wiring', () => {
    it('flags nothing, however the field is filled', () => {
      // Distinct from branch 3. The server writes a key for every field it offers, including those
      // with nothing on file, so an absent key means this field has no indicator wired — and
      // legacy rendered none for those either (F9/D9). Flagging here would put an indicator on a
      // field legacy never marked, with no tooltip text to show in it.
      expect(originalValueState(submitted, 'unwired', '600').changed).toBe(false)
      expect(originalValueState(submitted, 'unwired', '600').tooltip).toBeNull()
    })
  })

  it('accepts a number as the current value, not only a form string', () => {
    // Read-only cells pass the served figure straight through rather than a typed string.
    expect(originalValueState(submitted, 'volume', 60002).changed).toBe(true)
    expect(originalValueState(submitted, 'volume', 60000).changed).toBe(false)
  })
})
