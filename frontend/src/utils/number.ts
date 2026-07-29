// Shared numeric <-> form-string helpers for the schedule pages. Kept in one place so each page
// stops re-declaring the identical converters (Schedule 1, Schedule 2, Other Costs).

/** Display a numeric value, or an em dash when null/undefined (read-only cells). */
export const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

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
