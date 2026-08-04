package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The assignment's optimistic-lock token ({@code REVISION_COUNT}) no longer matches — another admin
 * changed it since it was read (AD-9). Maps to HTTP 409, reusing the shared revision-conflict message.
 */
public class AssignmentStaleException extends BusinessException {

  public AssignmentStaleException() {
    super(HttpStatus.CONFLICT, "scheduleRevisionConflictErrorMsg");
  }
}
