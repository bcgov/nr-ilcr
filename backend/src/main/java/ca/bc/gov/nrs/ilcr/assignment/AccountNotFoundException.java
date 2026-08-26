package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an account operation addresses a user who has no {@code ILCR_USER} row.
 *
 * <p>Only deactivation can reach it — activation provisions the missing row instead (a recorded
 * deviation) — and legacy could reach neither, because its screen only acted on listed users. Kept
 * distinct from {@link AssignmentNotFoundException} so a missing account is never reported as a
 * missing mill assignment.
 */
public class AccountNotFoundException extends BusinessException {

  public AccountNotFoundException() {
    super(HttpStatus.NOT_FOUND, "error.user.account.notfound");
  }
}
