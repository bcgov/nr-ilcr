package ca.bc.gov.nrs.ilcr.schedule7a;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 7A write carries a completion date that does not parse as a valid,
 * non-lenient {@code yyyy-MM} calendar year-month (legacy {@code f:convertDateTime
 * pattern="yyyy-MM"} / {@code ILCRBridgeBuiltDateValidator}, BR-05). Bean Validation cannot express
 * a non-lenient calendar parse, so the service performs it and raises this. Maps to 400 with the
 * verbatim legacy {@code bridgeDateformatErrorMsg}.
 */
public class BridgeDateFormatException extends BusinessException {

  public BridgeDateFormatException() {
    super(HttpStatus.BAD_REQUEST, "bridgeDateformatErrorMsg");
  }
}
