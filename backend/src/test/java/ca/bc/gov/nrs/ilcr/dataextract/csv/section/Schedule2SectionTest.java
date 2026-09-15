package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat;
import ca.bc.gov.nrs.ilcr.schedule2.dto.CostBlock;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Schedule2Section} — the column fidelity of the Schedule 2 CSV section.
 *
 * <p>The header and the title are written out literally rather than read from the production
 * constant, so that a column renamed or reordered in the rebuild fails here instead of silently
 * changing a file that downstream ministry spreadsheets parse by position.
 */
@DisplayName("Schedule2Section — legacy Schedule2Extract column fidelity")
class Schedule2SectionTest {

  private static final Schedule2Section SECTION = new Schedule2Section();

  private static final RowContext CTX = new RowContext("670", "2021", "Submitted", "8201");

  private static CostBlock block(String volume, Integer cost, String perUnit) {
    return new CostBlock(
        volume == null ? null : new BigDecimal(volume),
        cost,
        perUnit == null ? null : new BigDecimal(perUnit));
  }

  private static Schedule2Response response(
      String comments,
      CostBlock purchasedLogCost,
      CostBlock purchasedWoodOverhead,
      CostBlock subtotal,
      CostBlock lessLogSales,
      CostBlock netPurchased,
      CostBlock totalCompanyLogging,
      CostBlock totalAverage) {
    return new Schedule2Response(
        8201L,
        2021,
        "Submitted",
        false,
        3,
        comments,
        null,
        purchasedLogCost,
        purchasedWoodOverhead,
        subtotal,
        lessLogSales,
        netPurchased,
        totalCompanyLogging,
        totalAverage,
        null);
  }

  /** A document carrying nothing but its comments — every block absent. */
  private static Schedule2Response noBlocks(String comments) {
    return response(comments, null, null, null, null, null, null, null);
  }

  /** A document with one entered sales figure and nothing else. */
  private static Schedule2Response salesOnly(CostBlock lessLogSales) {
    return response(null, null, null, null, lessLogSales, null, null, null);
  }

  /** Every block present, the final per-unit absent. */
  private static Schedule2Response fullDocument() {
    return response(
        "Carried\nfrom Sch 3\tverified&nbsp;",
        block("125000", 1234567, "9.88"),
        block("125000", 300000, "2.40"),
        block("125000", 1534567, "1234.5"),
        block("5000", 40000, "8.00"),
        block("120000", 1494567, "12.45"),
        block("90000", 2000000, "22.22"),
        block("210000", 3494567, null));
  }

  @Nested
  @DisplayName("the fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the header is legacy's twenty-six columns, in legacy's order")
    void headerIsTheLegacyTwentySixColumns() {
      // Transcribed from Schedule2Extract.java:136-163. PO&P_OH_* and SCH1_COSTS_* are legacy's
      // labels for cells legacy fed from Schedule 3 and Schedule 1 respectively — the label does
      // not describe its source, and is kept because the file is parsed by column position.
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "PO&P_COSTS_M3",
        "PO&P_COSTS_$",
        "PO&P_COSTS_$/M3",
        "PO&P_OH_M3",
        "PO&P_OH_$",
        "PO&P_$/M3",
        "SUBTOT_M3",
        "SUBTOT_$",
        "SUBTOT_$/M3",
        "LESS_SALES_M3",
        "LESS_SALES_$",
        "LESS_SALES_$/M3",
        "NET_PO&P_M3",
        "NET_PO&P_$",
        "NET_PO&P_$/M3",
        "SCH1_COSTS_M3",
        "SCH1_COSTS_$",
        "SCH1_COSTS_$/M3",
        "TOT_AVG_COSTS_M3",
        "TOT_AVG_COSTS_$",
        "TOT_AVG_COSTS_$/M3",
        "COMMENTS"
      };

      assertThat(SECTION.header()).hasSize(26).containsExactly(expected);
    }

    @Test
    @DisplayName("the title is legacy's title cell")
    void titleIsTheLegacyTitleCell() {
      // Schedule2Extract.java:35.
      assertThat(SECTION.title()).isEqualTo("**** Schedule 2 ****");
    }

    @Test
    @DisplayName("header() hands back a copy, so a caller cannot edit the columns")
    void headerIsDefensivelyCopied() {
      SECTION.header()[0] = "TAMPERED";

      assertThat(SECTION.header()[0]).isEqualTo("MILL_NUMBER");
    }
  }

  @Nested
  @DisplayName("the data row")
  class DataRow {

    @Test
    @DisplayName("every block is formatted into its legacy cell")
    void fullRowFormatsEveryBlock() {
      // Volumes and costs take the whole-number pattern: thousands grouping, no decimals. Every
      // $/m³ takes exactly two decimals even when the figure carries one (1234.5 -> 1,234.50), and
      // an absent per-unit is the null marker rather than a zero (ExtractFormat.java:46-53).
      String[] expected = {
        "670",
        "2021",
        "Submitted",
        "8201",
        "125,000",
        "1,234,567",
        "9.88",
        "125,000",
        "300,000",
        "2.40",
        "125,000",
        "1,534,567",
        "1,234.50",
        "5,000",
        "40,000",
        "8.00",
        "120,000",
        "1,494,567",
        "12.45",
        "90,000",
        "2,000,000",
        "22.22",
        "210,000",
        "3,494,567",
        "-",
        // Tab to two spaces, newline to one space, the &nbsp; entity dropped
        // (CoreUtil.replaceCharsForExtractFormat, transcribed at ExtractFormat.java:98-100).
        "Carried from Sch 3  verified"
      };

      Optional<String[]> row = SECTION.row(CTX, fullDocument(), true);

      assertThat(row).isPresent();
      assertThat(row.get()).hasSize(26).containsExactly(expected);
    }

    @Test
    @DisplayName("the row is exactly as wide as the header")
    void rowIsAsWideAsTheHeader() {
      assertThat(SECTION.row(CTX, fullDocument(), true).orElseThrow())
          .hasSameSizeAs(SECTION.header());
    }

    @Test
    @DisplayName("absent carried figures are the null marker, never the NO SCHEDULE 3 sentinel")
    void absentFiguresAreTheNullMarkerNotTheSentinel() {
      // Nearly the whole row is carried or derived from Schedule 3, so a Schedule 3 that exists
      // but holds no figures leaves those cells empty. Only Schedule 1 ever wrote
      // *** NO SCHEDULE 3 *** into its cells (Schedule1Section.java:123-134); Schedule 2 wrote the
      // ordinary null marker, and a consumer keyed on the sentinel would mis-read this section.
      String[] row = SECTION.row(CTX, noBlocks("comment only"), true).orElseThrow();

      assertThat(row).hasSize(26).doesNotContain(ExtractFormat.NO_SCHEDULE_3);
      // Cells 4..24 are the twenty-one figure cells; cell 25 is the comment.
      assertThat(Arrays.copyOfRange(row, 4, 25)).hasSize(21).containsOnly(ExtractFormat.NULL_VALUE);
      assertThat(row[25]).isEqualTo("comment only");
    }

    @Test
    @DisplayName("a block present but half-filled renders its cells independently")
    void halfFilledBlockRendersCellByCell() {
      Schedule2Response s2 =
          response(
              null,
              block(null, 1234567, null),
              null,
              null,
              block("5000", null, null),
              null,
              null,
              null);

      String[] row = SECTION.row(CTX, s2, true).orElseThrow();

      assertThat(row[4]).isEqualTo(ExtractFormat.NULL_VALUE);
      assertThat(row[5]).isEqualTo("1,234,567");
      assertThat(row[6]).isEqualTo(ExtractFormat.NULL_VALUE);
      assertThat(row[13]).isEqualTo("5,000");
      assertThat(row[14]).isEqualTo(ExtractFormat.NULL_VALUE);
      // Absent comments are the null marker, not an empty cell (ExtractFormat.java:82-87).
      assertThat(row[25]).isEqualTo(ExtractFormat.NULL_VALUE);
    }
  }

  @Nested
  @DisplayName("the markers")
  class Markers {

    @Test
    @DisplayName("a pair with no Schedule 3 gets the five-cell per-record marker")
    void pairWithoutAScheduleThreeGetsThePerRecordMarker() {
      // Legacy required BOTH schedules for a row (Schedule2Extract.java:58) and, pairing its three
      // lists by index, simply dropped the record. The rebuild pairs by (mill, year) and marks the
      // pair, so the absence is visible instead of shifting another pair's figures onto this line.
      String[] expected = {"670", "2021", "Submitted", "8201", "*** NO DATA FOUND ***"};

      Optional<String[]> row = SECTION.row(CTX, fullDocument(), false);

      assertThat(row).isPresent();
      assertThat(row.get()).hasSize(5).containsExactly(expected);
      assertThat(row.get()).doesNotContain(ExtractFormat.NO_SCHEDULE_3);
    }

    @Test
    @DisplayName("the whole-schedule marker is legacy's five cells")
    void wholeScheduleMarkerIsFiveCells() {
      // Schedule2Extract.java:40-47: the mills string, the year range, two null markers, the
      // marker text.
      String[] expected = {"670, 671", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***"};

      assertThat(SECTION.noDataRow("670, 671", "2020 - 2021")).hasSize(5).containsExactly(expected);
    }
  }

  @Nested
  @DisplayName("legacy checkEmpty")
  class Emptiness {

    @Test
    @DisplayName("an empty Schedule 2 emits no row at all")
    void emptyScheduleEmitsNoRow() {
      // Legacy's loop body never ran for an empty record, so the pair contributed no line at all —
      // distinct from the per-record marker, which only a NON-empty unpaired record gets.
      assertThat(Schedule2Section.isEmpty(noBlocks(null))).isTrue();
      assertThat(SECTION.row(CTX, noBlocks(null), true)).isEmpty();
      assertThat(SECTION.row(CTX, noBlocks(null), false)).isEmpty();
    }

    @Test
    @DisplayName("whitespace-only comments count as absent")
    void blankCommentsCountAsAbsent() {
      // Legacy trimmed this one field: isNullOrEmptyString(comments, true)
      // (Schedule2Extract.java:196).
      assertThat(Schedule2Section.isEmpty(noBlocks("   \t  "))).isTrue();
      assertThat(Schedule2Section.isEmpty(noBlocks("x"))).isFalse();
    }

    @Test
    @DisplayName("a zero purchased cost counts as entered, not as absent")
    void zeroPurchasedCostCountsAsEntered() {
      // Legacy tested presence with CoreUtil.isNullOrEmpty (CoreUtil.java:178-186), which
      // stringifies first — "0" is not empty, so a deliberately entered zero kept the row.
      Schedule2Response s2 =
          response(null, block(null, 0, null), null, null, null, null, null, null);

      assertThat(Schedule2Section.isEmpty(s2)).isFalse();
      assertThat(SECTION.row(CTX, s2, true).orElseThrow()[5]).isEqualTo("0");
    }

    @Test
    @DisplayName("a purchased volume without its cost is still empty — legacy tested the cost only")
    void purchasedVolumeWithoutCostIsStillEmpty() {
      // Schedule2Extract.java:192 checked getPurchasedLogCost().getCost() and never the volume,
      // because the volume is carried from Schedule 3 rather than entered on this page.
      Schedule2Response s2 =
          response(null, block("125000", null, "9.88"), null, null, null, null, null, null);

      assertThat(Schedule2Section.isEmpty(s2)).isTrue();
    }

    @Test
    @DisplayName("any one of the three less-log-sales figures keeps the row")
    void anyLessLogSalesFigureKeepsTheRow() {
      // Schedule2Extract.java:193-195 tested all three of the entered sales figures.
      assertThat(Schedule2Section.isEmpty(salesOnly(block("5000", null, null)))).isFalse();
      assertThat(Schedule2Section.isEmpty(salesOnly(block(null, 40000, null)))).isFalse();
      assertThat(Schedule2Section.isEmpty(salesOnly(block(null, null, "8.00")))).isFalse();
    }
  }
}
