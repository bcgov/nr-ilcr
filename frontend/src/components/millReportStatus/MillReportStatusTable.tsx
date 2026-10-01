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
import { useRowSort } from '@/hooks/useRowSort'
import { millLabel, millNumberOrNull } from '@/components/millReportStatus/millIdentity'
import type MillReportStatusRow from '@/interfaces/MillReportStatusRow'

/** The four scalar columns legacy marks `sortBy`; the two track columns are deliberately not here. */
type SortKey = 'millNumber' | 'millName' | 'region' | 'active'

type StatusColumn = {
  readonly label: string
  readonly sortKey: SortKey | null
}

/**
 * Column order and header text verbatim from `millReportStatus.xhtml`, INCLUDING the two track
 * headers' quirks — "Schedules 1-10" with a hyphen and "Schedules 11" in the plural, which is what
 * the legacy page says. Only the four scalar columns carry `sortBy` there (`:78,:81,:87,:90`); the
 * two stacked track columns do not (`:93,:102`), because a cell holding four values has no single
 * value to order by.
 */
const COLUMNS: readonly StatusColumn[] = [
  { label: 'Mill Number', sortKey: 'millNumber' }, // xhtml:78
  { label: 'Mill', sortKey: 'millName' }, // xhtml:81
  { label: 'Region', sortKey: 'region' }, // xhtml:87
  { label: 'Active', sortKey: 'active' }, // xhtml:90
  { label: 'Report Info for Current Year Schedules 1-10', sortKey: null }, // xhtml:93
  { label: 'Report Info for Current Year Schedules 11', sortKey: null }, // xhtml:102
]

/**
 * Mill Number sorts NUMERICALLY, not as text — legacy's `sortBy` bound an int
 * (`MillReportStatusType.ilcrMillNumber`), so 514 precedes 7300.
 *
 * `MILL.MILL_NUMBER` is an Oracle `NUMBER(15)`; it only arrives as a string because that is how the
 * DTO carries it. There is deliberately no non-numeric fallback: a text-order fallback could never
 * fire, and if it somehow did it would silently switch the WHOLE column to `localeCompare` ordering,
 * which is worse than the NaN it was guarding against.
 */
const millNumberValue = (row: MillReportStatusRow) => {
  // Absent OR null OR blank: the wire omits nulls (Jackson non_null), so all three are the same
  // "no value" case and all three must sort last. Delegated to the shared predicate in
  // `millIdentity` so this column, the Mill cell's label and the download filename agree on what
  // "no mill number" means — they did not before review round 1, where a whitespace-only number
  // also sorted as 0 (`Number('   ')` is 0, not NaN) instead of last.
  const value = millNumberOrNull(row)
  return value === null ? null : Number(value)
}

/** A missing Region renders as a dash, exactly as legacy did (`MillReportStatusDAO.java:120`). */
const REGION_FALLBACK = '-'

/**
 * The milestone each stacked line holds, in order. Rendered as a visually-hidden label per line so a
 * screen reader hears "Submitted 2021-05-20" instead of a bare date, and hears the four milestone
 * NAMES even on a row that has reached none of them — where the cell is otherwise silent. Sighted
 * users read the same meaning off the column position, which is why these are hidden rather than
 * shown: the visible rendering is the ratified legacy-faithful stack and must not change.
 */
const MILESTONE_LABELS = ['Opened', 'Draft', 'Submitted', 'Verified'] as const

/**
 * One track's four milestones, stacked — legacy's four `h:outputText`s separated by `<br/>`.
 *
 * Values are rendered VERBATIM, prefix and all: `"O: 2021-01-05"` and the prefix-only `"D: "` both
 * reach the screen unchanged, because the O/D/S/V legend above the table is what decodes the letter.
 * A null renders as an EMPTY line — `?? ''` rather than letting React print the value — so a mill
 * that has not reached a milestone shows a blank line, never the text `null`. The line keeps its
 * height via CSS so the four lines stay aligned across rows.
 */
const stack = (values: readonly (string | null | undefined)[]) =>
  values.map((value, index) => (
    // The index IS the identity here: these are four fixed positions (Opened/Draft/Submitted/
    // Verified), not a reorderable list.
    // eslint-disable-next-line @eslint-react/no-array-index-key
    <div className="mill-report-status__line" key={index}>
      <span className="mill-report-status__line-label">{MILESTONE_LABELS[index]}</span>
      <span className="mill-report-status__line-value">{value ?? ''}</span>
    </div>
  ))

type MillReportStatusTableProps = {
  rows: readonly MillReportStatusRow[]
  /** Shown in place of rows; null once a year has been applied and answered with mills. */
  emptyMessage: string | null
  /**
   * The O/D/S/V legend's element id, referenced as the table's `aria-describedby`. Without it the
   * legend is a visually adjacent list with no programmatic relationship to the data it decodes, so
   * a screen-reader user reaching the table has no way to learn what the leading letters mean.
   */
  legendId: string
  /**
   * Called with the WHOLE clicked row when the Mill cell's drill-down is activated (Story 19.3).
   *
   * The row, not its index: sorting reorders the array, so an index would resolve to whichever mill
   * happens to sit at that position now (legacy's own technical sidecar warned about exactly this —
   * it bound the row var on a non-ajax POST, `millReportStatus.xhtml:82-84`). It is also the row and
   * not just `millId` because the caller needs `millNumber` for the parity filename.
   *
   * OPTIONAL, and that is deliberate: with no callback the cell stays the plain text 19.2 shipped.
   * The table is then still usable as a read-only view without inventing a drill-down that goes
   * nowhere.
   */
  onMillSelect?: (row: MillReportStatusRow) => void
  /**
   * The mills whose PDFs are currently being fetched — a SET, not a single id.
   *
   * Only those rows advertise themselves as busy, never the whole table: the other mills are
   * unaffected, and blanking every control while one downloads would read as a page-wide failure.
   * Sorting stays available throughout, because it changes nothing an in-flight request depends on.
   *
   * A set rather than a scalar BECAUSE the design deliberately leaves the other rows clickable, so
   * two downloads can genuinely overlap. Review round 1: with a scalar, starting a second download
   * re-enabled the first row while its request was still open, and the second request settling then
   * cleared the state altogether — so a duplicate request could be fired and the same file saved
   * twice.
   */
  downloadingMillIds?: ReadonlySet<number>
  /**
   * Whether drill-downs are unavailable because an Apply is in flight.
   *
   * Distinct from a row being in flight: this is about the TABLE being replaced. The rows on screen
   * belong to the applied year, so while a new year loads a click would download a PDF for a table
   * that is about to be replaced — or raise a 404 naming a mill from the outgoing year.
   */
  drillDownDisabled?: boolean
}

/**
 * The six-column Mill Status Report table.
 *
 * <p>Hand-built from `TableContainer`/`Table`/`TableHead`/`TableRow`/`TableHeader`/`TableBody`/
 * `TableCell` following the Schedule 11 locations table — the house idiom. Carbon `DataTable`,
 * grouped/multi-level headers and `rowSpan` were all considered and rejected: none exists anywhere
 * in this codebase, and the "two column groups" legacy shows are simply two ordinary columns whose
 * cells stack four lines each.
 *
 * <p>Its own component so the PAGE can remount it with a `key` when a different year is applied,
 * which resets the sort. That is legacy's behaviour: Apply was `ajax="false"`, a full page submit,
 * so the re-rendered table came back unsorted.
 */
const MillReportStatusTable: FC<MillReportStatusTableProps> = ({
  rows,
  emptyMessage,
  legendId,
  onMillSelect,
  downloadingMillIds,
  drillDownDisabled,
}) => {
  // Client-side sort over rows already fetched — the whole year is in memory, as legacy's
  // PrimeFaces header sort was. NONE restores the server's mill-id order.
  //
  // Every extractor sorts the value the user can SEE, which is what legacy sorted: its DAO built the
  // display strings first and `sortBy` ordered those (`MillReportStatusDAO.java:106,120`). So Active
  // sorts "Yes"/"No" (ascending puts No first) and Region sorts the "-" it renders for a missing
  // zone (ascending puts those first). Sorting the raw boolean, or the raw null, would silently
  // disagree with both legacy and the screen.
  const sort = useRowSort<MillReportStatusRow>(
    rows,
    {
      millNumber: millNumberValue,
      // millLabel, NOT row.millName — this block's own invariant is that every extractor sorts the
      // value the user can SEE, and since the drill-down the Mill cell may render a mill NUMBER or
      // an id where the name is absent. Sorting the raw name left a row visibly labelled "8010"
      // ordering as though blank, parked among the nulls, while `aria-sort` claimed an ordering the
      // column did not have (review round 1). millLabel returns a string, so it orders exactly as
      // rendered — which is also what legacy's display-string sort did.
      millName: millLabel,
      region: (row) => row.region ?? REGION_FALLBACK,
      active: (row) => (row.active ? 'Yes' : 'No'),
    },
    (row) => row.millId,
  )

  return (
    <TableContainer>
      <Table aria-label="Mill Status Report" aria-describedby={legendId}>
        <TableHead>
          <TableRow>
            {COLUMNS.map(({ label, sortKey }) => {
              const isSortHeader = sortKey !== null && sortKey === sort.activeKey
              return (
                <TableHeader
                  key={label}
                  isSortable={sortKey !== null}
                  isSortHeader={isSortHeader}
                  // Carbon reads aria-sort off this, so an inactive column must report NONE rather
                  // than the active column's direction.
                  sortDirection={isSortHeader ? sort.directionFor(sortKey) : 'NONE'}
                  onClick={sortKey === null ? undefined : () => sort.toggleSort(sortKey)}
                >
                  {label}
                </TableHeader>
              )
            })}
          </TableRow>
        </TableHead>
        <TableBody>
          {emptyMessage !== null ? (
            <TableRow>
              <TableCell colSpan={COLUMNS.length}>{emptyMessage}</TableCell>
            </TableRow>
          ) : (
            // Keyed by millId, never the array index: sorting reorders the array, and an index key
            // would let React reuse the wrong row's DOM.
            sort.sortedRows.map((row) => (
              <TableRow key={row.millId}>
                {/*
                  Through `??`, like every other nullable field on this row — the interface's own
                  invariant. EMPTY rather than the "-" Region uses: legacy dashed only Region
                  (`MillReportStatusDAO.java:120`) and rendered mill number/name straight through
                  `h:outputText`, where a null prints as nothing. A mill with no number or name is
                  referential corruption, not an absent optional attribute.
                */}
                <TableCell>{row.millNumber ?? ''}</TableCell>
                {/*
                  The drill-down (Story 19.3). Legacy wrapped this cell in a p:commandLink whose
                  p:fileDownload streamed the mill's own PDF (xhtml:82-86); here it is the house
                  in-table affordance — a ghost Button, as Schedule 1's Other Costs and Schedule 8's
                  TtT Samples use — so it is keyboard-reachable and announced as a button rather
                  than being a div with a click handler.

                  A BUTTON, not an anchor, even though legacy's was a link: activating it fetches a
                  blob and hands it to the browser's download machinery. There is no URL to follow,
                  nothing to open in a new tab and nothing to copy, so a link would misdescribe it
                  to both sighted users and assistive tech.

                  Without a callback it stays the plain text 19.2 shipped, so the table remains a
                  usable read-only view rather than growing a control that does nothing.
                */}
                <TableCell>
                  {onMillSelect ? (
                    <Button
                      kind="ghost"
                      size="sm"
                      className="mill-report-status__mill-link"
                      /*
                        aria-disabled, NOT the real `disabled` attribute — and the page's handler
                        enforces it with an early return.

                        Setting a real `disabled` on the element that was just activated makes the
                        browser blur it to <body>: a keyboard user who pressed Enter loses their
                        place in the table entirely and has to tab back from the top, and a screen
                        reader is left with nothing focused to announce. aria-disabled advertises
                        the state to assistive tech while keeping the control focusable, which is
                        the WCAG 2.1 AA-conformant half of this pair; the handler refusing the
                        second activation is the other half. Review round 1 (SC 4.1.3).
                      */
                      aria-disabled={
                        downloadingMillIds?.has(row.millId) || drillDownDisabled ? true : undefined
                      }
                      // This row is fetching. Paired with the page's polite live region, which is
                      // what actually ANNOUNCES start and outcome — aria-busy is a state, not a
                      // status message.
                      aria-busy={downloadingMillIds?.has(row.millId) ? true : undefined}
                      // Names the ACTION and the mill, because the visible label is only the mill
                      // name — "MILL INFO FULL" alone tells a screen-reader user nothing about what
                      // activating it does, and there are as many such buttons as there are rows.
                      aria-label={`Generate the mill information report for ${millLabel(row)}`}
                      onClick={() => onMillSelect(row)}
                    >
                      {millLabel(row)}
                    </Button>
                  ) : (
                    (row.millName ?? '')
                  )}
                </TableCell>
                <TableCell>{row.region ?? REGION_FALLBACK}</TableCell>
                <TableCell>{row.active ? 'Yes' : 'No'}</TableCell>
                <TableCell>
                  {stack([row.openDate, row.draftDate, row.submitDate, row.verifyDate])}
                </TableCell>
                <TableCell>
                  {/*
                    The SAME openDate as the Schedules 1-10 group. The reporting view has no
                    SILVI_STATUS_OPEN_DATE and the Schedule 11 track has no independent opened date,
                    so legacy renders the 1-10 value here too (xhtml:103).
                  */}
                  {stack([
                    row.openDate,
                    row.silviDraftDate,
                    row.silviSubmitDate,
                    row.silviVerifyDate,
                  ])}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

export default MillReportStatusTable
