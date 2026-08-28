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

import ca.bc.gov.nrs.ilcr.exception.RevisionCountRequiredException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordEntry;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6SaveRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
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

  private static RoadRecordRequest request(String areaType, String tflNumber, String supplyBlock) {
    return new RoadRecordRequest(
        areaType, tflNumber, supplyBlock, new BigDecimal("100"), 5000, "rc");
  }

  // ---- BR-02 counterpart-clear matrix -----------------------------------------------------------

  @Test
  @DisplayName("BR-02: TFL area type stores the TFL number and NULLs both TSA columns")
  void tflWrite_clearsTsaColumns() {
    stubDraft();
    service.addRecord(MILL, YEAR, request("TFL", "18", "01B"), true, USER);
    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), isNull(), isNull(), eq("18"), eq(USER));
  }

  @Test
  @DisplayName("BR-02: a TSA area type stores TSA+TSB and NULLs the TFL number")
  void tsaWrite_clearsTflColumn() {
    stubDraft();
    service.addRecord(MILL, YEAR, request("01", "18", "01B"), true, USER);
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
    service.addRecord(MILL, YEAR, request("tf", null, "01B"), true, USER);
    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), eq("tf"), eq("01B"), isNull(), eq(USER));
  }

  // ---- BR-03 TFL alias + validation matrix ------------------------------------------------------

  @ParameterizedTest(name = "alias \"{0}\" is stored as \"{1}\"")
  @CsvSource({"1,01", "3,03", "5,05", "8,08", "18,18", "62,62"})
  @DisplayName("BR-03: the complete leading-zero alias table normalizes onto the stored value")
  void tflAlias_normalizedOntoStoredValue(String entered, String stored) {
    stubDraft();
    service.addRecord(MILL, YEAR, request("TFL", entered, null), true, USER);
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
        () -> service.addRecord(MILL, YEAR, request("TFL", tflNumber, null), true, USER));
    verify(repository, never())
        .insertRoadReport(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
  }

  @Test
  @DisplayName("BR-03: a TFL record with the number missing entirely -> InvalidTflNumberException")
  void missingTfl_rejected() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    assertThrows(
        InvalidTflNumberException.class,
        () -> service.addRecord(MILL, YEAR, request("TFL", null, null), true, USER));
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

    service.addRecord(MILL, YEAR, request("01", null, "01B"), true, USER);

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

    service.addRecord(MILL, YEAR, request("01", null, "01B"), true, USER);

    verify(repository)
        .insertRoadReport(eq(9501), eq(MILL), eq(YEAR), eq("01"), eq("01B"), isNull(), eq(USER));
  }

  @Test
  @DisplayName(
      "BR-09: with real records present a fresh insert runs, then its detail — in order. "
          + "The general comment is NOT threaded through Java: insertRoadReport sources it in SQL so a "
          + "concurrent general-comments save cannot be reverted, so the replication invariant itself is "
          + "proven by Schedule6WriteIT.addRecord_carriesCurrentGeneralComment, not here")
  void addRecord_insertsThenDetail_inOrder() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8334, "01", "01B", null, "the shared comment", 0)));
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    when(repository.nextRoadReportId()).thenReturn(9502);

    service.addRecord(MILL, YEAR, request("03", null, "03B"), true, USER);

    InOrder order = inOrder(repository);
    order
        .verify(repository)
        .insertRoadReport(eq(9502), eq(MILL), eq(YEAR), eq("03"), eq("03B"), isNull(), eq(USER));
    order.verify(repository).upsertCostDetail(9502, new BigDecimal("100"), 5000, "rc", USER);
    verify(repository, never())
        .claimPlaceholder(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
  }

  // ---- Task 8: whole-document save ports the retired per-record updateRecord's AR11 coverage ----
  // (updateRecord itself is gone — Schedule6SaveDocumentIT proves the same 404/409/detail-upsert
  // behaviour at the HTTP layer; these are the unit-level equivalents that lived here for
  // updateRecord before its retirement.)

  @Test
  @DisplayName(
      "AR11 (ported from updateRecord): an entry hits (1 row) -> the detail is UPSERTED "
          + "after the master update")
  void saveDocument_upsertsDetailAfterMasterUpdate() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8336, "01", "01B", null, null, 0)));
    when(repository.updateRoadReport(8336, MILL, YEAR, 0, null, null, "18", USER)).thenReturn(1);
    Schedule6SaveRequest saveRequest =
        new Schedule6SaveRequest(
            null,
            List.of(
                new RoadRecordEntry(
                    8336, 0, "TFL", "18", null, new BigDecimal("100"), 5000, "rc")));

    service.saveDocument(MILL, YEAR, saveRequest, true, USER);

    InOrder order = inOrder(repository);
    order.verify(repository).updateRoadReport(8336, MILL, YEAR, 0, null, null, "18", USER);
    order.verify(repository).upsertCostDetail(8336, new BigDecimal("100"), 5000, "rc", USER);
  }

  @Test
  @DisplayName(
      "AR11 (ported from updateRecord): 0 rows + id absent -> 404; 0 rows + id present -> "
          + "409 stale; nothing upserted")
  void saveDocument_disambiguates404From409() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(79999, "01", "01B", null, null, 0)));
    when(repository.updateRoadReport(79999, MILL, YEAR, 0, "01", "01B", null, USER)).thenReturn(0);
    when(repository.countRoadRecord(79999, MILL, YEAR)).thenReturn(0);
    Schedule6SaveRequest absentRequest =
        new Schedule6SaveRequest(
            null,
            List.of(
                new RoadRecordEntry(
                    79999, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));
    assertThrows(
        RoadRecordNotFoundException.class,
        () -> service.saveDocument(MILL, YEAR, absentRequest, true, USER));

    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8336, "01", "01B", null, null, 0)));
    when(repository.updateRoadReport(8336, MILL, YEAR, 0, "01", "01B", null, USER)).thenReturn(0);
    when(repository.countRoadRecord(8336, MILL, YEAR)).thenReturn(1);
    Schedule6SaveRequest staleRequest =
        new Schedule6SaveRequest(
            null,
            List.of(
                new RoadRecordEntry(
                    8336, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));
    assertThrows(
        StaleRevisionException.class,
        () -> service.saveDocument(MILL, YEAR, staleRequest, true, USER));

    verify(repository, never()).upsertCostDetail(anyInt(), any(), any(), any(), anyString());
  }

  @ParameterizedTest(name = "placeholder classification [{0}] -> 404")
  @ValueSource(strings = {"NULL", "BLANK"})
  @DisplayName(
      "AR11 (ported from updateRecord): a placeholder id is 404 BEFORE the update — never "
          + "converted into a record; the guard is trim-aware, so a WHITESPACE-classification "
          + "placeholder is caught too (the IS NULL-only SQL predicate this replaced let that row "
          + "through)")
  void saveDocument_placeholderEntryId_is404(String shape) {
    String blank = "NULL".equals(shape) ? null : " ";
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8330, blank, blank, blank, "lone", 0)));
    Schedule6SaveRequest saveRequest =
        new Schedule6SaveRequest(
            null,
            List.of(
                new RoadRecordEntry(
                    8330, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));
    assertThrows(
        RoadRecordNotFoundException.class,
        () -> service.saveDocument(MILL, YEAR, saveRequest, true, USER));
    verify(repository, never())
        .updateRoadReport(
            anyInt(), anyLong(), anyInt(), anyInt(), any(), any(), any(), anyString());
  }

  @Test
  @DisplayName(
      "AR11 defence in depth (ported from updateRecord): a null revisionCount reaching the "
          + "service is a clean 400 (RevisionCountRequiredException), never an NPE and never a "
          + "coerced 409")
  void saveDocument_nullRevisionCount_is400() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8336, "01", "01B", null, null, 0)));
    Schedule6SaveRequest saveRequest =
        new Schedule6SaveRequest(
            null,
            List.of(
                new RoadRecordEntry(
                    8336, null, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));
    assertThrows(
        RevisionCountRequiredException.class,
        () -> service.saveDocument(MILL, YEAR, saveRequest, true, USER));
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
        () -> service.addRecord(MILL, YEAR, request("999", null, "01B"), true, USER));
    verify(repository, never())
        .insertRoadReport(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
  }

  // ---- Task 8: whole-document save ports the retired saveGeneralComments's BR-09 coverage -------
  // (saveGeneralComments itself is gone — saveDocument's comment handling is the same three
  // branches, now driven by the submitted document rather than an independent call.)

  @Test
  @DisplayName(
      "BR-09 (ported from saveGeneralComments): rows exist -> updateAllComments "
          + "(replication); no placeholder ops")
  void saveDocument_commentWithRows_replicates() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8334, "01", "01B", null, "old", 0)));
    when(repository.updateRoadReport(8334, MILL, YEAR, 0, "01", "01B", null, USER)).thenReturn(1);
    Schedule6SaveRequest saveRequest =
        new Schedule6SaveRequest(
            "new text",
            List.of(
                new RoadRecordEntry(
                    8334, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));

    service.saveDocument(MILL, YEAR, saveRequest, true, USER);

    verify(repository).updateAllComments(MILL, YEAR, "new text", USER);
    verify(repository, never())
        .insertPlaceholder(anyInt(), anyLong(), anyInt(), any(), anyString());
    verify(repository, never()).deletePlaceholder(anyInt(), anyLong(), anyInt());
  }

  @Test
  @DisplayName(
      "BR-09 (ported from saveGeneralComments): zero rows + non-blank -> insertPlaceholder "
          + "(raw text, id from the sequence)")
  void saveDocument_commentZeroRows_insertsPlaceholder() {
    stubDraft();
    Schedule6SaveRequest saveRequest = new Schedule6SaveRequest("  raw untrimmed  ", List.of());

    service.saveDocument(MILL, YEAR, saveRequest, true, USER);

    // Stored RAW — the 8.1 legacy-faithful comments decision covers the write side too.
    verify(repository).insertPlaceholder(9501, MILL, YEAR, "  raw untrimmed  ", USER);
    verify(repository, never()).updateAllComments(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName(
      "BR-09 (ported from saveGeneralComments): placeholder-only + blank -> "
          + "deletePlaceholder; zero rows + blank -> no-op")
  void saveDocument_commentBlankBranches() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8330, null, null, null, "lone", 0)))
        .thenReturn(List.of());
    when(repository.deletePlaceholder(8330, MILL, YEAR)).thenReturn(1);

    service.saveDocument(MILL, YEAR, new Schedule6SaveRequest("   ", List.of()), true, USER);
    verify(repository).deletePlaceholder(8330, MILL, YEAR);

    service.saveDocument(MILL, YEAR, new Schedule6SaveRequest(null, List.of()), true, USER);
    verify(repository, never())
        .insertPlaceholder(anyInt(), anyLong(), anyInt(), any(), anyString());
    verify(repository, never()).updateAllComments(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName(
      "BR-09 (ported from saveGeneralComments): a delete that matches NOTHING (whitespace "
          + "classification the IS NULL SQL cannot see, or a placeholder claimed by a concurrent "
          + "add) falls back to clearing COMMENTS in place — never a silent no-op behind \"Data "
          + "saved successfully\"")
  void saveDocument_commentDeleteMatchesNothing_fallsBackToClear() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8330, " ", " ", " ", "lone", 0)));
    when(repository.deletePlaceholder(8330, MILL, YEAR)).thenReturn(0);

    service.saveDocument(MILL, YEAR, new Schedule6SaveRequest(" ", List.of()), true, USER);

    verify(repository).deletePlaceholder(8330, MILL, YEAR);
    verify(repository).updateAllComments(MILL, YEAR, null, USER);
  }

  @Test
  @DisplayName(
      "BR-09 (ported from saveGeneralComments): real rows + blank comment -> the clear "
          + "replicates NULL onto every row (not a placeholder delete)")
  void saveDocument_commentRecordsPlusBlank_replicatesNull() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(List.of(new RoadRecordRow(8334, "01", "01B", null, "old", 0)));
    when(repository.updateRoadReport(8334, MILL, YEAR, 0, "01", "01B", null, USER)).thenReturn(1);
    Schedule6SaveRequest saveRequest =
        new Schedule6SaveRequest(
            "",
            List.of(
                new RoadRecordEntry(
                    8334, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));

    service.saveDocument(MILL, YEAR, saveRequest, true, USER);

    verify(repository).updateAllComments(MILL, YEAR, null, USER);
    verify(repository, never()).deletePlaceholder(anyInt(), anyLong(), anyInt());
  }

  // ---- Task 5: whole-document save — omitted-rows guard
  // ------------------------------------------

  @Test
  @DisplayName(
      "Task 5: a stored real row absent from the submitted list -> OmittedRoadRecordsException, "
          + "nothing written")
  void saveDocument_omitsAStoredRealRow_throws() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(
            List.of(
                new RoadRecordRow(8390, "01", "01B", null, null, 0),
                new RoadRecordRow(8391, "03", "03B", null, null, 0)));
    Schedule6SaveRequest request =
        new Schedule6SaveRequest(
            null,
            List.of(
                new RoadRecordEntry(
                    8390, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));

    assertThrows(
        OmittedRoadRecordsException.class,
        () -> service.saveDocument(MILL, YEAR, request, true, USER));
    verify(repository, never())
        .updateRoadReport(
            anyInt(), anyLong(), anyInt(), anyInt(), any(), any(), any(), anyString());
  }

  @Test
  @DisplayName(
      "Task 5: a placeholder row does NOT count as omitted -- the lone-comment state stays "
          + "savable with only the real row submitted")
  void saveDocument_placeholderNotCountedAsOmitted_succeeds() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenReturn(
            List.of(
                new RoadRecordRow(8390, "01", "01B", null, null, 0),
                new RoadRecordRow(8392, null, null, null, "lone comment", 0)));
    when(repository.updateRoadReport(8390, MILL, YEAR, 0, "01", "01B", null, USER)).thenReturn(1);
    Schedule6SaveRequest request =
        new Schedule6SaveRequest(
            "still here",
            List.of(
                new RoadRecordEntry(
                    8390, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));

    service.saveDocument(MILL, YEAR, request, true, USER);

    verify(repository).updateRoadReport(8390, MILL, YEAR, 0, "01", "01B", null, USER);
    verify(repository).updateAllComments(MILL, YEAR, "still here", USER);
  }

  // ---- Draft gate + failure translation ---------------------------------------------------------

  @ParameterizedTest(name = "track \"{0}\" -> 409, nothing written")
  @ValueSource(strings = {"S", "V", "A"})
  @DisplayName("Deviation (a): every write requires the 1-10 track Draft")
  void nonDraftTrack_rejectsAllWrites(String track) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of(track));
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.addRecord(MILL, YEAR, request("01", null, "01B"), true, USER));
    Schedule6SaveRequest saveRequest =
        new Schedule6SaveRequest(
            "x",
            List.of(
                new RoadRecordEntry(
                    8336, 0, "01", null, "01B", new BigDecimal("100"), 5000, "rc")));
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.saveDocument(MILL, YEAR, saveRequest, true, USER));
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
        () -> service.addRecord(MILL, YEAR, request("01", null, "01B"), true, USER));
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
            () -> service.addRecord(MILL, YEAR, request("01", null, "01B"), true, USER));
    assertEquals("scheduleNotSavedErrorMsg", ex.getMessageKey());
  }

  // ---- FLD-001 defence in depth -----------------------------------------------------------------

  @ParameterizedTest(name = "area type [{0}] -> 400, nothing written")
  @NullSource
  @ValueSource(strings = {"", "   "})
  @DisplayName(
      "FLD-001 defence in depth: a missing area type is the house 400, never an NPE-driven 500 — "
          + "@NotBlank protects the two endpoints, not the method")
  void blankAreaType_rejectedBeforeAnyWrite(String areaType) {
    stubDraft();
    AreaTypeRequiredException ex =
        assertThrows(
            AreaTypeRequiredException.class,
            () -> service.addRecord(MILL, YEAR, request(areaType, null, "01B"), true, USER));
    // Message parity with the @NotBlank route: the two are indistinguishable to a client.
    assertEquals("tsaOrTflRequiredErrorMsg", ex.getMessageKey());
    verify(repository, never())
        .insertRoadReport(anyInt(), anyLong(), anyInt(), any(), any(), any(), anyString());
  }

  // ---- deleteRecord (Task 3) --------------------------------------------------------------------
  // The delete path's HTTP surface is covered end-to-end by Schedule6DeleteIT; these pin the same
  // decisions at the service seam, where the BR-09 re-insert branch and the child-before-master
  // delete order can be asserted directly against the repository calls (code review 2026-08-24).

  /** A served (non-placeholder) row: TSA classification present. */
  private static RoadRecordRow servedRow(int recordId, String generalComment) {
    return new RoadRecordRow(recordId, "01", "01B", null, generalComment, 0);
  }

  /** A general-comment placeholder: classification entirely blank (the read-side S18 rule). */
  private static RoadRecordRow placeholderRow(int recordId, String generalComment) {
    return new RoadRecordRow(recordId, null, null, null, generalComment, 0);
  }

  private void stubDeleteDraft(List<RoadRecordRow> stored) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR)).thenReturn(stored);
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.nextRoadReportId()).thenReturn(9600);
    lenient().when(repository.deleteRoadReport(anyInt(), anyLong(), anyInt())).thenReturn(1);
  }

  @Test
  @DisplayName(
      "BR-09 delete side: deleting the SOLE record re-inserts a placeholder carrying its comment, "
          + "and deletes the cost children BEFORE the master (ILCR_LCRD_RM_RPT_FK, NO ACTION)")
  void deleteSoleRecord_reInsertsPlaceholderAndDeletesChildrenFirst() {
    stubDeleteDraft(List.of(servedRow(8336, "the general comment")));

    service.deleteRecord(MILL, YEAR, 8336, true, USER);

    InOrder order = inOrder(repository);
    // Parent-first would pass against a constraint-less test schema and raise ORA-02292 in
    // delivery — the exact defect R__90_cost_detail_bridge_culvert_fks.sql exists to catch.
    order.verify(repository).deleteCostDetailsFor(8336);
    order.verify(repository).deleteRoadReport(8336, MILL, YEAR);
    order.verify(repository).insertPlaceholder(9600, MILL, YEAR, "the general comment", USER);
  }

  @Test
  @DisplayName("Deleting the sole record with NO comment leaves nothing behind")
  void deleteSoleRecord_nullComment_insertsNoPlaceholder() {
    stubDeleteDraft(List.of(servedRow(8336, null)));

    service.deleteRecord(MILL, YEAR, 8336, true, USER);

    verify(repository).deleteRoadReport(8336, MILL, YEAR);
    verify(repository, never())
        .insertPlaceholder(anyInt(), anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName(
      "Legacy parity: a WHITESPACE-ONLY comment still re-inserts the placeholder — legacy's "
          + "CoreUtil.isNullOrEmptyString is empty-aware, not blank-aware")
  void deleteSoleRecord_whitespaceComment_reInsertsPlaceholder() {
    stubDeleteDraft(List.of(servedRow(8336, "   ")));

    service.deleteRecord(MILL, YEAR, 8336, true, USER);

    verify(repository).insertPlaceholder(9600, MILL, YEAR, "   ", USER);
  }

  @Test
  @DisplayName(
      "Deleting one of SEVERAL records re-inserts nothing — the comment survives on the rest")
  void deleteOneOfSeveral_insertsNoPlaceholder() {
    stubDeleteDraft(List.of(servedRow(8336, "shared comment"), servedRow(8337, "shared comment")));

    service.deleteRecord(MILL, YEAR, 8336, true, USER);

    verify(repository).deleteRoadReport(8336, MILL, YEAR);
    verify(repository, never())
        .insertPlaceholder(anyInt(), anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName("A placeholder id is a 404 — it is not a served record, and it holds the comment")
  void deletePlaceholderId_isNotFound_andNothingIsDeleted() {
    stubDeleteDraft(List.of(placeholderRow(8340, "the general comment")));

    assertThrows(
        RoadRecordNotFoundException.class,
        () -> service.deleteRecord(MILL, YEAR, 8340, true, USER));

    verify(repository, never()).deleteCostDetailsFor(anyInt());
    verify(repository, never()).deleteRoadReport(anyInt(), anyLong(), anyInt());
  }

  @Test
  @DisplayName(
      "An unknown/foreign record id is a 404 before anything is deleted (the IDOR scope guard)")
  void deleteUnknownId_isNotFound_andNothingIsDeleted() {
    stubDeleteDraft(List.of(servedRow(8336, null)));

    assertThrows(
        RoadRecordNotFoundException.class,
        () -> service.deleteRecord(MILL, YEAR, 9999, true, USER));

    verify(repository, never()).deleteCostDetailsFor(anyInt());
    verify(repository, never()).deleteRoadReport(anyInt(), anyLong(), anyInt());
  }

  @Test
  @DisplayName("A concurrent delete between the read and the DELETE (0 rows affected) is a 404")
  void deleteRacedByConcurrentDelete_isNotFound() {
    stubDeleteDraft(List.of(servedRow(8336, "the general comment")));
    when(repository.deleteRoadReport(8336, MILL, YEAR)).thenReturn(0);

    assertThrows(
        RoadRecordNotFoundException.class,
        () -> service.deleteRecord(MILL, YEAR, 8336, true, USER));

    // The raced delete must not resurrect the comment onto a placeholder the winner already
    // handled.
    verify(repository, never())
        .insertPlaceholder(anyInt(), anyLong(), anyInt(), any(), anyString());
  }

  @ParameterizedTest(name = "track \"{0}\" -> 409, nothing deleted")
  @ValueSource(strings = {"S", "V", "A"})
  @DisplayName("Deviation (a): the delete is Draft-gated like every other write")
  void deleteOnNonDraftTrack_rejects(String track) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of(track));

    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.deleteRecord(MILL, YEAR, 8336, true, USER));

    verify(repository, never()).deleteCostDetailsFor(anyInt());
    verify(repository, never()).deleteRoadReport(anyInt(), anyLong(), anyInt());
  }

  @Test
  @DisplayName(
      "AD-11: a DataAccessException on the delete path surfaces as ScheduleNotSavedException")
  void deleteDataAccessFailure_translated() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findRoadRecords(MILL, YEAR))
        .thenThrow(new DataAccessResourceFailureException("boom"));

    ScheduleNotSavedException ex =
        assertThrows(
            ScheduleNotSavedException.class,
            () -> service.deleteRecord(MILL, YEAR, 8336, true, USER));
    assertEquals("scheduleNotSavedErrorMsg", ex.getMessageKey());
  }
}
