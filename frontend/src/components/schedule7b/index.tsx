import type { FC } from 'react'
import { useCallback, useState } from 'react'
import { Accordion, AccordionItem, Button, Column, Grid, Modal, Pagination } from '@carbon/react'
import { TrashCan } from '@carbon/icons-react'
import type Schedule7bResponse from '@/interfaces/Schedule7bResponse'
import type { Culvert, Schedule7bCheckStatusResponse } from '@/interfaces/Schedule7bResponse'
import type CulvertRequest from '@/interfaces/Schedule7bRequest'
import type { CulvertErrors, CulvertFormValues, MaskedField } from './validation'
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { extractDetail } from '@/utils/error'
import { groupFixedInput, numStrFixed } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import CulvertFields from './CulvertFields'
import {
  COST_FIELDS,
  MASK_DIGITS,
  emptyCulvertForm,
  parseDecimalInput,
  roundCost,
  validateCulvert,
} from './validation'
import './index.scss'

// Client-only chrome (no request behind it), verbatim from the legacy bundle where legacy had text.
// Every success and error is rendered from the API `message.text` / ProblemDetail.detail — never
// hardcoded (AD-8). The context-missing literal has no trailing space (sibling convention); the
// SERVER's ERR-003 (with its real trailing space) still renders verbatim when a request returns it.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const ADD_PANEL_HEADING = 'Add a Culvert report'
const EMPTY_LIST = 'No culvert reports have been added.'
// Client-side gate text. The per-field messages under each input are the API's verbatim wording; this
// only says WHICH rows are blocking, which legacy conveyed by listing every failure at the top of the
// page in its <p:messages>.
const SAVE_BLOCKED = 'Cannot save. Correct the required values on Culvert report Id:'

const SCHEDULE7B_PATH = '/v1/schedule7b'
const CULVERTS_PATH = `${SCHEDULE7B_PATH}/culverts`
const CHECK_STATUS_PATH = `${SCHEDULE7B_PATH}/check-status`

// Legacy paginated the culvert list five per page (`paginator="true" rows="5"`).
const PAGE_SIZE = 5

const PAGE_HEADER = <ScheduleTombstone title="Schedule 7B" subtitle="Report Culvert Costs" />

const mapLoadError = (detail: string | undefined): string => detail ?? 'Unable to load Schedule 7B.'

// Seed an editor from a stored culvert. Numbers become the strings the inputs bind to; a null value
// seeds blank so "not entered" stays distinguishable from zero.
// Every attribute is nullable in storage — only Type and No of Pieces are required at Save, and
// legacy rows predate even that, which is why Check Status flags them — and Jackson omits nulls, so
// each arrives ABSENT. Seeding blanks keeps the inputs controlled and keeps validateCulvert off
// `undefined.trim()`.
// Each numeric value is seeded THROUGH ITS LEGACY MASK (`MASK_DIGITS`) — grouped, with that field's
// fixed decimal count — because the mask, not the column's return shape, is what the legacy screen
// showed. The visible case is `length`: a stored `12.0` reads as `12` if seeded raw, where legacy
// showed `12.0`. `spanSize`/`riseSize`/`culvertPieceCount` look identical either way until a value
// reaches four digits, at which point legacy showed `1,200` and a raw seed shows `1200`.
const formFromCulvert = (culvert: Culvert): CulvertFormValues => ({
  culvertTypeCode: culvert.culvertTypeCode ?? '',
  spanSize: numStrFixed(culvert.spanSize, MASK_DIGITS.spanSize),
  riseSize: numStrFixed(culvert.riseSize, MASK_DIGITS.riseSize),
  length: numStrFixed(culvert.length, MASK_DIGITS.length),
  culvertPieceCount: numStrFixed(culvert.culvertPieceCount, MASK_DIGITS.culvertPieceCount),
  materialCost: numStrFixed(culvert.materialCost, MASK_DIGITS.materialCost),
  installCost: numStrFixed(culvert.installCost, MASK_DIGITS.installCost),
  comments: culvert.comments ?? '',
})

// Only the entered fields cross the wire; `totalCost` and `rowCounter` are server-owned. Every
// optional value sends `null` when blank rather than being omitted, so clearing a stored span (or a
// cost) actually clears it in place instead of silently keeping the old figure.
const buildBody = (form: CulvertFormValues, revisionCount?: number): CulvertRequest => {
  const costs = Object.fromEntries(
    COST_FIELDS.map((field) => [field, roundCost(parseDecimalInput(form[field]))]),
  ) as Record<CostField, number | null>

  return {
    culvertTypeCode: form.culvertTypeCode,
    // Span, rise and piece count are whole numbers on the wire; the advisory gate has already checked
    // the rounded value's range, so rounding here is what keeps the two in agreement (legacy let the
    // NUMBER column do this rounding).
    spanSize: roundCost(parseDecimalInput(form.spanSize)),
    riseSize: roundCost(parseDecimalInput(form.riseSize)),
    // Sent as typed: the server rounds to one decimal rather than rejecting extra places.
    length: parseDecimalInput(form.length),
    // Validated non-null before this runs, so the assertion only satisfies the type.
    culvertPieceCount: roundCost(parseDecimalInput(form.culvertPieceCount)) as number,
    ...costs,
    // Trimmed like every other string on the request; whitespace-only clears the stored comment.
    comments: form.comments.trim() === '' ? null : form.comments.trim(),
    ...(revisionCount === undefined ? {} : { revisionCount }),
  }
}

const Schedule7b: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<Schedule7bCheckStatusResponse | null>(null)

  const [showAddPanel, setShowAddPanel] = useState(false)
  const [addForm, setAddForm] = useState<CulvertFormValues>(emptyCulvertForm)
  const [addErrors, setAddErrors] = useState<CulvertErrors>({})

  // Every row's editor is live at once (legacy parity), so form and error state are keyed by culvert.
  // An absent entry means "untouched": the row renders straight from the served culvert. Only edited
  // rows are held here, so a freshly applied document implicitly resets every one of them.
  const [rowForms, setRowForms] = useState<Record<number, CulvertFormValues>>({})
  const [rowErrors, setRowErrors] = useState<Record<number, CulvertErrors>>({})

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  // Which rows are open. FULLY controlled — the user's own toggling writes here too (`onHeadingClick`)
  // — because Carbon only re-syncs an AccordionItem when its `open` prop CHANGES
  // (`AccordionItem.js:54-58`). Tracking only "the row Save revealed" left the prop stuck at `true`
  // after the user collapsed that row, so a second blocked Save named it in the banner without ever
  // reopening it. Keeping both sources of truth in one set makes the prop transition every time.
  const [openIds, setOpenIds] = useState<ReadonlySet<number>>(() => new Set())
  const [page, setPage] = useState(1)

  // Clear all transient state whenever a fresh document loads (mill/year change), so a context change
  // cannot strand an open panel, a stale banner, or a page number past the end of the new list.
  const resetTransient = useCallback(() => {
    setSaving(false)
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
    setShowAddPanel(false)
    setAddForm(emptyCulvertForm())
    setAddErrors({})
    setRowForms({})
    setRowErrors({})
    setConfirmDeleteId(null)
    setOpenIds(new Set())
    setPage(1)
  }, [])

  const { data, setData, errorDetail, isLoading } = useScheduleDocument<Schedule7bResponse>({
    path: SCHEDULE7B_PATH,
    millId,
    year,
    contextMissing,
    seedForm: () => ({}),
    mapLoadError,
    onReset: resetTransient,
  })

  const query = `?millId=${String(millId)}&year=${String(year)}`

  const clearBanners = () => {
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
  }

  // A write echoes the recomputed document. Only the row that was just saved is re-derived from it;
  // every other open editor keeps its unsaved edits, because all rows are live at once and a blanket
  // reset would silently discard work the server never saw. `savedId` is absent for add and delete,
  // where nothing the user typed into an existing row is at stake.
  const applyDocument = (doc: Schedule7bResponse, savedId?: number) => {
    setData(doc)
    // Deleting the last culvert of a page leaves `page` past the end of the new list. Clamp it as the
    // document arrives — every mutation response funnels through here — rather than during render,
    // where setting state is a re-entrant update React can warn about. The clamp is STORED, not merely
    // derived at the slice below: a stale page would otherwise resurrect — paginate to 2, delete back
    // to one page, add a culvert, and the list would silently jump to page 2 again.
    setPage((current) => Math.min(current, Math.max(1, Math.ceil(doc.culverts.length / PAGE_SIZE))))
    // Unsaved edits in OTHER rows survive (a delete must not discard them), but a row that no longer
    // exists must not keep hoarding them, and the just-saved row re-derives from the echo.
    const surviving = new Set(doc.culverts.map((culvert) => culvert.culvertReportId))
    setRowForms((prev) =>
      Object.fromEntries(
        Object.entries(prev).filter(([id]) => surviving.has(Number(id)) && Number(id) !== savedId),
      ),
    )
    // Every inline rejection is stale once a write has succeeded: it was computed against values the
    // server has now replaced. Without this, red `Value Required` text from an earlier blocked Save sat
    // under the fields while the banner read `Data deleted successfully`.
    setRowErrors({})
    setMessage(doc.message?.text ?? null)
    setActionError(null)
    setCheckResult(null)
  }

  const failed = (error: unknown, fallback: string) => {
    // Keep entered values for correction; surface the API's verbatim detail.
    setActionError(extractDetail(error) || fallback)
  }

  const release = () => {
    // On a context change resetTransient already cleared `saving`, and a request dispatched under the
    // NEW context may be in flight — a stale finally must not release its lock.
    if (isCurrent()) {
      setSaving(false)
    }
  }

  // Editing a field clears its own error, so a corrected value stops showing a stale rejection. The
  // rest of the errors stand until the next submit re-evaluates them.
  const clearFieldError = (errors: CulvertErrors, key: keyof CulvertFormValues): CulvertErrors => {
    if (!(key in errors)) {
      return errors
    }
    const next = { ...errors }
    delete next[key]
    return next
  }

  // Re-apply a masked field's legacy format once the user leaves it ("1200" → "1,200", "12" → "12.0"
  // for the one-decimal length). Legacy did this on every field: each input carried an
  // `f:convertNumber` (or the costConverter) plus `<f:ajax event="change">`, so leaving the field
  // re-rendered it through the mask. A no-op when the text already matches, so it cannot loop through
  // a re-render; invalid text is left alone for the user to correct.
  const maskAddField = (key: MaskedField) => {
    setAddForm((prev) => {
      const masked = groupFixedInput(prev[key], MASK_DIGITS[key])
      return masked === prev[key] ? prev : { ...prev, [key]: masked }
    })
  }

  const maskRowField = (culvert: Culvert, key: MaskedField) => {
    setRowForms((prev) => {
      const current = prev[culvert.culvertReportId] ?? formFromCulvert(culvert)
      const masked = groupFixedInput(current[key], MASK_DIGITS[key])
      if (masked === current[key]) {
        return prev
      }
      return { ...prev, [culvert.culvertReportId]: { ...current, [key]: masked } }
    })
  }

  const setAddField = (key: keyof CulvertFormValues, value: string) => {
    setAddForm((prev) => ({ ...prev, [key]: value }))
    setAddErrors((prev) => clearFieldError(prev, key))
  }

  // The first edit to an untouched row seeds its form from the served culvert, so the other eight
  // fields survive the change instead of collapsing to blanks.
  const setRowField = (culvert: Culvert, key: keyof CulvertFormValues, value: string) => {
    setRowForms((prev) => ({
      ...prev,
      [culvert.culvertReportId]: {
        ...(prev[culvert.culvertReportId] ?? formFromCulvert(culvert)),
        [key]: value,
      },
    }))
    setRowErrors((prev) => ({
      ...prev,
      [culvert.culvertReportId]: clearFieldError(prev[culvert.culvertReportId] ?? {}, key),
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
    // No rowCounter: the Add form carries no `Id: {n} - ` prefix on its cost messages, matching legacy
    // (the prefix lived on the list-row fields only).
    const errors = validateCulvert(addForm)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .post<Schedule7bResponse>(`${CULVERTS_PATH}${query}`, buildBody(addForm))
      .then((response) => {
        if (!isCurrent()) {
          return
        }
        applyDocument(response.data)
        // Inputs clear only on success (add-is-save).
        setAddForm(emptyCulvertForm())
        setShowAddPanel(false)
      })
      .catch((error: unknown) => {
        if (isCurrent()) {
          failed(error, 'Schedule could not be saved.')
        }
      })
      .finally(release)
  }

  /**
   * The page-level Save (legacy parity): persist EVERY culvert in one request, edited or not, exactly
   * as legacy's Save button did (`Schedule7bMB.save()` → `Schedule7bDAO.saveSchedule()`). It is the
   * ONLY save on the page — legacy gave a culvert row no Save of its own, and the per-row
   * `PUT /culverts/{id}` stays available on the API for callers that want one.
   *
   * Every row is validated FIRST and the request is only sent when all of them pass — the server saves
   * the batch atomically, so dispatching a body with a known-invalid row could only produce a
   * whole-batch rejection while the reporter had to guess which row caused it.
   */
  const handleSaveAll = () => {
    if (!data || saving || data.culverts.length === 0) {
      return
    }
    clearBanners()
    // Read from `data` rather than the `culverts` binding destructured further down, which is not in
    // scope here.
    const forms = data.culverts.map((culvert) => ({
      culvert,
      form: rowForms[culvert.culvertReportId] ?? formFromCulvert(culvert),
    }))

    const errorsByRow: Record<number, CulvertErrors> = {}
    const failedRows: Culvert[] = []
    for (const { culvert, form } of forms) {
      const errors = validateCulvert(form, culvert.rowCounter)
      errorsByRow[culvert.culvertReportId] = errors
      if (Object.keys(errors).length > 0) {
        failedRows.push(culvert)
      }
    }
    // Replace wholesale rather than merging: a row that now passes must lose its old red text.
    setRowErrors(errorsByRow)
    if (failedRows.length > 0) {
      // Save validates EVERY culvert, but only five are on screen and each editor is collapsed, so a
      // failing row can be invisible — on another page, or simply unopened. Without this the button
      // would appear dead: no request, no banner, no way to find the offending row. Legacy listed every
      // failure in its page-level <p:messages>, so naming them here is the faithful behaviour.
      setActionError(
        `${SAVE_BLOCKED} ${failedRows.map((culvert) => String(culvert.rowCounter)).join(', ')}`,
      )
      // Bring the first offender into view and open it, so the inline errors are actually reachable.
      const first = failedRows[0]
      setPage(Math.floor(data.culverts.indexOf(first) / PAGE_SIZE) + 1)
      setOpenIds((prev) => new Set(prev).add(first.culvertReportId))
      return
    }

    setSaving(true)
    apiService
      .getAxiosInstance()
      .put<Schedule7bResponse>(`${CULVERTS_PATH}${query}`, {
        culverts: forms.map(({ culvert, form }) => ({
          culvertReportId: culvert.culvertReportId,
          culvert: buildBody(form, culvert.revisionCount),
        })),
      })
      .then((response) => {
        if (!isCurrent()) {
          return
        }
        applyDocument(response.data)
        // Every row was just persisted, so no editor holds unsaved work — dropping the lot returns them
        // all to "untouched" and re-derives them from the echoed document.
        setRowForms({})
        setRowErrors({})
      })
      .catch((error: unknown) => {
        if (isCurrent()) {
          failed(error, 'Schedule could not be saved.')
        }
      })
      .finally(release)
  }

  const handleDelete = () => {
    if (confirmDeleteId === null || saving) {
      return
    }
    const id = confirmDeleteId
    setConfirmDeleteId(null)
    clearBanners()
    setSaving(true)
    apiService
      .getAxiosInstance()
      .delete<Schedule7bResponse>(`${CULVERTS_PATH}/${String(id)}${query}`)
      .then((response) => {
        if (isCurrent()) {
          // SUC-002 `Data deleted successfully` arrives unconditionally, including for the last
          // culvert: legacy 7B has no empty-schedule branch (that message belongs to Schedule 7A).
          applyDocument(response.data)
        }
      })
      .catch((error: unknown) => {
        if (isCurrent()) {
          failed(error, 'Unable to delete culvert report.')
        }
      })
      .finally(release)
  }

  const handleCheckStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    // In-flight lock: rapid clicks must not issue concurrent POSTs, and a slow check result must not
    // interleave with a mutation. Read-only (BR-07) — mutates nothing.
    setSaving(true)
    apiService
      .getAxiosInstance()
      .post<Schedule7bCheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`)
      .then((response) => {
        if (isCurrent()) {
          setCheckResult(response.data)
        }
      })
      .catch((error: unknown) => {
        if (isCurrent()) {
          failed(error, 'Unable to check status.')
        }
      })
      .finally(release)
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
          <LoadingScreen label="Loading Schedule 7B" />
        </Column>
      </PageState>
    )
  }

  // Covers the three context guards AND the action-key denial: ERR-003 / ERR-004 / ERR-002 and the 403
  // all arrive as a ProblemDetail, and each renders its verbatim `detail` with the work area suppressed
  // (S11-S13, S30).
  if (errorDetail) {
    return (
      <PageState
        header={PAGE_HEADER}
        notification={{ kind: 'error', title: 'Unable to load Schedule 7B', subtitle: errorDetail }}
      />
    )
  }

  if (!data) {
    return null
  }

  const { editable, culverts, codeLists } = data
  // Legacy disabled Check Status outside Draft alongside every write control, even though the endpoint
  // itself is read-only and permitted at any status (`schedule7B.xhtml:264-265,558-559`; Story 13.1
  // recorded deviation 6 leaves the endpoint open and puts the button-disable here).
  const controlsDisabled = !editable || saving

  const totalPages = Math.max(1, Math.ceil(culverts.length / PAGE_SIZE))
  // `applyDocument` already clamps `page` for every mutation response, and a mill/year change resets it
  // to 1; this min is the belt-and-braces that keeps the slice in range for any other path that
  // shortens the list.
  const currentPage = Math.min(page, totalPages)
  const visible = culverts.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  // Legacy renders Save AND Check Status as a pair, both above and below the culvert list
  // (schedule7B.xhtml:258-268, :551-561). Save persists every culvert in one call, as legacy's did. It
  // is additionally disabled with no culverts: the batch endpoint rejects an empty body, so there is no
  // action to offer.
  const actionButtons = (key: string) => (
    <Column key={key} sm={4} md={8} lg={16} className="schedule-7b__actions">
      <Button
        kind="primary"
        disabled={controlsDisabled || culverts.length === 0}
        onClick={handleSaveAll}
      >
        Save
      </Button>
      <Button kind="tertiary" disabled={controlsDisabled} onClick={handleCheckStatus}>
        Check Status
      </Button>
    </Column>
  )

  return (
    <div className="app-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
        {actionError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
        )}
        {/* NotificationColumn IS a Carbon Column, so these are direct grid children like the
            message/actionError banners above — wrapping them in another Column would strip their span
            classes of meaning and misalign the two groups. */}
        {checkResult && (
          <>
            {checkResult.errors.map((error, index) => (
              <NotificationColumn
                // Missing-value lines repeat verbatim across culverts, so the list index is what keeps
                // otherwise-identical entries distinct.
                key={`culvert-check-error-${String(index)}-${error.key}`}
                kind="error"
                title="Action required"
                subtitle={error.text}
              />
            ))}
            {/* Present only when every culvert passes. Unlike Schedule 7A there is no per-culvert
                all-met line, so a schedule with gaps renders errors alone. */}
            {checkResult.requirementsMetMessage && (
              <NotificationColumn
                kind="success"
                title="Requirements met"
                subtitle={checkResult.requirementsMetMessage.text}
              />
            )}
            {/* A result carrying no message at all would otherwise render nothing, leaving the button
                looking dead. requirementsMet is the one field always populated. */}
            {checkResult.errors.length === 0 && !checkResult.requirementsMetMessage && (
              <NotificationColumn
                kind={checkResult.requirementsMet ? 'success' : 'warning'}
                title="Status checked"
                subtitle={
                  checkResult.requirementsMet
                    ? 'All requirements for this schedule have been met'
                    : 'This schedule has outstanding requirements.'
                }
              />
            )}
          </>
        )}

        {/* Write controls stay rendered and go disabled outside Draft rather than disappearing — legacy
            bound `disabled` on every one of them and never removed a control, so a read-only reporter
            can still see which actions exist (STA-001, S14). */}
        <Column sm={4} md={8} lg={16} className="schedule-7b__actions">
          <Button
            kind="primary"
            disabled={controlsDisabled}
            // Legacy toggled the tooltip with the label: `title="Close"` / `"Add Culvert Report"`
            // (schedule7B.xhtml:56), which says what the button opens where the one-word label cannot.
            title={showAddPanel ? 'Close' : 'Add Culvert Report'}
            onClick={() => {
              clearBanners()
              // Closing discards the draft, so reopening starts clean rather than restoring half-typed
              // values and the red errors from a previous failed submit.
              setAddForm(emptyCulvertForm())
              setAddErrors({})
              setShowAddPanel((open) => !open)
            }}
          >
            {showAddPanel ? 'Close' : 'Add'}
          </Button>
        </Column>

        {showAddPanel && (
          <Column sm={4} md={8} lg={16} className="schedule-7b__section">
            <h3 className="schedule-7b__heading">{ADD_PANEL_HEADING}</h3>
            <CulvertFields
              idPrefix="add"
              form={addForm}
              errors={addErrors}
              codeLists={codeLists}
              disabled={controlsDisabled}
              onChange={setAddField}
              onMask={maskAddField}
            />
            <div className="schedule-7b__panel-actions">
              <Button kind="primary" disabled={controlsDisabled} onClick={handleAdd}>
                Add Report
              </Button>
            </div>
          </Column>
        )}

        {actionButtons('page-actions-top')}

        <Column sm={4} md={8} lg={16} className="schedule-7b__section">
          {culverts.length === 0 ? (
            <p className="schedule-7b__empty">{EMPTY_LIST}</p>
          ) : (
            <>
              <Accordion>
                {visible.map((culvert) => (
                  <AccordionItem
                    key={culvert.culvertReportId}
                    open={openIds.has(culvert.culvertReportId)}
                    onHeadingClick={({ isOpen }) => {
                      setOpenIds((prev) => {
                        const next = new Set(prev)
                        if (isOpen) {
                          next.add(culvert.culvertReportId)
                        } else {
                          next.delete(culvert.culvertReportId)
                        }
                        return next
                      })
                    }}
                    title={`Culvert report Id: ${String(culvert.rowCounter)}`}
                  >
                    <CulvertFields
                      idPrefix={`culvert-${String(culvert.culvertReportId)}`}
                      form={rowForms[culvert.culvertReportId] ?? formFromCulvert(culvert)}
                      errors={rowErrors[culvert.culvertReportId] ?? {}}
                      codeLists={codeLists}
                      disabled={controlsDisabled}
                      onChange={(key, value) => setRowField(culvert, key, value)}
                      onMask={(key) => maskRowField(culvert, key)}
                    />
                    {/* Delete is the ONLY per-row control in legacy (schedule7B.xhtml:526-540). Saving
                        is a page-level action covering every culvert at once, so a per-row Save/Cancel
                        pair would offer a granularity the schedule does not have. */}
                    <div className="schedule-7b__panel-actions">
                      {/* Legacy labelled this button `Delete` with the icon beside it
                          (schedule7B.xhtml:527-529), and the repo's own `RowActionButtons` renders a
                          row delete the same way — a labelled `danger--ghost` button. The 7A twin's
                          icon-only variant is the outlier of the two, so this follows legacy and the
                          house convention rather than its sibling page. */}
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        renderIcon={TrashCan}
                        disabled={controlsDisabled}
                        onClick={() => setConfirmDeleteId(culvert.culvertReportId)}
                      >
                        Delete
                      </Button>
                    </div>
                  </AccordionItem>
                ))}
              </Accordion>
              {culverts.length > PAGE_SIZE && (
                <Pagination
                  page={currentPage}
                  pageSize={PAGE_SIZE}
                  pageSizes={[PAGE_SIZE]}
                  totalItems={culverts.length}
                  onChange={({ page: next }) => setPage(next)}
                />
              )}
            </>
          )}
        </Column>

        {actionButtons('page-actions-bottom')}
      </Grid>

      {/* Mounted only while a delete is pending, so its Yes/No do not sit in the accessibility tree
          competing with the row actions when no dialog is open. Cancelling (S05) sends no request and
          leaves the record unchanged with no message. */}
      {confirmDeleteId !== null && (
        <Modal
          open
          danger
          // Legacy's confirm dialog answered with Yes/No (schedule7B.xhtml:534-539), not
          // Delete/Cancel.
          modalHeading="Confirmation"
          primaryButtonText="Yes"
          secondaryButtonText="No"
          onRequestClose={() => setConfirmDeleteId(null)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule7b
