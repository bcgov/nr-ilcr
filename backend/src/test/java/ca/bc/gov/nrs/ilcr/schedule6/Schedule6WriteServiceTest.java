package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.GeneralCommentsRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for the {@link Schedule6Service} write path (Story 8.2): the BR-02 counterpart-clear
 * matrix, the BR-03 TFL alias/validation matrix (case-sensitive, {@code "52B"} dead-code rejected),
 * BR-09 branch selection (placeholder reuse / fresh insert / the three general-comment branches),
 * the detail upsert on edit, the 404-vs-409 disambiguation, the Draft gate, and the
 * DataAccessException translation. Pure JUnit + Mockito — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule6Service — write path (BR-02/BR-03/BR-09, AR11)")
class Schedule6WriteServiceTest {

  private static final long MILL = 661L;
  private static final int YEAR = 2021;
  private static final String USER = "tester";

  @Mock private Schedule6Repository repository;

  @InjectMocks private Schedule6Service service;

  private void stubDraft() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findRoadRecords(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.nextRoadReportId()).thenReturn(9501);
  }

  private static RoadRecordRequest request(
      String areaType, String tflNumber, String supplyBlock, Integer revisionCount) {
    return new RoadRecordRequest(
        areaType, tflNumber, supplyBlock, new BigDecimal("100"), 5000, "rc", revisionCount);
  }

  // ---- BR-02 counterpart-clear matrix -----------------------------------------------------------

  @Test
  @DisplayName("BR-02: TFL area type stores the TFL number and NULLs both TSA columns")
  void tflWrite_clearsTsaColumns() {
    stubDraft();
    service.addRecord(MILL, YEAR, request("TFL", "18", "01B", null), true, USER);
    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), isNull(), isNull(), eq("18"), eq(USER));
  }

  @Test
  @DisplayName("BR-02: a TSA area type stores TSA+TSB and NULLs the TFL number")
  void tsaWrite_clearsTflColumn() {
    stubDraft();
    service.addRecord(MILL, YEAR, request("01", "18", "01B", null), true, USER);
    // tflNumber was supplied in the request — the counterpart-clear must drop it.
    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), eq("01"), eq("01B"), isNull(), eq(USER));
  }

  @Test
  @DisplayName(
      "BR-02 is case-sensitive: only the exact literal \"TFL\" routes to the TFL side — "
          + "any other area type is stored as a TSA code")
  void nonLiteralAreaType_isTsaSide() {
    stubDraft();
    service.addRecord(MILL, YEAR, request("tf", null, "01B", null), true, USER);
    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), eq("tf"), eq("01B"), isNull(), eq(USER));
  }

  // ---- BR-03 TFL alias + validation matrix ------------------------------------------------------

  @ParameterizedTest(name = "alias \"{0}\" is stored as \"{1}\"")
  @CsvSource({"1,01", "3,03", "5,05", "8,08", "18,18", "62,62"})
  @DisplayName("BR-03: the complete leading-zero alias table normalizes onto the stored value")
  void tflAlias_normalizedOntoStoredValue(String entered, String stored) {
    stubDraft();
    service.addRecord(MILL, YEAR, request("TFL", entered, null, null), true, USER);
    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), isNull(), isNull(), eq(stored), eq(USER));
  }

  @ParameterizedTest(name = "\"{0}\" -> 400 FLD-002")
  @ValueSource(strings = {"99", "2", "52B", "0", " ", ""})
  @DisplayName("BR-03: a TFL number that resolves no RMG -> InvalidTflNumberException, no write")
  void invalidTfl_rejected(String tflNumber) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    assertThrows(
        InvalidTflNumberException.class,
        () -> service.addRecord(MILL, YEAR, request("TFL", tflNumber, null, null), true, USER));
    verify(repository, never())
        .insertRoadReport(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
  }

  @Test
  @DisplayName("BR-03: a TFL record with the number missing entirely -> InvalidTflNumberException")
  void missingTfl_rejected() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    assertThrows(
        InvalidTflNumberException.class,
        () -> service.addRecord(MILL, YEAR, request("TFL", null, null, null), true, USER));
  }

  // ---- BR-09 branch selection on add ------------------------------------------------------------

  @Test
  @DisplayName(
      "BR-09: the lone placeholder is CLAIMED (classification onto that row + its detail); "
          + "no fresh insert, no new id drawn")
  void addRecord_claimsLonePlaceholder() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8331, null, null, null, "kept comment", 0)));
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    when(repository.claimPlaceholder(8331, MILL, YEAR, "01", "01B", null, USER)).thenReturn(1);

    service.addRecord(MILL, YEAR, request("01", null, "01B", null), true, USER);

    verify(repository).claimPlaceholder(8331, MILL, YEAR, "01", "01B", null, USER);
    verify(repository).upsertCostDetail(8331, new BigDecimal("100"), 5000, "rc", USER);
    verify(repository, never())
        .insertRoadReport(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
    verify(repository, never()).nextRoadReportId();
  }

  @Test
  @DisplayName("BR-09: a raced claim (0 rows) falls back to a fresh insert")
  void addRecord_claimRace_fallsBackToInsert() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8331, null, null, null, "gc", 0)));
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    when(repository.claimPlaceholder(8331, MILL, YEAR, "01", "01B", null, USER)).thenReturn(0);
    when(repository.nextRoadReportId()).thenReturn(9501);

    service.addRecord(MILL, YEAR, request("01", null, "01B", null), true, USER);

    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), eq("01"), eq("01B"), isNull(), eq(USER));
  }

  @Test
  @DisplayName(
      "BR-09: with real records present a fresh insert runs, then its detail — in order. "
          + "The general comment is NOT threaded through Java: insertRoadReport sources it in SQL so a "
          + "concurrent general-comments save cannot be reverted, so the replication invariant itself is "
          + "proven by Schedule6GeneralCommentsIT.addRecord_carriesCurrentGeneralComment, not here")
  void addRecord_insertsThenDetail_inOrder() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8334, "01", "01B", null, "the shared comment", 0)));
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    when(repository.nextRoadReportId()).thenReturn(9502);

    service.addRecord(MILL, YEAR, request("03", null, "03B", null), true, USER);

    InOrder order = inOrder(repository);
    order
        .verify(repository)
        .insertRoadReport(eq(9502), eq(MILL), eq(YEAR), eq("03"), eq("03B"), isNull(), eq(USER));
    order.verify(repository).upsertCostDetail(9502, new BigDecimal("100"), 5000, "rc", USER);
    verify(repository, never())
        .claimPlaceholder(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
  }

  // ---- Edit: 404-vs-409 disambiguation + detail upsert ------------------------------------------

  @Test
  @DisplayName("AR11: update hits (1 row) -> the detail is UPSERTED after the master update")
  void updateRecord_upsertsDetailAfterMaster() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.updateRoadReport(8336, MILL, YEAR, 0, null, null, "18", USER)).thenReturn(1);
    lenient().when(repository.findRoadRecords(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

    service.updateRecord(MILL, YEAR, 8336, request("TFL", "18", null, 0), true, USER);

    InOrder order = inOrder(repository);
    order.verify(repository).updateRoadReport(8336, MILL, YEAR, 0, null, null, "18", USER);
    order.verify(repository).upsertCostDetail(8336, new BigDecimal("100"), 5000, "rc", USER);
  }

  @Test
  @DisplayName(
      "AR11: 0 rows + id absent -> 404; 0 rows + id present -> 409 stale; nothing upserted")
  void updateRecord_disambiguates404From409() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.updateRoadReport(79999, MILL, YEAR, 0, "01", "01B", null, USER)).thenReturn(0);
    when(repository.countRoadRecord(79999, MILL, YEAR)).thenReturn(0);
    assertThrows(
        RoadRecordNotFoundException.class,
        () -> service.updateRecord(MILL, YEAR, 79999, request("01", null, "01B", 0), true, USER));

    when(repository.updateRoadReport(8336, MILL, YEAR, 0, "01", "01B", null, USER)).thenReturn(0);
    when(repository.countRoadRecord(8336, MILL, YEAR)).thenReturn(1);
    assertThrows(
        StaleRevisionException.class,
        () -> service.updateRecord(MILL, YEAR, 8336, request("01", null, "01B", 0), true, USER));

    verify(repository, never()).upsertCostDetail(anyInt(), any(), any(), any(), anyString());
  }

  @ParameterizedTest(name = "placeholder classification [{0}] -> 404")
  @ValueSource(strings = {"NULL", "BLANK"})
  @DisplayName(
      "AR11: a placeholder id is 404 BEFORE the update — never converted into a record; "
          + "the guard is trim-aware, so a WHITESPACE-classification placeholder is caught too "
          + "(the IS NULL-only SQL predicate this replaced let that row through)")
  void updateRecord_placeholderId_is404(String shape) {
    String blank = "NULL".equals(shape) ? null : " ";
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8330, blank, blank, blank, "lone", 0)));
    assertThrows(
        RoadRecordNotFoundException.class,
        () -> service.updateRecord(MILL, YEAR, 8330, request("01", null, "01B", 0), true, USER));
    verify(repository, never())
        .updateRoadReport(
            anyInt(), anyLong(), anyInt(), anyInt(), any(), any(), any(), anyString());
  }

  @Test
  @DisplayName(
      "AR11 defence in depth: a null revisionCount reaching the service is a clean 400 "
          + "(RevisionCountRequiredException), never an NPE and never a coerced 409")
  void updateRecord_nullRevisionCount_is400() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    assertThrows(
        RevisionCountRequiredException.class,
        () -> service.updateRecord(MILL, YEAR, 8336, request("01", null, "01B", null), true, USER));
    verify(repository, never())
        .updateRoadReport(
            anyInt(), anyLong(), anyInt(), anyInt(), any(), any(), any(), anyString());
  }

  @Test
  @DisplayName(
      "A TSA area type wider than TSA_NUMBER VARCHAR2(2) is a clean 400, not an ORA-12899 "
          + "500 — the DTO's @Size(max=3) exists for the \"TFL\" literal, so 3 chars reach here")
  void tsaAreaTypeTooWide_is400() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    assertThrows(
        InvalidClassificationCodeException.class,
        () -> service.addRecord(MILL, YEAR, request("999", null, "01B", null), true, USER));
    verify(repository, never())
        .insertRoadReport(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
  }

  // ---- General-comment branch selection ---------------------------------------------------------

  @Test
  @DisplayName("BR-09: rows exist -> updateAllComments (replication); no placeholder ops")
  void saveComments_withRows_replicates() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8334, "01", "01B", null, "old", 0)));
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

    service.saveGeneralComments(MILL, YEAR, new GeneralCommentsRequest("new text"), true, USER);

    verify(repository).updateAllComments(MILL, YEAR, "new text", USER);
    verify(repository, never())
        .insertPlaceholder(anyInt(), anyLong(), anyInt(), any(), anyString());
    verify(repository, never()).deletePlaceholder(anyInt(), anyLong(), anyInt());
  }

  @Test
  @DisplayName("BR-09: zero rows + non-blank -> insertPlaceholder (raw text, id from the sequence)")
  void saveComments_zeroRows_insertsPlaceholder() {
    stubDraft();
    service.saveGeneralComments(
        MILL, YEAR, new GeneralCommentsRequest("  raw untrimmed  "), true, USER);
    // Stored RAW — the 8.1 legacy-faithful comments decision covers the write side too.
    verify(repository).insertPlaceholder(9501, MILL, YEAR, "  raw untrimmed  ", USER);
    verify(repository, never()).updateAllComments(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName("BR-09: placeholder-only + blank -> deletePlaceholder; zero rows + blank -> no-op")
  void saveComments_blankBranches() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8330, null, null, null, "lone", 0)))
        .thenReturn(List.of())
        .thenReturn(List.of());
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

    when(repository.deletePlaceholder(8330, MILL, YEAR)).thenReturn(1);

    service.saveGeneralComments(MILL, YEAR, new GeneralCommentsRequest("   "), true, USER);
    verify(repository).deletePlaceholder(8330, MILL, YEAR);

    service.saveGeneralComments(MILL, YEAR, new GeneralCommentsRequest(null), true, USER);
    verify(repository, never())
        .insertPlaceholder(anyInt(), anyLong(), anyInt(), any(), anyString());
    verify(repository, never()).updateAllComments(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName(
      "BR-09: a delete that matches NOTHING (whitespace classification the IS NULL SQL "
          + "cannot see, or a placeholder claimed by a concurrent add) falls back to clearing COMMENTS "
          + "in place — never a silent no-op behind \"Data saved successfully\"")
  void saveComments_deleteMatchesNothing_fallsBackToClear() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8330, " ", " ", " ", "lone", 0)));
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    when(repository.deletePlaceholder(8330, MILL, YEAR)).thenReturn(0);

    service.saveGeneralComments(MILL, YEAR, new GeneralCommentsRequest(" "), true, USER);

    verify(repository).deletePlaceholder(8330, MILL, YEAR);
    verify(repository).updateAllComments(MILL, YEAR, null, USER);
  }

  @Test
  @DisplayName(
      "BR-09: records + blank comment -> the clear replicates NULL onto every row "
          + "(not a placeholder delete)")
  void saveComments_recordsPlusBlank_replicatesNull() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8334, "01", "01B", null, "old", 0)));
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

    service.saveGeneralComments(MILL, YEAR, new GeneralCommentsRequest(""), true, USER);

    verify(repository).updateAllComments(MILL, YEAR, null, USER);
    verify(repository, never()).deletePlaceholder(anyInt(), anyLong(), anyInt());
  }

  // ---- Draft gate + failure translation ---------------------------------------------------------

  @ParameterizedTest(name = "track \"{0}\" -> 409, nothing written")
  @ValueSource(strings = {"S", "V", "A"})
  @DisplayName("Deviation (a): every write requires the 1-10 track Draft")
  void nonDraftTrack_rejectsAllWrites(String track) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of(track));
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.addRecord(MILL, YEAR, request("01", null, "01B", null), true, USER));
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.updateRecord(MILL, YEAR, 8336, request("01", null, "01B", 0), true, USER));
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.saveGeneralComments(MILL, YEAR, new GeneralCommentsRequest("x"), true, USER));
    verify(repository, never())
        .insertRoadReport(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
    verify(repository, never()).updateAllComments(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName("A missing track-status row can never be Draft -> 409")
  void missingTrackStatus_rejects() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.empty());
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.addRecord(MILL, YEAR, request("01", null, "01B", null), true, USER));
  }

  @Test
  @DisplayName("AD-11: a DataAccessException surfaces as ScheduleNotSavedException (500 ERR-004)")
  void dataAccessFailure_translated() {
    stubDraft();
    when(repository.findRoadRecords(MILL, YEAR))
        .thenThrow(new DataAccessResourceFailureException("boom"));
    ScheduleNotSavedException ex =
        assertThrows(
            ScheduleNotSavedException.class,
            () -> service.addRecord(MILL, YEAR, request("01", null, "01B", null), true, USER));
    assertEquals("scheduleNotSavedErrorMsg", ex.getMessageKey());
  }
}
