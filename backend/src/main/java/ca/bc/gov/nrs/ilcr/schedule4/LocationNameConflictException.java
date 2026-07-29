package ca.bc.gov.nrs.ilcr.schedule4;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 4 location is saved with a name that duplicates another location for the
 * same mill/year, compared case-INSENSITIVELY and excluding the location's own family (Story 4.2,
 * S14 / BR-02, legacy {@code Schedule4MB.doesLocationNameExist}). Nothing is persisted. Carries the
 * verbatim legacy key {@code locationAlreadyExists} (ERR-002, AD-8) and maps to 409 Conflict — the
 * request is well-formed but collides with existing stored data (contrast the blank-name case, which
 * is a request-body 400 via {@code @NotBlank}).
 */
public class LocationNameConflictException extends BusinessException {

  public LocationNameConflictException() {
    super(HttpStatus.CONFLICT, "locationAlreadyExists");
  }
}
