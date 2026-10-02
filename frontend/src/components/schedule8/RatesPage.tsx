import OriginalValueIndicator from '@/components/core/OriginalValueIndicator'
import type { FC } from 'react'
import type Schedule8Response from '@/interfaces/Schedule8Response'
import type { RateRow } from '@/interfaces/Schedule8Response'
import type { CodeOption } from '@/interfaces/Schedule8Options'
import { useState } from 'react'
import {
  Button,
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
import { Add, ArrowLeft, Close, Save, TrashCan } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import type { BannerEntry } from '@/utils/legacyValidationBanner'
import {
  emptyRateForm,
  fmt,
  rateBanner,
  toNum,
  validateRateForm,
  type RateForm,
} from './validation'
import { useJudgedForm } from './useJudgedForm'
import CodeComboBox from '@/components/core/CodeComboBox'
import LevelBanners from './LevelBanners'

// Client-only confirm chrome, verbatim from the legacy bundle (confirmDeleteMsg intent).
const CONFIRM_DELETE_ROW = 'This will delete the current record. Do you want to continue?'
// Back confirm — literal from the legacy markup (confirmNavigationMsg).
const NAV_UNSAVED = 'Unsaved data will be lost. Are you sure to continue?'

const sumRates = (rows: RateRow[]): number =>
  rows.reduce((total, r) => total + (r.costingRate ?? 0), 0)

const ADDITION_BANNER = rateBanner('addition')
const DEDUCTION_BANNER = rateBanner('deduction')

type RateEditor = ReturnType<typeof useJudgedForm<RateForm>>

interface RatesPageProps {
  millId: number
  year: number
  sampleId: number
  sampleTitle: string
  additions: RateRow[]
  deductions: RateRow[]
  additionCostItems: CodeOption[]
  deductionCostItems: CodeOption[]
  costTypes: CodeOption[]
  editable: boolean
  onBack: () => void
  onDocUpdate: (doc: Schedule8Response) => void
}

/**
 * The Schedule 8 Additions/Deductions screen (Story 14.4, S01/S06/S09) for a single saved sample: the
 * two rate tables, each with an add-row form (cost item / $/m³ / cost type + description), per-row
 * Delete, and a footer total (CNT-003). Add/Delete call the rate sub-resource and lift the recomputed
 * document up via onDocUpdate; which table a new row lands in is decided server-side by the cost
 * item's subcategory. Read-only when the schedule is not editable.
 */
const RatesPage: FC<RatesPageProps> = ({
  millId,
  year,
  sampleId,
  sampleTitle,
  additions,
  deductions,
  additionCostItems,
  deductionCostItems,
  costTypes,
  editable,
  onBack,
  onDocUpdate,
}) => {
  // The validation banner both add forms write into, one keyed line per failing field (#359 group C
  // change log, 2026-10-02): the Additions form's lines first, then the Deductions form's. Each Add
  // replaces only its own form's lines — legacy's Add processed only its own panel
  // (`schedule8AdditionsAndDeductions.xhtml:147-148,366-367`) — and leaving a changed field adds or
  // removes only that field's line.
  const [bannerEntries, setBannerEntries] = useState<readonly BannerEntry[]>([])
  const addition = useJudgedForm<RateForm>({
    initial: emptyRateForm,
    validate: validateRateForm,
    scheme: ADDITION_BANNER,
    setBanner: setBannerEntries,
  })
  const deduction = useJudgedForm<RateForm>({
    initial: emptyRateForm,
    validate: validateRateForm,
    scheme: DEDUCTION_BANNER,
    setBanner: setBannerEntries,
  })
  const addForm = addition.form
  const dedForm = deduction.form
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirmDeleteRow, setConfirmDeleteRow] = useState<RateRow | null>(null)
  const [confirmBack, setConfirmBack] = useState(false)

  // A form is "dirty" when the user has typed a not-yet-Added row (any field, INCLUDING description).
  const dirty = (form: RateForm) =>
    form.costItemCode.trim() !== '' ||
    form.costingRate.trim() !== '' ||
    form.costTypeCode.trim() !== '' ||
    form.itemDescription.trim() !== ''
  const isAddDirty = dirty(addForm)
  const isDedDirty = dirty(dedForm)
  const isDirty = isAddDirty || isDedDirty

  // Cancel = discard; confirm first only when there is an uncommitted draft (rows persist on Add).
  const requestBack = () => {
    if (editable && isDirty) setConfirmBack(true)
    else onBack()
  }

  // Resolves true on a successful save, false on validation/API failure — the caller clears the
  // add-row form ONLY on success, so a rejected POST keeps the user's typed values.
  const submitRate = (form: RateForm): Promise<boolean> => {
    setBusy(true)
    setMessage(null)
    setError(null)
    return apiService
      .getAxiosInstance()
      .post<Schedule8Response>(
        `/v1/schedule8/samples/${sampleId}/rates?millId=${millId}&year=${year}`,
        {
          id: null,
          revisionCount: null,
          costItemCode: toNum(form.costItemCode),
          costingRate: toNum(form.costingRate),
          costTypeCode: form.costTypeCode,
          itemDescription: form.itemDescription.trim() === '' ? null : form.itemDescription,
        },
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
        return true
      })
      .catch((err: unknown) => {
        setError(extractDetail(err) || 'Row could not be saved.')
        return false
      })
      .finally(() => setBusy(false))
  }

  /**
   * Check ONE add form, as its legacy Add did (`process="@this addAdditionsPnl"`, :148, and
   * `addDeductionsPnl`, :367): its own lines in the banner are replaced with what it finds, the other
   * form's red fields and lines stay as they are. True when the form passed.
   */
  const checkRateForm = (editor: RateEditor): boolean => {
    setMessage(null)
    setError(null)
    return Object.keys(editor.validateAll(true)).length === 0
  }

  const addRate = (editor: RateEditor) => {
    if (busy || !checkRateForm(editor)) return
    void submitRate(editor.formRef.current).then((ok) => {
      if (ok) {
        editor.seed(emptyRateForm())
      }
    })
  }

  // Save = commit any typed-but-not-yet-Added draft row(s), then return to the sample. An invalid
  // draft blocks the exit and surfaces its errors (never silently discarded). With nothing pending it
  // just returns (each Added row already persisted).
  //
  // Both drafts are checked, neither blocking the other, and the banner is REPLACED with every
  // failing line of both — Additions first, then Deductions — as legacy's Save processed both tables
  // at once (`process="AdditionsDT DeductionsDT @this"`, `schedule8AdditionsAndDeductions.xhtml:62`).
  // Legacy's tables were editable rows; here the rows are read-only and the drafts are what Save
  // commits, so the drafts are what it checks, with their own (add-row) labels.
  const handleSave = () => {
    if (busy) return
    // The forms as last written — a field's leave in the same click may have just written one.
    const addDraft = addition.formRef.current
    const dedDraft = deduction.formRef.current
    const addDirty = dirty(addDraft)
    const dedDirty = dirty(dedDraft)
    setBannerEntries([])
    addition.clearErrors()
    deduction.clearErrors()
    const addOk = !addDirty || checkRateForm(addition)
    const dedOk = !dedDirty || checkRateForm(deduction)
    if (!addOk || !dedOk) {
      return
    }
    const pending: Promise<boolean>[] = []
    if (addDirty) pending.push(submitRate(addDraft))
    if (dedDirty) pending.push(submitRate(dedDraft))
    if (pending.length === 0) {
      onBack()
      return
    }
    void Promise.all(pending).then((results) => {
      if (results.every(Boolean)) onBack()
    })
  }

  const handleDeleteRow = () => {
    if (busy || !confirmDeleteRow) return
    const rowId = confirmDeleteRow.id
    setConfirmDeleteRow(null)
    setBusy(true)
    setMessage(null)
    setError(null)
    apiService
      .getAxiosInstance()
      .delete<Schedule8Response>(
        `/v1/schedule8/samples/${sampleId}/rates/${rowId}?millId=${millId}&year=${year}`,
      )
      .then((response) => {
        onDocUpdate(response.data)
        setMessage(response.data.message?.text ?? null)
      })
      .catch((err: unknown) => setError(extractDetail(err) || 'Unable to delete row.'))
      .finally(() => setBusy(false))
  }

  const rateTable = (
    kind: 'addition' | 'deduction',
    label: string,
    rows: RateRow[],
    editor: RateEditor,
    costItems: CodeOption[],
  ) => {
    const { form, errors } = editor
    // Typing is not judged; a field is judged when it is left changed, a dropdown on selection.
    const setField = (field: keyof RateForm) => (event: React.ChangeEvent<HTMLInputElement>) =>
      editor.write({ ...editor.formRef.current, [field]: event.target.value })
    const setCode = (field: keyof RateForm) => (code: string) =>
      editor.select(field, { ...editor.formRef.current, [field]: code })
    // Resolve a stored cost-item code to its name for the table cell (falls back to the raw code).
    const costItemName = (code: number | null) =>
      costItems.find((o) => o.code === String(code))?.description ?? fmt(code)
    return (
      <div className="schedule-8__rate-section">
        <h4 className="schedule-8__rate-heading">{`${label} (${rows.length})`}</h4>
        {editable && (
          <div className="schedule-8__form">
            <CodeComboBox
              id={`${kind}-costItemCode`}
              titleText={`${label} — Cost Item`}
              items={costItems}
              selectedCode={form.costItemCode}
              invalid={Boolean(errors.costItemCode)}
              invalidText={errors.costItemCode}
              onSelect={setCode('costItemCode')}
              onLeaveChanged={() => editor.leave('costItemCode', true)}
            />
            <TextInput
              id={`${kind}-costingRate`}
              labelText={`${label} — $/m³`}
              size="sm"
              inputMode="numeric"
              value={form.costingRate}
              onChange={setField('costingRate')}
              onFocus={() => editor.enter('costingRate')}
              onBlur={() => editor.leave('costingRate')}
              invalid={Boolean(errors.costingRate)}
              invalidText={errors.costingRate}
            />
            <CodeComboBox
              id={`${kind}-costTypeCode`}
              titleText={`${label} — Cost Type`}
              items={costTypes}
              selectedCode={form.costTypeCode}
              invalid={Boolean(errors.costTypeCode)}
              invalidText={errors.costTypeCode}
              onSelect={setCode('costTypeCode')}
              onLeaveChanged={() => editor.leave('costTypeCode', true)}
            />
            <TextInput
              id={`${kind}-itemDescription`}
              labelText={`${label} — Description`}
              size="sm"
              maxLength={30}
              value={form.itemDescription}
              onChange={setField('itemDescription')}
              onFocus={() => editor.enter('itemDescription')}
              onBlur={() => editor.leave('itemDescription')}
              invalid={Boolean(errors.itemDescription)}
              invalidText={errors.itemDescription}
            />
            <Button
              kind="primary"
              size="sm"
              disabled={busy}
              renderIcon={Add}
              onClick={() => addRate(editor)}
            >
              Add {label}
            </Button>
          </div>
        )}

        <TableContainer className="schedule-8__grid">
          <Table aria-label={label}>
            <TableHead>
              <TableRow>
                <TableHeader>Cost Item</TableHeader>
                <TableHeader>Description</TableHeader>
                <TableHeader className="schedule-8__num">$/m³</TableHeader>
                <TableHeader>Cost Type</TableHeader>
                {editable && <TableHeader>Actions</TableHeader>}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={editable ? 5 : 4}>No rows have been added.</TableCell>
                </TableRow>
              ) : (
                rows.map((row) => (
                  <TableRow key={row.id}>
                    {/* Legacy rendered four indicators on a rate row
                        (TreeToTruckRateDetailDO.java:273-282); the row grid is read-only here, so
                        each cell compares its SERVED value. */}
                    <TableCell>
                      {costItemName(row.costItemCode)}
                      <OriginalValueIndicator
                        originals={row.originalValues}
                        field="costItemCode"
                        current={row.costItemCode}
                        numeric={false}
                        label="Cost Item"
                      />
                    </TableCell>
                    <TableCell>
                      {row.itemDescription ?? '—'}
                      <OriginalValueIndicator
                        originals={row.originalValues}
                        field="itemDescription"
                        current={row.itemDescription}
                        numeric={false}
                        label="Description"
                      />
                    </TableCell>
                    <TableCell className="schedule-8__num">
                      {fmt(row.costingRate)}
                      <OriginalValueIndicator
                        originals={row.originalValues}
                        field="costingRate"
                        current={row.costingRate}
                        label="$/m³"
                      />
                    </TableCell>
                    <TableCell>
                      {row.costTypeDescription ?? row.costTypeCode ?? '—'}
                      <OriginalValueIndicator
                        originals={row.originalValues}
                        field="costTypeCode"
                        current={row.costTypeCode}
                        numeric={false}
                        label="Cost Type"
                      />
                    </TableCell>
                    {editable && (
                      <TableCell>
                        <Button
                          kind="danger--tertiary"
                          size="sm"
                          disabled={busy}
                          renderIcon={TrashCan}
                          onClick={() => setConfirmDeleteRow(row)}
                        >
                          Delete
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))
              )}
              {rows.length > 0 && (
                // Totals as the table's last row (the $/m³ column carries the sum), like the legacy
                // footer and the Schedule 4 sub-page.
                <TableRow className="schedule-8__totals-row">
                  <TableCell>{label} Total</TableCell>
                  <TableCell />
                  <TableCell className="schedule-8__num">{fmt(sumRates(rows))}</TableCell>
                  <TableCell />
                  {editable && <TableCell />}
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </div>
    )
  }

  return (
    <div className="schedule-8__level">
      <div className="schedule-8__level-header">
        <h3 className="schedule-8__heading">Additions / Deductions — {sampleTitle}</h3>
      </div>

      <LevelBanners message={message} error={error} entries={bannerEntries} />

      {rateTable('addition', 'Additions', additions, addition, additionCostItems)}
      {rateTable('deduction', 'Deductions', deductions, deduction, deductionCostItems)}

      {/* Save commits any typed-but-not-yet-Added draft then returns; Cancel discards (confirming only
          when there is a draft). Read-only shows a single Close (nothing to save). */}
      <div className="schedule-8__panel-actions">
        {editable && (
          <Button kind="primary" disabled={busy} renderIcon={Save} onClick={handleSave}>
            Save
          </Button>
        )}
        <Button
          kind="secondary"
          disabled={busy}
          // Back on an editable panel, Close on a read-only one — the glyph follows the label.
          renderIcon={editable ? ArrowLeft : Close}
          onClick={requestBack}
        >
          {editable ? 'Back' : 'Close'}
        </Button>
      </div>

      {editable && (
        <Modal
          open={confirmDeleteRow !== null}
          danger
          modalHeading="Delete rate row"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmDeleteRow(null)}
          onRequestSubmit={handleDeleteRow}
        >
          <p>{CONFIRM_DELETE_ROW}</p>
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

export default RatesPage
