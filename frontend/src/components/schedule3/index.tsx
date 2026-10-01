import OriginalValueIndicator from '@/components/core/OriginalValueIndicator'
import type { OriginalValues } from '@/interfaces/OriginalValue'
import type { FC } from 'react'
import type Schedule3Response from '@/interfaces/Schedule3Response'
import type { CostLine, ThreeColumnTotal } from '@/interfaces/Schedule3Response'
import type Schedule3Request from '@/interfaces/Schedule3Request'
import type { Schedule3CheckRequest } from '@/interfaces/Schedule3Request'
import type CheckStatusResponse from '@/interfaces/CheckStatusResponse'
import { useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Grid,
  Modal,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react'
import CommentsTextArea from '@/components/core/CommentsTextArea'
import { ALL_LINE_CODES, HARVEST_POP_LINE_CODES } from '@/interfaces/Schedule3Request'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { useScheduleMutations } from '@/hooks/useScheduleMutations'
import { fmtCurrency, fmtNumber, groupInput, numStrGroup, toNum } from '@/utils/number'
import { isScheduleSaved } from '@/utils/schedule'
import NotificationColumn from '@/components/core/NotificationColumn'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import ScheduleActions from '@/components/core/ScheduleActions'
import ConfirmNavigationModal from '@/components/core/ConfirmNavigationModal'
import { useCommittedValues } from '@/hooks/useCommittedValues'
import { validateSchedule3 } from './validation'
import { deriveSchedule3, enteredFromForm } from './derived'
import './index.scss'

// Client-side chrome (a suppression with no request / a browser alert / a confirm dialog), so the
// verbatim text lives here. SUC/WRN/FLD strings come from the API `message`/`warnings`/`detail`
// (AD-8) — never hardcoded. Shared strings reuse Schedule 1's exact wording.
const ALT_S111 = 'Annual Rent (Forest Act, S111) is recorded as an Unacceptable Cost.'
// ALT-002 / ALT-003: both sub-pages require a saved parent, and legacy wrote a SEPARATE alert per
// link rather than one shared message — `schedule3.xhtml:267` on the Subtotal Other Costs link and
// `:293` on the Included Unacceptable Costs one. Both legacy-verbatim, and the inconsistency between
// them is the contract, not a typo to tidy: ALT-003 capitalises "Unacceptable" and names a different
// page. Only ALT-002 is shared with Schedule 1 (`schedule1.xhtml:497`, its single such link). Routing
// both links through ALT-002 was defect #373. (ALT-001 is the S111 alert above — not this pair.)
const ALT_SAVE_BEFORE_OTHER_COSTS = 'The schedule has to be saved before opening other costs'
const ALT_SAVE_BEFORE_UNACCEPTABLE =
  'The schedule has to be saved before opening Unacceptable costs'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'
const COMMENTS_MAX = 3500

// Story 4.4 sub-page routes (links + counts render here; the pages themselves are Story 4.4).
const ROUTE_OTHER_ACCEPTABLE = '/schedule-3/other-acceptable-costs'
const ROUTE_UNACCEPTABLE = '/schedule-3/included-unacceptable-costs'

// The save-first message is keyed by route through an EXHAUSTIVE map rather than selected by a
// `route === ROUTE_UNACCEPTABLE ? … : …` ternary. The ternary reads identically today but makes
// ALT-002 the CATCH-ALL, which is the exact shape of defect #373: a third sub-page, or either route
// constant re-valued, would silently inherit the Other Costs wording and nothing would fail. With a
// `Record<SubPageRoute, string>` and `openSubPage` narrowed to `SubPageRoute`, an unmapped or
// mistyped route is a COMPILE error instead of a wrong message on screen.
type SubPageRoute = typeof ROUTE_OTHER_ACCEPTABLE | typeof ROUTE_UNACCEPTABLE
const ALT_SAVE_BEFORE: Record<SubPageRoute, string> = {
  [ROUTE_OTHER_ACCEPTABLE]: ALT_SAVE_BEFORE_OTHER_COSTS,
  [ROUTE_UNACCEPTABLE]: ALT_SAVE_BEFORE_UNACCEPTABLE,
}

const CODE_ANNUAL_RENTS = 29
// Fixed-line labels — verbatim legacy schedule3.xhtml outputLabels (frontend-owned, like Schedule 1).
const LINE_LABELS: Record<number, string> = {
  27: 'Licenses, Fees, Insurance',
  28: 'Taxes, Leases, Rentals',
  29: 'Annual Rents',
  30: 'Wages/Salaries, incl Benefits',
  31: 'Vehicle Expense',
  32: 'Office Expense',
  33: 'Scaling Expense',
  34: 'Cruising & Layout Expense',
  35: 'Residue & Waste Expense',
  36: 'Depreciation Expense',
  37: 'Silviculture Admin Costs',
}
const HARVEST_POP = new Set<number>(HARVEST_POP_LINE_CODES)
// Harvest-only lines whose PO&P is not captured at all (legacy inputHidden) — render a blank cell,
// NOT the backend's 0. Scaling (33) is excluded: it shows a server-derived PO&P read-only.
const POP_HIDDEN = new Set<number>([29, 37])

type FieldValues = Record<string, string>

// Seed editable form state from the loaded document (entered fields only).
function seedForm(doc: Schedule3Response): FieldValues {
  const values: FieldValues = {}
  for (const code of ALL_LINE_CODES) {
    const line = doc.lineItems.find((li) => li.costItemCode === code)
    values[`harvest-${code}`] = numStrGroup(line?.harvest)
    if (HARVEST_POP.has(code)) {
      values[`pop-${code}`] = numStrGroup(line?.pop)
    }
  }
  values['popTimberVolume'] = numStrGroup(doc.popTimber?.volume)
  values['crownTimberVolume'] = numStrGroup(doc.crownTimber?.volume)
  values['overrideHarvestTotalPop'] = doc.overrideHarvestTotalPop ?? 'N'
  values['comments'] = doc.comments ?? ''
  return values
}

function buildRequest(doc: Schedule3Response, form: FieldValues): Schedule3Request {
  return {
    revisionCount: doc.revisionCount ?? 0,
    comments: form['comments'].trim() === '' ? null : form['comments'],
    overrideHarvestTotalPop: form['overrideHarvestTotalPop'] ?? 'N',
    // All 11 harvest codes; PO&P only for the both-columns lines (server ignores it for 29/33/37).
    lineItems: ALL_LINE_CODES.map((code) => ({
      costItemCode: code,
      harvest: toNum(form[`harvest-${code}`]),
      pop: HARVEST_POP.has(code) ? toNum(form[`pop-${code}`] ?? '') : null,
    })),
    popTimberVolume: toNum(form['popTimberVolume']),
    crownTimberVolume: toNum(form['crownTimberVolume']),
  }
}

// The Check Status body (#359): every checked value as it is ON SCREEN — `form`, the keystroke state,
// not the blur-committed snapshot, because that is what legacy's full postback submitted. The
// Override travels too: on screen it drives BOTH Harvest≥PO&P rules, the fixed-line one and the
// Other Acceptable subtotal one. `toNum` returns null for a blank field and that null is carried
// through deliberately: the server's check is a pure null test (a stored `0` passes), so a `?? 0`
// here would turn a missing value into a pass. The item-124/38 sub-page rows are not on this screen
// and are not sent.
function buildCheckRequest(form: FieldValues): Schedule3CheckRequest {
  return {
    overrideHarvestTotalPop: form['overrideHarvestTotalPop'] ?? 'N',
    lineItems: ALL_LINE_CODES.map((code) => ({
      costItemCode: code,
      harvest: toNum(form[`harvest-${code}`] ?? ''),
      pop: HARVEST_POP.has(code) ? toNum(form[`pop-${code}`] ?? '') : null,
    })),
    popTimberVolume: toNum(form['popTimberVolume'] ?? ''),
    crownTimberVolume: toNum(form['crownTimberVolume'] ?? ''),
  }
}

const mapLoadErrorDetail = (detail: string | undefined): string =>
  detail || 'Unable to load Schedule 3.'

const PAGE_HEADER = (
  <ScheduleTombstone title="Schedule 3" subtitle="Forest Management Administration Costs" />
)

const Schedule3: FC = () => {
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
  } = useScheduleMutations<CheckStatusResponse>({ path: '/v1/schedule3', millId, year, isCurrent })

  const [saveWarnings, setSaveWarnings] = useState<string[]>([])
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  // The sub-page whose open the save-first gate REFUSED (null = modal closed). A route rather than a
  // boolean because legacy's wording names the link that was clicked (ALT-002 vs ALT-003, defect
  // #373) — one source of truth, so no stale message can outlive the click that raised it.
  const [blockedRoute, setBlockedRoute] = useState<SubPageRoute | null>(null)
  // The sub-page a "Leave Schedule 3" confirm is pending for (null = modal closed). Deliberately
  // distinct from `blockedRoute` above — that one means "the gate refused", this one "a discard is
  // pending" — and neither handler ever writes the other.
  const [pendingRoute, setPendingRoute] = useState<string | null>(null)

  // Check Status describes one exact screen snapshot (#359). Incremented synchronously whenever a
  // checked field changes, so an older response cannot repaint a verdict over newer values.
  const checkSnapshotVersionRef = useRef(0)

  const invalidateCheckResult = () => {
    checkSnapshotVersionRef.current += 1
    setCheckResult(null)
  }

  const { data, setData, form, setForm, setField, loadState } =
    useScheduleDocument<Schedule3Response>({
      path: '/v1/schedule3',
      scheduleName: 'Schedule 3',
      header: PAGE_HEADER,
      millId,
      year,
      contextMissing,
      seedForm,
      mapLoadError: mapLoadErrorDetail,
      // saveWarnings is page-specific transient banner state; drop it on each fresh load alongside
      // the shared banners (resetBanners).
      onReset: () => {
        resetBanners()
        setSaveWarnings([])
      },
    })

  // The blur-committed snapshot the derived mirror reads (defect #291): `form` tracks every keystroke
  // because it drives the inputs, `committed` advances only when a field loses focus. Re-seeds
  // whenever `data` is replaced (load / Save echo / Delete reset).
  const { committed, commit } = useCommittedValues(form, data)

  // Every input on this page but the comments is a checked field — the Override included — so an
  // edit to any of them makes a shown Check Status verdict stale.
  const setCheckedField = (fieldKey: string) => {
    const set = setField(fieldKey)
    return (event: Parameters<typeof set>[0]) => {
      invalidateCheckResult()
      set(event)
    }
  }

  // Re-group a numeric field's value on blur, so it reads like the plain-text cells beside it. Only
  // on blur — regrouping mid-keystroke would fight the caret. Invalid text is left as typed
  // (groupInput passes it through) so the inline error still points at what the user actually wrote.
  const groupField = (key: string) => {
    setForm((prev) => {
      const grouped = groupInput(prev[key] ?? '')
      return grouped === prev[key] ? prev : { ...prev, [key]: grouped }
    })
  }

  const handleSave = () => {
    // Re-entrancy guard: the top + bottom Save buttons can be double-clicked within one tick before
    // `saving` disables them — avoid concurrent PUTs (which would trip the optimistic-lock 409).
    if (!data || saving) {
      return
    }
    // Advisory client-side validation (backend authoritative): block a doomed round-trip.
    if (Object.keys(validateSchedule3(form)).length > 0) {
      setSaveMessage(null)
      setSaveWarnings([])
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    clearBanners() // drop any prior banners incl. a now-stale Check Status result
    setSaveWarnings([])
    save<Schedule3Response>(buildRequest(data, form), {
      fallback: 'Schedule could not be saved.',
      onSuccess: (doc) => {
        setData(doc)
        setForm(seedForm(doc))
        // SUC-001 verbatim from the API message field (AD-8), never hardcoded.
        setSaveMessage(doc.message?.text ?? null)
        // BR-09 crown-push outcome (WRN-001/002) rides the echo's warnings channel.
        setSaveWarnings((doc.warnings ?? []).map((w) => w.text || w.key))
      },
    })
  }

  const handleDelete = () => {
    // Re-validate here, not just on the button's `disabled`: a disabled attribute is presentation,
    // and any other route into this handler (a mis-wired bar, a programmatic open, the modal's
    // submit) would otherwise fire a DELETE for a schedule that does not exist. Until defect #296
    // the server made that harmless — it 404'd — but the DELETE is idempotent now and answers 200,
    // so a stray call would show "Data deleted successfully" for a record that never existed.
    // Schedule 2 has had this gate since #292; Schedules 1/3 relied on the 404 that is gone.
    if (saving || !data || !isScheduleSaved(data)) {
      return
    }
    setConfirmDeleteOpen(false)
    clearBanners() // the deleted schedule's check result / save banner are stale
    setSaveWarnings([])
    remove<{ message?: { text?: string } }>({
      fallback: 'Unable to delete Schedule 3.',
      // Delete removed the summary. A re-GET no longer 404s (defect #296) — it serves the 200 empty
      // EDITABLE document — but this page still resets IN PLACE rather than re-fetching, which lands
      // on the same state without the extra round trip. This per-page empty-state lives at the call
      // site (Story 29.6): single-doc Schedules 1/3 reset in place; list pages re-seed from a reload.
      onSuccess: (resp) => {
        const empty: ThreeColumnTotal = { harvest: null, pop: null, crown: null }
        setData((prev) =>
          prev
            ? {
                ...prev,
                // The summary is gone, so the record is unsaved — but it is NOT uneditable. This
                // pinned `editable: false` when a re-GET would have 404'd and create-on-open was not
                // supported; defect #296 made both false. The server serves a 200 empty EDITABLE
                // document for this state and accepts a PUT that re-creates it, so freezing the form
                // would strand the Licensee on a read-only blank page and force a browser reload to
                // re-enter data (legacy AF1 expects immediate re-entry). `editable` is left as the
                // server last reported it; dropping `revisionCount` is what closes the Delete gate.
                revisionCount: null,
                overrideHarvestTotalPop: 'N',
                comments: null,
                lineItems: [],
                popTimber: { volume: null, cost: null, perUnit: null },
                crownTimber: { volume: null, cost: null, perUnit: null },
                totalOverhead: { volume: null, cost: null, perUnit: null },
                subtotalOtherCosts: empty,
                subtotalActualCosts: empty,
                includedUnacceptableCosts: empty,
                totalCosts: empty,
                otherAcceptableCount: 0,
                unacceptableCount: 0,
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
    // Legacy Check Status is validateClient="true": invalid entered values block the action with the
    // same FLD-* messages Save uses. Without this gate `toNum` would send `12x` as null and the
    // verdict would misreport a typo as "Value Required" (#359).
    // Gated on `editable`, exactly as `fieldErrors` is: a read-only page highlights nothing, so a stored
    // value failing the client range check must not block the check silently.
    if (data.editable && Object.keys(validateSchedule3(form)).length > 0) {
      setSaveMessage(null)
      setSaveWarnings([])
      setCheckResult(null)
      setSaveError('Please correct the highlighted fields before checking status.')
      return
    }
    clearBanners() // don't leave a stale Save success banner beside a new check result
    setSaveWarnings([])
    const submittedSnapshotVersion = checkSnapshotVersionRef.current
    // The body carries the screen (#359); nothing is persisted (AD-5).
    checkStatus<CheckStatusResponse>(
      {
        fallback: 'Unable to check status.',
        onSuccess: (result) => {
          if (checkSnapshotVersionRef.current === submittedSnapshotVersion) {
            setCheckResult(result)
          }
        },
      },
      buildCheckRequest(form),
    )
  }

  const openSubPage = (route: SubPageRoute) => {
    // Both sub-pages require a SAVED Schedule 3: their controllers still call
    // validateScheduleViewable (deliberately kept — #296 D1), so opening one from a never-saved
    // schedule would 404. Schedule 3 never had this gate, because before defect #296 the parent
    // page itself 404'd when unsaved and the case could not arise. It can now.
    if (!data || !isScheduleSaved(data)) {
      // Store WHICH link was refused, so the modal can carry that link's own legacy wording.
      setBlockedRoute(route)
      return
    }
    // Navigating away from an editable schedule discards unsaved edits — confirm via a Carbon Modal
    // (legacy confirmNavigationMsg) instead of a native browser dialog. A read-only schedule has
    // nothing to lose, so open directly.
    if (data?.editable) {
      setPendingRoute(route)
      return
    }
    navigate({ to: route })
  }

  // Confirmed via the "Leave Schedule 3" Modal: discard unsaved edits and open the pending sub-page.
  const confirmLeave = () => {
    if (pendingRoute === null) {
      return
    }
    const route = pendingRoute
    setPendingRoute(null)
    navigate({ to: route })
  }

  if (loadState) return loadState

  if (!data) {
    return null
  }

  const editable = data.editable
  // Advisory per-field validation (backend authoritative); drives inline invalid states + Save gate.
  const fieldErrors = editable ? validateSchedule3(form) : {}

  // The display-only mirror of every figure that moves with entry, fed by the COMMITTED values so the
  // read-only cells track data entry the way legacy did. Null whenever the document is NOT editable
  // for this caller — since Story 16.1 that is the role×status matrix, not Draft alone, so an
  // administrator correcting at Submitted or Verified DOES get the mirror — or in view mode, where
  // there is no entry and the document's own server-computed figures are rendered as-is (#291 AC7).
  const derived = editable ? deriveSchedule3(data, enteredFromForm(committed)) : null

  // A value cell: an editable TextInput when writable and the schedule is editable, else read-only
  // text. `onBlur` lets the Annual Rents Harvest field raise the S111 alert (legacy onchange).
  // Legacy paired each entered cell with an indicator (Schedule3DAO.java:147-321). The derived
  // total rows and the crown column carry none: nothing stores them.
  const indicator = (
    originals: OriginalValues | null | undefined,
    field: string | undefined,
    label: string,
    current: number | null | undefined,
    typed?: string,
  ) =>
    field === undefined ? null : (
      <OriginalValueIndicator
        originals={originals}
        field={field}
        current={typed ?? current}
        label={label}
      />
    )

  const numberCell = (
    fieldKey: string,
    label: string,
    writable: boolean,
    current: number | null | undefined,
    onBlur?: () => void,
    originals?: OriginalValues | null,
    originalField?: string,
  ) =>
    editable && writable ? (
      // --input: the value lives inside a TextInput, which supplies its own inline padding. The
      // plain-text cells are indented to match it (see index.scss) so the column shares one left edge.
      <TableCell className="schedule-3__num schedule-3__num--input">
        <TextInput
          id={fieldKey}
          labelText={label}
          hideLabel
          size="sm"
          value={form[fieldKey] ?? ''}
          onChange={setCheckedField(fieldKey)}
          // Re-group the value, commit it to the derived mirror's baseline (#291), AND run the
          // caller's own blur hook (the Annual Rents S111 alert). The GROUPED string is passed
          // explicitly so `committed` and `form` hold the same text, and an invalid field holds its
          // previous committed value rather than driving the cascade (code review 2026-08-21).
          onBlur={() => {
            groupField(fieldKey)
            commit(fieldKey, {
              value: groupInput(form[fieldKey] ?? ''),
              invalid: Boolean(fieldErrors[fieldKey]),
            })
            onBlur?.()
          }}
          invalid={Boolean(fieldErrors[fieldKey])}
          invalidText={fieldErrors[fieldKey]}
        />
        {indicator(originals, originalField, label, current, form[fieldKey])}
      </TableCell>
    ) : (
      <TableCell className="schedule-3__num">
        {fmtNumber(current)}
        {indicator(originals, originalField, label, current)}
      </TableCell>
    )

  const lineRow = (line: CostLine) => {
    const code = line.costItemCode
    const label = LINE_LABELS[code] ?? `Cost item ${code}`
    // The crown column, and Scaling (33)'s read-only PO&P, come from the mirror while editable so they
    // track entry; from the document otherwise (#291).
    const shown = derived ? derived.lines[code] : line
    const showPop = HARVEST_POP.has(code)
    const harvestBlur = code === CODE_ANNUAL_RENTS ? () => window.alert(ALT_S111) : undefined
    // Annual Rents (29) and Silviculture Admin (37) have NO PO&P (legacy renders the field hidden);
    // the backend returns pop=0 for them, so blank the cell (—) rather than showing "0" (AC2).
    // Scaling (33) keeps its server-derived PO&P shown read-only.
    const popCell = POP_HIDDEN.has(code) ? (
      <TableCell className="schedule-3__num">—</TableCell>
    ) : (
      numberCell(
        `pop-${code}`,
        `${label} PO&P`,
        showPop,
        shown.pop,
        undefined,
        line.originalValues,
        'pop',
      )
    )
    return (
      <TableRow key={code}>
        <TableCell>{label}</TableCell>
        {numberCell(
          `harvest-${code}`,
          `${label} Harvest`,
          true,
          line.harvest,
          harvestBlur,
          line.originalValues,
          'harvest',
        )}
        {popCell}
        <TableCell className="schedule-3__num">{fmtNumber(shown.crown)}</TableCell>
      </TableRow>
    )
  }

  // A read-only derived three-column total row. `isTotal` marks the schedule's FINAL figure (Total
  // Costs), which takes the darker band; the intermediate subtotals take the lighter one (#411
  // Overall 5). Note the final figure is not the final ROW — the Override dropdown sits below it and
  // is an input, so it carries no band at all.
  const totalRow = (key: string, label: string, total: ThreeColumnTotal, isTotal = false) => (
    <TableRow key={key} className={isTotal ? 'schedule-3__total-row' : 'schedule-3__subtotal-row'}>
      <TableCell>{label}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.harvest)}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.pop)}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.crown)}</TableCell>
    </TableRow>
  )

  // A read-only derived total row whose label cell is the count-labelled sub-page link (Story 4.4).
  // `popHidden` blanks the PO&P cell (—) for Included Unacceptable Costs, whose PO&P is a legacy
  // inputHidden (never shown) — matching the Annual Rents / Silviculture Admin blanks (POP_HIDDEN),
  // NOT the backend's 0. Subtotal Other Costs keeps its shown PO&P (legacy disabled box).
  const subPageRow = (
    key: string,
    label: string,
    count: number,
    route: SubPageRoute,
    total: ThreeColumnTotal,
    popHidden = false,
  ) => (
    // Derived like `totalRow`, and always an intermediate subtotal, so always the lighter band.
    <TableRow key={key} className="schedule-3__subtotal-row">
      <TableCell>
        <Button
          kind="ghost"
          size="sm"
          className="schedule-3__link"
          onClick={() => openSubPage(route)}
        >
          {`${label} (${count}):`}
        </Button>
      </TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.harvest)}</TableCell>
      <TableCell className="schedule-3__num">{popHidden ? '—' : fmtNumber(total.pop)}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.crown)}</TableCell>
    </TableRow>
  )

  const timberRow = (
    label: string,
    fieldKey: string | null,
    block: {
      volume: number | null
      cost: number | null
      perUnit: number | null
      originalValues?: OriginalValues | null
    },
  ) => (
    <TableRow key={label}>
      <TableCell>{label}</TableCell>
      {fieldKey !== null ? (
        numberCell(
          fieldKey,
          `${label} Harvest Volume`,
          true,
          block.volume,
          undefined,
          block.originalValues,
          'volume',
        )
      ) : (
        <TableCell className="schedule-3__num">{fmtNumber(block.volume)}</TableCell>
      )}
      <TableCell className="schedule-3__num">{fmtNumber(block.cost)}</TableCell>
      <TableCell className="schedule-3__num">{fmtCurrency(block.perUnit)}</TableCell>
    </TableRow>
  )

  // Two instances, deliberately asymmetric: legacy carried Save + Check Status above the schedule and
  // Save + Check Status + Delete below it (schedule3.xhtml:37-38 vs :420-426), the same shape as
  // Schedule 1. Deleting the whole schedule is the one destructive action on this page, and legacy
  // kept it off the bar a reporter meets first.
  // Delete is additionally gated on a persisted record, as legacy gated it on isScheduleOpen() as
  // well as on edit rights. That gate is now LOAD-BEARING, not belt-and-braces: this page used to be
  // protected incidentally because the GET 404'd when unsaved, and defect #296 removed exactly that
  // — an unsaved schedule now renders a full editable form, so `isScheduleSaved` is the only thing
  // standing between a never-saved schedule and a Delete button. The absent-vs-null subtlety lives
  // in `isScheduleSaved`.
  const actionBar = (showDelete: boolean) => (
    <ScheduleActions
      className="schedule-3__actions"
      editable={editable}
      saving={saving}
      onSave={handleSave}
      onCheckStatus={handleCheckStatus}
      onDelete={() => setConfirmDeleteOpen(true)}
      showDelete={showDelete}
      scheduleSaved={isScheduleSaved(data)}
    />
  )

  return (
    <div className="app-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        {/* Advisory warnings from a mutation echo (BR-09 crown push). Verbatim text (AD-8). */}
        {saveWarnings.map((w) => (
          <NotificationColumn key={`warn-${w}`} kind="warning" title="Notice" subtitle={w} />
        ))}
        {saveMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={saveMessage} />
        )}
        {saveError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={saveError} />
        )}

        {/* Check Status result. Severity is carried by the notification kind AND an explicit title
            word (Success/Error) — not colour alone (WCAG 2.1 AA). Verbatim text (AD-8). */}
        {checkResult?.requirementsMet && checkResult.message && (
          <NotificationColumn
            kind="success"
            title="Requirements met"
            subtitle={checkResult.message.text}
          />
        )}
        {(checkResult?.errors ?? []).map((e) => (
          <NotificationColumn
            key={`check-err-${e.text || e.key}`}
            kind="error"
            title="Error"
            subtitle={e.text || e.key}
          />
        ))}

        {actionBar(false)}

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          {/* No visible section title (legacy form has none); aria-label carries the name for a11y. */}
          <TableContainer>
            <Table aria-label="Administration Costs" className="schedule-3__cost-table">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
                  <TableHeader className="schedule-3__num">Harvest Total $</TableHeader>
                  <TableHeader className="schedule-3__num">PO&P $</TableHeader>
                  <TableHeader className="schedule-3__num">Crown $</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {/* Legacy form order: 27,28,29,30,31,32,33,34,35,36,37 (schedule3.xhtml). */}
                {ALL_LINE_CODES.map((code) => {
                  const line =
                    data.lineItems.find((li) => li.costItemCode === code) ??
                    ({ costItemCode: code, harvest: null, pop: null, crown: null } as CostLine)
                  return lineRow(line)
                })}
                {subPageRow(
                  'subtotalOther',
                  'Subtotal Other Costs',
                  data.otherAcceptableCount,
                  ROUTE_OTHER_ACCEPTABLE,
                  data.subtotalOtherCosts,
                )}
                {totalRow(
                  'subtotalActual',
                  'Subtotal (Actual Costs)',
                  derived ? derived.subtotalActualCosts : data.subtotalActualCosts,
                )}
                {subPageRow(
                  'inclUnacceptable',
                  'Included Unacceptable Costs',
                  derived ? derived.unacceptableCount : data.unacceptableCount,
                  ROUTE_UNACCEPTABLE,
                  derived ? derived.includedUnacceptableCosts : data.includedUnacceptableCosts,
                  true, // PO&P is a legacy inputHidden — render blank (—), not the backend's 0
                )}
                {totalRow(
                  'totalCosts',
                  'Total Costs',
                  derived ? derived.totalCosts : data.totalCosts,
                  true,
                )}
                {/* Legacy: the Override dropdown is the last row of this table (in the Harvest column). */}
                <TableRow key="overrideHarvestTotalPop">
                  <TableCell>Override Harvest ⁄ Total PO&P $</TableCell>
                  <TableCell>
                    <Select
                      id="overrideHarvestTotalPop"
                      labelText="Override Harvest ⁄ Total PO&P $"
                      hideLabel
                      size="sm"
                      value={form['overrideHarvestTotalPop'] ?? 'N'}
                      onChange={setCheckedField('overrideHarvestTotalPop')}
                      disabled={!editable}
                    >
                      <SelectItem value="N" text="No" />
                      <SelectItem value="Y" text="Yes" />
                    </Select>
                  </TableCell>
                  <TableCell className="schedule-3__num" />
                  <TableCell className="schedule-3__num" />
                </TableRow>
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <TableContainer title="Total Overhead and Cost Per Unit Calculation">
            <Table className="schedule-3__cost-table">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
                  <TableHeader className="schedule-3__num">Harvest Volume (m³)</TableHeader>
                  <TableHeader className="schedule-3__num">Total Cost $</TableHeader>
                  <TableHeader className="schedule-3__num">Cost per Unit ($/m³)</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {/* The live mirror supplies the figures while editing (#291) but carries no
                    originals, so the served block's map is spread back on. */}
                {timberRow('Privately Owned & Purchased (PO&P) Timber', 'popTimberVolume', {
                  ...(derived ? derived.popTimber : data.popTimber),
                  originalValues: data.popTimber.originalValues,
                })}
                {timberRow('Crown Timber', 'crownTimberVolume', {
                  ...(derived ? derived.crownTimber : data.crownTimber),
                  originalValues: data.crownTimber.originalValues,
                })}
                {timberRow(
                  'Total Overhead',
                  null,
                  derived ? derived.totalOverhead : data.totalOverhead,
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          {editable ? (
            <CommentsTextArea
              id="comments"
              labelText="If you have any additional comments, please enter them here:"
              maxCount={COMMENTS_MAX}
              value={form['comments'] ?? ''}
              onChange={setField('comments')}
            />
          ) : (
            <>
              <h3 className="schedule-3__heading">Comments</h3>
              <p className="schedule-3__comments">{data.comments ?? '—'}</p>
            </>
          )}
          <OriginalValueIndicator
            originals={data.originalValues}
            field="comments"
            current={editable ? (form['comments'] ?? '') : data.comments}
            numeric={false}
            label="Comments"
          />
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

      {/* Discard-unsaved-edits confirm before leaving an editable schedule for a sub-page. */}
      {editable && (
        <ConfirmNavigationModal
          open={pendingRoute !== null}
          heading="Leave Schedule 3"
          onCancel={() => setPendingRoute(null)}
          onContinue={confirmLeave}
        >
          {CONFIRM_NAVIGATION}
        </ConfirmNavigationModal>
      )}
      {/* The save-first gate. Legacy raised a per-link alert, so the message is looked up by the
          route that was refused rather than shared between both links (defect #373). */}
      {blockedRoute !== null && (
        <Modal
          open
          passiveModal
          modalHeading="Save required"
          onRequestClose={() => setBlockedRoute(null)}
        >
          <p>{ALT_SAVE_BEFORE[blockedRoute]}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule3
