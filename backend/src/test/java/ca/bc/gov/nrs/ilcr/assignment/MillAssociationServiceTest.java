package ca.bc.gov.nrs.ilcr.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millmaintenance.AdminMillEntity;
import ca.bc.gov.nrs.ilcr.millmaintenance.MillMaintenanceRepository;
import ca.bc.gov.nrs.ilcr.userlookup.DirectoryUnavailableException;
import ca.bc.gov.nrs.ilcr.userlookup.UserLookupClient;
import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
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
  private static final String GUID_B = "UNITMILL2BBBCCCCDDDDEEEEFFFF0002";
  private static final String ADMIN = "TESTADMN";

  @Mock private MillUserXrefRepository assignments;
  @Mock private MillMaintenanceRepository mills;
  @Mock private AssignmentService accounts;
  @Mock private UserLookupClient lookup;

  private MillAssociationService service;

  /** An {@link ObjectProvider} over one bean, or over nothing when the client is not configured. */
  private static ObjectProvider<UserLookupClient> providerOf(UserLookupClient client) {
    return new ObjectProvider<>() {
      @Override
      public UserLookupClient getObject() {
        return client;
      }

      @Override
      public UserLookupClient getObject(Object... args) {
        return client;
      }

      @Override
      public UserLookupClient getIfAvailable() {
        return client;
      }

      @Override
      public UserLookupClient getIfUnique() {
        return client;
      }
    };
  }

  private static DirectoryUser bob() {
    return new DirectoryUser(GUID, "Smith, Bob", "BSMITH", "BCEIDBUSINESS", "Bob", "Smith");
  }

  @BeforeEach
  void setUp() {
    service = new MillAssociationService(assignments, mills, accounts, providerOf(lookup));
    // lenient(): these serve most tests but not the unknown-mill refusals or the paths that never
    // reach the lock, and strict stubbing — which the rest of the class deliberately keeps — would
    // reject those.
    lenient().when(mills.findById(MILL_ID)).thenReturn(Optional.of(mill("ACT")));
    lenient().when(mills.lockStatusCode(MILL_ID)).thenReturn(Optional.of("ACT"));
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
    when(mills.lockStatusCode(MILL_ID)).thenReturn(Optional.of("CLS"));
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(inactiveRow(0)));
    doThrow(new MillNotActiveException()).when(accounts).requireMillActive("CLS");

    assertThatThrownBy(() -> service.activate(MILL_ID, GUID, 0, ADMIN))
        .isInstanceOf(MillNotActiveException.class);

    verify(assignments, never()).reactivateAssignment(anyLong(), anyString(), anyInt(), any());
  }

  @Test
  @DisplayName("Activation guards on the LOCKED status, not the one the display read returned")
  void activationGuardsOnTheLockedStatusNotTheDisplayRead() {
    // The two reads disagree on purpose: the display read still says ACT while the locked read says
    // CLS, which is exactly the state a mill closed between them leaves behind. Guarding on the
    // display read would activate an association on a closed mill and break BR-01 — and because
    // both reads go through the same repository, only distinguishing WHICH one feeds the guard
    // catches it.
    when(mills.findById(MILL_ID)).thenReturn(Optional.of(mill("ACT")));
    when(mills.lockStatusCode(MILL_ID)).thenReturn(Optional.of("CLS"));
    when(assignments.findAssignment(MILL_ID, GUID)).thenReturn(Optional.of(inactiveRow(0)));
    doThrow(new MillNotActiveException()).when(accounts).requireMillActive("CLS");

    assertThatThrownBy(() -> service.activate(MILL_ID, GUID, 0, ADMIN))
        .isInstanceOf(MillNotActiveException.class);

    verify(accounts, never()).requireMillActive("ACT");
    verify(assignments, never()).reactivateAssignment(anyLong(), anyString(), anyInt(), any());
  }

  @Test
  @DisplayName("Activation takes the mill row lock before it reads the status it guards on")
  void activationLocksBeforeReadingTheStatus() {
    when(assignments.findAssignment(MILL_ID, GUID))
        .thenReturn(Optional.of(inactiveRow(0)))
        .thenReturn(Optional.of(activeRow(1)));
    when(assignments.reactivateAssignment(MILL_ID, GUID, 0, ADMIN)).thenReturn(1);

    service.activate(MILL_ID, GUID, 0, ADMIN);

    // Locking after the write would serialize nothing against a concurrent mill closure.
    InOrder serialized = inOrder(mills, accounts, assignments);
    serialized.verify(mills).lockStatusCode(MILL_ID);
    serialized.verify(accounts).requireMillActive("ACT");
    serialized.verify(assignments).reactivateAssignment(MILL_ID, GUID, 0, ADMIN);
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

  @Test
  @DisplayName("The list resolves each row's GUID against the directory")
  void listByMill_enrichesRowsFromTheDirectory() {
    when(assignments.findByMill(MILL_ID)).thenReturn(List.of(activeRow(0)));
    when(lookup.findBusinessBceid("userGuid", GUID)).thenReturn(List.of(bob()));

    MillSubmitter row = service.listByMill(MILL_ID, true).getFirst();

    assertThat(row.firstName()).isEqualTo("Bob");
    assertThat(row.lastName()).isEqualTo("Smith");
    assertThat(row.bceid()).isEqualTo("BSMITH");
  }

  @Test
  @DisplayName(
      "A directory outage leaves every row bare — the panel still renders from the database")
  void listByMill_leavesNamesAbsentWhenTheDirectoryIsDown() {
    // Two distinct GUIDs so a regression that keeps asking per-row after the first failure is
    // caught by the times(1) below, not hidden by there being only one GUID to ask about. The
    // outage lands on the FIRST ask, which is what makes "every row bare" the right expectation --
    // an outage partway down the page keeps what it had resolved (the test above).
    when(assignments.findByMill(MILL_ID))
        .thenReturn(List.of(activeRow(0, GUID), inactiveRow(1, GUID_B)));
    when(lookup.findBusinessBceid(any(), any())).thenThrow(new DirectoryUnavailableException());

    List<MillSubmitter> rows = service.listByMill(MILL_ID, true);

    // Fail-soft: the panel still renders from database state alone, for every row.
    assertThat(rows).allSatisfy(row -> assertThat(row.firstName()).isNull());
    assertThat(rows).allSatisfy(row -> assertThat(row.bceid()).isNull());
    // The outage stops the loop outright: a second GUID must never be asked about.
    verify(lookup, times(1)).findBusinessBceid(any(), any());
  }

  @Test
  @DisplayName("An outage mid-page keeps the names already resolved — only the rest go bare")
  void listByMill_keepsTheNamesResolvedBeforeTheOutage() {
    // The outage lands on the SECOND GUID, so one name is already in hand when it hits. Returning
    // an empty map here would blank a row the panel could name, discarding a round-trip already
    // paid for (PR #481 review). GUID is listed first because the loop asks in row order.
    when(assignments.findByMill(MILL_ID))
        .thenReturn(List.of(activeRow(0, GUID), inactiveRow(1, GUID_B)));
    when(lookup.findBusinessBceid("userGuid", GUID)).thenReturn(List.of(bob()));
    when(lookup.findBusinessBceid("userGuid", GUID_B))
        .thenThrow(new DirectoryUnavailableException());

    List<MillSubmitter> rows = service.listByMill(MILL_ID, true);

    MillSubmitter resolved =
        rows.stream().filter(row -> row.userGuid().equals(GUID)).findFirst().orElseThrow();
    MillSubmitter bare =
        rows.stream().filter(row -> row.userGuid().equals(GUID_B)).findFirst().orElseThrow();

    assertThat(resolved.firstName()).isEqualTo("Bob");
    assertThat(resolved.lastName()).isEqualTo("Smith");
    assertThat(resolved.bceid()).isEqualTo("BSMITH");

    assertThat(bare.firstName()).isNull();
    assertThat(bare.lastName()).isNull();
    assertThat(bare.bceid()).isNull();
    // Still one ask per distinct GUID, and no retry of the one that reported the outage.
    verify(lookup, times(1)).findBusinessBceid("userGuid", GUID);
    verify(lookup, times(1)).findBusinessBceid("userGuid", GUID_B);
  }

  @Test
  @DisplayName("An unresolved GUID leaves only its own row bare — its neighbour still resolves")
  void listByMill_leavesAnUnresolvedRowBare() {
    // GUID_B (the empty answer) is listed FIRST: an implementation that stopped the whole
    // resolution on the first empty answer, rather than just skipping that one GUID, would
    // leave GUID unresolved too -- this ordering is what makes that regression visible.
    when(assignments.findByMill(MILL_ID))
        .thenReturn(List.of(inactiveRow(1, GUID_B), activeRow(0, GUID)));
    // An unknown user is an EMPTY answer, not a failure (findBusinessBceid's contract).
    when(lookup.findBusinessBceid("userGuid", GUID_B)).thenReturn(List.of());
    when(lookup.findBusinessBceid("userGuid", GUID)).thenReturn(List.of(bob()));

    List<MillSubmitter> rows = service.listByMill(MILL_ID, true);

    MillSubmitter resolved =
        rows.stream().filter(row -> row.userGuid().equals(GUID)).findFirst().orElseThrow();
    MillSubmitter unresolved =
        rows.stream().filter(row -> row.userGuid().equals(GUID_B)).findFirst().orElseThrow();

    assertThat(resolved.firstName()).isEqualTo("Bob");
    assertThat(resolved.lastName()).isEqualTo("Smith");
    assertThat(resolved.bceid()).isEqualTo("BSMITH");

    assertThat(unresolved.firstName()).isNull();
    assertThat(unresolved.bceid()).isNull();
  }

  @Test
  @DisplayName("Two rows sharing a GUID resolve the directory only once")
  void listByMill_resolvesEachDistinctGuidOnce() {
    // Two rows for one user: add() cannot produce this, but grandfathered rows can.
    when(assignments.findByMill(MILL_ID)).thenReturn(List.of(activeRow(0), inactiveRow(1)));
    when(lookup.findBusinessBceid("userGuid", GUID)).thenReturn(List.of(bob()));

    service.listByMill(MILL_ID, true);

    verify(lookup, times(1)).findBusinessBceid("userGuid", GUID);
  }

  @Test
  @DisplayName(
      "A non-DirectoryUnavailableException RuntimeException skips only that GUID's row — its"
          + " neighbour still resolves")
  void listByMill_skipsOnlyTheGuidThatThrowsAnUnexpectedRuntimeException() {
    // GUID_B is the one that misbehaves; GUID is its neighbour and must still resolve — proving
    // the loop continues instead of aborting the whole page the way DirectoryUnavailableException
    // does. IllegalArgumentException stands in for the escapee the client's own javadoc names (a
    // malformed relative URI), but the catch itself must not be specific to any one subtype.
    when(assignments.findByMill(MILL_ID))
        .thenReturn(List.of(inactiveRow(1, GUID_B), activeRow(0, GUID)));
    when(lookup.findBusinessBceid("userGuid", GUID_B))
        .thenThrow(new IllegalArgumentException("malformed relative URI"));
    when(lookup.findBusinessBceid("userGuid", GUID)).thenReturn(List.of(bob()));

    List<MillSubmitter> rows = service.listByMill(MILL_ID, true);

    MillSubmitter resolved =
        rows.stream().filter(row -> row.userGuid().equals(GUID)).findFirst().orElseThrow();
    MillSubmitter skipped =
        rows.stream().filter(row -> row.userGuid().equals(GUID_B)).findFirst().orElseThrow();

    assertThat(resolved.firstName()).isEqualTo("Bob");
    assertThat(resolved.lastName()).isEqualTo("Smith");
    assertThat(resolved.bceid()).isEqualTo("BSMITH");

    assertThat(skipped.firstName()).isNull();
    assertThat(skipped.bceid()).isNull();
    // Both GUIDs are still asked about — unlike the directory-outage case, this is not fatal to
    // the loop.
    verify(lookup, times(1)).findBusinessBceid("userGuid", GUID_B);
    verify(lookup, times(1)).findBusinessBceid("userGuid", GUID);
  }

  @Test
  @DisplayName("An unconfigured lookup client leaves every row bare without attempting a lookup")
  void listByMill_leavesNamesAbsentWhenTheLookupClientIsNotConfigured() {
    MillAssociationService withoutLookup =
        new MillAssociationService(assignments, mills, accounts, providerOf(null));
    when(assignments.findByMill(MILL_ID)).thenReturn(List.of(activeRow(0)));

    MillSubmitter row = withoutLookup.listByMill(MILL_ID, true).getFirst();

    assertThat(row.firstName()).isNull();
    assertThat(row.userGuid()).isEqualTo(GUID);
    verifyNoInteractions(lookup);
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
        0,
        "ITUSER",
        java.time.LocalDateTime.of(2026, 9, 1, 0, 0));
  }

  private static MillUserXrefEntity activeRow(int revisionCount) {
    return activeRow(revisionCount, GUID);
  }

  private static MillUserXrefEntity activeRow(int revisionCount, String userGuid) {
    return new MillUserXrefEntity(
        MILL_ID, userGuid, LocalDateTime.now(), null, revisionCount, ADMIN, null, ADMIN, null);
  }

  private static MillUserXrefEntity inactiveRow(int revisionCount) {
    return inactiveRow(revisionCount, GUID);
  }

  private static MillUserXrefEntity inactiveRow(int revisionCount, String userGuid) {
    return new MillUserXrefEntity(
        MILL_ID, userGuid, null, LocalDateTime.now(), revisionCount, ADMIN, null, ADMIN, null);
  }
}
