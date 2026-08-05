import type { FC } from 'react'
import type Schedule4Response from '@/interfaces/Schedule4Response'
import type { Location, Schedule4CheckStatusResponse } from '@/interfaces/Schedule4Response'
import type Schedule4LocationRequest from '@/interfaces/Schedule4Request'
import { useCallback, useState } from 'react'
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
import apiService from '@/service/api-service'
import { fmt, numStr, toNum } from '@/utils/number'
import { extractDetail } from '@/utils/error'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import useMillYear from '@/context/millYear/useMillYear'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'
import {
  ALL_CATEGORIES,
  DISTANCE_CATEGORIES,
  FIXED_CATEGORIES,
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

const TOWING = 43
const TRUCK_REHAUL = 46
const OTHER = 55

// NAV-002 (leaving a saved location to a sub-page — unsaved edits discarded) and NAV-003 (leaving an
// unsaved NEW location — must save first). Client-only confirm chrome, verbatim from the bundle.
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

// One category row (Volume / Cost / Distance / read-only $/m³). Distance cell only for the distance
// categories. Module-level so it is not recreated on every page render.
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
      <TableCell>{def.label}</TableCell>
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
      <TableCell className="schedule-4__num">{fmt(perUnit)}</TableCell>
    </TableRow>
  )
}

const Schedule4: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

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
  const [confirmDelete, setConfirmDelete] = useState<Location | null>(null)
  // Sub-page (Story 10.6): when set, the sub-page view replaces the list/panel.
  const [subPage, setSubPage] = useState<{ def: SubPageDef; locationId: number } | null>(null)
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
    setSubPage({ def, locationId })
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

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle
        breadCrumbs={[{ name: 'ILCR', path: '/' }]}
        title="Schedule 4"
        subtitle="Special Log Transportation Costs."
      />
    </Grid>
  )

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

  // ---- Sub-page view (Story 10.6) replaces the list/panel when open. -----------------------------
  if (subPage) {
    const location = data.locations.find((l) => l.id === subPage.locationId)
    const rows = (location?.subPageRows ?? []).filter((row) => row.code === subPage.def.code)
    return (
      <div className="app-page">
        {header}
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
              onBack={() => setSubPage(null)}
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
            <TableHeader className="schedule-4__num">Categories</TableHeader>
            <TableHeader className="schedule-4__num">Towing Total</TableHeader>
            <TableHeader className="schedule-4__num">Truck Rehaul</TableHeader>
            <TableHeader className="schedule-4__num">Other</TableHeader>
            <TableHeader>Actions</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.locations.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6}>No locations have been added.</TableCell>
            </TableRow>
          ) : (
            data.locations.map((location) => (
              <TableRow key={location.id ?? location.name}>
                <TableCell>{location.name}</TableCell>
                <TableCell className="schedule-4__num">{location.categories.length}</TableCell>
                <TableCell className="schedule-4__num">{subPageCount(location, TOWING)}</TableCell>
                <TableCell className="schedule-4__num">
                  {subPageCount(location, TRUCK_REHAUL)}
                </TableCell>
                <TableCell className="schedule-4__num">{subPageCount(location, OTHER)}</TableCell>
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

      <TableContainer title="Transportation Categories" className="schedule-4__grid">
        <Table aria-label="Transportation Categories">
          <TableHead>
            <TableRow>
              <TableHeader>Category</TableHeader>
              <TableHeader className="schedule-4__num">Volume</TableHeader>
              <TableHeader className="schedule-4__num">Cost</TableHeader>
              <TableHeader className="schedule-4__num">Distance</TableHeader>
              <TableHeader className="schedule-4__num">$/m³</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {FIXED_CATEGORIES.map(renderCategoryRow)}
            {DISTANCE_CATEGORIES.map(renderCategoryRow)}
          </TableBody>
        </Table>
      </TableContainer>

      <div className="schedule-4__subpage-links">
        <span className="schedule-4__field-label">Transportation sub-pages:</span>
        {SUB_PAGE_DEFS.map((def) => (
          <Button
            key={def.type}
            kind="ghost"
            size="sm"
            disabled={saving}
            onClick={() => requestOpenSubPage(def)}
          >
            {def.label} ({panelSubCount(def.code)})
          </Button>
        ))}
      </div>

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
        <Column sm={4} md={8} lg={16} className="schedule-4__meta">
          <dl className="schedule-4__summary">
            <div className="schedule-4__summary-item">
              <dt>Mill</dt>
              <dd>{data.millId}</dd>
            </div>
            <div className="schedule-4__summary-item">
              <dt>Reporting Year</dt>
              <dd>{data.year}</dd>
            </div>
            <div className="schedule-4__summary-item">
              <dt>Status</dt>
              <dd>{data.trackStatus ?? '—'}</dd>
            </div>
          </dl>
        </Column>

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
