package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NO_DATA_FOUND;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCosts;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRow;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule3UnacceptExtract}: a fixed {@code Annual Rents (Forest Act, S111)} row,
 * then the Included Unacceptable Costs rows of one (mill, year), then a {@code Total:} row that
 * includes the rents. {@code CROWN_$} is a copy of {@code TOTAL_$} on every row, as legacy wrote
 * it; the empty marker carries two trailing empty cells, as legacy's did.
 */
public final class Schedule3UnacceptSection implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "DESCRIPTION", "TOTAL_$", "CROWN_$"
  };

  @Override
  public String title() {
    return "**** Schedule 3 - Unacceptable Costs ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The rows for one (mill, year), or the seven-cell marker when it holds no item-38 rows. */
  public List<String[]> rows(RowContext ctx, UnacceptableDocument document) {
    List<UnacceptableRow> items =
        document == null || document.rows() == null ? List.of() : document.rows();
    if (items.isEmpty()) {
      return List.<String[]>of(ctx.with(NO_DATA_FOUND, "", ""));
    }
    List<String[]> rows = new ArrayList<>();
    List<Number> totals = new ArrayList<>();
    String rents = whole(document.annualRentsTotal());
    rows.add(ctx.with("Annual Rents (Forest Act, S111)", rents, rents));
    totals.add(document.annualRentsTotal());
    for (UnacceptableRow item : items) {
      rows.add(ctx.with(text(item.description()), whole(item.total()), whole(item.total())));
      totals.add(item.total());
    }
    String total = whole(sumCosts(totals));
    rows.add(new String[] {"", "", "", "", "Total:", total, total});
    return rows;
  }
}
