package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CodeLists;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Schedule 6 section's shape, pinned cell by cell.
 *
 * <p>The title and header are asserted as literal strings rather than against the production
 * constant: what matters is that the rebuilt column set still matches legacy's ({@code
 * Schedule6Extract.java:34, :94-107}), and an assertion that read the constant back would follow an
 * accidental edit instead of catching it.
 */
@DisplayName("Schedule6Section — legacy column fidelity")
class Schedule6SectionTest {

  private final Schedule6Section section = new Schedule6Section();

  /** The four leading cells are resolved once per (mill, year) and are not this class's concern. */
  private final RowContext ctx = new RowContext("1234", "2021", "Verified", "5678");

  private static final List<CodeDescriptionDto> TSA_NUMBERS =
      List.of(
          new CodeDescriptionDto("29", "Fort St. John TSA"),
          new CodeDescriptionDto("07", "Cranbrook TSA"));

  private static final List<CodeDescriptionDto> SUPPLY_BLOCKS =
      List.of(new CodeDescriptionDto("A", "Block A"), new CodeDescriptionDto("B", "Block B"));

  private static final Schedule6CodeLists CODE_LISTS =
      new Schedule6CodeLists(TSA_NUMBERS, SUPPLY_BLOCKS);

  /**
   * A road record through the no-original-values constructor, which is the shape every caller that
   * is not serving a stored document uses.
   */
  private static RoadRecord road(
      String areaType,
      String tflNumber,
      String supplyBlock,
      String rmg,
      BigDecimal volume,
      Integer cost,
      BigDecimal costPerVolume,
      String comments) {
    return new RoadRecord(
        1, 1, areaType, tflNumber, supplyBlock, rmg, volume, cost, costPerVolume, comments);
  }

  @Nested
  @DisplayName("fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the title is the bare **** Schedule 6 **** form")
    void pinsTitle() {
      // Legacy Schedule6Extract.java:34. This title's spacing is symmetrical; the asymmetry is
      // Schedule 7A's alone, so the two must not be "aligned" with each other.
      assertThat(section.title()).isEqualTo("**** Schedule 6 ****");
    }

    @Test
    @DisplayName("the header is legacy's twelve columns, in order")
    void pinsHeader() {
      // Schedule6Extract.java:94-107. TFL_# keeps its hash and CPU_$/M3 its slash: these are the
      // literal column labels a legacy sample file carries.
      assertThat(section.header())
          .containsExactly(
              "MILL_NUMBER",
              "REPORTING_YEAR",
              "STATUS",
              "MILL_ID",
              "TSA_TFL",
              "TFL_#",
              "S_BLOCK",
              "RMG",
              "VOL_M3",
              "COST_$",
              "CPU_$/M3",
              "COMMENTS")
          .hasSize(12);
    }

    @Test
    @DisplayName("header() hands back a copy, so a caller cannot edit the shared array")
    void headerIsDefensivelyCopied() {
      String[] first = section.header();
      first[0] = "MUTATED";

      assertThat(section.header()[0]).isEqualTo("MILL_NUMBER");
    }
  }

  @Nested
  @DisplayName("data rows")
  class DataRows {

    @Test
    @DisplayName("a TSA record renders all twelve cells")
    void rendersTsaRow() {
      RoadRecord record =
          road(
              "29",
              null,
              "A",
              "5",
              new BigDecimal("1234567"),
              98765,
              new BigDecimal("1234.56"),
              "Spur\trebuild\nsecond season");

      String[] row = section.row(ctx, record, CODE_LISTS);

      assertThat(row)
          .containsExactly(
              "1234",
              "2021",
              "Verified",
              "5678",
              // Resolved from the document's own TSA list, not from a code service.
              "29 - Fort St. John TSA",
              // A TSA-located record carries no TFL number at all.
              "-",
              "A - Block A",
              "5",
              // ###,###,##0 — grouped, no decimals.
              "1,234,567",
              "98,765",
              // The Excel-formula form, so a spreadsheet keeps the trailing digits.
              "=\"1,234.56\"",
              // Tab to two spaces, newline to one space, as legacy sanitised free text.
              "Spur  rebuild second season")
          .hasSize(12);
    }

    @Test
    @DisplayName("a TFL record shows the null marker under TSA_TFL, as legacy's cache miss did")
    void rendersTflRow() {
      // Legacy resolved TSA_TFL out of the TSA cache, and the synthetic "TFL" area type is not a
      // row of that code table, so the lookup missed and the cell came out as the null marker. The
      // TFL number itself is free text and is written through.
      RoadRecord record = road("TFL", "45", null, null, null, null, null, null);

      String[] row = section.row(ctx, record, CODE_LISTS);

      assertThat(row)
          .containsExactly(
              "1234", "2021", "Verified", "5678", "-", "45", "-", "-", "-", "-", "=\"-\"", "-");
    }

    @Test
    @DisplayName("a null $/m3 renders as =\"-\", the marker inside the formula quotes")
    void rendersNullCostPerVolumeInsideTheFormulaQuotes() {
      // Legacy wrapped the null marker in the formula quotes along with everything else, so the
      // cell reaches the file as ="-" rather than a bare dash. On the keep-verbatim list.
      RoadRecord record = road("29", null, "A", "5", null, null, null, null);

      assertThat(section.row(ctx, record, CODE_LISTS)[10]).isEqualTo("=\"-\"");
    }
  }

  @Nested
  @DisplayName("code resolution")
  class CodeResolution {

    @Test
    @DisplayName("a code present in the list renders <code> - <description>")
    void rendersCodeAndDescription() {
      assertThat(Schedule6Section.codeAndDescription("07", TSA_NUMBERS))
          .isEqualTo("07 - Cranbrook TSA");
    }

    @Test
    @DisplayName("a code absent from the list renders the null marker")
    void rendersUnlistedCodeAsNullMarker() {
      assertThat(Schedule6Section.codeAndDescription("99", TSA_NUMBERS)).isEqualTo("-");
    }

    @Test
    @DisplayName("a null code renders the null marker")
    void rendersNullCodeAsNullMarker() {
      assertThat(Schedule6Section.codeAndDescription(null, TSA_NUMBERS)).isEqualTo("-");
    }

    @Test
    @DisplayName("an absent code list renders the null marker rather than throwing")
    void rendersMissingListAsNullMarker() {
      // The generator can be handed a document whose code lists were omitted; the cell degrades
      // instead of failing the whole extract.
      assertThat(Schedule6Section.codeAndDescription("29", null)).isEqualTo("-");

      String[] row = section.row(ctx, road("29", null, "A", "5", null, null, null, null), null);

      assertThat(row[4]).isEqualTo("-");
      assertThat(row[6]).isEqualTo("-");
    }

    @Test
    @DisplayName("a listed code with no description keeps the separator and trails empty")
    void rendersDescriptionlessCodeWithTrailingSeparator() {
      // The cell is built by concatenation, so the separator survives with nothing after it.
      List<CodeDescriptionDto> options = List.of(new CodeDescriptionDto("29", null));

      assertThat(Schedule6Section.codeAndDescription("29", options)).isEqualTo("29 - ");
    }
  }

  @Nested
  @DisplayName("markers")
  class Markers {

    @Test
    @DisplayName("the per-record marker is the four leading cells then *** NO DATA FOUND ***")
    void pinsPerRecordMarker() {
      assertThat(ctx.noDataRow())
          .containsExactly("1234", "2021", "Verified", "5678", "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("the whole-schedule marker is the default five cells")
    void pinsWholeScheduleMarker() {
      // Schedule 6 does not override noDataRow, so it emits the five-cell form: the mills, the year
      // range, two null markers, then the marker text.
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(5)
          .containsExactly("Included Mills: 673", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***");
    }
  }
}
