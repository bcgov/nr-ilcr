package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an UPDATE reaches the service without its optimistic-lock token. Maps to 400.
 *
 * <p>Bean Validation already enforces this through the {@code OnUpdate} group, so this is the
 * belt-and-braces guard for any path that reaches the service without that group applied. Without
 * it the token would be unboxed from null and NPE into a 500.
 *
 * <p>Deliberately a 400 rather than a coerced 409: substituting a sentinel like {@code -1} matches
 * no row, so the optimistic-lock UPDATE misses and the user is told another user changed the row
 * when the real fix is to send the token. Several older schedules coerce; this one does not.
 */
public class RevisionCountRequiredException extends BusinessException {

  public RevisionCountRequiredException() {
    super(HttpStatus.BAD_REQUEST, "revisionCountRequiredErrorMsg");
  }
}
