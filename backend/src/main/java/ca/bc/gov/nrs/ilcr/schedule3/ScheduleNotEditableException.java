package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 3 write (PUT/DELETE) targets a schedule whose Schedules 1–10 track is not in
 * Draft (S15 write half). Server-side Draft gate (AD-9); legacy enforced this only via a disabled UI,
 * so the key text is a recorded deviation (AD-8). Maps to 409. Schedule3-local peer of the Schedule 1
 * exception (same bundle key).
 */
public class ScheduleNotEditableException extends BusinessException {

  public ScheduleNotEditableException() {
    super(HttpStatus.CONFLICT, "scheduleNotEditableErrorMsg");
  }
}
