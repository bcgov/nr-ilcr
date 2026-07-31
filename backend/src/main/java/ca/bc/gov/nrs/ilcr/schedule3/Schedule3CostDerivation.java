package ca.bc.gov.nrs.ilcr.schedule3;

import static ca.bc.gov.nrs.ilcr.schedule3.Schedule3Constants.LINES;
import static ca.bc.gov.nrs.ilcr.schedule3.Schedule3Constants.isTotalComments;
import static ca.bc.gov.nrs.ilcr.schedule3.Schedule3Constants.resolvePop;

import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Constants.LineSpec;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import java.math.BigDecimal;
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

  // The fixed admin-cost LINES, the LineSpec record, and the PO&P/other-acceptable derivation rules
  // (resolvePop, isTotalComments) live in Schedule3Constants — the single source of truth shared with
  // Schedule3Service, so the two derive the Subtotal Actual Costs identically and can never drift.
  private static final int CODE_SILV_ADMIN = Schedule3Constants.CODE_SILV_ADMIN; // BR-04 Less Silv Admin
  private static final int CODE_POP_TIMBER = 118;      // PO&P Timber volume (Scaling ratio numerator)
  private static final int CODE_CROWN_TIMBER = 119;    // BR-03 Crown Timber pre-fill source (volume)
  private static final int CODE_OTHER_ACCEPTABLE = 124; // Other Acceptable Costs sub-page rows

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
