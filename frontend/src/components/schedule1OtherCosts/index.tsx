import type { FC } from 'react'
import type { OtherCostsDocument } from '@/interfaces/OtherCosts'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react'
import { Add, TrashCan } from '@carbon/icons-react'
import { fmtCurrency, fmtNumber, groupInput, numStrGroup, toNum } from '@/utils/number'
import EditableSubPageLayout from '@/components/core/EditableSubPageLayout'
import SubPanel from '@/components/core/SubPanel'
import { useEditableCostRows, type EditRow } from '@/hooks/useEditableCostRows'
import { useRowSort } from '@/hooks/useRowSort'
import { validateOtherCost, DESCRIPTION_MAX_LENGTH } from './validation'
import './index.scss'

const OTHER_COSTS_PATH = '/v1/schedule1/other-costs'

const OtherCostsPage: FC = () => {
  const navigate = useNavigate()

  const editor = useEditableCostRows<OtherCostsDocument>({
    base: OTHER_COSTS_PATH,
    fieldKeys: ['cost'],
    loadError: 'Unable to load Other Costs.',
    saveError: 'Other cost could not be saved.',
    rowsFromDoc: (doc) =>
      (doc.rows ?? []).map((r) => ({
        id: r.id,
        description: r.description,
        values: { cost: numStrGroup(r.cost) },
      })),
    validate: (description, values) => validateOtherCost(description, values.cost),
    onBack: () => navigate({ to: '/schedule-1' }),
  })

  const {
    rows,
    rowErrors,
    addDescription,
    setAddDescription,
    addValues,
    setAddValue,
    addErrors,
    setRowDescription,
    setRowValue,
    handleAdd,
    removeRow,
    saving,
  } = editor

  // Client-side column sort, matching legacy schedule1OtherCosts.xhtml (Description / Volume / Cost
  // sortable; the derived $/m³ column is not). See useRowSort for the snapshot-on-click semantics.
  const sort = useRowSort(
    rows,
    {
      description: (row) => row.description,
      // Volume is the single shared Other-Costs volume (identical on every row), so sorting by it is
      // a no-op in practice — kept sortable for legacy parity (the legacy column carried sortBy
      // volume).
      volume: () => editor.data?.volume ?? null,
      cost: (row) => toNum(row.values.cost ?? ''),
    },
    (row) => row.key,
  )

  return (
    <EditableSubPageLayout
      editor={editor}
      scheduleName="Schedule 1"
      title="Subtotal Other Costs"
      backLabel="Back"
      loadingLabel="Loading Other Costs"
      errorTitle="Unable to load Other Costs"
    >
      {(data) => {
        const editable = data.editable
        const volume = data.volume

        // Live $/m³ = entered cost ÷ the shared Other-Costs volume; blank when either is absent.
        const perUnitOf = (costRaw: string): number | null => {
          const c = toNum(costRaw)
          return c !== null && volume !== null && volume !== 0 ? c / volume : null
        }

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
                <TableCell className="schedule-1__num">{fmtNumber(volume)}</TableCell>
                <TableCell className="schedule-1__num schedule-1__num--input">
                  <TextInput
                    id={`row-cost-${row.key}`}
                    labelText="Edit cost"
                    hideLabel
                    size="sm"
                    value={row.values.cost ?? ''}
                    onChange={(e) => setRowValue(row.key, 'cost', e.target.value)}
                    // Re-group on blur only — regrouping mid-keystroke would fight the caret.
                    onBlur={() => setRowValue(row.key, 'cost', groupInput(row.values.cost ?? ''))}
                    invalid={Boolean(errs.cost)}
                    invalidText={errs.cost}
                  />
                </TableCell>
                <TableCell className="schedule-1__num">
                  {fmtCurrency(perUnitOf(row.values.cost ?? ''))}
                </TableCell>
                <TableCell>
                  <Button
                    kind="danger--tertiary"
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
              <TableCell className="schedule-1__num">{fmtNumber(volume)}</TableCell>
              <TableCell className="schedule-1__num">
                {fmtNumber(toNum(row.values.cost ?? ''))}
              </TableCell>
              <TableCell className="schedule-1__num">
                {fmtCurrency(perUnitOf(row.values.cost ?? ''))}
              </TableCell>
            </>
          )
        }

        return (
          <>
            {/* Legacy layout: a titled "Add Other Cost" panel above the list. Volume is the shared
                Schedule 1 volume and $/m³ is a live cost÷volume preview — both read-only. */}
            {editable && (
              <Column sm={4} md={8} lg={16} className="schedule-1__section">
                <SubPanel title="Add Other Cost">
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
                      className="oc-add__field"
                      labelText="Volume"
                      size="sm"
                      value={numStrGroup(volume)}
                      onChange={() => undefined}
                      disabled
                    />
                    <TextInput
                      id="add-cost"
                      className="oc-add__field"
                      labelText="Cost"
                      size="sm"
                      value={addValues.cost ?? ''}
                      onChange={(e) => setAddValue('cost', e.target.value)}
                      onBlur={() => setAddValue('cost', groupInput(addValues.cost ?? ''))}
                      invalid={Boolean(addErrors.cost)}
                      invalidText={addErrors.cost}
                    />
                    <TextInput
                      id="add-perunit"
                      className="oc-add__field"
                      labelText="$ / m³"
                      size="sm"
                      value={numStrGroup(perUnitOf(addValues.cost ?? ''))}
                      onChange={() => undefined}
                      disabled
                    />
                    <div className="oc-add__actions">
                      <Button
                        kind="primary"
                        size="md"
                        disabled={saving}
                        renderIcon={Add}
                        onClick={handleAdd}
                      >
                        Add
                      </Button>
                    </div>
                  </div>
                </SubPanel>
              </Column>
            )}

            <Column sm={4} md={8} lg={16} className="schedule-1__section">
              <SubPanel title="Other Cost List">
                <TableContainer>
                  <Table aria-label="Other Cost List" className="schedule-1-other-costs__table">
                    <TableHead>
                      <TableRow>
                        <TableHeader
                          isSortable
                          isSortHeader={sort.activeKey === 'description'}
                          sortDirection={sort.directionFor('description')}
                          onClick={() => sort.toggleSort('description')}
                        >
                          Description
                        </TableHeader>
                        <TableHeader
                          className="schedule-1__num"
                          isSortable
                          isSortHeader={sort.activeKey === 'volume'}
                          sortDirection={sort.directionFor('volume')}
                          onClick={() => sort.toggleSort('volume')}
                        >
                          Volume m³
                        </TableHeader>
                        <TableHeader
                          className="schedule-1__num"
                          isSortable
                          isSortHeader={sort.activeKey === 'cost'}
                          sortDirection={sort.directionFor('cost')}
                          onClick={() => sort.toggleSort('cost')}
                        >
                          Cost $
                        </TableHeader>
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
                        sort.sortedRows.map((row) => (
                          <TableRow key={row.key}>{rowCells(row)}</TableRow>
                        ))
                      )}
                      {/* Totals footer — last-saved figures; refresh after Save (legacy recompute).
                          A null total (e.g. $/m³ when volume is 0/absent) shows 0, not an em dash,
                          so the empty/zero-volume Totals row reads 0 across every column. */}
                      <TableRow className="schedule-1-other-costs__totals">
                        <TableCell>Totals</TableCell>
                        <TableCell className="schedule-1__num">{fmtNumber(volume ?? 0)}</TableCell>
                        <TableCell className="schedule-1__num">
                          {fmtNumber(data.costSubtotal ?? 0)}
                        </TableCell>
                        <TableCell className="schedule-1__num">
                          {fmtCurrency(data.perUnit ?? 0)}
                        </TableCell>
                        {editable && <TableCell />}
                      </TableRow>
                    </TableBody>
                  </Table>
                </TableContainer>
              </SubPanel>
            </Column>
          </>
        )
      }}
    </EditableSubPageLayout>
  )
}

export default OtherCostsPage
