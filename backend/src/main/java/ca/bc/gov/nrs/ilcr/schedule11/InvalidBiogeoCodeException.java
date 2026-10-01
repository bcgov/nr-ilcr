package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 11 write carries a {@code biogeoclimaticCatalogueId} that does not resolve
 * to a {@code THE.BIOGEOCLIMATIC_CATALOGUE} row — the backend enforcement of the legacy BEC
 * autocomplete's {@code forceSelection="true"} (slice S16): a value must be a real catalogue entry,
 * not free text. Ported verbatim from the legacy {@code INVALID_BIOGEOCODE} mapping ({@code
 * invalidBiogeoCode}, legacy {@code messages.properties:132}). Maps to 400.
 */
public class InvalidBiogeoCodeException extends BusinessException {

  public InvalidBiogeoCodeException() {
    super(HttpStatus.BAD_REQUEST, "invalidBiogeoCode");
  }
}
