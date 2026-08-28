import type { ComponentProps } from 'react'
import type * as TanStackRouter from '@tanstack/react-router'
import { act } from 'react'
import { render, screen, userEvent } from '@/test-utils'
import {
  activeViewportListenerCount,
  crossViewportBreakpoint,
  setLargeViewport,
} from '@/test-setup'
import Layout from '@/components/Layout'

// LayoutSideNav renders TanStack `Link`s and reads `useLocation`, both of which throw outside a
// RouterProvider (AppProviders has none). Spread the real module so that adding any other router
// export to the Layout tree fails as a missing mock rather than as "undefined is not a function".
//
// The mock MUST forward the remaining props: Carbon passes our close-on-navigate handler down to the
// `as` element, so a mock taking only `children`/`to` silently swallows the very behaviour these
// tests assert (it makes the lg+ "stays open" case pass for the wrong reason).
type MockLinkProps = ComponentProps<'a'> & { to?: string }

vi.mock('@tanstack/react-router', async (importOriginal) => ({
  ...(await importOriginal<typeof TanStackRouter>()),
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

// The Carbon SideNav element. Asserted non-null by every caller: an earlier version returned `false`
// for "no nav in the document", which made every collapsed-state assertion pass on a broken render.
const sideNav = (): HTMLElement => {
  const nav = document.querySelector<HTMLElement>('.cds--side-nav')
  expect(nav).not.toBeNull()
  return nav as HTMLElement
}

const isExpanded = () => sideNav().classList.contains('cds--side-nav--expanded')

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

  test('AC3: at lg+ clicking a top-level nav link leaves the nav open', async () => {
    setLargeViewport(true)
    renderShell()

    await userEvent.click(screen.getByRole('link', { name: 'Check Status' }))

    expect(isExpanded()).toBe(true)
  })

  test('AC3: at lg+ clicking a SUBMENU item also leaves the nav open', async () => {
    setLargeViewport(true)
    renderShell()

    // A different Carbon code path from SideNavLink — SideNavMenuItem renders `as` directly, while
    // SideNavLink spreads through UIShell's own Link. One does not prove the other.
    await userEvent.click(screen.getByRole('button', { name: 'Schedules' }))
    await userEvent.click(screen.getByRole('link', { name: 'Schedule 1' }))

    expect(isExpanded()).toBe(true)
  })

  test('AC4: below lg clicking a nav link closes the overlay', async () => {
    setLargeViewport(false)
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(isExpanded()).toBe(true)

    await userEvent.click(screen.getByRole('link', { name: 'Check Status' }))

    expect(isExpanded()).toBe(false)
  })

  test('AC4: below lg clicking a SUBMENU item also closes the overlay', async () => {
    setLargeViewport(false)
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    await userEvent.click(screen.getByRole('button', { name: 'Schedules' }))
    await userEvent.click(screen.getByRole('link', { name: 'Schedule 1' }))

    expect(isExpanded()).toBe(false)
  })

  test('AC5: the toggle collapses and reopens the nav at lg+ (wide tables need the space back)', async () => {
    setLargeViewport(true)
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Close menu' }))
    expect(isExpanded()).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(isExpanded()).toBe(true)
  })

  test('AC5: the toggle also closes the overlay below lg', async () => {
    setLargeViewport(false)
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(isExpanded()).toBe(true)

    await userEvent.click(screen.getByRole('button', { name: 'Close menu' }))
    expect(isExpanded()).toBe(false)
  })

  test('AC6: the inset modifier tracks the nav, on the content AND the fixed footer', async () => {
    setLargeViewport(true)
    const { container } = renderShell()

    const content = container.querySelector('.app-content')
    const footer = container.querySelector('.app-footer')
    expect(content).toHaveClass('app-content--nav-expanded')
    expect(footer).toHaveClass('app-footer--nav-expanded')

    // Collapsing gives the full width back. (The 16rem itself is a CSS fact — vitest runs with
    // `css: false` — so this asserts the wiring; the measurement is a browser check.)
    await userEvent.click(screen.getByRole('button', { name: 'Close menu' }))
    expect(content).not.toHaveClass('app-content--nav-expanded')
    expect(footer).not.toHaveClass('app-footer--nav-expanded')
  })

  test('AC7: below lg an EXPANDED nav applies no inset — it overlays the page', async () => {
    setLargeViewport(false)
    const { container } = renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(isExpanded()).toBe(true)

    // The nav is open, but the page must not shift: below lg it is a modal overlay drawn on top.
    // Before this was gated on isLargeViewport, the class went on at every width and only a media
    // query nothing tested stopped a 16rem off-screen shove.
    expect(container.querySelector('.app-content')).not.toHaveClass('app-content--nav-expanded')
    expect(container.querySelector('.app-footer')).not.toHaveClass('app-footer--nav-expanded')
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

  test('AC8: a crossing also updates isLargeViewport, so close-on-navigate follows the viewport', async () => {
    setLargeViewport(true)
    renderShell()

    act(() => crossViewportBreakpoint(false))

    // Now genuinely below lg: reopening and clicking a link must close the overlay. Without the
    // isLargeViewport half of the crossing, handleNavigate would still be undefined and the overlay
    // would stay open on top of the page — AC4's defect, reached by resize instead of by load.
    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    await userEvent.click(screen.getByRole('link', { name: 'Check Status' }))

    expect(isExpanded()).toBe(false)
  })

  test('AC8 (amended): a manual toggle survives a later breakpoint crossing', async () => {
    setLargeViewport(true)
    renderShell()

    // The user deliberately collapses the nav to read a wide table...
    await userEvent.click(screen.getByRole('button', { name: 'Close menu' }))
    expect(isExpanded()).toBe(false)

    // ...then zooms, which moves the CSS-px viewport across the breakpoint with no window resize.
    // Their choice must survive; otherwise the nav reopens over the space they just cleared.
    act(() => crossViewportBreakpoint(false))
    act(() => crossViewportBreakpoint(true))

    expect(isExpanded()).toBe(false)
  })

  test('Escape closes the nav from the toggle — where focus actually is after opening it', async () => {
    setLargeViewport(false)
    renderShell()

    const toggle = screen.getByRole('button', { name: 'Open menu' })
    await userEvent.click(toggle)
    expect(isExpanded()).toBe(true)

    // Focus is on the hamburger, NOT inside the panel. Carbon's own Escape handler lives on the
    // <nav>, so it never sees this — an earlier version of this test typed Escape straight into the
    // nav and passed while the real browser did nothing.
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Close menu' }))
    await userEvent.keyboard('{Escape}')

    expect(isExpanded()).toBe(false)
  })

  test('Escape also closes the nav when focus is inside the panel', async () => {
    setLargeViewport(true)
    renderShell()

    screen.getByRole('link', { name: 'Check Status' }).focus()
    await userEvent.keyboard('{Escape}')

    expect(isExpanded()).toBe(false)
  })

  test('isPersistent tracks expansion — it is what puts the panel beside the page, not over it', () => {
    setLargeViewport(true)
    renderShell()

    // Carbon adds `--hidden` when isPersistent is false; the whole lg+ mode rests on this binding.
    expect(sideNav().classList.contains('cds--side-nav--hidden')).toBe(false)
  })

  test('toggling does not remount the header, so keyboard focus survives', async () => {
    setLargeViewport(true)
    renderShell()

    const headerBefore = document.querySelector('.cds--header')
    const toggle = screen.getByRole('button', { name: 'Close menu' })
    toggle.focus()

    await userEvent.click(toggle)

    // Carbon's HeaderContainer takes `render` as a component TYPE, so an inline arrow defined inside
    // a component that re-renders on toggle gives a new element type every time and remounts the
    // whole header — destroying the button mid-press and dropping focus to <body>. Keeping the
    // render prop at module scope is what holds this together; nothing else would catch it.
    expect(document.querySelector('.cds--header')).toBe(headerBefore)
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Open menu' }))
  })

  test('Escape outside the header is left alone — a Carbon Modal keeps its own Escape', async () => {
    setLargeViewport(true)
    const { container } = renderShell()

    // A Carbon Modal traps focus inside its own dialog, so scoping our window-level handler to
    // `.cds--header` is what stops the side nav swallowing the Escape that should close the dialog.
    const outside = container.querySelector('.app-content') as HTMLElement
    outside.setAttribute('tabindex', '-1')
    outside.focus()
    await userEvent.keyboard('{Escape}')

    expect(isExpanded()).toBe(true)
  })

  test('a key other than Escape does not close the nav', async () => {
    setLargeViewport(true)
    renderShell()

    screen.getByRole('button', { name: 'Close menu' }).focus()
    await userEvent.keyboard('{ArrowDown}')

    expect(isExpanded()).toBe(true)
  })

  test('the provider unsubscribes from matchMedia on unmount', () => {
    setLargeViewport(true)
    const { unmount } = renderShell()
    expect(activeViewportListenerCount()).toBeGreaterThan(0)

    unmount()

    expect(activeViewportListenerCount()).toBe(0)
  })
})
