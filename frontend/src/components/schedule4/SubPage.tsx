import type { FC } from 'react'
import type Schedule4Response from '@/interfaces/Schedule4Response'
import type { SubPageRow } from '@/interfaces/Schedule4Response'
import { useState } from 'react'
import {
  Button,
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
import {
  emptySubPageRowForm,
  validateSubPageRow,
  type SubPageDef,
  type SubPageRowForm,
} from './subPageDefs'

const CONFIRM_DELETE_ROW = 'This will delete the current record. Do you want to continue?'

const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

const toNum = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '') return null
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

const sum = (rows: SubPageRow[], pick: (r: SubPageRow) => number | null): number =>
  rows.reduce((total, r) => total + (pick(r) ?? 0), 0)

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    return (error as { response?: { data?: { detail?: string } } }).response?.data?.detail
  }
  return undefined
}

interface SubPageProps {
  millId: number
  year: number
  locationId: number
  locationName: string
  def: SubPageDef
  rows: SubPageRow[]
  editable: boolean
  onBack: () => void
  onDocUpdate: (doc: Schedule4Response) => void
}

/**
 * One Schedule 4 sub-page (Towing / Truck Rehaul / Other) for a single location (Story 10.6): an
 * add-row form (Description + Distance + Volume + Cost, + Cycle for Truck Rehaul), the rows table
 * with per-row Delete (NAV-005), and a running-totals footer. Add/Delete call the sub-page
 * sub-resource and lift the recomputed document up via {@code onDocUpdate}; totals are summed from
 * the (server-derived) rows for display. Read-only when the schedule is not editable.
 */
const SubPage: FC<SubPageProps> = ({
  millId,
  year,
  locationId,
  locationName,
  def,
  rows,
  editable,
  onBack,
  onDocUpdate,
}) => {
  const [form, setForm] = useState<SubPageRowForm>(() => emptySubPageRowForm())
  const [showErrors, setShowErrors] = useState(false)
  const [busy, setBusy] = useState(false)
  const [addMessage, setAddMessage] = useState<string | null>(null)
  const [addError, setAddError] = useState<string | null>(null)
  const [confirmDeleteRow, setConfirmDeleteRow] = useState<SubPageRow | null>(null)

  const errors = showErrors ? validateSubPageRow(form, def.hasCycle) : {}

  const setField =
    (field: keyof SubPageRowForm) => (event: React.ChangeEvent<HTMLInputElement>) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [field]: value }))
    }

  const handleAdd = () => {
    if (busy) return
    const validation = validateSubPageRow(form, def.hasCycle)
    if (Object.keys(validation).length > 0) {
      setShowErrors(true)
      return
    }
    setBusy(true)
    setAddMessage(null)
    setAddError(null)
    apiService
      .getAxiosInstance()
      .post<Schedule4Response>(
        `/v1/schedule4/locations/${locationId}/rows?millId=${millId}&year=${year}`,
        {
          type: def.type,
          description: form.description,
          distance: toNum(form.distance),
          volume: toNum(form.volume),
          cost: toNum(form.cost),
          cycle: def.hasCycle ? toNum(form.cycle) : null,
        },
      )
      .then((response) => {
        onDocUpdate(response.data)
        setForm(emptySubPageRowForm())
        setShowErrors(false)
        setAddMessage(response.data.message?.text ?? null)
      })
      .catch((error: unknown) => setAddError(extractDetail(error) || 'Row could not be saved.'))
      .finally(() => setBusy(false))
  }

  const handleDeleteRow = () => {
    if (busy || !confirmDeleteRow) return
    const rowId = confirmDeleteRow.id
    setConfirmDeleteRow(null)
    setBusy(true)
    setAddMessage(null)
    setAddError(null)
    apiService
      .getAxiosInstance()
      .delete<Schedule4Response>(
        `/v1/schedule4/locations/${locationId}/rows/${rowId}?millId=${millId}&year=${year}`,
      )
      .then((response) => {
        onDocUpdate(response.data)
        setAddMessage(response.data.message?.text ?? null)
      })
      .catch((error: unknown) => setAddError(extractDetail(error) || 'Unable to delete row.'))
      .finally(() => setBusy(false))
  }

  const numberField = (field: keyof SubPageRowForm, label: string) => (
    <TextInput
      id={`subpage-${field}`}
      labelText={label}
      size="sm"
      inputMode="numeric"
      value={form[field]}
      onChange={setField(field)}
      invalid={Boolean(errors[field])}
      invalidText={errors[field]}
    />
  )

  return (
    <div className="schedule-4__subpage">
      <div className="schedule-4__subpage-header">
        <Button kind="ghost" size="sm" onClick={onBack}>
          ← Back to location
        </Button>
        <h3 className="schedule-4__heading">
          {def.label} — {locationName}
        </h3>
      </div>

      {addMessage && (
        <InlineNotification kind="success" lowContrast title="Success" subtitle={addMessage} />
      )}
      {addError && (
        <InlineNotification kind="error" lowContrast title="Action failed" subtitle={addError} />
      )}

      {editable && (
        <div className="schedule-4__subpage-form">
          <TextInput
            id="subpage-description"
            labelText="Description"
            maxLength={120}
            value={form.description}
            onChange={setField('description')}
            invalid={Boolean(errors.description)}
            invalidText={errors.description}
          />
          {numberField('distance', 'Distance')}
          {numberField('volume', 'Volume')}
          {numberField('cost', 'Cost')}
          {def.hasCycle && numberField('cycle', 'Cycle')}
          <Button kind="primary" size="sm" disabled={busy} onClick={handleAdd}>
            Add row
          </Button>
        </div>
      )}

      <TableContainer title={`${def.label} rows`} className="schedule-4__grid">
        <Table aria-label={`${def.label} rows`}>
          <TableHead>
            <TableRow>
              <TableHeader>Description</TableHeader>
              <TableHeader className="schedule-4__num">Distance</TableHeader>
              <TableHeader className="schedule-4__num">Volume</TableHeader>
              <TableHeader className="schedule-4__num">Cost</TableHeader>
              {def.hasCycle && <TableHeader className="schedule-4__num">Cycle</TableHeader>}
              <TableHeader className="schedule-4__num">$/m³</TableHeader>
              {editable && <TableHeader>Actions</TableHeader>}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={editable ? 7 : 6}>No rows have been added.</TableCell>
              </TableRow>
            ) : (
              rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>{row.description ?? '—'}</TableCell>
                  <TableCell className="schedule-4__num">{fmt(row.distance)}</TableCell>
                  <TableCell className="schedule-4__num">{fmt(row.volume)}</TableCell>
                  <TableCell className="schedule-4__num">{fmt(row.cost)}</TableCell>
                  {def.hasCycle && (
                    <TableCell className="schedule-4__num">{fmt(row.cycle)}</TableCell>
                  )}
                  <TableCell className="schedule-4__num">{fmt(row.perUnit)}</TableCell>
                  {editable && (
                    <TableCell>
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        disabled={busy}
                        onClick={() => setConfirmDeleteRow(row)}
                      >
                        Delete
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <dl className="schedule-4__totals" aria-label={`${def.label} totals`}>
        <div>
          <dt>Total Distance</dt>
          <dd>{sum(rows, (r) => r.distance)}</dd>
        </div>
        <div>
          <dt>Total Volume</dt>
          <dd>{sum(rows, (r) => r.volume)}</dd>
        </div>
        <div>
          <dt>Total Cost</dt>
          <dd>{sum(rows, (r) => r.cost)}</dd>
        </div>
        {def.hasCycle && (
          <div>
            <dt>Total Cycle</dt>
            <dd>{sum(rows, (r) => r.cycle)}</dd>
          </div>
        )}
      </dl>

      {editable && (
        <Modal
          open={confirmDeleteRow !== null}
          danger
          modalHeading="Delete row"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteRow(null)}
          onRequestSubmit={handleDeleteRow}
        >
          <p>{CONFIRM_DELETE_ROW}</p>
        </Modal>
      )}
    </div>
  )
}

export default SubPage
