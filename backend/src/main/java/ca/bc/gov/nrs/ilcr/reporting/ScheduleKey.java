package ca.bc.gov.nrs.ilcr.reporting;

/**
 * The schedules Epic 20.2 can render, each bound to its classpath template and its bookmark title.
 * Declared in the FIXED legacy print order (5 → 6 → 7A → 7B → 9 → 11 within the 1..11 sequence), so
 * iterating the enum yields the combined PDF's section order (BR-08) without a separate ordering
 * table. Schedules 1/2/3/8/10 and the Mill Information report are out of scope (no enum constant) —
 * a selected-but-unimplemented schedule is skipped-with-a-log by the orchestrator.
 */
public enum ScheduleKey {
  SCHEDULE_5("reports/schedule5.jrxml", "Schedule 5: Camp and Access Expenses"),
  SCHEDULE_6("reports/schedule6.jrxml", "Schedule 6: Road Management Costs"),
  SCHEDULE_7A("reports/schedule7a.jrxml", "Schedule 7A: Bridge Costs"),
  SCHEDULE_7B("reports/schedule7b.jrxml", "Schedule 7B: Culvert Costs"),
  SCHEDULE_9("reports/schedule9.jrxml", "Schedule 9: Miscellaneous/Unique Logging Costs"),
  SCHEDULE_11("reports/schedule11.jrxml", "Schedule 11: Basic Silviculture");

  private final String templatePath;
  private final String bookmarkTitle;

  ScheduleKey(String templatePath, String bookmarkTitle) {
    this.templatePath = templatePath;
    this.bookmarkTitle = bookmarkTitle;
  }

  String templatePath() {
    return templatePath;
  }

  /** The top-level PDF bookmark title for this schedule's section. */
  public String bookmarkTitle() {
    return bookmarkTitle;
  }
}
