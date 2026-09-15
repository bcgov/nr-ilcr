import { describe, expect, test } from 'vitest'
import { render, screen } from '@/test-utils'
import OriginalValueIndicator from '@/components/core/OriginalValueIndicator'
import type { OriginalValues } from '@/interfaces/OriginalValue'

/**
 * The shared indicator's own contract (Story 16.2). `originalValueState` is unit-tested separately
 * in `utils/__tests__/originalValue.test.ts`; what is pinned here is what the COMPONENT adds — the
 * render/suppress decision and the accessibility wiring, which the per-schedule integration tests
 * take on trust.
 *
 * The accessible-name tests exist because of a defect found in review of PR #452: Carbon's Tooltip
 * `label` prop wires the popover through `aria-labelledby`, which replaces the trigger's own
 * `aria-label` outright. Every indicator therefore announced only "Original Submission Value: X"
 * with no hint of WHICH field it belonged to, while an `expect(...).toHaveAttribute('aria-label')`
 * assertion still passed because the attribute was present and correct — it was simply overridden.
 * These assertions go through the COMPUTED name and description so the override cannot come back.
 */
const submitted: OriginalValues = {
  volume: { value: '60000', tooltip: 'Original Submission Value: 60,000' },
}

const setup = (over: Partial<React.ComponentProps<typeof OriginalValueIndicator>> = {}) =>
  render(
    <OriginalValueIndicator
      originals={submitted}
      field="volume"
      current="60002"
      label="Standing Tree to Loaded Truck volume"
      {...over}
    />,
  )

describe('OriginalValueIndicator', () => {
  test('names the field it belongs to and describes it with the submitted value', () => {
    setup()
    const indicator = screen.getByTestId('original-value-volume')

    expect(indicator).toHaveAccessibleName(
      'Standing Tree to Loaded Truck volume differs from the originally submitted value',
    )
    expect(indicator).toHaveAccessibleDescription('Original Submission Value: 60,000')
  })

  test('the trigger is a real focusable button, so the tooltip is not hover-only', () => {
    setup()
    const indicator = screen.getByTestId('original-value-volume')

    expect(indicator.tagName).toBe('BUTTON')
    expect(indicator).toHaveAttribute('type', 'button')
    indicator.focus()
    expect(indicator).toHaveFocus()
  })

  test('renders nothing when the value matches what was submitted', () => {
    setup({ current: '60000' })
    expect(screen.queryByTestId('original-value-volume')).not.toBeInTheDocument()
  })

  test('renders nothing at Draft — originals are null however far the value has moved', () => {
    setup({ originals: null })
    expect(screen.queryByTestId('original-value-volume')).not.toBeInTheDocument()
  })

  test('a field with no submitted value on file says so rather than trailing off', () => {
    setup({ field: 'cost', current: '7000', label: 'Standing Tree to Loaded Truck cost' })
    const indicator = screen.getByTestId('original-value-cost')

    expect(indicator).toHaveAccessibleDescription(
      'No value was originally submitted by the Licensee',
    )
  })

  test('a text field compares exactly — a trailing space is a real change', () => {
    setup({
      originals: {
        comments: { value: 'as reported', tooltip: 'Original Submission Value: as reported' },
      },
      field: 'comments',
      current: 'as reported ',
      numeric: false,
      label: 'Comments',
    })
    expect(screen.getByTestId('original-value-comments')).toBeInTheDocument()
  })
})
