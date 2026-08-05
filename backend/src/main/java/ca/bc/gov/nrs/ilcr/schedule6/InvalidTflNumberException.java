package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a TFL-classified write carries a TFL number that — after the legacy leading-zero
 * alias normalization (BR-03) — does not resolve to an RMG for Interior Regions, or is missing
 * entirely. Maps to 400 with the verbatim legacy FLD-002 text ({@code tflNumberValidatorErrorMsg},
 * thrown by {@code ILCRTflNumberValidator} in legacy — the validator IS the lookup: validity means
 * "yields an RMG", not a shape rule).
 */
public class InvalidTflNumberException extends BusinessException {

  public InvalidTflNumberException() {
    super(HttpStatus.BAD_REQUEST, "tflNumberValidatorErrorMsg");
  }
}
