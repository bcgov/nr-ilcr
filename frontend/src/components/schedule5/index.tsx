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
import { useCallback, useState } from 'react'
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
import { extractDetail } from '@/utils/error'
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

const SCHEDULE5_PATH = '/v1/schedule5'
const CAMPS_PATH = `${SCHEDULE5_PATH}/camps`
const CHECK_STATUS_PATH = `${SCHEDULE5_PATH}/check-status`
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
}> = ({ inputId, label, value, readOnly, invalidText, onChange }) =>
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
  /** Present only on the two Other … rows, and only once the camp can be navigated to. */
  readonly onOpenSubPage?: () => void
}> = ({ row, values, served, subPageCount, readOnly, errors, onChange, onOpenSubPage }) => {
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
  readonly onOpenSubPage: (kind: SubPageKind) => void
}> = ({ values, served, readOnly, errors, onChange, onOpenSubPage }) => (
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
  readonly onCampVolumeChange: (value: string) => void
}> = ({ values, readOnly, errors, onFieldChange, onCampVolumeChange }) => (
  <div className="schedule-5__descriptors">
    <TextInput
      id="camp-name"
      labelText="Camp Name"
      maxLength={CAMP_NAME_MAX_LENGTH}
      value={values.campName}
      readOnly={readOnly}
      onChange={(event) => onFieldChange('campName', event.target.value)}
      invalid={Boolean(errors.campName)}
      invalidText={errors.campName}
    />
    <TextInput
      id="road-distance"
      labelText="Road Distance to Operating Area (km)"
      value={values.roadDistanceToOperatingArea}
      readOnly={readOnly}
      onChange={(event) => onFieldChange('roadDistanceToOperatingArea', event.target.value)}
      invalid={Boolean(errors.roadDistanceToOperatingArea)}
      invalidText={errors.roadDistanceToOperatingArea}
    />
    <TextInput
      id="size-of-camp"
      labelText="Size of Camp (number of persons)"
      value={values.sizeOfCamp}
      readOnly={readOnly}
      onChange={(event) => onFieldChange('sizeOfCamp', event.target.value)}
      invalid={Boolean(errors.sizeOfCamp)}
      invalidText={errors.sizeOfCamp}
    />
    <TextInput
      id="associated-camp-volume"
      labelText="Associated Camp Volume (m³)"
      value={values.associatedCampVolume}
      readOnly={readOnly}
      onChange={(event) => onCampVolumeChange(event.target.value)}
      invalid={Boolean(errors.associatedCampVolume)}
      invalidText={errors.associatedCampVolume}
    />
    <Select
      id="isolated-camp"
      labelText="Isolated Camp"
      value={values.isolatedCamp}
      disabled={readOnly}
      onChange={(event) => onFieldChange('isolatedCamp', event.target.value)}
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

  const [saving, setSaving] = useState(false)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [copyWarning, setCopyWarning] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<Schedule5CheckStatusResponse | null>(null)

  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [form, setForm] = useState<CampFormValues>(emptyForm)
  const [errors, setErrors] = useState<CampErrors>({})
  const [panelCampId, setPanelCampId] = useState<number | null>(null)
  const [panelRevision, setPanelRevision] = useState<number | null>(null)

  const [confirmDelete, setConfirmDelete] = useState<Camp | null>(null)
  const [confirmClose, setConfirmClose] = useState(false)
  const [pendingSwitch, setPendingSwitch] = useState<PendingSwitch | null>(null)
  /** Which sub-page a link asked for, held behind CFM-004 (new camp) or CFM-002 (existing). */
  const [pendingSubPage, setPendingSubPage] = useState<SubPageKind | null>(null)

  const clearBanners = () => {
    setActionMessage(null)
    setActionError(null)
    setCopyWarning(null)
    setCheckResult(null)
  }

  // A mill/year change abandons the open panel, its draft, every banner and the check verdict.
  const resetTransient = useCallback(() => {
    setActionMessage(null)
    setActionError(null)
    setCopyWarning(null)
    setCheckResult(null)
    setPanelMode('closed')
    setForm(emptyForm())
    setErrors({})
    setPanelCampId(null)
    setPanelRevision(null)
    setConfirmDelete(null)
    setConfirmClose(false)
    setPendingSwitch(null)
    setPendingSubPage(null)
    // Release the mutation lock: a request still in flight was dispatched under the OLD context and
    // its own guarded `finally` will deliberately skip the unlock, so without this every control
    // gated on `saving` stays dead in the new context until a remount.
    setSaving(false)
  }, [])

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
   * The single mutation tail. The context guard runs on `then`, `catch` AND `finally`: an unguarded
   * `finally` would release the `saving` lock belonging to a request dispatched under the NEW
   * context, letting two writes overlap.
   */
  const runMutation = <T,>(
    request: Promise<{ data: T }>,
    onSuccess: (payload: T) => void,
    fallbackError: string | null,
  ) => {
    setSaving(true)
    request
      .then((response) => {
        if (!isCurrent()) {
          return
        }
        onSuccess(response.data)
      })
      .catch((error: unknown) => {
        if (!isCurrent()) {
          return
        }
        // A null fallback fails SILENTLY (the copy resolve: a failed lookup leaves the banner
        // absent, never an invented sentence). Otherwise keep the panel open with every entered
        // value in place so a corrected save can retry.
        if (fallbackError !== null) {
          setActionError(extractDetail(error) || fallbackError)
        }
      })
      .finally(() => {
        if (isCurrent()) {
          setSaving(false)
        }
      })
  }

  const openEditOrView = (camp: Camp, mode: 'edit' | 'view') => {
    clearBanners()
    setPanelMode(mode)
    setForm(seedForm(camp, true))
    setErrors({})
    setPanelCampId(camp.campId)
    // THIS camp's own token, read from its row. A falsy 0 is a valid token — never coerce it.
    setPanelRevision(camp.revisionCount)
  }

  const openNew = () => {
    clearBanners()
    setPanelMode('new')
    setForm(emptyForm())
    setErrors({})
    setPanelCampId(null)
    setPanelRevision(null)
  }

  const openCopy = (camp: Camp) => {
    clearBanners()
    setPanelMode('copy')
    setForm(seedForm(camp, false))
    setErrors({})
    setPanelCampId(null)
    setPanelRevision(null)
    // WRN-001 is bundle text with no write behind it, so it is resolved over HTTP rather than
    // hardcoded (AD-8) — and under the same `saving` lock as every other action (AC14), so a second
    // Copy or a Save cannot race the resolve and land a banner naming the wrong camp over a newer
    // panel. The null fallback means a failed resolve leaves the banner absent rather than
    // substituting an invented sentence — the blank name in an obviously-new panel already carries
    // the instruction.
    runMutation(
      apiService.getAxiosInstance().get<MessageInfo>(MESSAGES_PATH, {
        params: { key: COPY_MESSAGE_KEY, arg: camp.campName ?? '' },
      }),
      (message) => setCopyWarning(message.text),
      null,
    )
  }

  const closePanel = () => {
    setPanelMode('closed')
    setForm(emptyForm())
    setErrors({})
    setPanelCampId(null)
    setPanelRevision(null)
    // The copy instruction belongs to the panel it opened with — closing must not leave "provide a
    // new Camp Name and invoke save" standing over a discarded draft. Other banners survive Close
    // on purpose (a delete echo outlives the panel it emptied).
    setCopyWarning(null)
  }

  const setField = (field: keyof CampFormValues, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
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
    setForm((prev) => {
      if (value.trim() !== '' && parseDecimalInput(value) === null) {
        return { ...prev, associatedCampVolume: value }
      }
      const categories = { ...prev.categories }
      for (const key of VOLUME_CATEGORY_KEYS) {
        categories[key] = { ...categories[key], volume: value }
      }
      return { ...prev, associatedCampVolume: value, categories }
    })
  }

  const handleCategoryChange = (key: CategoryKey, half: 'volume' | 'cost', value: string) => {
    setForm((prev) => ({
      ...prev,
      categories: { ...prev.categories, [key]: { ...prev.categories[key], [half]: value } },
    }))
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
      setErrors({})
    } else {
      closePanel()
    }
  }

  const handleSave = () => {
    if (saving || panelMode === 'closed' || panelMode === 'view') {
      return
    }
    clearBanners()
    const found = validateCamp(form)
    setErrors(found)
    if (!isCampFormValid(found)) {
      // A client rejection issues NO request; the entered values stay exactly as typed.
      return
    }
    const savedName = form.campName.trim()
    const isUpdate = panelMode === 'edit' && panelCampId !== null
    const body = buildRequest(form, isUpdate ? panelRevision : null)
    const axios = apiService.getAxiosInstance()
    runMutation(
      isUpdate
        ? axios.put<Schedule5Response>(`${CAMPS_PATH}/${String(panelCampId)}?${query}`, body)
        : axios.post<Schedule5Response>(`${CAMPS_PATH}?${query}`, body),
      (document) => applySaved(document, savedName, isUpdate ? panelCampId : null),
      'Camp could not be saved.',
    )
  }

  const handleDelete = () => {
    if (saving || !confirmDelete) {
      return
    }
    const target = confirmDelete
    setConfirmDelete(null)
    clearBanners()
    runMutation(
      apiService
        .getAxiosInstance()
        .delete<Schedule5Response>(`${CAMPS_PATH}/${String(target.campId)}?${query}`),
      (document) => {
        setData(document)
        setActionMessage(document.message?.text ?? null)
        // Deleting the camp the panel is showing leaves nothing to show.
        if (panelCampId === target.campId) {
          closePanel()
        }
      },
      'Unable to delete camp.',
    )
  }

  const handleCheckStatus = () => {
    if (saving) {
      return
    }
    clearBanners()
    runMutation(
      apiService
        .getAxiosInstance()
        .post<Schedule5CheckStatusResponse>(`${CHECK_STATUS_PATH}?${query}`),
      (result) => setCheckResult(result),
      'Unable to check status.',
    )
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
    const found = validateCamp(form)
    setErrors(found)
    if (!isCampFormValid(found)) {
      return
    }
    const savedName = form.campName.trim()
    const body = buildRequest(form, null)
    // The ids served BEFORE the save: the created camp is the one the echo carries that this set
    // does not. Matching by name instead is fragile — any server-side normalization of the name
    // breaks the lookup, and its miss fell back to a null id, a silent no-navigation after the
    // user confirmed a save-and-go.
    const knownIds = new Set((data?.camps ?? []).map((camp) => camp.campId))
    runMutation(
      apiService.getAxiosInstance().post<Schedule5Response>(`${CAMPS_PATH}?${query}`, body),
      (document) => {
        applySaved(document, savedName, null)
        const created = document.camps.find((camp) => !knownIds.has(camp.campId))
        // Name-match fallback for the degenerate case of an echo missing a new id entirely.
        const target =
          created?.campId ??
          document.camps.find((camp) => camp.campName === savedName)?.campId ??
          null
        openSubPage(kind, target)
      },
      'Camp could not be saved.',
    )
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
            panelOpen ? setPendingSwitch({ kind: 'edit', camp }) : openEditOrView(camp, 'edit')
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
        onCampVolumeChange={handleCampVolumeChange}
      />

      <CategoryGrid
        onOpenSubPage={requestSubPage}
        values={form}
        served={derivedSource}
        readOnly={readOnlyPanel}
        errors={errors}
        onChange={handleCategoryChange}
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
          onClick={() => (readOnlyPanel ? closePanel() : setConfirmClose(true))}
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
            onClick={() => (panelOpen ? setPendingSwitch({ kind: 'new' }) : openNew())}
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
