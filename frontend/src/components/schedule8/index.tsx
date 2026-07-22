import type { FC } from 'react'
import type Schedule8Response from '@/interfaces/Schedule8Response'
import type { Page, Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import type { Schedule8PageRequest } from '@/interfaces/Schedule8Request'
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
  TextArea,
  TextInput,
} from '@carbon/react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'
import {
  emptyPageForm,
  isTflSelected,
  seedPageForm,
  validatePageForm,
  type PageForm,
} from './validation'
import SamplePage from './SamplePage'
import RatesPage from './RatesPage'
import './index.scss'

// Client-only chrome (no request behind it). All success/error text comes from the API
// message.text / ProblemDetail.detail — never hardcoded (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'

type PanelMode = 'closed' | 'new' | 'edit' | 'copy' | 'view'
// In-component level switching (the three-level tree): the page list/editor, then a page's samples,
// then a sample's additions/deductions.
type NavView =
  | { level: 'pages' }
  | { level: 'samples'; pageId: number }
  | { level: 'rates'; pageId: number; sampleId: number }

const blankToNull = (raw: string): string | null => (raw.trim() === '' ? null : raw)

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    return (error as { response?: { data?: { detail?: string } } }).response?.data?.detail
  }
  return undefined
}

const Schedule8: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<Schedule8Response | null>(null)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<Schedule8CheckStatusResponse | null>(null)

  const [nav, setNav] = useState<NavView>({ level: 'pages' })

  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [form, setForm] = useState<PageForm>(() => emptyPageForm())
  const [editId, setEditId] = useState<number | null>(null)
  const [revision, setRevision] = useState<number | null>(null)
  const [showErrors, setShowErrors] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<Page | null>(null)

  useEffect(() => {
    if (contextMissing) return
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    setSaveMessage(null)
    setSaveError(null)
    setCheckResult(null)
    setPanelMode('closed')
    setNav({ level: 'pages' })
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
  }, [millId, year, contextMissing])

  const clearMessages = () => {
    setSaveMessage(null)
    setSaveError(null)
    setCheckResult(null)
  }

  const openNew = () => {
    clearMessages()
    setPanelMode('new')
    setForm(emptyPageForm())
    setEditId(null)
    setRevision(null)
    setShowErrors(false)
  }

  const openEditOrView = (page: Page, mode: 'edit' | 'view') => {
    clearMessages()
    setPanelMode(mode)
    setForm(seedPageForm(page))
    setEditId(page.id)
    setRevision(page.revisionCount)
    setShowErrors(false)
  }

  const openCopy = (page: Page) => {
    clearMessages()
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
      license: form.license,
      supportCentre: form.supportCentre,
      region: form.region,
      becZone: form.becZone,
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
    setSaving(true)
    clearMessages()
    apiService
      .getAxiosInstance()
      .put<Schedule8Response>(`/v1/schedule8/pages?millId=${millId}&year=${year}`, buildRequest())
      .then((response) => {
        setData(response.data)
        setSaveMessage(response.data.message?.text ?? null)
        setPanelMode('closed')
      })
      .catch((error: unknown) =>
        setSaveError(extractDetail(error) || 'Schedule could not be saved.'),
      )
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
        `/v1/schedule8/pages/${target.id}?millId=${millId}&year=${year}`,
      )
      .then((response) => {
        setSaveMessage(response.data?.message?.text ?? null)
        setPanelMode('closed')
        // Delete returns only a message — re-read the document so the list reflects the removal.
        return apiService
          .getAxiosInstance()
          .get<Schedule8Response>(`/v1/schedule8?millId=${millId}&year=${year}`)
          .then((reload) => setData(reload.data))
      })
      .catch((error: unknown) => setSaveError(extractDetail(error) || 'Unable to delete page.'))
      .finally(() => setSaving(false))
  }

  const handleCheckStatus = () => {
    if (saving) return
    clearMessages()
    apiService
      .getAxiosInstance()
      .post<Schedule8CheckStatusResponse>(
        `/v1/schedule8/check-status?millId=${millId}&year=${year}`,
      )
      .then((response) => setCheckResult(response.data))
      .catch((error: unknown) => setSaveError(extractDetail(error) || 'Unable to check status.'))
  }

  const openSamples = (pageId: number) => {
    clearMessages()
    setPanelMode('closed')
    setNav({ level: 'samples', pageId })
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle title="Schedule 8" subtitle="Report Tree to Truck Costs." />
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

  // ---- Sample level replaces the list/panel when open. -------------------------------------------
  if (nav.level === 'samples') {
    const page = data.pages.find((p) => p.id === nav.pageId)
    if (!page) {
      return shell(<InlineNotification kind="warning" lowContrast title="Page not found" />)
    }
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <SamplePage
              millId={millId as number}
              year={year as number}
              page={page}
              editable={editable}
              onBack={() => setNav({ level: 'pages' })}
              onDocUpdate={(doc) => setData(doc)}
              onOpenRates={(sampleId) => setNav({ level: 'rates', pageId: nav.pageId, sampleId })}
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
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <RatesPage
              millId={millId as number}
              year={year as number}
              sampleId={nav.sampleId}
              sampleTitle={sample.contractId ?? `Sample ${sample.id}`}
              additions={sample.additions}
              deductions={sample.deductions}
              editable={editable}
              onBack={() => setNav({ level: 'samples', pageId: nav.pageId })}
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

  const textField = (
    field: keyof PageForm,
    label: string,
    opts: { maxLength?: number; disabled?: boolean } = {},
  ) => {
    if (readOnly) {
      return (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">{label}</span>
          <span>{form[field] || '—'}</span>
        </div>
      )
    }
    return (
      <TextInput
        id={`page-${field}`}
        labelText={label}
        maxLength={opts.maxLength}
        disabled={opts.disabled}
        value={form[field]}
        onChange={setField(field)}
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
            <TableHeader>Tree To Truck Pages</TableHeader>
            <TableHeader>Support Centre</TableHeader>
            <TableHeader>Region</TableHeader>
            <TableHeader>Samples</TableHeader>
            <TableHeader>Actions</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.pages.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5}>No pages have been added.</TableCell>
            </TableRow>
          ) : (
            data.pages.map((page) => (
              <TableRow key={page.id}>
                <TableCell>{page.license ?? `Page ${page.id}`}</TableCell>
                <TableCell>{page.supportCentreLabel ?? page.supportCentre ?? '—'}</TableCell>
                <TableCell>{page.regionLabel ?? page.region ?? '—'}</TableCell>
                <TableCell>
                  <Button
                    kind="ghost"
                    size="sm"
                    disabled={saving}
                    onClick={() => openSamples(page.id as number)}
                  >
                    TtT Samples ({page.sampleCount})
                  </Button>
                </TableCell>
                <TableCell>
                  <div className="schedule-8__row-actions">
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() => openEditOrView(page, editable ? 'edit' : 'view')}
                    >
                      {editable ? 'Edit' : 'View'}
                    </Button>
                    <Button
                      kind="ghost"
                      size="sm"
                      disabled={!editable || saving}
                      onClick={() => openCopy(page)}
                    >
                      Copy
                    </Button>
                    <Button
                      kind="danger--ghost"
                      size="sm"
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
        {panelMode === 'edit' && 'Edit Page'}
        {panelMode === 'copy' && 'Copy Page'}
        {panelMode === 'view' && 'View Page'}
      </h3>

      <div className="schedule-8__fields">
        {textField('division', 'Division', { maxLength: 30 })}
        {textField('license', 'License', { maxLength: 8 })}
        {textField('contact', 'Contact', { maxLength: 50 })}
        {textField('phone', 'Phone', { maxLength: 12 })}
        {textField('cuttingPermit', 'Cutting Permit', { maxLength: 10 })}
      </div>

      <div className="schedule-8__fields">
        {textField('supportCentre', 'Support Centre')}
        {textField('region', 'Region')}
        {textField('becZone', 'Biogeoclimatic Zone')}
        {textField('tsaNumber', 'TSA or TFL')}
        {textField('tflNumber', 'TFL', { maxLength: 2, disabled: !tflActive })}
        {textField('supplyBlock', 'Supply Block', { disabled: tflActive })}
      </div>

      {readOnly ? (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">Comments</span>
          <span>{form.comments || '—'}</span>
        </div>
      ) : (
        <TextArea
          id="page-comments"
          labelText="Comments"
          maxLength={3500}
          value={form.comments}
          onChange={setComments}
        />
      )}

      <div className="schedule-8__panel-actions">
        {!readOnly && (
          <Button kind="primary" disabled={saving} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button kind="secondary" disabled={saving} onClick={closePanel}>
          {readOnly ? 'Close' : 'Cancel'}
        </Button>
      </div>
    </div>
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16} className="schedule-8__meta">
          <dl className="schedule-8__summary">
            <div className="schedule-8__summary-item">
              <dt>Mill</dt>
              <dd>{data.millId}</dd>
            </div>
            <div className="schedule-8__summary-item">
              <dt>Reporting Year</dt>
              <dd>{data.year}</dd>
            </div>
            <div className="schedule-8__summary-item">
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
        {checkResult && (
          <Column sm={4} md={8} lg={16} className="schedule-8__check">
            {checkResult.messages.map((msg) => (
              <InlineNotification
                key={`sch-${msg.key}`}
                kind="success"
                lowContrast
                title="Check Status"
                subtitle={msg.text}
              />
            ))}
            {checkResult.pages.flatMap((page) => [
              ...page.issues.map((issue) => (
                <InlineNotification
                  key={`page-${page.id}-${issue.field}`}
                  kind="warning"
                  lowContrast
                  title={`Page — ${issue.field}`}
                  subtitle={issue.message.text}
                />
              )),
              ...page.samples.flatMap((sample) =>
                sample.issues.map((issue) => (
                  <InlineNotification
                    key={`sample-${sample.id}-${issue.field}`}
                    kind="warning"
                    lowContrast
                    title={`Sample — ${issue.field}`}
                    subtitle={issue.message.text}
                  />
                )),
              ),
            ])}
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-8__actions">
          <Button kind="primary" disabled={!editable || saving || panelOpen} onClick={openNew}>
            Add New Page
          </Button>
          <Button kind="tertiary" disabled={saving} onClick={handleCheckStatus}>
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
    </div>
  )
}

export default Schedule8
