package ca.bc.gov.nrs.ilcr.schedule2;

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
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Request;
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
 * Unit test for the Schedule 2 write path (Story 3.2). Mocked repository — no DB, no Spring — so it
 * isolates the create-on-absent divergence, the Draft gate, optimistic-lock handling, item-25/26
 * upserts, clear-to-null, idempotent delete, and persistence-failure rollback translation.
 */
@ExtendWith(MockitoExtension.class)
class Schedule2WriteServiceTest {

  private static final long MILL = 522L;
  private static final int YEAR = 2021;
  private static final int SUMMARY_ID = 1022;
  private static final String USER = "dev-submitter";

  @Mock
  private Schedule2Repository repository;

  @InjectMocks
  private Schedule2Service service;

  private static Schedule2Request request(Integer revision, Integer cost25,
      BigDecimal vol26, Integer cost26) {
    return new Schedule2Request(revision, "c", cost25, vol26, cost26);
  }

  private void stubDraftExistingSummary() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, "c", 0)));
    // Recompute read after write — keep it minimal (no cross-schedule data).
    lenient().when(repository.findDetails(SUMMARY_ID)).thenReturn(List.of());
    lenient().when(repository.findSch3PopTimberVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3PopActualCost(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3CrownVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch1SubtotalLoggingCost(MILL, YEAR)).thenReturn(Optional.empty());
  }

  @Test
  void save_update_bumpsRevisionAndUpsertsBothItems() {
    stubDraftExistingSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule2(MILL, YEAR, request(0, 500000, new BigDecimal("2000"), 100000), true, USER);

    // item 25 — cost only (volume always null; carried from Sch3, never written).
    verify(repository).upsertDetail(SUMMARY_ID, 25, null, 500000, USER);
    // item 26 — volume + cost.
    verify(repository).upsertDetail(SUMMARY_ID, 26, new BigDecimal("2000"), 100000, USER);
  }

  @Test
  void save_createOnAbsent_insertsSummaryThenBumpsToOne() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.empty())            // create-on-absent lookup
        .thenReturn(Optional.of(new SummaryRow(9001, "c", 1))); // recompute read after save
    when(repository.insertSummary(MILL, YEAR, "c", USER)).thenReturn(9001);
    when(repository.bumpRevision(eq(9001), eq(0), anyString(), eq(USER))).thenReturn(1);
    lenient().when(repository.findDetails(9001)).thenReturn(List.of());
    lenient().when(repository.findSch3PopTimberVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3PopActualCost(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3CrownVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch1SubtotalLoggingCost(MILL, YEAR)).thenReturn(Optional.empty());

    // null revisionCount from the client means "new/unsaved" -> matches the freshly-inserted 0.
    service.saveSchedule2(MILL, YEAR, request(null, 500000, new BigDecimal("2000"), 100000), true, USER);

    verify(repository).insertSummary(MILL, YEAR, "c", USER);
    verify(repository).bumpRevision(9001, 0, "c", USER); // 0 -> 1
    verify(repository).upsertDetail(9001, 25, null, 500000, USER);
    verify(repository).upsertDetail(9001, 26, new BigDecimal("2000"), 100000, USER);
  }

  @Test
  void save_clearItem25Cost_persistsNull() {
    stubDraftExistingSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule2(MILL, YEAR, request(0, null, new BigDecimal("2000"), 100000), true, USER);

    verify(repository).upsertDetail(eq(SUMMARY_ID), eq(25), isNull(), isNull(), eq(USER));
  }

  @Test
  void save_staleRevision_throwsConflict_andNeverUpserts() {
    stubDraftExistingSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(0);

    assertThrows(StaleRevisionException.class, () ->
        service.saveSchedule2(MILL, YEAR, request(0, 500000, new BigDecimal("2000"), 100000), true, USER));

    verify(repository, never()).upsertDetail(anyInt(), anyInt(), any(), any(), anyString());
  }

  @Test
  void save_notDraft_throwsNotEditable_andNeverWrites() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(ScheduleNotEditableException.class, () ->
        service.saveSchedule2(MILL, YEAR, request(0, 500000, new BigDecimal("2000"), 100000), true, USER));

    verify(repository, never()).bumpRevision(anyInt(), anyInt(), anyString(), anyString());
    verify(repository, never()).insertSummary(anyLong(), anyInt(), anyString(), anyString());
  }

  @Test
  void save_persistenceFailure_translatesToScheduleNotSaved() {
    stubDraftExistingSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER)))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(ScheduleNotSavedException.class, () ->
        service.saveSchedule2(MILL, YEAR, request(0, 500000, new BigDecimal("2000"), 100000), true, USER));
  }

  @Test
  void delete_draftWithSummary_deletesSchedule() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, "c", 0)));

    service.deleteSchedule2(MILL, YEAR);

    verify(repository).deleteSchedule(SUMMARY_ID);
  }

  @Test
  void delete_draftNoSummary_isIdempotentNoOp() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());

    service.deleteSchedule2(MILL, YEAR); // must not throw

    verify(repository, never()).deleteSchedule(anyInt());
  }

  @Test
  void delete_notDraft_throwsNotEditable() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(ScheduleNotEditableException.class, () -> service.deleteSchedule2(MILL, YEAR));

    verify(repository, never()).deleteSchedule(anyInt());
  }
}
