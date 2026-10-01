import type { FC } from 'react'
import { Launch } from '@carbon/icons-react'

// The application footer: the ILCR version on the left, and the standard BC Gov site links centred.
// The links open the government copyright/disclaimer/privacy/accessibility pages in a new tab.
const FOOTER_LINKS: readonly { label: string; href: string }[] = [
  { label: 'Copyright', href: 'https://www.gov.bc.ca/for/com/copyright.html' },
  { label: 'Disclaimer', href: 'https://www.gov.bc.ca/for/com/disclaimer.html' },
  { label: 'Privacy', href: 'https://dlvrapps.nrs.gov.bc.ca/com/privacy.html' },
  { label: 'Accessibility', href: 'https://www.gov.bc.ca/for/com/accessibility.html' },
]

// `typeof` guard so the component never throws if the __APP_VERSION__ define is absent (e.g. a raw
// unit render outside the Vite pipeline); Vite/Vitest inline the real version in every normal build.
const APP_VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.0.0'

// `className` carries the side-nav inset modifier from the layout shell. The footer is
// `position: fixed`, so no ancestor can offset it — the class has to land on the element itself.
type Props = {
  readonly className?: string
}

const Footer: FC<Props> = ({ className }) => (
  <footer className={className ? `app-footer ${className}` : 'app-footer'}>
    <span className="app-footer__version">ILCR v{APP_VERSION}</span>
    <nav className="app-footer__links" aria-label="Footer">
      {FOOTER_LINKS.map((link) => (
        <a
          key={link.label}
          className="app-footer__link"
          href={link.href}
          target="_blank"
          rel="noreferrer"
        >
          {link.label}
          {/* Cue that the link opens a new tab; shown on hover/focus (decorative — aria-hidden). */}
          <Launch size={16} className="app-footer__launch" aria-hidden="true" />
        </a>
      ))}
    </nav>
  </footer>
)

export default Footer
