// Shared advisory field helpers for the Schedule 4 validators (the category grid in validation.ts and
// the sub-page add-row in subPageDefs.ts). Both do the same blank-guarded numeric range check; keeping
// one definition avoids the two drifting apart.

/** True when a raw form value is absent or whitespace-only. */
export const isBlank = (raw: string | undefined): boolean => raw === undefined || raw.trim() === ''

/**
 * Returns `message` when `raw` is a non-blank value that is NaN or outside `[range.min, range.max]`;
 * `undefined` when the value is blank (nothing to validate) or in range.
 */
export const rangeError = (
  raw: string,
  range: { min: number; max: number },
  message: string,
): string | undefined => {
  if (isBlank(raw)) return undefined
  const value = Number(raw)
  if (Number.isNaN(value) || value < range.min || value > range.max) return message
  return undefined
}
