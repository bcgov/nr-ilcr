package ca.bc.gov.nrs.ilcr.reporting;

/**
 * The schedules Epic 20 can render, each bound to its classpath template and its bookmark title.
 * Declared in the FIXED legacy print order (1 → 2 → 3 → 4 → 5 → 6 → 7A → 7B → 8 → 9 → 10 → 11
 * within the 1..11 sequence), so iterating the enum yields the combined PDF's section order (BR-08)
 * without a separate ordering table. Schedule 1, 2, 3, and 4 sit first in the legacy sequence, so
 * they are declared first. The Mill Information report is out of scope (no enum constant) — a
 * selected-but-unimplemented schedule is skipped-with-a-log by the orchestrator.
 */
public enum ScheduleKey {
  SCHEDULE_1("reports/schedule1.jrxml", "Schedule 1: Average Cost of Logging"),
  SCHEDULE_2("reports/schedule2.jrxml", "Schedule 2: Purchased and Private Log Costs and Sales"),
  SCHEDULE_3("reports/schedule3.jrxml", "Schedule 3: Forest Management Administration Costs"),
  SCHEDULE_4("reports/schedule4.jrxml", "Schedule 4: Special Log Transportation Costs"),
  SCHEDULE_5("reports/schedule5.jrxml", "Schedule 5: Camp and Access Expenses"),
  SCHEDULE_6("reports/schedule6.jrxml", "Schedule 6: Road Management Costs"),
  SCHEDULE_7A("reports/schedule7a.jrxml", "Schedule 7A: Bridge Costs"),
  SCHEDULE_7B("reports/schedule7b.jrxml", "Schedule 7B: Culvert Costs"),
  SCHEDULE_8("reports/schedule8.jrxml", "Schedule 8: Tree to Truck Costs"),
  SCHEDULE_9("reports/schedule9.jrxml", "Schedule 9: Miscellaneous/Unique Logging Costs"),
  SCHEDULE_10("reports/schedule10.jrxml", "Schedule 10: New Road Construction Costs"),
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
