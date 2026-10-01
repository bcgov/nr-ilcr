package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for ILCR business-rule exceptions that carry a target HTTP status and a LEGACY
 * message-bundle key (AD-8). {@link GlobalExceptionHandler} resolves the key to verbatim text and
 * emits a ProblemDetail — message text never lives in code.
 */
public abstract class BusinessException extends RuntimeException {

  private final transient HttpStatus status;
  private final transient String messageKey;
  private final transient Object[] messageArgs;

  protected BusinessException(HttpStatus status, String messageKey) {
    this(status, messageKey, null);
  }

  /**
   * A rejection whose legacy message is parameterized.
   *
   * <p>Arguments matter beyond filling in blanks: a key resolved with none at all skips {@code
   * MessageFormat} entirely, so its {@code {0}} placeholders and its doubled {@code ''} quote
   * escapes would reach the caller literally. Any key carrying either MUST be raised with
   * arguments.
   *
   * @param status the HTTP status this rejection maps to
   * @param messageKey the legacy message-bundle key
   * @param messageArgs the {@code MessageFormat} arguments, or null when the key takes none
   */
  protected BusinessException(HttpStatus status, String messageKey, Object[] messageArgs) {
    super(messageKey);
    this.status = status;
    this.messageKey = messageKey;
    this.messageArgs = messageArgs == null ? null : messageArgs.clone();
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getMessageKey() {
    return messageKey;
  }

  /**
   * The message arguments, or null when the key takes none.
   *
   * @return a copy of the arguments, or null
   */
  public Object[] getMessageArgs() {
    return messageArgs == null ? null : messageArgs.clone();
  }
}
