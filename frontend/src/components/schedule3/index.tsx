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
import { extractDetail } from '@/utils/error'
import { fmtCurrency, fmtNumber, groupInput, numStrGroup, toNum } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import ScheduleActions from '@/components/core/ScheduleActions'
import { validateSchedule3 } from './validation'
import './index.scss'

// Client-side chrome (a suppression with no request / a browser alert / a confirm dialog), so the
// verbatim text lives here. SUC/WRN/FLD strings come from the API `message`/`warnings`/`detail`
// (AD-8) — never hardcoded. Shared strings reuse Schedule 1's exact wording.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const ALT_S111 = 'Annual Rent (Forest Act, S111) is recorded as an Unacceptable Cost.'
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

// Seed editable form state from the loaded document (entered fields only).
function seedForm(doc: Schedule3Response): FieldValues {
  const values: FieldValues = {}
  for (const code of ALL_LINE_CODES) {
    const line = doc.lineItems.find((li) => li.costItemCode === code)
    values[`harvest-${code}`] = numStrGroup(line?.harvest)
    if (HARVEST_POP.has(code)) {
      values[`pop-${code}`] = numStrGroup(line?.pop)
    }
  }
  values['popTimberVolume'] = numStrGroup(doc.popTimber?.volume)
  values['crownTimberVolume'] = numStrGroup(doc.crownTimber?.volume)
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
  // The sub-page a "Leave Schedule 3" confirm is pending for (null = modal closed).
  const [pendingRoute, setPendingRoute] = useState<string | null>(null)

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

  // Re-group a numeric field's value on blur, so it reads like the plain-text cells beside it. Only
  // on blur — regrouping mid-keystroke would fight the caret. Invalid text is left as typed
  // (groupInput passes it through) so the inline error still points at what the user actually wrote.
  const groupField = (key: string) => {
    setForm((prev) => {
      const grouped = groupInput(prev[key] ?? '')
      return grouped === prev[key] ? prev : { ...prev, [key]: grouped }
    })
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

  const openSubPage = (route: string) => {
    // Navigating away from an editable schedule discards unsaved edits — confirm via a Carbon Modal
    // (legacy confirmNavigationMsg) instead of a native browser dialog. A read-only schedule has
    // nothing to lose, so open directly.
    if (data?.editable) {
      setPendingRoute(route)
      return
    }
    navigate({ to: route })
  }

  // Confirmed via the "Leave Schedule 3" Modal: discard unsaved edits and open the pending sub-page.
  const confirmLeave = () => {
    if (pendingRoute === null) {
      return
    }
    const route = pendingRoute
    setPendingRoute(null)
    navigate({ to: route })
  }

  const header = (
    <ScheduleTombstone title="Schedule 3" subtitle="Forest Management Administration Costs" />
  )

  if (contextMissing) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Mill and Reporting Year required',
          subtitle: ERR_MILL_YEAR_NOT_SELECTED,
        }}
      />
    )
  }

  if (isLoading) {
    return (
      <PageState header={header}>
        <Column sm={4} md={8} lg={16}>
          <LoadingScreen label="Loading Schedule 3" />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={header}
        notification={{ kind: 'error', title: 'Unable to load Schedule 3', subtitle: errorDetail }}
      />
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
      // --input: the value lives inside a TextInput, which supplies its own inline padding. The
      // plain-text cells are indented to match it (see index.scss) so the column shares one left edge.
      <TableCell className="schedule-3__num schedule-3__num--input">
        <TextInput
          id={fieldKey}
          labelText={label}
          hideLabel
          size="sm"
          value={form[fieldKey] ?? ''}
          onChange={setField(fieldKey)}
          // Re-group the value AND run the caller's own blur hook (the Annual Rents S111 alert).
          onBlur={() => {
            groupField(fieldKey)
            onBlur?.()
          }}
          invalid={Boolean(fieldErrors[fieldKey])}
          invalidText={fieldErrors[fieldKey]}
        />
      </TableCell>
    ) : (
      <TableCell className="schedule-3__num">{fmtNumber(current)}</TableCell>
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
        <TableCell className="schedule-3__num">{fmtNumber(line.crown)}</TableCell>
      </TableRow>
    )
  }

  // A read-only derived three-column total row.
  const totalRow = (key: string, label: string, total: ThreeColumnTotal) => (
    <TableRow key={key}>
      <TableCell>{label}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.harvest)}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.pop)}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.crown)}</TableCell>
    </TableRow>
  )

  // A read-only derived total row whose label cell is the count-labelled sub-page link (Story 4.4).
  // `popHidden` blanks the PO&P cell (—) for Included Unacceptable Costs, whose PO&P is a legacy
  // inputHidden (never shown) — matching the Annual Rents / Silviculture Admin blanks (POP_HIDDEN),
  // NOT the backend's 0. Subtotal Other Costs keeps its shown PO&P (legacy disabled box).
  const subPageRow = (
    key: string,
    label: string,
    count: number,
    route: string,
    total: ThreeColumnTotal,
    popHidden = false,
  ) => (
    <TableRow key={key}>
      <TableCell>
        <Button
          kind="ghost"
          size="sm"
          className="schedule-3__link"
          onClick={() => openSubPage(route)}
        >
          {`${label} (${count}):`}
        </Button>
      </TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.harvest)}</TableCell>
      <TableCell className="schedule-3__num">{popHidden ? '—' : fmtNumber(total.pop)}</TableCell>
      <TableCell className="schedule-3__num">{fmtNumber(total.crown)}</TableCell>
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
        <TableCell className="schedule-3__num">{fmtNumber(block.volume)}</TableCell>
      )}
      <TableCell className="schedule-3__num">{fmtNumber(block.cost)}</TableCell>
      <TableCell className="schedule-3__num">{fmtCurrency(block.perUnit)}</TableCell>
    </TableRow>
  )

  const actions = (
    <ScheduleActions
      className="schedule-3__actions"
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
        {/* Advisory warnings from a mutation echo (BR-09 crown push). Verbatim text (AD-8). */}
        {saveWarnings.map((w) => (
          <NotificationColumn key={`warn-${w}`} kind="warning" title="Notice" subtitle={w} />
        ))}
        {saveMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={saveMessage} />
        )}
        {saveError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={saveError} />
        )}

        {/* Check Status result. Severity is carried by the notification kind AND an explicit title
            word (Success/Error) — not colour alone (WCAG 2.1 AA). Verbatim text (AD-8). */}
        {checkResult?.requirementsMet && checkResult.message && (
          <NotificationColumn
            kind="success"
            title="Requirements met"
            subtitle={checkResult.message.text}
          />
        )}
        {(checkResult?.errors ?? []).map((e) => (
          <NotificationColumn
            key={`check-err-${e.text || e.key}`}
            kind="error"
            title="Error"
            subtitle={e.text || e.key}
          />
        ))}

        {actions}

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          {/* No visible section title (legacy form has none); aria-label carries the name for a11y. */}
          <TableContainer>
            <Table aria-label="Administration Costs" className="schedule-3__cost-table">
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
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
                  data.subtotalOtherCosts,
                )}
                {totalRow('subtotalActual', 'Subtotal (Actual Costs)', data.subtotalActualCosts)}
                {subPageRow(
                  'inclUnacceptable',
                  'Included Unacceptable Costs',
                  data.unacceptableCount,
                  ROUTE_UNACCEPTABLE,
                  data.includedUnacceptableCosts,
                  true, // PO&P is a legacy inputHidden — render blank (—), not the backend's 0
                )}
                {totalRow('totalCosts', 'Total Costs', data.totalCosts)}
                {/* Legacy: the Override dropdown is the last row of this table (in the Harvest column). */}
                <TableRow key="overrideHarvestTotalPop">
                  <TableCell>Override Harvest ⁄ Total PO&P $</TableCell>
                  <TableCell>
                    <Select
                      id="overrideHarvestTotalPop"
                      labelText="Override Harvest ⁄ Total PO&P $"
                      hideLabel
                      size="sm"
                      value={form['overrideHarvestTotalPop'] ?? 'N'}
                      onChange={setField('overrideHarvestTotalPop')}
                      disabled={!editable}
                    >
                      <SelectItem value="N" text="No" />
                      <SelectItem value="Y" text="Yes" />
                    </Select>
                  </TableCell>
                  <TableCell className="schedule-3__num" />
                  <TableCell className="schedule-3__num" />
                </TableRow>
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-3__section">
          <TableContainer title="Total Overhead and Cost Per Unit Calculation">
            <Table
              aria-label="Total Overhead and Cost Per Unit Calculation"
              className="schedule-3__cost-table"
            >
              <TableHead>
                <TableRow>
                  <TableHeader aria-label="Cost item" />
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
              labelText="If you have any additional comments, please enter them here:"
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

      {/* Discard-unsaved-edits confirm before leaving an editable schedule for a sub-page. */}
      {editable && (
        <Modal
          open={pendingRoute !== null}
          modalHeading="Leave Schedule 3"
          primaryButtonText="Continue"
          secondaryButtonText="Cancel"
          onRequestClose={() => setPendingRoute(null)}
          onRequestSubmit={confirmLeave}
        >
          <p>{CONFIRM_NAVIGATION}</p>
        </Modal>
      )}
    </div>
  )
}

export default Schedule3
