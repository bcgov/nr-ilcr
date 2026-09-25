package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 11 save names the same location more than once — twice among the updates,
 * twice among the deletions, or once in each. Maps to 400: left to run, a repeated update would
 * meet the {@code REVISION_COUNT} its own first pass had just bumped and fail as a 409 stale edit,
 * and an id both updated and deleted has no single meaning. Either way the caller would be told
 * another user had changed the row when the request itself was malformed (the Schedule 7A
 * precedent, {@code DuplicateBridgeException}).
 */
public class DuplicateLocationException extends BusinessException {

  public DuplicateLocationException() {
    super(HttpStatus.BAD_REQUEST, "duplicateLocationErrorMsg");
  }
}
