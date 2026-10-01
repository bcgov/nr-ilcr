import { describe, expect, test } from 'vitest'
import {
  legacyBannerLines,
  legacyRequiredMessage,
  legacyRowLabel,
} from '@/utils/legacyValidationBanner'

type Field = 'a' | 'b' | 'c' | 'd'
const ORDER: readonly Field[] = ['a', 'b', 'c', 'd']
const REQUIRED = 'Value Required'

describe('legacyBannerLines (#359 group B)', () => {
  test('JSF required text is `{label}: Value is required.` (validation.properties:11)', () => {
    expect(legacyRequiredMessage('Location Name')).toBe('Location Name: Value is required.')
    expect(legacyRowLabel(3, 'Type')).toBe('Id: 3 - Type')
  })

  test('lines follow the FIELD order, not the error map order; unlisted fields are never reported', () => {
    const errors = { d: 'range d', b: REQUIRED, a: 'range a', x: 'ignored' } as Partial<
      Record<Field, string>
    >
    expect(
      legacyBannerLines(errors, ORDER, REQUIRED, (f) => (f === 'b' ? 'Bee' : undefined)),
    ).toEqual(['range a', 'Bee: Value is required.', 'range d'])
  })

  test('a required failure with no known label falls back to its inline text, never invented wording', () => {
    expect(legacyBannerLines({ c: REQUIRED }, ORDER, REQUIRED, () => undefined)).toEqual([REQUIRED])
  })

  test('the row prefix applies to the listed fields NON-required lines only', () => {
    const errors: Partial<Record<Field, string>> = { a: 'range a', b: REQUIRED, c: 'range c' }
    expect(
      legacyBannerLines(
        errors,
        ORDER,
        REQUIRED,
        (f) => (f === 'b' ? legacyRowLabel(2, 'Bee') : undefined),
        { rowNumber: 2, fields: new Set<Field>(['a', 'b']) },
      ),
    ).toEqual(['Id: 2 - range a', 'Id: 2 - Bee: Value is required.', 'range c'])
  })
})
