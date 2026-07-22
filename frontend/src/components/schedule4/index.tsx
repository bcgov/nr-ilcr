import type { FC } from 'react'
import type Schedule4Response from '@/interfaces/Schedule4Response'
import type { Location, Schedule4CheckStatusResponse } from '@/interfaces/Schedule4Response'
import type Schedule4LocationRequest from '@/interfaces/Schedule4Request'
import { useEffect, useState } from 'react'
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

type PanelMode = 'closed' | 'new' | 'edit' | 'copy' | 'view'

const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

const toNum = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '') return null
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { data?: { detail?: string } } }).response
    return response?.data?.detail
  }
  return undefined
}

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

const Schedule4: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<Schedule4Response | null>(null)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
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

  useEffect(() => {
    if (contextMissing) return
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    setSaveMessage(null)
    setSaveError(null)
    setWarnMessage(null)
    setCheckResult(null)
    setPanelMode('closed')
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<Schedule4Response>(`/v1/schedule4?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Schedule 4.')
          setData(null)
        }
      })
      .finally(() => {
        if (active) setIsLoading(false)
      })
    return () => {
      active = false
    }
  }, [millId, year, contextMissing])

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
    name: panelName,
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

  const handleSave = () => {
    if (saving || panelMode === 'closed' || panelMode === 'view') return
    const validation = validateLocationForm(panelName, panelCategories)
    if (!isLocationFormValid(validation)) {
      // Generic banner; the specific verbatim messages (ERR-001, ranges, BR-04) show inline on the
      // fields so they are not duplicated.
      setSaveMessage(null)
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    setSaving(true)
    clearMessages()
    apiService
      .getAxiosInstance()
      .put<Schedule4Response>(
        `/v1/schedule4/locations?millId=${millId}&year=${year}`,
        buildRequest(),
      )
      .then((response) => {
        setData(response.data)
        setSaveMessage(response.data.message?.text ?? null)
        setPanelMode('closed')
      })
      .catch((error: unknown) => {
        // Keep the panel open + entered values; surface the API's verbatim detail (ERR-001/ERR-002…).
        setSaveError(extractDetail(error) || 'Schedule could not be saved.')
      })
      .finally(() => setSaving(false))
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
    clearMessages()
    apiService
      .getAxiosInstance()
      .post<Schedule4CheckStatusResponse>(
        `/v1/schedule4/check-status?millId=${millId}&year=${year}`,
      )
      .then((response) => setCheckResult(response.data))
      .catch((error: unknown) => setSaveError(extractDetail(error) || 'Unable to check status.'))
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle title="Schedule 4" subtitle="Special Log Transportation Costs." />
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
  const validation = validateLocationForm(panelName, panelCategories)
  const fieldErrors = panelMode === 'view' ? {} : validation.fieldErrors
  const panelOpen = panelMode !== 'closed'
  const readOnlyPanel = panelMode === 'view'

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
  const categoryInput = (code: number, field: 'volume' | 'cost' | 'distance', label: string) => {
    const key = `${code}-${field}`
    const value = panelCategories[code]?.[field] ?? ''
    if (readOnlyPanel) {
      return <TableCell className="schedule-4__num">{value === '' ? '—' : value}</TableCell>
    }
    return (
      <TableCell className="schedule-4__num">
        <TextInput
          id={key}
          labelText={label}
          hideLabel
          size="sm"
          inputMode="numeric"
          value={value}
          onChange={setCategoryField(code, field)}
          invalid={Boolean(fieldErrors[key])}
          invalidText={fieldErrors[key]}
        />
      </TableCell>
    )
  }

  const categoryRow = (def: (typeof ALL_CATEGORIES)[number]) => {
    const isDistance = def.kind === 'DISTANCE'
    return (
      <TableRow key={def.code}>
        <TableCell>{def.label}</TableCell>
        {categoryInput(def.code, 'volume', `${def.label} volume`)}
        {categoryInput(def.code, 'cost', `${def.label} cost`)}
        {isDistance ? (
          categoryInput(def.code, 'distance', `${def.label} distance`)
        ) : (
          <TableCell className="schedule-4__num">—</TableCell>
        )}
        <TableCell className="schedule-4__num">{fmt(panelPerUnit[def.code])}</TableCell>
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
            {FIXED_CATEGORIES.map(categoryRow)}
            {DISTANCE_CATEGORIES.map(categoryRow)}
          </TableBody>
        </Table>
      </TableContainer>

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
                key={`schedule-${msg.key}`}
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
                    key={`met-${location.id ?? location.name}`}
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
    </div>
  )
}

export default Schedule4
