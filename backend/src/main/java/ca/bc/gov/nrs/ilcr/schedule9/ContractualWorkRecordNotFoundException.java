package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an edit or delete targets a {@code CONTRACTUAL_WORK_REPORT_ID} that does not exist
 * under the requested mill/year/category — either genuinely absent, or belonging to a DIFFERENT
 * mill/year (the IDOR guard: legacy's delete loaded by primary key alone with no tenancy check).
 *
 * <p>404 rather than 403 deliberately: "that record exists but is not yours" would confirm another
 * licensee's data. Absent and foreign are indistinguishable from the outside. On an edit this is
 * the 404 half of the AR11 404-vs-409 disambiguation — a guarded {@code UPDATE} affecting zero rows
 * means either an unknown/foreign id (this) or a stale token ({@code StaleRevisionException}), and
 * only a scoped existence probe can tell them apart.
 *
 * <p>Legacy has no message for this case — the screen only ever passed ids it had just rendered —
 * so the key is new (the {@code campNotFoundErrorMsg} precedent). Maps to 404.
 */
public class ContractualWorkRecordNotFoundException extends BusinessException {

  public ContractualWorkRecordNotFoundException() {
    super(HttpStatus.NOT_FOUND, "contractualWorkRecordNotFoundErrorMsg");
  }
}
