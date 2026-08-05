import type { FC } from 'react'
import type Schedule4Response from '@/interfaces/Schedule4Response'
import type { Location, Schedule4CheckStatusResponse } from '@/interfaces/Schedule4Response'
import type Schedule4LocationRequest from '@/interfaces/Schedule4Request'
import { useCallback, useEffect, useState } from 'react'
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
  TextArea,
  TextInput,
} from '@carbon/react'
import apiService from '@/service/api-service'
import { fmtCurrency, numStr, toNum } from '@/utils/number'
import { extractDetail } from '@/utils/error'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { getRouteApi } from '@tanstack/react-router'
import useMillYear from '@/context/millYear/useMillYear'
import LoadingScreen from '@/components/core/LoadingScreen'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import {
  ALL_CATEGORIES,
  isLocationFormValid,
  validateLocationForm,
  type CategoryForm,
} from './validation'
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
// Per-location comments cap — the TRANSPORTATION_REPORT.COMMENTS column width (backend @Size(2000)).
const COMMENTS_MAX = 2000

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

const subPageCount = (location: Location, code: number): number =>
  location.subPageRows.filter((row) => row.code === code).length

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
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void
}> = ({ inputId, label, value, readOnly, invalidText, onChange }) => {
  if (readOnly) {
    return <TableCell className="schedule-4__num">{value === '' ? '—' : value}</TableCell>
  }
  return (
    <TableCell className="schedule-4__num">
      <TextInput
        id={inputId}
        labelText={label}
        hideLabel
        size="sm"
        inputMode="numeric"
        value={value}
        onChange={onChange}
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
  onFieldChange: (
    code: number,
    field: CategoryField,
  ) => (event: React.ChangeEvent<HTMLInputElement>) => void
}> = ({ def, values, perUnit, readOnly, fieldErrors, onFieldChange }) => {
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
          onChange={onFieldChange(def.code, 'distance')}
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
        onChange={onFieldChange(def.code, 'volume')}
      />
      <CategoryCell
        inputId={`${def.code}-cost`}
        label={`${def.label} cost`}
        value={values.cost}
        readOnly={readOnly}
        invalidText={fieldErrors[`${def.code}-cost`]}
        onChange={onFieldChange(def.code, 'cost')}
      />
      <TableCell className="schedule-4__num">{fmtCurrency(perUnit)}</TableCell>
      <TableCell className="schedule-4__num">—</TableCell>
    </TableRow>
  )
}

const Schedule4: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  // Sub-page level from the URL (loc = location id, sub = sub-page type); navigate updates it.
  const search = scheduleRoute.useSearch()
  const navigate = scheduleRoute.useNavigate()

  // Reset URL search parameters when millId or year switches (Comment 3)
  useEffect(() => {
    if (search.loc !== undefined || search.sub !== undefined) {
      void navigate({ to: '/schedule-4', search: {}, replace: true })
    }
  }, [millId, year, navigate, search.loc, search.sub])

  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [warnMessage, setWarnMessage] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<Schedule4CheckStatusResponse | null>(null)

  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [panelName, setPanelName] = useState('')
  const [panelCategories, setPanelCategories] = useState<CategoryForm>(() => emptyCategoryForm())
  const [panelPerUnit, setPanelPerUnit] = useState<Record<number, number | null>>({})
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
  // (mill/year change), mirroring the Schedule 1/2 onReset.
  const resetTransient = useCallback(() => {
    setSaveMessage(null)
    setSaveError(null)
    setWarnMessage(null)
    setCheckResult(null)
    setPanelMode('closed')
  }, [])

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

  const clearMessages = () => {
    setSaveMessage(null)
    setSaveError(null)
    setWarnMessage(null)
    setCheckResult(null)
  }

  const openNew = () => {
    clearMessages()
    setPanelMode('new')
    setPanelName('')
    setPanelCategories(emptyCategoryForm())
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
    setPanelPerUnit({})
    setPanelEditId(null)
    setPanelRevision(null)
    setPanelComments(location.comments ?? '') // copy clones the comments (not the name)
    setWarnMessage(copyWarning(location.name))
  }

  const closePanel = () => setPanelMode('closed')

  const setCategoryField =
    (code: number, field: 'volume' | 'cost' | 'distance') =>
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const { value } = event.target
      setPanelCategories((prev) => ({
        ...prev,
        [code]: { ...(prev[code] ?? { volume: '', cost: '', distance: '' }), [field]: value },
      }))
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

  // Validate + PUT the location panel (create or edit). Resolves to the saved document on success, or
  // null on validation failure / API error (panel stays open with its entered values). Shared by the
  // Save button (handleSave) and the save-before-subpage flow (saveLocationReturningId).
  const putLocation = (): Promise<Schedule4Response | null> => {
    const validation = validateLocationForm(panelName, panelCategories)
    if (!isLocationFormValid(validation)) {
      // Generic banner; the specific verbatim messages (ERR-001, ranges, BR-04) show inline on the
      // fields so they are not duplicated.
      setSaveMessage(null)
      setSaveError('Please correct the highlighted fields before saving.')
      return Promise.resolve(null)
    }
    setSaving(true)
    clearMessages()
    return apiService
      .getAxiosInstance()
      .put<Schedule4Response>(
        `/v1/schedule4/locations?millId=${millId}&year=${year}`,
        buildRequest(),
      )
      .then((response) => {
        setData(response.data)
        setSaveMessage(response.data.message?.text ?? null)
        return response.data
      })
      .catch((error: unknown) => {
        // Keep the panel open + entered values; surface the API's verbatim detail (ERR-001/ERR-002…).
        setSaveError(extractDetail(error) || 'Schedule could not be saved.')
        return null
      })
      .finally(() => setSaving(false))
  }

  const handleSave = () => {
    if (saving || panelMode === 'closed' || panelMode === 'view') return
    void putLocation().then((document) => {
      if (document) setPanelMode('closed')
    })
  }

  const handleDelete = () => {
    if (saving || !confirmDelete) return
    const target = confirmDelete
    setConfirmDelete(null)
    setSaving(true)
    clearMessages()
    apiService
      .getAxiosInstance()
      .delete<{ message?: { text?: string } }>(
        `/v1/schedule4/locations?millId=${millId}&year=${year}&id=${target.id}`,
      )
      .then((response) => {
        setSaveMessage(response.data?.message?.text ?? null)
        setPanelMode('closed')
        // Re-read the document so the list reflects the removed family (delete returns only a message).
        return apiService
          .getAxiosInstance()
          .get<Schedule4Response>(`/v1/schedule4?millId=${millId}&year=${year}`)
          .then((reload) => setData(reload.data))
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to delete location.')
      })
      .finally(() => setSaving(false))
  }

  const handleCheckStatus = () => {
    if (saving) return
    setSaving(true) // gate re-entrancy: disables the button and blocks overlapping check-status posts
    clearMessages()
    apiService
      .getAxiosInstance()
      .post<Schedule4CheckStatusResponse>(
        `/v1/schedule4/check-status?millId=${millId}&year=${year}`,
      )
      .then((response) => setCheckResult(response.data))
      .catch((error: unknown) => setSaveError(extractDetail(error) || 'Unable to check status.'))
      .finally(() => setSaving(false))
  }

  // ---- Sub-page navigation (Story 10.6). ---------------------------------------------------------

  const openSubPage = (def: SubPageDef, locationId: number) => {
    clearMessages()
    setPanelMode('closed')
    // Push a history entry (search: loc + sub) so browser Back returns here to the list.
    void navigate({ to: '/schedule-4', search: { loc: locationId, sub: def.type } })
  }

  // Save the panel (create path) and return the new location's id, or null on validation/API failure.
  const saveLocationReturningId = (): Promise<number | null> =>
    putLocation().then((document) =>
      document ? (document.locations.find((l) => l.name === panelName.trim())?.id ?? null) : null,
    )

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
      void saveLocationReturningId().then((id) => {
        if (id !== null) openSubPage(def, id)
      })
    }
  }

  const SCH4_BASE = 'Special Log Transportation Systems'
  const renderHeader = (trail: string[] = [SCH4_BASE]) => (
    <ScheduleTombstone title="Schedule 4" subtitle={trail} />
  )
  const header = renderHeader()

  const shell = (body: React.ReactNode) => (
    <div className="app-page">
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
      <div className="app-page">
        {renderHeader(subPageTrail)}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <SubPage
              millId={millId as number}
              year={year as number}
              locationId={subPage.locationId}
              locationName={location?.name ?? ''}
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
  const panelSubCount = (code: number): number =>
    panelLocation ? subPageCount(panelLocation, code) : 0

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
              <TableRow key={location.id ?? location.name}>
                <TableCell>{location.name}</TableCell>
                <TableCell>
                  <div className="schedule-4__row-actions">
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() => openEditOrView(location, editable ? 'edit' : 'view')}
                    >
                      {editable ? 'Edit' : 'View'}
                    </Button>
                    <Button
                      kind="ghost"
                      size="sm"
                      disabled={!editable || saving}
                      onClick={() => openCopy(location)}
                    >
                      Copy
                    </Button>
                    <Button
                      kind="danger--ghost"
                      size="sm"
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
  const renderCategoryRow = (def: CategoryDef) => (
    <CategoryRow
      key={def.code}
      def={def}
      values={panelCategories[def.code] ?? { volume: '', cost: '', distance: '' }}
      perUnit={panelPerUnit[def.code]}
      readOnly={readOnlyPanel}
      fieldErrors={fieldErrors}
      onFieldChange={setCategoryField}
    />
  )

  // A list sub-page appears as a group row inside the grid (legacy code position): its label + current
  // row count link to the sub-page; the amount columns are blank (its rows live on that page).
  const renderSubPageRow = (def: SubPageDef) => (
    <TableRow key={`sub-${def.code}`}>
      <TableCell>
        <Button
          kind="ghost"
          size="sm"
          className="schedule-4__subpage-link"
          disabled={saving}
          onClick={() => requestOpenSubPage(def)}
        >
          {`${def.label} (${panelSubCount(def.code)}):`}
        </Button>
      </TableCell>
      <TableCell colSpan={5} />
    </TableRow>
  )

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
          <span className="schedule-4__field-label">Comments</span>
          <p className="schedule-4__comments">{panelComments || '—'}</p>
        </div>
      ) : (
        <TextArea
          id="location-comments"
          labelText="Comments"
          enableCounter
          maxCount={COMMENTS_MAX}
          value={panelComments}
          onChange={(event) => setPanelComments(event.target.value)}
        />
      )}

      <div className="schedule-4__panel-actions">
        {!readOnlyPanel && (
          <Button kind="primary" disabled={saving} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button kind="secondary" disabled={saving} onClick={closePanel}>
          {readOnlyPanel ? 'Close' : 'Cancel'}
        </Button>
      </div>
    </div>
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {saveMessage && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification kind="success" lowContrast title="Success" subtitle={saveMessage} />
          </Column>
        )}
        {saveError && (
          <Column sm={4} md={8} lg={16}>
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
          <Column sm={4} md={8} lg={16} className="schedule-4__check">
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

        <Column sm={4} md={8} lg={16} className="schedule-4__actions">
          <Button kind="primary" disabled={!editable || saving || panelOpen} onClick={openNew}>
            Add New Location
          </Button>
          <Button kind="tertiary" disabled={saving} onClick={handleCheckStatus}>
            Check Status
          </Button>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-4__section">
          {locationsTable}
        </Column>

        {panel && (
          <Column sm={4} md={8} lg={16} className="schedule-4__section">
            {panel}
          </Column>
        )}
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

      <Modal
        open={navConfirm !== null}
        modalHeading={navConfirm?.kind === 'new' ? 'Save before continuing' : 'Unsaved changes'}
        primaryButtonText={navConfirm?.kind === 'new' ? 'Save and continue' : 'Continue'}
        secondaryButtonText="Cancel"
        onRequestClose={() => setNavConfirm(null)}
        onRequestSubmit={confirmNav}
      >
        <p>{navConfirm?.kind === 'new' ? NAV_SAVE_FIRST : NAV_UNSAVED_LOST}</p>
      </Modal>
    </div>
  )
}

export default Schedule4
