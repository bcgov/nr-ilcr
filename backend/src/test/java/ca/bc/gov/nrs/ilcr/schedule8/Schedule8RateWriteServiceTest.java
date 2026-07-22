package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8RateRequest;
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
 * Unit test for the Schedule 8 rate-detail write control flow (Story 14.4) — mocked repository. Covers
 * the Draft gate, unknown-sample / unknown-row 404s, the optimistic-lock stale path (409), add →
 * insert, and the idempotent delete guard. Full behaviour is proven against Oracle in
 * {@link Schedule8RateWriteIT}.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8RateWriteServiceTest {

  private static final long MILL = 594L;
  private static final int YEAR = 2021;
  private static final int SAMPLE = 8941;
  private static final String USER = "tester";

  @Mock
  private Schedule8Repository repository;

  @InjectMocks
  private Schedule8Service service;

  @BeforeEach
  void stubReadsForRecompute() {
    lenient().when(repository.findPages(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
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

  private static Schedule8RateRequest rate(Integer revisionCount) {
    return new Schedule8RateRequest(null, revisionCount, 82, new BigDecimal("5.00"), "CT1", "d");
  }

  @Test
  void unknownSample_throwsNotFound() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(false);
    assertThrows(ScheduleNotFoundException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, null, rate(null), true, USER));
    verify(repository, never()).insertRate(anyInt(), any(), any(), any(), any(), any());
  }

  @Test
  void add_insertsRateRow() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    service.saveRate(MILL, YEAR, SAMPLE, null, rate(null), true, USER);
    verify(repository).insertRate(eq(SAMPLE), eq("CT1"), eq(82), eq("d"),
        eq(new BigDecimal("5.00")), eq(USER));
  }

  @Test
  void editUnknownRow_throwsNotFound() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(7000, SAMPLE)).thenReturn(false);
    assertThrows(ScheduleNotFoundException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, 7000, rate(0), true, USER));
  }

  @Test
  void editStaleRevision_throwsStale() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(7000, SAMPLE)).thenReturn(true);
    when(repository.updateRateRow(eq(7000), eq(5), anyString(), anyInt(), any(), any(), anyString()))
        .thenReturn(0);
    assertThrows(StaleRevisionException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, 7000, rate(5), true, USER));
  }

  @Test
  void deleteUnknownRow_isNoOp() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(7000, SAMPLE)).thenReturn(false);
    service.deleteRate(MILL, YEAR, SAMPLE, 7000, true);
    verify(repository, never()).deleteRateRow(anyInt());
  }
}
