package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a save would give a camp a name another camp in the same mill/year already holds,
 * case-insensitively (BR-02, ERR-001). Maps to 409 with legacy's verbatim {@code Camp name already
 * exists.} ({@code messages.properties:179}).
 *
 * <p><strong>This must stay a PRE-CHECK, never a caught constraint violation:</strong> there is no
 * unique index on {@code CAMP_NAME} anywhere in delivery — {@code CAMP_REPORT} carries only its PK,
 * the composite {@code ILCR_REPORT_CATEGORY} FK, and eleven {@code IS NOT NULL} checks (Task 1
 * gates (i)/(vi)). Nothing in the database would reject the duplicate, so a {@code
 * DataIntegrityViolationException} handler could never fire and the duplicate would simply persist.
 *
 * <p>409 rather than 400 because the request is well-formed and the conflict is with server state
 * the client cannot see — the same reasoning the stale-revision 409 uses.
 */
public class CampNameConflictException extends BusinessException {

  public CampNameConflictException() {
    super(HttpStatus.CONFLICT, "campAlreadyExists");
  }
}
