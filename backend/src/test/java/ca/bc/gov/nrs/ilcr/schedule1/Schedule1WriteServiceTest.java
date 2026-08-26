package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request.EntryAmount;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request.LineItemInput;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request.SilvicultureInput;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation.Schedule1Sources;
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
 * Unit test for the Schedule 1 write path (Story 2.1, AC 1/2/4/5/7). Mocked repository — no DB, no
 * Spring — so it isolates the Draft gate, optimistic-lock handling, writable-code filtering, and
 * persistence-failure rollback translation.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1WriteServiceTest {

  private static final long MILL = 518L;
  private static final int YEAR = 2021;
  private static final int SUMMARY_ID = 1018;
  private static final String USER = "dev-submitter";

  @Mock private Schedule1Repository repository;

  @Mock private Schedule3CostDerivation schedule3CostDerivation;

  @InjectMocks private Schedule1Service service;

  private Schedule1Request request(int revision, LineItemInput... items) {
    return new Schedule1Request(
        revision, "c", List.of(items), null, new BigDecimal("8000"), null, null);
  }

  private void stubDraftSummary() {
    // The main save path takes the FOR UPDATE lock (defect #296 create-on-absent serialization);
    // delete and the sub-pages still use the plain read. Both lenient so either shape is fine.
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));
    lenient().when(repository.findDetails(SUMMARY_ID)).thenReturn(List.of());
    // saveSchedule1 reloads via getSchedule1 → no Schedule 3 sources needed for these write
    // assertions.
    lenient()
        .when(schedule3CostDerivation.schedule1Sources(MILL, YEAR))
        .thenReturn(new Schedule1Sources(null, null, null));
  }

  @Test
  void save_happyPath_bumpsRevisionAndWritesOnlyWritableCodes() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule1(
        MILL, YEAR, request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)), true, USER);

    verify(repository).upsertFixedDetail(SUMMARY_ID, 12, new BigDecimal("2000"), 60000, USER);
    // the shared Other-Costs volume row (code 19) is written from otherCostsVolume
    verify(repository).upsertFixedDetail(eq(SUMMARY_ID), eq(19), any(), eq(null), eq(USER));
  }

  @Test
  void save_143And144_writeVolumeOnly_costNeverWritten() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    // 143/144 VOLUME is user-entered (via the dedicated fields); their COST is pulled/derived and
    // must
    // never be written. A 143/144 sent through the lineItems channel is ignored (only 12–18 write
    // there).
    service.saveSchedule1(
        MILL,
        YEAR,
        new Schedule1Request(
            0,
            "c",
            List.of(
                new LineItemInput(12, new BigDecimal("2000"), 60000),
                new LineItemInput(144, new BigDecimal("5"), 999),
                new LineItemInput(143, new BigDecimal("5"), 999)),
            null,
            new BigDecimal("8000"),
            new BigDecimal("111"),
            new BigDecimal("222")),
        true,
        USER);

    verify(repository).upsertFixedDetail(eq(SUMMARY_ID), eq(12), any(), any(), eq(USER));
    // Volume-only writes for 143/144 (null cost), from the dedicated volume fields.
    verify(repository).upsertFixedDetail(SUMMARY_ID, 143, new BigDecimal("111"), null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 144, new BigDecimal("222"), null, USER);
    // The lineItems-channel 143/144 cost (999) is never persisted.
    verify(repository, never())
        .upsertFixedDetail(eq(SUMMARY_ID), eq(143), any(), eq(999), anyString());
    verify(repository, never())
        .upsertFixedDetail(eq(SUMMARY_ID), eq(144), any(), eq(999), anyString());
  }

  @Test
  void save_silviculture139And140_writeVolumeOnly() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule1(
        MILL,
        YEAR,
        new Schedule1Request(
            0,
            "c",
            List.of(),
            new SilvicultureInput(
                new EntryAmount(new BigDecimal("100"), 500),
                new EntryAmount(new BigDecimal("50"), 300),
                new BigDecimal("77"), // 139 volume
                new BigDecimal("88")), // 140 volume
            new BigDecimal("8000"),
            null,
            null),
        true,
        USER);

    verify(repository).upsertFixedDetail(SUMMARY_ID, 139, new BigDecimal("77"), null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 140, new BigDecimal("88"), null, USER);
  }

  @Test
  void save_staleRevision_throwsConflict() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(0);

    assertThrows(
        StaleRevisionException.class,
        () ->
            service.saveSchedule1(
                MILL,
                YEAR,
                request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)),
                true,
                USER));

    verify(repository, never()).upsertFixedDetail(anyInt(), anyInt(), any(), any(), anyString());
  }

  @Test
  void save_notDraft_throwsNotEditable_andNeverWrites() {
    // The main save path reads the status FOR UPDATE (defect #296 create-on-absent serialization).
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(
        ScheduleNotEditableException.class,
        () ->
            service.saveSchedule1(
                MILL,
                YEAR,
                request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)),
                true,
                USER));

    verify(repository, never()).bumpRevision(anyInt(), anyInt(), anyString(), anyString());
  }

  @Test
  void save_persistenceFailure_translatesToScheduleNotSaved() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER)))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(
        ScheduleNotSavedException.class,
        () ->
            service.saveSchedule1(
                MILL,
                YEAR,
                request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)),
                true,
                USER));
  }

  @Test
  void delete_notDraft_throwsNotEditable() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("S"));
    assertThrows(ScheduleNotEditableException.class, () -> service.deleteSchedule1(MILL, YEAR));
    verify(repository, never()).deleteSchedule(anyInt());
  }

  @Test
  void delete_draft_deletesSummary() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));

    assertTrue(service.deleteSchedule1(MILL, YEAR));

    verify(repository).deleteSchedule(SUMMARY_ID);
    // DELETE must take the LOCKING status read, as Schedule 2's does: without it a delete racing a
    // first-save reports "nothing was deleted" for a row that then commits (#296 code review).
    verify(repository).findTrackStatusForUpdate(MILL, YEAR);
    verify(repository, never()).findTrackStatus(MILL, YEAR);
  }

  /**
   * Defect #296: a Draft mill/year with no category-"1" summary is the legitimate unsaved state, so
   * DELETE is an idempotent no-op that returns false (never 404) — the controller then says
   * "nothing was deleted" rather than announcing success, as Schedule 2's has since the #292
   * review.
   */
  @Test
  void delete_noSummary_isIdempotentNoOp() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1")).thenReturn(Optional.empty());

    assertFalse(service.deleteSchedule1(MILL, YEAR));

    verify(repository, never()).deleteSchedule(anyInt());
  }

  /**
   * Defect #296, the heart of it: the FIRST save on a mill/year with no summary must CREATE the
   * summary rather than 404, so a Schedule 1 can be started at all. Before the fix this threw
   * ScheduleNotFoundException and there was no route by which a Schedule 1 could ever be created.
   */
  @Test
  void save_missingSummary_createsIt() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    // Absent on the write-path probe, present on the post-create reload (getSchedule1).
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));
    when(repository.insertSummary(eq(MILL), eq(YEAR), anyString(), eq(USER)))
        .thenReturn(SUMMARY_ID);
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);
    when(repository.findDetails(SUMMARY_ID)).thenReturn(List.of());
    when(schedule3CostDerivation.schedule1Sources(MILL, YEAR))
        .thenReturn(new Schedule1Sources(null, null, null));

    service.saveSchedule1(
        MILL, YEAR, request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)), true, USER);

    verify(repository).insertSummary(eq(MILL), eq(YEAR), anyString(), eq(USER));
    verify(repository).bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER));
  }

  /**
   * The Draft gate still bites on the create path — a non-Draft track is 409, not a silent create.
   */
  @Test
  void save_missingSummary_notDraft_stillNotEditable() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("S"));
    assertThrows(
        ScheduleNotEditableException.class,
        () ->
            service.saveSchedule1(
                MILL,
                YEAR,
                request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)),
                true,
                USER));
    verify(repository, never()).insertSummary(anyLong(), anyInt(), anyString(), anyString());
  }

  @Test
  void save_nullRevision_treatedAsFirstWrite() {
    stubDraftSummary();
    // A null optimistic-lock token coalesces to 0, matching Schedule 2 — and it has to, now that
    // this
    // path can CREATE: a freshly-MERGEd summary starts at REVISION_COUNT 0, which -1 could never
    // match (#296 code review).
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule1(
        MILL,
        YEAR,
        new Schedule1Request(
            null,
            "c",
            List.of(new LineItemInput(12, new BigDecimal("2000"), 60000)),
            null,
            new BigDecimal("8000"),
            null,
            null),
        true,
        USER);

    verify(repository).bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER));
  }

  @Test
  void delete_persistenceFailure_translatesToScheduleNotSaved() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));
    doThrow(new DataIntegrityViolationException("boom"))
        .when(repository)
        .deleteSchedule(SUMMARY_ID);

    assertThrows(ScheduleNotSavedException.class, () -> service.deleteSchedule1(MILL, YEAR));
  }

  @Test
  void save_nullLineItemsAndSilviculture_skipsOptionalWrites_writesSharedVolumeOnly() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    // null lineItems and null silviculture skip those write branches entirely; only the shared
    // Other-Costs volume (code 19) is written.
    service.saveSchedule1(
        MILL,
        YEAR,
        new Schedule1Request(0, "c", null, null, new BigDecimal("8000"), null, null),
        true,
        USER);

    verify(repository).upsertFixedDetail(eq(SUMMARY_ID), eq(19), any(), eq(null), eq(USER));
    verify(repository, never())
        .upsertFixedDetail(eq(SUMMARY_ID), eq(12), any(), any(), anyString());
  }

  @Test
  void save_nonWritableCode_isSkipped_butNullVolumesClear() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    // A non-writable line-item code (99) is skipped. The absent 1 / 2 entries write nothing, but
    // the
    // five volume-only fields are a PUT of the entered set: null means the user emptied the box, so
    // each is written through as null to CLEAR the stored volume (never silently left untouched).
    service.saveSchedule1(
        MILL,
        YEAR,
        new Schedule1Request(
            0,
            "c",
            List.of(new LineItemInput(99, new BigDecimal("1"), 1)),
            new SilvicultureInput(null, null, null, null),
            null,
            null,
            null),
        true,
        USER);

    verify(repository, never())
        .upsertFixedDetail(eq(SUMMARY_ID), eq(99), any(), any(), anyString());
    verify(repository, never()).upsertFixedDetail(eq(SUMMARY_ID), eq(1), any(), any(), anyString());
    verify(repository, never()).upsertFixedDetail(eq(SUMMARY_ID), eq(2), any(), any(), anyString());
    verify(repository).upsertFixedDetail(SUMMARY_ID, 19, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 139, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 140, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 143, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 144, null, null, USER);
  }

  @Test
  void save_clearedVolumeFields_overwriteStoredValuesWithNull() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    // The reported bug: emptying any of the five volume-only boxes reported Save success but the
    // old
    // number came back on reload, because a null was read as "field omitted, leave it alone". Every
    // one
    // of them must reach the repository as a null write. The 1 / 2 volumes (sent inside a present
    // entry)
    // clear the same way, so the whole cleared-row case is covered here.
    service.saveSchedule1(
        MILL,
        YEAR,
        new Schedule1Request(
            0,
            "c",
            List.of(new LineItemInput(12, null, null)),
            new SilvicultureInput(
                new EntryAmount(null, null), new EntryAmount(null, null), null, null),
            null,
            null,
            null),
        true,
        USER);

    verify(repository).upsertFixedDetail(SUMMARY_ID, 12, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 1, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 2, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 139, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 140, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 19, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 143, null, null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 144, null, null, USER);
  }
}
