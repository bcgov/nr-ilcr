package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.OnUpdate;
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
 * Bean-Validation contract for {@link BridgeRequest}, at the constraint layer rather than over HTTP
 * — the Schedule 7A twin of {@code Schedule7bRequestValidationTest} and {@code
 * Schedule5ByteLengthTest}. Runs under surefire, which the analysis pipeline reads (the {@code *IT}
 * suite it does not).
 *
 * <p>Pins two classes of thing a plausible tidy-up would undo:
 *
 * <ul>
 *   <li>the TWELVE required fields (legacy {@code required="true"}, schedule7A.xhtml) and the ten
 *       costs being OPTIONAL at Save — legacy only flagged missing costs at Check Status, so
 *       tightening them would stop a reporter saving a partly-costed bridge;
 *   <li>the CHARACTER and BYTE caps on the two free-text fields. Both columns are BYTE-declared in
 *       delivery ({@code LOCATION_NAME VARCHAR2(30 BYTE)}, {@code COMMENTS VARCHAR2(4000 BYTE)},
 *       {@code char_used = 'B'}), so a character-only cap let multi-byte text through to an
 *       ORA-12899 that could only surface as an opaque 500.
 * </ul>
 *
 * <p>Messages are asserted as bundle KEY templates ({@code {someKey}}) — this validator is built
 * standalone, without the Spring {@code MessageSource} that resolves them in production, so the raw
 * template is what surfaces. That is deliberate: it pins which KEY each field uses, which is the
 * thing that can silently regress.
 */
@DisplayName("BridgeRequest — validation constraints (required set, ranges, byte caps, lock token)")
class Schedule7aRequestValidationTest {

  private static final String REQUIRED = "{missingRequiredFieldMsg}";
  private static final String COMMENTS_MSG = "{commentsMaxLengthErrorMsg}";
  private static final String LOCATION_MSG = "{bridgeLocationMaxLengthErrorMsg}";

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

  /** A complete, valid bridge; each test rebuilds it overriding the field under examination. */
  private static BridgeRequest valid() {
    return new BridgeRequest(
        "North Fork Bridge", "2020-06", "N", "STL", "WD", "CONC", "L100", 50,
        new BigDecimal("5.0"), new BigDecimal("20.0"), new BigDecimal("4.0"), 12,
        1000, 5000, 500, 800, 3000, 300, 400, 700, 200, 100, "Spans the north fork", 0);
  }

  private static BridgeRequest withLocation(String locationName) {
    BridgeRequest v = valid();
    return new BridgeRequest(
        locationName, v.builtDate(), v.constructionTypeCode(), v.superstructureTypeCode(),
        v.deckTypeCode(), v.abutmentTypeCode(), v.loadRatingCode(), v.lifeSpan(),
        v.abutmentHeight(), v.length(), v.width(), v.distance(),
        v.sitePlanCost(), v.superstructureMaterialCost(), v.superstructureDeliverCost(),
        v.superstructureInstallCost(), v.abutmentMaterialCost(), v.abutmentDeliverCost(),
        v.abutmentInstallCost(), v.approachCost(), v.afterInstallCost(), v.otherCost(),
        v.comments(), v.revisionCount());
  }

  private static BridgeRequest withComments(String comments) {
    BridgeRequest v = valid();
    return new BridgeRequest(
        v.locationName(), v.builtDate(), v.constructionTypeCode(), v.superstructureTypeCode(),
        v.deckTypeCode(), v.abutmentTypeCode(), v.loadRatingCode(), v.lifeSpan(),
        v.abutmentHeight(), v.length(), v.width(), v.distance(),
        v.sitePlanCost(), v.superstructureMaterialCost(), v.superstructureDeliverCost(),
        v.superstructureInstallCost(), v.abutmentMaterialCost(), v.abutmentDeliverCost(),
        v.abutmentInstallCost(), v.approachCost(), v.afterInstallCost(), v.otherCost(),
        comments, v.revisionCount());
  }

  private Set<String> messagesFor(BridgeRequest request, Class<?>... groups) {
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

  // ----- the two free-text byte caps ------------------------------------------------------------

  @Test
  @DisplayName("locationName: 30 ASCII characters pass; 30 multi-byte characters do NOT")
  void locationNameByteCap() {
    assertThat(messagesFor(withLocation("A".repeat(30)), Default.class)).isEmpty();
    assertThat(messagesFor(withLocation("A".repeat(31)), Default.class)).contains(LOCATION_MSG);
    // 30 CHARACTERS but 90 BYTES — inside @Size, past VARCHAR2(30 BYTE). Character-only validation
    // passed this straight through to ORA-12899.
    assertThat(messagesFor(withLocation("河".repeat(30)), Default.class)).contains(LOCATION_MSG);
  }

  @Test
  @DisplayName("comments: 3500 ASCII characters pass; 3500 multi-byte characters do NOT")
  void commentsByteCap() {
    assertThat(messagesFor(withComments("x".repeat(3500)), Default.class)).isEmpty();
    assertThat(messagesFor(withComments("x".repeat(3501)), Default.class)).contains(COMMENTS_MSG);
    // 2000 characters is inside the 3500-character cap but 6000 bytes, past VARCHAR2(4000 BYTE).
    assertThat(messagesFor(withComments("河".repeat(2000)), Default.class)).contains(COMMENTS_MSG);
  }

  @Test
  @DisplayName("Both free-text fields are optional-or-blank per legacy: comments may be null")
  void commentsMayBeAbsent() {
    assertThat(messagesFor(withComments(null), Default.class)).isEmpty();
  }

  @Test
  @DisplayName("locationName is required — blank is not a name")
  void locationNameRequired() {
    assertThat(messagesFor(withLocation(null), Default.class)).contains(REQUIRED);
    assertThat(messagesFor(withLocation("   "), Default.class)).contains(REQUIRED);
  }

  // ----- the required set vs the optional costs --------------------------------------------------

  @Test
  @DisplayName("All TWELVE attribute fields are required (legacy required=\"true\")")
  void twelveRequiredFields() {
    BridgeRequest empty = new BridgeRequest(
        null, null, null, null, null, null, null, null, null, null, null, null,
        1000, 5000, 500, 800, 3000, 300, 400, 700, 200, 100, "ok", 0);
    // One violation per missing field, and nothing else — the costs supplied above are fine.
    assertThat(validator.validate(empty, Default.class)).hasSize(12);
    assertThat(messagesFor(empty, Default.class)).containsExactly(REQUIRED);
  }

  @Test
  @DisplayName("The TEN costs are OPTIONAL at Save — only Check Status flags them (BR-08)")
  void costsAreOptional() {
    BridgeRequest noCosts = new BridgeRequest(
        "North Fork Bridge", "2020-06", "N", "STL", "WD", "CONC", "L100", 50,
        new BigDecimal("5.0"), new BigDecimal("20.0"), new BigDecimal("4.0"), 12,
        null, null, null, null, null, null, null, null, null, null, null, 0);
    assertThat(messagesFor(noCosts, Default.class)).isEmpty();
  }

  // ----- ranges (BR-04/BR-06) --------------------------------------------------------------

  @Test
  @DisplayName("Measurement and cost ranges reject out-of-range values with their own message keys")
  void rangesUseDistinctMessageKeys() {
    BridgeRequest outOfRange = new BridgeRequest(
        "North Fork Bridge", "2020-06", "N", "STL", "WD", "CONC", "L100", 1000,
        new BigDecimal("10000.0"), new BigDecimal("10000.0"), new BigDecimal("10000.0"), 10000,
        100000000, null, null, null, null, null, null, null, null, null, "ok", 0);
    assertThat(messagesFor(outOfRange, Default.class)).containsExactlyInAnyOrder(
        "{lifeSpanValidatorErrorMsg}",
        "{abutmentsHtValidatorErrorMsg}",
        "{bridgeLengthValidatorErrorMsg}",
        "{bridgeWidthValidatorErrorMsg}",
        "{bridgeDistanceValidatorErrorMsg}",
        "{costValidatorErrorMsg}");
  }

  @Test
  @DisplayName("Range boundaries are INCLUSIVE, and a negative cost is allowed (legacy)")
  void boundariesAreInclusive() {
    BridgeRequest atBounds = new BridgeRequest(
        "North Fork Bridge", "2020-06", "N", "STL", "WD", "CONC", "L100", 999,
        new BigDecimal("9999.9"), new BigDecimal("9999.9"), new BigDecimal("9999.9"), 9999,
        -99999999, 99999999, 0, 0, 0, 0, 0, 0, 0, 0, "ok", 0);
    assertThat(messagesFor(atBounds, Default.class)).isEmpty();
  }

  // ----- the optimistic-lock token ---------------------------------------------------------------

  @Test
  @DisplayName("revisionCount is required on UPDATE only, and ignored on create")
  void revisionCountGroupedOnUpdate() {
    BridgeRequest v = valid();
    BridgeRequest noRevision = new BridgeRequest(
        v.locationName(), v.builtDate(), v.constructionTypeCode(), v.superstructureTypeCode(),
        v.deckTypeCode(), v.abutmentTypeCode(), v.loadRatingCode(), v.lifeSpan(),
        v.abutmentHeight(), v.length(), v.width(), v.distance(),
        v.sitePlanCost(), v.superstructureMaterialCost(), v.superstructureDeliverCost(),
        v.superstructureInstallCost(), v.abutmentMaterialCost(), v.abutmentDeliverCost(),
        v.abutmentInstallCost(), v.approachCost(), v.afterInstallCost(), v.otherCost(),
        v.comments(), null);

    assertThat(messagesFor(noRevision, Default.class)).isEmpty();
    assertThat(messagesFor(noRevision, Default.class, OnUpdate.class)).contains(REQUIRED);
  }
}
