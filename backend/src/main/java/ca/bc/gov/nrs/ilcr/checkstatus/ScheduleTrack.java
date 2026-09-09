package ca.bc.gov.nrs.ilcr.checkstatus;

/**
 * The two independent Check Status tracks (UC-CHK-001 BR-05, AD-9). Schedules 1–10 — with 7A and 7B
 * counted separately, so eleven validations — submit, verify and revert together; Schedule 11 sits
 * alone on its own track and is never moved by a 1–10 transition (UC-CHK-002 BR-08). The partition
 * is 11 + 1, not an even split ({@code UC-CHK-004-technical.md:165}).
 */
public enum ScheduleTrack {
  /**
   * Schedules 1, 2, 3, 4, 5, 6, 7A, 7B, 8, 9 and 10 — the {@code ILCR_MILL_REPORT_STATUS_CODE}
   * track.
   */
  SCHEDULES_1_TO_10,

  /** Schedule 11 alone — the {@code MILL_SILVICULTUR_STATUS_CODE} track. */
  SCHEDULE_11
}
