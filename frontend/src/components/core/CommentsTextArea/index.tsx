import type { ComponentProps, FC } from 'react'
import { TextArea } from '@carbon/react'

// #312 Overall 10: show characters REMAINING beneath a comments field — restoring the legacy
// "{n} characters remaining." wording. Carbon's `enableCounter` shows "used/max" instead (a deviation
// noted in several schedules, e.g. schedule7b/CulvertFields). This wraps Carbon `TextArea`:
//  - enforces the hard cap via `maxLength` (what `enableCounter` used to do implicitly), and
//  - renders the live remaining count as `helperText` (Carbon draws helperText below the field).
// Drop-in for the comment `TextArea`s: swap the tag and pass `maxCount` (was Carbon's maxCount) + the
// bound `value`; every other TextArea prop passes straight through.

type Props = Omit<ComponentProps<typeof TextArea>, 'enableCounter' | 'maxCount' | 'helperText'> & {
  /** Character limit (was Carbon's `maxCount`): enforced as `maxLength` and drives the remaining count. */
  readonly maxCount: number
}

const CommentsTextArea: FC<Props> = ({ maxCount, value, ...rest }) => {
  const used = typeof value === 'string' ? value.length : 0
  const remaining = Math.max(0, maxCount - used)
  return (
    <TextArea
      {...rest}
      value={value}
      maxLength={maxCount}
      helperText={`${remaining} characters remaining`}
    />
  )
}

export default CommentsTextArea
