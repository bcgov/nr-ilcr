package ca.bc.gov.nrs.ilcr.schedule8.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 8 (Tree to Truck / Special Skidding Costs) read document (AD-5, AD-12) — the pinned GET
 * response, frozen for all Schedule 8 stories. Unlike Schedules 1–3 (flat aggregates on
 * {@code ILCR_REPORT_SUMMARY}), Schedule 8 is a three-level hierarchy: each {@link Page} is a
 * {@code TREE_TO_TRUCK_REPORT} row carrying its {@code samples}, and each {@link Sample} carries its
 * {@code additions}/{@code deductions} ({@link RateRow}s) plus every server-computed roll-up.
 *
 * <p>{@code trackStatus} is {@code ILCR_MILL_REPORT_STATUS_CODE} (the Schedules 1–10 track).
 * {@code editable} = the caller holds {@code EDIT_SCHEDULE} AND {@code trackStatus == "D"} — computed
 * server-side (AD-5), never client-supplied. A non-Draft mill still lists its pages
 * ({@code editable:false}). A valid, active mill/year with no category-{@code '8'} pages returns
 * {@code pages: []} — never a 404 (that is reserved for the mill/year context guards).
 *
 * <p>{@code message} is the AD-8 success-message echo: null on a GET read (Jackson {@code non_null}
 * omits it) and set on the later write echoes (same pattern as {@code Schedule4Response}).
 */
public record Schedule8Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    List<Page> pages,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for a write echo, AD-8). */
  public Schedule8Response withMessage(MessageInfo message) {
    return new Schedule8Response(millId, year, trackStatus, editable, pages, message);
  }
}
