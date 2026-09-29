import type { FC } from 'react'
import { useCallback, useRef, useState } from 'react'
import { Accordion, AccordionItem, Button, Column, Grid } from '@carbon/react'
import { Pagination } from '@carbon/react'
import { Add, CheckmarkOutline, Close, Save, TrashCan } from '@carbon/icons-react'
import type Schedule9Response from '@/interfaces/Schedule9Response'
import type {
  ContractualWorkRecord,
  Schedule9CheckStatusResponse,
} from '@/interfaces/Schedule9Response'
import type {
  ContractualWorkCheckEntry,
  Schedule9CheckRequest,
} from '@/interfaces/Schedule9Request'
import apiService from '@/service/api-service'
import { useScheduleBanners } from '@/hooks/useScheduleBanners'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { clearFieldError } from '@/utils/forms'
import { groupFixedInput, parseDecimalInput, roundCost } from '@/utils/number'
import ConfirmDeleteModal from '@/components/core/ConfirmDeleteModal'
import ScheduleBanners from '@/components/core/ScheduleBanners'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import ContractualWorkFields from './ContractualWorkFields'
import type { MaskedField, RecordErrors, RecordFormValues } from './validation'
import {
  MASK_DIGITS,
  buildBody,
  emptyRecordForm,
  formFromRecord,
  RECORD_FIELD_ORDER,
  itemDescriptionEnabled,
  recordFieldBannerLine,
  sideSlopeEnabled,
  sourceDescriptionEnabled,
  unitDescriptionEnabled,
  validateRecord,
} from './validation'
import {
  rowBannerEntries,
  rowFieldRank,
  setBannerEntry,
  type BannerEntry,
} from '@/utils/legacyValidationBanner'
import './index.scss'

// Client-only chrome; every success/error renders from the API (AD-8), never hardcoded.
const ADD_PANEL_HEADING = 'Add a contractual work record'
const EMPTY_LIST = 'No contractual work records have been added.'

const SCHEDULE9_PATH = '/v1/schedule9'
const RECORDS_PATH = `${SCHEDULE9_PATH}/records`
const CHECK_STATUS_PATH = `${SCHEDULE9_PATH}/check-status`

// Modern list page size, matching the sibling Schedule 7B page (legacy Schedule 9 showed two per
// page — a recorded deviation for a consistent modern UX; the ordering is the load-bearing part).
const PAGE_SIZE = 5

const PAGE_HEADER = (
  <ScheduleTombstone title="Schedule 9" subtitle="Miscellaneous and Unique Logging Costs" />
)

const mapLoadError = (detail: string | undefined): string => detail ?? 'Unable to load Schedule 9.'

// Clearing a driver select clears the dependents it no longer enables, so a stale "Other" description
// or side slope never lingers in a disabled field (and buildBody would null it anyway). Mirrors the
// legacy on-change setters that nulled the dependent, and the backend's conditional-null.
const withConditionalClears = (
  form: RecordFormValues,
  key: keyof RecordFormValues,
  value: string,
): RecordFormValues => {
  const next: RecordFormValues = { ...form, [key]: value }
  if (key === 'contractualItemCode') {
    if (!itemDescriptionEnabled(value)) {
      next.itemDescription = ''
    }
    if (!sideSlopeEnabled(value)) {
      next.sideSlopePct = ''
    }
  }
  if (key === 'unitCode' && !unitDescriptionEnabled(value)) {
    next.unitDescription = ''
  }
  if (key === 'sourceCode' && !sourceDescriptionEnabled(value)) {
    next.sourceDescription = ''
  }
  return next
}

// The Check Status entry for one record (#359): its CURRENT form state — typed for an edited row,
// served for an untouched one — with a blank or unparseable field sent as null. Never `?? 0`: the
// server's check is a null test, so a zero would turn a missing value into a pass. Side slope is sent
// as it stands on screen; whether the item makes it matter is the server's rule.
const checkEntry = (form: RecordFormValues): ContractualWorkCheckEntry => {
  const codeOrNull = (raw: string): string | null => (raw.trim() === '' ? null : raw)
  return {
    contractorId: form.contractorId.trim() === '' ? null : form.contractorId.trim(),
    contractualItemCode:
      form.contractualItemCode.trim() === '' ? null : Number(form.contractualItemCode),
    sideSlopePct: roundCost(parseDecimalInput(form.sideSlopePct)),
    numberOfUnits: parseDecimalInput(form.numberOfUnits),
    unitCode: codeOrNull(form.unitCode),
    biogeoclimaticZone: codeOrNull(form.biogeoclimaticZone),
    cost: roundCost(parseDecimalInput(form.cost)),
    sourceCode: codeOrNull(form.sourceCode),
  }
}

const Schedule9: FC = () => {
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
  } = useScheduleBanners<Schedule9CheckStatusResponse>(isCurrent)

  // Check Status describes one exact screen snapshot (#359). Bumped synchronously whenever a checked
  // value changes — and whenever the banners are cleared for a new action — so an older response can
  // never repaint a verdict over newer values.
  const checkSnapshotVersionRef = useRef(0)

  // A checked value changed: the shown verdict (and any check in flight) describes the old screen.
  // The validation banner is NOT wiped here: on this page it accumulates per field (see
  // `commitRowFields`), so only the changed field's own line is recomputed, on its change.
  const invalidateCheckResult = () => {
    checkSnapshotVersionRef.current += 1
    setCheckResult(null)
  }

  // The validation banner, one keyed line per failing field in page order (#359 group B change log).
  // Save and Check Status REPLACE it with the full list; a field's change adds or removes only its own
  // line — a deliberate deviation from legacy, which replaced the banner on every change (BA ruling).
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
  const [addForm, setAddForm] = useState<RecordFormValues>(emptyRecordForm)
  const [addErrors, setAddErrors] = useState<RecordErrors>({})

  // Every row's editor is live at once; form/error state keyed by record id. Absent = "untouched".
  const [rowForms, setRowForms] = useState<Record<number, RecordFormValues>>({})
  const [rowErrors, setRowErrors] = useState<Record<number, RecordErrors>>({})

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  const [openIds, setOpenIds] = useState<ReadonlySet<number>>(() => new Set())
  const [page, setPage] = useState(1)

  const resetTransient = useCallback(() => {
    resetBanners()
    setBannerEntries([])
    changedFieldsRef.current.clear()
    setShowAddPanel(false)
    setAddForm(emptyRecordForm())
    setAddErrors({})
    setRowForms({})
    setRowErrors({})
    setConfirmDeleteId(null)
    setOpenIds(new Set())
    setPage(1)
  }, [resetBanners])

  const { data, setData, loadState } = useScheduleDocument<Schedule9Response>({
    path: SCHEDULE9_PATH,
    scheduleName: 'Schedule 9',
    header: PAGE_HEADER,
    millId,
    year,
    contextMissing,
    seedForm: () => ({}),
    mapLoadError,
    onReset: resetTransient,
  })

  const query = `?millId=${String(millId)}&year=${String(year)}`

  // A write echoes the recomputed document. The just-saved row re-derives from it; other open editors
  // keep unsaved edits (all rows are live at once). `savedId` is absent for add and delete.
  const applyDocument = (doc: Schedule9Response, savedId?: number) => {
    setData((prev) => (prev ? { ...doc, codeLists: doc.codeLists ?? prev.codeLists } : doc))
    changedFieldsRef.current.clear()
    setPage((current) => Math.min(current, Math.max(1, Math.ceil(doc.records.length / PAGE_SIZE))))
    const surviving = new Set(doc.records.map((record) => record.id))
    setRowForms((prev) =>
      Object.fromEntries(
        Object.entries(prev).filter(([id]) => surviving.has(Number(id)) && Number(id) !== savedId),
      ),
    )
    setRowErrors({})
    clearBanners()
    setMessage(doc.message?.text ?? null)
  }

  const maskAddField = (key: MaskedField) => {
    setAddForm((prev) => {
      const masked = groupFixedInput(prev[key], MASK_DIGITS[key])
      return masked === prev[key] ? prev : { ...prev, [key]: masked }
    })
  }

  const maskRowField = (record: ContractualWorkRecord, key: MaskedField) => {
    setRowForms((prev) => {
      const current = prev[record.id] ?? formFromRecord(record)
      const masked = groupFixedInput(current[key], MASK_DIGITS[key])
      if (masked === current[key]) {
        return prev
      }
      return { ...prev, [record.id]: { ...current, [key]: masked } }
    })
  }

  const setAddField = (key: keyof RecordFormValues, value: string) => {
    setAddForm((prev) => withConditionalClears(prev, key, value))
    setAddErrors((prev) => clearFieldError(prev, key))
  }

  /**
   * Validate row fields on their change (#359 group B change log): legacy's `f:ajax event="change"`
   * ran the changed field's own validators and re-rendered the banner. Each field turns red with its
   * inline text and its legacy line joins the banner — or, passing, loses both. Only the given fields
   * are judged; every other field waits for its own change, or for Save / Check Status. A text field
   * commits on leaving it after a change; a select on selection, with its new value (`form`), and
   * together with any dependent it just cleared (a disabled, emptied field must not keep a line).
   */
  const commitRowFields = (
    record: ContractualWorkRecord,
    keys: readonly (keyof RecordFormValues)[],
    form?: RecordFormValues,
  ) => {
    const changed = keys.filter(
      (key) => form !== undefined || changedFieldsRef.current.has(`${String(record.id)}:${key}`),
    )
    if (changed.length === 0 || !data) {
      return
    }
    const errors = validateRecord(form ?? rowForms[record.id] ?? formFromRecord(record))
    const rowIndex = data.records.indexOf(record)
    for (const key of changed) {
      const changedKey = `${String(record.id)}:${key}`
      changedFieldsRef.current.delete(changedKey)
      const message = errors[key]
      setRowErrors((prev) => {
        const row = prev[record.id] ?? {}
        return {
          ...prev,
          [record.id]:
            message === undefined ? clearFieldError(row, key) : { ...row, [key]: message },
        }
      })
      const rank = rowFieldRank(rowIndex, RECORD_FIELD_ORDER.indexOf(key))
      setBannerEntries((prev) =>
        setBannerEntry(
          prev,
          changedKey,
          message === undefined
            ? null
            : { key: changedKey, rank, line: recordFieldBannerLine(key, message, rowIndex + 1) },
        ),
      )
    }
  }

  const SELECT_FIELDS: ReadonlySet<keyof RecordFormValues> = new Set<keyof RecordFormValues>([
    'contractualItemCode',
    'unitCode',
    'biogeoclimaticZone',
    'sourceCode',
  ])

  const setRowField = (
    record: ContractualWorkRecord,
    key: keyof RecordFormValues,
    value: string,
  ) => {
    const current = rowForms[record.id] ?? formFromRecord(record)
    const next = withConditionalClears(current, key, value)
    changedFieldsRef.current.add(`${String(record.id)}:${key}`)
    setRowForms((prev) => ({
      ...prev,
      [record.id]: withConditionalClears(prev[record.id] ?? formFromRecord(record), key, value),
    }))
    // The red box and its inline text are NOT cleared while typing: like legacy (and Schedule 4) the
    // field is re-judged only when it is left after a change, or a select on selection
    // (`commitRowFields`), and the box and its banner line update together then.
    // A check-status result names fields by value-at-the-time; once the user edits, it is stale — and
    // a check still in flight describes the old values, so its answer is dropped when it lands.
    invalidateCheckResult()
    // A select's selection IS its change: validate it now, with the value just chosen, plus any
    // dependent the selection cleared.
    if (SELECT_FIELDS.has(key)) {
      const cleared = RECORD_FIELD_ORDER.filter(
        (field) => field !== key && current[field] !== next[field],
      )
      commitRowFields(record, [key, ...cleared], next)
    }
  }

  const handleAdd = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    const errors = validateRecord(addForm)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule9Response>(`${RECORDS_PATH}${query}`, buildBody(addForm)),
      {
        fallback: 'Schedule could not be saved.',
        onSuccess: (doc) => {
          applyDocument(doc)
          setAddForm(emptyRecordForm())
          setShowAddPanel(false)
        },
      },
    )
  }

  // Per-record Save (Schedule 9 is per-record — the backend has no batch endpoint). Each row PUTs
  // itself with its own optimistic-lock token; only that row re-derives from the echo.
  const handleSaveRow = (record: ContractualWorkRecord) => {
    if (!data || saving) {
      return
    }
    clearBanners()
    const form = rowForms[record.id] ?? formFromRecord(record)
    const errors = validateRecord(form)
    setRowErrors((prev) => ({ ...prev, [record.id]: errors }))
    if (Object.keys(errors).length > 0) {
      // The top banner names each failing field of THIS row in legacy's wording (#359 group B),
      // replacing whatever it held.
      const rowIndex = data.records.indexOf(record)
      setBannerEntries(
        rowBannerEntries(
          [{ row: record, key: record.id, errors }],
          RECORD_FIELD_ORDER,
          (_record, _index, field, message) => recordFieldBannerLine(field, message, rowIndex + 1),
        ).map((entry) => ({
          ...entry,
          rank: entry.rank + rowFieldRank(rowIndex, 0),
        })),
      )
      for (const field of RECORD_FIELD_ORDER) {
        changedFieldsRef.current.delete(`${String(record.id)}:${field}`)
      }
      return
    }
    run(
      apiService
        .getAxiosInstance()
        .put<Schedule9Response>(
          `${RECORDS_PATH}/${String(record.id)}${query}`,
          buildBody(form, record.revisionCount),
        ),
      {
        fallback: 'Schedule could not be saved.',
        onSuccess: (doc) => applyDocument(doc, record.id),
      },
    )
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
        .delete<Schedule9Response>(`${RECORDS_PATH}/${String(id)}${query}`),
      { fallback: 'Unable to delete record.', onSuccess: applyDocument },
    )
  }

  const handleCheckStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    // Every record's CURRENT form in document order: typed for an edited row, served otherwise.
    const forms = data.records.map((record) => ({
      record,
      form: rowForms[record.id] ?? formFromRecord(record),
    }))
    // Schedule 9 has no page-level Save, so the gate is Save's per-row validator over EVERY record
    // (#359) — only when the page is editable: a read-only page highlights nothing, so a stored value
    // failing a client rule must not block the check silently.
    if (data.editable) {
      const errorsByRow: Record<number, RecordErrors> = {}
      const validated: { row: ContractualWorkRecord; key: number; errors: RecordErrors }[] = []
      let firstFailed = -1
      for (const [index, { record, form }] of forms.entries()) {
        const errors = validateRecord(form)
        errorsByRow[record.id] = errors
        validated.push({ row: record, key: record.id, errors })
        if (Object.keys(errors).length > 0 && firstFailed === -1) {
          firstFailed = index
        }
      }
      // Replace wholesale rather than merging: a row that now passes must lose its old red text.
      setRowErrors(errorsByRow)
      // Everything has now been validated, so no field is waiting on its own change any more.
      changedFieldsRef.current.clear()
      if (firstFailed !== -1) {
        // Check Status REPLACES the banner with the full list, row by row, field by field.
        setBannerEntries(
          rowBannerEntries(validated, RECORD_FIELD_ORDER, (_record, index, field, message) =>
            recordFieldBannerLine(field, message, index + 1),
          ),
        )
        // Bring the first offender into view and open it, so its inline errors are reachable.
        setPage(Math.floor(firstFailed / PAGE_SIZE) + 1)
        setOpenIds((prev) => new Set(prev).add(forms[firstFailed].record.id))
        return
      }
    }
    // The body carries every record as it is ON SCREEN, in document order (the server numbers rows by
    // ordinal), including rows on other paginator pages. The Add draft is never sent.
    const body: Schedule9CheckRequest = { records: forms.map(({ form }) => checkEntry(form)) }
    const submittedSnapshotVersion = checkSnapshotVersionRef.current
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule9CheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`, body),
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

  const {
    editable,
    records,
    // Defensive default: the backend always serves codeLists (Story 9.3), but a partial/older payload
    // omitting it must not crash the page — the dropdowns just render empty.
    codeLists = { contractualItems: [], unitTypes: [], biogeoclimaticZones: [], sources: [] },
  } = data
  const controlsDisabled = !editable || saving

  const totalPages = Math.max(1, Math.ceil(records.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const visible = records.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  // Schedule 9 has no page-level Save (per-record writes); Check Status is the only page-level action,
  // rendered above and below the list. Check Status is read-only and permitted at any status, but the
  // button follows legacy in disabling alongside the write controls whenever the caller may not edit
  // (the role×status matrix since Story 16.1, not Draft alone). Legacy disabled it on 26 of 26
  // buttons across 15 pages on exactly that condition.
  const checkStatusButton = (key: string) => (
    <Column key={key} sm={4} md={8} lg={16} className="schedule-9__actions">
      <Button
        kind="tertiary"
        renderIcon={CheckmarkOutline}
        disabled={controlsDisabled}
        onClick={handleCheckStatus}
      >
        Check Status
      </Button>
    </Column>
  )

  return (
    <div className="app-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        <ScheduleBanners
          keyPrefix="record"
          message={message}
          actionError={actionError}
          validationErrors={bannerEntries.map((entry) => entry.line)}
          checkResult={checkResult}
        />

        {/* Write controls stay rendered and go disabled whenever the caller may not edit (the
            role×status matrix since Story 16.1, not Draft alone) rather than disappearing —
            a read-only reporter can still see which actions exist (S30). */}
        <Column sm={4} md={8} lg={16} className="schedule-9__actions">
          <Button
            kind="primary"
            // The icon tracks the label: this one control both opens and closes the add panel.
            renderIcon={showAddPanel ? Close : Add}
            disabled={controlsDisabled}
            title={showAddPanel ? 'Close' : 'Add Contractual Work Record'}
            onClick={() => {
              clearBanners()
              setAddForm(emptyRecordForm())
              setAddErrors({})
              setShowAddPanel((open) => !open)
            }}
          >
            {showAddPanel ? 'Close' : 'Add'}
          </Button>
        </Column>

        {showAddPanel && (
          <Column sm={4} md={8} lg={16} className="schedule-9__section">
            <h3 className="schedule-9__heading">{ADD_PANEL_HEADING}</h3>
            <ContractualWorkFields
              idPrefix="add"
              form={addForm}
              errors={addErrors}
              codeLists={codeLists}
              disabled={controlsDisabled}
              onChange={setAddField}
              onMask={maskAddField}
            />
            <div className="schedule-9__panel-actions">
              <Button
                kind="primary"
                renderIcon={Add}
                disabled={controlsDisabled}
                onClick={handleAdd}
              >
                Add Record
              </Button>
            </div>
          </Column>
        )}

        {checkStatusButton('page-actions-top')}

        <Column sm={4} md={8} lg={16} className="schedule-9__section">
          {records.length === 0 ? (
            <p className="schedule-9__empty">{EMPTY_LIST}</p>
          ) : (
            <>
              <Accordion>
                {visible.map((record) => (
                  <AccordionItem
                    key={record.id}
                    open={openIds.has(record.id)}
                    onHeadingClick={({ isOpen }) => {
                      setOpenIds((prev) => {
                        const next = new Set(prev)
                        if (isOpen) {
                          next.add(record.id)
                        } else {
                          next.delete(record.id)
                        }
                        return next
                      })
                    }}
                    title={`Contractual Work Report Id: ${String(record.id)}`}
                  >
                    <ContractualWorkFields
                      idPrefix={`record-${String(record.id)}`}
                      form={rowForms[record.id] ?? formFromRecord(record)}
                      errors={rowErrors[record.id] ?? {}}
                      codeLists={codeLists}
                      disabled={controlsDisabled}
                      servedCostPerUnit={record.id in rowForms ? undefined : record.costPerUnit}
                      onChange={(key, value) => setRowField(record, key, value)}
                      onMask={(key) => maskRowField(record, key)}
                      onCommit={(key) => commitRowFields(record, [key])}
                      originals={record.originalValues}
                    />
                    <div className="schedule-9__panel-actions">
                      <Button
                        kind="primary"
                        size="sm"
                        disabled={controlsDisabled}
                        renderIcon={Save}
                        onClick={() => handleSaveRow(record)}
                      >
                        Save
                      </Button>
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        disabled={controlsDisabled}
                        onClick={() => setConfirmDeleteId(record.id)}
                      >
                        Delete
                      </Button>
                    </div>
                  </AccordionItem>
                ))}
              </Accordion>
              {records.length > PAGE_SIZE && (
                <Pagination
                  page={currentPage}
                  pageSize={PAGE_SIZE}
                  pageSizes={[PAGE_SIZE]}
                  totalItems={records.length}
                  onChange={({ page: next }) => setPage(next)}
                />
              )}
            </>
          )}
        </Column>

        {checkStatusButton('page-actions-bottom')}
      </Grid>

      {confirmDeleteId !== null && (
        <ConfirmDeleteModal onCancel={() => setConfirmDeleteId(null)} onConfirm={handleDelete} />
      )}
    </div>
  )
}

export default Schedule9
