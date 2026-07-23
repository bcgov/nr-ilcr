import type { FC } from 'react'
import type { UnacceptableRow, UnacceptableDocument } from '@/interfaces/Schedule3Unacceptable'
import type { UnacceptableErrors } from './validation'
import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Grid,
  InlineNotification,
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
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'
import { validateUnacceptable, DESCRIPTION_MAX_LENGTH } from './validation'
import './index.scss'

// Client-side chrome (verbatim legacy text); SUC-* come from the API message.text (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
// Verbatim legacy intro (schedule3IncludedUnacceptableCosts.xhtml).
const INTRO =
  'Unacceptable costs include income and logging taxes, interest & penalties, annual rents ' +
  'discretionary costs (S111), stumpage & royalty, donations, residue & waste penalty billings ' +
  'and other.'

const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)

const toNum = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return null
  }
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { data?: { detail?: string } } }).response
    return response?.data?.detail
  }
  return undefined
}

const UnacceptableCostsPage: FC = () => {
  const { millId, year } = useMillYear()
  const navigate = useNavigate()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<UnacceptableDocument | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [addDescription, setAddDescription] = useState('')
  const [addTotal, setAddTotal] = useState('')
  const [addErrors, setAddErrors] = useState<UnacceptableErrors>({})

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editDescription, setEditDescription] = useState('')
  const [editTotal, setEditTotal] = useState('')
  const [editErrors, setEditErrors] = useState<UnacceptableErrors>({})

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

  const base = `/v1/schedule3/included-unacceptable-costs`
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
    setAddTotal('')
    setAddErrors({})
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<UnacceptableDocument>(`${base}${query}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Included Unacceptable Costs.')
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
  }, [millId, year, contextMissing, base, query])

  const applyDocument = (doc: UnacceptableDocument) => {
    setData(doc)
    setMessage(doc.message?.text ?? null)
    setActionError(null)
  }

  const handleAdd = () => {
    if (!data || saving) {
      return
    }
    setMessage(null)
    setActionError(null)
    const errors = validateUnacceptable(addDescription, addTotal)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .post<UnacceptableDocument>(`${base}${query}`, {
        description: addDescription.trim(),
        total: toNum(addTotal),
      })
      .then((response) => {
        applyDocument(response.data)
        setAddDescription('')
        setAddTotal('')
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || 'Unacceptable cost could not be saved.')
      })
      .finally(() => setSaving(false))
  }

  const startEdit = (row: UnacceptableRow) => {
    setEditingId(row.id)
    setEditDescription(row.description)
    setEditTotal(numStr(row.total))
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
    const errors = validateUnacceptable(editDescription, editTotal)
    if (Object.keys(errors).length > 0) {
      setEditErrors(errors)
      return
    }
    setEditErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .put<UnacceptableDocument>(`${base}/${editingId}${query}`, {
        description: editDescription.trim(),
        total: toNum(editTotal),
      })
      .then((response) => {
        applyDocument(response.data)
        setEditingId(null)
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || 'Unacceptable cost could not be saved.')
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
      .delete<UnacceptableDocument>(`${base}/${id}${query}`)
      .then((response) => {
        applyDocument(response.data)
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || 'Unable to delete unacceptable cost.')
      })
      .finally(() => setSaving(false))
  }

  const goBack = () => {
    navigate({ to: '/schedule-3' })
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle
        title="Included Unacceptable Costs"
        subtitle="Costs excluded from acceptable admin costs for Schedule 3."
      />
    </Grid>
  )

  if (contextMissing) {
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="Mill and Reporting Year required"
              subtitle={ERR_MILL_YEAR_NOT_SELECTED}
            />
          </Column>
        </Grid>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <LoadingScreen label="Loading Included Unacceptable Costs" />
          </Column>
        </Grid>
      </div>
    )
  }

  if (errorDetail) {
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="Unable to load Included Unacceptable Costs"
              subtitle={errorDetail}
            />
          </Column>
          <Column sm={4} md={8} lg={16}>
            <Button kind="secondary" onClick={goBack}>
              Back to Schedule 3
            </Button>
          </Column>
        </Grid>
      </div>
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable

  const rowCells = (row: UnacceptableRow) => {
    if (editable && editingId === row.id) {
      return (
        <>
          <TableCell>
            <TextInput
              id={`edit-description-${row.id}`}
              labelText="Edit description"
              hideLabel
              size="sm"
              maxLength={DESCRIPTION_MAX_LENGTH}
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              invalid={Boolean(editErrors.description)}
              invalidText={editErrors.description}
            />
          </TableCell>
          <TableCell className="schedule-3__num">
            <TextInput
              id={`edit-total-${row.id}`}
              labelText="Edit total"
              hideLabel
              size="sm"
              value={editTotal}
              onChange={(e) => setEditTotal(e.target.value)}
              invalid={Boolean(editErrors.total)}
              invalidText={editErrors.total}
            />
          </TableCell>
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
        <TableCell className="schedule-3__num">{fmt(row.total)}</TableCell>
        {editable && (
          <TableCell>
            <Button
              kind="ghost"
              size="sm"
              disabled={saving || editingId !== null}
              onClick={() => startEdit(row)}
            >
              Edit
            </Button>
            <Button
              kind="danger--ghost"
              size="sm"
              disabled={saving || editingId !== null}
              onClick={() => setConfirmDeleteId(row.id)}
            >
              Delete
            </Button>
          </TableCell>
        )}
      </>
    )
  }

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16} className="schedule-3__meta">
          <p className="schedule-3__intro">{INTRO}</p>
          <TextInput
            id="annualRentsS111"
            labelText="Annual Rents (Forest Act, S111)"
            size="sm"
            value={numStr(data.annualRentsTotal)}
            onChange={() => undefined}
            disabled
          />
        </Column>

        {message && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
          </Column>
        )}
        {actionError && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              title="Action failed"
              subtitle={actionError}
            />
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <TableContainer title="Included Unacceptable Costs">
            <Table aria-label="Included Unacceptable Costs">
              <TableHead>
                <TableRow>
                  <TableHeader>Description</TableHeader>
                  <TableHeader className="schedule-3__num">Total $</TableHeader>
                  {editable && <TableHeader>Actions</TableHeader>}
                </TableRow>
              </TableHead>
              <TableBody>
                {(data.rows ?? []).map((row) => (
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
            <div className="schedule-3__summary-item">
              <dt>Subtotal Total $</dt>
              <dd>{fmt(data.subtotalTotal)}</dd>
            </div>
          </dl>
        </Column>

        {editable && (
          <Column sm={4} md={8} lg={16} className="schedule-3__section">
            <h3 className="schedule-3__heading">Add Included Unacceptable Cost</h3>
            <div className="schedule-3-sub__add">
              <TextInput
                id="add-description"
                labelText="Description"
                size="sm"
                maxLength={DESCRIPTION_MAX_LENGTH}
                value={addDescription}
                onChange={(e) => setAddDescription(e.target.value)}
                invalid={Boolean(addErrors.description)}
                invalidText={addErrors.description}
              />
              <TextInput
                id="add-total"
                labelText="Total $"
                size="sm"
                value={addTotal}
                onChange={(e) => setAddTotal(e.target.value)}
                invalid={Boolean(addErrors.total)}
                invalidText={addErrors.total}
              />
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
          modalHeading="Delete unacceptable cost"
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

export default UnacceptableCostsPage
