package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Derives the Schedule 3 values that Schedule 1 reads (BR-03/BR-04), from the stored Schedule 3
 * fixed-line detail rows — never from persisted "subtotal" rows.
 *
 * <p><b>Why this exists.</b> Legacy Schedule 1 never read a stored Subtotal Actual Costs row: it
 * loaded Schedule 3 and computed {@code subtotalActualCosts.crownCost} live from the fixed lines
 * every render ({@code Schedule1MB.getForestManagementAdminCal} → {@code Schedule3DO
 * .getSubtotalActualCosts().getCrownCost()}). The rewrite briefly read cost-item ids 115/135 as if
 * they were persisted, but {@link Schedule3Service} (like legacy) only ever COMPUTES those subtotals
 * in memory — they are never written to {@code ILCR_COST_REPORT_DETAIL} — so the read always returned
 * null and Forest Management Administration cost surfaced as 0. This component re-derives the value
 * the same way {@link Schedule3Service#getSchedule3} does, so the two stay consistent (the shared
 * fixture on summary 1003 cross-checks them: {@code Schedule3DocumentIT.subtotalActualCosts.crown ==
 * Schedule1CrownPrefillIT.forestMgmtAdminCost}).
 *
 * <p>Kept as a separate component (not a method on {@link Schedule3Service}) because
 * {@code Schedule3Service} already depends on {@code Schedule1Service} for the BR-09 crown push;
 * having {@code Schedule1Service} depend back on {@code Schedule3Service} would be a cycle. This
 * component depends only on {@link Schedule3Repository}.
 */
@Component
public class Schedule3CostDerivation {

  // Fixed admin-cost lines — MUST match Schedule3Service.LINES. Each line's Harvest item id is its
  // identity; popCode is its PO&P item id (null for the Harvest-only lines). Annual Rents (29) and
  // Silviculture Admin (37) are Harvest-only (legacy forces their PO&P to 0). Scaling (33) has no
  // stored PO&P — it is derived from the timber-volume ratio (getScalingExpense).
  private static final int CODE_SCALING = 33;
  private static final int CODE_SILV_ADMIN = 37;       // BR-04 Less Silviculture Admin source
  private static final int CODE_POP_TIMBER = 118;      // PO&P Timber volume (Scaling ratio numerator)
  private static final int CODE_CROWN_TIMBER = 119;    // BR-03 Crown Timber pre-fill source (volume)
  private static final int CODE_OTHER_ACCEPTABLE = 124; // Other Acceptable Costs sub-page rows

  // Legacy Constant.SCH3_OTHERACCEPT — an item-124 COMMENTS encodes "SCH3_2_<TYPE>_<GRP>"; chars
  // 7..10 == "TOT" marks a group's Harvest-total row (all others carry the group's PO&P).
  private static final String OTHERACCEPT_TYPE_TOTAL = "TOT";

  private record LineSpec(int code, Integer popCode, boolean harvestOnly) {
  }

  private static final List<LineSpec> LINES = List.of(
      new LineSpec(27, 125, false),   // Licenses, Fees, Insurance
      new LineSpec(28, 126, false),   // Taxes, Leases, Rentals
      new LineSpec(29, null, true),   // Annual Rents (Harvest-only; PO&P forced 0)
      new LineSpec(30, 128, false),   // Wages/Salaries incl. Benefits
      new LineSpec(31, 129, false),   // Vehicle Expense
      new LineSpec(32, 130, false),   // Office Expense
      new LineSpec(CODE_SCALING, null, false),       // Scaling Expense (PO&P derived)
      new LineSpec(34, 132, false),   // Cruising & Layout Expense
      new LineSpec(35, 133, false),   // Residue & Waste Expense
      new LineSpec(36, 134, false),   // Depreciation Expense
      new LineSpec(CODE_SILV_ADMIN, null, true));    // Silviculture Admin Costs (Harvest-only; PO&P 0)

  private final Schedule3Repository repository;

  public Schedule3CostDerivation(Schedule3Repository repository) {
    this.repository = repository;
  }

  /**
   * The Schedule-3-sourced values Schedule 1 needs for a mill/year, or an all-null result when no
   * Schedule 3 (category "3") summary exists (legacy shows those cells blank, not 0).
   *
   * @param crownTimberVolume item-119 VOLUME — BR-03 pre-fill source + grand-total $/m³ divisor
   * @param silvicultureAdminCrownCost item-37 crown (PO&amp;P forced 0 ⇒ = its cost) — BR-04 Less
   *     Silviculture Admin cost; null when no item-37 row
   * @param forestMgmtAdminCrownCost the crown of Schedule 3's Subtotal Actual Costs (Σ fixed-line
   *     Harvest − Σ PO&amp;P + Other Acceptable) — BR-04 Forest Management Administration cost; null
   *     only when no Schedule 3 summary exists (0 when the summary exists but is empty)
   */
  public record Schedule1Sources(
      BigDecimal crownTimberVolume,
      Integer silvicultureAdminCrownCost,
      Long forestMgmtAdminCrownCost) {
  }

  private static final Schedule1Sources EMPTY = new Schedule1Sources(null, null, null);

  /** Resolve the BR-03/BR-04 Schedule-3 sources for Schedule 1 (empty when no Schedule 3 summary). */
  public Schedule1Sources schedule1Sources(long millId, int year) {
    return repository.findSummary(millId, year)
        .map(summary -> derive(repository.findDetails(summary.summaryId())))
        .orElse(EMPTY);
  }

  /** Compute the sources from a summary's detail rows (mirrors {@link Schedule3Service#getSchedule3}). */
  private Schedule1Sources derive(List<DetailRow> details) {
    Map<Integer, DetailRow> byCode = new HashMap<>();
    List<DetailRow> acceptable = new ArrayList<>();
    for (DetailRow row : details) {
      Integer code = row.costItemCode();
      if (code == null) {
        continue;
      }
      if (code == CODE_OTHER_ACCEPTABLE) {
        acceptable.add(row);
      } else {
        byCode.putIfAbsent(code, row); // first row per code wins (ordered by detail id)
      }
    }

    BigDecimal popTimberVolume = volumeOf(byCode.get(CODE_POP_TIMBER));
    BigDecimal crownTimberVolume = volumeOf(byCode.get(CODE_CROWN_TIMBER));
    BigDecimal overheadVolume = add(popTimberVolume, crownTimberVolume);

    // Subtotal Actual Costs = Σ(11 fixed lines) + Other Acceptable groups (seed at 0 ⇒ always
    // present). crown = harvest − pop (legacy CostType.getCrownCost on the seeded-non-null totals).
    long harvest = 0L;
    long pop = 0L;
    for (LineSpec spec : LINES) {
      Integer lineHarvest = costOf(byCode.get(spec.code()));
      Integer linePop = resolvePop(spec, lineHarvest, byCode, popTimberVolume, overheadVolume);
      harvest += nullToZero(lineHarvest);
      pop += nullToZero(linePop);
    }
    for (DetailRow row : acceptable) {
      if (isTotalComments(row.comments())) {
        harvest += nullToZero(row.cost());
      } else {
        pop += nullToZero(row.cost());
      }
    }

    return new Schedule1Sources(
        crownTimberVolume,
        costOf(byCode.get(CODE_SILV_ADMIN)),
        harvest - pop);
  }

  /**
   * Resolve a line's PO&P amount: Harvest-only lines (29/37) force 0 when a harvest is present;
   * Scaling (33) derives it from the timber-volume ratio; all others read their PO&P item's cost.
   */
  private Integer resolvePop(LineSpec spec, Integer harvest, Map<Integer, DetailRow> byCode,
      BigDecimal popTimberVolume, BigDecimal overheadVolume) {
    if (spec.harvestOnly()) {
      return harvest == null ? null : 0;
    }
    if (spec.code() == CODE_SCALING) {
      return scalingPop(harvest, popTimberVolume, overheadVolume);
    }
    return costOf(byCode.get(spec.popCode()));
  }

  /**
   * Legacy {@code Schedule3DO.getScalingExpense}: PO&P = round-to-whole-dollars(
   * (popTimberVolume / totalOverheadVolume) × scalingHarvest). Null when the harvest or a volume is
   * absent or the overhead volume is zero.
   */
  private static Integer scalingPop(Integer scalingHarvest, BigDecimal popTimberVolume,
      BigDecimal overheadVolume) {
    if (scalingHarvest == null || popTimberVolume == null
        || overheadVolume == null || overheadVolume.signum() == 0) {
      return null;
    }
    BigDecimal ratio = popTimberVolume.divide(overheadVolume, 15, RoundingMode.HALF_UP);
    return ratio.multiply(BigDecimal.valueOf(scalingHarvest)).setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  /** True when an item-124 {@code COMMENTS} encodes a Harvest-total row (chars 7..10 == "TOT"). */
  private static boolean isTotalComments(String comments) {
    return comments != null && comments.length() >= 10
        && OTHERACCEPT_TYPE_TOTAL.equals(comments.substring(7, 10));
  }

  /** Legacy {@code bigDecimalCostAddition}: null-tolerant (a null operand is treated as absent). */
  private static BigDecimal add(BigDecimal a, BigDecimal b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.add(b);
  }

  private static Integer costOf(DetailRow row) {
    return row == null ? null : row.cost();
  }

  private static BigDecimal volumeOf(DetailRow row) {
    return row == null ? null : row.volume();
  }

  private static long nullToZero(Integer value) {
    return value == null ? 0L : value;
  }
}
