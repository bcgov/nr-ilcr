package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 6 edit targets a record id that is not a served road record under the
 * given mill/year — unknown, another mill/year's, or the general-comment placeholder (which is
 * excluded from {@code roadRecords[]}, so a client can never legitimately address it). Legacy
 * matched rows in an in-memory list with no not-found message, so the key text is a recorded
 * deviation (AD-8), mirroring {@code silvicultureLocationNotFoundErrorMsg}. Maps to 404. Distinct
 * from a stale-revision 409: the update path re-checks existence to tell the two apart (AC6).
 */
public class RoadRecordNotFoundException extends BusinessException {

  public RoadRecordNotFoundException() {
    super(HttpStatus.NOT_FOUND, "roadRecordNotFoundErrorMsg");
  }
}
