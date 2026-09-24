import OriginalValueIndicator from '@/components/core/OriginalValueIndicator'
import type { FC } from 'react'
import type Schedule11Response from '@/interfaces/Schedule11Response'
import type {
  BiogeoclimaticOption,
  Schedule11CheckStatusResponse,
  SilvicultureLocation,
} from '@/interfaces/Schedule11Response'
import type SilvicultureLocationRequest from '@/interfaces/Schedule11Request'
import type { LocationSaveAllRequest } from '@/interfaces/Schedule11Request'
import type { LocationFormValues, SilvicultureErrors } from './validation'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Button,
  ComboBox,
  Column,
  Dropdown,
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
import CommentsTextArea from '@/components/core/CommentsTextArea'
import SaveCheckActions from '@/components/core/SaveCheckActions'
import { Add, TrashCan } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { extractDetail } from '@/utils/error'
import { numStr, numStrFixed } from '@/utils/number'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import {
  validateLocation,
  parseDecimalInput,
  COMMENTS_MAX_LENGTH,
  LOCATION_MAX_LENGTH,
} from './validation'
import './index.scss'

// Client-only chrome (no request behind it), verbatim from the legacy bundle. Every success/error is
// rendered from the API `message.text` / ProblemDetail.detail — never hardcoded (AD-8). The
// context-missing literal has no trailing space (sibling convention); the SERVER's ERR-001 (with its
// real trailing space) still renders verbatim when a request returns it.
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const SCHEDULE11_PATH = '/v1/schedule11'
const BEC_CATALOGUE_PATH = '/v1/schedule11/biogeoclimatic-catalogue'
const BEC_DEBOUNCE_MS = 250

// Legacy display masks (AD-5 no recompute — the values are server-computed, this only formats them).
// Delegate to the shared en-CA numStrFixed (Story 29.8): one locale app-wide, no local toLocaleString
// copy. numStrFixed keeps the same contract — fixed decimals, and BLANK (never "0") on null, since a
// null total means "no contributors", which is meaningful.
const money = (value: number | null | undefined): string => numStrFixed(value, 0) // #,###,##0
const area = (value: number | null | undefined): string => numStrFixed(value, 1) // #,###,##0.0
const ratio = (value: number | null | undefined): string => numStrFixed(value, 2) // #,###,##0.00

// Whole-dollar costs: legacy accepted fractional input (ILCRCostConverter BigDecimal parse) and
// Oracle COST NUMBER(15) ROUNDED it on insert, while the modern Integer wire would silently
// TRUNCATE at deserialization. Round half-away-from-zero (Oracle's rounding) before send so the
// stored value matches legacy; the backend independently rejects any fractional cost (@Digits).
const roundCost = (value: number | null): number | null =>
  value === null ? null : Math.sign(value) * Math.round(Math.abs(value))

const emptyForm = (): LocationFormValues => ({
  location: '',
  enhanced: null,
  bec: null,
  netArea: '',
  actualCost: '',
  plannedCost: '',
  comments: '',
})

// Enhanced (ES) is a required boolean rendered as a Yes/No Dropdown with NO default selection, so
// "required / not selected" (null) stays expressible — a checkbox could not represent it (S15).
const ENHANCED_ITEMS = [
  { value: true, label: 'Yes' },
  { value: false, label: 'No' },
] as const
type EnhancedItem = (typeof ENHANCED_ITEMS)[number]

type EnhancedDropdownProps = {
  readonly id: string
  readonly label: string
  readonly value: boolean | null
  readonly disabled?: boolean
  readonly invalidText?: string
  // Set inside table rows, where the column header already names the field and a per-cell label
  // would print as stray text above every control. Carbon keeps it in the a11y tree.
  readonly hideLabel?: boolean
  readonly onChange: (value: boolean | null) => void
}

const EnhancedDropdown: FC<EnhancedDropdownProps> = ({
  id,
  label,
  value,
  disabled,
  invalidText,
  hideLabel,
  onChange,
}) => (
  <Dropdown<EnhancedItem>
    id={id}
    titleText={label}
    hideLabel={hideLabel}
    label="Select"
    // Deliberately NO `size` prop: field height is owned app-wide by the global
    // --cds-layout-size-height-local override (styles/_overrides.scss, Story 30.2 / #312 — 48px
    // page-level, 40px inside .cds--data-table). Passing size="sm" here does NOT shrink a
    // TextInput or ComboBox (neither has a size rule that touches block-size, so both keep
    // reading the token) but it DOES shrink a Dropdown: `.cds--dropdown--sm` hardcodes
    // `block-size: 2rem` directly, which outranks the token at equal specificity by source
    // order. That left this one control 32px beside its 48px neighbours.
    items={ENHANCED_ITEMS as unknown as EnhancedItem[]}
    itemToString={(item) => item?.label ?? ''}
    selectedItem={ENHANCED_ITEMS.find((item) => item.value === value) ?? null}
    disabled={disabled}
    invalid={Boolean(invalidText)}
    invalidText={invalidText}
    onChange={({ selectedItem }) => onChange(selectedItem?.value ?? null)}
  />
)

// The one net-new widget: a type-ahead ComboBox over the BEC catalogue (BR-09 forced selection). On
// input it debounces a server search; only a value chosen from the suggestions resolves to an option
// (and thus an id) — free text that was never picked leaves the selection null (treated as empty).
// Module-level so it isn't recreated per page render; reused by the Add panel and inline row edit.
type BiogeoComboBoxProps = {
  readonly id: string
  readonly label: string
  readonly selected: BiogeoclimaticOption | null
  readonly disabled?: boolean
  readonly invalidText?: string
  readonly hideLabel?: boolean
  readonly onSelect: (option: BiogeoclimaticOption | null) => void
}

const BiogeoComboBox: FC<BiogeoComboBoxProps> = ({
  id,
  label,
  selected,
  disabled,
  invalidText,
  hideLabel,
  onSelect,
}) => {
  const [items, setItems] = useState<BiogeoclimaticOption[]>([])
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // Monotonic search token: only the LATEST dispatched search may populate the list, so an older,
  // slower response can never overwrite newer suggestions (out-of-order type-ahead race).
  const searchSeqRef = useRef(0)
  // Latest resolved label, updated synchronously on selection so the follow-up onInputChange (which
  // Carbon fires with the chosen label) does not mistake the selection for stray typing.
  const selectedLabelRef = useRef<string | null>(selected?.label ?? null)

  useEffect(
    () => () => {
      // Invalidate any in-flight search on unmount (its setItems must not land) + drop the timer.
      searchSeqRef.current += 1
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    },
    [],
  )

  const runSearch = (query: string) => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }
    const term = query.trim()
    // Client-side minQueryLength=1 (legacy): a blank term never costs a round-trip, and clearing
    // the field invalidates any in-flight search so stale suggestions cannot repopulate the list.
    if (term === '') {
      searchSeqRef.current += 1
      setItems([])
      return
    }
    timerRef.current = setTimeout(() => {
      const seq = ++searchSeqRef.current
      apiService
        .getAxiosInstance()
        .get<BiogeoclimaticOption[]>(`${BEC_CATALOGUE_PATH}?q=${encodeURIComponent(term)}`)
        .then((response) => {
          if (seq === searchSeqRef.current) {
            setItems(response.data)
          }
        })
        .catch(() => {
          if (seq === searchSeqRef.current) {
            setItems([])
          }
        })
    }, BEC_DEBOUNCE_MS)
  }

  return (
    <ComboBox
      id={id}
      // Unlike Dropdown, ComboBox has no hideLabel prop — but it falls back to aria-label for the
      // input whenever titleText is absent (ComboBox.js:486), so dropping the visible label still
      // leaves the control with an accessible name. Passing both is safe: Carbon ignores the
      // aria-label while titleText is set.
      titleText={hideLabel ? undefined : label}
      aria-label={label}
      // No `size` prop, same as EnhancedDropdown — the global height override owns this.
      placeholder="Type to search"
      disabled={disabled}
      items={items}
      selectedItem={selected}
      itemToString={(item) => item?.label ?? ''}
      // Server-side filtered: show every fetched suggestion (no client re-filtering by input text).
      shouldFilterItem={() => true}
      invalid={Boolean(invalidText)}
      invalidText={invalidText}
      onChange={({ selectedItem }) => {
        selectedLabelRef.current = selectedItem?.label ?? null
        onSelect(selectedItem ?? null)
      }}
      onInputChange={(text) => {
        // Forced selection: typing that no longer matches the resolved option drops it, so an id is
        // submitted only for a value chosen from the catalogue suggestions (BR-09/S16).
        if (selected && text !== selectedLabelRef.current) {
          onSelect(null)
        }
        runSearch(text)
      }}
    />
  )
}

// Column order, header text and which columns sort are all legacy-verbatim: every legacy
// p:column carries sortBy EXCEPT Comments (xhtml:353) and Delete (xhtml:364), so those two
// alone stay unsorted. Keeping the definitions in one list means the header row cannot drift
// out of step with the sortable set.
type SortKey =
  | 'location'
  | 'becLabel'
  | 'enhancedIndicator'
  | 'netArea'
  | 'actualCost'
  | 'plannedCost'
  | 'totalCost'
  | 'costPerNetArea'

type SortDirection = 'NONE' | 'ASC' | 'DESC'

type SilvicultureColumn = {
  readonly label: string
  readonly sortKey: SortKey | null
  readonly numeric?: boolean
}

const COLUMNS: readonly SilvicultureColumn[] = [
  { label: 'Location', sortKey: 'location' }, // xhtml:208
  { label: 'Biogeo/Subzone/Variant', sortKey: 'becLabel' }, // xhtml:228
  { label: 'ES', sortKey: 'enhancedIndicator' }, // xhtml:255
  { label: 'NAR(ha)', sortKey: 'netArea', numeric: true }, // xhtml:274
  { label: 'Actual Cost ($)', sortKey: 'actualCost', numeric: true }, // xhtml:296
  { label: 'Planned Cost ($)', sortKey: 'plannedCost', numeric: true }, // xhtml:313
  { label: 'Total Act Plus Plan Cost ($)', sortKey: 'totalCost', numeric: true }, // xhtml:334
  { label: 'Total/NAR(ha)', sortKey: 'costPerNetArea', numeric: true }, // xhtml:344
  { label: 'Comments', sortKey: null }, // xhtml:353 — no sortBy
]

// ASC -> DESC -> NONE, where NONE restores the server's order. Carbon's own DataTable cycles
// through the same three states; legacy PrimeFaces only toggled asc/desc, which left no way
// back to the document order the API returned.
const NEXT_DIRECTION: Record<SortDirection, SortDirection> = {
  NONE: 'ASC',
  ASC: 'DESC',
  DESC: 'NONE',
}

// Blank cells rank last in BOTH directions: a null carries no position of its own, so letting
// the direction flip it would push empty rows above real data on the descending pass. Booleans
// compare false < true (legacy sorted the raw enhancedIndicator, xhtml:255).
const compareRows = (
  a: SilvicultureLocation,
  b: SilvicultureLocation,
  key: SortKey,
  direction: SortDirection,
): number => {
  const left = a[key]
  const right = b[key]
  if (left === null || left === undefined) {
    return right === null || right === undefined ? 0 : 1
  }
  if (right === null || right === undefined) {
    return -1
  }
  const base =
    typeof left === 'string' && typeof right === 'string'
      ? left.localeCompare(right)
      : Number(left) - Number(right)
  return direction === 'DESC' ? -base : base
}

// Copy before sorting — sorting `data.locations` in place would mutate React state.
const sortLocations = (
  rows: readonly SilvicultureLocation[],
  key: SortKey | null,
  direction: SortDirection,
): readonly SilvicultureLocation[] =>
  key === null || direction === 'NONE'
    ? rows
    : [...rows].sort((a, b) => compareRows(a, b, key, direction))

const PAGE_HEADER = (
  <ScheduleTombstone title="Schedule 11" subtitle="Report Basic Silviculture Costs" />
)

// Schedule 11's load never 404s specially at the UI level: any ProblemDetail detail (ERR-001/002/003)
// renders verbatim (AC8); a network error with no detail falls back to a generic message.
const mapLoadError = (detail: string | undefined): string => detail ?? 'Unable to load Schedule 11.'

// The five fields that carry an original-value indicator, and how each compares (Story 16.2). Enhanced
// and Comments are absent on purpose — no submitted value exists for either (16.2 D5; 26.2 D3).
type TrackedField =
  'location' | 'biogeoclimaticCatalogueId' | 'netArea' | 'actualCost' | 'plannedCost'

const TRACKED_LABELS: Record<TrackedField, string> = {
  location: 'Location',
  biogeoclimaticCatalogueId: 'Biogeo/Subzone/Variant',
  netArea: 'NAR(ha)',
  actualCost: 'Actual Cost ($)',
  plannedCost: 'Planned Cost ($)',
}

// Client chrome for a blocked Save — the text five other pages already use for the same case.
const SAVE_BLOCKED = 'Please correct the highlighted fields before saving.'
// Why Check Status is greyed while changes are pending (Story 26.2 D7(a), deviation (C)): the check
// reads what is STORED, so running it over unsaved edits would describe a report nobody saved.
export const CHECK_NEEDS_SAVE = 'Save your changes before checking status'

const formFromRow = (row: SilvicultureLocation): LocationFormValues => ({
  location: row.location,
  enhanced: row.enhancedIndicator,
  // A row whose catalogue label is missing (dangling id — no FK in delivery) must NOT seed a phantom
  // selection: force a real re-pick instead of resubmitting the dangling id (BR-09).
  bec: row.becLabel === null ? null : { id: row.biogeoclimaticCatalogueId, label: row.becLabel },
  netArea: numStr(row.netArea),
  actualCost: numStr(row.actualCost),
  plannedCost: numStr(row.plannedCost),
  comments: row.comments ?? '',
})

// The value of each tracked field as the indicator compares it: the form's string for a live row,
// the served value for a read-only one.
const trackedValue = (form: LocationFormValues, field: TrackedField): string => {
  switch (field) {
    case 'biogeoclimaticCatalogueId':
      return form.bec === null ? '' : String(form.bec.id)
    default:
      return form[field]
  }
}

const Indicator: FC<{
  readonly row: SilvicultureLocation
  readonly field: TrackedField
  readonly form: LocationFormValues
}> = ({ row, field, form }) => (
  <OriginalValueIndicator
    originals={row.originalValues}
    field={field}
    current={trackedValue(form, field)}
    numeric={field === 'netArea' || field === 'actualCost' || field === 'plannedCost'}
    label={TRACKED_LABELS[field]}
  />
)

// A location row while the page is editable: every field a live input, as legacy's table was
// (schedule11.xhtml:204-374 — no per-row edit mode). Edits stay on the page until Save; the two
// server-derived cells (Total Cost, $/NAR) are display-only and refresh on the next save (AD-5).
// Each row's hidden labels name the row they sit in ("NAR(ha) for North Ridge"): unique on the page,
// where the Add panel carries the bare names, and a screen-reader user hears which row they are in.
// Keyed to the SERVED name so the label does not change under the user while they retype it.
const rowLabel = (label: string, row: SilvicultureLocation): string =>
  `${label} for ${row.location}`

type LocationRowProps = {
  readonly row: SilvicultureLocation
  readonly form: LocationFormValues
  readonly errors: SilvicultureErrors
  readonly saving: boolean
  readonly onFieldChange: <K extends keyof LocationFormValues>(
    key: K,
    value: LocationFormValues[K],
  ) => void
  readonly onDelete: () => void
}

const LocationRow: FC<LocationRowProps> = ({
  row,
  form,
  errors,
  saving,
  onFieldChange,
  onDelete,
}) => (
  <>
    <TableCell>
      <TextInput
        id={`edit-location-${row.locationId}`}
        labelText={rowLabel('Location', row)}
        hideLabel
        size="sm"
        maxLength={LOCATION_MAX_LENGTH}
        disabled={saving}
        value={form.location}
        onChange={(e) => onFieldChange('location', e.target.value)}
        invalid={Boolean(errors.location)}
        invalidText={errors.location}
      />
      <Indicator row={row} field="location" form={form} />
    </TableCell>
    <TableCell>
      <BiogeoComboBox
        id={`edit-bec-${row.locationId}`}
        label={rowLabel('Biogeo/Subzone/Variant', row)}
        hideLabel
        selected={form.bec}
        disabled={saving}
        invalidText={errors.bec}
        onSelect={(o) => onFieldChange('bec', o)}
      />
      <Indicator row={row} field="biogeoclimaticCatalogueId" form={form} />
    </TableCell>
    <TableCell>
      {/* Hidden label stays "Enhanced", not the "ES" header abbreviation — the accessible name is
          what a screen reader announces, and legacy names the field "Enhanced".

          NO INDICATOR HERE, DELIBERATELY (16.2 D5; Story 26.2 D3). Legacy drew a sixth indicator for
          this control, but its "original" was the CURRENT persisted value (Schedule11DAO.java:226),
          so the icon labelled the last-saved value "Original Submission Value" — untrue about what
          the Licensee submitted. The snapshot view carries no ENHANCED_IND, so no true original
          exists to show, and widening the view is delivery-schema DDL. The backend emits no key for
          it; an indicator bound to one would take the "added since submission" branch on every row
          of every submitted report. */}
      <EnhancedDropdown
        id={`edit-enhanced-${row.locationId}`}
        label={rowLabel('Enhanced', row)}
        hideLabel
        value={form.enhanced}
        disabled={saving}
        invalidText={errors.enhanced}
        onChange={(v) => onFieldChange('enhanced', v)}
      />
    </TableCell>
    <TableCell className="schedule-11__num">
      <TextInput
        id={`edit-net-area-${row.locationId}`}
        labelText={rowLabel('NAR(ha)', row)}
        hideLabel
        size="sm"
        inputMode="decimal"
        disabled={saving}
        value={form.netArea}
        onChange={(e) => onFieldChange('netArea', e.target.value)}
        invalid={Boolean(errors.netArea)}
        invalidText={errors.netArea}
      />
      <Indicator row={row} field="netArea" form={form} />
    </TableCell>
    <TableCell className="schedule-11__num">
      <TextInput
        id={`edit-actual-cost-${row.locationId}`}
        labelText={rowLabel('Actual Cost ($)', row)}
        hideLabel
        size="sm"
        inputMode="numeric"
        disabled={saving}
        value={form.actualCost}
        onChange={(e) => onFieldChange('actualCost', e.target.value)}
        invalid={Boolean(errors.actualCost)}
        invalidText={errors.actualCost}
      />
      <Indicator row={row} field="actualCost" form={form} />
    </TableCell>
    <TableCell className="schedule-11__num">
      <TextInput
        id={`edit-planned-cost-${row.locationId}`}
        labelText={rowLabel('Planned Cost ($)', row)}
        hideLabel
        size="sm"
        inputMode="numeric"
        disabled={saving}
        value={form.plannedCost}
        onChange={(e) => onFieldChange('plannedCost', e.target.value)}
        invalid={Boolean(errors.plannedCost)}
        invalidText={errors.plannedCost}
      />
      <Indicator row={row} field="plannedCost" form={form} />
    </TableCell>
    <TableCell className="schedule-11__num">{money(row.totalCost)}</TableCell>
    <TableCell className="schedule-11__num">{ratio(row.costPerNetArea)}</TableCell>
    <TableCell>
      {/* Legacy's table cell was a p:inputTextarea rows=3 (the character counter is Add-panel-only,
          matching legacy). No indicator: legacy declared none for Schedule 11 comments. */}
      <TextArea
        id={`edit-comments-${row.locationId}`}
        labelText={rowLabel('Comments', row)}
        hideLabel
        rows={3}
        maxLength={COMMENTS_MAX_LENGTH}
        disabled={saving}
        value={form.comments}
        onChange={(e) => onFieldChange('comments', e.target.value)}
      />
    </TableCell>
    <TableCell>
      <Button
        kind="danger--tertiary"
        size="sm"
        renderIcon={TrashCan}
        disabled={saving}
        onClick={onDelete}
      >
        Delete
      </Button>
    </TableCell>
  </>
)

// A location row while the page is read-only: formatted, server-computed values (AD-5). The
// original-value indicators render here too — legacy's were icons beside always-present, merely
// disabled inputs, so a read-only viewer saw them as well (Story 26.2 D2, schedule11.xhtml:214-325).
const DisplayRow: FC<{ readonly row: SilvicultureLocation }> = ({ row }) => {
  const form = formFromRow(row)
  return (
    <>
      <TableCell>
        {row.location}
        <Indicator row={row} field="location" form={form} />
      </TableCell>
      <TableCell>
        {row.becLabel ?? ''}
        <Indicator row={row} field="biogeoclimaticCatalogueId" form={form} />
      </TableCell>
      <TableCell>{row.enhancedIndicator ? 'Yes' : 'No'}</TableCell>
      <TableCell className="schedule-11__num">
        {area(row.netArea)}
        <Indicator row={row} field="netArea" form={form} />
      </TableCell>
      <TableCell className="schedule-11__num">
        {money(row.actualCost)}
        <Indicator row={row} field="actualCost" form={form} />
      </TableCell>
      <TableCell className="schedule-11__num">
        {money(row.plannedCost)}
        <Indicator row={row} field="plannedCost" form={form} />
      </TableCell>
      <TableCell className="schedule-11__num">{money(row.totalCost)}</TableCell>
      <TableCell className="schedule-11__num">{ratio(row.costPerNetArea)}</TableCell>
      <TableCell>{row.comments ?? ''}</TableCell>
    </>
  )
}

const NO_DELETES: ReadonlySet<number> = new Set()

const Schedule11: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  // The GET path's stale-response guard lives inside useScheduleDocument (its active flag); the
  // write/check handlers need their own: a response dispatched under one mill/year must never apply
  // after the context changes (the document it echoes belongs to the OLD context). Each handler
  // closes over its dispatch-time context; the ref always holds the current one.
  const contextRef = useRef({ millId, year })
  useEffect(() => {
    contextRef.current = { millId, year }
  }, [millId, year])
  const contextStillCurrent = () =>
    contextRef.current.millId === millId && contextRef.current.year === year

  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<Schedule11CheckStatusResponse | null>(null)

  const [addForm, setAddForm] = useState<LocationFormValues>(emptyForm)
  const [addErrors, setAddErrors] = useState<SilvicultureErrors>({})

  // Pending work, legacy's in-memory model (Schedule11MB.save()): the forms of rows the user has
  // edited, keyed by location id (an absent entry means "as served"), the revisionCount each was
  // edited AGAINST, and the rows flagged with Delete. All of it survives an Add and every refused
  // Save; only a successful Save or a context change clears it.
  const [rowForms, setRowForms] = useState<Record<number, LocationFormValues>>({})
  const [rowRevisions, setRowRevisions] = useState<Record<number, number>>({})
  const [rowErrors, setRowErrors] = useState<Record<number, SilvicultureErrors>>({})
  const [pendingDeletes, setPendingDeletes] = useState<ReadonlySet<number>>(NO_DELETES)

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

  // Presentation-only, so it deliberately survives saves: a re-sort after every save would yank the
  // row the user is working on back to document order.
  const [sortColumn, setSortColumn] = useState<SortKey | null>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('NONE')

  const handleSort = (key: SortKey) => {
    // A different column always starts fresh at ascending rather than inheriting the previous
    // column's direction.
    const next = key === sortColumn ? NEXT_DIRECTION[sortDirection] : 'ASC'
    setSortDirection(next)
    setSortColumn(next === 'NONE' ? null : key)
  }

  const clearPending = () => {
    setRowForms({})
    setRowRevisions({})
    setRowErrors({})
    setPendingDeletes(NO_DELETES)
  }

  // Clear all transient mutation + pending state whenever a fresh document loads (mill/year change),
  // so a context change can't carry one report's unsaved work onto another.
  const resetTransient = useCallback(() => {
    setSaving(false)
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
    setAddForm(emptyForm())
    setAddErrors({})
    setRowForms({})
    setRowRevisions({})
    setRowErrors({})
    setPendingDeletes(NO_DELETES)
    setConfirmDeleteId(null)
  }, [])

  const { data, setData, loadState } = useScheduleDocument<Schedule11Response>({
    path: SCHEDULE11_PATH,
    scheduleName: 'Schedule 11',
    header: PAGE_HEADER,
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

  const applyDocument = (doc: Schedule11Response) => {
    setData(doc)
    setMessage(doc.message?.text ?? null)
    setActionError(null)
    setCheckResult(null)
  }

  const buildBody = (
    form: LocationFormValues,
    revisionCount?: number,
  ): SilvicultureLocationRequest => ({
    location: form.location.trim(),
    // Validated non-null before this runs; the assertions only satisfy the required-field types.
    enhancedIndicator: form.enhanced as boolean,
    biogeoclimaticCatalogueId: (form.bec as BiogeoclimaticOption).id,
    // Parse with the SAME legacy DecimalFormat-faithful parser as validation.ts, not toNum/Number:
    // a grouped value like "1,000" that validateLocation accepts must serialize to 1000, not the
    // null Number("1,000") would yield (which would trip the backend's @NotNull netArea as a 400).
    netArea: parseDecimalInput(form.netArea) as number,
    actualCost: roundCost(parseDecimalInput(form.actualCost)),
    plannedCost: roundCost(parseDecimalInput(form.plannedCost)),
    comments: form.comments.trim() === '' ? null : form.comments,
    ...(revisionCount === undefined ? {} : { revisionCount }),
  })

  const setAddField = <K extends keyof LocationFormValues>(key: K, value: LocationFormValues[K]) =>
    setAddForm((prev) => ({ ...prev, [key]: value }))

  // The first edit of a row seeds its form from the row AND records the revision it was edited
  // against, so a later Add echo (which re-serves every row) cannot silently hand the save a newer
  // token than the one the user's edit was based on.
  const setRowField =
    (row: SilvicultureLocation) =>
    <K extends keyof LocationFormValues>(key: K, value: LocationFormValues[K]) => {
      setRowForms((prev) => ({
        ...prev,
        [row.locationId]: { ...(prev[row.locationId] ?? formFromRow(row)), [key]: value },
      }))
      setRowRevisions((prev) =>
        row.locationId in prev ? prev : { ...prev, [row.locationId]: row.revisionCount },
      )
      // A check verdict names fields by their value at the time; once the user edits, it is stale.
      setCheckResult(null)
    }

  const handleAdd = () => {
    if (!data || saving) {
      return
    }
    // Clear prior banners first so a validation failure never leaves a stale success/error notice.
    clearBanners()
    const errors = validateLocation(addForm)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .post<Schedule11Response>(`${SCHEDULE11_PATH}/locations${query}`, buildBody(addForm))
      .then((response) => {
        if (!contextStillCurrent()) {
          return
        }
        // Add persists only the new row (legacy addLocation() -> save(true)); the pending edits and
        // deletes stay pending over the refreshed document, keyed by the ids they already carry.
        applyDocument(response.data)
        setAddForm(emptyForm())
      })
      .catch((error: unknown) => {
        if (!contextStillCurrent()) {
          return
        }
        // Keep entered values for correction; surface the API's verbatim detail.
        setActionError(extractDetail(error) || 'Schedule could not be saved.')
      })
      // On a context change resetTransient already cleared `saving` — and a save dispatched under
      // the NEW context may be in flight, so a stale finally must not release its lock.
      .finally(() => {
        if (contextStillCurrent()) {
          setSaving(false)
        }
      })
  }

  // Delete FLAGS the row (legacy Schedule11MB.deleteLocation, :132-138) and takes it off the table;
  // nothing is written until Save. No message: legacy's "Data deleted successfully" was untrue at this
  // moment (Story 26.2 D6(b), deviation (B)).
  const flagDelete = () => {
    if (confirmDeleteId === null || saving) {
      return
    }
    const id = confirmDeleteId
    setConfirmDeleteId(null)
    clearBanners()
    setPendingDeletes((prev) => new Set(prev).add(id))
  }

  const handleSave = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    const kept = data.locations.filter((row) => !pendingDeletes.has(row.locationId))
    // Validate every row still on the page before anything is sent — legacy's JSF validated the whole
    // table on Save. The 7A/7B precedent: nothing is sent while one row fails.
    const errorsByRow: Record<number, SilvicultureErrors> = {}
    let firstFailing: number | null = null
    // In the order the user SEES, so the scroll lands on the topmost failing row under any sort.
    for (const row of sortLocations(kept, sortColumn, sortDirection)) {
      const errors = validateLocation(rowForms[row.locationId] ?? formFromRow(row))
      if (Object.keys(errors).length > 0) {
        errorsByRow[row.locationId] = errors
        firstFailing ??= row.locationId
      }
    }
    setRowErrors(errorsByRow)
    if (firstFailing !== null) {
      setActionError(SAVE_BLOCKED)
      document.getElementById(`schedule-11-row-${String(firstFailing)}`)?.scrollIntoView?.({
        block: 'center',
      })
      return
    }
    const body: LocationSaveAllRequest = {
      locations: kept.map((row) => ({
        basicSilvicultureReportId: row.locationId,
        location: buildBody(
          rowForms[row.locationId] ?? formFromRow(row),
          rowRevisions[row.locationId] ?? row.revisionCount,
        ),
      })),
      deletedIds: [...pendingDeletes],
    }
    setSaving(true)
    apiService
      .getAxiosInstance()
      .put<Schedule11Response>(`${SCHEDULE11_PATH}/locations${query}`, body)
      .then((response) => {
        if (!contextStillCurrent()) {
          return
        }
        applyDocument(response.data)
        clearPending()
      })
      .catch((error: unknown) => {
        if (!contextStillCurrent()) {
          return
        }
        // The pending work is KEPT: a refused save (a stale row, a clash) must never cost the user
        // the corrections they made — they fix the cause and save again.
        setActionError(extractDetail(error) || 'Schedule could not be saved.')
      })
      .finally(() => {
        if (contextStillCurrent()) {
          setSaving(false)
        }
      })
  }

  const handleCheckStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    // In-flight lock: rapid clicks must not issue concurrent POSTs, and a slow check result must
    // not interleave with (or resurrect state older than) a mutation — `saving` locks both ways.
    setSaving(true)
    // Read-only validation (BR-07) — mutates nothing. Disabled in read-only for S20/legacy parity.
    apiService
      .getAxiosInstance()
      .post<Schedule11CheckStatusResponse>(`${SCHEDULE11_PATH}/check-status${query}`)
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

  if (loadState) return loadState

  if (!data) {
    return null
  }

  const editable = data.editable
  const columnCount = editable ? 10 : 9
  const pending = Object.keys(rowForms).length > 0 || pendingDeletes.size > 0

  // Only the data rows are sorted; the Totals row is rendered after this list, so it stays pinned
  // to the bottom exactly as legacy's footer column group did (xhtml:377-412). A row flagged for
  // deletion leaves the table at once, as legacy's did; the server-computed Totals refresh on Save.
  const sortedLocations = sortLocations(data.locations, sortColumn, sortDirection).filter(
    (row) => !pendingDeletes.has(row.locationId),
  )

  const totals = data.totals

  const actions = (className: string) => (
    <SaveCheckActions
      className={className}
      saveDisabled={!editable || saving || !pending}
      checkDisabled={!editable || saving || pending}
      checkDisabledReason={editable && pending ? CHECK_NEEDS_SAVE : undefined}
      onSave={handleSave}
      onCheckStatus={handleCheckStatus}
    />
  )

  return (
    <div className="app-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        {/* No per-page mill/year/status summary: legacy's equivalent panel is commented out in
            schedule11.xhtml:40-54, and the app-wide ContextBanner (the modern #subMenu strip)
            already carries the working context on every page. */}
        {message && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
          </Column>
        )}
        {actionError && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              title="Action failed"
              subtitle={actionError}
            />
          </Column>
        )}
        {checkResult && (
          <Column sm={4} md={8} lg={16} className="schedule-11__check">
            {/* SUC-004 always; kind + a title word convey severity, not colour alone (NFR1). */}
            <InlineNotification
              kind="success"
              lowContrast
              title="Status checked"
              subtitle={checkResult.message.text}
            />
            {checkResult.requirementsMetMessage && (
              <InlineNotification
                kind="success"
                lowContrast
                title="Requirements met"
                subtitle={checkResult.requirementsMetMessage.text}
              />
            )}
            {checkResult.errors.map((error, index) => (
              <InlineNotification
                // FLD-004 entries can repeat verbatim (incl. the literal double space), so the list
                // index disambiguates otherwise-identical keys.
                key={`silv-check-error-${String(index)}-${error.key}`}
                kind="error"
                lowContrast
                title="Action required"
                subtitle={error.text}
              />
            ))}
          </Column>
        )}

        {editable && (
          <Column sm={4} md={8} lg={16} className="schedule-11__section">
            <h3 className="schedule-11__heading">Add New Location</h3>
            <div className="schedule-11__add">
              <div className="schedule-11__add-fields">
                <TextInput
                  id="add-location"
                  labelText="Location"
                  size="sm"
                  maxLength={LOCATION_MAX_LENGTH}
                  disabled={saving}
                  value={addForm.location}
                  onChange={(e) => setAddField('location', e.target.value)}
                  invalid={Boolean(addErrors.location)}
                  invalidText={addErrors.location}
                />
                <EnhancedDropdown
                  id="add-enhanced"
                  label="Enhanced"
                  value={addForm.enhanced}
                  disabled={saving}
                  invalidText={addErrors.enhanced}
                  onChange={(v) => setAddField('enhanced', v)}
                />
                <BiogeoComboBox
                  id="add-bec"
                  label="Biogeo/Subzone/Variant"
                  selected={addForm.bec}
                  disabled={saving}
                  invalidText={addErrors.bec}
                  onSelect={(o) => setAddField('bec', o)}
                />
                <TextInput
                  id="add-net-area"
                  labelText="NAR(ha)"
                  size="sm"
                  inputMode="decimal"
                  disabled={saving}
                  value={addForm.netArea}
                  onChange={(e) => setAddField('netArea', e.target.value)}
                  invalid={Boolean(addErrors.netArea)}
                  invalidText={addErrors.netArea}
                />
                <TextInput
                  id="add-actual-cost"
                  labelText="Actual Cost ($)"
                  size="sm"
                  inputMode="numeric"
                  disabled={saving}
                  value={addForm.actualCost}
                  onChange={(e) => setAddField('actualCost', e.target.value)}
                  invalid={Boolean(addErrors.actualCost)}
                  invalidText={addErrors.actualCost}
                />
                <TextInput
                  id="add-planned-cost"
                  labelText="Planned Cost ($)"
                  size="sm"
                  inputMode="numeric"
                  disabled={saving}
                  value={addForm.plannedCost}
                  onChange={(e) => setAddField('plannedCost', e.target.value)}
                  invalid={Boolean(addErrors.plannedCost)}
                  invalidText={addErrors.plannedCost}
                />
              </div>
              {/* Comments sits on its own row beneath the field row so the box gets the width
                  legacy gave it (cols="75", xhtml:140-141) instead of being squeezed into the
                  wrap flow beside the short numeric inputs. */}
              <div className="schedule-11__add-comments">
                <CommentsTextArea
                  id="add-comments"
                  labelText="Comments"
                  maxCount={COMMENTS_MAX_LENGTH}
                  disabled={saving}
                  value={addForm.comments}
                  onChange={(e) => setAddField('comments', e.target.value)}
                />
              </div>
              <Button kind="primary" renderIcon={Add} disabled={saving} onClick={handleAdd}>
                Add
              </Button>
            </div>
          </Column>
        )}

        {/* Save + Check Status above AND below the table, as legacy's two rows were
            (schedule11.xhtml:185-194, :420-429). */}
        {actions('schedule-11__actions schedule-11__actions--top')}

        <Column sm={4} md={8} lg={16} className="schedule-11__section">
          {/* Titled with the same h3 + class as "Add New Location" rather than TableContainer's
              `title` prop: Carbon renders that prop through its Section/Heading pair, which both
              sizes it at heading-03 AND picks its own level (h2), so the two section headings on
              this page disagreed on size and skipped a level. Same element + class = same size by
              construction. The table keeps its own accessible name via aria-label. */}
          <h3 className="schedule-11__heading">Silviculture Locations</h3>
          <TableContainer>
            <Table aria-label="Silviculture Locations">
              <TableHead>
                <TableRow>
                  {/* Order, header text and sortability all come from COLUMNS (legacy-verbatim).
                      The table says "ES" while the Add panel says "Enhanced" — that asymmetry is
                      legacy's too (xhtml:71 labels the form field "Enhanced"). */}
                  {COLUMNS.map(({ label, sortKey, numeric }) => {
                    const isSortHeader = sortKey !== null && sortKey === sortColumn
                    return (
                      <TableHeader
                        key={label}
                        className={numeric ? 'schedule-11__num' : undefined}
                        isSortable={sortKey !== null}
                        isSortHeader={isSortHeader}
                        // Carbon reads aria-sort off this, so an inactive column must report
                        // NONE rather than the active column's direction.
                        sortDirection={isSortHeader ? sortDirection : 'NONE'}
                        onClick={sortKey === null ? undefined : () => handleSort(sortKey)}
                      >
                        {label}
                      </TableHeader>
                    )
                  })}
                  {editable && <TableHeader>Actions</TableHeader>}
                </TableRow>
              </TableHead>
              <TableBody>
                {sortedLocations.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={columnCount}>
                      No silviculture locations have been added.
                    </TableCell>
                  </TableRow>
                ) : (
                  sortedLocations.map((row) => (
                    <TableRow key={row.locationId} id={`schedule-11-row-${String(row.locationId)}`}>
                      {editable ? (
                        <LocationRow
                          row={row}
                          form={rowForms[row.locationId] ?? formFromRow(row)}
                          errors={rowErrors[row.locationId] ?? {}}
                          saving={saving}
                          onFieldChange={setRowField(row)}
                          onDelete={() => setConfirmDeleteId(row.locationId)}
                        />
                      ) : (
                        <DisplayRow row={row} />
                      )}
                    </TableRow>
                  ))
                )}
                {/* Footer Totals (BR-08/CNT-001) — server-computed, null renders blank not 0. */}
                <TableRow className="schedule-11__totals">
                  <TableCell>Totals</TableCell>
                  <TableCell />
                  <TableCell />
                  <TableCell className="schedule-11__num">{area(totals.netArea)}</TableCell>
                  <TableCell className="schedule-11__num">{money(totals.actualCost)}</TableCell>
                  <TableCell className="schedule-11__num">{money(totals.plannedCost)}</TableCell>
                  <TableCell className="schedule-11__num">{money(totals.totalCost)}</TableCell>
                  <TableCell className="schedule-11__num">{ratio(totals.costPerNetArea)}</TableCell>
                  <TableCell />
                  {editable && <TableCell />}
                </TableRow>
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        {actions('schedule-11__actions schedule-11__actions--bottom')}
      </Grid>

      {editable && (
        <Modal
          open={confirmDeleteId !== null}
          danger
          modalHeading="Delete location"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteId(null)}
          onRequestSubmit={flagDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule11
