package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The byte shape of the Schedule 4 section: its 58 legacy columns, its Excel-formula {@code $/m³}
 * cells, and the three distance-based blocks legacy suppressed outright.
 *
 * <p>The expected header is written out literally rather than read from the production constant. A
 * test that compares the constant against itself passes however the columns are edited, and this
 * section's whole value is that a licensee's saved spreadsheet keeps working — the column list and
 * its order are the contract, including the {@code TRUCK_TRAN_$/M3} typo.
 */
@DisplayName("Schedule4Section — the 58-column legacy row")
class Schedule4SectionTest {

  /** The four leading cells every row carries; fixed across this class so rows stay comparable. */
  private static final RowContext CTX = new RowContext("673", "2021", "Draft", "8201");

  private final Schedule4Section section = new Schedule4Section();

  @Nested
  @DisplayName("header and title")
  class HeaderAndTitle {

    @Test
    @DisplayName("the header is legacy's 58 columns, in order, typo included")
    void header_isTheFiftyEightLegacyColumns() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "LOCATION",
        "LAKESIDE_M3",
        "LAKESIDE_$",
        "LAKESIDE_$/M3",
        "WATER_DUMP_M3",
        "WATER_DUMP_$",
        "WATER_DUMP_$/M3",
        "WATER_BOOM_M3",
        "WATER_BOOM_$",
        "WATER_BOOM_$/M3",
        "TOW_TOT_KM",
        "TOW_TOT_M3",
        "TOW_TOT_$",
        "TOW_TOT_$/M3",
        "WILLISTON_M3",
        "WILLISTON_$",
        "WILLISTON_$/M3",
        "DEWATER_RELOAD_M3",
        "DEWATER_RELOAD_$",
        "DEWATER_RELOAD_$/M3",
        "TRUCK_REHAUL_KM",
        "TRUCK_REHAUL_M3",
        "TRUCK_REHAUL_$",
        "TRUCK_REHAUL_$/M3",
        "TRUCK_REHAUL_CYCLE",
        "TRUCK_BARGE_KM",
        "TRUCK_BARGE_M3",
        "TRUCK_BARGE_$",
        "TRUCK_BARGE_$/M3",
        "CREW_BARGE_KM",
        "CREW_BARGE_M3",
        "CREW_BARGE_$",
        "CREW_BARGE_$/M3",
        "DAM_TRANS_M3",
        "DAM_TRANS_$",
        "DAM_TRANS_$/M3",
        "TRUCK_TRANS_M3",
        "TRUCK_TRANS_$",
        // Legacy's own typo, transcribed deliberately: the singular TRUCK_TRAN_ where both of
        // its neighbours read TRUCK_TRANS_. It is not a slip in the rebuild — a licensee's
        // spreadsheet keys on this spelling, so correcting it would break their workbook.
        "TRUCK_TRAN_$/M3",
        "RAIL_TRANS_M3",
        "RAIL_TRANS_$",
        "RAIL_TRANS_$/M3",
        "RAIL_HAUL_KM",
        "RAIL_HAUL_M3",
        "RAIL_HAUL_$",
        "RAIL_HAUL_$/M3",
        "LOW_BRIDGE_M3",
        "LOW_BRIDGE_$",
        "LOW_BRIDGE_$/M3",
        "OTH_TRANS_KM",
        "OTH_TRANS_M3",
        "OTH_TRANS_$",
        "OTH_TRANS_$/M3",
        "COMMENTS"
      };

      assertThat(section.header()).hasSize(58).containsExactly(expected);
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
      assertThat(section.title()).isEqualTo("**** Schedule 4 ****");
    }
  }

  @Nested
  @DisplayName("the data row")
  class DataRow {

    @Test
    @DisplayName("a populated location fills all 58 cells")
    void row_isTheFullFiftyEightCells() {
      String[] expected = {
        // The RowContext cells.
        "673",
        "2021",
        "Draft",
        "8201",
        // LOCATION — written raw, so no sanitising and no null marker.
        "Cedar Lake Dump",
        // Lakeside Dry Dump (40): 1234.56 m3 rounds to a whole cell, cost groups by thousands.
        "1,235",
        "12,000",
        "=\"9.72\"",
        // Water Dump (41) and Water Boom (42) were never reported for this location.
        "-",
        "-",
        "=\"-\"",
        "-",
        "-",
        "=\"-\"",
        // Towing (43) is summed from the location's own sub-page rows: 10+5, 100+50, 500+300,
        // and 800/150 = 5.33 at scale 2.
        "15",
        "150",
        "800",
        "=\"5.33\"",
        // Williston Dewater Only (44) and Dewater and Reload (45) unreported.
        "-",
        "-",
        "=\"-\"",
        "-",
        "-",
        "=\"-\"",
        // Truck Rehaul (46), the one sub-page carrying a cycle count.
        "20",
        "200",
        "1,000",
        "=\"5.00\"",
        "3",
        // Truck Barge/Ferry (47) and Crew Barge/Ferry (48), each with its own distance.
        "12",
        "80",
        "400",
        "=\"5.00\"",
        "30",
        "60",
        "300",
        "=\"5.00\"",
        // Hydro Dam (49), Truck to Truck (50) and Truck to Rail (51) unreported.
        "-",
        "-",
        "=\"-\"",
        "-",
        "-",
        "=\"-\"",
        "-",
        "-",
        "=\"-\"",
        // Rail Haul (52).
        "40",
        "90",
        "900",
        "=\"10.00\"",
        // Low Water Bridge (53) unreported.
        "-",
        "-",
        "=\"-\"",
        // Other Transportation (55), summed from its own sub-page row.
        "7",
        "70",
        "350",
        "=\"5.00\"",
        // COMMENTS go through the legacy sanitiser: tab to two spaces, newline to one space.
        "Ferry days  varied see attachment"
      };

      assertThat(section.row(CTX, populatedLocation()))
          .hasSize(section.header().length)
          .containsExactly(expected);
    }

    @Test
    @DisplayName("the location name is written raw, so a null name is an empty field")
    void locationName_isWrittenRaw() {
      String[] row = section.row(CTX, location(null, null, List.of(), List.of()));

      // Not the "-" marker: CsvWriter writes a null cell as nothing between its delimiters,
      // which is how a nameless legacy location reached the file.
      assertThat(cell(row, "LOCATION")).isNull();
    }

    @Test
    @DisplayName("comments are sanitised but a blank comment is the null marker")
    void comments_areSanitisedAndBlankBecomesTheMarker() {
      String[] withText = section.row(CTX, location("Dump", "a\tb\nc", List.of(), List.of()));
      String[] withNone = section.row(CTX, location("Dump", "", List.of(), List.of()));

      assertThat(cell(withText, "COMMENTS")).isEqualTo("a  b c");
      assertThat(cell(withNone, "COMMENTS")).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the $/m³ cells")
  class PerUnitCells {

    /** Every {@code $/m³} column legacy wrapped in the Excel formula, including a null one. */
    private final List<String> wrappedColumns =
        List.of(
            "LAKESIDE_$/M3",
            "WATER_DUMP_$/M3",
            "WATER_BOOM_$/M3",
            "TOW_TOT_$/M3",
            "WILLISTON_$/M3",
            "DEWATER_RELOAD_$/M3",
            "TRUCK_REHAUL_$/M3",
            "DAM_TRANS_$/M3",
            "TRUCK_TRAN_$/M3",
            "RAIL_TRANS_$/M3",
            "LOW_BRIDGE_$/M3",
            "OTH_TRANS_$/M3");

    @Test
    @DisplayName("a populated $/m³ cell is the Excel-formula form, grouped and two-placed")
    void populatedPerUnit_isTheExcelFormulaForm() {
      Location location =
          location("Dump", null, List.of(fixed(40, "5000", 6172800, "1234.56")), List.of());

      // The =" … " wrapper is what stops a spreadsheet dropping the trailing zeros of a
      // rate like 5.00; legacy wrote every Schedule 4 rate this way.
      assertThat(cell(section.row(CTX, location), "LAKESIDE_$/M3")).isEqualTo("=\"1,234.56\"");
    }

    @Test
    @DisplayName("a null $/m³ is wrapped too, giving the Excel-quoted null marker")
    void nullPerUnit_isTheWrappedNullMarker() {
      String[] row = section.row(CTX, location("Dump", null, List.of(), List.of()));

      for (String column : wrappedColumns) {
        assertThat(cell(row, column)).as(column).isEqualTo("=\"-\"");
      }
    }
  }

  /**
   * Truck Barge/Ferry (47), Crew Barge/Ferry (48) and Rail Haul (52) are the three blocks legacy
   * gated on the matched report carrying a cost or a volume ({@code Schedule4Extract.java:69-80}),
   * and whose {@code $/m³} it guarded with its own ternary rather than the formatter ({@code
   * Schedule4Extract.java:153, :161, :190}). Both behaviours are preserved, so both are pinned.
   */
  @Nested
  @DisplayName("the three distance-gated blocks")
  class DistanceGatedBlocks {

    @Test
    @DisplayName("a block carrying a cost populates all four cells, per-unit Excel-wrapped")
    void blockWithACost_populatesAllFourCells() {
      Location location =
          location("Dump", null, List.of(distance(47, "12", "80", 400, "5")), List.of());

      String[] row = section.row(CTX, location);

      assertThat(cell(row, "TRUCK_BARGE_KM")).isEqualTo("12");
      assertThat(cell(row, "TRUCK_BARGE_M3")).isEqualTo("80");
      assertThat(cell(row, "TRUCK_BARGE_$")).isEqualTo("400");
      assertThat(cell(row, "TRUCK_BARGE_$/M3")).isEqualTo("=\"5.00\"");
    }

    @Test
    @DisplayName("a volume alone also satisfies the gate, so the block reports")
    void blockWithAVolumeButNoCost_stillReports() {
      Location location =
          location("Dump", null, List.of(distance(52, "40", "90", null, null)), List.of());

      String[] row = section.row(CTX, location);

      assertThat(cell(row, "RAIL_HAUL_KM")).isEqualTo("40");
      assertThat(cell(row, "RAIL_HAUL_M3")).isEqualTo("90");
      assertThat(cell(row, "RAIL_HAUL_$")).isEqualTo("-");
      // The block reported, so its $/m³ goes through the formatter and IS wrapped.
      assertThat(cell(row, "RAIL_HAUL_$/M3")).isEqualTo("=\"-\"");
    }

    @Test
    @DisplayName("a distance with no cost and no volume is suppressed — legacy withheld it")
    void blockWithADistanceOnly_isSuppressedIncludingTheDistanceItself() {
      Location location =
          location("Dump", null, List.of(distance(48, "30", null, null, null)), List.of());

      String[] row = section.row(CTX, location);

      // The distance IS in the data and still does not reach the file: legacy's gate discards
      // the whole matched report, distance included, so the rebuilt column must too.
      assertThat(cell(row, "CREW_BARGE_KM")).isEqualTo("-");
      assertThat(cell(row, "CREW_BARGE_M3")).isEqualTo("-");
      assertThat(cell(row, "CREW_BARGE_$")).isEqualTo("-");
      // The one $/m³ asymmetry in the schedule: BARE "-", not the ="-" its populated siblings
      // and every other $/m³ column produce.
      assertThat(cell(row, "CREW_BARGE_$/M3")).isEqualTo("-");
    }

    @Test
    @DisplayName("an absent block is bare-dashed the same way, on all three blocks")
    void absentBlocks_areBareDashedOnAllThree() {
      String[] row = section.row(CTX, location("Dump", null, List.of(), List.of()));

      assertThat(cell(row, "TRUCK_BARGE_$/M3")).isEqualTo("-");
      assertThat(cell(row, "CREW_BARGE_$/M3")).isEqualTo("-");
      assertThat(cell(row, "RAIL_HAUL_$/M3")).isEqualTo("-");
    }
  }

  /**
   * The three list-type categories are summed here from the location's own sub-page rows, and the
   * sums keep legacy's zero seeding: once ANY figure has been added the other three sums stay at
   * the zero they started from rather than reverting to null.
   */
  @Nested
  @DisplayName("the summed list-type blocks")
  class SubPageTotalBlocks {

    @Test
    @DisplayName("costs with no volumes give a zero volume cell and a zero-divisor null rate")
    void costsWithoutVolumes_giveZeroAndANullRate() {
      Location location =
          location(
              "Dump", null, List.of(), List.of(subPageRow(43, "Tow", null, null, 500, null, null)));

      String[] row = section.row(CTX, location);

      assertThat(cell(row, "TOW_TOT_$")).isEqualTo("500");
      // Seeded at zero and kept, because the cost counted as "something added".
      assertThat(cell(row, "TOW_TOT_KM")).isEqualTo("0");
      assertThat(cell(row, "TOW_TOT_M3")).isEqualTo("0");
      // Zero divisor, so the division yields null — and this cell IS formatter-wrapped.
      assertThat(cell(row, "TOW_TOT_$/M3")).isEqualTo("=\"-\"");
    }

    @Test
    @DisplayName("no matching sub-page rows leave every figure null, not zero")
    void noRows_leaveEveryFigureNull() {
      String[] row = section.row(CTX, location("Dump", null, List.of(), List.of()));

      assertThat(cell(row, "TOW_TOT_KM")).isEqualTo("-");
      assertThat(cell(row, "TOW_TOT_M3")).isEqualTo("-");
      assertThat(cell(row, "TOW_TOT_$")).isEqualTo("-");
      assertThat(cell(row, "TRUCK_REHAUL_CYCLE")).isEqualTo("-");
      assertThat(cell(row, "OTH_TRANS_KM")).isEqualTo("-");
    }

    @Test
    @DisplayName("each block sums only its own code, and only rows its own filter keeps")
    void eachBlock_sumsOnlyItsOwnFilteredRows() {
      Location location =
          location(
              "Dump",
              null,
              List.of(),
              List.of(
                  subPageRow(43, "Tow", "10", "100", 500, null, null),
                  // Rehaul keeps rows with a COST, so this one never reaches the sum.
                  subPageRow(46, "Rehaul no cost", "999", "999", null, 9, null),
                  // Other keeps rows with a DISTANCE, so this one never reaches the sum.
                  subPageRow(55, "Other no distance", null, "999", 999, null, null)));

      String[] row = section.row(CTX, location);

      assertThat(cell(row, "TOW_TOT_KM")).isEqualTo("10");
      assertThat(cell(row, "TRUCK_REHAUL_KM")).isEqualTo("-");
      assertThat(cell(row, "OTH_TRANS_M3")).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the whole-schedule marker")
  class WholeScheduleMarker {

    @Test
    @DisplayName("is five cells wide — Schedule 4 does not override the default")
    void marker_isFiveCells() {
      // Schedule4SubPageSectionTest pins Towing's SIX-cell marker; the widths genuinely differ
      // between the legacy builders and neither is a transcription slip.
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(5)
          .containsExactly("Included Mills: 673", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***");
    }
  }

  private static Location populatedLocation() {
    return location(
        "Cedar Lake Dump",
        "Ferry days\tvaried\nsee attachment",
        List.of(
            fixed(Schedule4Section.LAKESIDE_DRY_DUMP, "1234.56", 12000, "9.72"),
            distance(Schedule4Section.TRUCK_BARGE_FERRY, "12", "80", 400, "5.00"),
            distance(Schedule4Section.CREW_BARGE_FERRY, "30", "60", 300, "5.00"),
            distance(Schedule4Section.RAIL_HAUL, "40", "90", 900, "10.00")),
        List.of(
            subPageRow(Schedule4Section.TOWING, "Tow A", "10", "100", 500, null, "5.00"),
            subPageRow(Schedule4Section.TOWING, "Tow B", "5", "50", 300, null, "6.00"),
            subPageRow(Schedule4Section.TRUCK_REHAUL, "Rehaul", "20", "200", 1000, 3, "5.00"),
            subPageRow(
                Schedule4Section.OTHER_TRANSPORTATION, "Other", "7", "70", 350, null, "5.00")));
  }

  private static Location location(
      String name, String comments, List<CategoryAmount> categories, List<SubPageRow> rows) {
    return new Location(8201, 0, name, comments, categories, rows);
  }

  /** One of the nine no-distance categories: its distance column is never populated. */
  private static CategoryAmount fixed(int code, String volume, Integer cost, String perUnit) {
    return new CategoryAmount(code, "FIXED", dec(volume), cost, null, dec(perUnit));
  }

  /** One of the three distance categories (47/48/52), each carrying its own distance. */
  private static CategoryAmount distance(
      int code, String distance, String volume, Integer cost, String perUnit) {
    return new CategoryAmount(code, "DISTANCE", dec(volume), cost, dec(distance), dec(perUnit));
  }

  private static SubPageRow subPageRow(
      int code,
      String description,
      String distance,
      String volume,
      Integer cost,
      Integer cycle,
      String perUnit) {
    return new SubPageRow(
        1, code, description, dec(distance), dec(volume), cost, cycle, dec(perUnit));
  }

  private static BigDecimal dec(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  /** The cell under a named legacy column; the header itself is pinned literally above. */
  private static String cell(String[] row, String column) {
    String[] header = new Schedule4Section().header();
    for (int i = 0; i < header.length; i++) {
      if (header[i].equals(column)) {
        return row[i];
      }
    }
    throw new IllegalArgumentException("no such Schedule 4 column: " + column);
  }
}
