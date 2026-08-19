package ca.bc.gov.nrs.ilcr.reportingyear;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Business failures for opening a reporting year (UC-RY-001): a missing/out-of-range starting-year
 * selection (400, FLD-001) or the year already being open (409). Carries a {@code messages.properties}
 * key the {@code GlobalExceptionHandler} resolves to the client-facing ProblemDetail detail (AD-8).
 * The recurring zero-active-mills case (INF-001 + ERR-002 together) uses {@code MultiMessageException}.
 */
public class ReportingYearException extends BusinessException {

  private ReportingYearException(HttpStatus status, String messageKey) {
    super(status, messageKey);
  }

  /** First-time setup with no (or an out-of-range) starting-year selection — nothing is created (FLD-001). */
  public static ReportingYearException invalidStartYear() {
    return new ReportingYearException(HttpStatus.BAD_REQUEST, "reportingYearNotValid");
  }

  /** The target reporting year is already open (defensive guard against a duplicate open). */
  public static ReportingYearException yearAlreadyOpen() {
    return new ReportingYearException(HttpStatus.CONFLICT, "reportingYearAlreadyOpenErrorMsg");
  }
}
