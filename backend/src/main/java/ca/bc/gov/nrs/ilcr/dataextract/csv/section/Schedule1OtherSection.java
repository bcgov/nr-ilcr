package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.divide;
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
 * <p>Every itemized row prints its OWN stored volume and its own cost/volume, as legacy did ({@code
 * ocl.getVolume()} and {@code ocl.getCostVolume()}, {@code Schedule1OtherExtract.java:77, :79}),
 * and the total row sums those per-row volumes ({@code :81, :88}). The document's shared
 * Other-Costs volume, and the owner's per-row {@code perUnit} derived from it, are the sub-page's
 * figures and are not read here: a (mill, year) with no shared null-description row has no shared
 * volume, yet its rows still carry theirs. So deviation (R) does not apply to this section's CPU.
 *
 * <p>One legacy quirk kept verbatim: the total row formats the summed VOLUME to two decimals but
 * the summed COST to none — the inverse of the rows above it.
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
    // Legacy iterated getOtherCostListFiltered() (Schedule1DO.java:180-188), which drops any row
    // whose description is null or empty, and decided the per-record marker on the FILTERED list —
    // so a pair holding only blank-description rows got the marker, and no blank row was ever
    // summed into Total:. The owner serves every stored row; the filter belongs here.
    List<OtherCostRow> items =
        document == null || document.rows() == null
            ? List.of()
            : document.rows().stream()
                .filter(r -> r.description() != null && !r.description().isEmpty())
                .toList();
    if (items.isEmpty()) {
      return List.<String[]>of(ctx.noDataRow());
    }
    List<String[]> rows = new ArrayList<>();
    List<BigDecimal> volumes = new ArrayList<>();
    List<Number> costs = new ArrayList<>();
    for (OtherCostRow item : items) {
      rows.add(
          ctx.with(
              text(item.description()),
              whole(item.volume()),
              whole(item.cost()),
              // Legacy getCostVolume() = bigDecimalDivision(cost, volume) (CostVolumeType.java:86).
              twoDecimals(divide(item.cost(), item.volume()))));
      volumes.add(item.volume());
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
