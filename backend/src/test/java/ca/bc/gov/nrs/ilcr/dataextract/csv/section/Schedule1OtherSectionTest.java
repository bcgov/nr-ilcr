package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule1OtherSection}. Beyond the header and title locks, this pins the two
 * legacy quirks the section keeps verbatim: every itemized row shows the SHARED Other-Costs volume
 * (so the total's volume is that one figure times the row count, not a sum of distinct volumes),
 * and the total row formats the summed VOLUME to two decimals while formatting the summed COST to
 * none — the inverse of the rows above it.
 */
@DisplayName("Schedule1OtherSection — the itemized Other Costs rows and their Total row")
class Schedule1OtherSectionTest {

  private static final String NO_DATA = "*** NO DATA FOUND ***";

  private static final RowContext CTX = new RowContext("670", "2021", "Draft", "514");

  private final Schedule1OtherSection section = new Schedule1OtherSection();

  @Nested
  @DisplayName("fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the title is legacy's verbatim")
    void title_isVerbatim() {
      // Schedule1OtherExtract.java:33.
      assertThat(section.title()).isEqualTo("**** Schedule 1 - Other Costs ****");
    }

    @Test
    @DisplayName("the header is the eight legacy columns, in order")
    void header_isTheEightLegacyColumns() {
      // Schedule1OtherExtract.java:109-118. Written out literally so a rename or reorder fails
      // here rather than in a downstream spreadsheet.
      assertThat(section.header())
          .containsExactly(
              "MILL_NUMBER",
              "REPORTING_YEAR",
              "STATUS",
              "MILL_ID",
              "DESCRIPTION",
              "VOLUME",
              "COST",
              "CPU_$/M3");
      assertThat(section.header()).hasSize(8);
    }

    @Test
    @DisplayName("header() hands out a copy, so a caller cannot mutate the shared columns")
    void header_isDefensivelyCopied() {
      String[] first = section.header();
      first[4] = "TAMPERED";

      assertThat(section.header()[4]).isEqualTo("DESCRIPTION");
    }
  }

  @Nested
  @DisplayName("the itemized rows")
  class ItemizedRows {

    @Test
    @DisplayName("each itemized cost becomes one row, then the Total row")
    void twoItems_becomeTwoRowsAndATotal() {
      OtherCostsDocument document =
          document("1500", row("Aerial\tsurvey", 1_200, "0.8"), row("Consulting", 1_800, null));

      List<String[]> rows = section.rows(CTX, document);

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0))
          .containsExactly(
              "670",
              "2021",
              "Draft",
              "514",
              "Aerial  survey", // the tab legacy replaced with two spaces
              "1,500", // VOLUME — grouped, no decimals (###,###,##0)
              "1,200", // COST — likewise
              "0.80"); // CPU_$/M3 — always exactly two decimals
      assertThat(rows.get(1))
          .containsExactly("670", "2021", "Draft", "514", "Consulting", "1,500", "1,800", "-");
    }

    @Test
    @DisplayName("every row carries the one SHARED volume, because no per-row volume is stored")
    void everyRow_carriesTheSharedVolume() {
      OtherCostsDocument document =
          document(
              "1000",
              row("First", 100, "0.1"),
              row("Second", 100, "0.1"),
              row("Third", 100, "0.1"));

      List<String[]> rows = section.rows(CTX, document);

      // Three distinct costs but one volume: the Other-Costs owner stamps the volume on the
      // summary, not on the rows, and legacy's rows all showed that single figure.
      assertThat(rows).hasSize(4);
      assertThat(rows.get(0)[5]).isEqualTo("1,000");
      assertThat(rows.get(1)[5]).isEqualTo("1,000");
      assertThat(rows.get(2)[5]).isEqualTo("1,000");
      // And so the total's volume is the shared figure times the row count, not a sum of three
      // different volumes.
      assertThat(rows.get(3)[5]).isEqualTo("3,000.00");
    }

    @Test
    @DisplayName("a null description renders the null marker")
    void nullDescription_rendersTheNullMarker() {
      List<String[]> rows = section.rows(CTX, document("1000", row(null, 500, null)));

      assertThat(rows.get(0)[4]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the Total row")
  class TotalRow {

    @Test
    @DisplayName("the summed volume gets two decimals and the summed cost gets none")
    void totalRow_invertsTheUsualDecimalPairing() {
      OtherCostsDocument document =
          document("1500", row("Aerial survey", 1_200, "0.8"), row("Consulting", 1_800, null));

      List<String[]> rows = section.rows(CTX, document);

      // Schedule1OtherExtract.java:85-92. Both sums are 3000 here, which is the point: legacy
      // printed the VOLUME total with two decimals and the COST total with none, the opposite of
      // the VOLUME/COST columns above. Not to be tidied into a consistent pairing.
      assertThat(rows.get(2))
          .containsExactly("", "", "", "", "Total:", "3,000.00", "3,000", "1.00");
    }

    @Test
    @DisplayName("the Total row drops the four leading context cells")
    void totalRow_hasNoContextCells() {
      OtherCostsDocument document = document("1500", row("Aerial survey", 1_200, "0.8"));

      List<String[]> rows = section.rows(CTX, document);

      // Legacy built the total row from scratch rather than from the row context, so the mill,
      // year, status and id cells are empty rather than repeated.
      assertThat(Arrays.copyOf(rows.get(rows.size() - 1), 4)).containsExactly("", "", "", "");
    }

    @Test
    @DisplayName("the volume total's two decimals are decoration: each term is rounded whole first")
    void totalRow_volumeIsRoundedWholeThenPrintedWithTwoPlaces() {
      OtherCostsDocument document = document("1234.40", row("Field crew", 1_000, "0.81"));

      List<String[]> rows = section.rows(CTX, document);

      // sumBigDecimalCosts rounds every term to a whole number before adding, so the ".00" the
      // total row prints can never carry the fractional part the shared volume actually has.
      assertThat(rows.get(0)[5]).isEqualTo("1,234"); // the row's own volume cell
      assertThat(rows.get(1))
          .containsExactly("", "", "", "", "Total:", "1,234.00", "1,000", "0.81");
    }

    @Test
    @DisplayName("the Total row's CPU divides the two-decimal sums, keeping the fraction")
    void totalRow_cpuDividesTheTwoDecimalSums() {
      OtherCostsDocument document = document("1234.40", row("Field crew", 1_000, null));

      List<String[]> rows = section.rows(CTX, document);

      // The CPU cell divides sumBig2DecimalCosts by sumBig2DecimalCosts — the unrounded 1234.40,
      // not the whole 1234 the volume cell shows — so 1000.00 / 1234.40 = 0.81.
      assertThat(rows.get(1)[7]).isEqualTo("0.81");
    }

    @Test
    @DisplayName("an all-null-cost set still emits a Total row, with the null marker in its sums")
    void allNullCosts_stillEmitATotalRow() {
      List<String[]> rows = section.rows(CTX, document(null, row("Unpriced", null, null)));

      // sumBigDecimalCosts returns null when nothing was added, and a null sum formats as the null
      // marker — the row count is still one per item plus the total.
      assertThat(rows).hasSize(2);
      assertThat(rows.get(1)).containsExactly("", "", "", "", "Total:", "-", "-", "-");
    }
  }

  @Nested
  @DisplayName("marker rows")
  class MarkerRows {

    @Test
    @DisplayName("a null document yields the per-record marker")
    void nullDocument_yieldsThePerRecordMarker() {
      // Schedule1OtherExtract.java:55-62.
      assertThat(section.rows(CTX, null))
          .singleElement()
          .satisfies(
              row -> assertThat(row).containsExactly("670", "2021", "Draft", "514", NO_DATA));
    }

    @Test
    @DisplayName("a document with no rows yields the per-record marker")
    void emptyRows_yieldThePerRecordMarker() {
      OtherCostsDocument empty =
          new OtherCostsDocument(new BigDecimal("1500"), 0L, null, 0, List.of(), false, null);

      // A shared volume with no itemized rows is still nothing to itemize.
      assertThat(section.rows(CTX, empty))
          .singleElement()
          .satisfies(
              row -> assertThat(row).containsExactly("670", "2021", "Draft", "514", NO_DATA));
    }

    @Test
    @DisplayName("a document whose row list is null yields the per-record marker")
    void nullRowList_yieldsThePerRecordMarker() {
      OtherCostsDocument noList =
          new OtherCostsDocument(new BigDecimal("1500"), 0L, null, 0, null, false, null);

      assertThat(section.rows(CTX, noList))
          .singleElement()
          .satisfies(
              row -> assertThat(row).containsExactly("670", "2021", "Draft", "514", NO_DATA));
    }

    @Test
    @DisplayName("the whole-schedule marker is the mills, the year range, two dashes, the marker")
    void wholeScheduleMarker_isTheFiveLegacyCells() {
      // The SectionBuilder default; this section does not override it.
      assertThat(section.noDataRow("670, 671", "2020 - 2021"))
          .containsExactly("670, 671", "2020 - 2021", "-", "-", NO_DATA);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  private static OtherCostRow row(String description, Integer cost, String perUnit) {
    return new OtherCostRow(
        8_201, description, cost, perUnit == null ? null : new BigDecimal(perUnit));
  }

  /** Only the shared volume and the rows are read by this section; the rest is filler. */
  private static OtherCostsDocument document(String sharedVolume, OtherCostRow... rows) {
    return new OtherCostsDocument(
        sharedVolume == null ? null : new BigDecimal(sharedVolume),
        null,
        null,
        rows.length,
        List.of(rows),
        false,
        null);
  }
}
