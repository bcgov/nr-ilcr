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
import { TrashCan } from '@carbon/icons-react'
import { fmt, numStr, toNum } from '@/utils/number'
import EditableSubPageLayout from '@/components/core/EditableSubPageLayout'
import SubPanel from '@/components/core/SubPanel'
import { useEditableCostRows, type EditRow } from '@/hooks/useEditableCostRows'
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
        values: { cost: numStr(r.cost) },
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

  return (
    <EditableSubPageLayout
      editor={editor}
      breadCrumbs={[
        { name: 'ILCR', path: '/' },
        { name: 'Schedule 1', path: '/schedule-1' },
      ]}
      title="Subtotal Other Costs"
      backLabel="Back to Schedule 1"
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
                <TableCell className="schedule-1__num">{fmt(volume)}</TableCell>
                <TableCell className="schedule-1__num">
                  <TextInput
                    id={`row-cost-${row.key}`}
                    labelText="Edit cost"
                    hideLabel
                    size="sm"
                    value={row.values.cost ?? ''}
                    onChange={(e) => setRowValue(row.key, 'cost', e.target.value)}
                    invalid={Boolean(errs.cost)}
                    invalidText={errs.cost}
                  />
                </TableCell>
                <TableCell className="schedule-1__num">
                  {fmt(perUnitOf(row.values.cost ?? ''))}
                </TableCell>
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
              <TableCell className="schedule-1__num">{fmt(volume)}</TableCell>
              <TableCell className="schedule-1__num">{fmt(toNum(row.values.cost ?? ''))}</TableCell>
              <TableCell className="schedule-1__num">
                {fmt(perUnitOf(row.values.cost ?? ''))}
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
                      className="oc-add__field oc-add__field--narrow"
                      labelText="Volume"
                      size="sm"
                      value={numStr(volume)}
                      onChange={() => undefined}
                      disabled
                    />
                    <TextInput
                      id="add-cost"
                      className="oc-add__field oc-add__field--narrow"
                      labelText="Cost"
                      size="sm"
                      value={addValues.cost ?? ''}
                      onChange={(e) => setAddValue('cost', e.target.value)}
                      invalid={Boolean(addErrors.cost)}
                      invalidText={addErrors.cost}
                    />
                    <TextInput
                      id="add-perunit"
                      className="oc-add__field oc-add__field--narrow"
                      labelText="$ / m³"
                      size="sm"
                      value={numStr(perUnitOf(addValues.cost ?? ''))}
                      onChange={() => undefined}
                      disabled
                    />
                    <div className="oc-add__actions">
                      <Button kind="primary" disabled={saving} onClick={handleAdd}>
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
                      {/* Totals footer — last-saved figures; refresh after Save (legacy recompute). */}
                      <TableRow className="schedule-1-other-costs__totals">
                        <TableCell>Totals</TableCell>
                        <TableCell className="schedule-1__num">{fmt(volume)}</TableCell>
                        <TableCell className="schedule-1__num">{fmt(data.costSubtotal)}</TableCell>
                        <TableCell className="schedule-1__num">{fmt(data.perUnit)}</TableCell>
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
