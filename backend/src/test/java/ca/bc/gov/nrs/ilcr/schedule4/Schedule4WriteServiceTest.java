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

import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryInput;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4LocationRequest;
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
 * Unit test for the Schedule 4 location write path (Story 4.2). Mocked repository — no DB, no Spring
 * — so it isolates the family write model: create (insert primary + bump 0→1 + fixed on primary +
 * distance child), edit (bump expected + rename + update-in-place), the delete-when-emptied distance
 * child, server-side name uniqueness (ERR-002), the Draft gate, optimistic-lock handling, idempotent
 * delete, delete cascade, and persistence-failure rollback translation.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4WriteServiceTest {

  private static final long MILL = 540L;
  private static final int YEAR = 2021;
  private static final String USER = "dev-submitter";

  @Mock
  private Schedule4Repository repository;

  @InjectMocks
  private Schedule4Service service;

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  /** The recompute read at the end of a successful save — kept minimal (content not asserted here). */
  private void stubRecompute() {
    lenient().when(repository.findLocations(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findSubPageRows(MILL, YEAR)).thenReturn(List.of());
  }

  @Test
  void save_create_insertsPrimaryBumpsAndWritesFixedPlusDistanceChild() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.nameExists(MILL, YEAR, "New Dump", null)).thenReturn(false);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("New Dump"), isNull(), eq(USER)))
        .thenReturn(9001);
    when(repository.bumpRevision(9001, 0, USER)).thenReturn(1);
    when(repository.findDistanceReportId(MILL, YEAR, "New Dump", 47)).thenReturn(Optional.empty());
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("New Dump"), eq(bd("60.0")), eq(USER)))
        .thenReturn(9002);
    stubRecompute();

    service.saveLocation(MILL, YEAR, new Schedule4LocationRequest(null, null, "New Dump", List.of(
        new CategoryInput(40, bd("1000"), 50000, null),
        new CategoryInput(47, bd("200"), 8000, bd("60.0")))), true, USER);

    verify(repository).insertReport(MILL, YEAR, "New Dump", null, USER);   // primary (distance null)
    verify(repository).bumpRevision(9001, 0, USER);                        // 0 -> 1
    verify(repository).upsertDetail(9001, 40, bd("1000"), 50000, USER);    // fixed on primary
    verify(repository).insertReport(MILL, YEAR, "New Dump", bd("60.0"), USER); // distance child
    verify(repository).upsertDetail(9002, 47, bd("200"), 8000, USER);      // distance detail
  }

  @Test
  void save_create_nameOnlyLocation_insertsPrimaryOnly() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.nameExists(MILL, YEAR, "Bare Dump", null)).thenReturn(false);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("Bare Dump"), isNull(), eq(USER)))
        .thenReturn(9001);
    when(repository.bumpRevision(9001, 0, USER)).thenReturn(1);
    stubRecompute();

    service.saveLocation(MILL, YEAR,
        new Schedule4LocationRequest(null, null, "Bare Dump", List.of()), true, USER);

    verify(repository).insertReport(MILL, YEAR, "Bare Dump", null, USER);
    verify(repository, never()).upsertDetail(anyInt(), anyInt(), any(), any(), anyString());
  }

  @Test
  void save_edit_bumpsExpectedRenamesUpdatesInPlace() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Renamed Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, USER)).thenReturn(1);
    when(repository.findDistanceReportId(MILL, YEAR, "Renamed Dump", 47))
        .thenReturn(Optional.of(8002));
    stubRecompute();

    service.saveLocation(MILL, YEAR, new Schedule4LocationRequest(8001, 0, "Renamed Dump", List.of(
        new CategoryInput(40, bd("1500"), 60000, null),
        new CategoryInput(47, bd("250"), 9000, bd("70.0")))), true, USER);

    verify(repository).bumpRevision(8001, 0, USER);
    verify(repository).renameFamily(MILL, YEAR, "Existing Dump", "Renamed Dump", USER);
    verify(repository).upsertDetail(8001, 40, bd("1500"), 60000, USER);
    verify(repository).updateReportDistance(8002, bd("70.0"), USER);
    verify(repository).upsertDetail(8002, 47, bd("250"), 9000, USER);
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
  }

  @Test
  void save_edit_sameName_doesNotRename() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, USER)).thenReturn(1);
    stubRecompute();

    service.saveLocation(MILL, YEAR, new Schedule4LocationRequest(8001, 0, "Existing Dump", List.of(
        new CategoryInput(40, bd("1500"), 60000, null))), true, USER);

    verify(repository, never()).renameFamily(anyLong(), anyInt(), anyString(), anyString(),
        anyString());
  }

  @Test
  void save_clearDistanceCategory_deletesChildReport() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, USER)).thenReturn(1);
    when(repository.findDistanceReportId(MILL, YEAR, "Existing Dump", 47))
        .thenReturn(Optional.of(8002));
    stubRecompute();

    // A distance category with all-null amounts clears it: the child report is deleted.
    service.saveLocation(MILL, YEAR, new Schedule4LocationRequest(8001, 0, "Existing Dump", List.of(
        new CategoryInput(47, null, null, null))), true, USER);

    verify(repository).deleteReport(8002);
    verify(repository, never()).updateReportDistance(anyInt(), any(), anyString());
  }

  @Test
  void save_duplicateName_throwsConflict_writesNothing() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Rival Dump", "Existing Dump")).thenReturn(true);

    assertThrows(LocationNameConflictException.class, () -> service.saveLocation(MILL, YEAR,
        new Schedule4LocationRequest(8001, 0, "Rival Dump", List.of()), true, USER));

    verify(repository, never()).bumpRevision(anyInt(), anyInt(), anyString());
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
    verify(repository, never()).renameFamily(anyLong(), anyInt(), anyString(), anyString(),
        anyString());
  }

  @Test
  void save_notDraft_throwsNotEditable_writesNothing() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(ScheduleNotEditableException.class, () -> service.saveLocation(MILL, YEAR,
        new Schedule4LocationRequest(null, null, "X", List.of()), true, USER));

    verify(repository, never()).nameExists(anyLong(), anyInt(), anyString(), any());
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
  }

  @Test
  void save_staleRevision_throwsConflict_neverUpserts() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 5, USER)).thenReturn(0);

    assertThrows(StaleRevisionException.class, () -> service.saveLocation(MILL, YEAR,
        new Schedule4LocationRequest(8001, 5, "Existing Dump",
            List.of(new CategoryInput(40, bd("1"), 1, null))), true, USER));

    verify(repository, never()).upsertDetail(anyInt(), anyInt(), any(), any(), anyString());
    verify(repository, never()).renameFamily(anyLong(), anyInt(), anyString(), anyString(),
        anyString());
  }

  @Test
  void save_persistenceFailure_translatesToScheduleNotSaved() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.nameExists(MILL, YEAR, "New Dump", null)).thenReturn(false);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("New Dump"), isNull(), eq(USER)))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(ScheduleNotSavedException.class, () -> service.saveLocation(MILL, YEAR,
        new Schedule4LocationRequest(null, null, "New Dump", List.of()), true, USER));
  }

  @Test
  void delete_draftWithLocation_deletesFamily() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001)).thenReturn(Optional.of("Existing Dump"));

    service.deleteLocation(MILL, YEAR, 8001);

    verify(repository).deleteFamily(MILL, YEAR, "Existing Dump");
  }

  @Test
  void delete_unknownId_isIdempotentNoOp() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(9999)).thenReturn(Optional.empty());

    service.deleteLocation(MILL, YEAR, 9999); // must not throw

    verify(repository, never()).deleteFamily(anyLong(), anyInt(), anyString());
  }

  @Test
  void delete_notDraft_throwsNotEditable() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(ScheduleNotEditableException.class, () -> service.deleteLocation(MILL, YEAR, 8001));

    verify(repository, never()).deleteFamily(anyLong(), anyInt(), anyString());
  }
}
