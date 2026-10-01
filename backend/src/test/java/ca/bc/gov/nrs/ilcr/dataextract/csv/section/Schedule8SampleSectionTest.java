package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Tree-to-Truck sample section, transcribed from legacy {@code Schedule8TTTExtract}.
 *
 * <p>Three of the assertions here pin behaviour that reads as a defect and is not: the deductions
 * cell gated on the additions figure, the two indicator cells that carry enum labels rather than
 * the stored Y/N, and the comments column that is a known gap in the owning read. Each has its own
 * test so a future change to any of them fails loudly rather than looking like a fix.
 */
@DisplayName("Schedule8SampleSection — legacy Schedule8TTTExtract")
class Schedule8SampleSectionTest {

  private static final RowContext CTX = new RowContext("1234", "2021", "Verified", "567");

  private final Schedule8SampleSection section = new Schedule8SampleSection();

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  /** A rate row carrying only a costing rate — all the totals' presence rule reads. */
  private static RateRow rate(BigDecimal costingRate) {
    return new RateRow(null, 0, 1, null, costingRate, null, null);
  }

  /** The rate rows behind a served total: none when the total is null, else one carrying it. */
  private static List<RateRow> ratesFor(BigDecimal total) {
    return total == null ? List.of() : List.of(rate(total));
  }

  /** The one page every test here hangs its samples from; only the sample list varies. */
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

  /** A fully populated sample; the values are chosen so each formatter shows a distinct result. */
  private static Sample populatedSample() {
    return new Sample(
        10,
        0,
        "C-99",
        "BLK-7",
        40,
        10,
        25,
        15,
        10,
        0,
        100,
        1250,
        2,
        bd("1234.5"),
        bd("2500"),
        bd("12.4"),
        true,
        false,
        "HO",
        "Horse",
        12345,
        678,
        13023,
        bd("28.75"),
        bd("3.5"),
        bd("1.25"),
        bd("31"),
        1,
        1,
        List.of(rate(bd("3.5"))),
        List.of(rate(bd("1.25"))));
  }

  /**
   * A sample varying only the two rate totals, each backed by one rate row carrying it (none for a
   * null total); every other component is a filler.
   */
  private static Sample totalsSample(BigDecimal additionsTotal, BigDecimal deductionsTotal) {
    return totalsSample(
        additionsTotal, deductionsTotal, ratesFor(additionsTotal), ratesFor(deductionsTotal));
  }

  /** As the owner serves it: totals and the rate rows behind them, set independently. */
  private static Sample totalsSample(
      BigDecimal additionsTotal,
      BigDecimal deductionsTotal,
      List<RateRow> additions,
      List<RateRow> deductions) {
    return new Sample(
        10,
        0,
        "C-99",
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
        null,
        false,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        additionsTotal,
        deductionsTotal,
        null,
        additions.size(),
        deductions.size(),
        additions,
        deductions);
  }

  /** A sample varying only its contract id, which is the name legacy put in the sample title. */
  private static Sample contractIdSample(String contractId) {
    return new Sample(
        10,
        0,
        contractId,
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
        0,
        0,
        List.of(),
        List.of());
  }

  /** A sample varying only the two legacy Y/N indicators. */
  private static Sample indicatorSample(boolean uphillDirection, boolean waterDumpDestination) {
    return new Sample(
        10,
        0,
        "C-99",
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
        null,
        uphillDirection,
        waterDumpDestination,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        0,
        List.of(),
        List.of());
  }

  @Nested
  @DisplayName("header")
  class Header {

    @Test
    @DisplayName("is legacy Schedule8TTTExtract's forty-one columns, in order")
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
        "SY_GROUNDBASE_%",
        "SY_GRAPPLE_%",
        "SY_HIGHLEAD_%",
        "SKY_%",
        "SKY_SLOPE_M",
        "SKY_SUPP_NUM",
        "SKY_AVG_DIST",
        "HELI_%",
        "DISTANCE",
        "HELI_CYCLE",
        "HELI_DIR",
        "HELI_DUMP",
        "SY_OTHER",
        "SY_OTHER_%",
        "TOTAL_%",
        "CONIF_M3",
        "DECID_M3",
        "ACTUAL_M3",
        "ORIG_TTT_RATE",
        "ADDITIONS",
        "DEDUCTIONS",
        "FINAL_TTT_RATE",
        "COMMENTS",
      };

      assertThat(section.header()).containsExactly(expected);
      assertThat(section.header()).hasSize(41);
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
      assertThat(section.title()).isEqualTo("**** Schedule 8 - TTT ****");
    }
  }

  @Nested
  @DisplayName("data row")
  class DataRow {

    @Test
    @DisplayName("builds every cell of a populated sample")
    void buildsPopulatedRow() {
      String[] expected = {
        "1234",
        "2021",
        "Verified",
        "567",
        // Legacy's sample title stands in for the page title in this section.
        "Sample # 1 - C-99",
        "Interior",
        "J. Smith",
        "250-555-0100",
        "RDCK",
        "27",
        "-",
        // Untruncated, unlike the main section's S_BLOCK (Schedule8TTTExtract.java:86).
        "27A123",
        "A12345",
        "CP123",
        "SC1",
        "IDF",
        "C-99",
        "BLK-7",
        "40",
        "10",
        // Highlead precedes skyline in the column order even though the record declares skyline
        // first, so a transposition here would compile and be wrong.
        "15",
        "25",
        "1,250",
        "2",
        // 1234.5 to a whole number is 1,234 under DecimalFormat's HALF_EVEN default.
        "1,234",
        "10",
        "2,500",
        "12",
        "Uphill",
        "Land Dump",
        "HO",
        "0",
        "100",
        "12,345",
        "678",
        "13,023",
        "29",
        "4",
        "1",
        "31",
        // The comments column is a hard-coded null marker; see the Comments tests below.
        "-",
      };

      assertThat(section.rows(CTX, page(List.of(populatedSample())), 3))
          .singleElement()
          .satisfies(row -> assertThat(row).containsExactly(expected));
    }

    @Test
    @DisplayName("is as wide as the header")
    void isHeaderWidth() {
      // isNotEmpty() first: allSatisfy passes vacuously on an empty list.
      assertThat(section.rows(CTX, page(List.of(populatedSample())), 1))
          .isNotEmpty()
          .allSatisfy(row -> assertThat(row).hasSize(41));
    }

    @Test
    @DisplayName("numbers the samples from one, in list order")
    void numbersSamplesFromOne() {
      List<String[]> rows =
          section.rows(CTX, page(List.of(populatedSample(), populatedSample())), 1);

      assertThat(rows).hasSize(2);
      assertThat(rows.get(0)[4]).isEqualTo("Sample # 1 - C-99");
      assertThat(rows.get(1)[4]).isEqualTo("Sample # 2 - C-99");
    }

    @Test
    @DisplayName("a sample with no contract id leaves the title's trailing name blank")
    void missingContractIdLeavesTitleTrailingBlank() {
      // Legacy concatenated the contractor straight onto " - " with an empty-string fallback, so
      // the title keeps its separator and ends in a space.
      assertThat(Schedule8SampleSection.sampleTitle(contractIdSample(null), 4))
          .isEqualTo("Sample # 4 - ");
      assertThat(Schedule8SampleSection.sampleTitle(contractIdSample("C-99"), 4))
          .isEqualTo("Sample # 4 - C-99");
    }
  }

  @Nested
  @DisplayName("the DEDUCTIONS cell is gated on the ADDITIONS figure, which is legacy's")
  class DeductionsGatedOnAdditions {

    /** The two rate-total columns' indices in a data row. */
    private static final int ADDITIONS = 37;

    private static final int DEDUCTIONS = 38;

    @Test
    @DisplayName("a deductions figure with no additions figure renders the null marker")
    void deductionsWithoutAdditionsIsNullMarker() {
      // The deductions cell reads the DEDUCTIONS figure but tests the ADDITIONS one for presence,
      // a legacy copy-paste. A sample that was only ever deducted therefore shows no deduction at
      // all in the extract. Kept verbatim: the file has to match legacy's, defect included.
      String[] row = section.rows(CTX, page(List.of(totalsSample(null, bd("500")))), 1).get(0);

      assertThat(row[ADDITIONS]).isEqualTo("-");
      assertThat(row[DEDUCTIONS]).isEqualTo("-");
    }

    @Test
    @DisplayName("a deductions figure with an additions figure present renders normally")
    void deductionsWithAdditionsRendersNormally() {
      String[] row = section.rows(CTX, page(List.of(totalsSample(bd("0"), bd("500")))), 1).get(0);

      // An additions total of zero is still "present", so the gate opens on it.
      assertThat(row[ADDITIONS]).isEqualTo("0");
      assertThat(row[DEDUCTIONS]).isEqualTo("500");
    }

    @Test
    @DisplayName("#474: a sample with no rate rows prints the null marker, not the owner's zero")
    void noRateRowsIsNullMarkerNotZero() {
      // The owner seeds both totals at ZERO for the screen, so "nothing recorded" arrives as 0.
      // Legacy's sum returned null with no rates to add, and printed "-" in both cells.
      String[] row =
          section
              .rows(CTX, page(List.of(totalsSample(bd("0"), bd("0"), List.of(), List.of()))), 1)
              .get(0);

      assertThat(row[ADDITIONS]).isEqualTo("-");
      assertThat(row[DEDUCTIONS]).isEqualTo("-");
    }

    @Test
    @DisplayName("#474: additions rows but no deduction rows prints the deductions null marker")
    void additionsRowsWithoutDeductionRowsIsDeductionsNullMarker() {
      // Mill 727's 24 / 666,444 / - / 666,468: the additions sum prints, the absent deductions do
      // not.
      String[] row =
          section
              .rows(
                  CTX,
                  page(
                      List.of(
                          totalsSample(
                              bd("666444"), bd("0"), List.of(rate(bd("666444"))), List.of()))),
                  1)
              .get(0);

      assertThat(row[ADDITIONS]).isEqualTo("666,444");
      assertThat(row[DEDUCTIONS]).isEqualTo("-");
    }

    @Test
    @DisplayName("#474: rate rows that genuinely sum to zero print 0, not the null marker")
    void realZeroSumPrintsZero() {
      // The positive control, mill 727's 25 / 2 / 0 / 26: a real zero stays a zero.
      String[] row =
          section
              .rows(
                  CTX,
                  page(
                      List.of(
                          totalsSample(
                              bd("2"), bd("0"), List.of(rate(bd("2"))), List.of(rate(bd("0")))))),
                  1)
              .get(0);

      assertThat(row[ADDITIONS]).isEqualTo("2");
      assertThat(row[DEDUCTIONS]).isEqualTo("0");
    }

    @Test
    @DisplayName("#474: rate rows whose costing rates are all blank count as no rates")
    void blankRatesOnlyIsNullMarker() {
      // sumBigDecimalValues skipped null items and returned null if nothing was added.
      String[] row =
          section
              .rows(
                  CTX,
                  page(List.of(totalsSample(bd("0"), bd("0"), List.of(rate(null)), List.of()))),
                  1)
              .get(0);

      assertThat(row[ADDITIONS]).isEqualTo("-");
      assertThat(row[DEDUCTIONS]).isEqualTo("-");
    }

    @Test
    @DisplayName("an additions figure with no deductions figure renders the null marker")
    void additionsWithoutDeductionsIsNullMarker() {
      String[] row = section.rows(CTX, page(List.of(totalsSample(bd("500"), null))), 1).get(0);

      assertThat(row[ADDITIONS]).isEqualTo("500");
      assertThat(row[DEDUCTIONS]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the two helicopter indicators carry enum labels, not the stored Y/N codes")
  class IndicatorLabels {

    private static final int HELI_DIR = 28;

    private static final int HELI_DUMP = 29;

    @Test
    @DisplayName("renders Uphill and Water Dump, not Y")
    void rendersPositiveLabels() {
      // Legacy held these as enums that overrode toString(), so the file carried the reader-facing
      // label. A bare "Y" here would be the regression this pins against.
      String[] row = section.rows(CTX, page(List.of(indicatorSample(true, true))), 1).get(0);

      assertThat(row[HELI_DIR]).isEqualTo("Uphill");
      assertThat(row[HELI_DUMP]).isEqualTo("Water Dump");
    }

    @Test
    @DisplayName("renders Downhill and Land Dump, not N")
    void rendersNegativeLabels() {
      String[] row = section.rows(CTX, page(List.of(indicatorSample(false, false))), 1).get(0);

      assertThat(row[HELI_DIR]).isEqualTo("Downhill");
      assertThat(row[HELI_DUMP]).isEqualTo("Land Dump");
    }

    @Test
    @DisplayName("the two indicators are independent of each other")
    void indicatorsAreIndependent() {
      String[] row = section.rows(CTX, page(List.of(indicatorSample(false, true))), 1).get(0);

      assertThat(row[HELI_DIR]).isEqualTo("Downhill");
      assertThat(row[HELI_DUMP]).isEqualTo("Water Dump");
    }
  }

  @Nested
  @DisplayName("the COMMENTS column is a known gap in the owning read, not an oversight")
  class CommentsColumnIsAKnownGap {

    private static final int COMMENTS = 40;

    @Test
    @DisplayName("is always the null marker, because no sample-level comment is served")
    void isAlwaysTheNullMarker() {
      // Legacy wrote the sample's own COMMENTS column here. The Schedule 8 read has never served
      // a sample-level comments field, and the extract takes its figures only from the owning
      // read, so the column keeps its header and its position and shows the null marker until the
      // owner serves the field. Anyone restoring a value here needs a new owner field first — the
      // page-level comment below is NOT it.
      String[] row = section.rows(CTX, page(List.of(populatedSample())), 1).get(0);

      assertThat(row[COMMENTS]).isEqualTo("-");
    }

    @Test
    @DisplayName("does not fall back to the page's comment")
    void doesNotFallBackToThePageComment() {
      // The page this sample hangs from carries "page level note"; the sample row must not borrow
      // it, because legacy's sample row never showed the page's comment.
      Page page = page(List.of(populatedSample()));
      String[] row = section.rows(CTX, page, 1).get(0);

      assertThat(page.comments()).isEqualTo("page level note");
      assertThat(row[COMMENTS]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("markers")
  class Markers {

    @Test
    @DisplayName("the per-page marker is eight cells, not the shared five")
    void perPageMarkerIsEightCells() {
      // Legacy's per-page marker repeated the division and contact after a dashed page cell, so
      // this row is wider than the five-cell whole-schedule marker and narrower than a data row.
      String[] expected = {
        "1234", "2021", "Verified", "567", "-", "Interior", "J. Smith", "*** NO DATA FOUND ***",
      };

      assertThat(section.rows(CTX, page(List.of()), 1))
          .singleElement()
          .satisfies(row -> assertThat(row).containsExactly(expected).hasSize(8));
    }

    @Test
    @DisplayName("a null sample list is treated as an empty one")
    void nullSampleListIsEmpty() {
      assertThat(section.rows(CTX, page(null), 1))
          .singleElement()
          .satisfies(row -> assertThat(row[7]).isEqualTo("*** NO DATA FOUND ***"));
    }

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
