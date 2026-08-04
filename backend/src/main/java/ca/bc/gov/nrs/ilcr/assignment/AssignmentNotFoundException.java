package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * No active submitter↔mill assignment exists for the addressed (mill, user) pair — e.g. ending an
 * assignment that is not currently active. Maps to HTTP 404 with the verbatim message text (AD-8).
 */
public class AssignmentNotFoundException extends BusinessException {

  public AssignmentNotFoundException() {
    super(HttpStatus.NOT_FOUND, "assignmentNotFoundErrorMsg");
  }
}
