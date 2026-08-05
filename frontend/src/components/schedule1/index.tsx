import type { FC } from 'react'
import type Schedule1Response from '@/interfaces/Schedule1Response'
import type { LineItem } from '@/interfaces/Schedule1Response'
import type Schedule1Request from '@/interfaces/Schedule1Request'
import type CheckStatusResponse from '@/interfaces/CheckStatusResponse'
import { useCallback, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
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
import { WRITABLE_LINE_ITEM_CODES } from '@/interfaces/Schedule1Request'
import useMillYear from '@/context/millYear/useMillYear'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import { extractDetail } from '@/utils/error'
import { fmt, numStr, toNum } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageTitle from '@/components/core/PageTitle'
import ScheduleActions from '@/components/core/ScheduleActions'
import { validateSchedule1 } from './validation'
import './index.scss'

// ERR-001 (mill/year not selected) and ALT-001 (open-other-costs-before-save) and confirmDeleteMsg
// are client-side chrome (a suppression with no request / a Carbon Modal / a confirm Modal), so
// their verbatim text lives here. SUC-001/SUC-002 come from the API `message.text` (AD-8) — never
// hardcoded.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const ALT_SAVE_BEFORE_OTHER_COSTS = 'The schedule has to be saved before opening other costs'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'
const COMMENTS_MAX = 3500

const LINE_ITEM_LABELS: Record<number, string> = {
  12: 'Standing Tree to Loaded Truck',
  13: 'Log Transportation',
  14: 'Road Management',
  15: 'Road Construction Costs',
  16: 'Post Logging Treatment',
  17: 'Stumpage and Royalty',
  18: 'Depletion and Amortization',
  143: 'Forest Management Administration Costs (Sch 3)',
  144: 'Subtotal Company Logging Cost (no Silviculture)',
}
const WRITABLE = new Set<number>(WRITABLE_LINE_ITEM_CODES)

// Silviculture code -> label. All four volumes are user-entered; codes 1 & 2 also have an editable
// cost, while 139's cost is pulled from Schedule 3 and 140's is derived (both costs read-only).
const SILV_ROWS: { code: number; label: string; key: keyof Schedule1Response['silviculture'] }[] = [
  { code: 1, label: 'Actual $ Spent', key: 'actualSpent' },
  { code: 139, label: 'Less Silviculture Admin Costs', key: 'lessAdmin' },
  { code: 2, label: 'Accrued less Actual $ Spent', key: 'accruedLessActual' },
  { code: 140, label: 'Total Silviculture (As per Financial Statements)', key: 'total' },
]

type FieldValues = Record<string, string>

const mapLoadErrorDetail = (
  detail: string | undefined,
  millId: number | null,
  year: number | null,
): string => {
  if (detail === 'Schedule not found.' && millId != null && year != null) {
    return `No Schedule 1 exists for Mill ${millId} in Reporting Year ${year}. Select another mill/year from Home, or create Schedule 1 data for this context.`
  }
  return detail || 'Unable to load Schedule 1.'
}

// Seed editable form state from the loaded document (writable fields only).
function seedForm(doc: Schedule1Response): FieldValues {
  const values: FieldValues = {}
  for (const code of WRITABLE_LINE_ITEM_CODES) {
    const item = doc.lineItems.find((li) => li.costItemCode === code)
    values[`vol-${code}`] = numStr(item?.volume)
    values[`cost-${code}`] = numStr(item?.cost)
  }
  values['vol-1'] = numStr(doc.silviculture.actualSpent?.volume)
  values['cost-1'] = numStr(doc.silviculture.actualSpent?.cost)
  values['vol-2'] = numStr(doc.silviculture.accruedLessActual?.volume)
  values['cost-2'] = numStr(doc.silviculture.accruedLessActual?.cost)
  // Volume-only editable fields: 143/144 (line items) and 139/140 (silviculture).
  values['vol-143'] = numStr(doc.lineItems.find((li) => li.costItemCode === 143)?.volume)
  values['vol-144'] = numStr(doc.lineItems.find((li) => li.costItemCode === 144)?.volume)
  values['vol-139'] = numStr(doc.silviculture.lessAdmin?.volume)
  values['vol-140'] = numStr(doc.silviculture.total?.volume)
  values['otherCostsVolume'] = numStr(doc.otherCosts.volume)
  values['comments'] = doc.comments ?? ''
  return values
}

function buildRequest(doc: Schedule1Response, form: FieldValues): Schedule1Request {
  return {
    revisionCount: doc.revisionCount ?? 0,
    comments: form['comments'].trim() === '' ? null : form['comments'],
    lineItems: WRITABLE_LINE_ITEM_CODES.map((code) => ({
      costItemCode: code,
      volume: toNum(form[`vol-${code}`]),
      cost: toNum(form[`cost-${code}`]),
    })),
    silviculture: {
      actualSpent: { volume: toNum(form['vol-1']), cost: toNum(form['cost-1']) },
      accruedLessActual: { volume: toNum(form['vol-2']), cost: toNum(form['cost-2']) },
      lessAdminVolume: toNum(form['vol-139']),
      totalVolume: toNum(form['vol-140']),
    },
    otherCostsVolume: toNum(form['otherCostsVolume']),
    forestMgmtAdminVolume: toNum(form['vol-143']),
    subtotalCompanyLoggingVolume: toNum(form['vol-144']),
  }
}

const Schedule1: FC = () => {
  const { millId, year } = useMillYear()
  const navigate = useNavigate()
  const contextMissing = millId === null || year === null

  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  const [confirmNavOpen, setConfirmNavOpen] = useState(false)
  const [otherCostsBlockedOpen, setOtherCostsBlockedOpen] = useState(false)
  const [checking, setChecking] = useState(false)
  const [checkResult, setCheckResult] = useState<CheckStatusResponse | null>(null)

  // Clear the save/check notifications whenever a fresh document loads (mill/year change).
  const resetMessages = useCallback(() => {
    setSaveMessage(null)
    setSaveError(null)
    setCheckResult(null)
  }, [])

  const { data, setData, form, setForm, setField, errorDetail, isLoading } =
    useScheduleDocument<Schedule1Response>({
      path: '/v1/schedule1',
      millId,
      year,
      contextMissing,
      seedForm,
      mapLoadError: mapLoadErrorDetail,
      onReset: resetMessages,
    })

  const handleSave = () => {
    // Re-entrancy guard: the top + bottom Save buttons can be double-clicked within one tick before
    // `saving` disables them — avoid concurrent PUTs (which would trip the optimistic-lock 409).
    if (!data || saving) {
      return
    }
    // Advisory client-side validation (backend remains authoritative): block a doomed round-trip and
    // point the user at the highlighted fields.
    if (Object.keys(validateSchedule1(form)).length > 0) {
      setSaveMessage(null)
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    setSaving(true)
    setSaveMessage(null)
    setSaveError(null)
    setCheckResult(null) // a prior Check Status result is stale once the data changes
    apiService
      .getAxiosInstance()
      .put<Schedule1Response>(
        `/v1/schedule1?millId=${millId}&year=${year}`,
        buildRequest(data, form),
      )
      .then((response) => {
        setData(response.data)
        setForm(seedForm(response.data))
        // SUC-001 verbatim from the API message field (AD-8), never hardcoded.
        setSaveMessage(response.data.message?.text ?? null)
      })
      .catch((error: unknown) => {
        // Keep the entered values (S23/S24); surface the API's verbatim ProblemDetail.detail.
        setSaveError(extractDetail(error) || 'Schedule could not be saved.')
      })
      .finally(() => setSaving(false))
  }

  const handleDelete = () => {
    if (saving) {
      return
    }
    setConfirmDeleteOpen(false)
    setSaving(true)
    setSaveMessage(null)
    setSaveError(null)
    setCheckResult(null) // the deleted schedule's check result is stale
    apiService
      .getAxiosInstance()
      .delete<{ message?: { text?: string } }>(`/v1/schedule1?millId=${millId}&year=${year}`)
      .then((response) => {
        // Delete removed the summary; a re-GET would 404, so reset to an empty schedule in place
        // (no re-fetch) and show SUC-002 from the API message.
        setData((prev) =>
          prev
            ? {
                ...prev,
                // The summary is gone; there is nothing to edit or re-save (create-on-open is not
                // supported), so render the empty schedule read-only and disable the actions.
                editable: false,
                revisionCount: null,
                comments: null,
                lineItems: [],
                silviculture: {
                  actualSpent: null,
                  accruedLessActual: null,
                  lessAdmin: null,
                  total: null,
                },
                otherCosts: { volume: null, costSubtotal: null, perUnit: null, count: 0 },
              }
            : prev,
        )
        setForm({})
        setSaveMessage(response.data?.message?.text ?? null)
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to delete Schedule 1.')
      })
      .finally(() => setSaving(false))
  }

  const handleCheckStatus = () => {
    if (!data || checking || saving) {
      return
    }
    setChecking(true)
    setCheckResult(null)
    setSaveError(null)
    setSaveMessage(null) // don't leave a stale Save success banner beside a new check result
    apiService
      .getAxiosInstance()
      .post<CheckStatusResponse>(`/v1/schedule1/check-status?millId=${millId}&year=${year}`)
      .then((response) => {
        setCheckResult(response.data)
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to check status.')
      })
      .finally(() => setChecking(false))
  }

  const handleOtherCosts = () => {
    // S08: before the schedule is saved/open, opening Other Costs is blocked with ALT-001. In the
    // current backend model an openable schedule is always saved (GET 404s for no summary), so this
    // guard is effectively unreachable.
    if (!data) {
      setOtherCostsBlockedOpen(true)
      return
    }
    // Navigating away from an editable Schedule 1 discards unsaved edits — confirm first (legacy
    // confirmNavigationMsg). A read-only schedule has nothing to lose, so open directly.
    if (data.editable) {
      setConfirmNavOpen(true)
      return
    }
    navigate({ to: '/schedule-1/other-costs' })
  }

  // Confirmed via the navigation Modal: discard unsaved edits and open Other Costs.
  const openOtherCosts = () => {
    setConfirmNavOpen(false)
    navigate({ to: '/schedule-1/other-costs' })
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle
        breadCrumbs={[{ name: 'ILCR', path: '/' }]}
        title="Schedule 1"
        subtitle="Average Cost of Logging."
      />
    </Grid>
  )

  if (contextMissing) {
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="Mill and Reporting Year required"
              subtitle={ERR_MILL_YEAR_NOT_SELECTED}
            />
          </Column>
        </Grid>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <LoadingScreen label="Loading Schedule 1" />
          </Column>
        </Grid>
      </div>
    )
  }

  if (errorDetail) {
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="Unable to load Schedule 1"
              subtitle={errorDetail}
            />
          </Column>
        </Grid>
      </div>
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable
  // Advisory per-field validation (backend authoritative); drives inline invalid states + Save gate.
  const fieldErrors = editable ? validateSchedule1(form) : {}

  // A value cell: an editable TextInput when the field is writable and the schedule is editable,
  // otherwise read-only text. perUnit is always read-only (server-computed).
  const numberCell = (
    fieldKey: string,
    label: string,
    writable: boolean,
    current: number | null | undefined,
  ) =>
    editable && writable ? (
      <TableCell className="schedule-1__num">
        <TextInput
          id={fieldKey}
          labelText={label}
          hideLabel
          size="sm"
          value={form[fieldKey] ?? ''}
          onChange={setField(fieldKey)}
          invalid={Boolean(fieldErrors[fieldKey])}
          invalidText={fieldErrors[fieldKey]}
        />
      </TableCell>
    ) : (
      <TableCell className="schedule-1__num">{fmt(current)}</TableCell>
    )

  const lineItemRow = (item: LineItem) => {
    const code = item.costItemCode
    const label = LINE_ITEM_LABELS[code] ?? `Cost item ${code}`
    const writableVolume = WRITABLE.has(code)
    // Cost is writable for the writable codes; pulled (143/139) and derived (144) costs stay read-only.
    const writableCost = WRITABLE.has(code)
    return (
      <TableRow key={code}>
        <TableCell>{label}</TableCell>
        {numberCell(`vol-${code}`, `${label} volume`, writableVolume, item.volume)}
        {numberCell(`cost-${code}`, `${label} cost`, writableCost, item.cost)}
        <TableCell className="schedule-1__num">{fmt(item.perUnit)}</TableCell>
      </TableRow>
    )
  }

  const silvicultureRow = (row: (typeof SILV_ROWS)[number]) => {
    const item = data.silviculture[row.key]
    // All four silviculture VOLUMES are user-entered; only 1 & 2 have an editable cost. 139's cost is
    // pulled from Schedule 3, 140's is derived — both read-only.
    const writableCost = row.code === 1 || row.code === 2
    // 139's cost is pulled from Schedule 3; 140's is the derived Total Silviculture cost (both read-only).
    const costValue =
      row.code === 139
        ? data.lessSilvAdminCost
        : row.code === 140
          ? data.totalSilvicultureCost
          : item?.cost
    // $/m³ = cost ÷ volume, computed server-side (139/140 fold in the Schedule 3 pulls).
    const perUnitValue =
      row.code === 139
        ? data.lessSilvAdminPerUnit
        : row.code === 140
          ? data.totalSilviculturePerUnit
          : item?.perUnit
    return (
      <TableRow key={row.code}>
        <TableCell>{row.label}</TableCell>
        {numberCell(`vol-${row.code}`, `${row.label} volume`, true, item?.volume)}
        {numberCell(`cost-${row.code}`, `${row.label} cost`, writableCost, costValue)}
        <TableCell className="schedule-1__num">{fmt(perUnitValue)}</TableCell>
      </TableRow>
    )
  }

  // Forest Management Admin (143): VOLUME is user-entered (8-digit); its COST is pulled from Schedule 3
  // (read-only). Subtotal Company Logging (144): VOLUME user-entered (8-digit); COST derived (—).
  // Both $/m³ cells are Schedule-3 cross-derivations deferred this story (—).
  const forestMgmtAdminRow = (
    <TableRow key={143}>
      <TableCell>{LINE_ITEM_LABELS[143]}</TableCell>
      {numberCell(
        'vol-143',
        'Forest Management Administration volume',
        true,
        data.lineItems.find((li) => li.costItemCode === 143)?.volume,
      )}
      <TableCell className="schedule-1__num">{fmt(data.forestMgmtAdminCost)}</TableCell>
      <TableCell className="schedule-1__num">{fmt(data.forestMgmtAdminPerUnit)}</TableCell>
    </TableRow>
  )

  const subtotalCompanyLoggingRow = (
    <TableRow key={144}>
      <TableCell>{LINE_ITEM_LABELS[144]}</TableCell>
      {numberCell(
        'vol-144',
        'Subtotal Company Logging volume',
        true,
        data.lineItems.find((li) => li.costItemCode === 144)?.volume,
      )}
      <TableCell className="schedule-1__num">{fmt(data.subtotalCompanyLoggingCost)}</TableCell>
      <TableCell className="schedule-1__num">{fmt(data.subtotalCompanyLoggingPerUnit)}</TableCell>
    </TableRow>
  )

  // Subtotal Other Costs (legacy: sits between Depletion & Amortization and Subtotal Company Logging).
  // The label is a link/button that opens the Other Costs sub-page (Story 2.5); the VOLUME is
  // user-entered (editable schedules) while the cost subtotal and $/m³ are server-derived (read-only).
  const otherCostsRow = (
    <TableRow key="other-costs">
      <TableCell>
        <Button
          kind="ghost"
          size="sm"
          className="schedule-1__other-costs-link"
          onClick={handleOtherCosts}
        >
          Subtotal Other Costs({data.otherCosts.count}):
        </Button>
      </TableCell>
      {editable ? (
        <TableCell className="schedule-1__num">
          <TextInput
            id="otherCostsVolume"
            labelText="Subtotal Other Costs volume"
            hideLabel
            size="sm"
            value={form['otherCostsVolume'] ?? ''}
            onChange={setField('otherCostsVolume')}
            invalid={Boolean(fieldErrors['otherCostsVolume'])}
            invalidText={fieldErrors['otherCostsVolume']}
          />
        </TableCell>
      ) : (
        <TableCell className="schedule-1__num">{fmt(data.otherCosts.volume)}</TableCell>
      )}
      <TableCell className="schedule-1__num">{fmt(data.otherCosts.costSubtotal)}</TableCell>
      <TableCell className="schedule-1__num">{fmt(data.otherCosts.perUnit)}</TableCell>
    </TableRow>
  )

  // Grand-total row (legacy "Total Company Logging Costs (Including total Silviculture Cost)"): the
  // Total Harvested Crown Timber volume (Sch 3), the total logging cost, and its $/m³ average.
  const totalCompanyLoggingRow = (
    <TableRow key="total-company-logging">
      <TableCell>Total Company Logging Costs (Including total Silviculture Cost)</TableCell>
      <TableCell className="schedule-1__num">{fmt(data.schedule3CrownVolume)}</TableCell>
      <TableCell className="schedule-1__num">{fmt(data.totalCompanyLoggingCost)}</TableCell>
      <TableCell className="schedule-1__num">{fmt(data.totalCompanyLoggingPerUnit)}</TableCell>
    </TableRow>
  )

  // Crown Timber Volume for all fields (Sch 3): BR-03 source, read-only. A plain label + read-only box
  // (no table) laid out on the SAME fixed column grid as the cost tables below (46% / 18% / 18% / 18%)
  // so the box lines up under the Volume m³ column (legacy layout). hideLabel keeps the descriptive
  // text as the input's a11y name (visually hidden) while "Volume m³" shows as the caption.
  const crownVolumeField = (
    <Column sm={4} md={8} lg={16} className="schedule-1__section schedule-1__crown">
      <span className="schedule-1__crown-label">Crown Timber Volume for all fields (Sch 3):</span>
      <div className="schedule-1__crown-field">
        <span className="schedule-1__crown-unit">Volume m³</span>
        <TextInput
          id="schedule3CrownVolume"
          labelText="Crown Timber Volume for all fields (Sch 3)"
          hideLabel
          size="sm"
          value={numStr(data.schedule3CrownVolume)}
          onChange={() => undefined}
          disabled
        />
      </div>
    </Column>
  )

  const actions = (
    <ScheduleActions
      className="schedule-1__actions"
      editable={editable}
      saving={saving}
      checking={checking}
      onSave={handleSave}
      onCheckStatus={handleCheckStatus}
      onDelete={() => setConfirmDeleteOpen(true)}
    />
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {/* Advisory warnings from the GET (WRN-001 crown pre-fill). Verbatim text from the API (AD-8). */}
        {(data.warnings ?? []).map((w, i) => (
          // Static, append-only notification list (never reordered); index disambiguates messages
          // that share the same key/text.
          // eslint-disable-next-line @eslint-react/no-array-index-key
          <Column key={`warn-${w.key}-${i}`} sm={4} md={8} lg={16}>
            <InlineNotification
              kind="warning"
              lowContrast
              title="Notice"
              subtitle={w.text || w.key}
            />
          </Column>
        ))}
        {saveMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={saveMessage} />
        )}
        {saveError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={saveError} />
        )}

        {/* Check Status result (Story 2.7). Severity is carried by the notification kind AND an explicit
            title word (Success/Error/Warning) — not colour alone (WCAG 2.1 AA). Verbatim text (AD-8). */}
        {checkResult?.requirementsMet && checkResult.message && (
          <NotificationColumn
            kind="success"
            title="Requirements met"
            subtitle={checkResult.message.text}
          />
        )}
        {(checkResult?.errors ?? []).map((e, i) => (
          <NotificationColumn
            // Static, append-only notification list (never reordered); index disambiguates messages
            // that share the same key/text.
            // eslint-disable-next-line @eslint-react/no-array-index-key
            key={`check-err-${e.text || e.key}-${i}`}
            kind="error"
            title="Error"
            subtitle={e.text || e.key}
          />
        ))}
        {(checkResult?.warnings ?? []).map((w, i) => (
          // Static, append-only notification list (never reordered); index disambiguates messages
          // that share the same key/text.
          // eslint-disable-next-line @eslint-react/no-array-index-key
          <Column key={`check-warn-${w.text || w.key}-${i}`} sm={4} md={8} lg={16}>
            <InlineNotification
              kind="warning"
              lowContrast
              title="Warning"
              subtitle={w.text || w.key}
            />
          </Column>
        ))}

        {actions}

        {crownVolumeField}

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          {/* No visible section title (legacy form has none); aria-label carries the name for a11y. */}
          <TableContainer>
            <Table aria-label="Company Logging Costs" className="schedule-1__cost-table">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
                  <TableHeader className="schedule-1__num">Volume m³</TableHeader>
                  <TableHeader className="schedule-1__num">Cost $</TableHeader>
                  <TableHeader className="schedule-1__num">$ / m³</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {/* Legacy form order: 12–16, 143, 17, 18, Other Costs, 144 (schedule1.xhtml). */}
                {[12, 13, 14, 15, 16, 143, 17, 18].map((code) => {
                  if (code === 143) {
                    return forestMgmtAdminRow
                  }
                  const item = data.lineItems.find((li) => li.costItemCode === code)
                  return item ? lineItemRow(item) : null
                })}
                {otherCostsRow}
                {subtotalCompanyLoggingRow}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          <TableContainer title="Silviculture">
            <Table aria-label="Silviculture" className="schedule-1__cost-table">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
                  <TableHeader className="schedule-1__num">Volume m³</TableHeader>
                  <TableHeader className="schedule-1__num">Cost $</TableHeader>
                  <TableHeader className="schedule-1__num">$ / m³</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {SILV_ROWS.map(silvicultureRow)}
                {totalCompanyLoggingRow}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          {editable ? (
            <TextArea
              id="comments"
              labelText="Comments"
              enableCounter
              maxCount={COMMENTS_MAX}
              value={form['comments'] ?? ''}
              onChange={setField('comments')}
            />
          ) : (
            <>
              <h3 className="schedule-1__heading">Comments</h3>
              <p className="schedule-1__comments">{data.comments ?? '—'}</p>
            </>
          )}
        </Column>

        {actions}
      </Grid>

      {editable && (
        <Modal
          open={confirmDeleteOpen}
          danger
          modalHeading="Delete schedule"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteOpen(false)}
          onRequestSubmit={handleDelete}
        >
          <p>{CONFIRM_DELETE}</p>
        </Modal>
      )}

      {/* Discard-unsaved-edits confirm before leaving an editable schedule for Other Costs. */}
      {editable && (
        <Modal
          open={confirmNavOpen}
          modalHeading="Leave Schedule 1"
          primaryButtonText="Continue"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmNavOpen(false)}
          onRequestSubmit={openOtherCosts}
        >
          <p>{CONFIRM_NAVIGATION}</p>
        </Modal>
      )}

      {/* ALT-001: schedule must be saved before Other Costs can open (informational, single action). */}
      {otherCostsBlockedOpen && (
        <Modal
          open
          passiveModal
          modalHeading="Save required"
          onRequestClose={() => setOtherCostsBlockedOpen(false)}
        >
          <p>{ALT_SAVE_BEFORE_OTHER_COSTS}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule1
