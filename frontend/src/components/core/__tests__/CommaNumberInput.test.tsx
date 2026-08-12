import { useState } from 'react'
import { fireEvent, waitFor } from '@testing-library/react'
import { render, screen } from '@/test-utils'
import CommaNumberInput from '@/components/core/CommaNumberInput'

// A controlled harness: state holds the RAW form string, the field shows it grouped.
function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <CommaNumberInput id="amt" labelText="Amount" value={value} onValueChange={setValue} />
      <span data-testid="raw">{value}</span>
    </>
  )
}

// Emulate a real keystroke: the browser inserts `char` at the current caret, THEN the change fires
// with the caret sitting just after the inserted char. Driving the event this way (rather than
// userEvent.type, which re-homes the caret to the end each call) is what exposes caret regressions.
function typeAt(input: HTMLInputElement, char: string) {
  const at = input.selectionStart ?? input.value.length
  const next = input.value.slice(0, at) + char + input.value.slice(at)
  fireEvent.change(input, { target: { value: next, selectionStart: at + 1, selectionEnd: at + 1 } })
}

describe('CommaNumberInput', () => {
  test('displays the raw value comma-grouped', () => {
    render(<Harness initial="1234567" />)
    expect(screen.getByLabelText('Amount')).toHaveValue('1,234,567')
  })

  test('reports edits back as the raw (comma-stripped) string', () => {
    render(<Harness />)
    const input = screen.getByLabelText('Amount')
    fireEvent.change(input, { target: { value: '1,234' } })
    expect(screen.getByTestId('raw')).toHaveTextContent('1234')
    expect(input).toHaveValue('1,234')
  })

  test('keeps the caret near the edit instead of jumping to the end when regrouping', async () => {
    render(<Harness initial="1234" />)
    const input = screen.getByLabelText('Amount') as HTMLInputElement
    input.focus()
    expect(input.value).toBe('1,234')
    // The repro: a regrouping keystroke (comma delete) leaves the raw value unchanged, so a naive
    // controlled input would snap the caret to the end (5). Here it's restored after the first digit.
    fireEvent.change(input, { target: { value: '1234', selectionStart: 1, selectionEnd: 1 } })
    expect(input.value).toBe('1,234')
    await waitFor(() => expect(input.selectionStart).toBe(1))
  })

  test('preserves a typed decimal point instead of swallowing it', async () => {
    render(<Harness initial="1234" />)
    const input = screen.getByLabelText('Amount') as HTMLInputElement
    input.focus()
    input.setSelectionRange(5, 5) // caret at the end of "1,234"
    typeAt(input, '.')
    expect(screen.getByTestId('raw')).toHaveTextContent('1234.')
    expect(input.value).toBe('1,234.')
    await waitFor(() => expect(input.selectionStart).toBe(6)) // just AFTER the '.'
    typeAt(input, '5')
    expect(screen.getByTestId('raw')).toHaveTextContent('1234.5')
    expect(input.value).toBe('1,234.5')
    await waitFor(() => expect(input.selectionStart).toBe(7))
  })

  test('preserves a leading minus sign instead of pushing digits in front of it', async () => {
    render(<Harness />)
    const input = screen.getByLabelText('Amount') as HTMLInputElement
    input.focus()
    typeAt(input, '-')
    expect(input.value).toBe('-')
    await waitFor(() => expect(input.selectionStart).toBe(1))
    typeAt(input, '5')
    typeAt(input, '0')
    typeAt(input, '0')
    // The old digits-only anchor produced "500-" (sign swallowed to the end); it must stay a leading -.
    expect(screen.getByTestId('raw')).toHaveTextContent('-500')
    expect(input.value).toBe('-500')
  })
})
