package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an ended assignment is brought back on a mill that is not itself active.
 *
 * <p>Reactivating a submitter on a closed mill would grant reporting rights on a mill that accepts
 * no reporting, so the mill has to be reopened first.
 */
public class MillNotActiveException extends BusinessException {

  public MillNotActiveException() {
    super(HttpStatus.CONFLICT, "error.user.activate.millinactive");
  }
}
