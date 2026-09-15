import type { MessageInfo, ScheduleCheckResult, ScheduleCode } from '@/interfaces/CheckStatusSweep'
import type CheckStatusResponse from '@/interfaces/CheckStatusResponse'
import type { CheckStatusResponse as Schedule2CheckStatusResponse } from '@/interfaces/Schedule2Response'
import type { Schedule4CheckStatusResponse } from '@/interfaces/Schedule4Response'
import type { Schedule5CheckStatusResponse } from '@/interfaces/Schedule5Response'
import type { Schedule6CheckStatusResponse } from '@/interfaces/Schedule6Response'
import type { CheckFieldIssue, Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import { labelFor } from '@/components/schedule4/validation'
import { summariseCheckStatus } from '@/components/schedule10/checkStatus'

/**
 * The legacy accordion tab titles, verbatim from checkStatus.xhtml (:68-:110, :149) — minus the
 * trailing space inside Schedule 4's title attribute and with Schedule 2's `&amp;` decoded. No bundle
 * key exists for these; legacy hardcoded them in the XHTML.
 */
export const SCHEDULE_TITLES: Readonly<Record<ScheduleCode, string>> = {
  '1': 'Schedule 1 - Average Cost of Logging',
  '2': 'Schedule 2 - Log Costs & Log Sales',
  '3': 'Schedule 3 - Forest Mgmt Admin Costs',
  '4': 'Schedule 4 - Special Log Transportation Systems',
  '5': 'Schedule 5 - Camp and Access Expense',
  '6': 'Schedule 6 - Road Maintenance',
  '7A': 'Schedule 7A - Bridge Costs',
  '7B': 'Schedule 7B - Culvert Costs',
  '8': 'Schedule 8 - Tree to Truck and Log Hauling',
  '9': 'Schedule 9 - Miscellaneous/Unique Logging Costs',
  '10': 'Schedule 10 - New Road Construction',
  '11': 'Schedule 11 - Basic Silviculture – Other Silviculture',
}

/** The flat shape the shared result renderer takes: the lines this page shows for one schedule. */
export type FlatVerdict = {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly warnings: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
}

const NONE: readonly MessageInfo[] = []

const flat = (
  requirementsMet: boolean,
  errors: readonly MessageInfo[],
  requirementsMetMessage: MessageInfo | null | undefined,
  warnings: readonly MessageInfo[] = NONE,
): FlatVerdict => ({
  requirementsMet,
  errors,
  warnings,
  requirementsMetMessage: requirementsMetMessage ?? null,
})

/** Schedules 1 and 3: the met text rides as `message` (omitted on fail), plus advisory `warnings`. */
const flattenSchedule1Or3 = (verdict: CheckStatusResponse): FlatVerdict =>
  flat(verdict.requirementsMet, verdict.errors, verdict.message, verdict.warnings ?? NONE)

/**
 * The `outcome` family's met line: `messages[0]` on MET, nothing otherwise. On ISSUES `messages` is
 * empty for the entity-bearing schedules and carries the errors for Schedule 2, so the caller decides
 * what ISSUES means.
 */
const metLine = (verdict: {
  outcome: string
  messages: readonly MessageInfo[]
}): MessageInfo | null => (verdict.outcome === 'MET' ? (verdict.messages[0] ?? null) : null)

/** Schedule 2 has no entity list: on ISSUES its `messages` ARE the failing lines. */
const flattenSchedule2 = (verdict: Schedule2CheckStatusResponse): FlatVerdict =>
  verdict.outcome === 'MET'
    ? flat(true, NONE, metLine(verdict))
    : flat(false, verdict.messages, null)

/**
 * Schedule 4 ships the bare bundle text and the cost-item code; the location, the category and the
 * field are composed here, as legacy's Check Status tab did (`Lake Side Dry Dump - Cost`,
 * checkStatusSchedule4.xhtml:52-58) with the unit legacy's Schedule 1 rows carry (`Cost $`). The
 * backend reports a missing COST and nothing else (Schedule4Service.checkStatus), so the field is a
 * constant. An unknown code names the location and the field only — never a fabricated category.
 * Met locations render nothing (legacy listed failing rows only).
 */
const SCHEDULE_4_FIELD = 'Cost $'

const flattenSchedule4 = (verdict: Schedule4CheckStatusResponse): FlatVerdict => {
  if (verdict.outcome === 'MET') {
    return flat(true, NONE, metLine(verdict))
  }
  const errors = verdict.locations
    .filter((location) => !location.met)
    .flatMap((location) =>
      location.issues.map((issue) => {
        const label = labelFor(issue.code)
        const where = label === undefined ? location.name : `${location.name} - ${label}`
        return { ...issue.message, text: `${where} - ${SCHEDULE_4_FIELD}: ${issue.message.text}` }
      }),
    )
  return flat(false, errors, null)
}

/**
 * Schedule 5's findings are a three-component record ({key, field, text}), not MessageInfo; they are
 * read structurally and carried as {key, text}. The text is already composed server-side. Met camps
 * render nothing.
 */
const flattenSchedule5 = (verdict: Schedule5CheckStatusResponse): FlatVerdict => {
  if (verdict.outcome === 'MET') {
    return flat(true, NONE, metLine(verdict))
  }
  const errors = verdict.camps
    .filter((camp) => !camp.requirementsMet)
    .flatMap((camp) => camp.messages.map(({ key, text }) => ({ key, text })))
  return flat(false, errors, null)
}

/** Schedule 6: text already composed; met records (and their `metMessage`) render nothing. */
const flattenSchedule6 = (verdict: Schedule6CheckStatusResponse): FlatVerdict => {
  if (verdict.outcome === 'MET') {
    return flat(true, NONE, metLine(verdict))
  }
  const errors = verdict.records
    .filter((record) => !record.met)
    .flatMap((record) => record.issues.map((issue) => issue.message))
  return flat(false, errors, null)
}

/** `Page # 1 - Sample # 2 - Cut Block: Value Required` — the where, the field label, the bare text. */
const attribute = (where: string, issue: CheckFieldIssue): MessageInfo => ({
  ...issue.message,
  text: `${where} - ${issue.field}: ${issue.message.text}`,
})

/**
 * Schedule 8 ships the bare text, a human field label and a DB id per page/sample — no page label.
 * Attribution is POSITIONAL (pages arrive in the order the Schedule 8 page numbers them), interim
 * until the verdict carries the legacy page title. The raw id is never rendered. `pages` is populated
 * on MET too, so the branch is on `outcome`, never on the list.
 */
const flattenSchedule8 = (verdict: Schedule8CheckStatusResponse): FlatVerdict => {
  if (verdict.outcome === 'MET') {
    return flat(true, NONE, metLine(verdict))
  }
  const errors = verdict.pages.flatMap((page, pageIndex) => {
    const pageLabel = `Page # ${String(pageIndex + 1)}`
    return [
      ...page.issues.map((issue) => attribute(pageLabel, issue)),
      ...page.samples.flatMap((sample, sampleIndex) =>
        sample.issues.map((issue) =>
          attribute(`${pageLabel} - Sample # ${String(sampleIndex + 1)}`, issue),
        ),
      ),
    ]
  })
  return flat(false, errors, null)
}

/**
 * Legacy's Check Status tabs labelled these fields WITH their unit — `Standing Tree to Loaded Truck -
 * Volume m³` / `- Cost $` (checkStatusSchedule1.xhtml:8-11), `Purchased/Private Log Costs - Cost $`
 * (checkStatusSchedule2.xhtml), `Associated Camp Volume (m³)` (checkStatusSchedule5.xhtml) — while the
 * schedule pages' own messages, which the API reuses for the sweep, do not. Legacy was inconsistent
 * between the two and this page follows its Check Status tabs (Scho, 2026-09-14). Applied to this page's
 * lines only; every other schedule's text already carries the unit legacy showed, or legacy showed none.
 * Anchored on the exact ` - <field>: ` segment so a line that already names a unit is never touched.
 */
const LEGACY_UNIT_LABELS: Partial<Record<ScheduleCode, readonly (readonly [RegExp, string])[]>> = {
  '1': [
    [/ - Volume: /, ' - Volume m³: '],
    [/ - Cost: /, ' - Cost $: '],
  ],
  '2': [[/ - Cost: /, ' - Cost $: ']],
  '5': [[/ - Associated Camp Volume: /, ' - Associated Camp Volume (m³): ']],
}

const withLegacyUnits = (
  code: ScheduleCode,
  errors: readonly MessageInfo[],
): readonly MessageInfo[] => {
  const rules = LEGACY_UNIT_LABELS[code]
  if (!rules) {
    return errors
  }
  return errors.map((error) => ({
    ...error,
    // Defensive: the wire types `text` as a string, but a resolver that ever left it unset must not
    // take the whole page down for one line.
    text: rules.reduce(
      (text, [pattern, unit]) => text.replace(pattern, () => unit),
      error.text ?? '',
    ),
  }))
}

const flattenLines = (entry: ScheduleCheckResult): FlatVerdict => {
  switch (entry.schedule) {
    case '1':
    case '3':
      return flattenSchedule1Or3(entry.verdict)
    case '2':
      return flattenSchedule2(entry.verdict)
    case '4':
      return flattenSchedule4(entry.verdict)
    case '5':
      return flattenSchedule5(entry.verdict)
    case '6':
      return flattenSchedule6(entry.verdict)
    case '8':
      return flattenSchedule8(entry.verdict)
    case '10': {
      const summary = summariseCheckStatus(entry.verdict)
      return flat(summary.requirementsMet, summary.errors, summary.requirementsMetMessage)
    }
    case '7A':
    case '7B':
    case '9':
    case '11':
      return flat(
        entry.verdict.requirementsMet,
        entry.verdict.errors,
        entry.verdict.requirementsMetMessage,
      )
  }
}

/**
 * Flatten one sweep entry into the lines this page renders for it. Legacy showed failing rows only,
 * and a met schedule showed the single schedule-wide line — so per-entity met messages (Schedule 4
 * locations, Schedule 5 camps, Schedule 6 records, Schedule 7A's `bridgeMessages`) are not carried,
 * and Schedule 11's always-present "Status has been checked" is its own page's post-click text, not
 * this one's.
 */
export const flattenVerdict = (entry: ScheduleCheckResult): FlatVerdict => {
  const lines = flattenLines(entry)
  return { ...lines, errors: withLegacyUnits(entry.schedule, lines.errors) }
}
