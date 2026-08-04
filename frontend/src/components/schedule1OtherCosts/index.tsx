import type { FC } from 'react'
import type { OtherCostsDocument } from '@/interfaces/OtherCosts'
import type { OtherCostErrors } from './validation'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Grid,
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
import { TrashCan } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import { extractDetail } from '@/utils/error'
import { fmt, numStr, toNum } from '@/utils/number'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import PageTitle from '@/components/core/PageTitle'
import { validateOtherCost, DESCRIPTION_MAX_LENGTH } from './validation'
import './index.scss'

// Client-side chrome (verbatim legacy text); SUC-* come from the API message.text (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'
const OTHER_COSTS_PATH = '/v1/schedule1/other-costs'

/** One editable row held in local state. `id` is null for a row added but not yet saved. */
interface EditRow {
  key: number
  id: number | null
  description: string
  cost: string
}

const OtherCostsPage: FC = () => {
  const { millId, year } = useMillYear()
  const navigate = useNavigate()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<OtherCostsDocument | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // The full editable row set (legacy: every row is a live input; nothing persists until Save).
  const [rows, setRows] = useState<EditRow[]>([])
  const [rowErrors, setRowErrors] = useState<Record<number, OtherCostErrors>>({})
  const [dirty, setDirty] = useState(false)

  const [addDescription, setAddDescription] = useState('')
  const [addCost, setAddCost] = useState('')
  const [addErrors, setAddErrors] = useState<OtherCostErrors>({})

  const [confirmBackOpen, setConfirmBackOpen] = useState(false)

  // Monotonic client key for React list identity (independent of the server id, null for new rows).
  const keyCounterRef = useRef(0)

  // Derived purely from millId/year (both effect deps). The request path is a module constant.
  const query = `?millId=${millId}&year=${year}`

  const seedRows = (doc: OtherCostsDocument): EditRow[] =>
    (doc.rows ?? []).map((r) => ({
      key: keyCounterRef.current++,
      id: r.id,
      description: r.description,
      cost: numStr(r.cost),
    }))

  useEffect(() => {
    if (contextMissing) {
      return
    }
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    setMessage(null)
    setActionError(null)
    setRows([])
    setRowErrors({})
    setDirty(false)
    setAddDescription('')
    setAddCost('')
    setAddErrors({})
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<OtherCostsDocument>(`${OTHER_COSTS_PATH}${query}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setRows(seedRows(response.data))
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || 'Unable to load Other Costs.')
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
    // `query`/`seedRows` derive from millId/year (already listed); keep the effect keyed on context.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [millId, year, contextMissing])

  // Live $/m³ = entered cost ÷ the shared Other-Costs volume; display-only (blank when either absent).
  const perUnitOf = (costRaw: string): number | null => {
    const c = toNum(costRaw)
    return c !== null && data && data.volume !== null && data.volume !== 0 ? c / data.volume : null
  }

  const setRowDescription = (key: number, value: string) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, description: value } : r)))
    setDirty(true)
  }

  const setRowCost = (key: number, value: string) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, cost: value } : r)))
    setDirty(true)
  }

  const applyDocument = (doc: OtherCostsDocument) => {
    setData(doc)
    setRows(seedRows(doc))
    setRowErrors({})
    setMessage(doc.message?.text ?? null)
    setActionError(null)
    setDirty(false)
  }

  /**
   * Persist the WHOLE current row set in one call — the legacy update() that every mutation (Add,
   * Delete, Save) funnels through: validate each row, then the server reconciles insert/update/delete
   * and re-derives the totals. `intent` only selects the success message ("Data saved successfully" vs
   * "Data deleted successfully"); the persistence is identical either way.
   */
  const persist = (rowsToSave: EditRow[], intent: 'save' | 'delete') => {
    if (!data || saving) {
      return
    }
    const errs: Record<number, OtherCostErrors> = {}
    for (const row of rowsToSave) {
      const rowErr = validateOtherCost(row.description, row.cost)
      if (Object.keys(rowErr).length > 0) {
        errs[row.key] = rowErr
      }
    }
    if (Object.keys(errs).length > 0) {
      setRowErrors(errs)
      return
    }
    setRowErrors({})
    setMessage(null)
    setActionError(null)
    setSaving(true)
    apiService
      .getAxiosInstance()
      .put<OtherCostsDocument>(`${OTHER_COSTS_PATH}${query}&intent=${intent}`, {
        rows: rowsToSave.map((row) => ({
          id: row.id,
          description: row.description.trim(),
          cost: toNum(row.cost),
        })),
      })
      .then((response) => {
        applyDocument(response.data)
        // Save (not delete) clears the add form — legacy clearAddOtherCostForm() inside update(true).
        if (intent === 'save') {
          setAddDescription('')
          setAddCost('')
          setAddErrors({})
        }
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || 'Other cost could not be saved.')
      })
      .finally(() => setSaving(false))
  }

  // "Add" appends the entered row and immediately persists the whole set (legacy addOtherCost → save).
  const handleAdd = () => {
    if (saving) {
      return
    }
    setMessage(null)
    setActionError(null)
    const errors = validateOtherCost(addDescription, addCost)
    if (Object.keys(errors).length > 0) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    const next = [
      ...rows,
      { key: keyCounterRef.current++, id: null, description: addDescription.trim(), cost: addCost },
    ]
    setRows(next)
    setAddDescription('')
    setAddCost('')
    setDirty(true)
    persist(next, 'save')
  }

  // "Remove" drops the row and immediately persists the whole set (legacy deleteOtherCost → delete →
  // update(false)) so the row is deleted server-side and pending edits are flushed in the same call.
  const removeRow = (key: number) => {
    if (saving) {
      return
    }
    const next = rows.filter((r) => r.key !== key)
    setRows(next)
    setRowErrors((prev) => {
      const cleared = { ...prev }
      delete cleared[key]
      return cleared
    })
    setDirty(true)
    persist(next, 'delete')
  }

  // "Save" persists the whole set (legacy save() → update(true)).
  const handleSave = () => {
    if (rows.length === 0) {
      return
    }
    persist(rows, 'save')
  }

  const goBack = () => {
    navigate({ to: '/schedule-1' })
  }

  // Guard unsaved edits on Back (legacy confirmNavigationMsg); navigate directly when nothing is dirty.
  const handleBack = () => {
    if (dirty) {
      setConfirmBackOpen(true)
      return
    }
    goBack()
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle
        title="Subtotal Other Costs"
        subtitle="Additional cost line items for Schedule 1."
      />
    </Grid>
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
          <LoadingScreen label="Loading Other Costs" />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Unable to load Other Costs',
          subtitle: errorDetail,
        }}
      >
        <Column sm={4} md={8} lg={16}>
          <Button kind="secondary" onClick={goBack}>
            Back to Schedule 1
          </Button>
        </Column>
      </PageState>
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable

  // Live $/m³ preview for the Add form (entered cost ÷ shared volume). Display-only.
  const addPerUnitPreview = numStr(perUnitOf(addCost))

  const rowCells = (row: EditRow) => {
    const errs = rowErrors[row.key] ?? {}
    if (editable) {
      return (
        <>
          <TableCell>
            <TextInput
              id={`row-description-${row.key}`}
              labelText="Edit description"
              hideLabel
              size="sm"
              maxLength={DESCRIPTION_MAX_LENGTH}
              value={row.description}
              onChange={(e) => setRowDescription(row.key, e.target.value)}
              invalid={Boolean(errs.description)}
              invalidText={errs.description}
            />
          </TableCell>
          <TableCell className="schedule-1__num">{fmt(data.volume)}</TableCell>
          <TableCell className="schedule-1__num">
            <TextInput
              id={`row-cost-${row.key}`}
              labelText="Edit cost"
              hideLabel
              size="sm"
              value={row.cost}
              onChange={(e) => setRowCost(row.key, e.target.value)}
              invalid={Boolean(errs.cost)}
              invalidText={errs.cost}
            />
          </TableCell>
          <TableCell className="schedule-1__num">{fmt(perUnitOf(row.cost))}</TableCell>
          <TableCell>
            <Button
              kind="danger--ghost"
              size="sm"
              hasIconOnly
              iconDescription="Remove"
              renderIcon={TrashCan}
              disabled={saving}
              onClick={() => removeRow(row.key)}
            />
          </TableCell>
        </>
      )
    }
    return (
      <>
        <TableCell>{row.description}</TableCell>
        <TableCell className="schedule-1__num">{fmt(data.volume)}</TableCell>
        <TableCell className="schedule-1__num">{fmt(toNum(row.cost))}</TableCell>
        <TableCell className="schedule-1__num">{fmt(perUnitOf(row.cost))}</TableCell>
      </>
    )
  }

  return (
    <div className="app-page">
      <Grid fullWidth className="app-page__body">
        {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
        {actionError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
        )}

        {/* Legacy layout: a titled "Add Other Cost" panel above the list (Description, Volume, Cost,
            $/m³ stacked with left labels). Volume is the shared Schedule 1 volume and $/m³ is a live
            cost÷volume preview — both read-only; only Description and Cost are entered. */}
        {editable && (
          <Column sm={4} md={8} lg={16} className="schedule-1__section">
            <section className="oc-panel">
              <h3 className="oc-panel__title">Add Other Cost</h3>
              <div className="oc-panel__body">
                <div className="oc-add">
                  <TextInput
                    id="add-description"
                    className="oc-add__field oc-add__field--wide"
                    labelText="Description"
                    size="sm"
                    maxLength={DESCRIPTION_MAX_LENGTH}
                    value={addDescription}
                    onChange={(e) => setAddDescription(e.target.value)}
                    invalid={Boolean(addErrors.description)}
                    invalidText={addErrors.description}
                  />
                  <TextInput
                    id="add-volume"
                    className="oc-add__field oc-add__field--narrow"
                    labelText="Volume"
                    size="sm"
                    value={numStr(data.volume)}
                    onChange={() => undefined}
                    disabled
                  />
                  <TextInput
                    id="add-cost"
                    className="oc-add__field oc-add__field--narrow"
                    labelText="Cost"
                    size="sm"
                    value={addCost}
                    onChange={(e) => setAddCost(e.target.value)}
                    invalid={Boolean(addErrors.cost)}
                    invalidText={addErrors.cost}
                  />
                  <TextInput
                    id="add-perunit"
                    className="oc-add__field oc-add__field--narrow"
                    labelText="$ / m³"
                    size="sm"
                    value={addPerUnitPreview}
                    onChange={() => undefined}
                    disabled
                  />
                  <div className="oc-add__actions">
                    <Button kind="primary" disabled={saving} onClick={handleAdd}>
                      Add
                    </Button>
                  </div>
                </div>
              </div>
            </section>
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="schedule-1__section">
          <section className="oc-panel">
            <h3 className="oc-panel__title">Other Cost List</h3>
            <div className="oc-panel__body">
              <TableContainer>
                <Table aria-label="Other Cost List">
                  <TableHead>
                    <TableRow>
                      <TableHeader>Description</TableHeader>
                      <TableHeader className="schedule-1__num">Volume m³</TableHeader>
                      <TableHeader className="schedule-1__num">Cost $</TableHeader>
                      <TableHeader className="schedule-1__num">$ / m³</TableHeader>
                      {editable && <TableHeader>Action</TableHeader>}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={editable ? 5 : 4}>No records found.</TableCell>
                      </TableRow>
                    ) : (
                      rows.map((row) => <TableRow key={row.key}>{rowCells(row)}</TableRow>)
                    )}
                    {/* Totals footer — last-saved figures; refresh after Save (legacy recomputed on save). */}
                    <TableRow className="schedule-1-other-costs__totals">
                      <TableCell>Totals</TableCell>
                      <TableCell className="schedule-1__num">{fmt(data.volume)}</TableCell>
                      <TableCell className="schedule-1__num">{fmt(data.costSubtotal)}</TableCell>
                      <TableCell className="schedule-1__num">{fmt(data.perUnit)}</TableCell>
                      {editable && <TableCell />}
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          </section>
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-1__actions">
          {editable && (
            <Button
              kind="primary"
              // Greyed out until there is data to save (and while saving) — legacy parity.
              disabled={saving || rows.length === 0}
              onClick={handleSave}
            >
              Save
            </Button>
          )}
          <Button kind="secondary" onClick={handleBack}>
            Back to Schedule 1
          </Button>
        </Column>
      </Grid>

      {editable && (
        <Modal
          open={confirmBackOpen}
          modalHeading="Leave page"
          primaryButtonText="Continue"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmBackOpen(false)}
          onRequestSubmit={() => {
            setConfirmBackOpen(false)
            goBack()
          }}
        >
          <p>{CONFIRM_NAVIGATION}</p>
        </Modal>
      )}
    </div>
  )
}

export default OtherCostsPage
