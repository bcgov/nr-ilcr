package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.add;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.area;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.divide;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.moneyTwoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.raw;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumAreas;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCosts;

import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule11Extract}: one 13-column row per location, a {@code Total:} row whenever
 * the (year, mill) changes, and a FINAL total row one column further right than the intermediate
 * ones — six leading blanks against five, a legacy misalignment kept verbatim ({@code
 * Schedule11Extract.java:70} against {@code :119}).
 *
 * <p>{@code STATUS} is the silviculture track's description (the caller's {@link RowContext} is
 * built with it); comments are written raw, as legacy wrote them; areas use {@code #,###,##0} and
 * money {@code #,###,##0.00}.
 */
public final class Schedule11Section implements SectionBuilder {

  /** One (mill, year)'s document with its leading cells, in the order the section walks them. */
  public record Entry(RowContext ctx, Schedule11Response response) {}

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "LOCATION",
    "BIOGEO_SUB_VAR",
    "ES",
    "NAR_HA",
    "ACTUAL_$",
    "PLANNED_$",
    "ACT_PLAN_$",
    "TOT_CPU_$/HA",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 11 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /**
   * The rows for the whole section: every location of every entry, with a sub-total at each (year,
   * mill) boundary and the shifted final total. An entry with no locations contributes nothing; the
   * caller decides whether the section as a whole is empty.
   */
  public List<String[]> rows(List<Entry> entries) {
    List<String[]> rows = new ArrayList<>();
    List<BigDecimal> netAreas = new ArrayList<>();
    List<Number> actuals = new ArrayList<>();
    List<Number> planned = new ArrayList<>();
    boolean anyRow = false;
    for (Entry entry : entries) {
      List<SilvicultureLocation> locations =
          entry.response().locations() == null ? List.of() : entry.response().locations();
      if (locations.isEmpty()) {
        continue;
      }
      if (anyRow) {
        rows.add(totalRow(5, netAreas, actuals, planned));
        netAreas.clear();
        actuals.clear();
        planned.clear();
      }
      for (SilvicultureLocation location : locations) {
        rows.add(row(entry.ctx(), location));
        netAreas.add(location.netArea());
        actuals.add(location.actualCost());
        planned.add(location.plannedCost());
      }
      anyRow = true;
    }
    if (anyRow) {
      rows.add(totalRow(6, netAreas, actuals, planned));
    }
    return rows;
  }

  private static String[] row(RowContext ctx, SilvicultureLocation location) {
    return ctx.with(
        raw(location.location()),
        location.becLabel() == null || location.becLabel().isEmpty()
            ? NULL_VALUE
            : location.becLabel(),
        location.enhancedIndicator() ? "Y" : "N",
        area(location.netArea()),
        moneyTwoDecimals(location.actualCost()),
        moneyTwoDecimals(location.plannedCost()),
        moneyTwoDecimals(location.totalCost()),
        moneyTwoDecimals(location.costPerNetArea()),
        location.comments() == null || location.comments().isEmpty()
            ? NULL_VALUE
            : location.comments());
  }

  private static String[] totalRow(
      int leadingBlanks, List<BigDecimal> netAreas, List<Number> actuals, List<Number> planned) {
    String[] row = new String[leadingBlanks + 6];
    for (int i = 0; i < leadingBlanks; i++) {
      row[i] = "";
    }
    row[leadingBlanks] = "Total:";
    row[leadingBlanks + 1] = area(sumAreas(netAreas));
    row[leadingBlanks + 2] = moneyTwoDecimals(sumCosts(actuals));
    row[leadingBlanks + 3] = moneyTwoDecimals(sumCosts(planned));
    final BigDecimal actualPlusPlanned = add(sumCosts(actuals), sumCosts(planned));
    row[leadingBlanks + 4] = moneyTwoDecimals(actualPlusPlanned);
    row[leadingBlanks + 5] = moneyTwoDecimals(divide(actualPlusPlanned, sumAreas(netAreas)));
    return row;
  }
}
