package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a TFL-located page carries a TFL number the reference table does not hold, after the
 * missing-leading-zero aliases have been applied. Maps to 400 with legacy's verbatim validator text.
 *
 * <p>Because the accept set and the Road-Group-derivable set are the same 22 keys, a TFL that passes
 * this check always derives a Road Group — so the "unmapped TFL saves with a blank Road Group" state
 * is unreachable through a write, in this application and in legacy alike. It exists only in stored
 * data that predates or bypassed the screen.
 */
public class InvalidTflNumberException extends BusinessException {

  public InvalidTflNumberException() {
    super(HttpStatus.BAD_REQUEST, "tflNumberValidatorErrorMsg");
  }
}
