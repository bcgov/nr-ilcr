import type { ComponentProps } from 'react'
import { describe, expect, test } from 'vitest'
import { render, screen } from '@/test-utils'
import CommentsTextArea from '@/components/core/CommentsTextArea'

// Story 30.6 / #312 Overall 10: the comments fields count DOWN ("{n} characters remaining", legacy's
// own counterTemplate) rather than showing Carbon's "used/max", and keep the hard cap legacy set with
// `maxlength`. The invalid/warn cases are the PR #381 review finding: Carbon renders `helperText` only
// when the field is neither invalid nor warned, so routing the count through helperText alone hid the
// character budget exactly while an error was on the field.

const setup = (over: Partial<ComponentProps<typeof CommentsTextArea>> = {}) =>
  render(
    <CommentsTextArea
      id="comments"
      labelText="Comments"
      maxCount={400}
      value="abc"
      onChange={() => {}}
      {...over}
    />,
  )

describe('CommentsTextArea', () => {
  test('counts down from the cap and applies it as maxLength', () => {
    setup()
    expect(screen.getByText('397 characters remaining')).toBeInTheDocument()
    expect(screen.getByLabelText('Comments')).toHaveAttribute('maxlength', '400')
  })

  test('an empty field reports the whole budget', () => {
    setup({ value: '' })
    expect(screen.getByText('400 characters remaining')).toBeInTheDocument()
  })

  test('the count stays visible while the field is invalid (#381 review)', () => {
    setup({ invalid: true, invalidText: 'Comments are too long' })
    expect(screen.getByText('Comments are too long')).toBeInTheDocument()
    expect(screen.getByText('397 characters remaining')).toBeInTheDocument()
  })

  test('the count stays visible while the field is warned (#381 review)', () => {
    setup({ warn: true, warnText: 'Check this' })
    expect(screen.getByText('Check this')).toBeInTheDocument()
    expect(screen.getByText('397 characters remaining')).toBeInTheDocument()
  })

  test('the count renders exactly once in every state', () => {
    const { unmount } = setup()
    expect(screen.getAllByText('397 characters remaining')).toHaveLength(1)
    unmount()
    setup({ invalid: true, invalidText: 'Comments are too long' })
    expect(screen.getAllByText('397 characters remaining')).toHaveLength(1)
  })
})
