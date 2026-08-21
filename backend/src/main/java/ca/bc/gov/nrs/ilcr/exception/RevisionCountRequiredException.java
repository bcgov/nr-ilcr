package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an edit reaches the service without its AR11 optimistic-lock token ({@code
 * revisionCount}). The HTTP path normally never gets here — the API's {@code PUT} validates
 * {@code @Validated({Default.class, OnUpdate.class})}, which already rejects a null token as a
 * clean 400 — but the service unboxes the token to an {@code int}, so this is the belt-and-braces
 * guard that keeps any future caller bypassing that group from turning a missing token into an
 * NPE-driven 500 (a validation group protects one entry point, not the method).
 *
 * <p>Deliberately a 400, NOT a coerced 409: a missing token is a malformed request, never a stale
 * one — a coerced 409 (e.g. a {@code -1} sentinel that matches no row) would tell the user to
 * reload when the real fix is to send the token (the Story 2.1 review lesson). It maps to the same
 * {@code revisionCountRequiredErrorMsg} the Bean Validation path produces, so the two routes are
 * indistinguishable to a client.
 *
 * <p>Canonical shared copy (Story 29.11) — one definition for every schedule instead of per-module
 * duplicates. {@code extends BusinessException}, so the single base-type {@code @ExceptionHandler}
 * maps it unchanged.
 */
public class RevisionCountRequiredException extends BusinessException {
  public RevisionCountRequiredException() {
    super(HttpStatus.BAD_REQUEST, "revisionCountRequiredErrorMsg");
  }
}
