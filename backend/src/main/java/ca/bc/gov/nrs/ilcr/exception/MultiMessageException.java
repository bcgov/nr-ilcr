package ca.bc.gov.nrs.ilcr.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A business rejection that surfaces MORE THAN ONE legacy message at once (AD-8) — for the cases
 * where legacy emitted several bundle messages together for a single outcome. {@link
 * GlobalExceptionHandler} resolves each key to its verbatim text and returns them together in the
 * {@code messages} extension (the same {@code {key, text}} shape as {@link
 * FieldValuesRequiredException}), with the given status. Keys are non-parameterized (the handler
 * resolves them without arguments).
 */
public class MultiMessageException extends RuntimeException {

  private final transient HttpStatus status;
  private final transient List<String> messageKeys;

  /**
   * Constructs a new MultiMessageException with the given status and message keys.
   *
   * @param status the HTTP status to return
   * @param messageKeys the list of message keys to resolve
   */
  public MultiMessageException(HttpStatus status, List<String> messageKeys) {
    super("Business rejection: " + (messageKeys == null ? "none" : String.join(", ", messageKeys)));
    this.status = status;
    this.messageKeys = messageKeys == null ? List.of() : List.copyOf(messageKeys);
  }

  public HttpStatus getStatus() {
    return status;
  }

  public List<String> getMessageKeys() {
    return messageKeys;
  }
}
