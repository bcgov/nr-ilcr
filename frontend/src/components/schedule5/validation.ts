// Advisory client-side validation for a Schedule 5 camp. The BACKEND is authoritative (Story 7.2);
// these checks give immediate inline feedback and gate the call to avoid a doomed round-trip. Ranges
// and messages MIRROR the backend CampRequest/CategoryEntry DTOs and the message bundle, so an
// advisory message reads identically to a server rejection — which still renders verbatim on a 400
// (AD-6/AD-8), never replaced by anything here.

import { parseDecimalInput, roundCost } from '@/utils/number'

export { parseDecimalInput, roundCost }

// Column-fidelity caps, exported so index.tsx binds the SAME numbers to maxLength/maxCount that this
// module validates against.
export const CAMP_NAME_MAX_LENGTH = 30
export const COMMENTS_MAX_LENGTH = 3500

// Verbatim from the backend bundle so an advisory message is byte-identical to the server's
// rejection for the same field.
export const CAMP_MESSAGES = {
  campNameRequired: 'Camp Name is required.',
  campNameMaxLength: 'Camp Name must be 30 characters or fewer.',
  isolatedCampRequired: 'Isolated Camp is required.',
  commentsMaxLength: 'Comments must be 3500 characters or fewer.',
  // The message understates its own bound by 0.9 — the validator accepts up to 999,999.9 while the
  // text says 999,999. Both are kept exactly as legacy has them rather than "corrected" here.
  distanceRange: 'Entered distance must be between 0 and 999,999.',
  sizeRange: 'Entered number of persons must be between 1 and 999.',
  volumeRange: 'Entered volume must be between 0 and 9,999,999.',
  volumeInvalid: 'Entered volume entry is invalid.',
  costRange: 'Entered cost must be between -9,999,999 and 9,999,999.',
  costRangeWide: 'Entered cost must be between -99,999,999 and 99,999,999.',
  costRangeNonNegative: 'Entered cost must be between 0 and 9,999,999.',
  costInvalid: 'Entered cost is invalid.',
  // BR-02/ERR-001. Mirrored so the client's pre-check and the server's 409 say the same sentence.
  campNameDuplicate: 'Camp name already exists.',
} as const

// The four cost bands. Encoded as DATA keyed by category rather than three copies of an `if`,
// because the split is not uniform and the odd ones out are easy to lose in a rewrite:
//
//   WIDE          — wagesAndBenefits ALONE, ten times wider than its siblings. Its legacy input is
//                   the only one missing the costSize="7" attribute (schedule5ExistingCamp.xhtml
//                   :160-162, and newWagesAndBenefitsCost :99-101 omits it too, so add and edit
//                   share the bound). A blanket ±9,999,999 rule would make any stored camp above
//                   9,999,999 un-re-saveable.
//   NON_NEGATIVE  — recoveries, floored at 0 per its legacy message's range (costSize="0" :252).
//                   Stored POSITIVE and subtracted; the floor is an INPUT rule only.
//   STANDARD      — the eight ordinary costSize="7" categories.
//   NONE          — the two Other … rows, whose cost is the sub-page row sum and is read-only.
const COST_BANDS = {
  STANDARD: { min: -9_999_999, max: 9_999_999, message: CAMP_MESSAGES.costRange },
  WIDE: { min: -99_999_999, max: 99_999_999, message: CAMP_MESSAGES.costRangeWide },
  NON_NEGATIVE: { min: 0, max: 9_999_999, message: CAMP_MESSAGES.costRangeNonNegative },
} as const

type CostBand = keyof typeof COST_BANDS

// Category volumes allow two decimals (ILCR_COST_REPORT_DETAIL.VOLUME NUMBER(10,2)). The camp-level
// Associated Camp Volume does NOT — its server constraint is @Digits(fraction = 0) — so BR-03 can
// only ever propagate a whole number into them.
const VOLUME = { min: 0, max: 9_999_999, maxFractionDigits: 2 }
const CAMP_VOLUME = { min: 0, max: 9_999_999, maxFractionDigits: 0 }
const DISTANCE = { min: 0, max: 999_999.9, maxFractionDigits: 2 }
const SIZE_OF_CAMP = { min: 1, max: 999 }

/** The twelve stored categories — the keys a write must carry, all of them, every time. */
export const CATEGORY_KEYS = [
  'cateringAndFood',
  'wagesAndBenefits',
  'depreciationLease',
  'generalCampExpenses',
  'otherCampExpenses',
  'recoveries',
  'crewTransportation',
  'equipAndSuppliesLand',
  'equipAndSuppliesRail',
  'equipAndSuppliesAir',
  'equipAndSuppliesWater',
  'otherAccessExpenses',
] as const

export type CategoryKey = (typeof CATEGORY_KEYS)[number]

/**
 * The eleven categories BR-03 propagates the Associated Camp Volume into — every category except
 * the volume-less `recoveries`, in the exact order legacy assigns them
 * (`Schedule5MB.updateCampVolumes():248-261`).
 */
export const VOLUME_CATEGORY_KEYS = CATEGORY_KEYS.filter(
  (key) => key !== 'recoveries',
) as readonly CategoryKey[]

/**
 * THE camp grid, in legacy render order — the single source of row order, labels, editability and
 * per-category bounds. `CategoryGrid` is its only renderer and the add and edit panels share it, so
 * they cannot drift apart the way Schedule 6's duplicated field blocks did.
 *
 * Labels are verbatim from `schedule5ExistingCamp.xhtml`, INCLUDING the trailing `": "`.
 */
export type GridRow =
  /** A section header spanning the four columns (`Camp Expenses` etc.). */
  | { readonly kind: 'section'; readonly label: string }
  /** A label-only row with no inputs (`Equipment and Supplies Transportation`). */
  | { readonly kind: 'group'; readonly label: string }
  /** A stored category: an editable volume and/or cost, plus a read-only server-derived $/m³. */
  | {
      readonly kind: 'category'
      readonly key: CategoryKey
      readonly label: string
      readonly indented?: true
      /** False for `recoveries`, which has no volume cell at all. */
      readonly hasVolume: boolean
      /** Absent for the two Other … rows, whose cost is server-derived and read-only. */
      readonly costBand?: CostBand
      /** Present on the two Other … rows: which sub-page count the label interpolates. */
      readonly subPageCount?: 'otherCampExpenseCount' | 'otherAccessExpenseCount'
    }
  /** A wholly server-derived row — every cell read-only, nothing submitted. */
  | { readonly kind: 'derived'; readonly key: DerivedKey; readonly label: string }

export type DerivedKey = 'campSubTotal' | 'campTotal' | 'accessExpenseTotal' | 'campAndAccessTotal'

export const GRID_ROWS: readonly GridRow[] = [
  { kind: 'section', label: 'Camp Expenses' },
  {
    kind: 'category',
    key: 'cateringAndFood',
    label: 'Catering and Food: ',
    hasVolume: true,
    costBand: 'STANDARD',
  },
  {
    kind: 'category',
    key: 'wagesAndBenefits',
    label: 'Wages and Benefits: ',
    hasVolume: true,
    costBand: 'WIDE',
  },
  {
    kind: 'category',
    key: 'depreciationLease',
    label: 'Depreciation/Lease: ',
    hasVolume: true,
    costBand: 'STANDARD',
  },
  {
    kind: 'category',
    key: 'generalCampExpenses',
    label: 'General Camp Expenses: ',
    hasVolume: true,
    costBand: 'STANDARD',
  },
  {
    kind: 'category',
    key: 'otherCampExpenses',
    label: 'Other Camp Expenses',
    hasVolume: true,
    subPageCount: 'otherCampExpenseCount',
  },
  { kind: 'derived', key: 'campSubTotal', label: 'Camp Sub-Total: ' },
  {
    kind: 'category',
    key: 'recoveries',
    label: 'Recoveries: ',
    hasVolume: false,
    costBand: 'NON_NEGATIVE',
  },
  { kind: 'derived', key: 'campTotal', label: 'Camp Total: ' },
  { kind: 'section', label: 'Access Expenses' },
  {
    kind: 'category',
    key: 'crewTransportation',
    label: 'Crew Transportation: ',
    hasVolume: true,
    costBand: 'STANDARD',
  },
  { kind: 'group', label: 'Equipment and Supplies Transportation' },
  {
    kind: 'category',
    key: 'equipAndSuppliesLand',
    label: 'Land: ',
    indented: true,
    hasVolume: true,
    costBand: 'STANDARD',
  },
  {
    kind: 'category',
    key: 'equipAndSuppliesRail',
    label: 'Rail: ',
    indented: true,
    hasVolume: true,
    costBand: 'STANDARD',
  },
  {
    kind: 'category',
    key: 'equipAndSuppliesAir',
    label: 'Air: ',
    indented: true,
    hasVolume: true,
    costBand: 'STANDARD',
  },
  {
    kind: 'category',
    key: 'equipAndSuppliesWater',
    label: 'Water: ',
    indented: true,
    hasVolume: true,
    costBand: 'STANDARD',
  },
  {
    kind: 'category',
    key: 'otherAccessExpenses',
    label: 'Other Access Expenses',
    hasVolume: true,
    subPageCount: 'otherAccessExpenseCount',
  },
  { kind: 'derived', key: 'accessExpenseTotal', label: 'Access Expense Total: ' },
  { kind: 'section', label: 'Total Expense' },
  { kind: 'derived', key: 'campAndAccessTotal', label: 'Camp and Access: ' },
]

/** The per-category entered values. Every field is a string — the raw text the licensee typed. */
export interface CategoryFormValues {
  volume: string
  cost: string
}

/**
 * The camp panel's entered values. All strings, including `isolatedCamp`: it is tri-state on the
 * wire (true/false/null) and `''` is the "nothing selected" state a legacy null must render as —
 * which then blocks the save rather than silently defaulting to No.
 */
export interface CampFormValues {
  campName: string
  roadDistanceToOperatingArea: string
  sizeOfCamp: string
  associatedCampVolume: string
  isolatedCamp: '' | 'true' | 'false'
  comments: string
  categories: Record<CategoryKey, CategoryFormValues>
}

/** Field-keyed errors. Category halves are keyed `${categoryKey}.volume` / `${categoryKey}.cost`. */
export type CampErrors = Record<string, string>

/** Fractional digits of the raw string (grouping lives only in the integer part). */
const fractionDigits = (trimmed: string): number =>
  trimmed.includes('.') ? (trimmed.split('.')[1]?.length ?? 0) : 0

type Bounds = { min: number; max: number; maxFractionDigits?: number }

// A blank optional field is CLEARED, not invalid: every legacy validator short-circuited unless the
// submitted value was a BigDecimal, so an empty input was silently accepted and stored NULL.
const validateOptionalNumber = (
  raw: string,
  bounds: Bounds,
  rangeMessage: string,
  invalidMessage: string,
): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return undefined
  }
  const value = parseDecimalInput(trimmed)
  if (value === null) {
    return invalidMessage
  }
  if (
    bounds.maxFractionDigits !== undefined &&
    fractionDigits(trimmed) > bounds.maxFractionDigits
  ) {
    return rangeMessage
  }
  return value < bounds.min || value > bounds.max ? rangeMessage : undefined
}

/**
 * BR-02's comparison, matching the server's predicate exactly — which is ASYMMETRIC:
 *
 *     UPPER(CAMP_NAME) = UPPER(:name)      -- Schedule5Repository.java:417
 *
 * Case is folded on both sides, but only the SUBMITTED side is trimmed, by
 * `Schedule5Service.trimmedCampName()` (`:862-864`) before the value is bound. The STORED side is
 * deliberately left untrimmed, and that repository docblock (`:400-410`) says why: legacy persisted
 * names untrimmed (it cites `Schedule5DAO.java:373`), so a stored `" Cedar "` does not collide with a
 * new `"Cedar"` there either, and adding `TRIM(CAMP_NAME)` "would retroactively 409 edits next to
 * padded incumbents legacy accepted."
 *
 * So this must NOT trim `name`. Doing so made the advisory check STRICTER than the authority it
 * mirrors, and because Save is gated on `validateCamp`, a padded legacy incumbent hard-blocked a save
 * the server accepts — with no way out, since `buildRequest` trims the entry.
 *
 * `toUpperCase`, not `toLocaleUpperCase`: Oracle's `UPPER` is not locale-aware, and a locale-aware
 * fold would disagree with it on a Turkish dotless i.
 */
const isDuplicateName = (raw: string, otherCampNames: readonly string[]): boolean => {
  const candidate = raw.trim().toUpperCase()
  return otherCampNames.some((name) => name.toUpperCase() === candidate)
}

const validateCampName = (raw: string): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return CAMP_MESSAGES.campNameRequired
  }
  // Judge the TRIMMED length — buildRequest trims before sending, so the rule must measure the
  // value that actually travels, never reject one whose sent form is legal.
  return trimmed.length > CAMP_NAME_MAX_LENGTH ? CAMP_MESSAGES.campNameMaxLength : undefined
}

const validateCategoryCost = (raw: string, band: CostBand): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return undefined
  }
  const parsed = parseDecimalInput(trimmed)
  if (parsed === null) {
    return CAMP_MESSAGES.costInvalid
  }
  // Range-check the ROUNDED value, not the raw entry: costs are whole dollars and are rounded
  // half-away-from-zero before sending, so a raw 9,999,999.5 becomes 10,000,000 and must be
  // rejected here rather than passing an advisory check only to be rejected by the server.
  const value = roundCost(parsed)
  const bounds = COST_BANDS[band]
  return value === null || value < bounds.min || value > bounds.max ? bounds.message : undefined
}

/**
 * Validate the whole camp panel. Returns a field-keyed error map, empty when the form may be sent.
 *
 * `otherCampNames` carries the names of every OTHER camp in the served mill/year, letting BR-02 be
 * reported inline instead of only as the server's 409 on a doomed round-trip. It is a PRE-check, not
 * a replacement: the served list is a snapshot, so a camp another licensee adds after this page
 * loaded is invisible here and only `Schedule5Service`'s own `countCampsNamed` can catch it. That
 * 409 still renders verbatim on the page banner (AD-6/AD-8).
 *
 * Legacy left this check entirely to the server, so the client half is a deliberate deviation.
 *
 * The caller supplies the list already filtered — by campId, see `otherCampNames` in index.tsx — so
 * this module stays free of transport types and of any notion of which camp the panel is showing.
 */
export const validateCamp = (
  values: CampFormValues,
  otherCampNames: readonly string[] = [],
): CampErrors => {
  const errors: CampErrors = {}

  const nameError = validateCampName(values.campName)
  if (nameError) {
    errors.campName = nameError
  } else if (isDuplicateName(values.campName, otherCampNames)) {
    // Only once the name is otherwise legal: "required"/"30 characters or fewer" and "already
    // exists" are two statements about one field where only the first is actionable.
    errors.campName = CAMP_MESSAGES.campNameDuplicate
  }

  // No converter message exists in the bundle for distance or size, so an unparseable entry falls
  // back to the range text — the closest verbatim string the server would answer with.
  const distanceError = validateOptionalNumber(
    values.roadDistanceToOperatingArea,
    DISTANCE,
    CAMP_MESSAGES.distanceRange,
    CAMP_MESSAGES.distanceRange,
  )
  if (distanceError) {
    errors.roadDistanceToOperatingArea = distanceError
  }

  const sizeTrimmed = values.sizeOfCamp.trim()
  if (sizeTrimmed !== '') {
    const size = parseDecimalInput(sizeTrimmed)
    if (
      size === null ||
      !Number.isInteger(size) ||
      size < SIZE_OF_CAMP.min ||
      size > SIZE_OF_CAMP.max
    ) {
      errors.sizeOfCamp = CAMP_MESSAGES.sizeRange
    }
  }

  const campVolumeError = validateOptionalNumber(
    values.associatedCampVolume,
    CAMP_VOLUME,
    CAMP_MESSAGES.volumeRange,
    CAMP_MESSAGES.volumeInvalid,
  )
  if (campVolumeError) {
    errors.associatedCampVolume = campVolumeError
  }

  if (values.isolatedCamp === '') {
    errors.isolatedCamp = CAMP_MESSAGES.isolatedCampRequired
  }

  if (values.comments.length > COMMENTS_MAX_LENGTH) {
    errors.comments = CAMP_MESSAGES.commentsMaxLength
  }

  for (const row of GRID_ROWS) {
    if (row.kind !== 'category') {
      continue
    }
    const entry = values.categories[row.key]
    if (row.hasVolume) {
      const volumeError = validateOptionalNumber(
        entry.volume,
        VOLUME,
        CAMP_MESSAGES.volumeRange,
        CAMP_MESSAGES.volumeInvalid,
      )
      if (volumeError) {
        errors[`${row.key}.volume`] = volumeError
      }
    }
    if (row.costBand) {
      const costError = validateCategoryCost(entry.cost, row.costBand)
      if (costError) {
        errors[`${row.key}.cost`] = costError
      }
    }
  }

  return errors
}

/** True when nothing is wrong and the write may be dispatched. */
export const isCampFormValid = (errors: CampErrors): boolean => Object.keys(errors).length === 0

/** An empty category grid — every one of the twelve present, both halves blank. */
export const emptyCategories = (): Record<CategoryKey, CategoryFormValues> =>
  Object.fromEntries(CATEGORY_KEYS.map((key) => [key, { volume: '', cost: '' }])) as Record<
    CategoryKey,
    CategoryFormValues
  >
