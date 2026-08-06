import type { FC } from 'react'
import type Schedule4Response from '@/interfaces/Schedule4Response'
import type { SubPageRow } from '@/interfaces/Schedule4Response'
import { useMemo, useState } from 'react'
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
import { fmtCurrency, fmtNumber, numStr, toNum } from '@/utils/number'
import CommaNumberInput from '@/components/core/CommaNumberInput'
import { extractDetail } from '@/utils/error'
import {
  emptySubPageRowForm,
  validateSubPageRow,
  type SubPageDef,
  type SubPageRowForm,
} from './subPageDefs'

const CONFIRM_DELETE_ROW = 'This will delete the current record. Do you want to continue?'

// The sortable row columns (everything except Actions).
type SortKey = 'description' | 'distance' | 'volume' | 'cost' | 'cycle' | 'perUnit'

const sum = (rows: SubPageRow[], pick: (r: SubPageRow) => number | null): number =>
  rows.reduce((total, r) => total + (pick(r) ?? 0), 0)

interface SubPageProps {
  millId: number
  year: number
  locationId: number
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
  // In-place edits to existing rows, keyed by row id — only touched rows appear here. Edits persist
  // locally until the user hits Save (which PUTs each dirty row); Add/Delete refresh the doc but keep
  // these overrides (row ids are stable). showRowErrors gates per-cell validation display on Save.
  const [edits, setEdits] = useState<Record<number, SubPageRowForm>>({})
  const [showRowErrors, setShowRowErrors] = useState(false)

  const errors = showErrors ? validateSubPageRow(form, def.hasCycle) : {}

  const rowToForm = (row: SubPageRow): SubPageRowForm => ({
    description: row.description ?? '',
    distance: numStr(row.distance),
    volume: numStr(row.volume),
    cost: numStr(row.cost),
    cycle: numStr(row.cycle),
  })
  // The current form for a row: its local edit if touched, else the (server) row values.
  const rowForm = (row: SubPageRow): SubPageRowForm => edits[row.id] ?? rowToForm(row)
  const setRowField = (row: SubPageRow, field: keyof SubPageRowForm, value: string) =>
    setEdits((prev) => ({
      ...prev,
      [row.id]: { ...(prev[row.id] ?? rowToForm(row)), [field]: value },
    }))
  const isRowDirty = (row: SubPageRow): boolean => {
    const edit = edits[row.id]
    return edit !== undefined && JSON.stringify(edit) !== JSON.stringify(rowToForm(row))
  }

  // Client-side column sort of the data rows (the totals row always stays last). Three-state per
  // header: unsorted → ascending → descending → unsorted. Nulls sort last in either direction.
  const [sort, setSort] = useState<{ key: SortKey; dir: 'ASC' | 'DESC' } | null>(null)
  const toggleSort = (key: SortKey) =>
    setSort((prev) => {
      if (prev?.key !== key) return { key, dir: 'ASC' }
      return prev.dir === 'ASC' ? { key, dir: 'DESC' } : null
    })
  const sortedRows = useMemo(() => {
    if (!sort) return rows
    const { key, dir } = sort
    const factor = dir === 'ASC' ? 1 : -1
    return [...rows].sort((a, b) => {
      const av = a[key]
      const bv = b[key]
      if (av == null && bv == null) return 0
      if (av == null) return 1
      if (bv == null) return -1
      if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * factor
      return String(av).localeCompare(String(bv)) * factor
    })
  }, [rows, sort])

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

  // PUT one edited existing row. Resolves true on success; the recomputed doc is lifted up.
  const putRow = (row: SubPageRow): Promise<boolean> => {
    const rf = edits[row.id]
    if (!rf) return Promise.resolve(true)
    setBusy(true)
    setAddMessage(null)
    setAddError(null)
    return apiService
      .getAxiosInstance()
      .put<Schedule4Response>(
        `/v1/schedule4/locations/${locationId}/rows/${row.id}?millId=${millId}&year=${year}`,
        {
          type: def.type,
          description: rf.description,
          distance: toNum(rf.distance),
          volume: toNum(rf.volume),
          cost: toNum(rf.cost),
          cycle: def.hasCycle ? toNum(rf.cycle) : null,
        },
      )
      .then((response) => {
        onDocUpdate(response.data)
        setAddMessage(response.data.message?.text ?? null)
        return true
      })
      .catch((error: unknown) => {
        setAddError(extractDetail(error) || 'Row could not be saved.')
        return false
      })
      .finally(() => setBusy(false))
  }

  // Save: commit a pending add-row draft AND every edited existing row (each row already-Added is only
  // re-persisted if the user changed it). Blocks on the first invalid row (surfacing its cell errors),
  // then STAYS on the sub-page (Back is the leave action) — mirrors the location panel's Save.
  const handleSave = () => {
    if (busy) return
    const dirty = rows.filter(isRowDirty)
    const pending = hasPendingRow()
    const addInvalid = pending && Object.keys(validateSubPageRow(form, def.hasCycle)).length > 0
    const rowInvalid = dirty.some(
      (row) => Object.keys(validateSubPageRow(rowForm(row), def.hasCycle)).length > 0,
    )
    if (addInvalid || rowInvalid) {
      if (addInvalid) setShowErrors(true)
      if (rowInvalid) setShowRowErrors(true)
      return
    }
    // Sequential (each call recomputes the doc; avoid racing concurrent doc updates).
    const tasks: Array<() => Promise<boolean>> = []
    if (pending) tasks.push(addRow)
    dirty.forEach((row) => tasks.push(() => putRow(row)))
    if (tasks.length === 0) return // nothing pending/dirty — stay put
    void tasks
      .reduce((chain, task) => chain.then((ok) => (ok ? task() : false)), Promise.resolve(true))
      .then((ok) => {
        if (ok) {
          // Clear the local edit overrides so the rows re-seed from the freshly saved doc, and stay.
          setEdits({})
          setShowRowErrors(false)
        }
      })
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
    <CommaNumberInput
      id={`subpage-${field}`}
      labelText={label}
      size="sm"
      value={form[field]}
      onValueChange={(raw) => setForm((prev) => ({ ...prev, [field]: raw }))}
      invalid={Boolean(errors[field])}
      invalidText={errors[field]}
    />
  )

  // Effective numeric value of a row field: the edited value when the row is touched, else the server
  // value. Drives the live $/m³ + totals so they track edits before Save (legacy parity).
  const rowNum = (
    row: SubPageRow,
    field: 'distance' | 'volume' | 'cost' | 'cycle',
  ): number | null => (edits[row.id] ? toNum(edits[row.id][field]) : row[field])
  const rowPerUnit = (row: SubPageRow): number | null => {
    const cost = rowNum(row, 'cost')
    const volume = rowNum(row, 'volume')
    return cost != null && volume != null && volume !== 0 ? cost / volume : null
  }

  // Sortable column header (all but Actions). Carbon renders the sort affordance + aria-sort.
  const sortHeader = (key: SortKey, label: string, numeric = false) => (
    <TableHeader
      className={numeric ? 'schedule-4__num' : undefined}
      isSortable
      isSortHeader={sort?.key === key}
      sortDirection={sort?.key === key ? sort.dir : 'NONE'}
      onClick={() => toggleSort(key)}
    >
      {label}
    </TableHeader>
  )

  return (
    <div className="schedule-4__subpage">
      {addMessage && (
        <InlineNotification kind="success" lowContrast title="Success" subtitle={addMessage} />
      )}
      {addError && (
        <InlineNotification kind="error" lowContrast title="Action failed" subtitle={addError} />
      )}

      {editable && (
        <>
          <h3 className="schedule-4__subpage-title">Add {def.label}</h3>
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
            {numberField('distance', 'Distance (km)')}
            {numberField('volume', 'Volume (m³)')}
            {numberField('cost', 'Cost $')}
            {def.hasCycle && numberField('cycle', 'Cycle')}
            <Button
              kind="primary"
              size="sm"
              className="schedule-4__add-row"
              disabled={busy}
              onClick={handleAdd}
            >
              Add row
            </Button>
          </div>
        </>
      )}

      <TableContainer title={def.label} className="schedule-4__grid schedule-4__subpage-table">
        <Table aria-label={`${def.label} rows`}>
          <TableHead>
            <TableRow>
              {sortHeader('description', 'Description')}
              {sortHeader('distance', 'Distance (km)', true)}
              {sortHeader('volume', 'Volume (m³)', true)}
              {sortHeader('cost', 'Cost $', true)}
              {def.hasCycle && sortHeader('cycle', 'Cycle', true)}
              {sortHeader('perUnit', '$/m³', true)}
              {editable && <TableHeader>Action</TableHeader>}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={editable ? 7 : 6}>No rows have been added.</TableCell>
              </TableRow>
            ) : (
              <>
                {sortedRows.map((row) => {
                  const rf = rowForm(row)
                  // Per-cell validation is shown (on Save) only for rows the user actually edited.
                  const rowErrors =
                    showRowErrors && isRowDirty(row) ? validateSubPageRow(rf, def.hasCycle) : {}
                  // One data cell: an editable input (edit mode) or the read-only value. Numeric cells
                  // comma-group; description is free text.
                  const cell = (field: keyof SubPageRowForm, label: string, numeric = true) => (
                    <TableCell className={numeric ? 'schedule-4__num' : undefined}>
                      {editable ? (
                        numeric ? (
                          <CommaNumberInput
                            id={`row-${row.id}-${field}`}
                            labelText={`${label} (row ${row.id})`}
                            hideLabel
                            size="sm"
                            value={rf[field]}
                            onValueChange={(raw) => setRowField(row, field, raw)}
                            invalid={Boolean(rowErrors[field])}
                            invalidText={rowErrors[field]}
                          />
                        ) : (
                          <TextInput
                            id={`row-${row.id}-${field}`}
                            labelText={`${label} (row ${row.id})`}
                            hideLabel
                            size="sm"
                            maxLength={120}
                            value={rf[field]}
                            onChange={(event) => setRowField(row, field, event.target.value)}
                            invalid={Boolean(rowErrors[field])}
                            invalidText={rowErrors[field]}
                          />
                        )
                      ) : numeric ? (
                        fmtNumber(row[field as 'distance' | 'volume' | 'cost' | 'cycle'])
                      ) : (
                        (row.description ?? '—')
                      )}
                    </TableCell>
                  )
                  return (
                    <TableRow key={row.id}>
                      {cell('description', 'Description', false)}
                      {cell('distance', 'Distance (km)')}
                      {cell('volume', 'Volume (m³)')}
                      {cell('cost', 'Cost $')}
                      {def.hasCycle && cell('cycle', 'Cycle')}
                      <TableCell className="schedule-4__num">
                        {fmtCurrency(rowPerUnit(row))}
                      </TableCell>
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
                  )
                })}
                {/* Summary row: totals of the respective columns (last row) — summed from the effective
                    (edited) values so they track in-progress edits before Save, like the legacy grid. */}
                <TableRow className="schedule-4__totals-row">
                  <TableCell>Totals</TableCell>
                  <TableCell className="schedule-4__num">
                    {fmtNumber(sum(rows, (r) => rowNum(r, 'distance')))}
                  </TableCell>
                  <TableCell className="schedule-4__num">
                    {fmtNumber(sum(rows, (r) => rowNum(r, 'volume')))}
                  </TableCell>
                  <TableCell className="schedule-4__num">
                    {fmtNumber(sum(rows, (r) => rowNum(r, 'cost')))}
                  </TableCell>
                  {def.hasCycle && (
                    <TableCell className="schedule-4__num">
                      {fmtNumber(sum(rows, (r) => rowNum(r, 'cycle')))}
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
              Back
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
