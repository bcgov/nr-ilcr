// Mirrors the backend CampRequest/CategoryEntry write DTOs. Entered fields only — no
// `costPerVolume`, no totals, no counts: derived values are never client-supplied (AD-5/AD-12).
// Sending them is ignored rather than rejected, but they have no business being in the type.
//
// READ/WRITE ASYMMETRY, deliberate: the served `CategoryAmount.cost` is a `Long`, the written
// `CategoryEntry.cost` is an `Integer`. Both are `number` in TypeScript, but costs are WHOLE
// DOLLARS on the way in and a fractional value is REJECTED, not truncated — round with `roundCost`
// before sending, and range-check the ROUNDED value.

/**
 * One entered volume/cost pair.
 *
 * Both halves are optional and `null` means CLEARED, not invalid — the legacy validators
 * short-circuited on a non-numeric value, so a blank input was silently accepted and stored NULL.
 * `null ≠ 0` in both directions.
 *
 * Which half applies depends on the category: `otherCampExpenses` and `otherAccessExpenses` are
 * VOLUME-ONLY (their cost is the sub-page row sum), and `recoveries` is COST-ONLY (it is the
 * volume-less twelfth category).
 *
 * Bounds: volume 0–9,999,999 with at most two decimals. Cost varies BY CATEGORY and cannot be
 * expressed on this type — see `validation.ts` for the four bands.
 */
export interface CategoryEntry {
  readonly volume?: number | null
  readonly cost?: number | null
}

/**
 * A camp create (POST) or update (PUT).
 *
 * ⚠ ALL TWELVE categories must be present on EVERY write. An omitted `CategoryEntry` CLEARS both
 * halves of that category server-side — absent and explicit-null are the same instruction, and
 * there is no PATCH semantic. Round-trip the full served camp; never submit only what was touched.
 *
 * `associatedCampVolume` is whole numbers only (server `@Digits(fraction = 0)`), unlike the
 * category volumes which allow two decimals.
 */
export default interface CampRequest {
  readonly campName: string
  readonly roadDistanceToOperatingArea?: number | null
  readonly sizeOfCamp?: number | null
  readonly associatedCampVolume?: number | null
  readonly isolatedCamp: boolean
  readonly comments?: string | null

  // --- The twelve stored categories, in legacy screen order. All twelve, always. ---
  readonly cateringAndFood: CategoryEntry
  readonly wagesAndBenefits: CategoryEntry
  readonly depreciationLease: CategoryEntry
  readonly generalCampExpenses: CategoryEntry
  /** Volume only — the cost half is the item-62 row sum and is server-derived. */
  readonly otherCampExpenses: CategoryEntry
  /** Cost only — the volume-less category. */
  readonly recoveries: CategoryEntry
  readonly crewTransportation: CategoryEntry
  readonly equipAndSuppliesLand: CategoryEntry
  readonly equipAndSuppliesRail: CategoryEntry
  readonly equipAndSuppliesAir: CategoryEntry
  readonly equipAndSuppliesWater: CategoryEntry
  /** Volume only — the cost half is the item-68 row sum and is server-derived. */
  readonly otherAccessExpenses: CategoryEntry

  /** Required on PUT only — THIS camp's token, read from its row. A falsy `0` is valid. */
  readonly revisionCount?: number
}

/**
 * The Check Status body — the camp panel currently ON SCREEN, mirroring the backend
 * `Schedule5CheckRequest`.
 *
 * Legacy's Check Status was a full form postback, so the verdict described the screen and not the
 * saved record (issue #359, and #476 for Schedule 5's instance). The server overlays this camp onto
 * the stored ones.
 *
 * ⚠ ONE camp, not all of them, unlike Schedule 6's equivalent: the camps table renders NAMES only,
 * so the open panel is the only camp whose descriptors the client can have changed. Sending the
 * whole list would assert values for camps the reporter is not looking at, out of a possibly stale
 * client copy of the document.
 *
 * ⚠ `null` means "no usable value on screen" and MUST stay null — the server's check is a pure null
 * test (a stored `0` passes), so coercing a blank or unparseable field to `0` turns a missing
 * descriptor into a pass.
 */
export interface CampCheckEntry {
  /** The stored id, or `null` for a camp that has never been saved (new or copied). */
  readonly campId: number | null
  readonly campName: string
  readonly roadDistanceToOperatingArea: number | null
  readonly sizeOfCamp: number | null
  readonly associatedCampVolume: number | null
}

/** `camp` is `null` when no panel is open — the stored camps are then evaluated alone. */
export interface Schedule5CheckRequest {
  readonly camp: CampCheckEntry | null
}
