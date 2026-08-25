package ca.bc.gov.nrs.ilcr.userlookup;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a directory search request cannot be forwarded as asked: no search criterion at all,
 * or an identity provider the directory does not serve.
 *
 * <p>The criteria half is a recorded departure from the legacy screen, whose blank search listed
 * every eligible WebADE user — the NR User Lookup API requires at least one search value, and the
 * government-wide IDIR directory has no "everyone eligible" to list.
 */
public class InvalidLookupRequestException extends BusinessException {

  /**
   * Rejects the request with the reason.
   *
   * @param messageKey {@code error.user.lookup.criteria} or {@code error.user.lookup.idp}
   */
  public InvalidLookupRequestException(String messageKey) {
    super(HttpStatus.BAD_REQUEST, messageKey);
  }
}
