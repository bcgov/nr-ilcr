package ca.bc.gov.nrs.ilcr.exception;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A business rejection that surfaces MORE THAN ONE legacy message at once (AD-8) — for the cases
 * where legacy emitted several bundle messages together for a single outcome. {@link
 * GlobalExceptionHandler} resolves each key to its verbatim text and returns them together in the
 * {@code messages} extension (the same {@code {key, text}} shape as {@link
 * FieldValuesRequiredException}), with the given status. Each key may carry its own optional {@link
 * java.text.MessageFormat} arguments.
 */
public class MultiMessageException extends RuntimeException {

  private final transient HttpStatus status;
  private final transient List<String> messageKeys;
  private final transient List<Object[]> messageArguments;

  /**
   * Constructs a new MultiMessageException with the given status and message keys.
   *
   * @param status the HTTP status to return
   * @param messageKeys the list of message keys to resolve
   */
  public MultiMessageException(HttpStatus status, List<String> messageKeys) {
    this(status, messageKeys, new Object[0][]);
  }

  /**
   * Constructs a multi-message rejection where each key can have its own message arguments.
   *
   * @param status the HTTP status to return
   * @param messageKeys the ordered message keys to resolve
   * @param messageArguments arguments in the same order as {@code messageKeys}; omit trailing
   *     entries for keys without arguments
   */
  public MultiMessageException(
      HttpStatus status, List<String> messageKeys, Object[]... messageArguments) {
    super("Business rejection: " + (messageKeys == null ? "none" : String.join(", ", messageKeys)));
    this.status = status;
    this.messageKeys = messageKeys == null ? List.of() : List.copyOf(messageKeys);
    if (messageArguments != null && messageArguments.length > this.messageKeys.size()) {
      throw new IllegalArgumentException("More message argument sets than message keys");
    }
    this.messageArguments =
        messageArguments == null
            ? List.of()
            : Arrays.stream(messageArguments)
                .map(args -> args == null ? null : args.clone())
                .toList();
  }

  public HttpStatus getStatus() {
    return status;
  }

  public List<String> getMessageKeys() {
    return messageKeys;
  }

  /** Returns the arguments for the message at {@code index}, or {@code null} when it is plain. */
  public Object[] getMessageArgs(int index) {
    if (index >= messageArguments.size()) {
      return null;
    }
    Object[] args = messageArguments.get(index);
    return args == null ? null : args.clone();
  }
}
