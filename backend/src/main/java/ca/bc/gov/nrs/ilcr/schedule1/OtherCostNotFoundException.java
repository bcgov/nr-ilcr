package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an Other-Costs PUT/DELETE targets an {@code {id}} that is not an itemized item-19 row
 * under the mill/year's Schedule 1 (Story 2.4, AC7). Legacy matched rows in an in-memory list with
 * no not-found path, so the text is a recorded deviation (AD-8). Maps to 404.
 */
public class OtherCostNotFoundException extends BusinessException {

  public OtherCostNotFoundException() {
    super(HttpStatus.NOT_FOUND, "otherCostNotFoundErrorMsg");
  }
}
