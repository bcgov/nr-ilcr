package ca.bc.gov.nrs.ilcr.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler that converts exceptions into RFC 7807 ProblemDetail
 * responses (application/problem+json).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  public GlobalExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * Handles ILCR business-rule exceptions (mill closed, schedule not found, …). Resolves the
   * exception's legacy message key to verbatim text via the message bundle (AD-8) and emits a
   * ProblemDetail using the exception's status.
   *
   * @param ex the business exception carrying status + message key
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with the exception's status
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusinessException(
      BusinessException ex, HttpServletRequest request) {
    // 4xx business outcomes (mill closed, schedule not found, not editable, stale revision) are
    // expected, client-facing results — log them at DEBUG so they don't spam the log. Reserve WARN
    // for genuine 5xx failures (e.g. a save that could not be persisted). Never log data (AD-11).
    if (ex.getStatus().is5xxServerError()) {
      log.warn("Business rule failure [{}]: {}", ex.getStatus(), ex.getMessageKey());
    } else {
      log.debug("Business rule rejection [{}]: {}", ex.getStatus(), ex.getMessageKey());
    }

    String detail = messageSource.getMessage(
        ex.getMessageKey(), null, ex.getMessageKey(), LocaleContextHolder.getLocale());

    ProblemDetail problem = ProblemDetail.forStatus(ex.getStatus());
    problem.setTitle(ex.getStatus().getReasonPhrase());
    problem.setDetail(detail);
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(ex.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles database integrity violations and returns a conflict problem response.
   *
   * @param ex the exception that was raised
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 409 status
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    // Log the specific cause server-side, but never return raw DB/constraint text to the client
    // (leaks schema/object names; AD-11). The client gets a generic message.
    log.warn("Data integrity violation: {}", extractConstraintMessage(ex), ex);

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    problem.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
    problem.setDetail("The request could not be completed due to a data conflict.");
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles bean-validation failures raised during request body binding.
   *
   * @param ex the validation exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 400 status
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    log.warn("Validation failed: {}", ex.getMessage());

    var errors = ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("; "));

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Validation Failed");
    problem.setDetail(errors.isEmpty() ? "One or more validation errors occurred." : errors);
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles constraint violations raised outside of request-body binding.
   *
   * @param ex the validation exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 400 status
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    log.warn("Constraint violations: {}", ex.getMessage());

    String detail = ex.getConstraintViolations().stream()
        .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
        .collect(Collectors.joining("; "));

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Validation Error");
    problem.setDetail(detail.isEmpty() ? "Constraint violation" : detail);
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles an unreadable/malformed request body. A non-numeric value on a typed numeric field
   * (e.g. {@code "cost": "abc"}) surfaces as a Jackson {@link InvalidFormatException}; we map it to
   * the verbatim legacy converter message (FLD-004 {@code costConverterErrorMsg} for a cost/whole
   * number, FLD-005 {@code volumeConverterErrorMsg} for a volume/decimal) per AD-8. Other malformed
   * bodies return a generic 400.
   *
   * @param ex the message-not-readable exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 400 status
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    Throwable cause = ex.getMostSpecificCause();
    log.warn("Unreadable request body: {}",
        cause == null ? "unknown" : cause.getClass().getSimpleName());

    // A non-numeric value on a typed numeric field surfaces as a Jackson mismatch whose message
    // names the target Java type. Map to the verbatim legacy converter text (AD-8) without a
    // compile-time dependency on jackson-databind's exception classes (runtime-only on this module).
    String causeMessage = cause == null ? "" : String.valueOf(cause.getMessage());
    String detail = "The request body is invalid.";
    String key = null;
    if (causeMessage.contains("java.math.BigDecimal")) {
      key = "volumeConverterErrorMsg";
    } else if (causeMessage.contains("java.lang.Integer")
        || causeMessage.contains("java.lang.Long")) {
      key = "costConverterErrorMsg";
    }
    if (key != null) {
      detail = messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Invalid Request Body");
    problem.setDetail(detail);
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles authorization denials from method security ({@code @PreAuthorize}). Without this
   * explicit handler an {@link AccessDeniedException} raised at the method layer would fall through
   * to the generic 500 handler instead of returning 403 (AD-7). {@code AuthorizationDeniedException}
   * extends {@link AccessDeniedException}, so this covers both.
   *
   * @param ex the access-denied exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 403 status
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied: {}", ex.getMessage());

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle(HttpStatus.FORBIDDEN.getReasonPhrase());
    problem.setDetail("You do not have permission to perform this action.");
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles a missing required request parameter (e.g. absent {@code millId}/{@code year}) and
   * returns a 400 problem response. Without this handler these fall through to the generic 500
   * handler (AD-4, slice S19).
   *
   * @param ex the missing-parameter exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 400 status
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingRequestParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    log.warn("Missing request parameter: {}", ex.getMessage());

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Missing Request Parameter");
    problem.setDetail("Required parameter '" + ex.getParameterName() + "' is missing.");
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles a request parameter that cannot be converted to the target type (e.g. non-numeric
   * {@code millId}) and returns a 400 problem response (AD-4, slice S19).
   *
   * @param ex the type-mismatch exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 400 status
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    log.warn("Parameter type mismatch: {}", ex.getMessage());

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Invalid Request Parameter");
    problem.setDetail("Parameter '" + ex.getName() + "' has an invalid value.");
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles {@link ResponseStatusException} instances raised by application code.
   *
   * @param ex the exception to translate
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response using the exception status
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> handleResponseStatusException(
      ResponseStatusException ex, HttpServletRequest request) {

    var status = ex.getStatusCode();

    if (status.is5xxServerError()) {
      log.error("ResponseStatusException: {}", ex.getMessage(), ex);
    } else {
      log.warn("ResponseStatusException: {}", ex.getMessage());
    }

    String title = HttpStatus.resolve(status.value()) != null
        ? HttpStatus.resolve(status.value()).getReasonPhrase()
        : status.toString();

    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(ex.getReason() != null ? ex.getReason() : ex.getMessage());
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles a request to an unmapped path / missing static resource. Returns a clean 404
   * {@link ProblemDetail} and logs a single WARN line (no stack trace) — without this, an unmapped
   * request (e.g. a stale client calling a removed endpoint) falls through to the generic handler and
   * spews a full ERROR stack trace on every hit.
   *
   * @param ex the no-resource-found exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 404 status
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResourceFound(
      NoResourceFoundException ex, HttpServletRequest request) {
    log.warn("No handler for {} {}", request.getMethod(), request.getRequestURI());

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
    problem.setDetail("The requested resource was not found.");
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles any uncaught exception and returns a generic internal-server-error response.
   *
   * @param ex the uncaught exception
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 500 status
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(
      Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception caught: {}", ex.getMessage(), ex);

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("Internal Server Error");
    problem.setDetail("An unexpected error occurred. Please contact support if this persists.");
    problem.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Attempts to extract the most useful message from a DataIntegrityViolationException.
   */
  private String extractConstraintMessage(DataIntegrityViolationException ex) {
    Throwable mostSpecific = ex.getMostSpecificCause();
    if (mostSpecific != null && mostSpecific.getMessage() != null) {
      return mostSpecific.getMessage();
    }
    if (ex.getMessage() != null) {
      return ex.getMessage();
    }
    return "A database constraint was violated.";
  }
}
