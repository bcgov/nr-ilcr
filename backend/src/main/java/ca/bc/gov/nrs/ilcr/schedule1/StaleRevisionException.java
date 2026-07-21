package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a PUT carries a {@code revisionCount} that no longer matches the stored
 * {@code ILCR_REPORT_SUMMARY.REVISION_COUNT} — a lost-update conflict (AC7 / AR11 optimistic lock).
 * Legacy enforced this via Hibernate {@code @Version}; no legacy message text exists, so the key
 * text is a recorded deviation (AD-8). Maps to 409.
 */
public class StaleRevisionException extends BusinessException {

  public StaleRevisionException() {
    super(HttpStatus.CONFLICT, "scheduleRevisionConflictErrorMsg");
  }
}
