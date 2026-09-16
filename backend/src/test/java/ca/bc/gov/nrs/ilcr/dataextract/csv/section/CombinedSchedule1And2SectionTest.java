package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.section.CombinedSchedule1And2Section.Schedule1Entry;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.CombinedSchedule1And2Section.Schedule2Entry;
import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule2.dto.CostBlock;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link CombinedSchedule1And2Section}, the layout legacy used for exactly Schedules 1
 * and 2 over several mills AND several years: a five-column mini-table per year for Schedule 1,
 * then the same series for Schedule 2.
 *
 * <p>Pins the two things that make this layout different from every other section: a year's
 * mini-table header is emitted only when that year actually has rows (legacy's change-of-year
 * check, never a walk of the selected range), and there is no NO-DATA handling at all — an unsaved
 * pair renders its one figure as the null marker and still occupies a row.
 */
@DisplayName("CombinedSchedule1And2Section — the per-year five-column mini-tables")
class CombinedSchedule1And2SectionTest {

  /** {@code CsvWriter.BLANK_CELL} — the single-space cell legacy opened each mini-table with. */
  private static final String BLANK = " ";

  private static final String SCHEDULE_1_TITLE = "**** Schedule 1 ****";
  private static final String SCHEDULE_2_TITLE = "**** Schedule 2 ****";

  private final CombinedSchedule1And2Section section = new CombinedSchedule1And2Section();

  @Nested
  @DisplayName("the two five-column headers")
  class Headers {

    @Test
    @DisplayName("the Schedule 1 mini-table is a blank row, the title, then five columns")
    void schedule1MiniTable_isBlankTitleHeader() {
      // MultipleSchedule1_2Extract.java:39 (title), :87-93 (header). Written out literally: the
      // five-column shape is what distinguishes this layout from the 51-column Schedule 1 row.
      List<String[]> rows = section.rows(List.of(entry1(2020, "670", "1234567")), List.of());

      assertThat(rows.get(0)).containsExactly(BLANK);
      assertThat(rows.get(1)).containsExactly(SCHEDULE_1_TITLE);
      assertThat(rows.get(2))
          .containsExactly("MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "STAND_TTT_M3");
      assertThat(rows.get(2)).hasSize(5);
    }

    @Test
    @DisplayName("the Schedule 2 mini-table is a blank row, the title, then five columns")
    void schedule2MiniTable_isBlankTitleHeader() {
      // MultipleSchedule1_2Extract.java:64 (title), :100-106 (header).
      List<String[]> rows = section.rows(List.of(), List.of(entry2(2020, "670", 2_500_000)));

      assertThat(rows.get(0)).containsExactly(BLANK);
      assertThat(rows.get(1)).containsExactly(SCHEDULE_2_TITLE);
      assertThat(rows.get(2))
          .containsExactly("MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "PO&P_COSTS_$");
      assertThat(rows.get(2)).hasSize(5);
    }

    @Test
    @DisplayName("a header row is a copy, so a later mini-table is unaffected by a mutated one")
    void headerRows_areDefensivelyCopied() {
      List<String[]> rows =
          section.rows(
              List.of(entry1(2020, "670", "1000"), entry1(2021, "670", "1000")), List.of());

      rows.get(2)[0] = "TAMPERED";

      // blank, title, header, row | blank, title, header, row — so the 2021 header is index 6.
      assertThat(rows.get(6)[0]).isEqualTo("MILL_NUMBER");
    }
  }

  @Nested
  @DisplayName("the per-year grouping")
  class PerYearGrouping {

    @Test
    @DisplayName("both schedules' whole bodies come back Schedule 1 first, then Schedule 2")
    void bothSchedules_comeBackInOrder() {
      List<String[]> rows =
          section.rows(
              List.of(entry1(2020, "670", "1234567"), entry1(2020, "671", "987.65")),
              List.of(entry2(2020, "670", 2_500_000)));

      assertThat(rows).hasSize(9);
      assertThat(rows.get(0)).containsExactly(BLANK);
      assertThat(rows.get(1)).containsExactly(SCHEDULE_1_TITLE);
      // Both mills of 2020 share the one header.
      assertThat(rows.get(3))
          .containsExactly("670", "2020", "Draft", "514", "1,234,567"); // grouped, no decimals
      assertThat(rows.get(4))
          .containsExactly("671", "2020", "Draft", "515", "988"); // rounded to the whole cell
      assertThat(rows.get(5)).containsExactly(BLANK);
      assertThat(rows.get(6)).containsExactly(SCHEDULE_2_TITLE);
      assertThat(rows.get(8))
          .containsExactly("670", "2020", "Draft", "514", "2,500,000"); // PO&P cost
    }

    @Test
    @DisplayName("a year with no rows gets no header, even when it sits inside the range")
    void yearWithNoRows_getsNoHeader() {
      // The selection spans 2020-2022 but only 2020 and 2022 have saved pairs. Legacy emitted a
      // mini-table on a CHANGE OF YEAR in the row list, so 2021 produces nothing at all — the
      // extract must not carry an empty 2021 header.
      List<String[]> rows =
          section.rows(
              List.of(entry1(2020, "670", "1000"), entry1(2022, "670", "2000")), List.of());

      assertThat(rows).hasSize(8);
      assertThat(rows)
          .filteredOn(row -> row.length == 1 && SCHEDULE_1_TITLE.equals(row[0]))
          .hasSize(2);
      assertThat(rows).noneMatch(row -> row.length == 5 && "2021".equals(row[1]));
    }

    @Test
    @DisplayName("consecutive rows of one year share a single header")
    void oneYearManyMills_shareOneHeader() {
      List<String[]> rows =
          section.rows(
              List.of(
                  entry1(2020, "670", "1000"),
                  entry1(2020, "671", "2000"),
                  entry1(2020, "672", "3000")),
              List.of());

      assertThat(rows).hasSize(6); // blank, title, header, three mill rows
      assertThat(rows)
          .filteredOn(row -> row.length == 1 && SCHEDULE_1_TITLE.equals(row[0]))
          .hasSize(1);
    }

    @Test
    @DisplayName("a year that recurs out of order gets a second header, as the legacy check did")
    void yearRecurringOutOfOrder_getsASecondHeader() {
      // The check compares each entry against the PREVIOUS year only, so unsorted input repeats a
      // mini-table rather than merging it. Preserved deliberately: the contract is that callers
      // sort year-then-mill, and a silent merge here would hide a sorting regression upstream.
      List<String[]> rows =
          section.rows(
              List.of(
                  entry1(2020, "670", "1000"),
                  entry1(2021, "670", "2000"),
                  entry1(2020, "671", "3000")),
              List.of());

      assertThat(rows)
          .filteredOn(row -> row.length == 1 && SCHEDULE_1_TITLE.equals(row[0]))
          .hasSize(3);
    }

    @Test
    @DisplayName("the Schedule 2 year walk restarts, so its first year always gets a header")
    void schedule2YearWalk_restartsIndependently() {
      // The year cursor is reset between the two passes; a Schedule 2 entry for the same year the
      // Schedule 1 pass ended on must still open its own mini-table.
      List<String[]> rows =
          section.rows(List.of(entry1(2020, "670", "1000")), List.of(entry2(2020, "670", 500)));

      assertThat(rows).hasSize(8);
      assertThat(rows.get(4)).containsExactly(BLANK);
      assertThat(rows.get(5)).containsExactly(SCHEDULE_2_TITLE);
    }

    @Test
    @DisplayName("no entries at all produce no rows, not an empty mini-table")
    void noEntries_produceNoRows() {
      assertThat(section.rows(List.of(), List.of())).isEmpty();
    }
  }

  @Nested
  @DisplayName("the absent-figure cases")
  class AbsentFigures {

    @Test
    @DisplayName("an unsaved Schedule 1 renders the null marker and no NO-DATA row")
    void unsavedSchedule1_rendersTheNullMarker() {
      // This layout has no NO-DATA handling: the pair still occupies a row and its one figure
      // dashes, so the mini-table keeps one row per selected (mill, year).
      List<String[]> rows =
          section.rows(List.of(new Schedule1Entry(2020, ctx("670", "514"), null)), List.of());

      assertThat(rows).hasSize(4);
      assertThat(rows.get(3)).containsExactly("670", "2020", "Draft", "514", "-");
      assertThat(rows).allSatisfy(row -> assertThat(row).doesNotContain("*** NO DATA FOUND ***"));
    }

    @Test
    @DisplayName("a saved Schedule 1 with no Standing-Tree-to-Truck line renders the null marker")
    void schedule1WithoutTheStandingTreeLine_rendersTheNullMarker() {
      // Only cost item 12 feeds this cell; a document carrying other items has nothing to show.
      Schedule1Response other = schedule1(new LineItem(17, new BigDecimal("500"), 10, null));

      List<String[]> rows =
          section.rows(List.of(new Schedule1Entry(2020, ctx("670", "514"), other)), List.of());

      assertThat(rows.get(3)[4]).isEqualTo("-");
    }

    @Test
    @DisplayName("an unsaved Schedule 2 renders the null marker and no NO-DATA row")
    void unsavedSchedule2_rendersTheNullMarker() {
      List<String[]> rows =
          section.rows(List.of(), List.of(new Schedule2Entry(2020, ctx("670", "514"), null)));

      assertThat(rows).hasSize(4);
      assertThat(rows.get(3)).containsExactly("670", "2020", "Draft", "514", "-");
      assertThat(rows).allSatisfy(row -> assertThat(row).doesNotContain("*** NO DATA FOUND ***"));
    }

    @Test
    @DisplayName("a Schedule 2 with no Purchased-Log block renders the null marker")
    void schedule2WithoutThePurchasedLogBlock_rendersTheNullMarker() {
      List<String[]> rows =
          section.rows(
              List.of(), List.of(new Schedule2Entry(2020, ctx("670", "514"), schedule2(null))));

      assertThat(rows.get(3)[4]).isEqualTo("-");
    }

    @Test
    @DisplayName("a Purchased-Log block with a null cost renders the null marker")
    void schedule2WithANullCost_rendersTheNullMarker() {
      // The block exists (its volume is carried from Schedule 3) but no cost was entered.
      CostBlock noCost = new CostBlock(new BigDecimal("1000"), null, null);

      List<String[]> rows =
          section.rows(
              List.of(), List.of(new Schedule2Entry(2020, ctx("670", "514"), schedule2(noCost))));

      assertThat(rows.get(3)[4]).isEqualTo("-");
    }

    @Test
    @DisplayName("a pair with no status row shows legacy's NO STATUS text in the status cell")
    void pairWithoutAStatus_showsTheNoStatusText() {
      RowContext noStatus = new RowContext("670", "2020", RowContext.NO_STATUS, "514");

      List<String[]> rows =
          section.rows(List.of(new Schedule1Entry(2020, noStatus, schedule1())), List.of());

      assertThat(rows.get(3)[2]).isEqualTo("** NO STATUS **");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  private static RowContext ctx(String millNumber, String millId) {
    return new RowContext(millNumber, "2020", "Draft", millId);
  }

  private static Schedule1Entry entry1(int year, String millNumber, String standingTreeVolume) {
    // The mill NUMBER and the mill ID are different columns and different values; keeping them
    // distinct in the fixture is what lets the row assertions catch a swap.
    String millId =
        switch (millNumber) {
          case "670" -> "514";
          case "671" -> "515";
          default -> "516";
        };
    RowContext ctx = new RowContext(millNumber, String.valueOf(year), "Draft", millId);
    return new Schedule1Entry(
        year, ctx, schedule1(new LineItem(12, new BigDecimal(standingTreeVolume), null, null)));
  }

  private static Schedule2Entry entry2(int year, String millNumber, Integer purchasedLogCost) {
    RowContext ctx = new RowContext(millNumber, String.valueOf(year), "Draft", "514");
    return new Schedule2Entry(
        year, ctx, schedule2(new CostBlock(new BigDecimal("1000"), purchasedLogCost, null)));
  }

  /** A Schedule 1 document carrying only the line items; this layout reads nothing else. */
  private static Schedule1Response schedule1(LineItem... lineItems) {
    return new Schedule1Response(
        514L,
        2020,
        "D",
        false,
        null,
        null,
        0,
        null,
        null,
        List.of(lineItems),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null);
  }

  /** A Schedule 2 document carrying only the Purchased-Log block; nothing else is read. */
  private static Schedule2Response schedule2(CostBlock purchasedLogCost) {
    return new Schedule2Response(
        514L,
        2020,
        "D",
        false,
        0,
        null,
        null,
        purchasedLogCost,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
