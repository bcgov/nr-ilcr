package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.dataextract.csv.CsvWriter;
import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code MultipleSchedule1_2Extract}: the special layout for exactly Schedules 1 and 2 over
 * several mills AND several years. Sorted year then mill (legacy inverted its sort keys for this
 * one case), it emits a five-column mini-table per year for Schedule 1 — blank row, title, header,
 * one row per mill — then the same series for Schedule 2. No Other Costs, no Schedule 3-derived
 * cells, no NO-DATA handling: an unsaved pair renders its figure as the null marker.
 *
 * <p>A year with no rows produces no header at all, as legacy's change-of-year check behaved.
 */
public final class CombinedSchedule1And2Section {

  private static final int STANDING_TREE_TO_TRUCK = 12;

  private static final String[] SCHEDULE_1_HEADER = {
    "MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "STAND_TTT_M3"
  };

  private static final String[] SCHEDULE_2_HEADER = {
    "MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "PO&P_COSTS_$"
  };

  /** One (mill, year)'s Schedule 1 document, year first for the year-grouped walk. */
  public record Schedule1Entry(int year, RowContext ctx, Schedule1Response response) {}

  /** One (mill, year)'s Schedule 2 document, year first for the year-grouped walk. */
  public record Schedule2Entry(int year, RowContext ctx, Schedule2Response response) {}

  /**
   * The whole body: every Schedule 1 mini-table then every Schedule 2 mini-table. Entries must
   * already be sorted year ascending then mill id ascending.
   */
  public List<String[]> rows(List<Schedule1Entry> schedule1, List<Schedule2Entry> schedule2) {
    List<String[]> rows = new ArrayList<>();
    Integer currentYear = null;
    for (Schedule1Entry entry : schedule1) {
      if (currentYear == null || currentYear != entry.year()) {
        currentYear = entry.year();
        rows.add(new String[] {CsvWriter.BLANK_CELL});
        rows.add(new String[] {"**** Schedule 1 ****"});
        rows.add(SCHEDULE_1_HEADER.clone());
      }
      rows.add(entry.ctx().with(whole(standingTreeToTruckVolume(entry.response()))));
    }
    currentYear = null;
    for (Schedule2Entry entry : schedule2) {
      if (currentYear == null || currentYear != entry.year()) {
        currentYear = entry.year();
        rows.add(new String[] {CsvWriter.BLANK_CELL});
        rows.add(new String[] {"**** Schedule 2 ****"});
        rows.add(SCHEDULE_2_HEADER.clone());
      }
      Schedule2Response s2 = entry.response();
      rows.add(
          entry
              .ctx()
              .with(
                  whole(
                      s2 == null || s2.purchasedLogCost() == null
                          ? null
                          : s2.purchasedLogCost().cost())));
    }
    return rows;
  }

  private static Number standingTreeToTruckVolume(Schedule1Response s1) {
    if (s1 == null || s1.lineItems() == null) {
      return null;
    }
    for (LineItem li : s1.lineItems()) {
      if (li != null && li.costItemCode() != null && li.costItemCode() == STANDING_TREE_TO_TRUCK) {
        return li.volume();
      }
    }
    return null;
  }
}
