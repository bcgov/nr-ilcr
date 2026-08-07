package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a camp edit reaches the service without its AR11 optimistic-lock token. The HTTP path
 * never gets here — the API's {@code PUT} validates {@code @Validated({Default.class,
 * OnUpdate.class})}, which rejects a null {@code revisionCount} as a clean 400 — but the service
 * unboxes the token to an {@code int}, so this keeps any future direct caller from turning a
 * missing token into an NPE-driven 500. A validation group protects one entry point, not the method
 * (the 8.2 review lesson).
 *
 * <p>Maps to 400 with the same {@code revisionCountRequiredErrorMsg} the Bean Validation path
 * produces, so the two routes are indistinguishable to a client. Critically NOT a 409: a missing
 * token is a malformed request, never a stale one — a coerced 409 would tell the licensee to reload
 * when the real fix is to send the token (the Story 2.1 review lesson).
 *
 * <p>Schedule5-local rather than imported from {@code schedule6}, matching that package's own
 * reason for not importing a sibling's copy; the extraction is queued in {@code
 * deferred-work.md:14, 185}.
 */
public class RevisionCountRequiredException extends BusinessException {

  public RevisionCountRequiredException() {
    super(HttpStatus.BAD_REQUEST, "revisionCountRequiredErrorMsg");
  }
}
