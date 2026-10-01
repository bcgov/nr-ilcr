package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckFieldIssue;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckRequest.SampleEntry;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckResult;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleCheckResult;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 8 Check Status evaluation (Story 14.6) — mocked repository. Covers the
 * all-met MET outcome and a representative ISSUES page (missing Contact + a sample with percent ≠
 * 100), plus the read-only contract. End-to-end coverage of every rule is in {@link
 * Schedule8CheckStatusIT}.
 *
 * <p>Since #359 (group C) both scopes take the SCREEN ({@code checkStatus}, {@code
 * checkStatusPage}); the schedule scope also has a stored path ({@code checkStatusStored}) for the
 * sweep. The nested classes cover the screen paths; the tests above them cover the stored verdict —
 * {@code checkStatusStored}, and {@code checkStatusPage} with no sample panel open — which keeps
 * its pre-#359 behaviour byte for byte.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8CheckStatusServiceTest {

  private static final long MILL = 600L;
  private static final int YEAR = 2021;

  @Mock private Schedule8Repository repository;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule8Service service;

  @BeforeEach
  void stubLabelMapsAndTrack() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.costItemSubcategories()).thenReturn(Map.of());
    lenient().when(repository.supportCentreLabels()).thenReturn(Map.of());
    lenient().when(repository.regionLabels()).thenReturn(Map.of());
    lenient().when(repository.becZoneLabels()).thenReturn(Map.of());
    lenient().when(repository.tsaNumberLabels()).thenReturn(Map.of());
    lenient().when(repository.supplyBlockLabels()).thenReturn(Map.of());
    lenient().when(repository.tflNumberLabels()).thenReturn(Map.of());
    lenient().when(repository.skidTypeLabels()).thenReturn(Map.of());
    lenient().when(repository.costTypeLabels()).thenReturn(Map.of());
  }

  private static TreeToTruckReportEntity page(String contact, String phone) {
    return page(8970, "TSA5", null, contact, phone);
  }

  /** A page with the identifiers the legacy title is built from (#461): TSA and cutting permit. */
  private static TreeToTruckReportEntity page(
      int id, String tsa, String cuttingPermit, String contact, String phone) {
    return new TreeToTruckReportEntity(
        id,
        "SC1",
        "R1",
        "BZ1",
        tsa,
        "B",
        null,
        cuttingPermit,
        "L600",
        "Div",
        contact,
        phone,
        "c",
        0);
  }

  private static TreeToTruckDetailReportEntity metSample() {
    return new TreeToTruckDetailReportEntity(
        8971,
        8970,
        "C",
        "CB",
        100,
        0,
        0,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        null,
        "N",
        "N",
        null,
        500,
        0,
        new BigDecimal("20.00"),
        0);
  }

  @Test
  void allFieldsPresent_returnsMet() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
    Schedule8CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertEquals("MET", result.outcome());
    assertTrue(result.pages().get(0).met());
    assertTrue(result.pages().get(0).samples().get(0).met());
    // #461: every result says which page and sample it is, in the words the screen uses.
    Schedule8PageCheckResult page = result.pages().get(0);
    assertEquals(1, page.pageNumber());
    assertEquals("Page # 1  -TSA: TSA5 -CP:  - ", page.pageLabel()); // no cutting permit -> " - "
    assertEquals(1, page.samples().get(0).sampleNumber());
    assertEquals("Sample # 1 - C", page.samples().get(0).sampleLabel());
  }

  @Test
  void missingContact_returnsIssuesWithContactFlag() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(null, "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
    Schedule8CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertEquals("ISSUES", result.outcome());
    assertTrue(
        result.pages().get(0).issues().stream()
            .map(Schedule8CheckFieldIssue::field)
            .anyMatch("Contact"::equals));
  }

  @Test
  void missingDivision_returnsIssuesWithDivisionFlag() {
    // Division (10th field) blank → flagged (legacy Schedule8CheckStatus.validateTtTReport requires
    // it).
    TreeToTruckReportEntity noDivision =
        new TreeToTruckReportEntity(
            8970, "SC1", "R1", "BZ1", "TSA5", "B", null, null, "L600", null, "Pat", "250", "c", 0);
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(noDivision));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
    Schedule8CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertEquals("ISSUES", result.outcome());
    assertTrue(
        result.pages().get(0).issues().stream()
            .map(Schedule8CheckFieldIssue::field)
            .anyMatch("Division"::equals));
  }

  @Test
  void percentNotHundred_flagsSkiddingYarding() {
    // Sample with only 50% skidding -> percentTotal 50 != 100 -> flagged at Check Status (S16
    // half).
    TreeToTruckDetailReportEntity sample =
        new TreeToTruckDetailReportEntity(
            8971,
            8970,
            "C",
            "CB",
            50,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            "N",
            "N",
            null,
            500,
            0,
            new BigDecimal("20.00"),
            0);
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(sample));
    Schedule8CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertEquals("ISSUES", result.outcome());
    assertTrue(
        result.pages().get(0).samples().get(0).issues().stream()
            .map(Schedule8CheckFieldIssue::field)
            .anyMatch("Skidding/Yarding"::equals));
  }

  @Test
  void checkStatusPage_numbersThePageByItsPositionInTheWholeDocument() {
    // Two pages; the single-page scope on the SECOND must still call it "Page # 2" — the number the
    // Page Summary shows — not "Page # 1" of a one-page list (#461).
    when(repository.findPages(MILL, YEAR))
        .thenReturn(List.of(page("Pat", "250"), page(8972, "TSA5", "cp123", "Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));

    Schedule8CheckStatusResponse result =
        service.checkStatusPage(MILL, YEAR, 8972, samplePanel(null));

    assertEquals(1, result.pages().size());
    Schedule8PageCheckResult page = result.pages().get(0);
    assertEquals(8972, page.id());
    assertEquals(2, page.pageNumber());
    assertEquals("Page # 2  -TSA: TSA5 -CP: cp123", page.pageLabel());
    // The page has no sample, so the scope reports that — and nothing about page 1.
    assertEquals("ISSUES", result.outcome());
    assertTrue(
        page.issues().stream().map(Schedule8CheckFieldIssue::field).anyMatch("Sample"::equals));
  }

  @Test
  void checkStatusPage_unknownPage_isVacuouslyMetWithNoPages() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));

    Schedule8CheckStatusResponse result =
        service.checkStatusPage(MILL, YEAR, 4242, samplePanel(null));

    assertEquals("MET", result.outcome());
    assertTrue(result.pages().isEmpty());
  }

  @Test
  void labels_nullGuardsMatchTheScreen() {
    // The screen renders a null TSA as empty and a blank cutting permit as " - "
    // (schedule8/index.tsx
    // pageLabel); legacy printed "null". The wire label follows the screen so the two read alike.
    when(repository.findPages(MILL, YEAR))
        .thenReturn(List.of(page(8970, null, "   ", "Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(sampleWithContract(null)));

    Schedule8PageCheckResult page = service.checkStatusStored(MILL, YEAR).pages().get(0);

    assertEquals("Page # 1  -TSA:  -CP:  - ", page.pageLabel());
    assertEquals("Sample # 1 - ", page.samples().get(0).sampleLabel());
  }

  private static TreeToTruckDetailReportEntity sampleWithContract(String contractId) {
    TreeToTruckDetailReportEntity s = metSample();
    return new TreeToTruckDetailReportEntity(
        s.id(),
        s.reportId(),
        contractId,
        s.cutBlock(),
        s.groundBasePct(),
        s.grapplePct(),
        s.skylinePct(),
        s.highleadPct(),
        s.helicopterPct(),
        s.otherSkiddingPct(),
        s.skylineSlopeDistance(),
        s.skylineSupportNumber(),
        s.supportAverageDistance(),
        s.cycleTime(),
        s.distance(),
        s.waterDumpDestinationInd(),
        s.uphillDirectionInd(),
        s.skidTypeCode(),
        s.coniferousVolume(),
        s.deciduousVolume(),
        s.originalRate(),
        s.revisionCount());
  }

  @Test
  void noPages_isVacuouslyMet() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
    assertEquals("MET", service.checkStatusStored(MILL, YEAR).outcome());
  }

  // ---------------------------------------------------------------------------------------------
  // #359 group C: the SCREEN paths. The stored fixture is one clean TSA page (8970) holding one
  // clean sample (8971), so the stored verdict is MET and every issue below was put there by the
  // body. Each test posts the open panel the way the client does — every checked field, blank as
  // null — and asserts the verdict follows the screen.
  // ---------------------------------------------------------------------------------------------

  /** Page 8970 as the client's open page panel sends it, unedited. */
  private static Schedule8CheckRequest.PageEntry storedPageOnScreen() {
    return new Schedule8CheckRequest.PageEntry(8970, "Div", "Pat", "250", "TSA5", null, "B", null);
  }

  /** Sample 8971 as the client's open sample panel sends it, unedited. */
  private static SampleEntry storedSampleOnScreen() {
    return new SampleEntry(
        8971, "C", "CB", 100, 0, 0, 0, 0, 0, null, null, null, 500, 0, new BigDecimal("20.00"));
  }

  private static Schedule8CheckRequest pagePanel(Schedule8CheckRequest.PageEntry page) {
    return new Schedule8CheckRequest(page);
  }

  private static Schedule8PageCheckRequest samplePanel(SampleEntry sample) {
    return new Schedule8PageCheckRequest(sample);
  }

  private static List<String> fields(List<Schedule8CheckFieldIssue> issues) {
    return issues.stream().map(Schedule8CheckFieldIssue::field).toList();
  }

  private void storedCleanPageAndSample() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
  }

  @Nested
  @DisplayName("#359 all-pages check — the open page panel over the stored pages")
  class PagePanel {

    @Test
    @DisplayName("no panel open, or the unedited panel, is exactly the stored verdict")
    void noPanelOrUneditedPanelIsTheStoredVerdict() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(null, "250")));
      when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
      Schedule8CheckStatusResponse stored = service.checkStatusStored(MILL, YEAR);

      assertEquals(stored, service.checkStatus(MILL, YEAR, pagePanel(null)));
      assertEquals(
          stored,
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8970, "Div", null, "250", "TSA5", null, "B", null))));
    }

    @Test
    @DisplayName("an unsaved Contact clear is reported")
    void contactClearedIsReported() {
      storedCleanPageAndSample();
      Schedule8CheckRequest.PageEntry s = storedPageOnScreen();

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      s.id(),
                      s.division(),
                      null,
                      s.phone(),
                      s.tsaNumber(),
                      s.tflNumber(),
                      s.supplyBlock(),
                      s.cuttingPermit())));

      assertEquals("ISSUES", result.outcome());
      assertEquals(List.of("Contact"), fields(result.pages().get(0).issues()));
      // Samples are always the stored ones: the page panel cannot move them.
      assertTrue(result.pages().get(0).samples().get(0).met());
    }

    @Test
    @DisplayName("a whitespace-only field is blank, as Save stores it")
    void whitespaceIsBlank() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8970, "   ", "Pat", "250", "TSA5", null, "B", null)));

      assertEquals(List.of("Division"), fields(result.pages().get(0).issues()));
    }

    @Test
    @DisplayName("a stored-missing Division typed on screen clears its line; the rest is unchanged")
    void storedMissingDivisionTypedClearsTheLine() {
      TreeToTruckReportEntity noDivision =
          new TreeToTruckReportEntity(
              8970, "SC1", "R1", "BZ1", "TSA5", "B", null, null, "L600", null, null, "250", "c", 0);
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(noDivision));
      when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
      assertEquals(
          List.of("Division", "Contact"),
          fields(service.checkStatusStored(MILL, YEAR).pages().get(0).issues()));

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8970, "Typed Div", null, "250", "TSA5", null, "B", null)));

      assertEquals(List.of("Contact"), fields(result.pages().get(0).issues()));
    }

    @Test
    @DisplayName("an unsaved TSA-to-TFL switch with TFL # blank reports TFL #, not Supply Block")
    void switchToTflWithBlankNumberReportsTflNumber() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8970, "Div", "Pat", "250", "TFL", null, null, null)));

      Schedule8PageCheckResult page = result.pages().get(0);
      assertEquals(List.of("TFL #"), fields(page.issues()));
      assertEquals("Page # 1  -TSA: TFL -CP:  - ", page.pageLabel());
    }

    @Test
    @DisplayName("a TFL # entered maps through Save's rule: TFL page, supply block ignored, MET")
    void switchToTflWithNumberIsComplete() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8970, "Div", "Pat", "250", "TFL", " 12 ", null, null)));

      assertEquals("MET", result.outcome());
      assertEquals("Page # 1  -TSA: TFL -CP:  - ", result.pages().get(0).pageLabel());
    }

    @Test
    @DisplayName("a TSA page with Supply Block cleared reports Supply Block")
    void supplyBlockClearedIsReported() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8970, "Div", "Pat", "250", "TSA5", null, null, null)));

      assertEquals(List.of("Supply Block"), fields(result.pages().get(0).issues()));
    }

    @Test
    @DisplayName("the page label carries the on-screen TSA and Cutting Permit")
    void labelCarriesOnScreenValues() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8970, null, "Pat", "250", "TSA9", null, "B", " cp9 ")));

      Schedule8PageCheckResult page = result.pages().get(0);
      assertEquals("Page # 1  -TSA: TSA9 -CP: cp9", page.pageLabel());
      assertEquals(List.of("Division"), fields(page.issues()));
    }

    @Test
    @DisplayName("a new page (null id) or an unknown id is not evaluated: the stored verdict")
    void newOrUnknownPageIsIgnored() {
      storedCleanPageAndSample();
      Schedule8CheckStatusResponse stored = service.checkStatusStored(MILL, YEAR);

      assertEquals(
          stored,
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      null, null, null, null, null, null, null, null))));
      assertEquals(
          stored,
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      4242, null, null, null, null, null, null, null))));
    }

    @Test
    @DisplayName("only the page with the panel's id moves; the others keep their stored verdict")
    void onlyTheMatchingPageMoves() {
      when(repository.findPages(MILL, YEAR))
          .thenReturn(List.of(page("Pat", "250"), page(8972, "TSA5", "cp123", "Pat", "250")));
      when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));

      Schedule8CheckStatusResponse result =
          service.checkStatus(
              MILL,
              YEAR,
              pagePanel(
                  new Schedule8CheckRequest.PageEntry(
                      8972, "Div", null, "250", "TSA5", null, "B", "cp123")));

      assertEquals(2, result.pages().size());
      assertEquals(List.of(), fields(result.pages().get(0).issues()));
      assertEquals(List.of("Contact", "Sample"), fields(result.pages().get(1).issues()));
      assertEquals("Page # 2  -TSA: TSA5 -CP: cp123", result.pages().get(1).pageLabel());
    }
  }

  @Nested
  @DisplayName("#359 single-page check — the open sample panel over the page's stored samples")
  class SamplePanel {

    @Test
    @DisplayName("no panel open, or the unedited panel, is exactly the stored verdict")
    void noPanelOrUneditedPanelIsTheStoredVerdict() {
      storedCleanPageAndSample();
      // No panel open is the stored single-page verdict; the unedited panel must not move it.
      Schedule8CheckStatusResponse stored =
          service.checkStatusPage(MILL, YEAR, 8970, samplePanel(null));

      assertEquals("MET", stored.outcome());
      assertEquals(
          stored, service.checkStatusPage(MILL, YEAR, 8970, samplePanel(storedSampleOnScreen())));
    }

    @Test
    @DisplayName("an unsaved Coniferous clear is reported as missing — never judged as 0")
    void coniferousClearedIsMissing() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(
              MILL,
              YEAR,
              8970,
              samplePanel(
                  new SampleEntry(
                      8971,
                      "C",
                      "CB",
                      100,
                      0,
                      0,
                      0,
                      0,
                      0,
                      null,
                      null,
                      null,
                      null,
                      0,
                      new BigDecimal("20.00"))));

      // Deciduous 0 is still entered, so Actual Harvested (recomputed: 0) is judged too, exactly
      // as the stored path would judge the same row.
      assertEquals(
          List.of("Coniferous", "Actual Harvested"),
          fields(result.pages().get(0).samples().get(0).issues()));
      assertEquals(8971, result.pages().get(0).samples().get(0).id());
    }

    @Test
    @DisplayName("an unsaved percentage edit is re-totalled: 90% total is reported")
    void percentTotalNinetyIsReported() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(
              MILL,
              YEAR,
              8970,
              samplePanel(
                  new SampleEntry(
                      8971,
                      "C",
                      "CB",
                      60,
                      30,
                      0,
                      0,
                      0,
                      null,
                      null,
                      null,
                      null,
                      500,
                      0,
                      new BigDecimal("20.00"))));

      assertEquals(
          List.of("Skidding/Yarding"), fields(result.pages().get(0).samples().get(0).issues()));
    }

    @Test
    @DisplayName("a stored issue fixed on screen clears; Skyline > 0 judges the on-screen supports")
    void storedIssueFixedOnScreen() {
      // Stored: 50% total. On screen: re-split to 100 with a Skyline share, every support entered
      // (the decimal average distance survives intact).
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
      TreeToTruckDetailReportEntity m = metSample();
      when(repository.findSamples(MILL, YEAR))
          .thenReturn(
              List.of(
                  new TreeToTruckDetailReportEntity(
                      m.id(),
                      m.reportId(),
                      "C",
                      "CB",
                      50,
                      0,
                      0,
                      0,
                      0,
                      0,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "N",
                      "N",
                      null,
                      500,
                      0,
                      new BigDecimal("20.00"),
                      0)));
      assertEquals(
          "ISSUES", service.checkStatusPage(MILL, YEAR, 8970, samplePanel(null)).outcome());

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(
              MILL,
              YEAR,
              8970,
              samplePanel(
                  new SampleEntry(
                      8971,
                      "C",
                      "CB",
                      50,
                      0,
                      50,
                      0,
                      0,
                      0,
                      300,
                      4,
                      new BigDecimal("12.5"),
                      500,
                      0,
                      new BigDecimal("20.00"))));

      assertEquals("MET", result.outcome());
    }

    @Test
    @DisplayName("a new sample (null id, Cut Block blank) is evaluated as the next Sample # n")
    void newSampleIsTheNextSample() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(
              MILL,
              YEAR,
              8970,
              samplePanel(
                  new SampleEntry(
                      null,
                      "NEW",
                      null,
                      100,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      400,
                      0,
                      new BigDecimal("18"))));

      Schedule8PageCheckResult page = result.pages().get(0);
      assertEquals("ISSUES", result.outcome());
      assertEquals(2, page.samples().size());
      assertTrue(page.samples().get(0).met(), "the stored sample keeps its stored verdict");
      Schedule8SampleCheckResult added = page.samples().get(1);
      assertNull(added.id(), "an unsaved sample has no stored id to report");
      assertEquals(2, added.sampleNumber());
      assertEquals("Sample # 2 - NEW", added.sampleLabel());
      assertEquals(List.of("Cut Block"), fields(added.issues()));
    }

    @Test
    @DisplayName("an unknown sample id is appended too, never dropped")
    void unknownSampleIdIsAppended() {
      storedCleanPageAndSample();
      SampleEntry s = storedSampleOnScreen();

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(
              MILL,
              YEAR,
              8970,
              samplePanel(
                  new SampleEntry(
                      4242,
                      "GONE",
                      s.cutBlock(),
                      s.groundBasePct(),
                      s.grapplePct(),
                      s.skylinePct(),
                      s.highleadPct(),
                      s.helicopterPct(),
                      s.otherSkiddingPct(),
                      null,
                      null,
                      null,
                      s.coniferousVolume(),
                      s.deciduousVolume(),
                      s.originalRate())));

      List<Schedule8SampleCheckResult> samples = result.pages().get(0).samples();
      assertEquals(2, samples.size());
      assertNull(samples.get(1).id());
      assertEquals("Sample # 2 - GONE", samples.get(1).sampleLabel());
      assertEquals("MET", result.outcome());
    }

    @Test
    @DisplayName("a new sample on a page with none satisfies the at-least-one-sample rule")
    void newSampleSatisfiesAtLeastOneSample() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
      when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
      assertEquals(
          List.of("Sample"),
          fields(
              service
                  .checkStatusPage(MILL, YEAR, 8970, samplePanel(null))
                  .pages()
                  .get(0)
                  .issues()));
      SampleEntry s = storedSampleOnScreen();

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(
              MILL,
              YEAR,
              8970,
              samplePanel(
                  new SampleEntry(
                      null,
                      s.contractId(),
                      s.cutBlock(),
                      s.groundBasePct(),
                      s.grapplePct(),
                      s.skylinePct(),
                      s.highleadPct(),
                      s.helicopterPct(),
                      s.otherSkiddingPct(),
                      null,
                      null,
                      null,
                      s.coniferousVolume(),
                      s.deciduousVolume(),
                      s.originalRate())));

      assertEquals("MET", result.outcome());
      assertEquals("Sample # 1 - C", result.pages().get(0).samples().get(0).sampleLabel());
    }

    @Test
    @DisplayName("the page header stays stored: a stored page issue is still reported")
    void pageHeaderStaysStored() {
      when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(null, "250")));
      when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(MILL, YEAR, 8970, samplePanel(storedSampleOnScreen()));

      assertEquals(List.of("Contact"), fields(result.pages().get(0).issues()));
    }

    @Test
    @DisplayName("an unknown page is vacuously MET even with a sample panel")
    void unknownPageIsVacuouslyMet() {
      storedCleanPageAndSample();

      Schedule8CheckStatusResponse result =
          service.checkStatusPage(MILL, YEAR, 4242, samplePanel(storedSampleOnScreen()));

      assertEquals("MET", result.outcome());
      assertTrue(result.pages().isEmpty());
    }
  }

  @Test
  @DisplayName("#359 stored parity: the stored paths evaluate the served document unchanged")
  void storedPathsEvaluateTheServedDocument() {
    when(repository.findPages(MILL, YEAR))
        .thenReturn(List.of(page(null, "250"), page(8972, "TSA5", "cp123", "Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));

    Schedule8CheckStatusResponse all = service.checkStatusStored(MILL, YEAR);
    Schedule8CheckStatusResponse page =
        service.checkStatusPage(MILL, YEAR, 8972, samplePanel(null));

    assertEquals("ISSUES", all.outcome());
    assertEquals(List.of("Contact"), fields(all.pages().get(0).issues()));
    assertEquals(List.of("Sample"), fields(all.pages().get(1).issues()));
    assertEquals(List.of(all.pages().get(1)), page.pages());
  }
}
