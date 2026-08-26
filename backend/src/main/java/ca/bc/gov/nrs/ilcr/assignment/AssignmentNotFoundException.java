package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a mill assignment is addressed that has no row at all — distinct from an assignment
 * that exists but has already been ended, which is a normal state and answers 200.
 *
 * <p>The legacy screens could not reach this: they only ever acted on a row the administrator had
 * already selected from the table. A REST caller can address any pair, so the case needs an answer.
 */
public class AssignmentNotFoundException extends BusinessException {

  public AssignmentNotFoundException() {
    super(HttpStatus.NOT_FOUND, "error.user.assignment.notfound");
  }
}
