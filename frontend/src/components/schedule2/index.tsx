import type { FC } from 'react'
import type Schedule2Response from '@/interfaces/Schedule2Response'
import type { CostBlock, CheckStatusResponse } from '@/interfaces/Schedule2Response'
import type Schedule2Request from '@/interfaces/Schedule2Request'
import { useCallback, useState } from 'react'
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
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { extractDetail } from '@/utils/error'
import { fmt, numStr, toNum } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageState from '@/components/core/PageState'
import PageTitle from '@/components/core/PageTitle'
import { validateSchedule2 } from './validation'
import './index.scss'

// ERR-001 (mill/year not selected) and the confirm-delete text are client-side chrome (a suppression
// with no request / a confirm dialog), so their verbatim text lives here. Success/error text comes
// from the API `message.text` / ProblemDetail.detail — never hardcoded.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const COMMENTS_MAX = 3500

// The editable form keys mirror the flat Schedule2Request field names.
const F_ITEM25_COST = 'purchasedLogCostCost'
const F_ITEM26_VOLUME = 'lessLogSalesVolume'
const F_ITEM26_COST = 'lessLogSalesCost'
const F_COMMENTS = 'comments'

type FieldValues = Record<string, string>

// Static page chrome — no props, so hoisted to module scope (allocated once, not per render).
const PAGE_HEADER = (
  <Grid fullWidth className="app-page__header">
    <PageTitle title="Schedule 2" subtitle="Cost of Purchased / Private Logs." />
  </Grid>
)

// Schedule 2's load never 404s specially (unlike Schedule 1's not-found): any detail passes through.
const mapLoadError = (detail: string | undefined): string => detail || 'Unable to load Schedule 2.'

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
  // `?? ''` guards the trim against an absent key (defence-in-depth; the form is always seeded).
  const rawComments = form[F_COMMENTS] ?? ''
  return {
    // A new/unsaved schedule (revisionCount null) sends 0, per the ratified write contract.
    revisionCount: doc.revisionCount ?? 0,
    comments: rawComments.trim() === '' ? null : rawComments,
    purchasedLogCostCost: toNum(form[F_ITEM25_COST] ?? ''),
    lessLogSalesVolume: toNum(form[F_ITEM26_VOLUME] ?? ''),
    lessLogSalesCost: toNum(form[F_ITEM26_COST] ?? ''),
  }
}

const Schedule2: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [statusMessages, setStatusMessages] = useState<CheckStatusResponse | null>(null)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

  // Clear the mutation notifications whenever a fresh document loads (mill/year change).
  const resetMessages = useCallback(() => {
    setSaveMessage(null)
    setSaveError(null)
    setStatusMessages(null)
  }, [])

  const { data, setData, form, setForm, setField, errorDetail, isLoading } =
    useScheduleDocument<Schedule2Response>({
      path: '/v1/schedule2',
      millId,
      year,
      contextMissing,
      seedForm,
      mapLoadError,
      onReset: resetMessages,
    })

  const handleSave = () => {
    // Re-entrancy guard: the top + bottom Save buttons can be double-clicked within one tick before
    // `saving` disables them — avoid concurrent PUTs (which would trip the optimistic-lock 409).
    if (!data || saving) {
      return
    }
    // Advisory client-side validation (backend remains authoritative): block a doomed round-trip.
    if (Object.keys(validateSchedule2(form)).length > 0) {
      setSaveMessage(null)
      setStatusMessages(null)
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
    const api = apiService.getAxiosInstance()
    api
      .delete<{ message?: { text?: string } }>(`/v1/schedule2?millId=${millId}&year=${year}`)
      .then((response) => {
        const deleteMessage = response.data?.message?.text ?? null
        // Schedule 2 never 404s: with the summary gone, a re-GET returns the 200 empty EDITABLE
        // document (revisionCount null). Reload it so the meta row / form reflect reality and the
        // Licensee can immediately re-enter data (legacy AF1), while keeping the API delete message.
        return api
          .get<Schedule2Response>(`/v1/schedule2?millId=${millId}&year=${year}`)
          .then((reload) => {
            setData(reload.data)
            setForm(seedForm(reload.data))
            setSaveMessage(deleteMessage)
          })
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to delete Schedule 2.')
      })
      .finally(() => setSaving(false))
  }

  const handleCheckStatus = () => {
    // `saving` doubles as the in-flight guard so a double-click can't fire concurrent POSTs and a
    // slow response can't repaint stale status over a later Save (Save/Check are mutually exclusive).
    if (!data || saving) {
      return
    }
    // Legacy Check Status is validateClient="true": invalid entered values block the action with the
    // same FLD-* messages Save uses, rather than firing a POST that ignores them.
    if (Object.keys(validateSchedule2(form)).length > 0) {
      setSaveMessage(null)
      setStatusMessages(null)
      setSaveError('Please correct the highlighted fields before checking status.')
      return
    }
    setSaving(true)
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
      .finally(() => setSaving(false))
  }

  if (contextMissing) {
    return (
      <PageState
        header={PAGE_HEADER}
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
      <PageState header={PAGE_HEADER}>
        <Column sm={4} md={8} lg={16}>
          <LoadingScreen label="Loading Schedule 2" />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={PAGE_HEADER}
        notification={{ kind: 'error', title: 'Unable to load Schedule 2', subtitle: errorDetail }}
      />
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable
  // Delete targets a persisted summary; an unsaved document (revisionCount null) has nothing to
  // delete, so gate it exactly like legacy isScheduleOpen() (BR-08 / S06).
  const deletable = editable && data.revisionCount !== null
  // Advisory per-field validation (backend authoritative); drives inline invalid states + Save gate.
  const fieldErrors = editable ? validateSchedule2(form) : {}

  // An editable value cell: a TextInput when the field is entered-by-user and the schedule is
  // editable, otherwise read-only text. The hidden `labelText` is a terse, stable a11y name (the
  // visible legacy label lives in the row's first cell).
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

  // Item 25 — Purchased/Private Log Costs: volume carried (read-only), cost editable, perUnit read-only.
  const item25Row = (
    <TableRow>
      <TableCell>Purchased/Private Log Costs:</TableCell>
      {readOnlyCell(data.purchasedLogCost.volume)}
      {editable
        ? inputCell(F_ITEM25_COST, 'Purchased Log Cost cost')
        : readOnlyCell(data.purchasedLogCost.cost)}
      {readOnlyCell(data.purchasedLogCost.perUnit)}
    </TableRow>
  )

  // Item 26 — (less) Log Sales: volume + cost editable, perUnit read-only.
  const item26Row = (
    <TableRow>
      <TableCell>(less) Log Sales:</TableCell>
      {editable
        ? inputCell(F_ITEM26_VOLUME, 'Less Log Sales volume')
        : readOnlyCell(data.lessLogSales.volume)}
      {editable
        ? inputCell(F_ITEM26_COST, 'Less Log Sales cost')
        : readOnlyCell(data.lessLogSales.cost)}
      {readOnlyCell(data.lessLogSales.perUnit)}
    </TableRow>
  )

  // Read-only derived / carried block (never inputs, never sent on write).
  const derivedRow = (label: string, block: CostBlock) => (
    <TableRow key={label}>
      <TableCell>{label}</TableCell>
      {readOnlyCell(block.volume)}
      {readOnlyCell(block.cost)}
      {readOnlyCell(block.perUnit)}
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
        disabled={!deletable || saving}
        onClick={() => setConfirmDeleteOpen(true)}
      >
        Delete
      </Button>
    </Column>
  )

  return (
    <div className="app-page">
      {PAGE_HEADER}
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
          (statusMessages.messages ?? []).map((msg) => (
            <Column sm={4} md={8} lg={16} key={`${msg.key}-${msg.text}`}>
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
              {/* Legacy row order + verbatim labels (schedule2.xhtml:52-142): Purchased/Private Log
                  Costs, Purchased/Private Wood Overhead, Subtotal, (less) Log Sales, Net Purchased,
                  Total Company Logging Costs(Sch 1), Total Average Logging Costs. */}
              <TableBody>
                {item25Row}
                {derivedRow('Purchased/Private Wood Overhead:', data.purchasedWoodOverhead)}
                {derivedRow('Subtotal:', data.subtotal)}
                {item26Row}
                {derivedRow('Net Purchased/Private Log Cost:', data.netPurchased)}
                {derivedRow('Total Company Logging Costs(Sch 1):', data.totalCompanyLogging)}
                {derivedRow('Total Average Logging Costs:', data.totalAverage)}
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
