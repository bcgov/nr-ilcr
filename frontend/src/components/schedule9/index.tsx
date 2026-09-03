import type { FC } from 'react'
import { useCallback, useState } from 'react'
import { Accordion, AccordionItem, Button, Column, Grid } from '@carbon/react'
import { Pagination } from '@carbon/react'
import { Add, CheckmarkOutline, Close, Save, TrashCan } from '@carbon/icons-react'
import type Schedule9Response from '@/interfaces/Schedule9Response'
import type {
  ContractualWorkRecord,
  Schedule9CheckStatusResponse,
} from '@/interfaces/Schedule9Response'
import apiService from '@/service/api-service'
import { useScheduleBanners } from '@/hooks/useScheduleBanners'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { clearFieldError } from '@/utils/forms'
import { groupFixedInput } from '@/utils/number'
import ConfirmDeleteModal from '@/components/core/ConfirmDeleteModal'
import ScheduleBanners from '@/components/core/ScheduleBanners'
import { renderScheduleLoadState } from '@/components/core/ScheduleLoadState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import ContractualWorkFields from './ContractualWorkFields'
import type { MaskedField, RecordErrors, RecordFormValues } from './validation'
import {
  MASK_DIGITS,
  buildBody,
  emptyRecordForm,
  formFromRecord,
  itemDescriptionEnabled,
  sideSlopeEnabled,
  sourceDescriptionEnabled,
  unitDescriptionEnabled,
  validateRecord,
} from './validation'
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

const Schedule9: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  const {
    saving,
    message,
    actionError,
    checkResult,
    setMessage,
    setCheckResult,
    clearBanners,
    resetBanners,
    run,
  } = useScheduleBanners<Schedule9CheckStatusResponse>(isCurrent)

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
    setShowAddPanel(false)
    setAddForm(emptyRecordForm())
    setAddErrors({})
    setRowForms({})
    setRowErrors({})
    setConfirmDeleteId(null)
    setOpenIds(new Set())
    setPage(1)
  }, [resetBanners])

  const { data, setData, errorDetail, isLoading } = useScheduleDocument<Schedule9Response>({
    path: SCHEDULE9_PATH,
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

  const setRowField = (
    record: ContractualWorkRecord,
    key: keyof RecordFormValues,
    value: string,
  ) => {
    setRowForms((prev) => ({
      ...prev,
      [record.id]: withConditionalClears(prev[record.id] ?? formFromRecord(record), key, value),
    }))
    setRowErrors((prev) => ({
      ...prev,
      [record.id]: clearFieldError(prev[record.id] ?? {}, key),
    }))
    // A check-status result names fields by value-at-the-time; once the user edits, it is stale.
    setCheckResult(null)
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
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule9CheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`),
      { fallback: 'Unable to check status.', onSuccess: setCheckResult },
    )
  }

  const loadState = renderScheduleLoadState({
    header: PAGE_HEADER,
    scheduleName: 'Schedule 9',
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
  // button follows legacy in disabling alongside the write controls outside Draft.
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
          checkResult={checkResult}
        />

        {/* Write controls stay rendered and go disabled outside Draft rather than disappearing —
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
