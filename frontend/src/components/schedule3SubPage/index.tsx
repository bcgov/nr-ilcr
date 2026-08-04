import type { FC } from 'react'
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
import { TrashCan } from '@carbon/icons-react'
import { fmt, numStr, toNum } from '@/utils/number'
import EditableSubPageLayout from '@/components/core/EditableSubPageLayout'
import SubPanel from '@/components/core/SubPanel'
import { useEditableCostRows, type EditRow } from '@/hooks/useEditableCostRows'
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
  header: string
  derive: (values: Record<string, number | null>) => number | null
}

/** One summary figure rendered under the table (last-saved; refreshes on Save). */
export interface Schedule3SubPageSummaryItem<TDoc extends Schedule3SubPageDoc> {
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
        values: Object.fromEntries(config.fields.map((f) => [f.key, numStr(f.get(r))])),
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

  // Numeric view of a row's entered values, for the live-derived read-only columns (e.g. Crown $).
  const numeric = (values: SubPageValues): Record<string, number | null> =>
    Object.fromEntries(config.fields.map((f) => [f.key, toNum(values[f.key])]))

  const rowCells = (row: EditRow, editable: boolean) => {
    const nums = numeric(row.values)
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
            <TableCell key={field.key} className="schedule-3__num">
              <TextInput
                id={`row-${field.key}-${row.key}`}
                labelText={`Edit ${field.label}`}
                hideLabel
                size="sm"
                value={row.values[field.key] ?? ''}
                onChange={(e) => setRowValue(row.key, field.key, e.target.value)}
                invalid={Boolean(errs[field.key])}
                invalidText={errs[field.key]}
              />
            </TableCell>
          ))}
          {readonlyColumns.map((col) => (
            <TableCell key={col.header} className="schedule-3__num">
              {fmt(col.derive(nums))}
            </TableCell>
          ))}
          <TableCell>
            <Button
              kind="danger--ghost"
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
            {fmt(nums[field.key])}
          </TableCell>
        ))}
        {readonlyColumns.map((col) => (
          <TableCell key={col.header} className="schedule-3__num">
            {fmt(col.derive(nums))}
          </TableCell>
        ))}
      </>
    )
  }

  return (
    <EditableSubPageLayout
      editor={editor}
      breadCrumbs={[
        { name: 'ILCR', path: '/' },
        { name: 'Schedule 3', path: '/schedule-3' },
      ]}
      title={config.title}
      backLabel="Back to Schedule 3"
      loadingLabel={`Loading ${config.title}`}
      errorTitle={`Unable to load ${config.title}`}
    >
      {(data) => {
        const editable = data.editable
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
                        invalid={Boolean(addErrors[field.key])}
                        invalidText={addErrors[field.key]}
                      />
                    ))}
                    <div className="schedule-3-sub__actions">
                      <Button kind="primary" disabled={saving} onClick={handleAdd}>
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
                    value={numStr(config.metaField.value(data))}
                    onChange={() => undefined}
                    disabled
                  />
                )}
                <TableContainer>
                  <Table aria-label={config.tableTitle}>
                    <TableHead>
                      <TableRow>
                        <TableHeader>Description</TableHeader>
                        {config.fields.map((field) => (
                          <TableHeader key={field.key} className="schedule-3__num">
                            {field.header}
                          </TableHeader>
                        ))}
                        {readonlyColumns.map((col) => (
                          <TableHeader key={col.header} className="schedule-3__num">
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
                        rows.map((row) => (
                          <TableRow key={row.key}>{rowCells(row, editable)}</TableRow>
                        ))
                      )}
                      {/* Totals footer: summaryItems align 1:1 with the numeric columns; last-saved
                            figures, refreshed after Save (legacy recomputed on save). */}
                      <TableRow className="schedule-3-sub__totals">
                        <TableCell>Totals</TableCell>
                        {config.summaryItems.map((item) => (
                          <TableCell key={item.label} className="schedule-3__num">
                            {fmt(item.value(data))}
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
