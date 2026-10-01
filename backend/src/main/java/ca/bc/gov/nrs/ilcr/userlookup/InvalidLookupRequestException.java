package ca.bc.gov.nrs.ilcr.userlookup;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a directory search request cannot be forwarded as asked: no search criterion, a
 * criterion too short to search on, an identity provider the directory does not serve, or a
 * parameter that does not apply to the chosen provider.
 *
 * <p>The criteria half is a recorded departure from the legacy screen, whose blank search listed
 * every eligible WebADE user — the NR User Lookup API requires at least one search value, and the
 * government-wide IDIR directory has no "everyone eligible" to list.
 *
 * <p>Raised only through the named factories below. The constructor is private because a
 * stringly-typed key is not a constraint: an unresolvable key is not an error to {@code
 * GlobalExceptionHandler}, which falls back to returning the key itself as the {@code detail} — so
 * a typo would ship {@code error.user.lookup.criteia} to the browser as user-facing text.
 */
public final class InvalidLookupRequestException extends BusinessException {

  private InvalidLookupRequestException(String messageKey, Object... messageArgs) {
    super(HttpStatus.BAD_REQUEST, messageKey, messageArgs.length == 0 ? null : messageArgs);
  }

  /**
   * The request carried no value the chosen directory can search on.
   *
   * @return the rejection
   */
  public static InvalidLookupRequestException noCriteria() {
    return new InvalidLookupRequestException("error.user.lookup.criteria");
  }

  /**
   * The request named an identity provider the NR User Lookup API does not serve.
   *
   * @return the rejection
   */
  public static InvalidLookupRequestException unknownIdentityProvider() {
    return new InvalidLookupRequestException("error.user.lookup.idp");
  }

  /**
   * A criterion was too short to put in front of a contains-search of the government-wide IDIR
   * directory.
   *
   * @param minimumLength the shortest criterion the search accepts
   * @return the rejection
   */
  public static InvalidLookupRequestException criterionTooShort(int minimumLength) {
    return new InvalidLookupRequestException("error.user.lookup.criteria.length", minimumLength);
  }

  /**
   * A parameter was supplied that the chosen identity provider cannot use. Rejected rather than
   * ignored: silently dropping a criterion answers a narrower question than the admin asked, and
   * they get no signal that their filter was discarded.
   *
   * @param parameterName the offending query parameter
   * @return the rejection
   */
  public static InvalidLookupRequestException unsupportedParameter(String parameterName) {
    return new InvalidLookupRequestException("error.user.lookup.parameter", parameterName);
  }
}
