package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule2.dto.CostBlock;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import java.util.Optional;

/**
 * Legacy {@code Schedule2Extract}: one 26-column row per (mill, year) whose Schedule 2 holds a
 * figure AND whose Schedule 3 exists — every derived block on the row (the PO&amp;P overhead, the
 * subtotal, the Schedule 1 costs, the total average) carries a Schedule 3 figure, so legacy
 * required both.
 *
 * <p>The blocks are the Schedule 2 owner's derived {@code CostBlock}s, formatted only (AD-14).
 * Pairing is by (mill, year): legacy paired its three lists by INDEX and misaligned silently when
 * one was shorter; the rebuild asks each owner for the same pair, so a pair with a Schedule 2
 * figure but no Schedule 3 summary gets the per-record marker instead of another pair's figures.
 */
public final class Schedule2Section implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "PO&P_COSTS_M3",
    "PO&P_COSTS_$",
    "PO&P_COSTS_$/M3",
    "PO&P_OH_M3",
    "PO&P_OH_$",
    "PO&P_$/M3",
    "SUBTOT_M3",
    "SUBTOT_$",
    "SUBTOT_$/M3",
    "LESS_SALES_M3",
    "LESS_SALES_$",
    "LESS_SALES_$/M3",
    "NET_PO&P_M3",
    "NET_PO&P_$",
    "NET_PO&P_$/M3",
    "SCH1_COSTS_M3",
    "SCH1_COSTS_$",
    "SCH1_COSTS_$/M3",
    "TOT_AVG_COSTS_M3",
    "TOT_AVG_COSTS_$",
    "TOT_AVG_COSTS_$/M3",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 2 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /**
   * The row for one (mill, year), the per-record marker when the pair has a Schedule 2 figure but
   * no Schedule 3 summary, or nothing when the Schedule 2 itself is empty (legacy emitted no row).
   *
   * @param ctx the leading cells
   * @param s2 the Schedule 2 document (never 404s; may be unsaved)
   * @param hasSchedule3 whether the pair has a Schedule 3 summary
   */
  public Optional<String[]> row(RowContext ctx, Schedule2Response s2, boolean hasSchedule3) {
    if (isEmpty(s2)) {
      return Optional.empty();
    }
    if (!hasSchedule3) {
      return Optional.of(ctx.noDataRow());
    }
    return Optional.of(
        ctx.with(
            volume(s2.purchasedLogCost()),
            cost(s2.purchasedLogCost()),
            perUnit(s2.purchasedLogCost()),
            volume(s2.purchasedWoodOverhead()),
            cost(s2.purchasedWoodOverhead()),
            perUnit(s2.purchasedWoodOverhead()),
            volume(s2.subtotal()),
            cost(s2.subtotal()),
            perUnit(s2.subtotal()),
            volume(s2.lessLogSales()),
            cost(s2.lessLogSales()),
            perUnit(s2.lessLogSales()),
            volume(s2.netPurchased()),
            cost(s2.netPurchased()),
            perUnit(s2.netPurchased()),
            volume(s2.totalCompanyLogging()),
            cost(s2.totalCompanyLogging()),
            perUnit(s2.totalCompanyLogging()),
            volume(s2.totalAverage()),
            cost(s2.totalAverage()),
            perUnit(s2.totalAverage()),
            text(s2.comments())));
  }

  /** Legacy {@code checkEmpty}: no purchased cost, no log-sales figure, no comment. */
  public static boolean isEmpty(Schedule2Response s2) {
    CostBlock purchased = s2.purchasedLogCost();
    CostBlock sales = s2.lessLogSales();
    boolean purchasedEmpty = purchased == null || purchased.cost() == null;
    boolean salesEmpty =
        sales == null
            || (sales.volume() == null && sales.cost() == null && sales.perUnit() == null);
    boolean commentsEmpty = s2.comments() == null || s2.comments().trim().isEmpty();
    return purchasedEmpty && salesEmpty && commentsEmpty;
  }

  private static String volume(CostBlock block) {
    return whole(block == null ? null : block.volume());
  }

  private static String cost(CostBlock block) {
    return whole(block == null ? null : block.cost());
  }

  private static String perUnit(CostBlock block) {
    return twoDecimals(block == null ? null : block.perUnit());
  }
}
