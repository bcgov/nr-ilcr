import type { FC } from 'react'
import type Schedule6Response from '@/interfaces/Schedule6Response'
import type { RoadRecord, Schedule6CheckStatusResponse } from '@/interfaces/Schedule6Response'
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
  TextArea,
  TextInput,
} from '@carbon/react'
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { extractDetail } from '@/utils/error'
import { numStr } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import PageTitle from '@/components/core/PageTitle'
import {
  AREA_TYPE_MAX_LENGTH,
  GENERAL_COMMENTS_MAX_LENGTH,
  RECORD_COMMENTS_MAX_LENGTH,
  SUPPLY_BLOCK_MAX_LENGTH,
  TFL_AREA_TYPE,
  TFL_MAX_LENGTH,
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
type RoadRecordFieldsProps = {
  readonly idPrefix: string
  readonly labelPrefix: string
  readonly form: RoadRecordFormValues
  readonly errors: RoadRecordErrors
  readonly disabled: boolean
  readonly rmg: string
  readonly costPerVolume: string
  readonly onAreaTypeChange: (value: string) => void
  readonly onFieldChange: (key: keyof RoadRecordFormValues, value: string) => void
}

const RoadRecordFields: FC<RoadRecordFieldsProps> = ({
  idPrefix,
  labelPrefix,
  form,
  errors,
  disabled,
  rmg,
  costPerVolume,
  onAreaTypeChange,
  onFieldChange,
}) => {
  const tfl = isTfl(form.areaType)
  return (
    <div className="schedule-6__fields">
      {/* Deviation (A): TSA and Supply Block are text inputs over the raw code — legacy's
          year-scoped selectOneMenu caches have no REST counterpart and no Schedule 6 codes
          endpoint exists (the blessed Schedule 8 simplification). */}
      <TextInput
        id={`${idPrefix}-area-type`}
        labelText={`${labelPrefix}TSA or TFL`}
        size="sm"
        maxLength={AREA_TYPE_MAX_LENGTH}
        disabled={disabled}
        value={form.areaType}
        onChange={(e) => onAreaTypeChange(e.target.value)}
        invalid={Boolean(errors.areaType)}
        invalidText={errors.areaType}
      />
      <TextInput
        id={`${idPrefix}-tfl-number`}
        labelText={`${labelPrefix}TFL`}
        size="sm"
        maxLength={TFL_MAX_LENGTH}
        disabled={disabled || !tfl}
        value={form.tflNumber}
        onChange={(e) => onFieldChange('tflNumber', e.target.value)}
        invalid={Boolean(errors.tflNumber)}
        invalidText={errors.tflNumber}
      />
      <TextInput
        id={`${idPrefix}-supply-block`}
        labelText={`${labelPrefix}Supply Block`}
        size="sm"
        maxLength={SUPPLY_BLOCK_MAX_LENGTH}
        disabled={disabled || tfl}
        value={form.supplyBlock}
        onChange={(e) => onFieldChange('supplyBlock', e.target.value)}
        invalid={Boolean(errors.supplyBlock)}
        invalidText={errors.supplyBlock}
      />
      <dl className="schedule-6__derived">
        <FieldValue label="RMG" value={rmg} />
      </dl>
      <TextInput
        id={`${idPrefix}-volume`}
        labelText={`${labelPrefix}Volume m³`}
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
        labelText={`${labelPrefix}Cost $`}
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
          labelText={`${labelPrefix}Comments`}
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
  readonly disabled: boolean
  readonly onAreaTypeChange: (value: string) => void
  readonly onFieldChange: (key: keyof RoadRecordFormValues, value: string) => void
  readonly onSubmit: () => void
}

const AddPanel: FC<AddPanelProps> = ({
  form,
  errors,
  disabled,
  onAreaTypeChange,
  onFieldChange,
  onSubmit,
}) => (
  <section className="schedule-6__section" aria-label={ADD_PANEL_HEADING}>
    <h3 className="schedule-6__heading">{ADD_PANEL_HEADING}</h3>
    <RoadRecordFields
      idPrefix="add"
      labelPrefix=""
      form={form}
      errors={errors}
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
  readonly editDisabled: boolean
  readonly onEdit: () => void
}

const RecordDisplay: FC<RecordDisplayProps> = ({ row, editDisabled, onEdit }) => (
  <>
    <dl className="schedule-6__fields">
      <FieldValue label="TSA or TFL" value={row.areaType ?? ''} />
      <FieldValue label="TFL" value={row.tflNumber ?? ''} />
      <FieldValue label="Supply Block" value={row.supplyBlock ?? ''} />
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
  </>
)

// A record's edit state. Legacy's rows were always directly editable and one page-wide Save posted
// every record at once; 8.2 replaced that with a per-record PUT, so an explicit edit mode is the only
// way to scope one PUT to one row and know which revision token it echoes (deviation C).
type RecordEditorProps = {
  readonly row: RoadRecord
  readonly form: RoadRecordFormValues
  readonly errors: RoadRecordErrors
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
  saving,
  onAreaTypeChange,
  onFieldChange,
  onSave,
  onCancel,
}) => (
  <>
    <RoadRecordFields
      idPrefix={`edit-${String(row.recordId)}`}
      labelPrefix="Edit "
      form={form}
      errors={errors}
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
  // Legacy's Check Status was an ajax="false" full postback (schedule6.xhtml:226-229): JSF applied
  // the on-screen values to the model BEFORE checkStatus() evaluated it, so the verdict always
  // reflected the screen. The modern check reads only the DB, so it is disabled while unsaved
  // entries are on screen (open row editor, or Add panel with entered values) — a verdict must
  // never contradict visible unsaved input.
  const addDirty = showAdd && Object.values(addForm).some((value) => value.trim() !== '')

  return (
    <div className="app-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16} className="schedule-6__meta">
          <dl className="schedule-6__summary">
            <div className="schedule-6__field">
              <dt>Mill</dt>
              <dd>{data.millId}</dd>
            </div>
            <div className="schedule-6__field">
              <dt>Reporting Year</dt>
              <dd>{data.year}</dd>
            </div>
            <div className="schedule-6__field">
              <dt>Status</dt>
              <dd>{data.trackStatus ?? '—'}</dd>
            </div>
          </dl>
        </Column>

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

        <Column sm={4} md={8} lg={16} className="schedule-6__actions">
          {/* Deviation (H): the API needs only VIEW_SCHEDULE, but legacy gates the button on
              disableReportEdits() (schedule6.xhtml:229,526) — legacy-faithful. */}
          <Button
            kind="tertiary"
            disabled={!editable || saving || editing || addDirty}
            onClick={handleCheckStatus}
          >
            Check Status
          </Button>
          <Button
            kind="tertiary"
            disabled={entryLocked}
            onClick={() => {
              setShowAdd((prev) => !prev)
            }}
          >
            {showAdd ? 'Close' : 'Add'}
          </Button>
        </Column>

        {showAdd && (
          <Column sm={4} md={8} lg={16}>
            <AddPanel
              form={addForm}
              errors={addErrors}
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
                      editDisabled={entryLocked}
                      onEdit={() => {
                        startEdit(row)
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
            <span className="schedule-6__totals-label">Totals: </span>
            <dl className="schedule-6__fields">
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
            {/* Its own save (deviation C): 8.2 decomposed legacy's page-wide Save into three
                independent endpoints, and this one saves with zero road records (BR-09). */}
            <Button
              kind="primary"
              className="schedule-6__comments-save"
              disabled={!editable || saving}
              onClick={handleSaveComments}
            >
              Save
            </Button>
          </section>
        </Column>
      </Grid>
    </div>
  )
}

export default Schedule6
