import type { FC } from 'react'
import { useCallback, useRef, useState } from 'react'
import { Accordion, AccordionItem, Button, Column, Grid, Pagination } from '@carbon/react'
import { Add, Close, TrashCan } from '@carbon/icons-react'
import type Schedule7aResponse from '@/interfaces/Schedule7aResponse'
import type { Bridge, Schedule7aCheckStatusResponse } from '@/interfaces/Schedule7aResponse'
import type BridgeRequest from '@/interfaces/Schedule7aRequest'
import type { BridgeCheckEntry, Schedule7aCheckRequest } from '@/interfaces/Schedule7aRequest'
import type { BridgeErrors, BridgeFormValues, CostField } from './validation'
import apiService from '@/service/api-service'
import { useScheduleBanners } from '@/hooks/useScheduleBanners'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { clearFieldError } from '@/utils/forms'
import { groupInput, numStr, numStrGroup } from '@/utils/number'
import ConfirmDeleteModal from '@/components/core/ConfirmDeleteModal'
import SaveCheckActions from '@/components/core/SaveCheckActions'
import ScheduleBanners from '@/components/core/ScheduleBanners'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import BridgeFields from './BridgeFields'
import {
  BRIDGE_FIELD_ORDER,
  CODE_FIELDS,
  COST_FIELDS,
  bridgeFieldBannerLine,
  emptyBridgeForm,
  parseDecimalInput,
  roundCost,
  validateBridge,
} from './validation'
import { deriveBridgeTotals } from './derived'
import {
  rowBannerEntries,
  rowFieldRank,
  setBannerEntry,
  type BannerEntry,
} from '@/utils/legacyValidationBanner'
import { isUnusableStrictEntry } from '@/utils/derivedMath'
import './index.scss'

// Client-only chrome (no request behind it), verbatim from the legacy bundle. Every success and
// error is rendered from the API `message.text` / ProblemDetail.detail — never hardcoded (AD-8). The
// context-missing and confirm-delete literals are shared with the sibling pages, so they live with
// the components that render them; the SERVER's ERR-001 (with its real trailing space) still renders
// verbatim when a request returns it.
const ADD_PANEL_HEADING = 'Add a Bridge report'
const EMPTY_LIST = 'No bridge reports have been added.'
// The client-side gate lists every failing field in the top banner, row by row, in legacy's own
// wording (`bridgeBannerLines`), as legacy's <p:messages> did — replacing the row-number summary this
// page showed before #359 group B.

const SCHEDULE7A_PATH = '/v1/schedule7a'
const BRIDGES_PATH = `${SCHEDULE7A_PATH}/bridges`
const CHECK_STATUS_PATH = `${SCHEDULE7A_PATH}/check-status`

// Legacy paginated the bridge list five per page.
const PAGE_SIZE = 5

const PAGE_HEADER = <ScheduleTombstone title="Schedule 7A" subtitle="Report Bridge Costs" />

const mapLoadError = (detail: string | undefined): string => detail ?? 'Unable to load Schedule 7A.'

// Seed an editor from a stored bridge. Numbers become the strings the inputs bind to; a null cost
// seeds blank so "not entered" stays distinguishable from zero.
// Every attribute is nullable in storage — legacy rows predate the validation, which is why Check
// Status flags them — and Jackson omits nulls, so each arrives ABSENT. Seeding blanks keeps the
// inputs controlled and keeps validateBridge off `undefined.trim()`.
const formFromBridge = (bridge: Bridge): BridgeFormValues => ({
  locationName: bridge.locationName ?? '',
  builtDate: bridge.builtDate ?? '',
  constructionTypeCode: bridge.constructionTypeCode ?? '',
  superstructureTypeCode: bridge.superstructureTypeCode ?? '',
  deckTypeCode: bridge.deckTypeCode ?? '',
  abutmentTypeCode: bridge.abutmentTypeCode ?? '',
  loadRatingCode: bridge.loadRatingCode ?? '',
  lifeSpan: numStr(bridge.lifeSpan),
  abutmentHeight: numStr(bridge.abutmentHeight),
  length: numStr(bridge.length),
  width: numStr(bridge.width),
  distance: numStr(bridge.distance),
  sitePlanCost: numStrGroup(bridge.sitePlanCost),
  superstructureMaterialCost: numStrGroup(bridge.superstructureMaterialCost),
  superstructureDeliverCost: numStrGroup(bridge.superstructureDeliverCost),
  superstructureInstallCost: numStrGroup(bridge.superstructureInstallCost),
  abutmentMaterialCost: numStrGroup(bridge.abutmentMaterialCost),
  abutmentDeliverCost: numStrGroup(bridge.abutmentDeliverCost),
  abutmentInstallCost: numStrGroup(bridge.abutmentInstallCost),
  approachCost: numStrGroup(bridge.approachCost),
  afterInstallCost: numStrGroup(bridge.afterInstallCost),
  otherCost: numStrGroup(bridge.otherCost),
  comments: bridge.comments ?? '',
})

// Only the entered fields cross the wire; the four totals and rowCounter are server-owned. Validated
// non-null before this runs, so the required assertions only satisfy the types.
const buildBody = (form: BridgeFormValues, revisionCount?: number): BridgeRequest => {
  const costs = Object.fromEntries(
    COST_FIELDS.map((field) => [field, roundCost(parseDecimalInput(form[field]))]),
  ) as Record<(typeof COST_FIELDS)[number], number | null>

  return {
    locationName: form.locationName.trim(),
    builtDate: form.builtDate.trim(),
    constructionTypeCode: form.constructionTypeCode,
    superstructureTypeCode: form.superstructureTypeCode,
    deckTypeCode: form.deckTypeCode,
    abutmentTypeCode: form.abutmentTypeCode,
    loadRatingCode: form.loadRatingCode,
    lifeSpan: parseDecimalInput(form.lifeSpan) as number,
    abutmentHeight: parseDecimalInput(form.abutmentHeight) as number,
    length: parseDecimalInput(form.length) as number,
    width: parseDecimalInput(form.width) as number,
    distance: parseDecimalInput(form.distance) as number,
    ...costs,
    // Trimmed like every other string on the request; whitespace-only clears the stored comment.
    comments: form.comments.trim() === '' ? null : form.comments.trim(),
    ...(revisionCount === undefined ? {} : { revisionCount }),
  }
}

// The Check Status entry for one bridge (#359): its CURRENT form state — typed for an edited row,
// served for an untouched one — with a blank or unparseable field sent as null. Never `?? 0`: the
// server's check is a null test, so a zero would turn a missing value into a pass. Whole-number wire
// fields are rounded the way `buildBody` rounds them.
const checkEntry = (form: BridgeFormValues): BridgeCheckEntry => {
  const costs = Object.fromEntries(
    COST_FIELDS.map((field) => [field, roundCost(parseDecimalInput(form[field]))]),
  ) as Record<CostField, number | null>
  return {
    locationName: form.locationName.trim() === '' ? null : form.locationName.trim(),
    builtDate: form.builtDate.trim() === '' ? null : form.builtDate.trim(),
    lifeSpan: roundCost(parseDecimalInput(form.lifeSpan)),
    abutmentHeight: parseDecimalInput(form.abutmentHeight),
    length: parseDecimalInput(form.length),
    width: parseDecimalInput(form.width),
    distance: roundCost(parseDecimalInput(form.distance)),
    ...costs,
  }
}

const Schedule7a: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  const {
    saving,
    message,
    actionError,
    checkResult,
    setMessage,
    setCheckResult,
    clearBanners: clearHookBanners,
    resetBanners,
    run,
  } = useScheduleBanners<Schedule7aCheckStatusResponse>(isCurrent)

  // Check Status describes one exact screen snapshot (#359). Bumped synchronously whenever a checked
  // value changes — and whenever the banners are cleared for a new action — so an older response can
  // never repaint a verdict over newer values.
  const checkSnapshotVersionRef = useRef(0)

  // A checked value changed: the shown verdict (and any check in flight) describes the old screen.
  // The validation banner is NOT wiped here: on this page it accumulates per field (see
  // `commitRowField`), so only the changed field's own line is recomputed, on its change.
  const invalidateCheckResult = () => {
    checkSnapshotVersionRef.current += 1
    setCheckResult(null)
  }

  // The validation banner, one keyed line per failing field in page order (#359 group B change log).
  // Save and Check Status REPLACE it with the full list; a field's change adds or removes only its own
  // line — a deliberate deviation from legacy, which replaced the banner on every change (Iman + BA).
  const [bannerEntries, setBannerEntries] = useState<readonly BannerEntry[]>([])
  // Which row fields the user has changed since they were last committed ("change and leave"): a
  // focus-and-leave with no change validates nothing, as legacy's `f:ajax event="change"` did not fire.
  const changedFieldsRef = useRef<Set<string>>(new Set())

  const clearBanners = () => {
    checkSnapshotVersionRef.current += 1
    clearHookBanners()
    setBannerEntries([])
  }

  const [showAddPanel, setShowAddPanel] = useState(false)
  const [addForm, setAddForm] = useState<BridgeFormValues>(emptyBridgeForm)
  const [addErrors, setAddErrors] = useState<BridgeErrors>({})
  // The blur-committed cost values the totals mirror reads. Legacy refreshed `totalCostMat` /
  // `totalCostIns` / the grand total on each cost field's own `change` handler, so the totals settle
  // when focus leaves rather than per keystroke (defect #291).
  const [addCommitted, setAddCommitted] = useState<BridgeFormValues>(emptyBridgeForm)

  // Every row's editor is live at once (legacy parity), so form and error state are keyed by bridge.
  // An absent entry means "untouched": the row renders straight from the served bridge. Only edited
  // rows are held here, so a freshly applied document implicitly resets every one of them.
  const [rowForms, setRowForms] = useState<Record<number, BridgeFormValues>>({})
  const [rowErrors, setRowErrors] = useState<Record<number, BridgeErrors>>({})
  const [rowCommitted, setRowCommitted] = useState<Record<number, BridgeFormValues>>({})

  /**
   * Advance a totals baseline only from an entry the Save could carry (ruled 2026-08-21): a value the
   * page reports invalid, or one the strict wire parser rejects, holds the previous figures.
   */
  const commitBridge = (form: BridgeFormValues, apply: (next: BridgeFormValues) => void): void => {
    const errors = validateBridge(form)
    const bad = COST_FIELDS.some(
      (field) => errors[field] !== undefined || isUnusableStrictEntry(form[field]),
    )
    if (!bad) {
      apply(form)
    }
  }

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  // Set only when Save needs to reveal a failing row; Carbon re-syncs an AccordionItem when its
  // `open` prop CHANGES, so this expands that row without taking over the user's own toggling.
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [page, setPage] = useState(1)

  // Clear all transient state whenever a fresh document loads (mill/year change), so a context change
  // cannot strand an open panel, a stale banner, or a page number past the end of the new list.
  const resetTransient = useCallback(() => {
    resetBanners()
    setBannerEntries([])
    changedFieldsRef.current.clear()
    setShowAddPanel(false)
    setAddForm(emptyBridgeForm())
    setAddCommitted(emptyBridgeForm())
    setAddErrors({})
    setRowForms({})
    setRowCommitted({})
    setRowErrors({})
    setConfirmDeleteId(null)
    setExpandedId(null)
    setPage(1)
  }, [resetBanners])

  const { data, setData, loadState } = useScheduleDocument<Schedule7aResponse>({
    path: SCHEDULE7A_PATH,
    scheduleName: 'Schedule 7A',
    header: PAGE_HEADER,
    millId,
    year,
    contextMissing,
    seedForm: () => ({}),
    mapLoadError,
    onReset: resetTransient,
  })

  const query = `?millId=${String(millId)}&year=${String(year)}`

  // A write echoes the recomputed document. Only the row that was just saved is re-derived from it;
  // every other open editor keeps its unsaved edits, because all rows are live at once and a blanket
  // reset would silently discard work the server never saw. `savedId` is absent for add and delete,
  // where nothing the user typed into an existing row is at stake.
  const applyDocument = (doc: Schedule7aResponse, savedId?: number) => {
    setData(doc)
    changedFieldsRef.current.clear()
    // Deleting the last bridge of a page leaves `page` past the end of the new list. Clamp it as the
    // document arrives — every mutation response funnels through here — rather than during render,
    // where setting state is a re-entrant update React can warn about, or in an effect, which costs
    // a second render pass. The clamp is STORED, not merely derived at the slice below: a stale page
    // would otherwise resurrect — paginate to 2, delete back to one page, add a bridge, and the list
    // would silently jump to page 2 again.
    setPage((current) => Math.min(current, Math.max(1, Math.ceil(doc.bridges.length / PAGE_SIZE))))
    if (savedId !== undefined) {
      setRowForms(({ [savedId]: _saved, ...rest }) => rest)
      setRowErrors(({ [savedId]: _clearedErrors, ...rest }) => rest)
    }
    // Banners: the echoed success line replaces whatever was on screen, and any earlier failure or
    // check result is stale the moment a write lands.
    clearBanners()
    setMessage(doc.message?.text ?? null)
  }

  // Re-group a money field after the user leaves it ("12000" → "12,000"). A no-op when already
  // grouped, so it cannot loop through a re-render.
  /**
   * Re-group a money field on blur and commit it to the mirror's baseline (#291).
   *
   * Computed OUTSIDE the state updater (code review 2026-08-21): dispatching `setAddCommitted` from
   * inside `setAddForm`'s updater made the updater impure — React invokes it during render, may
   * double-invoke it under StrictMode, and may run it on a render it then discards — and it fired even
   * on the `return prev` bail-out path. The commit is gated the same way every other page's is.
   */
  const groupAddField = (key: CostField) => {
    const grouped = groupInput(addForm[key])
    const next = grouped === addForm[key] ? addForm : { ...addForm, [key]: grouped }
    setAddForm(next)
    commitBridge(next, setAddCommitted)
  }

  const groupRowField = (bridge: Bridge, key: CostField) => {
    const untouched = !(bridge.bridgeReportId in rowForms)
    const current = rowForms[bridge.bridgeReportId] ?? formFromBridge(bridge)
    const grouped = groupInput(current[key])
    const changed = grouped !== current[key]
    // A blur that changed nothing on a row the reporter never edited must NOT hand that row to the
    // mirror: legacy fired on `change`, and a tab-through is not a change. Without this a stray Tab
    // silently replaced the served totals with a client recomputation — bypassing this page's own AC7
    // test (code review 2026-08-21).
    if (untouched && !changed) {
      return
    }
    const next = changed ? { ...current, [key]: grouped } : current
    if (changed) {
      setRowForms((prev) => ({ ...prev, [bridge.bridgeReportId]: next }))
    }
    commitBridge(next, (committed) => {
      setRowCommitted((prev) => ({ ...prev, [bridge.bridgeReportId]: committed }))
    })
  }

  const setAddField = (key: keyof BridgeFormValues, value: string) => {
    setAddForm((prev) => ({ ...prev, [key]: value }))
    setAddErrors((prev) => clearFieldError(prev, key))
  }

  /**
   * Validate ONE row field on its change (#359 group B change log): legacy's `f:ajax event="change"`
   * ran that field's own validators and re-rendered the banner. The field turns red with its inline
   * text and its legacy line joins the banner — or, passing, loses both. Only this field is judged;
   * every other field waits for its own change, or for Save / Check Status. A text field commits on
   * leaving it after a change (`onCommit`); a dropdown on selection, with its new value (`form`).
   */
  const commitRowField = (bridge: Bridge, key: keyof BridgeFormValues, form?: BridgeFormValues) => {
    const id = bridge.bridgeReportId
    const changedKey = `${String(id)}:${key}`
    if (form === undefined && !changedFieldsRef.current.has(changedKey)) {
      return
    }
    changedFieldsRef.current.delete(changedKey)
    if (!data) {
      return
    }
    const message = validateBridge(form ?? rowForms[id] ?? formFromBridge(bridge))[key]
    setRowErrors((prev) => {
      const row = prev[id] ?? {}
      return {
        ...prev,
        [id]: message === undefined ? clearFieldError(row, key) : { ...row, [key]: message },
      }
    })
    const rank = rowFieldRank(data.bridges.indexOf(bridge), BRIDGE_FIELD_ORDER.indexOf(key))
    setBannerEntries((prev) =>
      setBannerEntry(
        prev,
        changedKey,
        message === undefined
          ? null
          : { key: changedKey, rank, line: bridgeFieldBannerLine(key, message, bridge.rowCounter) },
      ),
    )
  }

  // The first edit to an untouched row seeds its form from the served bridge, so the other 26 fields
  // survive the change instead of collapsing to blanks.
  const setRowField = (bridge: Bridge, key: keyof BridgeFormValues, value: string) => {
    const next = {
      ...(rowForms[bridge.bridgeReportId] ?? formFromBridge(bridge)),
      [key]: value,
    }
    changedFieldsRef.current.add(`${String(bridge.bridgeReportId)}:${key}`)
    setRowForms((prev) => ({
      ...prev,
      [bridge.bridgeReportId]: {
        ...(prev[bridge.bridgeReportId] ?? formFromBridge(bridge)),
        [key]: value,
      },
    }))
    // The red box and its inline text are NOT cleared while typing: like legacy (and Schedule 4) the
    // field is re-judged only when it is left after a change, or a select on selection
    // (`commitRowField`), and the box and its banner line update together then.
    // A check-status result names fields by value-at-the-time; once the user edits, it is stale — and
    // a check still in flight describes the old values, so its answer is dropped when it lands.
    invalidateCheckResult()
    // A dropdown's selection IS its change: validate it now, with the value just chosen.
    if ((CODE_FIELDS as readonly string[]).includes(key)) {
      commitRowField(bridge, key, next)
    }
  }

  const handleAdd = () => {
    if (!data || saving) {
      return
    }
    // Clear prior banners first so a validation failure never leaves a stale success notice.
    clearBanners()
    const errors = validateBridge(addForm)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule7aResponse>(`${BRIDGES_PATH}${query}`, buildBody(addForm)),
      {
        fallback: 'Schedule could not be saved.',
        onSuccess: (doc) => {
          applyDocument(doc)
          // Inputs clear only on success (add-is-save).
          setAddForm(emptyBridgeForm())
          setAddCommitted(emptyBridgeForm())
          setShowAddPanel(false)
        },
      },
    )
  }

  // Every bridge's CURRENT form in document order: the typed one for an edited row, the served one
  // for an untouched row.
  const currentForms = (bridges: readonly Bridge[]) =>
    bridges.map((bridge) => ({
      bridge,
      form: rowForms[bridge.bridgeReportId] ?? formFromBridge(bridge),
    }))

  /**
   * Validate EVERY bridge with Save's own validator — the gate Save and Check Status share (legacy's
   * `validateClient` blocked both). On failure the inline errors are shown, the banner lists each
   * failing field in legacy's wording row by row, and the first offending row is brought into view:
   * only five rows are on screen and each editor is collapsed, so without that the button would look
   * dead. Returns every row's current form in document order, or null when the gate blocks.
   */
  const validateAllRows = (
    bridges: readonly Bridge[],
  ): { bridge: Bridge; form: BridgeFormValues }[] | null => {
    const forms = currentForms(bridges)
    const errorsByRow: Record<number, BridgeErrors> = {}
    const failedRows: Bridge[] = []
    const validated = forms.map(({ bridge, form }) => {
      const errors = validateBridge(form)
      errorsByRow[bridge.bridgeReportId] = errors
      if (Object.keys(errors).length > 0) {
        failedRows.push(bridge)
      }
      return { row: bridge, key: bridge.bridgeReportId, errors }
    })
    // Replace wholesale rather than merging: a row that now passes must lose its old red text.
    setRowErrors(errorsByRow)
    // Everything has now been validated, so no field is waiting on its own change any more.
    changedFieldsRef.current.clear()
    if (failedRows.length === 0) {
      return forms
    }
    // Save / Check Status REPLACE the banner with the full list, row by row, field by field.
    setBannerEntries(
      rowBannerEntries(validated, BRIDGE_FIELD_ORDER, (bridge, _index, field, message) =>
        bridgeFieldBannerLine(field, message, bridge.rowCounter),
      ),
    )
    // Bring the first offender into view and open it, so the inline errors are actually reachable.
    const first = failedRows[0]
    setPage(Math.floor(bridges.indexOf(first) / PAGE_SIZE) + 1)
    setExpandedId(first.bridgeReportId)
    return null
  }

  /**
   * The page-level Save (legacy parity): persist EVERY bridge in one request, edited or not, exactly
   * as legacy's Save button did. It is the ONLY save on the page — legacy gave a bridge row no Save
   * of its own, and the per-row `PUT /bridges/{id}` stays available on the API for callers that want
   * one.
   *
   * Every row is validated FIRST and the request is only sent when all of them pass — the server
   * saves the batch atomically, so dispatching a body with a known-invalid row could only produce a
   * whole-batch rejection while the reporter had to guess which row caused it.
   */
  const handleSaveAll = () => {
    if (!data || saving || data.bridges.length === 0) {
      return
    }
    clearBanners()
    // Read from `data` rather than the `bridges` binding destructured further down, which is not in
    // scope here.
    const forms = validateAllRows(data.bridges)
    if (forms === null) {
      return
    }

    const body = {
      bridges: forms.map(({ bridge, form }) => ({
        bridgeReportId: bridge.bridgeReportId,
        bridge: buildBody(form, bridge.revisionCount),
      })),
    }
    run(apiService.getAxiosInstance().put<Schedule7aResponse>(`${BRIDGES_PATH}${query}`, body), {
      fallback: 'Schedule could not be saved.',
      onSuccess: (doc) => {
        applyDocument(doc)
        // Every row was just persisted, so no editor holds unsaved work — dropping the lot returns
        // them all to "untouched" and re-derives them from the echoed document.
        setRowForms({})
        setRowCommitted({})
        setRowErrors({})
      },
    })
  }

  const handleDelete = () => {
    if (confirmDeleteId === null || saving) {
      return
    }
    const id = confirmDeleteId
    setConfirmDeleteId(null)
    clearBanners()
    run(
      apiService
        .getAxiosInstance()
        .delete<Schedule7aResponse>(`${BRIDGES_PATH}/${String(id)}${query}`),
      { fallback: 'Unable to delete bridge report.', onSuccess: applyDocument },
    )
  }

  const handleCheckStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    // Gated on Save's validator over every row (#359), but only when the page is editable: a read-only
    // page highlights nothing, so a stored value failing a client rule must not block it silently.
    const forms = data.editable ? validateAllRows(data.bridges) : currentForms(data.bridges)
    if (forms === null) {
      return
    }
    // The body carries every bridge as it is ON SCREEN, in document order (the server numbers rows by
    // ordinal), including rows on other paginator pages. The Add draft is never sent.
    const body: Schedule7aCheckRequest = { bridges: forms.map(({ form }) => checkEntry(form)) }
    const submittedSnapshotVersion = checkSnapshotVersionRef.current
    // In-flight lock: rapid clicks must not issue concurrent POSTs, and a slow check result must not
    // interleave with a mutation. Read-only (BR-08) — mutates nothing.
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule7aCheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`, body),
      {
        fallback: 'Unable to check status.',
        onSuccess: setCheckResult,
        // A response — success OR failure — for a superseded snapshot describes values no longer on
        // screen, so it is dropped.
        stillWanted: () => checkSnapshotVersionRef.current === submittedSnapshotVersion,
      },
    )
  }

  if (loadState) return loadState

  if (!data) {
    return null
  }

  const { editable, bridges, codeLists } = data
  // Legacy disabled Check Status alongside every write control whenever the report was not
  // editable by the caller — its rule was role×status, not Draft alone, even though the
  // endpoint itself is read-only and permitted at any status.
  const controlsDisabled = !editable || saving

  const totalPages = Math.max(1, Math.ceil(bridges.length / PAGE_SIZE))
  // `applyDocument` already clamps `page` for every mutation response, and a mill/year change resets
  // it to 1; this min is the belt-and-braces that keeps the slice in range for any other path that
  // shortens the list.
  const currentPage = Math.min(page, totalPages)
  const visible = bridges.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  // Legacy renders Save AND Check Status as a pair, both above and below the bridge list
  // (schedule7A.xhtml:538-548, :1274-1284). Save persists every bridge in one call, as legacy's did.
  // It is additionally disabled with no bridges: legacy answered that click with its "nothing to
  // save" notice, and the batch endpoint rejects an empty body, so there is no action to offer.
  const actionButtons = (key: string) => (
    <SaveCheckActions
      key={key}
      className="schedule-7a__actions"
      saveDisabled={controlsDisabled || bridges.length === 0}
      checkDisabled={controlsDisabled}
      onSave={handleSaveAll}
      onCheckStatus={handleCheckStatus}
    />
  )

  return (
    <div className="app-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        <ScheduleBanners
          keyPrefix="bridge"
          message={message}
          actionError={actionError}
          validationErrors={bannerEntries.map((entry) => entry.line)}
          checkResult={checkResult}
          rowMessages={checkResult?.bridgeMessages}
        />

        {/* Write controls stay rendered and go disabled whenever the caller may not edit (the
            role×status matrix since Story 16.1, not Draft alone) rather than disappearing —
            legacy bound `disabled` on all 32 of them and never removed a control, so a read-only
            reporter can still see which actions exist. */}
        <Column sm={4} md={8} lg={16} className="schedule-7a__actions">
          <Button
            kind="primary"
            // The icon tracks the label: this one control both opens and closes the add panel.
            renderIcon={showAddPanel ? Close : Add}
            disabled={controlsDisabled}
            onClick={() => {
              clearBanners()
              // Closing discards the draft, so reopening starts clean rather than restoring
              // half-typed values and the red errors from a previous failed submit.
              setAddForm(emptyBridgeForm())
              setAddCommitted(emptyBridgeForm())
              setAddErrors({})
              setShowAddPanel((open) => !open)
            }}
          >
            {showAddPanel ? 'Close' : 'Add'}
          </Button>
        </Column>

        {showAddPanel && (
          <Column sm={4} md={8} lg={16} className="schedule-7a__section">
            <h3 className="schedule-7a__heading">{ADD_PANEL_HEADING}</h3>
            <BridgeFields
              idPrefix="add"
              form={addForm}
              errors={addErrors}
              codeLists={codeLists}
              // Previously omitted, so all four totals read blank while a new bridge was entered —
              // the same "blank, not stale" shape as Schedule 4's copy mode (#291).
              totals={deriveBridgeTotals(addCommitted)}
              disabled={controlsDisabled}
              onChange={setAddField}
              onGroup={groupAddField}
            />
            <div className="schedule-7a__panel-actions">
              <Button
                kind="primary"
                renderIcon={Add}
                disabled={controlsDisabled}
                onClick={handleAdd}
              >
                Add Report
              </Button>
            </div>
          </Column>
        )}

        {actionButtons('page-actions-top')}

        <Column sm={4} md={8} lg={16} className="schedule-7a__section">
          {bridges.length === 0 ? (
            <p className="schedule-7a__empty">{EMPTY_LIST}</p>
          ) : (
            <>
              <Accordion>
                {visible.map((bridge) => (
                  <AccordionItem
                    key={bridge.bridgeReportId}
                    open={expandedId === bridge.bridgeReportId || undefined}
                    title={`Bridge report Id: ${String(bridge.rowCounter)}`}
                  >
                    <BridgeFields
                      idPrefix={`bridge-${String(bridge.bridgeReportId)}`}
                      form={rowForms[bridge.bridgeReportId] ?? formFromBridge(bridge)}
                      errors={rowErrors[bridge.bridgeReportId] ?? {}}
                      codeLists={codeLists}
                      disabled={controlsDisabled}
                      totals={
                        bridge.bridgeReportId in rowCommitted
                          ? deriveBridgeTotals(rowCommitted[bridge.bridgeReportId])
                          : bridge
                      }
                      onChange={(key, value) => setRowField(bridge, key, value)}
                      onGroup={(key) => groupRowField(bridge, key)}
                      onCommit={(key) => commitRowField(bridge, key)}
                      originals={bridge.originalValues}
                    />
                    {/* Delete is the ONLY per-row control in legacy (schedule7A.xhtml:1237).
                        Saving is a page-level action covering every bridge at once, so a per-row
                        Save/Cancel pair would offer a granularity the schedule does not have. */}
                    <div className="schedule-7a__panel-actions">
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        disabled={controlsDisabled}
                        onClick={() => setConfirmDeleteId(bridge.bridgeReportId)}
                      >
                        Delete
                      </Button>
                    </div>
                  </AccordionItem>
                ))}
              </Accordion>
              {bridges.length > PAGE_SIZE && (
                <Pagination
                  page={currentPage}
                  pageSize={PAGE_SIZE}
                  pageSizes={[PAGE_SIZE]}
                  totalItems={bridges.length}
                  onChange={({ page: next }) => setPage(next)}
                />
              )}
            </>
          )}
        </Column>

        {actionButtons('page-actions-bottom')}
      </Grid>

      {confirmDeleteId !== null && (
        <ConfirmDeleteModal onCancel={() => setConfirmDeleteId(null)} onConfirm={handleDelete} />
      )}
    </div>
  )
}

export default Schedule7a
