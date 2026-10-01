package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Derives the Schedule 1 cost figures that are shared between Schedule 1's own document and the
 * cross-schedule readers — today the legacy {@code Schedule1DO.getSubtotalLoggingCost} ("Subtotal
 * Company Logging Cost", no silviculture) that Schedule 2 carries into its {@code
 * totalCompanyLogging}.
 *
 * <p><b>Why this exists.</b> Schedule 1's served item-144 figure is {@code Σ logging(12–18) +
 * Forest Mgmt Admin(pulled from Sch 3) + Subtotal Other Costs}, but the legacy figure Schedule 2
 * carries EXCLUDES Forest Management Administration. Schedule 2 used to recover it by inverse
 * arithmetic — {@code subtotalCompanyLoggingCost − forestMgmtAdminCost} off the fully assembled
 * Schedule 1 document — which coupled Schedule 2's totals to what Schedule 1 happens to fold into
 * that subtotal: any future change on the Schedule 1 side would have silently shifted Schedule 2's
 * figures. This component makes the no-FMA subtotal a NAMED figure computed in one place
 * (bcgov/nr-ilcr#252): {@link Schedule1Service} adds Forest Mgmt Admin back on for its own display
 * figure, and Schedule 2 reads the named figure directly, so the two cannot drift.
 *
 * <p>Reading it here also keeps cross-schedule callers off {@code findSchedule1}: assembling the
 * whole document ran a full {@link ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation} pass
 * purely to subtract Forest Mgmt Admin back out, and Schedule 2 does that from inside its
 * {@code @Transactional} save and its {@code checkStatus}. This port is two queries and no Schedule
 * 3 work.
 *
 * <p>Kept as a separate component (not a method on {@link Schedule1Service}) for the same reason as
 * {@link ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation}: it depends only on {@link
 * Schedule1Repository}, so a cross-schedule caller can consume the figure without depending on the
 * assembling service. The sum helpers are {@code static} so {@code Schedule1Service} shares the
 * computation without taking the component as a constructor dependency — the point is one
 * implementation of each sum, not one bean.
 */
@Component
public class Schedule1CostDerivation {

  private static final String SCHEDULE_1_CATEGORY = "1";

  // The seven entered logging lines (legacy Constant.REPORT_COST_ITEMS, BR-02). Deliberately NOT
  // 143 (Forest Mgmt Admin — pulled from Schedule 3) and NOT 144 (the stored subtotal row, which
  // legacy never reads: it computes the subtotal every render).
  private static final int[] LOGGING_LINE_CODES = {12, 13, 14, 15, 16, 17, 18};

  /** Repeatable Other-Costs rows all share cost item code 19. */
  private static final int CODE_OTHER = 19;

  private final Schedule1Repository repository;

  public Schedule1CostDerivation(Schedule1Repository repository) {
    this.repository = repository;
  }

  /**
   * The legacy {@code Schedule1DO.getSubtotalLoggingCost} for a mill/year — the harvest cost blocks
   * (items 12–18) plus Subtotal Other Costs, EXCLUDING Forest Management Administration.
   *
   * <p>Empty when no category-{@code "1"} summary exists, which is the ABSENCE signal
   * cross-schedule callers need (defect #296): an absent Schedule 1 must leave a carried figure
   * blank, never $0. An existing but empty summary yields {@code 0} — legacy's Subtotal Company
   * Logging is never blank, its Subtotal Other Costs term seeding at zero.
   *
   * @param millId the mill id (context already validated by the caller)
   * @param year the reporting year
   * @return the no-FMA subtotal, or empty when the mill/year has no Schedule 1
   */
  public Optional<Long> subtotalLoggingNoFmaCost(long millId, int year) {
    return repository
        .findSummary(millId, year, SCHEDULE_1_CATEGORY)
        .map(SummaryRow::summaryId)
        .map(summaryId -> subtotalLoggingNoFma(repository.findDetails(summaryId)));
  }

  /**
   * Partition the stored detail rows into the code-keyed map (single row per code) and the
   * repeatable Other-Costs rows. Rows without a cost item code are ignored.
   *
   * <p>Last row per code wins — details arrive ordered by detail id, so the newest row is the
   * effective one. This is the OPPOSITE of {@link
   * ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation}'s first-wins {@code putIfAbsent}, which
   * is why {@code Schedule1Service} calls this method rather than each class splitting the rows
   * itself.
   */
  static void partitionDetails(
      List<DetailRow> details, Map<Integer, DetailRow> byCode, List<DetailRow> otherCostRows) {
    for (DetailRow row : details) {
      if (row.costItemCode() == null) {
        continue;
      }
      if (row.costItemCode() == CODE_OTHER) {
        otherCostRows.add(row);
      } else {
        byCode.put(row.costItemCode(), row);
      }
    }
  }

  /** The no-FMA subtotal from a summary's raw detail rows (partitions them first). */
  static long subtotalLoggingNoFma(List<DetailRow> details) {
    Map<Integer, DetailRow> byCode = new HashMap<>();
    List<DetailRow> otherCostRows = new ArrayList<>();
    partitionDetails(details, byCode, otherCostRows);
    return subtotalLoggingNoFma(byCode, otherCostRows);
  }

  /**
   * The no-FMA subtotal from already-partitioned rows — the entry point {@code
   * Schedule1Service.assemble} uses, since it has the partition in hand and adds Forest Mgmt Admin
   * back on afterwards.
   */
  static long subtotalLoggingNoFma(Map<Integer, DetailRow> byCode, List<DetailRow> otherCostRows) {
    return loggingLineCost(byCode) + itemizedOtherCostsSubtotal(otherCostRows);
  }

  /** Σ of the entered logging lines 12–18, an absent row or null cost counting as 0. */
  static long loggingLineCost(Map<Integer, DetailRow> byCode) {
    long total = 0L;
    for (int code : LOGGING_LINE_CODES) {
      DetailRow row = byCode.get(code);
      total += row == null || row.cost() == null ? 0L : row.cost();
    }
    return total;
  }

  /**
   * Subtotal Other Costs — Σ of the ITEMIZED item-19 rows' costs. The description-less row carries
   * the block's shared volume, not an itemized cost, so it never contributes.
   *
   * <p>Legacy splits on {@code isNullOrEmptyString} (null or {@code ""}), NOT a whitespace-trimmed
   * blank, so a whitespace-only description is an itemized row — matching the {@code IS NULL} /
   * {@code IS NOT NULL} read paths (on Oracle {@code ""} is stored as NULL, so this differs only on
   * whitespace). Summed as {@code long} to avoid silent int overflow across many/large rows.
   */
  static long itemizedOtherCostsSubtotal(List<DetailRow> otherCostRows) {
    return otherCostRows.stream()
        .filter(row -> StringUtils.isNotEmpty(row.itemDescription()))
        .map(DetailRow::cost)
        .filter(cost -> cost != null)
        .mapToLong(Integer::longValue)
        .sum();
  }
}
