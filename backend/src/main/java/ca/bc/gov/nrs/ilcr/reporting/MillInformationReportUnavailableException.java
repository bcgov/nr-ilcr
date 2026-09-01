package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The Print Schedules request asked ONLY for the Mill Information Report (no schedule and no other
 * content option). Maps to HTTP 404 with the verbatim {@code millInformationReportUnavailableMsg}.
 *
 * <p>Story 19.1 delivered the standalone all-mills report at {@code GET /reports/mill-information}
 * and does NOT satisfy this: the print option is the PER-MILL section inside the combined schedules
 * PDF for the Home working context, a different document from the all-mills report. It remains
 * deferred — do not read "a later epic" as meaning 19.1.
 *
 * <p>Distinct from {@link ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException} on purpose: a
 * mill-info-only selection produced no PDF for a reason that has nothing to do with a mill/year
 * having no schedule data, so answering the generic "Schedule not found." would mislead the
 * reporter into thinking their data was missing. This says what actually happened.
 */
public class MillInformationReportUnavailableException extends BusinessException {

  public MillInformationReportUnavailableException() {
    super(HttpStatus.NOT_FOUND, "millInformationReportUnavailableMsg");
  }
}
