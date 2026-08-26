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

describe('Schedule 2 error-fallback tripwire (defect #298)', () => {
  // (a) The strings still exist at their sites in the page.
  test('index.tsx still carries both page-owned fallback strings', () => {
    const source = read('../index.tsx')
    expect(source).toContain(`'${LOAD_FALLBACK}'`)
    expect(source).toContain(`'${DELETE_FALLBACK}'`)
  })

  // (b) The suite still exercises them. This is the half that guards the coverage record: deleting
  // the cases in Schedule2.test.tsx leaves (a) green and CI silent.
  test('Schedule2.test.tsx still exercises both fallbacks and the blank-detail shape', () => {
    const spec = read('./Schedule2.test.tsx')
    expect(spec).toContain(LOAD_FALLBACK)
    expect(spec).toContain(DELETE_FALLBACK)
    // The shape that pins `||` rather than `??`. See the note in that file's helper block.
    expect(spec).toContain('BLANK_DETAIL')
    expect(spec).toContain('problemBody(500, ')
  })
})
