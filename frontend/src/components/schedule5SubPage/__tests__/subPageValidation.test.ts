import { describe, expect, test } from 'vitest'
import type { SubPageRowForm } from '@/interfaces/Schedule5SubPage'
import {
  COST_BANDS,
  DESCRIPTION_MAX_LENGTH,
  SUB_PAGE_MESSAGES,
  VALIDATES_ROW_ON_CHANGE,
  isSubPageValid,
  rowFieldKey,
  toRowRequest,
  validateAddForm,
  validateRows,
} from '../validation'

/**
 * Advisory client-side validation for the Schedule 5 expense sub-pages (AC5, AC6, AC10).
 *
 * Every bound and every message here MIRRORS the backend, so an advisory rejection reads identically
 * to a server one. The expectations are written from the legacy source, not from a run.
 */

const row = (description: string, cost: string, rowId: number | null = null): SubPageRowForm => ({
  rowId,
  description,
  cost,
})

describe('the two cost bands (AC5) — per PAGE, not per control', () => {
  test('the Camp band is ±9,999,999 and the Access band is ±99,999,999', () => {
    // schedule5CampExpenses.xhtml:45 (add) and :79 (grid) BOTH carry costSize="7"; neither Access
    // input does (:36-38, :71-76). The committed AC and all three UC docs get this wrong.
    expect(COST_BANDS.CAMP).toMatchObject({ min: -9_999_999, max: 9_999_999 })
    expect(COST_BANDS.ACCESS).toMatchObject({ min: -99_999_999, max: 99_999_999 })
  })

  test('CAMP: the boundaries are accepted and one past them is rejected', () => {
    expect(validateAddForm(row('Max', '9999999'), 'CAMP')).toEqual({})
    expect(validateAddForm(row('Min', '-9999999'), 'CAMP')).toEqual({})
    expect(validateAddForm(row('Over', '10000000'), 'CAMP')).toEqual({
      cost: SUB_PAGE_MESSAGES.campCostRange,
    })
    expect(validateAddForm(row('Under', '-10000000'), 'CAMP')).toEqual({
      cost: SUB_PAGE_MESSAGES.campCostRange,
    })
  })

  test('ACCESS accepts the value CAMP rejects — the sharp case', () => {
    // If anyone ever collapses the two bands into one, this is the assertion that fails.
    expect(validateAddForm(row('Wide', '10000000'), 'ACCESS')).toEqual({})
    expect(validateAddForm(row('Max', '99999999'), 'ACCESS')).toEqual({})
    expect(validateAddForm(row('Over', '100000000'), 'ACCESS')).toEqual({
      cost: SUB_PAGE_MESSAGES.accessCostRange,
    })
  })

  test('a grouped entry is parsed, and junk is an invalid-cost message', () => {
    expect(validateAddForm(row('Grouped', '1,234,567'), 'CAMP')).toEqual({})
    expect(validateAddForm(row('Junk', '12abc'), 'CAMP')).toEqual({
      cost: SUB_PAGE_MESSAGES.costInvalid,
    })
  })

  test('the ROUNDED value is range-checked, not the raw one', () => {
    // 9,999,999.4 rounds DOWN to the accepted bound, so it must pass; .6 rounds UP past it and must
    // fail here rather than at the server. Checking the raw value would get both backwards.
    expect(validateAddForm(row('Rounds in', '9999999.4'), 'CAMP')).toEqual({})
    expect(validateAddForm(row('Rounds out', '9999999.6'), 'CAMP')).toEqual({
      cost: SUB_PAGE_MESSAGES.campCostRange,
    })
  })

  test('a blank cost is VALID — a null cost is storable, and Check Status is what flags it', () => {
    expect(validateAddForm(row('No cost', ''), 'CAMP')).toEqual({})
    expect(validateRows([row('No cost', '', 1)], 'CAMP', true)).toEqual({})
  })
})

describe('description (AC6) and the add form', () => {
  test('the cap is 30 characters, and 31 is rejected', () => {
    expect(DESCRIPTION_MAX_LENGTH).toBe(30)
    expect(validateAddForm(row('A'.repeat(30), '1'), 'CAMP')).toEqual({})
    expect(validateAddForm(row('A'.repeat(31), '1'), 'CAMP')).toEqual({
      description: SUB_PAGE_MESSAGES.descriptionMaxLength,
    })
  })

  test('BOTH add forms require a description — deviation (A)', () => {
    // Verified at source: schedule5CampExpenses.xhtml:39 and schedule5AccessExpenses.xhtml:32 both
    // carry required="true". The committed AC3 says the Camp one does not; it does.
    expect(validateAddForm(row('', '1'), 'CAMP')).toEqual({
      description: SUB_PAGE_MESSAGES.descriptionRequired,
    })
    expect(validateAddForm(row('', '1'), 'ACCESS')).toEqual({
      description: SUB_PAGE_MESSAGES.descriptionRequired,
    })
    expect(validateAddForm(row('   ', '1'), 'CAMP')).toEqual({
      description: SUB_PAGE_MESSAGES.descriptionRequired,
    })
  })
})

describe('the S21 vs S22 timing (AC10)', () => {
  test('only the ACCESS grid validates a cleared description on change', () => {
    // schedule5AccessExpenses.xhtml:63 carries <f:ajax event="change"> and
    // schedule5CampExpenses.xhtml:64-67 does not, so legacy genuinely defers the Camp check to Save.
    expect(VALIDATES_ROW_ON_CHANGE.ACCESS).toBe(true)
    expect(VALIDATES_ROW_ON_CHANGE.CAMP).toBe(false)
  })

  test('with required NOT enforced, a cleared grid description passes', () => {
    expect(validateRows([row('', '100', 1)], 'CAMP', false)).toEqual({})
  })

  test('with required enforced, the same row fails — and this is what Save does', () => {
    expect(validateRows([row('', '100', 1)], 'CAMP', true)).toEqual({
      [rowFieldKey(0, 'description')]: SUB_PAGE_MESSAGES.descriptionRequired,
    })
  })

  test('the cost band applies on either timing — only the REQUIRED check defers', () => {
    expect(validateRows([row('', '10000000', 1)], 'CAMP', false)).toEqual({
      [rowFieldKey(0, 'cost')]: SUB_PAGE_MESSAGES.campCostRange,
    })
  })

  test('errors are keyed per row, so two bad rows report separately', () => {
    const errors = validateRows([row('', '1', 1), row('ok', 'junk', 2)], 'ACCESS', true)
    expect(errors).toEqual({
      [rowFieldKey(0, 'description')]: SUB_PAGE_MESSAGES.descriptionRequired,
      [rowFieldKey(1, 'cost')]: SUB_PAGE_MESSAGES.costInvalid,
    })
    expect(isSubPageValid(errors)).toBe(false)
    expect(isSubPageValid({})).toBe(true)
  })
})

describe('what goes on the wire', () => {
  test('a blank description is sent as null, not ""', () => {
    // The client and server then agree on one representation of "absent" — Oracle stores an empty
    // string as NULL anyway, so sending "" would make the echoed document disagree with the post.
    expect(toRowRequest(row('', '100', 7))).toEqual({ rowId: 7, description: null, cost: 100 })
    expect(toRowRequest(row('   ', '100', 7)).description).toBeNull()
  })

  test('a description is sent VERBATIM, including a single space', () => {
    // A single space PASSES Check Status (isEmpty, not isBlank), so trimming it away here would
    // silently change a shipped server behaviour from the client side.
    expect(toRowRequest(row(' x ', '1', 7)).description).toBe(' x ')
  })

  test('the cost is rounded half-away-from-zero, matching Oracle', () => {
    expect(toRowRequest(row('a', '10.5', 1)).cost).toBe(11)
    expect(toRowRequest(row('a', '-10.5', 1)).cost).toBe(-11)
    expect(toRowRequest(row('a', '', 1)).cost).toBeNull()
  })

  test('a null rowId survives, because that is what makes the row an INSERT', () => {
    expect(toRowRequest(row('New', '1', null)).rowId).toBeNull()
  })
})
