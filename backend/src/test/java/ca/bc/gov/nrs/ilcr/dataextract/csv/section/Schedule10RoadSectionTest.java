package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule10.dto.BecClassification;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Road Data section, transcribed from legacy {@code Schedule10RoadExtract}.
 *
 * <p>Three of its columns are permanently dashed and two of them carry the same figure. Both look
 * like mistakes and neither is, so each has its own test: the dashed three are fields the Ministry
 * removed from the product whose headers stay for column parity, and the duplicated figure is the
 * one legacy put in both places.
 *
 * <p>Most numeric cells are the raw {@code toString()} of the stored value, so the fixtures below
 * use {@code BigDecimal} string literals — the scale reaches the file, and constructing the same
 * number a different way would change the expected text.
 */
@DisplayName("Schedule10RoadSection — legacy Schedule10RoadExtract")
class Schedule10RoadSectionTest {

  private static final RowContext CTX = new RowContext("1234", "2021", "Verified", "567");

  private final Schedule10RoadSection section = new Schedule10RoadSection();

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static ConstructionPage page(List<RoadDetail> details) {
    return new ConstructionPage(
        1,
        1,
        "Page # 1",
        "RCB",
        "27",
        "27A",
        null,
        "G1",
        "Coast",
        "2020/2021",
        details == null ? 0 : details.size(),
        0,
        details);
  }

  private static SubGrade subGrade() {
    return new SubGrade(
        bd("3.5"),
        bd("6.0"),
        bd("150000"),
        bd("2500"),
        bd("0"),
        bd("1000"),
        bd("2000"),
        bd("3000"),
        bd("4000"),
        bd("5000"),
        bd("6000"),
        bd("152500"),
        bd("21000"),
        bd("131500"),
        bd("37571.428"));
  }

  private static Stabilizing stabilizing() {
    return new Stabilizing(
        "PIT",
        "GRAV",
        bd("3.5"),
        bd("6.0"),
        bd("0.3"),
        bd("12.0"),
        bd("40000"),
        bd("500"),
        bd("0"),
        bd("40500"),
        bd("11571.428"));
  }

  private static MaterialComposition materials() {
    return new MaterialComposition(10, 20, 30, 35, 5, 100);
  }

  /** A fully populated road detail, including the three components the section digs into. */
  private static RoadDetail populatedDetail() {
    return new RoadDetail(
        1,
        1,
        "Road 1",
        "Mainline 100",
        "P",
        new BecClassification(42, "IDF", "dk", "3", "a", "IDFdk3a"),
        // The RSMR class the owner does serve, which must NOT reach the RSMS_CLASS column.
        "RSMR-4",
        35,
        subGrade(),
        stabilizing(),
        materials(),
        "Y",
        bd("2.5"),
        bd("1000"),
        bd("1.5"),
        bd("800"),
        "Wet\tseason\nreroute",
        1);
  }

  /** A detail with the three nested components absent, so every cell they feed is the marker. */
  private static RoadDetail bareDetail(String engineeringCostInd) {
    return new RoadDetail(
        1,
        1,
        "Road 1",
        null,
        null,
        null,
        "RSMR-4",
        null,
        null,
        null,
        null,
        engineeringCostInd,
        null,
        null,
        null,
        null,
        null,
        1);
  }

  private static String[] onlyRow(RoadDetail detail) {
    List<String[]> rows = new Schedule10RoadSection().detailRows(CTX, page(List.of(detail)));
    assertThat(rows).hasSize(1);
    return rows.get(0);
  }

  @Nested
  @DisplayName("header")
  class Header {

    @Test
    @DisplayName("is legacy Schedule10RoadExtract's fifty-eight columns, in order")
    void isLegacyHeader() {
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "PAGE_NO",
        "DIVISION",
        // The same literal trailing tab as the main Schedule 10 header carries; legacy's, and
        // deliberate. Do not "tidy" this to "PERIOD".
        "PERIOD\t",
        "REGION",
        "TSA_TFL",
        "S_BLOCK",
        "TFL",
        "ROAD_NAME",
        "ROAD_TYPE",
        // Removed from the product; the column stays, the value is always the null marker.
        "MOISTURE",
        "BIOGEO_SUBZONE_VARIANT",
        // Removed from the product; see above.
        "RSMS_CLASS",
        "SLOPE_%",
        // Removed from the product; see above.
        "BOULDER_%",
        "MAT_ROCK_SOLID_%",
        "MAT_ROCK_RIP_%",
        "MAT_COARSE_%",
        "MAT_FINE_%",
        "MAT_ORGANIC_%",
        "MAT_TOT_%",
        // ENG_COST and SG_OTHER_ENG_$ below are two columns fed by ONE stored figure.
        "ENG_COST",
        "ENG_COST_IND",
        "SG_LENGTH_KM",
        "SG_WIDTH_M",
        "SG_ACTUAL_$",
        "SG_TRANSFER_$",
        "SG_OTHER_$",
        "SG_COST_TOT_$",
        "SG_BRIDGE_$",
        "SG_CULVERTS_$",
        "SG_LANDINGS_$",
        "SG_END_HAUL_$",
        "SG_OVERLAND_$",
        "SG_OTHER_ENG_$",
        "SG_TOTAL_$",
        "SG_$/KM",
        "SG_END_HAUL_KM",
        "SG_END_HAUL_VOL",
        "SG_END_HAUL_CALC",
        "SG_OVERLAND_DIST",
        "SG_OVERLAND_VOL",
        "SG_OVERLAND_CALC",
        "AS_CODE",
        "AS_LEN_KM",
        "AS_WIDTH_M",
        "AS_TYPE",
        "AS_DEPTH_M",
        "AS_SOURCE_KM",
        "AS_ACTUAL_$",
        "AS_TT_TRANS_$",
        "AS_OTHER_$",
        "AS_TOTAL_$",
        "AS_$/KM",
        "COMMENTS",
      };

      assertThat(section.header()).containsExactly(expected);
      assertThat(section.header()).hasSize(58);
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
      assertThat(section.title()).isEqualTo("**** Schedule 10 - Road Data ****");
    }
  }

  @Nested
  @DisplayName("data row")
  class DataRow {

    @Test
    @DisplayName("builds every cell of a populated road detail")
    void buildsPopulatedRow() {
      String[] expected = {
        "1234",
        "2021",
        "Verified",
        "567",
        "Page # 1",
        "Coast",
        "2020/2021",
        "RCB",
        "27",
        "27A",
        "-",
        "Mainline 100",
        "P",
        // MOISTURE — removed from the product.
        "-",
        // The full classification label, not the base zone; see the Biogeoclimatic test below.
        "IDFdk3a",
        // RSMS_CLASS — removed from the product.
        "-",
        "35",
        // BOULDER_% — removed from the product.
        "-",
        "10",
        "20",
        "30",
        "35",
        "5",
        "100",
        // ENG_COST — the sub-grade's other-engineering deduction, which also feeds
        // SG_OTHER_ENG_$ fourteen columns to the right.
        "5000",
        // Legacy's asymmetric casing: "Yes" against "NO".
        "Yes",
        // The raw toString() of the stored value: no grouping, and the stored scale survives.
        "3.5",
        "6.0",
        "150000",
        "2500",
        "0",
        "152500",
        "1000",
        "2000",
        "3000",
        "6000",
        "4000",
        // SG_OTHER_ENG_$ — the same figure as ENG_COST above.
        "5000",
        "131500",
        // One of only four formatted numeric cells in the row.
        "37,571.43",
        "2.5",
        "1000",
        // 6000 / 1000 = 6.00, then 6.00 / 2.5 = 2.40 — legacy's two chained divisions, each
        // rounded to two places on the way.
        "2.40",
        "1.5",
        "800",
        // 4000 / 800 = 5.00, then 5.00 / 1.5 = 3.33.
        "3.33",
        "PIT",
        "3.5",
        "6.0",
        "GRAV",
        "0.3",
        "12.0",
        "40000",
        "500",
        "0",
        "40500",
        "11,571.43",
        // The comment goes through the sanitising text formatter.
        "Wet  season reroute",
      };

      assertThat(onlyRow(populatedDetail())).containsExactly(expected);
    }

    @Test
    @DisplayName("is as wide as the header")
    void isHeaderWidth() {
      assertThat(onlyRow(populatedDetail())).hasSameSizeAs(section.header());
    }

    @Test
    @DisplayName("an absent sub-grade, stabilizing or material block dashes its whole span")
    void absentNestedBlocksDashTheirCells() {
      String[] row = onlyRow(bareDetail("Y"));

      // The section reads three optional nested records; a detail saved before those tabs were
      // filled in has to produce a full-width row all the same.
      assertThat(row).hasSameSizeAs(section.header());
      assertThat(Arrays.copyOfRange(row, 18, 25)).containsOnly("-");
      assertThat(Arrays.copyOfRange(row, 26, 46)).containsOnly("-");
      assertThat(Arrays.copyOfRange(row, 46, 57)).containsOnly("-");
    }

    @Test
    @DisplayName("the detailed-engineering indicator is Yes or NO, with legacy's casing")
    void engineeringIndicatorKeepsLegacyCasing() {
      // "Yes" title-cased against "NO" upper-cased is legacy's, not a typo introduced here.
      // Anything that is not exactly "Y" — including a null — takes the NO branch.
      assertThat(onlyRow(bareDetail("Y"))[25]).isEqualTo("Yes");
      assertThat(onlyRow(bareDetail("N"))[25]).isEqualTo("NO");
      assertThat(onlyRow(bareDetail(null))[25]).isEqualTo("NO");
      assertThat(onlyRow(bareDetail("y"))[25]).isEqualTo("NO");
    }
  }

  @Nested
  @DisplayName("MOISTURE, RSMS_CLASS and BOULDER_% keep their headers and are always dashed")
  class RemovedFieldsAlwaysDashed {

    /** The Soil Moisture Code the Ministry removed. */
    private static final int MOISTURE = 13;

    /** The ASM Code the Ministry removed. */
    private static final int RSMS_CLASS = 15;

    /** The Boulder Area percentage the Ministry removed. */
    private static final int BOULDER_PCT = 17;

    @Test
    @DisplayName("all three are the null marker even on a fully populated detail")
    void allThreeAreDashedOnAPopulatedDetail() {
      // These three columns exist only so the file keeps legacy's column count and column
      // positions; the fields behind them are gone from the product and no owner field replaces
      // them. Do NOT wire a value in from a similarly-named field — in particular RSMS_CLASS is
      // not the owner's relSoilMoistRgmClsCode, which is the RSMR class, a different legacy
      // field that legacy never wrote to this column.
      String[] row = onlyRow(populatedDetail());

      assertThat(row[MOISTURE]).isEqualTo("-");
      assertThat(row[RSMS_CLASS]).isEqualTo("-");
      assertThat(row[BOULDER_PCT]).isEqualTo("-");
    }

    @Test
    @DisplayName("the headers are still there, so the column count matches legacy's")
    void theHeadersAreStillThere() {
      assertThat(section.header()[MOISTURE]).isEqualTo("MOISTURE");
      assertThat(section.header()[RSMS_CLASS]).isEqualTo("RSMS_CLASS");
      assertThat(section.header()[BOULDER_PCT]).isEqualTo("BOULDER_%");
    }

    @Test
    @DisplayName("RSMS_CLASS does not borrow the owner's RSMR class field")
    void rsmsClassDoesNotBorrowTheRsmrClass() {
      // The populated detail carries "RSMR-4" in relSoilMoistRgmClsCode. If it ever shows up in
      // this column, someone has restored the wrong field.
      RoadDetail detail = populatedDetail();

      assertThat(detail.relSoilMoistRgmClsCode()).isEqualTo("RSMR-4");
      assertThat(onlyRow(detail)).doesNotContain("RSMR-4");
    }
  }

  @Nested
  @DisplayName("ENG_COST and SG_OTHER_ENG_$ are two columns fed by one figure")
  class DuplicatedEngineeringCost {

    private static final int ENG_COST = 24;

    private static final int SG_OTHER_ENG = 37;

    @Test
    @DisplayName("both carry the sub-grade's other-engineering deduction, which is deliberate")
    void bothCarryTheSameFigure() {
      // Legacy filled both columns from the same sub-grade deduction. It reads like a
      // copy-and-paste, but the file legacy produced has the figure twice and the rebuilt one has
      // to as well, so neither column is "the wrong one" to leave alone.
      String[] row = onlyRow(populatedDetail());

      assertThat(row[ENG_COST]).isEqualTo("5000");
      assertThat(row[SG_OTHER_ENG]).isEqualTo("5000");
      assertThat(row[ENG_COST]).isEqualTo(row[SG_OTHER_ENG]);
      assertThat(section.header()[ENG_COST]).isEqualTo("ENG_COST");
      assertThat(section.header()[SG_OTHER_ENG]).isEqualTo("SG_OTHER_ENG_$");
    }
  }

  @Nested
  @DisplayName("BIOGEO_SUBZONE_VARIANT carries the full classification label")
  class Biogeoclimatic {

    private static final int BIOGEO = 14;

    @Test
    @DisplayName("is the classification's own label, not its base zone code")
    void isTheFullLabelNotTheZone() {
      // The classification record carries both; legacy wrote the assembled
      // zone+subzone+variant+phase label, so "IDF" alone in this cell would be a regression.
      String[] row = onlyRow(populatedDetail());

      assertThat(row[BIOGEO]).isEqualTo("IDFdk3a");
      assertThat(row[BIOGEO]).isNotEqualTo("IDF");
    }

    @Test
    @DisplayName("an absent classification is the null marker")
    void absentClassificationIsNullMarker() {
      assertThat(onlyRow(bareDetail("Y"))[BIOGEO]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the two chained-division rate cells")
  class ChainedDivisions {

    @Test
    @DisplayName("divide by volume and then by distance, each rounded on the way")
    void divideByVolumeThenDistance() {
      // Legacy's getEndHaulCostPerVolumePerLength divided twice rather than by the product, and
      // rounded to two places between the divisions, so the result is not the same as one
      // division by volume × distance.
      assertThat(Schedule10RoadSection.perVolumePerLength(bd("6000"), bd("1000"), bd("2.5")))
          .isEqualByComparingTo(bd("2.40"));
      assertThat(Schedule10RoadSection.perVolumePerLength(bd("4000"), bd("800"), bd("1.5")))
          .isEqualByComparingTo(bd("3.33"));
    }

    @Test
    @DisplayName("are null on a null or zero operand, so the cell is the null marker")
    void nullOrZeroOperandIsNull() {
      assertThat(Schedule10RoadSection.perVolumePerLength(null, bd("1000"), bd("2.5"))).isNull();
      assertThat(Schedule10RoadSection.perVolumePerLength(bd("6000"), null, bd("2.5"))).isNull();
      assertThat(Schedule10RoadSection.perVolumePerLength(bd("6000"), bd("1000"), null)).isNull();
      assertThat(Schedule10RoadSection.perVolumePerLength(bd("6000"), bd("0"), bd("2.5"))).isNull();
      assertThat(Schedule10RoadSection.perVolumePerLength(bd("6000"), bd("1000"), bd("0")))
          .isNull();
    }
  }

  @Nested
  @DisplayName("pages with no road details")
  class PagesWithNoDetails {

    @Test
    @DisplayName("contribute no detail rows and are flagged for the appended marker")
    void contributeNoDetailRows() {
      // Legacy collected these pages and wrote their markers AFTER every detail row of the whole
      // section, rather than in page order, which is why the section exposes the two separately.
      ConstructionPage empty = page(List.of());
      ConstructionPage absent = page(null);

      assertThat(section.detailRows(CTX, empty)).isEmpty();
      assertThat(section.detailRows(CTX, absent)).isEmpty();
      assertThat(section.hasNoDetails(empty)).isTrue();
      assertThat(section.hasNoDetails(absent)).isTrue();
      assertThat(section.hasNoDetails(page(List.of(populatedDetail())))).isFalse();
    }

    @Test
    @DisplayName("their marker is the twelve page cells with the marker in the twelfth")
    void markerIsTwelveCells() {
      String[] expected = {
        "1234",
        "2021",
        "Verified",
        "567",
        "Page # 1",
        "Coast",
        "2020/2021",
        "RCB",
        "27",
        "27A",
        "-",
        "*** NO DATA FOUND ***",
      };

      assertThat(section.noDetailsRow(CTX, page(List.of()))).containsExactly(expected);
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
