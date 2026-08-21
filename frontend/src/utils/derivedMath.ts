/**
 * Arithmetic primitives for the DISPLAY-ONLY derived-figure mirrors (defect #291, bcgov/nr-ilcr#291).
 *
 * Legacy recalculated every dependent read-only figure — subtotals, net, totals, `$/m³` — when focus
 * left an entry field, before Save. The modernized pages compute those figures server-side only, so
 * nothing moved until the Save echo returned. Spine AD-5 was amended 2026-08-20 to permit a
 * display-only mirror of the same arithmetic: the server remains the sole authority for every stored
 * and returned figure, a mirrored value is NEVER sent on a write, and the server echo supersedes the
 * mirror on every Save. Where the two disagree, the server is right and the mirror is the defect.
 *
 * These helpers exist because the backend computes in `BigDecimal` with specific rounding and
 * null-propagation rules and the frontend has no decimal library. Reproducing them exactly is what
 * keeps a figure from visibly JUMPING when the Save echo lands — a worse defect than the one being
 * fixed. Each rule below is transcribed from the backend helper it mirrors; do not "simplify" one
 * without reading its counterpart.
 *
 * Sources: `Schedule{1,2,3,4}Service.perUnit` / `toWholeDollars`, and the legacy `CoreUtil`
 * null-propagating `bigDecimalAddition` / `bigDecimalSubtraction` those services transcribe.
 */

/**
 * Round half-AWAY-FROM-ZERO at `decimals` places — Java's `RoundingMode.HALF_UP`, which is what every
 * derived figure in the backend uses.
 *
 * Deliberately NOT `Math.round` or `toFixed`: both round half-UP toward positive infinity, so they
 * disagree with the backend on negatives (`Math.round(-1.5)` is `-1`, HALF_UP gives `-2`). Costs are
 * normally positive, but a derived figure is a difference — `netPurchased`, `totalCosts` and the
 * `crown` column all subtract — so negatives are reachable from ordinary entry.
 */
export const halfUp = (value: number, decimals = 0): number => {
  if (value === 0 || !Number.isFinite(value)) {
    return value === 0 ? 0 : value
  }
  const factor = 10 ** decimals
  const rounded = Math.round(Math.abs(value) * factor) / factor
  // A small negative that rounds down to zero must come back as +0: `-0` renders as "-0" through
  // `toLocaleString`, so re-applying the sign unconditionally would put a phantom minus on screen.
  if (rounded === 0) {
    return 0
  }
  return value < 0 ? -rounded : rounded
}

/**
 * `$/m³` for **Schedules 2 and 4**: cost ÷ volume, null when either operand is null OR the volume is
 * zero (no divide-by-zero), then scale-4 HALF_UP — the scale frozen into those two wire contracts,
 * verified by `Schedule2ServiceTest.perUnit_roundsToScale4HalfUp_onNonTerminatingQuotient`
 * (200000 ÷ 30000 → `6.6667`, not `6.6666`).
 *
 * ⚠ Schedules 1 and 3 round DIFFERENTLY — use {@link perUnitLegacy} there. The two rules are not
 * interchangeable: they disagree at a two-decimal boundary, which is exactly where the display sits.
 *
 * Rounding at scale 4 matters even though the cells display two decimals: rounding the raw quotient
 * straight to two places disagrees with the server near a boundary (a true quotient of `6.66499…`
 * gives `6.6650` → "6.67" via the server, but "6.66" if the scale-4 step is skipped). The backend's
 * trailing `stripTrailingZeros` / minimum-scale-1 step is a JSON serialization concern only — a JS
 * number carries no scale — so it has no counterpart here.
 */
export const perUnitOf = (cost: number | null, volume: number | null): number | null => {
  if (cost === null || volume === null || volume === 0) {
    return null
  }
  return halfUp(cost / volume, 4)
}

/**
 * `$/m³` for **Schedules 1 and 3**: the legacy `CoreUtil.bigDecimalDivision` — divide at scale 10
 * HALF_UP, THEN round to scale 2 HALF_UP (`Schedule1Service.perUnit`, `Schedule3Service.perUnit`).
 * Null when the cost is null or the volume is null/zero.
 *
 * The two-step rounding is transcribed rather than collapsed to a single scale-2 round because that is
 * what the server does, and double rounding is not the same operation: a quotient of `6.66499999995`
 * rounds to `6.665` at scale 10 and then UP to `6.67`, where rounding straight to two places gives
 * `6.66`. Reachable only with contrived data, but the point of a mirror is to be the same function.
 *
 * Schedule 1's own javadoc records that scale 2 "fixes the earlier Schedule-1 divergence to 4
 * decimals", so this — not {@link perUnitOf} — is the legacy-faithful rule; Schedules 2 and 4 are the
 * ones that diverge. Do not unify them without changing the backend first.
 */
export const perUnitLegacy = (cost: number | null, volume: number | null): number | null => {
  if (cost === null || volume === null || volume === 0) {
    return null
  }
  return halfUp(halfUp(cost / volume, 10), 2)
}

/**
 * Round a derived cost to whole dollars (scale-0 HALF_UP), mirroring the services' `toWholeDollars`.
 * Legacy stores COST as an integer, so every derived money figure lands on a whole dollar.
 *
 * Distinct from {@link import('./number').roundCost}, which rounds an ENTERED cost before it goes on
 * the wire. Same arithmetic, different purpose: this one never leaves the screen.
 */
export const wholeDollars = (cost: number | null): number | null =>
  cost === null ? null : halfUp(cost, 0)

/**
 * Legacy `CoreUtil.bigDecimalAddition`: null ONLY when both operands are null, otherwise the non-null
 * operand(s). A total with no contributing value stays null — never `0` — so the cell renders `—`
 * rather than a fabricated zero.
 */
export const addN = (a: number | null, b: number | null): number | null => {
  if (a === null && b === null) {
    return null
  }
  if (a === null) {
    return b
  }
  if (b === null) {
    return a
  }
  return a + b
}

/**
 * Legacy `CoreUtil.bigDecimalSubtraction`: the minuend when the subtrahend is null; null when the
 * minuend is null. Note the asymmetry with {@link addN} — a missing subtrahend is "nothing to take
 * away" (keep the minuend), but a missing minuend leaves nothing to subtract FROM (null).
 */
export const subN = (a: number | null, b: number | null): number | null => {
  if (a === null) {
    return null
  }
  return b === null ? a : a - b
}

/**
 * Sum treating null as 0, returning 0 (not null) for an all-null input. The counterpart to
 * {@link addN}, for the specific figures the backend seeds at zero because legacy never showed them
 * blank — Schedule 1's `subtotalCompanyLoggingCost` and Schedule 3's column subtotals.
 *
 * The null-as-0 vs null-propagating choice is PER FIGURE, not per schedule: Schedule 1's
 * `totalSilvicultureCost` propagates null in the very same document. Transcribe each figure from its
 * service rather than picking one rule for a page.
 */
export const sumAsZero = (...values: readonly (number | null)[]): number =>
  values.reduce<number>((total, value) => total + (value ?? 0), 0)
