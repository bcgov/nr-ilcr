package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule8RateSection.Kind;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the one builder that stands in for legacy's two rate sections, {@code
 * Schedule8TTTAddExtract} and {@code Schedule8TTTDedExtract}. The two differ only in their title,
 * their one distinct header cell and which of the sample's two rate lists they walk, so each of
 * those three is asserted per kind.
 *
 * <p>The {@code Total:} row is the section's oddity: it is narrower than a data row and puts its
 * label under a description column rather than under a total column. Both are legacy's.
 */
@DisplayName("Schedule8RateSection — legacy Schedule8TTTAddExtract / Schedule8TTTDedExtract")
class Schedule8RateSectionTest {

  private static final RowContext CTX = new RowContext("1234", "2021", "Verified", "567");

  /** The owner's cost item code → catalogue name, as its editor dropdowns resolve it. */
  private static final Map<Integer, String> COST_ITEM_NAMES =
      Map.of(101, "Stump to Dump", 102, "Road Maintenance");

  private final Schedule8RateSection additions = new Schedule8RateSection(Kind.ADDITIONS);

  private final Schedule8RateSection deductions = new Schedule8RateSection(Kind.DEDUCTIONS);

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static RateRow rate(Integer costItemCode, String description, String costingRate) {
    return new RateRow(
        1,
        0,
        costItemCode,
        description,
        costingRate == null ? null : bd(costingRate),
        "F",
        "Fixed");
  }

  /** A sample varying only its two rate lists; the rest are fillers. */
  private static Sample sample(List<RateRow> additions, List<RateRow> deductions) {
    return new Sample(
        10,
        0,
        "C-99",
        "BLK-7",
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
        false,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        additions == null ? 0 : additions.size(),
        deductions == null ? 0 : deductions.size(),
        additions,
        deductions);
  }

  private static Page page(List<Sample> samples) {
    return new Page(
        1,
        0,
        "Interior",
        "A12345",
        "J. Smith",
        "250-555-0100",
        "CP123",
        "SC1",
        null,
        "RDCK",
        null,
        "IDF",
        null,
        "27",
        null,
        null,
        null,
        "27A123",
        null,
        "page level note",
        samples == null ? 0 : samples.size(),
        samples);
  }

  /** The eighteen columns both kinds share before their one distinct cell. */
  private static String[] leadingHeader() {
    return new String[] {
      "MILL_NUMBER",
      "REPORTING_YEAR",
      "STATUS",
      "MILL_ID",
      "PAGE_NO",
      "DIVISION",
      "CONTACT",
      "PHONE",
      "REGION",
      "TSA",
      "TFL",
      "S_BLOCK",
      "LICENSE",
      "CUT_PERMIT",
      "SUPPORT_CTR",
      "BIOGEO_ZONE",
      "CONTRACT_ID",
      "CUT_BLOCK",
    };
  }

  @Nested
  @DisplayName("the Additions kind")
  class AdditionsKind {

    @Test
    @DisplayName("is titled for legacy Schedule8TTTAddExtract")
    void isLegacyBanner() {
      assertThat(additions.title()).isEqualTo("**** Schedule 8 - TTT Additions ****");
    }

    @Test
    @DisplayName("names its nineteenth column ADDITIONS and keeps the shared twenty-two")
    void isLegacyHeader() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "PAGE_NO",
        "DIVISION",
        "CONTACT",
        "PHONE",
        "REGION",
        "TSA",
        "TFL",
        "S_BLOCK",
        "LICENSE",
        "CUT_PERMIT",
        "SUPPORT_CTR",
        "BIOGEO_ZONE",
        "CONTRACT_ID",
        "CUT_BLOCK",
        "ADDITIONS",
        "OTH_DESC",
        "$/M3",
        "COST_TYPE",
        "OTH_TYPE_DESC",
      };

      assertThat(additions.header()).containsExactly(expected);
      assertThat(additions.header()).hasSize(23);
      assertThat(additions.header()).startsWith(leadingHeader());
      assertThat(additions.header()[18]).isEqualTo("ADDITIONS");
    }

    @Test
    @DisplayName("walks the sample's additions list, not its deductions list")
    void walksTheAdditionsList() {
      Page page =
          page(
              List.of(
                  sample(
                      List.of(rate(101, "Extra haul", "100")),
                      List.of(rate(102, "Short haul", "200")))));

      List<String[]> rows = additions.rows(CTX, page, 1, COST_ITEM_NAMES);

      // Two rows: the one addition, then its total. The deduction is this section's sibling's.
      assertThat(rows).hasSize(2);
      assertThat(rows.get(0)[19]).isEqualTo("Extra haul");
      assertThat(rows.get(0)[18]).isEqualTo("Stump to Dump");
    }
  }

  @Nested
  @DisplayName("the Deductions kind")
  class DeductionsKind {

    @Test
    @DisplayName("is titled for legacy Schedule8TTTDedExtract")
    void isLegacyBanner() {
      assertThat(deductions.title()).isEqualTo("**** Schedule 8 - TTT Deductions ****");
    }

    @Test
    @DisplayName("names its nineteenth column DEDUCTIONS and keeps the shared twenty-two")
    void isLegacyHeader() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "PAGE_NO",
        "DIVISION",
        "CONTACT",
        "PHONE",
        "REGION",
        "TSA",
        "TFL",
        "S_BLOCK",
        "LICENSE",
        "CUT_PERMIT",
        "SUPPORT_CTR",
        "BIOGEO_ZONE",
        "CONTRACT_ID",
        "CUT_BLOCK",
        "DEDUCTIONS",
        "OTH_DESC",
        "$/M3",
        "COST_TYPE",
        "OTH_TYPE_DESC",
      };

      assertThat(deductions.header()).containsExactly(expected);
      assertThat(deductions.header()).hasSize(23);
      assertThat(deductions.header()).startsWith(leadingHeader());
      assertThat(deductions.header()[18]).isEqualTo("DEDUCTIONS");
    }

    @Test
    @DisplayName("walks the sample's deductions list, not its additions list")
    void walksTheDeductionsList() {
      Page page =
          page(
              List.of(
                  sample(
                      List.of(rate(101, "Extra haul", "100")),
                      List.of(rate(102, "Short haul", "200")))));

      List<String[]> rows = deductions.rows(CTX, page, 1, COST_ITEM_NAMES);

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0)[19]).isEqualTo("Short haul");
      assertThat(rows.get(0)[18]).isEqualTo("Road Maintenance");
    }

    @Test
    @DisplayName("emits the marker when the sample has additions but no deductions")
    void markerWhenOnlyTheOtherListIsPopulated() {
      Page page = page(List.of(sample(List.of(rate(101, "Extra haul", "100")), List.of())));

      assertThat(deductions.rows(CTX, page, 1, COST_ITEM_NAMES))
          .singleElement()
          .satisfies(row -> assertThat(row[7]).isEqualTo("*** NO DATA FOUND ***"));
    }
  }

  @Nested
  @DisplayName("data row")
  class DataRow {

    @Test
    @DisplayName("builds every cell of a populated rate row")
    void buildsPopulatedRow() {
      Page page = page(List.of(sample(List.of(rate(101, "Extra haul", "1250.5")), List.of())));

      String[] expected = {
        "1234",
        "2021",
        "Verified",
        "567",
        "Sample # 1 - C-99",
        "Interior",
        "J. Smith",
        "250-555-0100",
        "RDCK",
        "27",
        "-",
        // Untruncated, as in every sample section (Schedule8TTTExtract.java:86).
        "27A123",
        "A12345",
        "CP123",
        "SC1",
        "IDF",
        "C-99",
        "BLK-7",
        // The cost ITEM's catalogue name, which the rate row does not carry; it is resolved
        // through the owner's option lists.
        "Stump to Dump",
        "Extra haul",
        // 1250.5 to a whole number is 1,250 under DecimalFormat's HALF_EVEN default.
        "1,250",
        "F",
        "Fixed",
      };

      assertThat(additions.rows(CTX, page, 1, COST_ITEM_NAMES).get(0)).containsExactly(expected);
    }

    @Test
    @DisplayName("an unresolvable cost item code is the null marker")
    void unresolvableCostItemIsNullMarker() {
      // Two ways the name goes missing: the row carries no code at all, or it carries one the
      // owner's option list does not hold. Both land on the same cell value.
      Page noCode = page(List.of(sample(List.of(rate(null, "Extra haul", "100")), List.of())));
      Page unknownCode = page(List.of(sample(List.of(rate(999, "Extra haul", "100")), List.of())));

      assertThat(additions.rows(CTX, noCode, 1, COST_ITEM_NAMES).get(0)[18]).isEqualTo("-");
      assertThat(additions.rows(CTX, unknownCode, 1, COST_ITEM_NAMES).get(0)[18]).isEqualTo("-");
    }

    @Test
    @DisplayName("an empty cost type description is the null marker")
    void emptyCostTypeDescriptionIsNullMarker() {
      RateRow blankType = new RateRow(1, 0, 101, "Extra haul", bd("100"), "F", "");
      Page page = page(List.of(sample(List.of(blankType), List.of())));

      assertThat(additions.rows(CTX, page, 1, COST_ITEM_NAMES).get(0)[22]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the Total: row sits under OTH_DESC, which is legacy's misalignment")
  class TotalRowMisalignment {

    /** Where the label lands: the twentieth cell, which the header calls {@code OTH_DESC}. */
    private static final int LABEL = 19;

    /** Where the sum lands: the twenty-first cell, which the header calls {@code $/M3}. */
    private static final int SUM = 20;

    @Test
    @DisplayName("puts Total: under the description column and the sum under the rate column")
    void labelSitsUnderTheDescriptionColumn() {
      Page page =
          page(
              List.of(
                  sample(
                      List.of(rate(101, "Extra haul", "1250.5"), rate(102, "Grade", "748.5")),
                      List.of())));

      List<String[]> rows = additions.rows(CTX, page, 1, COST_ITEM_NAMES);
      String[] total = rows.get(rows.size() - 1);

      // Legacy wrote nineteen empty cells, then the label, then the sum — so the label appears
      // beneath OTH_DESC and the sum beneath $/M3, rather than the label sitting left of a
      // dedicated total column. Nothing downstream reads these positions; the file has to match.
      assertThat(additions.header()[LABEL]).isEqualTo("OTH_DESC");
      assertThat(additions.header()[SUM]).isEqualTo("$/M3");
      assertThat(total[LABEL]).isEqualTo("Total:");
      assertThat(total[SUM]).isEqualTo("2,000");
    }

    @Test
    @DisplayName("is twenty-one cells, two short of the header, which is also legacy's")
    void isTwoCellsShortOfTheHeader() {
      Page page = page(List.of(sample(List.of(rate(101, "Extra haul", "100")), List.of())));

      List<String[]> rows = additions.rows(CTX, page, 1, COST_ITEM_NAMES);
      String[] total = rows.get(rows.size() - 1);

      assertThat(total).hasSize(21);
      assertThat(additions.header()).hasSize(23);
      // Every cell before the label is an empty string, not a null and not the null marker: the
      // writer renders a null cell as nothing at all, and legacy's was a quoted empty cell.
      assertThat(total).startsWith(new String[] {"", "", "", "", ""});
      assertThat(Arrays.copyOfRange(total, 0, LABEL)).containsOnly("");
    }

    @Test
    @DisplayName("sums at HALF_UP even though each row cell rounds HALF_EVEN")
    void sumsAtHalfUpWhileRowsRoundHalfEven() {
      // The rate cells go through DecimalFormat (HALF_EVEN) and the total through legacy's
      // sumBigDecimalCosts, which rounds each term HALF_UP before adding. 1250.5 and 748.5
      // therefore display as 1,250 and 748 but total 2,000, not 1,998. Legacy's arithmetic.
      Page page =
          page(
              List.of(
                  sample(
                      List.of(rate(101, "Extra haul", "1250.5"), rate(102, "Grade", "748.5")),
                      List.of())));

      List<String[]> rows = additions.rows(CTX, page, 1, COST_ITEM_NAMES);

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0)[SUM]).isEqualTo("1,250");
      assertThat(rows.get(1)[SUM]).isEqualTo("748");
      assertThat(rows.get(2)[SUM]).isEqualTo("2,000");
    }

    @Test
    @DisplayName("a rate list of nothing but nulls totals to the null marker")
    void allNullRatesTotalToTheNullMarker() {
      Page page = page(List.of(sample(List.of(rate(101, "Extra haul", null)), List.of())));

      List<String[]> rows = additions.rows(CTX, page, 1, COST_ITEM_NAMES);

      assertThat(rows.get(1)[SUM]).isEqualTo("-");
    }

    @Test
    @DisplayName("each sample gets its own total, accumulated from that sample alone")
    void eachSampleGetsItsOwnTotal() {
      Page page =
          page(
              List.of(
                  sample(List.of(rate(101, "Extra haul", "100")), List.of()),
                  sample(List.of(rate(102, "Grade", "300")), List.of())));

      List<String[]> rows = additions.rows(CTX, page, 1, COST_ITEM_NAMES);

      assertThat(rows).hasSize(4);
      assertThat(rows.get(1)[SUM]).isEqualTo("100");
      assertThat(rows.get(3)[SUM]).isEqualTo("300");
    }
  }

  @Nested
  @DisplayName("markers")
  class Markers {

    @Test
    @DisplayName("a page with no samples gets the eight-cell per-page marker and no total")
    void pageWithNoSamplesGetsTheEightCellMarker() {
      String[] expected = {
        "1234", "2021", "Verified", "567", "-", "Interior", "J. Smith", "*** NO DATA FOUND ***",
      };

      assertThat(additions.rows(CTX, page(List.of()), 1, COST_ITEM_NAMES))
          .singleElement()
          .satisfies(row -> assertThat(row).containsExactly(expected).hasSize(8));
    }

    @Test
    @DisplayName("a sample with no rate rows gets the marker and contributes no total")
    void sampleWithNoRatesGetsTheMarker() {
      // Both an empty list and a null one; a total row for a sample with nothing in it would put
      // a stray "Total: -" in the file, which legacy did not emit.
      Page empty = page(List.of(sample(List.of(), List.of())));
      Page absent = page(List.of(sample(null, null)));

      assertThat(additions.rows(CTX, empty, 1, COST_ITEM_NAMES))
          .singleElement()
          .satisfies(row -> assertThat(row).hasSize(8));
      assertThat(additions.rows(CTX, absent, 1, COST_ITEM_NAMES))
          .singleElement()
          .satisfies(row -> assertThat(row).hasSize(8));
    }

    @Test
    @DisplayName("the whole-schedule marker is the shared five-cell shape for both kinds")
    void wholeScheduleMarkerIsFiveCells() {
      String[] expected = {
        "Included Mills: 1234", "2019 - 2021", "-", "-", "*** NO DATA FOUND ***",
      };

      assertThat(additions.noDataRow("Included Mills: 1234", "2019 - 2021"))
          .containsExactly(expected);
      assertThat(deductions.noDataRow("Included Mills: 1234", "2019 - 2021"))
          .containsExactly(expected);
    }
  }
}
