package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * A syntactically valid year that is not an opened reporting period.
 *
 * <p>No legacy analogue: the legacy control was a dropdown of opened periods reached only through a
 * server-rendered page, so an unlisted year was unreachable and needed no message. A REST endpoint
 * has no such guarantee, and without this the request would reach the report, find no mills, and
 * surface as {@code undefinedError} — telling an administrator the system had failed when they had
 * simply asked for a year that was never opened. Recorded as a deliberate deviation (AD-8).
 */
public class ReportYearNotOpenException extends BusinessException {

  public ReportYearNotOpenException() {
    super(HttpStatus.BAD_REQUEST, "reportYearNotOpenErrorMsg");
  }
}
