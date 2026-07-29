package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 3 PUT carries a stale {@code revisionCount} (another user saved in between),
 * i.e. the optimistic-lock {@code bumpRevision} matched 0 rows (AR11). Maps to 409. Schedule3-local
 * peer of the Schedule 1 exception (same bundle key).
 */
public class StaleRevisionException extends BusinessException {

  public StaleRevisionException() {
    super(HttpStatus.CONFLICT, "scheduleRevisionConflictErrorMsg");
  }
}
