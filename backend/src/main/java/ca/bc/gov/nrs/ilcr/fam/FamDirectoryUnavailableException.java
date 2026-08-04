package ca.bc.gov.nrs.ilcr.fam;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The upstream FAM/Cognito directory could not be reached or returned an error. Maps to HTTP 503 with
 * the verbatim message (AD-8) so the admin screen can show it without breaking the (local) assignments
 * view.
 */
public class FamDirectoryUnavailableException extends BusinessException {

  public FamDirectoryUnavailableException() {
    super(HttpStatus.SERVICE_UNAVAILABLE, "famDirectoryUnavailableErrorMsg");
  }
}
