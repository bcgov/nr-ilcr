import type { FC } from 'react'
import type Schedule6Response from '@/interfaces/Schedule6Response'
import type { RoadRecord, Schedule6CheckStatusResponse } from '@/interfaces/Schedule6Response'
import type { Schedule6CodeLists } from '@/interfaces/Schedule6Response'
import type { GeneralCommentsRequest, RoadRecordRequest } from '@/interfaces/Schedule6Request'
import type { RoadRecordErrors, RoadRecordFormValues } from './validation'
import { useCallback, useState } from 'react'
import {
  Accordion,
  AccordionItem,
  Button,
  Column,
  Grid,
  InlineNotification,
  Modal,
  TextArea,
  TextInput,
} from '@carbon/react'
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import CodeComboBox from '@/components/core/CodeComboBox'
import { describe, supplyBlocksFor } from '@/utils/codes'
import { extractDetail } from '@/utils/error'
import { numStr } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import PageTitle from '@/components/core/PageTitle'
import {
  GENERAL_COMMENTS_MAX_LENGTH,
  RECORD_COMMENTS_MAX_LENGTH,
  TFL_AREA_TYPE,
  TFL_MAX_LENGTH,
  areaTypeOptions,
  parseDecimalInput,
  roundCost,
  validateGeneralComments,
  validateRoadRecord,
} from './validation'
import './index.scss'

// Client-only chrome (no request behind it), verbatim from the legacy bundle. Every success/error is
// rendered from the API `message.text` / ProblemDetail.detail — never hardcoded (AD-8). The
// context-missing literal has no trailing space (sibling convention); the SERVER's ERR-001 (with its
// real trailing space) still renders verbatim when a request returns it.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
// Legacy's empty substitute list (schedule6.xhtml:459-464) sets no emptyMessage, so PrimeFaces
// rendered its default "No records found." — reproduced verbatim rather than inventing a literal.
const EMPTY_LIST = 'No records found.'
const ADD_PANEL_HEADING = 'Add Road Maintenance report'
// Verbatim legacy delete-confirmation copy (messages.properties:31, schedule6.xhtml:433-450). The
// dialog wording is client-owned chrome with no request behind it, so it is pinned here; every
// success/error still renders from the API (AD-8).
const CONFIRM_DELETE_HEADING = 'Confirmation'
const CONFIRM_DELETE_BODY = 'This will delete the current record. Do you want to continue?'
const DELETE_BUTTON_LABEL = 'Delete'
const DELETE_BUTTON_TITLE = 'Delete Road Maintenance Report'
const SCHEDULE6_PATH = '/v1/schedule6'
const RECORDS_PATH = `${SCHEDULE6_PATH}/records`
const GENERAL_COMMENTS_PATH = `${SCHEDULE6_PATH}/general-comments`
const CHECK_STATUS_PATH = `${SCHEDULE6_PATH}/check-status`

// Legacy display masks, transcribed from the JSF converters (AD-5 no recompute — every value here is
// server-computed and this only formats it). Those converters return "" for a null/non-BigDecimal
// value, so NULL RENDERS BLANK, never "0"/"0.00": in the S18 lone-comment state totalCostPerVolume is
// null (0/0 is undefined) while totalVolume/totalCost are real zeros that must still show.
const mask = (value: number | null | undefined, minFrac: number, maxFrac: number): string =>
  value === null || value === undefined
    ? ''
    : value.toLocaleString('en-US', {
        minimumFractionDigits: minFrac,
        maximumFractionDigits: maxFrac,
      })
const volumeMask = (value: number | null | undefined): string => mask(value, 0, 0) // #,###,###
const moneyMask = (value: number | null | undefined): string => mask(value, 0, 0) // ##,###,###
const ratioMask = (value: number | null | undefined): string => mask(value, 2, 2) // ###,##0.00

const emptyForm = (): RoadRecordFormValues => ({
  areaType: '',
  tflNumber: '',
  supplyBlock: '',
  volume: '',
  cost: '',
  comments: '',
})

const seedForm = (row: RoadRecord): RoadRecordFormValues => ({
  areaType: row.areaType ?? '',
  tflNumber: row.tflNumber ?? '',
  supplyBlock: row.supplyBlock ?? '',
  volume: numStr(row.volume),
  cost: numStr(row.cost),
  comments: row.comments ?? '',
})

const isTfl = (areaType: string): boolean => areaType.trim() === TFL_AREA_TYPE

// BR-02 counterpart clear. Clearing must be a STATE change, not just `disabled`: a disabled-but-
// populated input still serializes, and the server clearing it anyway is exactly what would make the
// bug invisible in an integration test but visible in the request body.
const applyAreaType = (form: RoadRecordFormValues, areaType: string): RoadRecordFormValues =>
  isTfl(areaType) ? { ...form, areaType, supplyBlock: '' } : { ...form, areaType, tflNumber: '' }

const buildBody = (form: RoadRecordFormValues, revisionCount?: number): RoadRecordRequest => {
  const tfl = isTfl(form.areaType)
  const blank = (raw: string): string | null => (raw.trim() === '' ? null : raw.trim())
  return {
    areaType: form.areaType.trim(),
    // Only the active side of BR-02 travels; the counterpart is explicitly nulled.
    tflNumber: tfl ? blank(form.tflNumber) : null,
    supplyBlock: tfl ? null : blank(form.supplyBlock),
    // Parsed with the legacy-DecimalFormat parser, not Number(): a grouped "1,000" that the advisory
    // gate accepts must serialize to 1000, not the null Number('1,000') would yield.
    volume: parseDecimalInput(form.volume),
    cost: roundCost(parseDecimalInput(form.cost)),
    comments: form.comments.trim() === '' ? null : form.comments,
    ...(revisionCount === undefined ? {} : { revisionCount }),
  }
}

const PAGE_HEADER = (
  <Grid fullWidth className="app-page__header">
    <PageTitle title="Schedule 6" subtitle="Road Management" />
  </Grid>
)

// Any ProblemDetail detail (ERR-001/002/003) renders verbatim (AC7); a network error with no detail
// falls back to a generic client-owned message.
const mapLoadError = (detail: string | undefined): string => detail ?? 'Unable to load Schedule 6.'

// One read-only label/value pair in the record display grid and the totals strip.
type FieldValueProps = {
  readonly label: string
  readonly value: string
  readonly numeric?: boolean
}

const FieldValue: FC<FieldValueProps> = ({ label, value, numeric }) => (
  <div className="schedule-6__field">
    <dt>{label}</dt>
    <dd className={numeric ? 'schedule-6__num' : undefined}>{value}</dd>
  </div>
)

// The six entered fields plus Comments, shared by the Add panel and the row editor so the BR-02
// toggle behaviour and the column caps cannot drift between the two. `rmg`/`costPerVolume` are
// server-derived (AD-5) and passed in as pre-formatted read-only text — blank in the Add panel, where
// no server answer exists yet (deviation D).
//
// Labels are the bare legacy field names in both modes. Legacy rows were always directly editable
// under those names (schedule6.xhtml:248-431) and had no edit mode to qualify; `idPrefix` keeps each
// instance's id/htmlFor pairing unique, so the repeated text costs nothing.
type RoadRecordFieldsProps = {
  readonly idPrefix: string
  readonly form: RoadRecordFormValues
  readonly errors: RoadRecordErrors
  readonly codeLists: Schedule6CodeLists
  readonly disabled: boolean
  readonly rmg: string
  readonly costPerVolume: string
  readonly onAreaTypeChange: (value: string) => void
  readonly onFieldChange: (key: keyof RoadRecordFormValues, value: string) => void
}

const RoadRecordFields: FC<RoadRecordFieldsProps> = ({
  idPrefix,
  form,
  errors,
  codeLists,
  disabled,
  rmg,
  costPerVolume,
  onAreaTypeChange,
  onFieldChange,
}) => {
  const tfl = isTfl(form.areaType)
  return (
    <div className="schedule-6__fields">
      {/* Corrections 2/3: legacy rendered both as a selectOneMenu over the code's DESCRIPTION
          (schedule6.xhtml:265-323); retires deviation (A) now that the document serves the two code
          lists (Schedule6CodeLists). TFL is a synthetic sentinel the CONTROL adds, not a served code
          (LookUpCacheDAO.java:229-230) — see areaTypeOptions. */}
      <CodeComboBox
        id={`${idPrefix}-area-type`}
        titleText="TSA or TFL"
        items={areaTypeOptions(codeLists.tsaNumbers)}
        selectedCode={form.areaType}
        disabled={disabled}
        invalid={Boolean(errors.areaType)}
        invalidText={errors.areaType}
        onSelect={onAreaTypeChange}
      />
      <TextInput
        id={`${idPrefix}-tfl-number`}
        labelText="TFL"
        size="sm"
        maxLength={TFL_MAX_LENGTH}
        disabled={disabled || !tfl}
        value={form.tflNumber}
        onChange={(e) => onFieldChange('tflNumber', e.target.value)}
        invalid={Boolean(errors.tflNumber)}
        invalidText={errors.tflNumber}
      />
      <CodeComboBox
        id={`${idPrefix}-supply-block`}
        titleText="Supply Block"
        items={supplyBlocksFor(codeLists.supplyBlocks, form.areaType, form.supplyBlock)}
        selectedCode={form.supplyBlock}
        disabled={disabled || tfl}
        invalid={Boolean(errors.supplyBlock)}
        invalidText={errors.supplyBlock}
        onSelect={(code) => onFieldChange('supplyBlock', code)}
      />
      <dl className="schedule-6__derived">
        <FieldValue label="RMG" value={rmg} />
      </dl>
      <TextInput
        id={`${idPrefix}-volume`}
        labelText="Volume m³"
        size="sm"
        inputMode="decimal"
        disabled={disabled}
        value={form.volume}
        onChange={(e) => onFieldChange('volume', e.target.value)}
        invalid={Boolean(errors.volume)}
        invalidText={errors.volume}
      />
      <TextInput
        id={`${idPrefix}-cost`}
        labelText="Cost $"
        size="sm"
        inputMode="numeric"
        disabled={disabled}
        value={form.cost}
        onChange={(e) => onFieldChange('cost', e.target.value)}
        invalid={Boolean(errors.cost)}
        invalidText={errors.cost}
      />
      <dl className="schedule-6__derived">
        <FieldValue label="$ / m³" value={costPerVolume} numeric />
      </dl>
      <div className="schedule-6__comments">
        <TextArea
          id={`${idPrefix}-comments`}
          labelText="Comments"
          rows={2}
          enableCounter
          // 400, not legacy's maxlength=3500: the per-record comment lands in
          // ILCR_COST_REPORT_DETAIL.COMMENTS VARCHAR2(400 BYTE) (deviation E).
          maxCount={RECORD_COMMENTS_MAX_LENGTH}
          disabled={disabled}
          value={form.comments}
          onChange={(e) => onFieldChange('comments', e.target.value)}
          invalid={Boolean(errors.comments)}
          invalidText={errors.comments}
        />
      </div>
    </div>
  )
}

// The Add panel (legacy's toggled `roadAddPanel`). `Add Report` posts immediately — add-is-save, so
// there is no page-level Save to fan out (deviation C).
type AddPanelProps = {
  readonly form: RoadRecordFormValues
  readonly errors: RoadRecordErrors
  readonly codeLists: Schedule6CodeLists
  readonly disabled: boolean
  readonly onAreaTypeChange: (value: string) => void
  readonly onFieldChange: (key: keyof RoadRecordFormValues, value: string) => void
  readonly onSubmit: () => void
}

const AddPanel: FC<AddPanelProps> = ({
  form,
  errors,
  codeLists,
  disabled,
  onAreaTypeChange,
  onFieldChange,
  onSubmit,
}) => (
  <section className="schedule-6__section" aria-label={ADD_PANEL_HEADING}>
    <h3 className="schedule-6__heading">{ADD_PANEL_HEADING}</h3>
    <RoadRecordFields
      idPrefix="add"
      form={form}
      errors={errors}
      codeLists={codeLists}
      disabled={disabled}
      // Both are derived server-side from the saved record; legacy re-derived them live over ajax,
      // which AD-5 forbids re-implementing on the client (deviation D).
      rmg=""
      costPerVolume=""
      onAreaTypeChange={onAreaTypeChange}
      onFieldChange={onFieldChange}
    />
    <Button kind="primary" disabled={disabled} onClick={onSubmit}>
      Add Report
    </Button>
  </section>
)

// A record's read-only display state inside its accordion row: the six legacy-labelled fields plus
// Comments, and the Edit button that opens the editor.
type RecordDisplayProps = {
  readonly row: RoadRecord
  readonly codeLists: Schedule6CodeLists
  readonly editDisabled: boolean
  readonly deleteDisabled: boolean
  readonly onEdit: () => void
  readonly onDelete: () => void
}

// TFL stores the literal 'TFL' in areaType, which is not a served code — describe() would otherwise
// look it up against codeLists.tsaNumbers and fall back to the bare 'TFL' anyway (the sentinel is its
// own description), but resolving it through areaTypeOptions keeps this row and the combo consistent.
const RecordDisplay: FC<RecordDisplayProps> = ({
  row,
  codeLists,
  editDisabled,
  deleteDisabled,
  onEdit,
  onDelete,
}) => (
  <>
    <dl className="schedule-6__fields">
      <FieldValue
        label="TSA or TFL"
        value={
          row.areaType === null ? '' : describe(areaTypeOptions(codeLists.tsaNumbers), row.areaType)
        }
      />
      <FieldValue label="TFL" value={row.tflNumber ?? ''} />
      <FieldValue
        label="Supply Block"
        value={row.supplyBlock === null ? '' : describe(codeLists.supplyBlocks, row.supplyBlock)}
      />
      <FieldValue label="RMG" value={row.rmg ?? ''} />
      <FieldValue label="Volume m³" value={volumeMask(row.volume)} numeric />
      <FieldValue label="Cost $" value={moneyMask(row.cost)} numeric />
      <FieldValue label="$ / m³" value={ratioMask(row.costPerVolume)} numeric />
      <div className="schedule-6__field schedule-6__comments">
        <dt>Comments</dt>
        <dd>{row.comments ?? ''}</dd>
      </div>
    </dl>
    <Button kind="ghost" size="sm" disabled={editDisabled} onClick={onEdit}>
      Edit
    </Button>
    <Button
      kind="danger--ghost"
      size="sm"
      title={DELETE_BUTTON_TITLE}
      // The visible label is the bare legacy "Delete"; the accessible name carries the legacy
      // title (schedule6.xhtml:434) so screen-reader users get the row context sighted users get
      // from position.
      aria-label={DELETE_BUTTON_TITLE}
      disabled={deleteDisabled}
      onClick={onDelete}
    >
      {DELETE_BUTTON_LABEL}
    </Button>
  </>
)

// A record's edit state. Legacy's rows were always directly editable and one page-wide Save posted
// every record at once; 8.2 replaced that with a per-record PUT, so an explicit edit mode is the only
// way to scope one PUT to one row and know which revision token it echoes (deviation C).
type RecordEditorProps = {
  readonly row: RoadRecord
  readonly form: RoadRecordFormValues
  readonly errors: RoadRecordErrors
  readonly codeLists: Schedule6CodeLists
  readonly saving: boolean
  readonly onAreaTypeChange: (value: string) => void
  readonly onFieldChange: (key: keyof RoadRecordFormValues, value: string) => void
  readonly onSave: () => void
  readonly onCancel: () => void
}

const RecordEditor: FC<RecordEditorProps> = ({
  row,
  form,
  errors,
  codeLists,
  saving,
  onAreaTypeChange,
  onFieldChange,
  onSave,
  onCancel,
}) => (
  <>
    <RoadRecordFields
      idPrefix={`edit-${String(row.recordId)}`}
      form={form}
      errors={errors}
      codeLists={codeLists}
      disabled={saving}
      // The row's last server-derived values; they refresh on the save echo.
      rmg={row.rmg ?? ''}
      costPerVolume={ratioMask(row.costPerVolume)}
      onAreaTypeChange={onAreaTypeChange}
      onFieldChange={onFieldChange}
    />
    <Button kind="primary" size="sm" disabled={saving} onClick={onSave}>
      Save
    </Button>
    <Button kind="ghost" size="sm" disabled={saving} onClick={onCancel}>
      Cancel
    </Button>
  </>
)

const Schedule6: FC = () => {
  const { millId, year, contextMissing, isCurrent: contextStillCurrent } = useScheduleContextGuard()

  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<Schedule6CheckStatusResponse | null>(null)

  const [showAdd, setShowAdd] = useState(false)
  const [addForm, setAddForm] = useState<RoadRecordFormValues>(emptyForm)
  const [addErrors, setAddErrors] = useState<RoadRecordErrors>({})

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editRevision, setEditRevision] = useState<number | null>(null)
  const [editForm, setEditForm] = useState<RoadRecordFormValues>(emptyForm)
  const [editErrors, setEditErrors] = useState<RoadRecordErrors>({})

  const [commentsError, setCommentsError] = useState<string | undefined>(undefined)

  // The record awaiting delete confirmation, or null. Holds the id rather than a boolean so a
  // context change mid-dialog cannot leave the confirm pointed at a row from the previous
  // mill/year — resetTransient below clears it on every context change.
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null)

  // Clear all transient mutation + add/edit state whenever a fresh document loads (mill/year change),
  // so a context change can't strand an open editor, an open add panel or a stale banner.
  const resetTransient = useCallback(() => {
    setSaving(false)
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
    setShowAdd(false)
    setAddForm(emptyForm())
    setAddErrors({})
    setEditingId(null)
    setEditRevision(null)
    setEditForm(emptyForm())
    setEditErrors({})
    setCommentsError(undefined)
    setPendingDeleteId(null)
  }, [])

  // The general comment is the one field the DOCUMENT seeds directly, so it rides the hook's form
  // state and is re-seeded on every context change with the rest of the document.
  const { data, setData, form, setForm, errorDetail, isLoading } =
    useScheduleDocument<Schedule6Response>({
      path: SCHEDULE6_PATH,
      millId,
      year,
      contextMissing,
      seedForm: (doc) => ({ generalComments: doc.generalComments ?? '' }),
      mapLoadError,
      onReset: resetTransient,
    })

  const query = `?millId=${String(millId)}&year=${String(year)}`
  const generalComments = form.generalComments ?? ''

  const clearBanners = () => {
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
  }

  const applyDocument = (doc: Schedule6Response) => {
    setData(doc)
    setMessage(doc.message?.text ?? null)
    setActionError(null)
    setCheckResult(null)
  }

  const setAddField = (key: keyof RoadRecordFormValues, value: string) =>
    setAddForm((prev) => ({ ...prev, [key]: value }))
  const setEditField = (key: keyof RoadRecordFormValues, value: string) =>
    setEditForm((prev) => ({ ...prev, [key]: value }))

  // Shared tail for the three DOCUMENT mutations (add, edit, general comment): apply on success, keep
  // entered values and surface the API's verbatim detail on failure, and release the in-flight lock —
  // each branch guarded, including the `finally`, where an unguarded release would free a lock
  // belonging to a NEWER request. Check Status shares the same three-branch guarding but deliberately
  // not this helper: it applies no document (read-only) and owns its own error text, so routing it
  // through here would mean threading an unused onSuccess and an unused applyDocument.
  const runMutation = (
    request: Promise<{ data: Schedule6Response }>,
    onSuccess: (doc: Schedule6Response) => void,
    fallbackError: string,
  ) => {
    setSaving(true)
    request
      .then((response) => {
        if (!contextStillCurrent()) {
          return
        }
        applyDocument(response.data)
        onSuccess(response.data)
      })
      .catch((error: unknown) => {
        if (!contextStillCurrent()) {
          return
        }
        setActionError(extractDetail(error) || fallbackError)
      })
      .finally(() => {
        if (contextStillCurrent()) {
          setSaving(false)
        }
      })
  }

  const handleAdd = () => {
    if (!data || saving) {
      return
    }
    // Clear prior banners first so a validation failure never leaves a stale success/error notice.
    clearBanners()
    const errors = validateRoadRecord(addForm)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    runMutation(
      apiService
        .getAxiosInstance()
        .post<Schedule6Response>(`${RECORDS_PATH}${query}`, buildBody(addForm)),
      () => {
        // add-is-save: inputs clear only on success, and the panel collapses — legacy's add() sets
        // showAddRoadReport = false before saving (Schedule6MB.java:203).
        setAddForm(emptyForm())
        setShowAdd(false)
      },
      'Schedule could not be saved.',
    )
  }

  const startEdit = (row: RoadRecord) => {
    clearBanners()
    setEditingId(row.recordId)
    // revisionCount is read from the LOADED row — never hardcoded, never coerced to 0 (a coerced 0
    // silently bypasses the stale-edit check). `?? null` preserves a legitimate 0.
    setEditRevision(row.revisionCount ?? null)
    setEditForm(seedForm(row))
    setEditErrors({})
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditRevision(null)
    setEditForm(emptyForm())
    setEditErrors({})
  }

  const handleSaveEdit = () => {
    if (editingId === null || saving) {
      return
    }
    clearBanners()
    // Contract guard: 8.1 always serves revisionCount, but the type admits null/absent. A silent
    // early-return would leave an enabled Save that does nothing and mask the contract regression —
    // surface it instead (client-owned generic fallback, AD-8).
    if (editRevision === null) {
      setActionError(
        'This record cannot be saved because it is missing its revision token. Reload the page and try again.',
      )
      return
    }
    const errors = validateRoadRecord(editForm)
    if (Object.keys(errors).length > 0) {
      setEditErrors(errors)
      return
    }
    setEditErrors({})
    runMutation(
      apiService
        .getAxiosInstance()
        .put<Schedule6Response>(
          `${RECORDS_PATH}/${String(editingId)}${query}`,
          buildBody(editForm, editRevision),
        ),
      () => cancelEdit(),
      'Schedule could not be saved.',
    )
  }

  const handleConfirmDelete = () => {
    const recordId = pendingDeleteId
    // Close the dialog whether or not the delete actually proceeds — a stale `saving` guard below
    // must not leave the confirm sitting open with nothing left to confirm.
    setPendingDeleteId(null)
    if (recordId === null || saving) {
      return
    }
    clearBanners()
    runMutation(
      // The recordId travels in the URL — never the 1-based ordinal shown in the accordion title,
      // which is display-only and does not identify the row server-side.
      apiService
        .getAxiosInstance()
        .delete<Schedule6Response>(`${RECORDS_PATH}/${String(recordId)}${query}`),
      () => undefined,
      'Record could not be deleted.',
    )
  }

  const handleSaveComments = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    const error = validateGeneralComments(generalComments)
    if (error) {
      setCommentsError(error)
      return
    }
    setCommentsError(undefined)
    const body: GeneralCommentsRequest = {
      // Blank clears the comment (BR-09 third branch).
      generalComments: generalComments.trim() === '' ? null : generalComments,
    }
    runMutation(
      apiService
        .getAxiosInstance()
        .put<Schedule6Response>(`${GENERAL_COMMENTS_PATH}${query}`, body),
      () => undefined,
      'Comments could not be saved.',
    )
  }

  const handleCheckStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    // In-flight lock: rapid clicks must not issue concurrent POSTs, and a slow check result must not
    // interleave with a mutation — `saving` locks both ways. Read-only; mutates nothing.
    setSaving(true)
    apiService
      .getAxiosInstance()
      .post<Schedule6CheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`)
      .then((response) => {
        if (contextStillCurrent()) {
          setCheckResult(response.data)
        }
      })
      .catch((error: unknown) => {
        if (contextStillCurrent()) {
          setActionError(extractDetail(error) || 'Unable to check status.')
        }
      })
      .finally(() => {
        if (contextStillCurrent()) {
          setSaving(false)
        }
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
          <LoadingScreen label="Loading Schedule 6" />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={PAGE_HEADER}
        notification={{ kind: 'error', title: 'Unable to load Schedule 6', subtitle: errorDetail }}
      />
    )
  }

  if (!data) {
    return null
  }

  // Server-authoritative (AD-9) — never derived from trackStatus or the role.
  const editable = data.editable
  const editing = editingId !== null
  const entryLocked = !editable || saving || editing
  // Deliberately NOT entryLocked: legacy gated Delete on disableReportEdits() only
  // (schedule6.xhtml:436-437), which never considered whether some OTHER row's editor was open.
  // entryLocked would make Delete unavailable across the whole page whenever any editor is open,
  // which legacy never did.
  const deleteDisabled = !editable || saving
  // Legacy's Check Status was an ajax="false" full postback (schedule6.xhtml:226-229): JSF applied
  // the on-screen values to the model BEFORE checkStatus() evaluated it, so the verdict always
  // reflected the screen. The modern check reads only the DB, so it is disabled while unsaved
  // entries are on screen (open row editor, or Add panel with entered values) — a verdict must
  // never contradict visible unsaved input.
  const addDirty = showAdd && Object.values(addForm).some((value) => value.trim() !== '')

  // Two instances, deliberately asymmetric: legacy carried Save + Check Status above the schedule
  // (saveButton0/checkStatusButton0, schedule6.xhtml:222-229) and the same pair again below the
  // General Comment (saveButton1/checkStatusButton1, :518-526) — the same mirrored shape as
  // Schedules 1 and 3. `Add` rides the top bar only: it toggles the entry panel that sits directly
  // beneath it, and legacy's bottom bar carried no add control.
  //
  // Save is the General Comment PUT (deviation C): 8.2 decomposed legacy's page-wide save() into
  // three endpoints, so the road records save themselves via Add Report and the per-row Save, and
  // this is the only page-level write left. Placement changed here; nothing about what it sends did.
  const actionBar = (includeAdd: boolean) => (
    <Column sm={4} md={8} lg={16} className="schedule-6__actions">
      <Button kind="primary" disabled={!editable || saving} onClick={handleSaveComments}>
        Save
      </Button>
      {/* Deviation (H): the API needs only VIEW_SCHEDULE, but legacy gates the button on
          disableReportEdits() (schedule6.xhtml:229,526) — legacy-faithful. */}
      <Button
        kind="tertiary"
        disabled={!editable || saving || editing || addDirty}
        onClick={handleCheckStatus}
      >
        Check Status
      </Button>
      {includeAdd && (
        <Button
          kind="tertiary"
          disabled={entryLocked}
          onClick={() => {
            setShowAdd((prev) => !prev)
          }}
        >
          {showAdd ? 'Close' : 'Add'}
        </Button>
      )}
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
        {checkResult && (
          <Column sm={4} md={8} lg={16} className="schedule-6__check">
            {/* Severity rides `kind` AND a title word, never colour alone (NFR1). Schedule-level
                messages arrive only on MET today, but severity follows the outcome discriminant so a
                message on an ISSUES response can never render under a success banner. */}
            {checkResult.messages.map((entry, index) => (
              <InlineNotification
                key={`sch6-check-message-${String(index)}-${entry.key}`}
                kind={checkResult.outcome === 'MET' ? 'success' : 'error'}
                lowContrast
                title={checkResult.outcome === 'MET' ? 'Requirements met' : 'Action required'}
                subtitle={entry.text}
              />
            ))}
            {checkResult.records.map((record) => (
              <div
                key={`sch6-check-record-${String(record.recordId)}`}
                className="schedule-6__check"
              >
                {record.issues.map((issue, index) => (
                  <InlineNotification
                    // Composed lines can repeat verbatim across fields, so the index disambiguates.
                    key={`sch6-issue-${String(record.recordId)}-${String(index)}-${issue.field}`}
                    kind="error"
                    lowContrast
                    title="Action required"
                    subtitle={issue.message.text}
                  />
                ))}
                {/* metMessage may be ABSENT rather than null (deviation I) — guard on truthiness. */}
                {record.metMessage && (
                  <InlineNotification
                    kind="success"
                    lowContrast
                    title="Requirements met"
                    subtitle={record.metMessage.text}
                  />
                )}
              </div>
            ))}
          </Column>
        )}

        {actionBar(true)}

        {showAdd && (
          <Column sm={4} md={8} lg={16}>
            <AddPanel
              form={addForm}
              errors={addErrors}
              codeLists={data.codeLists}
              disabled={entryLocked}
              onAreaTypeChange={(value) => {
                setAddForm((prev) => applyAreaType(prev, value))
              }}
              onFieldChange={setAddField}
              onSubmit={handleAdd}
            />
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-6__section">
          {data.roadRecords.length === 0 ? (
            // S18/legacy's empty substitute list: the accordion is replaced, while the totals and the
            // general comment below stay visible (deviation J).
            <p className="schedule-6__empty">{EMPTY_LIST}</p>
          ) : (
            <Accordion>
              {data.roadRecords.map((row, index) => (
                <AccordionItem
                  key={row.recordId}
                  // The 1-based ORDINAL into roadRecords[], matching the rowCounter the check-status
                  // lines key on — never recordId, which belongs only in the PUT URL and the key.
                  title={`Road Maintenance report Id: ${String(index + 1)}`}
                >
                  {editingId === row.recordId ? (
                    <RecordEditor
                      row={row}
                      form={editForm}
                      errors={editErrors}
                      codeLists={data.codeLists}
                      saving={saving}
                      onAreaTypeChange={(value) => {
                        setEditForm((prev) => applyAreaType(prev, value))
                      }}
                      onFieldChange={setEditField}
                      onSave={handleSaveEdit}
                      onCancel={cancelEdit}
                    />
                  ) : (
                    <RecordDisplay
                      row={row}
                      codeLists={data.codeLists}
                      editDisabled={entryLocked}
                      deleteDisabled={deleteDisabled}
                      onEdit={() => {
                        startEdit(row)
                      }}
                      onDelete={() => {
                        setPendingDeleteId(row.recordId)
                      }}
                    />
                  )}
                </AccordionItem>
              ))}
            </Accordion>
          )}
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-6__section">
          <section aria-label="Totals" className="schedule-6__totals">
            <span>Totals: </span>
            {/* Its own container, NOT the record-row grid: that grid's 10rem minimum track is sized
                for rows of inputs and wraps these three short numbers into a stack. */}
            <dl className="schedule-6__totals-fields">
              <FieldValue label="Volume m³" value={volumeMask(data.totalVolume)} numeric />
              <FieldValue label="Cost $" value={moneyMask(data.totalCost)} numeric />
              <FieldValue label="$ / m³" value={ratioMask(data.totalCostPerVolume)} numeric />
            </dl>
          </section>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-6__section">
          <section aria-label="General Comments">
            <TextArea
              id="general-comments"
              labelText="General Comments"
              rows={5}
              enableCounter
              // 3500 is the legacy UI cap over a 4000-wide column — a different, wider column than
              // the per-record comment's 400 (deviation E).
              maxCount={GENERAL_COMMENTS_MAX_LENGTH}
              disabled={!editable || saving}
              value={generalComments}
              onChange={(e) => {
                setForm((prev) => ({ ...prev, generalComments: e.target.value }))
              }}
              invalid={Boolean(commentsError)}
              invalidText={commentsError}
            />
          </section>
        </Column>

        {/* Legacy's bottom bar sits AFTER the General Comment, not beside it (schedule6.xhtml:515-529),
            and carries Save + Check Status only. */}
        {actionBar(false)}
      </Grid>
      {/* One dialog for the whole page, keyed on pendingDeleteId — legacy also declared a single
          global confirmDialog (schedule6.xhtml:444-450), not one per row. Conditionally MOUNTED
          (not just `open`-toggled): Carbon's Modal always renders its own icon "Close" button in
          the DOM regardless of `open`, which collides with the page's own Add/Close toggle button
          of the same accessible name. */}
      {pendingDeleteId !== null && (
        <Modal
          open
          danger
          modalHeading={CONFIRM_DELETE_HEADING}
          primaryButtonText="Yes"
          secondaryButtonText="No"
          onRequestSubmit={handleConfirmDelete}
          onRequestClose={() => {
            setPendingDeleteId(null)
          }}
        >
          <p>{CONFIRM_DELETE_BODY}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule6
