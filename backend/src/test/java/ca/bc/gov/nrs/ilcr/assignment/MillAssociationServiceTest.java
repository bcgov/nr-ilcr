package ca.bc.gov.nrs.ilcr.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millmaintenance.AdminMillEntity;
import ca.bc.gov.nrs.ilcr.millmaintenance.MillMaintenanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Rule logic for the mill record's association panel, with the database mocked. The acceptance test
 * covers the SQL and the wire contract; these pin the two semantics that deliberately differ from
 * the users-screen {@code assign} — the inactive create and the warn-on-any-pair duplicate rule —
 * plus the order the guards run in and what stays untouched when one refuses.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Mill-record association rules")
class MillAssociationServiceTest {

  private static final long MILL_ID = 751L;
  private static final String GUID = "UNITMILL1BBBCCCCDDDDEEEEFFFF0001";
  private static final String ADMIN = "TESTADMN";

  @Mock private MillUserXrefRepository assignments;
  @Mock private MillMaintenanceRepository mills;
  @Mock private AssignmentService accounts;

  private MillAssociationService service;

  @BeforeEach
  void setUp() {
    service = new MillAssociationService(assignments, mills, accounts);
    // lenient(): the tracked-mill read serves most tests but not the unknown-mill refusals, and
    // strict stubbing — which the rest of the class deliberately keeps — would reject those.
    lenient().when(mills.findById(MILL_ID)).thenReturn(Optional.of(mill("ACT")));
  }

  @Test
  @DisplayName("An unknown or untracked mill is refused on EVERY operation, before any read/write")
  void unknownMillIsRefusedFirst() {
    when(mills.findById(999L)).thenReturn(Optional.empty());

    // All four entry points, not just the add: the mill guard runs first on each, so reordering it
    // behind the association lookup would turn an untracked mill into a "no such assignment" 404 —
    // the same status with the wrong diagnosis for the operator.
    assertThatThrownBy(() -> service.add(999L, GUID, ADMIN))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThatThrownBy(() -> service.activate(999L, GUID, 0, ADMIN))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThatThrownBy(() -> service.deactivate(999L, GUID, 0, ADMIN))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThatThrownBy(() -> service.listByMill(999L, true))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);

    verify(assignments, never()).findAssignment(anyLong(), anyString());
    verify(assignments, never()).findByMill(anyLong());
    verify(accounts, never()).provisionAccountIfAbsent(anyString(), anyString());
  }

  // What this proves and what it does not: the assertions below pin which repository method the add
  // calls and which message key it returns. They do NOT pin the SQL — the ENDED status is read off
  // the stub this test installed, so swapping insertInactiveAssignment's `NULL, SYSDATE` for
  // `SYSDATE, NULL` is caught by MillAssociationIT against a real database, not here.
  @Test
  @DisplayName("The add calls the INACTIVE insert, never the active one, and answers MSG_ASSIGNED")
  void addCreatesTheAssociationInactive() {
    when(assignments.findAssignment(MILL_ID, GUID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(inactiveRow(0)));

    AssignmentService.Outcome outcome = service.add(MILL_ID, GUID, ADMIN);

    verify(accounts).provisionAccountIfAbsent(GUID, ADMIN);
    verify(assignments).insertInactiveAssignment(MILL_ID, GUID, ADMIN);
    verify(assignments, never()).insertActiveAssignment(anyLong(), anyString(), anyString());
    // The pinned quirk: the row is inactive, the sentence says activated.
    assertThat(outcome.messageKey()).isEqualTo(AssignmentService.MSG_ASSIGNED);
    assertThat(outcome.assignment().status()).isEqualTo(MillSubmitter.ENDED);
  }

  @Test
  @DisplayName("An add against an ACTIVE pair warns and writes nothing")
  void addWarnsOnAnActivePair() {
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(activeRow(0)));

    AssignmentService.Outcome outcome = service.add(MILL_ID, GUID, ADMIN);

    assertThat(outcome.messageKey()).isEqualTo(AssignmentService.MSG_ALREADY_ASSIGNED);
    verify(accounts, never()).provisionAccountIfAbsent(anyString(), anyString());
    verify(assignments, never()).insertInactiveAssignment(anyLong(), anyString(), anyString());
  }

  @Test
  @DisplayName("An add against an ENDED pair warns too — it never revives (the users-screen rule)")
  void addDoesNotReviveAnEndedPair() {
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(inactiveRow(3)));

    AssignmentService.Outcome outcome = service.add(MILL_ID, GUID, ADMIN);

    assertThat(outcome.messageKey()).isEqualTo(AssignmentService.MSG_ALREADY_ASSIGNED);
    verify(assignments, never()).reactivateAssignment(anyLong(), anyString(), anyInt(), any());
    verify(assignments, never()).insertInactiveAssignment(anyLong(), anyString(), anyString());
  }

  @Test
  @DisplayName("A lost insert race is reported as the duplicate warning, not a server error")
  void addAbsorbsTheInsertRace() {
    when(assignments.findAssignment(MILL_ID, GUID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(inactiveRow(0)));
    doThrow(new DataIntegrityViolationException("composite key"))
        .when(assignments)
        .insertInactiveAssignment(MILL_ID, GUID, ADMIN);

    AssignmentService.Outcome outcome = service.add(MILL_ID, GUID, ADMIN);

    assertThat(outcome.messageKey()).isEqualTo(AssignmentService.MSG_ALREADY_ASSIGNED);
  }

  @Test
  @DisplayName("A non-race integrity failure on the add keeps its own error")
  void addRethrowsANonRaceFailure() {
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.empty());
    DataIntegrityViolationException failure = new DataIntegrityViolationException("overflow");
    doThrow(failure).when(assignments).insertInactiveAssignment(MILL_ID, GUID, ADMIN);

    assertThatThrownBy(() -> service.add(MILL_ID, GUID, ADMIN)).isSameAs(failure);
  }

  // Ordering only. The rule itself (statusCode != "ACT") lives in the mocked
  // AssignmentService.requireMillActive and is proved by AssignmentServiceTest and by
  // MillAssociationIT.activateIsRefusedWhileTheMillIsClosed — what this pins is that the guard is
  // consulted, and that the write does not happen when it refuses.
  @Test
  @DisplayName("Activation consults the mill-status guard and writes nothing when it refuses (S13)")
  void activationGuardsTheMillStatusBeforeTheWrite() {
    when(mills.findById(MILL_ID)).thenReturn(Optional.of(mill("CLS")));
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(inactiveRow(0)));
    doThrow(new MillNotActiveException()).when(accounts).requireMillActive("CLS");

    assertThatThrownBy(() -> service.activate(MILL_ID, GUID, 0, ADMIN))
        .isInstanceOf(MillNotActiveException.class);

    verify(assignments, never()).reactivateAssignment(anyLong(), anyString(), anyInt(), any());
  }

  @Test
  @DisplayName("Activating an already-active association is a conflict, never a re-stamp")
  void activatingAnActiveAssociationIsRefused() {
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(activeRow(1)));

    assertThatThrownBy(() -> service.activate(MILL_ID, GUID, 1, ADMIN))
        .isInstanceOf(StaleRevisionException.class);

    verify(accounts, never()).requireMillActive(anyString());
    verify(assignments, never()).reactivateAssignment(anyLong(), anyString(), anyInt(), any());
  }

  @Test
  @DisplayName("An activation that matches no row at the caller's revision is stale")
  void aLostActivationIsStale() {
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(inactiveRow(2)));
    when(assignments.reactivateAssignment(MILL_ID, GUID, 2, ADMIN)).thenReturn(0);

    assertThatThrownBy(() -> service.activate(MILL_ID, GUID, 2, ADMIN))
        .isInstanceOf(StaleRevisionException.class);
  }

  @Test
  @DisplayName("Deactivation has no mill-status guard, matching legacy's asymmetry (S08)")
  void deactivationIgnoresTheMillStatus() {
    when(mills.findById(MILL_ID)).thenReturn(Optional.of(mill("CLS")));
    when(assignments.findAssignment(MILL_ID, GUID))
        .thenReturn(Optional.of(activeRow(1)))
        .thenReturn(Optional.of(inactiveRow(2)));
    when(assignments.endAssignment(MILL_ID, GUID, 1, ADMIN)).thenReturn(1);

    AssignmentService.Outcome outcome = service.deactivate(MILL_ID, GUID, 1, ADMIN);

    assertThat(outcome.messageKey()).isEqualTo(AssignmentService.MSG_ENDED);
    verify(accounts, never()).requireMillActive(anyString());
  }

  @Test
  @DisplayName("Deactivating an already-inactive association is a conflict, never a re-stamp")
  void deactivatingAnInactiveAssociationIsRefused() {
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(inactiveRow(1)));

    assertThatThrownBy(() -> service.deactivate(MILL_ID, GUID, 1, ADMIN))
        .isInstanceOf(StaleRevisionException.class);

    verify(assignments, never()).endAssignment(anyLong(), anyString(), anyInt(), any());
  }

  @Test
  @DisplayName("A pair with no association row is not found on either toggle")
  void aMissingAssociationIsNotFound() {
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.activate(MILL_ID, GUID, 0, ADMIN))
        .isInstanceOf(AssignmentNotFoundException.class);
    assertThatThrownBy(() -> service.deactivate(MILL_ID, GUID, 0, ADMIN))
        .isInstanceOf(AssignmentNotFoundException.class);
  }

  @Test
  @DisplayName("The list keeps inactive associations by default and can narrow to active only")
  void listIncludesBothStatesByDefault() {
    when(assignments.findByMill(MILL_ID)).thenReturn(List.of(activeRow(0), inactiveRow(0)));

    assertThat(service.listByMill(MILL_ID, true)).hasSize(2);
    assertThat(service.listByMill(MILL_ID, false))
        .singleElement()
        .extracting(MillSubmitter::status)
        .isEqualTo(MillSubmitter.ACTIVE);
  }

  private static AdminMillEntity mill(String statusCode) {
    return new AdminMillEntity(
        MILL_ID,
        "7510",
        "Cariboo Maintain Mill",
        statusCode,
        "ACT".equals(statusCode) ? "Active" : "Close",
        null,
        null,
        null,
        0);
  }

  private static MillUserXrefEntity activeRow(int revisionCount) {
    return new MillUserXrefEntity(
        MILL_ID, GUID, LocalDateTime.now(), null, revisionCount, ADMIN, null, ADMIN, null);
  }

  private static MillUserXrefEntity inactiveRow(int revisionCount) {
    return new MillUserXrefEntity(
        MILL_ID, GUID, null, LocalDateTime.now(), revisionCount, ADMIN, null, ADMIN, null);
  }
}
