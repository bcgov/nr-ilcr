package ca.bc.gov.nrs.ilcr.checkstatus.dto;

import java.util.List;

/**
 * One track's half of the Check Status sweep (Story 15.1 AC 1/8): its persisted status code and its
 * schedules' verdicts, in legacy tab order, rolled up to one answer.
 *
 * <p>{@code statusCode} is the track's {@code ILCR_MILL_REPORT_STATUS} code ({@code D}/{@code
 * S}/{@code V}, dead {@code O} passes through) — the value the legacy {@code submenu.xhtml:18,:20}
 * banner is built from and the value Story 15.3's submit gate reads. It carries no description or
 * date: those already ride on {@code GET /api/v1/mill-context}, and duplicating them here would
 * give the banner two sources that could disagree. Null when the row's column is null (legacy would
 * NPE on a null silviculture code — tolerated since Story 1.2); omitted from the JSON under the
 * global {@code non_null} setting. No category-state codes are carried: legacy writes them on every
 * transition and reads them nowhere.
 *
 * @param statusCode the track's persisted status code; null when none
 * @param requirementsMet true iff EVERY schedule on the track is met — the gate Story 15.3 re-runs
 *     inside its write transaction
 * @param schedules the per-schedule verdicts in legacy order (eleven for 1–10, one for 11)
 */
public record TrackCheckResult(
    String statusCode, boolean requirementsMet, List<ScheduleCheckResult> schedules) {

  /**
   * Roll a track's verdicts up: met iff every schedule is met (an empty list is vacuously met, but
   * the sweep never produces one).
   *
   * @param statusCode the track's persisted status code; null when none
   * @param schedules the per-schedule verdicts in legacy order
   * @return the track result
   */
  public static TrackCheckResult of(String statusCode, List<ScheduleCheckResult> schedules) {
    boolean allMet = schedules.stream().allMatch(ScheduleCheckResult::requirementsMet);
    return new TrackCheckResult(statusCode, allMet, List.copyOf(schedules));
  }
}
