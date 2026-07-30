import type { FC } from 'react'
import { useEffect, useState } from 'react'
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
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import { extractDetail } from '@/utils/error'
import { fmt, numStr, toNum } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import PageTitle from '@/components/core/PageTitle'
import RowActionButtons from '@/components/core/RowActionButtons'
import './index.scss'

// Client-side chrome (verbatim legacy text); SUC-* come from the API message.text (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'

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

/** One editable numeric field — an input when editing, a formatted value otherwise. */
export interface Schedule3SubPageField<TRow extends Schedule3SubPageRow> {
  key: string
  header: string
  label: string
  get: (row: TRow) => number | null
}

/** One read-only numeric column (e.g. server-computed Crown $). */
export interface Schedule3SubPageColumn<TRow extends Schedule3SubPageRow> {
  header: string
  value: (row: TRow) => number | null
}

/** One summary figure rendered under the table. */
export interface Schedule3SubPageSummaryItem<TDoc extends Schedule3SubPageDoc> {
  label: string
  value: (doc: TDoc) => number | null
}

/**
 * The full description of one Schedule 3 list sub-page. The generic component below owns every bit
 * of behaviour these pages share (load-on-context, add/edit/delete, guard states, the row/edit
 * markup); a page only declares WHAT differs — its endpoint, columns, labels, and validation.
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
  deleteHeading: string
  descriptionMaxLength: number
  loadError: string
  saveError: string
  deleteError: string
  /** Optional intro paragraph shown in a meta column above the notifications. */
  intro?: string
  /** Optional read-only figure (e.g. Annual Rents S111) shown in the meta column. */
  metaField?: { id: string; label: string; value: (doc: TDoc) => number | null }
  fields: Schedule3SubPageField<TRow>[]
  readonlyColumns?: Schedule3SubPageColumn<TRow>[]
  summaryItems: Schedule3SubPageSummaryItem<TDoc>[]
  rows: (doc: TDoc) => TRow[]
  validate: (description: string, values: SubPageValues) => SubPageErrors
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

  const [addDescription, setAddDescription] = useState('')
  const [addValues, setAddValues] = useState<SubPageValues>(() => emptyValues(fieldKeys))
  const [addErrors, setAddErrors] = useState<SubPageErrors>({})

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editDescription, setEditDescription] = useState('')
  const [editValues, setEditValues] = useState<SubPageValues>(() => emptyValues(fieldKeys))
  const [editErrors, setEditErrors] = useState<SubPageErrors>({})

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

  const base = config.base
  const query = `?millId=${millId}&year=${year}`

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
    setEditingId(null)
    setEditErrors({})
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
    // fieldKeys is derived from the static config; excluded to keep the effect keyed on context only.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [millId, year, contextMissing, base, query, config.loadError])

  const applyDocument = (doc: TDoc) => {
    setData(doc)
    setMessage(doc.message?.text ?? null)
    setActionError(null)
  }

  const requestBody = (description: string, values: SubPageValues) => ({
    description: description.trim(),
    ...Object.fromEntries(fieldKeys.map((k) => [k, toNum(values[k])])),
  })

  const handleAdd = () => {
    if (!data || saving) {
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
    setSaving(true)
    apiService
      .getAxiosInstance()
      .post<TDoc>(`${base}${query}`, requestBody(addDescription, addValues))
      .then((response) => {
        applyDocument(response.data)
        setAddDescription('')
        setAddValues(emptyValues(fieldKeys))
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || config.saveError)
      })
      .finally(() => setSaving(false))
  }

  const startEdit = (row: TRow) => {
    setEditingId(row.id)
    setEditDescription(row.description)
    setEditValues(Object.fromEntries(config.fields.map((f) => [f.key, numStr(f.get(row))])))
    setEditErrors({})
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditErrors({})
  }

  const handleSaveEdit = () => {
    if (editingId === null || saving) {
      return
    }
    setMessage(null)
    setActionError(null)
    const errors = config.validate(editDescription, editValues)
    if (hasErrors(errors)) {
      setEditErrors(errors)
      return
    }
    setEditErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .put<TDoc>(`${base}/${editingId}${query}`, requestBody(editDescription, editValues))
      .then((response) => {
        applyDocument(response.data)
        setEditingId(null)
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || config.saveError)
      })
      .finally(() => setSaving(false))
  }

  const handleDelete = () => {
    if (confirmDeleteId === null || saving) {
      return
    }
    const id = confirmDeleteId
    setConfirmDeleteId(null)
    setSaving(true)
    setMessage(null)
    setActionError(null)
    apiService
      .getAxiosInstance()
      .delete<TDoc>(`${base}/${id}${query}`)
      .then((response) => {
        applyDocument(response.data)
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || config.deleteError)
      })
      .finally(() => setSaving(false))
  }

  const goBack = () => {
    navigate({ to: '/schedule-3' })
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
  const showMeta = Boolean(config.intro) || Boolean(config.metaField)

  const rowCells = (row: TRow) => {
    if (editable && editingId === row.id) {
      return (
        <>
          <TableCell>
            <TextInput
              id={`edit-description-${row.id}`}
              labelText="Edit description"
              hideLabel
              size="sm"
              maxLength={config.descriptionMaxLength}
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              invalid={Boolean(editErrors.description)}
              invalidText={editErrors.description}
            />
          </TableCell>
          {config.fields.map((field) => (
            <TableCell key={field.key} className="schedule-3__num">
              <TextInput
                id={`edit-${field.key}-${row.id}`}
                labelText={`Edit ${field.label}`}
                hideLabel
                size="sm"
                value={editValues[field.key] ?? ''}
                onChange={(e) =>
                  setEditValues((prev) => ({ ...prev, [field.key]: e.target.value }))
                }
                invalid={Boolean(editErrors[field.key])}
                invalidText={editErrors[field.key]}
              />
            </TableCell>
          ))}
          {readonlyColumns.map((col) => (
            <TableCell key={col.header} className="schedule-3__num">
              {fmt(col.value(row))}
            </TableCell>
          ))}
          <TableCell>
            <Button kind="primary" size="sm" disabled={saving} onClick={handleSaveEdit}>
              Save
            </Button>
            <Button kind="ghost" size="sm" disabled={saving} onClick={cancelEdit}>
              Cancel
            </Button>
          </TableCell>
        </>
      )
    }
    return (
      <>
        <TableCell>{row.description}</TableCell>
        {config.fields.map((field) => (
          <TableCell key={field.key} className="schedule-3__num">
            {fmt(field.get(row))}
          </TableCell>
        ))}
        {readonlyColumns.map((col) => (
          <TableCell key={col.header} className="schedule-3__num">
            {fmt(col.value(row))}
          </TableCell>
        ))}
        {editable && (
          <RowActionButtons
            disabled={saving || editingId !== null}
            onEdit={() => startEdit(row)}
            onDelete={() => setConfirmDeleteId(row.id)}
          />
        )}
      </>
    )
  }

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {showMeta && (
          <Column sm={4} md={8} lg={16} className="schedule-3__meta">
            {config.intro && <p className="schedule-3__intro">{config.intro}</p>}
            {config.metaField && (
              <TextInput
                id={config.metaField.id}
                labelText={config.metaField.label}
                size="sm"
                value={numStr(config.metaField.value(data))}
                onChange={() => undefined}
                disabled
              />
            )}
          </Column>
        )}

        {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
        {actionError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
        )}

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <TableContainer title={config.tableTitle}>
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
                  {editable && <TableHeader>Actions</TableHeader>}
                </TableRow>
              </TableHead>
              <TableBody>
                {config.rows(data).map((row) => (
                  <TableRow key={row.id}>{rowCells(row)}</TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <dl className="schedule-3__summary">
            <div className="schedule-3__summary-item">
              <dt>Rows</dt>
              <dd>{data.count}</dd>
            </div>
            {config.summaryItems.map((item) => (
              <div key={item.label} className="schedule-3__summary-item">
                <dt>{item.label}</dt>
                <dd>{fmt(item.value(data))}</dd>
              </div>
            ))}
          </dl>
        </Column>

        {editable && (
          <Column sm={4} md={8} lg={16} className="schedule-3__section">
            <h3 className="schedule-3__heading">{config.addHeading}</h3>
            <div className="schedule-3-sub__add">
              <TextInput
                id="add-description"
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
              <Button kind="primary" disabled={saving || editingId !== null} onClick={handleAdd}>
                Add
              </Button>
            </div>
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-3__actions">
          <Button kind="secondary" onClick={goBack}>
            Back to Schedule 3
          </Button>
        </Column>
      </Grid>

      {editable && (
        <Modal
          open={confirmDeleteId !== null}
          danger
          modalHeading={config.deleteHeading}
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteId(null)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
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
