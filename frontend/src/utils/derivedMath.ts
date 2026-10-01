import { parseDecimalInput, toNum } from '@/utils/number'

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
 * null-propagation rules. Reproducing them exactly is what keeps a figure from visibly JUMPING when
 * the Save echo lands — a worse defect than the one being fixed.
 *
 * ## Everything rounds in INTEGER space (code review 2026-08-21)
 *
 * The first implementation rounded in double space — scale by `10ⁿ`, or expand with `toFixed` and
 * carry on the dropped digit — and review found four ways that diverges from `BigDecimal`:
 *
 * - `toFixed` **pre-rounds**, so a near-tie becomes a false tie: `0.4999999951` at scale 0 came back
 *   as `1` where HALF_UP gives `0`. Reachable at ordinary magnitudes — cost `41,956,411` over volume
 *   `40` produced `1,048,910.27` against the server's `1,048,910.28`, a one-cent error in a rate cell.
 * - `toFixed` switches to **exponential notation** at `|x| >= 1e21`, which positional string parsing
 *   silently mis-reads (`halfUp(2.5e21, 4)` returned `0.25`).
 * - Materialising the scaled digits as a `Number` **exceeds `MAX_SAFE_INTEGER`** on the scale-10 path
 *   for any quotient over ~1e6, so the "exact integer arithmetic" was not exact.
 * - Multiplying by `10ⁿ` loses exact halves outright: `3075 / 5000` is `0.615`, which must round UP to
 *   `0.62`, but the nearest double is `0.6149999999999999911…`.
 *
 * So division and rounding now go through an exact decimal core built on `BigInt` ({@link Dec}), which
 * reproduces `BigDecimal.divide(divisor, scale, HALF_UP)` at every magnitude. A JS number's
 * `toString()` is its shortest round-trip decimal — the same literal that went over the wire and that
 * Jackson handed to `BigDecimal` — so decomposing it that way is faithful to the server's operand, not
 * an approximation of it.
 *
 * Sources: `Schedule{1,2,3,4}Service.perUnit` / `toWholeDollars`, `Schedule3Constants.scalingPop`, and
 * the legacy `CoreUtil` null-propagating `bigDecimalAddition` / `bigDecimalSubtraction`.
 */

// ---------------------------------------------------------------------------------------------------
// Exact decimal core. A `Dec` is the exact rational `units / 10^scale`, so every operation below is
// integer arithmetic — no double ever holds an intermediate result.
// ---------------------------------------------------------------------------------------------------

interface Dec {
  readonly units: bigint
  readonly scale: number
}

const DECIMAL_LITERAL = /^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/

/**
 * Decompose a finite number into its exact decimal value. Uses `toString()`, whose shortest
 * round-trip form is the literal the user entered and the wire carried (`120.5`, not
 * `120.5000000000000284…`), so the mirror divides the same operand the server does. Handles the
 * exponential forms `toString()` produces outside `[1e-7, 1e21)`.
 */
const toDec = (value: number): Dec | null => {
  if (!Number.isFinite(value)) {
    return null
  }
  const match = DECIMAL_LITERAL.exec(value.toString())
  if (!match) {
    return null
  }
  const [, sign, whole, fraction = '', exponent] = match
  const digits = whole + fraction
  // Scale from the fractional digits, less the exponent (a positive exponent shifts the point right).
  const scale = fraction.length - (exponent ? Number(exponent) : 0)
  const units = BigInt(sign + digits)
  // A negative scale means the value has trailing zeros beyond the digits we hold; normalise it away
  // so `scale` is never negative and `10n ** BigInt(scale)` stays valid.
  return scale < 0 ? { units: units * 10n ** BigInt(-scale), scale: 0 } : { units, scale }
}

const TEN = 10n

/** Rescale `d` to exactly `scale` decimal places, rounding half-away-from-zero (Java HALF_UP). */
const rescale = (d: Dec, scale: number): Dec => {
  if (scale === d.scale) {
    return d
  }
  if (scale > d.scale) {
    return { units: d.units * TEN ** BigInt(scale - d.scale), scale }
  }
  const divisor = TEN ** BigInt(d.scale - scale)
  const negative = d.units < 0n
  const magnitude = negative ? -d.units : d.units
  // HALF_UP on the magnitude: carry when twice the remainder reaches the divisor.
  const quotient = magnitude / divisor
  const remainder = magnitude % divisor
  const carried = remainder * 2n >= divisor ? quotient + 1n : quotient
  return { units: negative ? -carried : carried, scale }
}

/** `a / b` rounded HALF_UP at `scale` — the exact equivalent of `BigDecimal.divide(b, scale, HALF_UP)`. */
const divide = (a: Dec, b: Dec, scale: number): Dec | null => {
  if (b.units === 0n) {
    return null
  }
  // a/b = (a.units * 10^b.scale) / (b.units * 10^a.scale); shift the numerator by `scale` first so the
  // single integer division below carries the rounding decision.
  const numerator = a.units * TEN ** BigInt(b.scale + scale)
  const denominator = b.units * TEN ** BigInt(a.scale)
  const negative = numerator < 0n !== denominator < 0n
  const absNumerator = numerator < 0n ? -numerator : numerator
  const absDenominator = denominator < 0n ? -denominator : denominator
  const quotient = absNumerator / absDenominator
  const remainder = absNumerator % absDenominator
  const carried = remainder * 2n >= absDenominator ? quotient + 1n : quotient
  return { units: negative ? -carried : carried, scale }
}

/** Exact product; scales add, as in `BigDecimal.multiply`. */
const multiply = (a: Dec, b: Dec): Dec => ({ units: a.units * b.units, scale: a.scale + b.scale })

/**
 * Back to a JS number for rendering. The result carries at most the scale it was rounded to, so it is
 * representable for every figure these pages display. Never returns `-0`: it renders as "-0" through
 * `toLocaleString`, which would put a phantom minus on screen.
 */
const decToNumber = (d: Dec): number => {
  if (d.units === 0n) {
    return 0
  }
  // Strip redundant scale first. Converting `10^23 / 10^2` term-by-term loses precision that the
  // equivalent `10^21 / 1` keeps, because 10^23 has no exact double while 10^21 does — so a figure
  // rounded at scale 2 could come back as 999999999999999900000 instead of 1e21.
  let units = d.units
  let scale = d.scale
  while (scale > 0 && units % TEN === 0n) {
    units /= TEN
    scale -= 1
  }
  return scale === 0 ? Number(units) : Number(units) / 10 ** scale
}

// ---------------------------------------------------------------------------------------------------
// Public helpers.
// ---------------------------------------------------------------------------------------------------

/**
 * An operand of a derived figure.
 *
 * `undefined` is admitted DELIBERATELY, and every guard below tests it with a LOOSE `== null` so it
 * behaves exactly like `null`. The reason is the wire: the API runs
 * `default-property-inclusion: non_null`, so a figure the server has nothing for is **omitted**, not
 * sent as null — an unsaved Schedule 2 serves `"purchasedWoodOverhead": {}` rather than three nulls,
 * and the carried value reaches these helpers as `undefined`.
 *
 * A strict `=== null` therefore lets `undefined` walk straight into the arithmetic, where
 * `12345 + undefined` is `NaN` and the cell renders the user a literal "NaN". That regression shipped
 * to main on 2026-08-24 and was caught by the Schedule 2 `blank-fields` e2e scenario, which expected
 * `8,000` and got `NaN`; the mirror runs on any editable document, and an unsaved schedule is
 * editable. Same defect family as #292's Delete gate — a strict null test against a field the wire
 * omits.
 *
 * Do not "tidy" any `== null` in this file to `===`.
 */
export type Operand = number | null | undefined

/**
 * Round half-AWAY-FROM-ZERO at `decimals` places — Java's `RoundingMode.HALF_UP`.
 *
 * Deliberately not `Math.round` or `toFixed`: both round half-UP toward positive infinity and so
 * disagree with the backend on negatives (`Math.round(-1.5)` is `-1`, HALF_UP gives `-2`). A derived
 * figure is often a difference — `netPurchased`, `totalCosts`, the `crown` column — so negatives are
 * reachable from ordinary entry. Non-finite input passes through unchanged; callers guard it at the
 * entry boundary ({@link enteredNum}) so it can never reach a cell.
 */
export const halfUp = (value: number, decimals = 0): number => {
  const dec = toDec(value)
  if (dec === null) {
    return value
  }
  return decToNumber(rescale(dec, decimals))
}

/**
 * `$/m³` for **Schedules 2 and 4**: cost ÷ volume at scale-4 HALF_UP — the scale frozen into those two
 * wire contracts, verified by
 * `Schedule2ServiceTest.perUnit_roundsToScale4HalfUp_onNonTerminatingQuotient` (200000 ÷ 30000 →
 * `6.6667`, not `6.6666`). Null when either operand is null or non-finite, or the volume is zero.
 *
 * ⚠ Schedules 1 and 3 round DIFFERENTLY — use {@link perUnitLegacy} there. The two rules disagree at a
 * two-decimal boundary, which is exactly where the display sits.
 *
 * The backend's trailing `stripTrailingZeros` / minimum-scale-1 step is a JSON serialization concern
 * only — a JS number carries no scale — so it has no counterpart here.
 */
export const perUnitOf = (cost: Operand, volume: Operand): number | null => {
  if (cost == null || volume == null) {
    return null
  }
  const a = toDec(cost)
  const b = toDec(volume)
  if (a === null || b === null) {
    return null
  }
  const quotient = divide(a, b, 4)
  return quotient === null ? null : decToNumber(quotient)
}

/**
 * `$/m³` for **Schedules 1 and 3**: the legacy `CoreUtil.bigDecimalDivision` — divide at scale 10
 * HALF_UP, THEN round to scale 2 HALF_UP (`Schedule1Service.perUnit`, `Schedule3Service.perUnit`).
 * Null when the cost is null or non-finite, or the volume is null/zero/non-finite.
 *
 * The two-step rounding is reproduced faithfully, and the scale-10 intermediate is kept as an exact
 * `Dec` rather than a double: at 10 decimal places a 7-digit quotient needs 17 significant digits,
 * past what a double holds, which is how the previous implementation lost a cent.
 *
 * Schedule 1's own javadoc records that scale 2 "fixes the earlier Schedule-1 divergence to 4
 * decimals", so this — not {@link perUnitOf} — is the legacy-faithful rule; Schedules 2 and 4 are the
 * ones that diverge. Do not unify them without changing the backend first.
 */
export const perUnitLegacy = (cost: Operand, volume: Operand): number | null => {
  if (cost == null || volume == null) {
    return null
  }
  const a = toDec(cost)
  const b = toDec(volume)
  if (a === null || b === null) {
    return null
  }
  const atScale10 = divide(a, b, 10)
  return atScale10 === null ? null : decToNumber(rescale(atScale10, 2))
}

/**
 * The Schedule 3 Scaling (33) PO&P, per `Schedule3Constants.scalingPop`: round-to-whole-dollars(
 * (popTimberVolume ÷ overheadVolume) × scalingHarvest), with the ratio taken at **scale 15 HALF_UP
 * before the multiply**. Null when the harvest or either volume is absent/non-finite, or the overhead
 * volume is zero.
 *
 * That scale-15 step is not decorative and must not be skipped: it decides which side of the `.5`
 * boundary the product lands on. With PO&P volume 5,000,000, Crown 1,000,000 and a Scaling harvest of
 * 999,999 the ratio rounds to `0.833333333333333`, the product is `833332.4999999997…` and the figure
 * is **833,332** — whereas the raw quotient gives exactly `833332.5` and rounds to **833,333**. Omitting
 * it shipped that $1 error, which then cascaded into nine other cells (code review 2026-08-21).
 */
export const scalingPopOf = (
  scalingHarvest: Operand,
  popTimberVolume: Operand,
  overheadVolume: Operand,
): number | null => {
  if (scalingHarvest == null || popTimberVolume == null || overheadVolume == null) {
    return null
  }
  const harvest = toDec(scalingHarvest)
  const pop = toDec(popTimberVolume)
  const overhead = toDec(overheadVolume)
  if (harvest === null || pop === null || overhead === null) {
    return null
  }
  const ratio = divide(pop, overhead, 15)
  if (ratio === null) {
    return null
  }
  return decToNumber(rescale(multiply(ratio, harvest), 0))
}

/**
 * Round a derived cost to whole dollars (scale-0 HALF_UP), mirroring the services' `toWholeDollars`.
 * Legacy stores COST as an integer, so every derived money figure lands on a whole dollar — which also
 * keeps a fractional ENTRY from putting cents in a whole-dollar column, where the server would reject
 * the save outright (`accept-float-as-int: false`).
 *
 * Distinct from {@link import('./number').roundCost}, which rounds an entered cost before it goes on
 * the wire. Same arithmetic, different purpose: this one never leaves the screen.
 */
export const wholeDollars = (cost: Operand): number | null =>
  cost == null ? null : halfUp(cost, 0)

/**
 * Parse a committed form string for the mirror: {@link toNum} plus a finiteness guard.
 *
 * `toNum` rejects only `NaN`, so `'Infinity'` and `'1e999'` parse to non-finite values that used to
 * flow to the screen as `∞` through `fmtNumber` (code review 2026-08-21). A non-finite entry is
 * treated as absent, so the cell reads `—` instead of a nonsense financial figure.
 */
export const enteredNum = (raw: string): number | null => {
  const value = toNum(raw)
  return value !== null && Number.isFinite(value) ? value : null
}

/**
 * Parse a committed form string the way the WIRE parses it: `parseDecimalInput` plus a finiteness
 * guard. Use this on any page whose `buildRequest`/`buildBody` uses `parseDecimalInput` — Schedules 5,
 * 5's sub-pages, 6, 7A and the Schedule 3 sub-pages.
 *
 * The distinction is not cosmetic (code review 2026-08-21). `toNum` is documented as accepting
 * "JS-only forms legacy never allowed" — `'1e3'` → 1000, `'.5'` → 0.5, `'0x10'` → 16, mis-grouped
 * `'12,34'` → 1234 — every one of which the strict parser rejects as null. A mirror built on the lax
 * parser therefore displays figures the Save can never persist, while validation blocks the write and
 * the reporter has no way to reconcile the two. Schedules 1-4 use `toNum` on BOTH sides, so
 * {@link enteredNum} is correct there and only there.
 */
export const committedNum = (raw: string): number | null => {
  const value = parseDecimalInput(raw)
  return value !== null && Number.isFinite(value) ? value : null
}

/**
 * True when a committed string is non-blank yet unusable as a number — unparseable (`'-'`, `'.'`,
 * `'1.2.3'`) or non-finite. Such a value must not advance the mirror's baseline: committing it would
 * silently drop that line out of every total, where legacy's failed round-trip left the last valid
 * figures on screen (code review 2026-08-21).
 */
export const isUnusableEntry = (raw: string): boolean =>
  raw.trim() !== '' && enteredNum(raw) === null

/**
 * The strict counterpart of {@link isUnusableEntry}, for the pages whose wire uses
 * `parseDecimalInput`: true when a non-blank entry is not a value the Save could carry. Catches the
 * lax-parser forms (`'1e3'`, `'0x10'`, `'12,34'`) that {@link isUnusableEntry} lets through.
 */
export const isUnusableStrictEntry = (raw: string): boolean =>
  raw.trim() !== '' && committedNum(raw) === null

/**
 * Legacy `CoreUtil.bigDecimalAddition`: null ONLY when both operands are null, otherwise the non-null
 * operand(s). A total with no contributing value stays null — never `0` — so the cell renders `—`
 * rather than a fabricated zero.
 */
export const addN = (a: Operand, b: Operand): number | null => {
  if (a == null && b == null) {
    return null
  }
  if (a == null) {
    return b ?? null
  }
  if (b == null) {
    return a
  }
  return a + b
}

/**
 * Legacy `CoreUtil.bigDecimalSubtraction`: the minuend when the subtrahend is null; null when the
 * minuend is null. Note the asymmetry with {@link addN} — a missing subtrahend is "nothing to take
 * away" (keep the minuend), but a missing minuend leaves nothing to subtract FROM (null).
 */
export const subN = (a: Operand, b: Operand): number | null => {
  if (a == null) {
    return null
  }
  return b == null ? a : a - b
}

/**
 * Null-tolerant sum: null ONLY when every operand is null, otherwise the sum of the non-null ones.
 * The n-ary form of {@link addN} — legacy `CoreUtil.sumBigDecimalCosts` /
 * `Schedule5Service.sumCosts` / `Schedule7aService.sum`. A total with no contributing value is null,
 * never `0`, so the cell renders `—`.
 *
 * Distinct from {@link sumAsZero}, which returns `0` for an all-null input. Pick per figure from the
 * service: Schedule 1's `subtotalCompanyLoggingCost` and Schedule 3's column subtotals seed at zero,
 * whereas Schedule 5's camp totals and Schedule 7A's bridge totals stay blank.
 */
export const sumN = (...values: readonly Operand[]): number | null => {
  let total = 0
  let any = false
  for (const value of values) {
    if (value != null) {
      total += value
      any = true
    }
  }
  return any ? total : null
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
export const sumAsZero = (...values: readonly Operand[]): number =>
  values.reduce<number>((total, value) => total + (value ?? 0), 0)
