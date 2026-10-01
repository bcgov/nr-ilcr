package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryAmount;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The byte shape of the Schedule 5 section: its 56 legacy columns and the plain two-decimal {@code
 * $/m³} cell that distinguishes it from Schedule 4's Excel-formula one.
 *
 * <p>The expected header is written out literally rather than read from the production constant, so
 * that an edit to the columns fails here instead of agreeing with itself.
 */
@DisplayName("Schedule5Section — the 56-column legacy row")
class Schedule5SectionTest {

  private static final RowContext CTX = new RowContext("673", "2021", "Submitted", "8201");

  private final Schedule5Section section = new Schedule5Section();

  @Nested
  @DisplayName("header and title")
  class HeaderAndTitle {

    @Test
    @DisplayName("the header is legacy's 56 columns, in order")
    void header_isTheFiftySixLegacyColumns() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "CAMP_NAME",
        "ROAD_DIST_KM",
        "CAMP_SIZE_PERS",
        "ASSOC_CAMP_VOL_M3",
        "ISOLATED_CAMP",
        "CATERER_M3",
        "CATERER_$",
        "CATERER_$/M3",
        "WAGES_M3",
        "WAGES_$",
        "WAGES_$/M3",
        "DEPREC_M3",
        "DEPREC_$",
        "DEPREC_$/M3",
        "GEN_EXP_M3",
        "GEN_EXP_$",
        "GEN_EXP_$/M3",
        "OTH_EXP_M3",
        "OTH_EXP_$",
        "OTH_EXP_$/M3",
        "SUB_TOT_M3",
        "SUB_TOT_$",
        "SUB_TOT_$/M3",
        // Recoveries is the volume-less twelfth category, so it gets a cost column and no
        // companion volume or rate column.
        "RECOVERIES_$",
        "CAMP_TOT_M3",
        "CAMP_TOT_$",
        "CAMP_TOT_$/M3",
        "CREW_TRANS_M3",
        "CREW_TRANS_$",
        "CREW_TRANS_$/M3",
        "EQ_LAND_M3",
        "EQ_LAND_$",
        "EQ_LAND_$/M3",
        "EQ_RAIL_M3",
        "EQ_RAIL_$",
        "EQ_RAIL_$/M3",
        "EQ_AIR_M3",
        "EQ_AIR_$",
        "EQ_AIR_$/M3",
        "EQ_WATER_M3",
        "EQ_WATER_$",
        "EQ_WATER_$/M3",
        "OTH_ACCESS_M3",
        "OTH_ACCESS_$",
        "OTH_ACCESS_$/M3",
        "ACCESS_TOT_M3",
        "ACCESS_TOT_$",
        "ACCESS_TOT_$/M3",
        "TOT_EXP_M3",
        "TOT_EXP_$",
        "TOT_EXP_$/M3",
        "COMMENTS"
      };

      assertThat(section.header()).hasSize(56).containsExactly(expected);
    }

    @Test
    @DisplayName("the header is handed out as a copy, so a caller cannot edit the constant")
    void header_isDefensivelyCopied() {
      String[] first = section.header();
      first[0] = "MUTATED";

      assertThat(section.header()[0]).isEqualTo("MILL_NUMBER");
    }

    @Test
    @DisplayName("the title is legacy's banner cell")
    void title_isTheLegacyBanner() {
      assertThat(section.title()).isEqualTo("**** Schedule 5 ****");
    }
  }

  @Nested
  @DisplayName("the data row")
  class DataRow {

    @Test
    @DisplayName("a populated camp fills all 56 cells")
    void row_isTheFullFiftySixCells() {
      String[] expected = {
        // The RowContext cells.
        "673",
        "2021",
        "Submitted",
        "8201",
        // CAMP_NAME goes through the legacy sanitiser, so plain text passes unchanged.
        "Cedar Flats Camp",
        // The four camp descriptors: 120.4 km rounds to a whole cell.
        "120",
        "48",
        "1,000",
        "Yes",
        // Catering and Food. Every volume here is the camp's own associated volume, as the
        // screen shows it; the costs differ so a column slip cannot pass.
        "1,000",
        "25,000",
        "24.99",
        // Wages and Benefits.
        "1,000",
        "31,000",
        "30.99",
        // Depreciation and Lease.
        "1,000",
        "4,000",
        "4.00",
        // General Camp Expenses.
        "1,000",
        "1,500",
        "1.50",
        // Other Camp Expenses — the item-62 row sum.
        "1,000",
        "900",
        "0.90",
        // Camp Sub-Total.
        "1,000",
        "62,400",
        "62.37",
        // Recoveries: its cost alone. Stored positive and subtracted from the sub-total.
        "400",
        // Camp Total = sub-total - recoveries.
        "1,000",
        "62,000",
        "61.98",
        // Crew Transportation.
        "1,000",
        "3,000",
        "3.00",
        // Equipment and Supplies — Land, Rail, Air, Water.
        "1,000",
        "2,500",
        "2.50",
        "1,000",
        "1,200",
        "1.20",
        "1,000",
        "800",
        "0.80",
        "1,000",
        "600",
        "0.60",
        // Other Access Expenses — the item-68 row sum.
        "1,000",
        "450",
        "0.45",
        // Access Expense Total.
        "1,000",
        "8,550",
        "8.55",
        // Camp and Access Total.
        "1,000",
        "70,550",
        "70.52",
        // COMMENTS: tab to two spaces, newline to one space.
        "Fly-in camp  seasonal only see notes"
      };

      assertThat(section.row(CTX, populatedCamp()))
          .hasSize(section.header().length)
          .containsExactly(expected);
    }

    @Test
    @DisplayName("the isolated-camp indicator is Yes, No, or the null marker")
    void isolatedCamp_isYesNoOrTheNullMarker() {
      assertThat(cell(row(camp(Boolean.TRUE)), "ISOLATED_CAMP")).isEqualTo("Yes");
      assertThat(cell(row(camp(Boolean.FALSE)), "ISOLATED_CAMP")).isEqualTo("No");
      // The delivery column is NOT NULL DEFAULT 'N', so null is only a defensive case — but
      // legacy's unguarded equals() would have failed the whole page on one.
      assertThat(cell(row(camp((Boolean) null)), "ISOLATED_CAMP")).isEqualTo("-");
    }

    @Test
    @DisplayName("an absent category dashes its volume, cost and rate")
    void absentCategory_isDashedThroughout() {
      String[] row = row(camp(Boolean.FALSE));

      assertThat(cell(row, "CATERER_M3")).isEqualTo("-");
      assertThat(cell(row, "CATERER_$")).isEqualTo("-");
      // Schedule 5's rate cell is the ORDINARY two-decimal cell, so its null is the bare
      // marker — not the Excel-wrapped ="-" Schedule 4 produces for the same absence.
      assertThat(cell(row, "CATERER_$/M3")).isEqualTo("-");
      assertThat(cell(row, "TOT_EXP_$/M3")).isEqualTo("-");
    }

    @Test
    @DisplayName("Recoveries contributes only its cost cell")
    void recoveries_contributesOnlyItsCostCell() {
      CategoryAmount[] amounts = noAmounts();
      amounts[6] = amount("999", 400L, "9.99");

      String[] row = section.row(CTX, camp("Cedar Flats Camp", null, amounts));

      assertThat(cell(row, "RECOVERIES_$")).isEqualTo("400");
      // Neither figure has a column of its own, so neither can appear anywhere in the row.
      assertThat(row).doesNotContain("999").doesNotContain("9.99");
    }

    @Test
    @DisplayName("the camp name and comments are sanitised, and a blank one is the null marker")
    void campNameAndComments_areSanitised() {
      String[] sanitised = section.row(CTX, camp("Cedar\tFlats\nCamp", "a\tb\nc", noAmounts()));
      String[] blank = section.row(CTX, camp(null, "", noAmounts()));

      assertThat(cell(sanitised, "CAMP_NAME")).isEqualTo("Cedar  Flats Camp");
      assertThat(cell(sanitised, "COMMENTS")).isEqualTo("a  b c");
      // Unlike the sub-page CAMP cell, the main row's name goes through the sanitising guard,
      // so a null name IS the marker here.
      assertThat(cell(blank, "CAMP_NAME")).isEqualTo("-");
      assertThat(cell(blank, "COMMENTS")).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the whole-schedule marker")
  class WholeScheduleMarker {

    @Test
    @DisplayName("is five cells wide — Schedule 5 does not override the default")
    void marker_isFiveCells() {
      // Its two sub-page builders both emit SIX; Schedule5SubPageSectionTest pins those. The
      // difference is legacy's, so neither width may be levelled to match the other.
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(5)
          .containsExactly("Included Mills: 673", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***");
    }
  }

  private String[] row(Camp camp) {
    return section.row(CTX, camp);
  }

  /** A camp with every category absent, carrying only the isolated-camp indicator under test. */
  private static Camp camp(Boolean isolated) {
    return camp("Cedar Flats Camp", "120.4", 48, "1000.40", isolated, null, noAmounts());
  }

  private static Camp camp(String name, String comments, CategoryAmount[] amounts) {
    return camp(name, "120.4", 48, "1000.40", Boolean.TRUE, comments, amounts);
  }

  private static Camp populatedCamp() {
    CategoryAmount[] amounts = {
      amount("1000.40", 25000L, "24.99"), // Catering and Food
      amount("1000.40", 31000L, "30.99"), // Wages and Benefits
      amount("1000.40", 4000L, "4.00"), // Depreciation and Lease
      amount("1000.40", 1500L, "1.50"), // General Camp Expenses
      amount("1000.40", 900L, "0.90"), // Other Camp Expenses
      amount("1000.40", 62400L, "62.37"), // Camp Sub-Total
      amount(null, 400L, null), // Recoveries — cost only
      amount("1000.40", 62000L, "61.98"), // Camp Total
      amount("1000.40", 3000L, "3.00"), // Crew Transportation
      amount("1000.40", 2500L, "2.50"), // Equipment and Supplies - Land
      amount("1000.40", 1200L, "1.20"), // Equipment and Supplies - Rail
      amount("1000.40", 800L, "0.80"), // Equipment and Supplies - Air
      amount("1000.40", 600L, "0.60"), // Equipment and Supplies - Water
      amount("1000.40", 450L, "0.45"), // Other Access Expenses
      amount("1000.40", 8550L, "8.55"), // Access Expense Total
      amount("1000.40", 70550L, "70.52") // Camp and Access Total
    };

    return camp(
        "Cedar Flats Camp",
        "120.4",
        48,
        "1000.40",
        Boolean.TRUE,
        "Fly-in camp\tseasonal only\nsee notes",
        amounts);
  }

  /**
   * The sixteen amounts in the order {@code Schedule5Section.row} emits them: the five camp
   * categories, the sub-total, Recoveries, the camp total, then the six access categories, the
   * access total and the camp-and-access total.
   */
  private static Camp camp(
      String name,
      String roadDistance,
      Integer sizeOfCamp,
      String associatedVolume,
      Boolean isolated,
      String comments,
      CategoryAmount[] amounts) {
    return new Camp(
        8201,
        0,
        name,
        dec(roadDistance),
        sizeOfCamp,
        dec(associatedVolume),
        isolated,
        comments,
        amounts[0],
        amounts[1],
        amounts[2],
        amounts[3],
        amounts[4],
        amounts[5],
        amounts[6],
        amounts[7],
        amounts[8],
        amounts[9],
        amounts[10],
        amounts[11],
        amounts[12],
        amounts[13],
        amounts[14],
        amounts[15],
        0,
        0);
  }

  private static CategoryAmount[] noAmounts() {
    return new CategoryAmount[16];
  }

  private static CategoryAmount amount(String volume, Long cost, String costPerVolume) {
    return new CategoryAmount(dec(volume), cost, dec(costPerVolume));
  }

  private static BigDecimal dec(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  /** The cell under a named legacy column; the header itself is pinned literally above. */
  private static String cell(String[] row, String column) {
    String[] header = new Schedule5Section().header();
    for (int i = 0; i < header.length; i++) {
      if (header[i].equals(column)) {
        return row[i];
      }
    }
    throw new IllegalArgumentException("no such Schedule 5 column: " + column);
  }
}
