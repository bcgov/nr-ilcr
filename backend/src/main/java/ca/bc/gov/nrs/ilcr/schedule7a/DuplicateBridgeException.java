package ca.bc.gov.nrs.ilcr.schedule7a;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 7A save-all names the same {@code bridgeReportId} more than once. Maps to
 * 400: left to run, the duplicate's second pass would meet the {@code REVISION_COUNT} its own first
 * pass had just bumped and fail as a 409 stale edit, telling the caller another user had changed
 * the row when in truth the request was malformed.
 */
public class DuplicateBridgeException extends BusinessException {

  public DuplicateBridgeException() {
    super(HttpStatus.BAD_REQUEST, "duplicateBridgeErrorMsg");
  }
}
