package ca.bc.gov.nrs.ilcr.reportingyear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.exception.MultiMessageException;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.OpenReportingYearResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link ReportingYearService} — the branch logic (recurring vs first-time, zero-active
 * mills, BR-07 range, duplicate guard) with a fixed {@link Clock} so the current-year math is
 * deterministic (2026). The Testcontainers wiring / SQL is proven by {@code ReportingYearIT}.
 */
@DisplayName("ReportingYearService — open-year branch logic (UC-RY-001)")
class ReportingYearServiceTest {

  private static final Clock CLOCK_2026 =
      Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String USER = "admin@idir";

  private final ReportingYearRepository repository =
      org.mockito.Mockito.mock(ReportingYearRepository.class);
  private final ReportingYearService service = new ReportingYearService(repository, CLOCK_2026);

  @Test
  @DisplayName("recurring: creates max+1 and a Draft/Draft/not-completed row per active mill (S01)")
  void recurring_createsNextYearForActiveMills() {
    when(repository.findMaxReportYear()).thenReturn(2025);    when(repository.findActiveMillIds()).thenReturn(List.of(11L, 22L));

    OpenReportingYearResult result = service.open(null, USER);

    assertEquals(2026, result.year());
    assertEquals(2, result.millsInitialized());
    verify(repository).insertReportingPeriod(2026, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31), USER);
    verify(repository).insertMillReportStatus(2026, 11L, "D", "D", "N", USER);
    verify(repository).insertMillReportStatus(2026, 22L, "D", "D", "N", USER);
    // 11 categories per active mill, Draft/reportable-Y (the repository stamps the state + indicator).
    verify(repository, times(11)).insertReportCategory(eq(2026), eq(11L), anyString(), eq(USER));
    verify(repository, times(11)).insertReportCategory(eq(2026), eq(22L), anyString(), eq(USER));
    verify(repository).insertReportCategory(2026, 11L, "1", USER);
    verify(repository).insertReportCategory(2026, 22L, "11", USER);
  }

  @Test
  @DisplayName("recurring + zero active mills: rejected with INF-001 + ERR-002, nothing created (S03)")
  void recurring_zeroActiveMills_createsNothing() {
    when(repository.findMaxReportYear()).thenReturn(2025);    when(repository.findActiveMillIds()).thenReturn(List.of());

    MultiMessageException ex = assertThrows(MultiMessageException.class, () -> service.open(null, USER));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    assertEquals(List.of("noActiveMillsForNewYearMsg", "reportingPeriodNotFoundMsg"), ex.getMessageKeys());
    verify(repository, never()).insertReportingPeriod(anyInt(), any(), any(), anyString());
    verify(repository, never()).insertMillReportStatus(anyInt(), anyLong(), any(), any(), any(), anyString());
  }

  @Test
  @DisplayName("first-time + zero active mills: allowed, empty year created (S07, decision D-1)")
  void firstTime_zeroActiveMills_createsEmptyYear() {
    when(repository.findMaxReportYear()).thenReturn(null);    when(repository.findActiveMillIds()).thenReturn(List.of());

    OpenReportingYearResult result = service.open(2026, USER);

    assertEquals(2026, result.year());
    assertEquals(0, result.millsInitialized());
    verify(repository).insertReportingPeriod(eq(2026), any(), eq(LocalDate.of(2026, 12, 31)), eq(USER));
    verify(repository, never()).insertMillReportStatus(anyInt(), anyLong(), any(), any(), any(), anyString());
    verify(repository, never()).insertReportCategory(anyInt(), anyLong(), anyString(), anyString());
  }

  @Test
  @DisplayName("first-time + active mills: creates the selected year and initializes each mill (S02)")
  void firstTime_withActiveMills_createsSelectedYear() {
    when(repository.findMaxReportYear()).thenReturn(null);    when(repository.findActiveMillIds()).thenReturn(List.of(5L));

    OpenReportingYearResult result = service.open(2027, USER);

    assertEquals(2027, result.year());
    assertEquals(1, result.millsInitialized());
    verify(repository).insertReportingPeriod(eq(2027), any(), eq(LocalDate.of(2027, 12, 31)), eq(USER));
    verify(repository).insertMillReportStatus(2027, 5L, "D", "D", "N", USER);
    verify(repository, times(11)).insertReportCategory(eq(2027), eq(5L), anyString(), eq(USER));
  }

  @Test
  @DisplayName("first-time: null selection is rejected (FLD-001), nothing created (S04)")
  void firstTime_nullSelection_rejected() {
    when(repository.findMaxReportYear()).thenReturn(null);

    ReportingYearException ex = assertThrows(ReportingYearException.class, () -> service.open(null, USER));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertEquals("reportingYearNotValid", ex.getMessageKey());
    verify(repository, never()).insertReportingPeriod(anyInt(), any(), any(), anyString());
  }

  @Test
  @DisplayName("first-time: a selection outside currentYear-2..currentYear+1 is rejected (BR-07)")
  void firstTime_outOfRange_rejected() {
    when(repository.findMaxReportYear()).thenReturn(null);

    assertThrows(ReportingYearException.class, () -> service.open(2028, USER)); // > 2027
    assertThrows(ReportingYearException.class, () -> service.open(2023, USER)); // < 2024
    verify(repository, never()).insertReportingPeriod(anyInt(), any(), any(), anyString());
  }

  @Test
  @DisplayName("concurrent open that loses the period PK race is mapped to 409 (not a 500)")
  void concurrentOpen_mappedToConflict() {
    when(repository.findMaxReportYear()).thenReturn(2025);
    when(repository.findActiveMillIds()).thenReturn(List.of(5L));
    doThrow(new DataIntegrityViolationException("ORA-00001 PK_ILCR_REPORTING_PERIOD"))
        .when(repository).insertReportingPeriod(eq(2026), any(), any(), eq(USER));

    ReportingYearException ex = assertThrows(ReportingYearException.class, () -> service.open(null, USER));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    assertEquals("reportingYearAlreadyOpenErrorMsg", ex.getMessageKey());
    // The period insert failed, so no mill rows were attempted.
    verify(repository, never()).insertMillReportStatus(anyInt(), anyLong(), any(), any(), any(), anyString());
  }
}
