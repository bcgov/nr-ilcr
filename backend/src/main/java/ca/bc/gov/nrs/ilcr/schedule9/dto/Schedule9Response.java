package ca.bc.gov.nrs.ilcr.schedule9.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The Schedule 9 (Miscellaneous and Unique Logging Costs) read document (AD-5, AD-12) — the pinned
 * GET response, frozen for all Schedule 9 stories (9.2–9.4 realize this contract; they do not
 * re-pin it).
 *
 * <p>Storage behind it is a dedicated master table {@code THE.CONTRACTUAL_WORK_REPORT} (category
 * {@code '9'}) whose Contractual Item + Cost live as a KEYED row in the shared
 * {@code THE.ILCR_COST_REPORT_DETAIL}, joined by {@code CONTRACTUAL_WORK_REPORT_ID} (legacy
 * {@code ILCRCostReportDetail.@JoinColumn("CONTRACTUAL_WORK_REPORT_ID")}); the cost line's
 * {@code ILCR_REPORT_COST_ITEM_ID} is the contractual item (108–114, BR-09). Like Schedules 4/5/6
 * this schedule is summary-less — there is NO category-{@code '9'} {@code ILCR_REPORT_SUMMARY} row,
 * so the read guards on {@code validateMillYearActive}, not {@code validateScheduleViewable("9")}
 * (which would 404 every request). Task-1 delivery gate confirms both against the delivery DB.
 *
 * <p>{@code trackStatus} = {@code ILCR_MILL_REPORT_STATUS_CODE} — the Schedules 1–10 track (AD-9).
 * {@code editable} = the caller holds {@code EDIT_SCHEDULE} AND {@code trackStatus == "D"}, computed
 * server-side and server-authoritative (AD-5/AD-9, S30); a non-Draft mill still lists every record
 * with {@code editable:false}.
 *
 * <p><strong>No document-level totals and no top-level {@code revisionCount}</strong> — there is no
 * schedule-level row to key one on; each {@link ContractualWorkRecord} carries its own optimistic-
 * lock token (writes are Story 9.2). A valid, ACTIVE mill/year with no records returns
 * {@code records: []}, never a 404. {@code message} is the AD-8 success echo: null on a GET read
 * (Jackson {@code non_null} omits it), carried on the 9.2 save echo via {@link #withMessage}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule9Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    List<ContractualWorkRecord> records,
    Schedule9CodeLists codeLists,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for the save echo, AD-8). */
  public Schedule9Response withMessage(MessageInfo message) {
    return new Schedule9Response(millId, year, trackStatus, editable, records, codeLists, message);
  }
}
