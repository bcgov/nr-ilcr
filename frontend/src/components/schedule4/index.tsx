import type { FC } from 'react'
import type Schedule4Response from '@/interfaces/Schedule4Response'
import type { Location, Schedule4CheckStatusResponse } from '@/interfaces/Schedule4Response'
import type Schedule4LocationRequest from '@/interfaces/Schedule4Request'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Button,
  Column,
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
  TextInput,
} from '@carbon/react'
import CommentsTextArea from '@/components/core/CommentsTextArea'
import {
  Add,
  ArrowLeft,
  CheckmarkOutline,
  Close,
  Copy,
  Edit,
  Save,
  TrashCan,
  View,
} from '@carbon/icons-react'
import apiService from '@/service/api-service'
import { fmtCurrency, fmtNumber, numStr, toNum, groupInput } from '@/utils/number'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { useScheduleMutations } from '@/hooks/useScheduleMutations'
import { getRouteApi } from '@tanstack/react-router'
import LoadingScreen from '@/components/core/LoadingScreen'
import CommaNumberInput from '@/components/core/CommaNumberInput'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import ConfirmNavigationModal from '@/components/core/ConfirmNavigationModal'
import {
  ALL_CATEGORIES,
  isLocationFormValid,
  validateLocationForm,
  type CategoryForm,
} from './validation'
import { isUnusableEntry } from '@/utils/derivedMath'
import { deriveCategoryPerUnits } from './derived'
import SubPage from './SubPage'
import { SUB_PAGE_DEFS, type SubPageDef } from './subPageDefs'
import './index.scss'

// Client-only chrome (no request behind it), verbatim from the legacy bundle. All success/error text
// comes from the API `message.text` / ProblemDetail.detail — never hardcoded (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
// WRN-001, {0} = source location name.
const copyWarning = (name: string): string =>
  `To complete copy of Location: ${name}, provide a new Location Name and invoke save.`

// NAV-002 (leaving a saved location to a sub-page — unsaved edits discarded) and NAV-003 (leaving an
// unsaved NEW location — must save first). Client-only confirm chrome, verbatim from the bundle.
// Per-location comments cap (backend @Size(3500); the TRANSPORTATION_REPORT.COMMENTS column is 4000).
const COMMENTS_MAX = 3500

// Typed accessor for this page's route: the sub-page level is URL-driven (search: loc + sub) so the
// browser Back button returns from a sub-page to the location list.
const scheduleRoute = getRouteApi('/schedule-4')

const NAV_UNSAVED_LOST = 'Any unsaved data will be lost. Are you sure you would like to continue?'
const NAV_SAVE_FIRST =
  'The information for the New Location must be saved before you can add other Transportation. Would you like to save the information now?'

type PanelMode = 'closed' | 'new' | 'edit' | 'copy' | 'view'

const emptyCategoryForm = (): CategoryForm => {
  const form: CategoryForm = {}
  for (const def of ALL_CATEGORIES) {
    form[def.code] = { volume: '', cost: '', distance: '' }
  }
  return form
}

// Seed the category grid from a stored location's categories (present codes only). `keepName=false`
// (copy) clones amounts but not the name; per-category $/m³ is captured read-only for display.
function seedCategoryForm(location: Location): {
  form: CategoryForm
  perUnit: Record<number, number | null>
} {
  const form = emptyCategoryForm()
  const perUnit: Record<number, number | null> = {}
  for (const category of location.categories) {
    form[category.code] = {
      volume: numStr(category.volume),
      cost: numStr(category.cost),
      distance: numStr(category.distance),
    }
    perUnit[category.code] = category.perUnit
  }
  return { form, perUnit }
}

type CategoryDef = (typeof ALL_CATEGORIES)[number]
type CategoryField = 'volume' | 'cost' | 'distance'

// The category grid renders every transportation line in legacy code order (40–55): the 12 amount
// categories interleaved with the 3 list sub-page group rows (43 Towing, 46 Truck Rehaul, 55 Other).
// A sub-page row links to its own list page and shows a row count instead of amounts.
type GridEntry =
  | { kind: 'category'; code: number; def: CategoryDef }
  | { kind: 'subpage'; code: number; def: SubPageDef }

const GRID_ENTRIES: GridEntry[] = [
  ...ALL_CATEGORIES.map((def) => ({ kind: 'category' as const, code: def.code, def })),
  ...SUB_PAGE_DEFS.map((def) => ({ kind: 'subpage' as const, code: def.code, def })),
].sort((a, b) => a.code - b.code)

// A single category grid cell: an editable numeric input, or its value as read-only text in View
// mode. Module-level (only depends on its props) so it is not recreated on every page render.
const CategoryCell: FC<{
  inputId: string
  label: string
  value: string
  readOnly: boolean
  invalidText?: string
  onValueChange: (raw: string) => void
  onCommit: () => void
}> = ({ inputId, label, value, readOnly, invalidText, onValueChange, onCommit }) => {
  if (readOnly) {
    return (
      <TableCell className="schedule-4__num">{value === '' ? '—' : groupInput(value)}</TableCell>
    )
  }
  return (
    <TableCell className="schedule-4__num">
      <CommaNumberInput
        id={inputId}
        labelText={label}
        hideLabel
        size="sm"
        value={value}
        onValueChange={onValueChange}
        onBlur={onCommit}
        invalid={Boolean(invalidText)}
        invalidText={invalidText}
      />
    </TableCell>
  )
}

// One category row in legacy column order: Dist Km (distance categories only) / Volume / Cost inputs,
// read-only $/m³, and a Cycle Time placeholder (categories carry no cycle — only the Truck Rehaul
// sub-page rows do). Module-level so it is not recreated on every page render.
const CategoryRow: FC<{
  def: CategoryDef
  values: { volume: string; cost: string; distance: string }
  perUnit: number | null | undefined
  readOnly: boolean
  fieldErrors: Record<string, string>
  onFieldChange: (code: number, field: CategoryField) => (raw: string) => void
  onFieldCommit: (code: number, field: CategoryField) => () => void
}> = ({ def, values, perUnit, readOnly, fieldErrors, onFieldChange, onFieldCommit }) => {
  const isDistance = def.kind === 'DISTANCE'
  return (
    <TableRow>
      <TableCell>{def.label}:</TableCell>
      {isDistance ? (
        <CategoryCell
          inputId={`${def.code}-distance`}
          label={`${def.label} distance`}
          value={values.distance}
          readOnly={readOnly}
          invalidText={fieldErrors[`${def.code}-distance`]}
          onValueChange={onFieldChange(def.code, 'distance')}
          onCommit={onFieldCommit(def.code, 'distance')}
        />
      ) : (
        <TableCell className="schedule-4__num">—</TableCell>
      )}
      <CategoryCell
        inputId={`${def.code}-volume`}
        label={`${def.label} volume`}
        value={values.volume}
        readOnly={readOnly}
        invalidText={fieldErrors[`${def.code}-volume`]}
        onValueChange={onFieldChange(def.code, 'volume')}
        onCommit={onFieldCommit(def.code, 'volume')}
      />
      <CategoryCell
        inputId={`${def.code}-cost`}
        label={`${def.label} cost`}
        value={values.cost}
        readOnly={readOnly}
        invalidText={fieldErrors[`${def.code}-cost`]}
        onValueChange={onFieldChange(def.code, 'cost')}
        onCommit={onFieldCommit(def.code, 'cost')}
      />
      <TableCell className="schedule-4__num">{fmtCurrency(perUnit)}</TableCell>
      <TableCell className="schedule-4__num">—</TableCell>
    </TableRow>
  )
}

const Schedule4: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  // Save (PUT) / delete / check-status all run through the shared hook's guarded run() (Story 29.6):
  // a stale in-flight write can no longer repaint a newly-switched mill/year. `saving` is the single
  // in-flight lock for every write (it also gates Check Status).
  const {
    saving,
    message: saveMessage,
    actionError: saveError,
    checkResult,
    setMessage: setSaveMessage,
    setActionError: setSaveError,
    setCheckResult,
    clearBanners,
    resetBanners,
    save,
    checkStatus,
    run,
  } = useScheduleMutations<Schedule4CheckStatusResponse>({
    path: '/v1/schedule4',
    millId,
    year,
    isCurrent,
  })

  // Sub-page level from the URL (loc = location id, sub = sub-page type); navigate updates it.
  const search = scheduleRoute.useSearch()
  const navigate = scheduleRoute.useNavigate()

  // Clear stale location/sub-page URL params when the mill/year context actually switches (Comment 3).
  // Guarded by a ref so it fires only on a real mill/year change — never on in-app sub-navigation,
  // which legitimately sets loc/sub (otherwise every drill-down would reset itself).
  const contextKey = `${String(millId)}:${String(year)}`
  const contextKeyRef = useRef(contextKey)
  useEffect(() => {
    if (contextKeyRef.current !== contextKey) {
      contextKeyRef.current = contextKey
      if (search.loc !== undefined || search.sub !== undefined) {
        void navigate({ to: '/schedule-4', search: {}, replace: true })
      }
    }
  }, [contextKey, navigate, search.loc, search.sub])

  // Copy nudge (WRN-001) is a page-local warning banner with no request behind it, so it stays here
  // rather than in the shared mutations hook (which owns success/error/check banners).
  const [warnMessage, setWarnMessage] = useState<string | null>(null)

  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [panelName, setPanelName] = useState('')
  const [panelCategories, setPanelCategories] = useState<CategoryForm>(() => emptyCategoryForm())
  // The $/m³ captured from the server when the panel opened. Still the source in VIEW mode, where
  // there is no entry to track (defect #291 AC7); the editable modes read the mirror below instead.
  const [panelPerUnit, setPanelPerUnit] = useState<Record<number, number | null>>({})
  // The blur-committed copy of the category grid that feeds the mirror. Legacy recalculated when focus
  // left the field, so `panelCategories` keeps every keystroke (it drives the inputs) and this only
  // advances on blur — and on save dispatch, since what was sent is by definition committed.
  const [panelCommitted, setPanelCommitted] = useState<CategoryForm>(() => emptyCategoryForm())
  const [panelEditId, setPanelEditId] = useState<number | null>(null)
  const [panelRevision, setPanelRevision] = useState<number | null>(null)
  const [panelComments, setPanelComments] = useState('')
  const [confirmDelete, setConfirmDelete] = useState<Location | null>(null)
  // Pending sub-page open awaiting a NAV-002 (existing) / NAV-003 (new, save-first) confirm.
  const [navConfirm, setNavConfirm] = useState<{
    kind: 'existing' | 'new'
    def: SubPageDef
  } | null>(null)

  // Clear the transient mutation notifications + close the panel whenever a fresh document loads
  // (mill/year change), mirroring Schedule 9's resetTransient: resetBanners() drops the hook-owned
  // banners + lock, then we clear the page-local warn banner and reset panel state.
  const resetTransient = useCallback(() => {
    resetBanners()
    setWarnMessage(null)
    setPanelMode('closed')
  }, [resetBanners])

  // Shared load-on-context-change concern (Schedule 1/2 idiom): owns data/errorDetail/isLoading,
  // resets on mill/year change, and ignores a stale response. Schedule 4's writable state is the
  // on-demand location panel (not a flat form), so seedForm is unused here.
  const { data, setData, errorDetail, isLoading } = useScheduleDocument<Schedule4Response>({
    path: '/v1/schedule4',
    millId,
    year,
    contextMissing,
    seedForm: () => ({}),
    mapLoadError: (detail) => detail ?? 'Unable to load Schedule 4.',
    onReset: resetTransient,
  })

  // Drop every banner before an action: the hook-owned success/error/check banners plus the
  // page-local copy nudge.
  const clearMessages = () => {
    clearBanners()
    setWarnMessage(null)
  }

  const openNew = () => {
    clearMessages()
    setPanelMode('new')
    setPanelName('')
    setPanelCategories(emptyCategoryForm())
    setPanelCommitted(emptyCategoryForm())
    setPanelPerUnit({})
    setPanelEditId(null)
    setPanelRevision(null)
    setPanelComments('')
  }

  const openEditOrView = (location: Location, mode: 'edit' | 'view') => {
    clearMessages()
    const seeded = seedCategoryForm(location)
    setPanelMode(mode)
    setPanelName(location.name)
    setPanelCategories(seeded.form)
    setPanelCommitted(seeded.form)
    setPanelPerUnit(seeded.perUnit)
    setPanelEditId(location.id)
    setPanelRevision(location.revisionCount)
    setPanelComments(location.comments ?? '')
  }

  const openCopy = (location: Location) => {
    clearMessages()
    const seeded = seedCategoryForm(location)
    setPanelMode('copy')
    setPanelName('') // name cleared — a copy must be given a new unique name (WRN-001)
    setPanelCategories(seeded.form)
    // A copy clones the amounts, so their $/m³ is known immediately — the mirror shows it without
    // waiting for a save, where `panelPerUnit` would have left the column blank.
    setPanelCommitted(seeded.form)
    setPanelPerUnit({})
    setPanelEditId(null)
    setPanelRevision(null)
    setPanelComments(location.comments ?? '') // copy clones the comments (not the name)
    setWarnMessage(copyWarning(location.name))
  }

  const closePanel = () => setPanelMode('closed')

  const setCategoryField =
    (code: number, field: 'volume' | 'cost' | 'distance') => (value: string) => {
      // value is already the raw digit string (CommaNumberInput strips its display grouping).
      setPanelCategories((prev) => ({
        ...prev,
        [code]: { ...(prev[code] ?? { volume: '', cost: '', distance: '' }), [field]: value },
      }))
    }

  // Commit one category field (its `onBlur`), advancing the mirror's baseline for that field only.
  // An invalid or unusable entry holds its previous committed value rather than driving the $/m³ from
  // something the server would refuse (ruled 2026-08-21 after code review).
  const commitCategoryField = (code: number, field: 'volume' | 'cost' | 'distance') => () => {
    // Validated here rather than read from `fieldErrors`, which is computed further down (after the
    // early returns); same source of truth, and it only runs on blur.
    const invalid = Boolean(
      validateLocationForm(panelName, panelCategories).fieldErrors[`${code}-${field}`],
    )
    setPanelCommitted((prev) => {
      const live = panelCategories[code] ?? { volume: '', cost: '', distance: '' }
      const committed = prev[code] ?? { volume: '', cost: '', distance: '' }
      if (invalid || isUnusableEntry(live[field])) {
        return prev
      }
      if (committed[field] === live[field]) {
        return prev // tabbing through an untouched field must not re-render the grid
      }
      return { ...prev, [code]: { ...committed, [field]: live[field] } }
    })
  }

  const buildRequest = (): Schedule4LocationRequest => ({
    id: panelMode === 'edit' ? panelEditId : null,
    revisionCount: panelMode === 'edit' ? (panelRevision ?? 0) : null,
    name: panelName.trim(),
    comments: panelComments.trim() || null,
    categories: ALL_CATEGORIES.flatMap((def) => {
      const value = panelCategories[def.code] ?? { volume: '', cost: '', distance: '' }
      const isDistance = def.kind === 'DISTANCE'
      const anyPresent =
        value.volume.trim() !== '' ||
        value.cost.trim() !== '' ||
        (isDistance && value.distance.trim() !== '')
      if (!anyPresent) return []
      return [
        {
          code: def.code,
          volume: toNum(value.volume),
          cost: toNum(value.cost),
          distance: isDistance ? toNum(value.distance) : null,
        },
      ]
    }),
  })

  // Validate + PUT the location panel (create or edit) through the shared hook's guarded save(). The
  // by-id write is expressed by the body's id/revisionCount (not a path segment), so the URL is just
  // the fixed `/locations` suffix. Applies the echoed document on success, then hands it to the
  // caller's `afterSave` (re-open the saved record / open a sub-page) — all inside the guarded
  // onSuccess so a stale response can no longer run those side effects. Validation failure / API
  // error keeps the panel open with its entered values (handled by the hook's error banner).
  const putLocation = (afterSave: (doc: Schedule4Response) => void): void => {
    const validation = validateLocationForm(panelName, panelCategories)
    if (!isLocationFormValid(validation)) {
      // Generic banner; the specific verbatim messages (ERR-001, ranges, BR-04) show inline on the
      // fields so they are not duplicated.
      setSaveMessage(null)
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    clearMessages()
    // What is being sent is committed by definition — this also covers a Save clicked from a field
    // whose blur has not landed yet.
    setPanelCommitted(panelCategories)
    save<Schedule4Response>(buildRequest(), {
      suffix: '/locations',
      fallback: 'Schedule could not be saved.',
      onSuccess: (doc) => {
        setData(doc)
        setSaveMessage(doc.message?.text ?? null)
        afterSave(doc)
      },
    })
  }

  const handleSave = () => {
    if (saving || panelMode === 'closed' || panelMode === 'view') return
    // Snapshot the dispatch-time panel context: onSuccess resolves later, so read edit-vs-create and
    // the entered name off these rather than the (possibly changed) live state.
    const wasEdit = panelMode === 'edit'
    const editId = panelEditId
    const prevIds = new Set(data?.locations.map((l) => l.id) ?? [])
    putLocation((document) => {
      // Stay on the saved record (don't close): re-open it in edit mode — found by id when editing, by
      // (unique) name after a new/copy create — refreshing the optimistic-lock token so a follow-up
      // save doesn't 409.
      const saved =
        wasEdit && editId !== null
          ? document.locations.find((l) => l.id === editId)
          : document.locations.find((l) => l.id != null && !prevIds.has(l.id))
      if (saved && saved.id != null) {
        setPanelMode('edit')
        setPanelEditId(saved.id)
        setPanelRevision(saved.revisionCount ?? 0)
        // Re-seed from the ECHO, not from the retained form. AD-5's amendment requires the server echo
        // to supersede the mirror on every Save; without this the panel kept rendering
        // `deriveCategoryPerUnits(panelCommitted)` for the rest of the session, so a category whose
        // rate the mirror rounded differently would show one figure in the panel and another in the
        // list row beneath it (code review 2026-08-21).
        const echoed = seedCategoryForm(saved)
        setPanelCategories(echoed.form)
        setPanelCommitted(echoed.form)
        setPanelPerUnit(echoed.perUnit)
      } else {
        setPanelMode('closed')
      }
    })
  }

  const handleDelete = () => {
    if (saving || !confirmDelete) return
    const target = confirmDelete
    setConfirmDelete(null)
    clearMessages()
    // Delete returns only a message; the DELETE runs through the hook's guarded run() so a stale
    // response can't repaint a switched context. The target id is a query param (not a path segment),
    // which the hook's fixed `?millId&year` query can't express, so the request is built here with the
    // verbatim URL (AC7: URL unchanged). onSuccess then re-GETs the document — PRESERVED as-is — so
    // the list reflects the removed family.
    run<{ message?: { text?: string } }>(
      apiService
        .getAxiosInstance()
        .delete<{ message?: { text?: string } }>(
          `/v1/schedule4/locations?millId=${millId}&year=${year}&id=${target.id}`,
        ),
      {
        fallback: 'Unable to delete location.',
        onSuccess: (resp) => {
          setSaveMessage(resp?.message?.text ?? null)
          setPanelMode('closed')
          // Re-read the document so the list reflects the removed family (delete returns only a message).
          run(
            apiService
              .getAxiosInstance()
              .get<Schedule4Response>(`/v1/schedule4?millId=${millId}&year=${year}`),
            {
              fallback: 'Deleted, but the list could not be refreshed.',
              onSuccess: (data) => setData(data),
            },
          )
        },
      },
    )
  }

  // Focus, not scroll (PR #353 review). The verdict renders in the `schedule-4__check` column at the
  // TOP of the page while the second Check Status sits at the foot, so a press down there changed
  // nothing the user could see. An earlier version answered that with `window.scrollTo(0, 0)`, which
  // moved the viewport but left focus on the now-off-screen button — no announcement for a screen
  // reader, and the next Tab scrolled straight back down. Focusing the verdict region instead brings
  // it into view, announces it, and respects prefers-reduced-motion, in one move. `focusVerdictRef`
  // makes it fire for THIS action only: a Save validation error must not yank focus off the field
  // the user is correcting.
  const focusVerdictRef = useRef(false)
  const verdictRef = useRef<HTMLDivElement>(null)
  const actionErrorRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!focusVerdictRef.current) return
    if (checkResult === null && saveError === null) return
    focusVerdictRef.current = false
    // Whichever landed: the verdict column on success, the "Action failed" column on error.
    ;(verdictRef.current ?? actionErrorRef.current)?.focus()
  }, [checkResult, saveError])

  const handleCheckStatus = () => {
    if (saving) return
    clearMessages()
    focusVerdictRef.current = true
    checkStatus<Schedule4CheckStatusResponse>({
      fallback: 'Unable to check status.',
      onSuccess: setCheckResult,
    })
  }

  // ---- Sub-page navigation (Story 10.6). ---------------------------------------------------------

  const openSubPage = (def: SubPageDef, locationId: number) => {
    clearMessages()
    setPanelMode('closed')
    // Push a history entry (search: loc + sub) so browser Back returns here to the list.
    void navigate({ to: '/schedule-4', search: { loc: locationId, sub: def.type } })
  }

  // Save the panel (create path) and open the sub-page for the new location — the create → save-first
  // (NAV-003) flow. Runs the post-save lookup inside putLocation's guarded onSuccess.
  const saveLocationThenOpen = (def: SubPageDef) => {
    const prevIds = new Set(data?.locations.map((l) => l.id) ?? [])
    putLocation((document) => {
      const id = document.locations.find((l) => l.id != null && !prevIds.has(l.id))?.id ?? null
      if (id !== null) openSubPage(def, id)
    })
  }

  // From the panel: a saved location opens the sub-page after NAV-002 (edits discarded); an unsaved
  // NEW/COPY location opens after NAV-003 (save first); a read-only View opens directly.
  const requestOpenSubPage = (def: SubPageDef) => {
    if (panelMode === 'view' && panelEditId !== null) {
      openSubPage(def, panelEditId)
    } else if (panelMode === 'edit') {
      setNavConfirm({ kind: 'existing', def })
    } else {
      setNavConfirm({ kind: 'new', def })
    }
  }

  const confirmNav = () => {
    if (!navConfirm) return
    const { kind, def } = navConfirm
    setNavConfirm(null)
    if (kind === 'existing' && panelEditId !== null) {
      openSubPage(def, panelEditId) // discard panel edits, open the sub-page
    } else {
      saveLocationThenOpen(def)
    }
  }

  const SCH4_BASE = 'Special Log Transportation Systems'
  const renderHeader = (trail: string[] = [SCH4_BASE]) => (
    <ScheduleTombstone title="Schedule 4" subtitle={trail} />
  )
  const header = renderHeader()

  const shell = (body: React.ReactNode) => (
    <div className="app-page schedule-page">
      {header}
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16}>
          {body}
        </Column>
      </Grid>
    </div>
  )

  if (contextMissing) {
    return shell(
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title="Mill and Reporting Year required"
        subtitle={ERR_MILL_YEAR_NOT_SELECTED}
      />,
    )
  }
  if (isLoading) {
    return shell(<LoadingScreen label="Loading Schedule 4" />)
  }
  if (errorDetail) {
    return shell(
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title="Unable to load Schedule 4"
        subtitle={errorDetail}
      />,
    )
  }
  if (!data) return null

  const editable = data.editable

  // The open sub-page is URL-driven (search: loc + sub) so browser Back returns to the list. Derive it
  // from the search + loaded data; a stale/unknown loc or sub (e.g. after a mill/year change) falls
  // back to the list.
  const subPageDef = search.sub ? SUB_PAGE_DEFS.find((d) => d.type === search.sub) : undefined
  const subPageLocation =
    search.loc != null ? data.locations.find((l) => l.id === search.loc) : undefined
  const subPage =
    subPageDef && subPageLocation
      ? { def: subPageDef, locationId: subPageLocation.id as number }
      : null

  // ---- Sub-page view (Story 10.6) replaces the list/panel when open. -----------------------------
  if (subPage) {
    const location = data.locations.find((l) => l.id === subPage.locationId)
    const rows = (location?.subPageRows ?? []).filter((row) => row.code === subPage.def.code)
    const subPageTrail = [SCH4_BASE, ...(location?.name ? [location.name] : []), subPage.def.label]
    return (
      <div className="app-page schedule-page">
        {renderHeader(subPageTrail)}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <SubPage
              millId={millId as number}
              year={year as number}
              locationId={subPage.locationId}
              def={subPage.def}
              rows={rows}
              editable={editable}
              onBack={() => void navigate({ to: '/schedule-4', search: {}, replace: true })}
              onDocUpdate={(doc) => setData(doc)}
            />
          </Column>
        </Grid>
      </div>
    )
  }

  const validation = validateLocationForm(panelName, panelCategories)
  const fieldErrors = panelMode === 'view' ? {} : validation.fieldErrors
  const panelOpen = panelMode !== 'closed'
  const readOnlyPanel = panelMode === 'view'
  const panelLocation =
    panelEditId !== null ? data.locations.find((l) => l.id === panelEditId) : undefined
  // Aggregate the group's sub-page rows for its grid row (legacy Towing/Truck-Rehaul/Other totals):
  // summed Distance/Volume/Cost/Cycle + derived $/m³ (total cost ÷ total volume). Empty when the group
  // has no rows.
  const panelSubTotals = (code: number) => {
    const rows = (panelLocation?.subPageRows ?? []).filter((row) => row.code === code)
    const distance = rows.reduce((total, row) => total + (row.distance ?? 0), 0)
    const volume = rows.reduce((total, row) => total + (row.volume ?? 0), 0)
    const cost = rows.reduce((total, row) => total + (row.cost ?? 0), 0)
    const cycle = rows.reduce((total, row) => total + (row.cycle ?? 0), 0)
    return {
      count: rows.length,
      distance,
      volume,
      cost,
      cycle,
      perUnit: volume !== 0 ? cost / volume : null,
    }
  }

  // ---- Existing Locations table. -----------------------------------------------------------------
  const locationsTable = (
    <TableContainer title="Existing Locations">
      <Table aria-label="Existing Locations">
        <TableHead>
          <TableRow>
            <TableHeader>Location Name</TableHeader>
            <TableHeader>Actions</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.locations.length === 0 ? (
            <TableRow>
              <TableCell colSpan={2}>No locations have been added.</TableCell>
            </TableRow>
          ) : (
            data.locations.map((location) => (
              <TableRow
                key={location.id ?? location.name}
                className={
                  panelOpen && location.id != null && location.id === panelEditId
                    ? 'schedule-4__row--editing'
                    : undefined
                }
              >
                <TableCell>{location.name}</TableCell>
                <TableCell>
                  <div className="schedule-4__row-actions">
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={editable ? Edit : View}
                      onClick={() => openEditOrView(location, editable ? 'edit' : 'view')}
                    >
                      {editable ? 'Edit' : 'View'}
                    </Button>
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Copy}
                      disabled={!editable || saving}
                      onClick={() => openCopy(location)}
                    >
                      Copy
                    </Button>
                    <Button
                      kind="danger--tertiary"
                      size="sm"
                      renderIcon={TrashCan}
                      disabled={!editable || saving}
                      onClick={() => setConfirmDelete(location)}
                    >
                      Delete
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  )

  // ---- Category grid (inside the panel). ---------------------------------------------------------
  // Per-category $/m³ mirrored from the committed values, so the column tracks entry before Save
  // (defect #291). View mode has no entry, so it keeps rendering the server's own figures (AC7); after
  // a Save the panel is re-seeded from the echo, so the mirror recomputes from the server's own values.
  const mirroredPerUnit = readOnlyPanel ? panelPerUnit : deriveCategoryPerUnits(panelCommitted)

  const renderCategoryRow = (def: CategoryDef) => (
    <CategoryRow
      key={def.code}
      def={def}
      values={panelCategories[def.code] ?? { volume: '', cost: '', distance: '' }}
      perUnit={mirroredPerUnit[def.code]}
      readOnly={readOnlyPanel}
      fieldErrors={fieldErrors}
      onFieldChange={setCategoryField}
      onFieldCommit={commitCategoryField}
    />
  )

  // A list sub-page appears as a group row inside the grid (legacy code position): its label + current
  // row count link to the sub-page, and the amount columns show the group's read-only totals summed
  // from its sub-page rows (legacy towing/truck-rehaul/other totals). Cycle Time only for Truck Rehaul.
  const renderSubPageRow = (def: SubPageDef) => {
    const totals = panelSubTotals(def.code)
    const has = totals.count > 0
    return (
      <TableRow key={`sub-${def.code}`}>
        <TableCell>
          <Button
            kind="ghost"
            size="sm"
            className="schedule-4__subpage-link"
            disabled={saving}
            onClick={() => requestOpenSubPage(def)}
          >
            {`${def.label} (${totals.count}):`}
          </Button>
        </TableCell>
        <TableCell className="schedule-4__num">{has ? fmtNumber(totals.distance) : '—'}</TableCell>
        <TableCell className="schedule-4__num">{has ? fmtNumber(totals.volume) : '—'}</TableCell>
        <TableCell className="schedule-4__num">{has ? fmtNumber(totals.cost) : '—'}</TableCell>
        <TableCell className="schedule-4__num">{fmtCurrency(totals.perUnit)}</TableCell>
        <TableCell className="schedule-4__num">
          {def.hasCycle && has ? fmtNumber(totals.cycle) : '—'}
        </TableCell>
      </TableRow>
    )
  }

  const panel = panelOpen && (
    <div className="schedule-4__panel">
      <h3 className="schedule-4__heading">
        {panelMode === 'new' && 'New Location'}
        {panelMode === 'edit' && 'Edit Location'}
        {panelMode === 'copy' && 'Copy Location'}
        {panelMode === 'view' && 'View Location'}
      </h3>
      {readOnlyPanel ? (
        <p className="schedule-4__field-label">Location Name: {panelName}</p>
      ) : (
        <TextInput
          id="location-name"
          labelText="Location Name"
          maxLength={30}
          value={panelName}
          onChange={(event) => setPanelName(event.target.value)}
          invalid={Boolean(validation.nameError) && saveError !== null}
          invalidText={validation.nameError}
        />
      )}

      <TableContainer className="schedule-4__grid">
        <Table aria-label="Transportation Categories">
          <TableHead>
            <TableRow>
              <TableHeader aria-label="Transportation category" />
              <TableHeader className="schedule-4__num">Distance (km)</TableHeader>
              <TableHeader className="schedule-4__num">Volume (m³)</TableHeader>
              <TableHeader className="schedule-4__num">Cost $</TableHeader>
              <TableHeader className="schedule-4__num">$/m³</TableHeader>
              <TableHeader className="schedule-4__num">Cycle Time</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {GRID_ENTRIES.map((entry) =>
              entry.kind === 'category'
                ? renderCategoryRow(entry.def)
                : renderSubPageRow(entry.def),
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {readOnlyPanel ? (
        <div className="schedule-4__field">
          <span className="schedule-4__field-label">
            If you have any additional comments, please enter them here:
          </span>
          <p className="schedule-4__comments">{panelComments || '—'}</p>
        </div>
      ) : (
        <CommentsTextArea
          id="location-comments"
          labelText="If you have any additional comments, please enter them here:"
          maxCount={COMMENTS_MAX}
          value={panelComments}
          onChange={(event) => setPanelComments(event.target.value)}
        />
      )}

      <div className="schedule-4__panel-actions">
        {!readOnlyPanel && (
          <Button kind="primary" disabled={saving} renderIcon={Save} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button
          kind="secondary"
          disabled={saving}
          // The icon tracks the label: this one button is Close on a view panel and Back on an
          // editable one, so a fixed glyph would contradict half of its own uses.
          renderIcon={readOnlyPanel ? Close : ArrowLeft}
          onClick={closePanel}
        >
          {readOnlyPanel ? 'Close' : 'Back'}
        </Button>
      </div>
    </div>
  )

  // Two instances, deliberately asymmetric: the top bar carries Add New Location plus Check Status,
  // the bottom is Check Status alone. Add rides the top bar only because it toggles the panel that
  // opens directly beneath it. `bottom` governs that difference and the marker, nothing else — both
  // buttons share one handler and behave identically.
  //
  // The legacy grounding for this layout, the deviation it carries, and the branches that bypass this
  // helper (the sub-page view and the context/loading/error shells) are recorded in
  // defect-293-check-status-bottom-row-schedules-4-6.md.
  const actionBar = (bottom: boolean) => (
    <Column
      sm={4}
      md={8}
      lg={16}
      className={`schedule-4__actions${bottom ? ' schedule-4__actions--bottom' : ''}`}
      data-testid={bottom ? 'schedule-4-bottom-actions' : 'schedule-4-top-actions'}
    >
      {!bottom && (
        <Button kind="primary" renderIcon={Add} disabled={!editable || saving} onClick={openNew}>
          Add New Location
        </Button>
      )}
      {/* `!editable` closes DIV-1 / issue #322 for Schedule 4: legacy bound EVERY Check Status instance
          to disableReportEdits() (schedule4.xhtml:43 and :220-221, schedule4NewLocation.xhtml:275,
          schedule4ExistingLocation.xhtml:1144), and the other seven schedules already include the term —
          Schedules 4 and 8 were the outliers. Schedule 8 is still open; #322 does not close on this alone. */}
      <Button
        kind="tertiary"
        renderIcon={CheckmarkOutline}
        disabled={!editable || saving}
        onClick={handleCheckStatus}
      >
        Check Status
      </Button>
    </Column>
  )

  return (
    <div className="app-page schedule-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {saveMessage && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification kind="success" lowContrast title="Success" subtitle={saveMessage} />
          </Column>
        )}
        {saveError && (
          <Column sm={4} md={8} lg={16} ref={actionErrorRef} tabIndex={-1}>
            <InlineNotification
              kind="error"
              lowContrast
              title="Action failed"
              subtitle={saveError}
            />
          </Column>
        )}
        {warnMessage && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="warning"
              lowContrast
              title="Copy location"
              subtitle={warnMessage}
            />
          </Column>
        )}
        {checkResult && (
          // tabIndex={-1} makes this a programmatic focus target only — never in the tab order.
          <Column
            sm={4}
            md={8}
            lg={16}
            className="schedule-4__check"
            ref={verdictRef}
            tabIndex={-1}
          >
            {checkResult.messages.map((msg) => (
              <InlineNotification
                key={`schedule-${msg.key}-${msg.text}`}
                kind="success"
                lowContrast
                title="Check Status"
                subtitle={msg.text}
              />
            ))}
            {checkResult.locations.map((location) => (
              <div key={`loc-${location.id ?? location.name}`}>
                {location.messages.map((msg) => (
                  <InlineNotification
                    key={`met-${location.id ?? location.name}-${msg.key}-${msg.text}`}
                    kind="success"
                    lowContrast
                    title="Check Status"
                    subtitle={msg.text}
                  />
                ))}
                {location.issues.map((issue) => (
                  <InlineNotification
                    key={`issue-${location.id ?? location.name}-${issue.code}`}
                    kind="warning"
                    lowContrast
                    title={`${location.name} — required`}
                    subtitle={issue.message.text}
                  />
                ))}
              </div>
            ))}
          </Column>
        )}

        {actionBar(false)}

        <Column sm={4} md={8} lg={16} className="schedule-4__section">
          {locationsTable}
        </Column>

        {panel && (
          <Column sm={4} md={8} lg={16} className="schedule-4__section">
            {panel}
          </Column>
        )}

        {/* The page's bottom row (deviation D) — Check Status alone, always last in the body. */}
        {actionBar(true)}
      </Grid>

      {editable && (
        <Modal
          open={confirmDelete !== null}
          danger
          modalHeading="Delete location"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDelete(null)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}

      <ConfirmNavigationModal
        open={navConfirm !== null}
        heading={navConfirm?.kind === 'new' ? 'Save before continuing' : 'Unsaved changes'}
        continueLabel={navConfirm?.kind === 'new' ? 'Save and continue' : 'Continue'}
        onCancel={() => setNavConfirm(null)}
        onContinue={confirmNav}
      >
        {navConfirm?.kind === 'new' ? NAV_SAVE_FIRST : NAV_UNSAVED_LOST}
      </ConfirmNavigationModal>
    </div>
  )
}

export default Schedule4
