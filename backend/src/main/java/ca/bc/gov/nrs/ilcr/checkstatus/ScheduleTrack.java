package ca.bc.gov.nrs.ilcr.checkstatus;

import java.util.List;

/**
 * The two independent Check Status tracks (UC-CHK-001 BR-05, AD-9). Schedules 1–10 — with 7A and 7B
 * counted separately, so eleven validations — submit, verify and revert together; Schedule 11 sits
 * alone on its own track and is never moved by a 1–10 transition (UC-CHK-002 BR-08). The partition
 * is 11 + 1, not an even split ({@code UC-CHK-004-technical.md:165}).
 */
public enum ScheduleTrack {
  /**
   * Schedules 1, 2, 3, 4, 5, 6, 7A, 7B, 8, 9 and 10 — the {@code ILCR_MILL_REPORT_STATUS_CODE}
   * track. Ten {@code ILCR_REPORT_CATEGORY} rows: Schedule 7 is ONE category ({@code '7'}) covering
   * both 7A and 7B (legacy {@code Constant.CATEGORIES:25-47}).
   */
  SCHEDULES_1_TO_10(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")),

  /** Schedule 11 alone — the {@code MILL_SILVICULTUR_STATUS_CODE} track, one category row. */
  SCHEDULE_11(List.of("11"));

  private final List<String> categoryIds;

  ScheduleTrack(List<String> categoryIds) {
    this.categoryIds = categoryIds;
  }

  /**
   * The {@code ILCR_REPORT_CATEGORY.ILCR_CATEGORY_ID} values a transition on this track advances,
   * in legacy order — the rows a status transition writes and nothing on the other track reads.
   *
   * @return the category ids, immutable
   */
  public List<String> categoryIds() {
    return categoryIds;
  }
}
