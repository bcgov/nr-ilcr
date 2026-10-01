package ca.bc.gov.nrs.ilcr.userlookup;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when the NR User Lookup API (or its token endpoint) cannot answer a directory search.
 *
 * <p>Maps to 502 rather than 500 because the failure is upstream, not ours — and it must stay a
 * {@code ProblemDetail} the screen can show beside a still-working assignments view: the
 * assignments read the local xref, never the directory, so a directory outage must not take them
 * down with it.
 */
public class DirectoryUnavailableException extends BusinessException {

  public DirectoryUnavailableException() {
    super(HttpStatus.BAD_GATEWAY, "error.user.lookup.unavailable");
  }
}
