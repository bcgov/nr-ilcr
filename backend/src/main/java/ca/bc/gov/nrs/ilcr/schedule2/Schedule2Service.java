package ca.bc.gov.nrs.ilcr.schedule2;

import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule2.dto.CostBlock;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Request;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 2 aggregate document from the stored line items (cost-items 25/26), the
 * carried Schedule 1/3 cross-schedule figures, and the server-computed derived blocks
 * ({@code subtotal}, {@code netPurchased}, {@code totalCompanyLogging}, {@code totalAverage}, and
 * every {@code perUnit}). Every derived/carried value is computed here (AD-5/AD-6) — never read from
 * storage on the Schedule 2 summary and never accepted from a client.
 *
 * <p>The mill/year context is validated by {@code MillContextService} in the controller before this
 * runs (AD-4). Unlike Schedule 1, a valid, active mill/year with NO category-{@code "2"} summary is
 * NOT a 404 — it is the legitimate unsaved-schedule state and yields a 200 empty editable document
 * (the carried Schedule 3 figures are still populated if that data exists).
 *
 * <p>Derivation is transcribed from the legacy {@code Schedule2MB} getters. Its cross-schedule
 * sources ({@code purchasedWoodOverhead.cost}, {@code totalCompanyLogging.cost}) are model-computed
 * aggregates in the legacy Schedule 3 graph; per the Story 3.1 cross-schedule-reads-not-features
 * decision (and because no Schedule 3 backend exists yet) they are sourced from the pinned
 * persisted figures — Schedule 3 item 135 (PO&amp;P actual cost) and Schedule 1 item 144 (Subtotal
 * Company Logging) respectively. Null propagation mirrors legacy {@code CoreUtil}: addition returns
 * the non-null operand when one side is null (null only when both null); subtraction returns the
 * minuend when the subtrahend is null (null when the minuend is null); division returns null when
 * either operand is null or the denominator is zero.
 */
@Service
@Slf4j
public class Schedule2Service {

  private static final String STATUS_DRAFT = "D";

  private static final int ITEM_PURCHASED_LOG_COST = 25; // cost entered
  private static final int ITEM_LESS_LOG_SALES = 26;     // volume + cost entered

  private final Schedule2Repository repository;

  public Schedule2Service(Schedule2Repository repository) {
    this.repository = repository;
  }

  /**
   * Persist the two entered Schedule 2 line items (25/26) + comments for a mill/year and return the
   * recomputed document (S12). The mill/year context is already validated in the controller (AD-4).
   * Enforces the server-side Draft gate (AD-9) and optimistic-lock concurrency (AR11).
   *
   * <p>The Schedule 2 divergence from Schedule 1: SAVE <em>creates the summary when none exists</em>
   * ({@link #getOrCreateEditableSummary}) — Schedule 2 never 404s. A brand-new summary is inserted at
   * revision 0 and then bumped to 1 by the same optimistic-lock write used for updates, so the read
   * always sees a consistent, monotonically-increasing {@code revisionCount}. A null client
   * {@code revisionCount} means "new/unsaved" and matches the freshly-inserted 0.
   *
   * <p>The whole method is one transaction: a persistence failure rolls back completely and surfaces
   * as 500 ({@code scheduleNotSavedErrorMsg}).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the entered fields + optimistic-lock token
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable} flag)
   * @param user the acting user id (audit columns)
   * @return the recomputed aggregate document (incremented {@code revisionCount})
   */
  @Transactional
  public Schedule2Response saveSchedule2(
      long millId, int year, Schedule2Request request, boolean callerMayEdit, String user) {
    int summaryId = getOrCreateEditableSummary(millId, year, request.comments(), user);
    int expectedRevision = request.revisionCount() == null ? 0 : request.revisionCount();
    try {
      int bumped = repository.bumpRevision(summaryId, expectedRevision, request.comments(), user);
      if (bumped == 0) {
        throw new StaleRevisionException();
      }
      // item 25 — cost only (its volume is carried from Schedule 3, never entered here).
      repository.upsertDetail(summaryId, ITEM_PURCHASED_LOG_COST, null,
          request.purchasedLogCostCost(), user);
      // item 26 — volume + cost.
      repository.upsertDetail(summaryId, ITEM_LESS_LOG_SALES,
          request.lessLogSalesVolume(), request.lessLogSalesCost(), user);
    } catch (StaleRevisionException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      // Never log cost/volume values (AD-11) — action + status + exception type only.
      log.warn("Schedule 2 save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule2(millId, year, callerMayEdit);
  }

  /**
   * Delete the whole Schedule 2 (summary + items 25/26) for a mill/year. Enforces the same Draft gate
   * as save. Idempotent: a Draft mill with no category-{@code "2"} summary is a no-op that still
   * returns 200 (never 404). Context is already validated in the controller (AD-4).
   *
   * @param millId the mill id
   * @param year the reporting year
   */
  @Transactional
  public void deleteSchedule2(long millId, int year) {
    requireDraft(millId, year);
    Optional<SummaryRow> summary = repository.findSummary(millId, year);
    if (summary.isEmpty()) {
      return; // idempotent — nothing to remove
    }
    try {
      repository.deleteSchedule(summary.get().summaryId());
    } catch (DataAccessException ex) {
      log.warn("Schedule 2 delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  /**
   * The Draft-gate guard for the create-on-absent save path: the track must be Draft (else 409), and
   * the category-{@code "2"} summary is created when absent (returning its id) — Schedule 2 never
   * 404s. This is the key deviation from {@code Schedule1Service.requireEditableSummary} (which 404s
   * on a missing summary).
   */
  private int getOrCreateEditableSummary(long millId, int year, String comments, String user) {
    requireDraft(millId, year);
    return repository.findSummary(millId, year)
        .map(SummaryRow::summaryId)
        .orElseGet(() -> repository.insertSummary(millId, year, comments, user));
  }

  /** The Draft gate shared by save and delete: the Schedules 1–10 track must be Draft (else 409). */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  /**
   * Assemble the Schedule 2 document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds the EDIT_SCHEDULE action (from the controller)
   * @return the aggregate document (never null; empty/editable when unsaved)
   */
  @Transactional(readOnly = true)
  public Schedule2Response getSchedule2(long millId, int year, boolean callerMayEdit) {
    Optional<SummaryRow> summary = repository.findSummary(millId, year);
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    // Stored line items 25/26 (empty when unsaved — AC6).
    Integer purchasedLogCostAmount = null; // item 25 cost
    BigDecimal lessLogSalesVolume = null;  // item 26 volume
    Integer lessLogSalesCost = null;       // item 26 cost
    String comments = null;
    Integer revisionCount = null;

    if (summary.isPresent()) {
      SummaryRow row = summary.get();
      comments = row.comments();
      revisionCount = row.revisionCount();
      List<DetailRow> details = repository.findDetails(row.summaryId());
      for (DetailRow d : details) {
        if (d.costItemCode() == null) {
          continue;
        }
        if (d.costItemCode() == ITEM_PURCHASED_LOG_COST) {
          purchasedLogCostAmount = d.cost();
        } else if (d.costItemCode() == ITEM_LESS_LOG_SALES) {
          lessLogSalesVolume = d.volume();
          lessLogSalesCost = d.cost();
        }
      }
    }

    // Carried cross-schedule figures (null when the source data is absent — no fabrication).
    BigDecimal popTimberVolume = repository.findSch3PopTimberVolume(millId, year).orElse(null);
    Integer popActualCost = repository.findSch3PopActualCost(millId, year).orElse(null);
    BigDecimal crownVolume = repository.findSch3CrownVolume(millId, year).orElse(null);
    Integer sch1SubtotalLoggingCost = repository.findSch1SubtotalLoggingCost(millId, year)
        .orElse(null);

    // --- purchasedLogCost: cost = item 25; volume carried from Sch3 118 (BR-03); perUnit derived. --
    CostBlock purchasedLogCost = new CostBlock(
        normalizeVolume(popTimberVolume),
        purchasedLogCostAmount,
        perUnit(bd(purchasedLogCostAmount), popTimberVolume)); // getPurchasedLogCostCal

    // --- purchasedWoodOverhead: all carried from Sch3 (vol 118, cost 135). ------------------------
    CostBlock purchasedWoodOverhead = new CostBlock(
        normalizeVolume(popTimberVolume),
        popActualCost,
        perUnit(bd(popActualCost), popTimberVolume)); // getPurchasedWoodCal

    // --- subtotal: cost = item25 + Sch3 135 (getSubtotalCost); volume = Sch3 118; ------------------
    //     perUnit = subtotalCost / Sch3 118 (getSubtotalCal). --------------------------------------
    BigDecimal subtotalCost = add(bd(purchasedLogCostAmount), bd(popActualCost));
    CostBlock subtotal = new CostBlock(
        normalizeVolume(popTimberVolume),
        toWholeDollars(subtotalCost),
        perUnit(subtotalCost, popTimberVolume));

    // --- lessLogSales: item 26 volume + cost; perUnit derived. ------------------------------------
    CostBlock lessLogSales = new CostBlock(
        normalizeVolume(lessLogSalesVolume),
        lessLogSalesCost,
        perUnit(bd(lessLogSalesCost), lessLogSalesVolume));

    // --- netPurchased: volume = Sch3 118 - lessLogSales.volume (getNetPurchasedVolume); ------------
    //     cost = subtotalCost - lessLogSales.cost (getNetPurchasedCost); perUnit = net/net. ---------
    BigDecimal netPurchasedVolume = subtract(popTimberVolume, lessLogSalesVolume);
    BigDecimal netPurchasedCost = subtract(subtotalCost, bd(lessLogSalesCost));
    CostBlock netPurchased = new CostBlock(
        normalizeVolume(netPurchasedVolume),
        toWholeDollars(netPurchasedCost),
        perUnit(netPurchasedCost, netPurchasedVolume));

    // --- totalCompanyLogging: volume = Sch3 Crown (119); cost = Sch1 total company logging (144, ---
    //     getTotalLoggingCost); perUnit = cost / Crown volume (getTotalLoggingCal). -----------------
    BigDecimal totalLoggingCost = bd(sch1SubtotalLoggingCost);
    CostBlock totalCompanyLogging = new CostBlock(
        normalizeVolume(crownVolume),
        sch1SubtotalLoggingCost,
        perUnit(totalLoggingCost, crownVolume));

    // --- totalAverage: volume = netPurchased.volume + Crown (getTotalAverageVolume); ---------------
    //     cost = netPurchased.cost + totalLoggingCost (getTotalAverageCost); perUnit = cost/vol. ----
    BigDecimal totalAverageVolume = add(netPurchasedVolume, crownVolume);
    BigDecimal totalAverageCost = add(netPurchasedCost, totalLoggingCost);
    CostBlock totalAverage = new CostBlock(
        normalizeVolume(totalAverageVolume),
        toWholeDollars(totalAverageCost),
        perUnit(totalAverageCost, totalAverageVolume));

    return new Schedule2Response(
        millId,
        year,
        trackStatus,
        editable,
        revisionCount,
        comments,
        purchasedLogCost,
        purchasedWoodOverhead,
        subtotal,
        lessLogSales,
        netPurchased,
        totalCompanyLogging,
        totalAverage,
        null); // success message is set by the controller on the PUT echo (AD-8)
  }

  // -------------------------------------------------------------------------------------------------
  // Arithmetic mirroring legacy CoreUtil null-semantics.
  // -------------------------------------------------------------------------------------------------

  private static BigDecimal bd(Integer value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  /** {@code CoreUtil.bigDecimalAddition}: null only when both null; else the non-null operand(s). */
  private static BigDecimal add(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) {
      return null;
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.add(b);
  }

  /**
   * {@code CoreUtil.bigDecimalSubtraction}: minuend when subtrahend null; null when minuend null;
   * else the difference.
   */
  private static BigDecimal subtract(BigDecimal minuend, BigDecimal subtrahend) {
    if (minuend != null && subtrahend == null) {
      return minuend;
    }
    if (minuend != null) {
      return minuend.subtract(subtrahend);
    }
    return null;
  }

  /**
   * $/m³ = cost / volume, computed server-side ({@code CoreUtil.bigDecimalDivision}). Null when
   * either operand is null or volume is zero. Scale 4 HALF_UP {@code stripTrailingZeros}, kept at
   * scale &ge; 1 so it serializes as a decimal (e.g. {@code 50.0}, not {@code 50}) — Schedule 1
   * parity.
   */
  private static BigDecimal perUnit(BigDecimal cost, BigDecimal volume) {
    if (cost == null || volume == null || volume.signum() == 0) {
      return null;
    }
    BigDecimal result = cost.divide(volume, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    return result.scale() < 1 ? result.setScale(1, RoundingMode.HALF_UP) : result;
  }

  /** Round a derived cost to whole dollars (legacy COST is an Integer). Null-safe. */
  private static Integer toWholeDollars(BigDecimal cost) {
    return cost == null ? null : cost.setScale(0, RoundingMode.HALF_UP).intValue();
  }

  /**
   * Normalize a volume so a whole value serializes as an integer ({@code 12345}, not
   * {@code 12345.0000} or {@code 1.2345E+4}) while a fractional value keeps its decimals. Null-safe.
   */
  private static BigDecimal normalizeVolume(BigDecimal volume) {
    if (volume == null) {
      return null;
    }
    BigDecimal stripped = volume.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
