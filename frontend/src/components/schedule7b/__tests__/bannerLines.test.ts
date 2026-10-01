import { describe, expect, test } from 'vitest'
import {
  CULVERT_MESSAGES,
  culvertBannerLines,
  emptyCulvertForm,
  validateCulvert,
} from '@/components/schedule7b/validation'

describe('culvertBannerLines — every legacy row label (schedule7B.xhtml:308,398)', () => {
  test('a blank row reports Type then No of pieces, verbatim', () => {
    expect(culvertBannerLines(validateCulvert(emptyCulvertForm(), 4), 4)).toEqual([
      'Id: 4 - Type: Value is required.',
      'Id: 4 - No of pieces: Value is required.',
    ])
  })

  test('row 2: the two costs carry their row prefix once (it lives on the inline text), other ranges none', () => {
    const form = {
      ...emptyCulvertForm(),
      culvertTypeCode: 'R',
      culvertPieceCount: '2',
      spanSize: '10000000',
      materialCost: '100000000',
      installCost: '100000000',
    }
    expect(culvertBannerLines(validateCulvert(form, 2), 2)).toEqual([
      CULVERT_MESSAGES.spanRange,
      `Id: 2 - ${CULVERT_MESSAGES.costRange}`,
      `Id: 2 - ${CULVERT_MESSAGES.costRange}`,
    ])
  })
})
