package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule2.dto.CostBlock;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 2 (Purchased/Private Log Costs and Sales) read document to the print section
 * datasource. Unlike the per-record fan-out sections (Schedule 5's one-row-per-camp), Schedule 2 is
 * a SINGLE aggregate document, so this emits exactly ONE row holding the seven fixed cost blocks —
 * {@code purchasedLogCost}, {@code purchasedWoodOverhead}, {@code subtotal}, {@code lessLogSales},
 * {@code netPurchased}, {@code totalCompanyLogging}, {@code totalAverage} — each as a
 * volume/cost/$-per-m³ triple. Every derived figure (the computed subtotal / net / totals and every
 * {@code perUnit}, plus the carried Schedule-1/3 terms) is already computed by {@code
 * Schedule2Service}; this only formats: whole-dollar cost, fractional volume + $-per-m³ at two
 * decimals, and {@code "-"} for null.
 *
 * <p>Skip-empty (BR-09) keys off the DATA, not a detail-row count: when every cost block is null
 * (an unsaved Schedule 2 with no carried Schedule-3 figures) this returns {@code null} so the
 * orchestrator omits the section.
 */
final class Schedule2SectionMapper {

  private Schedule2SectionMapper() {}

  static SectionData map(Schedule2Response response) {
    if (response == null || isEmpty(response)) {
      return null;
    }
    Map<String, Object> row = new LinkedHashMap<>();
    putBlock(row, "purchasedLogCost", response.purchasedLogCost());
    putBlock(row, "purchasedWoodOverhead", response.purchasedWoodOverhead());
    putBlock(row, "subtotal", response.subtotal());
    putBlock(row, "lessLogSales", response.lessLogSales());
    putBlock(row, "netPurchased", response.netPurchased());
    putBlock(row, "totalCompanyLogging", response.totalCompanyLogging());
    putBlock(row, "totalAverage", response.totalAverage());
    row.put("comments", SectionFormat.text(response.comments()));
    return new SectionData(List.of(row), Map.of());
  }

  /** Emit a block's three cells ({@code <name>Vol/Cos/Cal}), pre-formatted with "-" for null. */
  private static void putBlock(Map<String, Object> row, String name, CostBlock block) {
    row.put(name + "Vol", SectionFormat.decimal(block == null ? null : block.volume()));
    row.put(name + "Cos", SectionFormat.money(block == null ? null : block.cost()));
    row.put(name + "Cal", SectionFormat.decimal(block == null ? null : block.perUnit()));
  }

  /** True when no cost block carries any figure — the skip-empty signal (BR-09). */
  private static boolean isEmpty(Schedule2Response r) {
    return allNull(r.purchasedLogCost())
        && allNull(r.purchasedWoodOverhead())
        && allNull(r.subtotal())
        && allNull(r.lessLogSales())
        && allNull(r.netPurchased())
        && allNull(r.totalCompanyLogging())
        && allNull(r.totalAverage());
  }

  private static boolean allNull(CostBlock block) {
    return block == null
        || (block.volume() == null && block.cost() == null && block.perUnit() == null);
  }
}
