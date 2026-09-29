import { describe, expect, test } from 'vitest'
import {
  RECORD_MESSAGES,
  emptyRecordForm,
  recordBannerLines,
  validateRecord,
} from '@/components/schedule9/validation'

describe('recordBannerLines — every legacy row label (schedule9.xhtml:434-661)', () => {
  test('a blank row reports all five required selects, verbatim and in screen order', () => {
    expect(recordBannerLines(validateRecord(emptyRecordForm()), 5)).toEqual([
      'Id: 5 - Company: Value is required.',
      'Id: 5 - Contractual Item: Value is required.',
      'Id: 5 - Unit type: Value is required.',
      'Id: 5 - Biogeoclimatic Zone: Value is required.',
      'Id: 5 - Source: Value is required.',
    ])
  })

  test('row 2: side slope, units and cost ranges carry legacy`s `Id: 2 - ` prefix; description caps do not', () => {
    const form = {
      ...emptyRecordForm(),
      contractorId: 'CTR',
      contractualItemCode: '111',
      unitCode: 'O',
      biogeoclimaticZone: 'BZ1',
      sourceCode: 'A',
      sideSlopePct: '101',
      numberOfUnits: '100000',
      cost: '10000000',
      unitDescription: 'u'.repeat(121),
    }
    expect(recordBannerLines(validateRecord(form), 2)).toEqual([
      `Id: 2 - ${RECORD_MESSAGES.sideSlopeRange}`,
      `Id: 2 - ${RECORD_MESSAGES.unitsRange}`,
      RECORD_MESSAGES.unitDescriptionMaxLength,
      `Id: 2 - ${RECORD_MESSAGES.costRange}`,
    ])
  })
})
