import type { ComponentProps } from 'react'
import { useRef } from 'react'
import { TextInput } from '@carbon/react'
import { groupInput, stripGroup } from '@/utils/number'

// Everything a Carbon TextInput takes, minus the value/onChange we own. `value` is the RAW form
// string (no thousands separators, but sign and decimals intact); the field displays it grouped and
// reports edits back stripped of the separators.
type Props = Omit<ComponentProps<typeof TextInput>, 'value' | 'onChange' | 'ref'> & {
  value: string
  onValueChange: (raw: string) => void
}

// Count the NON-COMMA characters in the first `end` chars of `s`. Anchoring the caret on this count
// (rather than digits only) absorbs regrouping while keeping non-digit characters the user types —
// the decimal point and the leading minus sign — on the correct side of the caret.
const nonCommaBefore = (s: string, end: number): number => {
  let count = 0
  for (let i = 0; i < end && i < s.length; i += 1) {
    if (s[i] !== ',') count += 1
  }
  return count
}

// Index in `formatted` just past its `n`-th non-comma character (where the caret should land).
const caretAfterNonComma = (formatted: string, n: number): number => {
  let pos = 0
  let seen = 0
  while (pos < formatted.length && seen < n) {
    if (formatted[pos] !== ',') seen += 1
    pos += 1
  }
  return pos
}

/**
 * Comma-grouped numeric text input that keeps the caret put. State holds the raw form string and the
 * field shows the grouped form, so a naive controlled input reassigns `input.value` on any regrouping
 * keystroke and the caret jumps to the end — worst on a bare comma-delete, where the raw value is
 * unchanged, React bails the re-render, and the controlled-value revert snaps the caret to the end.
 * The change handler records the caret by NON-COMMA character count (so a typed `.` or `-` is
 * preserved, not swallowed), reports the raw value, then re-places the caret after that same character
 * once the DOM has settled (a rAF, which runs whether or not a re-render happened) — but only if the
 * field still has focus, so a caret restore never steals it back after the user has tabbed away.
 */
export default function CommaNumberInput({ value, onValueChange, ...rest }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)

  return (
    <TextInput
      {...rest}
      ref={inputRef}
      inputMode="decimal"
      value={groupInput(value)}
      onChange={(event) => {
        const el = event.target
        const caret = el.selectionStart ?? el.value.length
        const wanted = nonCommaBefore(el.value, caret)
        onValueChange(stripGroup(el.value))
        requestAnimationFrame(() => {
          const input = inputRef.current
          if (input && document.activeElement === input) {
            const pos = caretAfterNonComma(input.value, wanted)
            input.setSelectionRange(pos, pos)
          }
        })
      }}
    />
  )
}
