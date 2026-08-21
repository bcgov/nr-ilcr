package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a mutating request carries a {@code revisionCount} that no longer matches the stored
 * {@code ILCR_REPORT_SUMMARY.REVISION_COUNT} — a lost-update conflict (AR11 optimistic lock):
 * another user saved in between, so the optimistic-lock bump matched 0 rows. Maps to 409 with the
 * verbatim {@code scheduleRevisionConflictErrorMsg}. No legacy message text existed (legacy used
 * Hibernate {@code @Version}), so the key text is a recorded deviation (AD-8).
 *
 * <p>Canonical shared copy (Story 29.11) — one definition for every schedule instead of per-module
 * duplicates. {@code extends BusinessException}, so the single base-type {@code @ExceptionHandler}
 * maps it unchanged.
 */
public class StaleRevisionException extends BusinessException {
  public StaleRevisionException() {
    super(HttpStatus.CONFLICT, "scheduleRevisionConflictErrorMsg");
  }
}
