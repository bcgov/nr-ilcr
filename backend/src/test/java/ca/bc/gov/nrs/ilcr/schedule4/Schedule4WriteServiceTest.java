package ca.bc.gov.nrs.ilcr.schedule4;

import static ca.bc.gov.nrs.ilcr.support.TestAmounts.bd;
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

import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryInput;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4LocationRequest;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test for the Schedule 4 location write path (Story 4.2). Mocked repository — no DB, no
 * Spring — so it isolates the family write model: create (insert primary + bump 0→1 + fixed on
 * primary + distance child), edit (bump expected + rename + update-in-place), the
 * delete-when-emptied distance child, the #335 complete-state write (a category the request does
 * not send is written as empty and so cleared — fixed detail rows in one set-based delete, every
 * distance child deleted — an all-null fixed category deletes rather than inserts, a legacy
 * family's identity report is never deleted, and duplicate children for one code collapse),
 * server-side name uniqueness (ERR-002), the Draft gate, optimistic-lock handling, idempotent
 * delete, delete cascade, cross-context (mill/year-scoped) edit rejection, and persistence-failure
 * rollback translation.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4WriteServiceTest {

  private static final long MILL = 546L;
  private static final int YEAR = 2021;
  private static final String USER = "dev-submitter";

  @Mock private Schedule4Repository repository;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule4Service service;

  /**
   * The recompute read at the end of a successful save — kept minimal (content not asserted here).
   */
  private void stubRecompute() {
    lenient().when(repository.findLocations(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findSubPageRows(MILL, YEAR)).thenReturn(List.of());
  }

  /** The nine fixed codes in the order the write path walks them. */
  private static final List<Integer> ALL_FIXED = List.of(40, 41, 42, 44, 45, 49, 50, 51, 53);

  /** The fixed codes a request that sends only {@code kept} leaves empty — the set-based delete. */
  private static List<Integer> fixedExcept(Integer... kept) {
    List<Integer> keep = List.of(kept);
    return ALL_FIXED.stream().filter(code -> !keep.contains(code)).toList();
  }

  @Test
  void save_create_insertsPrimaryBumpsAndWritesFixedPlusDistanceChild() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.nameExists(MILL, YEAR, "New Dump", null)).thenReturn(false);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("New Dump"), isNull(), eq(USER)))
        .thenReturn(9001);
    when(repository.bumpRevision(9001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("New Dump"), eq(bd("60.0")), eq(USER)))
        .thenReturn(9002);
    stubRecompute();

    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            null,
            null,
            "New Dump",
            null,
            List.of(
                new CategoryInput(40, bd("1000"), 50000, null),
                new CategoryInput(47, bd("200"), 8000, bd("60.0")))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).insertReport(MILL, YEAR, "New Dump", null, USER); // primary (distance null)
    verify(repository).bumpRevision(9001, 0, MILL, YEAR, null, USER); // 0 -> 1
    verify(repository).upsertDetail(9001, 40, bd("1000"), 50000, USER); // fixed on primary
    verify(repository).insertReport(MILL, YEAR, "New Dump", bd("60.0"), USER); // distance child
    verify(repository).upsertDetail(9002, 47, bd("200"), 8000, USER); // distance detail
  }

  @Test
  void save_create_nameOnlyLocation_insertsPrimaryOnly() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.nameExists(MILL, YEAR, "Bare Dump", null)).thenReturn(false);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("Bare Dump"), isNull(), eq(USER)))
        .thenReturn(9001);
    when(repository.bumpRevision(9001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    stubRecompute();

    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(null, null, "Bare Dump", null, List.of()),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).insertReport(MILL, YEAR, "Bare Dump", null, USER);
    verify(repository, never()).upsertDetail(anyInt(), anyInt(), any(), any(), anyString());
  }

  @Test
  void save_edit_bumpsExpectedRenamesUpdatesInPlace() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Renamed Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    when(repository.findDistanceChildren(MILL, YEAR, "Renamed Dump"))
        .thenReturn(Map.of(47, List.of(8002)));
    stubRecompute();

    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001,
            0,
            "Renamed Dump",
            null,
            List.of(
                new CategoryInput(40, bd("1500"), 60000, null),
                new CategoryInput(47, bd("250"), 9000, bd("70.0")))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).bumpRevision(8001, 0, MILL, YEAR, null, USER);
    verify(repository).renameFamily(MILL, YEAR, "Existing Dump", "Renamed Dump", USER);
    verify(repository).upsertDetail(8001, 40, bd("1500"), 60000, USER);
    verify(repository).updateReportDistance(8002, bd("70.0"), USER);
    verify(repository).upsertDetail(8002, 47, bd("250"), 9000, USER);
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
  }

  @Test
  void save_edit_sameName_doesNotRename() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    stubRecompute();

    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001,
            0,
            "Existing Dump",
            null,
            List.of(new CategoryInput(40, bd("1500"), 60000, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository, never())
        .renameFamily(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  void save_clearDistanceCategory_deletesChildReport() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    when(repository.findDistanceChildren(MILL, YEAR, "Existing Dump"))
        .thenReturn(Map.of(47, List.of(8002)));
    stubRecompute();

    // A distance category with all-null amounts clears it: the child report is deleted.
    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001, 0, "Existing Dump", null, List.of(new CategoryInput(47, null, null, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).deleteReport(8002);
    verify(repository, never()).updateReportDistance(anyInt(), any(), anyString());
  }

  // ---- #335: the request's category list is the location's complete desired state on an edit.
  // A category the client no longer sends — the user emptied its last value, so `buildRequest`
  // omits it — must be cleared, not left as it was stored.

  @Test
  void save_edit_omittedFixedCategory_deletesItsDetailRow() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    stubRecompute();

    // Stored: fixed 40 and 41. Sent: only 41 — the user cleared 40's last value.
    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001, 0, "Existing Dump", null, List.of(new CategoryInput(41, bd("7"), 70, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).upsertDetail(8001, 41, bd("7"), 70, USER); // the survivor is written
    // Every fixed code not sent goes in ONE statement — 40 among them, 41 never.
    verify(repository).deleteDetails(8001, fixedExcept(41));
  }

  @Test
  void save_edit_omittedDistanceCategory_deletesItsChildReport() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    // Stored: distance child 8002 for code 47; 48 and 52 were never entered. ONE read serves all
    // three codes.
    when(repository.findDistanceChildren(MILL, YEAR, "Existing Dump"))
        .thenReturn(Map.of(47, List.of(8002)));
    stubRecompute();

    // Sent: only fixed 40 — the user emptied Truck Barge/Ferry's distance, volume and cost.
    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001,
            0,
            "Existing Dump",
            null,
            List.of(new CategoryInput(40, bd("1500"), 60000, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).upsertDetail(8001, 40, bd("1500"), 60000, USER);
    verify(repository).deleteReport(8002); // the omitted distance child goes
    verify(repository).deleteDetails(8001, fixedExcept(40)); // the sent fixed category stays
    // Never-entered distance codes have no child to delete, and nothing is inserted for them.
    verify(repository).findDistanceChildren(MILL, YEAR, "Existing Dump");
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
  }

  @Test
  void save_clearFixedCategory_allNull_deletesDetail_neverInsertsAnEmptyRow() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    stubRecompute();

    // A fixed category sent with all-null amounts is the explicit form of "cleared": the same
    // outcome as omitting it, and never an all-null row (which the read would list as a category).
    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001, 0, "Existing Dump", null, List.of(new CategoryInput(40, null, null, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).deleteDetails(8001, ALL_FIXED); // 40 is in the delete like any absent code
    verify(repository, never()).upsertDetail(anyInt(), anyInt(), any(), any(), anyString());
  }

  @Test
  void save_edit_partialClear_keepsTheCategoryAndWritesTheNullThrough() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    stubRecompute();

    // The other half of the #335 boundary: Cost emptied, Volume kept. The category is still
    // sent, so it is upserted with the null — this already worked and must keep working.
    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001, 0, "Existing Dump", null, List.of(new CategoryInput(40, bd("400"), null, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).upsertDetail(8001, 40, bd("400"), null, USER);
    verify(repository).deleteDetails(8001, fixedExcept(40)); // 40 is kept out of the delete
  }

  @Test
  void save_create_runsTheSameWritePath_itsClearsAreNoOps() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.nameExists(MILL, YEAR, "New Dump", null)).thenReturn(false);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("New Dump"), isNull(), eq(USER)))
        .thenReturn(9001);
    when(repository.bumpRevision(9001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    stubRecompute();

    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            null, null, "New Dump", null, List.of(new CategoryInput(40, bd("1000"), 50000, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).upsertDetail(9001, 40, bd("1000"), 50000, USER);
    // No create-vs-edit branch: the empties are written as clears, which touch nothing on a fresh
    // family — one no-op delete on the new primary, one read that finds no children.
    verify(repository).deleteDetails(9001, fixedExcept(40));
    verify(repository).findDistanceChildren(MILL, YEAR, "New Dump");
    verify(repository, never()).deleteReport(anyInt());
  }

  // ---- #335 review (PR #510): legacy-shaped families the 546/547 fixtures never are.

  @Test
  void save_edit_legacyFamilyWithoutPrimary_neverDeletesTheIdentityReport() {
    // No distance-null primary, so the document serves the lowest report id — 7001, a code-47
    // child that also carries the fixed rows — as the location's id, and that is what the edit is
    // addressed to. Clearing 47 must clear that report's 47 detail, not delete the report (and with
    // it the fixed rows just upserted onto it and the location itself).
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(7001, MILL, YEAR)).thenReturn(Optional.of("Old Dump"));
    when(repository.nameExists(MILL, YEAR, "Old Dump", "Old Dump")).thenReturn(false);
    when(repository.bumpRevision(7001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    when(repository.findDistanceChildren(MILL, YEAR, "Old Dump"))
        .thenReturn(Map.of(47, List.of(7001), 48, List.of(7002)));
    stubRecompute();

    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            7001, 0, "Old Dump", null, List.of(new CategoryInput(40, bd("1000"), 50000, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).upsertDetail(7001, 40, bd("1000"), 50000, USER); // fixed row on the identity
    verify(repository).deleteDetails(7001, List.of(47)); // its own code: the detail goes…
    verify(repository, never()).deleteReport(7001); // …the report never does
    verify(repository).deleteReport(7002); // an ordinary child goes whole
  }

  @Test
  void save_edit_duplicateChildrenForOneCode_clearingDeletesEveryOne() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    // Legacy data: two children for code 47. A first-row-only lookup deleted 8101 and left 8105 to
    // bring its figures back on the next read, save after save.
    when(repository.findDistanceChildren(MILL, YEAR, "Existing Dump"))
        .thenReturn(Map.of(47, List.of(8101, 8105)));
    stubRecompute();

    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001,
            0,
            "Existing Dump",
            null,
            List.of(new CategoryInput(40, bd("1500"), 60000, null))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).deleteReport(8101);
    verify(repository).deleteReport(8105);
  }

  @Test
  void save_edit_duplicateChildrenForOneCode_keepingCollapsesIntoTheFirst() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 0, MILL, YEAR, null, USER)).thenReturn(1);
    when(repository.findDistanceChildren(MILL, YEAR, "Existing Dump"))
        .thenReturn(Map.of(47, List.of(8101, 8105)));
    stubRecompute();

    // The form has one cell per code, so one child per code is the desired state: the first is
    // updated with what was entered and the duplicate goes.
    service.saveLocation(
        MILL,
        YEAR,
        new Schedule4LocationRequest(
            8001,
            0,
            "Existing Dump",
            null,
            List.of(new CategoryInput(47, bd("250"), 9000, bd("70.0")))),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).updateReportDistance(8101, bd("70.0"), USER);
    verify(repository).upsertDetail(8101, 47, bd("250"), 9000, USER);
    verify(repository).deleteReport(8105);
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
  }

  @Test
  void save_duplicateName_throwsConflict_writesNothing() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Rival Dump", "Existing Dump")).thenReturn(true);

    assertThrows(
        LocationNameConflictException.class,
        () ->
            service.saveLocation(
                MILL,
                YEAR,
                new Schedule4LocationRequest(8001, 0, "Rival Dump", null, List.of()),
                CallerRights.SUBMITTER,
                USER));

    verify(repository, never())
        .bumpRevision(anyInt(), anyInt(), anyLong(), anyInt(), isNull(), anyString());
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
    verify(repository, never())
        .renameFamily(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  void save_edit_foreignId_notInContext_throwsNotFound_writesNothing() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    // The id is not a category-4 report for THIS mill/year (foreign / cross-context) -> 404, and
    // the
    // request must not mutate anything (the IDOR guard SScholefield flagged).
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.empty());

    assertThrows(
        ScheduleNotFoundException.class,
        () ->
            service.saveLocation(
                MILL,
                YEAR,
                new Schedule4LocationRequest(8001, 0, "Whatever", null, List.of()),
                CallerRights.SUBMITTER,
                USER));

    verify(repository, never()).nameExists(anyLong(), anyInt(), anyString(), any());
    verify(repository, never())
        .bumpRevision(anyInt(), anyInt(), anyLong(), anyInt(), isNull(), anyString());
    verify(repository, never())
        .renameFamily(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  void save_notDraft_throwsNotEditable_writesNothing() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("S"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(
        ScheduleNotEditableException.class,
        () ->
            service.saveLocation(
                MILL,
                YEAR,
                new Schedule4LocationRequest(null, null, "X", null, List.of()),
                CallerRights.SUBMITTER,
                USER));

    verify(repository, never()).nameExists(anyLong(), anyInt(), anyString(), any());
    verify(repository, never()).insertReport(anyLong(), anyInt(), anyString(), any(), anyString());
  }

  @Test
  void save_staleRevision_throwsConflict_neverUpserts() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));
    when(repository.nameExists(MILL, YEAR, "Existing Dump", "Existing Dump")).thenReturn(false);
    when(repository.bumpRevision(8001, 5, MILL, YEAR, null, USER)).thenReturn(0);

    assertThrows(
        StaleRevisionException.class,
        () ->
            service.saveLocation(
                MILL,
                YEAR,
                new Schedule4LocationRequest(
                    8001,
                    5,
                    "Existing Dump",
                    null,
                    List.of(new CategoryInput(40, bd("1"), 1, null))),
                CallerRights.SUBMITTER,
                USER));

    verify(repository, never()).upsertDetail(anyInt(), anyInt(), any(), any(), anyString());
    verify(repository, never())
        .renameFamily(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  void save_persistenceFailure_translatesToScheduleNotSaved() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.nameExists(MILL, YEAR, "New Dump", null)).thenReturn(false);
    when(repository.insertReport(eq(MILL), eq(YEAR), eq("New Dump"), isNull(), eq(USER)))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(
        ScheduleNotSavedException.class,
        () ->
            service.saveLocation(
                MILL,
                YEAR,
                new Schedule4LocationRequest(null, null, "New Dump", null, List.of()),
                CallerRights.SUBMITTER,
                USER));
  }

  @Test
  void delete_draftWithLocation_deletesFamily() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocationName(8001, MILL, YEAR)).thenReturn(Optional.of("Existing Dump"));

    service.deleteLocation(MILL, YEAR, 8001, CallerRights.SUBMITTER);

    verify(repository).deleteFamily(MILL, YEAR, "Existing Dump");
  }

  @Test
  void delete_unknownOrForeignId_isIdempotentNoOp() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    // Unknown in this context (also covers a foreign id: mill/year-scoped lookup returns empty).
    when(repository.findLocationName(9999, MILL, YEAR)).thenReturn(Optional.empty());

    service.deleteLocation(MILL, YEAR, 9999, CallerRights.SUBMITTER); // must not throw

    verify(repository, never()).deleteFamily(anyLong(), anyInt(), anyString());
  }

  @Test
  void delete_notDraft_throwsNotEditable() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("S"));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.deleteLocation(MILL, YEAR, 8001, CallerRights.SUBMITTER));

    verify(repository, never()).deleteFamily(anyLong(), anyInt(), anyString());
  }
}
