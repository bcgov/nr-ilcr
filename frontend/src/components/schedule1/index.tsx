import type { FC } from 'react'
import type Schedule1Response from '@/interfaces/Schedule1Response'
import type { LineItem } from '@/interfaces/Schedule1Response'
import type Schedule1Request from '@/interfaces/Schedule1Request'
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
import { WRITABLE_LINE_ITEM_CODES } from '@/interfaces/Schedule1Request'
import useMillYear from '@/context/millYear/useMillYear'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'
import { validateSchedule1 } from './validation'
import './index.scss'

// ERR-001 (mill/year not selected) and ALT-001 (open-other-costs-before-save) and confirmDeleteMsg
// are client-side chrome (a suppression with no request / a browser alert / a confirm dialog), so
// their verbatim text lives here. SUC-001/SUC-002 come from the API `message.text` (AD-8) — never
// hardcoded.
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
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

// Silviculture code -> label; codes 1 & 2 are editable, 139 (pulled cost) and 140 (derived) read-only.
const SILV_ROWS: { code: number; label: string; key: keyof Schedule1Response['silviculture'] }[] = [
  { code: 1, label: 'Actual $ Spent', key: 'actualSpent' },
  { code: 2, label: 'Accrued less Actual $ Spent', key: 'accruedLessActual' },
  { code: 139, label: 'Less Silviculture Admin Costs', key: 'lessAdmin' },
  { code: 140, label: 'Total Silviculture', key: 'total' },
]

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
    },
    otherCostsVolume: toNum(form['otherCostsVolume']),
  }
}

const Schedule1: FC = () => {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<Schedule1Response | null>(null)
  const [form, setForm] = useState<FieldValues>({})
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

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
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<Schedule1Response>(`/v1/schedule1?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setForm(seedForm(response.data))
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Schedule 1.')
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
    (key: string) => (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [key]: value }))
    }

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

  const handleOtherCosts = () => {
    // Story 2.5 wires navigation to the Other Costs sub-page. The button is only rendered for an
    // editable (already-saved) schedule, so no "save before opening" guard is needed here.
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle title="Schedule 1" subtitle="Average Cost of Logging." />
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
    const writable = row.code === 1 || row.code === 2
    return (
      <TableRow key={row.code}>
        <TableCell>{row.label}</TableCell>
        {numberCell(`vol-${row.code}`, `${row.label} volume`, writable, item?.volume)}
        {numberCell(`cost-${row.code}`, `${row.label} cost`, writable, item?.cost)}
        <TableCell className="schedule-1__num">{fmt(item?.perUnit)}</TableCell>
      </TableRow>
    )
  }

  const actions = (
    <Column sm={4} md={8} lg={16} className="schedule-1__actions">
      <Button kind="primary" disabled={!editable || saving} onClick={handleSave}>
        Save
      </Button>
      {/* Check Status renders per the legacy layout; its endpoint is wired in Story 2.7. */}
      <Button kind="tertiary" disabled>
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
        <Column sm={4} md={8} lg={16} className="schedule-1__meta">
          <dl className="schedule-1__summary">
            <div className="schedule-1__summary-item">
              <dt>Mill</dt>
              <dd>{data.millId}</dd>
            </div>
            <div className="schedule-1__summary-item">
              <dt>Reporting Year</dt>
              <dd>{data.year}</dd>
            </div>
            <div className="schedule-1__summary-item">
              <dt>Status</dt>
              <dd>{data.trackStatus ?? '—'}</dd>
            </div>
            <div className="schedule-1__summary-item">
              <dt>Crown Timber Volume</dt>
              <dd>{fmt(data.crownVolume)}</dd>
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

        {actions}

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          <TableContainer title="Company Logging Costs">
            <Table aria-label="Company Logging Costs">
              <TableHead>
                <TableRow>
                  <TableHeader>Cost Item</TableHeader>
                  <TableHeader className="schedule-1__num">Volume</TableHeader>
                  <TableHeader className="schedule-1__num">Cost</TableHeader>
                  <TableHeader className="schedule-1__num">$/m³</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>{data.lineItems.map(lineItemRow)}</TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          <TableContainer title="Silviculture">
            <Table aria-label="Silviculture">
              <TableHead>
                <TableRow>
                  <TableHeader>Cost Item</TableHeader>
                  <TableHeader className="schedule-1__num">Volume</TableHeader>
                  <TableHeader className="schedule-1__num">Cost</TableHeader>
                  <TableHeader className="schedule-1__num">$/m³</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>{SILV_ROWS.map(silvicultureRow)}</TableBody>
            </Table>
          </TableContainer>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          <div className="schedule-1__other-costs">
            {editable ? (
              <Button kind="ghost" size="sm" onClick={handleOtherCosts}>
                Subtotal Other Costs({data.otherCosts.count}):
              </Button>
            ) : (
              <span>Subtotal Other Costs({data.otherCosts.count}):</span>
            )}
            <span className="schedule-1__num">{fmt(data.otherCosts.costSubtotal)}</span>
          </div>
          {editable && (
            <TextInput
              id="otherCostsVolume"
              labelText="Subtotal Other Costs volume"
              size="sm"
              value={form['otherCostsVolume'] ?? ''}
              onChange={setField('otherCostsVolume')}
              invalid={Boolean(fieldErrors['otherCostsVolume'])}
              invalidText={fieldErrors['otherCostsVolume']}
            />
          )}
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
    </div>
  )
}

export default Schedule1
