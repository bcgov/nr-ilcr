package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatus.DetailOutcome;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatus.Issue;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatus.PageOutcome;
import ca.bc.gov.nrs.ilcr.schedule10.dto.BecClassification;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Check Status rule engine — no Spring, no database, no message resolution.
 *
 * <p>Asserts the emitted bundle keys, the pre-formatted bound arguments and the composed label
 * prefixes, which is everything the controller needs to render the verbatim line. Several tests pin
 * legacy quirks deliberately: they are the behaviour, not defects to fix.
 */
@DisplayName("Schedule10CheckStatus")
class Schedule10CheckStatusTest {

  private static final int BEC_ID = 8801;
  private static final Set<Integer> ALLOWABLE = Set.of(BEC_ID);
  private static final String PAGE_LABEL = "Page 1, Period: 2021-06, TSA: 01, SB: 01A, TFL:-";

  private static BecClassification bec() {
    return new BecClassification(BEC_ID, "ICH", "dw", "1", null, "ICHdw1");
  }

  /** A sub-grade whose every checked figure sits inside its range. */
  private static SubGrade cleanSubGrade() {
    return new SubGrade(
        new BigDecimal("12.500"), new BigDecimal("6.5"), new BigDecimal("150000"),
        new BigDecimal("-5000"), new BigDecimal("2000"), new BigDecimal("1000"),
        new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"),
        new BigDecimal("5000"), new BigDecimal("6000"),
        new BigDecimal("147000"), new BigDecimal("21000"), new BigDecimal("126000"),
        new BigDecimal("10080.00"));
  }

  /** Ballast method N, so none of the additional-stabilizing rules are required. */
  private static Stabilizing stabilizingNotRequired() {
    return new Stabilizing(
        "N", "NA", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, null);
  }

  private static MaterialComposition material(Integer... pct) {
    return new MaterialComposition(pct[0], pct[1], pct[2], pct[3], pct[4], pct[5]);
  }

  private static RoadDetail detail(
      SubGrade subGrade, Stabilizing stabilizing, MaterialComposition material, Integer sideSlope) {
    return new RoadDetail(
        8910, 1, "Road #1, Mainline A", "Mainline A", "P", bec(), "3", sideSlope,
        subGrade, stabilizing, material, "N", null, null, null, null, null, 0);
  }

  private static RoadDetail cleanDetail() {
    return detail(
        cleanSubGrade(), stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), 25);
  }

  private static ConstructionPage page(List<RoadDetail> details) {
    return new ConstructionPage(
        8900, 1, PAGE_LABEL, "RNI", "01", "01A", null, "11", "North Division", "2021-06",
        details.size(), 0, details);
  }

  private static Issue issueFor(List<Issue> issues, String field) {
    return issues.stream()
        .filter(issue -> field.equals(issue.field()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no issue for field " + field));
  }

  @Nested
  @DisplayName("the passing case")
  class Passing {

    @Test
    @DisplayName("a complete page and road detail report nothing at all")
    void completeScheduleIsMet() {
      PageOutcome outcome = Schedule10CheckStatus.evaluatePage(page(List.of(cleanDetail())),
          ALLOWABLE);

      assertThat(outcome.issues()).isEmpty();
      assertThat(outcome.roadDetails()).hasSize(1);
      assertThat(outcome.roadDetails().get(0).issues()).isEmpty();
    }
  }

  @Nested
  @DisplayName("page-level rules")
  class PageRules {

    @Test
    @DisplayName("Division and Period Surveyed are required, and Region is checked NOWHERE")
    void requiresDivisionAndPeriodButNotRegion() {
      ConstructionPage blank = new ConstructionPage(
          8900, 1, PAGE_LABEL, null, "01", "01A", null, "11", null, null, 0, 0, List.of());

      PageOutcome outcome = Schedule10CheckStatus.evaluatePage(blank, ALLOWABLE);

      assertThat(outcome.issues()).extracting(Issue::field)
          .containsExactly("divisionName", "constructionPeriod");
      // forestRegionCode is null above and is deliberately NOT reported — legacy checks it on the
      // form but nowhere in Check Status.
      assertThat(outcome.issues()).extracting(Issue::field).doesNotContain("forestRegionCode");
      assertThat(issueFor(outcome.issues(), "divisionName").label())
          .isEqualTo(PAGE_LABEL + " Division");
    }

    @Test
    @DisplayName("a TSA page checks Supply Block; a TFL page checks TFL # instead")
    void checksTheLocationCounterpart() {
      ConstructionPage tsaPage = new ConstructionPage(
          8900, 1, PAGE_LABEL, "RNI", "01", null, null, "11", "D", "2021-06", 0, 0, List.of());
      ConstructionPage tflPage = new ConstructionPage(
          8902, 1, PAGE_LABEL, "RNI", null, null, null, "10", "D", "2021-06", 0, 0, List.of());

      assertThat(Schedule10CheckStatus.evaluatePage(tsaPage, ALLOWABLE).issues())
          .extracting(Issue::field).containsExactly("supplyBlock");
      assertThat(Schedule10CheckStatus.evaluatePage(tflPage, ALLOWABLE).issues())
          .extracting(Issue::field).containsExactly("tflNumberCode");
    }
  }

  @Nested
  @DisplayName("legacy quirks, preserved deliberately")
  class Quirks {

    @Test
    @DisplayName("an untouched material breakdown still reports that the total must equal 100")
    void blankMaterialStillReportsTotal() {
      // The legacy total coerces nulls to zero and is therefore never absent, so the optional
      // not-null escape is unreachable and 0 is checked against 100.
      RoadDetail blankMaterial = detail(
          cleanSubGrade(), stabilizingNotRequired(), material(null, null, null, null, null, 0), 25);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(blankMaterial, PAGE_LABEL, ALLOWABLE);

      Issue total = issueFor(outcome.issues(), "materialTypeTotal");
      assertThat(total.messageKey()).isEqualTo("invalidTotalErrorMsg");
      assertThat(total.args()).containsExactly("100");
      assertThat(total.label()).isEqualTo(PAGE_LABEL + ", Road #1, Mainline A"
          + " Material Type Total (%)");
    }

    @Test
    @DisplayName("Road Name and Sub Zone are titled with the PAGE label only, not the road label")
    void roadNameAndSubZoneUsePageLabelOnly() {
      // BOTH halves of the claim are asserted. The earlier version stopped after roadName while its
      // name promised Sub Zone too, and its fixture supplied a non-blank subzone so no subzone issue
      // could be produced at all (code review 2026-08-18). A blank subzone is unreachable from stored
      // data — the column is NOT NULL — but it IS reachable at this seam, which is the whole point of
      // pinning the rule here.
      BecClassification blankSubzone = new BecClassification(BEC_ID, "ICH", "  ", "1", null, "ICH1");
      RoadDetail nameless = new RoadDetail(
          8910, 1, "Road #1, null", null, "P", blankSubzone, "3", 25,
          cleanSubGrade(), stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), "N",
          null, null, null, null, null, 0);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(nameless, PAGE_LABEL, ALLOWABLE);

      // No road label in the prefix — so a multi-road page gives no clue which road is meant.
      assertThat(issueFor(outcome.issues(), "roadName").label())
          .isEqualTo(PAGE_LABEL + " Road Name");
      // The exact label string, asserted POSITIVELY. Elsewhere it appears only inside absence
      // assertions, so a typo in it would leave every one of those trivially true.
      assertThat(issueFor(outcome.issues(), "subzone").label())
          .isEqualTo(PAGE_LABEL + " Sub Zone");
      assertThat(issueFor(outcome.issues(), "subzone").messageKey())
          .isEqualTo("missingRequiredFieldMsg");
    }

    @Test
    @DisplayName("EMISSION ORDER is contractual — the ordinals in the user's list are positional")
    void emissionOrderIsContractual() {
      // The story declares the order contractual, and nothing asserted it: every multi-issue
      // assertion used order-insensitive matchers, so reordering any of the 36 road-detail rules left
      // the whole suite green (code review 2026-08-18). Pinned as an exact sequence.
      Stabilizing crushedEmpty = new Stabilizing(
          "C", null, null, null, null, null, null, null, null, null, null);
      RoadDetail bare = new RoadDetail(
          8910, 1, "Road #1, null", null, "P", bec(), null, null,
          new SubGrade(null, null, null, null, null, null, null, null, null, null, null,
              null, null, null, null),
          crushedEmpty, material(null, null, null, null, null, null), "N",
          null, null, null, null, null, 0);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(bare, PAGE_LABEL, ALLOWABLE);

      // Legacy's declaration order, road-detail scope. Two things this pins that are easy to get
      // wrong, both confirmed against the implementation rather than assumed:
      //
      // 1. The sub-grade dimensions and the five material percentages are OPTIONAL, so a null value
      //    emits nothing. Only the required fields and the ballast-gated block appear here.
      // 2. The Material Code Type rule sits IN THE MIDDLE of the additional-stabilizing block —
      //    after the four dimensions, before the three costs. That position is surprising and is
      //    exactly the kind of thing a "tidy the rules into groups" refactor would move.
      assertThat(outcome.issues()).extracting(Issue::field).containsExactly(
          "roadName",
          "relSoilMoistRgmClsCode",
          "sideSlopePct",
          "stabilizingLength",
          "stabilizingSurfaceWidth",
          "stabilizingDepth",
          "stabilizingDistanceToSource",
          "ballastMaterialCode",
          "stabilizingActualCost",
          "stabilizingTtTransfer",
          "stabilizingOtherTransfer");

      // The second "unreachable from stored data" label, asserted POSITIVELY — elsewhere it appears
      // only inside absence assertions, so a typo would leave those trivially true.
      assertThat(issueFor(outcome.issues(), "ballastMaterialCode").label())
          .isEqualTo(PAGE_LABEL + ", Road #1, null Additional Stabilizing Type");
    }

    @Test
    @DisplayName("End Haul and Overland figures are checked nowhere")
    void endHaulAndOverlandAreNotChecked() {
      RoadDetail negatives = new RoadDetail(
          8910, 1, "Road #1, Mainline A", "Mainline A", "P", bec(), "3", 25,
          cleanSubGrade(), stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), "N",
          new BigDecimal("-9999.9"), new BigDecimal("-1"), new BigDecimal("-9999.9"),
          new BigDecimal("-1"), null, 0);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(negatives, PAGE_LABEL, ALLOWABLE);

      // isEmpty(), not doesNotContain(...): with every other figure clean the outcome IS empty, and
      // doesNotContain is satisfied by an empty list — so it would pass even if the engine returned
      // nothing at all (Sonar Bug, 2026-08-18). This states the actual claim: four out-of-range haul
      // values produce no issue whatsoever.
      assertThat(outcome.issues()).isEmpty();

      // The control that makes the assertion above meaningful. Same fixture, with ONE unrelated
      // field driven out of range: the engine reports that field and still reports nothing for the
      // four haul values, which proves it ran rather than silently returning an empty list.
      RoadDetail alsoBadSideSlope = new RoadDetail(
          8910, 1, "Road #1, Mainline A", "Mainline A", "P", bec(), "3", 101,
          cleanSubGrade(), stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), "N",
          new BigDecimal("-9999.9"), new BigDecimal("-1"), new BigDecimal("-9999.9"),
          new BigDecimal("-1"), null, 0);

      assertThat(Schedule10CheckStatus.evaluateRoadDetail(alsoBadSideSlope, PAGE_LABEL, ALLOWABLE)
          .issues()).extracting(Issue::field).containsExactly("sideSlopePct");
    }

    @Test
    @DisplayName("stabilizing transfers are floored at zero here even though the form allows negatives")
    void stabilizingTransfersAreFlooredAtZero() {
      Stabilizing crushed = new Stabilizing(
          "C", "GR", new BigDecimal("3.000"), new BigDecimal("6.5"), new BigDecimal("0.3"),
          new BigDecimal("12.4"), new BigDecimal("40000"), new BigDecimal("-1500"),
          new BigDecimal("-2500"), new BigDecimal("36000"), new BigDecimal("12000.00"));
      RoadDetail withNegativeTransfers = detail(
          cleanSubGrade(), crushed, material(10, 20, 40, 20, 10, 100), 25);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(withNegativeTransfers, PAGE_LABEL, ALLOWABLE);

      Issue tt = issueFor(outcome.issues(), "stabilizingTtTransfer");
      assertThat(tt.messageKey()).isEqualTo("invalidRangeErrorMsg");
      assertThat(tt.args()).containsExactly("0", "9,999,999");
      assertThat(issueFor(outcome.issues(), "stabilizingOtherTransfer").args())
          .containsExactly("0", "9,999,999");
    }
  }

  @Nested
  @DisplayName("ballast method gates the additional-stabilizing rules")
  class BallastGate {

    @Test
    @DisplayName("method C requires the four dimensions, the material type and the costs")
    void crushedRequiresEverything() {
      Stabilizing empty = new Stabilizing("C", null, null, null, null, null, null, null, null, null,
          null);
      RoadDetail detail = detail(
          cleanSubGrade(), empty, material(10, 20, 40, 20, 10, 100), 25);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(detail, PAGE_LABEL, ALLOWABLE);

      // containsExactly, not contains: the sub-grade and material figures are all clean here, so the
      // gated stabilizing block is the WHOLE outcome, and its order is contractual.
      assertThat(outcome.issues()).extracting(Issue::field).containsExactly(
          "stabilizingLength", "stabilizingSurfaceWidth", "stabilizingDepth",
          "stabilizingDistanceToSource", "ballastMaterialCode", "stabilizingActualCost",
          "stabilizingTtTransfer", "stabilizingOtherTransfer");
      // The key is bound to the FIELD. The previous form asserted only that
      // "missingRequiredFieldMsg" appeared somewhere in the outcome, which any of the eight issues
      // satisfied — so it never pinned the material-type rule to its own message (flagged by both the
      // code review and Sonar, 2026-08-18).
      assertThat(issueFor(outcome.issues(), "ballastMaterialCode").messageKey())
          .isEqualTo("missingRequiredFieldMsg");
    }

    @Test
    @DisplayName("method N requires none of them, and stops before the material-type rule")
    void notRequiredSkipsTheGatedRules() {
      Stabilizing empty = new Stabilizing("N", null, null, null, null, null, null, null, null, null,
          null);
      RoadDetail detail = detail(
          cleanSubGrade(), empty, material(10, 20, 40, 20, 10, 100), 25);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(detail, PAGE_LABEL, ALLOWABLE);

      // isEmpty(), not doesNotContain(...): everything outside the gated block is clean, so the
      // outcome IS empty — and doesNotContain passes on an empty list, which would let a broken
      // engine through (Sonar Bug, 2026-08-18).
      assertThat(outcome.issues()).isEmpty();

      // crushedRequiresEverything is this test's control: the SAME empty stabilizing block under
      // method "C" produces all eight gated issues. Asserted here too so the pair cannot drift
      // apart — if the gate stopped working, one of these two would fail.
      Stabilizing sameButCrushed = new Stabilizing("C", null, null, null, null, null, null, null,
          null, null, null);
      assertThat(Schedule10CheckStatus.evaluateRoadDetail(
          detail(cleanSubGrade(), sameButCrushed, material(10, 20, 40, 20, 10, 100), 25),
          PAGE_LABEL, ALLOWABLE).issues())
          .as("the gate is what suppresses these, not an empty rule set")
          .isNotEmpty();
    }
  }

  @Nested
  @DisplayName("BEC classification")
  class Bec {

    @Test
    @DisplayName("a classification outside the allowable list is reported with legacy's own message")
    void unallowableBecIsReported() {
      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(cleanDetail(), PAGE_LABEL, Set.of(9999));

      Issue issue = issueFor(outcome.issues(), "becClassification");
      assertThat(issue.messageKey()).isEqualTo("invalidBiogeoCode");
      assertThat(issue.args()).isEmpty();
    }

    @Test
    @DisplayName("a NULL classification is reported, not thrown — legacy aborts the whole check")
    void nullBecIsReportedRatherThanThrowing() {
      RoadDetail noBec = new RoadDetail(
          8910, 1, "Road #1, Mainline A", "Mainline A", "P", null, "3", 25,
          cleanSubGrade(), stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), "N",
          null, null, null, null, null, 0);

      DetailOutcome outcome =
          Schedule10CheckStatus.evaluateRoadDetail(noBec, PAGE_LABEL, ALLOWABLE);

      assertThat(issueFor(outcome.issues(), "becClassification").messageKey())
          .isEqualTo("invalidBiogeoCode");
      // Sub Zone comes from the same classification, so it is reported missing alongside.
      assertThat(issueFor(outcome.issues(), "subzone").messageKey())
          .isEqualTo("missingRequiredFieldMsg");
    }
  }

  @Nested
  @DisplayName("bound formatting")
  class BoundFormatting {

    @Test
    @DisplayName("percentage bounds render without grouping")
    void percentageBounds() {
      RoadDetail overSlope = detail(
          cleanSubGrade(), stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), 101);

      Issue issue = issueFor(
          Schedule10CheckStatus.evaluateRoadDetail(overSlope, PAGE_LABEL, ALLOWABLE).issues(),
          "sideSlopePct");

      assertThat(issue.messageKey()).isEqualTo("invalidRangeErrorMsg");
      assertThat(issue.args()).containsExactly("0", "100");
    }

    @Test
    @DisplayName("money bounds render with grouping, and both use the LOWER bound's pattern")
    void moneyBounds() {
      SubGrade overCost = new SubGrade(
          new BigDecimal("12.500"), new BigDecimal("6.5"), new BigDecimal("99999999"),
          null, null, null, null, null, null, null, null, null, null, null, null);
      RoadDetail detail = detail(
          overCost, stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), 25);

      Issue issue = issueFor(
          Schedule10CheckStatus.evaluateRoadDetail(detail, PAGE_LABEL, ALLOWABLE).issues(),
          "subGradeActualCost");

      assertThat(issue.args()).containsExactly("0", "9,999,999");
    }

    @Test
    @DisplayName("the sub-grade length bound renders at three decimals, capped at legacy's 100")
    void lengthBounds() {
      SubGrade tooLong = new SubGrade(
          new BigDecimal("101.000"), null, null, null, null, null, null, null, null, null, null,
          null, null, null, null);
      RoadDetail detail = detail(
          tooLong, stabilizingNotRequired(), material(10, 20, 40, 20, 10, 100), 25);

      Issue issue = issueFor(
          Schedule10CheckStatus.evaluateRoadDetail(detail, PAGE_LABEL, ALLOWABLE).issues(),
          "subGradeLength");

      // Legacy binds this maximum to its percentage constant; the cap really is 100, not 999.999.
      assertThat(issue.args()).containsExactly("0", "100");
    }
  }
}
