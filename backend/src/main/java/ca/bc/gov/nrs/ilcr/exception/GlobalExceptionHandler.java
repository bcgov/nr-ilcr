package ca.bc.gov.nrs.ilcr.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Global exception handler that converts exceptions into RFC 7807 ProblemDetail responses
 * (application/problem+json).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /** Legacy JSF required-field bundle key, ported verbatim (Story 1.2, AD-8). */
  private static final String REQUIRED_FIELD_KEY = "javax.faces.component.UIInput.REQUIRED";

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

    // The exception's own arguments, never a hardcoded null — see the BusinessException
    // three-argument constructor for why a parameterized key must be resolved with them.
    String detail =
        messageSource.getMessage(
            ex.getMessageKey(),
            ex.getMessageArgs(),
            ex.getMessageKey(),
            LocaleContextHolder.getLocale());

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
    // Field names and constraint codes only — NEVER ex.getMessage(), which embeds "rejected value
    // [...]" for every field error (AD-11: no cost or comment data in logs). A rejected 20-culvert
    // batch used to write 20 rows of costs and comments here (PR #266 review).
    log.warn(
        "Validation failed on {}: {}",
        request.getRequestURI(),
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + "/" + error.getCode())
            .collect(Collectors.joining(", ")));

    var errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::describeFieldError)
            // Identical (row, message) pairs collapse rather than handing the reporter the same
            // sentence twice. Two annotations sharing a key on one field is already prevented at
            // source
            // (MaxByteLength.charMax); this is the backstop for any other pairing.
            .distinct()
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

    String detail =
        ex.getConstraintViolations().stream()
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
   * The first collection index in a bean-validation property path, e.g. {@code 6} for {@code
   * culverts[6].culvert.installCost}.
   */
  private static final Pattern INDEXED_PATH = Pattern.compile("\\[(\\d+)]");

  /**
   * The ONE batch list whose rows carry the legacy {@code Id: n - } label: {@code culverts}, the
   * list field of Schedule 7B's page-level Save ({@code CulvertSaveAllRequest}). Anchored at the
   * path root, so it matches {@code culverts[6].culvert.installCost} and nothing nested deeper.
   *
   * <p><strong>Scoped to one list rather than "any indexed path"</strong> because the label is a
   * legacy-fidelity detail of 7B's list form, not a house style. Every other batch body in the app
   * is indexed too — {@code lineItems} (Schedules 1, 3), {@code rows} (Schedules 1, 3, 5), {@code
   * categories} (Schedule 4) — and their legacy screens carry NO row label, so an unscoped prefix
   * silently rewrote their 400 wording. Their own acceptance tests pin the bare sentence and went
   * red the first time CI actually ran the ITs (PR #268: two Schedule 1, three Schedule 4, one
   * Schedule 5). Nothing caught it on {@code main}, whose Analysis job runs {@code verify} WITHOUT
   * {@code -Dskip.integration.tests=false} — the ITs are skipped there.
   *
   * <p>Matching the path root rather than the binding's object name is deliberate: Spring derives
   * the object name from the {@code @RequestBody} TYPE at runtime, but a hand-built {@link
   * FieldError} in a unit test names it whatever it likes, so an object-name test would pass in
   * production and silently do nothing under {@code GlobalExceptionHandlerTest}.
   */
  private static final Pattern ROW_LABELLED_LIST = Pattern.compile("^culverts\\[");

  /**
   * One field error's message, prefixed with the legacy row label when the failure came from an
   * INDEXED entry of {@link #ROW_LABELLED_LIST} — {@code "Id: 7 - Entered cost must be between …"}.
   *
   * <p>Without this, a page-level Save of N culverts whose row 7 carried an out-of-range install
   * cost answered with the bare sentence and no row identity, while the whole batch rolled back —
   * leaving the reporter to find the offending row by inspection (PR #266 review). The prefix is
   * the legacy list-row form: {@code validatorMessage="Id: #{obj.rowCounter} -
   * #{msg.costValidatorErrorMsg}"} ({@code schedule7B.xhtml:436-437,455-456}). The Add form carried
   * no prefix, and neither does a non-indexed path here, so the single-record POST/PUT wording is
   * unchanged.
   *
   * <p><strong>The number is the 1-based batch index, which equals the legacy {@code rowCounter}
   * only because the batch endpoints take the WHOLE schedule in list order</strong> (an empty list
   * is rejected, and the page sends every row). A client posting a subset would get its own
   * position back, not the row's ordinal on screen. Resolving the true {@code rowCounter} needs
   * domain state this handler deliberately has no access to; if a partial-batch caller ever
   * appears, move the labelling into the schedule service.
   */
  private static String describeFieldError(FieldError error) {
    String message = Objects.requireNonNullElse(error.getDefaultMessage(), "");
    if (!ROW_LABELLED_LIST.matcher(error.getField()).find()) {
      return message;
    }
    Matcher indexed = INDEXED_PATH.matcher(error.getField());
    if (!indexed.find()) {
      return message;
    }
    return "Id: " + (Integer.parseInt(indexed.group(1)) + 1) + " - " + message;
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
    log.warn(
        "Unreadable request body: {}",
        cause == null ? "unknown" : cause.getClass().getSimpleName());

    // A non-numeric value on a typed numeric field surfaces as a Jackson mismatch whose message
    // names the target Java type. Map to the verbatim legacy converter text (AD-8) without a
    // compile-time dependency on jackson-databind's exception classes (runtime-only on this
    // module).
    String causeMessage = cause == null ? "" : String.valueOf(cause.getMessage());
    String detail = "The request body is invalid.";
    // Field-name overrides come FIRST, because the type-based fallback below can only guess from
    // the target Java type and so answers "Entered cost is invalid." for ANY Integer field. That
    // was right while every Integer on the wire was a cost, and wrong the moment Schedule 7B put
    // three non-cost Integer fields (span, rise, piece count) on one form: a mistyped span told the
    // reporter their COST was invalid and sent them hunting the wrong input. Legacy named the field
    // the reporter actually typed in. Deliberately keyed on names unique across the app — `length`
    // is NOT listed, because Schedule 7A's BridgeRequest also has one and changing a shipped
    // schedule's message is not this change's business (recorded in deferred-work.md).
    String key = converterKeyForField(causeMessage);
    if (key == null) {
      if (causeMessage.contains("java.math.BigDecimal")) {
        key = VOLUME_CONVERTER;
      } else if (causeMessage.contains("java.lang.Integer")
          || causeMessage.contains("java.lang.Long")) {
        key = "costConverterErrorMsg";
      }
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
   * The converter message key for a field the type-based fallback would mis-describe, or {@code
   * null} when no override applies.
   *
   * <p>Jackson names the offending property in its exception message (…{@code ["spanSize"]}…),
   * which is matched here rather than taking a compile-time dependency on {@code
   * jackson-databind}'s exception types — the same constraint the type matching above works around.
   * Only field names that are UNAMBIGUOUS across the whole app belong here.
   *
   * @param causeMessage the most-specific cause's message
   * @return a bundle key, or {@code null} to fall back to the type-based mapping
   */
  private static String converterKeyForField(String causeMessage) {
    return CONVERTER_KEYS_BY_TARGET.entrySet().stream()
        .filter(entry -> causeMessage.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  /** Shared by the five Schedule 10 material percentages, which all fail identically. */
  private static final String PERCENTAGE_CONVERTER = "percentageConverterErrorMsg";

  /** Shared by every volume field, Schedule 10's two haul volumes included. */
  private static final String VOLUME_CONVERTER = "volumeConverterErrorMsg";

  /**
   * Converter message keys scoped to {@code DeclaringType["property"]}, matched against the
   * reference chain Jackson puts in its exception message (…{@code CulvertRequest["spanSize"]}).
   *
   * <p>Scoped by TYPE, not by bare property name (PR #266 review). Matching {@code "spanSize"}
   * alone worked only while that name was unique across every DTO in the app, and the guard for
   * that lived in a comment — so the next request record to declare a {@code spanSize} would have
   * silently inherited culvert wording with nothing failing. Keying on the owning type costs the
   * same lookup and makes the constraint structural: a second declarer gets the type-based fallback
   * until someone adds its own entry here, and a per-schedule {@code length} becomes expressible
   * (7A's {@code BridgeRequest} declares one too, which is why the bare-name form had to leave
   * {@code length} out entirely — see {@code deferred-work.md}).
   *
   * <p>The chain is matched as a substring, so it resolves the same for a single-record body
   * ({@code CulvertRequest["spanSize"]}) and for a batch entry, where Jackson prefixes the
   * collection hops ({@code CulvertSaveAllRequest["culverts"]->…->CulvertRequest["spanSize"]}).
   */
  private static final Map<String, String> CONVERTER_KEYS_BY_TARGET =
      Map.ofEntries(
          Map.entry("CulvertRequest[\"spanSize\"]", "culvertSpanConverterErrorMsg"),
          Map.entry("CulvertRequest[\"riseSize\"]", "culvertRiseConverterErrorMsg"),
          Map.entry("CulvertRequest[\"culvertPieceCount\"]", "culvertPieceCountConverterErrorMsg"),
          // Schedule 10 (code review 2026-08-18). Without these, every malformed Integer on a road
          // detail — a percentage or a haul volume — fell through to the type default and told the
          // reporter their COST was invalid.
          Map.entry("RoadDetailRequest[\"sideSlopePct\"]", "sideSlopePercentageConverterErrorMsg"),
          Map.entry("RoadDetailRequest[\"endHaulVolume\"]", VOLUME_CONVERTER),
          Map.entry("RoadDetailRequest[\"overlandVolume\"]", VOLUME_CONVERTER),
          Map.entry("MaterialCompositionRequest[\"solidRockPct\"]", PERCENTAGE_CONVERTER),
          Map.entry("MaterialCompositionRequest[\"rippableRockPct\"]", PERCENTAGE_CONVERTER),
          Map.entry("MaterialCompositionRequest[\"coarsePct\"]", PERCENTAGE_CONVERTER),
          Map.entry("MaterialCompositionRequest[\"finePct\"]", PERCENTAGE_CONVERTER),
          Map.entry("MaterialCompositionRequest[\"organicPct\"]", PERCENTAGE_CONVERTER));

  /**
   * Handles authorization denials from method security ({@code @PreAuthorize}). Without this
   * explicit handler an {@link AccessDeniedException} raised at the method layer would fall through
   * to the generic 500 handler instead of returning 403 (AD-7). {@code
   * AuthorizationDeniedException} extends {@link AccessDeniedException}, so this covers both.
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
   * Handles missing/blank/invalid required selection fields (UC-SEC-001 S04/S05/S08, Story 1.2).
   * Resolves the verbatim legacy required-field template ({@code
   * javax.faces.component.UIInput.REQUIRED = "{0}: Value is required."}) once per field — passing
   * the field label as the {@code {0}} argument (parameterized keys MUST get an args array) — and
   * returns ALL field messages together on one 400: {@code detail} joins the texts and the {@code
   * messages} extension property carries each {@code {key, text}} pair (the pinned shape the
   * frontend renders per field, mirroring {@code MessageInfo}).
   *
   * @param ex the exception carrying the ordered missing-field labels
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} response with HTTP 400 status and a {@code messages} array
   */
  @ExceptionHandler(FieldValuesRequiredException.class)
  public ResponseEntity<ProblemDetail> handleFieldValuesRequired(
      FieldValuesRequiredException ex, HttpServletRequest request) {
    log.debug("Required selection fields missing: {}", ex.getFieldLabels());

    var messages =
        ex.getFieldLabels().stream()
            .map(
                label ->
                    new FieldMessage(
                        REQUIRED_FIELD_KEY,
                        messageSource.getMessage(
                            REQUIRED_FIELD_KEY,
                            new Object[] {label},
                            REQUIRED_FIELD_KEY,
                            LocaleContextHolder.getLocale())))
            .toList();

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Validation Failed");
    problem.setDetail(messages.stream().map(FieldMessage::text).collect(Collectors.joining("; ")));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("messages", messages);

    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Handles a business rejection carrying MORE THAN ONE legacy message (AD-8) — resolves each key
   * to its verbatim text and returns them together in the {@code messages} extension (same shape as
   * the required-field handler), with the exception's status. Used e.g. by the reporting-year open
   * when zero active mills exist (INF-001 + ERR-002 together).
   *
   * @param ex the exception carrying the ordered message keys and target status
   * @param request the current HTTP request
   * @return a {@link ProblemDetail} with the exception's status and a {@code messages} array
   */
  @ExceptionHandler(MultiMessageException.class)
  public ResponseEntity<ProblemDetail> handleMultiMessage(
      MultiMessageException ex, HttpServletRequest request) {
    log.info("Multi-message business rejection ({}): {}", ex.getStatus(), ex.getMessageKeys());

    var messages =
        ex.getMessageKeys().stream()
            .map(
                key ->
                    new FieldMessage(
                        key,
                        messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale())))
            .toList();

    ProblemDetail problem = ProblemDetail.forStatus(ex.getStatus());
    problem.setTitle(ex.getStatus().getReasonPhrase());
    problem.setDetail(messages.stream().map(FieldMessage::text).collect(Collectors.joining("; ")));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("messages", messages);

    return ResponseEntity.status(ex.getStatus())
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

    String title =
        HttpStatus.resolve(status.value()) != null
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
   * Handles a request to an unmapped path / missing static resource. Returns a clean 404 {@link
   * ProblemDetail} and logs a single WARN line (no stack trace) — without this, an unmapped request
   * (e.g. a stale client calling a removed endpoint) falls through to the generic handler and spews
   * a full ERROR stack trace on every hit.
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
   * One resolved field-level message on a 400 {@code messages} array: the legacy bundle key plus
   * its resolved verbatim text (mirrors {@code MessageInfo}; pinned in Story 1.2's wire contract).
   *
   * @param key the legacy bundle key (e.g. {@code javax.faces.component.UIInput.REQUIRED})
   * @param text the resolved verbatim text (e.g. {@code Mill: Value is required.})
   */
  public record FieldMessage(String key, String text) {}

  /** Attempts to extract the most useful message from a DataIntegrityViolationException. */
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
