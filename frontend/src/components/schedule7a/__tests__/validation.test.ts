import { describe, expect, test } from 'vitest'
import {
  BRIDGE_MESSAGES,
  emptyBridgeForm,
  parseDecimalInput,
  roundCost,
  validateBridge,
} from '../validation'
import type { BridgeFormValues } from '../validation'

// A fully valid bridge; each case below perturbs exactly one field so a failure names its own cause.
const valid = (overrides: Partial<BridgeFormValues> = {}): BridgeFormValues => ({
  ...emptyBridgeForm(),
  locationName: 'North Fork Bridge',
  builtDate: '2020-06',
  constructionTypeCode: 'N',
  superstructureTypeCode: 'STL',
  deckTypeCode: 'WD',
  abutmentTypeCode: 'CONC',
  loadRatingCode: 'L100',
  lifeSpan: '50',
  abutmentHeight: '5.0',
  length: '20.0',
  width: '4.0',
  distance: '12',
  ...overrides,
})

describe('parseDecimalInput', () => {
  test.each([
    ['1000', 1000],
    ['1,000', 1000],
    ['1,234.5', 1234.5],
    ['-250', -250],
    ['0', 0],
  ])('accepts the legacy DecimalFormat shape %s', (raw, expected) => {
    expect(parseDecimalInput(raw)).toBe(expected)
  })

  test.each(['', '   ', 'abc', '1e2', '0x10', 'Infinity', '12,34', '1000abc', '1.2.3'])(
    'rejects %s as not a legacy decimal',
    (raw) => {
      expect(parseDecimalInput(raw)).toBeNull()
    },
  )
})

describe('roundCost', () => {
  test.each([
    [1.4, 1],
    [1.5, 2],
    [-1.5, -2],
    [-1.4, -1],
    [0, 0],
  ])('rounds %s half-away-from-zero like Oracle', (input, expected) => {
    expect(roundCost(input)).toBe(expected)
  })

  test('passes null through', () => {
    expect(roundCost(null)).toBeNull()
  })
})

describe('validateBridge — required attributes', () => {
  test('a fully valid bridge produces no errors', () => {
    expect(validateBridge(valid())).toEqual({})
  })

  test('blank name/location is required', () => {
    expect(validateBridge(valid({ locationName: '   ' })).locationName).toBe(
      BRIDGE_MESSAGES.valueRequired,
    )
  })

  test('name/location over 30 characters is rejected', () => {
    expect(validateBridge(valid({ locationName: 'x'.repeat(31) })).locationName).toBe(
      BRIDGE_MESSAGES.locationMaxLength,
    )
  })

  test('name/location is ALSO capped at 30 UTF-8 bytes, as the column is (VARCHAR2(30 BYTE))', () => {
    // The DTO carries @Size(max = 30) AND @MaxByteLength(30, charMax = 30). This is the sharper of
    // the two byte caps on this form: BridgeFields sets maxLength={LOCATION_MAX_LENGTH}, so 15 CJK
    // characters are inside every limit the UI shows the reporter — and 45 bytes. Counting characters
    // alone let that through the gate and 400d on save with "must be 30 characters or fewer."
    const cjk = '橋'.repeat(15)
    expect(cjk.length).toBeLessThanOrEqual(30)
    expect(validateBridge(valid({ locationName: cjk })).locationName).toBe(
      BRIDGE_MESSAGES.locationMaxLength,
    )
    // 10 of the same character is exactly 30 bytes — the last length that fits.
    expect(validateBridge(valid({ locationName: '橋'.repeat(10) })).locationName).toBeUndefined()
    // ASCII is one byte per character, so the byte rule never fires before the character one.
    expect(validateBridge(valid({ locationName: 'x'.repeat(30) })).locationName).toBeUndefined()
  })

  test.each([
    'constructionTypeCode',
    'superstructureTypeCode',
    'deckTypeCode',
    'abutmentTypeCode',
    'loadRatingCode',
  ] as const)('unselected %s is required', (field) => {
    expect(validateBridge(valid({ [field]: '' }))[field]).toBe(BRIDGE_MESSAGES.valueRequired)
  })
})

describe('validateBridge — built date', () => {
  test('blank date is required', () => {
    expect(validateBridge(valid({ builtDate: '' })).builtDate).toBe(BRIDGE_MESSAGES.valueRequired)
  })

  test.each(['2020-13', '2020-00', '2020-6', '06-2020', '2020/06', '2020-06-01', 'not-a-date'])(
    'rejects %s with the legacy date-format message',
    (raw) => {
      expect(validateBridge(valid({ builtDate: raw })).builtDate).toBe(BRIDGE_MESSAGES.dateFormat)
    },
  )

  test.each(['2020-01', '2020-12', '1999-07'])('accepts %s', (raw) => {
    expect(validateBridge(valid({ builtDate: raw })).builtDate).toBeUndefined()
  })
})

describe('validateBridge — measurements', () => {
  test.each([
    ['lifeSpan', '1000', 'lifeSpanRange'],
    ['lifeSpan', '-1', 'lifeSpanRange'],
    ['abutmentHeight', '10000', 'abutmentHeightRange'],
    ['abutmentHeight', '5.55', 'abutmentHeightRange'],
    ['length', '10000', 'lengthRange'],
    ['length', '20.55', 'lengthRange'],
    ['width', '-0.1', 'widthRange'],
    ['distance', '10000', 'distanceRange'],
  ] as const)('%s = %s is out of range', (field, raw, messageKey) => {
    expect(validateBridge(valid({ [field]: raw }))[field]).toBe(BRIDGE_MESSAGES[messageKey])
  })

  test.each(['lifeSpan', 'abutmentHeight', 'length', 'width', 'distance'] as const)(
    'blank %s is required',
    (field) => {
      expect(validateBridge(valid({ [field]: '' }))[field]).toBe(BRIDGE_MESSAGES.valueRequired)
    },
  )

  // The legacy Add-form emitted a bare resource key for non-numeric height/length; catching it here
  // is what replaces that bug with real text.
  test.each(['abutmentHeight', 'length'] as const)(
    'non-numeric %s reports the range message rather than a bare key',
    (field) => {
      const message = validateBridge(valid({ [field]: 'abc' }))[field]
      expect(message).toBeDefined()
      expect(message).not.toMatch(/^[a-z][A-Za-z]*Msg$/)
    },
  )

  test('distance enforces 0-9,999 while displaying the legacy 0.0-999.99 text', () => {
    expect(validateBridge(valid({ distance: '9999' })).distance).toBeUndefined()
    expect(validateBridge(valid({ distance: '10000' })).distance).toBe(
      'Entered bridge distance must be between 0.0 and 999.99',
    )
  })
})

describe('validateBridge — costs and comments', () => {
  test('every cost is optional', () => {
    expect(validateBridge(valid())).toEqual({})
  })

  test.each([
    'sitePlanCost',
    'superstructureMaterialCost',
    'superstructureDeliverCost',
    'superstructureInstallCost',
    'abutmentMaterialCost',
    'abutmentDeliverCost',
    'abutmentInstallCost',
    'approachCost',
    'afterInstallCost',
    'otherCost',
  ] as const)('%s out of range is rejected', (field) => {
    expect(validateBridge(valid({ [field]: '100000000' }))[field]).toBe(BRIDGE_MESSAGES.costRange)
    expect(validateBridge(valid({ [field]: '-100000000' }))[field]).toBe(BRIDGE_MESSAGES.costRange)
  })

  test('an unparseable cost reports the converter message', () => {
    expect(validateBridge(valid({ otherCost: 'abc' })).otherCost).toBe(BRIDGE_MESSAGES.costInvalid)
  })

  test('a cost is range-checked on the rounded value the integer wire carries', () => {
    expect(validateBridge(valid({ otherCost: '99999999.4' })).otherCost).toBeUndefined()
    expect(validateBridge(valid({ otherCost: '99999999.5' })).otherCost).toBe(
      BRIDGE_MESSAGES.costRange,
    )
  })

  test('comments over 3500 characters are rejected', () => {
    expect(validateBridge(valid({ comments: 'x'.repeat(3501) })).comments).toBe(
      BRIDGE_MESSAGES.commentsMaxLength,
    )
  })

  test('comments are measured trimmed, matching what is sent', () => {
    expect(validateBridge(valid({ comments: `  ${'x'.repeat(3500)}  ` })).comments).toBeUndefined()
  })

  test('comments are ALSO capped at 4,000 UTF-8 bytes, as the column is (VARCHAR2(4000 BYTE))', () => {
    // 1,400 CJK characters are well under the 3,500-character cap and 4,200 bytes over the column's.
    const cjk = '柱'.repeat(1400)
    expect(cjk.length).toBeLessThan(3500)
    expect(validateBridge(valid({ comments: cjk })).comments).toBe(
      BRIDGE_MESSAGES.commentsMaxLength,
    )
    // 1,333 of the same character is 3,999 bytes — the last length that fits.
    expect(validateBridge(valid({ comments: '柱'.repeat(1333) })).comments).toBeUndefined()
  })
})

// Each pair pins the accepting side as well as the rejecting side. Without the accepting half, any
// of these bounds could be silently narrowed and the suite would stay green while the advisory gate
// drifted away from the backend DTO it is documented to mirror.
describe('validateBridge — range boundaries are pinned on both sides', () => {
  test.each([
    ['lifeSpan', '0', '999', '1000'],
    ['abutmentHeight', '0.0', '9999.9', '10000.0'],
    ['length', '0.0', '9999.9', '10000.0'],
    ['width', '0.0', '9999.9', '10000.0'],
    ['distance', '0', '9999', '10000'],
  ] as const)('%s accepts %s and %s but rejects %s', (field, min, max, over) => {
    expect(validateBridge(valid({ [field]: min }))[field]).toBeUndefined()
    expect(validateBridge(valid({ [field]: max }))[field]).toBeUndefined()
    expect(validateBridge(valid({ [field]: over }))[field]).toBeDefined()
  })

  test('costs accept both signed extremes and reject one beyond', () => {
    expect(validateBridge(valid({ otherCost: '99999999' })).otherCost).toBeUndefined()
    expect(validateBridge(valid({ otherCost: '-99999999' })).otherCost).toBeUndefined()
    expect(validateBridge(valid({ otherCost: '100000000' })).otherCost).toBe(
      BRIDGE_MESSAGES.costRange,
    )
    expect(validateBridge(valid({ otherCost: '-100000000' })).otherCost).toBe(
      BRIDGE_MESSAGES.costRange,
    )
  })

  test('locationName accepts exactly 30 characters and rejects 31', () => {
    expect(validateBridge(valid({ locationName: 'x'.repeat(30) })).locationName).toBeUndefined()
    expect(validateBridge(valid({ locationName: 'x'.repeat(31) })).locationName).toBe(
      BRIDGE_MESSAGES.locationMaxLength,
    )
  })

  test('comments accept exactly 3500 characters', () => {
    expect(validateBridge(valid({ comments: 'x'.repeat(3500) })).comments).toBeUndefined()
  })

  test('every required field is reported at once, not just the first', () => {
    const errors = validateBridge(emptyBridgeForm())
    expect(Object.keys(errors)).toHaveLength(12)
  })
})
