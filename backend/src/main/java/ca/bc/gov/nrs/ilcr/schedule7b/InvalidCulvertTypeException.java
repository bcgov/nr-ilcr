package ca.bc.gov.nrs.ilcr.schedule7b;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 7B write carries a culvert type that resolves to no {@code
 * THE.ILCR_CULVERT_TYPE_CODE} row EFFECTIVE for the reporting year (force-selection backend
 * enforcement — the legacy FK plus the year-scoped lookup cache would have rejected it). Maps to
 * 400.
 */
public class InvalidCulvertTypeException extends BusinessException {

  public InvalidCulvertTypeException() {
    super(HttpStatus.BAD_REQUEST, "invalidCulvertTypeErrorMsg");
  }
}
