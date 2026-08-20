package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an edit reaches the service without its AR11 optimistic-lock token. The normal path
 * never gets here — {@code @Validated({Default.class, OnUpdate.class})} on the API's PUT already
 * rejects a null {@code revisionCount} as a 400 — but the service unboxes the token to an {@code
 * int}, so this makes the failure a clean 400 instead of an NPE-driven 500 for any future caller
 * that bypasses the group (code review 2026-08-04, defence in depth).
 *
 * <p>Maps to 400 with the same {@code revisionCountRequiredErrorMsg} the Bean Validation path
 * produces, so the two routes are indistinguishable to a client. Critically NOT a 409: a missing
 * token is a malformed request, never a stale one (the Story 2.1 review lesson — a coerced 409
 * tells the user to reload when the real fix is to send the token).
 */
public class RevisionCountRequiredException extends BusinessException {

  public RevisionCountRequiredException() {
    super(HttpStatus.BAD_REQUEST, "revisionCountRequiredErrorMsg");
  }
}
