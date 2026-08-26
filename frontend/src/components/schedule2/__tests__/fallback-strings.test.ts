import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A tripwire over Schedule 2's two page-owned error-fallback strings and the cases that cover them,
 * added by the defect #298 code review. It follows the pattern established by
 * `schedule7a/__tests__/layout-rules.test.ts` and `styles/__tests__/overrides.test.ts`.
 *
 * READ THIS BEFORE TRUSTING IT. It is not coverage of anything, and it is deliberately narrow:
 *
 *   - It asserts TEXT in two files, not behaviour. `Schedule2.test.tsx` is what proves the fallbacks
 *     actually render; this only fails when one of those things stops existing.
 *   - It CANNOT fail on a weakened guard. Swap `||` for `??` at either site and this file stays
 *     green — that regression is caught by the `BLANK_DETAIL` shape in `Schedule2.test.tsx`, which
 *     is why that shape must not be removed.
 *   - It cannot fail on a wrong `kind`, a wrong title, or a reworded fallback that is reworded
 *     consistently in both files.
 *
 * Made formatting-agnostic by the PR #364 review (SScholefield), which was right that the first
 * version was brittle: it matched `'…'` with the quotes baked in, so a Prettier `singleQuote` flip
 * would have failed it while the app worked, and it matched `problemBody(500, ` as a literal, which a
 * line wrap would have broken. Quotes are no longer part of any pattern and the call is matched with a
 * whitespace-tolerant regex. A tripwire that cries wolf gets deleted, which costs more than the
 * brittleness itself.
 *
 * NOT adopted from that review: exporting these strings from `index.tsx` and importing them into
 * `Schedule2.test.tsx` in place of this file. Tested rather than argued — with the constants imported,
 * rewording BOTH fallbacks to 'Oops.' left the whole spec GREEN (33/33) and `tsc --noEmit` said
 * nothing, because the assertion then compares the rendered text against the very constant that
 * produced it. TypeScript checks that an identifier resolves, never a string's value. The literal
 * spellings in `Schedule2.test.tsx` are what make that mutation fail (8 tests), and they must stay.
 *
 * Why it exists: `frontend/vitest.config.ts` sets coverage reporters but no thresholds, and CI runs
 * `npm run test:cov` (`analysis.yml`). So CI fails if a fallback string breaks — and passes if the
 * five cases covering it are deleted or renamed, while
 * `e2e/features/sch2/uc-sch2-001-report-costs/coverage.md` goes on asserting `covered (unit)`. That
 * is a coverage record certifying coverage that no longer exists. Part (b) below is what makes the
 * record self-defending; part (a) catches a silent reword of the source.
 */
const read = (relative: string) => readFileSync(resolve(__dirname, relative), 'utf8')

const LOAD_FALLBACK = 'Unable to load Schedule 2.'
const DELETE_FALLBACK = 'Unable to delete Schedule 2.'

/** The string as a source literal, in any quote style: 'x' | "x" | `x`. */
const asLiteral = (text: string) =>
  new RegExp(`['"\`]${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}['"\`]`)

describe('Schedule 2 error-fallback tripwire (defect #298)', () => {
  // (a) The strings still exist at their sites in the page.
  test('index.tsx still carries both page-owned fallback strings', () => {
    const source = read('../index.tsx')
    // Quote-agnostic: the assertion is about the string being there, not how it is delimited.
    expect(source).toMatch(asLiteral(LOAD_FALLBACK))
    expect(source).toMatch(asLiteral(DELETE_FALLBACK))
  })

  // (b) The suite still exercises them. This is the half that guards the coverage record: deleting
  // the cases in Schedule2.test.tsx leaves (a) green and CI silent.
  test('Schedule2.test.tsx still exercises both fallbacks and the blank-detail shape', () => {
    const spec = read('./Schedule2.test.tsx')
    expect(spec).toContain(LOAD_FALLBACK)
    expect(spec).toContain(DELETE_FALLBACK)
    // The shape that pins `||` rather than `??`. See the note in that file's helper block.
    expect(spec).toContain('BLANK_DETAIL')
    // Whitespace-tolerant, so a line wrap cannot fail this: problemBody(500, '') across any breaks.
    // The `,?` matters: Prettier adds a trailing comma when it wraps a call, and the first version
    // of this regex omitted it — so the very reformatting this pattern exists to tolerate broke it.
    // Found by reformatting the call and re-running, not by reading.
    expect(spec).toMatch(/problemBody\(\s*500\s*,\s*['"`]['"`]\s*,?\s*\)/)
  })
})
