import type { FC } from 'react'
import type { OtherCostRow, OtherCostsDocument } from '@/interfaces/OtherCosts'
import type { OtherCostErrors } from './validation'
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
import PageState from '@/components/core/PageState'
import PageTitle from '@/components/core/PageTitle'
import { validateOtherCost, DESCRIPTION_MAX_LENGTH } from './validation'
import './index.scss'

// Client-side chrome (verbatim legacy text); SUC-* come from the API message.text (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'

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

const OtherCostsPage: FC = () => {
  const { millId, year } = useMillYear()
  const navigate = useNavigate()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<OtherCostsDocument | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [addDescription, setAddDescription] = useState('')
  const [addCost, setAddCost] = useState('')
  const [addErrors, setAddErrors] = useState<OtherCostErrors>({})

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editDescription, setEditDescription] = useState('')
  const [editCost, setEditCost] = useState('')
  const [editErrors, setEditErrors] = useState<OtherCostErrors>({})

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

  const base = `/v1/schedule1/other-costs`
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
    // Also clear in-progress add/edit state so a context change can't strand an open editor with no
    // Cancel (its row may be absent from the reloaded list, leaving all actions disabled).
    setEditingId(null)
    setEditErrors({})
    setAddDescription('')
    setAddCost('')
    setAddErrors({})
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<OtherCostsDocument>(`${base}${query}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Other Costs.')
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

  const applyDocument = (doc: OtherCostsDocument) => {
    setData(doc)
    setMessage(doc.message?.text ?? null)
    setActionError(null)
  }

  const handleAdd = () => {
    if (!data || saving) {
      return
    }
    // Clear prior banners first so a validation failure never leaves a stale success/error notice.
    setMessage(null)
    setActionError(null)
    const errors = validateOtherCost(addDescription, addCost)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .post<OtherCostsDocument>(`${base}${query}`, {
        description: addDescription.trim(),
        cost: toNum(addCost),
      })
      .then((response) => {
        applyDocument(response.data)
        setAddDescription('')
        setAddCost('')
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || 'Other cost could not be saved.')
      })
      .finally(() => setSaving(false))
  }

  const startEdit = (row: OtherCostRow) => {
    setEditingId(row.id)
    setEditDescription(row.description)
    setEditCost(numStr(row.cost))
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
    const errors = validateOtherCost(editDescription, editCost)
    if (Object.keys(errors).length > 0) {
      setEditErrors(errors)
      return
    }
    setEditErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .put<OtherCostsDocument>(`${base}/${editingId}${query}`, {
        description: editDescription.trim(),
        cost: toNum(editCost),
      })
      .then((response) => {
        applyDocument(response.data)
        setEditingId(null)
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || 'Other cost could not be saved.')
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
      .delete<OtherCostsDocument>(`${base}/${id}${query}`)
      .then((response) => {
        applyDocument(response.data)
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || 'Unable to delete other cost.')
      })
      .finally(() => setSaving(false))
  }

  const goBack = () => {
    navigate({ to: '/schedule-1' })
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle
        title="Subtotal Other Costs"
        subtitle="Additional cost line items for Schedule 1."
      />
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
          <LoadingScreen label="Loading Other Costs" />
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
          title: 'Unable to load Other Costs',
          subtitle: errorDetail,
        }}
      >
        <Column sm={4} md={8} lg={16}>
          <Button kind="secondary" onClick={goBack}>
            Back to Schedule 1
          </Button>
        </Column>
      </PageState>
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable

  const rowCells = (row: OtherCostRow) => {
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
          <TableCell className="schedule-1__num">
            <TextInput
              id={`edit-cost-${row.id}`}
              labelText="Edit cost"
              hideLabel
              size="sm"
              value={editCost}
              onChange={(e) => setEditCost(e.target.value)}
              invalid={Boolean(editErrors.cost)}
              invalidText={editErrors.cost}
            />
          </TableCell>
          <TableCell className="schedule-1__num">{fmt(row.perUnit)}</TableCell>
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
        <TableCell className="schedule-1__num">{fmt(row.cost)}</TableCell>
        <TableCell className="schedule-1__num">{fmt(row.perUnit)}</TableCell>
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
        <Column sm={4} md={8} lg={16} className="schedule-1__meta">
          <TextInput
            id="otherCostsSharedVolume"
            labelText="Volume m³ (shared, from Schedule 1)"
            size="sm"
            value={numStr(data.volume)}
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

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          <TableContainer title="Other Costs">
            <Table aria-label="Other Costs">
              <TableHead>
                <TableRow>
                  <TableHeader>Description</TableHeader>
                  <TableHeader className="schedule-1__num">Cost</TableHeader>
                  <TableHeader className="schedule-1__num">$/m³</TableHeader>
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
          <dl className="schedule-1__summary">
            <div className="schedule-1__summary-item">
              <dt>Rows</dt>
              <dd>{data.count}</dd>
            </div>
            <div className="schedule-1__summary-item">
              <dt>Subtotal Cost</dt>
              <dd>{fmt(data.costSubtotal)}</dd>
            </div>
            <div className="schedule-1__summary-item">
              <dt>$/m³</dt>
              <dd>{fmt(data.perUnit)}</dd>
            </div>
          </dl>
        </Column>

        {editable && (
          <Column sm={4} md={8} lg={16} className="schedule-1__section">
            <h3 className="schedule-1__heading">Add Other Cost</h3>
            <div className="schedule-1-other-costs__add">
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
                id="add-cost"
                labelText="Cost"
                size="sm"
                value={addCost}
                onChange={(e) => setAddCost(e.target.value)}
                invalid={Boolean(addErrors.cost)}
                invalidText={addErrors.cost}
              />
              <Button kind="primary" disabled={saving || editingId !== null} onClick={handleAdd}>
                Add
              </Button>
            </div>
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-1__actions">
          <Button kind="secondary" onClick={goBack}>
            Back to Schedule 1
          </Button>
        </Column>
      </Grid>

      {editable && (
        <Modal
          open={confirmDeleteId !== null}
          danger
          modalHeading="Delete other cost"
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

export default OtherCostsPage
