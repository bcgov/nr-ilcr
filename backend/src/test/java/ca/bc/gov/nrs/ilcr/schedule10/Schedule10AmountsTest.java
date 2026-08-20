package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the transcribed legacy arithmetic, with particular attention to null semantics.
 *
 * <p>This matters more than it looks, but not in the direction an earlier revision assumed. Schedule
 * 10 has ZERO cost lines in the real delivery database, so an all-absent substructure is the NORMAL
 * production shape — and legacy serves {@code 0.00} for it, because
 * {@code RoadConstructionReportDetailType.getCostValue} (:1160-1168) coerces each absent line to
 * zero before summing. Omitting the totals instead would regress 100% of production rows to blanks.
 *
 * <p>The {@code sum}/{@code subtract}/{@code divide} primitives keep their faithful
 * {@code CoreUtil} null semantics and are tested here as such, but for Schedule 10 the
 * null-return branch of {@code sum} is unreachable — the coercion happens first.
 */
class Schedule10AmountsTest {

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  @Nested
  @DisplayName("sum — CoreUtil.sumBigDecimalValues")
  class Sum {

    @Test
    @DisplayName("all-null returns null, NOT zero")
    void allNullReturnsNull() {
      assertThat(Schedule10Amounts.sum(null, null, null)).isNull();
    }

    @Test
    @DisplayName("one non-null term makes the sum non-null")
    void oneNonNullTermCounts() {
      assertThat(Schedule10Amounts.sum(null, bd("100"), null)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("an explicit zero is a value, so the sum is zero and not null")
    void explicitZeroIsNotNull() {
      assertThat(Schedule10Amounts.sum(BigDecimal.ZERO, null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("negative transfers reduce the sum (BR-09)")
    void negativeTermsAreSummed() {
      assertThat(Schedule10Amounts.sum(bd("150000"), bd("-5000"), bd("2000")))
          .isEqualByComparingTo("147000");
    }
  }

  @Nested
  @DisplayName("subtract — CoreUtil.bigDecimalSubtraction, deliberately asymmetric")
  class Subtract {

    @Test
    @DisplayName("a null subtrahend returns the minuend unchanged, not null")
    void nullSubtrahendReturnsMinuend() {
      assertThat(Schedule10Amounts.subtract(bd("147000"), null)).isEqualByComparingTo("147000");
    }

    @Test
    @DisplayName("a null minuend returns null regardless of the subtrahend")
    void nullMinuendReturnsNull() {
      assertThat(Schedule10Amounts.subtract(null, bd("21000"))).isNull();
      assertThat(Schedule10Amounts.subtract(null, null)).isNull();
    }

    @Test
    void subtractsWhenBothPresent() {
      assertThat(Schedule10Amounts.subtract(bd("147000"), bd("21000")))
          .isEqualByComparingTo("126000");
    }
  }

  @Nested
  @DisplayName("divide — CoreUtil.bigDecimalDivision")
  class Divide {

    @Test
    @DisplayName("a zero denominator returns null rather than throwing")
    void zeroDenominatorReturnsNull() {
      assertThat(Schedule10Amounts.divide(bd("126000"), BigDecimal.ZERO)).isNull();
      // A road of zero length is real data, not a bug — it must not blow up the whole document.
      assertThat(Schedule10Amounts.divide(bd("126000"), bd("0.000"))).isNull();
    }

    @Test
    void nullOperandReturnsNull() {
      assertThat(Schedule10Amounts.divide(null, bd("12.5"))).isNull();
      assertThat(Schedule10Amounts.divide(bd("126000"), null)).isNull();
    }

    @Test
    @DisplayName("divides at scale 10 HALF_UP then rounds to 2")
    void dividesAndRounds() {
      assertThat(Schedule10Amounts.divide(bd("126000"), bd("12.500")))
          .isEqualByComparingTo("10080.00");
      // 40000 / 3.000 = 13333.333... -> 13333.33
      assertThat(Schedule10Amounts.divide(bd("40000"), bd("3.000")))
          .isEqualByComparingTo("13333.33");
    }
  }

  @Nested
  @DisplayName("substructure totals")
  class SubstructureTotals {

    @Test
    @DisplayName("the pinned sub-grade chain for the seeded fixture")
    void pinnedSubGradeChain() {
      BigDecimal totalCosts =
          Schedule10Amounts.subGradeTotalCosts(bd("150000"), bd("-5000"), bd("2000"));
      BigDecimal totalDeductions = Schedule10Amounts.subGradeTotalDeductions(
          bd("1000"), bd("2000"), bd("3000"), bd("6000"), bd("4000"), bd("5000"));
      BigDecimal total = Schedule10Amounts.subGradeTotal(totalCosts, totalDeductions);

      assertThat(totalCosts).isEqualByComparingTo("147000");
      assertThat(totalDeductions).isEqualByComparingTo("21000");
      assertThat(total).isEqualByComparingTo("126000");
      assertThat(Schedule10Amounts.costPerLength(total, bd("12.500")))
          .isEqualByComparingTo("10080.00");
    }

    @Test
    @DisplayName("a detail with no cost lines yields ZERO totals, not absent ones")
    void noCostLinesYieldsZeroTotals() {
      // Legacy getCostValue (:1160-1168) coerces every absent cost line to BigDecimal(0) BEFORE
      // summing, so itemAdded is always true and the null-return branch is unreachable from this
      // schedule. This is the shape of ALL 66 real delivery road-detail rows (zero cost lines), so
      // omitting the totals here would regress 100% of production data to blanks where the legacy
      // screen shows $0.00.
      BigDecimal totalCosts = Schedule10Amounts.subGradeTotalCosts(null, null, null);
      BigDecimal totalDeductions =
          Schedule10Amounts.subGradeTotalDeductions(null, null, null, null, null, null);
      BigDecimal total = Schedule10Amounts.subGradeTotal(totalCosts, totalDeductions);

      assertThat(totalCosts).isEqualByComparingTo("0");
      assertThat(totalDeductions).isEqualByComparingTo("0");
      assertThat(total).isEqualByComparingTo("0");
      // costPerLength still follows bigDecimalDivision: 0.00 over a real length is 0.00 ...
      assertThat(Schedule10Amounts.costPerLength(total, bd("12.500"))).isEqualByComparingTo("0");
      // ... but a null or zero length still yields null, which IS reachable (length is nullable).
      assertThat(Schedule10Amounts.costPerLength(total, null)).isNull();
      assertThat(Schedule10Amounts.costPerLength(total, BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("costs present but no deductions returns the costs minus zero")
    void costsWithoutDeductionsKeepsTotal() {
      BigDecimal totalCosts = Schedule10Amounts.subGradeTotalCosts(bd("150000"), null, null);
      BigDecimal totalDeductions =
          Schedule10Amounts.subGradeTotalDeductions(null, null, null, null, null, null);
      assertThat(totalCosts).isEqualByComparingTo("150000");
      assertThat(totalDeductions).isEqualByComparingTo("0");
      assertThat(Schedule10Amounts.subGradeTotal(totalCosts, totalDeductions))
          .isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("deductions present but no costs yields a NEGATIVE total, not an absent one")
    void deductionsWithoutCostsGoesNegative() {
      // The reverse shape — plausible as the first rows Story 11.2 writes. Under the old
      // null-propagating behaviour this served no total at all; legacy serves -21000.00.
      BigDecimal totalCosts = Schedule10Amounts.subGradeTotalCosts(null, null, null);
      BigDecimal totalDeductions = Schedule10Amounts.subGradeTotalDeductions(
          bd("1000"), bd("2000"), bd("3000"), bd("6000"), bd("4000"), bd("5000"));
      assertThat(Schedule10Amounts.subGradeTotal(totalCosts, totalDeductions))
          .isEqualByComparingTo("-21000");
    }

    @Test
    @DisplayName("stabilizing has no deduction leg, and an empty one totals zero")
    void stabilizingIsAPlainSum() {
      assertThat(Schedule10Amounts.stabilizingTotal(bd("40000"), BigDecimal.ZERO, BigDecimal.ZERO))
          .isEqualByComparingTo("40000");
      assertThat(Schedule10Amounts.stabilizingTotal(null, null, null)).isEqualByComparingTo("0");
    }
  }

  @Nested
  @DisplayName("materialTypeTotal — deliberately unlike every other method here")
  class MaterialTypeTotal {

    @Test
    @DisplayName("nulls coerce to zero and the result is NEVER null")
    void nullsCoerceToZero() {
      assertThat(Schedule10Amounts.materialTypeTotal(null, null, null, null, null)).isZero();
      assertThat(Schedule10Amounts.materialTypeTotal(10, null, 40, null, 10)).isEqualTo(60);
    }

    @Test
    void sumsThePinnedFixture() {
      assertThat(Schedule10Amounts.materialTypeTotal(10, 20, 40, 20, 10)).isEqualTo(100);
    }

    @Test
    @DisplayName("legacy does not validate against 100, so odd totals are served as-is")
    void doesNotValidateAgainstOneHundred() {
      assertThat(Schedule10Amounts.materialTypeTotal(50, 50, 50, 0, 0)).isEqualTo(150);
      assertThat(Schedule10Amounts.materialTypeTotal(1, 1, 1, 1, 1)).isEqualTo(5);
    }
  }
}
