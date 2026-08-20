package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a road-detail write names a BEC classification the cross-reference does not offer, or
 * when that classification plus the RSMR class resolves to no moisture-code pair at all. Maps to
 * 400 with legacy's own {@code invalidBiogeoCode} text.
 *
 * <p>Two distinct causes, one message, because legacy uses this same string for both: its Check
 * Status emits it when a stored BEC id is outside the allowable filtered list, and its autocomplete
 * refuses a typed value that resolves to nothing.
 *
 * <p>The zero-candidate case matters for the write path specifically: the two moisture codes are
 * derived from this classification and the RSMR class, and both target columns are {@code NOT NULL}
 * with enabled foreign keys. Failing here with a clear 400 is the alternative to letting the insert
 * reach Oracle and return an opaque constraint violation.
 *
 * <p>Extends {@link BusinessException} directly rather than {@code
 * InvalidClassificationCodeException}, even though a BEC code IS a kind of classification code. The
 * subtype relationship bought nothing — nothing catches either type, both map to 400, and the only
 * thing it required was a {@code protected} constructor on the parent existing solely to pass this
 * class's message key through. It also pushed the inheritance depth to six. Flattened at code
 * review follow-up 2026-08-18.
 */
public class InvalidBecClassificationException extends BusinessException {

  public InvalidBecClassificationException() {
    super(HttpStatus.BAD_REQUEST, "invalidBiogeoCode");
  }
}
