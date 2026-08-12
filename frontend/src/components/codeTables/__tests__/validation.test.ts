import { validateCodeEntry } from '../validation'

const form = (over: Partial<Parameters<typeof validateCodeEntry>[0]> = {}) => ({
  code: 'X',
  description: 'd',
  effectiveDate: '2020-01-01',
  expiryDate: '',
  ...over,
})

describe('validateCodeEntry', () => {
  test('a complete entry with no expiry is valid (expiry optional)', () => {
    expect(validateCodeEntry(form())).toEqual({})
  })

  test('code, description, and effective date are required', () => {
    expect(validateCodeEntry(form({ code: ' ', description: '', effectiveDate: '' }))).toEqual({
      code: 'Code: Value is required.',
      description: 'Description: Value is required.',
      effectiveDate: 'Effective Date: Value is required.',
    })
  })

  test('requireCode=false skips the blank-code rule (inline edit of a fixed code)', () => {
    expect(validateCodeEntry(form({ code: '' }), false)).toEqual({})
  })

  test('expiry before effective is rejected', () => {
    expect(
      validateCodeEntry(form({ effectiveDate: '2030-01-01', expiryDate: '2020-01-01' })).expiryDate,
    ).toBe('Expiry Date must be greater than or equal to Effective Date.')
  })
})
