package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The Mill Information report could not be produced. Maps to HTTP 500 carrying the legacy {@code
 * undefinedError} text, which is what the legacy page showed for any failure while the report was
 * building — it caught {@code ILCSException} and rendered the generic unhandled-error message
 * rather than anything specific to the cause.
 *
 * <p>It also covers the year that has no mills at all. Legacy guarded that case with {@code
 * !mills.isEmpty()} and produced no file and no message whatsoever, so there is no legacy text to
 * reuse; routing it here tells the user something true instead of failing silently.
 */
public class MillInformationReportException extends BusinessException {

  public MillInformationReportException() {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "undefinedError");
  }
}
