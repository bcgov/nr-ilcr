package ca.bc.gov.nrs.ilcr.schedule11.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 11 (Basic Silviculture) aggregate document — the pinned Story 25.1 wire contract
 * (AD-12). Sub-shapes ({@code trackStatus}/{@code editable}/{@code revisionCount}) reuse the Story
 * 1.1 contract verbatim, with the story's one key divergence: {@code trackStatus} is the
 * <b>Schedule 11 track's</b> {@code MILL_SILVICULTUR_STATUS_CODE} (legacy spelling, no final E) —
 * never the 1–10 track's code (AD-9/AR7 track independence).
 *
 * @param millId the mill id
 * @param year the reporting year
 * @param trackStatus the silviculture track code ({@code D}/{@code S}/{@code V} live; dead
 *     {@code O} passes through read-only per A-8); null when the status row's code column is null
 *     (legacy renders "Not Initiated" — display text is 25.3's concern)
 * @param editable caller holds {@code EDIT_SCHEDULE} AND the silviculture track is Draft (AD-5;
 *     the 1–10 track never affects this — S10)
 * @param revisionCount ALWAYS null: no {@code ILCR_REPORT_SUMMARY} row exists for this list
 *     schedule; 25.2 keys concurrency per-row on {@code BASIC_SILVICULTURE_REPORT.REVISION_COUNT}
 *     (recorded AR11 keying delta)
 * @param locations the location rows, ordered by {@code BASIC_SILVICULTURE_REPORT_ID} ascending
 * @param totals the footer totals (fields null — never zero — without contributors)
 * @param message success message on a 25.2 mutation echo (AD-8); ALWAYS null on the GET (Jackson
 *     {@code non_null} omits it, so the Story 25.1 GET wire is unchanged — a contract extension,
 *     not a re-shape)
 */
public record Schedule11Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    Integer revisionCount,
    List<SilvicultureLocation> locations,
    SilvicultureTotals totals,
    MessageInfo message) {

  /** A copy carrying the given success message (for a POST/PUT/DELETE echo, AD-8). */
  public Schedule11Response withMessage(MessageInfo message) {
    return new Schedule11Response(
        millId, year, trackStatus, editable, revisionCount, locations, totals, message);
  }
}
