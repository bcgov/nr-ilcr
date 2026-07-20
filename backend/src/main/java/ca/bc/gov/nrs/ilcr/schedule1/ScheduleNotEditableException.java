package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a write (PUT/DELETE) targets a schedule whose Schedules 1–10 track is not in Draft
 * (AC4 / slice S22 write half). Server-side Draft gate (AD-9) — legacy enforced this only by
 * disabling the UI, so no legacy message text exists; the key text is a recorded deviation (AD-8).
 * Maps to 409.
 */
public class ScheduleNotEditableException extends BusinessException {

  public ScheduleNotEditableException() {
    super(HttpStatus.CONFLICT, "scheduleNotEditableErrorMsg");
  }
}
