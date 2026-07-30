package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 11 edit/delete targets a location id that is not a Schedule 11 row under
 * the given mill/year (unknown id, or an id belonging to another mill/year). Legacy matched rows in
 * an in-memory list with no not-found message, so the key text is a recorded deviation (AD-8),
 * mirroring {@code otherCostNotFoundErrorMsg}. Maps to 404. Distinct from a stale-revision 409: the
 * update path first re-checks existence to tell the two apart (AC7).
 */
public class SilvicultureLocationNotFoundException extends BusinessException {

  public SilvicultureLocationNotFoundException() {
    super(HttpStatus.NOT_FOUND, "silvicultureLocationNotFoundErrorMsg");
  }
}
