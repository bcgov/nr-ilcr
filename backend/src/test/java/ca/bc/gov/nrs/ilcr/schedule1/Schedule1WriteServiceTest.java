package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request.EntryAmount;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request.LineItemInput;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request.SilvicultureInput;
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

  @Mock
  private Schedule1Repository repository;

  @InjectMocks
  private Schedule1Service service;

  private Schedule1Request request(int revision, LineItemInput... items) {
    return new Schedule1Request(
        revision, "c", List.of(items), null, new BigDecimal("8000"), null, null);
  }

  private void stubDraftSummary() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));
    lenient().when(repository.findDetails(SUMMARY_ID)).thenReturn(List.of());
  }

  @Test
  void save_happyPath_bumpsRevisionAndWritesOnlyWritableCodes() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule1(
        MILL, YEAR,
        request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)),
        true, USER);

    verify(repository).upsertFixedDetail(SUMMARY_ID, 12, new BigDecimal("2000"), 60000, USER);
    // the shared Other-Costs volume row (code 19) is written from otherCostsVolume
    verify(repository).upsertFixedDetail(eq(SUMMARY_ID), eq(19), any(), eq(null), eq(USER));
  }

  @Test
  void save_143And144_writeVolumeOnly_costNeverWritten() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    // 143/144 VOLUME is user-entered (via the dedicated fields); their COST is pulled/derived and must
    // never be written. A 143/144 sent through the lineItems channel is ignored (only 12–18 write there).
    service.saveSchedule1(
        MILL, YEAR,
        new Schedule1Request(0, "c",
            List.of(new LineItemInput(12, new BigDecimal("2000"), 60000),
                new LineItemInput(144, new BigDecimal("5"), 999),
                new LineItemInput(143, new BigDecimal("5"), 999)),
            null, new BigDecimal("8000"),
            new BigDecimal("111"), new BigDecimal("222")),
        true, USER);

    verify(repository).upsertFixedDetail(eq(SUMMARY_ID), eq(12), any(), any(), eq(USER));
    // Volume-only writes for 143/144 (null cost), from the dedicated volume fields.
    verify(repository).upsertFixedDetail(SUMMARY_ID, 143, new BigDecimal("111"), null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 144, new BigDecimal("222"), null, USER);
    // The lineItems-channel 143/144 cost (999) is never persisted.
    verify(repository, never()).upsertFixedDetail(eq(SUMMARY_ID), eq(143), any(), eq(999), anyString());
    verify(repository, never()).upsertFixedDetail(eq(SUMMARY_ID), eq(144), any(), eq(999), anyString());
  }

  @Test
  void save_silviculture139And140_writeVolumeOnly() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule1(
        MILL, YEAR,
        new Schedule1Request(0, "c", List.of(), new SilvicultureInput(
            new EntryAmount(new BigDecimal("100"), 500),
            new EntryAmount(new BigDecimal("50"), 300),
            new BigDecimal("77"),   // 139 volume
            new BigDecimal("88")),  // 140 volume
            new BigDecimal("8000"), null, null),
        true, USER);

    verify(repository).upsertFixedDetail(SUMMARY_ID, 139, new BigDecimal("77"), null, USER);
    verify(repository).upsertFixedDetail(SUMMARY_ID, 140, new BigDecimal("88"), null, USER);
  }

  @Test
  void save_staleRevision_throwsConflict() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(0);

    assertThrows(StaleRevisionException.class, () ->
        service.saveSchedule1(MILL, YEAR,
            request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)), true, USER));

    verify(repository, never()).upsertFixedDetail(anyInt(), anyInt(), any(), any(), anyString());
  }

  @Test
  void save_notDraft_throwsNotEditable_andNeverWrites() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));

    assertThrows(ScheduleNotEditableException.class, () ->
        service.saveSchedule1(MILL, YEAR,
            request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)), true, USER));

    verify(repository, never()).bumpRevision(anyInt(), anyInt(), anyString(), anyString());
  }

  @Test
  void save_persistenceFailure_translatesToScheduleNotSaved() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER)))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(ScheduleNotSavedException.class, () ->
        service.saveSchedule1(MILL, YEAR,
            request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)), true, USER));
  }

  @Test
  void delete_notDraft_throwsNotEditable() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    assertThrows(ScheduleNotEditableException.class, () -> service.deleteSchedule1(MILL, YEAR));
    verify(repository, never()).deleteSchedule(anyInt());
  }

  @Test
  void delete_draft_deletesSummary() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));

    service.deleteSchedule1(MILL, YEAR);

    verify(repository).deleteSchedule(SUMMARY_ID);
  }

  @Test
  void save_missingSummary_throwsNotFound() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1")).thenReturn(Optional.empty());
    assertThrows(ScheduleNotFoundException.class, () ->
        service.saveSchedule1(MILL, YEAR,
            request(0, new LineItemInput(12, new BigDecimal("2000"), 60000)), true, USER));
  }

  @Test
  void save_nullRevision_treatedAsFirstWrite() {
    stubDraftSummary();
    // A null optimistic-lock token means "no prior revision" -> expectedRevision -1 (first write).
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(-1), anyString(), eq(USER))).thenReturn(1);

    service.saveSchedule1(
        MILL, YEAR,
        new Schedule1Request(null, "c",
            List.of(new LineItemInput(12, new BigDecimal("2000"), 60000)),
            null, new BigDecimal("8000"), null, null),
        true, USER);

    verify(repository).bumpRevision(eq(SUMMARY_ID), eq(-1), anyString(), eq(USER));
  }

  @Test
  void delete_persistenceFailure_translatesToScheduleNotSaved() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));
    doThrow(new DataIntegrityViolationException("boom")).when(repository).deleteSchedule(SUMMARY_ID);

    assertThrows(ScheduleNotSavedException.class, () -> service.deleteSchedule1(MILL, YEAR));
  }

  @Test
  void save_nullLineItemsAndSilviculture_skipsOptionalWrites_writesSharedVolumeOnly() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    // null lineItems and null silviculture skip those write branches entirely; only the shared
    // Other-Costs volume (code 19) is written.
    service.saveSchedule1(
        MILL, YEAR,
        new Schedule1Request(0, "c", null, null, new BigDecimal("8000"), null, null),
        true, USER);

    verify(repository).upsertFixedDetail(eq(SUMMARY_ID), eq(19), any(), eq(null), eq(USER));
    verify(repository, never()).upsertFixedDetail(eq(SUMMARY_ID), eq(12), any(), any(), anyString());
  }

  @Test
  void save_nonWritableCodeAndNullSilvFields_persistNothingOptional() {
    stubDraftSummary();
    when(repository.bumpRevision(eq(SUMMARY_ID), eq(0), anyString(), eq(USER))).thenReturn(1);

    // A non-writable line-item code (99) is skipped; a present silviculture block whose sub-fields
    // are all null writes none of them; a null otherCostsVolume leaves code 19 untouched.
    service.saveSchedule1(
        MILL, YEAR,
        new Schedule1Request(0, "c",
            List.of(new LineItemInput(99, new BigDecimal("1"), 1)),
            new SilvicultureInput(null, null, null, null),
            null, null, null),
        true, USER);

    verify(repository, never()).upsertFixedDetail(eq(SUMMARY_ID), eq(99), any(), any(), anyString());
    verify(repository, never()).upsertFixedDetail(eq(SUMMARY_ID), eq(19), any(), any(), anyString());
  }
}
