import type { FC } from 'react'
import type Schedule11Response from '@/interfaces/Schedule11Response'
import type {
  BiogeoclimaticOption,
  Schedule11CheckStatusResponse,
  SilvicultureLocation,
} from '@/interfaces/Schedule11Response'
import type SilvicultureLocationRequest from '@/interfaces/Schedule11Request'
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
import { Add, CheckmarkOutline, Edit, TrashCan } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { extractDetail } from '@/utils/error'
import { numStr, numStrFixed } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageState from '@/components/core/PageState'
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
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
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

// A location row in its inline-edit state: input controls bound to the edit form, with the two
// server-derived cells (Total Cost, $/NAR) shown read-only. Split out from the display rendering so
// each mode reads on its own and new fields can be added without growing one 100+ line function.
type EditRowProps = {
  readonly row: SilvicultureLocation
  readonly form: LocationFormValues
  readonly errors: SilvicultureErrors
  readonly saving: boolean
  readonly onFieldChange: <K extends keyof LocationFormValues>(
    key: K,
    value: LocationFormValues[K],
  ) => void
  readonly onSave: () => void
  readonly onCancel: () => void
}

const EditRow: FC<EditRowProps> = ({
  row,
  form,
  errors,
  saving,
  onFieldChange,
  onSave,
  onCancel,
}) => (
  <>
    <TableCell>
      <TextInput
        id={`edit-location-${row.locationId}`}
        labelText="Edit Location"
        hideLabel
        size="sm"
        maxLength={LOCATION_MAX_LENGTH}
        disabled={saving}
        value={form.location}
        onChange={(e) => onFieldChange('location', e.target.value)}
        invalid={Boolean(errors.location)}
        invalidText={errors.location}
      />
    </TableCell>
    <TableCell>
      <BiogeoComboBox
        id={`edit-bec-${row.locationId}`}
        label="Edit Biogeo/Subzone/Variant"
        hideLabel
        selected={form.bec}
        disabled={saving}
        invalidText={errors.bec}
        onSelect={(o) => onFieldChange('bec', o)}
      />
    </TableCell>
    <TableCell>
      {/* Hidden label stays "Enhanced", not the "ES" header abbreviation — the accessible name is
          what a screen reader announces for the control, and legacy names the field "Enhanced". */}
      <EnhancedDropdown
        id={`edit-enhanced-${row.locationId}`}
        label="Edit Enhanced"
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
        labelText="Edit NAR(ha)"
        hideLabel
        size="sm"
        inputMode="decimal"
        disabled={saving}
        value={form.netArea}
        onChange={(e) => onFieldChange('netArea', e.target.value)}
        invalid={Boolean(errors.netArea)}
        invalidText={errors.netArea}
      />
    </TableCell>
    <TableCell className="schedule-11__num">
      <TextInput
        id={`edit-actual-cost-${row.locationId}`}
        labelText="Edit Actual Cost ($)"
        hideLabel
        size="sm"
        inputMode="numeric"
        disabled={saving}
        value={form.actualCost}
        onChange={(e) => onFieldChange('actualCost', e.target.value)}
        invalid={Boolean(errors.actualCost)}
        invalidText={errors.actualCost}
      />
    </TableCell>
    <TableCell className="schedule-11__num">
      <TextInput
        id={`edit-planned-cost-${row.locationId}`}
        labelText="Edit Planned Cost ($)"
        hideLabel
        size="sm"
        inputMode="numeric"
        disabled={saving}
        value={form.plannedCost}
        onChange={(e) => onFieldChange('plannedCost', e.target.value)}
        invalid={Boolean(errors.plannedCost)}
        invalidText={errors.plannedCost}
      />
    </TableCell>
    {/* Total Cost + $/NAR are server-derived (AD-5); shown read-only, they refresh on re-save. */}
    <TableCell className="schedule-11__num">{money(row.totalCost)}</TableCell>
    <TableCell className="schedule-11__num">{ratio(row.costPerNetArea)}</TableCell>
    <TableCell>
      {/* Legacy's table cell was a p:inputTextarea rows=3 (the character counter is
          Add-panel-only, matching legacy). */}
      <TextArea
        id={`edit-comments-${row.locationId}`}
        labelText="Edit Comments"
        hideLabel
        rows={3}
        maxLength={COMMENTS_MAX_LENGTH}
        disabled={saving}
        value={form.comments}
        onChange={(e) => onFieldChange('comments', e.target.value)}
      />
    </TableCell>
    <TableCell>
      <Button kind="primary" size="sm" disabled={saving} onClick={onSave}>
        Save
      </Button>
      <Button kind="ghost" size="sm" disabled={saving} onClick={onCancel}>
        Cancel
      </Button>
    </TableCell>
  </>
)

// A location row in its read-only display state: formatted values (server-computed, AD-5) plus the
// per-row Edit/Delete actions, rendered only when the schedule is editable.
type DisplayRowProps = {
  readonly row: SilvicultureLocation
  readonly editable: boolean
  readonly actionsDisabled: boolean
  readonly onEdit: () => void
  readonly onDelete: () => void
}

const DisplayRow: FC<DisplayRowProps> = ({ row, editable, actionsDisabled, onEdit, onDelete }) => (
  <>
    <TableCell>{row.location}</TableCell>
    <TableCell>{row.becLabel ?? ''}</TableCell>
    <TableCell>{row.enhancedIndicator ? 'Yes' : 'No'}</TableCell>
    <TableCell className="schedule-11__num">{area(row.netArea)}</TableCell>
    <TableCell className="schedule-11__num">{money(row.actualCost)}</TableCell>
    <TableCell className="schedule-11__num">{money(row.plannedCost)}</TableCell>
    <TableCell className="schedule-11__num">{money(row.totalCost)}</TableCell>
    <TableCell className="schedule-11__num">{ratio(row.costPerNetArea)}</TableCell>
    <TableCell>{row.comments ?? ''}</TableCell>
    {editable && (
      <TableCell>
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Edit}
          disabled={actionsDisabled}
          onClick={onEdit}
        >
          Edit
        </Button>
        <Button
          kind="danger--ghost"
          size="sm"
          renderIcon={TrashCan}
          disabled={actionsDisabled}
          onClick={onDelete}
        >
          Delete
        </Button>
      </TableCell>
    )}
  </>
)

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

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editRevision, setEditRevision] = useState<number | null>(null)
  const [editForm, setEditForm] = useState<LocationFormValues>(emptyForm)
  const [editErrors, setEditErrors] = useState<SilvicultureErrors>({})

  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

  // Presentation-only, so it deliberately survives saves: a re-sort after every add/edit would
  // yank the row the user is working on back to document order.
  const [sortColumn, setSortColumn] = useState<SortKey | null>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('NONE')

  const handleSort = (key: SortKey) => {
    // A different column always starts fresh at ascending rather than inheriting the previous
    // column's direction.
    const next = key === sortColumn ? NEXT_DIRECTION[sortDirection] : 'ASC'
    setSortDirection(next)
    setSortColumn(next === 'NONE' ? null : key)
  }

  // Clear all transient mutation + add/edit state whenever a fresh document loads (mill/year change),
  // so a context change can't strand an open editor or a stale banner (Story 2.5 carryover patch).
  const resetTransient = useCallback(() => {
    setSaving(false)
    setMessage(null)
    setActionError(null)
    setCheckResult(null)
    setAddForm(emptyForm())
    setAddErrors({})
    setEditingId(null)
    setEditRevision(null)
    setEditForm(emptyForm())
    setEditErrors({})
    setConfirmDeleteId(null)
  }, [])

  const { data, setData, errorDetail, isLoading } = useScheduleDocument<Schedule11Response>({
    path: SCHEDULE11_PATH,
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
    // a grouped value like "1,000" that validateLocation now accepts must serialize to 1000, not the
    // null Number("1,000") would yield (which would trip the backend's @NotNull netArea as a 400).
    netArea: parseDecimalInput(form.netArea) as number,
    actualCost: roundCost(parseDecimalInput(form.actualCost)),
    plannedCost: roundCost(parseDecimalInput(form.plannedCost)),
    comments: form.comments.trim() === '' ? null : form.comments,
    ...(revisionCount === undefined ? {} : { revisionCount }),
  })

  const setAddField = <K extends keyof LocationFormValues>(key: K, value: LocationFormValues[K]) =>
    setAddForm((prev) => ({ ...prev, [key]: value }))
  const setEditField = <K extends keyof LocationFormValues>(key: K, value: LocationFormValues[K]) =>
    setEditForm((prev) => ({ ...prev, [key]: value }))

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
        applyDocument(response.data)
        // Inputs cleared only on success (add-is-save).
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

  const startEdit = (row: SilvicultureLocation) => {
    clearBanners()
    setEditingId(row.locationId)
    setEditRevision(row.revisionCount)
    setEditForm({
      location: row.location,
      enhanced: row.enhancedIndicator,
      // A row whose catalogue label is missing (dangling id — no FK in delivery) must NOT seed a
      // phantom selection: force a real re-pick instead of resubmitting the dangling id (BR-09).
      bec:
        row.becLabel === null ? null : { id: row.biogeoclimaticCatalogueId, label: row.becLabel },
      netArea: numStr(row.netArea),
      actualCost: numStr(row.actualCost),
      plannedCost: numStr(row.plannedCost),
      comments: row.comments ?? '',
    })
    setEditErrors({})
  }

  const cancelEdit = () => {
    setEditingId(null)
    setEditRevision(null)
    setEditForm(emptyForm())
    setEditErrors({})
  }

  const handleSaveEdit = () => {
    // revisionCount is read from the loaded row at startEdit — never hardcoded or coerced; an
    // unseeded token cannot reach the PUT (a coerced 0 could silently bypass the stale-edit check).
    if (editingId === null || editRevision === null || saving) {
      return
    }
    clearBanners()
    const errors = validateLocation(editForm)
    if (Object.keys(errors).length > 0) {
      setEditErrors(errors)
      return
    }
    setEditErrors({})
    setSaving(true)
    apiService
      .getAxiosInstance()
      .put<Schedule11Response>(
        `${SCHEDULE11_PATH}/locations/${editingId}${query}`,
        buildBody(editForm, editRevision),
      )
      .then((response) => {
        if (!contextStillCurrent()) {
          return
        }
        applyDocument(response.data)
        cancelEdit()
      })
      .catch((error: unknown) => {
        if (!contextStillCurrent()) {
          return
        }
        setActionError(extractDetail(error) || 'Schedule could not be saved.')
      })
      .finally(() => {
        if (contextStillCurrent()) {
          setSaving(false)
        }
      })
  }

  const handleDelete = () => {
    if (confirmDeleteId === null || saving) {
      return
    }
    const id = confirmDeleteId
    setConfirmDeleteId(null)
    setSaving(true)
    clearBanners()
    // Modern immediate-DELETE (shipped 25.2 contract), no revision token; the recomputed document is
    // echoed with the delete success message.
    apiService
      .getAxiosInstance()
      .delete<Schedule11Response>(`${SCHEDULE11_PATH}/locations/${id}${query}`)
      .then((response) => {
        if (contextStillCurrent()) {
          applyDocument(response.data)
        }
      })
      .catch((error: unknown) => {
        if (contextStillCurrent()) {
          setActionError(extractDetail(error) || 'Unable to delete location.')
        }
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
          <LoadingScreen label="Loading Schedule 11" />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={PAGE_HEADER}
        notification={{ kind: 'error', title: 'Unable to load Schedule 11', subtitle: errorDetail }}
      />
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable
  const columnCount = editable ? 10 : 9

  const rowCells = (row: SilvicultureLocation) =>
    editable && editingId === row.locationId ? (
      <EditRow
        row={row}
        form={editForm}
        errors={editErrors}
        saving={saving}
        onFieldChange={setEditField}
        onSave={handleSaveEdit}
        onCancel={cancelEdit}
      />
    ) : (
      <DisplayRow
        row={row}
        editable={editable}
        actionsDisabled={saving || editingId !== null}
        onEdit={() => startEdit(row)}
        onDelete={() => setConfirmDeleteId(row.locationId)}
      />
    )

  // Only the data rows are sorted; the Totals row is rendered after this list, so it stays
  // pinned to the bottom exactly as legacy's footer column group did (xhtml:377-412).
  const sortedLocations = sortLocations(data.locations, sortColumn, sortDirection)

  const totals = data.totals

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

        <Column sm={4} md={8} lg={16} className="schedule-11__actions">
          <Button
            kind="tertiary"
            renderIcon={CheckmarkOutline}
            disabled={!editable || saving}
            onClick={handleCheckStatus}
          >
            Check Status
          </Button>
        </Column>

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
              <Button
                kind="primary"
                renderIcon={Add}
                disabled={saving || editingId !== null}
                onClick={handleAdd}
              >
                Add
              </Button>
            </div>
          </Column>
        )}

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
                      The table says "ES" while the Add panel below says "Enhanced" — that
                      asymmetry is legacy's too (xhtml:71 labels the form field "Enhanced"). */}
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
                {data.locations.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={columnCount}>
                      No silviculture locations have been added.
                    </TableCell>
                  </TableRow>
                ) : (
                  sortedLocations.map((row) => (
                    <TableRow key={row.locationId}>{rowCells(row)}</TableRow>
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
      </Grid>

      {editable && (
        <Modal
          open={confirmDeleteId !== null}
          danger
          modalHeading="Delete location"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteId(null)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule11
