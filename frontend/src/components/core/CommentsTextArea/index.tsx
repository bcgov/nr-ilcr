import type { ComponentProps, FC } from 'react'
import { TextArea } from '@carbon/react'

// #312 Overall 10: show characters REMAINING beneath a comments field — restoring the legacy
// "{n} characters remaining." wording. Carbon's `enableCounter` shows "used/max" instead (a deviation
// noted in several schedules, e.g. schedule7b/CulvertFields). This wraps Carbon `TextArea`:
//  - enforces the hard cap via `maxLength` (what `enableCounter` used to do implicitly), and
//  - renders the live remaining count below the field.
// Drop-in for the comment `TextArea`s: swap the tag and pass `maxCount` (was Carbon's maxCount) + the
// bound `value`; every other TextArea prop passes straight through.

type Props = Omit<ComponentProps<typeof TextArea>, 'enableCounter' | 'maxCount' | 'helperText'> & {
  /** Character limit (was Carbon's `maxCount`): enforced as `maxLength` and drives the remaining count. */
  readonly maxCount: number
}

const CommentsTextArea: FC<Props> = ({ maxCount, value, invalid, warn, ...rest }) => {
  const used = typeof value === 'string' ? value.length : 0
  const remaining = Math.max(0, maxCount - used)
  const counter = `${remaining} characters remaining`

  // Carbon renders `helperText` ONLY when the field is neither invalid nor warned (@carbon/react
  // TextArea: `!invalid && !warn && !isFluid ? helper : null`). Routing the count through helperText
  // alone therefore hid the character budget at the one moment the user most needs it — while an
  // error is on the field (PR #381 review). So: helperText while Carbon will show it (it also earns
  // the count an `aria-describedby` link that way), and an identically-styled standalone line when
  // Carbon suppresses it. Exactly one of the two renders. Visual-only in the error state matches
  // Carbon's own counter, which is `aria-hidden` and leaves the error to `aria-errormessage`.
  const suppressed = Boolean(invalid) || Boolean(warn)

  return (
    <>
      <TextArea
        {...rest}
        invalid={invalid}
        warn={warn}
        value={value}
        maxLength={maxCount}
        helperText={suppressed ? undefined : counter}
      />
      {suppressed && <div className="cds--form__helper-text">{counter}</div>}
    </>
  )
}

export default CommentsTextArea
