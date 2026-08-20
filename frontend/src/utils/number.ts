// Shared numeric <-> form-string helpers for the schedule pages. Kept in one place so each page
// stops re-declaring the identical converters (Schedule 1, Schedule 2, Other Costs).

/** Display a numeric value, or an em dash when null/undefined (read-only cells). */
export const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

/**
 * Display a numeric value grouped with thousands separators, preserving its own decimals
 * (e.g. 50000 → "50,000", 1234.5 → "1,234.5"). Em dash when null/undefined. Use for quantity/count
 * read-only cells (e.g. Volume, Cost) where commas aid readability but a fixed decimal count is wrong.
 */
export const fmtNumber = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : value.toLocaleString('en-CA')

/**
 * Display a numeric value in the shared currency style: grouped with thousands separators and fixed to
 * two decimals (e.g. 1234.5 → "1,234.50", 47.857 → "47.86"). Em dash when null/undefined. No `$` sign
 * — the column header (e.g. `$/m³`) already carries the unit. Use for currency/rate read-only cells.
 */
export const fmtCurrency = (value: number | null | undefined): string =>
  value === null || value === undefined
    ? '—'
    : value.toLocaleString('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/**
 * Display a whole-dollar figure the way the legacy `mask.int.7digits` (`#,###,##0`) converter did:
 * grouped, no decimals. Em dash when null/undefined. Use for the derived INTEGER totals — the money
 * columns whose scale is 0 — where {@link fmtCurrency}'s two decimals invent a precision the column
 * does not hold. Rates (`mask.bigDecimal.7digits2decimals`) still go through {@link fmtCurrency}.
 */
export const fmtWholeCost = (value: number | null | undefined): string =>
  value === null || value === undefined
    ? '—'
    : value.toLocaleString('en-CA', { minimumFractionDigits: 0, maximumFractionDigits: 0 })

/**
 * Drop the thousands separators a grouped form string carries (e.g. "1,234.5" → "1234.5"), so it can
 * be parsed. The editable numeric fields DISPLAY grouped values (see {@link groupInput}), and legacy
 * accepted grouped typing too, so every parse of a form string must go through this first —
 * `Number('1,000')` is NaN.
 */
export const stripGroup = (raw: string): string => raw.replaceAll(',', '')

/** Insert thousands separators into a run of digits ("1234567" → "1,234,567"). */
const groupDigits = (digits: string): string => {
  if (digits.length <= 3) {
    return digits
  }
  // Split from the right in threes: the leading group holds the 1-3 digit remainder.
  const lead = digits.length % 3 || 3
  const groups = [digits.slice(0, lead)]
  for (let i = lead; i < digits.length; i += 3) {
    groups.push(digits.slice(i, i + 3))
  }
  return groups.join(',')
}

/**
 * Re-group a form input string for display, preserving exactly what the user typed apart from the
 * separators: the sign, the fractional digits (including trailing zeros and a lone trailing '.'), and
 * anything that is not a plain decimal at all — invalid text is returned UNCHANGED so a typo stays on
 * screen for the user to correct rather than being silently rewritten or blanked.
 */
export const groupInput = (raw: string): string => {
  const stripped = stripGroup(raw).trim()
  if (stripped === '') {
    return ''
  }
  const match = /^(-?)(\d*)(\.\d*)?$/.exec(stripped)
  if (!match) {
    return raw
  }
  const [, sign, digits, fraction = ''] = match
  return `${sign}${groupDigits(digits)}${fraction}`
}

/** Parse a form input string (grouped or plain) to a number, or null when blank / not a number. */
export const toNum = (raw: string): number | null => {
  const trimmed = stripGroup(raw).trim()
  if (trimmed === '') {
    return null
  }
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

/** Render a numeric value as a form input string, blank when null/undefined. */
export const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)

/**
 * Parse a form string using the legacy JSF converter's accepted syntax: an optional leading '-',
 * digits with optional comma grouping, and an optional '.' fractional part — the same format the
 * pages display. Deliberately STRICTER than legacy, whose `DecimalFormat.parse` had no
 * consumed-length check and silently mangled junk-suffixed or mis-grouped input ('12,34' -> 1234,
 * '1000abc' -> 1000). Native {@link toNum} diverges the other way and must not be used where legacy
 * fidelity matters: it rejects grouped input legacy accepted ('1,000' -> NaN) and accepts JS-only
 * forms legacy never allowed ('1e2', '0x10', 'Infinity'). Returns null when blank or not a valid
 * decimal in this strict format.
 *
 * The single source: Schedules 6 and 11 import this (their identical local copies were removed in
 * the Story 29.8 consolidation).
 */
const DECIMAL_INPUT = /^-?(\d{1,3}(,\d{3})+|\d+)(\.\d+)?$/
export const parseDecimalInput = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '' || !DECIMAL_INPUT.test(trimmed)) {
    return null
  }
  return Number(stripGroup(trimmed))
}

/**
 * Round a whole-dollar cost half-away-from-zero, the way Oracle rounds on insert. Legacy accepted
 * fractional cost entry and the database rounded it; the modern Integer wire would instead TRUNCATE
 * at deserialization, so rounding before send keeps the stored value faithful to legacy. The backend
 * independently rejects any fractional cost.
 */
export const roundCost = (value: number | null): number | null =>
  value === null ? null : Math.sign(value) * Math.round(Math.abs(value))

/**
 * Render a numeric value as a GROUPED form input string ("50000" → "50,000"), blank when
 * null/undefined. Use to seed editable numeric fields and to fill read-only preview inputs, so a
 * field reads the same as the plain-text cells beside it.
 */
export const numStrGroup = (value: number | null | undefined): string => groupInput(numStr(value))

/**
 * Render a numeric value as a GROUPED form input string carrying EXACTLY `fractionDigits` decimals
 * ("12" → "12.0" at 1; "1234.5" → "1,235" at 0). Blank when null/undefined.
 *
 * This is the modern equivalent of a legacy `f:convertNumber pattern="..."` mask, which every ILCR
 * numeric input carried: the mask is what decided how many decimals a stored value DISPLAYED with,
 * independently of how the database happened to return it. Seeding a masked field with
 * {@link numStrGroup} instead drops that decision — a `NUMBER(7,1)` column returning `12.0` renders
 * as `12` where legacy showed `12.0`, and the reporter reads a different value than the legacy screen
 * showed them.
 */
export const numStrFixed = (value: number | null | undefined, fractionDigits: number): string =>
  value === null || value === undefined
    ? ''
    : value.toLocaleString('en-CA', {
        minimumFractionDigits: fractionDigits,
        maximumFractionDigits: fractionDigits,
      })

/**
 * Re-apply a fixed-decimal grouped mask to a form input string, the way a legacy `f:convertNumber`
 * re-rendered its field on every `change` event: parse what was typed, then format it back through
 * the mask ("1200" → "1,200"; "12.55" → "12.6" at 1 decimal; "1.5" → "2" at 0).
 *
 * Text that is not a valid decimal is returned UNCHANGED so a typo stays on screen for the user to
 * correct rather than being silently rewritten or blanked (the {@link groupInput} contract).
 */
export const groupFixedInput = (raw: string, fractionDigits: number): string => {
  if (raw.trim() === '') {
    return ''
  }
  const value = parseDecimalInput(raw)
  return value === null ? raw : numStrFixed(value, fractionDigits)
}
