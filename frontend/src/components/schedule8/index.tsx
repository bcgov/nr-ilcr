import type { FC } from 'react'
import type Schedule8Response from '@/interfaces/Schedule8Response'
import type { Page, Sample, Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import type Schedule8Options from '@/interfaces/Schedule8Options'
import type { CodeOption } from '@/interfaces/Schedule8Options'
import type { Schedule8PageRequest } from '@/interfaces/Schedule8Request'
import { useEffect, useRef, useState } from 'react'
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
  ArrowRight,
  CheckmarkOutline,
  Close,
  Copy,
  Edit,
  Save,
  TrashCan,
  View,
} from '@carbon/icons-react'
import { getRouteApi } from '@tanstack/react-router'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import { blankToNull } from '@/utils/forms'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleMutations } from '@/hooks/useScheduleMutations'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import CodeComboBox from '@/components/core/CodeComboBox'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import {
  emptyPageForm,
  isTflSelected,
  seedPageForm,
  validatePageForm,
  type PageForm,
} from './validation'
import CheckStatusResult from './CheckStatusResult'
import SamplePage from './SamplePage'
import RatesPage from './RatesPage'
import './index.scss'

// Client-only chrome (no request behind it). All success/error text comes from the API
// message.text / ProblemDetail.detail — never hardcoded (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'

type PanelMode = 'closed' | 'new' | 'edit' | 'copy' | 'view'
// The three-level tree: the page list/editor, then a page's samples, then a sample's additions/
// deductions. The level is derived from the URL search (pageId, sampleId) so browser Back steps back.
type NavView =
  | { level: 'pages' }
  | { level: 'samples'; pageId: number }
  | { level: 'rates'; pageId: number; sampleId: number }

// Typed accessor for this page's route: the samples/rates levels are URL-driven (search: pageId +
// sampleId) so the browser Back button steps back through them.
const scheduleRoute = getRouteApi('/schedule-8')

// The legacy Tree-to-Truck page label (TreeToTruckReportDO): a composite of the 1-based row number,
// the TSA/TFL identifier, and the cutting permit — e.g. "Page # 1  -TSA: TFL -CP: cp123".
const pageLabel = (page: Page, index: number): string => {
  const tsa = page.tsaNumber ?? ''
  const cp = page.cuttingPermit && page.cuttingPermit.trim() !== '' ? page.cuttingPermit : ' - '
  return `Page # ${index + 1}  -TSA: ${tsa} -CP: ${cp}`
}

// The legacy Tree-to-Truck sample label (TreeToTruckDetailReportDO): the 1-based row number and the
// contract id — e.g. "Sample # 1 - one".
const sampleLabel = (sample: Sample, index: number): string => {
  const contract = sample.contractId && sample.contractId.trim() !== '' ? sample.contractId : ''
  return `Sample # ${index + 1} - ${contract}`
}

// Format a phone as 222-222-2222 — used both for read-only display and to live-format entry as the
// user types: keep only digits (max 10) and insert dashes so the value reads 222, 222-222, then
// 222-222-2222. A value with fewer than 10 digits formats the digits it has (no padding).
const phoneInput = (raw: string): string => {
  const digits = raw.replace(/\D/g, '').slice(0, 10)
  if (digits.length <= 3) return digits
  if (digits.length <= 6) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`
}

const Schedule8: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  // Save/delete/check-status all run through the shared hook's guarded run() (Story 29.6): a stale
  // in-flight write can no longer repaint a newly-switched mill/year. `saving` is the single in-flight
  // lock for every write (it also gates Check Status) — Schedule 8 had no separate checking lock.
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
    run,
    save,
    remove,
    checkStatus,
  } = useScheduleMutations<Schedule8CheckStatusResponse>({
    path: '/v1/schedule8',
    millId,
    year,
    isCurrent,
  })

  // Sample/rates level from the URL (pageId, sampleId); navigate updates it.
  const search = scheduleRoute.useSearch()
  const navigate = scheduleRoute.useNavigate()

  // Clear stale sample/rates URL params when the mill/year context actually switches (Comment 3).
  // Guarded by a ref so it fires only on a real mill/year change — never on in-app sub-navigation,
  // which legitimately sets pageId/sampleId (otherwise every drill-down would reset itself).
  const contextKey = `${String(millId)}:${String(year)}`
  const contextKeyRef = useRef(contextKey)
  useEffect(() => {
    if (contextKeyRef.current !== contextKey) {
      contextKeyRef.current = contextKey
      if (search.pageId !== undefined || search.sampleId !== undefined) {
        void navigate({ to: '/schedule-8', search: {}, replace: true })
      }
    }
  }, [contextKey, navigate, search.pageId, search.sampleId])

  const [data, setData] = useState<Schedule8Response | null>(null)
  // Reference-data option lists for the page-editor dropdowns. Fetched once (global, not mill/year
  // scoped); null until loaded — the dropdowns fall back to an empty list so the panel still renders.
  const [options, setOptions] = useState<Schedule8Options | null>(null)
  const [optionsError, setOptionsError] = useState<string | null>(null)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)

  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [form, setForm] = useState<PageForm>(() => emptyPageForm())
  const [editId, setEditId] = useState<number | null>(null)
  const [revision, setRevision] = useState<number | null>(null)
  const [showErrors, setShowErrors] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<Page | null>(null)
  // Set to a page id when the user clicks TtT Samples with unsaved page edits (nav-away confirm).
  const [confirmSamplesPageId, setConfirmSamplesPageId] = useState<number | null>(null)

  useEffect(() => {
    if (contextMissing) return
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    // Drop the banners AND release the in-flight lock (Story 29.6 reset) on a mill/year change.
    resetBanners()
    setPanelMode('closed')
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<Schedule8Response>(`/v1/schedule8?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Schedule 8.')
          setData(null)
        }
      })
      .finally(() => {
        if (active) setIsLoading(false)
      })
    return () => {
      active = false
    }
  }, [millId, year, contextMissing, resetBanners])

  // Load the dropdown option lists once — reference data, independent of mill/year. A failure leaves
  // options null (dropdowns show empty lists); it never blocks the schedule read.
  useEffect(() => {
    let active = true
    apiService
      .getAxiosInstance()
      .get<Schedule8Options>('/v1/schedule8/options')
      .then((response) => {
        if (active) {
          setOptions(response.data)
          setOptionsError(null)
        }
      })
      .catch((err) => {
        if (active) {
          setOptions(null)
          setOptionsError(extractDetail(err) || 'Failed to load reference options.')
        }
      })
    return () => {
      active = false
    }
  }, [])

  const openNew = () => {
    clearBanners()
    setPanelMode('new')
    setForm(emptyPageForm())
    setEditId(null)
    setRevision(null)
    setShowErrors(false)
  }

  const openEditOrView = (page: Page, mode: 'edit' | 'view') => {
    clearBanners()
    setPanelMode(mode)
    setForm(seedPageForm(page))
    setEditId(page.id)
    setRevision(page.revisionCount)
    setShowErrors(false)
  }

  const openCopy = (page: Page) => {
    clearBanners()
    setPanelMode('copy')
    setForm(seedPageForm(page))
    setEditId(null)
    setRevision(null)
    setShowErrors(false)
  }

  const closePanel = () => setPanelMode('closed')

  const setField = (field: keyof PageForm) => (event: React.ChangeEvent<HTMLInputElement>) => {
    const { value } = event.target
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  const setComments = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    const { value } = event.target
    setForm((prev) => ({ ...prev, comments: value }))
  }

  const buildRequest = (): Schedule8PageRequest => {
    const tfl = isTflSelected(form)
    return {
      id: panelMode === 'edit' ? editId : null,
      revisionCount: panelMode === 'edit' ? (revision ?? 0) : null,
      license: form.license.trim(),
      supportCentre: form.supportCentre.trim(),
      region: form.region.trim(),
      becZone: form.becZone.trim(),
      tsaNumber: blankToNull(form.tsaNumber),
      tflNumber: tfl ? blankToNull(form.tflNumber) : null,
      supplyBlock: tfl ? null : blankToNull(form.supplyBlock),
      division: blankToNull(form.division),
      contact: blankToNull(form.contact),
      phone: blankToNull(form.phone),
      cuttingPermit: blankToNull(form.cuttingPermit),
      comments: blankToNull(form.comments),
    }
  }

  const handleSave = () => {
    if (saving || panelMode === 'closed' || panelMode === 'view') return
    const validation = validatePageForm(form)
    if (Object.keys(validation).length > 0) {
      setShowErrors(true)
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    clearBanners()
    // Page ids present before the save — used to find a freshly created page (new/copy) in the reply.
    const prevIds = new Set(data.pages.map((p) => p.id))
    // List-shaped write: PUT the /pages list endpoint (no by-id suffix — the id/revision travels in the
    // body). run()'s isCurrent() guard drops the echo if mill/year changed mid-flight (Story 29.6).
    save<Schedule8Response>(buildRequest(), {
      suffix: '/pages',
      fallback: 'Schedule could not be saved.',
      onSuccess: (doc) => {
        setData(doc)
        setSaveMessage(doc.message?.text ?? null)
        // Stay on the saved record (don't close): re-open it in edit mode — by id when editing, or the
        // one new id (new/copy) — refreshing the optimistic-lock token so a follow-up save doesn't 409.
        const saved =
          panelMode === 'edit' && editId !== null
            ? doc.pages.find((p) => p.id === editId)
            : doc.pages.find((p) => p.id != null && !prevIds.has(p.id))
        if (saved && saved.id != null) {
          setPanelMode('edit')
          setEditId(saved.id)
          setRevision(saved.revisionCount ?? 0)
        } else {
          setPanelMode('closed')
        }
      },
    })
  }

  const handleDelete = () => {
    if (saving || !confirmDelete) return
    const target = confirmDelete
    setConfirmDelete(null)
    clearBanners()
    // By-id DELETE; the id/revision travels in the path. Schedule 8 is list-shaped, so (unlike the
    // single-doc pages) it re-GETs after delete — DELETE returns only a message and the list must
    // refresh. The re-GET stays hand-rolled at the call site (Story 29.6 per-page empty-state).
    remove<{ message?: { text?: string } }>({
      suffix: `/pages/${target.id}`,
      fallback: 'Unable to delete page.',
      onSuccess: (resp) => {
        setSaveMessage(resp?.message?.text ?? null)
        setPanelMode('closed')
        // Delete returns only a message — re-read the document so the list reflects the removal.
        run(
          apiService
            .getAxiosInstance()
            .get<Schedule8Response>(`/v1/schedule8?millId=${millId}&year=${year}`),
          {
            fallback: 'Deleted, but the list could not be refreshed.',
            onSuccess: (data) => setData(data),
          },
        )
      },
    })
  }

  const handleCheckStatus = () => {
    if (saving) return
    // The single `saving` lock (shared with save/delete via run()) gates re-entrancy — Schedule 8 had
    // no separate checking flag, so Check Status disables alongside any in-flight write.
    clearBanners()
    checkStatus<Schedule8CheckStatusResponse>({
      fallback: 'Unable to check status.',
      onSuccess: setCheckResult,
    })
  }

  const openSamples = (pageId: number) => {
    clearBanners()
    setPanelMode('closed')
    // Push a history entry (search: pageId) so browser Back returns here to the page list.
    void navigate({ to: '/schedule-8', search: { pageId } })
  }

  const SCH8_BASE = 'Tree to Truck'
  const renderHeader = (trail: string[] = [SCH8_BASE]) => (
    <ScheduleTombstone title="Schedule 8" subtitle={trail} />
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
    return shell(<LoadingScreen label="Loading Schedule 8" />)
  }
  if (errorDetail) {
    return shell(
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title="Unable to load Schedule 8"
        subtitle={errorDetail}
      />,
    )
  }
  if (!data) return null

  const editable = data.editable

  // The samples/rates level is URL-driven (search: pageId + sampleId) so browser Back steps back.
  // Derive it from the search + loaded data; a stale/unknown pageId or sampleId (e.g. after a mill/year
  // change) falls back to the pages list.
  const currentPage =
    search.pageId != null ? data.pages.find((p) => p.id === search.pageId) : undefined
  const currentSample =
    currentPage && search.sampleId != null
      ? currentPage.samples.find((s) => s.id === search.sampleId)
      : undefined
  const nav: NavView =
    currentPage && currentSample
      ? { level: 'rates', pageId: currentPage.id as number, sampleId: currentSample.id as number }
      : currentPage
        ? { level: 'samples', pageId: currentPage.id as number }
        : { level: 'pages' }

  // ---- Sample level replaces the list/panel when open. -------------------------------------------
  if (nav.level === 'samples') {
    const pageIndex = data.pages.findIndex((p) => p.id === nav.pageId)
    const page = pageIndex >= 0 ? data.pages[pageIndex] : undefined
    if (!page) {
      return shell(<InlineNotification kind="warning" lowContrast title="Page not found" />)
    }
    const pageTitle = pageLabel(page, pageIndex)
    return (
      <div className="app-page schedule-page">
        {renderHeader([SCH8_BASE, pageTitle, 'Samples'])}
        <Grid fullWidth className="app-page__body">
          {optionsError && (
            <NotificationColumn
              kind="error"
              title="Reference options failed to load"
              subtitle={optionsError}
            />
          )}
          <Column sm={4} md={8} lg={16}>
            <SamplePage
              millId={millId as number}
              year={year as number}
              page={page}
              pageTitle={pageTitle}
              skidTypes={options?.skidTypes ?? []}
              editable={editable}
              onBack={() => void navigate({ to: '/schedule-8', search: {}, replace: true })}
              onDocUpdate={(doc) => setData(doc)}
              onOpenRates={(sampleId) =>
                void navigate({ to: '/schedule-8', search: { pageId: nav.pageId, sampleId } })
              }
            />
          </Column>
        </Grid>
      </div>
    )
  }

  // ---- Additions/Deductions leaf level. ----------------------------------------------------------
  if (nav.level === 'rates') {
    const page = data.pages.find((p) => p.id === nav.pageId)
    const sample = page?.samples.find((s) => s.id === nav.sampleId)
    if (!page || !sample) {
      return shell(<InlineNotification kind="warning" lowContrast title="Sample not found" />)
    }
    const sampleIndex = page.samples.findIndex((s) => s.id === nav.sampleId)
    const sampleTitle = sampleLabel(sample, sampleIndex)
    return (
      <div className="app-page schedule-page">
        {renderHeader([SCH8_BASE, sampleTitle, 'Additions / Deductions'])}
        <Grid fullWidth className="app-page__body">
          {optionsError && (
            <NotificationColumn
              kind="error"
              title="Reference options failed to load"
              subtitle={optionsError}
            />
          )}
          <Column sm={4} md={8} lg={16}>
            <RatesPage
              millId={millId as number}
              year={year as number}
              sampleId={nav.sampleId}
              sampleTitle={sampleTitle}
              additions={sample.additions}
              deductions={sample.deductions}
              additionCostItems={options?.additionCostItems ?? []}
              deductionCostItems={options?.deductionCostItems ?? []}
              costTypes={options?.costTypes ?? []}
              editable={editable}
              onBack={() =>
                void navigate({ to: '/schedule-8', search: { pageId: nav.pageId }, replace: true })
              }
              onDocUpdate={(doc) => setData(doc)}
            />
          </Column>
        </Grid>
      </div>
    )
  }

  // ---- Page level (list + editor). ---------------------------------------------------------------
  const readOnly = panelMode === 'view'
  const panelOpen = panelMode !== 'closed'
  const errors = showErrors && !readOnly ? validatePageForm(form) : {}
  const tflActive = isTflSelected(form)
  // The page being edited/viewed (has an id); its samples open from inside the panel.
  const panelPage = editId !== null ? data.pages.find((p) => p.id === editId) : undefined

  // Unsaved edits in the page editor (edit mode only): the current form differs from the stored page.
  const pageDirty =
    panelMode === 'edit' && panelPage
      ? JSON.stringify(form) !== JSON.stringify(seedPageForm(panelPage))
      : false

  // Opening the samples from the page editor discards unsaved page edits — confirm first when dirty.
  const requestOpenSamples = (pageId: number) => {
    if (pageDirty) setConfirmSamplesPageId(pageId)
    else openSamples(pageId)
  }

  // The TSA-or-TFL selector lists every TSA plus the legacy 'TFL' marker (unless the code table
  // already carries it). Choosing 'TFL' sets tsaNumber='TFL' → isTflSelected → enables the TFL list
  // and disables the supply-block list (BR-03).
  const tsaNumbers = options?.tsaNumbers ?? []
  const tsaOrTflItems: CodeOption[] = tsaNumbers.some((o) => o.code === 'TFL')
    ? tsaNumbers
    : [...tsaNumbers, { code: 'TFL', description: 'TFL' }]

  const textField = (
    field: keyof PageForm,
    label: string,
    opts: {
      maxLength?: number
      disabled?: boolean
      format?: (value: string) => string
    } = {},
  ) => {
    if (readOnly) {
      const raw = form[field]
      const shown = raw ? (opts.format ? opts.format(raw) : raw) : '—'
      return (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">{label}</span>
          <span>{shown}</span>
        </div>
      )
    }
    // When a formatter is supplied it also normalizes entry live (e.g. phone → 222-222-2222).
    const onChange = opts.format
      ? (event: React.ChangeEvent<HTMLInputElement>) =>
          setForm((prev) => ({ ...prev, [field]: opts.format!(event.target.value) }))
      : setField(field)
    return (
      <TextInput
        id={`page-${field}`}
        labelText={label}
        maxLength={opts.maxLength}
        disabled={opts.disabled}
        // Format the shown value too (not just onChange), so a seeded value (e.g. a stored phone with
        // no dashes) displays formatted on open — phoneInput is idempotent, so this is a no-op once typed.
        value={opts.format ? opts.format(form[field]) : form[field]}
        onChange={onChange}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
      />
    )
  }

  // Code-backed selector: shows each option's description (never the raw code) and writes back the
  // code. View mode renders the resolved description; an unknown/legacy code falls back to itself.
  const dropdownField = (
    field: keyof PageForm,
    label: string,
    items: CodeOption[],
    opts: { disabled?: boolean; onChange?: (code: string) => void; className?: string } = {},
  ) => {
    const current = form[field]
    if (readOnly) {
      const selected = items.find((option) => option.code === current) ?? null
      return (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">{label}</span>
          <span>{selected?.description || current || '—'}</span>
        </div>
      )
    }
    // A stored code that the reference/options list doesn't carry (e.g. a legacy TFL/TSA code no longer
    // in its code table) would otherwise resolve to null and the Dropdown would show the empty "Select"
    // placeholder, silently dropping the saved value. Surface it as its own (code-labelled) option so
    // the field still shows what's stored instead of appearing unselected.
    const itemList =
      current && !items.some((option) => option.code === current)
        ? [...items, { code: current, description: current }]
        : items
    return (
      <CodeComboBox
        id={`page-${field}`}
        className={opts.className}
        titleText={label}
        items={itemList}
        selectedCode={current}
        onSelect={(code) =>
          opts.onChange ? opts.onChange(code) : setForm((prev) => ({ ...prev, [field]: code }))
        }
        disabled={opts.disabled}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
      />
    )
  }

  const pagesTable = (
    <TableContainer title="Page Summary">
      <Table aria-label="Page Summary">
        <TableHead>
          <TableRow>
            <TableHeader>Tree to Truck Pages</TableHeader>
            <TableHeader>Actions</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.pages.length === 0 ? (
            <TableRow>
              <TableCell colSpan={2}>No pages have been added.</TableCell>
            </TableRow>
          ) : (
            data.pages.map((page, index) => (
              <TableRow
                key={page.id}
                className={
                  panelOpen && page.id != null && page.id === editId
                    ? 'schedule-8__row--editing'
                    : undefined
                }
              >
                <TableCell>{pageLabel(page, index)}</TableCell>
                <TableCell>
                  <div className="schedule-8__row-actions">
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={editable ? Edit : View}
                      onClick={() => openEditOrView(page, editable ? 'edit' : 'view')}
                    >
                      {editable ? 'Edit' : 'View'}
                    </Button>
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={Copy}
                      disabled={!editable || saving}
                      onClick={() => openCopy(page)}
                    >
                      Copy
                    </Button>
                    <Button
                      kind="danger--tertiary"
                      size="sm"
                      renderIcon={TrashCan}
                      disabled={!editable || saving}
                      onClick={() => setConfirmDelete(page)}
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

  const panel = panelOpen && (
    <div className="schedule-8__panel">
      <h3 className="schedule-8__heading">
        {panelMode === 'new' && 'New Page'}
        {panelMode === 'edit' &&
          (panelPage
            ? `Edit Page — ${pageLabel(
                panelPage,
                data.pages.findIndex((p) => p.id === editId),
              )}`
            : 'Edit Page')}
        {panelMode === 'copy' && 'Copy Page'}
        {panelMode === 'view' && 'View Page'}
      </h3>

      <div className="schedule-8__fields">
        {textField('division', 'Division', { maxLength: 30 })}
        {textField('license', 'License', { maxLength: 8 })}
        {textField('contact', 'Contact', { maxLength: 50 })}
        {textField('phone', 'Phone', { maxLength: 12, format: phoneInput })}
        {textField('cuttingPermit', 'Cutting Permit', { maxLength: 10 })}
      </div>

      <div className="schedule-8__fields">
        {dropdownField('supportCentre', 'Support Centre', options?.supportCentres ?? [])}
        {dropdownField('region', 'Region', options?.regions ?? [])}
        {dropdownField('becZone', 'Biogeoclimatic Zone', options?.becZones ?? [])}
        {dropdownField('tsaNumber', 'TSA or TFL', tsaOrTflItems, {
          className: 'schedule-8__tsa-tfl',
          onChange: (code) =>
            setForm((prev) => {
              const next = { ...prev, tsaNumber: code }
              if (code === 'TFL') {
                next.supplyBlock = ''
              } else {
                next.tflNumber = ''
              }
              return next
            }),
        })}
        {/* TFL is a free-text 2-char code (legacy ILCRTflNumberValidator, ILCR-161: NOT restricted to
            the TFL_NUMBER_CODE table), enabled only when the TSA-or-TFL selector holds 'TFL'. */}
        {textField('tflNumber', 'TFL', { maxLength: 2, disabled: !tflActive })}
        {dropdownField('supplyBlock', 'Supply Block', options?.supplyBlocks ?? [], {
          disabled: tflActive,
        })}
        {/* A saved page's Tree-to-Truck samples open from inside the page (not the list Actions);
            sits beside Supply Block, aligned to the bottom of the selector row. */}
        {editId !== null && (
          <Button
            kind="ghost"
            size="sm"
            className="schedule-8__samples-action"
            disabled={saving}
            // Navigational, like the other "open a level" buttons — it drills into the samples level.
            renderIcon={ArrowRight}
            onClick={() => requestOpenSamples(editId)}
          >
            TtT Samples ({panelPage?.sampleCount ?? 0})
          </Button>
        )}
      </div>

      {readOnly ? (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">
            If you have any additional comments, please enter them here:
          </span>
          <span>{form.comments || '—'}</span>
        </div>
      ) : (
        <CommentsTextArea
          id="page-comments"
          labelText="If you have any additional comments, please enter them here:"
          maxCount={3500}
          value={form.comments}
          onChange={setComments}
        />
      )}

      {/* Save feedback shown in the panel (next to Save) so it's visible where the user is acting —
          the panel opens below the table, far from the page-top notifications. */}
      {saveMessage && (
        <InlineNotification kind="success" lowContrast title="Success" subtitle={saveMessage} />
      )}
      {saveError && (
        <InlineNotification kind="error" lowContrast title="Action failed" subtitle={saveError} />
      )}

      <div className="schedule-8__panel-actions">
        {!readOnly && (
          <Button kind="primary" disabled={saving} renderIcon={Save} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button
          kind="secondary"
          disabled={saving}
          renderIcon={readOnly ? Close : ArrowLeft}
          onClick={closePanel}
        >
          {readOnly ? 'Close' : 'Back'}
        </Button>
      </div>
    </div>
  )

  return (
    <div className="app-page schedule-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {optionsError && (
          <NotificationColumn
            kind="error"
            title="Reference options failed to load"
            subtitle={optionsError}
          />
        )}
        {/* When the editor panel is open its own copy (above Save) carries the save feedback. */}
        {!panelOpen && saveMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={saveMessage} />
        )}
        {!panelOpen && saveError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={saveError} />
        )}
        {checkResult && (
          <Column sm={4} md={8} lg={16} className="schedule-8__check">
            <CheckStatusResult result={checkResult} />
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-8__actions">
          <Button kind="primary" renderIcon={Add} disabled={!editable || saving} onClick={openNew}>
            Add New Page
          </Button>
          <Button
            kind="tertiary"
            renderIcon={CheckmarkOutline}
            disabled={saving}
            onClick={handleCheckStatus}
          >
            Check Status
          </Button>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-8__section">
          {pagesTable}
        </Column>

        {panel && (
          <Column sm={4} md={8} lg={16} className="schedule-8__section">
            {panel}
          </Column>
        )}
      </Grid>

      {editable && (
        <Modal
          open={confirmDelete !== null}
          danger
          modalHeading="Delete page"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDelete(null)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}

      {editable && (
        <Modal
          open={confirmSamplesPageId !== null}
          modalHeading="Unsaved changes"
          primaryButtonText="Continue"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmSamplesPageId(null)}
          onRequestSubmit={() => {
            const pageId = confirmSamplesPageId
            setConfirmSamplesPageId(null)
            if (pageId !== null) openSamples(pageId)
          }}
        >
          <p>Unsaved data will be lost. Are you sure to continue?</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule8
