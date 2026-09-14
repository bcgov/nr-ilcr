package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.divideNoRounding;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCosts;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCostsTwoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule1OtherExtract}: the itemized Other Costs rows of one (mill, year), then a
 * {@code Total:} row.
 *
 * <p>Two legacy quirks kept verbatim. Every itemized row carries the SHARED Other-Costs volume (the
 * owner stamps no per-row volume, and legacy's rows all showed the one summary figure), so the
 * total row's volume is that figure times the row count. And the total row formats the summed
 * VOLUME to two decimals but the summed COST to none — the inverse of the rows above it.
 */
public final class Schedule1OtherSection implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "DESCRIPTION",
    "VOLUME",
    "COST",
    "CPU_$/M3"
  };

  @Override
  public String title() {
    return "**** Schedule 1 - Other Costs ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The rows for one (mill, year): one per itemized cost plus the total, or the marker. */
  public List<String[]> rows(RowContext ctx, OtherCostsDocument document) {
    List<OtherCostRow> items =
        document == null || document.rows() == null ? List.of() : document.rows();
    if (items.isEmpty()) {
      return List.<String[]>of(ctx.noDataRow());
    }
    BigDecimal sharedVolume = document.volume();
    List<String[]> rows = new ArrayList<>();
    List<BigDecimal> volumes = new ArrayList<>();
    List<Number> costs = new ArrayList<>();
    for (OtherCostRow item : items) {
      rows.add(
          ctx.with(
              text(item.description()),
              whole(sharedVolume),
              whole(item.cost()),
              twoDecimals(item.perUnit())));
      volumes.add(sharedVolume);
      costs.add(item.cost());
    }
    rows.add(
        new String[] {
          "",
          "",
          "",
          "",
          "Total:",
          twoDecimals(sumCosts(volumes)),
          whole(sumCosts(costs)),
          twoDecimals(divideNoRounding(sumCostsTwoDecimals(costs), sumCostsTwoDecimals(volumes)))
        });
    return rows;
  }
}
