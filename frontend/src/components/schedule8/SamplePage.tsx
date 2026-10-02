import OriginalValueIndicator from '@/components/core/OriginalValueIndicator'
import type { FC } from 'react'
import type Schedule8Response from '@/interfaces/Schedule8Response'
import type { Page, Sample, Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import type {
  Schedule8PageCheckRequest,
  Schedule8SampleRequest,
} from '@/interfaces/Schedule8Request'
import type { CodeOption } from '@/interfaces/Schedule8Options'
import { useRef, useState } from 'react'
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
import {
  Add,
  ArrowLeft,
  CheckmarkOutline,
  Close,
  Copy,
  Edit,
  Information,
  Save,
  TrashCan,
  View,
} from '@carbon/icons-react'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import { blankToNull } from '@/utils/forms'
import type { BannerEntry } from '@/utils/legacyValidationBanner'
import {
  SAMPLE_BANNER,
  emptySampleForm,
  fmt,
  liveActualHarvested,
  seedSampleForm,
  skiddingTotal,
  toNum,
  validateSampleForm,
  type SampleForm,
} from './validation'
import { useJudgedForm } from './useJudgedForm'
import CheckStatusResult from './CheckStatusResult'
import LevelBanners from './LevelBanners'
import CodeComboBox from '@/components/core/CodeComboBox'

const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const NAV_UNSAVED = 'Unsaved data will be lost. Are you sure to continue?'

const PCT_FIELDS = new Set<keyof SampleForm>([
  'groundBasePct',
  'grapplePct',
  'skylinePct',
  'highleadPct',
  'helicopterPct',
  'otherSkiddingPct',
])

/**
 * What a change of `field` can also affect, re-judged with it when already showing an error: a
 * percentage moves the total, Helicopter % governs its four conditional fields, Other % the skid type.
 */
const sampleDependents = (field: keyof SampleForm): readonly string[] => {
  if (!PCT_FIELDS.has(field)) return []
  if (field === 'helicopterPct') {
    return ['percentTotal', 'distance', 'cycleTime', 'uphillDirection', 'waterDumpDestination']
  }
  if (field === 'otherSkiddingPct') return ['percentTotal', 'skidTypeCode']
  return ['percentTotal']
}

type PanelMode = 'closed' | 'new' | 'edit' | 'view'

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
  const [editId, setEditId] = useState<number | null>(null)
  const [revision, setRevision] = useState<number | null>(null)
  // The validation banner, one keyed line per failing field in legacy order (#359 group C change
  // log, 2026-10-02). Save REPLACES it with the full list; leaving a changed field adds or removes
  // only its own line. Check Status validates nothing (legacy's button is `process="@this"`).
  const [bannerEntries, setBannerEntries] = useState<readonly BannerEntry[]>([])
  const editor = useJudgedForm<SampleForm>({
    initial: emptySampleForm,
    validate: validateSampleForm,
    scheme: SAMPLE_BANNER,
    setBanner: setBannerEntries,
    dependents: sampleDependents,
  })
  const form = editor.form
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

  // Check Status describes one exact screen snapshot (#359). Incremented synchronously whenever that
  // snapshot changes, so an older response cannot repaint a verdict for values no longer on screen.
  const checkSnapshotVersionRef = useRef(0)

  const invalidateCheckResult = () => {
    checkSnapshotVersionRef.current += 1
    setCheckResult(null)
  }

  // Every action clears the messages first — the validation banner and the red fields with them, so
  // the two never disagree — and in doing so supersedes any check still in flight.
  const clearMessages = () => {
    setMessage(null)
    setError(null)
    setBannerEntries([])
    editor.clearErrors()
    invalidateCheckResult()
  }

  const openNew = () => {
    clearMessages()
    setPanelMode('new')
    editor.seed(emptySampleForm())
    setEditId(null)
    setRevision(null)
  }

  const openEditOrView = (sample: Sample, mode: 'edit' | 'view') => {
    clearMessages()
    setPanelMode(mode)
    editor.seed(seedSampleForm(sample))
    setEditId(sample.id)
    setRevision(sample.revisionCount)
  }

  // Closing the panel takes its values off screen, so a verdict that included them is cleared, and
  // so are its validation lines and red fields.
  const closePanel = () => {
    setPanelMode('closed')
    setBannerEntries([])
    editor.clearErrors()
    invalidateCheckResult()
  }

  const requestBack = () => {
    if (editable && panelMode !== 'closed' && panelMode !== 'view') setConfirmBack(true)
    else onBack()
  }

  // Every sample-editor edit goes through here: it changes the screen a shown verdict describes. The
  // red box and its inline text are NOT re-judged while typing: like legacy (and Schedule 10) a field
  // is judged when it is left after a change, a dropdown on selection.
  const setField = (field: keyof SampleForm) => (event: React.ChangeEvent<HTMLInputElement>) => {
    editor.write({ ...editor.formRef.current, [field]: event.target.value })
    invalidateCheckResult()
  }

  // A selection IS the change, so it is judged at once.
  const selectValue = (field: keyof SampleForm, value: string) => {
    editor.select(field, { ...editor.formRef.current, [field]: value })
    invalidateCheckResult()
  }

  /** The focus / leave pair that judges a text field when it is left changed. */
  const judgedOnLeave = (field: keyof SampleForm) => ({
    onFocus: () => editor.enter(field),
    onBlur: () => editor.leave(field),
  })

  // The sample write body for `source`; a null id is a create (Add New Sample, and Copy).
  const buildRequest = (
    source: SampleForm,
    id: number | null,
    revisionCount: number | null,
  ): Schedule8SampleRequest => ({
    id,
    revisionCount,
    contractId: source.contractId.trim(),
    cutBlock: blankToNull(source.cutBlock),
    groundBasePct: toNum(source.groundBasePct),
    grapplePct: toNum(source.grapplePct),
    skylinePct: toNum(source.skylinePct),
    highleadPct: toNum(source.highleadPct),
    helicopterPct: toNum(source.helicopterPct),
    otherSkiddingPct: toNum(source.otherSkiddingPct),
    skylineSlopeDistance: toNum(source.skylineSlopeDistance),
    skylineSupportNumber: toNum(source.skylineSupportNumber),
    supportAvgDistance: toNum(source.supportAvgDistance),
    cycleTime: toNum(source.cycleTime),
    distance: toNum(source.distance),
    uphillDirection: source.uphillDirection === '' ? null : source.uphillDirection === 'Y',
    waterDumpDestination:
      source.waterDumpDestination === '' ? null : source.waterDumpDestination === 'Y',
    skidTypeCode: blankToNull(source.skidTypeCode),
    coniferousVolume: toNum(source.coniferousVolume),
    deciduousVolume: toNum(source.deciduousVolume),
    originalRate: toNum(source.originalRate),
  })

  const samplesUrl = `/v1/schedule8/pages/${pageId}/samples?millId=${millId}&year=${year}`

  /**
   * Copy saves at once, as legacy did (`Schedule8DetailMB.copyReport`, then save()): the sample fields
   * are written as a NEW sample, never its additions or deductions, then the success message shows
   * and the editor opens on the copy in edit mode. A rejected write shows its error and opens
   * nothing, so no unsaved copy ever exists on screen.
   */
  const copySample = (sample: Sample) => {
    if (busy || !editable) return
    setBusy(true)
    clearMessages()
    // Sample ids present before the write. The reply names no saved id, so the copy is identified as
    // the ONE new id in it; the client's list may be stale, so if another session added a sample
    // meanwhile there are several and none can be told apart from someone else's record.
    const prevIds = new Set(samples.map((s) => s.id))
    apiService
      .getAxiosInstance()
      .put<Schedule8Response>(samplesUrl, buildRequest(seedSampleForm(sample), null, null))
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
        const pageSamples = response.data.pages.find((p) => p.id === pageId)?.samples ?? []
        // Ambiguous (zero or several new ids): the list and message refresh, but nothing opens.
        const added = pageSamples.filter((s) => s.id != null && !prevIds.has(s.id))
        const copy = added.length === 1 ? added[0] : undefined
        if (copy?.id != null) {
          setPanelMode('edit')
          editor.seed(seedSampleForm(copy))
          setEditId(copy.id)
          setRevision(copy.revisionCount ?? 0)
        }
      })
      .catch((err: unknown) => setError(extractDetail(err) || 'Sample could not be saved.'))
      .finally(() => setBusy(false))
  }

  const handleSave = () => {
    if (busy || panelMode === 'closed' || panelMode === 'view') return
    clearMessages()
    // Save judges the whole editor and REPLACES the banner with each failing field's legacy line.
    if (Object.keys(editor.validateAll()).length > 0) {
      return
    }
    setBusy(true)
    // Sample ids present before the save — used to find a freshly created sample in the reply.
    const prevIds = new Set(samples.map((s) => s.id))
    const editing = panelMode === 'edit'
    apiService
      .getAxiosInstance()
      .put<Schedule8Response>(
        samplesUrl,
        buildRequest(form, editing ? editId : null, editing ? (revision ?? 0) : null),
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
        // Stay on the saved record (don't close): re-open it in edit mode — by id when editing, or the
        // one new id — refreshing the optimistic-lock token so a follow-up save doesn't 409.
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

  // The Check Status body (#359): the open panel as it is on screen. A NEW sample is sent too, with id
  // null — legacy's Add put the unsaved row straight into the checked list
  // (`Schedule8DetailMB.java:222-231`), so the server appends it as the page's next sample. A field
  // failing Save's check for it contributes its last valid value, as legacy's model did (its Check
  // Status processed no field).
  const buildCheckRequest = (): Schedule8PageCheckRequest => {
    if (panelMode === 'closed') {
      return { sample: null }
    }
    const body = buildRequest(editor.modelSnapshot(), panelMode === 'new' ? null : editId, null)
    return {
      sample: {
        id: body.id,
        contractId: blankToNull(body.contractId),
        cutBlock: body.cutBlock,
        groundBasePct: body.groundBasePct,
        grapplePct: body.grapplePct,
        skylinePct: body.skylinePct,
        highleadPct: body.highleadPct,
        helicopterPct: body.helicopterPct,
        otherSkiddingPct: body.otherSkiddingPct,
        skylineSlopeDistance: body.skylineSlopeDistance,
        skylineSupportNumber: body.skylineSupportNumber,
        supportAvgDistance: body.supportAvgDistance,
        coniferousVolume: body.coniferousVolume,
        deciduousVolume: body.deciduousVolume,
        originalRate: body.originalRate,
      },
    }
  }

  const handleCheckStatus = () => {
    if (busy) return
    // No field is validated: legacy's Check Status button is `process="@this"`
    // (`schedule8Detail.xhtml:57-63`), so the check always runs. It re-rendered only the messages
    // area (`update=":schedule8DetailFrm:messages"`), so the banner gives way to the check's result
    // while the editor's red fields and inline texts stay as they were.
    setMessage(null)
    setError(null)
    setBannerEntries([])
    invalidateCheckResult()
    setBusy(true) // gate re-entrancy: disables the button and blocks overlapping check-status posts
    const submittedSnapshotVersion = checkSnapshotVersionRef.current
    // A response — success OR failure — for a superseded snapshot describes a panel no longer on
    // screen, so it is dropped.
    const stillWanted = () => checkSnapshotVersionRef.current === submittedSnapshotVersion
    apiService
      .getAxiosInstance()
      .post<Schedule8CheckStatusResponse>(
        `/v1/schedule8/pages/${pageId}/check-status?millId=${millId}&year=${year}`,
        buildCheckRequest(),
      )
      .then((response) => {
        if (stillWanted()) setCheckResult(response.data)
      })
      .catch((err: unknown) => {
        if (stillWanted()) setError(extractDetail(err) || 'Unable to check status.')
      })
      .finally(() => setBusy(false))
  }

  const readOnly = panelMode === 'view'
  const panelOpen = panelMode !== 'closed'
  // The red fields as Save / a field's leave last judged them; a View panel marks nothing.
  const errors: Record<string, string> = readOnly ? {} : editor.errors

  // ---- Editor field helpers ----------------------------------------------------------------------
  // The info tooltip carrying a legacy "Note:" entry hint (hover/focus) beside a field's label.
  const noteTrigger = (note: string) => (
    <Tooltip label={note} align="top">
      <button type="button" className="schedule-8__note-trigger" aria-label={note}>
        <Information />
      </button>
    </Tooltip>
  )

  // A field heading with its optional note. Read-only and computed fields render it as their visible
  // heading; the editable fields render it ABOVE the input instead of as `labelText`, because Carbon's
  // TextInput rejects interactive content inside its label (`useNoInteractiveChildren`, enforced from
  // @carbon/react 1.116 — it throws in development). The input keeps a plain, visually hidden label
  // of its own, so its accessible name is the bare field name rather than "label + note".
  const fieldLabel = (label: string, note?: string) =>
    note ? (
      <span className="schedule-8__label-note">
        {label}
        {noteTrigger(note)}
      </span>
    ) : (
      label
    )

  // Legacy rendered nineteen indicators on a sample (TreeToTruckDetailReportDO.java:551-632). Every
  // form key here already matches the served document's. The derived figures — % Total, Actual
  // Harvested, the addition/deduction totals and the final rate — get none.
  const sampleIndicator = (field: keyof SampleForm, label: string, numeric = true) => (
    <OriginalValueIndicator
      originals={editId === null ? null : openSample?.originalValues}
      field={field}
      current={form[field]}
      numeric={numeric}
      label={label}
    />
  )

  const numberField = (field: keyof SampleForm, label: string, note?: string) => {
    if (readOnly) {
      return (
        <div className="schedule-8__field">
          <span className="schedule-8__field-label">{fieldLabel(label, note)}</span>
          <span>{form[field] === '' ? '—' : form[field]}</span>
          {sampleIndicator(field, label)}
        </div>
      )
    }
    return (
      <div className="schedule-8__field">
        {note !== undefined && (
          <span className="cds--label schedule-8__label-note">
            {label}
            {noteTrigger(note)}
          </span>
        )}
        <TextInput
          id={`sample-${field}`}
          labelText={label}
          hideLabel={note !== undefined}
          size="sm"
          inputMode="numeric"
          value={form[field]}
          onChange={setField(field)}
          {...judgedOnLeave(field)}
          invalid={Boolean(errors[field])}
          invalidText={errors[field]}
        />
        {sampleIndicator(field, label)}
      </div>
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
          {sampleIndicator(field, label, false)}
        </div>
      )
    }
    return (
      <Select
        id={`sample-${field}`}
        labelText={label}
        size="sm"
        value={form[field]}
        onChange={(event) => selectValue(field, event.target.value)}
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
      <Table>
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
            samples.map((sample, index) => {
              // The sample open in the panel cannot act on itself: its row actions grey out while it
              // is open, as legacy's disableReport(report) did (Schedule8MB.java:135-137).
              const isOpen = panelOpen && sample.id != null && sample.id === editId
              return (
                <TableRow
                  key={sample.id}
                  className={isOpen ? 'schedule-8__row--editing' : undefined}
                >
                  <TableCell>{sampleLabel(sample, index)}</TableCell>
                  <TableCell>
                    <div className="schedule-8__row-actions">
                      <Button
                        kind="ghost"
                        size="sm"
                        renderIcon={editable ? Edit : View}
                        disabled={isOpen}
                        onClick={() => openEditOrView(sample, editable ? 'edit' : 'view')}
                      >
                        {editable ? 'Edit' : 'View'}
                      </Button>
                      <Button
                        kind="ghost"
                        size="sm"
                        disabled={!editable || busy || isOpen}
                        renderIcon={Copy}
                        onClick={() => copySample(sample)}
                      >
                        Copy
                      </Button>
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        disabled={!editable || busy || isOpen}
                        renderIcon={TrashCan}
                        onClick={() => setConfirmDelete(sample)}
                      >
                        Delete
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              )
            })
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
        {panelMode === 'view' && 'View Sample'}
      </h3>

      <div className="schedule-8__fields">
        {readOnly ? (
          <div className="schedule-8__field">
            <span className="schedule-8__field-label">Contract ID</span>
            <span>{form.contractId || '—'}</span>
            {sampleIndicator('contractId', 'Contract ID', false)}
          </div>
        ) : (
          <TextInput
            id="sample-contractId"
            labelText="Contract ID"
            maxLength={12}
            value={form.contractId}
            onChange={setField('contractId')}
            {...judgedOnLeave('contractId')}
            invalid={Boolean(errors.contractId)}
            invalidText={errors.contractId}
          />
        )}
        {!readOnly && sampleIndicator('contractId', 'Contract ID', false)}
        {readOnly ? (
          <div className="schedule-8__field">
            <span className="schedule-8__field-label">Cut Block</span>
            <span>{form.cutBlock || '—'}</span>
            {sampleIndicator('cutBlock', 'Cut Block', false)}
          </div>
        ) : (
          <TextInput
            id="sample-cutBlock"
            labelText="Cut Block"
            maxLength={12}
            value={form.cutBlock}
            onChange={setField('cutBlock')}
            {...judgedOnLeave('cutBlock')}
          />
        )}
        {!readOnly && sampleIndicator('cutBlock', 'Cut Block', false)}
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
            onSelect={(code) => selectValue('skidTypeCode', code)}
            onLeaveChanged={() => editor.leave('skidTypeCode', true)}
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

      <div className="schedule-8__panel-actions">
        {!readOnly && (
          <Button kind="primary" disabled={busy} renderIcon={Save} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button
          kind="secondary"
          disabled={busy}
          renderIcon={readOnly ? Close : ArrowLeft}
          onClick={closePanel}
        >
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

      {/* Every result renders here at the top, as on Schedule 10 (#359 group C): Save's success or
          failure, then the validation banner's lines, then the Check Status result. */}
      <LevelBanners message={message} error={error} entries={bannerEntries} />
      {checkResult && (
        <div className="schedule-8__check">
          <CheckStatusResult result={checkResult} />
        </div>
      )}

      <div className="schedule-8__actions">
        <Button kind="secondary" renderIcon={ArrowLeft} onClick={requestBack}>
          Back to pages
        </Button>
        <Button kind="primary" renderIcon={Add} disabled={!editable || busy} onClick={openNew}>
          Add New Sample
        </Button>
        {/* Check Status mutates nothing and the endpoint is VIEW_SCHEDULE-gated, but the BUTTON
            follows legacy, which disabled it alongside the write controls whenever the report was
            not editable by the caller (26 of 26 buttons across 15 pages). This previously read
            `disabled={busy}` — the twin of the same omission on the main page
            (`index.tsx:842`), and out of step with every sibling button here. */}
        <Button
          kind="tertiary"
          renderIcon={CheckmarkOutline}
          disabled={!editable || busy}
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
