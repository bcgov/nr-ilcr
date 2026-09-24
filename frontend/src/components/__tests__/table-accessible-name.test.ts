import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A tripwire for defect #321: a `<Table aria-label>` inside a `<TableContainer title>` is dead code.
 *
 * Carbon's `TableContainer` renders its `title` as a heading and hands the heading's id to the
 * `Table` through context, which sets `aria-labelledby` on the `<table>`
 * (@carbon/react DataTable/TableContainer.js, Table.js). Per the accessible-name computation
 * `aria-labelledby` wins over `aria-label`, so the label never reaches assistive technology. Eight
 * tables carried one when #321 was raised and two more (Schedule 10) had appeared by the time it was
 * fixed — two of the ten with a label that had drifted from the live name. This fails on the next one.
 *
 * READ THIS BEFORE TRUSTING IT. It is a source scan, in the pattern of
 * `schedule2/__tests__/fallback-strings.test.ts` and `schedule7a/__tests__/layout-rules.test.ts`:
 *
 *   - It matches TEXT, not the rendered tree. "Inside" means: the nearest `<TableContainer` opening
 *     tag before the `<Table` tag has not been closed by a `</TableContainer>` in between. That is
 *     right for every table in this codebase today and obvious when it stops being right.
 *   - It says nothing about tables whose container has NO title. There the `aria-label` IS the
 *     accessible name (the ten tables #321 lists as correct) and it must stay.
 *   - It cannot tell a good name from a bad one. It only fails when a name is declared that cannot
 *     be heard.
 */
const COMPONENTS_DIR = resolve(__dirname, '..')

function componentSources(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry !== '__tests__') {
        out.push(...componentSources(full))
      }
    } else if (entry.endsWith('.tsx') && !entry.endsWith('.test.tsx')) {
      out.push(full)
    }
  }
  return out
}

interface TitledTable {
  where: string
  ariaLabel: string | null
}

/** Every `<Table …>` that sits inside a `<TableContainer …title=…>` in `source`. */
function titledTables(source: string, file: string): TitledTable[] {
  const found: TitledTable[] = []
  // `<Table` followed by whitespace, `>` or `/` — not `<TableHead`, `<TableRow`, `<TableContainer`.
  const tableTag = /<Table(?=[\s>/])([^>]*)>/g
  for (const match of source.matchAll(tableTag)) {
    const before = source.slice(0, match.index)
    const containerStart = before.lastIndexOf('<TableContainer')
    if (containerStart < 0) {
      continue
    }
    if (before.indexOf('</TableContainer>', containerStart) >= 0) {
      continue
    }
    const containerTag = before.slice(containerStart, before.indexOf('>', containerStart) + 1)
    if (!/\btitle=/.test(containerTag)) {
      continue
    }
    const attrs = match[1]
    const label = /aria-label=(\{[^}]*\}|"[^"]*"|'[^']*')/.exec(attrs)
    const line = before.split('\n').length
    found.push({ where: `${file}:${line}`, ariaLabel: label ? label[1] : null })
  }
  return found
}

describe('data tables inside a titled TableContainer (defect #321)', () => {
  const tables = componentSources(COMPONENTS_DIR).flatMap((full) =>
    titledTables(readFileSync(full, 'utf8'), relative(COMPONENTS_DIR, full)),
  )

  test('the scan still finds the titled tables it exists to police', () => {
    // A rename of `TableContainer`/`Table`, or a scan bug, would otherwise pass the assertion below
    // vacuously. Ten titled tables exist at the time of writing (the eight in #321 plus Schedule 10's
    // two); the floor is lower so that removing a table is not a failure of this file.
    expect(tables.length).toBeGreaterThanOrEqual(8)
  })

  test('none of them declares an aria-label the container title would override', () => {
    const dead = tables.filter((t) => t.ariaLabel !== null).map((t) => `${t.where} ${t.ariaLabel}`)
    expect(dead).toEqual([])
  })
})
