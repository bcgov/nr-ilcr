package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an association is made effective on a mill that is not itself active (BR-02).
 *
 * <p>Activating a submitter on a closed mill would grant reporting rights on a mill that accepts no
 * reporting, so the mill has to be reopened first.
 *
 * <p>Both screens over this cross-reference raise it, and they reach it differently: the users
 * screen on the revive branch of {@link AssignmentService#assign} (an ended assignment brought
 * back), and the mill record on {@link MillAssociationService#activate}, an explicit per-row
 * Activate that revives nothing. Anyone auditing where BR-02 is enforced needs both call sites.
 */
public class MillNotActiveException extends BusinessException {

  public MillNotActiveException() {
    super(HttpStatus.CONFLICT, "error.user.activate.millinactive");
  }
}
