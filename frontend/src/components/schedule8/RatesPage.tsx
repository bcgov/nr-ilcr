import type { FC } from 'react'
import type Schedule8Response from '@/interfaces/Schedule8Response'
import type { RateRow } from '@/interfaces/Schedule8Response'
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
import { emptyRateForm, fmt, toNum, validateRateForm, type RateForm } from './validation'

// Client-only confirm chrome, verbatim from the legacy bundle (confirmDeleteMsg intent).
const CONFIRM_DELETE_ROW = 'This will delete the current record. Do you want to continue?'
// Back confirm — literal from the legacy markup (confirmNavigationMsg).
const NAV_UNSAVED = 'Unsaved data will be lost. Are you sure to continue?'

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    return (error as { response?: { data?: { detail?: string } } }).response?.data?.detail
  }
  return undefined
}

const sumRates = (rows: RateRow[]): number =>
  rows.reduce((total, r) => total + (r.costingRate ?? 0), 0)

interface RatesPageProps {
  millId: number
  year: number
  sampleId: number
  sampleTitle: string
  additions: RateRow[]
  deductions: RateRow[]
  editable: boolean
  onBack: () => void
  onDocUpdate: (doc: Schedule8Response) => void
}

/**
 * The Schedule 8 Additions/Deductions screen (Story 14.4, S01/S06/S09) for a single saved sample: the
 * two rate tables, each with an add-row form (cost item / $/m³ / cost type + description), per-row
 * Delete, and a footer total (CNT-003). Add/Delete call the rate sub-resource and lift the recomputed
 * document up via onDocUpdate; which table a new row lands in is decided server-side by the cost
 * item's subcategory. Read-only when the schedule is not editable.
 */
const RatesPage: FC<RatesPageProps> = ({
  millId,
  year,
  sampleId,
  sampleTitle,
  additions,
  deductions,
  editable,
  onBack,
  onDocUpdate,
}) => {
  const [addForm, setAddForm] = useState<RateForm>(() => emptyRateForm())
  const [dedForm, setDedForm] = useState<RateForm>(() => emptyRateForm())
  const [showAddErrors, setShowAddErrors] = useState(false)
  const [showDedErrors, setShowDedErrors] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirmDeleteRow, setConfirmDeleteRow] = useState<RateRow | null>(null)
  const [confirmBack, setConfirmBack] = useState(false)

  const requestBack = () => {
    if (editable) setConfirmBack(true)
    else onBack()
  }

  const submitRate = (form: RateForm) => {
    setBusy(true)
    setMessage(null)
    setError(null)
    apiService
      .getAxiosInstance()
      .post<Schedule8Response>(
        `/v1/schedule8/samples/${sampleId}/rates?millId=${millId}&year=${year}`,
        {
          id: null,
          revisionCount: null,
          costItemCode: toNum(form.costItemCode),
          costingRate: toNum(form.costingRate),
          costTypeCode: form.costTypeCode,
          itemDescription: form.itemDescription.trim() === '' ? null : form.itemDescription,
        },
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
      })
      .catch((err: unknown) => setError(extractDetail(err) || 'Row could not be saved.'))
      .finally(() => setBusy(false))
  }

  const handleAddAddition = () => {
    if (busy) return
    const validation = validateRateForm(addForm)
    if (Object.keys(validation).length > 0) {
      setShowAddErrors(true)
      return
    }
    submitRate(addForm)
    setAddForm(emptyRateForm())
    setShowAddErrors(false)
  }

  const handleAddDeduction = () => {
    if (busy) return
    const validation = validateRateForm(dedForm)
    if (Object.keys(validation).length > 0) {
      setShowDedErrors(true)
      return
    }
    submitRate(dedForm)
    setDedForm(emptyRateForm())
    setShowDedErrors(false)
  }

  const handleDeleteRow = () => {
    if (busy || !confirmDeleteRow) return
    const rowId = confirmDeleteRow.id
    setConfirmDeleteRow(null)
    setBusy(true)
    setMessage(null)
    setError(null)
    apiService
      .getAxiosInstance()
      .delete<Schedule8Response>(
        `/v1/schedule8/samples/${sampleId}/rates/${rowId}?millId=${millId}&year=${year}`,
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
      })
      .catch((err: unknown) => setError(extractDetail(err) || 'Unable to delete row.'))
      .finally(() => setBusy(false))
  }

  const rateTable = (
    kind: 'addition' | 'deduction',
    label: string,
    rows: RateRow[],
    form: RateForm,
    setForm: (updater: (prev: RateForm) => RateForm) => void,
    errors: Record<string, string>,
    onAdd: () => void,
  ) => {
    const setField = (field: keyof RateForm) => (event: React.ChangeEvent<HTMLInputElement>) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [field]: value }))
    }
    return (
      <div className="schedule-8__section">
        {editable && (
          <div className="schedule-8__form">
            <TextInput
              id={`${kind}-costItemCode`}
              labelText={`${label} — Cost Item`}
              size="sm"
              inputMode="numeric"
              value={form.costItemCode}
              onChange={setField('costItemCode')}
              invalid={Boolean(errors.costItemCode)}
              invalidText={errors.costItemCode}
            />
            <TextInput
              id={`${kind}-costingRate`}
              labelText={`${label} — $/m³`}
              size="sm"
              inputMode="numeric"
              value={form.costingRate}
              onChange={setField('costingRate')}
              invalid={Boolean(errors.costingRate)}
              invalidText={errors.costingRate}
            />
            <TextInput
              id={`${kind}-costTypeCode`}
              labelText={`${label} — Cost Type`}
              size="sm"
              value={form.costTypeCode}
              onChange={setField('costTypeCode')}
              invalid={Boolean(errors.costTypeCode)}
              invalidText={errors.costTypeCode}
            />
            <TextInput
              id={`${kind}-itemDescription`}
              labelText={`${label} — Description`}
              size="sm"
              maxLength={30}
              value={form.itemDescription}
              onChange={setField('itemDescription')}
              invalid={Boolean(errors.itemDescription)}
              invalidText={errors.itemDescription}
            />
            <Button kind="primary" size="sm" disabled={busy} onClick={onAdd}>
              Add {label}
            </Button>
          </div>
        )}

        <TableContainer title={`${label} (${rows.length})`} className="schedule-8__grid">
          <Table aria-label={label}>
            <TableHead>
              <TableRow>
                <TableHeader>Cost Item</TableHeader>
                <TableHeader>Description</TableHeader>
                <TableHeader className="schedule-8__num">$/m³</TableHeader>
                <TableHeader>Cost Type</TableHeader>
                {editable && <TableHeader>Actions</TableHeader>}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={editable ? 5 : 4}>No rows have been added.</TableCell>
                </TableRow>
              ) : (
                rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{fmt(row.costItemCode)}</TableCell>
                    <TableCell>{row.itemDescription ?? '—'}</TableCell>
                    <TableCell className="schedule-8__num">{fmt(row.costingRate)}</TableCell>
                    <TableCell>{row.costTypeDescription ?? row.costTypeCode ?? '—'}</TableCell>
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

        <dl className="schedule-8__totals" aria-label={`${label} total`}>
          <div>
            <dt>{label} Total</dt>
            <dd>{sumRates(rows)}</dd>
          </div>
        </dl>
      </div>
    )
  }

  return (
    <div className="schedule-8__level">
      <div className="schedule-8__level-header">
        <Button kind="ghost" size="sm" onClick={requestBack}>
          ← Back to sample
        </Button>
        <h3 className="schedule-8__heading">Additions / Deductions — {sampleTitle}</h3>
      </div>

      {message && (
        <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
      )}
      {error && (
        <InlineNotification kind="error" lowContrast title="Action failed" subtitle={error} />
      )}

      {rateTable(
        'addition',
        'Additions',
        additions,
        addForm,
        setAddForm,
        showAddErrors ? validateRateForm(addForm) : {},
        handleAddAddition,
      )}
      {rateTable(
        'deduction',
        'Deductions',
        deductions,
        dedForm,
        setDedForm,
        showDedErrors ? validateRateForm(dedForm) : {},
        handleAddDeduction,
      )}

      {editable && (
        <Modal
          open={confirmDeleteRow !== null}
          danger
          modalHeading="Delete rate row"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteRow(null)}
          onRequestSubmit={handleDeleteRow}
        >
          <p>{CONFIRM_DELETE_ROW}</p>
        </Modal>
      )}

      <Modal
        open={confirmBack}
        modalHeading="Unsaved changes"
        primaryButtonText="Continue"
        secondaryButtonText="Cancel"
        onRequestClose={() => setConfirmBack(false)}
        onRequestSubmit={() => {
          setConfirmBack(false)
          onBack()
        }}
      >
        <p>{NAV_UNSAVED}</p>
      </Modal>
    </div>
  )
}

export default RatesPage
