import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A source tripwire over the heading scale (#411 Overall 11), following the pattern of
 * `styles/__tests__/overrides.test.ts` and `components/__tests__/summary-row-bold.test.ts`.
 *
 * READ THIS BEFORE TRUSTING IT. Vitest runs in jsdom with `css: false`, so no stylesheet is compiled
 * and nothing here observes a rendered font size. It asserts the SOURCE: that every heading element
 * in the app is accounted for by one of the documented tiers.
 *
 * Why it exists. The 1.25rem section-heading rule names its selectors in a hand-maintained list, and
 * Carbon resets bare headings to its own scale (h2 2rem / h3 1.75rem / h4 1.25rem). So a heading whose
 * class nobody added to that list does not fall back to the app standard — it silently renders at
 * Carbon's size for whichever level the author picked. That is exactly how nine section headings came
 * to sit at 1.75rem, 40% over the standard, with the same "Comments" heading reading 28px on Schedule
 * 1 and 20px on Schedule 2. The list is the fix's one remaining maintenance burden; this guards it.
 *
 * Adding a heading? Put its class in the 1.25rem rule in `styles/index.scss`, or — if it is genuinely
 * a subordinate tier — in SUBORDINATE below, with the size it takes and why.
 */
const SRC = resolve(process.cwd(), 'src')

/**
 * Page-identity headings — the h1 each page header renders. NOT the section tier.
 *
 * The two differ by design, and it is recorded rather than silently allowed. The tombstone is 2rem,
 * matching CSP's own PageTitle (nr-csp core/PageTitle/index.scss:24), and EVERY schedule now uses it
 * — Schedule 6 was the last one on `PageTitle`, where its title rendered at half its siblings' size
 * until #411 Overall 11 moved it across. `page-title-heading` stays 1rem, sitting with the
 * working-context text beside it, and now covers only the non-schedule screens (Home, Dashboard,
 * Placeholder and the error pages). So the split is by SCREEN KIND, not an accident of which header
 * component a page happened to pick.
 */
const PAGE_TITLE: Record<string, string> = {
  'schedule-tombstone__title': '2rem (heading-05)',
  'page-title-heading': '1rem',
}

/** Heading classes that deliberately sit BELOW the section-heading tier, with their size. */
const SUBORDINATE: Record<string, string> = {
  // A label rendered as a heading element, so it takes the label tier, not a heading one.
  'schedule-2__comments-label': '0.875rem',
  // Panel / table heading — 1rem, matching CSP's EditableLineItemsTable heading.
  'sub-panel__title': '1rem',
  'schedule-8__subheading': '1rem',
  'schedule-10__detail-heading': '1rem',
  'schedule-5-sub-page__panel-heading': '1rem',
  // One tier lower again — 0.875rem, matching CSP's Invoice subsection heading.
  'schedule-10__detail-subheading': '0.875rem',
}

/**
 * Files allowed to use an UNCLASSED heading, which takes Carbon's raw scale. The Dashboard is a
 * landing screen and keeps Carbon's sizes on purpose — CSP's own landing page does the same, so
 * normalising it would move away from the reference app. The other two are error/empty states.
 */
const BARE_HEADING_FILES = new Set([
  'components/Dashboard.tsx',
  'components/core/EmptySection/index.tsx',
  'context/auth/RealAuthProvider.tsx',
])

function tsxFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) {
      if (entry !== '__tests__') tsxFiles(path, out)
    } else if (entry.endsWith('.tsx') && !entry.includes('.test.')) {
      out.push(path)
    }
  }
  return out
}

/** The selector list of the app-wide 1.25rem section-heading rule. */
function sectionHeadingClasses(): Set<string> {
  const source = readFileSync(join(SRC, 'styles/index.scss'), 'utf8')
    .replace(/\/\/[^\n]*/g, '')
    // Flatten SCSS interpolations (`.#{$bcgov-prefix}--data-table-header__title`) BEFORE matching:
    // their braces otherwise terminate the selector-list scan early and silently return a short list,
    // which would make the assertions below vacuous rather than failing loudly.
    .replace(/#\{[^}]*\}/g, 'cds')
  // The rule is the one declaring `font-size: 1.25rem` at the top level.
  const rule = /((?:\s*\.[^,{]+,)+\s*\.[^,{]+)\{\s*font-size:\s*1\.25rem/.exec(source)
  const selectors = rule?.[1] ?? ''
  return new Set(
    [...selectors.matchAll(/\.([a-z0-9_-]+)/gi)].map((match) => match[1]).filter(Boolean),
  )
}

describe('heading scale (source tripwire, not a behaviour test — #411 Overall 11)', () => {
  const listed = sectionHeadingClasses()

  test('the app-wide section-heading rule is present and covers the schedules', () => {
    // Guards the extraction above as much as the rule: an empty match would make every other
    // assertion in this file vacuous.
    expect(listed.size).toBeGreaterThan(10)
    for (const schedule of ['1', '2', '3', '4', '6', '9', '10', '11']) {
      expect(listed, `Schedule ${schedule}`).toContain(`schedule-${schedule}__heading`)
    }
    for (const cls of ['schedule-5__heading', 'schedule-7a__heading', 'schedule-7b__heading']) {
      expect(listed).toContain(cls)
    }
  })

  test('every heading in the app is on the scale or documented as subordinate', () => {
    const unaccounted: string[] = []
    for (const file of tsxFiles(SRC)) {
      const rel = file.slice(SRC.length + 1).replace(/\\/g, '/')
      const source = readFileSync(file, 'utf8')
      for (const match of source.matchAll(/<h[1-6](\s+className="([^"]+)")?[\s/>]/g)) {
        const classes = match[2]
        if (classes === undefined) {
          if (!BARE_HEADING_FILES.has(rel)) unaccounted.push(`${rel}: unclassed <h*>`)
          continue
        }
        // A heading is accounted for if ANY of its classes is on the scale or subordinate.
        const known = classes
          .split(/\s+/)
          .some(
            (cls) =>
              listed.has(cls) || Object.hasOwn(SUBORDINATE, cls) || Object.hasOwn(PAGE_TITLE, cls),
          )
        if (!known) unaccounted.push(`${rel}: ${classes}`)
      }
    }
    expect(
      unaccounted,
      'Headings not covered by the 1.25rem rule in styles/index.scss nor listed as a subordinate ' +
        'tier. An unlisted heading does not inherit the app standard — it takes Carbon’s size ' +
        'for its element level (h2 2rem / h3 1.75rem), which is the #411 defect. Add the class to ' +
        'the rule, or to SUBORDINATE with its size and reason.',
    ).toEqual([])
  })
})
