import type { FC } from 'react'
import { useCallback, useState } from 'react'
import { Accordion, AccordionItem, Button, Column, Grid, Pagination } from '@carbon/react'
import { TrashCan } from '@carbon/icons-react'
import type Schedule7aResponse from '@/interfaces/Schedule7aResponse'
import type { Bridge, Schedule7aCheckStatusResponse } from '@/interfaces/Schedule7aResponse'
import type BridgeRequest from '@/interfaces/Schedule7aRequest'
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
import { renderScheduleLoadState } from '@/components/core/ScheduleLoadState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import BridgeFields from './BridgeFields'
import {
  COST_FIELDS,
  emptyBridgeForm,
  parseDecimalInput,
  roundCost,
  validateBridge,
} from './validation'
import './index.scss'

// Client-only chrome (no request behind it), verbatim from the legacy bundle. Every success and
// error is rendered from the API `message.text` / ProblemDetail.detail — never hardcoded (AD-8). The
// context-missing and confirm-delete literals are shared with the sibling pages, so they live with
// the components that render them; the SERVER's ERR-001 (with its real trailing space) still renders
// verbatim when a request returns it.
const ADD_PANEL_HEADING = 'Add a Bridge report'
const EMPTY_LIST = 'No bridge reports have been added.'
// Client-side gate text. The per-field messages under each input are the API's verbatim wording;
// this only says WHICH rows are blocking, which legacy conveyed by listing every failure at the top
// of the page.
const SAVE_BLOCKED = 'Cannot save. Correct the required values on Bridge report Id:'

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

const Schedule7a: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  const {
    saving,
    message,
    actionError,
    checkResult,
    setMessage,
    setActionError,
    setCheckResult,
    clearBanners,
    resetBanners,
    run,
  } = useScheduleBanners<Schedule7aCheckStatusResponse>(isCurrent)

  const [showAddPanel, setShowAddPanel] = useState(false)
  const [addForm, setAddForm] = useState<BridgeFormValues>(emptyBridgeForm)
  const [addErrors, setAddErrors] = useState<BridgeErrors>({})

  // Every row's editor is live at once (legacy parity), so form and error state are keyed by bridge.
  // An absent entry means "untouched": the row renders straight from the served bridge. Only edited
  // rows are held here, so a freshly applied document implicitly resets every one of them.
  const [rowForms, setRowForms] = useState<Record<number, BridgeFormValues>>({})
  const [rowErrors, setRowErrors] = useState<Record<number, BridgeErrors>>({})

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  // Set only when Save needs to reveal a failing row; Carbon re-syncs an AccordionItem when its
  // `open` prop CHANGES, so this expands that row without taking over the user's own toggling.
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [page, setPage] = useState(1)

  // Clear all transient state whenever a fresh document loads (mill/year change), so a context change
  // cannot strand an open panel, a stale banner, or a page number past the end of the new list.
  const resetTransient = useCallback(() => {
    resetBanners()
    setShowAddPanel(false)
    setAddForm(emptyBridgeForm())
    setAddErrors({})
    setRowForms({})
    setRowErrors({})
    setConfirmDeleteId(null)
    setExpandedId(null)
    setPage(1)
  }, [resetBanners])

  const { data, setData, errorDetail, isLoading } = useScheduleDocument<Schedule7aResponse>({
    path: SCHEDULE7A_PATH,
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
  const groupAddField = (key: CostField) => {
    setAddForm((prev) => {
      const grouped = groupInput(prev[key])
      return grouped === prev[key] ? prev : { ...prev, [key]: grouped }
    })
  }

  const groupRowField = (bridge: Bridge, key: CostField) => {
    setRowForms((prev) => {
      const current = prev[bridge.bridgeReportId] ?? formFromBridge(bridge)
      const grouped = groupInput(current[key])
      if (grouped === current[key]) {
        return prev
      }
      return { ...prev, [bridge.bridgeReportId]: { ...current, [key]: grouped } }
    })
  }

  const setAddField = (key: keyof BridgeFormValues, value: string) => {
    setAddForm((prev) => ({ ...prev, [key]: value }))
    setAddErrors((prev) => clearFieldError(prev, key))
  }

  // The first edit to an untouched row seeds its form from the served bridge, so the other 26 fields
  // survive the change instead of collapsing to blanks.
  const setRowField = (bridge: Bridge, key: keyof BridgeFormValues, value: string) => {
    setRowForms((prev) => ({
      ...prev,
      [bridge.bridgeReportId]: {
        ...(prev[bridge.bridgeReportId] ?? formFromBridge(bridge)),
        [key]: value,
      },
    }))
    setRowErrors((prev) => ({
      ...prev,
      [bridge.bridgeReportId]: clearFieldError(prev[bridge.bridgeReportId] ?? {}, key),
    }))
    // A check-status result names fields by value-at-the-time; once the user edits, it is stale.
    setCheckResult(null)
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
          setShowAddPanel(false)
        },
      },
    )
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
    const forms = data.bridges.map((bridge) => ({
      bridge,
      form: rowForms[bridge.bridgeReportId] ?? formFromBridge(bridge),
    }))

    const errorsByRow: Record<number, BridgeErrors> = {}
    const failedRows: Bridge[] = []
    for (const { bridge, form } of forms) {
      const errors = validateBridge(form)
      errorsByRow[bridge.bridgeReportId] = errors
      if (Object.keys(errors).length > 0) {
        failedRows.push(bridge)
      }
    }
    // Replace wholesale rather than merging: a row that now passes must lose its old red text.
    setRowErrors(errorsByRow)
    if (failedRows.length > 0) {
      // Save validates EVERY bridge, but only five are on screen and each editor is collapsed, so a
      // failing row can be invisible — on another page, or simply unopened. Without this the button
      // would appear dead: no request, no banner, no way to find the offending row. Legacy listed
      // every failure in its page-level <p:messages>, so naming them here is the faithful behaviour.
      setActionError(
        `${SAVE_BLOCKED} ${failedRows.map((bridge) => String(bridge.rowCounter)).join(', ')}`,
      )
      // Bring the first offender into view and open it, so the inline errors are actually reachable.
      const first = failedRows[0]
      setPage(Math.floor((data.bridges.indexOf(first) ?? 0) / PAGE_SIZE) + 1)
      setExpandedId(first.bridgeReportId)
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
    // In-flight lock: rapid clicks must not issue concurrent POSTs, and a slow check result must not
    // interleave with a mutation. Read-only (BR-08) — mutates nothing.
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule7aCheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`),
      { fallback: 'Unable to check status.', onSuccess: setCheckResult },
    )
  }

  const loadState = renderScheduleLoadState({
    header: PAGE_HEADER,
    scheduleName: 'Schedule 7A',
    contextMissing,
    isLoading,
    errorDetail,
  })
  if (loadState) {
    return loadState
  }

  if (!data) {
    return null
  }

  const { editable, bridges, codeLists } = data
  // Legacy disabled Check Status outside Draft alongside every write control, even though the
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
          checkResult={checkResult}
          rowMessages={checkResult?.bridgeMessages}
        />

        {/* Write controls stay rendered and go disabled outside Draft rather than disappearing —
            legacy bound `disabled` on all 32 of them and never removed a control, so a read-only
            reporter can still see which actions exist. */}
        <Column sm={4} md={8} lg={16} className="schedule-7a__actions">
          <Button
            kind="primary"
            disabled={controlsDisabled}
            onClick={() => {
              clearBanners()
              // Closing discards the draft, so reopening starts clean rather than restoring
              // half-typed values and the red errors from a previous failed submit.
              setAddForm(emptyBridgeForm())
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
              disabled={controlsDisabled}
              onChange={setAddField}
              onGroup={groupAddField}
            />
            <div className="schedule-7a__panel-actions">
              <Button kind="primary" disabled={controlsDisabled} onClick={handleAdd}>
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
                      totals={bridge}
                      onChange={(key, value) => setRowField(bridge, key, value)}
                      onGroup={(key) => groupRowField(bridge, key)}
                    />
                    {/* Delete is the ONLY per-row control in legacy (schedule7A.xhtml:1237).
                        Saving is a page-level action covering every bridge at once, so a per-row
                        Save/Cancel pair would offer a granularity the schedule does not have. */}
                    <div className="schedule-7a__panel-actions">
                      {/* Icon-only. `iconDescription` is what names the control for assistive tech
                          and drives the hover tooltip, so the action is still "Delete" to anyone
                          who cannot read the glyph. */}
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription="Delete"
                        tooltipPosition="bottom"
                        disabled={controlsDisabled}
                        onClick={() => setConfirmDeleteId(bridge.bridgeReportId)}
                      />
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
