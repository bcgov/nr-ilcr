package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.util.List;

/**
 * The Schedule 1 aggregate document (AD-5, AD-12) — the pinned GET response. All derived/read-only
 * fields ({@code perUnit}, subtotals, {@code trackStatus}, {@code editable}) are computed
 * server-side. {@code revisionCount} is the optimistic-lock token echoed for Story 2.1 writes.
 * {@code message} is null on GET (Jackson omits it) and carries the success message on the PUT echo
 * (AD-8/EQ-M3).
 */
public record Schedule1Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    Integer crownVolume,
    Integer revisionCount,
    String comments,
    List<LineItem> lineItems,
    SilvicultureBlock silviculture,
    Integer forestMgmtAdminCost,
    Integer lessSilvAdminCost,
    OtherCostsSummary otherCosts,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for the PUT echo, AD-8). */
  public Schedule1Response withMessage(MessageInfo message) {
    return new Schedule1Response(
        millId, year, trackStatus, editable, crownVolume, revisionCount, comments,
        lineItems, silviculture, forestMgmtAdminCost, lessSilvAdminCost, otherCosts, message);
  }
}
