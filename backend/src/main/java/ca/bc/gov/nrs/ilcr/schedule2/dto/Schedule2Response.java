package ca.bc.gov.nrs.ilcr.schedule2.dto;

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
 * <p>{@code revisionCount} is the optimistic-lock token echoed for the (deferred) Story 3.2 write —
 * null/0 when the schedule is unsaved.
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
    CostBlock totalAverage) {
}
