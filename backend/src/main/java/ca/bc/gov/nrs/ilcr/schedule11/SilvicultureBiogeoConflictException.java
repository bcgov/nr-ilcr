package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 11 create/edit would duplicate the delivery unique key {@code
 * BSRPT_BSRPT_UK_UK (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, BECBIOGEOCLIMATIC_CATALOGUE_ID,
 * LOCATION)} — the same integrity legacy surfaced by mapping a {@code ConstraintViolationException}
 * to {@code SILVICULTURE_UNIQUE_BIOGEOCODE} ({@code silvicultureBiogeoUniqueConstraint}, legacy
 * {@code messages.properties:168}). The service catches the {@link
 * org.springframework.dao.DataIntegrityViolationException} on the unique key and rethrows this so
 * the caller gets the verbatim biogeo-unique message rather than the generic data-conflict 409.
 * Maps to 409.
 */
public class SilvicultureBiogeoConflictException extends BusinessException {

  public SilvicultureBiogeoConflictException() {
    super(HttpStatus.CONFLICT, "silvicultureBiogeoUniqueConstraint");
  }
}
