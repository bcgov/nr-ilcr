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
import { fmtCurrency, fmtNumber, toNum } from '@/utils/number'
import { extractDetail } from '@/utils/error'
import {
  emptySubPageRowForm,
  validateSubPageRow,
  type SubPageDef,
  type SubPageRowForm,
} from './subPageDefs'

const CONFIRM_DELETE_ROW = 'This will delete the current record. Do you want to continue?'

const sum = (rows: SubPageRow[], pick: (r: SubPageRow) => number | null): number =>
  rows.reduce((total, r) => total + (pick(r) ?? 0), 0)

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

  // POST the entered add-row (each row saves immediately). Resolves true on success, false on
  // validation failure / API error. Shared by "Add row" and "Save and Back".
  const addRow = (): Promise<boolean> => {
    const validation = validateSubPageRow(form, def.hasCycle)
    if (Object.keys(validation).length > 0) {
      setShowErrors(true)
      return Promise.resolve(false)
    }
    setBusy(true)
    setAddMessage(null)
    setAddError(null)
    return apiService
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
        return true
      })
      .catch((error: unknown) => {
        setAddError(extractDetail(error) || 'Row could not be saved.')
        return false
      })
      .finally(() => setBusy(false))
  }

  const handleAdd = () => {
    if (!busy) void addRow()
  }

  // Any add-row input the user has typed but not yet committed with "Add row".
  const hasPendingRow = (): boolean =>
    form.description.trim() !== '' ||
    form.distance.trim() !== '' ||
    form.volume.trim() !== '' ||
    form.cost.trim() !== '' ||
    (def.hasCycle && form.cycle.trim() !== '')

  // Save: commit any pending add-row input, then return to the location. With nothing pending it just
  // returns (each row is already saved on Add).
  const handleSave = () => {
    if (busy) return
    if (hasPendingRow()) {
      void addRow().then((ok) => {
        if (ok) onBack()
      })
    } else {
      onBack()
    }
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
              <TableHeader className="schedule-4__num">Distance (km)</TableHeader>
              <TableHeader className="schedule-4__num">Volume (m³)</TableHeader>
              <TableHeader className="schedule-4__num">Cost $</TableHeader>
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
              <>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{row.description ?? '—'}</TableCell>
                    <TableCell className="schedule-4__num">{fmtNumber(row.distance)}</TableCell>
                    <TableCell className="schedule-4__num">{fmtNumber(row.volume)}</TableCell>
                    <TableCell className="schedule-4__num">{fmtNumber(row.cost)}</TableCell>
                    {def.hasCycle && (
                      <TableCell className="schedule-4__num">{fmtNumber(row.cycle)}</TableCell>
                    )}
                    <TableCell className="schedule-4__num">{fmtCurrency(row.perUnit)}</TableCell>
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
                ))}
                {/* Summary row: totals of the respective columns (last row of the table). */}
                <TableRow className="schedule-4__totals-row">
                  <TableCell>Totals</TableCell>
                  <TableCell className="schedule-4__num">
                    {fmtNumber(sum(rows, (r) => r.distance))}
                  </TableCell>
                  <TableCell className="schedule-4__num">
                    {fmtNumber(sum(rows, (r) => r.volume))}
                  </TableCell>
                  <TableCell className="schedule-4__num">
                    {fmtNumber(sum(rows, (r) => r.cost))}
                  </TableCell>
                  {def.hasCycle && (
                    <TableCell className="schedule-4__num">
                      {fmtNumber(sum(rows, (r) => r.cycle))}
                    </TableCell>
                  )}
                  <TableCell className="schedule-4__num" />
                  {editable && <TableCell />}
                </TableRow>
              </>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <div className="schedule-4__panel-actions">
        {editable ? (
          <>
            <Button kind="primary" disabled={busy} onClick={handleSave}>
              Save
            </Button>
            <Button kind="secondary" disabled={busy} onClick={onBack}>
              Cancel
            </Button>
          </>
        ) : (
          <Button kind="secondary" onClick={onBack}>
            Back
          </Button>
        )}
      </div>

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
