package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Culvert;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertCodeLists;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Schedule 7B section's shape, pinned cell by cell.
 *
 * <p>Title and header are written out literally rather than read back off the production constant,
 * because the constant is what these tests guard ({@code Schedule7bExtract.java:31, :92-106}).
 */
@DisplayName("Schedule7bSection — legacy column fidelity")
class Schedule7bSectionTest {

  private final Schedule7bSection section = new Schedule7bSection();

  private final RowContext ctx = new RowContext("1234", "2021", "Verified", "5678");

  private static final CulvertCodeLists CODES =
      new CulvertCodeLists(
          List.of(
              new CodeDescriptionDto("R", "Round"),
              new CodeDescriptionDto("O", "Others"),
              new CodeDescriptionDto("RP", "Round Plastic")));

  /**
   * A culvert carrying only its type; every other component is absent. Built through the
   * no-original-values constructor, whose three primitive components cannot be null.
   */
  private static Culvert culvertOfType(String typeCode) {
    return new Culvert(1L, 1, typeCode, null, null, null, null, null, null, null, null, 1);
  }

  @Nested
  @DisplayName("fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the title DOES have a space before its closing asterisks, unlike 7A's")
    void pinsTitleWithTheSpaceBeforeTheClosingStars() {
      // Schedule7bExtract.java:31. Its twin, "**** Schedule 7A - Bridge****", has no space there.
      // The inconsistency between the two is legacy's; both spellings are deliberate and neither
      // should be made to match the other.
      assertThat(section.title()).isEqualTo("**** Schedule 7B - Culvert ****");
    }

    @Test
    @DisplayName("the header is legacy's thirteen columns, in order")
    void pinsHeader() {
      // Schedule7bExtract.java:92-106.
      assertThat(section.header())
          .containsExactly(
              "MILL_NUMBER",
              "REPORTING_YEAR",
              "STATUS",
              "MILL_ID",
              "TYPE",
              "SPAN_MM",
              "RISE_MM",
              "LENGTH_M",
              "NO_OF_PIECES",
              "MATERIAL_$",
              "INSTALL_$",
              "TOTAL_$",
              "COMMENTS")
          .hasSize(13);
    }

    @Test
    @DisplayName("header() hands back a copy, so a caller cannot edit the shared array")
    void headerIsDefensivelyCopied() {
      String[] first = section.header();
      first[4] = "MUTATED";

      assertThat(section.header()[4]).isEqualTo("TYPE");
    }
  }

  @Nested
  @DisplayName("data rows")
  class DataRows {

    @Test
    @DisplayName("a fully populated culvert renders all thirteen cells")
    void rendersFullRow() {
      Culvert culvert =
          new Culvert(
              1L,
              1,
              "R",
              1200,
              null,
              new BigDecimal("18.4"),
              3,
              1234567,
              45000,
              1279567,
              "Round\tsteel\npipe",
              1);

      String[] row = section.row(ctx, culvert, CODES);

      assertThat(row)
          .containsExactly(
              "1234",
              "2021",
              "Verified",
              "5678",
              "R - Round",
              // Span and rise are the raw toString of the stored millimetre value, so they carry no
              // thousands grouping.
              "1200",
              // Rise is the one measurement Check Status never requires; absent, it is the marker.
              "-",
              // LENGTH_M goes through the whole-number formatter even though the owner stores one
              // decimal metre, so the stored .4 is rounded away in the file.
              "18",
              "3",
              // Costs are ###,###,##0 — grouped, whole dollars.
              "1,234,567",
              "45,000",
              "1,279,567",
              // Tab to two spaces, newline to one space, as legacy sanitised free text.
              "Round  steel pipe")
          .hasSize(13);
    }

    @Test
    @DisplayName("every absent value is the null marker, an empty comment included")
    void rendersAbsentValuesAsNullMarker() {
      String[] row = section.row(ctx, culvertOfType("R"), CODES);

      assertThat(row)
          .containsExactly(
              "1234",
              "2021",
              "Verified",
              "5678",
              "R - Round",
              "-",
              "-",
              "-",
              "-",
              "-",
              "-",
              "-",
              "-");
    }

    @Test
    @DisplayName("a maintenance-table addition resolves without a code change")
    void resolvesACodeAddedByTableMaintenance() {
      // The type list is read from the code table rather than an enum, which is what let the
      // business add Round Plastic without touching this section.
      String[] row = section.row(ctx, culvertOfType("RP"), CODES);

      assertThat(row[4]).isEqualTo("RP - Round Plastic");
    }
  }

  @Nested
  @DisplayName("code resolution")
  class CodeResolution {

    @Test
    @DisplayName("a null lookup code renders the null marker instead of throwing")
    void rendersNullCodeAsNullMarkerRatherThanThrowing() {
      // Shared with 7A, whose Schedule7aExtract.java:50-54 dereferenced the cache hit unguarded: a
      // single row with a missing code threw a NullPointerException and lost the whole extract for
      // every mill and year in the request. The marker here is a deliberate behavioural fix.
      String[] row = section.row(ctx, culvertOfType(null), CODES);

      assertThat(row[4]).isEqualTo("-");
    }

    @Test
    @DisplayName("a code absent from the list renders the null marker")
    void rendersUnlistedCodeAsNullMarker() {
      assertThat(section.row(ctx, culvertOfType("X"), CODES)[4]).isEqualTo("-");
    }

    @Test
    @DisplayName("an absent code list renders the null marker rather than throwing")
    void rendersMissingListAsNullMarker() {
      assertThat(section.row(ctx, culvertOfType("R"), null)[4]).isEqualTo("-");
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
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(5)
          .containsExactly("Included Mills: 673", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***");
    }
  }
}
