// Advisory client-side validation for the Schedule 2 form. The BACKEND is authoritative; these checks
// only give immediate inline feedback and gate Save to avoid a doomed round-trip. Ranges + messages
// MIRROR the Schedule 2 backend write DTO / message bundle — keep the two in sync. Blank is always
// allowed (Check Status catches missing required fields).

// item 25 cost (purchasedLogCostCost)
const ITEM_25_COST = { min: -99_999_999, max: 99_999_999 }
// item 26 volume (lessLogSalesVolume) — min 0, NOT signed
const ITEM_26_VOLUME = { min: 0, max: 9_999_999 }
// item 26 cost (lessLogSalesCost)
const ITEM_26_COST = { min: -999_999_999, max: 999_999_999 }

// Verbatim wording (matches the backend bundle) so the advisory message reads identically to what the
// server would return; frontend constants only because no request has been made yet.
export const VALIDATION_MESSAGES = {
  item25Cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  item26Volume: 'Entered volume must be between 0 and 9,999,999.',
  item26Cost: 'Entered cost must be between -999,999,999 and 999,999,999.',
  costInvalid: 'Entered cost is invalid.',
  volumeInvalid: 'Entered volume entry is invalid.',
} as const

// The three editable numeric form keys — these mirror the flat Schedule2Request field names.
const FIELD_RULES: Record<
  string,
  { kind: 'cost' | 'volume'; range: { min: number; max: number }; rangeMessage: string }
> = {
  purchasedLogCostCost: {
    kind: 'cost',
    range: ITEM_25_COST,
    rangeMessage: VALIDATION_MESSAGES.item25Cost,
  },
  lessLogSalesVolume: {
    kind: 'volume',
    range: ITEM_26_VOLUME,
    rangeMessage: VALIDATION_MESSAGES.item26Volume,
  },
  lessLogSalesCost: {
    kind: 'cost',
    range: ITEM_26_COST,
    rangeMessage: VALIDATION_MESSAGES.item26Cost,
  },
}

/** Advisory validation: returns a map of fieldKey → error message for every invalid editable field. */
export function validateSchedule2(form: Record<string, string>): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const [key, rule] of Object.entries(FIELD_RULES)) {
    const raw = form[key]
    if (raw === undefined || raw.trim() === '') {
      continue
    }
    const value = Number(raw)
    if (Number.isNaN(value)) {
      errors[key] =
        rule.kind === 'cost' ? VALIDATION_MESSAGES.costInvalid : VALIDATION_MESSAGES.volumeInvalid
      continue
    }
    if (value < rule.range.min || value > rule.range.max) {
      errors[key] = rule.rangeMessage
    }
  }
  return errors
}
