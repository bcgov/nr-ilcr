package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeCodeLists;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Schedule 7A section's shape, pinned cell by cell.
 *
 * <p>The title and header are written out literally rather than read back off the production
 * constant, because the constant is exactly what these tests are guarding ({@code
 * Schedule7aExtract.java:33, :129-161}). Two of the strings here look like mistakes and are not:
 * see the title and header tests.
 */
@DisplayName("Schedule7aSection — legacy column fidelity")
class Schedule7aSectionTest {

  private final Schedule7aSection section = new Schedule7aSection();

  private final RowContext ctx = new RowContext("1234", "2021", "Verified", "5678");

  private static final BridgeCodeLists CODES =
      new BridgeCodeLists(
          List.of(new CodeDescriptionDto("N", "New"), new CodeDescriptionDto("U", "Used")),
          List.of(new CodeDescriptionDto("STL", "Steel")),
          List.of(new CodeDescriptionDto("TIM", "Timber")),
          List.of(new CodeDescriptionDto("CON", "Concrete")),
          List.of(new CodeDescriptionDto("L75", "L75 Legal")));

  /**
   * A bridge carrying only the five code selections; every other component is absent. Built through
   * the no-original-values constructor, whose three primitive components cannot be null.
   */
  private static Bridge bridgeWithCodes(
      String constructionType,
      String superstructureType,
      String deckType,
      String abutmentType,
      String loadRating) {
    return new Bridge(
        1L,
        1,
        null,
        null,
        constructionType,
        superstructureType,
        deckType,
        abutmentType,
        loadRating,
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
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        1);
  }

  @Nested
  @DisplayName("fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the title has NO space before its closing asterisks")
    void pinsTitleWithoutTheSpaceBeforeTheClosingStars() {
      // Schedule7aExtract.java:33. Every other section pads both sides — Schedule 7B's twin title
      // is "**** Schedule 7B - Culvert ****" — but 7A's does not, and a legacy sample file shows
      // the unpadded form. This is deliberate, not a typo to tidy up.
      assertThat(section.title()).isEqualTo("**** Schedule 7A - Bridge****");
    }

    @Test
    @DisplayName("the header is legacy's thirty-one columns, in order, COMMMENT_A included")
    void pinsHeader() {
      // Schedule7aExtract.java:129-161. The last label really is COMMMENT_A with three Ms; it is
      // legacy's spelling, it is what consumers of the file parse against, and it is deliberately
      // preserved. Correcting it would break the column contract.
      assertThat(section.header())
          .containsExactly(
              "MILL_NUMBER",
              "REPORTING_YEAR",
              "STATUS",
              "MILL_ID",
              "LOCATION_NAME",
              "BUILT_DATE",
              "NEW/USED",
              "LIFE",
              "SS_TYPE",
              "ABUT_TYPE",
              "ABUT_HT_M",
              "LOAD_RATING",
              "LENGTH_M",
              "DECK_WIDTH_M",
              "DECK_TYPE",
              "DISTANCE_KM",
              "SITE_PLAN_$",
              "SS_MATERIAL_$",
              "SS_DELIVER_$",
              "SS_INSTALL_$",
              "ABUT_MATERIAL_$",
              "ABUT_DELIVER_$",
              "ABUT_INSTALL_$",
              "TOT_MATERIAL_$",
              "TOT_DELIVER_$",
              "TOT_INSTALL_$",
              "APPROACH_$",
              "AFTER_INSTALL_$",
              "OTH_COSTS_$",
              "GRAND_TOT_$",
              "COMMMENT_A")
          .hasSize(31);
    }

    @Test
    @DisplayName("header() hands back a copy, so a caller cannot edit the shared array")
    void headerIsDefensivelyCopied() {
      String[] first = section.header();
      first[30] = "COMMENT_A";

      assertThat(section.header()[30]).isEqualTo("COMMMENT_A");
    }
  }

  @Nested
  @DisplayName("data rows")
  class DataRows {

    @Test
    @DisplayName("a fully populated bridge renders all thirty-one cells")
    void rendersFullRow() {
      Bridge bridge =
          new Bridge(
              1L,
              1,
              "Moberly Creek Crossing",
              "2021-06",
              "N",
              "STL",
              "TIM",
              "CON",
              "L75",
              40,
              new BigDecimal("2.5"),
              new BigDecimal("18.0"),
              new BigDecimal("4.5"),
              12,
              1500,
              1234567,
              2000,
              3000,
              4000,
              null,
              6000,
              500,
              null,
              250,
              "Bridge\treplaced\nspring",
              1238567,
              2000,
              9000,
              1251817,
              1);

      String[] row = section.row(ctx, bridge, CODES);

      // The row's column order is NOT the record's component order: DECK_TYPE sits after
      // DECK_WIDTH_M in the file although deckTypeCode is declared before the measurements.
      assertThat(row)
          .containsExactly(
              "1234",
              "2021",
              "Verified",
              "5678",
              "Moberly Creek Crossing",
              // The owner stores yyyy-MM; legacy's cell is MM/yyyy.
              "06/2021",
              "N - New",
              "40",
              "STL - Steel",
              "CON - Concrete",
              // Measurements are the raw toString, so the stored scale shows through.
              "2.5",
              "L75 - L75 Legal",
              "18.0",
              "4.5",
              "TIM - Timber",
              "12",
              // Costs are ###,###,##0 — grouped, whole dollars.
              "1,500",
              "1,234,567",
              "2,000",
              "3,000",
              "4,000",
              // An omitted optional cost is the null marker, not a zero.
              "-",
              "6,000",
              "1,238,567",
              "2,000",
              "9,000",
              "500",
              "-",
              "250",
              "1,251,817",
              "Bridge  replaced spring")
          .hasSize(31);
    }

    @Test
    @DisplayName("an empty comment is the null marker, an absent measurement likewise")
    void rendersAbsentValuesAsNullMarker() {
      String[] row = section.row(ctx, bridgeWithCodes("N", "STL", "TIM", "CON", "L75"), CODES);

      // LIFE, ABUT_HT_M, LENGTH_M, DECK_WIDTH_M, DISTANCE_KM and every cost.
      assertThat(row[7]).isEqualTo("-");
      assertThat(row[10]).isEqualTo("-");
      assertThat(row[12]).isEqualTo("-");
      assertThat(row[13]).isEqualTo("-");
      assertThat(row[15]).isEqualTo("-");
      assertThat(row[16]).isEqualTo("-");
      assertThat(row[29]).isEqualTo("-");
      assertThat(row[30]).isEqualTo("-");
    }

    @Test
    @DisplayName("LOCATION_NAME is written unguarded, so an unnamed bridge leaves a null cell")
    void writesLocationNameUnguarded() {
      // Legacy wrote the name straight through with no null check, and the writer turns a null cell
      // into an empty field rather than the "-" marker. Kept, so the two cases stay
      // distinguishable in the file.
      String[] row = section.row(ctx, bridgeWithCodes("N", "STL", "TIM", "CON", "L75"), CODES);

      assertThat(row[4]).isNull();
    }
  }

  @Nested
  @DisplayName("built date")
  class BuiltDate {

    @Test
    @DisplayName("a yyyy-MM date becomes MM/yyyy")
    void reordersTheStoredMonth() {
      assertThat(Schedule7aSection.builtDate("2021-06")).isEqualTo("06/2021");
    }

    @Test
    @DisplayName("a null date is the null marker")
    void rendersNullDateAsNullMarker() {
      assertThat(Schedule7aSection.builtDate(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("anything not shaped yyyy-MM passes through untouched")
    void passesThroughAnUnrecognisedShape() {
      // The reorder is positional, so a value the owner did not store as yyyy-MM is written as it
      // stands rather than being sliced into nonsense.
      assertThat(Schedule7aSection.builtDate("June 2021")).isEqualTo("June 2021");
      assertThat(Schedule7aSection.builtDate("2021")).isEqualTo("2021");
    }
  }

  @Nested
  @DisplayName("code resolution")
  class CodeResolution {

    @Test
    @DisplayName("a null lookup code renders the null marker instead of throwing")
    void rendersNullCodeAsNullMarkerRatherThanThrowing() {
      // Schedule7aExtract.java:50-54 dereferenced the cache hit without a null check, so one bridge
      // saved with a missing code threw a NullPointerException and killed the whole extract — for
      // every mill and year in the request, not just that row. The rebuild renders the marker; this
      // is a deliberate behavioural fix and the reason the test exists.
      Bridge bridge = bridgeWithCodes(null, null, null, null, null);

      String[] row = section.row(ctx, bridge, CODES);

      assertThat(row[6]).isEqualTo("-");
      assertThat(row[8]).isEqualTo("-");
      assertThat(row[9]).isEqualTo("-");
      assertThat(row[11]).isEqualTo("-");
      assertThat(row[14]).isEqualTo("-");
    }

    @Test
    @DisplayName("a code absent from its list renders the null marker")
    void rendersUnlistedCodeAsNullMarker() {
      String[] row = section.row(ctx, bridgeWithCodes("X", "X", "X", "X", "X"), CODES);

      assertThat(row[6]).isEqualTo("-");
      assertThat(row[14]).isEqualTo("-");
    }

    @Test
    @DisplayName("absent code lists render the null marker rather than throwing")
    void rendersMissingListsAsNullMarker() {
      String[] row = section.row(ctx, bridgeWithCodes("N", "STL", "TIM", "CON", "L75"), null);

      assertThat(row[6]).isEqualTo("-");
      assertThat(row[8]).isEqualTo("-");
      assertThat(row[9]).isEqualTo("-");
      assertThat(row[11]).isEqualTo("-");
      assertThat(row[14]).isEqualTo("-");
    }

    @Test
    @DisplayName("a listed code with no description keeps the separator and trails empty")
    void rendersDescriptionlessCodeWithTrailingSeparator() {
      List<CodeDescriptionDto> options = List.of(new CodeDescriptionDto("N", null));

      assertThat(Schedule7aSection.code("N", options)).isEqualTo("N - ");
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
