import type { FC } from 'react'
import { useEffect, useRef, useState } from 'react'
import { Button, Column, Grid, Select, SelectItem } from '@carbon/react'
import apiService from '@/service/api-service'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import NotificationColumn from '@/components/core/NotificationColumn'
import MillReportStatusTable from '@/components/millReportStatus/MillReportStatusTable'
import { extractDetail } from '@/utils/error'
import { assertCompletePdf, extractBlobDetail, triggerDownload } from '@/utils/download'
import { millLabel, millReportFilename } from '@/components/millReportStatus/millIdentity'
import type ReportingYear from '@/interfaces/ReportingYear'
import type MillReportStatusRow from '@/interfaces/MillReportStatusRow'
import './index.scss'

const api = () => apiService.getAxiosInstance()
const STATUS_PATH = '/v1/reports/mill-status'
const YEARS_PATH = '/v1/reporting-years'
/**
 * The per-mill drill-down PDF (Story 19.3). The mill goes in the PATH and the year in the query,
 * matching the backend's `/mill-information/{millId}?year=` — it is the Mill Information report
 * scoped to one mill, not a separate report.
 */
const DRILL_DOWN_PATH = '/v1/reports/mill-information'

const YEARS_FAILED = 'Unable to load the reporting years.'
const ROWS_FAILED = 'Unable to load the mill status report.'
const PDF_FAILED = 'Unable to generate the mill information report.'
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
 *
 * <p>The Mill cell is a drill-down (Story 19.3): activating it downloads that one mill's Mill
 * Information PDF for the APPLIED year. Legacy did the same from a `p:commandLink` +
 * `p:fileDownload` (`:82-86`) — passing the already-loaded row object to the renderer, where this
 * sends the row's mill id and lets the backend re-read it, which is what makes the drill-down
 * resolve to the clicked mill regardless of the current sort.
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
  /**
   * A drill-down PDF failure, held SEPARATELY from `error` and not routed through it.
   *
   * `emptyMessage()` below suppresses its text whenever `error` is set (a failed Apply establishes
   * no claim about the year's data). A PDF failure establishes nothing about the rows either way —
   * they are on screen and correct — so reusing `error` would blank a legitimate table message over
   * an unrelated download failure. Two facts, two states.
   */
  const [pdfError, setPdfError] = useState<string | null>(null)
  /**
   * The mills whose PDFs are in flight — a SET, because the design leaves the other rows clickable
   * and two downloads can overlap. A scalar could not express that: starting a second download
   * re-enabled the first row while its request was still open, and the second settling then cleared
   * the state altogether (review round 1, P1).
   */
  const [downloadingMillIds, setDownloadingMillIds] = useState<ReadonlySet<number>>(() => new Set())
  /**
   * The polite live-region text announcing a drill-down's start and outcome (WCAG 2.1 AA SC 4.1.3).
   *
   * A separate channel from `pdfError`, which paints the visible banner. A freshly mounted
   * notification is not reliably announced — a live region has to already exist for a change inside
   * it to be read — so this string feeds a region that is mounted for the life of the page.
   */
  const [pdfStatus, setPdfStatus] = useState('')
  /**
   * Bumped whenever the rows on screen stop belonging to the year a drill-down was issued for.
   *
   * This is the staleness guard, and it is a REF rather than state because the settle handlers must
   * read the value as of NOW, not the value captured when their closure was created. Each request
   * records the generation it was issued under and drops its own result if the table has moved on
   * (review round 1, P2/P3). Without it a late failure re-armed the very banner `changeYear`
   * clears, unremovably — `NotificationColumn` has no dismiss control — and a late SUCCESS silently
   * saved a PDF for the previous year, indistinguishable from the right one because the parity
   * filename carries no year.
   */
  const tableGenerationRef = useRef(0)

  /** Abandon every in-flight drill-down: its result no longer describes what is on screen. */
  const abandonDownloads = () => {
    tableGenerationRef.current += 1
    setDownloadingMillIds(new Set())
    setPdfError(null)
    setPdfStatus('')
  }

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
    // A drill-down failure belongs to the year whose rows were on screen; the same reasoning as the
    // Apply banner above. Once the year moves on it describes a mill the user is no longer looking
    // at, and NotificationColumn has no dismiss control. Clearing is not enough on its own — an
    // in-flight request would re-arm it on arrival — so this also invalidates those requests.
    abandonDownloads()
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
    // A held drill-down failure is about rows that are being replaced, so it goes too. Cleared
    // BEFORE the no-op guard for the same reason `error` is.
    setPdfError(null)
    setPdfStatus('')
    // Both guards mirror the Apply button's own disabled state and legacy's bean, so a keyboard
    // activation or a stale click cannot get past what the UI says is unavailable.
    if (selectedYear === '' || selectedYear === appliedYear) {
      return
    }
    // Only past the no-op guard. Re-applying the SAME year does not refetch and does not replace
    // the rows, so an in-flight drill-down is still answering for what is on screen and must NOT be
    // abandoned. A real load does replace them.
    abandonDownloads()
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
   * Download ONE mill's Mill Information PDF — the drill-down legacy streamed straight from this
   * table's Mill cell (UC-MRPT-002 S02 / UC-MRPT-004 S02, `millReportStatus.xhtml:82-86`).
   *
   * The YEAR sent is `appliedYear`, never `selectedYear`. The rows on screen belong to the applied
   * year, and the two differ the moment the user picks a new year without pressing Apply — sending
   * the selection would produce a PDF for a year whose table is not the one they clicked in.
   *
   * The filename is built HERE from the clicked row rather than read off the Content-Disposition
   * header, matching how 19.1's report page names its file. Both sides derive
   * `mill_<millNumber>_print.pdf` from the same mill (`PrintSchedulesMB.java:332`) with the same
   * fall back to the mill id for a mill whose number is absent, so the two derivations agree
   * without the frontend having to parse a header axios does not expose by default anyway.
   *
   * Nothing about the table changes on failure: rows, applied year and the year selection are all
   * left alone, so the action retries by clicking the same mill again (MRPT-002 S07 / MRPT-004 S05).
   */
  const downloadMillReport = (row: MillReportStatusRow) => {
    if (appliedYear === null) {
      // Unreachable from the UI — the drill-down only exists on rendered rows, and rows only exist
      // after a successful Apply. Guarded anyway rather than sending `year=null` to the backend and
      // getting the required-field 400 back for a state the user never created.
      return
    }
    // The ENFORCING half of the control's aria-disabled (review round 1, P8/P3). The button stays a
    // real focusable button so activating it never blurs a keyboard user to <body>, which means the
    // refusal has to live here: a second activation of a row already fetching is dropped, and so is
    // any activation while an Apply is replacing the table underneath.
    if (busy || downloadingMillIds.has(row.millId)) {
      return
    }
    // Captured AT CLICK TIME, all three. The year is what the rows on screen belong to — never
    // `selectedYear`, which may already have moved on — and the generation is what lets the settle
    // handlers below tell "still describes the table" from "arrived too late".
    const year = appliedYear
    const generation = tableGenerationRef.current
    const label = millLabel(row)
    setPdfError(null)
    setPdfStatus(`Generating the mill information report for ${label}.`)
    // Functional updates throughout, never `new Set([...downloadingMillIds])`: two clicks in the
    // same tick would otherwise each build from the same stale snapshot and the first mill would
    // vanish from the set.
    setDownloadingMillIds((current) => new Set(current).add(row.millId))
    api()
      .get(`${DRILL_DOWN_PATH}/${row.millId}`, {
        params: { year },
        responseType: 'blob',
      })
      .then(async (response) => {
        // Belt and braces. A generation failure no longer reaches here at all: the backend exports
        // the PDF to a temp file BEFORE it commits a status, so it answers 500 problem+json and
        // this .then never runs. A transfer cut short after the commit is caught by the
        // Content-Length the backend now sends — axios rejects it as a network error. Both land in
        // the .catch below with no file saved (MRPT-002 S07 / MRPT-004 S05). What this still buys
        // is the case neither of those covers: a length-complete 200 that is not a PDF, e.g. a
        // gateway or SSO interstitial answering for the API.
        await assertCompletePdf(response.data as Blob)
        if (generation !== tableGenerationRef.current) {
          // The table moved on while this was in flight. Dropping the blob is the WHOLE point:
          // saving it would hand the administrator a PDF for the previous year under a filename
          // that carries no year, so nothing on disk would distinguish it from the right one.
          return
        }
        triggerDownload(response.data as Blob, millReportFilename(row))
        setPdfStatus(`The mill information report for ${label} has downloaded.`)
      })
      .catch(async (cause: unknown) => {
        // extractBlobDetail, not extractDetail: under responseType 'blob' the backend's
        // application/problem+json error body arrives as a Blob too, so the `detail` has to be read
        // out of it rather than accessed as an object. Awaited BEFORE the staleness check, because
        // the check must be re-read after the await — the table can move on while the Blob is being
        // read.
        const detail = (await extractBlobDetail(cause)) || PDF_FAILED
        if (generation !== tableGenerationRef.current) {
          return
        }
        setPdfError(detail)
        setPdfStatus(`The mill information report for ${label} failed. ${detail}`)
      })
      .finally(() => {
        // Two conditions, and the generation one is NOT redundant. `abandonDownloads` already
        // emptied the set for a stale request, so the delete looked like a harmless no-op — but
        // only while the mill stays out of the set. Re-apply the year and click the SAME mill
        // again and it is back in, belonging to the new generation; the old request then settles
        // and deletes an entry it does not own, re-enabling a row whose request is still in
        // flight. Skipping the delete when stale is safe precisely because the abandon already
        // cleared it, and the live request owns its own removal.
        if (generation !== tableGenerationRef.current) {
          return
        }
        // Removes only ITS OWN mill, so a settling request cannot re-enable a row that is still
        // fetching or clear one that never started.
        setDownloadingMillIds((current) => {
          const next = new Set(current)
          next.delete(row.millId)
          return next
        })
      })
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
        {/* Its own banner, titled for the action that failed rather than the page: the rows below it
            are intact and correct, so "Error" alone would read as a claim about the table. */}
        {pdfError && <NotificationColumn kind="error" title="Report failed" subtitle={pdfError} />}
        <Column sm={4} md={8} lg={16} className="mill-report-status">
          {/* The year control and Apply share a row, bottom-aligned so the button sits on the
              field's baseline rather than its label's — the Users admin screen's idiom. Control
              heights (48px) come from the global Carbon overrides and are deliberately not restated
              here. */}
          {/*
            Mounted for the LIFE of the page, always, even while empty — that is what makes it work.
            A live region has to already be in the accessibility tree for a change inside it to be
            announced, so rendering it conditionally alongside the banner would announce nothing.
            Visually hidden because the banner and the button's own busy state already carry this
            for sighted users; this is the SC 4.1.3 channel for everyone else.
          */}
          <div
            className="cds--visually-hidden"
            role="status"
            aria-live="polite"
            // Named because Carbon's own InlineNotification also renders role="status", so the two
            // are otherwise indistinguishable to assistive tech and to tests. The banner is still
            // not a substitute for this region: it mounts fresh when the failure arrives, and a
            // live region has to already be in the accessibility tree for a change inside it to be
            // announced reliably.
            aria-label="Report generation status"
          >
            {pdfStatus}
          </div>

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
            onMillSelect={downloadMillReport}
            downloadingMillIds={downloadingMillIds}
            // An Apply in flight is about to replace these rows, so drill-downs go unavailable for
            // its duration — otherwise a click here downloads a PDF for the outgoing year, or 404s
            // naming a mill the incoming table may not even contain (review round 1, P3).
            drillDownDisabled={busy}
          />
        </Column>
      </Grid>
    </div>
  )
}

export default MillReportStatus
