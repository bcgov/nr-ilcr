package ca.bc.gov.nrs.ilcr.schedule7b;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 7B page-level Save batch names the same culvert more than once. Left to
 * run, the second pass would meet the revision its own first pass had just bumped and surface as a
 * 409 stale-edit — telling the caller someone else changed the row when the request was simply
 * malformed. Maps to 400.
 */
public class DuplicateCulvertException extends BusinessException {

  public DuplicateCulvertException() {
    super(HttpStatus.BAD_REQUEST, "duplicateCulvertErrorMsg");
  }
}
