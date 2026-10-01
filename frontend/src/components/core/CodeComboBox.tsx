import type { FC, FocusEvent } from 'react'
import { useRef } from 'react'
import { ComboBox } from '@carbon/react'

/** A code-backed option: the stored value + the human-readable label shown in the menu. */
export interface ComboOption {
  readonly code: string
  readonly description: string
}

/**
 * How typed text is matched against an option's description.
 *
 * `substring` is the historical behaviour every caller had before this prop existed and stays the
 * default, so Schedules 2, 4 and 8 filter exactly as they always have. `prefix` is opt-in for the
 * controls whose AC specifies it — Schedule 10's BEC Zone (AC5), where typing `SBS` must offer the
 * `SBS*` zones and not every zone containing those letters.
 */
export type ComboMatchMode = 'substring' | 'prefix'

interface CodeComboBoxProps {
  id: string
  titleText: string
  /**
   * An accessible name to use INSTEAD of `titleText`, for a control whose visible label is only
   * unambiguous in the context of the column it is printed under — Schedule 10's Additional
   * Stabilizing `Code`, whose legacy label is that one word (#440 item 3). Omit it and the visible
   * label names the control, which is the right answer everywhere else.
   */
  accessibleName?: string
  /** The full option list (code + description). */
  items: ComboOption[]
  /** The currently selected code ('' when none). */
  selectedCode: string
  /** Called with the chosen option's code ('' when cleared). */
  onSelect: (code: string) => void
  /** Defaults to `substring`, the behaviour every pre-existing caller relies on. */
  matchMode?: ComboMatchMode
  disabled?: boolean
  invalid?: boolean
  invalidText?: string
  className?: string
  /**
   * Called when focus leaves the control with a value — its selected code or its text — different
   * from the one it had when focus entered (JSF `onchange` semantics), so a combo box that can be
   * TYPED into is judged on leave like any field (#359 group C). A focus-and-leave, or typing and
   * then undoing it, does not call it. A SELECTION is the caller's to judge at once, in `onSelect`;
   * this covers what typing leaves behind.
   */
  onLeaveChanged?: () => void
}

/**
 * Shared searchable single-select for code-backed dropdowns (Schedule 2/4/8 selectors). Wraps Carbon
 * {@link ComboBox} so users can type to filter long lists (autocomplete) while still seeing the full
 * option list on click; the menu shows each option's full description (no truncation — see the global
 * `.cds--list-box__menu-item__option` wrap rule). Shows the description, writes back the code.
 */
const CodeComboBox: FC<CodeComboBoxProps> = ({
  id,
  titleText,
  accessibleName,
  items,
  selectedCode,
  onSelect,
  matchMode = 'substring',
  disabled,
  invalid,
  invalidText,
  className,
  onLeaveChanged,
}) => {
  // The value (selected code + text) when focus entered the control, compared on leave.
  const focusValueRef = useRef<string | null>(null)
  // The latest selected code, read at leave time (a selection in the same event is not rendered yet).
  const selectedCodeRef = useRef(selectedCode)
  selectedCodeRef.current = selectedCode
  const selectedItem = items.find((option) => option.code === selectedCode) ?? null
  const matches = (description: string, typed: string): boolean => {
    const haystack = description.toLowerCase()
    const needle = typed.toLowerCase()
    return matchMode === 'prefix' ? haystack.startsWith(needle) : haystack.includes(needle)
  }
  const comboBox = (
    <ComboBox<ComboOption>
      id={id}
      className={className}
      titleText={titleText}
      // Carbon only sets `aria-label` on the input when there is NO `titleText`, and downshift
      // labels the input with `aria-labelledby` pointing at that title — which wins over any
      // `aria-label`. Overriding both through `inputProps` (they are spread last into
      // `getInputProps`) is what actually renames the control for a screen reader; the visible
      // title is untouched.
      //
      // Carbon marks an invalid combo only on its list-box wrapper (`data-invalid`); `aria-invalid` on
      // the input is what a screen reader (and a test) reads, as it is on a TextInput.
      inputProps={{
        ...(accessibleName === undefined
          ? {}
          : { 'aria-label': accessibleName, 'aria-labelledby': undefined }),
        ...(invalid === true ? { 'aria-invalid': true } : {}),
      }}
      placeholder="Select"
      items={items}
      itemToString={(item) => item?.description ?? ''}
      selectedItem={selectedItem}
      // Autocomplete: typing filters the list by a case-insensitive match on the description, of the
      // kind `matchMode` names. Show the whole list when nothing is typed OR when the input still
      // equals the current selection (menu just opened) — otherwise a selected value would filter the
      // list down to itself and hide the other options. Explicit so filtering doesn't depend on the
      // Carbon default.
      shouldFilterItem={({ item, inputValue }) =>
        !inputValue ||
        inputValue === selectedItem?.description ||
        matches(item?.description ?? '', inputValue)
      }
      disabled={disabled}
      invalid={invalid}
      invalidText={invalidText}
      onChange={({ selectedItem: chosen }) => {
        selectedCodeRef.current = chosen?.code ?? ''
        onSelect(chosen?.code ?? '')
      }}
    />
  )
  if (onLeaveChanged === undefined) {
    return comboBox
  }
  // Focus moving WITHIN the control (input, toggle, clear button) is neither an entry nor a leave.
  const outside = (event: FocusEvent<HTMLDivElement>) =>
    !(event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget))
  const valueOf = (scope: HTMLDivElement) =>
    `${selectedCodeRef.current}\u0000${scope.querySelector('input')?.value ?? ''}`
  return (
    // `display: contents` adds no layout box, but it IS an extra element: a child or sibling
    // selector (`>`, `+`, `:first-child`) aimed at the combo's wrapper will not match through it.
    // That is why the scope is rendered only for a caller that asks for `onLeaveChanged`.
    <div
      className="code-combo-box__focus-scope"
      style={{ display: 'contents' }}
      onFocus={(event) => {
        if (outside(event)) {
          focusValueRef.current = valueOf(event.currentTarget)
        }
      }}
      onBlur={(event) => {
        if (!outside(event)) {
          return
        }
        const before = focusValueRef.current
        focusValueRef.current = null
        if (before !== null && before !== valueOf(event.currentTarget)) {
          onLeaveChanged()
        }
      }}
    >
      {comboBox}
    </div>
  )
}

export default CodeComboBox
