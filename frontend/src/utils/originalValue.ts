import type { OriginalValue, OriginalValues } from '@/interfaces/OriginalValue'
import { parseDecimalInput } from '@/utils/number'

/**
 * Whether a field differs from what the licensee originally submitted, and the original to show —
 * the client-side mirror of legacy's `CoreUtil.isOriginalVal` family (`CoreUtil.java:988-1026`).
 *
 * WHY THIS LIVES ON THE CLIENT. Legacy re-rendered the indicator from the field's own `change`
 * event: `schedule1.xhtml:113` names the indicator's panel group in its `<f:ajax render>` list, so
 * the comparison ran against the operator's UNSAVED value and the icon appeared and cleared on blur.
 * The server therefore serves the submitted value whenever one is on file — not only when the stored
 * value currently differs — and the difference is decided here, against what is in the field right
 * now. `UC-CHK-005-detailed.md:40` claims no such ajax exists and that the comparison defers to
 * Save; that claim is wrong (the listener is on line 113) and `UC-CHK-010-detailed.md:39` is right.
 *
 * The three branches are legacy's, in legacy's order:
 *
 *   1. `originalValues == null`  -> the track is at Draft: nothing is flagged, for any role. This is
 *      the `isSubmit()` gate (`UserSessionMB.java:541-554`), a STATUS gate and not a permission one,
 *      so a licensee viewing a Submitted report sees the indicators too.
 *   2. a submitted value is on file -> flag when it differs from the current value. Legacy ignored
 *      `isSubmit` entirely on this branch.
 *   3. no submitted value on file -> flag when the current value is non-empty ("added since
 *      submission"). Not an edge case: roughly half the live rows carry no snapshot at all.
 */
export interface OriginalValueState {
  // Whether to render the indicator.
  readonly changed: boolean
  // The verbatim tooltip text from the API, or null when nothing is on file for the field.
  readonly tooltip: string | null
}

const NOT_CHANGED: OriginalValueState = { changed: false, tooltip: null }

const differs = (submitted: string, current: string, numeric: boolean): boolean => {
  if (!numeric) {
    return submitted.trim() !== current
  }
  const submittedNumber = parseDecimalInput(submitted)
  const currentNumber = parseDecimalInput(current)
  // Either side unparseable (an empty field, or mid-typing junk the strict parser rejects) falls back
  // to text comparison, so a half-typed entry never silently reads as "unchanged".
  if (submittedNumber === null || currentNumber === null) {
    return submitted.trim() !== current
  }
  return submittedNumber !== currentNumber
}

/**
 * Compare one field's current form value against its submitted original.
 *
 * @param originals the owning object's `originalValues` map — null/undefined at Draft
 * @param field the field's own camelCase name, as the backend keyed it
 * @param current the value in the field right now, as the form holds it (a display string)
 * @param numeric true for figures, so the comparison is by rounded value rather than by text — the
 *   mirror of `isBigDecimalOriginalVal`, which compared rounded BigDecimals so `600` and `600.0` are
 *   the same submitted value. Text fields (comments, descriptions, codes) compare with `equals`,
 *   mirroring the generic `isOriginalVal`.
 */
export const originalValueState = (
  originals: OriginalValues | null | undefined,
  field: string,
  current: string | number | null | undefined,
  numeric = true,
): OriginalValueState => {
  // Branch 1 — the Draft gate.
  if (originals == null) {
    return NOT_CHANGED
  }

  const currentText = current === null || current === undefined ? '' : String(current).trim()
  const submitted: OriginalValue | undefined = originals[field]

  // Branch 3 — nothing on file: any non-empty value is a value added since submission. The tooltip
  // is null rather than empty so a caller can tell "no original" from "an original that is blank".
  if (submitted === undefined) {
    return currentText === '' ? NOT_CHANGED : { changed: true, tooltip: null }
  }

  // Branch 2 — compare, ignoring the Draft-only `isSubmit` consideration as legacy did here.
  return { changed: differs(submitted.value, currentText, numeric), tooltip: submitted.tooltip }
}
