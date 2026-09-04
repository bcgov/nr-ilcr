import type { FC } from 'react'
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react'
import { Add, ArrowLeft, Close, Edit, Save, TrashCan, View } from '@carbon/icons-react'
import type {
  ConstructionPage,
  RoadDetail,
  Schedule10CodeLists,
} from '@/interfaces/Schedule10Response'
import RoadDetailFields from './RoadDetailFields'
import type { MaskedField, RoadDetailErrors, RoadDetailFormValues } from './validation'

export type PanelMode = 'closed' | 'new' | 'edit' | 'view'

type RoadDetailPageProps = {
  readonly page: ConstructionPage
  readonly codeLists: Schedule10CodeLists
  readonly editable: boolean
  readonly saving: boolean
  readonly panelMode: PanelMode
  readonly openDetailId: number | null
  readonly form: RoadDetailFormValues
  readonly errors: RoadDetailErrors
  readonly onOpenNew: () => void
  readonly onOpenDetail: (detail: RoadDetail) => void
  readonly onCloseForm: () => void
  readonly onSave: () => void
  readonly onRequestDelete: (detail: RoadDetail) => void
  readonly onBack: () => void
  readonly onChange: (key: keyof RoadDetailFormValues, value: string) => void
  readonly onMask: (key: MaskedField) => void
}

const EMPTY_LIST = 'No records found.'

/**
 * The road-detail level: the list of a page's roads with the road form below it. Both render
 * together, matching the legacy screen where opening a road keeps its list in view.
 */
const RoadDetailPage: FC<RoadDetailPageProps> = ({
  page,
  codeLists,
  editable,
  saving,
  panelMode,
  openDetailId,
  form,
  errors,
  onOpenNew,
  onOpenDetail,
  onCloseForm,
  onSave,
  onRequestDelete,
  onBack,
  onChange,
  onMask,
}) => {
  const controlsDisabled = !editable || saving
  const readOnly = panelMode === 'view'

  const openLabel =
    page.roadDetails.find((detail) => detail.roadDetailId === openDetailId)?.roadDetailLabel ?? ''
  const modeWord = readOnly ? 'View' : 'Edit'
  const panelHeading = panelMode === 'new' ? 'New Road' : `${modeWord} Road — ${openLabel}`

  return (
    <>
      <div className="schedule-10__actions">
        <Button kind="primary" disabled={controlsDisabled} renderIcon={Add} onClick={onOpenNew}>
          Add Road
        </Button>
        {/* Back is never disabled, including outside Draft — a read-only reporter must be able to
            leave the level. */}
        <Button kind="secondary" renderIcon={ArrowLeft} onClick={onBack}>
          Back
        </Button>
      </div>

      <TableContainer title={`${page.pageLabel} -> Roads`} className="schedule-10__section">
        <Table aria-label="Road details">
          <TableHead>
            <TableRow>
              <TableHeader>Roads</TableHeader>
              <TableHeader>Action</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {page.roadDetails.length === 0 ? (
              <TableRow>
                <TableCell colSpan={2}>{EMPTY_LIST}</TableCell>
              </TableRow>
            ) : (
              page.roadDetails.map((detail) => (
                <TableRow
                  key={detail.roadDetailId}
                  className={
                    openDetailId === detail.roadDetailId && panelMode !== 'closed'
                      ? 'schedule-10__row--editing'
                      : undefined
                  }
                >
                  <TableCell>{detail.roadDetailLabel}</TableCell>
                  <TableCell>
                    <div className="schedule-10__row-actions">
                      {/* Unlike the page list, legacy leaves a road row actionable while that road
                          is open in the panel below — re-opening it simply reloads the form. */}
                      <Button
                        kind="ghost"
                        size="sm"
                        disabled={saving}
                        renderIcon={editable ? Edit : View}
                        onClick={() => onOpenDetail(detail)}
                      >
                        {editable ? 'Edit' : 'View'}
                      </Button>
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        disabled={controlsDisabled}
                        renderIcon={TrashCan}
                        onClick={() => onRequestDelete(detail)}
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

      {panelMode !== 'closed' && (
        <section className="schedule-10__section">
          <div className="schedule-10__panel">
            <h3 className="schedule-10__heading">{panelHeading}</h3>
            <RoadDetailFields
              idPrefix={panelMode === 'new' ? 'road-new' : `road-${String(openDetailId ?? 0)}`}
              form={form}
              errors={errors}
              codeLists={codeLists}
              disabled={controlsDisabled}
              readOnly={readOnly}
              onChange={onChange}
              onMask={onMask}
            />
            <div className="schedule-10__panel-actions">
              {/* AC11 and deviation 7: rendered and disabled outside Draft, never removed. */}
              <Button
                kind="primary"
                disabled={controlsDisabled || readOnly}
                renderIcon={Save}
                onClick={onSave}
              >
                Save
              </Button>
              <Button kind="secondary" renderIcon={Close} onClick={onCloseForm}>
                Close
              </Button>
            </div>
          </div>
        </section>
      )}
    </>
  )
}

export default RoadDetailPage
