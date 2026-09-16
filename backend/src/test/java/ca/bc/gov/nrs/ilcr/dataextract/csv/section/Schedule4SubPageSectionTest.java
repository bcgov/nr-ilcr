package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule4SubPageSection.Kind;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The three legacy builders this one class replaces — {@code Schedule4TowingExtract}, {@code
 * Schedule4RehaulExtract} and {@code Schedule4OtherExtract} — each pinned on its own: its title,
 * its header, its row filter and its whole-schedule marker width.
 *
 * <p>Every expected header is written out literally. Reading the production constant would make
 * these tests agree with any edit to the columns, and the columns are the contract a licensee's
 * spreadsheet keys on.
 */
@DisplayName("Schedule4SubPageSection — three legacy builders, one shape")
class Schedule4SubPageSectionTest {

  private static final RowContext CTX = new RowContext("673", "2021", "Draft", "8201");

  private static final String LOCATION = "Cedar Lake Dump";

  @Nested
  @DisplayName("Towing (code 43)")
  class Towing {

    private final Schedule4SubPageSection section = new Schedule4SubPageSection(Kind.TOWING);

    @Test
    @DisplayName("the title is legacy's Towing banner")
    void title_isTheTowingBanner() {
      assertThat(section.title()).isEqualTo("**** Schedule 4 - Towing ****");
    }

    @Test
    @DisplayName("the header is legacy's ten sub-page columns, in order")
    void header_isTheTenLegacyColumns() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "LOCATION",
        "DESCRIPTION",
        "DIST_KM",
        "VOL_M3",
        "COST_$",
        "CPU_$/M3"
      };

      assertThat(section.header()).hasSize(10).containsExactly(expected);
    }

    @Test
    @DisplayName("the whole-schedule marker is SIX cells — Towing alone, and deliberately so")
    void marker_isSixCells_unlikeRehaulAndOther() {
      // Legacy's Towing builder emitted one more null-marker cell than its two siblings. The
      // asymmetry is in the legacy source, so Rehaul's and Other's five-cell markers are
      // pinned below to keep a future "tidy-up" from levelling all three.
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(6)
          .containsExactly(
              "Included Mills: 673", "2020 - 2021", "-", "-", "-", "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("the rows are the cost-bearing code-43 rows, then the Total")
    void rows_areTheCostBearingRowsThenTheTotal() {
      List<String[]> rows = section.rows(CTX, towingLocation());

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0))
          .containsExactly(
              "673",
              "2021",
              "Draft",
              "8201",
              LOCATION,
              // The description goes through the legacy sanitiser: tab to two spaces,
              // newline to one space.
              "Tow to mill  segment A north leg",
              "10",
              "1,000",
              "5,000",
              "=\"5.00\"");
      assertThat(rows.get(1))
          .containsExactly(
              "673",
              "2021",
              "Draft",
              "8201",
              LOCATION,
              "Barge assist",
              "5",
              "200",
              "2,500",
              "=\"12.53\"");
      assertThat(rows.get(2))
          .containsExactly(
              "",
              "",
              "",
              "",
              "",
              "Total:",
              // Legacy summed each term rounded to a whole number FIRST, so 10.4 + 5.4 totals
              // 15 and not the 16 the true sum of 15.8 would give.
              "15",
              "1,200",
              "7,500",
              // 7,500.00 / 1,200.00 — the ratio of the two sums. The per-row rates were 5.00
              // and 12.53, so neither their sum (17.53) nor their mean (8.77) can produce this.
              "6.25");
    }

    @Test
    @DisplayName("a code-43 row with no cost is dropped, as legacy's filter dropped it")
    void rowWithoutACost_isDropped() {
      Location location =
          location(
              List.of(
                  row(43, "Counted", "10", "100", 500, null, "5.00"),
                  row(43, "Quoted only", "99", "99", null, null, null)));

      // One list row plus the Total, so the no-cost row reached neither.
      assertThat(section.rows(CTX, location)).hasSize(2);
    }

    private Location towingLocation() {
      return location(
          List.of(
              row(43, "Tow to mill\tsegment A\nnorth leg", "10.4", "1000.40", 5000, null, "5.00"),
              row(43, "Barge assist", "5.4", "199.60", 2500, null, "12.53"),
              // Dropped: Towing keeps only rows carrying a cost.
              row(43, "Quoted only", "99", "99", null, null, null),
              // Dropped: a different category's rows live in the same list.
              row(46, "Rehaul", "20", "200", 1000, 3, "5.00")));
    }
  }

  @Nested
  @DisplayName("Rehaul (code 46)")
  class Rehaul {

    private final Schedule4SubPageSection section = new Schedule4SubPageSection(Kind.REHAUL);

    @Test
    @DisplayName("the title is legacy's Rehaul banner")
    void title_isTheRehaulBanner() {
      assertThat(section.title()).isEqualTo("**** Schedule 4 - Rehaul ****");
    }

    @Test
    @DisplayName("the header is legacy's ten sub-page columns, in order")
    void header_isTheTenLegacyColumns() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "LOCATION",
        "DESCRIPTION",
        "DIST_KM",
        "VOL_M3",
        "COST_$",
        "CPU_$/M3"
      };

      assertThat(section.header()).hasSize(10).containsExactly(expected);
    }

    @Test
    @DisplayName("the whole-schedule marker is FIVE cells — one narrower than Towing's")
    void marker_isFiveCells_unlikeTowing() {
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(5)
          .containsExactly("Included Mills: 673", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("the rows are the cost-bearing code-46 rows, then the Total")
    void rows_areTheCostBearingRowsThenTheTotal() {
      Location location =
          location(
              List.of(
                  row(46, "Rehaul spur 12", "20", "400.00", 3200, 4, "8.00"),
                  // Dropped: Rehaul keeps only rows carrying a cost.
                  row(46, "Rehaul quote only", "9", "100", null, 2, null)));

      List<String[]> rows = section.rows(CTX, location);

      assertThat(rows).hasSize(2);
      // Ten cells, so the cycle count of 4 reached no column: the sub-page header has none,
      // and the cycle surfaces only in the main Schedule 4 row's TRUCK_REHAUL_CYCLE cell.
      assertThat(rows.get(0))
          .containsExactly(
              "673",
              "2021",
              "Draft",
              "8201",
              LOCATION,
              "Rehaul spur 12",
              "20",
              "400",
              "3,200",
              "=\"8.00\"");
      assertThat(rows.get(1))
          .containsExactly("", "", "", "", "", "Total:", "20", "400", "3,200", "8.00");
    }
  }

  @Nested
  @DisplayName("Other Transport (code 55)")
  class Other {

    private final Schedule4SubPageSection section = new Schedule4SubPageSection(Kind.OTHER);

    @Test
    @DisplayName("the title is legacy's Other Transport banner")
    void title_isTheOtherTransportBanner() {
      assertThat(section.title()).isEqualTo("**** Schedule 4 - Other Transport ****");
    }

    @Test
    @DisplayName("the header is legacy's ten sub-page columns, in order")
    void header_isTheTenLegacyColumns() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "LOCATION",
        "DESCRIPTION",
        "DIST_KM",
        "VOL_M3",
        "COST_$",
        "CPU_$/M3"
      };

      assertThat(section.header()).hasSize(10).containsExactly(expected);
    }

    @Test
    @DisplayName("the whole-schedule marker is FIVE cells — one narrower than Towing's")
    void marker_isFiveCells_unlikeTowing() {
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(5)
          .containsExactly("Included Mills: 673", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("the rows are the DISTANCE-bearing code-55 rows, then the Total")
    void rows_areTheDistanceBearingRowsThenTheTotal() {
      Location location =
          location(
              List.of(
                  row(55, "Barge shuttle", "33", "250.00", 1250, null, "5.00"),
                  // Dropped: Other is the one variant filtered on a distance, not a cost, so a
                  // row with a cost but no distance is the one legacy discarded here.
                  row(55, "Quoted, not run", null, "10", 100, null, "10.00")));

      List<String[]> rows = section.rows(CTX, location);

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0))
          .containsExactly(
              "673",
              "2021",
              "Draft",
              "8201",
              LOCATION,
              "Barge shuttle",
              "33",
              "250",
              "1,250",
              "=\"5.00\"");
      assertThat(rows.get(1))
          .containsExactly("", "", "", "", "", "Total:", "33", "250", "1,250", "5.00");
    }

    @Test
    @DisplayName("a distance-only row totals to a null cost, volume and rate")
    void distanceOnlyRow_totalsToNullCostVolumeAndRate() {
      Location location = location(List.of(row(55, "Survey leg", "44", null, null, null, null)));

      List<String[]> rows = section.rows(CTX, location);

      assertThat(rows.get(0))
          .containsExactly(
              "673", "2021", "Draft", "8201", LOCATION, "Survey leg", "44", "-", "-", "=\"-\"");
      // Nothing was added to the cost or volume sums, so both stay null rather than becoming 0,
      // and the rate's numerator and denominator are both absent.
      assertThat(rows.get(1)).containsExactly("", "", "", "", "", "Total:", "44", "-", "-", "-");
    }
  }

  @Nested
  @DisplayName("the per-location marker and the location cell")
  class PerLocationMarker {

    private final Schedule4SubPageSection section = new Schedule4SubPageSection(Kind.TOWING);

    @Test
    @DisplayName("a location with no matching rows yields the six-cell per-location marker")
    void noMatchingRows_yieldTheSixCellMarker() {
      List<String[]> rows =
          section.rows(CTX, location(List.of(row(55, "Other", "1", "1", 1, null, "1"))));

      // Not padded out to the ten-column header: legacy emitted the four context cells, the
      // location and the marker, and that short row is what a legacy sample holds.
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0))
          .hasSize(6)
          .containsExactly("673", "2021", "Draft", "8201", LOCATION, "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("a null subPageRows list is treated as no rows at all")
    void nullRowList_isTreatedAsNoRows() {
      Location location = new Location(8201, 0, LOCATION, null, List.of(), null);

      assertThat(section.rows(CTX, location)).hasSize(1);
    }

    @Test
    @DisplayName("a null location name is the null marker, unlike the main Schedule 4 row")
    void nullLocationName_isTheNullMarker() {
      Location location = new Location(8201, 0, null, null, List.of(), null);

      // The sub-page builders guarded the name; Schedule4Section writes it raw and emits an
      // empty field instead. Both guards are legacy's, applied to their own cells.
      assertThat(section.rows(CTX, location).get(0)[4]).isEqualTo("-");
    }

    @Test
    @DisplayName("an EMPTY location name is an empty cell, not the null marker")
    void emptyLocationName_isAnEmptyCell() {
      Location location = new Location(8201, 0, "", null, List.of(), null);

      // Legacy guarded this cell on null ALONE rather than with its own isNullOrEmptyString
      // helper, so an empty name reaches the file as an empty quoted cell.
      assertThat(section.rows(CTX, location).get(0)[4]).isEmpty();
    }
  }

  private static Location location(List<SubPageRow> rows) {
    return new Location(8201, 0, LOCATION, null, List.of(), rows);
  }

  private static SubPageRow row(
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
}
