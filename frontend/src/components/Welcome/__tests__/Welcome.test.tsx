import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Welcome from '@/components/Welcome'

describe('Welcome', () => {
  it('offers both sign-in options to a signed-out visitor', () => {
    render(<Welcome onSignIn={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'Welcome to ILCR' })).toBeInTheDocument()
    expect(screen.getByText('Interior Logging Cost Report')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /log in with idir/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /log in with business bceid/i })).toBeInTheDocument()
  })

  it('exposes the BC Gov logo but keeps the photo out of the accessibility tree', () => {
    const { container } = render(<Welcome onSignIn={vi.fn()} />)

    expect(screen.getAllByRole('img')).toHaveLength(1)
    expect(screen.getByAltText('Government of British Columbia')).toBeInTheDocument()
    expect(container.querySelector('.welcome-page__photo img')).toHaveAttribute('alt', '')
  })

  // A stored dark theme would otherwise put light text on the white panel.
  it('always renders in the light theme', () => {
    render(<Welcome onSignIn={vi.fn()} />)

    expect(screen.getByRole('main')).toHaveClass('cds--g10')
  })

  it('starts the IDIR sign-in and holds both buttons while the redirect starts', async () => {
    const user = userEvent.setup()
    const onSignIn = vi.fn(() => new Promise<void>(() => {}))
    render(<Welcome onSignIn={onSignIn} />)

    await user.click(screen.getByRole('button', { name: /log in with idir/i }))

    expect(onSignIn).toHaveBeenCalledExactlyOnceWith('idir')
    expect(screen.getByRole('button', { name: /log in with idir/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /log in with business bceid/i })).toBeDisabled()
  })

  it('reports a sign-in that cannot start and lets the visitor try again', async () => {
    const user = userEvent.setup()
    const onSignIn = vi
      .fn()
      .mockResolvedValue(undefined)
      .mockRejectedValueOnce(new Error('discovery unavailable'))
    render(<Welcome onSignIn={onSignIn} />)

    await user.click(screen.getByRole('button', { name: /log in with idir/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not start sign-in')
    await user.click(screen.getByRole('button', { name: /log in with idir/i }))
    expect(onSignIn).toHaveBeenCalledTimes(2)
  })

  // CSS signs a Business BCeID hint in with IDIR until Business BCeID is provisioned.
  it('explains that Business BCeID is not available yet instead of starting a sign-in', async () => {
    const user = userEvent.setup()
    const onSignIn = vi.fn()
    render(<Welcome onSignIn={onSignIn} />)

    await user.click(screen.getByRole('button', { name: /log in with business bceid/i }))

    expect(await screen.findByText(/business bceid is not provisioned yet/i)).toBeInTheDocument()
    expect(onSignIn).not.toHaveBeenCalled()
  })
})
