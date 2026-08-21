package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The Print Schedules request asked ONLY for the Mill Information Report (no schedule and no other
 * content option), which Epic 20.2 does not yet render (deferred to a later epic). Maps to HTTP 404
 * with the verbatim {@code millInformationReportUnavailableMsg}.
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
