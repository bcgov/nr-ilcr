package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckFieldIssue;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckResult;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
    Schedule8CheckStatusResponse result = service.checkStatus(MILL, YEAR);
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
    Schedule8CheckStatusResponse result = service.checkStatus(MILL, YEAR);
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
    Schedule8CheckStatusResponse result = service.checkStatus(MILL, YEAR);
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
    Schedule8CheckStatusResponse result = service.checkStatus(MILL, YEAR);
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

    Schedule8CheckStatusResponse result = service.checkStatusPage(MILL, YEAR, 8972);

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

    Schedule8CheckStatusResponse result = service.checkStatusPage(MILL, YEAR, 4242);

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

    Schedule8PageCheckResult page = service.checkStatus(MILL, YEAR).pages().get(0);

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
    assertEquals("MET", service.checkStatus(MILL, YEAR).outcome());
  }
}
