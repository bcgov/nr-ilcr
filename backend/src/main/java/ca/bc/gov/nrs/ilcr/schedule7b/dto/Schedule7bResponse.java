package ca.bc.gov.nrs.ilcr.schedule7b.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 7B (Culvert Costs) aggregate document — the pinned Story 13.1 wire contract (AD-12).
 * {@code trackStatus} is the Schedules 1–10 track code ({@code ILCR_MILL_REPORT_STATUS_CODE}, BR-01
 * — Schedule 7B has no track of its own); {@code editable} is server-authoritative ({@code
 * EDIT_SCHEDULE} ∧ {@code trackStatus == "D"}). {@code culverts} is the ordered list of stored
 * culverts (empty is a valid document); {@code codeLists} carries the Type dropdown options. {@code
 * message} is populated only on a mutating (Story 13.2) response echo — Jackson {@code non_null}
 * omits it on the GET, so the GET wire is unchanged.
 *
 * @param millId the mill id
 * @param year the reporting year
 * @param trackStatus the Schedules 1–10 track code ({@code D}/{@code S}/{@code V}/{@code O}); null
 *     when the status row's code column is null
 * @param editable caller holds {@code EDIT_SCHEDULE} AND the 1–10 track is Draft (AD-5/AD-9)
 * @param culverts the stored culverts, ordered by {@code CULVERT_REPORT_ID} ascending
 * @param codeLists the Culvert Type option list for the dropdown
 * @param message success message on a Story 13.2 mutation echo (AD-8); always null on the GET
 */
public record Schedule7bResponse(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    List<Culvert> culverts,
    CulvertCodeLists codeLists,
    MessageInfo message) {

  /** A copy carrying the given success message (for a POST/PUT/DELETE echo, AD-8). */
  public Schedule7bResponse withMessage(MessageInfo message) {
    return new Schedule7bResponse(
        millId, year, trackStatus, editable, culverts, codeLists, message);
  }
}
