import type { FC } from 'react'
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
  items,
  selectedCode,
  onSelect,
  matchMode = 'substring',
  disabled,
  invalid,
  invalidText,
  className,
}) => {
  const selectedItem = items.find((option) => option.code === selectedCode) ?? null
  const matches = (description: string, typed: string): boolean => {
    const haystack = description.toLowerCase()
    const needle = typed.toLowerCase()
    return matchMode === 'prefix' ? haystack.startsWith(needle) : haystack.includes(needle)
  }
  return (
    <ComboBox<ComboOption>
      id={id}
      className={className}
      titleText={titleText}
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
      onChange={({ selectedItem: chosen }) => onSelect(chosen?.code ?? '')}
    />
  )
}

export default CodeComboBox
