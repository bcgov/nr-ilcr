import { describe, expect, test } from 'vitest'
import type { ContractualWorkRecord } from '@/interfaces/Schedule9Response'
import {
  RECORD_MESSAGES,
  buildBody,
  emptyRecordForm,
  formFromRecord,
  previewCostPerUnit,
  validateRecord,
} from '../validation'

const valid = () => ({
  ...emptyRecordForm(),
  contractorId: 'CTR-1',
  contractualItemCode: '108',
  unitCode: 'M3',
  biogeoclimaticZone: 'BZ1',
  sourceCode: 'A',
})

describe('validateRecord — required (FLD-001)', () => {
  test('flags the five required selects when all blank, and nothing else', () => {
    const errors = validateRecord(emptyRecordForm())
    expect(errors.contractorId).toBe(RECORD_MESSAGES.valueRequired)
    expect(errors.contractualItemCode).toBe(RECORD_MESSAGES.valueRequired)
    expect(errors.unitCode).toBe(RECORD_MESSAGES.valueRequired)
    expect(errors.biogeoclimaticZone).toBe(RECORD_MESSAGES.valueRequired)
    expect(errors.sourceCode).toBe(RECORD_MESSAGES.valueRequired)
    // Units, cost, side slope and the descriptions are NOT required.
    expect(errors.numberOfUnits).toBeUndefined()
    expect(errors.cost).toBeUndefined()
    expect(errors.itemDescription).toBeUndefined()
  })

  test('a fully valid record has no errors', () => {
    expect(validateRecord(valid())).toEqual({})
  })

  test('"Other" descriptions are NOT required even when enabled (9.2 parity)', () => {
    const item114 = { ...valid(), contractualItemCode: '114', itemDescription: '' }
    expect(validateRecord(item114).itemDescription).toBeUndefined()
    const unitO = { ...valid(), unitCode: 'O', unitDescription: '' }
    expect(validateRecord(unitO).unitDescription).toBeUndefined()
    const sourceS = { ...valid(), sourceCode: 'S', sourceDescription: '' }
    expect(validateRecord(sourceS).sourceDescription).toBeUndefined()
  })
})

describe('validateRecord — ranges (FLD-002/003/004)', () => {
  test('cost over 9,999,999 is out of range; the boundary accepts', () => {
    expect(validateRecord({ ...valid(), cost: '10000000' }).cost).toBe(RECORD_MESSAGES.costRange)
    expect(validateRecord({ ...valid(), cost: '9999999' }).cost).toBeUndefined()
  })

  test('units over 99,999.9 is out of range; the boundary accepts', () => {
    expect(validateRecord({ ...valid(), numberOfUnits: '100000' }).numberOfUnits).toBe(
      RECORD_MESSAGES.unitsRange,
    )
    expect(validateRecord({ ...valid(), numberOfUnits: '99999.9' }).numberOfUnits).toBeUndefined()
  })

  test('side slope is range-checked ONLY when the item enables it (111/112)', () => {
    // item 108 disables side slope -> an out-of-range value is ignored (cleared, not sent).
    expect(
      validateRecord({ ...valid(), contractualItemCode: '108', sideSlopePct: '150' }).sideSlopePct,
    ).toBeUndefined()
    // item 111 enables it -> 150 is out of range, 100 accepts.
    expect(
      validateRecord({ ...valid(), contractualItemCode: '111', sideSlopePct: '150' }).sideSlopePct,
    ).toBe(RECORD_MESSAGES.sideSlopeRange)
    expect(
      validateRecord({ ...valid(), contractualItemCode: '111', sideSlopePct: '100' }).sideSlopePct,
    ).toBeUndefined()
  })

  test('comments over 2000 characters is rejected (not the legacy 3500)', () => {
    expect(validateRecord({ ...valid(), comments: 'x'.repeat(2001) }).comments).toBe(
      RECORD_MESSAGES.commentsMaxLength,
    )
    expect(validateRecord({ ...valid(), comments: 'x'.repeat(2000) }).comments).toBeUndefined()
  })
})

describe('buildBody — conditional-null + shape', () => {
  test('item 108 sends NULL item description and side slope regardless of the form', () => {
    const body = buildBody({
      ...valid(),
      contractualItemCode: '108',
      itemDescription: 'ignored',
      sideSlopePct: '55',
    })
    expect(body.contractualItemCode).toBe(108)
    expect(body.itemDescription).toBeNull()
    expect(body.sideSlopePct).toBeNull()
  })

  test('item 111 keeps side slope; item 114 keeps the item description', () => {
    expect(
      buildBody({ ...valid(), contractualItemCode: '111', sideSlopePct: '55' }).sideSlopePct,
    ).toBe(55)
    expect(
      buildBody({ ...valid(), contractualItemCode: '114', itemDescription: 'Custom gate' })
        .itemDescription,
    ).toBe('Custom gate')
  })

  test('unit O keeps its description; source S keeps its description', () => {
    expect(
      buildBody({ ...valid(), unitCode: 'O', unitDescription: 'linear metre' }).unitDescription,
    ).toBe('linear metre')
    expect(
      buildBody({ ...valid(), sourceCode: 'S', sourceDescription: 'quote' }).sourceDescription,
    ).toBe('quote')
  })

  test('blank optional fields send null; revisionCount omitted unless supplied', () => {
    const create = buildBody(valid())
    expect(create.cost).toBeNull()
    expect(create.numberOfUnits).toBeNull()
    expect(create.comments).toBeNull()
    expect('revisionCount' in create).toBe(false)
    expect(buildBody(valid(), 3).revisionCount).toBe(3)
  })
})

describe('previewCostPerUnit + formFromRecord', () => {
  test('$/Unit is cost ÷ units at scale 2, null when units are 0/blank', () => {
    expect(previewCostPerUnit({ ...valid(), cost: '5000', numberOfUnits: '12.5' })).toBe(400)
    expect(previewCostPerUnit({ ...valid(), cost: '5000', numberOfUnits: '0' })).toBeNull()
    expect(previewCostPerUnit({ ...valid(), cost: '5000', numberOfUnits: '' })).toBeNull()
    expect(previewCostPerUnit({ ...valid(), cost: '', numberOfUnits: '10' })).toBeNull()
  })

  test('formFromRecord seeds selects from codes and units through the one-decimal mask', () => {
    const record: ContractualWorkRecord = {
      id: 1,
      revisionCount: 0,
      contractorId: 'CTR-1',
      contractualItem: { code: '108', description: 'Cattleguard' },
      itemDescription: null,
      unitType: { code: 'M3', description: 'Cubic Metres' },
      unitDescription: null,
      numberOfUnits: 12.5,
      biogeoclimaticZone: { code: 'BZ1', description: 'BEC Zone One' },
      cost: 5000,
      costPerUnit: 400,
      sideSlopePct: null,
      source: { code: 'A', description: 'Actual Cost' },
      sourceDescription: null,
      comments: null,
    }
    const form = formFromRecord(record)
    expect(form.contractualItemCode).toBe('108')
    expect(form.unitCode).toBe('M3')
    expect(form.numberOfUnits).toBe('12.5')
    expect(form.itemDescription).toBe('')
  })
})
