package ca.bc.gov.nrs.ilcr.millmaintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.reportingyear.ReportingYearService;
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
 * Rule logic for the mill lifecycle, with the database mocked. The acceptance test covers the SQL
 * and the wire contract; these cover the decisions — criterion parsing and escaping, the order the
 * guards run in, and what is left untouched when one refuses.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Mill lifecycle rules")
class MillMaintenanceServiceTest {

  private static final long MILL_ID = 751L;

  @Mock private MillMaintenanceRepository repository;
  @Mock private ReportingYearService reportingYears;

  private MillMaintenanceService service;

  @BeforeEach
  void setUp() {
    service = new MillMaintenanceService(repository, reportingYears);
    // lenient(): these two baseline stubs serve most tests but not all, and strict stubbing —
    // which the rest of the class deliberately keeps, so a dead stub fails rather than lying —
    // would otherwise reject every test that skips them.
    lenient().when(reportingYears.currentReportingYear()).thenReturn(2021);
    lenient().when(repository.findById(MILL_ID)).thenReturn(Optional.of(entity("ACT", 0)));
  }

  @Test
  @DisplayName("Blank criteria are not filters")
  void blankCriteriaBecomeNull() {
    when(repository.search(null, null, null)).thenReturn(List.of());

    service.search("  ", "", null);

    verify(repository).search(null, null, null);
  }

  @Test
  @DisplayName("A non-numeric mill number is refused before the search runs")
  void nonNumericMillNumberIsRefusedBeforeQuerying() {
    assertThatThrownBy(() -> service.search("12a", null, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(repository, never()).search(any(), any(), any());
  }

  @Test
  @DisplayName("An unknown status criterion is refused before the search runs")
  void unknownStatusCriterionIsRefusedBeforeQuerying() {
    // Legacy's fixed dropdown made this input unreachable; over the open wire it is a caller bug,
    // and answering it with the zero-match "try importing the mill" advice would misdiagnose it.
    assertThatThrownBy(() -> service.search(null, null, "CLOSED"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(repository, never()).search(any(), any(), any());
  }

  @Test
  @DisplayName("LIKE metacharacters in a name fragment are escaped")
  void nameFragmentIsEscaped() {
    when(repository.search(any(), anyString(), any())).thenReturn(List.of());

    service.search(null, "50%_a\\b", null);

    verify(repository).search(null, "50\\%\\_a\\\\b", null);
  }

  @Test
  @DisplayName("Deactivation checks for active users before writing anything")
  void deactivationGuardRunsBeforeTheWrite() {
    when(repository.hasActiveAssignment(MILL_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.deactivate(MILL_ID, 0, "TESTADMN"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getMessageKey())
        .isEqualTo("error.mill.deactivate.hasactiveusers");

    verify(repository, never()).updateStatusCode(anyLong(), anyString(), anyInt(), anyString());
  }

  @Test
  @DisplayName("Activation has no user guard, matching legacy's asymmetry")
  void activationIgnoresActiveUsers() {
    when(repository.updateStatusCode(MILL_ID, "ACT", 0, "TESTADMN")).thenReturn(1);
    when(reportingYears.isMillEnrolled(MILL_ID, 2021)).thenReturn(true);

    service.activate(MILL_ID, 0, "TESTADMN");

    verify(repository).updateStatusCode(MILL_ID, "ACT", 0, "TESTADMN");
    // The asymmetry itself: the assignment table is never even consulted. (A stub returning true
    // here would be dead code — activate has no call to feed it — so the never() is the proof.)
    verify(repository, never()).hasActiveAssignment(anyLong());
  }

  @Test
  @DisplayName("Activation creates report records only when the mill has none for the year")
  void activationCreatesRecordsOnlyWhenMissing() {
    when(repository.updateStatusCode(anyLong(), anyString(), anyInt(), anyString())).thenReturn(1);
    when(reportingYears.isMillEnrolled(MILL_ID, 2021)).thenReturn(true);

    service.activate(MILL_ID, 0, "TESTADMN");

    verify(reportingYears, never()).enrolMillInYear(anyLong(), anyInt(), anyString());

    when(reportingYears.isMillEnrolled(MILL_ID, 2021)).thenReturn(false);

    service.activate(MILL_ID, 0, "TESTADMN");

    verify(reportingYears).enrolMillInYear(MILL_ID, 2021, "TESTADMN");
  }

  @Test
  @DisplayName("A status write that matches no row is a stale-revision conflict")
  void aLostStatusUpdateIsStale() {
    when(repository.hasActiveAssignment(MILL_ID)).thenReturn(false);
    when(repository.updateStatusCode(MILL_ID, "CLS", 3, "TESTADMN")).thenReturn(0);

    assertThatThrownBy(() -> service.deactivate(MILL_ID, 3, "TESTADMN"))
        .isInstanceOf(StaleRevisionException.class);
  }

  @Test
  @DisplayName("Both contacts are checked against the mill's client location before writing")
  void bothContactsAreChecked() {
    when(repository.contactBelongsToMill(MILL_ID, 7551L)).thenReturn(true);
    when(repository.contactBelongsToMill(MILL_ID, 7561L)).thenReturn(false);

    assertThatThrownBy(() -> service.saveContacts(MILL_ID, "Y", 7551L, 7561L, 0, "TESTADMN"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getMessageKey())
        .isEqualTo("error.mill.contact.notselectable");

    verify(repository, never())
        .updateContacts(anyLong(), anyString(), any(), any(), anyInt(), anyString());
  }

  @Test
  @DisplayName("A null contact selection is not membership-checked, it clears the column")
  void nullContactsSkipTheMembershipCheck() {
    when(repository.updateContacts(MILL_ID, "N", null, null, 0, "TESTADMN")).thenReturn(1);

    service.saveContacts(MILL_ID, "N", null, null, 0, "TESTADMN");

    verify(repository, never()).contactBelongsToMill(anyLong(), anyLong());
    verify(repository).updateContacts(MILL_ID, "N", null, null, 0, "TESTADMN");
  }

  @Test
  @DisplayName("Import refuses a mill that already has a cross-reference")
  void importRefusesATrackedMill() {
    when(repository.millExists(MILL_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.importMill(MILL_ID, "TESTADMN"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getMessageKey())
        .isEqualTo("mill.already.tracked");

    verify(repository, never())
        .insertStatusXref(anyLong(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("Import refuses an unknown ministry mill before looking for a cross-reference")
  void importRefusesAnUnknownMill() {
    when(repository.millExists(999L)).thenReturn(false);

    assertThatThrownBy(() -> service.importMill(999L, "TESTADMN"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Import creates the cross-reference closed, with the legacy provenance comment")
  void importCreatesAClosedCrossReference() {
    long untracked = 750L;
    when(repository.millExists(untracked)).thenReturn(true);
    when(repository.findById(untracked))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(entity("CLS", 0)));

    service.importMill(untracked, "TESTADMN");

    verify(repository)
        .insertStatusXref(untracked, "CLS", "Y", "Imported from ISP Mill table", "TESTADMN");
    verify(reportingYears).enrolMillInYear(untracked, 2021, "TESTADMN");
  }

  @Test
  @DisplayName("A failed import answers with the legacy failure message, not a fabricated conflict")
  void aFailedImportIsTheLegacyFailure() {
    long untracked = 750L;
    when(repository.millExists(untracked)).thenReturn(true);
    when(repository.findById(untracked)).thenReturn(Optional.empty());
    doThrow(new DataIntegrityViolationException("report-record collision"))
        .when(reportingYears)
        .enrolMillInYear(untracked, 2021, "TESTADMN");

    // Any integrity failure past the existence checks — not only the concurrent-import race — must
    // surface as S14's own answer. Mapping them all to "already tracked" told an untracked mill's
    // administrator a permanent falsehood.
    assertThatThrownBy(() -> service.importMill(untracked, "TESTADMN"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> {
              assertThat(((BusinessException) e).getStatus())
                  .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(((BusinessException) e).getMessageKey()).isEqualTo("failImportingMillMsg");
            });
  }

  @Test
  @DisplayName("Activation over partial report records is a named conflict, not a 500 (D7)")
  void activationOverPartialRecordsIsANamedConflict() {
    when(repository.updateStatusCode(MILL_ID, "ACT", 0, "TESTADMN")).thenReturn(1);
    when(reportingYears.isMillEnrolled(MILL_ID, 2021)).thenReturn(false);
    doThrow(new DataIntegrityViolationException("category PK collision"))
        .when(reportingYears)
        .enrolMillInYear(MILL_ID, 2021, "TESTADMN");

    assertThatThrownBy(() -> service.activate(MILL_ID, 0, "TESTADMN"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> {
              assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(((BusinessException) e).getMessageKey())
                  .isEqualTo("error.mill.activate.partialrecords");
            });
  }

  @Test
  @DisplayName("With no reporting year open, import and activation refuse rather than half-run")
  void noOpenReportingYearRefuses() {
    when(reportingYears.currentReportingYear()).thenReturn(null);
    when(repository.millExists(750L)).thenReturn(true);
    when(repository.findById(750L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.importMill(750L, "TESTADMN"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getMessageKey())
        .isEqualTo("reportingPeriodNotFoundMsg");

    assertThatThrownBy(() -> service.activate(MILL_ID, 0, "TESTADMN"))
        .isInstanceOf(BusinessException.class);

    verify(repository, never())
        .insertStatusXref(anyLong(), anyString(), anyString(), anyString(), anyString());
    verify(repository, never()).updateStatusCode(anyLong(), eq("ACT"), anyInt(), anyString());
  }

  @Test
  @DisplayName("Contact options require the mill to be tracked")
  void contactOptionsRequireATrackedMill() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.contactOptions(999L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("The mill status code drives the active flag on the wire record")
  void statusCodeDrivesTheActiveFlag() {
    when(repository.findById(MILL_ID)).thenReturn(Optional.of(entity("CLS", 2)));

    assertThat(service.findById(MILL_ID).isActive()).isFalse();

    when(repository.findById(MILL_ID)).thenReturn(Optional.of(entity("ACT", 2)));

    assertThat(service.findById(MILL_ID).isActive()).isTrue();
  }

  private static AdminMillEntity entity(String statusCode, int revisionCount) {
    return new AdminMillEntity(
        MILL_ID,
        "7510",
        "Cariboo Maintain Mill",
        statusCode,
        "ACT".equals(statusCode) ? "Active" : "Close",
        null,
        null,
        null,
        revisionCount);
  }
}
