package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the Schedule 3 fixed cost-line specification and the derivation rules
 * shared by {@link Schedule3Service} (the read/write document) and {@link Schedule3CostDerivation}
 * (the Schedule 1 BR-03/BR-04 pre-fill). Both compute the Subtotal Actual Costs the same way, so
 * the line set ({@link #LINES}) and the PO&amp;P/other-acceptable rules ({@link #resolvePop},
 * {@link #scalingPop}, {@link #isTotalComments}) MUST agree between them; keeping the one copy here
 * makes that structural rather than a comment nobody can enforce.
 */
final class Schedule3Constants {

  private Schedule3Constants() {}

  // Fixed admin-cost line codes referenced by name across the schedule. Annual Rents (29) and
  // Silviculture Admin (37) are Harvest-only: legacy forces their PO&P to ZERO on load (crown =
  // harvest). Scaling (33) has no stored PO&P — its PO&P is derived from the timber-volume ratio.
  static final int CODE_ANNUAL_RENTS = 29;
  static final int CODE_SCALING = 33;
  static final int CODE_SILV_ADMIN = 37;

  // Legacy Constant.SCH3_OTHERACCEPT — an item-124 COMMENTS encodes "SCH3_2_<TYPE>_<GRP>"; chars
  // 7..10 == "TOT" marks a group's Harvest-total row (all others carry the group's PO&P).
  static final String OTHERACCEPT_TYPE_TOTAL = "TOT";

  /**
   * A fixed admin-cost line (legacy {@code Constant.REPORT_COST_ITEMS}). Each line's Harvest item
   * id is its identity; {@code popCode} is its PO&amp;P item id (null for the Harvest-only lines).
   */
  record LineSpec(int code, Integer popCode, boolean harvestOnly) {}

  static final List<LineSpec> LINES =
      List.of(
          new LineSpec(27, 125, false), // Licenses, Fees, Insurance
          new LineSpec(28, 126, false), // Taxes, Leases, Rentals
          new LineSpec(CODE_ANNUAL_RENTS, null, true), // Annual Rents (Harvest-only; PO&P forced 0)
          new LineSpec(30, 128, false), // Wages/Salaries incl. Benefits
          new LineSpec(31, 129, false), // Vehicle Expense
          new LineSpec(32, 130, false), // Office Expense
          new LineSpec(CODE_SCALING, null, false), // Scaling Expense (PO&P derived)
          new LineSpec(34, 132, false), // Cruising & Layout Expense
          new LineSpec(35, 133, false), // Residue & Waste Expense
          new LineSpec(36, 134, false), // Depreciation Expense
          new LineSpec(
              CODE_SILV_ADMIN, null, true)); // Silviculture Admin Costs (Harvest-only; PO&P 0)

  /**
   * Resolve a line's PO&amp;P amount: Harvest-only lines (29/37) force 0 when a harvest is present
   * (legacy {@code Schedule3DAO} sets {@code popCost = ZERO}); Scaling (33) derives it from the
   * timber-volume ratio ({@code getScalingExpense}); all others read their PO&amp;P item's cost.
   */
  static Integer resolvePop(
      LineSpec spec,
      Integer harvest,
      Map<Integer, DetailRow> byCode,
      BigDecimal popTimberVolume,
      BigDecimal overheadVolume) {
    if (spec.harvestOnly()) {
      return harvest == null ? null : 0;
    }
    if (spec.code() == CODE_SCALING) {
      return scalingPop(harvest, popTimberVolume, overheadVolume);
    }
    return costOf(byCode.get(spec.popCode()));
  }

  /**
   * Legacy {@code Schedule3DO.getScalingExpense}: PO&amp;P = round-to-whole-dollars(
   * (popTimberVolume / totalOverheadVolume) × scalingHarvest). Null when the harvest or a volume is
   * absent or the overhead volume is zero.
   */
  static Integer scalingPop(
      Integer scalingHarvest, BigDecimal popTimberVolume, BigDecimal overheadVolume) {
    if (scalingHarvest == null
        || popTimberVolume == null
        || overheadVolume == null
        || overheadVolume.signum() == 0) {
      return null;
    }
    BigDecimal ratio = popTimberVolume.divide(overheadVolume, 15, RoundingMode.HALF_UP);
    return ratio
        .multiply(BigDecimal.valueOf(scalingHarvest))
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  /** True when an item-124 {@code COMMENTS} encodes a Harvest-total row (chars 7..10 == "TOT"). */
  static boolean isTotalComments(String comments) {
    return comments != null
        && comments.length() >= 10
        && OTHERACCEPT_TYPE_TOTAL.equals(comments.substring(7, 10));
  }

  static Integer costOf(DetailRow row) {
    return row == null ? null : row.cost();
  }
}
