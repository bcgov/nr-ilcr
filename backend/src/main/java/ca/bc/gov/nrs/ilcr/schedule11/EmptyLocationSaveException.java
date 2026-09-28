package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 11 save carries neither a row to update nor a row to delete. Maps to 400.
 * The page never sends one — its Save is disabled while nothing is pending — so only a forged or
 * broken request reaches this; answering 200 would report "Data saved successfully" for a request
 * that saved nothing.
 */
public class EmptyLocationSaveException extends BusinessException {

  public EmptyLocationSaveException() {
    super(HttpStatus.BAD_REQUEST, "locationSaveEmptyErrorMsg");
  }
}
