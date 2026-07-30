package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckFieldIssue;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 8 Check Status evaluation (Story 14.6) — mocked repository. Covers the
 * all-met MET outcome and a representative ISSUES page (missing Contact + a sample with percent ≠ 100),
 * plus the read-only contract. End-to-end coverage of every rule is in {@link Schedule8CheckStatusIT}.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8CheckStatusServiceTest {

  private static final long MILL = 600L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule8Repository repository;

  @InjectMocks
  private Schedule8Service service;

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
    return new TreeToTruckReportEntity(8970, "SC1", "R1", "BZ1", "TSA5", "B", null, null, "L600",
        "Div", contact, phone, "c", 0);
  }

  private static TreeToTruckDetailReportEntity metSample() {
    return new TreeToTruckDetailReportEntity(8971, 8970, "C", "CB", 100, 0, 0, 0, 0, 0,
        null, null, null, null, null, "N", "N", null, 500, 0, new BigDecimal("20.00"), 0);
  }

  @Test
  void allFieldsPresent_returnsMet() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
    Schedule8CheckStatusResponse result = service.checkStatus(MILL, YEAR);
    assertEquals("MET", result.outcome());
    assertTrue(result.pages().get(0).met());
    assertTrue(result.pages().get(0).samples().get(0).met());
  }

  @Test
  void missingContact_returnsIssuesWithContactFlag() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(null, "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(metSample()));
    Schedule8CheckStatusResponse result = service.checkStatus(MILL, YEAR);
    assertEquals("ISSUES", result.outcome());
    assertTrue(result.pages().get(0).issues().stream()
        .map(Schedule8CheckFieldIssue::field)
        .anyMatch("Contact"::equals));
  }

  @Test
  void percentNotHundred_flagsSkiddingYarding() {
    // Sample with only 50% skidding -> percentTotal 50 != 100 -> flagged at Check Status (S16 half).
    TreeToTruckDetailReportEntity sample = new TreeToTruckDetailReportEntity(8971, 8970, "C", "CB",
        50, 0, 0, 0, 0, 0, null, null, null, null, null, "N", "N", null, 500, 0,
        new BigDecimal("20.00"), 0);
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page("Pat", "250")));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(sample));
    Schedule8CheckStatusResponse result = service.checkStatus(MILL, YEAR);
    assertEquals("ISSUES", result.outcome());
    assertTrue(result.pages().get(0).samples().get(0).issues().stream()
        .map(Schedule8CheckFieldIssue::field)
        .anyMatch("Skidding/Yarding"::equals));
  }

  @Test
  void noPages_isVacuouslyMet() {
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
    assertEquals("MET", service.checkStatus(MILL, YEAR).outcome());
  }
}
