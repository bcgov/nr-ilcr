import type MillSummary from '@/interfaces/MillSummary'

/**
 * Rule placement for the Data Extract page (AD-6).
 *
 * **There is deliberately no client-side refusal here, and that is the rule this module records.**
 * The screen's defining behaviour is that every failing check arrives together on one submit, and
 * the server owns those checks and their verbatim text. A client gate would make that response
 * unreachable: the blank-selection case could never be submitted, so the messages it exists to
 * produce could never be seen. It would also have to restate server-owned text on the client, which
 * is the exact defect already recorded against a shipped sibling page.
 *
 * What lives here instead is the pure, testable shaping the page needs on the way OUT: how a mill
 * names itself, what the summary echoes, and how a selection becomes the pinned request body.
 */

/** The eleven picker options. ELEVEN, not twelve: Schedule 7 is one choice covering 7A and 7B. */
export const SCHEDULE_OPTIONS: readonly string[] = Array.from(
  { length: 11 },
  (_unused, index) => `Schedule ${String(index + 1)}`,
)

/** Absent for a nullable text column: null, undefined, empty, or whitespace-only. */
const isBlank = (value: string | null | undefined): boolean =>
  value === null || value === undefined || value.trim() === ''

/**
 * The mill's number, or null when it has none.
 *
 * Whitespace-only counts as none. `MILL_NUMBER` is nullable with no non-blank constraint, and `??`
 * alone would pass `'   '` through as if it were a value — which would put an invisible entry in
 * the summary echo.
 */
export const millNumberOrNull = (mill: MillSummary): string | null =>
  isBlank(mill.millNumber) ? null : (mill.millNumber as string).trim()

/**
 * A mill option's label, `"<number> - <name>"` verbatim from legacy (`extractData.xhtml:81`).
 *
 * Either column may be absent, so the label is built from the parts that are actually there rather
 * than formatted blindly — a blind template renders `" - "` for a mill with neither, which is an
 * option the administrator cannot identify or even see. The mill id is never otherwise shown and
 * appears only in that last-resort case.
 */
export const millOptionLabel = (mill: MillSummary): string => {
  const parts = [millNumberOrNull(mill), isBlank(mill.millName) ? null : mill.millName?.trim()]
  const label = parts
    .filter((part): part is string => part !== null && part !== undefined)
    .join(' - ')
  return label === '' ? `Mill ${String(mill.millId)}` : label
}

/**
 * Prefix matching, case-insensitive — legacy's `filterMatchMode="startsWith"` on both pickers
 * (`extractData.xhtml:75`, `:98`).
 *
 * Prefix rather than substring is a real observable behaviour, not a detail. It is matched against
 * the whole LABEL, as legacy's was: typing "Schedule 1" offers Schedule 1, 10 and 11 — three
 * options where an exact match gives one — while a bare "1" offers none, because no label begins
 * with a digit. Switching to a substring match would look like an improvement and would be a silent
 * parity change.
 */
export const matchesPrefix = (label: string, query: string): boolean =>
  label.toLocaleLowerCase().startsWith(query.trim().toLocaleLowerCase())

/**
 * The summary echo for the mills: their NUMBERS, comma-space separated (legacy
 * `printSelectedMills`, `ExtractDataMB.java:201-211` — ids in the model, numbers on screen).
 *
 * A mill with no number contributes nothing rather than an empty slot, so the echo never shows a
 * stray ", ,". Legacy would have thrown on that row instead.
 */
export const millNumberEcho = (mills: readonly MillSummary[]): string =>
  mills
    .map(millNumberOrNull)
    .filter((number): number is string => number !== null)
    .join(', ')

/** The selection as the page holds it, before it becomes a request. */
export type ExtractSelection = {
  readonly startYear: string
  readonly endYear: string
  readonly mills: readonly MillSummary[]
  readonly schedules: readonly string[]
}

/** The pinned request body (AD-12). */
export type DataExtractRequest = {
  readonly startYear: string
  readonly endYear: string
  readonly millIds: number[]
  readonly schedules: string[]
}

/**
 * Shape a selection into the pinned request.
 *
 * The years travel as raw STRINGS, empty included. A blank year has to stay distinguishable from a
 * chosen one all the way to the server, which is what lets it answer with the verbatim required
 * message instead of a framework type error.
 */
export const buildExtractRequest = (selection: ExtractSelection): DataExtractRequest => ({
  startYear: selection.startYear,
  endYear: selection.endYear,
  millIds: selection.mills.map((mill) => mill.millId),
  schedules: [...selection.schedules],
})
