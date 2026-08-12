package ca.bc.gov.nrs.ilcr.codetable;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Business failures for code-table maintenance (Story 24.3 / UC-CODE-001): an unknown table key (404)
 * or a field/date-range validation rejection (400, FLD-001..005). Carries a {@code messages.properties}
 * key the {@code GlobalExceptionHandler} resolves to the client-facing ProblemDetail detail (AD-8).
 */
public class CodeTableException extends BusinessException {

  private CodeTableException(HttpStatus status, String messageKey) {
    super(status, messageKey);
  }

  /** The selected table key is not one of the maintainable code tables. */
  public static CodeTableException unknownTable() {
    return new CodeTableException(HttpStatus.NOT_FOUND, "codeTableNotFoundErrorMsg");
  }

  /** A required-field or date-range rejection (nothing is saved). */
  public static CodeTableException validation(String messageKey) {
    return new CodeTableException(HttpStatus.BAD_REQUEST, messageKey);
  }
}
