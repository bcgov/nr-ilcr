package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8RateRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test for the Schedule 8 sample + rate write control flow and the page edit/error branches
 * (Stories 14.2–14.4) — mocked repository, no DB. Covers create/edit persistence, the 404 (unknown
 * page/sample/row) and 409 (stale optimistic-lock) guards, the idempotent deletes, and the
 * DataAccess→500 translation. The SQL is proven end-to-end against Oracle in the {@code *IT} suite.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8WriteServiceTest {

  private static final long MILL = 580L;
  private static final int YEAR = 2021;
  private static final String USER = "tester";
  private static final int PAGE = 8800;
  private static final int SAMPLE = 9900;
  private static final int ROW = 7700;

  @Mock private Schedule8Repository repository;

  @InjectMocks private Schedule8Service service;

  @BeforeEach
  void stubReadsForRecompute() {
    // The write methods recompute the document at the end via getSchedule8 — default its reads
    // empty.
    lenient().when(repository.findPages(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of());
    // Known code-table values so the write-path code checks pass (cost item 47 = addition "1"; the
    // page/rate codes used by the requests below all resolve).
    lenient().when(repository.costItemSubcategories()).thenReturn(Map.of(47, "1"));
    lenient().when(repository.supportCentreLabels()).thenReturn(Map.of("SC", "Support"));
    lenient().when(repository.regionLabels()).thenReturn(Map.of("R", "Region"));
    lenient().when(repository.becZoneLabels()).thenReturn(Map.of("BZ", "Zone"));
    lenient().when(repository.tsaNumberLabels()).thenReturn(Map.of("TSA5", "Tsa"));
    lenient().when(repository.supplyBlockLabels()).thenReturn(Map.of("B", "Block"));
    lenient().when(repository.tflNumberLabels()).thenReturn(Map.of("48", "Tfl"));
    lenient().when(repository.skidTypeLabels()).thenReturn(Map.of());
    lenient().when(repository.costTypeLabels()).thenReturn(Map.of("CT", "Cost Type"));
  }

  private void draft() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
  }

  private static Schedule8PageRequest pageEdit() {
    return new Schedule8PageRequest(
        PAGE, 3, "LIC", "SC", "R", "BZ", "TSA5", null, "B", "Div", "Contact", "250", "CP", "notes");
  }

  private static Schedule8SampleRequest sample(Integer id, Integer rev) {
    return new Schedule8SampleRequest(
        id, rev, "C", "CB", 40, 0, 0, 0, 0, 0, null, null, null, null, null, null, null, null, null,
        null, null);
  }

  private static Schedule8RateRequest rate(Integer id, Integer rev) {
    return new Schedule8RateRequest(id, rev, 47, new BigDecimal("10"), "CT", "desc");
  }

  // ---- savePage edit + persistence-error branches
  // ------------------------------------------------

  @Test
  void savePage_edit_bumpsThenUpdatesFields() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.bumpPageRevision(PAGE, 3, USER)).thenReturn(1);

    service.savePage(MILL, YEAR, pageEdit(), true, USER);

    verify(repository)
        .updatePageFields(
            eq(PAGE),
            eq("SC"),
            eq("R"),
            eq("BZ"),
            eq("TSA5"),
            eq("B"),
            any(),
            eq("CP"),
            eq("LIC"),
            eq("Div"),
            eq("Contact"),
            eq("250"),
            eq("notes"),
            eq(USER));
  }

  @Test
  void savePage_editForeignPage_throwsNotFound_noWrite() {
    // H1 — the page id is not in this mill/year: must 404, never bump/overwrite (cross-context
    // guard).
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(false);

    assertThrows(
        ScheduleNotFoundException.class,
        () -> service.savePage(MILL, YEAR, pageEdit(), true, USER));

    verify(repository, never()).bumpPageRevision(anyInt(), anyInt(), any());
    verify(repository, never())
        .updatePageFields(
            anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any());
  }

  @Test
  void savePage_tflPage_stampsTflSentinelIntoTsaNumber_andClearsSupplyBlock() {
    // H2 — a TFL page must persist TSA_NUMBER = "TFL" so Check Status routes to the TFL-# branch.
    draft();
    when(repository.insertPage(
            anyLong(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any()))
        .thenReturn(9100);
    Schedule8PageRequest tflCreate =
        new Schedule8PageRequest(
            null, null, "LIC", "SC", "R", "BZ", null, "48", "IGNORED", "Div", "Contact", "250",
            "CP", "notes");

    service.savePage(MILL, YEAR, tflCreate, true, USER);

    // tsaNumber (arg 6, 0-based) = "TFL" sentinel; supplyBlock (arg 7) cleared; tflNumber (arg 8)
    // "48".
    verify(repository)
        .insertPage(
            eq(MILL),
            eq(YEAR),
            eq("SC"),
            eq("R"),
            eq("BZ"),
            eq("TFL"),
            isNull(),
            eq("48"),
            eq("CP"),
            eq("LIC"),
            eq("Div"),
            eq("Contact"),
            eq("250"),
            eq("notes"),
            eq(USER));
  }

  @Test
  void savePage_persistenceFailure_translatesToNotSaved() {
    draft();
    when(repository.insertPage(
            anyLong(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("x"));
    Schedule8PageRequest create =
        new Schedule8PageRequest(
            null, null, "LIC", "SC", "R", "BZ", "TSA5", null, "B", null, null, null, null, null);

    assertThrows(
        ScheduleNotSavedException.class, () -> service.savePage(MILL, YEAR, create, true, USER));
  }

  @Test
  void deletePage_persistenceFailure_translatesToNotSaved() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    doThrow(new DataIntegrityViolationException("x")).when(repository).deletePage(PAGE);

    assertThrows(ScheduleNotSavedException.class, () -> service.deletePage(MILL, YEAR, PAGE));
  }

  // ---- saveSample create / edit / guards
  // ---------------------------------------------------------

  @Test
  void saveSample_create_insertsThenBumpsRevision() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.insertSample(
            anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SAMPLE);

    service.saveSample(MILL, YEAR, PAGE, sample(null, null), true, USER);

    verify(repository).bumpSampleRevision(SAMPLE, 0, USER);
  }

  @Test
  void saveSample_blankSkidType_defaultsToNa() {
    // ILCR_SKID_TYPE_CODE is NOT NULL: a sample with no skid type (the request's skidTypeCode is
    // null)
    // must persist the "NA" code, not null — otherwise the insert hits ORA-01400 (legacy default).
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.insertSample(
            anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SAMPLE);

    service.saveSample(MILL, YEAR, PAGE, sample(null, null), true, USER);

    // skidTypeCode is the 17th insertSample argument (0-based 16): defaulted to "NA".
    verify(repository)
        .insertSample(
            anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), eq("NA"), any(), any(), any(), any());
  }

  @Test
  void saveSample_edit_bumpsThenUpdatesFields() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.sampleExists(SAMPLE, PAGE)).thenReturn(true);
    when(repository.bumpSampleRevision(SAMPLE, 2, USER)).thenReturn(1);

    service.saveSample(MILL, YEAR, PAGE, sample(SAMPLE, 2), true, USER);

    verify(repository)
        .updateSampleFields(
            eq(SAMPLE),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(USER));
  }

  @Test
  void saveSample_nonDraft_throwsNotEditable() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.saveSample(MILL, YEAR, PAGE, sample(null, null), true, USER));
  }

  @Test
  void saveSample_unknownPage_throwsNotFound() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(false);
    assertThrows(
        ScheduleNotFoundException.class,
        () -> service.saveSample(MILL, YEAR, PAGE, sample(null, null), true, USER));
  }

  @Test
  void saveSample_editUnknownSample_throwsNotFound() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.sampleExists(SAMPLE, PAGE)).thenReturn(false);
    assertThrows(
        ScheduleNotFoundException.class,
        () -> service.saveSample(MILL, YEAR, PAGE, sample(SAMPLE, 0), true, USER));
  }

  @Test
  void saveSample_staleRevision_throwsStale() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.sampleExists(SAMPLE, PAGE)).thenReturn(true);
    when(repository.bumpSampleRevision(SAMPLE, 9, USER)).thenReturn(0);
    assertThrows(
        StaleRevisionException.class,
        () -> service.saveSample(MILL, YEAR, PAGE, sample(SAMPLE, 9), true, USER));
  }

  @Test
  void saveSample_persistenceFailure_translatesToNotSaved() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.insertSample(
            anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("x"));
    assertThrows(
        ScheduleNotSavedException.class,
        () -> service.saveSample(MILL, YEAR, PAGE, sample(null, null), true, USER));
  }

  // ---- deleteSample
  // ------------------------------------------------------------------------------

  @Test
  void deleteSample_existing_cascades() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.sampleExists(SAMPLE, PAGE)).thenReturn(true);

    service.deleteSample(MILL, YEAR, PAGE, SAMPLE, true);

    verify(repository).deleteSample(SAMPLE);
  }

  @Test
  void deleteSample_unknown_isNoOp() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(false);

    service.deleteSample(MILL, YEAR, PAGE, SAMPLE, true);

    verify(repository, never()).deleteSample(anyInt());
  }

  @Test
  void deleteSample_persistenceFailure_translatesToNotSaved() {
    draft();
    when(repository.pageExists(PAGE, MILL, YEAR)).thenReturn(true);
    when(repository.sampleExists(SAMPLE, PAGE)).thenReturn(true);
    doThrow(new DataIntegrityViolationException("x")).when(repository).deleteSample(SAMPLE);
    assertThrows(
        ScheduleNotSavedException.class,
        () -> service.deleteSample(MILL, YEAR, PAGE, SAMPLE, true));
  }

  // ---- saveRate add / edit / guards
  // --------------------------------------------------------------

  @Test
  void saveRate_add_inserts() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);

    service.saveRate(MILL, YEAR, SAMPLE, null, rate(null, null), true, USER);

    verify(repository).insertRate(eq(SAMPLE), eq("CT"), eq(47), eq("desc"), any(), eq(USER));
  }

  @Test
  void saveRate_edit_updatesRow() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(ROW, SAMPLE)).thenReturn(true);
    when(repository.updateRateRow(eq(ROW), eq(1), any(), any(), any(), any(), eq(USER)))
        .thenReturn(1);

    service.saveRate(MILL, YEAR, SAMPLE, ROW, rate(ROW, 1), true, USER);

    verify(repository).updateRateRow(eq(ROW), eq(1), eq("CT"), eq(47), eq("desc"), any(), eq(USER));
  }

  @Test
  void saveRate_unknownSample_throwsNotFound() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(false);
    assertThrows(
        ScheduleNotFoundException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, null, rate(null, null), true, USER));
  }

  @Test
  void saveRate_editUnknownRow_throwsNotFound() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(ROW, SAMPLE)).thenReturn(false);
    assertThrows(
        ScheduleNotFoundException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, ROW, rate(ROW, 0), true, USER));
  }

  @Test
  void saveRate_staleRevision_throwsStale() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(ROW, SAMPLE)).thenReturn(true);
    when(repository.updateRateRow(eq(ROW), eq(4), any(), any(), any(), any(), eq(USER)))
        .thenReturn(0);
    assertThrows(
        StaleRevisionException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, ROW, rate(ROW, 4), true, USER));
  }

  @Test
  void saveRate_persistenceFailure_translatesToNotSaved() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.insertRate(anyInt(), any(), any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("x"));
    assertThrows(
        ScheduleNotSavedException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, null, rate(null, null), true, USER));
  }

  // ---- deleteRate
  // --------------------------------------------------------------------------------

  @Test
  void deleteRate_existing_deletesRow() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(ROW, SAMPLE)).thenReturn(true);

    service.deleteRate(MILL, YEAR, SAMPLE, ROW, true);

    verify(repository).deleteRateRow(ROW);
  }

  @Test
  void deleteRate_persistenceFailure_translatesToNotSaved() {
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    when(repository.rateExists(ROW, SAMPLE)).thenReturn(true);
    doThrow(new DataIntegrityViolationException("x")).when(repository).deleteRateRow(ROW);
    assertThrows(
        ScheduleNotSavedException.class, () -> service.deleteRate(MILL, YEAR, SAMPLE, ROW, true));
  }

  // ---- Code-table validation (M1/M4)
  // -------------------------------------------------------------

  @Test
  void saveRate_unknownCostItem_throwsInvalidCode_noInsert() {
    // Cost item 999 resolves to no category-8 subcategory → 400, never persisted (else it would
    // silently vanish from finalRate on read).
    draft();
    when(repository.sampleInMillYear(SAMPLE, MILL, YEAR)).thenReturn(true);
    Schedule8RateRequest bad =
        new Schedule8RateRequest(null, null, 999, new BigDecimal("10"), "CT", "d");

    assertThrows(
        Schedule8InvalidCodeException.class,
        () -> service.saveRate(MILL, YEAR, SAMPLE, null, bad, true, USER));
    verify(repository, never()).insertRate(anyInt(), any(), any(), any(), any(), any());
  }

  @Test
  void savePage_unknownCode_throwsInvalidCode() {
    // Region "NOPE" is not in the reference table → 400 (rather than a DB FK 500 in prod).
    draft();
    Schedule8PageRequest bad =
        new Schedule8PageRequest(
            null, null, "LIC", "SC", "NOPE", "BZ", "TSA5", null, "B", null, null, null, null, null);

    assertThrows(
        Schedule8InvalidCodeException.class, () -> service.savePage(MILL, YEAR, bad, true, USER));
  }
}
