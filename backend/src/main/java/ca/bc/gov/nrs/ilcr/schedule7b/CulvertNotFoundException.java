package ca.bc.gov.nrs.ilcr.schedule7b;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 7B culvert PUT/DELETE targets a {@code CULVERT_REPORT_ID} that is not a
 * category-{@code '7'} culvert under the caller's mill/year (unknown id, or an IDOR attempt against
 * another mill's culvert). Maps to 404.
 */
public class CulvertNotFoundException extends BusinessException {

  public CulvertNotFoundException() {
    super(HttpStatus.NOT_FOUND, "culvertNotFoundErrorMsg");
  }
}
