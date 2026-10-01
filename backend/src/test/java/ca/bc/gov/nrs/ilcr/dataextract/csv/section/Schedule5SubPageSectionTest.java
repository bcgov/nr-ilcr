package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule5SubPageSection.Kind;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageDocument;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two legacy builders this one class replaces — {@code Schedule5CampExpensesExtract} and {@code
 * Schedule5AccessExtract} — each pinned on its own, including the description guard they disagree
 * about and the six-cell marker they agree on.
 *
 * <p>Every expected header is written out literally rather than read from the production constant,
 * so that an edit to the columns fails here instead of agreeing with itself.
 */
@DisplayName("Schedule5SubPageSection — two legacy builders, one shape")
class Schedule5SubPageSectionTest {

  private static final RowContext CTX = new RowContext("673", "2021", "Submitted", "8201");

  private static final String CAMP = "Cedar Flats Camp";

  /** A description carrying both characters the legacy sanitiser rewrites. */
  private static final String WHITESPACE_DESCRIPTION = "Linen\tservice\nweekly";

  @Nested
  @DisplayName("Camp Expenses (item 62)")
  class CampExpenses {

    private final Schedule5SubPageSection section = new Schedule5SubPageSection(Kind.CAMP);

    @Test
    @DisplayName("the title is legacy's Camp Expenses banner")
    void title_isTheCampBanner() {
      assertThat(section.title()).isEqualTo("**** Schedule 5 - Camp Expenses ****");
    }

    @Test
    @DisplayName("the header is legacy's nine sub-page columns, in order")
    void header_isTheNineLegacyColumns() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "CAMP",
        "DESCRIPTION",
        "VOL_M3",
        "COST_$",
        "CPU_$/M3"
      };

      assertThat(section.header()).hasSize(9).containsExactly(expected);
    }

    @Test
    @DisplayName("the whole-schedule marker is SIX cells, as it is on Access")
    void marker_isSixCells() {
      // Six on both sub-pages and FIVE on the main Schedule 5 section, which keeps the default.
      // Schedule5SectionTest pins that five; the widths differ in the legacy source.
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(6)
          .containsExactly(
              "Included Mills: 673", "2020 - 2021", "-", "-", "-", "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("the item rows are followed by the Total row")
    void rows_areTheItemsThenTheTotal() {
      List<String[]> rows = section.rows(CTX, camp(CAMP), document(twoItems()));

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0))
          .containsExactly(
              "673",
              "2021",
              "Submitted",
              "8201",
              CAMP,
              // The Camp builder wrote the description RAW, so both whitespace characters
              // survive into the cell. The Access nested class pins the other half of this.
              WHITESPACE_DESCRIPTION,
              "1,000",
              "5,000",
              "5.00");
      assertThat(rows.get(1))
          .containsExactly(
              "673",
              "2021",
              "Submitted",
              "8201",
              CAMP,
              "Satellite internet",
              "200",
              "2,500",
              "12.53");
      assertThat(rows.get(2))
          .containsExactly(
              "",
              "",
              "",
              "",
              "",
              "Total:",
              // 1,000.40 and 199.60, each rounded to a whole number before summing.
              "1,200",
              "7,500",
              // 7,500.00 / 1,200.00 — the ratio of the summed cost to the summed volume. The
              // two per-row rates were 5.00 and 12.53, so neither their sum (17.53) nor their
              // mean (8.77) can produce this figure.
              "6.25");
    }

    @Test
    @DisplayName("the description is written RAW, so a null one is an empty field")
    void nullDescription_isAnEmptyField() {
      List<String[]> rows =
          section.rows(CTX, camp(CAMP), document(List.of(item(null, "100", 500, "5.00"))));

      // Not the "-" marker and not an empty string: the raw value goes straight through, and
      // CsvWriter writes a null cell as nothing between its delimiters.
      assertThat(rows.get(0)[5]).isNull();
    }
  }

  @Nested
  @DisplayName("Access Expenses (item 68)")
  class AccessExpenses {

    private final Schedule5SubPageSection section = new Schedule5SubPageSection(Kind.ACCESS);

    @Test
    @DisplayName("the title is legacy's Access Expenses banner")
    void title_isTheAccessBanner() {
      assertThat(section.title()).isEqualTo("**** Schedule 5 - Access Expenses ****");
    }

    @Test
    @DisplayName("the header is legacy's nine sub-page columns, in order")
    void header_isTheNineLegacyColumns() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "CAMP",
        "DESCRIPTION",
        "VOL_M3",
        "COST_$",
        "CPU_$/M3"
      };

      assertThat(section.header()).hasSize(9).containsExactly(expected);
    }

    @Test
    @DisplayName("the whole-schedule marker is SIX cells, as it is on Camp")
    void marker_isSixCells() {
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(6)
          .containsExactly(
              "Included Mills: 673", "2020 - 2021", "-", "-", "-", "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("the item rows are followed by the Total row")
    void rows_areTheItemsThenTheTotal() {
      List<String[]> rows = section.rows(CTX, camp(CAMP), document(twoItems()));

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0))
          .containsExactly(
              "673",
              "2021",
              "Submitted",
              "8201",
              CAMP,
              // The Access builder SANITISED the same description the Camp builder wrote raw:
              // tab to two spaces, newline to one space. The asymmetry is legacy's.
              "Linen  service weekly",
              "1,000",
              "5,000",
              "5.00");
      assertThat(rows.get(2))
          .containsExactly("", "", "", "", "", "Total:", "1,200", "7,500", "6.25");
    }

    @Test
    @DisplayName("the description is sanitised, so a null one becomes the null marker")
    void nullDescription_isTheNullMarker() {
      List<String[]> rows =
          section.rows(CTX, camp(CAMP), document(List.of(item(null, "100", 500, "5.00"))));

      assertThat(rows.get(0)[5]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the per-camp marker and the camp cell")
  class PerCampMarker {

    private final Schedule5SubPageSection section = new Schedule5SubPageSection(Kind.CAMP);

    @Test
    @DisplayName("a null document yields the six-cell per-camp marker")
    void nullDocument_yieldsTheMarker() {
      List<String[]> rows = section.rows(CTX, camp(CAMP), null);

      // Not padded out to the nine-column header: the four context cells, the camp and the
      // marker are exactly what a legacy sample holds.
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0))
          .hasSize(6)
          .containsExactly("673", "2021", "Submitted", "8201", CAMP, "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("a null row list and an empty row list are the same as no document")
    void nullOrEmptyRowList_yieldsTheMarker() {
      assertThat(section.rows(CTX, camp(CAMP), document(null)).get(0))
          .containsExactly("673", "2021", "Submitted", "8201", CAMP, "*** NO DATA FOUND ***");
      assertThat(section.rows(CTX, camp(CAMP), document(List.of())).get(0))
          .containsExactly("673", "2021", "Submitted", "8201", CAMP, "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("a null camp name is the null marker")
    void nullCampName_isTheNullMarker() {
      assertThat(section.rows(CTX, camp(null), null).get(0)[4]).isEqualTo("-");
    }

    @Test
    @DisplayName("an EMPTY camp name is an empty cell, not the null marker")
    void emptyCampName_isAnEmptyCell() {
      // Legacy guarded this cell on null ALONE rather than with its own isNullOrEmptyString
      // helper, so an empty name reaches the file as an empty quoted cell.
      assertThat(section.rows(CTX, camp(""), null).get(0)[4]).isEmpty();
    }
  }

  /**
   * The Total row's three sums use two different legacy helpers, and the fixture here is chosen so
   * that swapping them is visible. Kind is irrelevant to the total row, so Camp stands for both.
   */
  @Nested
  @DisplayName("the Total row's CPU divides the 2-dp sums, not the whole-rounded ones it shows")
  class TotalRowCpu {

    private final Schedule5SubPageSection section = new Schedule5SubPageSection(Kind.CAMP);

    @Test
    @DisplayName("fractional volumes: the visible VOL_M3 total is whole-rounded, the CPU is not")
    void cpu_dividesTwoDecimalSumsWhileTheVisibleTotalsAreWholeRounded() {
      // Cost is Integer (SubPageRow.cost, COST NUMBER(8,0)), so no cost can carry a fraction and
      // sumCosts / sumCostsTwoDecimals agree on the cost sum; VOLUME is BigDecimal, so the
      // discrimination is on volumes. Two rows at 100.40 m3 and 300 dollars each:
      //   sumCosts(volumes)            = 100 + 100       = 200     (each term whole-rounded)
      //   sumCostsTwoDecimals(volumes) = 100.40 + 100.40 = 200.80  (each term 2-dp)
      //   sumCosts(costs)              = 300 + 300       = 600
      //   CPU = 600.00 / 200.80 = 2.98804780…  -> twoDecimals -> "2.99"
      // Dividing by the whole-rounded 200 gives exactly 3.00, so using sumCosts for the CPU's
      // divisor fails here. The visible VOL_M3 total is that whole-rounded 200 and the visible
      // COST_$ total the whole-rounded 600 (Schedule5CampExtract's sumBigDecimalCosts), so the
      // file's own totals do not reproduce its CPU — legacy's arithmetic, kept.
      List<SubPageRow> items =
          List.of(item("Propane", "100.40", 300, "2.99"), item("Water", "100.40", 300, "2.99"));

      List<String[]> rows = section.rows(CTX, camp(CAMP), document(items));

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0)[6]).isEqualTo("100"); // the row's own volume, whole
      assertThat(rows.get(2)).containsExactly("", "", "", "", "", "Total:", "200", "600", "2.99");
    }
  }

  private static List<SubPageRow> twoItems() {
    return List.of(
        item(WHITESPACE_DESCRIPTION, "1000.40", 5000, "5.00"),
        item("Satellite internet", "199.60", 2500, "12.53"));
  }

  private static SubPageRow item(
      String description, String volume, Integer cost, String costPerVolume) {
    return new SubPageRow(1, description, dec(volume), cost, dec(costPerVolume));
  }

  /** Only {@code rows} is read here; the rest of the document is the page's own context. */
  private static SubPageDocument document(List<SubPageRow> rows) {
    return new SubPageDocument(8201, CAMP, dec("1000.40"), true, rows, null, null);
  }

  /** A camp carrying only the name the sub-page rows repeat; no category amount is read. */
  private static Camp camp(String name) {
    return new Camp(
        8201, 0, name, null, // roadDistanceToOperatingArea
        null, // sizeOfCamp
        null, // associatedCampVolume
        null, // isolatedCamp
        null, // comments
        null, // cateringAndFood
        null, // wagesAndBenefits
        null, // depreciationLease
        null, // generalCampExpenses
        null, // otherCampExpenses
        null, // campSubTotal
        null, // recoveries
        null, // campTotal
        null, // crewTransportation
        null, // equipAndSuppliesLand
        null, // equipAndSuppliesRail
        null, // equipAndSuppliesAir
        null, // equipAndSuppliesWater
        null, // otherAccessExpenses
        null, // accessExpenseTotal
        null, // campAndAccessTotal
        0, 0);
  }

  private static BigDecimal dec(String value) {
    return value == null ? null : new BigDecimal(value);
  }
}
