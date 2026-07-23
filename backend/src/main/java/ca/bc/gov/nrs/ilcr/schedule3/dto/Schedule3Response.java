package ca.bc.gov.nrs.ilcr.schedule3.dto;

import java.util.List;

/**
 * The Schedule 3 aggregate document (AD-5, AD-12) — the pinned GET response for the Forest Management
 * Administration Costs report. All derived/read-only fields (each line's {@code crown}, the timber
 * costs/{@code perUnit}, the four total blocks, {@code trackStatus}, {@code editable}, the counts)
 * are computed server-side and are ignored/rejected on write (Story 4.2). {@code revisionCount} is the
 * optimistic-lock token echoed for the Story 4.2 PUT. {@code message} is null on GET (Jackson omits
 * it) and carries the success message on the mutation echo (AD-8/EQ-M3).
 *
 * @param millId the mill id
 * @param year the reporting year
 * @param trackStatus the Schedules 1–10 track status ({@code ILCR_MILL_REPORT_STATUS_CODE}, AD-9)
 * @param editable server-authoritative: {@code EDIT_SCHEDULE} held AND track is Draft
 * @param revisionCount optimistic-lock token (summary {@code REVISION_COUNT})
 * @param overrideHarvestTotalPop the Override Harvest/Total PO&amp;P indicator ("Y"/"N"), from the
 *     summary {@code LOCATION} column (legacy carrier); defaults to "N" (BR-10, Story 4.2 writes it)
 * @param comments the free-text comments (max 3500 chars)
 * @param lineItems the 11 fixed admin-cost lines (27–37), each with harvest/pop/derived crown
 * @param popTimber the PO&amp;P Timber block (item 118 volume; cost/perUnit derived)
 * @param crownTimber the Crown Timber block (item 119 volume; drives the BR-09 push in Story 4.2)
 * @param totalOverhead the Total Overhead block (all derived)
 * @param subtotalOtherCosts the Other Acceptable Costs subtotal (from item-124 rows)
 * @param subtotalActualCosts the Subtotal Actual Costs (sum of the fixed lines + Subtotal Other Costs)
 * @param includedUnacceptableCosts the Included Unacceptable Costs (item-38 rows + Annual Rents harvest)
 * @param totalCosts the Total Costs (Subtotal Actual &minus; Included Unacceptable)
 * @param otherAcceptableCount the Other Acceptable Costs sub-page row-group count (CNT-001)
 * @param unacceptableCount the Included Unacceptable Costs sub-page row count (CNT-001; +1 for Annual Rents)
 * @param warnings advisory, non-blocking messages carried on a mutation echo (empty on GET); the BR-09
 *     Crown Timber push outcome (WRN-001 applied / WRN-002 not-opened) rides here on the Story 4.2 PUT
 * @param message the success message on a mutation echo, else null
 */
public record Schedule3Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    Integer revisionCount,
    String overrideHarvestTotalPop,
    String comments,
    List<CostLine> lineItems,
    TimberBlock popTimber,
    TimberBlock crownTimber,
    TimberBlock totalOverhead,
    ThreeColumnTotal subtotalOtherCosts,
    ThreeColumnTotal subtotalActualCosts,
    ThreeColumnTotal includedUnacceptableCosts,
    ThreeColumnTotal totalCosts,
    int otherAcceptableCount,
    int unacceptableCount,
    List<MessageInfo> warnings,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for the PUT echo, AD-8). */
  public Schedule3Response withMessage(MessageInfo message) {
    return new Schedule3Response(
        millId, year, trackStatus, editable, revisionCount, overrideHarvestTotalPop, comments,
        lineItems, popTimber, crownTimber, totalOverhead, subtotalOtherCosts, subtotalActualCosts,
        includedUnacceptableCosts, totalCosts, otherAcceptableCount, unacceptableCount, warnings,
        message);
  }

  /** A copy of this document carrying the given advisory warnings (e.g. the BR-09 crown-push outcome). */
  public Schedule3Response withWarnings(List<MessageInfo> warnings) {
    return new Schedule3Response(
        millId, year, trackStatus, editable, revisionCount, overrideHarvestTotalPop, comments,
        lineItems, popTimber, crownTimber, totalOverhead, subtotalOtherCosts, subtotalActualCosts,
        includedUnacceptableCosts, totalCosts, otherAcceptableCount, unacceptableCount, warnings,
        message);
  }
}
