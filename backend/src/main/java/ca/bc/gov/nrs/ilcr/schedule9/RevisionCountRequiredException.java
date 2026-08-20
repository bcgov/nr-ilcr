package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 9 edit reaches the service without its AR11 optimistic-lock token ({@code
 * revisionCount}). The HTTP path never gets here — the {@code PUT} validates
 * {@code @Validated({Default.class, OnUpdate.class})}, rejecting a null token as a clean 400 — but
 * the service unboxes the token to an {@code int}, so this keeps a future direct caller from
 * turning a missing token into an NPE-driven 500 (a validation group protects one entry point, not
 * the method — the 8.2 review lesson).
 *
 * <p>Maps to 400 with the same {@code revisionCountRequiredErrorMsg} the Bean Validation path
 * produces, so the two routes are indistinguishable to a client. Critically NOT a 409: a missing
 * token is a malformed request, never a stale one (the 2.1 review lesson).
 */
public class RevisionCountRequiredException extends BusinessException {

  public RevisionCountRequiredException() {
    super(HttpStatus.BAD_REQUEST, "revisionCountRequiredErrorMsg");
  }
}
