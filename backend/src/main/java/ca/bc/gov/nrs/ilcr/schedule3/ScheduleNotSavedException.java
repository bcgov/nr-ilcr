package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 3 write fails to persist (a {@code DataAccessException} from the repository),
 * mapped to the legacy ERR-001 (S17). The transaction rolls back and a retried PUT can succeed. Maps
 * to 500. Schedule3-local peer of the Schedule 1 exception (same bundle key).
 */
public class ScheduleNotSavedException extends BusinessException {

  public ScheduleNotSavedException() {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "scheduleNotSavedErrorMsg");
  }
}
