import type { FC } from 'react'
import type Schedule2Response from '@/interfaces/Schedule2Response'
import type { CostBlock, CheckStatusResponse } from '@/interfaces/Schedule2Response'
import type Schedule2Request from '@/interfaces/Schedule2Request'
import { useState } from 'react'
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
  TextArea,
} from '@carbon/react'
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { useScheduleMutations } from '@/hooks/useScheduleMutations'
import { fmtCurrency, fmtNumber, numStr, toNum } from '@/utils/number'
import CommaNumberInput from '@/components/core/CommaNumberInput'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
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

// The tombstone page header (left: page + sub-page identity; right: working-context mill/status). It
// owns hooks (mill-context fetch, document.title), so it renders as an element per branch below rather
// than a module-scope const.
const PAGE_HEADER = (
  <ScheduleTombstone title="Schedule 2" subtitle="Purchased/Priv. Log Costs & Sales" />
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
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  // Save/delete/check-status all run through the shared hook's guarded run() (Story 29.6): a stale
  // in-flight write can no longer repaint a newly-switched mill/year. `saving` is the single in-flight
  // lock for every write (it also gates Check Status). `checkResult` holds the Check Status response.
  const {
    saving,
    message: saveMessage,
    actionError: saveError,
    checkResult: statusMessages,
    setMessage: setSaveMessage,
    setActionError: setSaveError,
    setCheckResult: setStatusMessages,
    clearBanners,
    resetBanners,
    run,
    save,
    remove,
    checkStatus,
  } = useScheduleMutations<CheckStatusResponse>({ path: '/v1/schedule2', millId, year, isCurrent })

  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

  const { data, setData, form, setForm, setField, errorDetail, isLoading } =
    useScheduleDocument<Schedule2Response>({
      path: '/v1/schedule2',
      millId,
      year,
      contextMissing,
      seedForm,
      mapLoadError,
      onReset: resetBanners,
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
    clearBanners() // drop any prior banners incl. a now-stale Check Status result
    save<Schedule2Response>(buildRequest(data, form), {
      fallback: 'Schedule could not be saved.',
      onSuccess: (doc) => {
        setData(doc)
        setForm(seedForm(doc))
        // Success text verbatim from the API message field (AD-8), never hardcoded.
        setSaveMessage(doc.message?.text ?? null)
      },
    })
  }

  const handleDelete = () => {
    if (saving) {
      return
    }
    setConfirmDeleteOpen(false)
    clearBanners() // the deleted schedule's check result / save banner are stale
    remove<{ message?: { text?: string } }>({
      fallback: 'Unable to delete Schedule 2.',
      // Schedule 2 never 404s: with the summary gone, a re-GET returns the 200 empty EDITABLE
      // document (revisionCount null). Reload it so the meta row / form reflect reality and the
      // Licensee can immediately re-enter data (legacy AF1), while keeping the API delete message.
      // This per-page empty-state lives at the call site (Story 29.6): list/re-GET pages re-seed
      // from the reload, single-doc reset-in-place pages (Schedules 1/3) reset in place instead.
      onSuccess: (delResp) => {
        const deleteMessage = delResp?.message?.text ?? null
        run(
          apiService
            .getAxiosInstance()
            .get<Schedule2Response>(`/v1/schedule2?millId=${millId}&year=${year}`),
          {
            fallback: 'Deleted, but the list could not be refreshed.',
            onSuccess: (data) => {
              setData(data)
              setForm(seedForm(data))
              setSaveMessage(deleteMessage)
            },
          },
        )
      },
    })
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
    clearBanners() // don't leave a stale Save success banner beside a new check result
    checkStatus<CheckStatusResponse>({
      fallback: 'Unable to check status.',
      onSuccess: setStatusMessages,
    })
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

  // An editable value cell: a caret-preserving comma-grouped input when the field is entered-by-user
  // and the schedule is editable, otherwise read-only text. Right-aligned so the entered numbers line
  // up with the read-only cells above/below. The hidden `labelText` is a terse, stable a11y name (the
  // visible legacy label lives in the row's first cell).
  const inputCell = (fieldKey: string, label: string) => (
    <TableCell className="schedule-2__num">
      <CommaNumberInput
        id={fieldKey}
        labelText={label}
        hideLabel
        size="sm"
        value={form[fieldKey] ?? ''}
        onValueChange={(raw) => setForm((prev) => ({ ...prev, [fieldKey]: raw }))}
        invalid={Boolean(fieldErrors[fieldKey])}
        invalidText={fieldErrors[fieldKey]}
      />
    </TableCell>
  )

  const readOnlyCell = (value: number | null | undefined) => (
    <TableCell className="schedule-2__num">{fmtNumber(value)}</TableCell>
  )

  // The $/m³ column is currency: thousands-separated with two decimals (shared currency style).
  const perUnitCell = (value: number | null | undefined) => (
    <TableCell className="schedule-2__num">{fmtCurrency(value)}</TableCell>
  )

  // Item 25 — Purchased/Private Log Costs: volume carried (read-only), cost editable, perUnit read-only.
  const item25Row = (
    <TableRow>
      <TableCell>Purchased/Private Log Costs:</TableCell>
      {readOnlyCell(data.purchasedLogCost.volume)}
      {editable
        ? inputCell(F_ITEM25_COST, 'Purchased Log Cost cost')
        : readOnlyCell(data.purchasedLogCost.cost)}
      {perUnitCell(data.purchasedLogCost.perUnit)}
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
      {perUnitCell(data.lessLogSales.perUnit)}
    </TableRow>
  )

  // Read-only derived / carried block (never inputs, never sent on write).
  // `sectionStart` draws a heavier top border to visually divide the table into its 4 cost groups.
  const derivedRow = (label: string, block: CostBlock, sectionStart = false) => (
    <TableRow key={label} className={sectionStart ? 'schedule-2__section-start' : undefined}>
      <TableCell>{label}</TableCell>
      {readOnlyCell(block.volume)}
      {readOnlyCell(block.cost)}
      {perUnitCell(block.perUnit)}
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
    <div className="app-page schedule-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        {saveMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={saveMessage} />
        )}
        {saveError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={saveError} />
        )}
        {statusMessages &&
          (statusMessages.messages ?? []).map((msg) => (
            <NotificationColumn
              key={`${msg.key}-${msg.text}`}
              kind={statusMessages.outcome === 'MET' ? 'success' : 'warning'}
              title="Check Status"
              subtitle={msg.text}
            />
          ))}

        {actions}

        <Column sm={4} md={8} lg={16} className="schedule-2__section">
          <TableContainer>
            <Table aria-label="Purchased / Private Log Costs">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
                  <TableHeader className="schedule-2__num">Volume (m³)</TableHeader>
                  <TableHeader className="schedule-2__num">Cost ($)</TableHeader>
                  <TableHeader className="schedule-2__num">$/m³</TableHeader>
                </TableRow>
              </TableHead>
              {/* Legacy row order + verbatim labels (schedule2.xhtml:52-142): Purchased/Private Log
                  Costs, Purchased/Private Wood Overhead, Subtotal, (less) Log Sales, Net Purchased,
                  Total Company Logging Costs(Sch 1), Total Average Logging Costs. */}
              <TableBody>
                {item25Row}
                {derivedRow('Purchased/Private Wood Overhead:', data.purchasedWoodOverhead)}
                {derivedRow('Subtotal:', data.subtotal, true)}
                {item26Row}
                {derivedRow('Net Purchased/Private Log Cost:', data.netPurchased, true)}
                {derivedRow('Total Company Logging Costs(Sch 1):', data.totalCompanyLogging)}
                {derivedRow('Total Average Logging Costs:', data.totalAverage, true)}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-2__section">
          {editable ? (
            <TextArea
              id="comments"
              className="schedule-2__comments-field"
              labelText="If you have any additional comments, please enter them here:"
              enableCounter
              maxCount={COMMENTS_MAX}
              value={form[F_COMMENTS] ?? ''}
              onChange={setField(F_COMMENTS)}
            />
          ) : (
            <>
              <h3 className="schedule-2__comments-label">
                If you have any additional comments, please enter them here:
              </h3>
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
