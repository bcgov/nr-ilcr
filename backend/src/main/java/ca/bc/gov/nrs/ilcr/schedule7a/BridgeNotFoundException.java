package ca.bc.gov.nrs.ilcr.schedule7a;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 7A bridge PUT/DELETE targets a {@code BRIDGE_REPORT_ID} that is not a
 * category-{@code '7'} bridge under the caller's mill/year (unknown id, or an IDOR attempt against
 * another mill's bridge). Maps to 404.
 */
public class BridgeNotFoundException extends BusinessException {

  public BridgeNotFoundException() {
    super(HttpStatus.NOT_FOUND, "bridgeNotFoundErrorMsg");
  }
}
