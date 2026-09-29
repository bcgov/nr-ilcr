// The top-of-page validation banner, as legacy rendered it (#359 group B, Iman 2026-09-28).
//
// When a JSF field validator blocked Save or Check Status, legacy listed ONE message per failing
// field in the page's `p:messages` banner. A required field that was left blank reported JSF's
// `UIInput.REQUIRED` override, `{0}: Value is required.` (`common/validation.properties:11`), where
// `{0}` is the input's `label` attribute exactly as the XHTML declares it. Every other field error
// (range, format, length) reports the same verbatim text the rebuild already shows inline.
//
// The inline `Value Required` under a blank field is the rebuild's own enhancement and is NOT what
// the banner shows; the banner is where the legacy wording lives. So a page's validator keeps
// returning `Value Required` for a blank required field, and this module translates that one
// message, field by field, into the legacy banner line.

/** JSF `javax.faces.component.UIInput.REQUIRED`, as overridden by legacy's validation bundle. */
export const legacyRequiredMessage = (label: string): string => `${label}: Value is required.`

/** Legacy's per-row label prefix on the list pages (`Id: #{obj.rowCounter} - `). */
export const legacyRowLabel = (rowNumber: number, label: string): string =>
  `Id: ${String(rowNumber)} - ${label}`

/**
 * The banner lines for one validated form, in the page's own field order.
 *
 * @param errors the page validator's result (field → the inline message)
 * @param order every field of the form in on-screen order; a field not listed is never reported
 * @param requiredText the inline "value required" text the validator uses for a blank required field
 * @param label the legacy XHTML `label` of a field that can be required; a blank required field
 *   whose label is unknown falls back to its inline text rather than inventing wording
 * @param rowPrefix on a list row, the fields whose legacy `validatorMessage` / `converterMessage`
 *   was `Id: #{row} - #{msg...}`: their NON-required lines get that prefix in front of the inline
 *   text (the inline text itself is the bundle message, unprefixed)
 */
export function legacyBannerLines<K extends string>(
  errors: Partial<Record<K, string>>,
  order: readonly K[],
  requiredText: string,
  label: (field: K) => string | undefined,
  rowPrefix?: { readonly rowNumber: number; readonly fields: ReadonlySet<K> },
): string[] {
  const lines: string[] = []
  for (const field of order) {
    const message = errors[field]
    if (message === undefined) {
      continue
    }
    const fieldLabel = message === requiredText ? label(field) : undefined
    if (fieldLabel !== undefined) {
      lines.push(legacyRequiredMessage(fieldLabel))
    } else if (rowPrefix?.fields.has(field) === true) {
      lines.push(legacyRowLabel(rowPrefix.rowNumber, message))
    } else {
      lines.push(message)
    }
  }
  return lines
}

/**
 * One line of an ACCUMULATING validation banner (#359 group B change log, Schedules 4, 7A and 9).
 * `key` names the field the line belongs to, so a later change to that field can replace or remove
 * exactly its own line; `rank` is its page position (row, then field order), so the list always
 * reads in the same order as the full Save / Check Status list.
 */
export interface BannerEntry {
  readonly key: string
  readonly rank: number
  readonly line: string
}

/**
 * Put one field's line into the banner (or take it out, with `entry` null), keeping page order. Used
 * on a field's change: legacy re-rendered `p:messages` on every change, and on these pages the
 * banner deliberately ACCUMULATES rather than being replaced (Iman + BA ruling).
 */
export const setBannerEntry = (
  entries: readonly BannerEntry[],
  key: string,
  entry: BannerEntry | null,
): readonly BannerEntry[] => {
  const rest = entries.filter((existing) => existing.key !== key)
  if (entry === null) {
    return rest.length === entries.length ? entries : rest
  }
  return [...rest, entry].sort((a, b) => a.rank - b.rank)
}

/** The rank of a list-row field: rows in order, then the row's own field order. */
export const rowFieldRank = (rowIndex: number, fieldIndex: number): number =>
  rowIndex * 1000 + fieldIndex

/**
 * Every banner entry for a list page's rows, in page order — the full list Save / Check Status
 * REPLACE the banner with. `lineFor` renders one field's legacy line from its inline message.
 */
export function rowBannerEntries<Row, K extends string>(
  rows: readonly {
    readonly row: Row
    readonly key: string | number
    readonly errors: Partial<Record<K, string>>
  }[],
  order: readonly K[],
  lineFor: (row: Row, rowIndex: number, field: K, message: string) => string,
): BannerEntry[] {
  const entries: BannerEntry[] = []
  rows.forEach(({ row, key, errors }, rowIndex) => {
    order.forEach((field, fieldIndex) => {
      const message = errors[field]
      if (message !== undefined) {
        entries.push({
          key: `${String(key)}:${field}`,
          rank: rowFieldRank(rowIndex, fieldIndex),
          line: lineFor(row, rowIndex, field, message),
        })
      }
    })
  })
  return entries
}
