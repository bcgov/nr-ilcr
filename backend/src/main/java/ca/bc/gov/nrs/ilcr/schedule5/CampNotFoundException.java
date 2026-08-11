package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an edit or delete targets a camp id that does not exist under the requested mill/year
 * — either genuinely absent, or belonging to a DIFFERENT mill/year/category (deviation (M):
 * legacy's {@code deleteCampFromReport} loads by primary key alone with no tenancy check, {@code
 * Schedule5DAO.java:550}, which this port must not reproduce).
 *
 * <p>404 rather than 403 deliberately: telling a caller "that camp exists but is not yours" would
 * confirm the existence of another licensee's record. Absent and foreign are indistinguishable from
 * the outside.
 *
 * <p>On an edit, this is the 404 half of the AR11 404-vs-409 disambiguation — a guarded {@code
 * UPDATE} affecting zero rows means either an unknown/foreign id (this) or a stale token ({@code
 * StaleRevisionException}), and only a scoped existence probe can tell them apart.
 *
 * <p>Legacy has no message for this case at all: the screen only ever passed ids it had just
 * rendered, so the key is new — mirroring {@code roadRecordNotFoundErrorMsg} and {@code
 * silvicultureLocationNotFoundErrorMsg}.
 */
public class CampNotFoundException extends BusinessException {

  public CampNotFoundException() {
    super(HttpStatus.NOT_FOUND, "campNotFoundErrorMsg");
  }
}
