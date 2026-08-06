import type { ComponentProps } from 'react'
import { useRef } from 'react'
import { TextInput } from '@carbon/react'
import { withCommas } from '@/utils/number'

// Everything a Carbon TextInput takes, minus the value/onChange we own. `value` is the RAW numeric
// string (no grouping); the field displays it comma-grouped and reports edits back raw.
type Props = Omit<ComponentProps<typeof TextInput>, 'value' | 'onChange' | 'ref'> & {
  value: string
  onValueChange: (raw: string) => void
}

// Count the digit characters in the first `end` chars of `s`.
const digitsBefore = (s: string, end: number): number => s.slice(0, end).replace(/\D/g, '').length

// Index in `formatted` just past its `n`-th digit (where the caret should land).
const caretAfterDigits = (formatted: string, n: number): number => {
  let pos = 0
  let seen = 0
  while (pos < formatted.length && seen < n) {
    if (/\d/.test(formatted[pos])) seen += 1
    pos += 1
  }
  return pos
}

/**
 * Comma-grouped numeric text input that keeps the caret put. State holds the raw digits and the field
 * shows the grouped form, so a naive controlled input reassigns `input.value` on any regrouping
 * keystroke and the caret jumps to the end — worst on a bare comma-delete, where the raw value is
 * unchanged, React bails the re-render, and the controlled-value revert snaps the caret to the end.
 * Here the change handler records the caret by digit count, reports the raw value, then re-places the
 * caret after that same digit once the DOM has settled (a rAF, which runs whether or not a re-render
 * happened) — so editing a seven-figure number or backspacing across a comma behaves normally.
 */
export default function CommaNumberInput({ value, onValueChange, ...rest }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)

  return (
    <TextInput
      {...rest}
      ref={inputRef}
      inputMode="numeric"
      value={withCommas(value)}
      onChange={(event) => {
        const el = event.target
        const caret = el.selectionStart ?? el.value.length
        const wanted = digitsBefore(el.value, caret)
        onValueChange(el.value.replace(/,/g, ''))
        requestAnimationFrame(() => {
          const input = inputRef.current
          if (input) {
            const pos = caretAfterDigits(input.value, wanted)
            input.setSelectionRange(pos, pos)
          }
        })
      }}
    />
  )
}
