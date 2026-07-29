package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 3 sub-page PUT/DELETE targets an {@code {id}} that is not an itemized item-124
 * (Other Acceptable) or item-38 (Included Unacceptable) row under the mill/year's Schedule 3 (Story
 * 4.4). Schedule3-local peer of the Schedule 1 exception (same bundle key). Maps to 404.
 */
public class OtherCostNotFoundException extends BusinessException {

  public OtherCostNotFoundException() {
    super(HttpStatus.NOT_FOUND, "otherCostNotFoundErrorMsg");
  }
}
