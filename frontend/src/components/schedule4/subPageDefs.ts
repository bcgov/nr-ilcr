// Sub-page (list-based transportation) types + advisory validation (Story 10.6). The BACKEND is
// authoritative; these checks give inline feedback and gate Add. Ranges + messages MIRROR the
// Schedule 4 sub-page write DTO / message bundle (tighter than the category grid).

export type SubPageType = 'TOWING' | 'TRUCK_REHAUL' | 'OTHER'

export interface SubPageDef {
  type: SubPageType
  code: number // legacy ILCR_REPORT_COST_ITEM_ID
  label: string
  hasCycle: boolean
}

// The three list sub-pages (code 54 is dead — never shown), in legacy order.
export const SUB_PAGE_DEFS: SubPageDef[] = [
  { type: 'TOWING', code: 43, label: 'Towing Total', hasCycle: false },
  { type: 'TRUCK_REHAUL', code: 46, label: 'Truck Rehaul-Dewater/Transfer', hasCycle: true },
  { type: 'OTHER', code: 55, label: 'Other Transportation', hasCycle: false },
]

export const subPageDefByCode = (code: number): SubPageDef | undefined =>
  SUB_PAGE_DEFS.find((d) => d.code === code)

const VOLUME = { min: 0, max: 999_999 }
const COST = { min: -9_999_999, max: 9_999_999 }
const DISTANCE = { min: 0, max: 999_999.9 }
const CYCLE = { min: 0, max: 999_999 }

export const SUB_PAGE_MESSAGES = {
  descriptionRequired: 'Value Required',
  volume: 'Entered volume must be between 0 and 999,999.',
  cost: 'Entered cost must be between -9,999,999 and 9,999,999.',
  distance: 'Entered distance must be between 0 and 999,999.',
  cycle: 'Entered cycle time must be between 0 and 999,999.',
} as const

export interface SubPageRowForm {
  description: string
  distance: string
  volume: string
  cost: string
  cycle: string
}

export const emptySubPageRowForm = (): SubPageRowForm => ({
  description: '',
  distance: '',
  volume: '',
  cost: '',
  cycle: '',
})

const isBlank = (raw: string): boolean => raw.trim() === ''

const rangeError = (
  raw: string,
  range: { min: number; max: number },
  message: string,
): string | undefined => {
  if (isBlank(raw)) return undefined
  const value = Number(raw)
  if (Number.isNaN(value) || value < range.min || value > range.max) return message
  return undefined
}

/**
 * Advisory validation for a sub-page add-row: Description required (ERR/FLD `Value Required`), and
 * per-field ranges (volume 0–999,999 / cost ±9,999,999 / distance 0–999,999.9 / cycle 0–999,999,
 * cycle only for Truck Rehaul). Returns a map of field → message for every invalid field.
 */
export function validateSubPageRow(
  form: SubPageRowForm,
  hasCycle: boolean,
): Record<string, string> {
  const errors: Record<string, string> = {}
  if (isBlank(form.description)) errors.description = SUB_PAGE_MESSAGES.descriptionRequired
  const volume = rangeError(form.volume, VOLUME, SUB_PAGE_MESSAGES.volume)
  if (volume) errors.volume = volume
  const cost = rangeError(form.cost, COST, SUB_PAGE_MESSAGES.cost)
  if (cost) errors.cost = cost
  const distance = rangeError(form.distance, DISTANCE, SUB_PAGE_MESSAGES.distance)
  if (distance) errors.distance = distance
  if (hasCycle) {
    const cycle = rangeError(form.cycle, CYCLE, SUB_PAGE_MESSAGES.cycle)
    if (cycle) errors.cycle = cycle
  }
  return errors
}
