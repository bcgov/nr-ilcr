package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import org.springframework.http.HttpStatus;

/**
 * The Check Status page's own "not found" (UC-CHK-001 S06): the mill/reporting-year has no {@code
 * ILCR_MILL_REPORT_STATUS} row, or one of the twelve schedules' reads reported its data missing.
 * Maps to HTTP 404 with legacy message {@code checkStatusScheduleNotFoundErrorMsg} — "One or more
 * of the schedules for the report have not been found." ({@code messages.properties:66}).
 *
 * <p>Distinct from {@link ScheduleNotFoundException}'s {@code scheduleNotFoundErrorMsg} ("Schedule
 * not found."), which is what a single schedule's PAGE shows. Legacy {@code CheckStatusMB.init()}
 * sets {@code scheduleNotFound} both when the session has no status row ({@code :116-118}) and when
 * any {@code getScheduleN} throws {@code SCHEDULE_NOT_FOUND} ({@code :120-123}), and {@code
 * checkStatus.xhtml:20} renders this text for either — so the sweep endpoint translates the shared
 * guard's 404 into this one rather than answering with the per-page wording.
 */
public class CheckStatusScheduleNotFoundException extends BusinessException {

  /**
   * Wraps the underlying schedule-context miss.
   *
   * @param cause the guard's or a schedule's own not-found, kept for diagnostics
   */
  public CheckStatusScheduleNotFoundException(ScheduleNotFoundException cause) {
    super(HttpStatus.NOT_FOUND, "checkStatusScheduleNotFoundErrorMsg");
    initCause(cause);
  }
}
