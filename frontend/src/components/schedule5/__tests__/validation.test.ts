import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  CAMP_MESSAGES,
  CATEGORY_KEYS,
  GRID_ROWS,
  VOLUME_CATEGORY_KEYS,
  emptyCategories,
  isCampFormValid,
  parseDecimalInput,
  roundCost,
  validateCamp,
  type CampFormValues,
} from '../validation'

// A minimally valid camp: the two required fields set, everything else blank (blank optional fields
// are CLEARED, not invalid).
const baseForm = (overrides: Partial<CampFormValues> = {}): CampFormValues => ({
  campName: 'Cedar Flats Camp',
  roadDistanceToOperatingArea: '',
  sizeOfCamp: '',
  associatedCampVolume: '',
  isolatedCamp: 'true',
  comments: '',
  categories: emptyCategories(),
  ...overrides,
})

const withCategory = (
  key: (typeof CATEGORY_KEYS)[number],
  half: 'volume' | 'cost',
  value: string,
): CampFormValues => {
  const form = baseForm()
  form.categories[key] = { ...form.categories[key], [half]: value }
  return form
}

describe('grid definition', () => {
  it('carries all twelve categories, and exactly eleven of them take a volume', () => {
    expect(CATEGORY_KEYS).toHaveLength(12)
    expect(VOLUME_CATEGORY_KEYS).toHaveLength(11)
    expect(VOLUME_CATEGORY_KEYS).not.toContain('recoveries')
  })

  it('renders the categories in legacy order, with Recoveries between the two camp totals', () => {
    const order = GRID_ROWS.filter((row) => row.kind === 'category').map((row) => row.key)
    expect(order).toEqual(CATEGORY_KEYS)
  })

  it('gives the two Other … rows an editable volume but NO editable cost', () => {
    const others = GRID_ROWS.filter(
      (row) => row.kind === 'category' && row.subPageCount !== undefined,
    )
    expect(others).toHaveLength(2)
    for (const row of others) {
      if (row.kind !== 'category') throw new Error('unreachable')
      expect(row.hasVolume).toBe(true)
      expect(row.costBand).toBeUndefined()
    }
  })

  it('gives Recoveries a cost but no volume cell at all', () => {
    const recoveries = GRID_ROWS.find((row) => row.kind === 'category' && row.key === 'recoveries')
    if (recoveries?.kind !== 'category') throw new Error('Recoveries row missing')
    expect(recoveries.hasVolume).toBe(false)
    expect(recoveries.costBand).toBe('NON_NEGATIVE')
  })
})

describe('required fields', () => {
  it('requires a camp name, rejecting whitespace-only', () => {
    expect(validateCamp(baseForm({ campName: '   ' })).campName).toBe(
      CAMP_MESSAGES.campNameRequired,
    )
  })

  it('accepts a 30-character name and rejects 31', () => {
    expect(validateCamp(baseForm({ campName: 'x'.repeat(30) })).campName).toBeUndefined()
    expect(validateCamp(baseForm({ campName: 'x'.repeat(31) })).campName).toBe(
      CAMP_MESSAGES.campNameMaxLength,
    )
  })

  it('requires isolatedCamp — a legacy null must block the save, not default to No', () => {
    expect(validateCamp(baseForm({ isolatedCamp: '' })).isolatedCamp).toBe(
      CAMP_MESSAGES.isolatedCampRequired,
    )
    expect(validateCamp(baseForm({ isolatedCamp: 'false' })).isolatedCamp).toBeUndefined()
  })

  it('accepts 3500 comment characters and rejects 3501', () => {
    expect(validateCamp(baseForm({ comments: 'x'.repeat(3500) })).comments).toBeUndefined()
    expect(validateCamp(baseForm({ comments: 'x'.repeat(3501) })).comments).toBe(
      CAMP_MESSAGES.commentsMaxLength,
    )
  })

  it('passes a minimally valid camp with every optional field blank', () => {
    expect(isCampFormValid(validateCamp(baseForm()))).toBe(true)
  })
})

describe('descriptor ranges', () => {
  it('accepts distance 999999.9 and rejects 1000000.0', () => {
    expect(
      validateCamp(baseForm({ roadDistanceToOperatingArea: '999999.9' }))
        .roadDistanceToOperatingArea,
    ).toBeUndefined()
    expect(
      validateCamp(baseForm({ roadDistanceToOperatingArea: '1000000.0' }))
        .roadDistanceToOperatingArea,
    ).toBe(CAMP_MESSAGES.distanceRange)
  })

  it('rejects size 0 and 1000, accepts 1 and 999', () => {
    expect(validateCamp(baseForm({ sizeOfCamp: '0' })).sizeOfCamp).toBe(CAMP_MESSAGES.sizeRange)
    expect(validateCamp(baseForm({ sizeOfCamp: '1000' })).sizeOfCamp).toBe(CAMP_MESSAGES.sizeRange)
    expect(validateCamp(baseForm({ sizeOfCamp: '1' })).sizeOfCamp).toBeUndefined()
    expect(validateCamp(baseForm({ sizeOfCamp: '999' })).sizeOfCamp).toBeUndefined()
  })

  it('rejects a fractional size — number of persons is a whole count', () => {
    expect(validateCamp(baseForm({ sizeOfCamp: '2.5' })).sizeOfCamp).toBe(CAMP_MESSAGES.sizeRange)
  })

  it('rejects a fractional Associated Camp Volume — the server allows it no decimals', () => {
    expect(validateCamp(baseForm({ associatedCampVolume: '120000.5' })).associatedCampVolume).toBe(
      CAMP_MESSAGES.volumeRange,
    )
    expect(
      validateCamp(baseForm({ associatedCampVolume: '120000' })).associatedCampVolume,
    ).toBeUndefined()
  })

  it('accepts camp volume 9,999,999 and rejects 10,000,000', () => {
    expect(
      validateCamp(baseForm({ associatedCampVolume: '9,999,999' })).associatedCampVolume,
    ).toBeUndefined()
    expect(validateCamp(baseForm({ associatedCampVolume: '10000000' })).associatedCampVolume).toBe(
      CAMP_MESSAGES.volumeRange,
    )
  })
})

describe('category volumes', () => {
  it('accepts 9,999,999 and rejects 10,000,000 on every one of the eleven', () => {
    for (const key of VOLUME_CATEGORY_KEYS) {
      expect(validateCamp(withCategory(key, 'volume', '9999999'))[`${key}.volume`]).toBeUndefined()
      expect(validateCamp(withCategory(key, 'volume', '10000000'))[`${key}.volume`]).toBe(
        CAMP_MESSAGES.volumeRange,
      )
    }
  })

  it('allows two decimals but not three', () => {
    expect(
      validateCamp(withCategory('cateringAndFood', 'volume', '120000.25'))[
        'cateringAndFood.volume'
      ],
    ).toBeUndefined()
    expect(
      validateCamp(withCategory('cateringAndFood', 'volume', '120000.255'))[
        'cateringAndFood.volume'
      ],
    ).toBe(CAMP_MESSAGES.volumeRange)
  })

  it('rejects a negative volume', () => {
    expect(
      validateCamp(withCategory('cateringAndFood', 'volume', '-1'))['cateringAndFood.volume'],
    ).toBe(CAMP_MESSAGES.volumeRange)
  })

  it('reports unparseable entry with the converter message, not the range message', () => {
    expect(
      validateCamp(withCategory('cateringAndFood', 'volume', '12,34'))['cateringAndFood.volume'],
    ).toBe(CAMP_MESSAGES.volumeInvalid)
  })

  it('ignores a volume typed into Recoveries — it has no volume half to validate', () => {
    expect(
      validateCamp(withCategory('recoveries', 'volume', '10000000'))['recoveries.volume'],
    ).toBeUndefined()
  })

  it('ignores a cost typed into either Other … row — their cost is server-derived', () => {
    expect(
      validateCamp(withCategory('otherCampExpenses', 'cost', '99999999999'))[
        'otherCampExpenses.cost'
      ],
    ).toBeUndefined()
    expect(
      validateCamp(withCategory('otherAccessExpenses', 'cost', '99999999999'))[
        'otherAccessExpenses.cost'
      ],
    ).toBeUndefined()
  })
})

describe('the four cost bands', () => {
  const STANDARD_KEYS = [
    'cateringAndFood',
    'depreciationLease',
    'generalCampExpenses',
    'crewTransportation',
    'equipAndSuppliesLand',
    'equipAndSuppliesRail',
    'equipAndSuppliesAir',
    'equipAndSuppliesWater',
  ] as const

  it('holds the eight ordinary categories at ±9,999,999', () => {
    for (const key of STANDARD_KEYS) {
      expect(validateCamp(withCategory(key, 'cost', '9999999'))[`${key}.cost`]).toBeUndefined()
      expect(validateCamp(withCategory(key, 'cost', '-9999999'))[`${key}.cost`]).toBeUndefined()
      expect(validateCamp(withCategory(key, 'cost', '10000000'))[`${key}.cost`]).toBe(
        CAMP_MESSAGES.costRange,
      )
      expect(validateCamp(withCategory(key, 'cost', '-10000000'))[`${key}.cost`]).toBe(
        CAMP_MESSAGES.costRange,
      )
    }
  })

  it('lets wagesAndBenefits alone reach ±99,999,999 — the trap that bricks stored camps', () => {
    expect(
      validateCamp(withCategory('wagesAndBenefits', 'cost', '10000000'))['wagesAndBenefits.cost'],
    ).toBeUndefined()
    expect(
      validateCamp(withCategory('wagesAndBenefits', 'cost', '99999999'))['wagesAndBenefits.cost'],
    ).toBeUndefined()
    expect(
      validateCamp(withCategory('wagesAndBenefits', 'cost', '-99999999'))['wagesAndBenefits.cost'],
    ).toBeUndefined()
    expect(
      validateCamp(withCategory('wagesAndBenefits', 'cost', '100000000'))['wagesAndBenefits.cost'],
    ).toBe(CAMP_MESSAGES.costRangeWide)
    expect(
      validateCamp(withCategory('wagesAndBenefits', 'cost', '-100000000'))['wagesAndBenefits.cost'],
    ).toBe(CAMP_MESSAGES.costRangeWide)
  })

  it('rejects on wages the value the eight siblings reject, only ten times higher', () => {
    // The same entry, opposite verdicts — the asymmetry stated as a single assertion pair.
    expect(
      validateCamp(withCategory('wagesAndBenefits', 'cost', '10000000'))['wagesAndBenefits.cost'],
    ).toBeUndefined()
    expect(
      validateCamp(withCategory('cateringAndFood', 'cost', '10000000'))['cateringAndFood.cost'],
    ).toBe(CAMP_MESSAGES.costRange)
  })

  it('floors recoveries at 0 with its own message', () => {
    expect(validateCamp(withCategory('recoveries', 'cost', '0'))['recoveries.cost']).toBeUndefined()
    expect(
      validateCamp(withCategory('recoveries', 'cost', '9999999'))['recoveries.cost'],
    ).toBeUndefined()
    expect(validateCamp(withCategory('recoveries', 'cost', '-1'))['recoveries.cost']).toBe(
      CAMP_MESSAGES.costRangeNonNegative,
    )
    expect(validateCamp(withCategory('recoveries', 'cost', '10000000'))['recoveries.cost']).toBe(
      CAMP_MESSAGES.costRangeNonNegative,
    )
  })

  it('range-checks the ROUNDED cost, so 9,999,999.5 is rejected rather than sent as 10,000,000', () => {
    expect(
      validateCamp(withCategory('cateringAndFood', 'cost', '9999999.5'))['cateringAndFood.cost'],
    ).toBe(CAMP_MESSAGES.costRange)
    expect(
      validateCamp(withCategory('cateringAndFood', 'cost', '9999999.4'))['cateringAndFood.cost'],
    ).toBeUndefined()
  })

  it('reports unparseable cost with the converter message', () => {
    expect(
      validateCamp(withCategory('cateringAndFood', 'cost', '1000abc'))['cateringAndFood.cost'],
    ).toBe(CAMP_MESSAGES.costInvalid)
  })
})

describe('parseDecimalInput (legacy converter fidelity)', () => {
  it('accepts grouped input the way legacy did', () => {
    expect(parseDecimalInput('1,000')).toBe(1000)
    expect(parseDecimalInput('9,999,999')).toBe(9999999)
  })

  it('rejects the JS-only forms legacy never allowed', () => {
    expect(parseDecimalInput('1e2')).toBeNull()
    expect(parseDecimalInput('0x10')).toBeNull()
    expect(parseDecimalInput('Infinity')).toBeNull()
  })

  it('rejects mis-grouped and junk-suffixed input legacy silently mangled', () => {
    expect(parseDecimalInput('12,34')).toBeNull()
    expect(parseDecimalInput('1000abc')).toBeNull()
  })

  it('treats blank as absent', () => {
    expect(parseDecimalInput('')).toBeNull()
    expect(parseDecimalInput('   ')).toBeNull()
  })
})

describe('roundCost', () => {
  it('rounds half AWAY from zero, matching Oracle rather than JS', () => {
    expect(roundCost(0.5)).toBe(1)
    // Native Math.round(-0.5) is -0 (half-up); Oracle rounds away from zero.
    expect(roundCost(-0.5)).toBe(-1)
    expect(roundCost(2.5)).toBe(3)
    expect(roundCost(-2.5)).toBe(-3)
  })

  it('passes null through — null is cleared, not zero', () => {
    expect(roundCost(null)).toBeNull()
  })
})

describe('camp name length is judged on the trimmed value (review fix)', () => {
  it('accepts a 30-char name padded past 30 by whitespace — the trimmed form is what is sent', () => {
    const name = `  ${'x'.repeat(30)}  `
    expect(validateCamp(baseForm({ campName: name })).campName).toBeUndefined()
  })

  it('still rejects a name whose TRIMMED length exceeds 30', () => {
    expect(validateCamp(baseForm({ campName: 'x'.repeat(31) })).campName).toBe(
      CAMP_MESSAGES.campNameMaxLength,
    )
  })
})

describe('CAMP_MESSAGES drift guard (review fix)', () => {
  // The thirteen mirrored strings claim to be "verbatim from the backend bundle"; this is the check
  // that keeps the claim true. A backend wording change must fail HERE, not silently split the
  // advisory text from the server's 400 detail for the same field.
  const bundle = (() => {
    // Vitest runs with cwd = frontend/, and the backend lives beside it in the monorepo.
    const path = resolve(process.cwd(), '../backend/src/main/resources/messages.properties')
    const entries: Record<string, string> = {}
    for (const line of readFileSync(path, 'utf8').split('\n')) {
      const trimmed = line.trim()
      if (trimmed === '' || trimmed.startsWith('#')) {
        continue
      }
      const eq = trimmed.indexOf('=')
      if (eq > 0) {
        entries[trimmed.slice(0, eq)] = trimmed.slice(eq + 1)
      }
    }
    return entries
  })()

  // Which bundle key each client literal mirrors (story § Validation mirror).
  const BUNDLE_KEYS: Record<keyof typeof CAMP_MESSAGES, string> = {
    campNameRequired: 'campNameRequiredErrorMsg',
    campNameMaxLength: 'campNameMaxLengthErrorMsg',
    isolatedCampRequired: 'isolatedCampRequiredErrorMsg',
    commentsMaxLength: 'campCommentsMaxLengthErrorMsg',
    distanceRange: 'distanceValidatorErrorMsg',
    sizeRange: 'numberOfPersonsValidatorErrorMsg',
    volumeRange: 'volumeValidatorErrorMsg',
    volumeInvalid: 'volumeConverterErrorMsg',
    costRange: 'costSize7ValidatorErrorMsg',
    costRangeWide: 'costValidatorErrorMsg',
    costRangeNonNegative: 'costValidatorSchedule9ErrorMsg',
    costInvalid: 'costConverterErrorMsg',
    campNameDuplicate: 'campAlreadyExists',
  }

  it.each(Object.keys(BUNDLE_KEYS) as (keyof typeof CAMP_MESSAGES)[])(
    '%s is byte-identical to its bundle entry',
    (local) => {
      const bundleKey = BUNDLE_KEYS[local]
      expect(bundle[bundleKey], `bundle key ${bundleKey} is missing`).toBeDefined()
      expect(CAMP_MESSAGES[local]).toBe(bundle[bundleKey])
    },
  )
})

describe('camp name uniqueness (BR-02, client pre-check)', () => {
  it('reports a duplicate case-insensitively and ignoring surrounding whitespace', () => {
    // The server upper-cases both sides (Schedule5Repository:419) and trims the submitted name
    // (Schedule5Service.trimmedCampName():863), so this entry WOULD be rejected server-side.
    //
    // Asserted against the LITERAL, not CAMP_MESSAGES.campNameDuplicate: if the whole feature
    // (constant and logic together) were reverted, the constant access would also become
    // `undefined` and this assertion would pass vacuously against itself. The bundle drift-guard
    // `it.each` above is what pins the constant to the backend string; this test's job is to pin
    // the FEATURE to that same string.
    const errors = validateCamp(baseForm({ campName: '  cedar flats camp  ' }), [
      'Cedar Flats Camp',
    ])
    expect(errors.campName).toBe('Camp name already exists.')
  })

  it('does NOT collide with a padded STORED name — the server does not trim that side either', () => {
    // `countCampsNamed` compares `UPPER(CAMP_NAME)` with no TRIM on the stored side
    // (Schedule5Repository.java:400-410): legacy persisted names untrimmed, so the server ACCEPTS
    // this save. Trimming here would hard-block it, and buildRequest trims the entry so the
    // licensee could not type padding to escape.
    expect(
      validateCamp(baseForm({ campName: 'Cedar Flats Camp' }), ['  Cedar Flats Camp  ']),
    ).toEqual({})
  })

  it('accepts a name no other camp holds', () => {
    expect(validateCamp(baseForm({ campName: 'Birch Ridge Camp' }), ['Cedar Flats Camp'])).toEqual(
      {},
    )
  })

  it('checks nothing when the caller supplies no names, including by omission', () => {
    expect(validateCamp(baseForm())).toEqual({})
    expect(validateCamp(baseForm(), [])).toEqual({})
  })

  it('reports the name’s OWN error ahead of the duplicate when it is also blank or over-length', () => {
    // Two statements about one field where only the first is actionable. Both entries below are
    // ALSO their own duplicate under the fold (blank trims to '' on both sides; overLong is listed
    // verbatim in otherCampNames), so this only proves precedence — rather than merely re-proving
    // the pre-existing required/max-length rules — if it also fails were the `else` at
    // validation.ts's name block dropped, letting an unconditional duplicate check run after and
    // overwrite the required/max-length error. Asserting the duplicate message's ABSENCE, not just
    // the own-error's presence, is what makes that mutation observable here.
    const blank = validateCamp(baseForm({ campName: '   ' }), ['   ']).campName
    expect(blank).toBe(CAMP_MESSAGES.campNameRequired)
    expect(blank).not.toBe('Camp name already exists.')

    const overLong = 'C'.repeat(31)
    const long = validateCamp(baseForm({ campName: overLong }), [overLong]).campName
    expect(long).toBe(CAMP_MESSAGES.campNameMaxLength)
    expect(long).not.toBe('Camp name already exists.')
  })

  it('leaves every other field’s rules untouched', () => {
    // A duplicate name must not mask, or be masked by, an unrelated error. campName asserted
    // against the LITERAL for the same reason as the first test in this block.
    const errors = validateCamp(baseForm({ campName: 'Cedar Flats Camp', sizeOfCamp: '0' }), [
      'Cedar Flats Camp',
    ])
    expect(errors.campName).toBe('Camp name already exists.')
    expect(errors.sizeOfCamp).toBe(CAMP_MESSAGES.sizeRange)
  })
})
