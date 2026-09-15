package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCosts;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRow;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule3AcceptExtract}: the Other Acceptable Costs rows of one (mill, year), then
 * a {@code Total:} row. The legacy title says "Other Costs", not "Acceptable" — kept.
 */
public final class Schedule3AcceptSection implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "DESCRIPTION",
    "TOTAL_$",
    "PO&P_$",
    "CROWN_$"
  };

  @Override
  public String title() {
    return "**** Schedule 3 - Other Costs ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The rows for one (mill, year): one per group plus the total, or the marker. */
  public List<String[]> rows(RowContext ctx, OtherAcceptableDocument document) {
    List<OtherAcceptableRow> items =
        document == null || document.rows() == null ? List.of() : document.rows();
    if (items.isEmpty()) {
      return List.<String[]>of(ctx.noDataRow());
    }
    List<String[]> rows = new ArrayList<>();
    List<Number> totals = new ArrayList<>();
    List<Number> pops = new ArrayList<>();
    List<Number> crowns = new ArrayList<>();
    for (OtherAcceptableRow item : items) {
      rows.add(
          ctx.with(
              text(item.description()),
              whole(item.total()),
              whole(item.pop()),
              whole(item.crown())));
      totals.add(item.total());
      pops.add(item.pop());
      crowns.add(item.crown());
    }
    rows.add(
        new String[] {
          "",
          "",
          "",
          "",
          "Total:",
          whole(sumCosts(totals)),
          whole(sumCosts(pops)),
          whole(sumCosts(crowns))
        });
    return rows;
  }
}
