import type { FC } from 'react'
import type Schedule2Response from '@/interfaces/Schedule2Response'
import type { CostBlock, CheckStatusResponse } from '@/interfaces/Schedule2Response'
import type Schedule2Request from '@/interfaces/Schedule2Request'
import { useEffect, useState } from 'react'
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
  TextArea,
  TextInput,
} from '@carbon/react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'
import { validateSchedule2 } from './validation'
import './index.scss'

// ERR-001 (mill/year not selected) and the confirm-delete text are client-side chrome (a suppression
// with no request / a confirm dialog), so their verbatim text lives here. Success/error text comes
// from the API `message.text` / ProblemDetail.detail — never hardcoded.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const COMMENTS_MAX = 3500

// The three editable form keys mirror the flat Schedule2Request field names.
const F_ITEM25_COST = 'purchasedLogCostCost'
const F_ITEM26_VOLUME = 'lessLogSalesVolume'
const F_ITEM26_COST = 'lessLogSalesCost'
const F_COMMENTS = 'comments'

type FieldValues = Record<string, string>

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { data?: { detail?: string } } }).response
    return response?.data?.detail
  }
  return undefined
}

const EMPTY_BLOCK: CostBlock = { volume: null, cost: null, perUnit: null }

const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

const toNum = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return null
  }
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)

// Seed editable form state from the loaded document (editable fields only).
function seedForm(doc: Schedule2Response): FieldValues {
  return {
    [F_ITEM25_COST]: numStr(doc.purchasedLogCost?.cost),
    [F_ITEM26_VOLUME]: numStr(doc.lessLogSales?.volume),
    [F_ITEM26_COST]: numStr(doc.lessLogSales?.cost),
    [F_COMMENTS]: doc.comments ?? '',
  }
}

function buildRequest(doc: Schedule2Response, form: FieldValues): Schedule2Request {
  return {
    // A new/unsaved schedule (revisionCount null) sends 0, per the ratified write contract.
    revisionCount: doc.revisionCount ?? 0,
    comments: form[F_COMMENTS].trim() === '' ? null : form[F_COMMENTS],
    purchasedLogCostCost: toNum(form[F_ITEM25_COST]),
    lessLogSalesVolume: toNum(form[F_ITEM26_VOLUME]),
    lessLogSalesCost: toNum(form[F_ITEM26_COST]),
  }
}

const Schedule2: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<Schedule2Response | null>(null)
  const [form, setForm] = useState<FieldValues>({})
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [statusMessages, setStatusMessages] = useState<CheckStatusResponse | null>(null)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

  useEffect(() => {
    if (contextMissing) {
      return
    }
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    setSaveMessage(null)
    setSaveError(null)
    setStatusMessages(null)
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<Schedule2Response>(`/v1/schedule2?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setForm(seedForm(response.data))
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Schedule 2.')
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
  }, [millId, year, contextMissing])

  const setField =
    (key: string) => (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [key]: value }))
    }

  const handleSave = () => {
    // Re-entrancy guard: the top + bottom Save buttons can be double-clicked within one tick before
    // `saving` disables them — avoid concurrent PUTs (which would trip the optimistic-lock 409).
    if (!data || saving) {
      return
    }
    // Advisory client-side validation (backend remains authoritative): block a doomed round-trip.
    if (Object.keys(validateSchedule2(form)).length > 0) {
      setSaveMessage(null)
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    setSaving(true)
    setSaveMessage(null)
    setSaveError(null)
    setStatusMessages(null)
    apiService
      .getAxiosInstance()
      .put<Schedule2Response>(
        `/v1/schedule2?millId=${millId}&year=${year}`,
        buildRequest(data, form),
      )
      .then((response) => {
        setData(response.data)
        setForm(seedForm(response.data))
        // Success text verbatim from the API message field (AD-8), never hardcoded.
        setSaveMessage(response.data.message?.text ?? null)
      })
      .catch((error: unknown) => {
        // Keep the entered values; surface the API's verbatim ProblemDetail.detail.
        setSaveError(extractDetail(error) || 'Schedule could not be saved.')
      })
      .finally(() => setSaving(false))
  }

  const handleDelete = () => {
    if (saving) {
      return
    }
    setConfirmDeleteOpen(false)
    setSaving(true)
    setSaveMessage(null)
    setSaveError(null)
    setStatusMessages(null)
    apiService
      .getAxiosInstance()
      .delete<{ message?: { text?: string } }>(`/v1/schedule2?millId=${millId}&year=${year}`)
      .then((response) => {
        // Delete removed the summary; a re-GET could 404, so reset to an empty read-only schedule in
        // place (no re-fetch) and show the API delete message.
        setData((prev) =>
          prev
            ? {
                ...prev,
                editable: false,
                revisionCount: null,
                comments: null,
                purchasedLogCost: EMPTY_BLOCK,
                purchasedWoodOverhead: EMPTY_BLOCK,
                subtotal: EMPTY_BLOCK,
                lessLogSales: EMPTY_BLOCK,
                netPurchased: EMPTY_BLOCK,
                totalCompanyLogging: EMPTY_BLOCK,
                totalAverage: EMPTY_BLOCK,
              }
            : prev,
        )
        setForm({})
        setSaveMessage(response.data?.message?.text ?? null)
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to delete Schedule 2.')
      })
      .finally(() => setSaving(false))
  }

  const handleCheckStatus = () => {
    if (!data || saving) {
      return
    }
    setSaveMessage(null)
    setSaveError(null)
    setStatusMessages(null)
    apiService
      .getAxiosInstance()
      .post<CheckStatusResponse>(`/v1/schedule2/check-status?millId=${millId}&year=${year}`)
      .then((response) => {
        setStatusMessages(response.data)
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to check status.')
      })
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle title="Schedule 2" subtitle="Cost of Purchased / Private Logs." />
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
            <LoadingScreen label="Loading Schedule 2" />
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
              title="Unable to load Schedule 2"
              subtitle={errorDetail}
            />
          </Column>
        </Grid>
      </div>
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable
  // Advisory per-field validation (backend authoritative); drives inline invalid states + Save gate.
  const fieldErrors = editable ? validateSchedule2(form) : {}

  // An editable value cell: a TextInput when the field is entered-by-user and the schedule is
  // editable, otherwise read-only text.
  const inputCell = (fieldKey: string, label: string) => (
    <TableCell className="schedule-2__num">
      <TextInput
        id={fieldKey}
        labelText={label}
        hideLabel
        size="sm"
        value={form[fieldKey] ?? ''}
        onChange={setField(fieldKey)}
        invalid={Boolean(fieldErrors[fieldKey])}
        invalidText={fieldErrors[fieldKey]}
      />
    </TableCell>
  )

  const readOnlyCell = (value: number | null | undefined) => (
    <TableCell className="schedule-2__num">{fmt(value)}</TableCell>
  )

  // Item 25 — Purchased Log Cost: volume carried (read-only), cost editable, perUnit read-only.
  const item25Row = (
    <TableRow>
      <TableCell>Purchased Log Cost</TableCell>
      {readOnlyCell(data.purchasedLogCost.volume)}
      {editable
        ? inputCell(F_ITEM25_COST, 'Purchased Log Cost cost')
        : readOnlyCell(data.purchasedLogCost.cost)}
      {readOnlyCell(data.purchasedLogCost.perUnit)}
    </TableRow>
  )

  // Item 26 — Less Log Sales: volume + cost editable, perUnit read-only.
  const item26Row = (
    <TableRow>
      <TableCell>Less Log Sales</TableCell>
      {editable
        ? inputCell(F_ITEM26_VOLUME, 'Less Log Sales volume')
        : readOnlyCell(data.lessLogSales.volume)}
      {editable
        ? inputCell(F_ITEM26_COST, 'Less Log Sales cost')
        : readOnlyCell(data.lessLogSales.cost)}
      {readOnlyCell(data.lessLogSales.perUnit)}
    </TableRow>
  )

  // Read-only derived / carried blocks (never inputs, never sent on write).
  const readOnlyRows: { label: string; block: CostBlock }[] = [
    { label: 'Purchased Wood Overhead', block: data.purchasedWoodOverhead },
    { label: 'Subtotal', block: data.subtotal },
    { label: 'Net Purchased', block: data.netPurchased },
    { label: 'Total Company Logging', block: data.totalCompanyLogging },
    { label: 'Total Average', block: data.totalAverage },
  ]

  const derivedRow = (row: { label: string; block: CostBlock }) => (
    <TableRow key={row.label}>
      <TableCell>{row.label}</TableCell>
      {readOnlyCell(row.block.volume)}
      {readOnlyCell(row.block.cost)}
      {readOnlyCell(row.block.perUnit)}
    </TableRow>
  )

  const actions = (
    <Column sm={4} md={8} lg={16} className="schedule-2__actions">
      <Button kind="primary" disabled={!editable || saving} onClick={handleSave}>
        Save
      </Button>
      <Button kind="tertiary" disabled={!editable || saving} onClick={handleCheckStatus}>
        Check Status
      </Button>
      <Button
        kind="danger--tertiary"
        disabled={!editable || saving}
        onClick={() => setConfirmDeleteOpen(true)}
      >
        Delete
      </Button>
    </Column>
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16} className="schedule-2__meta">
          <dl className="schedule-2__summary">
            <div className="schedule-2__summary-item">
              <dt>Mill</dt>
              <dd>{data.millId}</dd>
            </div>
            <div className="schedule-2__summary-item">
              <dt>Reporting Year</dt>
              <dd>{data.year}</dd>
            </div>
            <div className="schedule-2__summary-item">
              <dt>Status</dt>
              <dd>{data.trackStatus ?? '—'}</dd>
            </div>
          </dl>
        </Column>

        {saveMessage && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification kind="success" lowContrast title="Success" subtitle={saveMessage} />
          </Column>
        )}
        {saveError && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              title="Action failed"
              subtitle={saveError}
            />
          </Column>
        )}
        {statusMessages &&
          statusMessages.messages.map((msg) => (
            <Column sm={4} md={8} lg={16} key={msg.key}>
              <InlineNotification
                kind={statusMessages.outcome === 'MET' ? 'success' : 'warning'}
                lowContrast
                title="Check Status"
                subtitle={msg.text}
              />
            </Column>
          ))}

        {actions}

        <Column sm={4} md={8} lg={16} className="schedule-2__section">
          <TableContainer title="Purchased / Private Log Costs">
            <Table aria-label="Purchased / Private Log Costs">
              <TableHead>
                <TableRow>
                  <TableHeader>Cost Item</TableHeader>
                  <TableHeader className="schedule-2__num">Volume</TableHeader>
                  <TableHeader className="schedule-2__num">Cost</TableHeader>
                  <TableHeader className="schedule-2__num">$/m³</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {item25Row}
                {item26Row}
                {readOnlyRows.map(derivedRow)}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-2__section">
          {editable ? (
            <TextArea
              id="comments"
              labelText="Comments"
              enableCounter
              maxCount={COMMENTS_MAX}
              value={form[F_COMMENTS] ?? ''}
              onChange={setField(F_COMMENTS)}
            />
          ) : (
            <>
              <h3 className="schedule-2__heading">Comments</h3>
              <p className="schedule-2__comments">{data.comments ?? '—'}</p>
            </>
          )}
        </Column>

        {actions}
      </Grid>

      {editable && (
        <Modal
          open={confirmDeleteOpen}
          danger
          modalHeading="Delete schedule"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteOpen(false)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule2
