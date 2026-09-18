package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The extract could not be built — an owner read threw, the spool could not be written, a lookup
 * failed — after the selection had already passed the gate.
 *
 * <p>A {@link BusinessException} on purpose, even though nothing about it is a business rule: it is
 * the one route through which {@code GlobalExceptionHandler} resolves a bundle key, and the text
 * this failure must carry is legacy's {@code undefinedError} ("ILCR has found an unhandled
 * error/exception. Please refer to application log files."). A plain {@code RuntimeException} would
 * fall to the generic handler and its hardcoded English, which is not the legacy sentence.
 *
 * <p>Legacy split this into two texts by exception type — an {@code IOException} went to a
 * full-page {@code error.xhtml} with a second message, anything else stayed inline with this one. A
 * spool-first REST endpoint has exactly one pre-commit failure path, so both collapse here.
 *
 * <p>Nothing here names a mill, a year or a row (AD-11).
 */
public class DataExtractGenerationException extends BusinessException {

  /**
   * Wraps a build failure. The cause is kept for whoever catches this; the diagnostic log line is
   * written by the service before it throws, because the global handler logs only status and key.
   *
   * @param cause the failure
   */
  public DataExtractGenerationException(Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "undefinedError");
    initCause(cause);
  }
}
