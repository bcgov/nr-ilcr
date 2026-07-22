import type { FC } from 'react'
import type Schedule8Response from '@/interfaces/Schedule8Response'
import type { Page, Sample, Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import type { Schedule8SampleRequest } from '@/interfaces/Schedule8Request'
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
} from '@carbon/react'
import apiService from '@/service/api-service'
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

const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const NAV_UNSAVED = 'Unsaved data will be lost. Are you sure to continue?'

type PanelMode = 'closed' | 'new' | 'edit' | 'copy' | 'view'

const blankToNull = (raw: string): string | null => (raw.trim() === '' ? null : raw)

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    return (error as { response?: { data?: { detail?: string } } }).response?.data?.detail
  }
  return undefined
}

interface SamplePageProps {
  millId: number
  year: number
  page: Page
  editable: boolean
  onBack: () => void
  onDocUpdate: (doc: Schedule8Response) => void
  onOpenRates: (sampleId: number) => void
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
    contractId: form.contractId,
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
    apiService
      .getAxiosInstance()
      .put<Schedule8Response>(
        `/v1/schedule8/pages/${pageId}/samples?millId=${millId}&year=${year}`,
        buildRequest(),
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
        setPanelMode('closed')
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
    clearMessages()
    apiService
      .getAxiosInstance()
      .post<Schedule8CheckStatusResponse>(
        `/v1/schedule8/pages/${pageId}/check-status?millId=${millId}&year=${year}`,
      )
      .then((response) => setCheckResult(response.data))
      .catch((err: unknown) => setError(extractDetail(err) || 'Unable to check status.'))
  }

  const readOnly = panelMode === 'view'
  const panelOpen = panelMode !== 'closed'
  const errors = showErrors && !readOnly ? validateSampleForm(form) : {}
  const helicopterActive = (toNum(form.helicopterPct) ?? 0) !== 0
  const otherActive = (toNum(form.otherSkiddingPct) ?? 0) !== 0

  // ---- Editor field helpers ----------------------------------------------------------------------
  const numberField = (field: keyof SampleForm, label: string) => {
    if (readOnly) {
      return (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">{label}</span>
          <span>{form[field] === '' ? '—' : form[field]}</span>
        </div>
      )
    }
    return (
      <TextInput
        id={`sample-${field}`}
        labelText={label}
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
      <span>{fmt(value)}</span>
    </div>
  )

  // ---- Samples table -----------------------------------------------------------------------------
  const samplesTable = (
    <TableContainer title={`Samples (${samples.length})`}>
      <Table aria-label="Samples">
        <TableHead>
          <TableRow>
            <TableHeader>Contract ID</TableHeader>
            <TableHeader>Cut Block</TableHeader>
            <TableHeader className="schedule-8__num">% Total</TableHeader>
            <TableHeader className="schedule-8__num">Final Rate</TableHeader>
            <TableHeader>Actions</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {samples.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5}>No samples have been added.</TableCell>
            </TableRow>
          ) : (
            samples.map((sample) => (
              <TableRow key={sample.id}>
                <TableCell>{sample.contractId ?? '—'}</TableCell>
                <TableCell>{sample.cutBlock ?? '—'}</TableCell>
                <TableCell className="schedule-8__num">{fmt(sample.percentTotal)}</TableCell>
                <TableCell className="schedule-8__num">{fmt(sample.finalRate)}</TableCell>
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
        {panelMode === 'edit' && 'Edit Sample'}
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

      <h4 className="schedule-8__subheading">Skidding / Yarding %</h4>
      <div className="schedule-8__fields">
        {numberField('groundBasePct', 'Ground Base %')}
        {numberField('grapplePct', 'Grapple %')}
        {numberField('highleadPct', 'Highlead %')}
        {numberField('skylinePct', 'Skyline %')}
        {numberField('helicopterPct', 'Helicopter %')}
        {numberField('otherSkiddingPct', 'Other %')}
        {computedField('Total %', skiddingTotal(form))}
      </div>
      {errors.percentTotal && (
        <InlineNotification
          kind="error"
          lowContrast
          hideCloseButton
          title="Skidding / Yarding"
          subtitle={errors.percentTotal}
        />
      )}

      <h4 className="schedule-8__subheading">Skyline Support</h4>
      <div className="schedule-8__fields">
        {numberField('skylineSlopeDistance', 'Slope Distance')}
        {numberField('skylineSupportNumber', 'Support Number')}
        {numberField('supportAvgDistance', 'Support Avg Distance')}
      </div>

      {helicopterActive && (
        <>
          <h4 className="schedule-8__subheading">Helicopter</h4>
          <div className="schedule-8__fields">
            {numberField('distance', 'Distance')}
            {numberField('cycleTime', 'Cycle Time')}
            {ynSelect('uphillDirection', 'Direction', 'Uphill', 'Downhill')}
            {ynSelect('waterDumpDestination', 'Dump Destination', 'Water Dump', 'Land Dump')}
          </div>
        </>
      )}

      {otherActive && (
        <>
          <h4 className="schedule-8__subheading">Other Skid Type</h4>
          <div className="schedule-8__fields">
            {readOnly ? (
              <div className="schedule-8__field">
                <span className="schedule-8__field-label">Skid Type</span>
                <span>{form.skidTypeCode || '—'}</span>
              </div>
            ) : (
              <TextInput
                id="sample-skidTypeCode"
                labelText="Skid Type"
                value={form.skidTypeCode}
                onChange={setField('skidTypeCode')}
                invalid={Boolean(errors.skidTypeCode)}
                invalidText={errors.skidTypeCode}
              />
            )}
          </div>
        </>
      )}

      <h4 className="schedule-8__subheading">Volumes</h4>
      <div className="schedule-8__fields">
        {numberField('coniferousVolume', 'Coniferous Volume')}
        {numberField('deciduousVolume', 'Deciduous Volume')}
        {computedField('Actual Harvested', liveActualHarvested(form))}
      </div>

      <h4 className="schedule-8__subheading">Rate</h4>
      <div className="schedule-8__fields">
        {numberField('originalRate', 'Original Rate')}
        {computedField('Additions', openSample?.additionsTotal)}
        {computedField('Deductions', openSample?.deductionsTotal)}
        {computedField('Final Rate', openSample?.finalRate)}
      </div>

      {openSample && editId !== null && (
        <div className="schedule-8__nav-links">
          <Button kind="ghost" size="sm" onClick={() => onOpenRates(editId)}>
            Additions ({openSample.additionCount}):
          </Button>
          <Button kind="ghost" size="sm" onClick={() => onOpenRates(editId)}>
            Deductions ({openSample.deductionCount}):
          </Button>
        </div>
      )}

      <div className="schedule-8__panel-actions">
        {!readOnly && (
          <Button kind="primary" disabled={busy} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button kind="secondary" disabled={busy} onClick={closePanel}>
          {readOnly ? 'Close' : 'Cancel'}
        </Button>
      </div>
    </div>
  )

  return (
    <div className="schedule-8__level">
      <div className="schedule-8__level-header">
        <Button kind="ghost" size="sm" onClick={requestBack}>
          ← Back to pages
        </Button>
        <h3 className="schedule-8__heading">Samples — {page.license ?? `Page ${page.id}`}</h3>
      </div>

      {message && (
        <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
      )}
      {error && (
        <InlineNotification kind="error" lowContrast title="Action failed" subtitle={error} />
      )}
      {checkResult && (
        <div className="schedule-8__check">
          {checkResult.messages.map((msg) => (
            <InlineNotification
              key={`sch-${msg.key}`}
              kind="success"
              lowContrast
              title="Check Status"
              subtitle={msg.text}
            />
          ))}
          {checkResult.pages.flatMap((p) => [
            ...p.issues.map((issue) => (
              <InlineNotification
                key={`page-${p.id}-${issue.field}`}
                kind="warning"
                lowContrast
                title={`${issue.field} — required`}
                subtitle={issue.message.text}
              />
            )),
            ...p.samples.flatMap((s) =>
              s.issues.map((issue) => (
                <InlineNotification
                  key={`sample-${s.id}-${issue.field}`}
                  kind="warning"
                  lowContrast
                  title={`Sample — ${issue.field}`}
                  subtitle={issue.message.text}
                />
              )),
            ),
          ])}
        </div>
      )}

      <div className="schedule-8__actions">
        <Button kind="primary" disabled={!editable || busy || panelOpen} onClick={openNew}>
          Add New Sample
        </Button>
        <Button kind="tertiary" disabled={busy} onClick={handleCheckStatus}>
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
