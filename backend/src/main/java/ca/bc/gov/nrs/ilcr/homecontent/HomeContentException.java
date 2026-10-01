package ca.bc.gov.nrs.ilcr.homecontent;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Business failures for Home content editing (Story 24.2 / UC-CNT-001): a message exceeding the
 * column cap (400). Missing role rows are represented as empty content on read and created by the
 * administrator save, so partial legacy data remains repairable. Carries a {@code
 * messages.properties} key the {@code GlobalExceptionHandler} resolves (AD-8). Blank-editor
 * rejections use {@code FieldValuesRequiredException} (all blanks reported together, FLD-001).
 */
public class HomeContentException extends BusinessException {

  private HomeContentException(HttpStatus status, String messageKey) {
    super(status, messageKey);
  }

  /** Retained for callers that need the legacy not-found error key; Home reads no longer use it. */
  public static HomeContentException contentNotFound() {
    return new HomeContentException(HttpStatus.NOT_FOUND, "homeContentNotFoundErrorMsg");
  }

  /** A message exceeds the {@code MESSAGE_TEXT} column cap (4000). */
  public static HomeContentException tooLong() {
    return new HomeContentException(HttpStatus.BAD_REQUEST, "homeContentTooLongErrorMsg");
  }
}
