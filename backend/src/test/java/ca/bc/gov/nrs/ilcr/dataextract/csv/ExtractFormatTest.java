package ca.bc.gov.nrs.ilcr.dataextract.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the cell formatters transcribed from legacy {@code CoreUtil}.
 *
 * <p>The values here are chosen at the rounding boundaries, because that is the only place a
 * transcription can be wrong without looking wrong. {@code DecimalFormat} rounds HALF_EVEN and the
 * division helpers round HALF_UP, so the two disagree on an exact half — and legacy's choice of
 * which to use for which cell is the thing being preserved.
 */
@DisplayName("ExtractFormat — legacy CoreUtil cell formatting")
class ExtractFormatTest {

  private static BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }

  @Nested
  @DisplayName("whole numbers (###,###,##0)")
  class Whole {

    @Test
    @DisplayName("groups thousands and drops the decimals")
    void groupsAndTruncates() {
      assertThat(ExtractFormat.whole(decimal("1234567"))).isEqualTo("1,234,567");
      assertThat(ExtractFormat.whole(decimal("999"))).isEqualTo("999");
      assertThat(ExtractFormat.whole(decimal("0"))).isEqualTo("0");
    }

    @Test
    @DisplayName("rounds HALF_EVEN, as DecimalFormat's default does")
    void roundsHalfEven() {
      // 1234.5 to zero places is 1234 under HALF_EVEN (1234 is even) and would be 1235 under
      // HALF_UP. Legacy left DecimalFormat on its default, so the even-ward answer is correct.
      assertThat(ExtractFormat.whole(decimal("1234.5"))).isEqualTo("1,234");
      assertThat(ExtractFormat.whole(decimal("1235.5"))).isEqualTo("1,236");
      assertThat(ExtractFormat.whole(decimal("1234.6"))).isEqualTo("1,235");
    }

    @Test
    @DisplayName("a null is the null marker")
    void nullIsMarker() {
      assertThat(ExtractFormat.whole(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("a negative keeps its sign")
    void keepsNegativeSign() {
      assertThat(ExtractFormat.whole(decimal("-1234"))).isEqualTo("-1,234");
    }

    @Test
    @DisplayName("an Integer or Long formats like a BigDecimal of the same value")
    void acceptsBoxedIntegers() {
      // The owner DTOs mix Integer, Long and BigDecimal; a boxed int must not go through
      // Number.toString and pick up a different shape.
      assertThat(ExtractFormat.whole(1234)).isEqualTo("1,234");
      assertThat(ExtractFormat.whole(1234L)).isEqualTo("1,234");
    }
  }

  @Nested
  @DisplayName("two decimals (###,###,##0.00)")
  class TwoDecimals {

    @Test
    @DisplayName("always shows exactly two decimal places")
    void alwaysTwoPlaces() {
      assertThat(ExtractFormat.twoDecimals(decimal("1234.5"))).isEqualTo("1,234.50");
      assertThat(ExtractFormat.twoDecimals(decimal("1234"))).isEqualTo("1,234.00");
      assertThat(ExtractFormat.twoDecimals(decimal("0.005"))).isEqualTo("0.00");
    }

    @Test
    @DisplayName("a null is the null marker")
    void nullIsMarker() {
      assertThat(ExtractFormat.twoDecimals(null)).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the Excel-formula cell")
  class Formula {

    @Test
    @DisplayName("wraps the two-decimal form as an Excel formula")
    void wrapsAsFormula() {
      // Schedules 4 and 6 only. The wrapper is what stops a spreadsheet dropping the trailing
      // zeros of a rate; the writer then doubles these inner quotes.
      assertThat(ExtractFormat.formula(decimal("1234.56"))).isEqualTo("=\"1,234.56\"");
      assertThat(ExtractFormat.formula(decimal("1234.5"))).isEqualTo("=\"1,234.50\"");
    }

    @Test
    @DisplayName("a null is the WRAPPED null marker, not a bare one")
    void nullIsWrappedMarker() {
      // Legacy wrapped the marker along with everything else, so an absent rate in one of these
      // cells reads ="-" rather than -. Preserved verbatim; the three distance-gated Schedule 4
      // cells are the only ones that produce the bare marker, and they do it by their own guard.
      assertThat(ExtractFormat.formula(null)).isEqualTo("=\"-\"");
    }
  }

  @Nested
  @DisplayName("Schedule 11's own patterns")
  class Schedule11Patterns {

    @Test
    @DisplayName("area uses #,###,##0 and money uses #,###,##0.00")
    void usesItsOwnPatterns() {
      assertThat(ExtractFormat.area(decimal("12345.6"))).isEqualTo("12,346");
      assertThat(ExtractFormat.moneyTwoDecimals(decimal("12345.6"))).isEqualTo("12,345.60");
      assertThat(ExtractFormat.area(null)).isEqualTo("-");
      assertThat(ExtractFormat.moneyTwoDecimals(null)).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("division")
  class Division {

    @Test
    @DisplayName("divides at scale 10 then rounds to two places, HALF_UP")
    void dividesLegacyStyle() {
      // The legacy worked example: 1234.567 over 10 is 123.4567, which rounds to 123.46. Doing it
      // in one step at scale 2 would give the same answer here; the two-step matters when the
      // quotient repeats, which is why the intermediate scale is pinned rather than assumed.
      assertThat(ExtractFormat.divide(decimal("1234.567"), decimal("10")))
          .isEqualByComparingTo("123.46");
      assertThat(ExtractFormat.divide(decimal("10"), decimal("3"))).isEqualByComparingTo("3.33");
      assertThat(ExtractFormat.divide(decimal("1"), decimal("8"))).isEqualByComparingTo("0.13");
    }

    @Test
    @DisplayName("a zero divisor is null, not an exception")
    void zeroDivisorIsNull() {
      // A zero volume is ordinary data, so this guard is load-bearing: without it the whole
      // extract would fail on one row's arithmetic.
      assertThat(ExtractFormat.divide(decimal("100"), decimal("0"))).isNull();
      assertThat(ExtractFormat.divide(decimal("100"), BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("a null operand on either side is null")
    void nullOperandIsNull() {
      assertThat(ExtractFormat.divide(null, decimal("10"))).isNull();
      assertThat(ExtractFormat.divide(decimal("10"), null)).isNull();
    }

    @Test
    @DisplayName("the no-rounding variant divides at scale 15")
    void divideNoRoundingKeepsScale() {
      // Used by the sub-page Total: rows, where legacy took a ratio of sums at this scale.
      assertThat(ExtractFormat.divideNoRounding(decimal("1"), decimal("3")).scale()).isEqualTo(15);
      assertThat(ExtractFormat.divideNoRounding(decimal("100"), decimal("0"))).isNull();
    }
  }

  @Nested
  @DisplayName("text sanitising")
  class Text {

    @Test
    @DisplayName("a tab becomes two spaces and a newline becomes one space")
    void sanitisesWhitespace() {
      assertThat(ExtractFormat.sanitize("a\tb")).isEqualTo("a  b");
      assertThat(ExtractFormat.sanitize("a\nb")).isEqualTo("a b");
      assertThat(ExtractFormat.sanitize("a&nbsp;b")).isEqualTo("ab");
    }

    @Test
    @DisplayName("a carriage return is NOT stripped")
    void leavesCarriageReturn() {
      // Legacy's replaceCharsForExtractFormat handled \n and never \r, so a Windows-entered comment
      // carried its CR into the file. On the keep-verbatim list.
      assertThat(ExtractFormat.sanitize("a\r\nb")).isEqualTo("a\r b");
    }

    @Test
    @DisplayName("text() treats null and empty as absent, but not blank")
    void textGuardsNullAndEmpty() {
      assertThat(ExtractFormat.text(null)).isEqualTo("-");
      assertThat(ExtractFormat.text("")).isEqualTo("-");
      // A single space is NOT absent to this guard — legacy's one-argument helper did not trim.
      assertThat(ExtractFormat.text(" ")).isEqualTo(" ");
    }

    @Test
    @DisplayName("textTrimmed() also treats blank as absent")
    void textTrimmedGuardsBlank() {
      // The two-argument legacy helper did trim, and its cells are the ones using this.
      assertThat(ExtractFormat.textTrimmed("   ")).isEqualTo("-");
      assertThat(ExtractFormat.textTrimmed(null)).isEqualTo("-");
      assertThat(ExtractFormat.textTrimmed("ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("raw() treats empty as absent; nullOnly() does not")
    void rawAndNullOnlyDifferOnEmpty() {
      // Legacy guarded some cells with its own null-or-empty helper and others with a plain null
      // check, and the difference is visible in the file: the sub-page location and camp names take
      // the plain check, so an empty name is an empty cell rather than a dash.
      assertThat(ExtractFormat.raw("")).isEqualTo("-");
      assertThat(ExtractFormat.nullOnly("")).isEmpty();
      assertThat(ExtractFormat.raw(null)).isEqualTo("-");
      assertThat(ExtractFormat.nullOnly(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("plain() renders a number by toString with the null marker")
    void plainRendersByToString() {
      // The Schedule 10 Road raw cells: legacy called String.valueOf, so no grouping appears.
      assertThat(ExtractFormat.plain(1234)).isEqualTo("1234");
      assertThat(ExtractFormat.plain(null)).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("defusing spreadsheet formulas in user-entered text")
  class Defuse {

    @Test
    @DisplayName("a leading =, +, @, tab or carriage return gains an apostrophe")
    void formulaTriggersGainApostrophe() {
      // The extract is opened in a spreadsheet by an administrator, and a licensee-typed comment
      // beginning with one of these would be EVALUATED there, not shown. Recorded deviation from
      // legacy, which shared the exposure. The apostrophe is the spreadsheet convention for "text"
      // and is invisible in the cell.
      assertThat(ExtractFormat.defuse("=SUM(A1)")).isEqualTo("'=SUM(A1)");
      assertThat(ExtractFormat.defuse("+1")).isEqualTo("'+1");
      assertThat(ExtractFormat.defuse("@cmd")).isEqualTo("'@cmd");
      assertThat(ExtractFormat.defuse("\tx")).isEqualTo("'\tx");
      assertThat(ExtractFormat.defuse("\rx")).isEqualTo("'\rx");
    }

    @Test
    @DisplayName("a leading minus is defused only when arithmetic could follow it")
    void minusIsDefusedOnlyWhenArithmeticFollows() {
      // -1+1 and -(2) are formulas to a spreadsheet; "- see note" is a dash-led comment, ordinary
      // legacy data that must reach the file untouched.
      assertThat(ExtractFormat.defuse("-1+1")).isEqualTo("'-1+1");
      assertThat(ExtractFormat.defuse("-(2)")).isEqualTo("'-(2)");
      assertThat(ExtractFormat.defuse("- see note")).isEqualTo("- see note");
    }

    @Test
    @DisplayName("plain text, the empty string and null pass through unchanged")
    void harmlessValuesAreUnchanged() {
      assertThat(ExtractFormat.defuse("plain")).isEqualTo("plain");
      assertThat(ExtractFormat.defuse("")).isEmpty();
      assertThat(ExtractFormat.defuse(null)).isNull();
    }

    @Test
    @DisplayName("the null marker never gains an apostrophe")
    void nullMarkerIsNeverDefused() {
      // A bare "-" is legacy's SHOW_WHEN_NULL_VALUE and appears in thousands of cells; the minus
      // rule requires a second character, so the marker can never trip it — directly or through
      // the text helpers that substitute it for an absent value.
      assertThat(ExtractFormat.defuse(ExtractFormat.NULL_VALUE)).isEqualTo("-");
      assertThat(ExtractFormat.text(null)).isEqualTo("-");
      assertThat(ExtractFormat.raw("")).isEqualTo("-");
      assertThat(ExtractFormat.nullOnly(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("every text helper defuses; the formula cell keeps its leading =")
    void textHelpersDefuse_formulaDoesNot() {
      // Applied by the text helpers ONLY: formula()'s leading = is the point of that cell, and the
      // numeric formatters' leading - is a sign, so neither goes through defuse.
      assertThat(ExtractFormat.text("=x")).isEqualTo("'=x");
      assertThat(ExtractFormat.raw("=x")).isEqualTo("'=x");
      assertThat(ExtractFormat.nullOnly("=x")).isEqualTo("'=x");
      assertThat(ExtractFormat.textTrimmed("=x")).isEqualTo("'=x");
      assertThat(ExtractFormat.formula(new BigDecimal("1"))).startsWith("=\"");
      assertThat(ExtractFormat.whole(decimal("-1"))).isEqualTo("-1");
    }

    @Test
    @DisplayName("text() sanitises BEFORE defusing, so a tab-led value is already harmless")
    void textSanitisesBeforeDefusing() {
      // Order matters and is pinned: sanitize turns the leading tab into two spaces, and a
      // space-led cell is not a formula, so text() adds no apostrophe where raw() would.
      assertThat(ExtractFormat.text("\tx")).isEqualTo("  x");
      assertThat(ExtractFormat.raw("\tx")).isEqualTo("'\tx");
    }
  }

  @Nested
  @DisplayName("summing")
  class Summing {

    @Test
    @DisplayName("sumCosts rounds each term to whole before adding")
    void sumCostsRoundsEachTerm() {
      // Per-term rounding, not round-at-the-end: 0.5 + 0.5 is 1 + 1 = 2 here, where summing first
      // would give 1. Legacy rounded each cost as it added it.
      assertThat(ExtractFormat.sumCosts(List.of(decimal("0.5"), decimal("0.5"))))
          .isEqualByComparingTo("2");
      assertThat(ExtractFormat.sumCosts(List.of(decimal("10"), decimal("20"))))
          .isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("a list with nothing to add is null, not zero")
    void nothingAddedIsNull() {
      // The distinction reaches the file: null renders as the marker, zero renders as 0.
      assertThat(ExtractFormat.sumCosts(List.of())).isNull();
      assertThat(ExtractFormat.sumCosts(Arrays.asList((Number) null, null))).isNull();
    }

    @Test
    @DisplayName("nulls among real terms are skipped, not treated as zero-and-absent")
    void skipsNullsAmongTerms() {
      assertThat(ExtractFormat.sumCosts(Arrays.asList(decimal("10"), null, decimal("5"))))
          .isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("sumCostsTwoDecimals keeps two places per term")
    void sumTwoDecimalsRoundsToTwo() {
      assertThat(ExtractFormat.sumCostsTwoDecimals(List.of(decimal("0.005"), decimal("0.005"))))
          .isEqualByComparingTo("0.02");
      assertThat(ExtractFormat.sumCostsTwoDecimals(List.of())).isNull();
    }

    @Test
    @DisplayName("sumAreas sums first and rounds once, to one place")
    void sumAreasRoundsOnce() {
      // Unlike the cost sums: areas are added at full precision and the total rounded, which is a
      // different answer from rounding each term.
      assertThat(ExtractFormat.sumAreas(List.of(decimal("0.05"), decimal("0.05"))))
          .isEqualByComparingTo("0.1");
      assertThat(ExtractFormat.sumAreas(List.of())).isNull();
    }
  }

  @Nested
  @DisplayName("addition and subtraction")
  class AddAndSubtract {

    @Test
    @DisplayName("either side null makes the result null")
    void nullPropagates() {
      // Not treated as zero: an absent figure must not be reported as if it were nil.
      assertThat(ExtractFormat.add(decimal("1"), null)).isNull();
      assertThat(ExtractFormat.add(null, decimal("1"))).isNull();
      assertThat(ExtractFormat.subtract(decimal("1"), null)).isNull();
      assertThat(ExtractFormat.subtract(null, decimal("1"))).isNull();
    }

    @Test
    @DisplayName("otherwise adds and subtracts exactly")
    void computesExactly() {
      assertThat(ExtractFormat.add(decimal("1.5"), decimal("2.25"))).isEqualByComparingTo("3.75");
      assertThat(ExtractFormat.subtract(decimal("3"), decimal("1.25")))
          .isEqualByComparingTo("1.75");
    }
  }

  @Nested
  @DisplayName("the marker constants")
  class Markers {

    @Test
    @DisplayName("are legacy's strings, asterisks and spacing included")
    void markersAreVerbatim() {
      // Compared against legacy samples character for character, so the asterisk counts and the
      // inner spaces are part of the contract.
      assertThat(ExtractFormat.NO_DATA_FOUND).isEqualTo("*** NO DATA FOUND ***");
      assertThat(ExtractFormat.NO_SCHEDULE_3).isEqualTo("*** NO SCHEDULE 3 ***");
      assertThat(ExtractFormat.NULL_VALUE).isEqualTo("-");
    }
  }
}
