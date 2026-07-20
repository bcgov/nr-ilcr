package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for ILCR business-rule exceptions that carry a target HTTP status and a LEGACY message-bundle
 * key (AD-8). {@link GlobalExceptionHandler} resolves the key to verbatim text and emits a
 * ProblemDetail — message text never lives in code.
 */
public abstract class BusinessException extends RuntimeException {

  private final transient HttpStatus status;
  private final transient String messageKey;

  protected BusinessException(HttpStatus status, String messageKey) {
    super(messageKey);
    this.status = status;
    this.messageKey = messageKey;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getMessageKey() {
    return messageKey;
  }
}
