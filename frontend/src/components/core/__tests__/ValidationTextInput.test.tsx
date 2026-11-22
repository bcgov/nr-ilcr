import { render, screen } from '@/test-utils'
import ValidationTextInput from '../ValidationTextInput'

describe('ValidationTextInput', () => {
  test('preserves a supplied description and removes the error when corrected', () => {
    const field = (invalid: boolean) => (
      <>
        <p id="units">Cubic metres</p>
        <ValidationTextInput
          id="volume"
          labelText="Volume"
          aria-describedby="units"
          invalid={invalid}
          invalidText="Volume is required."
        />
      </>
    )
    const { rerender } = render(field(true))
    const input = screen.getByRole('textbox', { name: 'Volume' })
    expect(input).toHaveAccessibleDescription('Cubic metres Volume is required.')
    rerender(field(false))
    expect(input).toHaveAccessibleDescription('Cubic metres')
  })

  test('keeps the Carbon helper text association for a valid input', () => {
    render(<ValidationTextInput id="volume" labelText="Volume" helperText="Cubic metres" />)
    expect(screen.getByRole('textbox', { name: 'Volume' })).toHaveAccessibleDescription(
      'Cubic metres',
    )
  })
})
