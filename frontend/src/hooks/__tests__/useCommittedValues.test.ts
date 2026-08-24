import { act, renderHook } from '@testing-library/react'
import { useCommittedValues } from '@/hooks/useCommittedValues'

// The hook had no test file before the 2026-08-21 code review: deleting its re-seed effect failed
// nothing, because after a Save the echo carries back the same entered values the snapshot already
// held. These cover the cases the effect and the commit gate actually exist for.

// Document identities must be STABLE across renders — the hook keys its re-seed on identity, so an
// inline object literal would look like a new document on every render.
const DOC_A = { id: 1 }
const DOC_B = { id: 2 }

describe('useCommittedValues', () => {
  test('seeds from the form it is given', () => {
    const { result } = renderHook(() => useCommittedValues({ 'cost-12': '50000' }, DOC_A))
    expect(result.current.committed).toEqual({ 'cost-12': '50000' })
  })

  test('does not advance until a field is committed', () => {
    const { result, rerender } = renderHook(({ form, data }) => useCommittedValues(form, data), {
      initialProps: { form: { 'cost-12': '50000' }, data: DOC_A },
    })
    // Typing changes `form` but not `committed`.
    rerender({ form: { 'cost-12': '60000' }, data: DOC_A })
    expect(result.current.committed).toEqual({ 'cost-12': '50000' })

    act(() => result.current.commit('cost-12'))
    expect(result.current.committed).toEqual({ 'cost-12': '60000' })
  })

  test('re-seeds when the document IDENTITY changes, even to different values', () => {
    // The case the effect exists for: a mill/year switch, a Delete reload, or a Save echo the server
    // normalized. Previously untested, because a Save echo usually carries back what was already held.
    const { result, rerender } = renderHook(({ form, data }) => useCommittedValues(form, data), {
      initialProps: { form: { 'cost-12': '50000' }, data: DOC_A },
    })
    act(() => result.current.commit('cost-12'))

    // A different document arrives with a different seeded form — the baseline must move with it.
    rerender({ form: { 'cost-12': '999' }, data: DOC_B })
    expect(result.current.committed).toEqual({ 'cost-12': '999' })
  })

  test('does NOT re-seed while the document identity is unchanged', () => {
    const { result, rerender } = renderHook(({ form }) => useCommittedValues(form, DOC_A), {
      initialProps: { form: { 'cost-12': '50000' } },
    })
    rerender({ form: { 'cost-12': '60000' } })
    expect(result.current.committed).toEqual({ 'cost-12': '50000' })
  })

  test('an explicit value overrides the form (the grouped-string case)', () => {
    // Schedules 1 and 3 re-group the display on blur; `groupField` only QUEUES its setForm, so the
    // grouped string must be passed in or `committed` and `form` end up holding different text.
    const { result } = renderHook(() => useCommittedValues({ 'vol-12': '1234' }, DOC_A))
    act(() => result.current.commit('vol-12', { value: '1,234' }))
    expect(result.current.committed).toEqual({ 'vol-12': '1,234' })
  })

  test('an invalid field holds its previous committed value', () => {
    const { result, rerender } = renderHook(({ form }) => useCommittedValues(form, DOC_A), {
      initialProps: { form: { 'cost-12': '50000' } },
    })
    rerender({ form: { 'cost-12': '99999999999' } })
    act(() => result.current.commit('cost-12', { invalid: true }))
    // Frozen at the last valid figure rather than driving the cascade from something unsavable.
    expect(result.current.committed).toEqual({ 'cost-12': '50000' })
  })

  test('an unusable entry holds its previous committed value without the caller saying so', () => {
    const { result, rerender } = renderHook(({ form }) => useCommittedValues(form, DOC_A), {
      initialProps: { form: { 'cost-12': '50000' } },
    })
    for (const junk of ['-', '.', '1.2.3', 'Infinity']) {
      rerender({ form: { 'cost-12': junk } })
      act(() => result.current.commit('cost-12'))
      expect(result.current.committed).toEqual({ 'cost-12': '50000' })
    }
  })

  test('clearing a field IS committed — blank is a legitimate value', () => {
    const { result, rerender } = renderHook(({ form }) => useCommittedValues(form, DOC_A), {
      initialProps: { form: { 'cost-12': '50000' } },
    })
    rerender({ form: { 'cost-12': '' } })
    act(() => result.current.commit('cost-12'))
    expect(result.current.committed).toEqual({ 'cost-12': '' })
  })

  test('committing an unchanged field does not produce a new object', () => {
    const { result } = renderHook(() => useCommittedValues({ 'cost-12': '50000' }, DOC_A))
    const before = result.current.committed
    act(() => result.current.commit('cost-12'))
    expect(result.current.committed).toBe(before) // same reference — no re-render
  })
})
