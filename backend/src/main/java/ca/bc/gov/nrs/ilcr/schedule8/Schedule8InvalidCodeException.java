package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 8 write carries a code-table value (page support-centre / region / BEC /
 * TSA / TFL / supply-block, sample skid-type, or a rate row's cost-item / cost-type) that does not
 * resolve to a real reference row. Backend enforcement of the legacy autocomplete's "a value must
 * be selected from the list" — a bad code must be rejected with a 400, not silently persisted (a
 * rate row whose cost item is neither an addition nor a deduction would otherwise vanish from
 * {@code finalRate} on read). Mirrors the Schedule 11 {@code InvalidBiogeoCodeException} pattern.
 */
public class Schedule8InvalidCodeException extends BusinessException {

  public Schedule8InvalidCodeException() {
    super(HttpStatus.BAD_REQUEST, "invalidCodeValueErrorMsg");
  }
}
