package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a schedule save/delete fails at the persistence layer (a repository {@code
 * DataAccessException}). Maps to 500 with the verbatim legacy {@code scheduleNotSavedErrorMsg}
 * ("Schedule could not be saved."). The {@code @Transactional} write boundary rolls back before
 * this surfaces, so a retried request can succeed.
 *
 * <p>Canonical shared copy (Story 29.11) — one definition for every schedule instead of per-module
 * duplicates. {@code extends BusinessException}, so the single base-type {@code @ExceptionHandler}
 * maps it unchanged.
 */
public class ScheduleNotSavedException extends BusinessException {
  public ScheduleNotSavedException() {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "scheduleNotSavedErrorMsg");
  }
}
