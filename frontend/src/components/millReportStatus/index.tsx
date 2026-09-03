import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { Button, Column, Grid, Select, SelectItem } from '@carbon/react'
import apiService from '@/service/api-service'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import NotificationColumn from '@/components/core/NotificationColumn'
import MillReportStatusTable from '@/components/millReportStatus/MillReportStatusTable'
import { extractDetail } from '@/utils/error'
import type ReportingYear from '@/interfaces/ReportingYear'
import type MillReportStatusRow from '@/interfaces/MillReportStatusRow'
import './index.scss'

const api = () => apiService.getAxiosInstance()
const STATUS_PATH = '/v1/reports/mill-status'
const YEARS_PATH = '/v1/reporting-years'

const YEARS_FAILED = 'Unable to load the reporting years.'
const ROWS_FAILED = 'Unable to load the mill status report.'
/** Before any year has been applied. Not a claim about the data — nothing has been asked for yet. */
const PROMPT = 'Select a Report Year and choose Apply to list the mills.'
/**
 * No reporting period has ever been opened, so the year list is empty and Apply can never enable.
 * Verbatim the sibling report page's wording (`millInformationReport/index.tsx`) — telling the
 * administrator to "select a Report Year and choose Apply" would instruct an action the page has not
 * offered and cannot offer.
 */
const NO_OPEN_YEAR = 'No reporting period has been opened.'

/** The legend's element id, referenced as the table's `aria-describedby`. */
const LEGEND_ID = 'mill-report-status-legend'

/** The legend legacy prints above the table (millReportStatus.xhtml:57-73), in its order. */
const LEGEND = ['O - Open', 'D - Draft', 'S - Submitted', 'V - Verified'] as const

/**
 * Mill Status Report (UC-MRPT-004, legacy `millReportStatus.xhtml`). Administrators pick a report
 * year, press Apply, and see where every mill stands on each of the two independent schedule tracks.
 *
 * <p>Unlike the schedule pages this one takes NO mill/year working context: it neither reads nor
 * needs the Home selection, which is why there is no context guard here. Legacy agrees — its
 * `areMillYearSelected()` render guard is commented out (`:10-24`). The mill set is every mill, and
 * no mill parameter is ever sent.
 *
 * <p>Three interaction rules come straight from the legacy managed bean, and one fixes it:
 * <ul>
 *   <li>The year starts at a "Select Reporting Year" no-selection item with Apply disabled and no
 *       rows (`:36-37`, `applyDisabled = true`).</li>
 *   <li>Re-applying the SAME year does not refetch (`applyYearChanged`, `:62-68` — "avoid loading
 *       the list in case of report year does not change").</li>
 *   <li>Clearing the year back to the no-selection item disables Apply and empties the table
 *       (`listChanged`, `:70-79`).</li>
 *   <li><b>Fixed:</b> legacy's clear-the-year path called
 *       `millReportStatusReportDO.setMillReportStatusReports(...)` on a field that is null until the
 *       first Apply (`:77`), so clearing the year before ever pressing Apply threw an NPE. Emptying
 *       an array here cannot fail, so the guarded empty state replaces it.</li>
 * </ul>
 */
const MillReportStatus: FC = () => {
  const [years, setYears] = useState<ReportingYear[]>([])
  const [selectedYear, setSelectedYear] = useState('')
  /** The year the rows on screen belong to; null until the first successful Apply. */
  const [appliedYear, setAppliedYear] = useState<string | null>(null)
  const [rows, setRows] = useState<MillReportStatusRow[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api()
      .get<ReportingYear[]>(YEARS_PATH)
      .then((response) => {
        setLoadError(null)
        setYears(response.data)
        // Deliberately NOT pre-selected, unlike the Mill Information Report: this page starts at the
        // no-selection item with Apply disabled, which is what legacy did.
      })
      .catch((cause: unknown) => setLoadError(extractDetail(cause) || YEARS_FAILED))
  }, [])

  const changeYear = (value: string) => {
    setSelectedYear(value)
    // ANY year change drops a standing failure. The banner belongs to the year that failed; leaving
    // it up while the user moves on describes a selection that is no longer on screen, and
    // NotificationColumn has no dismiss control, so nothing else would clear it.
    setError(null)
    if (value === '') {
      // Emptying the table on clear, and never throwing while doing it — see the S09 note above.
      setRows([])
      setAppliedYear(null)
    }
  }

  const apply = () => {
    // Cleared BEFORE the no-op guard below, not after. Otherwise: apply 2021 (ok), select 2020,
    // Apply fails, re-select 2021, Apply — the early return fires and the 2020 failure banner stays
    // on screen above correct 2021 rows, unremovable.
    setError(null)
    // Both guards mirror the Apply button's own disabled state and legacy's bean, so a keyboard
    // activation or a stale click cannot get past what the UI says is unavailable.
    if (selectedYear === '' || selectedYear === appliedYear) {
      return
    }
    setBusy(true)
    api()
      .get<MillReportStatusRow[]>(STATUS_PATH, { params: { year: selectedYear } })
      .then((response) => {
        setRows(response.data)
        setAppliedYear(selectedYear)
      })
      .catch((cause: unknown) => {
        // The rows and the selected year are both KEPT: the table stays readable and pressing Apply
        // again retries the year that was held when it failed. Legacy showed an empty table with no
        // message at all here (MillReportStatusDAO returns null, the bean has no try/catch); the
        // banner is a recorded improvement.
        setError(extractDetail(cause) || ROWS_FAILED)
      })
      .finally(() => setBusy(false))
  }

  /**
   * What the table shows when it holds no mills — four different situations, four answers.
   *
   * Silent while an error banner is up: the message is a CLAIM about the selected year's data, and
   * after a failed Apply no such claim has been established. Rendering "No mill has a report status
   * for 2020" beside a "2020 could not be loaded" banner asserts the opposite of what happened.
   *
   * The no-mills wording names the year actually APPLIED, not the current selection: the two differ
   * the moment the user picks a new year without pressing Apply, and the rows on screen belong to the
   * applied one.
   */
  const emptyMessage = (): string | null => {
    if (error !== null || rows.length > 0) {
      return null
    }
    if (appliedYear !== null) {
      return `No mill has a report status for ${appliedYear}.`
    }
    // A failed year load already has its own banner; a second message would just repeat it.
    if (loadError !== null) {
      return null
    }
    return years.length === 0 ? NO_OPEN_YEAR : PROMPT
  }

  return (
    <div className="app-page schedule-page">
      <ScheduleTombstone title="Mill Status Report" />
      <Grid fullWidth className="app-page__body">
        {loadError && <NotificationColumn kind="error" title="Error" subtitle={loadError} />}
        {error && <NotificationColumn kind="error" title="Error" subtitle={error} />}
        <Column sm={4} md={8} lg={16} className="mill-report-status">
          {/* The year control and Apply share a row, bottom-aligned so the button sits on the
              field's baseline rather than its label's — the Users admin screen's idiom. Control
              heights (48px) come from the global Carbon overrides and are deliberately not restated
              here. */}
          <div className="mill-report-status__controls">
            <Select
              id="mill-report-status-year"
              className="mill-report-status__year"
              labelText="Report Year:"
              value={selectedYear}
              disabled={busy}
              onChange={(event) => changeYear(event.target.value)}
            >
              {/* The permanent no-selection item — legacy's f:selectItem with
                  noSelectionOption="true" (xhtml:41). It stays selectable, because clearing back to
                  it is a supported action that empties the table. */}
              <SelectItem value="" text="Select Reporting Year" />
              {years.map((year) => (
                <SelectItem
                  key={year.reportYear}
                  value={String(year.reportYear)}
                  text={String(year.reportYear)}
                />
              ))}
            </Select>
            <Button
              className="mill-report-status__apply"
              onClick={apply}
              disabled={busy || selectedYear === ''}
            >
              Apply
            </Button>
          </div>

          {/* The legend is the reason the milestone cells keep their raw status prefix: it is what
              tells the reader that the "O" in "O: 2021-01-05" means Open. Marked up as a list so
              the four entries are announced as a set of four rather than one run-on line. */}
          <ul
            className="mill-report-status__legend"
            id={LEGEND_ID}
            aria-label="Report status legend"
          >
            {LEGEND.map((entry) => (
              <li className="mill-report-status__legend-item" key={entry}>
                {entry}
              </li>
            ))}
          </ul>

          <MillReportStatusTable
            // Remounting on a new applied year resets the sort, which is what legacy's ajax="false"
            // full-page Apply did. Without it a sort snapshotted over one year's mill ids would
            // survive into the next year's rows and leave the header claiming a sort that is not
            // applied.
            key={appliedYear ?? 'none'}
            rows={rows}
            emptyMessage={emptyMessage()}
            legendId={LEGEND_ID}
          />
        </Column>
      </Grid>
    </div>
  )
}

export default MillReportStatus
