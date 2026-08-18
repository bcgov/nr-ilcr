package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPageRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialCompositionRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetailRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.StabilizingRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGradeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Bean-Validation contract for the two Schedule 10 write requests, at the constraint layer rather
 * than over HTTP — the Schedule 10 twin of {@code Schedule7aRequestValidationTest}. Runs under
 * surefire, which the analysis pipeline reads (the {@code *IT} suite it does not).
 *
 * <p>The ITs prove each rejection answers 400 and persists nothing. What they cannot enumerate
 * cheaply is the field-to-KEY pairing, and that is exactly what silently regresses: every one of
 * these messages is a legacy byte sequence a reporter reads, and several neighbouring fields
 * deliberately use DIFFERENT keys for the same-looking failure.
 *
 * <p>Messages are asserted as bundle KEY templates ({@code {someKey}}) — this validator is built
 * standalone, without the Spring {@code MessageSource} that resolves them in production, so the raw
 * template is what surfaces. That is deliberate: it pins which KEY each field uses.
 *
 * <p>Four traps this class exists to hold shut:
 *
 * <ul>
 *   <li>{@code tsaOrTfl} must use {@code schedule10TsaOrTflRequiredErrorMsg}, NOT Schedule 6's
 *       {@code tsaOrTflRequiredErrorMsg} — that key reads "TSA or TFL: Value is required." and
 *       reusing it would ship the wrong bytes with nothing to catch it;
 *   <li>sub-grade {@code length} caps at 100 while additional-stabilizing {@code length} caps at
 *       999.999. The asymmetry is legacy's and is asserted from BOTH sides, because "fixing" the
 *       inconsistency is the obvious tidy-up;
 *   <li>the two cost bands carry distinct keys — non-negative costs use {@code
 *       costValidatorSchedule9ErrorMsg}, transfers use {@code costSize7ValidatorErrorMsg} and may go
 *       negative;
 *   <li>no {@code @Digits} anywhere: a scaled decimal such as {@code 12.50} must pass, because
 *       {@code @Digits} reads {@code BigDecimal.scale()} and would reject it while the numerically
 *       identical {@code 12.5} passed.
 * </ul>
 */
@DisplayName("Schedule 10 write requests — validation constraints, and which KEY each field uses")
class Schedule10RequestValidationTest {

  // The four FLD-005 required keys. These carry legacy's RESOLVED text — "Road Type: Value is
  // required." and so on — because the parameterised JSF template key cannot work on a Bean
  // Validation annotation: there are no positional arguments, so {0} reached the reporter literally
  // (code review 2026-08-18). Pinning the key per field is the point of this class.
  private static final String ROAD_TYPE_REQUIRED = "{roadTypeRequiredErrorMsg}";
  private static final String BEC_ZONE_REQUIRED = "{becZoneRequiredErrorMsg}";
  private static final String BALLAST_METHOD_REQUIRED = "{ballastMethodRequiredErrorMsg}";

  // Range keys are per BAND for dimensions, because legacy has no save-time text for them, and
  // legacy's OWN keys for percentages, which it does.
  private static final String RANGE_0_100 = "{rangeZeroToOneHundredErrorMsg}";
  private static final String RANGE_0_999_999 = "{rangeZeroTo999Point999ErrorMsg}";
  private static final String PERCENTAGE = "{percentageValidatorErrorMsg}";
  private static final String SIDE_SLOPE = "{sideSlopePercentageValidatorErrorMsg}";
  private static final String COST_NON_NEGATIVE = "{costValidatorSchedule9ErrorMsg}";
  private static final String COST_TRANSFER = "{costSize7ValidatorErrorMsg}";
  private static final String VOLUME = "{volumeValidatorErrorMsg}";
  private static final String REVISION_REQUIRED = "{revisionCountRequiredErrorMsg}";
  private static final String COMMENTS_MSG = "{commentsMaxLengthErrorMsg}";
  private static final String CODE_VALUE = "{invalidCodeValueErrorMsg}";

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

  private static Set<String> messagesFor(Object request, Class<?>... groups) {
    return validator.validate(request, groups).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  /**
   * Every violation message, MULTIPLICITY PRESERVED.
   *
   * <p>{@link #messagesFor} collapses to a {@code Set}, which is what a key-per-field assertion
   * wants — but it also means {@code containsExactly(SOME_KEY)} passes when two fields share a key
   * and only one of them is still constrained. Code review 2026-08-18 found exactly that: three tests
   * drove several fields out of range at once and would have survived deleting any one constraint.
   * Use this whenever more than one field is expected to fail.
   */
  private static List<String> allMessagesFor(Object request, Class<?>... groups) {
    return validator.validate(request, groups).stream()
        .map(ConstraintViolation::getMessage)
        .toList();
  }

  // ===== the construction page ====================================================================

  @Nested
  @DisplayName("ConstructionPageRequest")
  class Page {

    /** A complete, valid TSA-located page; each test rebuilds it overriding one field. */
    private ConstructionPageRequest valid() {
      return new ConstructionPageRequest("1", "27", "A", null, "North Division", "2024-06", 0);
    }

    private ConstructionPageRequest page(
        String region, String tsaOrTfl, String division, String period, Integer revision) {
      return new ConstructionPageRequest(
          region, tsaOrTfl, "A", null, division, period, revision);
    }

    @Test
    @DisplayName("The baseline page is valid in both groups")
    void baselineIsValid() {
      assertThat(messagesFor(valid(), Default.class)).isEmpty();
      assertThat(messagesFor(valid(), Default.class, OnUpdate.class)).isEmpty();
    }

    @Test
    @DisplayName("Region and TSA/TFL are required, each with its OWN verbatim key (FLD-001, FLD-002)")
    void requiredFieldsUseTheirOwnKeys() {
      assertThat(messagesFor(page(null, null, "North", "2024-06", 0), Default.class))
          .containsExactlyInAnyOrder(
              "{regionRequiredErrorMsg}", "{schedule10TsaOrTflRequiredErrorMsg}");
      // Blank is not a value.
      assertThat(messagesFor(page("  ", "  ", "North", "2024-06", 0), Default.class))
          .containsExactlyInAnyOrder(
              "{regionRequiredErrorMsg}", "{schedule10TsaOrTflRequiredErrorMsg}");
    }

    @Test
    @DisplayName("tsaOrTfl does NOT use Schedule 6's tsaOrTflRequiredErrorMsg decoy key")
    void tsaOrTflAvoidsTheDecoyKey() {
      // Schedule 6's key reads "TSA or TFL: Value is required."; Schedule 10's literal is
      // "TSA or TFL is required." Reusing the decoy would be invisible without this assertion.
      assertThat(messagesFor(page("1", null, "North", "2024-06", 0), Default.class))
          .doesNotContain("{tsaOrTflRequiredErrorMsg}")
          .contains("{schedule10TsaOrTflRequiredErrorMsg}");
    }

    @Test
    @DisplayName("divisionName caps at the COLUMN's 20, not the screen's 30")
    void divisionNameCapsAtColumnWidth() {
      assertThat(messagesFor(page("1", "27", "A".repeat(20), "2024-06", 0), Default.class))
          .isEmpty();
      assertThat(messagesFor(page("1", "27", "A".repeat(21), "2024-06", 0), Default.class))
          .containsExactly("{divisionNameMaxLengthErrorMsg}");
      // 30 is what schedule10.xhtml allows, and what raises ORA-12899 in legacy today.
      assertThat(messagesFor(page("1", "27", "A".repeat(30), "2024-06", 0), Default.class))
          .containsExactly("{divisionNameMaxLengthErrorMsg}");
    }

    @Test
    @DisplayName("divisionName and constructionPeriod are both OPTIONAL")
    void optionalFieldsMayBeAbsent() {
      assertThat(messagesFor(page("1", "27", null, null, 0), Default.class)).isEmpty();
    }

    @Test
    @DisplayName("constructionPeriod is strict YYYY-MM — legacy accepted 2024-1 and broke on re-read")
    void constructionPeriodIsStrict() {
      assertThat(messagesFor(page("1", "27", "North", "2024-01", 0), Default.class)).isEmpty();
      for (String bad : new String[] {"2024-1", "24-01", "2024/01", "2024-13-01", "not-a-date"}) {
        assertThat(messagesFor(page("1", "27", "North", bad, 0), Default.class))
            .as("period %s", bad)
            .containsExactly("{bridgeDateformatErrorMsg}");
      }
    }

    @Test
    @DisplayName("revisionCount is required on UPDATE only, and ignored on create")
    void revisionCountGroupedOnUpdate() {
      ConstructionPageRequest noToken = page("1", "27", "North", "2024-06", null);
      assertThat(messagesFor(noToken, Default.class)).isEmpty();
      assertThat(messagesFor(noToken, Default.class, OnUpdate.class))
          .containsExactly(REVISION_REQUIRED);
    }

    @Test
    @DisplayName("A negative revisionCount is a 400 in BOTH groups, never a coerced 409")
    void negativeRevisionIsRejectedOutright() {
      // Without the floor, -1 reaches the optimistic-lock UPDATE, matches no row, and surfaces as
      // "changed by another user" for what is simply a malformed body.
      assertThat(messagesFor(page("1", "27", "North", "2024-06", -1), Default.class))
          .containsExactly(REVISION_REQUIRED);
      assertThat(messagesFor(page("1", "27", "North", "2024-06", -1), Default.class, OnUpdate.class))
          .containsExactly(REVISION_REQUIRED);
    }

    @Test
    @DisplayName("Code widths are capped so a long code cannot reach Oracle as an opaque 500")
    void codeWidthsAreCapped() {
      assertThat(messagesFor(page("1".repeat(11), "27", "North", "2024-06", 0), Default.class))
          .containsExactly(CODE_VALUE);
      assertThat(messagesFor(page("1", "TFLX", "North", "2024-06", 0), Default.class))
          .containsExactly(CODE_VALUE);
      // tflNumberCode has its own key — the TFL branch's own message (FLD-011).
      assertThat(messagesFor(
          new ConstructionPageRequest("1", "TFL", null, "999", "North", "2024-06", 0),
          Default.class))
          .containsExactly("{tflNumberValidatorErrorMsg}");
    }
  }

  // ===== the road detail ==========================================================================

  @Nested
  @DisplayName("RoadDetailRequest")
  class Detail {

    private SubGradeRequest subGrade() {
      return new SubGradeRequest(
          new BigDecimal("12.500"), new BigDecimal("6.5"), 500000, 1000, -2000,
          100, 200, 300, 400, 500, 600);
    }

    private StabilizingRequest stabilizing() {
      return new StabilizingRequest(
          "C", "CRG", new BigDecimal("5.000"), new BigDecimal("6.0"), new BigDecimal("0.30"),
          new BigDecimal("12.0"), 20000, 500, -300);
    }

    private MaterialCompositionRequest material() {
      return new MaterialCompositionRequest(20, 20, 20, 20, 20);
    }

    private RoadDetailRequest valid() {
      return detail("Mainline 400", "L20", 8801, "SD", subGrade(), stabilizing(), material(), 0);
    }

    private RoadDetailRequest detail(
        String roadName, String lifetime, Integer becId, String rsmr,
        SubGradeRequest sub, StabilizingRequest stab, MaterialCompositionRequest mat,
        Integer revision) {
      return new RoadDetailRequest(
          roadName, lifetime, becId, rsmr, 45, "Y", sub, stab, mat,
          new BigDecimal("12.5"), 5000, new BigDecimal("3.0"), 2000, "ok", revision);
    }

    private RoadDetailRequest withSubGrade(SubGradeRequest sub) {
      return detail("Mainline 400", "L20", 8801, "SD", sub, stabilizing(), material(), 0);
    }

    private RoadDetailRequest withStabilizing(StabilizingRequest stab) {
      return detail("Mainline 400", "L20", 8801, "SD", subGrade(), stab, material(), 0);
    }

    @Test
    @DisplayName("The baseline road detail is valid in both groups")
    void baselineIsValid() {
      assertThat(messagesFor(valid(), Default.class)).isEmpty();
      assertThat(messagesFor(valid(), Default.class, OnUpdate.class)).isEmpty();
    }

    @Test
    @DisplayName("The FOUR required fields each use their own key (FLD-003, FLD-004, FLD-005)")
    void requiredFieldsUseTheirOwnKeys() {
      RoadDetailRequest empty =
          detail(null, null, null, null, subGrade(), stabilizing(), material(), 0);
      // FOUR fields, FOUR distinct keys. Every one carries legacy's resolved text: the two literals
      // legacy hardcodes in schedule10.xhtml (Road Name, RSMR Class) and the two the JSF template
      // renders from the component label (Road Type, BEC Zone). Sharing one parameterised key here is
      // what shipped a literal "{0}" to the reporter.
      assertThat(messagesFor(empty, Default.class)).containsExactlyInAnyOrder(
          "{roadNameRequiredErrorMsg}", "{rsmrClassRequiredErrorMsg}",
          ROAD_TYPE_REQUIRED, BEC_ZONE_REQUIRED);
      assertThat(validator.validate(empty, Default.class)).hasSize(4);
    }

    @Test
    @DisplayName("An omitted stabilizing substructure is REJECTED, not skipped")
    void nullStabilizingIsRejected() {
      // Bean Validation skips a null nested object, so @Valid alone let a client omit the whole
      // substructure and slip past the required ballast method code.
      assertThat(messagesFor(withStabilizing(null), Default.class))
          .containsExactly(BALLAST_METHOD_REQUIRED);
    }

    @Test
    @DisplayName("Nested constraints participate in the same 400")
    void nestedConstraintsParticipate() {
      SubGradeRequest overLength = new SubGradeRequest(
          new BigDecimal("101"), null, null, null, null, null, null, null, null, null, null);
      assertThat(messagesFor(withSubGrade(overLength), Default.class))
          .containsExactly(RANGE_0_100);
    }

    @Test
    @DisplayName("Sub-grade length caps at legacy's 100 while stabilizing length allows 999.999")
    void theLengthCapAsymmetryIsLegacys() {
      SubGradeRequest atCap = new SubGradeRequest(
          new BigDecimal("100"), null, null, null, null, null, null, null, null, null, null);
      SubGradeRequest overCap = new SubGradeRequest(
          new BigDecimal("100.001"), null, null, null, null, null, null, null, null, null, null);
      assertThat(messagesFor(withSubGrade(atCap), Default.class)).isEmpty();
      assertThat(messagesFor(withSubGrade(overCap), Default.class))
          .containsExactly(RANGE_0_100);

      // The other side of the asymmetry: the same magnitude is legal for stabilizing. Note the two
      // sides now carry DIFFERENT keys, because each renders its own real bounds — which is the
      // clearest possible statement that the caps genuinely differ.
      StabilizingRequest longStabilizing = new StabilizingRequest(
          "C", "CRG", new BigDecimal("999.999"), null, null, null, null, null, null);
      StabilizingRequest tooLong = new StabilizingRequest(
          "C", "CRG", new BigDecimal("1000"), null, null, null, null, null, null);
      assertThat(messagesFor(withStabilizing(longStabilizing), Default.class)).isEmpty();
      assertThat(messagesFor(withStabilizing(tooLong), Default.class))
          .containsExactly(RANGE_0_999_999);
    }

    @Test
    @DisplayName("The two cost bands use DIFFERENT keys, and only transfers may go negative")
    void costBandsAreDistinct() {
      SubGradeRequest negativeActual = new SubGradeRequest(
          null, null, -1, null, null, null, null, null, null, null, null);
      assertThat(messagesFor(withSubGrade(negativeActual), Default.class))
          .containsExactly(COST_NON_NEGATIVE);

      SubGradeRequest negativeTransfer = new SubGradeRequest(
          null, null, null, -9999999, -9999999, null, null, null, null, null, null);
      assertThat(messagesFor(withSubGrade(negativeTransfer), Default.class)).isEmpty();

      SubGradeRequest transferTooLow = new SubGradeRequest(
          null, null, null, -10000000, null, null, null, null, null, null, null);
      assertThat(messagesFor(withSubGrade(transferTooLow), Default.class))
          .containsExactly(COST_TRANSFER);

      // ALL SIX deductions driven out of range, with the count asserted — the earlier version drove
      // only lessBridges, so the other five constraints were unpinned (code review 2026-08-18).
      SubGradeRequest allSixDeductionsTooHigh = new SubGradeRequest(
          null, null, null, null, null,
          10000000, 10000000, 10000000, 10000000, 10000000, 10000000);
      assertThat(allMessagesFor(withSubGrade(allSixDeductionsTooHigh), Default.class))
          .hasSize(6)
          .containsOnly(COST_NON_NEGATIVE);
    }

    @Test
    @DisplayName("Every cost is OPTIONAL at Save — a blank clears the row, Check Status flags it")
    void costsAreOptional() {
      SubGradeRequest noCosts = new SubGradeRequest(
          null, null, null, null, null, null, null, null, null, null, null);
      StabilizingRequest methodOnly =
          new StabilizingRequest("N", null, null, null, null, null, null, null, null);
      RoadDetailRequest bare = new RoadDetailRequest(
          "Mainline 400", "L20", 8801, "SD", null, null, noCosts, methodOnly, null,
          null, null, null, null, null, 0);
      assertThat(messagesFor(bare, Default.class)).isEmpty();
    }

    @Test
    @DisplayName("No @Digits anywhere: a scaled decimal such as 12.50 passes")
    void scaledDecimalsPass() {
      // @Digits reads BigDecimal.scale(), so it would reject 12.50 while passing 12.5. Scale is
      // normalised on write instead.
      SubGradeRequest scaled = new SubGradeRequest(
          new BigDecimal("12.500"), new BigDecimal("6.50"), null, null, null,
          null, null, null, null, null, null);
      assertThat(messagesFor(withSubGrade(scaled), Default.class)).isEmpty();

      StabilizingRequest scaledDepth = new StabilizingRequest(
          "C", "CRG", new BigDecimal("5.000"), new BigDecimal("6.00"), new BigDecimal("0.30"),
          new BigDecimal("12.00"), null, null, null);
      assertThat(messagesFor(withStabilizing(scaledDepth), Default.class)).isEmpty();
    }

    @Test
    @DisplayName("Haul DISTANCES may be negative (legacy); haul VOLUMES may not")
    void haulSignRules() {
      RoadDetailRequest negativeDistances = new RoadDetailRequest(
          "Mainline 400", "L20", 8801, "SD", 45, "Y", subGrade(), stabilizing(), material(),
          new BigDecimal("-9999.9"), 0, new BigDecimal("-9999.9"), 0, "ok", 0);
      assertThat(messagesFor(negativeDistances, Default.class)).isEmpty();

      // BOTH volumes are driven negative and the count asserted, so deleting either constraint fails
      // this test. With a Set and containsExactly it passed on one (code review 2026-08-18).
      RoadDetailRequest negativeVolumes = new RoadDetailRequest(
          "Mainline 400", "L20", 8801, "SD", 45, "Y", subGrade(), stabilizing(), material(),
          null, -1, null, -1, "ok", 0);
      assertThat(allMessagesFor(negativeVolumes, Default.class))
          .hasSize(2)
          .containsOnly(VOLUME);
    }

    @Test
    @DisplayName("sideSlopePct and the five material percentages are 0–100")
    void percentagesAreBounded() {
      // Side slope has its OWN legacy key, distinct from the material percentages.
      assertThat(messagesFor(
          new RoadDetailRequest(
              "Mainline 400", "L20", 8801, "SD", 101, "Y", subGrade(), stabilizing(), material(),
              null, null, null, null, "ok", 0),
          Default.class))
          .containsExactly(SIDE_SLOPE);

      // ALL FIVE percentages driven out of range, and the count asserted. The earlier version drove
      // only two and used containsExactly on a Set, so it passed if either constraint were deleted —
      // and its @DisplayName claimed five (code review 2026-08-18).
      MaterialCompositionRequest allFiveBad =
          new MaterialCompositionRequest(-1, 101, -1, 101, 101);
      assertThat(allMessagesFor(
          detail("Mainline 400", "L20", 8801, "SD", subGrade(), stabilizing(), allFiveBad, 0),
          Default.class))
          .hasSize(5)
          .containsOnly(PERCENTAGE);
    }

    @Test
    @DisplayName("The five percentages are NOT summed at Save — only Check Status totals them")
    void percentagesAreNotSummedAtSave() {
      // Legacy saves any combination and reports the total only at Check Status. Enforcing 100 here
      // would stop a reporter saving a partly-classified road.
      MaterialCompositionRequest sumsTo300 = new MaterialCompositionRequest(60, 60, 60, 60, 60);
      assertThat(messagesFor(
          detail("Mainline 400", "L20", 8801, "SD", subGrade(), stabilizing(), sumsTo300, 0),
          Default.class))
          .isEmpty();
    }

    @Test
    @DisplayName("comments: 3500 ASCII characters pass; 3500 multi-byte characters do NOT")
    void commentsByteCap() {
      assertThat(messagesFor(withComments("x".repeat(3500)), Default.class)).isEmpty();
      assertThat(messagesFor(withComments("x".repeat(3501)), Default.class))
          .contains(COMMENTS_MSG);
      // 2000 characters is inside the 3500-character cap but 6000 bytes, past VARCHAR2(4000 BYTE).
      assertThat(messagesFor(withComments("河".repeat(2000)), Default.class))
          .contains(COMMENTS_MSG);
      assertThat(messagesFor(withComments(null), Default.class)).isEmpty();
    }

    private RoadDetailRequest withComments(String comments) {
      return new RoadDetailRequest(
          "Mainline 400", "L20", 8801, "SD", 45, "Y", subGrade(), stabilizing(), material(),
          null, null, null, null, comments, 0);
    }

    @Test
    @DisplayName("detailedEngineeringCostInd accepts only Y or N")
    void indicatorIsConstrained() {
      RoadDetailRequest badIndicator = new RoadDetailRequest(
          "Mainline 400", "L20", 8801, "SD", 45, "X", subGrade(), stabilizing(), material(),
          null, null, null, null, "ok", 0);
      assertThat(messagesFor(badIndicator, Default.class)).containsExactly(CODE_VALUE);
    }

    @Test
    @DisplayName("revisionCount is required on UPDATE only, and a negative is rejected outright")
    void revisionCountRules() {
      RoadDetailRequest noToken =
          detail("Mainline 400", "L20", 8801, "SD", subGrade(), stabilizing(), material(), null);
      assertThat(messagesFor(noToken, Default.class)).isEmpty();
      assertThat(messagesFor(noToken, Default.class, OnUpdate.class))
          .containsExactly(REVISION_REQUIRED);

      RoadDetailRequest negative =
          detail("Mainline 400", "L20", 8801, "SD", subGrade(), stabilizing(), material(), -1);
      assertThat(messagesFor(negative, Default.class)).containsExactly(REVISION_REQUIRED);
    }

    @Test
    @DisplayName("roadName is capped at 30, and the cap reuses the FLD-003 key")
    void roadNameCap() {
      assertThat(messagesFor(
          detail("A".repeat(30), "L20", 8801, "SD", subGrade(), stabilizing(), material(), 0),
          Default.class))
          .isEmpty();
      assertThat(messagesFor(
          detail("A".repeat(31), "L20", 8801, "SD", subGrade(), stabilizing(), material(), 0),
          Default.class))
          .containsExactly("{roadNameRequiredErrorMsg}");
    }
  }
}
