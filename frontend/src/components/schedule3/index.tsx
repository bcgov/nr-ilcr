import type { FC } from 'react'
import type Schedule3Response from '@/interfaces/Schedule3Response'
import type { CostLine, ThreeColumnTotal } from '@/interfaces/Schedule3Response'
import type Schedule3Request from '@/interfaces/Schedule3Request'
import type CheckStatusResponse from '@/interfaces/CheckStatusResponse'
import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Grid,
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
  TextArea,
  TextInput,
} from '@carbon/react'
import apiService from '@/service/api-service'
import { ALL_LINE_CODES, HARVEST_POP_LINE_CODES } from '@/interfaces/Schedule3Request'
import useMillYear from '@/context/millYear/useMillYear'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'
import { validateSchedule3 } from './validation'
import './index.scss'

// Client-side chrome (a suppression with no request / a browser alert / a confirm dialog), so the
// verbatim text lives here. SUC/WRN/FLD strings come from the API `message`/`warnings`/`detail`
// (AD-8) — never hardcoded. Shared strings reuse Schedule 1's exact wording.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const ALT_S111 = 'Annual Rent (Forest Act, S111) is recorded as an Unacceptable Cost.'
const ALT_SAVE_BEFORE_OTHER = 'The schedule has to be saved before opening other costs'
const ALT_SAVE_BEFORE_UNACCEPTABLE =
  'The schedule has to be saved before opening Unacceptable costs'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'
const COMMENTS_MAX = 3500

// Story 4.4 sub-page routes (links + counts render here; the pages themselves are Story 4.4).
const ROUTE_OTHER_ACCEPTABLE = '/schedule-3/other-acceptable-costs'
const ROUTE_UNACCEPTABLE = '/schedule-3/included-unacceptable-costs'

const CODE_ANNUAL_RENTS = 29
// Fixed-line labels — verbatim legacy schedule3.xhtml outputLabels (frontend-owned, like Schedule 1).
const LINE_LABELS: Record<number, string> = {
  27: 'Licenses, Fees, Insurance',
  28: 'Taxes, Leases, Rentals',
  29: 'Annual Rents',
  30: 'Wages/Salaries, incl Benefits',
  31: 'Vehicle Expense',
  32: 'Office Expense',
  33: 'Scaling Expense',
  34: 'Cruising & Layout Expense',
  35: 'Residue & Waste Expense',
  36: 'Depreciation Expense',
  37: 'Silviculture Admin Costs',
}
const HARVEST_POP = new Set<number>(HARVEST_POP_LINE_CODES)
// Harvest-only lines whose PO&P is not captured at all (legacy inputHidden) — render a blank cell,
// NOT the backend's 0. Scaling (33) is excluded: it shows a server-derived PO&P read-only.
const POP_HIDDEN = new Set<number>([29, 37])

type FieldValues = Record<string, string>

function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { data?: { detail?: string } } }).response
    return response?.data?.detail
  }
  return undefined
}

const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

const toNum = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return null
  }
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)

// Seed editable form state from the loaded document (entered fields only).
function seedForm(doc: Schedule3Response): FieldValues {
  const values: FieldValues = {}
  for (const code of ALL_LINE_CODES) {
    const line = doc.lineItems.find((li) => li.costItemCode === code)
    values[`harvest-${code}`] = numStr(line?.harvest)
    if (HARVEST_POP.has(code)) {
      values[`pop-${code}`] = numStr(line?.pop)
    }
  }
  values['popTimberVolume'] = numStr(doc.popTimber?.volume)
  values['crownTimberVolume'] = numStr(doc.crownTimber?.volume)
  values['overrideHarvestTotalPop'] = doc.overrideHarvestTotalPop ?? 'N'
  values['comments'] = doc.comments ?? ''
  return values
}

function buildRequest(doc: Schedule3Response, form: FieldValues): Schedule3Request {
  return {
    revisionCount: doc.revisionCount ?? 0,
    comments: form['comments'].trim() === '' ? null : form['comments'],
    overrideHarvestTotalPop: form['overrideHarvestTotalPop'] ?? 'N',
    // All 11 harvest codes; PO&P only for the both-columns lines (server ignores it for 29/33/37).
    lineItems: ALL_LINE_CODES.map((code) => ({
      costItemCode: code,
      harvest: toNum(form[`harvest-${code}`]),
      pop: HARVEST_POP.has(code) ? toNum(form[`pop-${code}`] ?? '') : null,
    })),
    popTimberVolume: toNum(form['popTimberVolume']),
    crownTimberVolume: toNum(form['crownTimberVolume']),
  }
}

const Schedule3: FC = () => {
  const { millId, year } = useMillYear()
  const navigate = useNavigate()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<Schedule3Response | null>(null)
  const [form, setForm] = useState<FieldValues>({})
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveWarnings, setSaveWarnings] = useState<string[]>([])
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  const [checking, setChecking] = useState(false)
  const [checkResult, setCheckResult] = useState<CheckStatusResponse | null>(null)

  useEffect(() => {
    if (contextMissing) {
      return
    }
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    setSaveMessage(null)
    setSaveError(null)
    setSaveWarnings([])
    setCheckResult(null)
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<Schedule3Response>(`/v1/schedule3?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setForm(seedForm(response.data))
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Schedule 3.')
          setData(null)
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [millId, year, contextMissing])

  const setField =
    (key: string) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [key]: value }))
    }

  const handleSave = () => {
    // Re-entrancy guard: the top + bottom Save buttons can be double-clicked within one tick before
    // `saving` disables them — avoid concurrent PUTs (which would trip the optimistic-lock 409).
    if (!data || saving) {
      return
    }
    // Advisory client-side validation (backend authoritative): block a doomed round-trip.
    if (Object.keys(validateSchedule3(form)).length > 0) {
      setSaveMessage(null)
      setSaveWarnings([])
      setSaveError('Please correct the highlighted fields before saving.')
      return
    }
    setSaving(true)
    setSaveMessage(null)
    setSaveError(null)
    setSaveWarnings([])
    setCheckResult(null) // a prior Check Status result is stale once the data changes
    apiService
      .getAxiosInstance()
      .put<Schedule3Response>(
        `/v1/schedule3?millId=${millId}&year=${year}`,
        buildRequest(data, form),
      )
      .then((response) => {
        setData(response.data)
        setForm(seedForm(response.data))
        // SUC-001 verbatim from the API message field (AD-8), never hardcoded.
        setSaveMessage(response.data.message?.text ?? null)
        // BR-09 crown-push outcome (WRN-001/002) rides the echo's warnings channel.
        setSaveWarnings((response.data.warnings ?? []).map((w) => w.text || w.key))
      })
      .catch((error: unknown) => {
        // Keep the entered values (S17); surface the API's verbatim ProblemDetail.detail.
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
    setSaveWarnings([])
    setCheckResult(null)
    apiService
      .getAxiosInstance()
      .delete<{ message?: { text?: string } }>(`/v1/schedule3?millId=${millId}&year=${year}`)
      .then((response) => {
        // Delete removed the summary; a re-GET would 404, so reset to an empty schedule in place
        // (no re-fetch) and show SUC-002 from the API message.
        const empty: ThreeColumnTotal = { harvest: null, pop: null, crown: null }
        setData((prev) =>
          prev
            ? {
                ...prev,
                editable: false,
                revisionCount: null,
                overrideHarvestTotalPop: 'N',
                comments: null,
                lineItems: [],
                popTimber: { volume: null, cost: null, perUnit: null },
                crownTimber: { volume: null, cost: null, perUnit: null },
                totalOverhead: { volume: null, cost: null, perUnit: null },
                subtotalOtherCosts: empty,
                subtotalActualCosts: empty,
                includedUnacceptableCosts: empty,
                totalCosts: empty,
                otherAcceptableCount: 0,
                unacceptableCount: 0,
              }
            : prev,
        )
        setForm({})
        setSaveMessage(response.data?.message?.text ?? null)
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to delete Schedule 3.')
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
    setSaveMessage(null)
    setSaveWarnings([])
    apiService
      .getAxiosInstance()
      .post<CheckStatusResponse>(`/v1/schedule3/check-status?millId=${millId}&year=${year}`)
      .then((response) => {
        setCheckResult(response.data)
      })
      .catch((error: unknown) => {
        setSaveError(extractDetail(error) || 'Unable to check status.')
      })
      .finally(() => setChecking(false))
  }

  const openSubPage = (route: string, altText: string) => {
    // S18/S19: before the schedule is saved/open, opening a sub-page is blocked with the ALT gate.
    // In the current backend model an openable schedule is always saved (GET 404s for no summary),
    // so this guard is effectively unreachable — mirrors Schedule 1's Other Costs guard.
    if (!data) {
      window.alert(altText)
      return
    }
    // Navigating away from an editable schedule discards unsaved edits — confirm first.
    if (data.editable && !window.confirm(CONFIRM_NAVIGATION)) {
      return
    }
    navigate({ to: route })
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle title="Schedule 3" subtitle="Forest Management Administration Costs." />
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
            <LoadingScreen label="Loading Schedule 3" />
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
              title="Unable to load Schedule 3"
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
  const fieldErrors = editable ? validateSchedule3(form) : {}

  // A value cell: an editable TextInput when writable and the schedule is editable, else read-only
  // text. `onBlur` lets the Annual Rents Harvest field raise the S111 alert (legacy onchange).
  const numberCell = (
    fieldKey: string,
    label: string,
    writable: boolean,
    current: number | null | undefined,
    onBlur?: () => void,
  ) =>
    editable && writable ? (
      <TableCell className="schedule-3__num">
        <TextInput
          id={fieldKey}
          labelText={label}
          hideLabel
          size="sm"
          value={form[fieldKey] ?? ''}
          onChange={setField(fieldKey)}
          onBlur={onBlur}
          invalid={Boolean(fieldErrors[fieldKey])}
          invalidText={fieldErrors[fieldKey]}
        />
      </TableCell>
    ) : (
      <TableCell className="schedule-3__num">{fmt(current)}</TableCell>
    )

  const lineRow = (line: CostLine) => {
    const code = line.costItemCode
    const label = LINE_LABELS[code] ?? `Cost item ${code}`
    const showPop = HARVEST_POP.has(code)
    const harvestBlur = code === CODE_ANNUAL_RENTS ? () => window.alert(ALT_S111) : undefined
    // Annual Rents (29) and Silviculture Admin (37) have NO PO&P (legacy renders the field hidden);
    // the backend returns pop=0 for them, so blank the cell (—) rather than showing "0" (AC2).
    // Scaling (33) keeps its server-derived PO&P shown read-only.
    const popCell = POP_HIDDEN.has(code) ? (
      <TableCell className="schedule-3__num">—</TableCell>
    ) : (
      numberCell(`pop-${code}`, `${label} PO&P`, showPop, line.pop)
    )
    return (
      <TableRow key={code}>
        <TableCell>{label}</TableCell>
        {numberCell(`harvest-${code}`, `${label} Harvest`, true, line.harvest, harvestBlur)}
        {popCell}
        <TableCell className="schedule-3__num">{fmt(line.crown)}</TableCell>
      </TableRow>
    )
  }

  // A read-only derived three-column total row.
  const totalRow = (key: string, label: string, total: ThreeColumnTotal) => (
    <TableRow key={key}>
      <TableCell>{label}</TableCell>
      <TableCell className="schedule-3__num">{fmt(total.harvest)}</TableCell>
      <TableCell className="schedule-3__num">{fmt(total.pop)}</TableCell>
      <TableCell className="schedule-3__num">{fmt(total.crown)}</TableCell>
    </TableRow>
  )

  // A read-only derived total row whose label cell is the count-labelled sub-page link (Story 4.4).
  const subPageRow = (
    key: string,
    label: string,
    count: number,
    route: string,
    altText: string,
    total: ThreeColumnTotal,
  ) => (
    <TableRow key={key}>
      <TableCell>
        <Button kind="ghost" size="sm" onClick={() => openSubPage(route, altText)}>
          {`${label} (${count}):`}
        </Button>
      </TableCell>
      <TableCell className="schedule-3__num">{fmt(total.harvest)}</TableCell>
      <TableCell className="schedule-3__num">{fmt(total.pop)}</TableCell>
      <TableCell className="schedule-3__num">{fmt(total.crown)}</TableCell>
    </TableRow>
  )

  const timberRow = (
    label: string,
    fieldKey: string | null,
    block: { volume: number | null; cost: number | null; perUnit: number | null },
  ) => (
    <TableRow key={label}>
      <TableCell>{label}</TableCell>
      {fieldKey !== null ? (
        numberCell(fieldKey, `${label} Harvest Volume`, true, block.volume)
      ) : (
        <TableCell className="schedule-3__num">{fmt(block.volume)}</TableCell>
      )}
      <TableCell className="schedule-3__num">{fmt(block.cost)}</TableCell>
      <TableCell className="schedule-3__num">{fmt(block.perUnit)}</TableCell>
    </TableRow>
  )

  const actions = (
    <Column sm={4} md={8} lg={16} className="schedule-3__actions">
      <Button kind="primary" disabled={!editable || saving} onClick={handleSave}>
        Save
      </Button>
      <Button
        kind="tertiary"
        disabled={!editable || saving || checking}
        onClick={handleCheckStatus}
      >
        Check Status
      </Button>
      <Button
        kind="danger--tertiary"
        disabled={!editable || saving}
        onClick={() => setConfirmDeleteOpen(true)}
      >
        Delete
      </Button>
    </Column>
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16} className="schedule-3__meta">
          <dl className="schedule-3__summary">
            <div className="schedule-3__summary-item">
              <dt>Mill</dt>
              <dd>{data.millId}</dd>
            </div>
            <div className="schedule-3__summary-item">
              <dt>Reporting Year</dt>
              <dd>{data.year}</dd>
            </div>
            <div className="schedule-3__summary-item">
              <dt>Status</dt>
              <dd>{data.trackStatus ?? '—'}</dd>
            </div>
          </dl>
        </Column>

        {/* Advisory warnings from a mutation echo (BR-09 crown push). Verbatim text (AD-8). */}
        {saveWarnings.map((w) => (
          <Column key={`warn-${w}`} sm={4} md={8} lg={16}>
            <InlineNotification kind="warning" lowContrast title="Notice" subtitle={w} />
          </Column>
        ))}
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

        {/* Check Status result. Severity is carried by the notification kind AND an explicit title
            word (Success/Error) — not colour alone (WCAG 2.1 AA). Verbatim text (AD-8). */}
        {checkResult?.requirementsMet && checkResult.message && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="success"
              lowContrast
              title="Requirements met"
              subtitle={checkResult.message.text}
            />
          </Column>
        )}
        {(checkResult?.errors ?? []).map((e) => (
          <Column key={`check-err-${e.text || e.key}`} sm={4} md={8} lg={16}>
            <InlineNotification kind="error" lowContrast title="Error" subtitle={e.text || e.key} />
          </Column>
        ))}

        {actions}

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <TableContainer title="Administration Costs">
            <Table aria-label="Administration Costs">
              <TableHead>
                <TableRow>
                  <TableHeader>Cost Item</TableHeader>
                  <TableHeader className="schedule-3__num">Harvest Total $</TableHeader>
                  <TableHeader className="schedule-3__num">PO&P $</TableHeader>
                  <TableHeader className="schedule-3__num">Crown $</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {/* Legacy form order: 27,28,29,30,31,32,33,34,35,36,37 (schedule3.xhtml). */}
                {ALL_LINE_CODES.map((code) => {
                  const line =
                    data.lineItems.find((li) => li.costItemCode === code) ??
                    ({ costItemCode: code, harvest: null, pop: null, crown: null } as CostLine)
                  return lineRow(line)
                })}
                {subPageRow(
                  'subtotalOther',
                  'Subtotal Other Costs',
                  data.otherAcceptableCount,
                  ROUTE_OTHER_ACCEPTABLE,
                  ALT_SAVE_BEFORE_OTHER,
                  data.subtotalOtherCosts,
                )}
                {totalRow('subtotalActual', 'Subtotal (Actual Costs)', data.subtotalActualCosts)}
                {subPageRow(
                  'inclUnacceptable',
                  'Included Unacceptable Costs',
                  data.unacceptableCount,
                  ROUTE_UNACCEPTABLE,
                  ALT_SAVE_BEFORE_UNACCEPTABLE,
                  data.includedUnacceptableCosts,
                )}
                {totalRow('totalCosts', 'Total Costs', data.totalCosts)}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <Select
            id="overrideHarvestTotalPop"
            labelText="Override Harvest ⁄ Total PO&P $"
            value={form['overrideHarvestTotalPop'] ?? 'N'}
            onChange={setField('overrideHarvestTotalPop')}
            disabled={!editable}
          >
            <SelectItem value="N" text="No" />
            <SelectItem value="Y" text="Yes" />
          </Select>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <TableContainer title="Total Overhead and Cost Per Unit Calculation">
            <Table aria-label="Total Overhead and Cost Per Unit Calculation">
              <TableHead>
                <TableRow>
                  <TableHeader>Cost Item</TableHeader>
                  <TableHeader className="schedule-3__num">Harvest Volume (m³)</TableHeader>
                  <TableHeader className="schedule-3__num">Total Cost $</TableHeader>
                  <TableHeader className="schedule-3__num">Cost per Unit ($/m³)</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {timberRow(
                  'Privately Owned & Purchased (PO&P) Timber',
                  'popTimberVolume',
                  data.popTimber,
                )}
                {timberRow('Crown Timber', 'crownTimberVolume', data.crownTimber)}
                {timberRow('Total Overhead', null, data.totalOverhead)}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
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
              <h3 className="schedule-3__heading">Comments</h3>
              <p className="schedule-3__comments">{data.comments ?? '—'}</p>
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
    </div>
  )
}

export default Schedule3
