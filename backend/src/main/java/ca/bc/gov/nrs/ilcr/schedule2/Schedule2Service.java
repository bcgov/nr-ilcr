package ca.bc.gov.nrs.ilcr.schedule2;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule2.dto.CostBlock;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Request;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
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
 * Schedule-3 sources are model-computed aggregates in the legacy Schedule 1/3 graph, so they are read
 * from Schedule 3's own computed document ({@link Schedule3Service#getSchedule3}) — the single source
 * of truth — NOT from ad-hoc stored-detail queries. {@code purchasedWoodOverhead.cost} (and the
 * subtotal PO&amp;P term) is the Schedule 3 <em>Subtotal Actual Costs PO&amp;P column</em>
 * ({@code getPurchasedWoodCal}/{@code getSubtotalCost}), NOT a persisted "item 135" row.
 * {@code totalCompanyLogging.cost} implements the full legacy {@code Schedule2MB.getTotalLoggingCost}:
 * {@code (sch1 subtotalLoggingCost[144] + sch3 subtotalActualCosts.crownCost)
 * + ((sch1 silvActualSpent[1] − sch3 silvAdmin.crownCost) + sch1 silvAccruedSpent[2])}. The
 * {@code subtotalLoggingCost} term is Schedule 1's COMPUTED {@code getSubtotalLoggingCost} (its
 * {@code subtotalCompanyLoggingCost} minus Forest Management Admin — NOT the stored item 144); the
 * silviculture terms are persisted items 1/2; the two Schedule-3 crown operands come from the Schedule
 * 3 document (Subtotal Actual Costs Crown column; item-37 Silviculture Admin crown).
 *
 * <p>An absent Schedule 3 (no category-{@code "3"} summary) makes {@code getSchedule3} raise
 * {@link ScheduleNotFoundException}; Schedule 2 never 404s, so it is swallowed and every carried
 * Schedule-3 figure is treated as null. Null propagation mirrors legacy {@code CoreUtil}: addition
 * returns the non-null operand when one side is null (null only when both null); subtraction returns
 * the minuend when the subtrahend is null (null when the minuend is null); division returns null when
 * either operand is null or the denominator is zero.
 */
@Service
@Slf4j
public class Schedule2Service {

  private static final String STATUS_DRAFT = "D";

  private static final int ITEM_PURCHASED_LOG_COST = 25; // cost entered
  private static final int ITEM_LESS_LOG_SALES = 26;     // volume + cost entered
  // Schedule 3 Silviculture Admin Costs line (category-'3' item 37, Harvest-only → crown = its cost).
  private static final int ITEM_SILV_ADMIN = 37;

  private static final String OUTCOME_MET = "MET";
  private static final String OUTCOME_ISSUES = "ISSUES";
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_MISSING_REQUIRED = "missingRequiredFieldMsg";
  // Legacy field label for the ISSUES message (Schedule2MB.java:168) — the controller prefixes the
  // resolved missingRequiredFieldMsg text with "<label>: ", matching Schedule1Service.valueRequired.
  private static final String LABEL_PURCHASED_LOG_COST = "Purchased/Private Log Costs - Cost";

  private final Schedule2Repository repository;
  private final Schedule1Service schedule1Service;
  private final Schedule3Service schedule3Service;

  public Schedule2Service(Schedule2Repository repository, Schedule1Service schedule1Service,
      Schedule3Service schedule3Service) {
    this.repository = repository;
    this.schedule1Service = schedule1Service;
    this.schedule3Service = schedule3Service;
  }

  /**
   * Persist the two entered Schedule 2 line items (25/26) + comments for a mill/year and return the
   * recomputed document (S12). The mill/year context is already validated in the controller (AD-4).
   * Enforces the server-side Draft gate (AD-9) and optimistic-lock concurrency (AR11).
   *
   * <p>The Schedule 2 divergence from Schedule 1: SAVE <em>creates the summary when none exists</em>
   * ({@link #getOrCreateEditableSummary}) — Schedule 2 never 404s. A brand-new summary is inserted at
   * revision 0 and then bumped to 1 by the same optimistic-lock write used for updates, so the read
   * always sees a consistent, monotonically-increasing {@code revisionCount}. Over HTTP the client
   * always sends {@code revisionCount} 0 for a new/unsaved schedule (never null — the DTO field is
   * {@code @NotNull}); a new schedule's 0 matches the freshly-created summary's revision 0. The
   * {@code null → 0} coalesce below is unreachable via HTTP and kept only as defense-in-depth for
   * direct (non-validated) callers.
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
    // null → 0 is defense-in-depth only: the DTO's @NotNull makes null unreachable over HTTP (0 is the
    // new/unsaved token). Kept for direct callers that bypass bean validation.
    int expectedRevision = request.revisionCount() == null ? 0 : request.revisionCount();
    try {
      // Create-on-absent runs INSIDE the try so a persistence failure on the create path
      // (INSERT / sequence fetch) is translated to ScheduleNotSaved (500) exactly like the update
      // path — never leaked as a raw DataAccessException (which the shared handler would map to 409).
      // requireDraft's 409 still propagates: ScheduleNotEditableException is not a DataAccessException.
      int summaryId = getOrCreateEditableSummary(millId, year, request.comments(), user);
      int bumped = repository.bumpRevision(summaryId, expectedRevision, request.comments(), user);
      if (bumped == 0) {
        // A stale-revision conflict is a normal concurrent-edit outcome (→ 409), not an error, so
        // this is debug-level. Guarded so the extra revision lookup only runs when debug is enabled.
        // Revision counts are safe to log (AD-11 bars only cost/volume values).
        if (log.isDebugEnabled()) {
          Integer storedRevision = repository.findSummary(millId, year)
              .map(SummaryRow::revisionCount)
              .orElse(null);
          log.debug("Stale revision for mill {} year {}: expected {}, stored {}",
              millId, year, expectedRevision, storedRevision);
        }
        throw new StaleRevisionException();
      }
      // item 25 — cost only (its volume is carried from Schedule 3, never entered here).
      repository.upsertDetail(summaryId, ITEM_PURCHASED_LOG_COST, null,
          request.purchasedLogCostCost(), user);
      // item 26 — volume + cost.
      repository.upsertDetail(summaryId, ITEM_LESS_LOG_SALES,
          request.lessLogSalesVolume(), request.lessLogSalesCost(), user);
      // Recompute-and-return INSIDE the try so a late DataAccessException on the read path is also
      // translated to ScheduleNotSaved (500) rather than leaking to the shared handler (409).
      return getSchedule2(millId, year, callerMayEdit);
    } catch (StaleRevisionException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      // Log the DB cause (constraint name / ORA text) but NEVER the cost/volume values (AD-11) — the
      // most-specific cause is the SQL error, not business data, so this stays diagnosable in prod.
      log.warn("Schedule 2 save failed for mill {} year {} [{}]: {}", millId, year,
          ex.getClass().getSimpleName(), NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
      throw new ScheduleNotSavedException();
    }
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
      log.warn("Schedule 2 delete failed for mill {} year {} [{}]: {}", millId, year,
          ex.getClass().getSimpleName(), NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
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

  /**
   * The Draft gate shared by save and delete: the Schedules 1–10 track must be Draft (else 409).
   * Uses the {@code FOR UPDATE} locking read so concurrent first-saves for the same mill/year
   * serialize on the report-status row — closing the create-on-absent duplicate-summary race (the
   * real schema has no unique constraint on year+mill+category). Safe: only write paths call this,
   * and both run inside a {@code @Transactional}.
   */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatusForUpdate(millId, year).orElse(null);
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

    // Carried Schedule 3 figures — sourced from Schedule 3's computed document (single source of truth,
    // matching the legacy Schedule2MB which reads the Schedule 3 model), NOT ad-hoc stored-detail
    // queries. Absent Schedule 3 (no category-'3' summary) → getSchedule3 404s; Schedule 2 never 404s,
    // so swallow it and treat every carried figure as null (legacy CoreUtil null-propagation).
    //   purchasedWoodOverhead cost / subtotal PO&P term = Sch3 Subtotal Actual Costs PO&P column
    //     (getPurchasedWoodCal / getSubtotalCost), NOT a persisted "item 135" row.
    //   PO&P + Crown timber volumes = Sch3 popTimber / crownTimber volumes (items 118 / 119).
    Schedule3Response sch3;
    try {
      sch3 = schedule3Service.getSchedule3(millId, year, false);
    } catch (ScheduleNotFoundException ex) {
      // Expected when the mill/year has no category-'3' summary — the carried Sch3 figures drop to null.
      log.debug("No Schedule 3 for mill {} year {}; carried Sch3 figures null", millId, year);
      sch3 = null;
    }
    BigDecimal popTimberVolume = sch3 == null ? null : sch3.popTimber().volume();
    Integer popActualCost = sch3 == null ? null : longToInt(sch3.subtotalActualCosts().pop());
    BigDecimal crownVolume = sch3 == null ? null : sch3.crownTimber().volume();

    // Schedule 1 "Subtotal Company Logging Cost (no silviculture)" — the legacy
    // Schedule1DO.getSubtotalLoggingCost: the computed sum of the harvest cost blocks + Subtotal Other
    // Costs, EXCLUDING Forest Management Admin (its javadoc note). NOT the stored item 144. Schedule 1's
    // computed subtotalCompanyLoggingCost includes FMA (Schedule1Service line: logging + fma + other),
    // so the legacy no-FMA figure is subtotalCompanyLoggingCost − forestMgmtAdminCost. Absent Schedule 1
    // (404) → null (term drops).
    Schedule1Response sch1;
    try {
      sch1 = schedule1Service.getSchedule1(millId, year, false);
    } catch (ScheduleNotFoundException ex) {
      // Expected when the mill/year has no Schedule 1 summary — the carried Sch1 terms drop.
      log.debug("No Schedule 1 for mill {} year {}; carried Sch1 terms null", millId, year);
      sch1 = null;
    }
    Integer sch1SubtotalLoggingCost = sch1 == null ? null : subtotalLoggingNoFma(sch1);
    // Schedule 1 silviculture actual/accrued $ spent (items 1/2) — the stored terms of the legacy
    // totalCompanyLogging formula (getTotalLoggingCost); these are stored CostVolumeType costs.
    Integer sch1SilvActualSpent = repository.findSch1SilvActualSpentCost(millId, year).orElse(null);
    Integer sch1SilvAccruedSpent = repository.findSch1SilvAccruedSpentCost(millId, year).orElse(null);

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

    // --- totalCompanyLogging: volume = Sch3 Crown (119); cost = legacy getTotalLoggingCost; ---------
    //     perUnit = cost / Crown volume (getTotalLoggingCal). -----------------------------------------
    // Legacy Schedule2MB.getTotalLoggingCost():
    //   subtotalLoggingCost = sch1.subtotalLoggingCost(144) + sch3.subtotalActualCosts.crownCost
    //   totalSilvCost       = (sch1.silvActualSpent(1) - sch3.silvAdmin.crownCost) + sch1.silvAccruedSpent(2)
    //   result              = subtotalLoggingCost + totalSilvCost
    // Both Schedule-3 crown-cost operands are now sourced from the Schedule 3 document (AD-12 resolved):
    // subtotalActualCosts.crown is the computed Crown column of the Actual Costs subtotal; the
    // silviculture-admin crown is the Crown of the item-37 line (Harvest-only → crown = its cost).
    // Null when Schedule 3 is absent — CoreUtil null-propagation then drops the term (Sch1-only partial).
    BigDecimal sch3SubtotalActualsCrownCost =
        sch3 == null ? null : longToBd(sch3.subtotalActualCosts().crown());
    BigDecimal sch3SilvAdminCrownCost = sch3 == null ? null : bd(silvAdminCrown(sch3));
    BigDecimal subtotalLoggingCostTerm =
        add(bd(sch1SubtotalLoggingCost), sch3SubtotalActualsCrownCost);
    BigDecimal silvBd = subtract(bd(sch1SilvActualSpent), sch3SilvAdminCrownCost);
    BigDecimal totalSilvCost = add(silvBd, bd(sch1SilvAccruedSpent));
    BigDecimal totalLoggingCost = add(subtotalLoggingCostTerm, totalSilvCost);
    CostBlock totalCompanyLogging = new CostBlock(
        normalizeVolume(crownVolume),
        toWholeDollars(totalLoggingCost),
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

  /**
   * Evaluate the Schedule 2 completion requirement (BR-07) for a mill/year — read-only (AD-5), never
   * mutates. Reuses the server-assembled document ({@link #getSchedule2}) and inspects
   * {@code purchasedLogCost.cost} (cost-item 25): non-null &rarr; {@code MET} with one
   * {@code scheduleRequirementsMetMsg}; null (including the unsaved-schedule state — never 404)
   * &rarr; {@code ISSUES} with one {@code missingRequiredFieldMsg}. The mill/year context is already
   * validated in the controller (AD-4). The returned {@link MessageInfo} carries the bundle KEY only;
   * the controller resolves the verbatim text (AD-8), mirroring the save/delete split.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return the outcome + one message key (text resolved by the controller)
   */
  @Transactional(readOnly = true)
  public Schedule2CheckStatusResponse checkStatus(long millId, int year) {
    // callerMayEdit is irrelevant to BR-07 (only the item-25 cost matters); pass false.
    Schedule2Response document = getSchedule2(millId, year, false);
    boolean met = document.purchasedLogCost().cost() != null;
    String outcome = met ? OUTCOME_MET : OUTCOME_ISSUES;
    String key = met ? MSG_REQUIREMENTS_MET : MSG_MISSING_REQUIRED;
    // For the ISSUES message the label is carried in text as the prefix the controller prepends to the
    // resolved bundle text ("<label>: Value Required"), mirroring legacy Schedule2MB:168 + Schedule 1
    // (Schedule1Service.valueRequired). The MET message needs no label prefix.
    String labelPrefix = met ? null : LABEL_PURCHASED_LOG_COST;
    return new Schedule2CheckStatusResponse(outcome, List.of(new MessageInfo(key, labelPrefix)));
  }

  // -------------------------------------------------------------------------------------------------
  // Arithmetic mirroring legacy CoreUtil null-semantics.
  // -------------------------------------------------------------------------------------------------

  private static BigDecimal bd(Integer value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  /** Null-safe {@code Long}→{@code BigDecimal} for the Schedule 3 crown/PO&P subtotals. */
  private static BigDecimal longToBd(Long value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  /**
   * Null-safe {@code Long}→{@code Integer} (whole-dollar cost). An out-of-int-range value null-
   * propagates (with a debug log) rather than throwing an {@code ArithmeticException} to the client —
   * consistent with the rest of this service's null handling. Schedule 3 sums are {@code Long}, but a
   * per-mill PO&amp;P actual-cost subtotal is well within {@code Integer} range in practice (legacy
   * stored COST as an int), so the guard is theoretical.
   */
  private static Integer longToInt(Long value) {
    if (value == null) {
      return null;
    }
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      log.debug("Schedule 2 cross-schedule cost {} is out of Integer range — treated as null", value);
      return null;
    }
    return value.intValue();
  }

  /**
   * The legacy {@code Schedule1DO.getSubtotalLoggingCost} (Subtotal Company Logging Cost, no
   * silviculture) — the harvest cost blocks + Subtotal Other Costs, EXCLUDING Forest Management Admin.
   * Schedule 1's own {@code subtotalCompanyLoggingCost} includes FMA, so subtract it back out.
   *
   * <p>Follow-up: this inverse arithmetic couples Schedule 2's totals to Schedule 1's subtotal
   * composition; {@code Schedule1Service} should expose the no-FMA subtotal directly. Tracked in
   * bcgov/nr-ilcr#252 (the relationship is pinned by
   * {@code Schedule2ServiceTest.totalCompanyLogging_usesSchedule1SubtotalMinusFma_notRawSubtotal}).
   */
  private static Integer subtotalLoggingNoFma(Schedule1Response sch1) {
    Long subtotalWithFma = sch1.subtotalCompanyLoggingCost();
    if (subtotalWithFma == null) {
      return null;
    }
    long fma = sch1.forestMgmtAdminCost() == null ? 0L : sch1.forestMgmtAdminCost();
    return longToInt(subtotalWithFma - fma); // range-safe (null-propagates on overflow, never 500)
  }

  /**
   * The Schedule 3 Silviculture Admin Costs crown cost — the {@code crown} of the item-37 line
   * ({@code harvest − pop}; Harvest-only so it equals its cost). Null when the line is absent.
   */
  private static Integer silvAdminCrown(Schedule3Response sch3) {
    return sch3.lineItems().stream()
        .filter(line -> line.costItemCode() != null && line.costItemCode() == ITEM_SILV_ADMIN)
        .map(CostLine::crown)
        .findFirst()
        .orElse(null);
  }

  /** {@code CoreUtil.bigDecimalAddition}: null only when both null; else the non-null operand(s). */
  private static BigDecimal add(BigDecimal augend, BigDecimal addend) {
    if (augend == null && addend == null) {
      return null;
    }
    if (augend == null) {
      return addend;
    }
    if (addend == null) {
      return augend;
    }
    return augend.add(addend);
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

  /**
   * Round a derived cost to whole dollars (legacy COST is an Integer). Null-safe. Uses
   * {@code intValueExact} so an out-of-int-range derived sum throws {@link ArithmeticException} rather
   * than silently wrapping to a wrong financial figure.
   */
  private static Integer toWholeDollars(BigDecimal cost) {
    return cost == null ? null : cost.setScale(0, RoundingMode.HALF_UP).intValueExact();
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
