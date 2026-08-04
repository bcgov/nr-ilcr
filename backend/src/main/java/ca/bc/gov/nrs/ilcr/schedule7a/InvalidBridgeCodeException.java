package ca.bc.gov.nrs.ilcr.schedule7a;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 7A write carries a code value (New/Used, superstructure, deck, abutment,
 * or load rating) that resolves to no row in its {@code THE.*_CODE} table (force-selection backend
 * enforcement — the legacy FK would have rejected it). Maps to 400.
 */
public class InvalidBridgeCodeException extends BusinessException {

  public InvalidBridgeCodeException() {
    super(HttpStatus.BAD_REQUEST, "invalidBridgeCodeErrorMsg");
  }
}
