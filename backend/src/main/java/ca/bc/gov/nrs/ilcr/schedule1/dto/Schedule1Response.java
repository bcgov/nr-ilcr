package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Schedule 1 aggregate document (AD-5, AD-12) — the pinned GET response. All derived/read-only
 * fields ({@code perUnit}, subtotals, {@code trackStatus}, {@code editable}) are computed
 * server-side. {@code revisionCount} is the optimistic-lock token echoed for Story 2.1 writes.
 * {@code message} is null on GET (Jackson omits it) and carries the success message on the PUT echo
 * (AD-8/EQ-M3).
 *
 * <p>Story 2.3 adds two read-only fields: {@code schedule3CrownVolume} — the Schedule 3 Crown Timber
 * volume (the BR-03 pre-fill source), read from the category-3 item-119 detail row — and
 * {@code warnings} — advisory, non-blocking messages carried on the GET (never fails the request).
 * WRN-001 (crown pre-fill) rides on {@code warnings}. {@code forestMgmtAdminCost} and
 * {@code lessSilvAdminCost} are the BR-04 costs pulled from Schedule 3 (not from Schedule 1's own
 * rows) and are ignored on write.
 */
public record Schedule1Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    Integer crownVolume,
    BigDecimal schedule3CrownVolume,
    Integer revisionCount,
    String comments,
    List<LineItem> lineItems,
    SilvicultureBlock silviculture,
    Integer forestMgmtAdminCost,
    Integer lessSilvAdminCost,
    OtherCostsSummary otherCosts,
    List<MessageInfo> warnings,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for the PUT echo, AD-8). */
  public Schedule1Response withMessage(MessageInfo message) {
    return new Schedule1Response(
        millId, year, trackStatus, editable, crownVolume, schedule3CrownVolume, revisionCount,
        comments, lineItems, silviculture, forestMgmtAdminCost, lessSilvAdminCost, otherCosts,
        warnings, message);
  }
}
