package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 1 save/delete fails at the persistence layer (AC5 / slice S23). Maps to
 * 500 with the verbatim legacy message {@code scheduleNotSavedErrorMsg} ("Schedule could not be
 * saved.", ERR-004). The {@code @Transactional} write boundary rolls back before this surfaces.
 */
public class ScheduleNotSavedException extends BusinessException {

  public ScheduleNotSavedException() {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "scheduleNotSavedErrorMsg");
  }
}
