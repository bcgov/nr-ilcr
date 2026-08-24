import type { FC } from 'react'
import type Schedule1Response from '@/interfaces/Schedule1Response'
import type { LineItem } from '@/interfaces/Schedule1Response'
import type Schedule1Request from '@/interfaces/Schedule1Request'
import type CheckStatusResponse from '@/interfaces/CheckStatusResponse'
import { useState } from 'react'
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
  TextArea,
  TextInput,
} from '@carbon/react'
import { WRITABLE_LINE_ITEM_CODES } from '@/interfaces/Schedule1Request'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { useScheduleMutations } from '@/hooks/useScheduleMutations'
import { fmtCurrency, fmtNumber, groupInput, numStrGroup, toNum } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import ScheduleActions from '@/components/core/ScheduleActions'
import { useCommittedValues } from '@/hooks/useCommittedValues'
import { validateSchedule1 } from './validation'
import { deriveSchedule1, enteredFromForm } from './derived'
import './index.scss'

// ERR-001 (mill/year not selected) and ALT-001 (open-other-costs-before-save) and confirmDeleteMsg
// are client-side chrome (a suppression with no request / a Carbon Modal / a confirm Modal), so
// their verbatim text lives here. SUC-001/SUC-002 come from the API `message.text` (AD-8) — never
// hardcoded.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const ALT_SAVE_BEFORE_OTHER_COSTS = 'The schedule has to be saved before opening other costs'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'
const COMMENTS_MAX = 3500

const LINE_ITEM_LABELS: Record<number, string> = {
  12: 'Standing Tree to Loaded Truck',
  13: 'Log Transportation',
  14: 'Road Management',
  15: 'Road Construction Costs',
  16: 'Post Logging Treatment',
  17: 'Stumpage and Royalty',
  18: 'Depletion and Amortization',
  143: 'Forest Management Administration Costs (Sch 3)',
  144: 'Subtotal Company Logging Cost (no Silviculture)',
}
const WRITABLE = new Set<number>(WRITABLE_LINE_ITEM_CODES)

// Silviculture code -> label. All four volumes are user-entered; codes 1 & 2 also have an editable
// cost, while 139's cost is pulled from Schedule 3 and 140's is derived (both costs read-only).
const SILV_ROWS: { code: number; label: string; key: keyof Schedule1Response['silviculture'] }[] = [
  { code: 1, label: 'Actual $ Spent', key: 'actualSpent' },
  { code: 139, label: 'Less Silviculture Admin Costs', key: 'lessAdmin' },
  { code: 2, label: 'Accrued less Actual $ Spent', key: 'accruedLessActual' },
  { code: 140, label: 'Total Silviculture (As per Financial Statements)', key: 'total' },
]

type FieldValues = Record<string, string>

const mapLoadErrorDetail = (
  detail: string | undefined,
  millId: number | null,
  year: number | null,
): string => {
  if (detail === 'Schedule not found.' && millId != null && year != null) {
    return `No Schedule 1 exists for Mill ${millId} in Reporting Year ${year}. Select another mill/year from Home, or create Schedule 1 data for this context.`
  }
  return detail || 'Unable to load Schedule 1.'
}

// Seed editable form state from the loaded document (writable fields only).
function seedForm(doc: Schedule1Response): FieldValues {
  const values: FieldValues = {}
  for (const code of WRITABLE_LINE_ITEM_CODES) {
    const item = doc.lineItems.find((li) => li.costItemCode === code)
    values[`vol-${code}`] = numStrGroup(item?.volume)
    values[`cost-${code}`] = numStrGroup(item?.cost)
  }
  values['vol-1'] = numStrGroup(doc.silviculture.actualSpent?.volume)
  values['cost-1'] = numStrGroup(doc.silviculture.actualSpent?.cost)
  values['vol-2'] = numStrGroup(doc.silviculture.accruedLessActual?.volume)
  values['cost-2'] = numStrGroup(doc.silviculture.accruedLessActual?.cost)
  // Volume-only editable fields: 143/144 (line items) and 139/140 (silviculture).
  values['vol-143'] = numStrGroup(doc.lineItems.find((li) => li.costItemCode === 143)?.volume)
  values['vol-144'] = numStrGroup(doc.lineItems.find((li) => li.costItemCode === 144)?.volume)
  values['vol-139'] = numStrGroup(doc.silviculture.lessAdmin?.volume)
  values['vol-140'] = numStrGroup(doc.silviculture.total?.volume)
  values['otherCostsVolume'] = numStrGroup(doc.otherCosts.volume)
  values['comments'] = doc.comments ?? ''
  return values
}

function buildRequest(doc: Schedule1Response, form: FieldValues): Schedule1Request {
  return {
    revisionCount: doc.revisionCount ?? 0,
    comments: form['comments'].trim() === '' ? null : form['comments'],
    lineItems: WRITABLE_LINE_ITEM_CODES.map((code) => ({
      costItemCode: code,
      volume: toNum(form[`vol-${code}`]),
      cost: toNum(form[`cost-${code}`]),
    })),
    silviculture: {
      actualSpent: { volume: toNum(form['vol-1']), cost: toNum(form['cost-1']) },
      accruedLessActual: { volume: toNum(form['vol-2']), cost: toNum(form['cost-2']) },
      lessAdminVolume: toNum(form['vol-139']),
      totalVolume: toNum(form['vol-140']),
    },
    otherCostsVolume: toNum(form['otherCostsVolume']),
    forestMgmtAdminVolume: toNum(form['vol-143']),
    subtotalCompanyLoggingVolume: toNum(form['vol-144']),
  }
}

const Schedule1: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()
  const navigate = useNavigate()

  // Save/delete/check-status all run through the shared hook's guarded run() (Story 29.6): a stale
  // in-flight write can no longer repaint a newly-switched mill/year. `saving` is the single in-flight
  // lock for every write (it also gates Check Status), replacing the old separate save/checking locks.
  const {
    saving,
    message: saveMessage,
    actionError: saveError,
    checkResult,
    setMessage: setSaveMessage,
    setActionError: setSaveError,
    setCheckResult,
    clearBanners,
    resetBanners,
    save,
    remove,
    checkStatus,
  } = useScheduleMutations<CheckStatusResponse>({ path: '/v1/schedule1', millId, year, isCurrent })

  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  const [confirmNavOpen, setConfirmNavOpen] = useState(false)
  const [otherCostsBlockedOpen, setOtherCostsBlockedOpen] = useState(false)

  const { data, setData, form, setForm, setField, errorDetail, isLoading } =
    useScheduleDocument<Schedule1Response>({
      path: '/v1/schedule1',
      millId,
      year,
      contextMissing,
      seedForm,
      mapLoadError: mapLoadErrorDetail,
      onReset: resetBanners,
    })

  // The blur-committed snapshot the derived mirror reads (defect #291): `form` tracks every keystroke
  // because it drives the inputs, `committed` advances only when a field loses focus. Re-seeds
  // whenever `data` is replaced (load / Save echo / Delete reset).
  const { committed, commit } = useCommittedValues(form, data)

  // Re-group a numeric field's value on blur, so it reads like the plain-text cells beside it. Only
  // on blur — regrouping mid-keystroke would fight the caret. Invalid text is left as typed
  // (groupInput passes it through) so the inline error still points at what the user actually wrote.
  const groupField = (fieldKey: string) => () => {
    setForm((prev) => {
      const grouped = groupInput(prev[fieldKey] ?? '')
      return grouped === prev[fieldKey] ? prev : { ...prev, [fieldKey]: grouped }
    })
  }

  // A field's blur does two things: re-group its display (as before) and commit its value to the
  // derived mirror's baseline (defect #291). The GROUPED string is handed to `commit` explicitly —
  // `groupField` only queues its `setForm`, so reading the ref would give the pre-grouping value and
  // leave `committed` and `form` holding different strings, defeating the unchanged-value skip
  // (code review 2026-08-21). An invalid field holds its previous committed value.
  const commitField = (fieldKey: string) => () => {
    groupField(fieldKey)()
    const grouped = groupInput(form[fieldKey] ?? '')
    // Validated here rather than read from `fieldErrors`, which is computed further down (after the
    // early returns); same source of truth, and it only runs on blur.
    commit(fieldKey, {
      value: grouped,
      invalid: Boolean(validateSchedule1(form)[fieldKey]),
    })
  }

  const handleSave = () => {
    // Re-entrancy guard: the top + bottom Save buttons can be double-clicked within one tick before
    // `saving` disables them — avoid concurrent PUTs (which would trip the optimistic-lock 409).
    if (!data || saving) {
      return
    }
    // Advisory client-side validation (backend remains authoritative): block a doomed round-trip and
    // point the user at the highlighted fields.
    if (Object.keys(validateSchedule1(form)).length > 0) {
      setSaveMessage(null)
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    clearBanners() // drop any prior banners incl. a now-stale Check Status result
    save<Schedule1Response>(buildRequest(data, form), {
      fallback: 'Schedule could not be saved.',
      onSuccess: (doc) => {
        setData(doc)
        setForm(seedForm(doc))
        // SUC-001 verbatim from the API message field (AD-8), never hardcoded.
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
      fallback: 'Unable to delete Schedule 1.',
      // Delete removed the summary; a re-GET would 404, so reset to an empty schedule in place (no
      // re-fetch) and show SUC-002 from the API message. This per-page empty-state lives at the call
      // site (Story 29.6): single-doc Schedules 1/3 reset in place; list pages re-seed from a reload.
      onSuccess: (resp) => {
        setData((prev) =>
          prev
            ? {
                ...prev,
                // The summary is gone; there is nothing to edit or re-save (create-on-open is not
                // supported), so render the empty schedule read-only and disable the actions.
                editable: false,
                revisionCount: null,
                comments: null,
                lineItems: [],
                silviculture: {
                  actualSpent: null,
                  accruedLessActual: null,
                  lessAdmin: null,
                  total: null,
                },
                otherCosts: { volume: null, costSubtotal: null, perUnit: null, count: 0 },
              }
            : prev,
        )
        setForm({})
        setSaveMessage(resp?.message?.text ?? null)
      },
    })
  }

  const handleCheckStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners() // don't leave a stale Save success banner beside a new check result
    checkStatus<CheckStatusResponse>({
      fallback: 'Unable to check status.',
      onSuccess: setCheckResult,
    })
  }

  const handleOtherCosts = () => {
    // S08: before the schedule is saved/open, opening Other Costs is blocked with ALT-001. In the
    // current backend model an openable schedule is always saved (GET 404s for no summary), so this
    // guard is effectively unreachable.
    if (!data) {
      setOtherCostsBlockedOpen(true)
      return
    }
    // Navigating away from an editable Schedule 1 discards unsaved edits — confirm first (legacy
    // confirmNavigationMsg). A read-only schedule has nothing to lose, so open directly.
    if (data.editable) {
      setConfirmNavOpen(true)
      return
    }
    navigate({ to: '/schedule-1/other-costs' })
  }

  // Confirmed via the navigation Modal: discard unsaved edits and open Other Costs.
  const openOtherCosts = () => {
    setConfirmNavOpen(false)
    navigate({ to: '/schedule-1/other-costs' })
  }

  const header = <ScheduleTombstone title="Schedule 1" subtitle="Average Cost of Logging" />

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
            <LoadingScreen label="Loading Schedule 1" />
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
              title="Unable to load Schedule 1"
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
  const fieldErrors = editable ? validateSchedule1(form) : {}

  // The display-only mirror of every figure that moves with entry, fed by the COMMITTED values so the
  // read-only cells track data entry the way legacy did. Null outside Draft / in view mode, where
  // there is no entry and the document's own server-computed figures are rendered as-is (#291 AC7).
  const derived = editable ? deriveSchedule1(data, enteredFromForm(committed)) : null

  // A value cell: an editable TextInput when the field is writable and the schedule is editable,
  // otherwise read-only text. perUnit is always read-only (server-computed).
  const numberCell = (
    fieldKey: string,
    label: string,
    writable: boolean,
    current: number | null | undefined,
  ) =>
    editable && writable ? (
      // --input marks the cells whose value sits inside a TextInput: the field supplies its own
      // inline padding, so these keep Carbon's stock cell padding while the plain-text cells are
      // inset to match (see index.scss).
      <TableCell className="schedule-1__num schedule-1__num--input">
        <TextInput
          id={fieldKey}
          labelText={label}
          hideLabel
          size="sm"
          value={form[fieldKey] ?? ''}
          onChange={setField(fieldKey)}
          onBlur={commitField(fieldKey)}
          invalid={Boolean(fieldErrors[fieldKey])}
          invalidText={fieldErrors[fieldKey]}
        />
      </TableCell>
    ) : (
      <TableCell className="schedule-1__num">{fmtNumber(current)}</TableCell>
    )

  const lineItemRow = (item: LineItem) => {
    const code = item.costItemCode
    const label = LINE_ITEM_LABELS[code] ?? `Cost item ${code}`
    const writableVolume = WRITABLE.has(code)
    // Cost is writable for the writable codes; pulled (143/139) and derived (144) costs stay read-only.
    const writableCost = WRITABLE.has(code)
    return (
      <TableRow key={code}>
        <TableCell>{label}</TableCell>
        {numberCell(`vol-${code}`, `${label} volume`, writableVolume, item.volume)}
        {numberCell(`cost-${code}`, `${label} cost`, writableCost, item.cost)}
        <TableCell className="schedule-1__num">
          {fmtCurrency(derived ? derived.perUnit[code] : item.perUnit)}
        </TableCell>
      </TableRow>
    )
  }

  // 139's cost is pulled from Schedule 3 and stays served even mid-entry, because nothing on this
  // page is an input to it; only 140's cost has a mirror. Extracted from a nested ternary (SonarQube
  // 2026-08-21) -- worth doing, because the nesting hid that `derived` changes exactly ONE of these
  // three branches.
  const silvicultureCost = (code: number, item: LineItem | null): number | null | undefined => {
    if (code === 139) return data.lessSilvAdminCost
    if (code === 140) return derived ? derived.totalSilvicultureCost : data.totalSilvicultureCost
    return item?.cost
  }

  // $/m³ = cost ÷ volume (139/140 fold in the Schedule 3 pulls). Mirrored while editable so it
  // tracks entry; the document's server-computed figure otherwise (#291).
  //
  // NOTE the precedence is deliberately the OPPOSITE of `silvicultureCost` above: the mirror
  // supersedes every row here, 139 and 140 included, because `deriveSchedule1` computes a rate for
  // both (derived.ts:98,115) even though it computes a cost only for 140. Same two codes, different
  // answer -- which is precisely what a three-deep ternary is bad at showing.
  const silviculturePerUnit = (code: number, item: LineItem | null): number | null | undefined => {
    if (derived) return derived.perUnit[code]
    if (code === 139) return data.lessSilvAdminPerUnit
    if (code === 140) return data.totalSilviculturePerUnit
    return item?.perUnit
  }

  const silvicultureRow = (row: (typeof SILV_ROWS)[number]) => {
    const item = data.silviculture[row.key]
    // All four silviculture VOLUMES are user-entered; only 1 & 2 have an editable cost. 139's cost is
    // pulled from Schedule 3, 140's is derived — both read-only.
    const writableCost = row.code === 1 || row.code === 2
    const costValue = silvicultureCost(row.code, item)
    const perUnitValue = silviculturePerUnit(row.code, item)
    return (
      <TableRow key={row.code}>
        <TableCell>{row.label}</TableCell>
        {numberCell(`vol-${row.code}`, `${row.label} volume`, true, item?.volume)}
        {numberCell(`cost-${row.code}`, `${row.label} cost`, writableCost, costValue)}
        <TableCell className="schedule-1__num">{fmtCurrency(perUnitValue)}</TableCell>
      </TableRow>
    )
  }

  // Forest Management Admin (143): VOLUME is user-entered (8-digit); its COST is pulled from Schedule 3
  // (read-only). Subtotal Company Logging (144): VOLUME user-entered (8-digit); COST derived (—).
  // Both $/m³ cells are Schedule-3 cross-derivations deferred this story (—).
  const forestMgmtAdminRow = (
    <TableRow key={143}>
      <TableCell>{LINE_ITEM_LABELS[143]}</TableCell>
      {numberCell(
        'vol-143',
        'Forest Management Administration volume',
        true,
        data.lineItems.find((li) => li.costItemCode === 143)?.volume,
      )}
      <TableCell className="schedule-1__num">{fmtNumber(data.forestMgmtAdminCost)}</TableCell>
      <TableCell className="schedule-1__num">
        {fmtCurrency(derived ? derived.perUnit[143] : data.forestMgmtAdminPerUnit)}
      </TableCell>
    </TableRow>
  )

  const subtotalCompanyLoggingRow = (
    <TableRow key={144}>
      <TableCell>{LINE_ITEM_LABELS[144]}</TableCell>
      {numberCell(
        'vol-144',
        'Subtotal Company Logging volume',
        true,
        data.lineItems.find((li) => li.costItemCode === 144)?.volume,
      )}
      <TableCell className="schedule-1__num">
        {fmtNumber(derived ? derived.subtotalCompanyLoggingCost : data.subtotalCompanyLoggingCost)}
      </TableCell>
      <TableCell className="schedule-1__num">
        {fmtCurrency(derived ? derived.perUnit[144] : data.subtotalCompanyLoggingPerUnit)}
      </TableCell>
    </TableRow>
  )

  // Subtotal Other Costs (legacy: sits between Depletion & Amortization and Subtotal Company Logging).
  // The label is a link/button that opens the Other Costs sub-page (Story 2.5); the VOLUME is
  // user-entered (editable schedules) while the cost subtotal and $/m³ are server-derived (read-only).
  const otherCostsRow = (
    <TableRow key="other-costs">
      <TableCell>
        <Button
          kind="ghost"
          size="sm"
          className="schedule-1__other-costs-link"
          onClick={handleOtherCosts}
        >
          Subtotal Other Costs({data.otherCosts.count}):
        </Button>
      </TableCell>
      {editable ? (
        // This row builds its own field rather than going through numberCell, so it has to carry the
        // --input modifier itself — without it the cell takes the read-only indent and the field is
        // pushed out of line with every other input in the column.
        <TableCell className="schedule-1__num schedule-1__num--input">
          <TextInput
            id="otherCostsVolume"
            labelText="Subtotal Other Costs volume"
            hideLabel
            size="sm"
            value={form['otherCostsVolume'] ?? ''}
            onChange={setField('otherCostsVolume')}
            onBlur={commitField('otherCostsVolume')}
            invalid={Boolean(fieldErrors['otherCostsVolume'])}
            invalidText={fieldErrors['otherCostsVolume']}
          />
        </TableCell>
      ) : (
        <TableCell className="schedule-1__num">{fmtNumber(data.otherCosts.volume)}</TableCell>
      )}
      <TableCell className="schedule-1__num">{fmtNumber(data.otherCosts.costSubtotal)}</TableCell>
      <TableCell className="schedule-1__num">
        {fmtCurrency(derived ? derived.otherCostsPerUnit : data.otherCosts.perUnit)}
      </TableCell>
    </TableRow>
  )

  // Grand-total row (legacy "Total Company Logging Costs (Including total Silviculture Cost)"): the
  // Total Harvested Crown Timber volume (Sch 3), the total logging cost, and its $/m³ average.
  // Legacy closes the Silviculture panel before this row, so it carries a rule above it (index.scss).
  const totalCompanyLoggingRow = (
    <TableRow key="total-company-logging" className="schedule-1__grand-total-row">
      <TableCell>Total Company Logging Costs (Including total Silviculture Cost)</TableCell>
      <TableCell className="schedule-1__num">{fmtNumber(data.schedule3CrownVolume)}</TableCell>
      <TableCell className="schedule-1__num">
        {fmtNumber(derived ? derived.totalCompanyLoggingCost : data.totalCompanyLoggingCost)}
      </TableCell>
      <TableCell className="schedule-1__num">
        {fmtCurrency(
          derived ? derived.totalCompanyLoggingPerUnit : data.totalCompanyLoggingPerUnit,
        )}
      </TableCell>
    </TableRow>
  )

  // Crown Timber Volume for all fields (Sch 3): BR-03 source, read-only. A plain label + read-only box
  // (no table) laid out on the SAME fixed column grid as the cost tables below (46% / 18% / 18% / 18%)
  // so the box lines up under the Volume m³ column (legacy layout). hideLabel keeps the descriptive
  // text as the input's a11y name (visually hidden) while "Volume m³" shows as the caption.
  const crownVolumeField = (
    <Column sm={4} md={8} lg={16} className="schedule-1__section schedule-1__crown">
      <span className="schedule-1__crown-label">Crown Timber Volume for all fields (Sch 3):</span>
      <div className="schedule-1__crown-field">
        <span className="schedule-1__crown-unit">Volume m³</span>
        <TextInput
          id="schedule3CrownVolume"
          labelText="Crown Timber Volume for all fields (Sch 3)"
          hideLabel
          size="sm"
          value={numStrGroup(data.schedule3CrownVolume)}
          onChange={() => undefined}
          disabled
        />
      </div>
    </Column>
  )

  // Two instances, deliberately asymmetric: legacy carried Save + Check Status above the schedule and
  // Save + Check Status + Delete below it (schedule1.xhtml:35-38 vs :796-803). Deleting the whole
  // schedule is the one destructive action on this page, and legacy kept it off the bar a reporter
  // meets first.
  const actionBar = (showDelete: boolean) => (
    <ScheduleActions
      className="schedule-1__actions"
      editable={editable}
      saving={saving}
      onSave={handleSave}
      onCheckStatus={handleCheckStatus}
      onDelete={() => setConfirmDeleteOpen(true)}
      showDelete={showDelete}
    />
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {/* Advisory warnings from the GET (WRN-001 crown pre-fill). Verbatim text from the API (AD-8). */}
        {(data.warnings ?? []).map((w, i) => (
          // Static, append-only notification list (never reordered); index disambiguates messages
          // that share the same key/text.
          // eslint-disable-next-line @eslint-react/no-array-index-key
          <Column key={`warn-${w.key}-${i}`} sm={4} md={8} lg={16}>
            <InlineNotification
              kind="warning"
              lowContrast
              title="Notice"
              subtitle={w.text || w.key}
            />
          </Column>
        ))}
        {saveMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={saveMessage} />
        )}
        {saveError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={saveError} />
        )}

        {/* Check Status result (Story 2.7). Severity is carried by the notification kind AND an explicit
            title word (Success/Error/Warning) — not colour alone (WCAG 2.1 AA). Verbatim text (AD-8). */}
        {checkResult?.requirementsMet && checkResult.message && (
          <NotificationColumn
            kind="success"
            title="Requirements met"
            subtitle={checkResult.message.text}
          />
        )}
        {(checkResult?.errors ?? []).map((e, i) => (
          <NotificationColumn
            // Static, append-only notification list (never reordered); index disambiguates messages
            // that share the same key/text.
            // eslint-disable-next-line @eslint-react/no-array-index-key
            key={`check-err-${e.text || e.key}-${i}`}
            kind="error"
            title="Error"
            subtitle={e.text || e.key}
          />
        ))}
        {(checkResult?.warnings ?? []).map((w, i) => (
          // Static, append-only notification list (never reordered); index disambiguates messages
          // that share the same key/text.
          // eslint-disable-next-line @eslint-react/no-array-index-key
          <Column key={`check-warn-${w.text || w.key}-${i}`} sm={4} md={8} lg={16}>
            <InlineNotification
              kind="warning"
              lowContrast
              title="Warning"
              subtitle={w.text || w.key}
            />
          </Column>
        ))}

        {actionBar(false)}

        {crownVolumeField}

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          {/* No visible section title (legacy form has none); aria-label carries the name for a11y. */}
          <TableContainer>
            <Table aria-label="Company Logging Costs" className="schedule-1__cost-table">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
                  <TableHeader className="schedule-1__num">Volume m³</TableHeader>
                  <TableHeader className="schedule-1__num">Cost $</TableHeader>
                  <TableHeader className="schedule-1__num">$ / m³</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {/* Legacy form order: 12–16, 143, 17, 18, Other Costs, 144 (schedule1.xhtml). */}
                {[12, 13, 14, 15, 16, 143, 17, 18].map((code) => {
                  if (code === 143) {
                    return forestMgmtAdminRow
                  }
                  const item = data.lineItems.find((li) => li.costItemCode === code)
                  return item ? lineItemRow(item) : null
                })}
                {otherCostsRow}
                {subtotalCompanyLoggingRow}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          <TableContainer title="Silviculture">
            <Table aria-label="Silviculture" className="schedule-1__cost-table">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
                  <TableHeader className="schedule-1__num">Volume m³</TableHeader>
                  <TableHeader className="schedule-1__num">Cost $</TableHeader>
                  <TableHeader className="schedule-1__num">$ / m³</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {SILV_ROWS.map(silvicultureRow)}
                {totalCompanyLoggingRow}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          {editable ? (
            <TextArea
              id="comments"
              labelText="If you have any additional comments, please enter them here:"
              enableCounter
              maxCount={COMMENTS_MAX}
              value={form['comments'] ?? ''}
              onChange={setField('comments')}
            />
          ) : (
            <>
              <h3 className="schedule-1__heading">Comments</h3>
              <p className="schedule-1__comments">{data.comments ?? '—'}</p>
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

      {/* Discard-unsaved-edits confirm before leaving an editable schedule for Other Costs. */}
      {editable && (
        <Modal
          open={confirmNavOpen}
          modalHeading="Leave Schedule 1"
          primaryButtonText="Continue"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmNavOpen(false)}
          onRequestSubmit={openOtherCosts}
        >
          <p>{CONFIRM_NAVIGATION}</p>
        </Modal>
      )}

      {/* ALT-001: schedule must be saved before Other Costs can open (informational, single action). */}
      {otherCostsBlockedOpen && (
        <Modal
          open
          passiveModal
          modalHeading="Save required"
          onRequestClose={() => setOtherCostsBlockedOpen(false)}
        >
          <p>{ALT_SAVE_BEFORE_OTHER_COSTS}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule1
