import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { ESLint } from 'eslint'
import { beforeAll, describe, expect, test } from 'vitest'

/**
 * Proof that the #321 lint rule fires — and only where it should.
 *
 * The rule itself lives in `eslint.config.mjs` (`no-restricted-syntax`, the selector commented
 * "#321"). Carbon's `TableContainer` renders its `title` as a heading and hands the heading's id to
 * the `Table` through context, which sets `aria-labelledby` on the `<table>`
 * (@carbon/react DataTable/TableContainer.js, Table.js). Per the accessible-name computation
 * `aria-labelledby` wins over `aria-label`, so a `<Table aria-label>` inside a
 * `<TableContainer title>` is dead: never heard, and free to drift from the live name. Ten tables
 * carried one when #321 was fixed; `npm run lint` (gated in CI by analysis.yml) now fails the next.
 *
 * WHY THIS FILE EXISTS. A lint rule that matches nothing passes every run, silently. The first draft
 * of this rule did exactly that — esquery does not honour a chained `:has(> A > B)` — and only a
 * mutation run caught it. So this test lints TSX fixtures through the project's real config and
 * asserts the rule fires on the dead shapes and stays quiet on the live ones, including the
 * "valid TSX" shapes the PR #507 review raised against the regex scan this replaced:
 *
 *   - a `>` inside an earlier prop (`isSortable={n > 0}`, an arrow function, `->` in a title)
 *   - whitespace around `=`, template-literal values, a Table nested deeper in the container
 *   - an untitled container: there the `aria-label` IS the accessible name and must stay
 *   - a titled and an untitled container side by side in one tree (a descendant `:has` would
 *     wrongly flag the untitled one — this is what the nested `:has(> …)` buys)
 *
 * It then mutates a real component in memory — re-adding the exact attribute #321 removed — and
 * asserts the rule names that line. That is the "still finds the tables it polices" check the old
 * source scan had, tied to the real shape instead of a count.
 */
const FRONTEND_ROOT = resolve(__dirname, '../../..')
const RULE_TAG = '#321'

let eslint: ESLint

/** Lint `code` as a component file and return the #321 messages. */
async function dead321(code: string, filePath = 'src/components/__fixture__/Fixture.tsx') {
  const [result] = await eslint.lintText(code, { filePath: resolve(FRONTEND_ROOT, filePath) })
  return result.messages.filter(
    (m) => m.ruleId === 'no-restricted-syntax' && m.message.includes(RULE_TAG),
  )
}

const component = (jsx: string) => `export const Fixture = () => (${jsx})\n`

beforeAll(() => {
  eslint = new ESLint({ cwd: FRONTEND_ROOT })
})

describe('the #321 lint rule: <Table aria-label> inside <TableContainer title>', () => {
  test.each<[string, string, number]>([
    [
      'plain',
      `<TableContainer title="Roads"><Table aria-label="Roads rows" /></TableContainer>`,
      1,
    ],
    [
      'a `>` in an earlier Table prop',
      `<TableContainer title="Roads"><Table isSortable={items.length > 0} aria-label="Roads rows" /></TableContainer>`,
      1,
    ],
    [
      'an arrow function in an earlier container prop and `->` in a template-literal title',
      `<TableContainer description={(v) => v > 0} title={\`\${page.label} -> Roads\`}><Table aria-label={\`\${def.label} rows\`} /></TableContainer>`,
      1,
    ],
    [
      'spaces around `=`',
      `<TableContainer title = "Roads"><Table aria-label = "Roads rows" /></TableContainer>`,
      1,
    ],
    [
      'the Table nested deeper in the container',
      `<TableContainer title="Roads"><div><Table aria-label="x"><TableHead /></Table></div></TableContainer>`,
      1,
    ],
    [
      'two tables in one titled container — both reported',
      `<TableContainer title="Roads"><Table aria-label="x" /><Table aria-label="y" /></TableContainer>`,
      2,
    ],
  ])('fires on a dead label: %s', async (_name, jsx, count) => {
    await expect(dead321(component(jsx))).resolves.toHaveLength(count)
  })

  test.each<[string, string]>([
    [
      'an untitled container — the label is the name',
      `<TableContainer><Table aria-label="Roads rows" /></TableContainer>`,
    ],
    [
      'a self-closing untitled container before the one that holds the table',
      `<><TableContainer /><TableContainer><Table aria-label="Roads rows" /></TableContainer></>`,
    ],
    [
      'a titled container beside an untitled one in the same tree',
      `<div><TableContainer title="A"><Table /></TableContainer><TableContainer><Table aria-label="B rows" /></TableContainer></div>`,
    ],
    [
      'a titled container whose table has no label',
      `<TableContainer title="A"><Table /></TableContainer>`,
    ],
    [
      'TableHead is not Table',
      `<TableContainer title="A"><Table><TableHead aria-label="h" /></Table></TableContainer>`,
    ],
    [
      'a `title` on an element inside a prop, not on the container',
      `<TableContainer description={<span title="t" />}><Table aria-label="x" /></TableContainer>`,
    ],
  ])('stays quiet on a live label: %s', async (_name, jsx) => {
    await expect(dead321(component(jsx))).resolves.toEqual([])
  })

  test('a real component: clean as committed, flagged at the line if the attribute comes back', async () => {
    // Schedule 4's sub-page table — `<TableContainer title={def.label}>` wrapping `<Table>` — carried
    // aria-label={`${def.label} rows`} until #321. Re-adding it in memory must fail at that line.
    const file = 'src/components/schedule4/SubPage.tsx'
    const source = readFileSync(resolve(FRONTEND_ROOT, file), 'utf8')
    const container = source.indexOf('<TableContainer title=')
    const table = source.indexOf('<Table>', container)
    expect(
      container,
      'the fixture component no longer has a titled TableContainer',
    ).toBeGreaterThan(-1)
    expect(table, 'the fixture component no longer has a bare <Table> under it').toBeGreaterThan(-1)

    await expect(dead321(source, file)).resolves.toEqual([])

    const mutated = `${source.slice(0, table)}<Table aria-label={\`\${def.label} rows\`}>${source.slice(table + '<Table>'.length)}`
    const line = source.slice(0, table).split('\n').length
    const hits = await dead321(mutated, file)
    expect(hits.map((m) => m.line)).toEqual([line])
  })
})
