import { useState } from 'react'
import { fireEvent, waitFor } from '@testing-library/react'
import { render, screen } from '@/test-utils'
import CommaNumberInput from '@/components/core/CommaNumberInput'

// A controlled harness: state holds the RAW digits, the field shows them grouped.
function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <CommaNumberInput id="amt" labelText="Amount" value={value} onValueChange={setValue} />
      <span data-testid="raw">{value}</span>
    </>
  )
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
    expect(input.value).toBe('1,234')
    // The repro: a regrouping keystroke (comma delete) leaves the raw value unchanged, so a naive
    // controlled input would snap the caret to the end (5). Here it's restored after the first digit.
    fireEvent.change(input, { target: { value: '1234', selectionStart: 1, selectionEnd: 1 } })
    expect(input.value).toBe('1,234')
    await waitFor(() => expect(input.selectionStart).toBe(1))
  })
})
