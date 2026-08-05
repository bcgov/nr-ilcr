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

/** Parse a form input string to a number, or null when blank / not a number. */
export const toNum = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return null
  }
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

/** Render a numeric value as a form input string, blank when null/undefined. */
export const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)
