package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import ca.bc.gov.nrs.ilcr.schedule3.dto.TimberBlock;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Schedule3Section} — the column fidelity of the fifty-six-column Schedule 3
 * CSV section, including the three legacy quirks the rebuild keeps verbatim.
 *
 * <p>The header is written out literally rather than read from the production constant: it is the
 * regression lock on a file that ministry spreadsheets parse by column position, so a rename or a
 * reorder has to fail a test rather than quietly ship.
 */
@DisplayName("Schedule3Section — legacy Schedule3Extract column fidelity")
class Schedule3SectionTest {

  private static final Schedule3Section SECTION = new Schedule3Section();

  private static final RowContext CTX = new RowContext("670", "2021", "Submitted", "8201");

  /**
   * A figure no cell of a correctly built row may carry. It is parked on {@code
   * subtotalActualCosts}, the field the section is expected to IGNORE, so a wiring mistake shows up
   * as this number appearing in the file.
   */
  private static final ThreeColumnTotal NEVER_READ = new ThreeColumnTotal(777L, 777L, 777L);

  private static CostLine line(int code, Integer harvest, Integer pop, Integer crown) {
    return new CostLine(code, harvest, pop, crown);
  }

  private static TimberBlock timber(String volume, Long cost, String perUnit) {
    return new TimberBlock(
        volume == null ? null : new BigDecimal(volume),
        cost,
        perUnit == null ? null : new BigDecimal(perUnit));
  }

  /** A response carrying only what the section reads, plus the never-read sentinel. */
  private static Schedule3Response response(
      String comments,
      List<CostLine> lineItems,
      TimberBlock popTimber,
      TimberBlock crownTimber,
      TimberBlock totalOverhead,
      ThreeColumnTotal subtotalOtherCosts,
      ThreeColumnTotal includedUnacceptableCosts,
      ThreeColumnTotal totalCosts) {
    return new Schedule3Response(
        8201L,
        2021,
        "Submitted",
        false,
        3,
        "N",
        comments,
        null,
        lineItems,
        popTimber,
        crownTimber,
        totalOverhead,
        subtotalOtherCosts,
        NEVER_READ,
        includedUnacceptableCosts,
        totalCosts,
        0,
        0,
        null,
        null);
  }

  private static Schedule3Response nothingEntered() {
    return response(null, null, null, null, null, null, null, null);
  }

  private static Schedule3Response withLines(CostLine... items) {
    return response(null, List.of(items), null, null, null, null, null, null);
  }

  private static Schedule3Response withComments(String comments) {
    return response(comments, null, null, null, null, null, null, null);
  }

  private static Schedule3Response withTimber(
      TimberBlock popTimber, TimberBlock crownTimber, TimberBlock totalOverhead) {
    return response(null, List.of(), popTimber, crownTimber, totalOverhead, null, null, null);
  }

  /** The eleven fixed admin-cost lines (27-37) with a distinct figure in every column. */
  private static List<CostLine> fixedLines() {
    return List.of(
        line(27, 100000, 40000, 60000),
        line(28, 50000, 20000, 30000),
        // Annual Rents and Silviculture Admin are Harvest-only, so the load sets PO&P to zero.
        line(29, 30000, 0, 30000),
        line(30, 285000, 155000, 130000),
        line(31, 40000, 10000, 30000),
        line(32, 25000, 5000, 20000),
        line(33, 60000, 30000, 30000),
        line(34, 12000, 3000, 9000),
        line(35, 7000, 2000, 5000),
        line(36, 1500000, 500000, 1000000),
        line(37, 80000, 0, 80000));
  }

  private static Schedule3Response fullDocument() {
    return response(
        "Verified\nby region\tA&nbsp;",
        fixedLines(),
        timber("125000", 1100000L, "8.80"),
        timber("250000", 2200000L, "8.80"),
        timber("375000", 3300000L, null),
        new ThreeColumnTotal(1234567L, 234567L, 1000000L),
        new ThreeColumnTotal(45000L, 0L, 45000L),
        new ThreeColumnTotal(3300000L, 1100000L, 2200000L));
  }

  @Nested
  @DisplayName("the fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the header is legacy's fifty-six columns, in legacy's order")
    void headerIsTheLegacyFiftySixColumns() {
      // Transcribed from Schedule3Extract.java:183-240.
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "LIC_TOTAL_$",
        "LIC_PO&P_$",
        "LIC_CROWN_$",
        "TAX_TOTAL_$",
        "TAX_PO&P_$",
        "TAX_CROWN_$",
        "RENTS_TOTAL_$",
        "RENTS_CROWN_$",
        "WAGE_TOTAL_$",
        "WAGE_PO&P_$",
        "WAGE_CROWN_$",
        "VEH_TOTAL_$",
        "VEH_PO&P_$",
        "VEH_CROWN_$",
        "OFFICE_TOTAL_$",
        "OFFICE_PO&P_$",
        "OFFICE_CROWN_$",
        "SCALING_TOTAL_$",
        "SCALING_PO&P_$",
        "SCALING_CROWN_$",
        "CRUIS_TOTAL_$",
        "CRUIS_PO&P_$",
        "CRUIS_CROWN_$",
        "RES_TOTAL_$",
        "RES_PO&P_$",
        "RES_CROWN_$",
        "DEPREC_TOTAL_$",
        "DEPREC_PO&P_$",
        "DEPREC_CROWN_$",
        "SILVI_TOTAL_$",
        "SILVI_CROWN_$",
        "SUB_OTH_TOTAL_$",
        "SUB_OTH_PO&P_$",
        "SUB_OTH_CROWN_$",
        "SUB_ACT_TOTAL_$",
        "SUB_ACT_PO&P_$",
        "SUB_ACT_CROWN_$",
        "INCL_UNAC_TOTAL_$",
        "INCL_UNAC_CROWN_$",
        "TOT_TOTAL_$",
        "TOT_PO&P_$",
        "TOT_CROWN_$",
        "PO&P_M3",
        "PO&P_$",
        "PO&P_$/M3",
        "CROWN_M3",
        "CROWN_$",
        "CROWN_$/M3",
        "TOTAL_OH_M3",
        "TOTAL_OH_$",
        "TOTAL_OH_$/M3",
        "COMMENTS"
      };

      assertThat(SECTION.header()).hasSize(56).containsExactly(expected);
    }

    @Test
    @DisplayName("Annual Rents and Silviculture Admin carry no PO&P column")
    void harvestOnlyLinesHaveTwoColumnsNotThree() {
      // Both lines are Harvest-only on the screen, so legacy gave them a TOTAL and a CROWN cell
      // and no PO&P cell (Schedule3Extract.java:71-73, :117-119). That is why the row is 56 cells
      // and not 58, and why the gap must not be "completed" by a later reader.
      String[] header = SECTION.header();

      assertThat(Arrays.copyOfRange(header, 10, 12))
          .containsExactly("RENTS_TOTAL_$", "RENTS_CROWN_$");
      assertThat(Arrays.copyOfRange(header, 33, 35))
          .containsExactly("SILVI_TOTAL_$", "SILVI_CROWN_$");
      assertThat(header).doesNotContain("RENTS_PO&P_$", "SILVI_PO&P_$");
    }

    @Test
    @DisplayName("the title is legacy's title cell")
    void titleIsTheLegacyTitleCell() {
      // Schedule3Extract.java:30.
      assertThat(SECTION.title()).isEqualTo("**** Schedule 3 ****");
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
    @DisplayName("every line, total and timber block is formatted into its legacy cell")
    void fullRowFormatsEveryCell() {
      // Money and volume cells take the whole-number pattern: thousands grouping, no decimals.
      // The two per-unit cells take exactly two decimals, and the absent third is the null marker
      // rather than a zero (ExtractFormat.java:46-53).
      String[] expected = {
        "670",
        "2021",
        "Submitted",
        "8201",
        "100,000",
        "40,000",
        "60,000",
        "50,000",
        "20,000",
        "30,000",
        "30,000",
        "30,000",
        "285,000",
        "155,000",
        "130,000",
        "40,000",
        "10,000",
        "30,000",
        "25,000",
        "5,000",
        "20,000",
        "60,000",
        "30,000",
        "30,000",
        "12,000",
        "3,000",
        "9,000",
        "7,000",
        "2,000",
        "5,000",
        "1,500,000",
        "500,000",
        "1,000,000",
        "80,000",
        "80,000",
        "1,234,567",
        "234,567",
        "1,000,000",
        // SUB_ACT_* repeats the SUB_OTH_* triple above it — see the quirks group below.
        "1,234,567",
        "234,567",
        "1,000,000",
        // INCL_UNAC_CROWN_$ repeats INCL_UNAC_TOTAL_$ — see the quirks group below.
        "45,000",
        "45,000",
        "3,300,000",
        "1,100,000",
        "2,200,000",
        "125,000",
        "1,100,000",
        "8.80",
        "250,000",
        "2,200,000",
        "8.80",
        "375,000",
        "3,300,000",
        "-",
        // Tab to two spaces, newline to one space, the &nbsp; entity dropped
        // (CoreUtil.replaceCharsForExtractFormat, transcribed at ExtractFormat.java:98-100).
        "Verified by region  A"
      };

      String[] row = SECTION.row(CTX, fullDocument());

      assertThat(row).hasSize(56).containsExactly(expected);
    }

    @Test
    @DisplayName("the row is exactly as wide as the header")
    void rowIsAsWideAsTheHeader() {
      assertThat(SECTION.row(CTX, fullDocument())).hasSameSizeAs(SECTION.header());
    }

    @Test
    @DisplayName("the lines are placed by cost-item code, not by list order")
    void linesArePlacedByCostItemCode() {
      // The section indexes the list by code before reading it, so a repository that returns the
      // eleven lines in another order (or omits some) still fills the right columns.
      Schedule3Response s3 =
          withLines(
              line(37, 80000, 0, 80000), line(27, 100000, 40000, 60000), line(99, 4242, 1, 1));

      String[] row = SECTION.row(CTX, s3);

      assertThat(row[4]).isEqualTo("100,000");
      assertThat(row[33]).isEqualTo("80,000");
      // Code 99 has no column of its own, so it is dropped rather than shifting the row along.
      assertThat(row).hasSize(56).doesNotContain("4,242");
    }

    @Test
    @DisplayName("a missing line leaves the null marker in its cells")
    void missingLineLeavesTheNullMarker() {
      String[] row = SECTION.row(CTX, withLines(line(27, 100000, 40000, 60000)));

      // Taxes (28) was not stored, so its three cells are empty rather than zero.
      assertThat(Arrays.copyOfRange(row, 7, 10)).containsOnly(ExtractFormat.NULL_VALUE);
      // So are the derived totals and timber blocks this document does not carry.
      assertThat(Arrays.copyOfRange(row, 35, 55)).containsOnly(ExtractFormat.NULL_VALUE);
    }
  }

  @Nested
  @DisplayName("the deliberately preserved legacy quirks")
  class Quirks {

    @Test
    @DisplayName("SUB_ACT_* deliberately repeats the SUB_OTH_* triple — do not 'fix' it")
    void subtotalActualTripleRepeatsSubtotalOther() {
      // Legacy filled SUB_ACT_TOTAL/PO&P/CROWN from getAcceptableCostsTotals()
      // (Schedule3Extract.java:127-131), which is the same sum of Other Acceptable Costs rows that
      // feeds getSubtotalOtherCosts() — so six columns carry one triple and the real Subtotal
      // Actual Costs never reaches the file. Kept verbatim: consumers read these positions today.
      // The proof is that subtotalActualCosts holds the never-read sentinel and does NOT appear.
      String[] row = SECTION.row(CTX, fullDocument());

      assertThat(Arrays.copyOfRange(row, 35, 38))
          .containsExactly("1,234,567", "234,567", "1,000,000");
      assertThat(Arrays.copyOfRange(row, 38, 41)).containsExactly(Arrays.copyOfRange(row, 35, 38));
      assertThat(row).doesNotContain("777");
    }

    @Test
    @DisplayName("INCL_UNAC_CROWN_$ deliberately repeats INCL_UNAC_TOTAL_$ — do not 'fix' it")
    void includedUnacceptableCrownRepeatsItsTotal() {
      // Schedule3Extract.java:133-135 wrote getUnaccecptableCostsTotals().getTotalCost() into BOTH
      // cells. The distinct PO&P and Crown figures below prove the section reads the Harvest
      // figure twice rather than taking the block's own crown.
      Schedule3Response s3 =
          response(
              null,
              fixedLines(),
              null,
              null,
              null,
              null,
              new ThreeColumnTotal(45000L, 11111L, 33889L),
              null);

      String[] row = SECTION.row(CTX, s3);

      assertThat(row[41]).isEqualTo("45,000");
      assertThat(row[42]).isEqualTo("45,000");
      assertThat(row).doesNotContain("11,111", "33,889");
    }

    @Test
    @DisplayName("the Cruising pair deliberately precedes the Residue pair, unlike the screen")
    void cruisingColumnsPrecedeResidueColumns() {
      // The screen lists Residue/Waste above Cruising/Layout, but legacy emitted Cruising first
      // (Schedule3Extract.java:99-109, header :208-213). Swapping them to match the screen would
      // silently transpose two column groups in every consumer's spreadsheet.
      assertThat(Arrays.copyOfRange(SECTION.header(), 24, 30))
          .containsExactly(
              "CRUIS_TOTAL_$",
              "CRUIS_PO&P_$",
              "CRUIS_CROWN_$",
              "RES_TOTAL_$",
              "RES_PO&P_$",
              "RES_CROWN_$");
      // Cruising (34) holds 12,000 and Residue (35) holds 7,000 in the fixture.
      assertThat(Arrays.copyOfRange(SECTION.row(CTX, fullDocument()), 24, 30))
          .containsExactly("12,000", "3,000", "9,000", "7,000", "2,000", "5,000");
    }

    @Test
    @DisplayName("an empty record is a LONE marker cell, not a four-cell identified one")
    void emptyRecordIsALoneMarkerCell() {
      // Every other section identifies its per-record marker with the four leading cells. Legacy
      // had commented those four expressions out here (Schedule3Extract.java:164-172), leaving a
      // one-cell row whose mill and year are unrecoverable from the file. Kept verbatim.
      String[] row = SECTION.row(CTX, nothingEntered());

      assertThat(row).hasSize(1).containsExactly("*** NO DATA FOUND ***");
      assertThat(row).doesNotContain("670", "2021", "8201");
    }

    @Test
    @DisplayName("the whole-schedule marker keeps the ordinary five cells")
    void wholeScheduleMarkerIsFiveCells() {
      // The lone-cell quirk is the PER-RECORD marker only; the whole-schedule marker is the same
      // five cells as every other builder's (Schedule3Extract.java:35-42).
      String[] expected = {"670, 671", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***"};

      assertThat(SECTION.noDataRow("670, 671", "2020 - 2021")).hasSize(5).containsExactly(expected);
    }
  }

  @Nested
  @DisplayName("legacy checkEmpty")
  class Emptiness {

    @Test
    @DisplayName("nothing entered anywhere is empty")
    void nothingEnteredIsEmpty() {
      assertThat(Schedule3Section.isEmpty(nothingEntered())).isTrue();
      assertThat(Schedule3Section.isEmpty(withLines(line(27, null, null, null)))).isTrue();
    }

    @Test
    @DisplayName("any one entered figure on any line keeps the record")
    void anyEnteredFigureKeepsTheRecord() {
      // Schedule3Extract.java:268-299 tested all three columns of all eleven lines.
      assertThat(Schedule3Section.isEmpty(withLines(line(31, 40000, null, null)))).isFalse();
      assertThat(Schedule3Section.isEmpty(withLines(line(31, null, 10000, null)))).isFalse();
      assertThat(Schedule3Section.isEmpty(withLines(line(31, null, null, 30000)))).isFalse();
    }

    @Test
    @DisplayName("a zero figure counts as entered, not as absent")
    void zeroFigureCountsAsEntered() {
      // Legacy tested presence with CoreUtil.isNullOrEmpty (CoreUtil.java:178-186), which
      // stringifies first — "0" is not empty, so a Harvest-only line's zero PO&P kept the record.
      assertThat(Schedule3Section.isEmpty(withLines(line(29, null, 0, null)))).isFalse();
    }

    @Test
    @DisplayName("a timber volume alone keeps the record")
    void timberVolumeAloneKeepsTheRecord() {
      // Schedule3Extract.java:300-305. The section tests the three VOLUMES only: a derived cost or
      // per-unit cannot exist without one, so legacy's extra cost-volume checks add nothing.
      TimberBlock volume = timber("125000", null, null);

      assertThat(Schedule3Section.isEmpty(withTimber(volume, null, null))).isFalse();
      assertThat(Schedule3Section.isEmpty(withTimber(null, volume, null))).isFalse();
      assertThat(Schedule3Section.isEmpty(withTimber(null, null, volume))).isFalse();
      // A cost with no volume does not, which is unreachable in practice but pins the check.
      assertThat(Schedule3Section.isEmpty(withTimber(timber(null, 1100000L, null), null, null)))
          .isTrue();
    }

    @Test
    @DisplayName("comments alone keep the record, but whitespace-only comments do not")
    void commentsAloneKeepTheRecord() {
      // Legacy trimmed this one field: isNullOrEmptyString(comments, true)
      // (Schedule3Extract.java:306).
      assertThat(Schedule3Section.isEmpty(withComments("x"))).isFalse();
      assertThat(Schedule3Section.isEmpty(withComments("  \t "))).isTrue();
    }

    @Test
    @DisplayName("the derived totals alone do NOT keep the record")
    void derivedTotalsAloneDoNotKeepTheRecord() {
      // Legacy checked only ENTERED figures, so a document carrying nothing but server-derived
      // subtotals (which seed at zero and are therefore always present) is still empty, and still
      // gets the lone marker cell rather than a row of zeros.
      ThreeColumnTotal zero = new ThreeColumnTotal(0L, 0L, 0L);
      Schedule3Response s3 = response(null, List.of(), null, null, null, zero, zero, zero);

      assertThat(Schedule3Section.isEmpty(s3)).isTrue();
      assertThat(SECTION.row(CTX, s3)).hasSize(1);
    }
  }
}
