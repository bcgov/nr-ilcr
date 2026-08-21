package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.schedule10.dto.BecClassification;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Schedule 10 Check Status rules, transcribed from legacy {@code Schedule10CheckStatus}
 * (:32-339).
 *
 * <p><strong>It evaluates the assembled document, not the database.</strong> Legacy validates its
 * loaded domain object, so this takes the same input — which means Check Status sees exactly what
 * the GET serves, needs no queries of its own, and can be unit-tested from a hand-built document.
 * It also puts the derived totals within reach: several rules check {@code Total Costs}, {@code
 * Total} and {@code $/km}, which exist only as derived values.
 *
 * <p><strong>Emission order is contractual.</strong> Rules run page-by-page, and within a page the
 * three page-level rules come before the per-road ones, in the order legacy declares them. The
 * ordinals that appear in the composed text are positional, so a reordering would silently renumber
 * the user's error list.
 *
 * <p>This class resolves no text. It emits bundle keys plus pre-formatted arguments and the label
 * prefix; the controller performs the single concatenation {@code label + ": " + resolvedText}, so
 * the verbatim byte composition lives in exactly one place.
 *
 * <p><strong>Legacy quirks preserved deliberately</strong> — each is reproduced, not corrected:
 *
 * <ul>
 *   <li>{@code Road Name} and {@code Sub Zone} are titled with the PAGE label only, so on a page
 *       with several roads the user cannot tell which road is at fault.
 *   <li>{@code Sub Zone} has no control anywhere on the screen; it is populated only on read from
 *       the catalogue row.
 *   <li>{@code Material Type Total (%)} is reported whenever the five percentages do not total 100
 *       — including when all five are blank, because the legacy total coerces nulls to zero and is
 *       therefore never absent.
 *   <li>Additional-stabilizing transfers are checked against a floor of ZERO here while the entry
 *       form accepts negatives, so a value the form allowed can be reported as out of range.
 *   <li>{@code Region}, {@code Road Type} and {@code Ballast Method Code} are required on the form
 *       but checked NOWHERE here.
 *   <li>End Haul and Overland figures are checked nowhere — those rules are commented out in
 *       legacy.
 *   <li>Both range bounds are formatted with the LOWER bound's pattern; legacy accepts an upper
 *       pattern and then ignores it.
 *   <li>Label drift against the screen is kept as legacy writes it: {@code Ripple Rock} for the
 *       screen's "Rippable Rock", {@code Less Landing} singular, {@code Less Other Eng}, and a
 *       lower-case {@code total} in one stabilizing label.
 * </ul>
 *
 * <p>Two deliberate departures: the Boulder Area rule is dropped, because that field is removed by
 * business direction; and a road detail with no BEC classification is reported as an invalid
 * classification rather than aborting the whole check, which is what legacy does by dereferencing a
 * nullable foreign key without a guard.
 */
final class Schedule10CheckStatus {

  /** Value is absent where it is required. */
  private static final String MSG_REQUIRED = "missingRequiredFieldMsg";

  /** Value sits outside an inclusive two-sided range. */
  private static final String MSG_RANGE = "invalidRangeErrorMsg";

  /** Value must equal a single figure — the both-bounds-identical case. */
  private static final String MSG_TOTAL = "invalidTotalErrorMsg";

  /** Stored BEC classification is outside the allowable filtered list. */
  private static final String MSG_BEC = "invalidBiogeoCode";

  /** Ballast method requiring the additional-stabilizing figures and a material type. */
  private static final String BALLAST_CRUSHED = "C";

  // Legacy number patterns, transcribed per rule. Formatting is applied mechanically so the
  // rendered bounds are whatever the pattern produces, exactly as legacy renders them.
  private static final String FMT_INT = "###";
  private static final String FMT_3DP = "###.###";
  private static final String FMT_1DP = "###.#";
  private static final String FMT_2DP_SMALL = "##.#";
  private static final String FMT_MONEY = "#,###,###";
  private static final String FMT_MONEY_2DP = "#,###,###.##";
  private static final String FMT_MONEY_WIDE = "#,###,###,###";
  private static final String FMT_MONEY_NARROW = "###,###";

  private static final BigDecimal PCT_MAX = new BigDecimal("100");

  /** Shared upper bound for the three one-decimal width/distance rules. */
  private static final BigDecimal WIDTH_MAX = new BigDecimal("999.9");

  private static final BigDecimal SEVEN_DIGITS = new BigDecimal("9999999");
  private static final BigDecimal EIGHT_DIGITS = new BigDecimal("99999999");
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private Schedule10CheckStatus() {}

  /**
   * One outstanding requirement: the machine field, its label prefix, and an unresolved message.
   */
  record Issue(String field, String label, String messageKey, List<String> args) {}

  /** Per-road-detail outcome. */
  record DetailOutcome(
      int roadDetailId, int rowNumber, String roadDetailLabel, List<Issue> issues) {}

  /** Per-page outcome, carrying its own issues and those of its road details. */
  record PageOutcome(
      int pageId,
      int pageNumber,
      String pageLabel,
      List<Issue> issues,
      List<DetailOutcome> roadDetails) {}

  /** Whole-schedule outcome. {@code met} is true only when nothing at all was reported. */
  record Outcome(boolean met, List<PageOutcome> pages) {}

  /**
   * Evaluates every page and road detail of the document.
   *
   * <p>A schedule with no pages is a vacuous pass, exactly as legacy's loop simply never runs.
   *
   * @param document the assembled Schedule 10 document
   * @return the outcome, with issues in legacy emission order
   */
  static Outcome evaluate(Schedule10Response document) {
    // The allowable BEC list is the offerable set the document already serves as its dropdown — the
    // same filtered list legacy checks a stored classification against.
    Set<Integer> allowableBec =
        document.codeLists() == null
            ? Set.of()
            : document.codeLists().becClassifications().stream()
                .map(BecClassification::biogeoclimaticCatalogueId)
                .collect(Collectors.toSet());

    List<PageOutcome> pages = new ArrayList<>();
    boolean met = true;
    for (ConstructionPage page : document.pages()) {
      PageOutcome outcome = evaluatePage(page, allowableBec);
      pages.add(outcome);
      met =
          met
              && outcome.issues().isEmpty()
              && outcome.roadDetails().stream().allMatch(detail -> detail.issues().isEmpty());
    }
    return new Outcome(met, pages);
  }

  /** The three page-level rules, then every road detail beneath the page. */
  static PageOutcome evaluatePage(ConstructionPage page, Set<Integer> allowableBec) {
    String prefix = page.pageLabel();
    List<Issue> issues = new ArrayList<>();

    requirePresent(issues, "divisionName", prefix + " Division", page.divisionName());
    requirePresent(
        issues, "constructionPeriod", prefix + " Period Surveyed", page.constructionPeriod());

    // Legacy reconstructs the TSA/TFL selector on read, setting it to the TFL sentinel when the TSA
    // column is null. The served document keeps the raw columns, so a null TSA is the TFL branch.
    if (page.tsaNumber() == null) {
      requirePresent(issues, "tflNumberCode", prefix + " TFL #", page.tflNumberCode());
    } else {
      requirePresent(issues, "supplyBlock", prefix + " Supply Block", page.tsbNumberCode());
    }

    List<DetailOutcome> details = new ArrayList<>();
    for (RoadDetail detail : page.roadDetails()) {
      details.add(evaluateRoadDetail(detail, prefix, allowableBec));
    }
    return new PageOutcome(page.pageId(), page.pageNumber(), page.pageLabel(), issues, details);
  }

  /** Every road-detail rule, in legacy emission order. */
  static DetailOutcome evaluateRoadDetail(
      RoadDetail detail, String pagePrefix, Set<Integer> allowableBec) {
    List<Issue> issues = new ArrayList<>();
    String prefix = pagePrefix + ", " + detail.roadDetailLabel();
    BecClassification bec = detail.becClassification();

    // Road Name and Sub Zone are titled with the PAGE label only — legacy does not add the road
    // label to these two, so a multi-road page gives no clue which road is meant.
    requirePresent(issues, "roadName", pagePrefix + " Road Name", detail.roadName());
    requirePresent(issues, "subzone", pagePrefix + " Sub Zone", bec == null ? null : bec.subzone());

    // Legacy dereferences the classification and its id without a guard, so one road detail holding
    // a null foreign key aborts the entire check before any message is emitted. Reported instead.
    if (bec == null || !allowableBec.contains(bec.biogeoclimaticCatalogueId())) {
      issues.add(new Issue("becClassification", prefix + " BEC Zone", MSG_BEC, List.of()));
    }

    requirePresent(
        issues, "relSoilMoistRgmClsCode", prefix + " RSMR Class", detail.relSoilMoistRgmClsCode());
    requireRange(
        issues,
        "sideSlopePct",
        prefix + " Side Slope (%)",
        value(detail.sideSlopePct()),
        band(ZERO, FMT_INT, PCT_MAX),
        true);

    MaterialComposition material = detail.materialComposition();
    requireRange(
        issues,
        "solidRockPct",
        prefix + " Solid (Hard) Rock (%)",
        value(material == null ? null : material.solidRockPct()),
        band(ZERO, FMT_INT, PCT_MAX),
        false);
    requireRange(
        issues,
        "rippableRockPct",
        prefix + " Ripple Rock (%)",
        value(material == null ? null : material.rippableRockPct()),
        band(ZERO, FMT_INT, PCT_MAX),
        false);
    requireRange(
        issues,
        "coarsePct",
        prefix + " Coarse (%)",
        value(material == null ? null : material.coarsePct()),
        band(ZERO, FMT_INT, PCT_MAX),
        false);
    requireRange(
        issues,
        "finePct",
        prefix + " Fine (%)",
        value(material == null ? null : material.finePct()),
        band(ZERO, FMT_INT, PCT_MAX),
        false);
    requireRange(
        issues,
        "organicPct",
        prefix + " Organic (%)",
        value(material == null ? null : material.organicPct()),
        band(ZERO, FMT_INT, PCT_MAX),
        false);
    // Both bounds are 100, which selects the must-equal message. The legacy total coerces nulls to
    // zero and is never absent, so an untouched material breakdown reports 0 against 100.
    requireRange(
        issues,
        "materialTypeTotal",
        prefix + " Material Type Total (%)",
        value(material == null ? null : material.totalPct()),
        band(PCT_MAX, FMT_INT, PCT_MAX),
        false);

    SubGrade subGrade = detail.subGrade();
    requireRange(
        issues,
        "subGradeLength",
        prefix + " Sub-Grade: Length (km)",
        field(subGrade, SubGrade::length),
        band(ZERO, FMT_3DP, PCT_MAX),
        false);
    requireRange(
        issues,
        "subGradeSurfaceWidth",
        prefix + " Sub-Grade: Surface Width (m)",
        field(subGrade, SubGrade::surfaceWidth),
        band(ZERO, FMT_1DP, WIDTH_MAX),
        false);
    requireRange(
        issues,
        "subGradeActualCost",
        prefix + " Sub-Grade: Actual Cost ($)",
        field(subGrade, SubGrade::actualCost),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "subGradeTtTransfer",
        prefix + " Sub-Grade: TtT Transfer ($)",
        field(subGrade, SubGrade::ttTransfer),
        band(SEVEN_DIGITS.negate(), FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "subGradeOtherTransfer",
        prefix + " Sub-Grade: Other Transfer ($)",
        field(subGrade, SubGrade::otherTransfer),
        band(SEVEN_DIGITS.negate(), FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "subGradeTotalCosts",
        prefix + " Sub-Grade: Total Costs ($)",
        field(subGrade, SubGrade::totalCosts),
        band(EIGHT_DIGITS.negate(), FMT_MONEY, EIGHT_DIGITS),
        false);
    requireRange(
        issues,
        "lessBridges",
        prefix + " Sub-Grade: Less Bridges ($)",
        field(subGrade, SubGrade::lessBridges),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "lessCulverts",
        prefix + " Sub-Grade: Less Culverts ($)",
        field(subGrade, SubGrade::lessCulverts),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "lessLandings",
        prefix + " Sub-Grade: Less Landing ($)",
        field(subGrade, SubGrade::lessLandings),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "lessEndHaul",
        prefix + " Sub-Grade: Less End Haul ($)",
        field(subGrade, SubGrade::lessEndHaul),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "lessOverland",
        prefix + " Sub-Grade: Less Overland ($)",
        field(subGrade, SubGrade::lessOverland),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "lessOtherEng",
        prefix + " Sub-Grade: Less Other Eng ($)",
        field(subGrade, SubGrade::lessOtherEng),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "subGradeTotal",
        prefix + " Sub-Grade: Total ($)",
        field(subGrade, SubGrade::total),
        band(EIGHT_DIGITS.negate(), FMT_MONEY_2DP, EIGHT_DIGITS),
        false);
    requireRange(
        issues,
        "subGradeCostPerLength",
        prefix + " Sub-Grade: $/km",
        field(subGrade, SubGrade::costPerLength),
        band(EIGHT_DIGITS.negate(), FMT_MONEY_2DP, EIGHT_DIGITS),
        false);

    Stabilizing stabilizing = detail.stabilizing();
    boolean crushed =
        stabilizing != null && BALLAST_CRUSHED.equals(stabilizing.ballastMethodCode());

    requireRange(
        issues,
        "stabilizingLength",
        prefix + " Additional Stabilizing: Length (km)",
        field(stabilizing, Stabilizing::length),
        band(ZERO, FMT_3DP, new BigDecimal("999.999")),
        crushed);
    requireRange(
        issues,
        "stabilizingSurfaceWidth",
        prefix + " Additional Stabilizing: Surface Width (m)",
        field(stabilizing, Stabilizing::surfaceWidth),
        band(ZERO, FMT_1DP, WIDTH_MAX),
        crushed);
    requireRange(
        issues,
        "stabilizingDepth",
        prefix + " Additional Stabilizing: Depth (m)",
        field(stabilizing, Stabilizing::depth),
        band(ZERO, FMT_2DP_SMALL, new BigDecimal("99.9")),
        crushed);
    requireRange(
        issues,
        "stabilizingDistanceToSource",
        prefix + " Additional Stabilizing: Distance to Source (km)",
        field(stabilizing, Stabilizing::distanceToSource),
        band(ZERO, FMT_1DP, WIDTH_MAX),
        crushed);

    if (!crushed) {
      return new DetailOutcome(
          detail.roadDetailId(), detail.rowNumber(), detail.roadDetailLabel(), issues);
    }

    requirePresent(
        issues,
        "ballastMaterialCode",
        prefix + " Additional Stabilizing Type",
        stabilizing.ballastMaterialCode());
    requireRange(
        issues,
        "stabilizingActualCost",
        prefix + " Additional Stabilizing: Actual Cost ($)",
        stabilizing.actualCost(),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        true);
    // Floor of ZERO, while the entry form accepts down to -9,999,999 for both transfers. A value
    // the form allowed is therefore reported here. Legacy carries the same disagreement.
    requireRange(
        issues,
        "stabilizingTtTransfer",
        prefix + " Additional Stabilizing: TtT Transfer ($)",
        stabilizing.ttTransfer(),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        true);
    requireRange(
        issues,
        "stabilizingOtherTransfer",
        prefix + " Additional Stabilizing: Other Transfer ($)",
        stabilizing.otherTransfer(),
        band(ZERO, FMT_MONEY, SEVEN_DIGITS),
        true);
    requireRange(
        issues,
        "stabilizingTotal",
        prefix + " Additional Stabilizing: total ($)",
        stabilizing.total(),
        band(new BigDecimal("-999999"), FMT_MONEY_NARROW, SEVEN_DIGITS),
        false);
    requireRange(
        issues,
        "stabilizingCostPerLength",
        prefix + " Additional Stabilizing: $/km",
        stabilizing.costPerLength(),
        band(ZERO, FMT_MONEY_WIDE, EIGHT_DIGITS),
        false);

    return new DetailOutcome(
        detail.roadDetailId(), detail.rowNumber(), detail.roadDetailLabel(), issues);
  }

  private static void requirePresent(List<Issue> issues, String field, String label, String value) {
    if (value == null || value.trim().isEmpty()) {
      issues.add(new Issue(field, label, MSG_REQUIRED, List.of()));
    }
  }

  /**
   * One rule's inclusive bounds and the pattern both are rendered with.
   *
   * <p>Bundled into a record so {@link #requireRange} takes six parameters rather than eight (Sonar
   * brain-overload, 2026-08-18). The three genuinely travel together: legacy formats BOTH bounds
   * with the LOWER bound's pattern, which is why one format field serves both and why they cannot
   * sensibly be separated.
   *
   * @param lower the inclusive lower bound
   * @param upper the inclusive upper bound; equal to {@code lower} selects the must-equal message
   * @param format the legacy number pattern both bounds are rendered with
   */
  private record Band(BigDecimal lower, BigDecimal upper, String format) {}

  /** Reads as a bound pair at the call site, in the legacy lower/format/upper order. */
  private static Band band(BigDecimal lower, String format, BigDecimal upper) {
    return new Band(lower, upper, format);
  }

  /**
   * The legacy numeric rule: an absent optional value passes, an absent required value is reported
   * as missing, and otherwise the value must sit inside the inclusive range.
   *
   * <p>When both bounds are identical the must-equal message is used with a single argument. Legacy
   * reaches that branch by comparing the two bounds by REFERENCE, which happens to work for the one
   * rule that uses it because small boxed integers are cached; numeric equality is used here
   * instead, which agrees for every rule in this schedule and does not depend on that accident.
   */
  private static void requireRange(
      List<Issue> issues,
      String field,
      String label,
      BigDecimal value,
      Band band,
      boolean required) {
    BigDecimal lower = band.lower();
    BigDecimal upper = band.upper();
    String lowerFormat = band.format();
    if (value == null) {
      if (required) {
        issues.add(new Issue(field, label, MSG_REQUIRED, List.of()));
      }
      return;
    }
    if (value.compareTo(lower) >= 0 && value.compareTo(upper) <= 0) {
      return;
    }
    if (lower.compareTo(upper) == 0) {
      issues.add(new Issue(field, label, MSG_TOTAL, List.of(format(upper, lowerFormat))));
      return;
    }
    issues.add(
        new Issue(
            field,
            label,
            MSG_RANGE,
            List.of(format(lower, lowerFormat), format(upper, lowerFormat))));
  }

  /**
   * Renders a bound with its pattern.
   *
   * <p>Both bounds use the LOWER bound's pattern: legacy accepts an upper pattern and then never
   * applies it. Symbols are pinned to a fixed locale so the rendered separators cannot drift with
   * the server's default.
   */
  private static String format(BigDecimal bound, String pattern) {
    DecimalFormat format =
        new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.CANADA));
    return format.format(bound);
  }

  private static BigDecimal value(Integer boxed) {
    return boxed == null ? null : BigDecimal.valueOf(boxed);
  }

  private static <T> BigDecimal field(T holder, java.util.function.Function<T, BigDecimal> getter) {
    return holder == null ? null : getter.apply(holder);
  }
}
