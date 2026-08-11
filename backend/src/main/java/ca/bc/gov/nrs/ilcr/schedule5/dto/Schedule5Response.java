package ca.bc.gov.nrs.ilcr.schedule5.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The Schedule 5 (Camp and Access Expenses) read document (AD-5, AD-12) — the pinned GET response,
 * frozen for all Schedule 5 stories (7.2-7.5 realize this contract; they do not re-pin it).
 *
 * <p>Storage behind it is a HYBRID unlike any other shipped schedule (delivery-DB confirmed, Story
 * 7.1 Task 1): a dedicated master table {@code THE.CAMP_REPORT} (category {@code '5'}) whose
 * category amounts live as KEYED rows in the shared {@code THE.ILCR_COST_REPORT_DETAIL}, joined by
 * {@code CAMP_REPORT_ID} — items 56/58/59/60/61/63-67/141/142 are the fixed grid, 62/68 the
 * sub-page rows (counted here, itemized in 7.4). There is NO category-{@code '5'}
 * {@code ILCR_REPORT_SUMMARY} row (gate (ii): zero rows; summaries exist only for categories
 * 1/2/3), so Schedule 5 is summary-less like Schedules 4 and 6.
 *
 * <p>{@code trackStatus} = {@code ILCR_MILL_REPORT_STATUS_CODE} — the Schedules 1-10 track, never
 * the silviculture track (AD-9). {@code editable} = the caller holds {@code EDIT_SCHEDULE} AND
 * {@code trackStatus == "D"}, computed server-side and server-authoritative (AD-5/AD-9, S19); a
 * non-Draft mill still lists every camp with {@code editable:false}.
 *
 * <p><strong>There are no document-level totals.</strong> Every total is per camp — the legacy
 * screen has no cross-camp grand total ({@code schedule5.xhtml:50-119}) — and there is no top-level
 * {@code revisionCount} either, because there is no schedule-level row to key one on (deviation
 * (b)). A valid, ACTIVE mill/year with no camps returns {@code camps: []}, never a 404 (deviation
 * (a)); 404 is reserved for a missing {@code ILCR_MILL_REPORT_STATUS} context row (ERR-005).
 *
 * <p>{@code message} is the AD-8 success-message echo: null on a GET read (Jackson
 * {@code non_null} omits it), carrying the resolved {@link MessageInfo} on the Story 7.2 save
 * echo — which is why {@link #withMessage} exists here now, so 7.2 needs no re-shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule5Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    List<Camp> camps,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for the save echo, AD-8). */
  public Schedule5Response withMessage(MessageInfo message) {
    return new Schedule5Response(millId, year, trackStatus, editable, camps, message);
  }
}
