package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 10 road-detail write targets a {@code ROAD_CONSTRUCTION_REPRT_DTL_ID} that
 * does not hang off the addressed page under the caller's mill and year. Maps to 404.
 *
 * <p>The parent page is part of the identity check, not just the URL: a road detail id that exists
 * but belongs to a different page must not be editable through another page's path.
 */
public class RoadDetailNotFoundException extends BusinessException {

  public RoadDetailNotFoundException() {
    super(HttpStatus.NOT_FOUND, "roadDetailNotFoundErrorMsg");
  }
}
