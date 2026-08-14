package ca.bc.gov.nrs.ilcr.schedule10;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The Schedule 10 derived arithmetic, transcribed verbatim from legacy
 * {@code RoadConstructionReportDetailType} and {@code CoreUtil}.
 *
 * <p>This lives in ONE pure, testable place on purpose. Legacy spread the same arithmetic across
 * the
 * type class, the JSF view and the Jasper report, and the read path and the print path could
 * disagree. Every derived Schedule 10 value must be computed here and nowhere else — never inside a
 * row mapper, never re-implemented in SQL.
 *
 * <p><strong>Null is not zero, and that rule is load-bearing.</strong> Schedule 10 has ZERO cost
 * lines in the real delivery database (52 pages, 66 detail rows, 0 cost rows — Story 11.1 Task 1
 * data probe), so an all-null substructure is the NORMAL production shape, not an edge case.
 * Collapsing null to {@code 0} would fabricate a $0 total for every real road detail in the system.
 *
 * <p>The legacy semantics, reproduced exactly:
 * <ul>
 *   <li>{@code CoreUtil.sumBigDecimalValues} (:347-361) — sums only non-null terms and returns
 *       {@code null} when EVERY term is null. One non-null term makes the sum non-null.</li>
 *   <li>{@code CoreUtil.bigDecimalSubtraction} (:379-389) — a null subtrahend returns the minuend
 *       unchanged; a null minuend returns {@code null}. It is NOT symmetric.</li>
 *   <li>{@code CoreUtil.bigDecimalDivision} (:413-424) — returns {@code null} if either operand is
 *       null AND, critically, if the denominator is zero. Division never throws.</li>
 *   <li>{@code getMaterialTypeTotal} (:634-647) — deliberately UNLIKE the rules above: it is
 *       {@code int} arithmetic that coerces nulls to {@code 0} and is therefore ALWAYS non-null.
 *       That asymmetry is legacy behaviour, not an oversight here.</li>
 * </ul>
 */
final class Schedule10Amounts {

  /** Legacy {@code CoreUtil.roundBigDecimal} scale for derived money/rate values. */
  private static final int DERIVED_SCALE = 2;

  /** Legacy divides at scale 10 HALF_UP before rounding ({@code bigDecimalDivision} :418). */
  private static final int DIVISION_SCALE = 10;

  private Schedule10Amounts() {
  }

  /**
   * Sums the non-null terms, or returns {@code null} when every term is null.
   *
   * <p>Verbatim {@code CoreUtil.sumBigDecimalValues} (:347-361): the legacy method tracks an
   * {@code itemAdded} flag and returns {@code null} rather than {@code ZERO} when nothing was
   * added.
   *
   * @param values the terms, any of which may be {@code null}
   * @return the rounded sum, or {@code null} if all terms were null
   */
  static BigDecimal sum(BigDecimal... values) {
    BigDecimal total = BigDecimal.ZERO;
    boolean itemAdded = false;
    for (BigDecimal value : values) {
      if (value != null) {
        itemAdded = true;
        total = total.add(value);
      }
    }
    return itemAdded ? round(total) : null;
  }

  /**
   * Subtracts with legacy's asymmetric null handling.
   *
   * <p>Verbatim {@code CoreUtil.bigDecimalSubtraction} (:379-389): a null {@code subtract} yields
   * {@code total} unchanged (NOT null), while a null {@code total} yields {@code null} regardless
   * of
   * {@code subtract}.
   *
   * @param total the minuend
   * @param subtract the subtrahend
   * @return the rounded difference, or {@code null} per the rule above
   */
  static BigDecimal subtract(BigDecimal total, BigDecimal subtract) {
    if (total != null && subtract == null) {
      return total;
    } else if (total != null) {
      return round(total.subtract(subtract));
    }
    return null;
  }

  /**
   * Divides, returning {@code null} on a null operand or a zero denominator.
   *
   * <p>Verbatim {@code CoreUtil.bigDecimalDivision} (:413-424). The zero-denominator branch is why
   * a
   * road detail with a cost but no length serves a {@code null} rate instead of throwing
   * {@code ArithmeticException} — a road with zero length is real data, not a bug.
   *
   * @param numerator the dividend
   * @param denominator the divisor
   * @return the rounded quotient, or {@code null}
   */
  static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null) {
      return null;
    }
    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return round(numerator.divide(denominator, DIVISION_SCALE, RoundingMode.HALF_UP));
  }

  /**
   * Sub-grade total costs: actual + tree-to-truck transfer + other transfer.
   *
   * <p>Verbatim {@code getSubGradeTotalCosts} (:1170-1178). Transfers may be negative (BR-09), so
   * this total can legitimately be less than the actual cost alone.
   *
   * @param actualCost the sub-grade actual cost
   * @param ttTransfer the tree-to-truck transfer (may be negative)
   * @param otherTransfer the other transfer (may be negative)
   * @return the total, or {@code null} if all three are null
   */
  static BigDecimal subGradeTotalCosts(
      BigDecimal actualCost, BigDecimal ttTransfer, BigDecimal otherTransfer) {
    return sum(actualCost, ttTransfer, otherTransfer);
  }

  /**
   * Sub-grade total deductions: the six "Less" lines.
   *
   * <p>Verbatim {@code getSubGradeTotalDeductions} (:1180-1190). NOTE the six lines do not all live
   * under one cost subcategory — {@code lessOtherEngineering} is stored under subcategory
   * {@code '3'} ({@code Schedule10_OtherSubGrade}, cost item 4) while the other five sit under
   * subcategory {@code '1'}. A read that scanned only subcategory 1 would silently under-count.
   *
   * @param lessBridges deduction for bridges
   * @param lessCulverts deduction for culverts
   * @param lessLandings deduction for landings
   * @param lessEndHaul deduction for end haul
   * @param lessOverland deduction for overland
   * @param lessOtherEngineering deduction for other engineering (subcategory 3)
   * @return the total deductions, or {@code null} if all six are null
   */
  static BigDecimal subGradeTotalDeductions(
      BigDecimal lessBridges,
      BigDecimal lessCulverts,
      BigDecimal lessLandings,
      BigDecimal lessEndHaul,
      BigDecimal lessOverland,
      BigDecimal lessOtherEngineering) {
    // Legacy order: bridges, culverts, landings, endHaul, overLand, otherEngineering (:1183-1188).
    return sum(lessBridges, lessCulverts, lessLandings, lessEndHaul, lessOverland,
        lessOtherEngineering);
  }

  /**
   * Sub-grade total: costs minus deductions. Verbatim {@code getSubGradeTotal} (:1192-1195).
   *
   * @param totalCosts the sub-grade total costs
   * @param totalDeductions the sub-grade total deductions
   * @return the net total, or {@code null}
   */
  static BigDecimal subGradeTotal(BigDecimal totalCosts, BigDecimal totalDeductions) {
    return subtract(totalCosts, totalDeductions);
  }

  /**
   * Stabilizing total: actual + tree-to-truck transfer + other transfer. There is no deduction leg
   * on the stabilizing substructure. Verbatim {@code getStabilizingTotal} (:1289-1295).
   *
   * @param actualCost the stabilizing actual cost
   * @param ttTransfer the tree-to-truck transfer
   * @param otherTransfer the other transfer
   * @return the total, or {@code null} if all three are null
   */
  static BigDecimal stabilizingTotal(
      BigDecimal actualCost, BigDecimal ttTransfer, BigDecimal otherTransfer) {
    return sum(actualCost, ttTransfer, otherTransfer);
  }

  /**
   * Cost per kilometre for either substructure. Verbatim {@code getSubGradeCostPerLength}
   * (:1197-1201) and {@code getStablizingCostPerLength} (:1297-1301) — the legacy misspelling
   * "Stablizing" is not reproduced in this name because it is not user-facing.
   *
   * @param total the substructure total
   * @param length the substructure length in km
   * @return the rate, or {@code null} on a null operand or zero length
   */
  static BigDecimal costPerLength(BigDecimal total, BigDecimal length) {
    return divide(total, length);
  }

  /**
   * Material composition total percentage.
   *
   * <p>Verbatim {@code getMaterialTypeTotal} (:634-647). <strong>Deliberately unlike every other
   * method here:</strong> legacy uses {@code int} arithmetic with each null term coerced to
   * {@code 0}, so the result is ALWAYS non-null — a road detail with no material percentages
   * recorded serves {@code 0}, not {@code null}. Legacy performs no "sums to 100" validation, so a
   * total of 87 or 140 is served as-is.
   *
   * @param percentages the five composition percentages, any of which may be {@code null}
   * @return the total, never {@code null}
   */
  static Integer materialTypeTotal(Integer... percentages) {
    int sum = 0;
    for (Integer percentage : percentages) {
      sum += percentage != null ? percentage : 0;
    }
    return sum;
  }

  /** Legacy {@code CoreUtil.roundBigDecimal} — scale 2, HALF_UP. */
  private static BigDecimal round(BigDecimal value) {
    return value == null ? null : value.setScale(DERIVED_SCALE, RoundingMode.HALF_UP);
  }
}
