package ca.bc.gov.nrs.ilcr.messages;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a message key is asked for that is not client-renderable — either absent from the
 * bundle entirely, or present but not on {@code MessageController}'s allowlist.
 *
 * <p>404 for BOTH cases, deliberately and with the same body: a distinct status or detail for
 * "exists but is not yours to render" would turn the endpoint into an oracle for which keys the
 * bundle holds. Absent and not-allowlisted are indistinguishable from the outside, the same
 * reasoning {@code CampNotFoundException} applies to absent-vs-foreign camps.
 */
public class MessageNotResolvableException extends BusinessException {

  public MessageNotResolvableException() {
    super(HttpStatus.NOT_FOUND, "messageNotResolvableErrorMsg");
  }
}
