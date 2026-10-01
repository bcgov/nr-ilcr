package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a TSA-classified write carries an area-type code too wide for {@code TSA_NUMBER
 * VARCHAR2(2)}. The DTO's {@code @Size(max = 3)} on {@code areaType} exists for the 3-character
 * literal {@code "TFL"}, so a 3-character NON-TFL code clears Bean Validation and would reach
 * Oracle as ORA-12899 — which the service's {@code catch (DataAccessException)} can only surface as
 * a 500. This closes that gap with the house 400 (code review 2026-08-04).
 *
 * <p>Maps to 400 {@code invalidCodeValueErrorMsg} ({@code A valid value must be selected from the
 * list.}) — the key § Validation rules row 2 designated for an unusable classification code, and
 * the same key/status Schedule 8 uses for its code guard. Kept schedule6-local rather than
 * importing {@code schedule8.Schedule8InvalidCodeException}: cross-schedule consolidation rides the
 * client-blessed consistency PR (deferred-work.md), not this story.
 *
 * <p>This is NOT the deviation (f) unknown-code guard, which stays skipped: Task 1 verified no FK
 * constrains the classification columns (only the composite {@code RM_RPT_ILCR_RCAT_FK}), so an
 * unknown-but-well-sized code is still stored verbatim. Only physical width is rejected here.
 */
public class InvalidClassificationCodeException extends BusinessException {

  public InvalidClassificationCodeException() {
    super(HttpStatus.BAD_REQUEST, "invalidCodeValueErrorMsg");
  }
}
