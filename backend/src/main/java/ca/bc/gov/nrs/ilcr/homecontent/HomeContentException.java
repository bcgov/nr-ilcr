package ca.bc.gov.nrs.ilcr.homecontent;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Business failures for Home content editing (Story 24.2 / UC-CNT-001): a role's message record
 * missing at load/save (404, ERR-002) or a message exceeding the column cap (400). Carries a {@code
 * messages.properties} key the {@code GlobalExceptionHandler} resolves (AD-8). Blank-editor
 * rejections use {@code FieldValuesRequiredException} (all blanks reported together, FLD-001).
 */
public class HomeContentException extends BusinessException {

  private HomeContentException(HttpStatus status, String messageKey) {
    super(status, messageKey);
  }

  /** A role's message record does not exist (S10). */
  public static HomeContentException contentNotFound() {
    return new HomeContentException(HttpStatus.NOT_FOUND, "homeContentNotFoundErrorMsg");
  }

  /** A message exceeds the {@code MESSAGE_TEXT} column cap (4000). */
  public static HomeContentException tooLong() {
    return new HomeContentException(HttpStatus.BAD_REQUEST, "homeContentTooLongErrorMsg");
  }
}
