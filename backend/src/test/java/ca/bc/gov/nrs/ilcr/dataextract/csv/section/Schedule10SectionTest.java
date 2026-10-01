package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the main Schedule 10 section, transcribed from legacy {@code Schedule10Extract}.
 *
 * <p>The header is written out literally, which is the only way the trailing tab on the {@code
 * PERIOD} cell can be pinned: it is invisible in any rendering of the file and would be silently
 * lost by anyone retyping the column list.
 */
@DisplayName("Schedule10Section — legacy Schedule10Extract")
class Schedule10SectionTest {

  private static final RowContext CTX = new RowContext("1234", "2021", "Verified", "567");

  private final Schedule10Section section = new Schedule10Section();

  /** A page carrying only the components this section reads; the rest are fillers. */
  private static ConstructionPage page(
      String pageLabel,
      String forestRegionCode,
      String tsaNumber,
      String tsbNumberCode,
      String tflNumberCode,
      String roadGroup,
      String divisionName,
      String constructionPeriod) {
    return new ConstructionPage(
        1,
        1,
        pageLabel,
        forestRegionCode,
        tsaNumber,
        tsbNumberCode,
        tflNumberCode,
        roadGroup,
        divisionName,
        constructionPeriod,
        0,
        0,
        List.of());
  }

  private static ConstructionPage populatedPage() {
    return page("Page # 1", "RCB", "27", "27A", null, "G1", "Coast", "2020/2021");
  }

  @Nested
  @DisplayName("header")
  class Header {

    @Test
    @DisplayName("is legacy Schedule10Extract's twelve columns, in order")
    void isLegacyHeader() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "PAGE_NO",
        "DIVISION",
        // Seven characters then a LITERAL TAB. Legacy's header cell carried it, and the extract
        // has to be byte-identical, so the tab is deliberate rather than a stray keystroke.
        // Do not "tidy" this to "PERIOD".
        "PERIOD\t",
        "REGION",
        "TSA_TFL",
        "S_BLOCK",
        "TFL",
        "ROAD_GROUP",
      };

      assertThat(section.header()).containsExactly(expected);
      assertThat(section.header()).hasSize(12);
    }

    @Test
    @DisplayName("the PERIOD cell ends in a tab and nothing else")
    void periodCellEndsInATab() {
      // Asserted on its own as well as in the array above, because a diff on a failing array
      // assertion would not show which of the two cells differs by an invisible character.
      assertThat(section.header()[6]).isEqualTo("PERIOD\t").endsWith("\t").hasSize(7);
      assertThat(Schedule10Section.PERIOD_HEADER).isEqualTo("PERIOD\t");
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
      assertThat(section.title()).isEqualTo("**** Schedule 10 ****");
    }
  }

  @Nested
  @DisplayName("data row")
  class DataRow {

    @Test
    @DisplayName("builds every cell of a populated page")
    void buildsPopulatedRow() {
      ConstructionPage page =
          page("Page # 1", "RCB", "27", "27A", null, "G1", "Coast\tDivision", "2020/2021");

      String[] expected = {
        "1234",
        "2021",
        "Verified",
        "567",
        "Page # 1",
        // The division goes through the sanitising text formatter, so its tab becomes two spaces.
        "Coast  Division",
        "2020/2021",
        "RCB",
        "27",
        // S_BLOCK here is the stored TSB code as-is. Unlike Schedule 8's S_BLOCK, legacy did not
        // shed its leading two characters in this section.
        "27A",
        // An absent TFL number is the null marker, not an empty cell.
        "-",
        "G1",
      };

      assertThat(section.row(CTX, page)).containsExactly(expected);
    }

    @Test
    @DisplayName("is as wide as the header, with no comments column")
    void isHeaderWidthWithNoComments() {
      // Legacy's Schedule 10 page row ends at ROAD_GROUP; the comments live on the road detail,
      // which is the Road Data section's business.
      assertThat(section.row(CTX, populatedPage())).hasSameSizeAs(section.header());
      assertThat(section.header()).doesNotContain("COMMENTS");
    }

    @Test
    @DisplayName("an absent label, division or period is the null marker")
    void absentTextCellsAreNullMarkers() {
      ConstructionPage bare = page(null, null, null, null, null, null, null, null);

      assertThat(section.row(CTX, bare))
          .containsExactly(
              "1234", "2021", "Verified", "567", "-", "-", "-", "-", "-", "-", "-", "-");
    }
  }

  @Nested
  @DisplayName("shared page cells")
  class SharedPageCells {

    @Test
    @DisplayName("are the seven the Road Data section reuses, in the same order")
    void areTheSevenSharedWithRoadData() {
      // The Road Data section builds its own rows from these, so a reordering here would move
      // both sections' columns at once and the header assertions would be the only guard.
      assertThat(Schedule10Section.pageCells(populatedPage()))
          .containsExactly("Page # 1", "Coast", "2020/2021", "RCB", "27", "27A", "-");
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
