package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsSummary;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule1.dto.SilvicultureBlock;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule1Section}. The header and title assertions are written out as literals
 * rather than read from the production constant so that a column rename, reorder or drop fails here
 * instead of silently shipping a file the ministry's downstream spreadsheets cannot read.
 *
 * <p>Also pins the {@code *** NO SCHEDULE 3 ***} sentinel, which legacy wrote into the Schedule
 * 3-derived cells of a Schedule 1 row whose (mill, year) had no Schedule 3 summary.
 */
@DisplayName("Schedule1Section — the 51-column Schedule 1 extract row")
class Schedule1SectionTest {

  private static final String SENTINEL = "*** NO SCHEDULE 3 ***";
  private static final String NO_DATA = "*** NO DATA FOUND ***";

  /** The four leading cells of every row: mill NUMBER, year, status DESCRIPTION, mill id. */
  private static final RowContext CTX = new RowContext("670", "2021", "Draft", "514");

  private final Schedule1Section section = new Schedule1Section();

  @Nested
  @DisplayName("fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the title is legacy's verbatim, including its four leading stars")
    void title_isVerbatim() {
      // Schedule1Extract.java:35.
      assertThat(section.title()).isEqualTo("**** Schedule 1 ****");
    }

    @Test
    @DisplayName("the header is the 51 legacy columns, in order")
    void header_isTheFiftyOneLegacyColumns() {
      // Schedule1Extract.java:365-417. Written out literally: this is the regression lock on
      // column fidelity, so deriving it from the production constant would assert nothing.
      assertThat(section.header())
          .containsExactly(
              "MILL_NUMBER",
              "REPORTING_YEAR",
              "STATUS",
              "MILL_ID",
              "CROWN_VOL_SCH_3",
              "STAND_TTT_M3",
              "STAND_TTT_$",
              "STAND_TTT_$/M3",
              "LOG_TRANS_M3",
              "LOG_TRANS_$",
              "LOG_TRANS_$/M3",
              "ROAD_MNGMT_M3",
              "ROAD_MNGMT_$",
              "ROAD_MNGMT_$/M3",
              "ROAD_CONS_M3",
              "ROAD_CONS_$",
              "ROAD_CONS_$/M3",
              "POST_LOG_M3",
              "POST_LOG_$",
              "POST_LOG_$/M3",
              "MGMT_ADMIN_M3",
              "MGMT_ADMIN_$",
              "MGMT_ADMIN_$/M3",
              "STUMP_M3",
              "STUMP_$",
              "STUMP_$/M3",
              "DEPLETION_M3",
              "DEPLETION_$",
              "DEPLETION_$/M3",
              "SUB_OTHER_COST_M3",
              "SUB_OTHER_COST_$",
              "SUB_OTHER_COST_$/M3",
              "SUB_COMPANY_LOGGING_M3",
              "SUB_COMPANY_LOGGING_$",
              "SUB_COMPANY_LOGGING_$/M3",
              "SILV_ACT_M3",
              "SILV_ACT_$",
              "SILV_ACT_$/M3",
              "SILV_LESS_ADMIN_M3",
              "SILV_LESS_ADMIN_$",
              "SILV_LESS_ADMIN_$/M3",
              "SILV_ACCRUED_M3",
              "SILV_ACCRUED_$",
              "SILV_ACCRUED_$/M3",
              "SILVIC_TOTAL_M3",
              "SILVIC_TOTAL_$",
              "SILVIC_TOTAL_$/M3",
              "TOTAL_LOG_COSTS_M3",
              "TOTAL_LOG_COSTS_$",
              "TOTAL_LOG_COSTS_$/M3",
              "COMMENTS");
      assertThat(section.header()).hasSize(51);
    }

    @Test
    @DisplayName("header() hands out a copy, so a caller cannot mutate the shared columns")
    void header_isDefensivelyCopied() {
      String[] first = section.header();
      first[0] = "TAMPERED";

      assertThat(section.header()[0]).isEqualTo("MILL_NUMBER");
    }
  }

  @Nested
  @DisplayName("the data row")
  class DataRow {

    @Test
    @DisplayName("a full summary with a Schedule 3 renders every cell in its legacy format")
    void fullSummaryWithSchedule3_rendersEveryCell() {
      String[] row = section.row(CTX, fullResponse(), schedule3Present());

      // Every cell asserted at once: the row is the deliverable, and a whole-array comparison is
      // what catches a figure written into the wrong column.
      assertThat(row)
          .containsExactly(
              "670", // MILL_NUMBER — the number as stored, never the id
              "2021",
              "Draft",
              "514",
              "1,234,567", // CROWN_VOL_SCH_3 — grouped, no decimals (###,###,##0)
              "1,234,567", // STAND_TTT_M3
              "9,876,543", // STAND_TTT_$
              "8.00", // STAND_TTT_$/M3 — always exactly two decimals (###,###,##0.00)
              "-", // LOG_TRANS_M3 — item 13 present but every figure null
              "-", // LOG_TRANS_$
              "-", // LOG_TRANS_$/M3
              "-", // ROAD_MNGMT_M3 — item 14 absent from the document entirely
              "-", // ROAD_MNGMT_$
              "-", // ROAD_MNGMT_$/M3
              "100", // ROAD_CONS_M3
              "200", // ROAD_CONS_$
              "2.50", // ROAD_CONS_$/M3 — a one-place figure still prints two places
              "0", // POST_LOG_M3 — a stored zero is a figure, not an absence
              "0", // POST_LOG_$
              "0.00", // POST_LOG_$/M3
              "555,000", // MGMT_ADMIN_M3 — from the item-143 line, not from Schedule 3
              "25,000", // MGMT_ADMIN_$ — from the derived scalar (the Schedule 3 pull)
              "2.03", // MGMT_ADMIN_$/M3 — likewise
              "10", // STUMP_M3
              "20", // STUMP_$
              "2.00", // STUMP_$/M3
              "30", // DEPLETION_M3
              "40", // DEPLETION_$
              "1.33", // DEPLETION_$/M3 — 1.333 rounded to the cell's two places
              "12,345", // SUB_OTHER_COST_M3 — the shared Other-Costs volume
              "3,000", // SUB_OTHER_COST_$
              "0.24", // SUB_OTHER_COST_$/M3
              "777", // SUB_COMPANY_LOGGING_M3 — from the item-144 line
              "537,000", // SUB_COMPANY_LOGGING_$ — derived scalar
              "43.50", // SUB_COMPANY_LOGGING_$/M3 — derived scalar
              "1,000", // SILV_ACT_M3
              "5,000", // SILV_ACT_$
              "5.00", // SILV_ACT_$/M3
              "3,000", // SILV_LESS_ADMIN_M3 — volume from the item-139 line
              "1,500", // SILV_LESS_ADMIN_$ — cost from the derived scalar
              "0.12", // SILV_LESS_ADMIN_$/M3 — likewise
              "2,000", // SILV_ACCRUED_M3
              "6,000", // SILV_ACCRUED_$
              "3.00", // SILV_ACCRUED_$/M3
              "4,000", // SILVIC_TOTAL_M3 — volume from the item-140 line
              "40,000", // SILVIC_TOTAL_$ — derived scalar
              "3.24", // SILVIC_TOTAL_$/M3 — derived scalar
              "1,234,567", // TOTAL_LOG_COSTS_M3 — the Schedule 3 Crown volume again
              "1,577,000", // TOTAL_LOG_COSTS_$
              "46.70", // TOTAL_LOG_COSTS_$/M3
              "See the notes"); // COMMENTS
      assertThat(row).hasSize(51);
    }

    @Test
    @DisplayName("the grand-total volume cell repeats the Crown volume rather than summing")
    void totalLogCostsVolume_repeatsTheCrownVolume() {
      String[] row = section.row(CTX, fullResponse(), schedule3Present());

      // TOTAL_LOG_COSTS_M3 is the Schedule 3 Crown Timber volume, the same figure CROWN_VOL_SCH_3
      // carries, because the grand total's $/m³ divides by that volume and legacy printed the
      // divisor in the volume column.
      assertThat(row[47]).isEqualTo(row[4]).isEqualTo("1,234,567");
    }

    @Test
    @DisplayName("a null comment renders the null marker, not an empty cell")
    void nullComment_rendersTheNullMarker() {
      Schedule1Response response = withComments(fullResponse(), null);

      assertThat(section.row(CTX, response, schedule3Present())[50]).isEqualTo("-");
    }

    @Test
    @DisplayName("a present Schedule 3 puts the sentinel nowhere in the row")
    void presentSchedule3_hasNoSentinelAnywhere() {
      assertThat(section.row(CTX, fullResponse(), schedule3Present())).doesNotContain(SENTINEL);
    }
  }

  @Nested
  @DisplayName("the *** NO SCHEDULE 3 *** sentinel")
  class NoSchedule3Sentinel {

    @Test
    @DisplayName("an absent Schedule 3 fills every Schedule 3-derived cell with the sentinel")
    void absentSchedule3_fillsTheDerivedCells() {
      String[] row = section.row(CTX, fullResponse(), null);

      // Schedule1Extract.java:101-116: the choice is made on the EXISTENCE of a Schedule 3 summary
      // for the pair, not on whether the derived figures happen to be null — the fixture here
      // carries every one of those figures and they are all suppressed anyway.
      assertThat(row[4]).isEqualTo(SENTINEL); // CROWN_VOL_SCH_3
      assertThat(row[21]).isEqualTo(SENTINEL); // MGMT_ADMIN_$
      assertThat(row[22]).isEqualTo(SENTINEL); // MGMT_ADMIN_$/M3
      assertThat(row[33]).isEqualTo(SENTINEL); // SUB_COMPANY_LOGGING_$
      assertThat(row[34]).isEqualTo(SENTINEL); // SUB_COMPANY_LOGGING_$/M3
      assertThat(row[39]).isEqualTo(SENTINEL); // SILV_LESS_ADMIN_$
      assertThat(row[40]).isEqualTo(SENTINEL); // SILV_LESS_ADMIN_$/M3
      assertThat(row[45]).isEqualTo(SENTINEL); // SILVIC_TOTAL_$
      assertThat(row[46]).isEqualTo(SENTINEL); // SILVIC_TOTAL_$/M3
      assertThat(row[47]).isEqualTo(SENTINEL); // TOTAL_LOG_COSTS_M3 (the Crown volume repeat)
      assertThat(row[48]).isEqualTo(SENTINEL); // TOTAL_LOG_COSTS_$
      assertThat(row[49]).isEqualTo(SENTINEL); // TOTAL_LOG_COSTS_$/M3
    }

    @Test
    @DisplayName("the sentinel lands in exactly twelve cells — the eleven figures, Crown twice")
    void absentSchedule3_fillsExactlyTwelveCells() {
      String[] row = section.row(CTX, fullResponse(), null);

      // The eleven Schedule 3-derived FIGURES occupy twelve columns: the Crown volume is written
      // into both CROWN_VOL_SCH_3 and TOTAL_LOG_COSTS_M3, so its sentinel appears twice.
      assertThat(row).filteredOn(SENTINEL::equals).hasSize(12);
    }

    @Test
    @DisplayName("an absent Schedule 3 leaves the Schedule 1-owned cells untouched")
    void absentSchedule3_leavesScheduleOneCellsAlone() {
      String[] withSch3 = section.row(CTX, fullResponse(), schedule3Present());
      String[] withoutSch3 = section.row(CTX, fullResponse(), null);

      // The volume halves of the cross-schedule rows come from Schedule 1's own line items, so
      // they survive; only the cost and $/m³ halves are Schedule 3-derived.
      assertThat(withoutSch3[20]).isEqualTo(withSch3[20]).isEqualTo("555,000"); // MGMT_ADMIN_M3
      assertThat(withoutSch3[32]).isEqualTo(withSch3[32]).isEqualTo("777"); // SUB_COMPANY_M3
      assertThat(withoutSch3[38]).isEqualTo(withSch3[38]).isEqualTo("3,000"); // SILV_LESS_ADMIN_M3
      assertThat(withoutSch3[44]).isEqualTo(withSch3[44]).isEqualTo("4,000"); // SILVIC_TOTAL_M3
      assertThat(withoutSch3[5]).isEqualTo("1,234,567"); // STAND_TTT_M3
      assertThat(withoutSch3[50]).isEqualTo("See the notes"); // COMMENTS
    }

    @Test
    @DisplayName("the sentinel never reaches the header or either marker row")
    void sentinel_neverReachesTheFixedRows() {
      assertThat(section.header()).doesNotContain(SENTINEL);
      assertThat(section.row(CTX, emptyResponse(), null)).doesNotContain(SENTINEL);
      assertThat(section.noDataRow("670, 671", "2020 - 2021")).doesNotContain(SENTINEL);
    }
  }

  @Nested
  @DisplayName("marker rows")
  class MarkerRows {

    @Test
    @DisplayName("an empty summary yields the per-record marker, not a row of dashes")
    void emptySummary_yieldsThePerRecordMarker() {
      // Schedule1Extract.java:220-229: the four leading cells then the marker, five cells total.
      assertThat(section.row(CTX, emptyResponse(), schedule3Present()))
          .containsExactly("670", "2021", "Draft", "514", NO_DATA);
    }

    @Test
    @DisplayName("the per-record marker wins over the sentinel when there is no Schedule 3 either")
    void emptySummaryWithoutSchedule3_stillYieldsThePerRecordMarker() {
      // Emptiness is decided before the sentinel is, so an empty record with no Schedule 3 is a
      // five-cell marker row rather than a 51-cell row of sentinels.
      assertThat(section.row(CTX, emptyResponse(), null))
          .containsExactly("670", "2021", "Draft", "514", NO_DATA);
    }

    @Test
    @DisplayName("the whole-schedule marker is the mills, the year range, two dashes, the marker")
    void wholeScheduleMarker_isTheFiveLegacyCells() {
      // The SectionBuilder default, which Schedule 1 does not override
      // (Schedule1Extract.java:39-46).
      assertThat(section.noDataRow("670, 671", "2020 - 2021"))
          .containsExactly("670, 671", "2020 - 2021", "-", "-", NO_DATA);
    }
  }

  @Nested
  @DisplayName("legacy checkEmpty")
  class Emptiness {

    @Test
    @DisplayName("no figure, no shared volume and no comment is empty")
    void nothingEntered_isEmpty() {
      assertThat(Schedule1Section.isEmpty(emptyResponse())).isTrue();
    }

    @Test
    @DisplayName("a comment alone makes the record non-empty")
    void commentAlone_isNotEmpty() {
      assertThat(Schedule1Section.isEmpty(withComments(emptyResponse(), "Nothing to report")))
          .isFalse();
    }

    @Test
    @DisplayName("a whitespace-only comment does not make the record non-empty")
    void blankComment_isStillEmpty() {
      assertThat(Schedule1Section.isEmpty(withComments(emptyResponse(), "   "))).isTrue();
    }

    @Test
    @DisplayName("a single line-item figure makes the record non-empty")
    void oneLineItemFigure_isNotEmpty() {
      Schedule1Response response =
          response(null, List.of(li(12, null, 1, null)), null, null, null, null);

      assertThat(Schedule1Section.isEmpty(response)).isFalse();
    }

    @Test
    @DisplayName("a silviculture figure makes the record non-empty")
    void oneSilvicultureFigure_isNotEmpty() {
      SilvicultureBlock silv = new SilvicultureBlock(null, null, null, li(140, "1", null, null));
      Schedule1Response response = response(null, List.of(), silv, null, null, null);

      assertThat(Schedule1Section.isEmpty(response)).isFalse();
    }

    @Test
    @DisplayName("the shared Other-Costs volume makes the record non-empty")
    void sharedOtherCostsVolume_isNotEmpty() {
      OtherCostsSummary other = new OtherCostsSummary(new BigDecimal("10"), null, null, 0);
      Schedule1Response response = response(null, List.of(), null, other, null, null);

      assertThat(Schedule1Section.isEmpty(response)).isFalse();
    }

    @Test
    @DisplayName("an Other-Costs summary with a subtotal but no shared volume is still empty")
    void otherCostsSubtotalWithoutVolume_isStillEmpty() {
      // Legacy's guard tested the shared VOLUME alone; the derived subtotal never entered it.
      OtherCostsSummary other = new OtherCostsSummary(null, 3_000L, null, 2);
      Schedule1Response response = response(null, List.of(), null, other, null, null);

      assertThat(Schedule1Section.isEmpty(response)).isTrue();
    }

    @Test
    @DisplayName("a derived scalar alone does not make the record non-empty")
    void derivedScalarAlone_isStillEmpty() {
      // The Schedule 3 pulls are not entered figures, so they cannot resurrect an empty record.
      Schedule1Response response = response(null, List.of(), null, null, 25_000L, 1_500);

      assertThat(Schedule1Section.isEmpty(response)).isTrue();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  private static LineItem li(int code, String volume, Integer cost, String perUnit) {
    return new LineItem(
        code,
        volume == null ? null : new BigDecimal(volume),
        cost,
        perUnit == null ? null : new BigDecimal(perUnit));
  }

  /**
   * A Schedule 1 document carrying only the components this section reads; everything else is null
   * or zero so the twenty-four-component constructor stays out of the assertions.
   */
  private static Schedule1Response response(
      String comments,
      List<LineItem> lineItems,
      SilvicultureBlock silviculture,
      OtherCostsSummary otherCosts,
      Long forestMgmtAdminCost,
      Integer lessSilvAdminCost) {
    return new Schedule1Response(
        514L,
        2021,
        "D",
        false,
        null,
        null,
        0,
        comments,
        null,
        lineItems,
        silviculture,
        forestMgmtAdminCost,
        lessSilvAdminCost,
        otherCosts,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null);
  }

  private static Schedule1Response withComments(Schedule1Response base, String comments) {
    return new Schedule1Response(
        base.millId(),
        base.year(),
        base.trackStatus(),
        base.editable(),
        base.crownVolume(),
        base.schedule3CrownVolume(),
        base.revisionCount(),
        comments,
        base.originalValues(),
        base.lineItems(),
        base.silviculture(),
        base.forestMgmtAdminCost(),
        base.lessSilvAdminCost(),
        base.otherCosts(),
        base.forestMgmtAdminPerUnit(),
        base.lessSilvAdminPerUnit(),
        base.totalSilvicultureCost(),
        base.totalSilviculturePerUnit(),
        base.subtotalCompanyLoggingCost(),
        base.subtotalCompanyLoggingPerUnit(),
        base.totalCompanyLoggingCost(),
        base.totalCompanyLoggingPerUnit(),
        base.warnings(),
        base.message());
  }

  /** Legacy's "empty record": line items present but every figure null, no volume, no comment. */
  private static Schedule1Response emptyResponse() {
    return response(
        null, List.of(li(12, null, null, null), li(17, null, null, null)), null, null, null, null);
  }

  /**
   * The exercise fixture. Item 13 carries a row of nulls and item 14 is absent altogether, so both
   * ways a cell can reach the null marker are covered; item 16 carries zeros, which are entered
   * figures rather than absences.
   */
  private static Schedule1Response fullResponse() {
    List<LineItem> lineItems =
        List.of(
            li(12, "1234567", 9_876_543, "8"),
            li(13, null, null, null),
            li(15, "100", 200, "2.5"),
            li(16, "0", 0, "0"),
            li(143, "555000", null, null),
            li(17, "10", 20, "2"),
            li(18, "30", 40, "1.333"),
            li(144, "777", null, null));
    SilvicultureBlock silv =
        new SilvicultureBlock(
            li(1, "1000", 5_000, "5"),
            li(2, "2000", 6_000, "3"),
            li(139, "3000", null, null),
            li(140, "4000", null, null));
    return new Schedule1Response(
        514L,
        2021,
        "D",
        false,
        1_234_567,
        new BigDecimal("1234567"),
        0,
        "See the notes",
        null,
        lineItems,
        silv,
        25_000L,
        1_500,
        new OtherCostsSummary(new BigDecimal("12345"), 3_000L, new BigDecimal("0.24"), 2),
        new BigDecimal("2.03"),
        new BigDecimal("0.12"),
        40_000L,
        new BigDecimal("3.24"),
        537_000L,
        new BigDecimal("43.50"),
        1_577_000L,
        new BigDecimal("46.70"),
        List.of(),
        null);
  }

  /**
   * Only the EXISTENCE of this document matters to the section: every Schedule 3 figure a Schedule
   * 1 row shows has already been folded into the Schedule 1 derived scalars, so the sentinel
   * decision reads nothing out of it.
   */
  private static Schedule3Response schedule3Present() {
    return new Schedule3Response(
        514L, 2021, "D", false, 0, "N", null, null, List.of(), null, null, null, null, null, null,
        null, 0, 0, List.of(), null);
  }
}
