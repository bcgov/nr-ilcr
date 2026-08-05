import { render, screen } from '@/test-utils'
import Footer from '@/components/Layout/Footer'

describe('Footer', () => {
  test('shows the ILCR version on the left and the four BC Gov links (new tab)', () => {
    render(<Footer />)

    // Version label (the exact number is injected by Vite `define` at build; assert the prefix).
    expect(screen.getByText(/^ILCR v\d/)).toBeInTheDocument()

    const expected: [string, string][] = [
      ['Copyright', 'https://www.gov.bc.ca/for/com/copyright.html'],
      ['Disclaimer', 'https://www.gov.bc.ca/for/com/disclaimer.html'],
      ['Privacy', 'https://dlvrapps.nrs.gov.bc.ca/com/privacy.html'],
      ['Accessibility', 'https://www.gov.bc.ca/for/com/accessibility.html'],
    ]
    for (const [label, href] of expected) {
      const link = screen.getByRole('link', { name: label })
      expect(link).toHaveAttribute('href', href)
      expect(link).toHaveAttribute('target', '_blank')
      expect(link).toHaveAttribute('rel', 'noreferrer')
    }
  })
})
