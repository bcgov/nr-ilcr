import type { FC } from 'react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Grid,
  Modal,
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
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import { extractDetail } from '@/utils/error'
import { fmt, numStr, toNum } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import PageTitle from '@/components/core/PageTitle'
import './index.scss'

// Client-side chrome (verbatim legacy text); SUC-* come from the API message.text (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'

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
 * The full description of one Schedule 3 list sub-page. The generic component below owns every bit of
 * behaviour these pages share: load-on-context, the legacy edit-everything-inline model (Add/Remove
 * mutate a local list; one Save reconciles insert/update/delete server-side), guard states, and the
 * row markup. A page only declares WHAT differs — its endpoint, columns, labels, and validation.
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

/** One editable row held in local state. `id` is null for a row added but not yet saved. */
interface EditRow {
  key: number
  id: number | null
  description: string
  values: SubPageValues
}

const emptyValues = (keys: string[]): SubPageValues => Object.fromEntries(keys.map((k) => [k, '']))

const hasErrors = (errors: SubPageErrors): boolean =>
  Object.values(errors).some((v) => v !== undefined)

function Schedule3SubPage<TRow extends Schedule3SubPageRow, TDoc extends Schedule3SubPageDoc>({
  config,
}: {
  config: Schedule3SubPageConfig<TRow, TDoc>
}) {
  const { millId, year } = useMillYear()
  const navigate = useNavigate()
  const contextMissing = millId === null || year === null

  const fieldKeys = config.fields.map((f) => f.key)

  const [data, setData] = useState<TDoc | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // The full editable row set (legacy: every row is a live input; nothing persists until Save).
  const [rows, setRows] = useState<EditRow[]>([])
  const [rowErrors, setRowErrors] = useState<Record<number, SubPageErrors>>({})
  const [dirty, setDirty] = useState(false)

  const [addDescription, setAddDescription] = useState('')
  const [addValues, setAddValues] = useState<SubPageValues>(() => emptyValues(fieldKeys))
  const [addErrors, setAddErrors] = useState<SubPageErrors>({})

  const [confirmBackOpen, setConfirmBackOpen] = useState(false)

  // Monotonic client key for React list identity (independent of the server detail id, which is null
  // for freshly-added rows). Re-seeding after a load/save mints fresh keys so inputs remount cleanly.
  const keyCounterRef = useRef(0)

  const base = config.base
  const query = `?millId=${millId}&year=${year}`

  const seedRows = (doc: TDoc): EditRow[] =>
    config.rows(doc).map((r) => ({
      key: keyCounterRef.current++,
      id: r.id,
      description: r.description,
      values: Object.fromEntries(config.fields.map((f) => [f.key, numStr(f.get(r))])),
    }))

  useEffect(() => {
    if (contextMissing) {
      return
    }
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    setMessage(null)
    setActionError(null)
    setRows([])
    setRowErrors({})
    setDirty(false)
    setAddDescription('')
    setAddValues(emptyValues(fieldKeys))
    setAddErrors({})
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<TDoc>(`${base}${query}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setRows(seedRows(response.data))
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || config.loadError)
          setData(null)
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false)
        }
      })
    return () => {
      active = false
    }
    // fieldKeys/seedRows derive from the static config; excluded to keep the effect keyed on context.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [millId, year, contextMissing, base, query, config.loadError])

  // Numeric view of a row's entered values, for the live-derived read-only columns (e.g. Crown $).
  const numeric = (values: SubPageValues): Record<string, number | null> =>
    Object.fromEntries(config.fields.map((f) => [f.key, toNum(values[f.key])]))

  const setRowDescription = (key: number, value: string) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, description: value } : r)))
    setDirty(true)
  }

  const setRowValue = (key: number, fieldKey: string, value: string) => {
    setRows((prev) =>
      prev.map((r) => (r.key === key ? { ...r, values: { ...r.values, [fieldKey]: value } } : r)),
    )
    setDirty(true)
  }

  const applyDocument = (doc: TDoc) => {
    setData(doc)
    setRows(seedRows(doc))
    setRowErrors({})
    setMessage(doc.message?.text ?? null)
    setActionError(null)
    setDirty(false)
  }

  /**
   * Persist the WHOLE current row set in one call — the legacy {@code update()} that every mutation
   * (Add, Delete, Save) funnels through: it validates each row, then the server reconciles
   * insert/update/delete and re-derives the totals. {@code intent} only selects the success message
   * ("Data saved successfully" vs "Data deleted successfully") — the persistence is identical either
   * way. A blocked validation leaves the local edits in place so the user can fix and retry.
   */
  const persist = (rowsToSave: EditRow[], intent: 'save' | 'delete') => {
    if (!data || saving) {
      return
    }
    const errs: Record<number, SubPageErrors> = {}
    for (const row of rowsToSave) {
      const rowErr = config.validate(row.description, row.values)
      if (hasErrors(rowErr)) {
        errs[row.key] = rowErr
      }
    }
    if (Object.keys(errs).length > 0) {
      setRowErrors(errs)
      return
    }
    setRowErrors({})
    setMessage(null)
    setActionError(null)
    setSaving(true)
    const rowsPayload = rowsToSave.map((row) => ({
      id: row.id,
      description: row.description.trim(),
      ...Object.fromEntries(fieldKeys.map((k) => [k, toNum(row.values[k])])),
    }))
    apiService
      .getAxiosInstance()
      .put<TDoc>(`${base}${query}&intent=${intent}`, { rows: rowsPayload })
      .then((response) => {
        applyDocument(response.data)
        // Save (not delete) clears the add form — legacy clearAdd*Form() inside update(true).
        if (intent === 'save') {
          setAddDescription('')
          setAddValues(emptyValues(fieldKeys))
          setAddErrors({})
        }
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || config.saveError)
      })
      .finally(() => setSaving(false))
  }

  // "Add" appends the entered row and immediately persists the whole set (legacy addOtherCost → save).
  const handleAdd = () => {
    if (saving) {
      return
    }
    setMessage(null)
    setActionError(null)
    const errors = config.validate(addDescription, addValues)
    if (hasErrors(errors)) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    const next = [
      ...rows,
      {
        key: keyCounterRef.current++,
        id: null,
        description: addDescription.trim(),
        values: { ...addValues },
      },
    ]
    setRows(next)
    setAddDescription('')
    setAddValues(emptyValues(fieldKeys))
    setDirty(true)
    persist(next, 'save')
  }

  // "Remove" drops the row and immediately persists the whole set (legacy deleteCost → delete →
  // update(false)) so the row is deleted server-side and any pending edits are flushed in the same call.
  const removeRow = (key: number) => {
    if (saving) {
      return
    }
    const next = rows.filter((r) => r.key !== key)
    setRows(next)
    setRowErrors((prev) => {
      const cleared = { ...prev }
      delete cleared[key]
      return cleared
    })
    setDirty(true)
    persist(next, 'delete')
  }

  // "Save" persists the whole set (legacy save() → update(true)).
  const handleSave = () => {
    if (rows.length === 0) {
      return
    }
    persist(rows, 'save')
  }

  const goBack = () => {
    navigate({ to: '/schedule-3' })
  }

  // Guard unsaved edits on Back (legacy confirmNavigationMsg); navigate directly when nothing is dirty.
  const handleBack = () => {
    if (dirty) {
      setConfirmBackOpen(true)
      return
    }
    goBack()
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle title={config.title} subtitle={config.subtitle} />
    </Grid>
  )

  if (contextMissing) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Mill and Reporting Year required',
          subtitle: ERR_MILL_YEAR_NOT_SELECTED,
        }}
      />
    )
  }

  if (isLoading) {
    return (
      <PageState header={header}>
        <Column sm={4} md={8} lg={16}>
          <LoadingScreen label={`Loading ${config.title}`} />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: `Unable to load ${config.title}`,
          subtitle: errorDetail,
        }}
      >
        <Column sm={4} md={8} lg={16}>
          <Button kind="secondary" onClick={goBack}>
            Back to Schedule 3
          </Button>
        </Column>
      </PageState>
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable
  const readonlyColumns = config.readonlyColumns ?? []
  // Description + numeric (field + readonly) columns + optional Action column — for the empty-state colSpan.
  const totalColumns = 1 + config.fields.length + readonlyColumns.length + (editable ? 1 : 0)

  const rowCells = (row: EditRow) => {
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
    <div className="app-page">
      <Grid fullWidth className="app-page__body">
        {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
        {actionError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
        )}

        {/* Add section — TOP (legacy: the "Add …" panel precedes the list). A titled panel: grey
            header bar + padded body holding the intro and the stacked fields. */}
        {editable && (
          <Column sm={4} md={8} lg={16} className="schedule-3__section">
            <section className="schedule-3-sub__panel">
              <h3 className="schedule-3-sub__panel-title">{config.addHeading}</h3>
              <div className="schedule-3-sub__panel-body">
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
                      onChange={(e) =>
                        setAddValues((prev) => ({ ...prev, [field.key]: e.target.value }))
                      }
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
              </div>
            </section>
          </Column>
        )}

        {/* List section — BELOW. A matching titled panel: header bar (the table title), then the
            read-only meta field (Annual Rents S111), then the table with a "No records found." empty
            state and a Totals footer row. Every row is a live input (legacy edit-in-place). */}
        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <section className="schedule-3-sub__panel">
            <h3 className="schedule-3-sub__panel-title">{config.tableTitle}</h3>
            <div className="schedule-3-sub__panel-body">
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
                      rows.map((row) => <TableRow key={row.key}>{rowCells(row)}</TableRow>)
                    )}
                    {/* Totals footer: summaryItems align 1:1 with the numeric (field + readonly) columns.
                        Shows the last-saved figures — they refresh after Save (legacy recomputed on save). */}
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
            </div>
          </section>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-3__actions">
          {editable && (
            <Button
              kind="primary"
              // Greyed out until there is data to save (and while saving) — legacy parity.
              disabled={saving || rows.length === 0}
              onClick={handleSave}
            >
              Save
            </Button>
          )}
          <Button kind="secondary" onClick={handleBack}>
            Back to Schedule 3
          </Button>
        </Column>
      </Grid>

      {editable && (
        <Modal
          open={confirmBackOpen}
          modalHeading="Leave page"
          primaryButtonText="Continue"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmBackOpen(false)}
          onRequestSubmit={() => {
            setConfirmBackOpen(false)
            goBack()
          }}
        >
          <p>{CONFIRM_NAVIGATION}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule3SubPage as <
  TRow extends Schedule3SubPageRow,
  TDoc extends Schedule3SubPageDoc,
>(props: {
  config: Schedule3SubPageConfig<TRow, TDoc>
}) => ReturnType<FC>
