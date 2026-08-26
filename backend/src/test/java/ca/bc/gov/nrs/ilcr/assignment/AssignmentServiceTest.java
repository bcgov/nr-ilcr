package ca.bc.gov.nrs.ilcr.assignment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import ca.bc.gov.nrs.ilcr.assignment.dto.SubmitterAccount;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the branch logic in {@link AssignmentService} that an end-to-end test cannot
 * reach: the lost-insert race, and the guard ordering that decides whether a write happens at all.
 * The SQL and the wiring are proven by {@code AssignmentWriteIT}.
 */
@DisplayName("AssignmentService — branch logic (UC-USR-001/002)")
class AssignmentServiceTest {

  private static final long MILL = 514L;
  // 32 characters, matching the real directory GUID width — a shorter fixture is exactly the
  // request-boundary trap the story's Debug Log records.
  private static final String GUID = "UNITTEST1BBBCCCCDDDDEEEEFFFF0001";
  private static final String ADMIN = "TESTADMN";

  private final IlcrUserRepository users = mock(IlcrUserRepository.class);
  private final MillUserXrefRepository assignments = mock(MillUserXrefRepository.class);
  private final MillContextRepository mills = mock(MillContextRepository.class);
  private final AssignmentService service = new AssignmentService(users, assignments, mills);

  @Test
  @DisplayName("a lost insert race reports the already-assigned warning, never a server error")
  void concurrentInsertBecomesTheAlreadyAssignedWarning() {
    activeMill();
    when(users.findUser(GUID)).thenReturn(Optional.of(account()));
    // The pre-check sees nothing, so the code takes the insert path — and loses the race, which is
    // the one ordering a single-threaded end-to-end test can never produce.
    when(assignments.findAssignment(MILL, GUID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(activeAssignment()));
    when(assignments.insertActiveAssignment(MILL, GUID, ADMIN))
        .thenThrow(new DataIntegrityViolationException("ORA-00001: unique constraint violated"));

    AssignmentService.Outcome outcome = service.assign(MILL, GUID, ADMIN);

    assertEquals(AssignmentService.MSG_ALREADY_ASSIGNED, outcome.messageKey());
    assertEquals(MillSubmitter.ACTIVE, outcome.assignment().status());
  }

  @Test
  @DisplayName("an already-active pair is not written to at all")
  void duplicateAssignmentWritesNothing() {
    activeMill();
    when(users.findUser(GUID)).thenReturn(Optional.of(account()));
    when(assignments.findAssignment(MILL, GUID)).thenReturn(Optional.of(activeAssignment()));

    AssignmentService.Outcome outcome = service.assign(MILL, GUID, ADMIN);

    assertEquals(AssignmentService.MSG_ALREADY_ASSIGNED, outcome.messageKey());
    verify(assignments, never()).insertActiveAssignment(anyLong(), anyString(), anyString());
    verify(assignments, never())
        .reactivateAssignment(anyLong(), anyString(), anyInt(), anyString());
  }

  @Test
  @DisplayName("a closed mill blocks reviving an ended assignment before any write is attempted")
  void closedMillBlocksRevivalBeforeWriting() {
    when(mills.findSelectableMillById(MILL))
        .thenReturn(Optional.of(new MillSummary(MILL, "0001", "Closed Mill", "CLS")));
    when(users.findUser(GUID)).thenReturn(Optional.of(account()));
    when(assignments.findAssignment(MILL, GUID)).thenReturn(Optional.of(endedAssignment()));

    assertThrows(MillNotActiveException.class, () -> service.assign(MILL, GUID, ADMIN));

    verify(assignments, never())
        .reactivateAssignment(anyLong(), anyString(), anyInt(), anyString());
  }

  @Test
  @DisplayName("deactivation is refused before the flag is touched")
  void deactivationIsRefusedBeforeWriting() {
    when(assignments.hasActiveAssignment(GUID)).thenReturn(true);

    assertThrows(
        AccountHasActiveMillsException.class, () -> service.setAccountActive(GUID, false, ADMIN));

    // The refusal must leave the flag exactly as it was, which means not writing at all.
    verify(users, never()).setAccountActive(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("activation never consults the active-assignment guard")
  void activationIsNotGuarded() {
    when(users.setAccountActive(GUID, "Y", ADMIN)).thenReturn(1);
    when(users.findUser(GUID))
        .thenReturn(
            Optional.of(
                new IlcrUserEntity(
                    GUID,
                    "LICENSEE",
                    "Y",
                    1,
                    ADMIN,
                    LocalDateTime.now(),
                    ADMIN,
                    LocalDateTime.now())));

    service.setAccountActive(GUID, true, ADMIN);

    // Legacy's activate path had no guard, and adding one would block reinstating a user.
    verify(assignments, never()).hasActiveAssignment(anyString());
  }

  @Test
  @DisplayName("a revive that loses a concurrent update is refused as stale")
  void staleReviveIsRefused() {
    activeMill();
    when(users.findUser(GUID)).thenReturn(Optional.of(account()));
    when(assignments.findAssignment(MILL, GUID)).thenReturn(Optional.of(endedAssignment()));
    // The revision the service just read no longer matches by the time the update runs.
    when(assignments.reactivateAssignment(MILL, GUID, 1, ADMIN)).thenReturn(0);

    assertThrows(StaleRevisionException.class, () -> service.assign(MILL, GUID, ADMIN));
  }

  @Test
  @DisplayName("re-ending an already-ended assignment is refused without writing")
  void reEndingAnEndedAssignmentIsRefused() {
    when(assignments.findAssignment(MILL, GUID)).thenReturn(Optional.of(endedAssignment()));

    // Even with the current revision: re-stamping would overwrite the historical end date.
    assertThrows(StaleRevisionException.class, () -> service.end(MILL, GUID, 1, ADMIN));

    verify(assignments, never()).endAssignment(anyLong(), anyString(), anyInt(), anyString());
  }

  @Test
  @DisplayName("an assignment can be ended even when its mill is no longer selectable")
  void endWorksWhenTheMillIsNotSelectable() {
    when(mills.findSelectableMillById(MILL)).thenReturn(Optional.empty());
    when(assignments.findAssignment(MILL, GUID))
        .thenReturn(Optional.of(activeAssignment()))
        .thenReturn(Optional.of(endedAssignment()));
    when(assignments.endAssignment(MILL, GUID, 0, ADMIN)).thenReturn(1);

    MillSubmitter ended = service.end(MILL, GUID, 0, ADMIN);

    // The row must stay endable — otherwise the account deactivation guard, which still counts
    // this assignment, could never be satisfied. The mill fields simply render absent.
    assertEquals(MillSubmitter.ENDED, ended.status());
    assertNull(ended.millNumber());
    assertNull(ended.millName());
  }

  @Test
  @DisplayName("a row whose mill has vanished still renders, with the mill fields absent")
  void listByUserToleratesAVanishedMill() {
    when(assignments.findByUser(GUID)).thenReturn(List.of(activeAssignment()));
    when(mills.findSelectableMillById(MILL)).thenReturn(Optional.empty());

    List<MillSubmitter> rows = service.listByUser(GUID, false);

    assertEquals(1, rows.size());
    assertNull(rows.get(0).millNumber());
    assertNull(rows.get(0).millName());
  }

  @Test
  @DisplayName("a lost provisioning race is absorbed — the account exists, which is all we needed")
  void provisioningRaceIsAbsorbed() {
    activeMill();
    // Absent on the read, present by the time the insert lands: another request won the race.
    when(users.findUser(GUID)).thenReturn(Optional.empty()).thenReturn(Optional.of(account()));
    doThrow(new DataIntegrityViolationException("ORA-00001: unique constraint"))
        .when(users)
        .insertAccount(GUID, "LICENSEE", SubmitterAccount.INACTIVE, ADMIN);
    when(assignments.findAssignment(MILL, GUID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(activeAssignment()));

    assertDoesNotThrow(() -> service.assign(MILL, GUID, ADMIN));
  }

  @Test
  @DisplayName("a provisioning failure that is NOT a race keeps its own error")
  void provisioningIntegrityFailureIsNotSwallowed() {
    // The same exception type, but no row appears on the re-read — so it was never a duplicate.
    // Swallowing it would hide a genuinely broken insert (a role absent from ILCR_ROLE, a value
    // too wide for its column) and resurface it later as a confusing assignment failure.
    activeMill();
    when(users.findUser(GUID)).thenReturn(Optional.empty());
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException(
            "ORA-02291: integrity constraint - parent key not found");
    doThrow(failure).when(users).insertAccount(GUID, "LICENSEE", SubmitterAccount.INACTIVE, ADMIN);

    assertSame(
        failure,
        assertThrows(
            DataIntegrityViolationException.class, () -> service.assign(MILL, GUID, ADMIN)));
    verify(assignments, never()).insertActiveAssignment(anyLong(), anyString(), anyString());
  }

  private void activeMill() {
    when(mills.findSelectableMillById(MILL))
        .thenReturn(Optional.of(new MillSummary(MILL, "0001", "Active Mill", "ACT")));
  }

  private static IlcrUserEntity account() {
    LocalDateTime stamp = LocalDateTime.of(2026, 8, 25, 9, 0);
    return new IlcrUserEntity(GUID, "LICENSEE", "N", 0, ADMIN, stamp, ADMIN, stamp);
  }

  private static MillUserXrefEntity activeAssignment() {
    LocalDateTime stamp = LocalDateTime.of(2026, 8, 25, 9, 0);
    return new MillUserXrefEntity(MILL, GUID, stamp, null, 0, ADMIN, stamp, ADMIN, stamp);
  }

  private static MillUserXrefEntity endedAssignment() {
    LocalDateTime stamp = LocalDateTime.of(2026, 8, 25, 9, 0);
    return new MillUserXrefEntity(MILL, GUID, null, stamp, 1, ADMIN, stamp, ADMIN, stamp);
  }
}
