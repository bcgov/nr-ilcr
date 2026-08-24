import type { FC } from 'react'
import type Schedule6Response from '@/interfaces/Schedule6Response'
import type { RoadRecord, Schedule6CheckStatusResponse } from '@/interfaces/Schedule6Response'
import type { Schedule6CodeLists } from '@/interfaces/Schedule6Response'
import type {
  RoadRecordEntry,
  RoadRecordRequest,
  Schedule6CheckRequest,
  Schedule6SaveRequest,
} from '@/interfaces/Schedule6Request'
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
import { supplyBlocksFor } from '@/utils/codes'
import { extractDetail } from '@/utils/error'
import { groupFixedInput, groupInput, numStrGroup } from '@/utils/number'
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
import { EMPTY_RATE_INPUTS, rateInputsOf, recordCostPerVolume, type RateInputs } from './derived'
import { isUnusableStrictEntry } from '@/utils/derivedMath'
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
const CHECK_STATUS_PATH = `${SCHEDULE6_PATH}/check-status`
// A missing revisionCount is a contract regression (8.1 always serves it) -- a coerced 0 would
// silently bypass the stale-edit check, so this surfaces instead of sending anything (hazard 1,
// ported from the retired handleSaveEdit guard).
const ERR_MISSING_REVISION =
  'This record cannot be saved because it is missing its revision token. Reload the page and try again.'

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

// Seeded GROUPED, matching every sibling schedule (1, 3-subpage, 7b, 9, 1-other-costs): this was
// the one page still seeding numeric fields with the bare digit string, so 15000 read "15000"
// instead of "15,000" beside plain-text cells that already group.
const seedForm = (row: RoadRecord): RoadRecordFormValues => ({
  areaType: row.areaType ?? '',
  tflNumber: row.tflNumber ?? '',
  supplyBlock: row.supplyBlock ?? '',
  volume: numStrGroup(row.volume),
  cost: numStrGroup(row.cost),
  comments: row.comments ?? '',
})

const isTfl = (areaType: string): boolean => areaType.trim() === TFL_AREA_TYPE

// BR-02 counterpart clear. Clearing must be a STATE change, not just `disabled`: a disabled-but-
// populated input still serializes, and the server clearing it anyway is exactly what would make the
// bug invisible in an integration test but visible in the request body.
const applyAreaType = (form: RoadRecordFormValues, areaType: string): RoadRecordFormValues =>
  isTfl(areaType) ? { ...form, areaType, supplyBlock: '' } : { ...form, areaType, tflNumber: '' }

// Shared by the Add panel POST, the page-level Save PUT and Check Status: the same
// parseDecimalInput/roundCost parse for all three, or a verdict could describe different numbers
// than a save would store (step 4.6). Check-status entries are exactly this shape -- no recordId, no
// revisionCount, rows identified by payload ordinal (Task 6) -- so buildCheckEntry is this function
// under a name that says what it is used for at each call site.
const buildBody = (form: RoadRecordFormValues): RoadRecordRequest => {
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
  }
}

const buildCheckEntry = buildBody

// One row of the Save PUT: the parsed fields plus the identifiers the batch endpoint needs to place
// and lock it. `recordId`/`revisionCount` exist only on this shape, not on RoadRecordRequest --
// callers must resolve the hazard-1 guard before this is ever called.
const buildSaveEntry = (
  form: RoadRecordFormValues,
  recordId: number,
  revisionCount: number,
): Schedule6SaveRequest['records'][number] => ({
  ...buildBody(form),
  recordId,
  revisionCount,
})

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
  /** Blur commit for the two fields the $ / m³ is computed from (defect #291). */
  readonly onRateCommit: () => void
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
  onRateCommit,
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
        // Options render the code's DESCRIPTION (corrections 2/3 above), and a description like
        // "Arrowsmith TSA" truncates under the shared grid's 10rem minimum track — wide spans two.
        className="schedule-6__field--wide"
        titleText="TSA or TFL"
        items={areaTypeOptions(codeLists.tsaNumbers, form.areaType)}
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
        // Same widening as TSA or TFL above, for the same reason — its options are descriptions too.
        className="schedule-6__field--wide"
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
        // One blur does two things (#291 + fix 2), in this order:
        //
        // 1. Commit the $ / m³ baseline, which validates the form AS TYPED -- so the re-group must
        //    come after it, never before (the derived-figure work found that re-grouping first
        //    replaced the form mid-validation and stopped the commit landing at all).
        // 2. Re-group the field itself, on blur only and never mid-keystroke (that would fight the
        //    caret) -- through groupInput, not a fixed mask, because volume permits up to 2 decimals
        //    and groupInput preserves exactly what was typed (sibling schedules 1 / 1-other-costs /
        //    7b / 9). Invalid text passes through unchanged, so a typo stays on screen for the user
        //    to correct. Deliberately NOT gated on the commit succeeding: masking the entry and
        //    moving the derived cell are separate legacy behaviours, and an out-of-range volume that
        //    holds the rate at its last valid figure must still be shown grouped.
        onBlur={() => {
          onRateCommit()
          const grouped = groupInput(form.volume)
          if (grouped !== form.volume) {
            onFieldChange('volume', grouped)
          }
        }}
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
        // Commit-then-re-group, as on Volume above. Fixed to 0 decimals, not plain groupInput:
        // legacy's mask for this field was ##,###,### (mask.int.7digits, `moneyMask` above) and
        // roundCost already sends a whole-dollar wire value, so a typed "1500.7" must re-render as
        // "1,501" -- matching what actually gets stored -- rather than lingering on screen as a
        // fractional value the field will never save. The rate agrees with it by construction:
        // recordCostPerVolume rounds the cost through the same whole-dollar step (derived.ts).
        onBlur={() => {
          onRateCommit()
          const grouped = groupFixedInput(form.cost, 0)
          if (grouped !== form.cost) {
            onFieldChange('cost', grouped)
          }
        }}
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

// The Add panel (legacy's toggled `roadAddPanel`). `Add Report` posts immediately — add-is-save,
// independent of the page-level Save (`handleSave`) that fans the rows and general comment out
// together; deviation (C) is retired now that every row is always editable (Task 7).
type AddPanelProps = {
  readonly form: RoadRecordFormValues
  readonly errors: RoadRecordErrors
  readonly codeLists: Schedule6CodeLists
  readonly disabled: boolean
  readonly rateInputs: RateInputs
  readonly onAreaTypeChange: (value: string) => void
  readonly onFieldChange: (key: keyof RoadRecordFormValues, value: string) => void
  readonly onRateCommit: () => void
  readonly onSubmit: () => void
}

const AddPanel: FC<AddPanelProps> = ({
  form,
  errors,
  codeLists,
  disabled,
  rateInputs,
  onAreaTypeChange,
  onFieldChange,
  onRateCommit,
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
      rmg=""
      // The rate tracks the committed (blurred) volume/cost, as legacy's own `change` handler did
      // (#291). `rmg` stays server-derived — see derived.ts for why it is not mirrored.
      costPerVolume={ratioMask(recordCostPerVolume(rateInputs))}
      onAreaTypeChange={onAreaTypeChange}
      onFieldChange={onFieldChange}
      onRateCommit={onRateCommit}
    />
    <Button kind="primary" disabled={disabled} onClick={onSubmit}>
      Add Report
    </Button>
  </section>
)

// A record's row inside its accordion: the six legacy-labelled fields (always live -- legacy rows
// were always directly editable, schedule6.xhtml:248-431) plus the Delete button (Task 4).
type RoadRecordRowProps = {
  readonly row: RoadRecord
  readonly ordinal: number
  readonly form: RoadRecordFormValues
  readonly errors: RoadRecordErrors
  readonly codeLists: Schedule6CodeLists
  readonly disabled: boolean
  readonly deleteDisabled: boolean
  readonly rateInputs: RateInputs
  readonly onAreaTypeChange: (value: string) => void
  readonly onFieldChange: (key: keyof RoadRecordFormValues, value: string) => void
  readonly onRateCommit: () => void
  readonly onDelete: () => void
}

const RoadRecordRow: FC<RoadRecordRowProps> = ({
  row,
  ordinal,
  form,
  errors,
  codeLists,
  disabled,
  deleteDisabled,
  rateInputs,
  onAreaTypeChange,
  onFieldChange,
  onRateCommit,
  onDelete,
}) => (
  <>
    <RoadRecordFields
      idPrefix={`row-${String(row.recordId)}`}
      form={form}
      errors={errors}
      codeLists={codeLists}
      disabled={disabled}
      // `rmg` is the row's last server-derived value and refreshes on the save echo; the rate now
      // tracks the committed (blurred) volume/cost, as legacy's own `change` handler did (#291).
      rmg={row.rmg ?? ''}
      costPerVolume={ratioMask(recordCostPerVolume(rateInputs))}
      onAreaTypeChange={onAreaTypeChange}
      onFieldChange={onFieldChange}
      onRateCommit={onRateCommit}
    />
    <Button
      kind="danger--ghost"
      size="sm"
      title={DELETE_BUTTON_TITLE}
      // The VISIBLE label stays the bare legacy "Delete" (legacy carries no per-row text of its
      // own to diverge from — this is chrome, not a fidelity claim). The ACCESSIBLE name appends the
      // ordinal so an N-row schedule doesn't collapse into N identically-named buttons (Carbon renders
      // every AccordionItem's children into the DOM regardless of which panel is expanded) — matching
      // the row's own "Road Maintenance report Id: N" accordion title (final-review M8).
      aria-label={`${DELETE_BUTTON_TITLE} ${String(ordinal)}`}
      disabled={deleteDisabled}
      onClick={onDelete}
    >
      {DELETE_BUTTON_LABEL}
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
  // The blur-committed volume/cost the $ / m³ mirror reads. Legacy refreshed the row's own rate on the
  // field's own `change` handler, so the rate settles when focus leaves rather than per keystroke (#291).
  const [addRate, setAddRate] = useState<RateInputs>(EMPTY_RATE_INPUTS)
  const [addErrors, setAddErrors] = useState<RoadRecordErrors>({})

  // One form per row, keyed by recordId. Legacy's rows were always directly editable and one
  // page-wide Save posted every record at once (schedule6.xhtml:248-431, Schedule6MB.save); the
  // shipped Edit button existed only to scope a per-record PUT, which no longer exists (correction 4,
  // retiring deviation (C)). A row with no entry here falls back to the document's own values (see
  // getRowForm) -- so a freshly-loaded or freshly-added row needs no explicit seed.
  const [rowForms, setRowForms] = useState<Record<number, RoadRecordFormValues>>({})
  const [rowErrors, setRowErrors] = useState<Record<number, RoadRecordErrors>>({})
  // The blur-committed volume/cost each row's $ / m³ mirror reads, keyed by recordId (#291). Same
  // fallback rule as rowForms: a row with no entry here reads the document's own values (getRowRate),
  // so the load and every echo seed it without an explicit write. The per-row map replaces the single
  // editRate the derived-figure work carried, because there is no longer one open editor at a time.
  const [rowRates, setRowRates] = useState<Record<number, RateInputs>>({})

  /**
   * Advance a rate baseline only from a usable, valid entry (ruled 2026-08-21). Legacy's round-trip
   * failed conversion/validation and left the derived cell at its last valid figure; committing an
   * out-of-range or unparseable value instead drives the rate from something no Save can persist.
   *
   * Re-grouping the blurred FIELD is the caller's job here (the `onBlur` handlers in
   * RoadRecordFields), not this helper's: this page masks Volume and Cost differently -- groupInput
   * vs groupFixedInput(_, 0) -- and masks unconditionally, whereas the rate below moves only when the
   * entry passes the gate.
   */
  const commitRate = (form: RoadRecordFormValues, apply: (next: RateInputs) => void): void => {
    const errors = validateRoadRecord(form)
    if (errors.volume !== undefined || errors.cost !== undefined) {
      return
    }
    if (isUnusableStrictEntry(form.volume) || isUnusableStrictEntry(form.cost)) {
      return
    }
    apply(rateInputsOf(form))
  }

  const [commentsError, setCommentsError] = useState<string | undefined>(undefined)

  // The record awaiting delete confirmation, or null. Holds the id rather than a boolean so a
  // context change mid-dialog cannot leave the confirm pointed at a row from the previous
  // mill/year — resetTransient below clears it on every context change.
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null)

  // Clear all transient mutation + add/row state whenever a fresh document loads (mill/year change),
  // so a context change can't strand an open add panel, a stale banner, or -- the hazard this design
  // most invites, with N row forms instead of one -- a PREVIOUS mill's edits sitting over the new
  // document. Without this, a row form only clears by being re-seeded (save echo) or never at all.
  const resetTransient = useCallback(() => {
    setSaving(false)
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
    setShowAdd(false)
    setAddForm(emptyForm())
    setAddRate(EMPTY_RATE_INPUTS)
    setAddErrors({})
    setRowForms({})
    setRowErrors({})
    setRowRates({})
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

  // The row's current on-screen values: whatever the user has typed, or the document's own values
  // when the user hasn't touched this row yet (or the document just (re)loaded/echoed and cleared the
  // map). This is what every read of a row's form -- render, Save, Check Status -- goes through, so
  // the three can never disagree about what "on screen" means for a given row.
  const getRowForm = (row: RoadRecord): RoadRecordFormValues =>
    rowForms[row.recordId] ?? seedForm(row)

  // The row's rate baseline: whatever the user last committed on this row, or the document's own
  // values when they haven't committed one (fresh load, save echo, or an untouched row). Read from
  // seedForm(row) rather than getRowForm(row) deliberately -- falling back to the LIVE form would make
  // the $ / m³ track every keystroke, which is exactly what committing on blur exists to prevent.
  const getRowRate = (row: RoadRecord): RateInputs =>
    rowRates[row.recordId] ?? rateInputsOf(seedForm(row))

  const commitRowRate = (row: RoadRecord) => {
    commitRate(getRowForm(row), (next) => {
      setRowRates((prev) => ({ ...prev, [row.recordId]: next }))
    })
  }

  const updateRowForm = (
    row: RoadRecord,
    updater: (form: RoadRecordFormValues) => RoadRecordFormValues,
  ) => {
    setRowForms((prev) => ({
      ...prev,
      [row.recordId]: updater(prev[row.recordId] ?? seedForm(row)),
    }))
  }

  // Shared tail for the DOCUMENT mutations (add, delete, whole-document save): apply on success, keep
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
        setAddRate(EMPTY_RATE_INPUTS)
        setShowAdd(false)
      },
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
    // A successful delete leaves this row's rowForms/rowErrors entry behind, orphaned -- deliberately
    // not cleaned up here. Both the render loop and handleSave iterate `data.roadRecords`, so an entry
    // with no matching row is never read or sent, and resetTransient bounds its lifetime to the
    // current mill/year session (it clears on the next context change regardless).
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

  // Replaces handleSaveEdit + handleSaveComments: legacy's page-wide Save posted every record plus
  // the general comment in one transaction (schedule6.xhtml:222-229/518-526, Schedule6MB.save), which
  // Story 8.2 decomposed into per-record PUTs -- Task 5 restores the single endpoint, so this restores
  // the single handler.
  const handleSave = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    const commentError = validateGeneralComments(generalComments)
    const errors: Record<number, RoadRecordErrors> = {}
    for (const row of data.roadRecords) {
      const rowError = validateRoadRecord(getRowForm(row))
      if (Object.keys(rowError).length > 0) {
        errors[row.recordId] = rowError
      }
    }
    setRowErrors(errors)
    setCommentsError(commentError)
    if (commentError || Object.keys(errors).length > 0) {
      return
    }
    // Hazard 1: 8.1 always serves revisionCount, but the type admits null/absent. Sending a coerced 0
    // would silently bypass the stale-edit check for exactly this row -- a missing token must surface
    // as a client-owned error and the WHOLE save must stop (one transaction; partial send is worse
    // than no send).
    // Built in the SAME pass that checks the token (rather than a separate .map that would need a
    // cast at the call site) -- a cast that depends on a guard six lines away having already run rots
    // quietly once the two stop being read together.
    // Mutable while it is being built, then assigned to the request's `readonly records` below --
    // a mutable array is assignable to a readonly one, not the reverse. Typing this as
    // `Schedule6SaveRequest['records']` made `.push` a TS2339 (readonly arrays expose no mutators);
    // nothing in the pipeline type-checks -- `build` is bare `vite build` and Vitest runs through
    // esbuild, both of which strip types without checking them -- so it stayed invisible until a
    // reviewer ran `tsc` (code review 2026-08-24).
    const entries: RoadRecordEntry[] = []
    for (const row of data.roadRecords) {
      if (row.revisionCount === null || row.revisionCount === undefined) {
        setActionError(ERR_MISSING_REVISION)
        return
      }
      entries.push(buildSaveEntry(getRowForm(row), row.recordId, row.revisionCount))
    }
    const body: Schedule6SaveRequest = {
      generalComments: generalComments.trim() === '' ? null : generalComments,
      // EVERY served row travels, each with the revision token from the document it was seeded
      // from. An omitted row is a 400 -- the server refuses to guess what the user meant to leave
      // alone.
      records: entries,
    }
    runMutation(
      apiService.getAxiosInstance().put<Schedule6Response>(`${SCHEDULE6_PATH}${query}`, body),
      (doc) => {
        // Re-seed every row form from the echo -- NOT for the revision token (RoadRecordFormValues
        // carries no such field; the token always comes straight off `data.roadRecords`, fresh on
        // every applyDocument). This adopts the server's canonical/normalised values (e.g. a trimmed
        // or otherwise server-corrected entry) over whatever draft the user was looking at, so the
        // screen matches what was actually stored.
        setRowForms(Object.fromEntries(doc.roadRecords.map((row) => [row.recordId, seedForm(row)])))
        // The rate baselines follow the same re-seed: clearing the map hands every row back to
        // getRowRate's document fallback, which now reads the echoed values (#291).
        setRowRates({})
      },
      'Schedule could not be saved.',
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
    // Posts the ON-SCREEN values, not the stored ones (Task 6): legacy's Check Status was an
    // ajax="false" full postback (schedule6.xhtml:226-229) that applied entered values to the model
    // BEFORE evaluating, so the verdict always reflected the screen -- never gated on dirtiness.
    const body: Schedule6CheckRequest = {
      generalComments: generalComments.trim() === '' ? null : generalComments,
      records: data.roadRecords.map((row) => buildCheckEntry(getRowForm(row))),
    }
    apiService
      .getAxiosInstance()
      .post<Schedule6CheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`, body)
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

  // Server-authoritative (AD-9) — never derived from trackStatus or the role. No `editing` term any
  // more: every row is always live, so there is no separate "an editor is open" state to fold in.
  const editable = data.editable
  // Shared by every row control AND Delete (deliberately the same gate now, not the historical
  // per-row-editor distinction): legacy gated Delete on disableReportEdits() only
  // (schedule6.xhtml:436-437), same as every other row input.
  const entryLocked = !editable || saving
  const deleteDisabled = entryLocked

  // Two instances, deliberately asymmetric: legacy carried Save + Check Status above the schedule
  // (saveButton0/checkStatusButton0, schedule6.xhtml:222-229) and the same pair again below the
  // General Comment (saveButton1/checkStatusButton1, :518-526) — the same mirrored shape as
  // Schedules 1 and 3. `Add` rides the top bar only: it toggles the entry panel that sits directly
  // beneath it, and legacy's bottom bar carried no add control.
  const actionBar = (includeAdd: boolean) => (
    <Column sm={4} md={8} lg={16} className="schedule-6__actions">
      <Button kind="primary" disabled={!editable || saving} onClick={handleSave}>
        Save
      </Button>
      {/* Deviation (H): the API needs only VIEW_SCHEDULE, but legacy gates the button on
          disableReportEdits() (schedule6.xhtml:229,526) — legacy-faithful. Gated on THAT only: legacy
          never disabled Check Status while unsaved input sat on screen (schedule6.xhtml:226-229), and
          the modern body now posts the on-screen values itself instead of needing the DB to agree. */}
      <Button kind="tertiary" disabled={!editable || saving} onClick={handleCheckStatus}>
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
              rateInputs={addRate}
              onRateCommit={() => {
                commitRate(addForm, setAddRate)
              }}
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
                  <RoadRecordRow
                    row={row}
                    ordinal={index + 1}
                    form={getRowForm(row)}
                    errors={rowErrors[row.recordId] ?? {}}
                    codeLists={data.codeLists}
                    disabled={entryLocked}
                    deleteDisabled={deleteDisabled}
                    rateInputs={getRowRate(row)}
                    onAreaTypeChange={(value) => {
                      updateRowForm(row, (prev) => applyAreaType(prev, value))
                    }}
                    onFieldChange={(key, value) => {
                      updateRowForm(row, (prev) => ({ ...prev, [key]: value }))
                    }}
                    onRateCommit={() => {
                      commitRowRate(row)
                    }}
                    onDelete={() => {
                      setPendingDeleteId(row.recordId)
                    }}
                  />
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
