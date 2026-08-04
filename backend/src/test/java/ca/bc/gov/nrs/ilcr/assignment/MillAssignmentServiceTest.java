package ca.bc.gov.nrs.ilcr.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentOutcome;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillLabel;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit test for {@link MillAssignmentService} — mocked repository; isolates the assign/end rules. */
@ExtendWith(MockitoExtension.class)
class MillAssignmentServiceTest {

  private static final long MILL = 514L;
  private static final String GUID = "B29C746A6BAF45B9844EE2E2984CA472";
  private static final String ADMIN = "GRPASCUC";

  @Mock private MillUserProfileXrefRepository repository;
  @InjectMocks private MillAssignmentService service;

  private static MillUserProfileXrefEntity active(int revision) {
    return new MillUserProfileXrefEntity(1L, GUID, MILL, "Pat Submitter", "PSUBMIT",
        LocalDate.of(2026, 8, 4), null, revision, ADMIN, LocalDateTime.now(), ADMIN, LocalDateTime.now());
  }

  private static MillUserProfileXrefEntity ended() {
    return new MillUserProfileXrefEntity(1L, GUID, MILL, "Pat Submitter", "PSUBMIT",
        LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5), 1, ADMIN, LocalDateTime.now(), ADMIN,
        LocalDateTime.now());
  }

  @Test
  void listByMill_mapsEntitiesToSubmittersWithLabelAndStatus() {
    when(repository.findMillLabel(MILL)).thenReturn(Optional.of(new MillLabel("514", "Test Mill")));
    when(repository.findByMill(MILL)).thenReturn(List.of(active(0), ended()));

    List<MillSubmitter> rows = service.listByMill(MILL);

    assertEquals(2, rows.size());
    assertEquals("ACTIVE", rows.get(0).status());
    assertEquals("ENDED", rows.get(1).status());
    assertEquals("514", rows.get(0).millNumber());
    assertEquals("Test Mill", rows.get(0).millName());
  }

  @Test
  void assign_newActive_insertsAndReturnsActivateMessage() {
    when(repository.findMillLabel(MILL)).thenReturn(Optional.of(new MillLabel("514", "Test Mill")));
    when(repository.findActive(MILL, GUID)).thenReturn(Optional.empty(), Optional.of(active(0)));
    when(repository.nextId()).thenReturn(9001L);

    AssignmentOutcome outcome = service.assign(MILL, GUID, ADMIN);

    verify(repository).insertActive(9001L, GUID, MILL, null, null, ADMIN);
    assertEquals("user.activate.mill", outcome.messageKey());
    assertEquals("ACTIVE", outcome.submitter().status());
  }

  @Test
  void assign_alreadyActive_returnsDuplicateWarning_andNeverInserts() {
    when(repository.findMillLabel(MILL)).thenReturn(Optional.of(new MillLabel("514", "Test Mill")));
    when(repository.findActive(MILL, GUID)).thenReturn(Optional.of(active(0)));

    AssignmentOutcome outcome = service.assign(MILL, GUID, ADMIN);

    assertEquals("user.not.associated.to.mill", outcome.messageKey());
    verify(repository, never()).insertActive(anyLong(), anyString(), anyLong(), any(), any(), anyString());
  }

  @Test
  void assign_raceOnUniqueIndex_returnsDuplicateWarning_not500() {
    when(repository.findMillLabel(MILL)).thenReturn(Optional.of(new MillLabel("514", "Test Mill")));
    // absent on the pre-check, then present (the other admin's row) after the failed insert
    when(repository.findActive(MILL, GUID)).thenReturn(Optional.empty(), Optional.of(active(0)));
    when(repository.nextId()).thenReturn(9002L);
    when(repository.insertActive(eq(9002L), eq(GUID), eq(MILL), isNull(), isNull(), eq(ADMIN)))
        .thenThrow(new DataIntegrityViolationException("ORA-00001 unique index"));

    AssignmentOutcome outcome = service.assign(MILL, GUID, ADMIN);

    assertEquals("user.not.associated.to.mill", outcome.messageKey());
  }

  @Test
  void end_active_softEndsAndReturnsDeactivateMessage() {
    when(repository.findActive(MILL, GUID)).thenReturn(Optional.of(active(0)));
    when(repository.endActive(MILL, GUID, 0, ADMIN)).thenReturn(1);
    when(repository.findMillLabel(MILL)).thenReturn(Optional.of(new MillLabel("514", "Test Mill")));
    when(repository.findByMill(MILL)).thenReturn(List.of(ended()));

    AssignmentOutcome outcome = service.end(MILL, GUID, 0, ADMIN);

    assertEquals("user.deactivate.mill", outcome.messageKey());
    assertEquals("ENDED", outcome.submitter().status());
  }

  @Test
  void end_noActiveAssignment_throwsNotFound() {
    when(repository.findActive(MILL, GUID)).thenReturn(Optional.empty());

    assertThrows(AssignmentNotFoundException.class, () -> service.end(MILL, GUID, 0, ADMIN));
    verify(repository, never()).endActive(anyLong(), anyString(), anyInt(), anyString());
  }

  @Test
  void end_staleRevision_throwsConflict() {
    when(repository.findActive(MILL, GUID)).thenReturn(Optional.of(active(3)));
    when(repository.endActive(MILL, GUID, 0, ADMIN)).thenReturn(0); // revision moved under us

    assertThrows(AssignmentStaleException.class, () -> service.end(MILL, GUID, 0, ADMIN));
  }
}
