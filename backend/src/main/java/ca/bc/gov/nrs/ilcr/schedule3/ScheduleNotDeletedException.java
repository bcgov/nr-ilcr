package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 3 delete fails to persist (a {@code DataAccessException} from the
 * repository). Distinct from {@link ScheduleNotSavedException} so a failed delete is unambiguous in
 * logs and handlers (the "not saved" name reads as an insert/update problem and would mislead
 * diagnosis). The transaction rolls back and a retried DELETE can succeed. Maps to 500.
 */
public class ScheduleNotDeletedException extends BusinessException {

  public ScheduleNotDeletedException() {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "scheduleNotDeletedErrorMsg");
  }
}
