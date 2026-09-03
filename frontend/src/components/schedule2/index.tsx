import type { FC } from 'react'
import type Schedule2Response from '@/interfaces/Schedule2Response'
import type { CostBlock, CheckStatusResponse } from '@/interfaces/Schedule2Response'
import type Schedule2Request from '@/interfaces/Schedule2Request'
import { useState } from 'react'
import {
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
} from '@carbon/react'
import CommentsTextArea from '@/components/core/CommentsTextArea'
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { useScheduleMutations } from '@/hooks/useScheduleMutations'
import { useCommittedValues } from '@/hooks/useCommittedValues'
import { fmtCurrency, fmtNumber, numStr, toNum } from '@/utils/number'
import { isScheduleSaved } from '@/utils/schedule'
import { enteredNum } from '@/utils/derivedMath'
import { deriveSchedule2 } from './derived'
import CommaNumberInput from '@/components/core/CommaNumberInput'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import ScheduleActions from '@/components/core/ScheduleActions'
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

  // The blur-committed snapshot the derived mirror reads (defect #291). `form` still tracks every
  // keystroke because it drives the inputs; `committed` only advances when a field loses focus, so the
  // read-only figures settle once per field instead of churning mid-number — legacy's AJAX-on-blur
  // behaviour. Re-seeds whenever `data` is replaced (load / Save echo / Delete reload).
  const { committed, commit } = useCommittedValues(form, data)

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
    // Re-check the gate in the handler, not only in the button's `disabled` (defect #292 code
    // review): a disabled attribute is presentation, and any other route into this handler — a
    // mis-wired bar, a programmatic open, the modal's submit — would otherwise fire a DELETE for a
    // schedule that does not exist. Mirrors handleSave, which also re-validates here.
    if (saving || !data || !isScheduleSaved(data)) {
      return
    }
    setConfirmDeleteOpen(false)
    clearBanners() // the deleted schedule's check result / save banner are stale
    remove<{ message?: { text?: string } }>({
      fallback: 'Unable to delete Schedule 2.',
      // Schedule 2 never 404s: with the summary gone, a re-GET returns the 200 empty EDITABLE
      // document (no revisionCount). Reload it so the meta row / form reflect reality and the
      // Licensee can immediately re-enter data (legacy AF1), while keeping the API delete message.
      // This per-page empty-state lives at the call site (Story 29.6): list/re-GET pages re-seed
      // from the reload, single-doc reset-in-place pages (Schedules 1/3) reset in place instead.
      onSuccess: (delResp) => {
        const deleteMessage = delResp?.message?.text ?? null
        // Drop the optimistic-lock token BEFORE the reload is dispatched, so "this schedule is
        // saved" becomes false the instant the record is gone (defect #292 code review, face 2).
        // Schedules 1/3 avoid the whole problem by resetting in place; this is the same move,
        // narrowed to the token the gate reads.
        setData((prev) => (prev ? { ...prev, revisionCount: null } : prev))
        // RETURN the reload so delete→reload is ONE locked operation (PR #351 review). Clearing the
        // token alone closed the DELETE gate but left the WINDOW open: `run`'s `.finally` released
        // `saving` when the DELETE settled while this GET was still out, so for the length of the
        // reload — and permanently if it failed — `saving` was false, `form` still held the
        // pre-delete values and `revisionCount` was null. Save is gated on `saving`, not on the
        // persisted-record check, so a click in that window PUT `revisionCount: 0` and RE-CREATED
        // the schedule with the old figures; the reload then painted an empty document over a row
        // that now existed. Returning the promise keeps the lock held until the reload settles.
        return run(
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
  // Delete targets a persisted summary; an unsaved document has nothing to delete, so gate it exactly
  // like legacy isScheduleOpen() (BR-08 / S06). The absent-vs-null subtlety that made #292 possible
  // lives in the shared predicate — read it before touching this line.
  const scheduleSaved = isScheduleSaved(data)
  // Advisory per-field validation (backend authoritative); drives inline invalid states + Save gate.
  const fieldErrors = editable ? validateSchedule2(form) : {}

  // What the value rows render. While the schedule is editable, the figures that depend on entry come
  // from the display-only mirror fed by the COMMITTED (blurred) values, so they track data entry the
  // way legacy did; the Save echo then replaces the document and the mirror re-seeds from it. Outside
  // Draft / in view mode there is no entry, so the document's own server-computed figures are rendered
  // as-is (defect #291). `purchasedWoodOverhead` and `totalCompanyLogging` are wholly carried from
  // Schedules 3 and 1 and always come from `data` — the mirror deliberately does not return them.
  const figures = editable
    ? deriveSchedule2(data, {
        purchasedLogCostCost: enteredNum(committed[F_ITEM25_COST] ?? ''),
        lessLogSalesVolume: enteredNum(committed[F_ITEM26_VOLUME] ?? ''),
        lessLogSalesCost: enteredNum(committed[F_ITEM26_COST] ?? ''),
      })
    : data

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
        onBlur={() => commit(fieldKey, { invalid: Boolean(fieldErrors[fieldKey]) })}
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
      {perUnitCell(figures.purchasedLogCost.perUnit)}
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
      {perUnitCell(figures.lessLogSales.perUnit)}
    </TableRow>
  )

  // Read-only derived / carried block (never inputs, never sent on write).
  //
  // `sectionStart` draws a heavier top border to visually divide the table into its 4 cost groups —
  // a GROUPING cue and nothing else. The summary band is keyed off the row being calculated instead
  // (#411 Overall 5): every derived row gets one, the table's final figure takes the darker total
  // band and the rest the lighter subtotal band. The two used to ride the same flag, which shaded by
  // where a group happened to start rather than by what the row is — leaving Wood Overhead and Total
  // Company Logging Costs bare, and giving the grand total the subtotal grey.
  const derivedRow = (label: string, block: CostBlock, sectionStart = false, isTotal = false) => (
    <TableRow
      key={label}
      className={[
        isTotal ? 'schedule-2__total-row' : 'schedule-2__subtotal-row',
        sectionStart ? 'schedule-2__section-start' : null,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <TableCell>{label}</TableCell>
      {readOnlyCell(block.volume)}
      {readOnlyCell(block.cost)}
      {perUnitCell(block.perUnit)}
    </TableRow>
  )

  // Two instances, deliberately asymmetric: legacy carried Save + Check Status above the schedule and
  // Save + Check Status + Delete below it (schedule2.xhtml:35-36 vs :172-178), the same shape as
  // Schedules 1 and 3. Deleting the whole schedule is the one destructive action on this page, and
  // legacy kept it off the bar a reporter meets first (defect #292 — it used to render on both).
  const actionBar = (showDelete: boolean) => (
    <ScheduleActions
      className="schedule-2__actions"
      editable={editable}
      saving={saving}
      onSave={handleSave}
      onCheckStatus={handleCheckStatus}
      onDelete={() => setConfirmDeleteOpen(true)}
      showDelete={showDelete}
      scheduleSaved={scheduleSaved}
    />
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

        {actionBar(false)}

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
                {derivedRow('Subtotal:', figures.subtotal, true)}
                {item26Row}
                {derivedRow('Net Purchased/Private Log Cost:', figures.netPurchased, true)}
                {derivedRow('Total Company Logging Costs(Sch 1):', data.totalCompanyLogging)}
                {derivedRow('Total Average Logging Costs:', figures.totalAverage, true, true)}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-2__section">
          {editable ? (
            <CommentsTextArea
              id="comments"
              className="schedule-2__comments-field"
              labelText="If you have any additional comments, please enter them here:"
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

        {actionBar(true)}
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
