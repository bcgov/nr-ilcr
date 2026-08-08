/**
 * Cross-domain URL-matching helpers. No domain vocabulary.
 */

/**
 * Escape a string for safe literal use inside a `RegExp`. Scale site ids can be alphanumeric
 * (e.g. "28E"), and a caller-supplied value interpolated into a `new RegExp(...)` would otherwise let a
 * metacharacter change the pattern. Numeric ids are unaffected; this just makes the match robust.
 */
export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
