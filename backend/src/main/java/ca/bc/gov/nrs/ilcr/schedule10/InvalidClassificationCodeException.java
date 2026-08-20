package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 10 write carries a classification code that no reference row can satisfy —
 * an unknown or non-offerable code, or a TSA code too wide for {@code TSA_NUMBER}. Maps to 400.
 *
 * <p>Legacy resolved codes through a cache and silently stored a NULL on a miss. That is not
 * reproducible here: all five classification columns carry enabled foreign keys, so an unknown code
 * cannot be silently nulled — it would raise {@code ORA-02291} and surface as an opaque 500. A 400
 * naming the field is the only sound behaviour, and it is recorded as a deliberate departure.
 *
 * <p>An unchanged code that has since expired is deliberately exempt: a retired code must not
 * permanently block re-saving a row that already carries it.
 */
public class InvalidClassificationCodeException extends BusinessException {

  public InvalidClassificationCodeException() {
    super(HttpStatus.BAD_REQUEST, "invalidCodeValueErrorMsg");
  }
}
