package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a write (PUT/DELETE) targets a schedule whose Schedules 1–10 track is not in Draft —
 * the server-side Draft gate (AD-9). Legacy enforced this only by disabling the UI, so no legacy
 * message text exists and the key text is a recorded deviation (AD-8). Maps to 409.
 *
 * <p>Canonical shared copy (Story 29.11) — one definition for every schedule instead of per-module
 * duplicates. {@code extends BusinessException}, so the single base-type {@code @ExceptionHandler}
 * maps it unchanged.
 */
public class ScheduleNotEditableException extends BusinessException {
  public ScheduleNotEditableException() {
    super(HttpStatus.CONFLICT, "scheduleNotEditableErrorMsg");
  }
}
