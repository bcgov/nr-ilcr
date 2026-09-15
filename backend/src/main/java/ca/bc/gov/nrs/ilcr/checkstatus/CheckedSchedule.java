package ca.bc.gov.nrs.ilcr.checkstatus;

/**
 * The twelve schedules Check Status validates, declared in the FIXED legacy tab order — the eleven
 * {@code checkStatus.xhtml} accordion tabs (1 → 2 → 3 → 4 → 5 → 6 → 7A → 7B → 8 → 9 → 10, {@code
 * :67-114}) followed by the separate Schedule 11 accordion ({@code :148-185}) — so iterating the
 * enum yields the order the licensee reads the results in. Each constant knows the wire code the
 * sweep reports it under and the track it sits on.
 *
 * <p><strong>Deliberately parallel to {@code reporting.ScheduleKey}, not a reuse of it.</strong>
 * That enum's members are print-coupled ({@code templatePath}, {@code bookmarkTitle}) and its
 * javadoc states its iteration order IS the combined-PDF section order. Validation must not inherit
 * print's concerns — or break when print's do change — so the two orders are asserted equal by test
 * rather than shared by type.
 */
public enum CheckedSchedule {
  SCHEDULE_1("1", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_2("2", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_3("3", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_4("4", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_5("5", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_6("6", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_7A("7A", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_7B("7B", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_8("8", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_9("9", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_10("10", ScheduleTrack.SCHEDULES_1_TO_10),
  SCHEDULE_11("11", ScheduleTrack.SCHEDULE_11);

  private final String code;
  private final ScheduleTrack track;

  CheckedSchedule(String code, ScheduleTrack track) {
    this.code = code;
    this.track = track;
  }

  /**
   * The schedule code the sweep reports this schedule under — the number as the legacy UI names it
   * ({@code "7A"}/{@code "7B"}, not {@code "7a"}).
   *
   * @return the wire code
   */
  public String code() {
    return code;
  }

  /**
   * The track this schedule's verdict rolls up into.
   *
   * @return the track
   */
  public ScheduleTrack track() {
    return track;
  }
}
