import { describe, expect, test } from 'vitest'
import { Grid } from '@carbon/react'
import { render, screen } from '@/test-utils'
import CheckStatusActions from '../CheckStatusActions'

const enabled = { enabled: true, onClick: () => undefined }
const greyed = (disabledReason: string) => ({
  enabled: false,
  disabledReason,
  onClick: () => undefined,
})

const hintFor = (button: HTMLElement): HTMLElement | null => {
  const id = button.getAttribute('aria-describedby')
  return id ? document.getElementById(id) : null
}

describe('CheckStatusActions', () => {
  test('every disabled action, the admin reversals included, carries a resolvable visually-hidden hint', () => {
    render(
      <Grid>
        <CheckStatusActions
          setToDraft={greyed('draft reason')}
          setToSubmit={greyed('submit-back reason')}
          submit={greyed('submit reason')}
          verify={greyed('verify reason')}
        />
      </Grid>,
    )
    const expected: Record<string, string> = {
      'Set to Draft': 'draft reason',
      'Set to Submit': 'submit-back reason',
      Submit: 'submit reason',
      Verified: 'verify reason',
    }
    const ids = new Set<string>()
    for (const [label, reason] of Object.entries(expected)) {
      const button = screen.getByRole('button', { name: label })
      expect(button).toBeDisabled()
      const hint = hintFor(button)
      expect(hint).toHaveTextContent(reason)
      expect(hint).toHaveClass('cds--visually-hidden')
      ids.add(hint!.id)
    }
    expect(ids.size).toBe(4)
  })

  test('an enabled action carries no hint and no dangling aria-describedby', () => {
    render(
      <Grid>
        <CheckStatusActions setToDraft={enabled} submit={enabled} verify={enabled} />
      </Grid>,
    )
    for (const label of ['Set to Draft', 'Submit', 'Verified']) {
      const button = screen.getByRole('button', { name: label })
      expect(button).toBeEnabled()
      expect(button).not.toHaveAttribute('aria-describedby')
    }
    expect(screen.queryByRole('button', { name: 'Set to Submit' })).not.toBeInTheDocument()
  })

  test('an action with no click handler is disabled even when its gate is open', () => {
    render(
      <Grid>
        <CheckStatusActions
          submit={{ enabled: true, disabledReason: 'not wired' }}
          verify={enabled}
        />
      </Grid>,
    )
    const submit = screen.getByRole('button', { name: 'Submit' })
    expect(submit).toBeDisabled()
    expect(hintFor(submit)).toHaveTextContent('not wired')
  })
})
