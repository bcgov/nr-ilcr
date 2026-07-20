package ca.bc.gov.nrs.ilcr.schedule4.dto;

import java.util.List;

/**
 * The Schedule 4 (Special Log Transportation Costs) read document (AD-5, AD-12) — the pinned GET
 * response, frozen for all Schedule 4 stories. Unlike Schedules 1–3 (flat aggregates on
 * {@code ILCR_REPORT_SUMMARY}), Schedule 4 is a multi-location structure: each {@link Location} is a
 * {@code TRANSPORTATION_REPORT} row with its transportation-category amounts.
 *
 * <p>{@code trackStatus} is {@code ILCR_MILL_REPORT_STATUS_CODE} (the Schedules 1–10 track).
 * {@code editable} = the caller holds {@code EDIT_SCHEDULE} AND {@code trackStatus == "D"} — computed
 * server-side (AD-5), never client-supplied. A non-Draft mill still lists its locations
 * ({@code editable:false}). A valid, active mill/year with no {@code TRANSPORTATION_REPORT} rows for
 * category {@code "4"} returns {@code locations: []} — never a 404 (that is reserved for the
 * mill/year context guards).
 */
public record Schedule4Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    List<Location> locations) {
}
