// Shared form-field helpers for the schedule pages. Kept in one place so the same trim-to-null
// pattern has a single address (Schedule 8 page + sample editors, and future pages).

/**
 * A blank (whitespace-only) form value becomes null; otherwise the original string is kept as-is
 * (untrimmed — the server owns any trimming). Use when mapping optional text/code inputs to a request.
 */
export const blankToNull = (raw: string): string | null => (raw.trim() === '' ? null : raw)
