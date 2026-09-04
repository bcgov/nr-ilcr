import type { FC } from 'react'
import { useLayoutEffect, useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react'
import { Add, TrashCan } from '@carbon/icons-react'
import { fmtNumber, groupInput, numStrGroup, toNum } from '@/utils/number'
import { committedNum, isUnusableStrictEntry } from '@/utils/derivedMath'
import EditableSubPageLayout from '@/components/core/EditableSubPageLayout'
import SubPanel from '@/components/core/SubPanel'
import { useEditableCostRows, type EditRow } from '@/hooks/useEditableCostRows'
import { useRowSort } from '@/hooks/useRowSort'
import './index.scss'

/** Map of form/request keys (e.g. `total`, `pop`) to their raw string values. */
export type SubPageValues = Record<string, string>
/** Advisory validation errors keyed by `description` and each editable field key. */
export type SubPageErrors = Record<string, string | undefined>

/** The minimum shape every sub-page row satisfies. Field values are read through the config. */
export interface Schedule3SubPageRow {
  id: number
  description: string
}

/** The minimum shape every sub-page document satisfies. */
export interface Schedule3SubPageDoc {
  editable: boolean
  count: number
  message?: { text: string } | null
}

/** One editable numeric field — an input in every row (legacy edit-in-place). */
export interface Schedule3SubPageField<TRow extends Schedule3SubPageRow> {
  key: string
  header: string
  label: string
  get: (row: TRow) => number | null
}

/**
 * One read-only derived column (e.g. Crown $). Recomputed live from the row's entered field values so
 * it tracks edits before Save, mirroring the legacy disabled/derived cell.
 */
export interface Schedule3SubPageColumn {
  /** Stable sort/React key (e.g. {@code 'crown'}) — decoupled from the display label. */
  key: string
  header: string
  derive: (values: Record<string, number | null>) => number | null
}

/** One summary figure rendered under the table (last-saved; refreshes on Save). */
export interface Schedule3SubPageSummaryItem<TDoc extends Schedule3SubPageDoc> {
  /**
   * Stable key the footer mirror is looked up by. Keyed rather than positional (code review
   * 2026-08-21): indexing a returned array against `summaryItems` couples the two across a config
   * boundary with `noUncheckedIndexedAccess` off, so a short or reordered array mis-pairs figures with
   * labels with no type error — the same invisible-to-tsc shape as the inert-mirror bug this file
   * already records.
   */
  key: string
  label: string
  value: (doc: TDoc) => number | null
}

/**
 * The full description of one Schedule 3 list sub-page. {@link EditableSubPageLayout} owns the page
 * chrome and {@link useEditableCostRows} owns the legacy edit-everything-inline behaviour; this
 * generic body renders only the page-specific panels (columns/labels). A page declares WHAT differs —
 * its endpoint, columns, labels, and validation.
 */
export interface Schedule3SubPageConfig<
  TRow extends Schedule3SubPageRow,
  TDoc extends Schedule3SubPageDoc,
> {
  /** API base path, e.g. {@code '/v1/schedule3/included-unacceptable-costs'}. */
  base: string
  title: string
  subtitle: string
  tableTitle: string
  addHeading: string
  descriptionMaxLength: number
  loadError: string
  saveError: string
  /** Optional intro paragraph shown above the add fields. */
  intro?: string
  /** Optional read-only figure (e.g. Annual Rents S111) shown above the table. */
  metaField?: { id: string; label: string; value: (doc: TDoc) => number | null }
  fields: Schedule3SubPageField<TRow>[]
  readonlyColumns?: Schedule3SubPageColumn[]
  summaryItems: Schedule3SubPageSummaryItem<TDoc>[]
  /**
   * Optional display-only mirror for the Totals footer (defect #291): given the blur-committed row
   * values, return a figure per `summaryItems` entry KEYED BY that entry's `key` — not a positional
   * list. The keyed contract is deliberate (see `schedule3OtherAcceptableCosts/derived.ts`, where
   * `OtherAcceptableSubtotal` is "keyed so it cannot be mis-paired with the summary labels
   * positionally"): a triple looked up by name cannot silently pair with the wrong label when a
   * column is added or reordered. Present only on the pages
   * whose legacy footer refreshed during entry — Other Acceptable Costs, whose Total $ / PO&P $
   * handlers rendered `footerValues` (schedule3SubtotalOtherCosts.xhtml:74,83). The Included
   * Unacceptable page omits it: its only handler was `render="cost"`, with no derived target, so
   * leaving that footer to the Save echo is faithful.
   */
  deriveSummary?: (rows: readonly SubPageValues[]) => Readonly<Record<string, number | null>>
  rows: (doc: TDoc) => TRow[]
  validate: (description: string, values: SubPageValues) => SubPageErrors
}

function Schedule3SubPage<TRow extends Schedule3SubPageRow, TDoc extends Schedule3SubPageDoc>({
  config,
}: {
  config: Schedule3SubPageConfig<TRow, TDoc>
}) {
  const navigate = useNavigate()
  const fieldKeys = config.fields.map((f) => f.key)

  const editor = useEditableCostRows<TDoc>({
    base: config.base,
    fieldKeys,
    loadError: config.loadError,
    saveError: config.saveError,
    rowsFromDoc: (doc) =>
      config.rows(doc).map((r) => ({
        id: r.id,
        description: r.description,
        values: Object.fromEntries(config.fields.map((f) => [f.key, numStrGroup(f.get(r))])),
      })),
    validate: config.validate,
    onBack: () => navigate({ to: '/schedule-3' }),
  })

  const {
    rows,
    rowErrors,
    addDescription,
    setAddDescription,
    addValues,
    setAddValue,
    addErrors,
    setRowDescription,
    setRowValue,
    handleAdd,
    removeRow,
    saving,
  } = editor

  const readonlyColumns = config.readonlyColumns ?? []

  // The blur-committed row values the footer mirror reads (defect #291). Keyed by row key; a row not
  // in the map has never been committed, so it contributes nothing yet. Re-seeded whenever the
  // document is replaced (load / Save echo / delete reload).
  const [committed, setCommitted] = useState<Record<string, SubPageValues>>({})
  const rowsRef = useRef(rows)
  rowsRef.current = rows
  useLayoutEffect(() => {
    setCommitted(Object.fromEntries(rowsRef.current.map((row) => [row.key, row.values])))
  }, [editor.data])

  // Numeric view of a row's entered values, for the live-derived read-only columns (e.g. Crown $).
  //
  // `committedNum`, NOT `toNum` (PR #344 review): this must be the SAME parse the footer mirror uses
  // (`deriveOtherAcceptableSubtotal` → `committedNum` → `parseDecimalInput`), or the two disagree on
  // every form the lax parser accepts and the strict one rejects. `1e3` was the clearest case —
  // `validateOtherAcceptable` passes it (`Number('1e3')` is 1000, in range), the row's Crown $ showed
  // `1,000 − pop`, and the footer excluded the row entirely because `committedNum('1e3')` is null. Same
  // for `0x10` → 16 and a mis-grouped `12,34` → 1234. That is the same self-contradiction the comment
  // on `rowCells` says was ruled out — row Crowns of 700 and 400 under a Subtotal Crown of 900 —
  // reached by a parser mismatch instead of a timing one.
  //
  // It also closes a smaller gap the review noted in passing: `toNum` has no finiteness guard, so
  // `Infinity` rendered `∞` in Crown $. `committedNum` rejects it, and the wire never carried it.
  const numeric = (values: SubPageValues): Record<string, number | null> =>
    Object.fromEntries(config.fields.map((f) => [f.key, committedNum(values[f.key] ?? '')]))

  // Client-side column sort, matching the legacy Schedule 3 sub-page dataTables: Description, every
  // editable field, AND each derived read-only column (e.g. Crown $) are sortable. See useRowSort for
  // the snapshot-on-click semantics.
  const sort = useRowSort(
    rows,
    {
      description: (row) => row.description,
      ...Object.fromEntries(
        config.fields.map((f) => [f.key, (row: EditRow) => toNum(row.values[f.key])]),
      ),
      ...Object.fromEntries(
        readonlyColumns.map((col) => [col.key, (row: EditRow) => col.derive(numeric(row.values))]),
      ),
    },
    (row) => row.key,
  )

  /**
   * A row's committed values, falling back to its LIVE values when it has never been committed — only
   * true of a row appended locally by `handleAdd` before its PUT lands. Falling back to `{}` made the
   * footer silently omit a row visibly sitting in the grid (code review 2026-08-21, proven by probe
   * with a delayed PUT).
   */
  const committedFor = (row: EditRow): SubPageValues => committed[row.key] ?? row.values

  const rowCells = (row: EditRow, editable: boolean) => {
    // The derived read-only columns read the COMMITTED values, so every derived cell on the page
    // settles on ONE event — matching legacy's single `update="otherCrownTabel footerValues"`
    // (schedule3SubtotalOtherCosts.xhtml:74,83) and the ratified blur trigger. Deriving Crown from the
    // live values while the footer moved on blur made the page contradict itself mid-typing: row
    // Crowns of 700 and 400 under a Subtotal Crown of 900 (ruled 2026-08-21).
    const nums = numeric(committedFor(row))
    const errs = rowErrors[row.key] ?? {}
    if (editable) {
      return (
        <>
          <TableCell>
            <TextInput
              id={`row-description-${row.key}`}
              labelText="Edit description"
              hideLabel
              size="sm"
              maxLength={config.descriptionMaxLength}
              value={row.description}
              onChange={(e) => setRowDescription(row.key, e.target.value)}
              invalid={Boolean(errs.description)}
              invalidText={errs.description}
            />
          </TableCell>
          {config.fields.map((field) => (
            <TableCell key={field.key} className="schedule-3__num schedule-3__num--input">
              <TextInput
                id={`row-${field.key}-${row.key}`}
                labelText={`Edit ${field.label}`}
                hideLabel
                size="sm"
                value={row.values[field.key] ?? ''}
                onChange={(e) => setRowValue(row.key, field.key, e.target.value)}
                // Re-group on blur only — regrouping mid-keystroke would fight the caret. The
                // re-group is cosmetic and unconditional (`groupInput` returns invalid text
                // unchanged, so a typo stays on screen); only the COMMIT is gated below.
                onBlur={() => {
                  const grouped = groupInput(row.values[field.key] ?? '')
                  setRowValue(row.key, field.key, grouped)
                  // An invalid or unusable entry HOLDS its previous committed value (PR #344 review).
                  // This is the rule `useCommittedValues` documents and every other surface already
                  // followed — Schedule 4 reimplements the same guard. Without it an out-of-range
                  // 999,999,999 in Total $ moved the footer to a figure the server can never produce.
                  //
                  // Validate HERE rather than reading `rowErrors`: that map is populated only by
                  // `persist` (useEditableCostRows.ts:204), i.e. on a Save attempt, so it is still
                  // empty during the entry this gate has to catch. `errs` below is right for the
                  // field's `invalid` styling and wrong as a commit gate.
                  const next = { ...row.values, [field.key]: grouped }
                  const blurErrs = config.validate(row.description, next)
                  if (blurErrs[field.key] !== undefined || isUnusableStrictEntry(grouped)) {
                    return
                  }
                  // Merge onto the row's previous committed values rather than the live `row.values`,
                  // so a blur advances only the field that blurred. Not a reachable bug today —
                  // moving focus to another field blurs this one first — but it keeps the snapshot's
                  // meaning exact rather than relying on focus ordering.
                  setCommitted((prev) => ({
                    ...prev,
                    // Commit the grouped string, so `committed` and the field hold the same text.
                    [row.key]: { ...(prev[row.key] ?? row.values), [field.key]: grouped },
                  }))
                }}
                invalid={Boolean(errs[field.key])}
                invalidText={errs[field.key]}
              />
            </TableCell>
          ))}
          {readonlyColumns.map((col) => (
            <TableCell key={col.key} className="schedule-3__num">
              {fmtNumber(col.derive(nums))}
            </TableCell>
          ))}
          <TableCell>
            <Button
              kind="danger--tertiary"
              size="sm"
              hasIconOnly
              iconDescription="Remove"
              renderIcon={TrashCan}
              disabled={saving}
              onClick={() => removeRow(row.key)}
            />
          </TableCell>
        </>
      )
    }
    return (
      <>
        <TableCell>{row.description}</TableCell>
        {config.fields.map((field) => (
          <TableCell key={field.key} className="schedule-3__num">
            {fmtNumber(nums[field.key])}
          </TableCell>
        ))}
        {readonlyColumns.map((col) => (
          <TableCell key={col.key} className="schedule-3__num">
            {fmtNumber(col.derive(nums))}
          </TableCell>
        ))}
      </>
    )
  }

  return (
    <EditableSubPageLayout
      editor={editor}
      scheduleName="Schedule 3"
      title={config.title}
      backLabel="Back"
      loadingLabel={`Loading ${config.title}`}
      errorTitle={`Unable to load ${config.title}`}
    >
      {(data) => {
        const editable = data.editable
        // The footer mirror: only when the page supplies one and the schedule is editable (#291 AC7).
        // `editable` comes from the DOCUMENT — the editor hook does not expose it, and reading a
        // non-existent `editor.editable` silently disabled the mirror entirely.
        const mirroredSummary =
          config.deriveSummary && editable
            ? config.deriveSummary(rows.map((row) => committedFor(row)))
            : null
        // Description + numeric (field + readonly) columns + optional Action column — empty-state colSpan.
        const totalColumns = 1 + config.fields.length + readonlyColumns.length + (editable ? 1 : 0)
        return (
          <>
            {/* Add section — TOP (legacy: the "Add …" panel precedes the list): grey header bar + body. */}
            {editable && (
              <Column sm={4} md={8} lg={16} className="schedule-3__section">
                <SubPanel title={config.addHeading}>
                  {config.intro && <p className="schedule-3__intro">{config.intro}</p>}
                  <div className="schedule-3-sub__add">
                    <TextInput
                      id="add-description"
                      className="schedule-3-sub__field schedule-3-sub__field--wide"
                      labelText="Description"
                      size="sm"
                      maxLength={config.descriptionMaxLength}
                      value={addDescription}
                      onChange={(e) => setAddDescription(e.target.value)}
                      invalid={Boolean(addErrors.description)}
                      invalidText={addErrors.description}
                    />
                    {config.fields.map((field) => (
                      <TextInput
                        key={field.key}
                        id={`add-${field.key}`}
                        className="schedule-3-sub__field schedule-3-sub__field--narrow"
                        labelText={field.header}
                        size="sm"
                        value={addValues[field.key] ?? ''}
                        onChange={(e) => setAddValue(field.key, e.target.value)}
                        onBlur={() =>
                          setAddValue(field.key, groupInput(addValues[field.key] ?? ''))
                        }
                        invalid={Boolean(addErrors[field.key])}
                        invalidText={addErrors[field.key]}
                      />
                    ))}
                    <div className="schedule-3-sub__actions">
                      <Button
                        kind="primary"
                        size="md"
                        disabled={saving}
                        renderIcon={Add}
                        onClick={handleAdd}
                      >
                        Add
                      </Button>
                    </div>
                  </div>
                </SubPanel>
              </Column>
            )}

            {/* List section — BELOW: header bar, optional meta field (Annual Rents S111), then the
                table with a "No records found." empty state and a Totals footer. Rows are live inputs. */}
            <Column sm={4} md={8} lg={16} className="schedule-3__section">
              <SubPanel title={config.tableTitle}>
                {config.metaField && (
                  <TextInput
                    id={config.metaField.id}
                    className="schedule-3-sub__meta-field"
                    labelText={config.metaField.label}
                    size="sm"
                    value={numStrGroup(config.metaField.value(data))}
                    onChange={() => undefined}
                    disabled
                  />
                )}
                <TableContainer>
                  <Table aria-label={config.tableTitle} className="schedule-3-sub__table">
                    <TableHead>
                      <TableRow>
                        <TableHeader
                          isSortable
                          isSortHeader={sort.activeKey === 'description'}
                          sortDirection={sort.directionFor('description')}
                          onClick={() => sort.toggleSort('description')}
                        >
                          Description
                        </TableHeader>
                        {config.fields.map((field) => (
                          <TableHeader
                            key={field.key}
                            className="schedule-3__num"
                            isSortable
                            isSortHeader={sort.activeKey === field.key}
                            sortDirection={sort.directionFor(field.key)}
                            onClick={() => sort.toggleSort(field.key)}
                          >
                            {field.header}
                          </TableHeader>
                        ))}
                        {readonlyColumns.map((col) => (
                          <TableHeader
                            key={col.key}
                            className="schedule-3__num"
                            isSortable
                            isSortHeader={sort.activeKey === col.key}
                            sortDirection={sort.directionFor(col.key)}
                            onClick={() => sort.toggleSort(col.key)}
                          >
                            {col.header}
                          </TableHeader>
                        ))}
                        {editable && <TableHeader>Action</TableHeader>}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={totalColumns}>No records found.</TableCell>
                        </TableRow>
                      ) : (
                        sort.sortedRows.map((row) => (
                          <TableRow key={row.key}>{rowCells(row, editable)}</TableRow>
                        ))
                      )}
                      {/* Totals footer: summaryItems align 1:1 with the numeric columns; last-saved
                            figures, refreshed after Save (legacy recomputed on save). */}
                      <TableRow className="schedule-3-sub__totals">
                        <TableCell>Totals</TableCell>
                        {config.summaryItems.map((item) => (
                          <TableCell key={item.label} className="schedule-3__num">
                            {fmtNumber(
                              mirroredSummary ? mirroredSummary[item.key] : item.value(data),
                            )}
                          </TableCell>
                        ))}
                        {editable && <TableCell />}
                      </TableRow>
                    </TableBody>
                  </Table>
                </TableContainer>
              </SubPanel>
            </Column>
          </>
        )
      }}
    </EditableSubPageLayout>
  )
}

export default Schedule3SubPage as <
  TRow extends Schedule3SubPageRow,
  TDoc extends Schedule3SubPageDoc,
>(props: {
  config: Schedule3SubPageConfig<TRow, TDoc>
}) => ReturnType<FC>
