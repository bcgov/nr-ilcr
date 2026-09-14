package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NO_DATA_FOUND;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.defuse;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.divideNoRounding;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.nullOnly;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCosts;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCostsTwoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageDocument;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageRow;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule5CampExtract} / {@code Schedule5AccessExtract}: the itemized rows of one
 * camp's sub-page, then a {@code Total:} row. Both whole-schedule markers are six cells wide.
 *
 * <p>One legacy asymmetry kept: the Camp builder wrote the item description RAW (a null is an empty
 * field) while the Access builder sanitised it and dashed a null.
 */
public final class Schedule5SubPageSection implements SectionBuilder {

  /** Which of the two legacy builders this instance is. */
  public enum Kind {
    CAMP("**** Schedule 5 - Camp Expenses ****"),
    ACCESS("**** Schedule 5 - Access Expenses ****");

    private final String title;

    Kind(String title) {
      this.title = title;
    }
  }

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "CAMP",
    "DESCRIPTION",
    "VOL_M3",
    "COST_$",
    "CPU_$/M3"
  };

  private final Kind kind;

  public Schedule5SubPageSection(Kind kind) {
    this.kind = kind;
  }

  @Override
  public String title() {
    return kind.title;
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  @Override
  public String[] noDataRow(String millNumbers, String yearRange) {
    return new String[] {millNumbers, yearRange, NULL_VALUE, NULL_VALUE, NULL_VALUE, NO_DATA_FOUND};
  }

  /** The rows for one camp: one per item plus the total, or the per-camp marker. */
  public List<String[]> rows(RowContext ctx, Camp camp, SubPageDocument document) {
    List<SubPageRow> items =
        document == null || document.rows() == null ? List.of() : document.rows();
    String campName = nullOnly(camp.campName());
    if (items.isEmpty()) {
      return List.<String[]>of(ctx.with(campName, NO_DATA_FOUND));
    }
    List<String[]> rows = new ArrayList<>();
    List<Number> costs = new ArrayList<>();
    List<Number> volumes = new ArrayList<>();
    for (SubPageRow item : items) {
      rows.add(
          ctx.with(
              campName,
              kind == Kind.CAMP ? defuse(item.description()) : text(item.description()),
              whole(item.volume()),
              whole(item.cost()),
              twoDecimals(item.costPerVolume())));
      costs.add(item.cost());
      volumes.add(item.volume());
    }
    rows.add(
        new String[] {
          "",
          "",
          "",
          "",
          "",
          "Total:",
          whole(sumCosts(volumes)),
          whole(sumCosts(costs)),
          twoDecimals(divideNoRounding(sumCostsTwoDecimals(costs), sumCostsTwoDecimals(volumes)))
        });
    return rows;
  }
}
