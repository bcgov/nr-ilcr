package ca.bc.gov.nrs.ilcr.schedule4;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4SubPageRowRequest;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRowType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test for the Schedule 4 sub-page list-row write path (Story 4.3). Mocked repository — isolates
 * the add (separate report + detail-with-description; cycle written for Truck Rehaul only), the
 * unknown-location 404, the Draft gate, persistence-failure translation, and the guarded/idempotent
 * row delete.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4SubPageServiceTest {

  private static final long MILL = 550L;
  private static final int YEAR = 2021;
  private static final int LOCATION_ID = 8050;
  private static final String NAME = "Rowed Dump";
  private static final String USER = "dev-submitter";

  @Mock
  private Schedule4Repository repository;

  @InjectMocks
  private Schedule4Service service;

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  private static Schedule4SubPageRowRequest req(SubPageRowType type, Integer cycle) {
    return new Schedule4SubPageRowRequest(type, "  New Row  ", bd("30.0"), bd("100"), 3000, cycle);
  }

  private void stubRecompute() {
    lenient().when(repository.findLocations(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findSubPageRows(MILL, YEAR)).thenReturn(List.of());
  }

  @Test
  void add_towing_insertsReportAndDescriptionDetail_noCycle() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(LOCATION_ID)).thenReturn(Optional.of(NAME));
    when(repository.insertSubPageReport(eq(MILL), eq(YEAR), eq(NAME), eq(bd("30.0")), isNull(),
        eq(USER))).thenReturn(9100);
    stubRecompute();

    service.addSubPageRow(MILL, YEAR, LOCATION_ID, req(SubPageRowType.TOWING, null), true, USER);

    verify(repository).insertSubPageReport(MILL, YEAR, NAME, bd("30.0"), null, USER);
    // code 43, description trimmed.
    verify(repository).insertDetailWithDescription(9100, 43, bd("100"), 3000, "New Row", USER);
  }

  @Test
  void add_truckRehaul_writesCycle() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(LOCATION_ID)).thenReturn(Optional.of(NAME));
    when(repository.insertSubPageReport(eq(MILL), eq(YEAR), eq(NAME), eq(bd("30.0")), eq(7),
        eq(USER))).thenReturn(9101);
    stubRecompute();

    service.addSubPageRow(MILL, YEAR, LOCATION_ID, req(SubPageRowType.TRUCK_REHAUL, 7), true, USER);

    verify(repository).insertSubPageReport(MILL, YEAR, NAME, bd("30.0"), 7, USER); // cycle kept
    verify(repository).insertDetailWithDescription(9101, 46, bd("100"), 3000, "New Row", USER);
  }

  @Test
  void add_other_ignoresCycle() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(LOCATION_ID)).thenReturn(Optional.of(NAME));
    when(repository.insertSubPageReport(eq(MILL), eq(YEAR), eq(NAME), eq(bd("30.0")), isNull(),
        eq(USER))).thenReturn(9102);
    stubRecompute();

    // A cycle sent on a non-Rehaul type is ignored (null written).
    service.addSubPageRow(MILL, YEAR, LOCATION_ID, req(SubPageRowType.OTHER, 9), true, USER);

    verify(repository).insertSubPageReport(MILL, YEAR, NAME, bd("30.0"), null, USER);
    verify(repository).insertDetailWithDescription(9102, 55, bd("100"), 3000, "New Row", USER);
  }

  @Test
  void add_unknownLocation_throws404_writesNothing() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(LOCATION_ID)).thenReturn(Optional.empty());

    assertThrows(ScheduleNotFoundException.class, () -> service.addSubPageRow(
        MILL, YEAR, LOCATION_ID, req(SubPageRowType.TOWING, null), true, USER));

    verify(repository, never()).insertSubPageReport(anyLong(), anyInt(), anyString(), any(), any(),
        anyString());
  }

  @Test
  void add_notDraft_throws409_writesNothing() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(ScheduleNotEditableException.class, () -> service.addSubPageRow(
        MILL, YEAR, LOCATION_ID, req(SubPageRowType.TOWING, null), true, USER));

    verify(repository, never()).findLocationName(anyInt());
    verify(repository, never()).insertSubPageReport(anyLong(), anyInt(), anyString(), any(), any(),
        anyString());
  }

  @Test
  void add_persistenceFailure_translatesToScheduleNotSaved() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(LOCATION_ID)).thenReturn(Optional.of(NAME));
    when(repository.insertSubPageReport(anyLong(), anyInt(), anyString(), any(), any(), anyString()))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(ScheduleNotSavedException.class, () -> service.addSubPageRow(
        MILL, YEAR, LOCATION_ID, req(SubPageRowType.TOWING, null), true, USER));
  }

  @Test
  void delete_subPageRow_deletesReport() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.isSubPageRow(8051, MILL, YEAR)).thenReturn(true);
    stubRecompute();

    service.deleteSubPageRow(MILL, YEAR, 8051, true);

    verify(repository).deleteReport(8051);
  }

  @Test
  void delete_notASubPageRow_isIdempotentNoOp() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.isSubPageRow(8050, MILL, YEAR)).thenReturn(false); // e.g. a primary report id
    stubRecompute();

    service.deleteSubPageRow(MILL, YEAR, 8050, true); // must not throw, must not delete

    verify(repository, never()).deleteReport(anyInt());
  }

  @Test
  void delete_notDraft_throws409() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(ScheduleNotEditableException.class,
        () -> service.deleteSubPageRow(MILL, YEAR, 8051, true));

    verify(repository, never()).deleteReport(anyInt());
  }
}
