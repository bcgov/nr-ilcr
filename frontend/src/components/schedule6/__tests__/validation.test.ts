import { describe, expect, it } from 'vitest'
import {
  GENERAL_COMMENTS_MAX_LENGTH,
  RECORD_COMMENTS_MAX_LENGTH,
  ROAD_MESSAGES,
  TFL_AREA_TYPE,
  TFL_MAX_LENGTH,
  areaTypeOptions,
  parseDecimalInput,
  validateGeneralComments,
  validateRoadRecord,
} from '../validation'
import type { RoadRecordFormValues } from '../validation'

// A fully-valid TSA record; each test overrides the one field under test so validateRoadRecord
// returns only that field's error (or {}).
const validForm = (overrides: Partial<RoadRecordFormValues> = {}): RoadRecordFormValues => ({
  areaType: '01',
  tflNumber: '',
  supplyBlock: '01B',
  volume: '1000',
  cost: '50000',
  comments: '',
  ...overrides,
})

// The caps are exported so index.tsx binds the SAME numbers to maxLength/maxCount that this module
// validates against. The 400-vs-3500 split is the Story 8.2 review's headline finding: the
// per-record comment lands in a 400-wide column, the general comment in a 4000-wide one.
describe('exported caps mirror the 8.2 DTO columns', () => {
  it('caps the per-record comment at 400 and the general comment at 3500', () => {
    expect(RECORD_COMMENTS_MAX_LENGTH).toBe(400)
    expect(GENERAL_COMMENTS_MAX_LENGTH).toBe(3500)
  })

  // AREA_TYPE_MAX_LENGTH / SUPPLY_BLOCK_MAX_LENGTH were retired with the TextInputs they bound
  // maxLength to (2026-08-21 corrections): both fields are now CodeComboBoxes, and the validators
  // that still gate width (validateAreaType, the supplyBlock check) reference their own internal
  // constants rather than these exports. TFL stays a TextInput, so its cap is still exported.
  it('caps the TFL field at its delivery column width', () => {
    expect(TFL_MAX_LENGTH).toBe(2)
  })
})

// Legacy's numeric fields (volume, cost) bound through a US-locale DecimalFormat, NOT JS Number().
// These lock the two ways Number() diverges from that format: grouped input the legacy app accepts,
// and JS-only syntax (exponent, hex, Infinity) Number() silently coerces. The parser is deliberately
// STRICTER than legacy on malformed input — DecimalFormat.parse had no consumed-length check, so
// legacy silently mangled '12,34'->1234, '1e2'->1, '1000abc'->1000; this gate rejects those outright
// (recorded deviation (L), Story 8.3).
describe('parseDecimalInput (legacy DecimalFormat display format, strict full-string)', () => {
  it('accepts comma-grouped values Number() would reject as NaN', () => {
    expect(parseDecimalInput('1,000')).toBe(1000)
    expect(parseDecimalInput('1,000,000')).toBe(1000000)
    expect(parseDecimalInput('10,000.5')).toBe(10000.5)
    expect(parseDecimalInput('-99,999,999')).toBe(-99999999)
  })

  it('accepts ordinary plain and decimal values', () => {
    expect(parseDecimalInput('1000')).toBe(1000)
    expect(parseDecimalInput('1000.50')).toBe(1000.5)
    expect(parseDecimalInput('0')).toBe(0)
    expect(parseDecimalInput('-500')).toBe(-500)
    expect(parseDecimalInput('  42  ')).toBe(42)
  })

  it('returns null for blank input', () => {
    expect(parseDecimalInput('')).toBeNull()
    expect(parseDecimalInput('   ')).toBeNull()
  })

  // Legacy's lenient DecimalFormat.parse coerced '1e2' to 1 (prefix parse) where Number() coerces
  // it to 100 — rejecting outright is the deviation-(L) strictness, safer than either coercion.
  it('rejects JS-only numeric syntax that Number() silently coerces', () => {
    expect(parseDecimalInput('1e2')).toBeNull() // Number('1e2') === 100; legacy parsed the '1' prefix
    expect(parseDecimalInput('0x10')).toBeNull() // Number('0x10') === 16
    expect(parseDecimalInput('Infinity')).toBeNull()
    expect(parseDecimalInput('-Infinity')).toBeNull()
    expect(parseDecimalInput('0b101')).toBeNull()
    expect(parseDecimalInput('1_000')).toBeNull()
  })

  // Legacy ACCEPTED most of these by silently mangling them ('12,34'->1234, '1.2.3'->1.2,
  // '1000abc'->1000); rejecting is deliberately stricter (deviation (L)).
  it('rejects non-numeric and malformed input legacy would have silently mangled', () => {
    expect(parseDecimalInput('abc')).toBeNull()
    expect(parseDecimalInput('1.2.3')).toBeNull()
    expect(parseDecimalInput('12,34')).toBeNull() // grouping must be 3-digit groups
    expect(parseDecimalInput('1,00')).toBeNull()
    expect(parseDecimalInput('+100')).toBeNull() // DecimalFormat has no positive prefix
  })
})

describe('validateRoadRecord — area type (FLD-001, BR-02)', () => {
  it('accepts a valid TSA record', () => {
    expect(validateRoadRecord(validForm())).toEqual({})
  })

  it('requires an area type', () => {
    expect(validateRoadRecord(validForm({ areaType: '' })).areaType).toBe(
      ROAD_MESSAGES.areaTypeRequired,
    )
    expect(validateRoadRecord(validForm({ areaType: '   ' })).areaType).toBe(
      ROAD_MESSAGES.areaTypeRequired,
    )
  })

  it('rejects an area type wider than the 3-char cap', () => {
    expect(validateRoadRecord(validForm({ areaType: 'TFLX' })).areaType).toBe(
      ROAD_MESSAGES.invalidCodeValue,
    )
  })

  // The server caps areaType at 3 for the literal "TFL" but additionally enforces ≤2 on the TSA
  // branch (TSA_NUMBER VARCHAR2(2)) — a 3-char TSA code reaches Oracle as ORA-12899 otherwise.
  it('rejects a 3-char code that is not the literal TFL', () => {
    expect(validateRoadRecord(validForm({ areaType: '01B' })).areaType).toBe(
      ROAD_MESSAGES.invalidCodeValue,
    )
  })

  it('accepts the literal TFL at 3 chars', () => {
    expect(
      validateRoadRecord(validForm({ areaType: TFL_AREA_TYPE, tflNumber: '01', supplyBlock: '' }))
        .areaType,
    ).toBeUndefined()
  })
})

describe('validateRoadRecord — TFL number required iff TFL (BR-03)', () => {
  it('requires the TFL number on a TFL record', () => {
    expect(
      validateRoadRecord(validForm({ areaType: TFL_AREA_TYPE, tflNumber: '', supplyBlock: '' }))
        .tflNumber,
    ).toBe(ROAD_MESSAGES.tflNumberInvalid)
  })

  it('accepts a TFL record carrying its TFL number', () => {
    expect(
      validateRoadRecord(validForm({ areaType: TFL_AREA_TYPE, tflNumber: '1', supplyBlock: '' })),
    ).toEqual({})
  })

  it('rejects an over-long TFL number', () => {
    expect(
      validateRoadRecord(validForm({ areaType: TFL_AREA_TYPE, tflNumber: '123', supplyBlock: '' }))
        .tflNumber,
    ).toBe(ROAD_MESSAGES.tflNumberInvalid)
  })

  // A TSA record needs no TFL number — the counterpart is cleared, not validated.
  it('does not require a TFL number on a TSA record', () => {
    expect(validateRoadRecord(validForm({ tflNumber: '' })).tflNumber).toBeUndefined()
  })
})

describe('validateRoadRecord — supply block', () => {
  // A missing Supply Block is a Check Status finding (S09), never a save failure.
  it('accepts a missing supply block', () => {
    expect(validateRoadRecord(validForm({ supplyBlock: '' })).supplyBlock).toBeUndefined()
  })

  it('rejects a supply block wider than the column', () => {
    expect(validateRoadRecord(validForm({ supplyBlock: '01BX' })).supplyBlock).toBe(
      ROAD_MESSAGES.invalidCodeValue,
    )
  })
})

describe('validateRoadRecord — volume (FLD-003)', () => {
  it('accepts a blank volume', () => {
    expect(validateRoadRecord(validForm({ volume: '' })).volume).toBeUndefined()
  })

  it('accepts the range bounds and comma-grouped input', () => {
    expect(validateRoadRecord(validForm({ volume: '0' })).volume).toBeUndefined()
    expect(validateRoadRecord(validForm({ volume: '9,999,999' })).volume).toBeUndefined()
    expect(validateRoadRecord(validForm({ volume: '1,000.25' })).volume).toBeUndefined()
  })

  it('rejects an out-of-range volume with the range message', () => {
    expect(validateRoadRecord(validForm({ volume: '10000000' })).volume).toBe(
      ROAD_MESSAGES.volumeRange,
    )
    expect(validateRoadRecord(validForm({ volume: '-1' })).volume).toBe(ROAD_MESSAGES.volumeRange)
  })

  // @Digits(fraction = 2) resolves the SAME volumeValidatorErrorMsg key as the range bound.
  it('rejects more than two decimal places with the range message', () => {
    expect(validateRoadRecord(validForm({ volume: '1000.123' })).volume).toBe(
      ROAD_MESSAGES.volumeRange,
    )
  })

  it('rejects unparseable volume with the converter message', () => {
    expect(validateRoadRecord(validForm({ volume: 'abc' })).volume).toBe(
      ROAD_MESSAGES.volumeInvalid,
    )
    expect(validateRoadRecord(validForm({ volume: '1e2' })).volume).toBe(
      ROAD_MESSAGES.volumeInvalid,
    )
  })
})

describe('validateRoadRecord — cost (FLD-004)', () => {
  it('accepts a blank cost', () => {
    expect(validateRoadRecord(validForm({ cost: '' })).cost).toBeUndefined()
  })

  it('accepts the signed range bounds and comma-grouped input', () => {
    expect(validateRoadRecord(validForm({ cost: '-99,999,999' })).cost).toBeUndefined()
    expect(validateRoadRecord(validForm({ cost: '99999999' })).cost).toBeUndefined()
  })

  it('rejects an out-of-range cost with the range message', () => {
    expect(validateRoadRecord(validForm({ cost: '100000000' })).cost).toBe(ROAD_MESSAGES.costRange)
    expect(validateRoadRecord(validForm({ cost: '-100000000' })).cost).toBe(ROAD_MESSAGES.costRange)
  })

  // Fractional costs are ROUNDED before send (the Integer wire would otherwise truncate), so the
  // range must be checked against the rounded value the server will actually receive.
  it('range-checks the rounded value, not the raw entry', () => {
    expect(validateRoadRecord(validForm({ cost: '99999999.4' })).cost).toBeUndefined()
    expect(validateRoadRecord(validForm({ cost: '99999999.5' })).cost).toBe(ROAD_MESSAGES.costRange)
  })

  it('rejects unparseable cost with the converter message', () => {
    expect(validateRoadRecord(validForm({ cost: 'abc' })).cost).toBe(ROAD_MESSAGES.costInvalid)
    expect(validateRoadRecord(validForm({ cost: '0x10' })).cost).toBe(ROAD_MESSAGES.costInvalid)
  })
})

describe('comments boundaries — 400 per record, 3500 general', () => {
  it('accepts exactly 400 per-record characters and rejects 401', () => {
    expect(validateRoadRecord(validForm({ comments: 'x'.repeat(400) })).comments).toBeUndefined()
    expect(validateRoadRecord(validForm({ comments: 'x'.repeat(401) })).comments).toBe(
      ROAD_MESSAGES.recordCommentsMaxLength,
    )
  })

  // The 3500 that legacy's per-record textarea allowed is the GENERAL comment's cap, and only its.
  it('rejects a 3500-character per-record comment (legacy would have too, at the column)', () => {
    expect(validateRoadRecord(validForm({ comments: 'x'.repeat(3500) })).comments).toBe(
      ROAD_MESSAGES.recordCommentsMaxLength,
    )
  })

  it('accepts exactly 3500 general-comment characters and rejects 3501', () => {
    expect(validateGeneralComments('x'.repeat(3500))).toBeUndefined()
    expect(validateGeneralComments('x'.repeat(3501))).toBe(ROAD_MESSAGES.generalCommentsMaxLength)
  })

  it('accepts a blank general comment (blank clears, BR-09)', () => {
    expect(validateGeneralComments('')).toBeUndefined()
  })
})

// Final-review I3: a stored areaType with no TSA_NUMBER_CODE row at all (deviation (f) still lets
// the write path store one) must still display, mirroring supplyBlocksFor's stored-code synthesis
// (utils/codes.ts:47-55).
describe('areaTypeOptions', () => {
  const tsaNumbers = [
    { code: '01', description: 'Arrowsmith TSA' },
    { code: '02', description: 'Boundary TSA' },
  ]

  it('puts the TFL sentinel first, ahead of the served TSA numbers', () => {
    expect(areaTypeOptions(tsaNumbers).map((o) => o.code)).toEqual(['TFL', '01', '02'])
  })

  it('a stored code absent from the served list is still offered, over itself', () => {
    expect(areaTypeOptions(tsaNumbers, '09')).toEqual([
      { code: 'TFL', description: 'TFL' },
      { code: '01', description: 'Arrowsmith TSA' },
      { code: '02', description: 'Boundary TSA' },
      { code: '09', description: '09' },
    ])
  })

  it('does not duplicate a stored code already on the list', () => {
    expect(areaTypeOptions(tsaNumbers, '01').map((o) => o.code)).toEqual(['TFL', '01', '02'])
  })

  it('does not synthesise an entry for the TFL sentinel itself', () => {
    expect(areaTypeOptions(tsaNumbers, 'TFL').map((o) => o.code)).toEqual(['TFL', '01', '02'])
  })

  it('does not synthesise an entry when nothing is selected', () => {
    expect(areaTypeOptions(tsaNumbers, '').map((o) => o.code)).toEqual(['TFL', '01', '02'])
  })
})
