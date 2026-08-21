package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.CostLineRow;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for Schedule 10 assembly and derivation — no Spring, no database.
 *
 * <p>Covers the {@code editable} matrix, cost-line routing by legacy ordinal, page/detail nesting,
 * and the positional numbering that legacy assigns on read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Schedule10Service")
class Schedule10ServiceTest {

  private static final long MILL = 710L;
  private static final int YEAR = 2021;

  @Mock private Schedule10Repository repository;

  private Schedule10Service service;

  @BeforeEach
  void setUp() {
    service = new Schedule10Service(repository);
    // Default: no data anywhere. Individual tests override what they need.
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of());
    when(repository.findRoadDetails(MILL, YEAR)).thenReturn(List.of());
    when(repository.findCostLines(MILL, YEAR)).thenReturn(List.of());
    when(repository.findOfferableBecClassifications()).thenReturn(List.of());
    when(repository.findReferencedBecClassifications(MILL, YEAR)).thenReturn(List.of());
    when(repository.findForestRegions(MILL, YEAR)).thenReturn(List.of());
    when(repository.findRoadLifetimes(MILL, YEAR)).thenReturn(List.of());
    when(repository.findBallastMethods(MILL, YEAR)).thenReturn(List.of());
    when(repository.findBallastMaterials(MILL, YEAR)).thenReturn(List.of());
    when(repository.findRsmrClasses(MILL, YEAR)).thenReturn(List.of());
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
  }

  private static RoadConstructionReportEntity page(int id, String tsa, String tsb, String tfl) {
    return new RoadConstructionReportEntity(
        id, YEAR, MILL, "10", "2021-06", "North Division", "RNI", tsb, tsa, tfl, 0);
  }

  private static RoadConstructionReportDetailEntity detail(int id, int pageId, String name) {
    return new RoadConstructionReportDetailEntity(
        id,
        pageId,
        name,
        null,
        "P",
        null,
        null,
        null,
        8801,
        null,
        null,
        new BigDecimal("12.500"),
        "N",
        null,
        null,
        null,
        null,
        "C",
        null,
        "GR",
        new BigDecimal("3.000"),
        null,
        null,
        null,
        null,
        null,
        0);
  }

  @Nested
  @DisplayName("editable — SUBMITTER row only (AD-9, deviation (g))")
  class EditableMatrix {

    @ParameterizedTest(name = "track={0}, callerMayEdit={1} -> editable={2}")
    @CsvSource({
      "D,    true,  true",
      "D,    false, false",
      "S,    true,  false",
      "S,    false, false",
      "V,    true,  false",
      "V,    false, false",
      "O,    true,  false",
    })
    void followsTrackStatusAndPermission(String track, boolean mayEdit, boolean expected) {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of(track));
      assertThat(service.getSchedule10(MILL, YEAR, mayEdit).editable()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a missing context row yields a null track and editable:false")
    void missingTrackStatusIsNotEditable() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.empty());
      Schedule10Response response = service.getSchedule10(MILL, YEAR, true);
      assertThat(response.trackStatus()).isNull();
      assertThat(response.editable()).isFalse();
    }
  }

  @Nested
  @DisplayName("assembly")
  class Assembly {

    @Test
    @DisplayName("details nest under their own page, and numbering is positional per page")
    void nestsDetailsAndNumbersPositionally() {
      when(repository.findPages(MILL, YEAR))
          .thenReturn(List.of(page(8900, "01", "01A", null), page(8901, "16", "16G", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(
              List.of(
                  detail(8910, 8900, "Mainline A"),
                  detail(8911, 8900, "Spur B"),
                  detail(8912, 8901, "Regex Road")));

      List<ConstructionPage> pages = service.getSchedule10(MILL, YEAR, true).pages();

      assertThat(pages).hasSize(2);
      assertThat(pages.get(0).pageNumber()).isEqualTo(1);
      assertThat(pages.get(0).roadDetailCount()).isEqualTo(2);
      assertThat(pages.get(0).roadDetails())
          .extracting(RoadDetail::roadDetailId)
          .containsExactly(8910, 8911);
      // rowNumber restarts at 1 on each page — it is positional within the page, not global.
      assertThat(pages.get(0).roadDetails())
          .extracting(RoadDetail::rowNumber)
          .containsExactly(1, 2);
      assertThat(pages.get(1).pageNumber()).isEqualTo(2);
      assertThat(pages.get(1).roadDetails()).extracting(RoadDetail::rowNumber).containsExactly(1);
      assertThat(pages.get(1).roadDetails().get(0).roadDetailLabel())
          .isEqualTo("Road #1, Regex Road");
    }

    @Test
    @DisplayName("a page with no details serves count 0 and an empty list, not null")
    void pageWithoutDetailsServesZero() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8906, "01", "01A", null)));

      ConstructionPage page = service.getSchedule10(MILL, YEAR, true).pages().get(0);

      assertThat(page.roadDetailCount()).isZero();
      assertThat(page.roadDetails()).isEmpty();
    }

    @Test
    @DisplayName("Road Group is derived per page and blank when unmapped")
    void derivesRoadGroupPerPage() {
      when(repository.findPages(MILL, YEAR))
          .thenReturn(
              List.of(
                  page(8900, "01", "01A", null), // -> "11"
                  page(8902, null, null, "08"), // -> "10" via the TFL table
                  page(8903, "99", "99A", null))); // -> unmapped

      List<ConstructionPage> pages = service.getSchedule10(MILL, YEAR, true).pages();

      assertThat(pages.get(0).roadGroup()).isEqualTo("11");
      assertThat(pages.get(1).roadGroup()).isEqualTo("10");
      assertThat(pages.get(2).roadGroup()).isNull();
    }

    @Test
    @DisplayName("a whitespace-only TFL takes the TFL branch and yields blank, as legacy does")
    void whitespaceTflTakesTheTflBranch() {
      // Legacy tests `tflNumberCode != null` against the RAW column, so a whitespace-only TFL —
      // legal in VARCHAR2(2) — enters the TFL branch, misses every case, and yields blank. An
      // earlier revision trimmed it to null first, which rerouted to the TSA table and served
      // Road Group "11" where legacy serves nothing. Parity restored at code review 2026-08-17.
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", "  ")));

      ConstructionPage page = service.getSchedule10(MILL, YEAR, true).pages().get(0);

      // The raw value is served, not normalized away.
      assertThat(page.tflNumberCode()).isEqualTo("  ");
      // TFL-first routing wins even though the value is blank, so no Road Group is derived.
      assertThat(page.roadGroup()).isNull();
      // And the label carries the raw value, not a "-" placeholder.
      assertThat(page.pageLabel()).endsWith(", TFL:  ");
    }
  }

  @Nested
  @DisplayName("silent-data-loss guards on the cost map")
  class CostMapGuards {

    @Test
    @DisplayName("duplicate rows for one (detail, item): the LAST row wins, exactly as legacy")
    void duplicateCostRowsTakeTheLastRow() {
      // Nothing enforces one row per (detail, item) — no unique constraint, and delivery holds zero
      // Schedule 10 cost rows so the invariant has never been observed against data.
      //
      // Legacy ASSIGNS rather than accumulates: Schedule10DAO:556-600 loops the cost-detail Set and
      // calls setSubGradeActualCost(...) and its eleven siblings, so a second row for the same item
      // overwrites the first. Story 11.1 summed instead — it looked safer, since it conserves the
      // money — but it was an unrecorded deviation AND it made a duplicated value unfixable through
      // the API, because the write path's UPDATE touches every duplicate row and the read then
      // re-summed them. Corrected to legacy at code review 2026-08-18.
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(
              List.of(
                  new CostLineRow(8910, 20, new BigDecimal("100000")),
                  new CostLineRow(8910, 20, new BigDecimal("50000"))));

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      // 50000, not 150000: the second row wins outright. The cost query's ORDER BY makes which row
      // that is deterministic here — legacy iterates a HashSet, so there it is arbitrary.
      assertThat(detail.subGrade().actualCost()).isEqualByComparingTo("50000");
      assertThat(detail.subGrade().totalCosts()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("a duplicated cost is CORRECTABLE: resubmitting one value settles the read")
    void duplicateCostIsCorrectable() {
      // The point of last-row-wins rather than summing. The write path's UPDATE is unbounded, so a
      // resubmitted figure lands on every duplicate row; with summing the read multiplied it back
      // up
      // and the value could never be corrected through the API. Both rows now carry 7000.
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(
              List.of(
                  new CostLineRow(8910, 20, new BigDecimal("7000")),
                  new CostLineRow(8910, 20, new BigDecimal("7000"))));

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      assertThat(detail.subGrade().actualCost()).isEqualByComparingTo("7000");
    }

    @Test
    @DisplayName("a cost row with an unrouted ordinal is excluded, not misattributed")
    void unroutedCostItemIsExcluded() {
      // Item 99 belongs to no substructure. Legacy throws; we exclude and log, because refusing to
      // render a whole report screen is worse for the licensee than rendering with a warning.
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(
              List.of(
                  new CostLineRow(8910, 20, new BigDecimal("150000")),
                  new CostLineRow(8910, 99, new BigDecimal("777777"))));

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      // The stray amount must not appear anywhere, and must not inflate any total.
      assertThat(detail.subGrade().actualCost()).isEqualByComparingTo("150000");
      assertThat(detail.subGrade().totalCosts()).isEqualByComparingTo("150000");
      assertThat(detail.stabilizing().total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a stored NULL cost assembles cleanly, renders blank, and counts as zero")
    void nullCostRowIsNotAnError() {
      // COST is nullable, and legacy writes NULL for a cost left blank
      // (Schedule10DAO:722 stores intValueExact() or null), so this is ordinary data rather than a
      // defect. A stored NULL must be indistinguishable from an absent row: the individual field is
      // omitted while the derived total coerces it to zero.
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(
              List.of(
                  new CostLineRow(8910, 20, null),
                  new CostLineRow(8910, 22, new BigDecimal("40000"))));

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      assertThat(detail.subGrade().actualCost()).isNull();
      assertThat(detail.subGrade().totalCosts()).isEqualByComparingTo("0");
      assertThat(detail.subGrade().total()).isEqualByComparingTo("0");
      // A sibling ordinal on the same detail must still resolve normally.
      assertThat(detail.stabilizing().actualCost()).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("duplicate rows where one cost is NULL keep the stored value")
    void duplicateCostRowsWithOneNullKeepTheValue() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(
              List.of(
                  new CostLineRow(8910, 20, null),
                  new CostLineRow(8910, 20, new BigDecimal("150000"))));

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      // Legacy's sum rule: one non-null term makes the sum non-null, so the money survives.
      assertThat(detail.subGrade().actualCost()).isEqualByComparingTo("150000");
      assertThat(detail.subGrade().totalCosts()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("duplicate rows that are BOTH NULL stay blank rather than becoming zero")
    void duplicateNullCostRowsStayBlank() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(List.of(new CostLineRow(8910, 20, null), new CostLineRow(8910, 20, null)));

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      // All terms null keeps the field blank; the total still coerces to zero for display.
      assertThat(detail.subGrade().actualCost()).isNull();
      assertThat(detail.subGrade().totalCosts()).isEqualByComparingTo("0");
    }
  }

  @Nested
  @DisplayName("cost-line routing by legacy ordinal (BR-08)")
  class CostRouting {

    @Test
    @DisplayName("each ordinal lands in its own substructure field across all four subcategories")
    void routesEveryOrdinal() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(
              List.of(
                  new CostLineRow(8910, 20, new BigDecimal("150000")), // sub-grade actual   (sub 1)
                  new CostLineRow(8910, 3, new BigDecimal("-5000")), // sub-grade TtT      (sub 1)
                  new CostLineRow(8910, 5, new BigDecimal("2000")), // other TtT transfer (sub 3)
                  new CostLineRow(8910, 7, new BigDecimal("1000")), // less bridges       (sub 1)
                  new CostLineRow(8910, 6, new BigDecimal("2000")), // less culverts      (sub 1)
                  new CostLineRow(8910, 8, new BigDecimal("3000")), // less landings      (sub 1)
                  new CostLineRow(8910, 11, new BigDecimal("4000")), // less overland      (sub 1)
                  new CostLineRow(8910, 4, new BigDecimal("5000")), // less other eng     (sub 3)
                  new CostLineRow(8910, 21, new BigDecimal("6000")), // less end haul      (sub 1)
                  new CostLineRow(8910, 22, new BigDecimal("40000")), // stabilizing actual (sub 2)
                  new CostLineRow(8910, 10, BigDecimal.ZERO), // stabilizing TtT    (sub 2)
                  new CostLineRow(8910, 9, BigDecimal.ZERO))); // stabilizing other  (sub 4)

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      assertThat(detail.subGrade().actualCost()).isEqualByComparingTo("150000");
      assertThat(detail.subGrade().ttTransfer()).isEqualByComparingTo("-5000");
      assertThat(detail.subGrade().otherTransfer()).isEqualByComparingTo("2000");
      assertThat(detail.subGrade().lessBridges()).isEqualByComparingTo("1000");
      assertThat(detail.subGrade().lessCulverts()).isEqualByComparingTo("2000");
      assertThat(detail.subGrade().lessLandings()).isEqualByComparingTo("3000");
      assertThat(detail.subGrade().lessOverland()).isEqualByComparingTo("4000");
      assertThat(detail.subGrade().lessOtherEng()).isEqualByComparingTo("5000");
      assertThat(detail.subGrade().lessEndHaul()).isEqualByComparingTo("6000");
      assertThat(detail.stabilizing().actualCost()).isEqualByComparingTo("40000");

      // Derived — the deduction total spans three subcategories, so a single-subcategory scan
      // would under-count it here.
      assertThat(detail.subGrade().totalCosts()).isEqualByComparingTo("147000");
      assertThat(detail.subGrade().totalDeductions()).isEqualByComparingTo("21000");
      assertThat(detail.subGrade().total()).isEqualByComparingTo("126000");
      assertThat(detail.subGrade().costPerLength()).isEqualByComparingTo("10080.00");
      assertThat(detail.stabilizing().total()).isEqualByComparingTo("40000");
      assertThat(detail.stabilizing().costPerLength()).isEqualByComparingTo("13333.33");
    }

    @Test
    @DisplayName("cost lines never leak between road details")
    void costLinesDoNotLeakBetweenDetails() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A"), detail(8911, 8900, "Spur B")));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(List.of(new CostLineRow(8910, 20, new BigDecimal("150000"))));

      List<RoadDetail> details =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails();

      assertThat(details.get(0).subGrade().actualCost()).isEqualByComparingTo("150000");
      // 8911 has no cost lines at all — the normal shape in real delivery data. Its individual
      // cost is blank, but its total is 0.00, not absent.
      assertThat(details.get(1).subGrade().actualCost()).isNull();
      assertThat(details.get(1).subGrade().total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a detail with no cost lines yields ZERO totals and blank individual costs")
    void noCostLinesYieldsZeroTotals() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8900, "01", "01A", null)));
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(List.of(detail(8910, 8900, "Mainline A")));

      RoadDetail detail =
          service.getSchedule10(MILL, YEAR, true).pages().get(0).roadDetails().get(0);

      // Individual lines stay null (rendered blank); the totals coerce to zero, per legacy
      // getCostValue (:1160-1168). This is the shape of every real delivery row.
      assertThat(detail.subGrade().actualCost()).isNull();
      assertThat(detail.subGrade().ttTransfer()).isNull();
      assertThat(detail.subGrade().totalCosts()).isEqualByComparingTo("0");
      assertThat(detail.subGrade().totalDeductions()).isEqualByComparingTo("0");
      assertThat(detail.subGrade().total()).isEqualByComparingTo("0");
      assertThat(detail.stabilizing().total()).isEqualByComparingTo("0");
      // The fixture detail() carries SUB_GRADE_LENGTH 12.500, so 0.00 / 12.500 = 0.00.
      assertThat(detail.subGrade().costPerLength()).isEqualByComparingTo("0");
      // Material total is int arithmetic and is always present.
      assertThat(detail.materialComposition().totalPct()).isZero();
    }
  }
}
