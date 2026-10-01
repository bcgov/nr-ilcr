package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatus.DetailOutcome;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatus.Issue;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatus.Outcome;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatus.PageOutcome;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.BecClassificationRow;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.MaterialCompositionEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.PageEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.RoadEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.StabilizingEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.SubGradeEntry;
import ca.bc.gov.nrs.ilcr.security.EditableStatuses;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@code Schedule10Service.checkStatus} (the SCREEN path) and {@code
 * checkStatusStored} (bcgov/nr-ilcr#359, group C) — mocked repository, real assembler, real
 * evaluator.
 *
 * <p>The stored fixture is one clean TSA page holding one clean road, so the stored verdict is MET
 * and every issue a test sees was put there by the body. Each test posts the open page panel or
 * road editor the way the client does — every checked field, blank as null — and asserts the
 * verdict follows the screen: an unsaved clear appears, an unsaved fix disappears, labels carry the
 * on-screen values, derived totals are recomputed, and a new or unknown id is ignored.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Schedule10Service — Check Status against the screen (#359)")
class Schedule10CheckStatusServiceTest {

  private static final long MILL = 710L;
  private static final int YEAR = 2021;
  private static final int PAGE_ID = 8900;
  private static final int ROAD_ID = 8910;
  private static final int BEC_STORED = 8801;
  private static final int BEC_OTHER = 8802;
  private static final int BEC_NO_SUBZONE = 8803;
  private static final String STORED_LABEL = "Page 1, Period: 2021-06, TSA: 01, SB: 01A, TFL:-";

  @Mock private Schedule10Repository repository;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  private final OriginalValues originalValues = OriginalValuesFixture.real();

  private Schedule10Service service;

  @BeforeEach
  void setUp() {
    service = new Schedule10Service(repository, originalValues, costSnapshots);
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findCostLines(MILL, YEAR)).thenReturn(List.of());
    when(repository.findReferencedBecClassifications(MILL, YEAR)).thenReturn(List.of());
    when(repository.findOfferableBecClassifications())
        .thenReturn(
            List.of(
                new BecClassificationRow(BEC_STORED, "ICH", "dw", "1", null),
                new BecClassificationRow(BEC_OTHER, "SBS", "mk", "1", null),
                new BecClassificationRow(BEC_NO_SUBZONE, "BAFA", null, null, null)));
    storedPage("North Division");
    when(repository.findRoadDetails(MILL, YEAR)).thenReturn(List.of(storedRoad()));
  }

  private void storedPage(String division) {
    when(repository.findPages(MILL, YEAR))
        .thenReturn(
            List.of(
                new RoadConstructionReportEntity(
                    PAGE_ID, YEAR, MILL, "10", "2021-06", division, "RNI", "01A", "01", null, 0)));
  }

  /** A clean stored road: ballast N, material 100%, every figure in range, no cost rows. */
  private static RoadConstructionReportDetailEntity storedRoad() {
    return new RoadConstructionReportDetailEntity(
        ROAD_ID,
        PAGE_ID,
        "Mainline A",
        25,
        "P",
        20,
        10,
        40,
        BEC_STORED,
        20,
        10,
        new BigDecimal("12.500"),
        "N",
        null,
        null,
        null,
        null,
        "N",
        new BigDecimal("6.5"),
        "NA",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "3",
        null,
        0);
  }

  /** The stored page as the client's open panel sends it, unedited. */
  private static PageEntry storedPageOnScreen() {
    return new PageEntry(PAGE_ID, "North Division", "2021-06", "01", "01A", null);
  }

  private static SubGradeEntry storedSubGradeOnScreen() {
    return new SubGradeEntry(
        new BigDecimal("12.500"),
        new BigDecimal("6.5"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static StabilizingEntry storedStabilizingOnScreen() {
    return new StabilizingEntry(
        "N",
        "NA",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        null,
        null);
  }

  private static MaterialCompositionEntry storedMaterialOnScreen() {
    return new MaterialCompositionEntry(10, 20, 40, 20, 10);
  }

  /** The stored road as the client's open editor sends it, unedited. */
  private static RoadEntry storedRoadOnScreen() {
    return road(
        "Mainline A",
        BEC_STORED,
        25,
        storedSubGradeOnScreen(),
        storedStabilizingOnScreen(),
        storedMaterialOnScreen());
  }

  private static RoadEntry road(
      String name,
      Integer becId,
      Integer sideSlope,
      SubGradeEntry subGrade,
      StabilizingEntry stabilizing,
      MaterialCompositionEntry material) {
    return new RoadEntry(
        PAGE_ID, ROAD_ID, name, becId, "3", sideSlope, subGrade, stabilizing, material);
  }

  private Outcome check(PageEntry page, RoadEntry road) {
    return service.checkStatus(MILL, YEAR, new Schedule10CheckRequest(page, road));
  }

  private static PageOutcome onlyPage(Outcome outcome) {
    assertThat(outcome.pages()).hasSize(1);
    return outcome.pages().get(0);
  }

  private static DetailOutcome onlyRoad(Outcome outcome) {
    PageOutcome page = onlyPage(outcome);
    assertThat(page.roadDetails()).hasSize(1);
    return page.roadDetails().get(0);
  }

  private static List<String> pageFields(Outcome outcome) {
    return onlyPage(outcome).issues().stream().map(Issue::field).toList();
  }

  private static List<String> roadFields(Outcome outcome) {
    return onlyRoad(outcome).issues().stream().map(Issue::field).toList();
  }

  private static Issue roadIssue(Outcome outcome, String field) {
    return onlyRoad(outcome).issues().stream()
        .filter(issue -> issue.field().equals(field))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName(
      "fixture guard: the stored schedule is MET, so every issue below comes from the body")
  void storedFixtureIsMet() {
    assertThat(service.checkStatusStored(MILL, YEAR).met()).isTrue();
  }

  @Nested
  @DisplayName("no editor, or an editor the server cannot place")
  class Ignored {

    @Test
    @DisplayName("an all-null body is the stored verdict, byte for byte")
    void allNullBodyIsTheStoredVerdict() {
      storedPage(null);

      Outcome screen = check(null, null);

      assertThat(screen).isEqualTo(service.checkStatusStored(MILL, YEAR));
      assertThat(screen.met()).isFalse();
    }

    @Test
    @DisplayName("the unedited open page and road give the stored verdict")
    void uneditedEditorsAreTheStoredVerdict() {
      storedPage(null);

      assertThat(check(new PageEntry(PAGE_ID, null, "2021-06", "01", "01A", null), null))
          .isEqualTo(service.checkStatusStored(MILL, YEAR));
      assertThat(check(null, storedRoadOnScreen()))
          .isEqualTo(service.checkStatusStored(MILL, YEAR));
    }

    @Test
    @DisplayName("a NEW page or road (null id) is not evaluated — legacy never checked it")
    void newPageOrRoadIsNotEvaluated() {
      PageEntry newPage = new PageEntry(null, null, null, null, null, null);
      RoadEntry newRoad = new RoadEntry(PAGE_ID, null, null, null, null, null, null, null, null);

      Outcome outcome = check(newPage, newRoad);

      assertThat(outcome).isEqualTo(service.checkStatusStored(MILL, YEAR));
      assertThat(outcome.met()).isTrue();
    }

    @Test
    @DisplayName("an unknown page id, or a road under the wrong page, is ignored")
    void unknownIdsAreIgnored() {
      PageEntry unknownPage = new PageEntry(4242, null, null, null, null, null);
      RoadEntry wrongPage = new RoadEntry(4242, ROAD_ID, null, null, null, null, null, null, null);
      RoadEntry unknownRoad =
          new RoadEntry(PAGE_ID, 4242, null, null, null, null, null, null, null);

      assertThat(check(unknownPage, wrongPage).met()).isTrue();
      assertThat(check(null, unknownRoad).met()).isTrue();
    }
  }

  @Nested
  @DisplayName("page level")
  class PageLevel {

    @Test
    @DisplayName("unsaved clear: Period emptied reports Period Surveyed, labelled from the screen")
    void periodClearedIsReported() {
      Outcome outcome =
          check(new PageEntry(PAGE_ID, "North Division", null, "01", "01A", null), null);

      assertThat(pageFields(outcome)).containsExactly("constructionPeriod");
      assertThat(onlyPage(outcome).issues().get(0).label())
          .isEqualTo("Page 1, Period: null, TSA: 01, SB: 01A, TFL:- Period Surveyed");
      assertThat(outcome.met()).isFalse();
    }

    @Test
    @DisplayName("a whitespace-only field is blank, as Save stores it")
    void whitespaceIsBlank() {
      Outcome outcome = check(new PageEntry(PAGE_ID, "   ", "2021-06", "01", "01A", null), null);

      assertThat(pageFields(outcome)).containsExactly("divisionName");
    }

    @Test
    @DisplayName("unsaved fix: a stored-missing Division typed on screen clears its line")
    void divisionTypedOnScreenClearsTheLine() {
      storedPage(null);
      assertThat(service.checkStatusStored(MILL, YEAR).met()).isFalse();

      Outcome outcome = check(storedPageOnScreen(), null);

      assertThat(outcome.met()).isTrue();
    }

    @Test
    @DisplayName("TSA → TFL with TFL # blank reports TFL #, not Supply Block")
    void switchToTflWithBlankNumberReportsTflNumber() {
      Outcome outcome =
          check(new PageEntry(PAGE_ID, "North Division", "2021-06", "TFL", "01A", null), null);

      assertThat(pageFields(outcome)).containsExactly("tflNumberCode");
      // The write path's rule drops the supply block on a TFL page, so the label shows "-" for SB
      // and legacy's un-guarded "TSA: null".
      assertThat(onlyPage(outcome).issues().get(0).label())
          .isEqualTo("Page 1, Period: 2021-06, TSA: null, SB: -, TFL:- TFL #");
    }

    @Test
    @DisplayName("TSA → TFL with TFL # typed is complete, and the label carries the TFL")
    void switchToTflWithNumberIsComplete() {
      Outcome outcome =
          check(new PageEntry(PAGE_ID, "North Division", "2021-06", "TFL", null, "01"), null);

      assertThat(outcome.met()).isTrue();
    }

    @Test
    @DisplayName("the page label carries the on-screen Period, TSA and Supply Block")
    void labelCarriesOnScreenValues() {
      Outcome outcome = check(new PageEntry(PAGE_ID, null, "2021-09", "02", "02B", null), null);

      String label = "Page 1, Period: 2021-09, TSA: 02, SB: 02B, TFL:-";
      assertThat(onlyPage(outcome).pageLabel()).isEqualTo(label);
      assertThat(onlyPage(outcome).issues().get(0).label()).isEqualTo(label + " Division");
      // The road rules are prefixed with the page label, so they follow it too.
      RoadEntry sideSlopeCleared =
          road(
              "Mainline A",
              BEC_STORED,
              null,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());
      Outcome both =
          check(new PageEntry(PAGE_ID, null, "2021-09", "02", "02B", null), sideSlopeCleared);
      assertThat(roadIssue(both, "sideSlopePct").label())
          .isEqualTo(label + ", Road #1, Mainline A Side Slope (%)");
    }
  }

  @Nested
  @DisplayName("road level")
  class RoadLevel {

    @Test
    @DisplayName("unsaved clear: Road Name emptied reports Road Name, and the road label follows")
    void roadNameClearedIsReported() {
      RoadEntry cleared =
          road(
              null,
              BEC_STORED,
              25,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());

      Outcome outcome = check(null, cleared);

      assertThat(roadFields(outcome)).containsExactly("roadName");
      assertThat(roadIssue(outcome, "roadName").label()).isEqualTo(STORED_LABEL + " Road Name");
      assertThat(onlyRoad(outcome).roadDetailLabel()).isEqualTo("Road #1, null");
    }

    @Test
    @DisplayName("the road label carries the on-screen name")
    void roadLabelCarriesOnScreenName() {
      RoadEntry renamed =
          road(
              "Spur 7",
              BEC_STORED,
              150,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());

      Outcome outcome = check(null, renamed);

      assertThat(onlyRoad(outcome).roadDetailLabel()).isEqualTo("Road #1, Spur 7");
      assertThat(roadIssue(outcome, "sideSlopePct").label())
          .isEqualTo(STORED_LABEL + ", Road #1, Spur 7 Side Slope (%)");
    }

    @Test
    @DisplayName("Side Slope 120 is out of range")
    void sideSlopeOutOfRange() {
      RoadEntry edited =
          road(
              "Mainline A",
              BEC_STORED,
              120,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());

      Issue issue = roadIssue(check(null, edited), "sideSlopePct");

      assertThat(issue.messageKey()).isEqualTo("invalidRangeErrorMsg");
      assertThat(issue.args()).containsExactly("0", "100");
    }

    @Test
    @DisplayName("null stays null: a cleared Side Slope is missing, never judged as 0")
    void clearedSideSlopeIsMissing() {
      RoadEntry cleared =
          road(
              "Mainline A",
              BEC_STORED,
              null,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());

      Issue issue = roadIssue(check(null, cleared), "sideSlopePct");

      assertThat(issue.messageKey()).isEqualTo("missingRequiredFieldMsg");
    }

    @Test
    @DisplayName("method C with Type blank reports the type and every blank required figure")
    void crushedWithTypeBlank() {
      StabilizingEntry crushed =
          new StabilizingEntry(
              "C",
              null,
              new BigDecimal("1.000"),
              new BigDecimal("5.0"),
              new BigDecimal("0.3"),
              new BigDecimal("2.0"),
              null,
              null,
              null);
      RoadEntry edited =
          road(
              "Mainline A",
              BEC_STORED,
              25,
              storedSubGradeOnScreen(),
              crushed,
              storedMaterialOnScreen());

      Outcome outcome = check(null, edited);

      assertThat(roadFields(outcome))
          .containsExactly(
              "ballastMaterialCode",
              "stabilizingActualCost",
              "stabilizingTtTransfer",
              "stabilizingOtherTransfer");
      assertThat(roadIssue(outcome, "stabilizingActualCost").messageKey())
          .isEqualTo("missingRequiredFieldMsg");
      assertThat(roadIssue(outcome, "ballastMaterialCode").label())
          .isEqualTo(STORED_LABEL + ", Road #1, Mainline A Additional Stabilizing Type");
    }

    @Test
    @DisplayName("method C cleared on screen drops the stabilizing requirements again")
    void notCrushedOnScreenIsNotRequired() {
      StabilizingEntry blank =
          new StabilizingEntry("N", null, null, null, null, null, null, null, null);
      RoadEntry edited =
          road(
              "Mainline A",
              BEC_STORED,
              25,
              storedSubGradeOnScreen(),
              blank,
              storedMaterialOnScreen());

      assertThat(check(null, edited).met()).isTrue();
    }

    @Test
    @DisplayName("derived values are recomputed from the screen: material total and $/km")
    void derivedValuesAreRecomputed() {
      SubGradeEntry steep =
          new SubGradeEntry(
              new BigDecimal("0.001"),
              new BigDecimal("6.5"),
              new BigDecimal("1000000"),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      RoadEntry edited =
          road(
              "Mainline A",
              BEC_STORED,
              25,
              steep,
              storedStabilizingOnScreen(),
              new MaterialCompositionEntry(10, 20, 40, 20, null));

      Outcome outcome = check(null, edited);

      // 10+20+40+20 with Organic blank totals 90 — the blank counts as zero in the TOTAL only.
      Issue total = roadIssue(outcome, "materialTypeTotal");
      assertThat(total.messageKey()).isEqualTo("invalidTotalErrorMsg");
      assertThat(roadFields(outcome)).doesNotContain("organicPct");
      // 1,000,000 over 0.001 km is 1e9 $/km, outside the 8-digit band; nothing stored says so.
      assertThat(roadFields(outcome)).contains("subGradeCostPerLength");
      assertThat(roadFields(outcome)).doesNotContain("subGradeTotalCosts", "subGradeTotal");
    }

    @Test
    @DisplayName("BEC changed on screen: Sub Zone comes from the NEW catalogue row (D3)")
    void becChangedUsesTheNewRow() {
      RoadEntry noSubzone =
          road(
              "Mainline A",
              BEC_NO_SUBZONE,
              25,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());
      RoadEntry otherBec =
          road(
              "Mainline A",
              BEC_OTHER,
              25,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());

      assertThat(roadFields(check(null, noSubzone))).containsExactly("subzone");
      assertThat(check(null, otherBec).met()).isTrue();
    }

    @Test
    @DisplayName("BEC cleared on screen reports Sub Zone and BEC Zone rather than failing")
    void becClearedIsReported() {
      RoadEntry cleared =
          road(
              "Mainline A",
              null,
              25,
              storedSubGradeOnScreen(),
              storedStabilizingOnScreen(),
              storedMaterialOnScreen());

      assertThat(roadFields(check(null, cleared))).containsExactly("subzone", "becClassification");
    }

    @Test
    @DisplayName("unsaved fix: a stored out-of-range figure corrected on screen clears its line")
    void storedIssueFixedOnScreen() {
      RoadConstructionReportDetailEntity steep = storedRoad();
      when(repository.findRoadDetails(MILL, YEAR))
          .thenReturn(
              List.of(
                  new RoadConstructionReportDetailEntity(
                      steep.roadConstructionReprtDtlId(),
                      steep.roadConstructionReprtId(),
                      steep.roadName(),
                      140,
                      steep.ilcrRoadLifetimeCode(),
                      steep.rippableRockPct(),
                      steep.solidRockPct(),
                      steep.coarseMaterialPct(),
                      steep.becbiogeoCatalogueId(),
                      steep.fineMaterialPct(),
                      steep.organicMaterialPct(),
                      steep.subGradeLength(),
                      steep.detailEngineeringCostInd(),
                      null,
                      null,
                      null,
                      null,
                      steep.ilcrRoadBallastMethodCode(),
                      steep.subGradeSurfaceWidth(),
                      steep.ilcrRoadBallastMaterlCode(),
                      steep.stabilizingLength(),
                      steep.stabilizingSurfaceWidth(),
                      steep.stabilizingDepth(),
                      steep.stabilizingDistanceToSource(),
                      steep.relSoilMoistRgmClsCode(),
                      null,
                      0)));
      assertThat(service.checkStatusStored(MILL, YEAR).met()).isFalse();

      assertThat(check(null, storedRoadOnScreen()).met()).isTrue();
    }
  }

  @Test
  @DisplayName("stored path: the evaluator over the GET's own document, unchanged")
  void storedPathEvaluatesTheServedDocument() {
    storedPage(null);

    assertThat(service.checkStatusStored(MILL, YEAR))
        .isEqualTo(
            Schedule10CheckStatus.evaluate(
                service.getSchedule10(MILL, YEAR, EditableStatuses.NONE)));
  }
}
