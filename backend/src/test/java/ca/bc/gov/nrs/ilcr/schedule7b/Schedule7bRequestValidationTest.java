package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.OnUpdate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bean-Validation contract for {@link CulvertRequest}, at the constraint layer rather than over HTTP.
 *
 * <p>Covers the boundaries the write ITs cannot reach cheaply, and pins three fixes that a plausible
 * "tidy-up" would undo: that a length carrying extra decimals is ACCEPTED (rounded on write, not
 * rejected), that span and rise report DISTINGUISHABLE messages, and that a negative
 * {@code revisionCount} is a validation failure rather than a phantom 409. Runs under surefire.
 *
 * <p>Messages are asserted as bundle KEY templates ({@code {someKey}}) — this validator is built
 * standalone, without the Spring {@code MessageSource} that resolves them in production, so the raw
 * template is what surfaces. That is deliberate: it pins which KEY each field uses, which is the thing
 * that can silently regress.
 */
@DisplayName("CulvertRequest — validation constraints (ranges, scale, byte cap, lock token)")
class Schedule7bRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void openValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  /** A valid culvert; each test overrides just the field under examination. */
  private static CulvertRequest request(
      Integer span, Integer rise, BigDecimal length, Integer pieces, String comments,
      Integer revision) {
    return new CulvertRequest("R", span, rise, length, pieces, 4000, 1500, comments, revision);
  }

  private static CulvertRequest valid() {
    return request(1200, 900, new BigDecimal("12.5"), 3, "ok", 0);
  }

  private Set<String> messagesFor(CulvertRequest request, Class<?>... groups) {
    return validator.validate(request, groups).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("The baseline request is valid in both groups")
  void baselineIsValid() {
    assertThat(messagesFor(valid(), Default.class)).isEmpty();
    assertThat(messagesFor(valid(), Default.class, OnUpdate.class)).isEmpty();
  }

  // ----- length: scale is NOT a constraint (regression guard for the @Digits trap) ----------------

  @Test
  @DisplayName("A length with a trailing zero is ACCEPTED — 12.50 is the same number as 12.5")
  void trailingZeroLengthIsAccepted() {
    // The bug this guards: @Digits(fraction = 1) reads BigDecimal.scale(), so 12.50 failed while the
    // identical 12.5 passed — and the message returned was the RANGE message, for a value inside the
    // range. Any client formatting to two decimals could not save a culvert at all.
    assertThat(messagesFor(request(1200, 900, new BigDecimal("12.50"), 3, "ok", 0), Default.class))
        .isEmpty();
    assertThat(messagesFor(request(1200, 900, new BigDecimal("12.500"), 3, "ok", 0), Default.class))
        .isEmpty();
  }

  @Test
  @DisplayName("A genuinely two-decimal length is ACCEPTED and rounded on write, as legacy did")
  void twoDecimalLengthIsAccepted() {
    // Legacy validated range only (f:validateDoubleRange) and let NUMBER(7,1) round 12.55 to 12.6.
    // The rounding is asserted in Schedule7bServiceTest.lengthScaleIsNormalisedOnWrite.
    assertThat(messagesFor(request(1200, 900, new BigDecimal("12.55"), 3, "ok", 0), Default.class))
        .isEmpty();
  }

  @Test
  @DisplayName("Length range endpoints hold: 0.0 and 999,999.9 in, 1,000,000.0 and -0.1 out")
  void lengthRangeIsEnforced() {
    assertThat(messagesFor(request(1200, 900, new BigDecimal("0.0"), 3, "ok", 0), Default.class))
        .isEmpty();
    assertThat(messagesFor(request(1200, 900, new BigDecimal("999999.9"), 3, "ok", 0), Default.class))
        .isEmpty();
    assertThat(messagesFor(request(1200, 900, new BigDecimal("1000000.0"), 3, "ok", 0), Default.class))
        .containsExactly("{culvertLengthValidatorErrorMsg}");
    assertThat(messagesFor(request(1200, 900, new BigDecimal("-0.1"), 3, "ok", 0), Default.class))
        .containsExactly("{culvertLengthValidatorErrorMsg}");
  }

  // ----- span and rise must be distinguishable ----------------------------------------------------

  @Test
  @DisplayName("Span and rise report DIFFERENT keys, so a failed save names the field that failed")
  void spanAndRiseAreDistinguishable() {
    assertThat(messagesFor(request(10000000, 900, null, 3, "ok", 0), Default.class))
        .containsExactly("{culvertSpanValidatorErrorMsg}");
    assertThat(messagesFor(request(1200, 10000000, null, 3, "ok", 0), Default.class))
        .containsExactly("{culvertRiseValidatorErrorMsg}");
    // Both wrong at once: two distinct messages, not one repeated. The 400 detail is a bare join with
    // no field names, so sharing a key left the reporter unable to tell which dimension failed.
    assertThat(messagesFor(request(-1, 10000000, null, 3, "ok", 0), Default.class))
        .containsExactlyInAnyOrder(
            "{culvertSpanValidatorErrorMsg}", "{culvertRiseValidatorErrorMsg}");
  }

  @Test
  @DisplayName("Span and rise range endpoints hold: 0 and 9,999,999 in, -1 and 10,000,000 out")
  void dimensionRangeEndpoints() {
    assertThat(messagesFor(request(0, 9999999, null, 3, "ok", 0), Default.class)).isEmpty();
    assertThat(messagesFor(request(-1, 900, null, 3, "ok", 0), Default.class))
        .containsExactly("{culvertSpanValidatorErrorMsg}");
  }

  // ----- piece count ------------------------------------------------------------------------------

  @Test
  @DisplayName("Piece count is required and bounded 1-9,999 — 0 is below the floor")
  void pieceCountIsRequiredAndBounded() {
    assertThat(messagesFor(request(1200, 900, null, 1, "ok", 0), Default.class)).isEmpty();
    assertThat(messagesFor(request(1200, 900, null, 9999, "ok", 0), Default.class)).isEmpty();
    assertThat(messagesFor(request(1200, 900, null, 0, "ok", 0), Default.class))
        .containsExactly("{culvertPieceCountValidatorErrorMsg}");
    assertThat(messagesFor(request(1200, 900, null, null, "ok", 0), Default.class))
        .containsExactly("{missingRequiredFieldMsg}");
  }

  @Test
  @DisplayName("Span, rise, length and comments are all OPTIONAL at save (only type + pieces required)")
  void onlyTypeAndPieceCountAreRequired() {
    assertThat(messagesFor(request(null, null, null, 3, null, 0), Default.class)).isEmpty();
  }

  // ----- comments: characters AND bytes -----------------------------------------------------------

  @Test
  @DisplayName("3,500 ASCII comment characters are accepted; 3,501 are not")
  void commentCharacterCapIsEnforced() {
    assertThat(messagesFor(request(1200, 900, null, 3, "x".repeat(3500), 0), Default.class))
        .isEmpty();
    assertThat(messagesFor(request(1200, 900, null, 3, "x".repeat(3501), 0), Default.class))
        .containsExactly("{commentsMaxLengthErrorMsg}");
  }

  @Test
  @DisplayName("A comment inside the CHARACTER cap but over the 4,000-BYTE column is rejected")
  void commentByteCapIsEnforced() {
    // 3,000 x 2-byte characters = 6,000 bytes: satisfies @Size(3500) but overflows VARCHAR2(4000
    // BYTE). Without the byte cap Oracle raised ORA-12899 and the reporter got an opaque 500
    // "Schedule could not be saved." with nothing pointing at the comment.
    String twoByteChars = "é".repeat(3000);
    assertThat(twoByteChars.length()).isLessThan(3500);
    assertThat(twoByteChars.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .hasSizeGreaterThan(4000);

    assertThat(messagesFor(request(1200, 900, null, 3, twoByteChars, 0), Default.class))
        .containsExactly("{commentsMaxLengthErrorMsg}");
  }

  @Test
  @DisplayName("A multibyte comment that fits BOTH caps is accepted")
  void multibyteCommentWithinBothCapsIsAccepted() {
    String twoByteChars = "é".repeat(1500);  // 1,500 chars / 3,000 bytes
    assertThat(messagesFor(request(1200, 900, null, 3, twoByteChars, 0), Default.class)).isEmpty();
  }

  @Test
  @DisplayName("An over-long ASCII comment reports ONE message, not the same key twice")
  void oversizedAsciiCommentReportsOneMessage() {
    // The character and byte caps share a key, so charMax makes the byte validator defer whenever
    // @Size already failed — otherwise the handler's "; " join produced the message twice.
    assertThat(validator.validate(
        request(1200, 900, null, 3, "x".repeat(5000), 0), Default.class)).hasSize(1);
  }

  // ----- revisionCount ---------------------------------------------------------------------------

  @Test
  @DisplayName("revisionCount is required only on UPDATE, and never negative")
  void revisionCountRules() {
    // Absent on create: fine. Absent on update: a clean 400.
    assertThat(messagesFor(request(1200, 900, null, 3, "ok", null), Default.class)).isEmpty();
    assertThat(messagesFor(request(1200, 900, null, 3, "ok", null), Default.class, OnUpdate.class))
        .containsExactly("{revisionCountRequiredErrorMsg}");

    // Negative in EITHER group: a never-issued token matched no row and surfaced as a 409
    // "someone else changed this row" for what is simply a malformed body.
    assertThat(messagesFor(request(1200, 900, null, 3, "ok", -1), Default.class))
        .containsExactly("{revisionCountRequiredErrorMsg}");
    assertThat(messagesFor(request(1200, 900, null, 3, "ok", -1), Default.class, OnUpdate.class))
        .containsExactly("{revisionCountRequiredErrorMsg}");

    assertThat(messagesFor(request(1200, 900, null, 3, "ok", 0), Default.class, OnUpdate.class))
        .isEmpty();
  }

  @Test
  @DisplayName("A blank culvert type is rejected as a required field")
  void typeIsRequired() {
    assertThat(validator.validate(
        new CulvertRequest("", 1200, 900, null, 3, 4000, 1500, "ok", 0), Default.class))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("{missingRequiredFieldMsg}");
  }

  @Test
  @DisplayName("Cost band endpoints hold at ±99,999,999")
  void costBandEndpoints() {
    assertThat(validator.validate(
        new CulvertRequest("R", null, null, null, 3, 99999999, -99999999, null, 0), Default.class))
        .isEmpty();
    assertThat(validator.validate(
        new CulvertRequest("R", null, null, null, 3, 100000000, 1500, null, 0), Default.class))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("{costValidatorErrorMsg}");
  }
}
