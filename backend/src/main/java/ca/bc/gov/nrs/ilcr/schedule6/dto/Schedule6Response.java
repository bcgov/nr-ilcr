package ca.bc.gov.nrs.ilcr.schedule6.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Schedule 6 (Road Management Costs) read document (AD-5, AD-12) — the pinned GET response,
 * frozen for all Schedule 6 stories. Like Schedule 4, Schedule 6 is a multi-record structure stored
 * in a dedicated master table ({@code THE.ROAD_MAINTENANCE_REPORT}) with per-record cost/volume in
 * {@code ILCR_COST_REPORT_DETAIL} — NOT the flat {@code ILCR_REPORT_SUMMARY} shape of Sch 1-3, and
 * there is no category-{@code "6"} summary row (delivery-DB confirmed, Story 8.1 Task 1).
 *
 * <p>{@code trackStatus} = {@code ILCR_MILL_REPORT_STATUS_CODE} (the Schedules 1-10 track, AD-9).
 * {@code editable} = the caller holds {@code EDIT_SCHEDULE} AND {@code trackStatus == "D"},
 * computed server-side (AD-5/AD-9), never client-supplied — a non-Draft mill still lists records
 * ({@code editable:false}). {@code generalComments} is the single schedule-level comment (stored
 * replicated on every road-record row in legacy). {@code totalVolume}/{@code totalCost}/ {@code
 * totalCostPerVolume} are DERIVED running totals (BR-07). A valid, active mill/year with no road
 * records returns {@code roadRecords: []} with zero totals and no general comment — never a 404
 * (that is reserved for the mill/year context guard, ERR-003).
 *
 * <p>{@code message} is the AD-8 success-message echo: null on a GET read (Jackson {@code non_null}
 * omits it), carrying the resolved {@link MessageInfo} on the Story 8.2 save echo.
 *
 * <p>{@code codeLists} carries the TSA and Supply Block dropdown options (code + description).
 * Legacy rendered both controls as {@code p:selectOneMenu} with {@code itemLabel} bound to the
 * code's {@code DESCRIPTION} ({@code schedule6.xhtml:265-323}), so the page must be able to show a
 * name for a stored code. This retires deviation (A), which served the raw codes as free text.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule6Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    String generalComments,
    List<RoadRecord> roadRecords,
    BigDecimal totalVolume,
    Long totalCost,
    BigDecimal totalCostPerVolume,
    Schedule6CodeLists codeLists,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (for the save echo, AD-8). */
  public Schedule6Response withMessage(MessageInfo message) {
    return new Schedule6Response(
        millId,
        year,
        trackStatus,
        editable,
        generalComments,
        roadRecords,
        totalVolume,
        totalCost,
        totalCostPerVolume,
        codeLists,
        message);
  }
}
