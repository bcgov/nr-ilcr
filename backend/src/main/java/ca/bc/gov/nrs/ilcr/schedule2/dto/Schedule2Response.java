package ca.bc.gov.nrs.ilcr.schedule2.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;

/**
 * The Schedule 2 (Purchased/Private Log Costs) aggregate document (AD-5, AD-12) — the pinned GET
 * response, frozen for all Schedule 2 stories. Every derived/read-only field ({@code perUnit},
 * {@code subtotal}, {@code netPurchased}, {@code totalCompanyLogging}, {@code totalAverage},
 * {@code trackStatus}, {@code editable}) is computed server-side and never accepted from a client.
 *
 * <p>The two editable line items are {@code purchasedLogCost} (cost-item 25 — cost entered here, its
 * volume carried from Schedule 3 item 118) and {@code lessLogSales} (cost-item 26 — volume + cost
 * entered). {@code purchasedWoodOverhead}, {@code totalCompanyLogging} volume, and the derived blocks
 * are carried/computed. When the Schedule 3 source data is absent the carried/derived figures are
 * null and (with the app-wide Jackson {@code non_null} inclusion) omitted from the JSON.
 *
 * <p>{@code revisionCount} is the optimistic-lock token echoed for the Story 3.2 write — null when
 * the schedule is unsaved, then the bumped count after a save.
 *
 * <p>{@code message} is the AD-8 success-message echo: null on a GET read (Jackson {@code non_null}
 * omits it) and carrying the resolved success {@link MessageInfo} on the PUT save echo. Adding it is
 * an additive, backward-compatible extension of the frozen read contract (same pattern as
 * {@code Schedule1Response}).
 */
public record Schedule2Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    Integer revisionCount,
    String comments,
    CostBlock purchasedLogCost,
    CostBlock purchasedWoodOverhead,
    CostBlock subtotal,
    CostBlock lessLogSales,
    CostBlock netPurchased,
    CostBlock totalCompanyLogging,
    CostBlock totalAverage,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for the PUT echo, AD-8). */
  public Schedule2Response withMessage(MessageInfo message) {
    return new Schedule2Response(
        millId, year, trackStatus, editable, revisionCount, comments,
        purchasedLogCost, purchasedWoodOverhead, subtotal, lessLogSales,
        netPurchased, totalCompanyLogging, totalAverage, message);
  }
}
