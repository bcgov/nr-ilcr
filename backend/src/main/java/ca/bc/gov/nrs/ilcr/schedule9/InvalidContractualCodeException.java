package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 9 write carries a code value — Contractual Item, Unit Type, Biogeoclimatic
 * Zone, or Source — that resolves to no row in its reference table (force-selection backend
 * enforcement, FLD-005/S26; the legacy FK would have rejected it). A blank code is instead a missing
 * required field (FLD-001), reported separately; this is the present-but-not-in-the-list case.
 *
 * <p>Maps to 400 with the verbatim {@code invalidCodeValueErrorMsg}
 * ({@code "A valid value must be selected from the list."}).
 */
public class InvalidContractualCodeException extends BusinessException {

  public InvalidContractualCodeException() {
    super(HttpStatus.BAD_REQUEST, "invalidCodeValueErrorMsg");
  }
}
