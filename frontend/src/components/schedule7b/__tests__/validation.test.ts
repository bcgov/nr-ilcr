import { describe, expect, test } from 'vitest'
import {
  CULVERT_MESSAGES,
  emptyCulvertForm,
  validateCulvert,
} from '@/components/schedule7b/validation'
import type { CulvertFormValues } from '@/components/schedule7b/validation'

// A complete, valid culvert. Only Type and No of Pieces are required at Save, so this is deliberately
// richer than the minimum — each test blanks or breaks exactly one field.
const valid = (overrides: Partial<CulvertFormValues> = {}): CulvertFormValues => ({
  ...emptyCulvertForm(),
  culvertTypeCode: 'R',
  spanSize: '1200',
  riseSize: '900',
  length: '12.5',
  culvertPieceCount: '3',
  materialCost: '4,000',
  installCost: '1,500',
  comments: 'Main haul road',
  ...overrides,
})

describe('validateCulvert', () => {
  test('a complete culvert has no errors', () => {
    expect(validateCulvert(valid())).toEqual({})
  })

  test('ONLY Type and No of Pieces are required — everything else may be blank (BR-07)', () => {
    // The whole point of this schedule's save rules: a reporter must be able to store a
    // partially-measured culvert and come back to it. Check Status, not Save, flags the gaps.
    expect(validateCulvert(valid({ culvertTypeCode: '', culvertPieceCount: '' }))).toEqual({
      culvertTypeCode: CULVERT_MESSAGES.valueRequired,
      culvertPieceCount: CULVERT_MESSAGES.valueRequired,
    })
    expect(
      validateCulvert({
        ...emptyCulvertForm(),
        culvertTypeCode: 'O',
        culvertPieceCount: '1',
      }),
    ).toEqual({})
  })

  test.each([
    ['spanSize', '9999999', undefined],
    ['spanSize', '10000000', CULVERT_MESSAGES.spanRange],
    ['spanSize', '-1', CULVERT_MESSAGES.spanRange],
    ['spanSize', 'abc', CULVERT_MESSAGES.spanInvalid],
    ['riseSize', '0', undefined],
    ['riseSize', '10000000', CULVERT_MESSAGES.riseRange],
    ['riseSize', 'x', CULVERT_MESSAGES.riseInvalid],
  ] as const)('%s = %s', (field, value, expected) => {
    expect(validateCulvert(valid({ [field]: value }))[field]).toBe(expected)
  })

  test('length is a RANGE check only — extra decimals are the server’s to round', () => {
    // Read through a helper rather than asserting on `.length` inline: the field IS named `length`, and
    // an assertion shaped `expect(x.length).toBe(…)` reads to static analysis as a miscounted array.
    const lengthError = (value: string) => validateCulvert(valid({ length: value })).length

    expect(lengthError('999999.9')).toBeUndefined()
    // Two decimals must PASS: the backend deliberately carries no @Digits and normalizes the scale on
    // write, because a client formatting to two places could otherwise never save a culvert.
    expect(lengthError('12.50')).toBeUndefined()
    expect(lengthError('1000000')).toBe(CULVERT_MESSAGES.lengthRange)
    expect(lengthError('-0.1')).toBe(CULVERT_MESSAGES.lengthRange)
    // No length-specific converter key exists on the backend, so unparseable text reports the range
    // line (the Schedule 7A precedent) rather than the volume wording the type fallback would give.
    expect(lengthError('abc')).toBe(CULVERT_MESSAGES.lengthRange)
  })

  test.each([
    ['1', undefined],
    ['9999', undefined],
    ['0', CULVERT_MESSAGES.pieceCountRange],
    ['10000', CULVERT_MESSAGES.pieceCountRange],
    ['two', CULVERT_MESSAGES.pieceCountInvalid],
  ])('piece count %s (BR-04: 1-9,999)', (value, expected) => {
    expect(validateCulvert(valid({ culvertPieceCount: value })).culvertPieceCount).toBe(expected)
  })

  test('the range is checked on the value AS PARSED, the way legacy validated it', () => {
    // Legacy's `f:validateDoubleRange` saw the parsed value, not the masked one, so 0.6 pieces failed
    // `0.6 < one.minValue`. Range-checking only the ROUNDED value let it through as 1 — the defect the
    // 2026-08-12 review caught.
    expect(validateCulvert(valid({ culvertPieceCount: '0.6' })).culvertPieceCount).toBe(
      CULVERT_MESSAGES.pieceCountRange,
    )
    expect(validateCulvert(valid({ spanSize: '-0.4' })).spanSize).toBe(CULVERT_MESSAGES.spanRange)
    // A fraction INSIDE the range still passes and is rounded for the wire, as legacy stored it.
    expect(validateCulvert(valid({ culvertPieceCount: '1.4' })).culvertPieceCount).toBeUndefined()
    // And the rounded value is checked too, so a span that only leaves the range once rounded is
    // caught here rather than by the server.
    expect(validateCulvert(valid({ spanSize: '9999999.5' })).spanSize).toBe(
      CULVERT_MESSAGES.spanRange,
    )
  })

  test('costs accept grouped entry and reject the band and unparseable text', () => {
    expect(validateCulvert(valid({ materialCost: '99,999,999' })).materialCost).toBeUndefined()
    expect(validateCulvert(valid({ materialCost: '-99,999,999' })).materialCost).toBeUndefined()
    expect(validateCulvert(valid({ installCost: '100,000,000' })).installCost).toBe(
      CULVERT_MESSAGES.costRange,
    )
    expect(validateCulvert(valid({ installCost: '12abc' })).installCost).toBe(
      CULVERT_MESSAGES.costInvalid,
    )
    // Checked on the ROUNDED value, which is what the Integer wire carries — otherwise this passes the
    // client gate and the server rejects 100,000,000.
    expect(validateCulvert(valid({ materialCost: '99999999.5' })).materialCost).toBe(
      CULVERT_MESSAGES.costRange,
    )
  })

  test('an inline-edit row prefixes Id: {rowCounter} - onto the COST messages only (S23)', () => {
    const errors = validateCulvert(
      valid({ materialCost: 'abc', culvertPieceCount: '', spanSize: 'nope' }),
      4,
    )
    // Legacy carried the prefix through validatorMessage/converterMessage on the list-row cost fields
    // alone (schedule7B.xhtml:434-435,453-454) — never on the other inputs, and never on the Add form.
    expect(errors.materialCost).toBe(`Id: 4 - ${CULVERT_MESSAGES.costInvalid}`)
    expect(errors.culvertPieceCount).toBe(CULVERT_MESSAGES.valueRequired)
    expect(errors.spanSize).toBe(CULVERT_MESSAGES.spanInvalid)
    // The Add form gets the same message unprefixed.
    expect(validateCulvert(valid({ materialCost: 'abc' })).materialCost).toBe(
      CULVERT_MESSAGES.costInvalid,
    )
  })

  test('comments are capped at 3,500 characters, measured on the trimmed value', () => {
    expect(validateCulvert(valid({ comments: 'x'.repeat(3500) })).comments).toBeUndefined()
    expect(validateCulvert(valid({ comments: 'x'.repeat(3501) })).comments).toBe(
      CULVERT_MESSAGES.commentsMaxLength,
    )
    // The request body trims, so surrounding whitespace must not push a legal comment over.
    expect(validateCulvert(valid({ comments: ` ${'x'.repeat(3500)} ` })).comments).toBeUndefined()
  })

  test('comments are ALSO capped at 4,000 UTF-8 bytes, as the column is (VARCHAR2(4000 BYTE))', () => {
    // The DTO carries @Size(max = 3500) AND @MaxByteLength(4000, charMax = 3500). Multibyte text is
    // where the two part company: 1,400 CJK characters are well under the character cap and 4,200
    // bytes over the column's. Counting characters alone let this through the gate and 400d on save.
    const cjk = '柱'.repeat(1400)
    expect(cjk.length).toBeLessThan(3500)
    expect(validateCulvert(valid({ comments: cjk })).comments).toBe(
      CULVERT_MESSAGES.commentsMaxLength,
    )
    // 1,333 of the same character is 3,999 bytes — the last length that fits.
    expect(validateCulvert(valid({ comments: '柱'.repeat(1333) })).comments).toBeUndefined()
    // Plain ASCII is one byte per character, so the byte rule never fires before the character one:
    // the two bounds must not interact for the text a reporter usually types.
    expect(validateCulvert(valid({ comments: 'x'.repeat(3500) })).comments).toBeUndefined()
  })

  test('every failing field is reported in one pass (S25)', () => {
    const errors = validateCulvert(
      valid({
        culvertTypeCode: '',
        spanSize: '10000000',
        length: '1000000',
        culvertPieceCount: '0',
        materialCost: 'abc',
        installCost: '100000000',
      }),
    )
    // Short-circuiting after the first failure would leave the reporter fixing one field per submit.
    expect(Object.keys(errors).sort()).toEqual([
      'culvertPieceCount',
      'culvertTypeCode',
      'installCost',
      'length',
      'materialCost',
      'spanSize',
    ])
  })
})
