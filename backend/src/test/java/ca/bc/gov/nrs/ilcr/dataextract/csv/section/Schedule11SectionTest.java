package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule11Section.Entry;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Basic Silviculture section, transcribed from legacy {@code Schedule11Extract}.
 *
 * <p>Its two total rows sit at different column offsets, which is the thing most likely to be
 * "fixed" by a later reader, so the offsets have a test of their own.
 */
@DisplayName("Schedule11Section — legacy Schedule11Extract")
class Schedule11SectionTest {

  /**
   * The leading cells for one (mill, year). The status is the SILVICULTURE track's description:
   * Schedule 11 is the one section whose status comes from that track rather than the Schedules
   * 1–10 one, and the caller resolves it before building the context.
   */
  private static final RowContext CTX = new RowContext("1234", "2021", "Submitted", "567");

  private static final RowContext OTHER_MILL = new RowContext("5678", "2021", "Verified", "890");

  private final Schedule11Section section = new Schedule11Section();

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static SilvicultureLocation location(
      String location,
      boolean enhancedIndicator,
      String becLabel,
      String netArea,
      Integer actualCost,
      Integer plannedCost,
      Integer totalCost,
      String costPerNetArea,
      String comments) {
    return new SilvicultureLocation(
        1L,
        location,
        enhancedIndicator,
        7L,
        becLabel,
        netArea == null ? null : bd(netArea),
        actualCost,
        plannedCost,
        totalCost,
        costPerNetArea == null ? null : bd(costPerNetArea),
        comments,
        1);
  }

  private static Schedule11Response response(List<SilvicultureLocation> locations) {
    return new Schedule11Response(567L, 2021, "S", false, null, locations, null, null);
  }

  /** The first location of every multi-row fixture here; 1250.5 ha, 45,000 actual. */
  private static SilvicultureLocation firstLocation() {
    return location(
        "Block 42", true, "IDFdk3", "1250.5", 45000, 30000, 75000, "59.9760", "Planted\tspring");
  }

  /** The second; no actual cost, a blank BEC label and no comment. */
  private static SilvicultureLocation secondLocation() {
    return location("Block 43", false, "", "499.5", null, 12000, 12000, "24.0240", null);
  }

  @Nested
  @DisplayName("header")
  class Header {

    @Test
    @DisplayName("is legacy Schedule11Extract's thirteen columns, in order")
    void isLegacyHeader() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "LOCATION",
        "BIOGEO_SUB_VAR",
        "ES",
        "NAR_HA",
        "ACTUAL_$",
        "PLANNED_$",
        "ACT_PLAN_$",
        "TOT_CPU_$/HA",
        "COMMENTS",
      };

      assertThat(section.header()).containsExactly(expected);
      assertThat(section.header()).hasSize(13);
    }

    @Test
    @DisplayName("is a fresh array each call")
    void isDefensivelyCopied() {
      String[] first = section.header();
      first[0] = "MUTATED";

      assertThat(section.header()[0]).isEqualTo("MILL_NUMBER");
    }
  }

  @Nested
  @DisplayName("title")
  class Title {

    @Test
    @DisplayName("is the legacy banner verbatim")
    void isLegacyBanner() {
      assertThat(section.title()).isEqualTo("**** Schedule 11 ****");
    }
  }

  @Nested
  @DisplayName("data row")
  class DataRow {

    @Test
    @DisplayName("builds every cell of a populated location")
    void buildsPopulatedRow() {
      String[] expected = {
        "1234",
        "2021",
        // The silviculture track's status description, not the Schedules 1–10 track's.
        "Submitted",
        "567",
        "Block 42",
        "IDFdk3",
        // The stored ENHANCED_IND comes back as a boolean and goes out as legacy's Y/N, not as a
        // reader-facing label.
        "Y",
        // The area pattern has no decimals, and 1250.5 rounds to 1,250 under DecimalFormat's
        // HALF_EVEN default rather than up.
        "1,250",
        "45,000.00",
        "30,000.00",
        "75,000.00",
        "59.98",
        // Comments are written raw — the sanitising formatter is NOT applied here, so the tab
        // survives into the file, exactly as legacy wrote it.
        "Planted\tspring",
      };

      List<String[]> rows =
          section.rows(List.of(new Entry(CTX, response(List.of(firstLocation())))));

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0)).containsExactly(expected);
    }

    @Test
    @DisplayName("is as wide as the header")
    void isHeaderWidth() {
      List<String[]> rows =
          section.rows(List.of(new Entry(CTX, response(List.of(firstLocation())))));

      assertThat(rows.get(0)).hasSameSizeAs(section.header());
    }

    @Test
    @DisplayName("an absent cost, blank BEC label or absent comment is the null marker")
    void absentValuesAreNullMarkers() {
      List<String[]> rows =
          section.rows(List.of(new Entry(CTX, response(List.of(secondLocation())))));
      String[] row = rows.get(0);

      // A blank BEC label happens legitimately: the label is assembled from four nullable code
      // parts, each mapped to the empty string, so all-null gives "" rather than null.
      assertThat(row[5]).isEqualTo("-");
      assertThat(row[6]).isEqualTo("N");
      assertThat(row[8]).isEqualTo("-");
      assertThat(row[12]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the final total sits one column right of the sub-totals, which is deliberate")
  class TotalRowsAreOffsetByOneColumn {

    /** Where a sub-total's label lands: five blanks in ({@code Schedule11Extract.java:119}). */
    private static final int SUB_TOTAL_LABEL = 5;

    /** Where the final total's label lands: six blanks in ({@code Schedule11Extract.java:70}). */
    private static final int FINAL_TOTAL_LABEL = 6;

    @Test
    @DisplayName("a sub-total labels its sixth cell and the final total its seventh")
    void theTwoTotalsAreOneColumnApart() {
      // Legacy wrote five leading blanks before the boundary totals (Schedule11Extract.java:119)
      // and six before the closing one (Schedule11Extract.java:70), so the last total row of the
      // section is shifted a column right of the ones above it. The file reads as misaligned and
      // legacy's did too; keeping the shift is the point.
      List<String[]> rows =
          section.rows(
              List.of(
                  new Entry(CTX, response(List.of(firstLocation()))),
                  new Entry(OTHER_MILL, response(List.of(secondLocation())))));

      assertThat(rows).hasSize(4);
      String[] subTotal = rows.get(1);
      String[] finalTotal = rows.get(3);

      assertThat(subTotal[SUB_TOTAL_LABEL]).isEqualTo("Total:");
      assertThat(finalTotal[FINAL_TOTAL_LABEL]).isEqualTo("Total:");
      // And the cell where the other row's label sits is blank in each, which is what makes the
      // shift visible in the file rather than merely internal.
      assertThat(subTotal[SUB_TOTAL_LABEL - 1]).isEmpty();
      assertThat(finalTotal[SUB_TOTAL_LABEL]).isEmpty();
    }

    @Test
    @DisplayName("the two totals are eleven and twelve cells wide, not the header's thirteen")
    void theTwoTotalsAreDifferentWidths() {
      List<String[]> rows =
          section.rows(
              List.of(
                  new Entry(CTX, response(List.of(firstLocation()))),
                  new Entry(OTHER_MILL, response(List.of(secondLocation())))));

      assertThat(rows.get(1)).hasSize(11);
      assertThat(rows.get(3)).hasSize(12);
      assertThat(section.header()).hasSize(13);
    }

    @Test
    @DisplayName("a sub-total covers only the entry it closes, and the accumulators reset")
    void subTotalCoversOnlyItsOwnEntry() {
      List<String[]> rows =
          section.rows(
              List.of(
                  new Entry(CTX, response(List.of(firstLocation()))),
                  new Entry(OTHER_MILL, response(List.of(secondLocation())))));
      String[] subTotal = rows.get(1);
      String[] finalTotal = rows.get(3);

      // First entry: 1250.5 ha, 45,000 actual, 30,000 planned.
      assertThat(subTotal[SUB_TOTAL_LABEL + 1]).isEqualTo("1,250");
      assertThat(subTotal[SUB_TOTAL_LABEL + 2]).isEqualTo("45,000.00");
      assertThat(subTotal[SUB_TOTAL_LABEL + 3]).isEqualTo("30,000.00");
      assertThat(subTotal[SUB_TOTAL_LABEL + 4]).isEqualTo("75,000.00");

      // Second entry alone: 499.5 ha (499.5 rounds to 500 under HALF_EVEN), no actual cost at
      // all, so the actual column is the null marker rather than a zero.
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 1]).isEqualTo("500");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 2]).isEqualTo("-");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 3]).isEqualTo("12,000.00");
      // With no actual figure to add, the combined column is the null marker too: legacy's
      // addition is null when either side is.
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 4]).isEqualTo("-");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 5]).isEqualTo("-");
    }

    @Test
    @DisplayName("a single entry gets only the final total, at the shifted offset")
    void singleEntryGetsOnlyTheFinalTotal() {
      List<String[]> rows =
          section.rows(
              List.of(new Entry(CTX, response(List.of(firstLocation(), secondLocation())))));

      assertThat(rows).hasSize(3);
      String[] finalTotal = rows.get(2);

      assertThat(finalTotal).hasSize(12);
      assertThat(finalTotal[FINAL_TOTAL_LABEL]).isEqualTo("Total:");
      // 1250.5 + 499.5 = 1750.0 ha; 45,000 + 30,000 + 12,000 = 87,000 combined; and
      // 87,000 / 1,750 is 49.714… which legacy's division rounds to 49.71.
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 1]).isEqualTo("1,750");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 2]).isEqualTo("45,000.00");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 3]).isEqualTo("42,000.00");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 4]).isEqualTo("87,000.00");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 5]).isEqualTo("49.71");
    }
  }

  /**
   * Legacy {@code sumBigDecimalAreas} adds the areas at full precision and rounds the SUM once, to
   * one place ({@code HALF_UP}); the area cell then prints {@code #,###,##0} under DecimalFormat's
   * {@code HALF_EVEN}. The nearby cost helper, {@code sumBigDecimalCosts}, rounds EVERY term first,
   * and that is the substitution this fixture is built to catch.
   */
  @Nested
  @DisplayName("the NAR_HA total sums at full precision and rounds once")
  class AreaTotalRoundsOnce {

    /** Where the final total's label lands: six blanks in ({@code Schedule11Extract.java:70}). */
    private static final int FINAL_TOTAL_LABEL = 6;

    @Test
    @DisplayName("100.75 + 99.75 ha totals 200, not the 201 that rounding each term first gives")
    void areaTotal_isSumThenRoundNotRoundThenSum() {
      // Production: 100.75 + 99.75 = 200.50 -> setScale(1, HALF_UP) = 200.5 -> area() HALF_EVEN
      // -> 200 (even neighbour). Rounding each term first, to one place (100.8 + 99.8 = 200.6) or
      // to a whole number as sumCosts does (101 + 100 = 201), prints "201" either way, so a swap
      // to a per-term helper fails here. Skipping the one-place step (raw 200.50 -> "200") is the
      // one alternative this cell cannot see; no fixture separates it, because HALF_UP to one
      // place only moves a figure whose hundredths are exactly 5, and HALF_EVEN then lands on the
      // same whole number either way.
      //
      // The costs are Integer (SilvicultureLocation.actualCost / plannedCost), so sumCosts's
      // per-term whole rounding cannot be discriminated on the money cells; they are asserted
      // only so the CPU's numerator is pinned: 2,010 + 2,000 = 4,010, and 4,010 / 200.5 = 20.00
      // exactly, where 4,010 / 200.6 = 19.990… -> "19.99". So the CPU cell catches the swap too.
      SilvicultureLocation first =
          location("Block 50", false, "SBSmc2", "100.75", 2010, 0, 2010, "19.95", null);
      SilvicultureLocation second =
          location("Block 51", false, "SBSmc2", "99.75", 0, 2000, 2000, "20.05", null);

      List<String[]> rows = section.rows(List.of(new Entry(CTX, response(List.of(first, second)))));

      assertThat(rows).hasSize(3);
      // The rows' own area cells: 100.75 -> "101" and 99.75 -> "100" under HALF_EVEN, so the
      // displayed cells add to 201 while the total below them reads 200. Legacy's file did the
      // same; the total is not the sum of the printed cells.
      assertThat(rows.get(0)[7]).isEqualTo("101");
      assertThat(rows.get(1)[7]).isEqualTo("100");
      String[] finalTotal = rows.get(2);
      assertThat(finalTotal[FINAL_TOTAL_LABEL]).isEqualTo("Total:");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 1]).isEqualTo("200");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 2]).isEqualTo("2,010.00");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 3]).isEqualTo("2,000.00");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 4]).isEqualTo("4,010.00");
      assertThat(finalTotal[FINAL_TOTAL_LABEL + 5]).isEqualTo("20.00");
    }
  }

  @Nested
  @DisplayName("entries without locations")
  class EntriesWithoutLocations {

    @Test
    @DisplayName("contribute neither a row nor a total, and do not trigger a boundary total")
    void contributeNothing() {
      // A (mill, year) with an initiated but empty Schedule 11 must not push an empty "Total:"
      // row into the file, nor split the neighbouring entries' accumulations.
      List<String[]> rows =
          section.rows(
              List.of(
                  new Entry(CTX, response(List.of(firstLocation()))),
                  new Entry(OTHER_MILL, response(List.of())),
                  new Entry(OTHER_MILL, response(null))));

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0)[4]).isEqualTo("Block 42");
      assertThat(rows.get(1)[6]).isEqualTo("Total:");
    }

    @Test
    @DisplayName("no entries at all produce no rows, not an empty total")
    void noEntriesProduceNoRows() {
      assertThat(section.rows(List.of())).isEmpty();
      assertThat(section.rows(List.of(new Entry(CTX, response(List.of()))))).isEmpty();
    }
  }

  @Nested
  @DisplayName("markers")
  class Markers {

    @Test
    @DisplayName("the whole-schedule marker is the shared five-cell shape")
    void wholeScheduleMarkerIsFiveCells() {
      String[] expected = {
        "Included Mills: 1234", "2019 - 2021", "-", "-", "*** NO DATA FOUND ***",
      };

      assertThat(section.noDataRow("Included Mills: 1234", "2019 - 2021"))
          .containsExactly(expected);
    }
  }
}
