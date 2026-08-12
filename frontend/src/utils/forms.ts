// Shared form-field helpers for the schedule pages. Kept in one place so the same trim-to-null
// pattern has a single address (Schedule 8 page + sample editors, and future pages).

/**
 * A blank (whitespace-only) form value becomes null; otherwise the original string is kept as-is
 * (untrimmed — the server owns any trimming). Use when mapping optional text/code inputs to a request.
 */
export const blankToNull = (raw: string): string | null => (raw.trim() === '' ? null : raw)

/**
 * A string's length in UTF-8 BYTES — the unit the Oracle columns are actually sized in (AL32UTF8),
 * and the one the backend's `@MaxByteLength` measures. Characters alone understate any accented or
 * CJK text, which is how a comment can pass a 3,500-character gate and still overflow a
 * VARCHAR2(4000 BYTE) column.
 */
export const utf8Length = (value: string): number => new TextEncoder().encode(value).length

/**
 * Drop one field's message from an error map, returning the map UNCHANGED when it holds no message
 * for that field — so an edit to a field that was never rejected cannot trigger a re-render.
 *
 * Called as the user types: a corrected value stops showing its stale rejection immediately, while
 * the rest of the errors stand until the next submit re-evaluates them.
 */
export const clearFieldError = <K extends string>(
  errors: Partial<Record<K, string>>,
  key: K,
): Partial<Record<K, string>> => {
  if (!(key in errors)) {
    return errors
  }
  const next = { ...errors }
  delete next[key]
  return next
}
