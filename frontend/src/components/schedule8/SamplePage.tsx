import type { FC } from 'react'
import type Schedule8Response from '@/interfaces/Schedule8Response'
import type { Page, Sample, Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import type { Schedule8SampleRequest } from '@/interfaces/Schedule8Request'
import type { CodeOption } from '@/interfaces/Schedule8Options'
import { useState } from 'react'
import {
  Button,
  InlineNotification,
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
  TextInput,
  Tooltip,
} from '@carbon/react'
import { Add, CheckmarkOutline, Information } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import { blankToNull } from '@/utils/forms'
import {
  emptySampleForm,
  fmt,
  liveActualHarvested,
  seedSampleForm,
  skiddingTotal,
  toNum,
  validateSampleForm,
  type SampleForm,
} from './validation'
import CheckStatusResult from './CheckStatusResult'
import CodeComboBox from '@/components/core/CodeComboBox'

const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const NAV_UNSAVED = 'Unsaved data will be lost. Are you sure to continue?'

type PanelMode = 'closed' | 'new' | 'edit' | 'copy' | 'view'

interface SamplePageProps {
  millId: number
  year: number
  page: Page
  /** The parent page's composite label (e.g. "Page # 1  -TSA: TFL -CP: cp123") for the breadcrumb. */
  pageTitle: string
  /** Skid-type options (code + description) for the Other skid-type dropdown. */
  skidTypes: CodeOption[]
  editable: boolean
  onBack: () => void
  onDocUpdate: (doc: Schedule8Response) => void
  onOpenRates: (sampleId: number) => void
}

// The legacy Tree-to-Truck sample label (TreeToTruckDetailReportDO): the 1-based row number and the
// contract id — e.g. "Sample # 1 - one". A blank contract id leaves the trailing "- " (legacy parity).
const sampleLabel = (sample: Sample, index: number): string => {
  const contract = sample.contractId && sample.contractId.trim() !== '' ? sample.contractId : ''
  return `Sample # ${index + 1} - ${contract}`
}

/**
 * The Schedule 8 sample level (Story 14.3, S03/S05/S08) for one saved report page: the samples table
 * (Edit/Copy/Delete/View + Add New Sample), the sample editor (six skidding %s with a live Total, the
 * conditional Helicopter + Other + Skyline sub-blocks, volumes with computed Actual Harvested, the
 * read-only Original/Additions/Deductions/Final rate roll-up + Additions/Deductions count links), and
 * a single-page Check Status button (S14, scoped to this page — 14.6). Save/Delete lift the recomputed
 * document up via onDocUpdate. Read-only when the schedule is not editable (STA-001).
 */
const SamplePage: FC<SamplePageProps> = ({
  millId,
  year,
  page,
  pageTitle,
  skidTypes,
  editable,
  onBack,
  onDocUpdate,
  onOpenRates,
}) => {
  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [form, setForm] = useState<SampleForm>(() => emptySampleForm())
  const [editId, setEditId] = useState<number | null>(null)
  const [revision, setRevision] = useState<number | null>(null)
  const [showErrors, setShowErrors] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [checkResult, setCheckResult] = useState<Schedule8CheckStatusResponse | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Sample | null>(null)
  const [confirmBack, setConfirmBack] = useState(false)

  const pageId = page.id as number
  const samples = page.samples
  // The open sample's stored record (for the read-only computed roll-up in the editor).
  const openSample = editId !== null ? samples.find((s) => s.id === editId) : undefined

  const clearMessages = () => {
    setMessage(null)
    setError(null)
    setCheckResult(null)
  }

  const openNew = () => {
    clearMessages()
    setPanelMode('new')
    setForm(emptySampleForm())
    setEditId(null)
    setRevision(null)
    setShowErrors(false)
  }

  const openEditOrView = (sample: Sample, mode: 'edit' | 'view') => {
    clearMessages()
    setPanelMode(mode)
    setForm(seedSampleForm(sample))
    setEditId(sample.id)
    setRevision(sample.revisionCount)
    setShowErrors(false)
  }

  const openCopy = (sample: Sample) => {
    clearMessages()
    setPanelMode('copy')
    setForm(seedSampleForm(sample))
    setEditId(null)
    setRevision(null)
    setShowErrors(false)
  }

  const closePanel = () => setPanelMode('closed')

  const requestBack = () => {
    if (editable && panelMode !== 'closed' && panelMode !== 'view') setConfirmBack(true)
    else onBack()
  }

  const setField = (field: keyof SampleForm) => (event: React.ChangeEvent<HTMLInputElement>) => {
    const { value } = event.target
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  const setSelect = (field: keyof SampleForm) => (event: React.ChangeEvent<HTMLSelectElement>) => {
    const { value } = event.target
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  const buildRequest = (): Schedule8SampleRequest => ({
    id: panelMode === 'edit' ? editId : null,
    revisionCount: panelMode === 'edit' ? (revision ?? 0) : null,
    contractId: form.contractId.trim(),
    cutBlock: blankToNull(form.cutBlock),
    groundBasePct: toNum(form.groundBasePct),
    grapplePct: toNum(form.grapplePct),
    skylinePct: toNum(form.skylinePct),
    highleadPct: toNum(form.highleadPct),
    helicopterPct: toNum(form.helicopterPct),
    otherSkiddingPct: toNum(form.otherSkiddingPct),
    skylineSlopeDistance: toNum(form.skylineSlopeDistance),
    skylineSupportNumber: toNum(form.skylineSupportNumber),
    supportAvgDistance: toNum(form.supportAvgDistance),
    cycleTime: toNum(form.cycleTime),
    distance: toNum(form.distance),
    uphillDirection: form.uphillDirection === '' ? null : form.uphillDirection === 'Y',
    waterDumpDestination:
      form.waterDumpDestination === '' ? null : form.waterDumpDestination === 'Y',
    skidTypeCode: blankToNull(form.skidTypeCode),
    coniferousVolume: toNum(form.coniferousVolume),
    deciduousVolume: toNum(form.deciduousVolume),
    originalRate: toNum(form.originalRate),
  })

  const handleSave = () => {
    if (busy || panelMode === 'closed' || panelMode === 'view') return
    const validation = validateSampleForm(form)
    if (Object.keys(validation).length > 0) {
      setShowErrors(true)
      setError('Please correct the highlighted fields before saving.')
      return
    }
    setBusy(true)
    clearMessages()
    // Sample ids present before the save — used to find a freshly created sample (new/copy) in the reply.
    const prevIds = new Set(samples.map((s) => s.id))
    apiService
      .getAxiosInstance()
      .put<Schedule8Response>(
        `/v1/schedule8/pages/${pageId}/samples?millId=${millId}&year=${year}`,
        buildRequest(),
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
        // Stay on the saved record (don't close): re-open it in edit mode — by id when editing, or the
        // one new id (new/copy) — refreshing the optimistic-lock token so a follow-up save doesn't 409.
        const pageSamples = response.data.pages.find((p) => p.id === pageId)?.samples ?? []
        const saved =
          panelMode === 'edit' && editId !== null
            ? pageSamples.find((s) => s.id === editId)
            : pageSamples.find((s) => s.id != null && !prevIds.has(s.id))
        if (saved && saved.id != null) {
          setPanelMode('edit')
          setEditId(saved.id)
          setRevision(saved.revisionCount ?? 0)
        } else {
          setPanelMode('closed')
        }
      })
      .catch((err: unknown) => setError(extractDetail(err) || 'Sample could not be saved.'))
      .finally(() => setBusy(false))
  }

  const handleDelete = () => {
    if (busy || !confirmDelete) return
    const target = confirmDelete
    setConfirmDelete(null)
    setBusy(true)
    clearMessages()
    apiService
      .getAxiosInstance()
      .delete<Schedule8Response>(
        `/v1/schedule8/pages/${pageId}/samples/${target.id}?millId=${millId}&year=${year}`,
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
        setPanelMode('closed')
      })
      .catch((err: unknown) => setError(extractDetail(err) || 'Unable to delete sample.'))
      .finally(() => setBusy(false))
  }

  const handleCheckStatus = () => {
    if (busy) return
    setBusy(true) // gate re-entrancy: disables the button and blocks overlapping check-status posts
    clearMessages()
    apiService
      .getAxiosInstance()
      .post<Schedule8CheckStatusResponse>(
        `/v1/schedule8/pages/${pageId}/check-status?millId=${millId}&year=${year}`,
      )
      .then((response) => setCheckResult(response.data))
      .catch((err: unknown) => setError(extractDetail(err) || 'Unable to check status.'))
      .finally(() => setBusy(false))
  }

  const readOnly = panelMode === 'view'
  const panelOpen = panelMode !== 'closed'
  const errors = showErrors && !readOnly ? validateSampleForm(form) : {}

  // ---- Editor field helpers ----------------------------------------------------------------------
  // A field label with an optional info tooltip carrying the legacy "Note:" entry hint (hover/focus).
  const fieldLabel = (label: string, note?: string) =>
    note ? (
      <span className="schedule-8__label-note">
        {label}
        <Tooltip label={note} align="top">
          <button type="button" className="schedule-8__note-trigger" aria-label={note}>
            <Information />
          </button>
        </Tooltip>
      </span>
    ) : (
      label
    )

  const numberField = (field: keyof SampleForm, label: string, note?: string) => {
    if (readOnly) {
      return (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">{fieldLabel(label, note)}</span>
          <span>{form[field] === '' ? '—' : form[field]}</span>
        </div>
      )
    }
    return (
      <TextInput
        id={`sample-${field}`}
        labelText={fieldLabel(label, note)}
        size="sm"
        inputMode="numeric"
        value={form[field]}
        onChange={setField(field)}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
      />
    )
  }

  const ynSelect = (
    field: 'uphillDirection' | 'waterDumpDestination',
    label: string,
    yes: string,
    no: string,
  ) => {
    if (readOnly) {
      const text = form[field] === 'Y' ? yes : form[field] === 'N' ? no : '—'
      return (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">{label}</span>
          <span>{text}</span>
        </div>
      )
    }
    return (
      <Select
        id={`sample-${field}`}
        labelText={label}
        size="sm"
        value={form[field]}
        onChange={setSelect(field)}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
      >
        <SelectItem value="" text="—" />
        <SelectItem value="Y" text={yes} />
        <SelectItem value="N" text={no} />
      </Select>
    )
  }

  const computedField = (label: string, value: number | null | undefined) => (
    <div className="schedule-8__field">
      <span className="schedule-8__field-label">{label}</span>
      <span className="schedule-8__field-value">{fmt(value)}</span>
    </div>
  )

  // ---- Samples table -----------------------------------------------------------------------------
  const samplesTable = (
    <TableContainer title={`Samples (${samples.length})`}>
      <Table aria-label="Samples">
        <TableHead>
          <TableRow>
            {/* Legacy samples list (schedule8Detail.xhtml) uses this exact "Tree To Truck Pages"
                header + singular "Action" — kept verbatim. */}
            <TableHeader>Tree to Truck Pages</TableHeader>
            <TableHeader>Action</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {samples.length === 0 ? (
            <TableRow>
              <TableCell colSpan={2}>No samples have been added.</TableCell>
            </TableRow>
          ) : (
            samples.map((sample, index) => (
              <TableRow
                key={sample.id}
                className={
                  panelOpen && sample.id != null && sample.id === editId
                    ? 'schedule-8__row--editing'
                    : undefined
                }
              >
                <TableCell>{sampleLabel(sample, index)}</TableCell>
                <TableCell>
                  <div className="schedule-8__row-actions">
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() => openEditOrView(sample, editable ? 'edit' : 'view')}
                    >
                      {editable ? 'Edit' : 'View'}
                    </Button>
                    <Button
                      kind="ghost"
                      size="sm"
                      disabled={!editable || busy}
                      onClick={() => openCopy(sample)}
                    >
                      Copy
                    </Button>
                    <Button
                      kind="danger--ghost"
                      size="sm"
                      disabled={!editable || busy}
                      onClick={() => setConfirmDelete(sample)}
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

  // ---- Sample editor panel -----------------------------------------------------------------------
  const panel = panelOpen && (
    <div className="schedule-8__panel">
      <h3 className="schedule-8__heading">
        {panelMode === 'new' && 'New Sample'}
        {panelMode === 'edit' &&
          (openSample
            ? `Edit Sample — ${sampleLabel(
                openSample,
                samples.findIndex((s) => s.id === editId),
              )}`
            : 'Edit Sample')}
        {panelMode === 'copy' && 'Copy Sample'}
        {panelMode === 'view' && 'View Sample'}
      </h3>

      <div className="schedule-8__fields">
        {readOnly ? (
          <div className="schedule-8__field">
            <span className="schedule-8__field-label">Contract ID</span>
            <span>{form.contractId || '—'}</span>
          </div>
        ) : (
          <TextInput
            id="sample-contractId"
            labelText="Contract ID"
            maxLength={12}
            value={form.contractId}
            onChange={setField('contractId')}
            invalid={Boolean(errors.contractId)}
            invalidText={errors.contractId}
          />
        )}
        {readOnly ? (
          <div className="schedule-8__field">
            <span className="schedule-8__field-label">Cut Block</span>
            <span>{form.cutBlock || '—'}</span>
          </div>
        ) : (
          <TextInput
            id="sample-cutBlock"
            labelText="Cut Block"
            maxLength={12}
            value={form.cutBlock}
            onChange={setField('cutBlock')}
          />
        )}
      </div>

      {/* Legacy sectioning (schedule8EditDetail.xhtml): each skidding system is its own section with
          its associated detail fields; Skyline/Helicopter are always shown (their fields become
          required only when the matching % is non-zero, enforced in validateSampleForm). */}
      <h4 className="schedule-8__subheading schedule-8__section-start">Skidding / Yarding</h4>
      <div className="schedule-8__fields">
        {numberField('groundBasePct', 'Ground Base %')}
        {numberField('grapplePct', 'Grapple %')}
        {numberField('highleadPct', 'Highlead %')}
      </div>

      <h4 className="schedule-8__subheading schedule-8__section-start">Skyline Support</h4>
      <div className="schedule-8__fields">
        {numberField('skylinePct', 'Skyline %')}
        {numberField('skylineSlopeDistance', 'Slope Distance (m)')}
        {numberField('skylineSupportNumber', 'Support Number', 'enter number')}
        {numberField('supportAvgDistance', 'Support Avg Distance (m)', 'enter number - average')}
      </div>

      <h4 className="schedule-8__subheading schedule-8__section-start">Helicopter</h4>
      <div className="schedule-8__fields">
        {numberField('helicopterPct', 'Helicopter %')}
        {numberField('distance', 'Distance (km)')}
        {numberField('cycleTime', 'Cycle Time (min)')}
        {ynSelect('uphillDirection', 'Direction', 'Uphill', 'Downhill')}
        {ynSelect('waterDumpDestination', 'Dump Destination', 'Water Dump', 'Land Dump')}
      </div>

      <h4 className="schedule-8__subheading schedule-8__section-start">Other</h4>
      <div className="schedule-8__fields">
        {readOnly ? (
          <div className="schedule-8__field">
            <span className="schedule-8__field-label">Skid Type</span>
            <span>
              {skidTypes.find((o) => o.code === form.skidTypeCode)?.description ||
                form.skidTypeCode ||
                '—'}
            </span>
          </div>
        ) : (
          <CodeComboBox
            id="sample-skidTypeCode"
            titleText="Skid Type"
            items={skidTypes}
            selectedCode={form.skidTypeCode}
            invalid={Boolean(errors.skidTypeCode)}
            invalidText={errors.skidTypeCode}
            onSelect={(code) => setForm((prev) => ({ ...prev, skidTypeCode: code }))}
          />
        )}
        {numberField('otherSkiddingPct', 'Other %')}
      </div>

      <h4 className="schedule-8__subheading schedule-8__section-start">Total</h4>
      <div className="schedule-8__fields">{computedField('Total %', skiddingTotal(form))}</div>
      {errors.percentTotal && (
        <InlineNotification
          kind="error"
          lowContrast
          hideCloseButton
          title="Skidding / Yarding"
          subtitle={errors.percentTotal}
        />
      )}

      <h4 className="schedule-8__subheading schedule-8__section-start">Harvested Volumes</h4>
      <div className="schedule-8__fields">
        {numberField('coniferousVolume', 'Coniferous Volume (m³)')}
        {numberField('deciduousVolume', 'Deciduous Volume (m³)')}
        {computedField('Actual Harvested (m³)', liveActualHarvested(form))}
      </div>

      <h4 className="schedule-8__subheading schedule-8__section-start">Rate</h4>
      <div className="schedule-8__fields">
        {numberField('originalRate', 'Original TtT Rate')}
        {openSample && editId !== null ? (
          <>
            {/* Additions/Deductions: label row is a link into the rate detail (with its count); the
                total sits on the value row below it, in line with the other data. */}
            <div className="schedule-8__field">
              <Button
                kind="ghost"
                size="sm"
                className="schedule-8__rate-link"
                onClick={() => onOpenRates(editId)}
              >
                Additions ({openSample.additionCount}):
              </Button>
              <span className="schedule-8__field-value">{fmt(openSample.additionsTotal)}</span>
            </div>
            <div className="schedule-8__field">
              <Button
                kind="ghost"
                size="sm"
                className="schedule-8__rate-link"
                onClick={() => onOpenRates(editId)}
              >
                Deductions ({openSample.deductionCount}):
              </Button>
              <span className="schedule-8__field-value">{fmt(openSample.deductionsTotal)}</span>
            </div>
          </>
        ) : (
          <>
            {computedField('Additions', openSample?.additionsTotal)}
            {computedField('Deductions', openSample?.deductionsTotal)}
          </>
        )}
        {computedField('Final TtT Rate', openSample?.finalRate)}
      </div>

      {/* Save feedback shown in the panel (next to the Save button) so it's visible where the user is
          acting — the panel opens below the table, far from the page-top notifications. */}
      {message && (
        <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
      )}
      {error && (
        <InlineNotification kind="error" lowContrast title="Action failed" subtitle={error} />
      )}

      <div className="schedule-8__panel-actions">
        {!readOnly && (
          <Button kind="primary" disabled={busy} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button kind="secondary" disabled={busy} onClick={closePanel}>
          {readOnly ? 'Close' : 'Back'}
        </Button>
      </div>
    </div>
  )

  return (
    <div className="schedule-8__level">
      <div className="schedule-8__level-header">
        <h3 className="schedule-8__heading">{pageTitle} → Samples</h3>
      </div>

      {/* Page-level feedback (Check Status / delete) shows here when no editor panel is open; while the
          panel is open its own copy (above the Save button) carries the save feedback instead. */}
      {!panelOpen && message && (
        <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
      )}
      {!panelOpen && error && (
        <InlineNotification kind="error" lowContrast title="Action failed" subtitle={error} />
      )}
      {checkResult && (
        <div className="schedule-8__check">
          <CheckStatusResult result={checkResult} />
        </div>
      )}

      <div className="schedule-8__actions">
        <Button kind="secondary" onClick={requestBack}>
          Back to pages
        </Button>
        <Button kind="primary" renderIcon={Add} disabled={!editable || busy} onClick={openNew}>
          Add New Sample
        </Button>
        <Button
          kind="tertiary"
          renderIcon={CheckmarkOutline}
          disabled={busy}
          onClick={handleCheckStatus}
        >
          Check Status
        </Button>
      </div>

      <div className="schedule-8__section">{samplesTable}</div>
      {panel && <div className="schedule-8__section">{panel}</div>}

      {editable && (
        <Modal
          open={confirmDelete !== null}
          danger
          modalHeading="Delete sample"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDelete(null)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}

      <Modal
        open={confirmBack}
        modalHeading="Unsaved changes"
        primaryButtonText="Continue"
        secondaryButtonText="Cancel"
        onRequestClose={() => setConfirmBack(false)}
        onRequestSubmit={() => {
          setConfirmBack(false)
          onBack()
        }}
      >
        <p>{NAV_UNSAVED}</p>
      </Modal>
    </div>
  )
}

export default SamplePage
