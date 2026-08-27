import type { ComponentProps } from 'react'
import { act } from 'react'
import { render, screen, userEvent } from '@/test-utils'
import { crossViewportBreakpoint, setLargeViewport } from '@/test-setup'
import Layout from '@/components/Layout'

// LayoutSideNav renders TanStack `Link`s and reads `useLocation`, both of which throw outside a
// RouterProvider (AppProviders has none). Mock the router like the schedule tests do; `Link` renders
// a real anchor so the nav items keep their `link` role and the click path is genuine.
//
// The mock MUST forward the remaining props: Carbon passes our close-on-navigate handler down to the
// `as` element, so a mock that only takes `children`/`to` silently swallows the very behaviour these
// tests assert (it makes the lg+ "stays open" case pass for the wrong reason).
type MockLinkProps = ComponentProps<'a'> & { to?: string }

vi.mock('@tanstack/react-router', () => ({
  useLocation: () => ({ pathname: '/' }),
  Link: ({ children, to, onClick, ...rest }: MockLinkProps) => (
    <a
      href={to}
      onClick={(event) => {
        // jsdom cannot navigate; swallow the anchor default, then run Carbon's handler.
        event.preventDefault()
        onClick?.(event)
      }}
      {...rest}
    >
      {children}
    </a>
  ),
}))

const renderShell = () => render(<Layout>page body</Layout>)

// The Carbon SideNav element itself — `expanded` shows up as the `cds--side-nav--expanded` class.
const sideNav = () => document.querySelector('.cds--side-nav')
const isExpanded = () => sideNav()?.classList.contains('cds--side-nav--expanded') ?? false

describe('LayoutSideNav — default state and persistence (#316)', () => {
  test('AC1: at lg+ the nav is expanded on first paint, with no interaction', () => {
    setLargeViewport(true)
    renderShell()

    expect(isExpanded()).toBe(true)
    // The toggle reflects the open state, so a user is not told to "Open menu" on an open menu.
    expect(screen.getByRole('button', { name: 'Close menu' })).toBeInTheDocument()
  })

  test('AC2: below lg the nav is collapsed on first paint', () => {
    setLargeViewport(false)
    renderShell()

    expect(isExpanded()).toBe(false)
    expect(screen.getByRole('button', { name: 'Open menu' })).toBeInTheDocument()
  })

  test('AC3: at lg+ clicking a nav item leaves the nav open', async () => {
    setLargeViewport(true)
    renderShell()

    await userEvent.click(screen.getByRole('link', { name: 'Check Status' }))

    expect(isExpanded()).toBe(true)
  })

  test('AC4: below lg clicking a nav item closes the overlay', async () => {
    setLargeViewport(false)
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(isExpanded()).toBe(true)

    await userEvent.click(screen.getByRole('link', { name: 'Check Status' }))

    expect(isExpanded()).toBe(false)
  })

  test('AC5: the toggle still collapses the nav at lg+ (the wide tables need the space back)', async () => {
    setLargeViewport(true)
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Close menu' }))
    expect(isExpanded()).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(isExpanded()).toBe(true)
  })

  test('AC6/AC7: the inset modifier tracks the nav, on the content AND the fixed footer', async () => {
    setLargeViewport(true)
    const { container } = renderShell()

    const content = container.querySelector('.app-content')
    const footer = container.querySelector('.app-footer')
    expect(content).toHaveClass('app-content--nav-expanded')
    expect(footer).toHaveClass('app-footer--nav-expanded')

    // Collapsing gives the full width back. (The 16rem itself is a CSS fact scoped to lg+ that jsdom
    // cannot see — this asserts the wiring; the measurement is a browser check.)
    await userEvent.click(screen.getByRole('button', { name: 'Close menu' }))
    expect(content).not.toHaveClass('app-content--nav-expanded')
    expect(footer).not.toHaveClass('app-footer--nav-expanded')
  })

  test('AC8: crossing the breakpoint resettles the nav to that viewport default', () => {
    setLargeViewport(true)
    renderShell()
    expect(isExpanded()).toBe(true)

    // Dragged narrow: the panel must not be left covering the page.
    act(() => crossViewportBreakpoint(false))
    expect(isExpanded()).toBe(false)

    // Back to desktop: expanded again, per AC1.
    act(() => crossViewportBreakpoint(true))
    expect(isExpanded()).toBe(true)
  })
})
