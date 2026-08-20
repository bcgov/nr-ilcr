import type { FC } from 'react'
import type Schedule5Response from '@/interfaces/Schedule5Response'
import type {
  Camp,
  CategoryAmount,
  MessageInfo,
  Schedule5CheckStatusResponse,
} from '@/interfaces/Schedule5Response'
import type CampRequest from '@/interfaces/Schedule5Request'
import type { CategoryEntry } from '@/interfaces/Schedule5Request'
import type { SubPageKind } from '@/interfaces/Schedule5SubPage'
import type { CampErrors, CampFormValues, CategoryKey, DerivedKey, GridRow } from './validation'
import { useCallback, useMemo, useState } from 'react'
import { getRouteApi } from '@tanstack/react-router'
import Schedule5SubPage from '@/components/schedule5SubPage'
import {
  Button,
  Column,
  Grid,
  Modal,
  Select,
  SelectItem,
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
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { useScheduleMutations } from '@/hooks/useScheduleMutations'
import { numStr, numStrGroup, parseDecimalInput, roundCost } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import {
  CAMP_NAME_MAX_LENGTH,
  COMMENTS_MAX_LENGTH,
  GRID_ROWS,
  VOLUME_CATEGORY_KEYS,
  emptyCategories,
  isCampFormValid,
  validateCamp,
} from './validation'
import { fmtCost, fmtCostPerVolume, fmtVolume } from './masks'
import './index.scss'

// Client-only chrome — every one of these is either confirm-dialog text or is rendered when NO
// request is issued. Every success/error/warning string comes from the API and renders verbatim
// (AD-8); the copy warning in particular is now resolved from the bundle over HTTP rather than
// hardcoded, which is why there is no copy literal here.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
// Legacy's p:dataTable (schedule5.xhtml:51) sets no emptyMessage, so PrimeFaces rendered its
// default — reproduced verbatim rather than inventing a placeholder.
const EMPTY_LIST = 'No records found.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'
// CFM-004. Hardcoded with the other three confirms rather than resolved through GET /v1/messages —
// Open question 2, settled by Scho on 2026-08-12: it is confirm chrome fired before any request,
// which § Chrome literals treats as client-owned, and consistency within the page beat consistency
// with the messages endpoint. The bundle key `confirmNavigationFromNewCamp` is therefore NOT added
// to CLIENT_RENDERABLE_KEYS, and MessageController is untouched by this story.
// The sub-page level is URL-driven (search: camp + sub), read through the route API exactly as
// Schedule 4 does (`schedule4/index.tsx:55`).
const scheduleRoute = getRouteApi('/schedule-5')

const CONFIRM_SAVE_NEW_CAMP =
  'The information for the New Camp must be saved before you can add other expenses. Would you like to save the information now?'
const CONFIRM_CAMP_SWITCH =
  'Any unsaved changes to the current camp report will be lost. Are you sure you would like to continue?'
const NEW_CAMP_HEADING = 'New Camp Details'
const COMMENTS_HEADING = 'If you have any additional comments, please enter them here.'
const SECTION_HEADING = 'Existing Camps'

// Base path for the shared mutations hook: camps writes go through its `/camps[/{id}]` suffix and
// Check Status through its default `/check-status` suffix, so those literals no longer live here.
const SCHEDULE5_PATH = '/v1/schedule5'
const MESSAGES_PATH = '/v1/messages'
const COPY_MESSAGE_KEY = 'sch5.copy.msg'

// Legacy display masks, transcribed from the JSF converters. Every value passed through these is
// server-computed and this only formats it (AD-5 — no recompute). Those converters return "" for a
// null value, so NULL RENDERS BLANK, never "0"/"0.00": a camp that never had a Recoveries cost must
// look different from one whose Recoveries cost is genuinely 0.
// Re-exported so the masks remain part of this module's public surface (Story 7.4 Task 7) while
// their definitions sit in ./masks — see that file for why the indirection exists.
export { mask, fmtVolume, fmtCost, fmtCostPerVolume } from './masks'

type PanelMode = 'closed' | 'new' | 'edit' | 'copy' | 'view'

/** A pending action held behind a confirm dialog. */
type PendingSwitch = { readonly kind: 'new' } | { readonly kind: 'edit'; readonly camp: Camp }

const emptyForm = (): CampFormValues => ({
  campName: '',
  roadDistanceToOperatingArea: '',
  sizeOfCamp: '',
  associatedCampVolume: '',
  isolatedCamp: '',
  comments: '',
  categories: emptyCategories(),
})

/**
 * Seed the panel from a stored camp. `keepName=false` is the copy path: legacy's copy constructor
 * clones every descriptor and category amount but sets `campName = null`
 * (`CampReportType.java:116-163`), forcing a new unique name before the save can succeed.
 */
const seedForm = (camp: Camp, keepName: boolean): CampFormValues => {
  const categories = emptyCategories()
  for (const row of GRID_ROWS) {
    if (row.kind !== 'category') {
      continue
    }
    const amount = camp[row.key]
    categories[row.key] = {
      volume: numStrGroup(amount?.volume),
      cost: numStrGroup(amount?.cost),
    }
  }
  return {
    campName: keepName ? (camp.campName ?? '') : '',
    roadDistanceToOperatingArea: numStrGroup(camp.roadDistanceToOperatingArea),
    // A whole count, never grouped on screen (legacy binds it through numberOfPersonsConverter).
    sizeOfCamp: numStr(camp.sizeOfCamp),
    associatedCampVolume: numStrGroup(camp.associatedCampVolume),
    // Tri-state: a stored null renders as "nothing selected" and blocks the save (FLD-001), rather
    // than silently defaulting to No.
    isolatedCamp:
      camp.isolatedCamp === null || camp.isolatedCamp === undefined
        ? ''
        : camp.isolatedCamp
          ? 'true'
          : 'false',
    comments: camp.comments ?? '',
    categories,
  }
}

/**
 * Build the write body. ALL TWELVE categories are always present: an omitted `CategoryEntry` clears
 * both halves server-side, so a body carrying only what the licensee touched would silently NULL
 * everything else. Each entry carries only the half that category actually stores — the two
 * `Other …` costs and the Recoveries volume are server-derived and are never sent.
 */
const buildRequest = (values: CampFormValues, revisionCount: number | null): CampRequest => {
  const entries = {} as Record<CategoryKey, CategoryEntry>
  for (const row of GRID_ROWS) {
    if (row.kind !== 'category') {
      continue
    }
    const raw = values.categories[row.key]
    entries[row.key] = {
      ...(row.hasVolume ? { volume: parseDecimalInput(raw.volume) } : {}),
      // Whole dollars: round half-away-from-zero before sending. A fractional cost reaching the API
      // is rejected outright, not truncated.
      ...(row.costBand ? { cost: roundCost(parseDecimalInput(raw.cost)) } : {}),
    }
  }
  return {
    campName: values.campName.trim(),
    roadDistanceToOperatingArea: parseDecimalInput(values.roadDistanceToOperatingArea),
    sizeOfCamp: parseDecimalInput(values.sizeOfCamp),
    associatedCampVolume: parseDecimalInput(values.associatedCampVolume),
    isolatedCamp: values.isolatedCamp === 'true',
    comments: values.comments.trim() === '' ? null : values.comments,
    ...entries,
    ...(revisionCount !== null ? { revisionCount } : {}),
  } as CampRequest
}

// ---- Module-level presentational pieces (props only, so they are not rebuilt per page render). ---

/** One editable numeric cell, or its value as read-only text. */
const AmountCell: FC<{
  readonly inputId: string
  readonly label: string
  readonly value: string
  readonly readOnly: boolean
  readonly invalidText?: string
  readonly onChange?: (value: string) => void
  readonly onBlur?: () => void
}> = ({ inputId, label, value, readOnly, invalidText, onChange, onBlur }) =>
  readOnly ? (
    <TableCell className="schedule-5__num">{value}</TableCell>
  ) : (
    <TableCell className="schedule-5__num">
      <TextInput
        id={inputId}
        labelText={label}
        hideLabel
        size="sm"
        value={value}
        onChange={(event) => onChange?.(event.target.value)}
        onBlur={onBlur}
        invalid={Boolean(invalidText)}
        invalidText={invalidText}
      />
    </TableCell>
  )

/** An empty cell for a column this row genuinely does not have (Recoveries' volume and $/m³). */
const AbsentCell: FC = () => <TableCell className="schedule-5__num" />

const CategoryGridRow: FC<{
  readonly row: Extract<GridRow, { kind: 'category' }>
  readonly values: { volume: string; cost: string }
  readonly served?: CategoryAmount
  readonly subPageCount: number
  readonly readOnly: boolean
  readonly errors: CampErrors
  readonly onChange: (key: CategoryKey, half: 'volume' | 'cost', value: string) => void
  readonly onBlur: (key: CategoryKey, half: 'volume' | 'cost') => void
  /** Present only on the two Other … rows, and only once the camp can be navigated to. */
  readonly onOpenSubPage?: () => void
}> = ({ row, values, served, subPageCount, readOnly, errors, onChange, onBlur, onOpenSubPage }) => {
  // The two Other … rows carry their live sub-page row count in the label itself.
  const label = row.subPageCount === undefined ? row.label : `${row.label} (${subPageCount}): `
  // The displayed label keeps legacy's trailing ": "; the accessible name drops it so a screen
  // reader announces "Catering and Food volume", not "Catering and Food: volume".
  const fieldName = row.label.replace(/:\s*$/, '')
  return (
    <TableRow>
      <TableCell className={row.indented ? 'schedule-5__label--indented' : undefined}>
        {/* The two Other … labels are the sub-page links (AC14). They keep the exact
            `Other Camp Expenses (n): ` text 7.3 shipped — only the element changes, from static
            text to a control — so the live count still reads the same. Rendered as a button even in
            read-only, because legacy's link navigates there too; only the CONFIRM differs. */}
        {onOpenSubPage === undefined ? (
          label
        ) : (
          <Button kind="ghost" size="sm" onClick={onOpenSubPage}>
            {label}
          </Button>
        )}
      </TableCell>
      {row.hasVolume ? (
        <AmountCell
          inputId={`${row.key}-volume`}
          label={`${fieldName} volume`}
          value={values.volume}
          readOnly={readOnly}
          invalidText={errors[`${row.key}.volume`]}
          onChange={(value) => onChange(row.key, 'volume', value)}
          onBlur={() => onBlur(row.key, 'volume')}
        />
      ) : (
        <AbsentCell />
      )}
      {row.costBand === undefined ? (
        // Server-derived (the sub-page row sum) — displayed, never entered.
        <TableCell className="schedule-5__num">{fmtCost(served?.cost)}</TableCell>
      ) : (
        <AmountCell
          inputId={`${row.key}-cost`}
          label={`${fieldName} cost`}
          value={values.cost}
          readOnly={readOnly}
          invalidText={errors[`${row.key}.cost`]}
          onChange={(value) => onChange(row.key, 'cost', value)}
          onBlur={() => onBlur(row.key, 'cost')}
        />
      )}
      {row.hasVolume ? (
        <TableCell className="schedule-5__num">{fmtCostPerVolume(served?.costPerVolume)}</TableCell>
      ) : (
        <AbsentCell />
      )}
    </TableRow>
  )
}

/** A wholly server-derived row — every cell read-only, nothing submitted (AD-5). */
const DerivedGridRow: FC<{
  readonly label: string
  readonly amount?: CategoryAmount
}> = ({ label, amount }) => (
  <TableRow>
    <TableCell>{label}</TableCell>
    <TableCell className="schedule-5__num">{fmtVolume(amount?.volume)}</TableCell>
    <TableCell className="schedule-5__num">{fmtCost(amount?.cost)}</TableCell>
    <TableCell className="schedule-5__num">{fmtCostPerVolume(amount?.costPerVolume)}</TableCell>
  </TableRow>
)

/**
 * The sixteen-row expense grid. GRID_ROWS is the single source of order, labels and bounds, and both
 * the add and the edit panel render through here — so the two cannot drift apart.
 */
const CategoryGrid: FC<{
  readonly values: CampFormValues
  readonly served?: Camp
  readonly readOnly: boolean
  readonly errors: CampErrors
  readonly onChange: (key: CategoryKey, half: 'volume' | 'cost', value: string) => void
  readonly onBlur: (key: CategoryKey, half: 'volume' | 'cost') => void
  readonly onOpenSubPage: (kind: SubPageKind) => void
}> = ({ values, served, readOnly, errors, onChange, onBlur, onOpenSubPage }) => (
  <TableContainer className="schedule-5__grid">
    <Table aria-label="Camp and access expenses">
      <TableBody>
        {GRID_ROWS.map((row) => {
          if (row.kind === 'section') {
            // Legacy repeats the four column headers on EVERY section row rather than heading the
            // table once (`schedule5ExistingCamp.xhtml:122-125`, `:275-278`, `:442-445`) — restored
            // per the review decision 2026-08-11. The editable cells stay screen-reader-addressable
            // through their own per-input labels.
            return (
              <TableRow key={`section-${row.label}`} className="schedule-5__section-row">
                <TableCell>{row.label}</TableCell>
                <TableCell className="schedule-5__num">Volume (m³)</TableCell>
                <TableCell className="schedule-5__num">Cost $</TableCell>
                <TableCell className="schedule-5__num">$/m³</TableCell>
              </TableRow>
            )
          }
          if (row.kind === 'group') {
            return (
              <TableRow key={`group-${row.label}`}>
                <TableCell colSpan={4}>{row.label}</TableCell>
              </TableRow>
            )
          }
          if (row.kind === 'derived') {
            return (
              <DerivedGridRow
                key={row.key}
                label={row.label}
                amount={served?.[row.key as DerivedKey]}
              />
            )
          }
          return (
            <CategoryGridRow
              key={row.key}
              row={row}
              values={values.categories[row.key]}
              served={served?.[row.key]}
              subPageCount={row.subPageCount === undefined ? 0 : (served?.[row.subPageCount] ?? 0)}
              readOnly={readOnly}
              errors={errors}
              onChange={onChange}
              onBlur={onBlur}
              onOpenSubPage={
                row.subPageCount === undefined
                  ? undefined
                  : () => {
                      onOpenSubPage(
                        row.subPageCount === 'otherCampExpenseCount' ? 'CAMP' : 'ACCESS',
                      )
                    }
              }
            />
          )
        })}
      </TableBody>
    </Table>
  </TableContainer>
)

/** The five descriptors, in legacy order and with legacy labels and unit suffixes. */
const DescriptorFields: FC<{
  readonly values: CampFormValues
  readonly readOnly: boolean
  readonly errors: CampErrors
  readonly onFieldChange: (field: keyof CampFormValues, value: string) => void
  readonly onFieldBlur: (field: keyof CampFormValues) => void
  readonly onIsolatedCampChange: (value: string) => void
  readonly onCampVolumeChange: (value: string) => void
}> = ({
  values,
  readOnly,
  errors,
  onFieldChange,
  onFieldBlur,
  onIsolatedCampChange,
  onCampVolumeChange,
}) => (
  <div className="schedule-5__descriptors">
    <TextInput
      id="camp-name"
      labelText="Camp Name"
      maxLength={CAMP_NAME_MAX_LENGTH}
      value={values.campName}
      readOnly={readOnly}
      onChange={(event) => onFieldChange('campName', event.target.value)}
      onBlur={() => onFieldBlur('campName')}
      invalid={Boolean(errors.campName)}
      invalidText={errors.campName}
    />
    <TextInput
      id="road-distance"
      labelText="Road Distance to Operating Area (km)"
      value={values.roadDistanceToOperatingArea}
      readOnly={readOnly}
      onChange={(event) => onFieldChange('roadDistanceToOperatingArea', event.target.value)}
      onBlur={() => onFieldBlur('roadDistanceToOperatingArea')}
      invalid={Boolean(errors.roadDistanceToOperatingArea)}
      invalidText={errors.roadDistanceToOperatingArea}
    />
    <TextInput
      id="size-of-camp"
      labelText="Size of Camp (number of persons)"
      value={values.sizeOfCamp}
      readOnly={readOnly}
      onChange={(event) => onFieldChange('sizeOfCamp', event.target.value)}
      onBlur={() => onFieldBlur('sizeOfCamp')}
      invalid={Boolean(errors.sizeOfCamp)}
      invalidText={errors.sizeOfCamp}
    />
    <TextInput
      id="associated-camp-volume"
      labelText="Associated Camp Volume (m³)"
      value={values.associatedCampVolume}
      readOnly={readOnly}
      onChange={(event) => onCampVolumeChange(event.target.value)}
      onBlur={() => onFieldBlur('associatedCampVolume')}
      invalid={Boolean(errors.associatedCampVolume)}
      invalidText={errors.associatedCampVolume}
    />
    <Select
      id="isolated-camp"
      labelText="Isolated Camp"
      value={values.isolatedCamp}
      disabled={readOnly}
      onChange={(event) => onIsolatedCampChange(event.target.value)}
      // A change IS this control's commit, so it reports immediately — but blur is still needed:
      // tabbing THROUGH the empty option fires no change at all, and without this the required
      // field would stay silent until Save while every text field beside it reports on blur.
      onBlur={() => onFieldBlur('isolatedCamp')}
      invalid={Boolean(errors.isolatedCamp)}
      invalidText={errors.isolatedCamp}
    >
      {/* The empty option exists so a stored null has something to render as. */}
      <SelectItem value="" text="" />
      <SelectItem value="false" text="No" />
      <SelectItem value="true" text="Yes" />
    </Select>
  </div>
)

const Schedule5: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()
  const navigate = scheduleRoute.useNavigate()
  // The sub-page level is a search param, not a second route (see routes/schedule-5.tsx).
  const { camp: subPageCampId, sub: subPageKind } = scheduleRoute.useSearch()

  // Save/delete/check-status all run through the shared hook's guarded run() (Story 29.6): the
  // request/error/lock scaffolding and the stale-response guard live in one place rather than
  // re-hand-rolled here. `saving` is the single in-flight lock for every write.
  const {
    saving,
    message: actionMessage,
    actionError,
    checkResult,
    setMessage: setActionMessage,
    setCheckResult,
    clearBanners: clearHookBanners,
    resetBanners: resetHookBanners,
    run,
    save,
    remove,
    checkStatus,
  } = useScheduleMutations<Schedule5CheckStatusResponse>({
    path: SCHEDULE5_PATH,
    millId,
    year,
    isCurrent,
  })

  // Page-specific banner (not one of the hook's four): the copy instruction WRN-001, tied to the
  // panel that opened it rather than to a write.
  const [copyWarning, setCopyWarning] = useState<string | null>(null)

  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [form, setForm] = useState<CampFormValues>(emptyForm)
  /**
   * Which fields are worth reporting on. Errors themselves are DERIVED at render from
   * `validateCamp` (the single rule source, the `schedule3/index.tsx:352` pattern); this set decides
   * only which of them are on screen.
   *
   * Keyed exactly as `CampErrors` is, category halves included (`cateringAndFood.volume`), so the
   * gate and the error map can never drift into two naming schemes.
   *
   * Legacy validated the camp panel only at submit, so reporting on blur is a deliberate deviation.
   */
  const [blurred, setBlurred] = useState<ReadonlySet<string>>(() => new Set())
  const [panelCampId, setPanelCampId] = useState<number | null>(null)
  const [panelRevision, setPanelRevision] = useState<number | null>(null)

  const [confirmDelete, setConfirmDelete] = useState<Camp | null>(null)
  const [confirmClose, setConfirmClose] = useState(false)
  const [pendingSwitch, setPendingSwitch] = useState<PendingSwitch | null>(null)
  /** Which sub-page a link asked for, held behind CFM-004 (new camp) or CFM-002 (existing). */
  const [pendingSubPage, setPendingSubPage] = useState<SubPageKind | null>(null)

  // The hook's clearBanners covers message/actionError/checkResult; the page adds its own copyWarning.
  const clearBanners = () => {
    clearHookBanners()
    setCopyWarning(null)
  }

  // A mill/year change abandons the open panel, its draft, every banner and the check verdict.
  const resetTransient = useCallback(() => {
    // resetBanners drops the three hook banners AND releases the saving lock (see below); copyWarning
    // is the page's own banner and is cleared alongside it.
    resetHookBanners()
    setCopyWarning(null)
    setPanelMode('closed')
    setForm(emptyForm())
    setBlurred(new Set())
    setPanelCampId(null)
    setPanelRevision(null)
    setConfirmDelete(null)
    setConfirmClose(false)
    setPendingSwitch(null)
    setPendingSubPage(null)
    // resetHookBanners() above also releases the mutation lock: a request still in flight was
    // dispatched under the OLD context and its own guarded `finally` deliberately skips the unlock,
    // so without this every control gated on `saving` stays dead in the new context until a remount.
  }, [resetHookBanners])

  const { data, setData, errorDetail, isLoading } = useScheduleDocument<Schedule5Response>({
    path: SCHEDULE5_PATH,
    millId,
    year,
    contextMissing,
    // Schedule 5's writable state is the on-demand camp panel, not a flat document form.
    seedForm: () => ({}),
    mapLoadError: (detail) => detail ?? 'Unable to load Schedule 5.',
    onReset: resetTransient,
  })

  const query = `millId=${String(millId)}&year=${String(year)}`

  /**
   * Every OTHER camp's name in the served mill/year — the client's half of BR-02.
   *
   * Excluded by campId, never by name, which is what makes the three panel modes come out right with
   * no mode-specific branching. An EDIT excludes the camp it is editing, so re-saving an unrenamed
   * camp cannot collide with itself — and a rename that duplicates a THIRD camp is still caught. A
   * NEW and a COPY both carry a null panelCampId, so every stored name collides; for a copy that is
   * exactly the rename WRN-001 asks for, now enforced before the request instead of by the 409
   * afterwards. And once `applySaved` re-seats the panel in edit mode, panelCampId is set, so the
   * second Save of a camp is not blocked by the row the first Save created.
   */
  const otherCampNames = useMemo(
    () =>
      (data?.camps ?? [])
        .filter((camp) => camp.campId !== panelCampId)
        .map((camp) => camp.campName)
        // `typeof name === 'string'`, NOT `name !== null`: `Camp` is serialised with
        // `@JsonInclude(NON_NULL)` (`Camp.java:37`), so a null CAMP_NAME is OMITTED from the JSON
        // rather than sent as null. The served value is then `undefined` — which the interface's
        // `string | null` does not describe — and a null-only guard would pass it through to
        // `name.toUpperCase()` in `isDuplicateName`, throwing during render.
        .filter((name): name is string => typeof name === 'string'),
    [data, panelCampId],
  )

  /**
   * What the panel would be discarding nothing against. DERIVED from the served document rather than
   * snapshotted into state, so there is nothing to keep in sync — and so "since the last save" falls
   * out for free: `applySaved` re-seeds the form from the saved camp, which means a successful save
   * lands the panel exactly on its own new baseline.
   *
   * `emptyForm()` for a NEW or COPIED camp, because neither exists server-side and there is no saved
   * state to compare with. A copy is therefore dirty from the moment it opens — it carries the source
   * camp's values against an empty baseline — which is exactly right: closing it discards a whole
   * camp the licensee asked to create. An empty new panel matches its baseline and closes silently.
   *
   * `null` means "cannot compare": either no panel, or an edited camp the served document no longer
   * carries (deleted in another session).
   */
  const panelBaseline = useMemo<CampFormValues | null>(() => {
    if (panelMode === 'closed') {
      return null
    }
    if (panelCampId === null) {
      return emptyForm()
    }
    const served = (data?.camps ?? []).find((camp) => camp.campId === panelCampId)
    return served === undefined ? null : seedForm(served, true)
  }, [data, panelMode, panelCampId])

  /**
   * Whether the panel holds anything not saved since the last save — the gate on all three "unsaved
   * data will be lost" confirms. Legacy warned on every one of those transitions regardless of state,
   * so gating them is a deliberate deviation.
   *
   * Compared as the ENTERED TEXT (`schedule8/index.tsx:490-493` does the same for its page editor).
   * Safe here because both sides are built by `seedForm`/`emptyForm` and every update spreads rather
   * than rebuilds, so key order is stable. Text rather than parsed values means retyping `120,000` as
   * `120000` counts as dirty though the number is unchanged: it over-warns only there and NEVER
   * under-warns, and legacy warned every time, so the over-warn is the legacy-faithful direction.
   * Schedule 5 has no blur-time re-grouping (its masks format only read-only served values), so
   * merely focusing and leaving a field cannot fake a change.
   *
   * A `view` panel is excluded explicitly rather than relying on its form matching its baseline, so
   * that a view panel whose camp went missing cannot start prompting. `view` is in any case reachable
   * only on a NON-editable document, where `Add New Camp` is disabled and the rows render `View`
   * alone with no `panelOpen` gate — so no switch confirm exists there to gate, and this flag reaches
   * only the Close button. An unprovable baseline is otherwise treated as dirty: a spurious confirm
   * costs a click, a missing one costs the licensee's work.
   */
  const panelDirty =
    panelMode !== 'closed' &&
    panelMode !== 'view' &&
    (panelBaseline === null || JSON.stringify(form) !== JSON.stringify(panelBaseline))

  const openEditOrView = (camp: Camp, mode: 'edit' | 'view') => {
    clearBanners()
    setPanelMode(mode)
    setForm(seedForm(camp, true))
    setBlurred(new Set())
    setPanelCampId(camp.campId)
    // THIS camp's own token, read from its row. A falsy 0 is a valid token — never coerce it.
    setPanelRevision(camp.revisionCount)
  }

  const openNew = () => {
    clearBanners()
    setPanelMode('new')
    setForm(emptyForm())
    setBlurred(new Set())
    setPanelCampId(null)
    setPanelRevision(null)
  }

  const openCopy = (camp: Camp) => {
    clearBanners()
    setPanelMode('copy')
    setForm(seedForm(camp, false))
    setBlurred(new Set())
    setPanelCampId(null)
    setPanelRevision(null)
    // WRN-001 is bundle text with no write behind it, so it is resolved over HTTP rather than
    // hardcoded (AD-8) — and under the same `saving` lock as every other action (AC14), so a second
    // Copy or a Save cannot race the resolve and land a banner naming the wrong camp over a newer
    // panel. The null fallback means a failed resolve leaves the banner absent rather than
    // substituting an invented sentence — the blank name in an obviously-new panel already carries
    // the instruction.
    // Raw run(): the resolve hits /v1/messages with a params object, not the /v1/schedule5 base +
    // mill/year query the hook's save/remove/checkStatus build — so it cannot go through a suffix.
    // It still shares the hook's guarded run() (same saving lock, same isCurrent guard). The null
    // fallback fails SILENTLY: a failed lookup leaves the banner absent rather than inventing text.
    run(
      apiService.getAxiosInstance().get<MessageInfo>(MESSAGES_PATH, {
        params: { key: COPY_MESSAGE_KEY, arg: camp.campName ?? '' },
      }),
      { fallback: null, onSuccess: (message) => setCopyWarning(message.text) },
    )
  }

  const closePanel = () => {
    setPanelMode('closed')
    setForm(emptyForm())
    setBlurred(new Set())
    setPanelCampId(null)
    setPanelRevision(null)
    // The copy instruction belongs to the panel it opened with — closing must not leave "provide a
    // new Camp Name and invoke save" standing over a discarded draft. Other banners survive Close
    // on purpose (a delete echo outlives the panel it emptied).
    setCopyWarning(null)
  }

  /** Blur is the commit point: a field's error appears only once the licensee has left it. */
  const markBlurred = (key: string) => {
    setBlurred((prev) => (prev.has(key) ? prev : new Set(prev).add(key)))
  }

  /**
   * Clear-on-type. Editing a reported field un-reports it, so the message goes while the licensee is
   * correcting it and returns on the next blur if the value is still wrong.
   *
   * This is also what keeps a rejected Save from freezing its errors on screen: Save reports every
   * offending field through the SAME set, so each message is then cleared by the very edit that
   * starts fixing it. A separate "show everything" flag would have suppressed that.
   */
  const clearBlurred = (key: string) => {
    setBlurred((prev) => {
      if (!prev.has(key)) {
        return prev
      }
      const next = new Set(prev)
      next.delete(key)
      return next
    })
  }

  const setField = (field: keyof CampFormValues, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
    clearBlurred(field)
  }

  /**
   * The Select's change IS its commit — there is no half-entered state to protect from flicker — so
   * choosing a value reports the field at once. Choosing the blank option therefore surfaces
   * "Isolated Camp is required." immediately, which is the point of a tri-state control whose empty
   * state is invalid.
   */
  const handleIsolatedCampChange = (value: string) => {
    setForm((prev) => ({ ...prev, isolatedCamp: value as CampFormValues['isolatedCamp'] }))
    markBlurred('isolatedCamp')
  }

  /**
   * BR-03. Changing the Associated Camp Volume assigns it to ALL ELEVEN category volumes
   * unconditionally, clobbering any per-category edit — legacy's `updateCampVolumes()`
   * (`Schedule5MB.java:248-261`) does exactly that, and only from this input's ajax listener.
   *
   * It deliberately does NOT re-run at save, so a category volume edited AFTER the last camp-volume
   * change is submitted as edited. The server stores what it is sent and never re-derives.
   *
   * The propagation is gated on a successful parse (review decision 2026-08-11): legacy's listener
   * ran only after BigDecimal CONVERSION succeeded, so an unparseable entry never reached the
   * categories — it stayed confined to this field with a single converter error, not twelve. A
   * blank DOES propagate: legacy converts an empty submit to null and clears all eleven.
   */
  const handleCampVolumeChange = (value: string) => {
    // Computed OUT here, not inside the updater: the updater must stay pure, and the same condition
    // decides both whether the eleven volumes change and whether they should be un-reported.
    const propagates = value.trim() === '' || parseDecimalInput(value) !== null
    setForm((prev) => {
      if (!propagates) {
        return { ...prev, associatedCampVolume: value }
      }
      const categories = { ...prev.categories }
      for (const key of VOLUME_CATEGORY_KEYS) {
        categories[key] = { ...categories[key], volume: value }
      }
      return { ...prev, associatedCampVolume: value, categories }
    })
    // Clear-on-type for this input, plus the eleven volumes BR-03 just overwrote. Those values were
    // not typed into those fields, and an out-of-range camp volume already reports itself at its own
    // input rather than as eleven duplicates of one message — the reasoning
    // `schedule5SubPage/validation.ts:53-55` uses for not flagging untouched rows. An unparseable
    // entry propagates nothing, so it must un-report nothing but itself.
    setBlurred((prev) => {
      const next = new Set(prev)
      next.delete('associatedCampVolume')
      if (propagates) {
        for (const key of VOLUME_CATEGORY_KEYS) {
          next.delete(`${key}.volume`)
        }
      }
      return next
    })
  }

  const handleCategoryChange = (key: CategoryKey, half: 'volume' | 'cost', value: string) => {
    setForm((prev) => ({
      ...prev,
      categories: { ...prev.categories, [key]: { ...prev.categories[key], [half]: value } },
    }))
    clearBlurred(`${key}.${half}`)
  }

  const handleCategoryBlur = (key: CategoryKey, half: 'volume' | 'cost') => {
    markBlurred(`${key}.${half}`)
  }

  /**
   * Apply a write echo: replace the document, render its message verbatim, and re-seat the panel on
   * the saved camp in edit mode.
   *
   * Legacy's `save()` leaves the panel open (`Schedule5MB.java:285-305`) and tracks `savedCampId` so
   * a second save UPDATES rather than inserting again. Re-seating reproduces that: the panel keeps
   * showing the camp, now with its freshly derived totals and a current revision token, and the next
   * Save is a PUT rather than a duplicate POST.
   */
  const applySaved = (document: Schedule5Response, savedName: string, savedId: number | null) => {
    setData(document)
    setActionMessage(document.message?.text ?? null)
    // An update re-seats by the id the PUT was sent to; only a create falls back to the (unique,
    // BR-02) name, because the echo carries no created-id marker to find the new row by.
    const saved = document.camps.find((camp) =>
      savedId !== null ? camp.campId === savedId : camp.campName === savedName,
    )
    if (saved) {
      setPanelMode('edit')
      setForm(seedForm(saved, true))
      setPanelCampId(saved.campId)
      setPanelRevision(saved.revisionCount)
      setBlurred(new Set())
    } else {
      closePanel()
    }
  }

  /**
   * Reveal every offending field — the one place blur is bypassed, because at Save the licensee has
   * asked about the whole form rather than one field.
   *
   * Minus the ones BR-03 merely COPIED. An out-of-range Associated Camp Volume propagates into all
   * eleven volume-bearing categories (`handleCampVolumeChange`), so revealing every erroring key puts
   * TWELVE copies of one mistake on screen.
   *
   * Scoped to categories still holding the propagated text, NOT to every `.volume` key: a category
   * the licensee edited afterwards differs, and that is their own error to see. Blanket-suppressing
   * volume errors would leave Save refusing with nothing on screen to explain why. Nothing stays
   * hidden either way — fixing the camp volume re-propagates over all eleven, and BR-03 clobbers
   * unconditionally, so the copies resolve with the field that caused them.
   */
  const revealErrors = (found: CampErrors) => {
    const propagated = new Set(
      found.associatedCampVolume === undefined
        ? []
        : VOLUME_CATEGORY_KEYS.filter(
            (key) => form.categories[key].volume === form.associatedCampVolume,
          ).map((key) => `${key}.volume`),
    )
    setBlurred(new Set(Object.keys(found).filter((key) => !propagated.has(key))))
  }

  const handleSave = () => {
    if (saving || panelMode === 'closed' || panelMode === 'view') {
      return
    }
    clearBanners()
    const found = validateCamp(form, otherCampNames)
    if (!isCampFormValid(found)) {
      // A client rejection issues NO request; the entered values stay exactly as typed.
      revealErrors(found)
      return
    }
    const savedName = form.campName.trim()
    const isUpdate = panelMode === 'edit' && panelCampId !== null
    const body = buildRequest(form, isUpdate ? panelRevision : null)
    // An update PUTs /v1/schedule5/camps/{id}; a new camp POSTs /v1/schedule5/camps. The suffix
    // reproduces each URL verbatim over the '/v1/schedule5' base, with the mill/year query the hook
    // appends.
    save<Schedule5Response>(body, {
      method: isUpdate ? 'put' : 'post',
      suffix: isUpdate ? `/camps/${String(panelCampId)}` : '/camps',
      fallback: 'Camp could not be saved.',
      onSuccess: (document) => applySaved(document, savedName, isUpdate ? panelCampId : null),
    })
  }

  const handleDelete = () => {
    if (saving || !confirmDelete) {
      return
    }
    const target = confirmDelete
    setConfirmDelete(null)
    clearBanners()
    remove<Schedule5Response>({
      suffix: `/camps/${String(target.campId)}`,
      fallback: 'Unable to delete camp.',
      onSuccess: (document) => {
        // A list page re-seeds from the reload rather than resetting to an empty read-only shape
        // (the per-page empty-state difference the hook documents at the call site).
        setData(document)
        setActionMessage(document.message?.text ?? null)
        // Deleting the camp the panel is showing leaves nothing to show.
        if (panelCampId === target.campId) {
          closePanel()
        }
      },
    })
  }

  const handleCheckStatus = () => {
    if (saving) {
      return
    }
    clearBanners()
    // The hook's default `/check-status` suffix over the '/v1/schedule5' base reproduces the
    // check-status URL verbatim.
    checkStatus<Schedule5CheckStatusResponse>({
      fallback: 'Unable to check status.',
      onSuccess: (result) => setCheckResult(result),
    })
  }

  /**
   * The sub-page confirm ladder (AC13/S05), `schedule4/index.tsx` as the shape.
   *
   * Three distinct behaviours, and the difference is whether the panel's camp EXISTS server-side:
   *
   * - **Unsaved camp (new OR copy)** → CFM-004. The camp does not exist yet, so there is nothing to
   *   navigate to; legacy's `goToOtherCampExpensesForNewCamp()` (`Schedule5MB.java:212-217`) saves
   *   first. A copy is an unsaved camp exactly like a new one (`panelCampId` is null on both) —
   *   routing it to CFM-002 instead would confirm a navigation `openSubPage` then silently drops.
   * - **Existing camp** → CFM-002 and navigate WITHOUT saving. `Schedule5MB.java:195-203` does no
   *   save at all here, so any unsaved panel edit is genuinely discarded — which is exactly what
   *   CFM-002 warns about.
   * - **Read-only** → navigate with no confirm. There is no unsaved data to lose.
   */
  const panelIsUnsavedCamp = panelMode === 'new' || panelMode === 'copy'

  const openSubPage = (kind: SubPageKind, campId: number | null) => {
    if (campId === null) {
      return
    }
    void navigate({ to: '/schedule-5', search: { camp: campId, sub: kind } })
  }

  const requestSubPage = (kind: SubPageKind) => {
    if (saving) {
      return
    }
    clearBanners()
    // Read-only navigates straight through: legacy renders a bare link there and there is no
    // unsaved data to warn about. `data.editable` is read directly rather than through the
    // `editable` const, which is not in scope until after the loading guards below.
    if (panelMode === 'view' || data?.editable !== true) {
      openSubPage(kind, panelCampId)
      return
    }
    // `pendingSubPage` drives TWO modals and only one of them warns about losing edits, so only one
    // is dirty-gated:
    //
    //   CFM-004 (unsaved new-or-copied camp) is not a warning at all — it is the ONLY route to a
    //   sub-page for a camp that does not exist server-side yet, which is why legacy saves first
    //   (`Schedule5MB.java:212-217`). It must fire even for a pristine, empty new panel.
    //
    //   CFM-002 (existing camp) is a genuine warning: legacy saves nothing here and discards the
    //   panel's edits outright (`:195-203`). With nothing entered there is nothing to discard.
    if (!panelIsUnsavedCamp && !panelDirty) {
      openSubPage(kind, panelCampId)
      return
    }
    setPendingSubPage(kind)
  }

  /**
   * CFM-004 Yes: save the unsaved (new or copied) camp, then navigate ONLY on success.
   *
   * Legacy dereferences `savedCampId.toString()` (`Schedule5MB.java:215`) with no null guard, so a
   * duplicate name or an ILCSException NPEs the page (deviation (J)). Here the error renders on the
   * camp panel with every entered value still in place, and no navigation happens.
   *
   * This fires only from the CFM-004 modal, which opens only for an UNSAVED camp — so the save is
   * always a POST; an existing camp's link goes through CFM-002 with no save at all.
   */
  const confirmSubPageSave = () => {
    const kind = pendingSubPage
    setPendingSubPage(null)
    if (kind === null || saving) {
      return
    }
    clearBanners()
    const found = validateCamp(form, otherCampNames)
    if (!isCampFormValid(found)) {
      revealErrors(found)
      return
    }
    const savedName = form.campName.trim()
    const body = buildRequest(form, null)
    // The ids served BEFORE the save: the created camp is the one the echo carries that this set
    // does not. Matching by name instead is fragile — any server-side normalization of the name
    // breaks the lookup, and its miss fell back to a null id, a silent no-navigation after the
    // user confirmed a save-and-go.
    const knownIds = new Set((data?.camps ?? []).map((camp) => camp.campId))
    // Always a POST to /v1/schedule5/camps: this ladder opens only for an UNSAVED (new or copied)
    // camp, so the save is a create.
    save<Schedule5Response>(body, {
      method: 'post',
      suffix: '/camps',
      fallback: 'Camp could not be saved.',
      onSuccess: (document) => {
        applySaved(document, savedName, null)
        const created = document.camps.find((camp) => !knownIds.has(camp.campId))
        // Name-match fallback for the degenerate case of an echo missing a new id entirely.
        const target =
          created?.campId ??
          document.camps.find((camp) => camp.campName === savedName)?.campId ??
          null
        openSubPage(kind, target)
      },
    })
  }

  /** CFM-002 Yes from an existing camp: navigate, discarding the panel edits. No save (`:195-203`). */
  const confirmSubPageNavigate = () => {
    const kind = pendingSubPage
    setPendingSubPage(null)
    if (kind !== null) {
      openSubPage(kind, panelCampId)
    }
  }

  const confirmSwitch = () => {
    if (!pendingSwitch) {
      return
    }
    const pending = pendingSwitch
    setPendingSwitch(null)
    if (pending.kind === 'new') {
      openNew()
    } else {
      openEditOrView(pending.camp, 'edit')
    }
  }

  const header = <ScheduleTombstone title="Schedule 5" subtitle="Camp and Access Expense" />

  // The expense sub-pages render INSTEAD of the camp list, as an early return driven by the search
  // params. Not a second route file: this keeps browser Back stepping from a sub-page to the list,
  // needs no nav entry (sub-pages are not in ROUTES), and reuses the already-loaded context.
  if (subPageCampId !== undefined && subPageKind !== undefined) {
    return (
      <Schedule5SubPage
        // Keyed on everything the sub-page's identity depends on, so switching camp, switching
        // list, or a mid-flight mill/year change REMOUNTS it with fresh state instead of leaving a
        // stale draft — or a stranded `saving` lock — behind.
        key={`${String(subPageCampId)}-${subPageKind}-${String(millId)}-${String(year)}`}
        campId={subPageCampId}
        kind={subPageKind}
        onBack={() => {
          // The camp document was loaded BEFORE the sub-page edits: its "(n)" link counts and
          // roll-up totals are stale the moment a row was added or deleted, and the load hook only
          // re-fetches on a mill/year change. Refresh alongside the navigation; a failed refresh
          // keeps the stale document rather than blanking a working page (a reload recovers).
          apiService
            .getAxiosInstance()
            .get<Schedule5Response>(`${SCHEDULE5_PATH}?${query}`)
            .then((response) => {
              if (isCurrent()) {
                setData(response.data)
              }
            })
            .catch(() => undefined)
          void navigate({ to: '/schedule-5', search: {} })
        }}
      />
    )
  }

  if (contextMissing) {
    return (
      <PageState
        header={header}
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
      <PageState header={header}>
        <Column sm={4} md={8} lg={16}>
          <LoadingScreen label="Loading Schedule 5" />
        </Column>
      </PageState>
    )
  }
  if (errorDetail) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Unable to load Schedule 5',
          subtitle: errorDetail,
        }}
      />
    )
  }
  if (!data) {
    return null
  }

  // Server-authoritative — never derived from trackStatus or the role (AD-9).
  const editable = data.editable
  const panelOpen = panelMode !== 'closed'
  const readOnlyPanel = panelMode === 'view'

  // Derived, never stored: `validateCamp` stays the single rule source and `blurred` filters it
  // (the `schedule3/index.tsx:352` pattern). A read-only or non-editable panel validates nothing, so
  // a stored value today's rules would reject is never flagged at a licensee who cannot fix it.
  const allErrors: CampErrors = editable && !readOnlyPanel ? validateCamp(form, otherCampNames) : {}
  const errors: CampErrors = Object.fromEntries(
    Object.entries(allErrors).filter(([key]) => blurred.has(key)),
  )

  const servedCamp =
    panelCampId !== null ? data.camps.find((camp) => camp.campId === panelCampId) : undefined
  // A new or copied camp has no served document yet, so it has no derived figures to show. Legacy's
  // copy constructor carries the SOURCE camp's totals across, which would present another camp's
  // figures as this one's and go stale the moment any amount is edited — and AD-5 forbids
  // recomputing them here. Left blank until the save echo brings the real ones back (deviation (O)).
  const derivedSource = panelMode === 'edit' || panelMode === 'view' ? servedCamp : undefined

  const rowActions = (camp: Camp) => {
    if (!editable) {
      // Legacy also renders a permanently-disabled Delete here (schedule5.xhtml:103-119); the epics
      // AC collapses the column to a single View and Schedule 6 set the same precedent, so the inert
      // control is dropped. Net user-reachable behaviour is identical (deviation (B)).
      return (
        <Button kind="ghost" size="sm" onClick={() => openEditOrView(camp, 'view')}>
          View
        </Button>
      )
    }
    return (
      <>
        <Button
          kind="ghost"
          size="sm"
          disabled={saving}
          onClick={() =>
            panelOpen && panelDirty
              ? setPendingSwitch({ kind: 'edit', camp })
              : openEditOrView(camp, 'edit')
          }
        >
          Edit
        </Button>
        <Button
          kind="danger--ghost"
          size="sm"
          disabled={saving}
          onClick={() => setConfirmDelete(camp)}
        >
          Delete
        </Button>
        {/* Legacy attaches no confirm to Copy in either editable column, so an open panel is
            replaced without one — copyCamp() calls addNewCamp() directly. */}
        <Button kind="ghost" size="sm" disabled={saving} onClick={() => openCopy(camp)}>
          Copy
        </Button>
      </>
    )
  }

  const campsTable = (
    <TableContainer title={SECTION_HEADING}>
      <Table aria-label={SECTION_HEADING}>
        <TableHead>
          <TableRow>
            <TableHeader>Camp Name</TableHeader>
            <TableHeader>Action</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.camps.length === 0 ? (
            <TableRow>
              <TableCell colSpan={2}>{EMPTY_LIST}</TableCell>
            </TableRow>
          ) : (
            data.camps.map((camp) => (
              <TableRow key={camp.campId}>
                <TableCell>{camp.campName}</TableCell>
                <TableCell>
                  <div className="schedule-5__row-actions">{rowActions(camp)}</div>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  )

  const panel = panelOpen && (
    <div className="schedule-5__panel">
      <h3 className="schedule-5__heading">
        {/* The existing-camp panel is headed by the camp's OWN name; the new-camp panel by the
            literal (schedule5.xhtml:150, :202). */}
        {panelMode === 'edit' || panelMode === 'view'
          ? (servedCamp?.campName ?? '')
          : NEW_CAMP_HEADING}
      </h3>

      <DescriptorFields
        values={form}
        readOnly={readOnlyPanel}
        errors={errors}
        onFieldChange={setField}
        onFieldBlur={markBlurred}
        onIsolatedCampChange={handleIsolatedCampChange}
        onCampVolumeChange={handleCampVolumeChange}
      />

      <CategoryGrid
        onOpenSubPage={requestSubPage}
        values={form}
        served={derivedSource}
        readOnly={readOnlyPanel}
        errors={errors}
        onChange={handleCategoryChange}
        onBlur={handleCategoryBlur}
      />

      <div className="schedule-5__comments">
        <h4 className="schedule-5__comments-heading">{COMMENTS_HEADING}</h4>
        <TextArea
          id="camp-comments"
          labelText="Comments"
          enableCounter
          maxCount={COMMENTS_MAX_LENGTH}
          value={form.comments}
          readOnly={readOnlyPanel}
          onChange={(event) => setField('comments', event.target.value)}
          onBlur={() => {
            markBlurred('comments')
          }}
          invalid={Boolean(errors.comments)}
          invalidText={errors.comments}
        />
      </div>

      <div className="schedule-5__panel-actions">
        {/* Legacy renders Save DISABLED in the read-only state rather than removing it (AC11,
            review decision 2026-08-11); Close stays enabled — it is the only way out of a View
            panel, the one place the AC's "everything disabled" cannot be taken literally. */}
        <Button kind="primary" disabled={!editable || readOnlyPanel || saving} onClick={handleSave}>
          Save
        </Button>
        <Button
          kind="secondary"
          disabled={saving}
          // The `readOnlyPanel` test this replaces is subsumed: a view panel is never dirty.
          onClick={() => (panelDirty ? setConfirmClose(true) : closePanel())}
        >
          Close
        </Button>
      </div>
    </div>
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {actionMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={actionMessage} />
        )}
        {actionError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
        )}
        {copyWarning && (
          <NotificationColumn kind="warning" title="Copy camp" subtitle={copyWarning} />
        )}
        {checkResult && (
          <>
            {/* On MET the schedule banner is emitted ALONE and camps is empty — no per-camp lines
                are synthesised. Severity follows the outcome, carried by both the kind and a title
                word, never colour alone — keyed off `outcome` rather than hardcoded, so a future
                schedule-level advisory arriving alongside ISSUES could never render under a green
                "requirements met" banner. */}
            {checkResult.messages.map((message) => (
              <NotificationColumn
                key={`schedule-${message.key}`}
                kind={checkResult.outcome === 'MET' ? 'success' : 'warning'}
                title={
                  checkResult.outcome === 'MET'
                    ? 'Check Status — requirements met'
                    : 'Check Status — value required'
                }
                subtitle={message.text}
              />
            ))}
            {checkResult.camps.map((camp) =>
              camp.messages.map((message) => (
                <NotificationColumn
                  key={`camp-${String(camp.campId)}-${message.key}-${message.field ?? ''}`}
                  kind={camp.requirementsMet ? 'success' : 'warning'}
                  title={
                    camp.requirementsMet
                      ? 'Check Status — requirements met'
                      : 'Check Status — value required'
                  }
                  subtitle={message.text}
                />
              )),
            )}
          </>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-5__actions">
          <Button
            kind="primary"
            disabled={!editable || saving}
            onClick={() =>
              panelOpen && panelDirty ? setPendingSwitch({ kind: 'new' }) : openNew()
            }
          >
            Add New Camp
          </Button>
          {/* Disabled when the schedule is not editable (legacy gates both Check Status buttons on
              disableReportEdits(), :44 and :257) and while a panel is open: legacy's button was a
              full postback, so JSF applied the entered values to the model BEFORE the check ran and
              the verdict always reflected the screen. The modern check reads only the database, so
              a verdict must never be shown that contradicts visible unsaved input. */}
          <Button
            kind="tertiary"
            disabled={!editable || saving || panelOpen}
            onClick={handleCheckStatus}
          >
            Check Status
          </Button>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-5__section">
          {campsTable}
        </Column>

        {panel && (
          <Column sm={4} md={8} lg={16} className="schedule-5__section">
            {panel}
          </Column>
        )}
      </Grid>

      <Modal
        open={confirmDelete !== null}
        danger
        modalHeading="Delete camp"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={() => setConfirmDelete(null)}
        onRequestSubmit={handleDelete}
      >
        <p>{CONFIRM_DELETE}</p>
      </Modal>

      {/* Legacy attaches its close confirm unconditionally (:169, :192, :221, :244) — there is no
          dirty check anywhere, so it fires whenever a panel is open (deviation (K)). */}
      <Modal
        open={confirmClose}
        modalHeading="Close camp report"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={() => setConfirmClose(false)}
        onRequestSubmit={() => {
          setConfirmClose(false)
          closePanel()
        }}
      >
        <p>{CONFIRM_NAVIGATION}</p>
      </Modal>

      {/* Legacy heads every confirm "Confirmation" (schedule5.xhtml:32); the three are given
          distinct headings so a screen reader can tell which dialog opened. The MESSAGES — the part
          the AC pins — are verbatim. */}
      <Modal
        open={pendingSwitch !== null}
        modalHeading="Switch camp report"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={() => setPendingSwitch(null)}
        onRequestSubmit={confirmSwitch}
      >
        <p>{CONFIRM_CAMP_SWITCH}</p>
      </Modal>

      {/* CFM-004 — the UNSAVED-camp ladder (new AND copy: both have no server-side camp yet). Yes
          saves and then navigates; a failed save renders on the panel and does NOT navigate
          (deviation (J), where legacy NPEs). */}
      <Modal
        open={pendingSubPage !== null && panelIsUnsavedCamp}
        modalHeading="Save camp report"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={() => {
          setPendingSubPage(null)
        }}
        onRequestSubmit={confirmSubPageSave}
      >
        {/* Rendered only while pending. Carbon keeps a closed modal's children mounted, and the
            CFM-002 twin below carries text the close-camp confirm already renders — leaving both in
            the DOM unconditionally would make "the dialog saying X" ambiguous for any query. */}
        {pendingSubPage !== null && <p>{CONFIRM_SAVE_NEW_CAMP}</p>}
      </Modal>

      {/* CFM-002 — from an EXISTING camp. Legacy issues no save here (Schedule5MB.java:195-203), so
          this genuinely discards the panel's unsaved edits, which is what the message warns about. */}
      <Modal
        open={pendingSubPage !== null && !panelIsUnsavedCamp}
        modalHeading="Leave camp report"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={() => {
          setPendingSubPage(null)
        }}
        onRequestSubmit={confirmSubPageNavigate}
      >
        {pendingSubPage !== null && <p>{CONFIRM_NAVIGATION}</p>}
      </Modal>
    </div>
  )
}

export default Schedule5
