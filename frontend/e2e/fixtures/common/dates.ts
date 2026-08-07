/**
 * Cross-domain date helpers — no domain vocabulary. Promoted out of a domain's test-data file once
 * more domains needed the same today-relative pattern. Many of these apps enforce non-future dates and
 * effective/expiry windows on reference data, so test dates MUST be today-relative — never hardcoded
 * literals that silently age into "future date" or out-of-window failures. Any screen with a date field
 * is a candidate (e.g., in SCS: scale dates, inspection dates).
 */

/**
 * Local (not UTC) `YYYY-MM-DD` for `n` days before today — avoids a timezone off-by-one near midnight
 * and keeps the value non-future. These apps typically treat such dates as plain LocalDate, so local is correct.
 */
export function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** Today-relative future date (`n` days ahead) for the re-grounded "date cannot be in the future" checks. */
export function daysAhead(n: number): string {
  return daysAgo(-n);
}
