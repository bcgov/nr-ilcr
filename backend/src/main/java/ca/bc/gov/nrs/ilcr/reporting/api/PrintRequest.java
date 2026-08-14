package ca.bc.gov.nrs.ilcr.reporting.api;

/**
 * The Print Schedules selection (Epic 20.2). Carries the twelve schedule flags plus the "all"
 * shortcut and the three print options, mirroring the legacy {@code PrintSchedulesMB} screen so the
 * frontend can build the full page while the backend renders only the in-scope sections
 * (5, 6, 7A, 7B, 9, 11). A selected-but-unimplemented schedule (1/2/3/8/10) and {@code
 * printMillInformationReport} are accepted for forward-compatibility but produce no section yet
 * (skipped, logged) until their story lands.
 *
 * <p>Flags are boxed {@link Boolean} (the request-record convention in this codebase) so Jackson has
 * a property-based creator; the compact constructor defaults any OMITTED flag to {@code false}, so a
 * caller that sends only the flags it wants set gets exactly that selection and every accessor is
 * null-safe. {@code allSchedules} expands to every schedule flag (BR-07), resolved by the service.
 *
 * @param schedule1 print Schedule 1 (accepted; not rendered in 20.2)
 * @param schedule2 print Schedule 2 (accepted; not rendered in 20.2)
 * @param schedule3 print Schedule 3 (accepted; not rendered in 20.2)
 * @param schedule4 print Schedule 4 (accepted; not rendered in 20.2)
 * @param schedule5 print Schedule 5 (Camp and Access Expenses)
 * @param schedule6 print Schedule 6 (Road Management Costs)
 * @param schedule7a print Schedule 7A (Bridge Costs)
 * @param schedule7b print Schedule 7B (Culvert Costs)
 * @param schedule8 print Schedule 8 (accepted; not rendered in 20.2)
 * @param schedule9 print Schedule 9 (Miscellaneous/Unique Logging Costs)
 * @param schedule10 print Schedule 10 (accepted; not rendered in 20.2)
 * @param schedule11 print Schedule 11 (Basic Silviculture)
 * @param allSchedules select every schedule (BR-07); expanded server-side
 * @param printScheduleInformation render the template body ({@code p_do_print_body})
 * @param printComments render the comments block ({@code p_do_print_comment})
 * @param printMillInformationReport accepted but not rendered in 20.2 (deferred to Epic 19)
 */
public record PrintRequest(
    Boolean schedule1,
    Boolean schedule2,
    Boolean schedule3,
    Boolean schedule4,
    Boolean schedule5,
    Boolean schedule6,
    Boolean schedule7a,
    Boolean schedule7b,
    Boolean schedule8,
    Boolean schedule9,
    Boolean schedule10,
    Boolean schedule11,
    Boolean allSchedules,
    Boolean printScheduleInformation,
    Boolean printComments,
    Boolean printMillInformationReport) {

  /** Default every omitted flag to {@code false} so all accessors are null-safe. */
  public PrintRequest {
    schedule1 = orFalse(schedule1);
    schedule2 = orFalse(schedule2);
    schedule3 = orFalse(schedule3);
    schedule4 = orFalse(schedule4);
    schedule5 = orFalse(schedule5);
    schedule6 = orFalse(schedule6);
    schedule7a = orFalse(schedule7a);
    schedule7b = orFalse(schedule7b);
    schedule8 = orFalse(schedule8);
    schedule9 = orFalse(schedule9);
    schedule10 = orFalse(schedule10);
    schedule11 = orFalse(schedule11);
    allSchedules = orFalse(allSchedules);
    printScheduleInformation = orFalse(printScheduleInformation);
    printComments = orFalse(printComments);
    printMillInformationReport = orFalse(printMillInformationReport);
  }

  private static Boolean orFalse(Boolean value) {
    return value != null && value;
  }

  /** Whether any schedule flag is set (after expanding {@code allSchedules}). */
  public boolean anyScheduleSelected() {
    return allSchedules
        || schedule1 || schedule2 || schedule3 || schedule4 || schedule5 || schedule6
        || schedule7a || schedule7b || schedule8 || schedule9 || schedule10 || schedule11;
  }

  /** Whether either content print option is set (schedule information or comments). */
  public boolean anyContentOptionSelected() {
    return printScheduleInformation || printComments;
  }

  /** Whether any print option at all is set (either content option OR the mill-information report). */
  public boolean anyPrintOptionSelected() {
    return anyContentOptionSelected() || printMillInformationReport;
  }
}
