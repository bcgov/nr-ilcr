package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the main Schedule 8 section, transcribed from legacy {@code Schedule8Extract}.
 *
 * <p>The header is written out as literal strings rather than compared against the production
 * constant: a column list is only worth a test if the test fails when the list changes, and
 * comparing the constant to itself cannot do that. The same goes for the section title.
 */
@DisplayName("Schedule8Section — legacy Schedule8Extract")
class Schedule8SectionTest {

  private static final RowContext CTX = new RowContext("1234", "2021", "Verified", "567");

  private final Schedule8Section section = new Schedule8Section();

  /**
   * A page carrying only the components the Schedule 8 cells read. {@link Page} has twenty-two, and
   * the fifteen this section never touches are fillers.
   */
  private static Page page(
      String division,
      String license,
      String contact,
      String phone,
      String cuttingPermit,
      String supportCentre,
      String region,
      String becZone,
      String tsaNumber,
      String tflNumber,
      String supplyBlock,
      String comments) {
    return new Page(
        1,
        0,
        division,
        license,
        contact,
        phone,
        cuttingPermit,
        supportCentre,
        null,
        region,
        null,
        becZone,
        null,
        tsaNumber,
        null,
        tflNumber,
        null,
        supplyBlock,
        null,
        comments,
        0,
        List.of());
  }

  /** A page that varies only the two components the {@code S_BLOCK} cell consults. */
  private static Page supplyBlockPage(String tsaNumber, String supplyBlock) {
    return page(null, null, null, null, null, null, null, null, tsaNumber, null, supplyBlock, null);
  }

  @Nested
  @DisplayName("header")
  class Header {

    @Test
    @DisplayName("is legacy Schedule8Extract's seventeen columns, in order")
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
        "COMMENTS",
      };

      assertThat(section.header()).containsExactly(expected);
      // The width is asserted separately: a column appended past COMMENTS would still leave every
      // assertion above satisfied if only the prefix were compared.
      assertThat(section.header()).hasSize(17);
    }

    @Test
    @DisplayName("is a fresh array each call, so a caller cannot corrupt the next section")
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
      assertThat(section.title()).isEqualTo("**** Schedule 8 ****");
    }
  }

  @Nested
  @DisplayName("data row")
  class DataRow {

    @Test
    @DisplayName("builds every cell of a populated page")
    void buildsPopulatedRow() {
      Page page =
          page(
              "Interior",
              "A12345",
              "J. Smith",
              "250-555-0100",
              "CP123",
              "SC1",
              "RDCK",
              "IDF",
              "27",
              null,
              "27A123",
              "Wet\tspring\nlate start");

      String[] expected = {
        "1234",
        "2021",
        "Verified",
        "567",
        // Legacy's page title, built from the 1-based position within the mill/year; the two
        // spaces after the number and the single space before "-CP:" are legacy's spacing.
        "Page # 3  -TSA: 27 -CP: CP123",
        "Interior",
        "J. Smith",
        "250-555-0100",
        "RDCK",
        "27",
        // A null TFL number goes through the plain null guard, not a blank cell.
        "-",
        // Truncated: the stored code is 27A123 and the leading "27" repeats the TSA beside it.
        "A123",
        "A12345",
        "CP123",
        "SC1",
        "IDF",
        // text() replaces a tab with two spaces and a newline with one space, and never strips
        // a carriage return.
        "Wet  spring late start",
      };

      assertThat(section.row(CTX, page, 3)).containsExactly(expected);
    }

    @Test
    @DisplayName("is as wide as the header")
    void isHeaderWidth() {
      Page page = supplyBlockPage("27", "27A123");

      assertThat(section.row(CTX, page, 1)).hasSameSizeAs(section.header());
    }

    @Test
    @DisplayName("an absent comment is the null marker, not an empty cell")
    void absentCommentIsNullMarker() {
      Page page = supplyBlockPage("27", "27A123");

      assertThat(section.row(CTX, page, 1)[16]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("page title")
  class PageTitle {

    @Test
    @DisplayName("substitutes a spaced dash for a missing cutting permit")
    void missingCuttingPermitIsSpacedDash() {
      // Legacy substituted " - " — with its surrounding spaces — rather than the bare null
      // marker, so the cell reads "-CP:  - " with two spaces before the dash.
      Page noPermit = supplyBlockPage("27", null);
      Page emptyPermit = page(null, null, null, null, "", null, null, null, "27", null, null, null);

      assertThat(Schedule8Section.pageTitle(noPermit, 2)).isEqualTo("Page # 2  -TSA: 27 -CP:  - ");
      assertThat(Schedule8Section.pageTitle(emptyPermit, 2))
          .isEqualTo("Page # 2  -TSA: 27 -CP:  - ");
    }

    @Test
    @DisplayName("concatenates a null TSA as the literal text null, as Java did for legacy")
    void nullTsaConcatenatesAsLiteralNull() {
      // Legacy built this title by string concatenation with no null guard on the TSA, so the
      // word "null" reached the file. Preserved deliberately.
      assertThat(Schedule8Section.pageTitle(supplyBlockPage(null, "27A123"), 1))
          .isEqualTo("Page # 1  -TSA: null -CP:  - ");
    }
  }

  @Nested
  @DisplayName("S_BLOCK supply-block cell")
  class SupplyBlock {

    /** The {@code S_BLOCK} cell's index in both the header and a data row. */
    private static final int S_BLOCK = 11;

    @Test
    @DisplayName("strips the stored code's first two characters, as legacy's DAO did")
    void stripsFirstTwoCharacters() {
      // Legacy's extract wrote getSupplyBlock() (Schedule8Extract.java:67) over a value its DAO
      // had already set to tsb_number_code.substring(2) (Schedule8DAO.java:283) — the leading two
      // characters repeat the TSA in the neighbouring column.
      assertThat(section.row(CTX, supplyBlockPage("27", "27A123"), 1)[S_BLOCK]).isEqualTo("A123");
      assertThat(section.row(CTX, supplyBlockPage("27", "27ABC"), 1)[S_BLOCK]).isEqualTo("ABC");
    }

    @Test
    @DisplayName("is the null marker when the TSA holds the TFL sentinel")
    void tflSentinelIsNullMarker() {
      // The TSA-or-TFL selector stores legacy's Constant.TFL marker in the TSA column when the
      // page is a TFL page; legacy dashed the supply block outright in that case.
      assertThat(Schedule8Section.TFL).isEqualTo("TFL");
      assertThat(section.row(CTX, supplyBlockPage("TFL", "27A123"), 1)[S_BLOCK]).isEqualTo("-");
    }

    @Test
    @DisplayName("emits a code too short to truncate whole rather than losing the file")
    void tooShortToTruncateIsEmittedWhole() {
      // A stored code of two characters or fewer has nothing left after shedding two, and a bare
      // substring(2) on one would throw part-way through writing the extract.
      assertThat(section.row(CTX, supplyBlockPage("27", "27"), 1)[S_BLOCK]).isEqualTo("27");
      assertThat(section.row(CTX, supplyBlockPage("27", "7"), 1)[S_BLOCK]).isEqualTo("7");
    }

    @Test
    @DisplayName("an absent or empty code is the null marker")
    void absentCodeIsNullMarker() {
      assertThat(section.row(CTX, supplyBlockPage("27", null), 1)[S_BLOCK]).isEqualTo("-");
      assertThat(section.row(CTX, supplyBlockPage("27", ""), 1)[S_BLOCK]).isEqualTo("-");
    }

    @Test
    @DisplayName("the three sample sections deliberately do NOT truncate or dash the same field")
    void sampleSectionsDoNotTruncate() {
      // Legacy's TTT builders wrote the same field untruncated and undashed
      // (Schedule8TTTExtract.java:86). The divergence between the two column sets looks like an
      // inconsistency and is one, but it is legacy's, so both halves are pinned.
      Page page = supplyBlockPage("27", "27A123");
      Page tflPage = supplyBlockPage("TFL", "27A123");

      assertThat(Schedule8Section.samplePageCells(page, 1, "Sample # 1 - ")[7]).isEqualTo("27A123");
      assertThat(Schedule8Section.samplePageCells(tflPage, 1, "Sample # 1 - ")[7])
          .isEqualTo("27A123");
      assertThat(Schedule8Section.pageCells(page, 1)[7]).isEqualTo("A123");
    }
  }

  @Nested
  @DisplayName("markers")
  class Markers {

    @Test
    @DisplayName("the whole-schedule marker is the shared five-cell shape")
    void wholeScheduleMarkerIsFiveCells() {
      String[] expected = {
        "Included Mills: 1234, 5678", "2019 - 2021", "-", "-", "*** NO DATA FOUND ***",
      };

      assertThat(section.noDataRow("Included Mills: 1234, 5678", "2019 - 2021"))
          .containsExactly(expected);
    }
  }
}
