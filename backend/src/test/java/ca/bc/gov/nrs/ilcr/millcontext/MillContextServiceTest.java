package ca.bc.gov.nrs.ilcr.millcontext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.StatusDates;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.TrackCodes;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.WorkingContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the mill/year guard decisions (AD-4). Mocked repository — no DB, no Spring. Covers
 * the decision table pinned in the story (getActiveMills / CLS shape).
 */
@ExtendWith(MockitoExtension.class)
class MillContextServiceTest {

  private static final int YEAR = 2021;
  private static final String CATEGORY = "1";

  @Mock
  private MillContextRepository repository;

  @InjectMocks
  private MillContextService service;

  @Test
  void unknownContext_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(999999L, YEAR)).thenReturn(Optional.empty());
    assertThrows(ScheduleNotFoundException.class,
        () -> service.validateScheduleViewable(999999L, YEAR, CATEGORY));
  }

  @Test
  void millClosedForYear_throwsMillClosed() {
    when(repository.findMillStatusCodeForYear(516L, YEAR)).thenReturn(Optional.of("CLS"));
    assertThrows(MillClosedException.class,
        () -> service.validateScheduleViewable(516L, YEAR, CATEGORY));
  }

  @Test
  void unexpectedNonActiveStatus_throwsMillClosed() {
    // Legacy has only ACT/CLS, but the guard whitelists ACT: any other status is not viewable.
    when(repository.findMillStatusCodeForYear(518L, YEAR)).thenReturn(Optional.of("SUS"));
    assertThrows(MillClosedException.class,
        () -> service.validateScheduleViewable(518L, YEAR, CATEGORY));
  }

  @Test
  void activeButNoSummary_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(515L, YEAR)).thenReturn(Optional.of("ACT"));
    when(repository.scheduleSummaryExists(515L, YEAR, CATEGORY)).thenReturn(false);
    assertThrows(ScheduleNotFoundException.class,
        () -> service.validateScheduleViewable(515L, YEAR, CATEGORY));
  }

  @Test
  void activeWithSummary_isValid() {
    when(repository.findMillStatusCodeForYear(514L, YEAR)).thenReturn(Optional.of("ACT"));
    when(repository.scheduleSummaryExists(514L, YEAR, CATEGORY)).thenReturn(true);
    assertDoesNotThrow(() -> service.validateScheduleViewable(514L, YEAR, CATEGORY));
  }

  // ---- validateMillYearActive: mill/year context guard WITHOUT the summary-exists requirement ----

  @Test
  void millYearActive_unknownContext_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(999999L, YEAR)).thenReturn(Optional.empty());
    assertThrows(ScheduleNotFoundException.class,
        () -> service.validateMillYearActive(999999L, YEAR));
  }

  @Test
  void millYearActive_millClosed_throwsMillClosed() {
    when(repository.findMillStatusCodeForYear(516L, YEAR)).thenReturn(Optional.of("CLS"));
    assertThrows(MillClosedException.class,
        () -> service.validateMillYearActive(516L, YEAR));
  }

  @Test
  void millYearActive_activeButNoSummary_isValid() {
    // The key difference from validateScheduleViewable: an active mill/year with NO summary is
    // valid here (drives the 200 "not initiated" empty document) rather than 404.
    when(repository.findMillStatusCodeForYear(515L, YEAR)).thenReturn(Optional.of("ACT"));
    assertDoesNotThrow(() -> service.validateMillYearActive(515L, YEAR));
  }

  // ---- resolveWorkingContext (Story 1.2): Home semantics, distinct from the guards above ----

  private static final MillSummary MILL_514 = new MillSummary(514L, "514", "AAA Milling", "ACT");
  private static final MillSummary MILL_516 = new MillSummary(516L, "516", "Closed Milling", "CLS");

  private void stubSelectable(MillSummary mill, int year) {
    when(repository.findSelectableMillById(mill.millId())).thenReturn(Optional.of(mill));
    when(repository.reportingYearExists(year)).thenReturn(true);
  }

  @Test
  void resolve_missingBoth_throwsWithBothLabelsInScreenOrder() {
    FieldValuesRequiredException ex = assertThrows(FieldValuesRequiredException.class,
        () -> service.resolveWorkingContext(null, "  "));
    // S08: BOTH fields reported together, Mill first (home.xhtml screen order).
    assertEquals(List.of("Mill", "Reporting Year"), ex.getFieldLabels());
  }

  @Test
  void resolve_nonNumericMill_reportsMillRequired() {
    FieldValuesRequiredException ex = assertThrows(FieldValuesRequiredException.class,
        () -> service.resolveWorkingContext("abc", "2021"));
    assertEquals(List.of("Mill"), ex.getFieldLabels());
  }

  @Test
  void resolve_unknownMill_throwsNotFound() {
    when(repository.findSelectableMillById(999L)).thenReturn(Optional.empty());
    assertThrows(MillYearContextNotFoundException.class,
        () -> service.resolveWorkingContext("999", "2021"));
  }

  @Test
  void resolve_unopenedYear_throwsNotFound() {
    when(repository.findSelectableMillById(514L)).thenReturn(Optional.of(MILL_514));
    when(repository.reportingYearExists(2019)).thenReturn(false);
    assertThrows(MillYearContextNotFoundException.class,
        () -> service.resolveWorkingContext("514", "2019"));
  }

  @Test
  void resolve_noStatusRow_returnsNullStatuses_S07() {
    stubSelectable(MILL_514, 2020);
    when(repository.findTrackStatusCodes(514L, 2020)).thenReturn(Optional.empty());
    when(repository.findStatusDates(514L, 2020)).thenReturn(Optional.empty());

    WorkingContext ctx = service.resolveWorkingContext("514", "2020");

    assertNull(ctx.schedules1To10Status());
    assertNull(ctx.schedule11Status());
    assertTrue(ctx.millViewable());
    assertEquals(514L, ctx.millId());
    assertEquals(2020, ctx.reportYear());
  }

  @Test
  void resolve_closedMill_isFlagNotError_S06() {
    stubSelectable(MILL_516, 2021);
    when(repository.findTrackStatusCodes(516L, 2021))
        .thenReturn(Optional.of(new TrackCodes("D", null)));
    when(repository.findStatusDates(516L, 2021)).thenReturn(Optional.empty());
    when(repository.findStatusDescription("D")).thenReturn(Optional.of("Draft"));

    WorkingContext ctx = service.resolveWorkingContext("516", "2021");

    assertFalse(ctx.millViewable());
    assertEquals("D", ctx.schedules1To10Status().code());
    // No view row -> date null (frontend renders "Not Initiated").
    assertNull(ctx.schedules1To10Status().date());
    assertNull(ctx.schedule11Status());
  }

  @Test
  void resolve_eachTrackPicksItsOwnDate_legacyCrossTrackBugNotReproduced() {
    stubSelectable(MILL_514, 2020);
    // 1-10 'S' (submit), silvi 'D' (draft): legacy's bug would test the 1-10 code in the silvi
    // branch; the deviation pins each track to its OWN code.
    when(repository.findTrackStatusCodes(514L, 2020))
        .thenReturn(Optional.of(new TrackCodes("S", "D")));
    when(repository.findStatusDates(514L, 2020)).thenReturn(Optional.of(new StatusDates(
        "00 2020-01-01", "01 2020-02-02", "02 2020-11-30", "03 2020-12-31",
        "01 2020-08-01", "02 2020-09-09", "03 2020-10-10")));
    when(repository.findStatusDescription("S")).thenReturn(Optional.of("Submitted"));
    when(repository.findStatusDescription("D")).thenReturn(Optional.of("Draft"));

    WorkingContext ctx = service.resolveWorkingContext("514", "2020");

    assertEquals("2020-11-30", ctx.schedules1To10Status().date()); // S -> submit, prefix stripped
    assertEquals("2020-08-01", ctx.schedule11Status().date());     // D -> SILVI draft
  }

  @Test
  void resolve_codeWithNoDescriptionLookup_omitsDescription() {
    // A status code present in ILCR_MILL_REPORT_STATUS but absent from the description lookup:
    // code is kept, description is null (Jackson non_null omits it) — 1.4 must render defensively.
    stubSelectable(MILL_514, 2020);
    when(repository.findTrackStatusCodes(514L, 2020))
        .thenReturn(Optional.of(new TrackCodes("D", null)));
    when(repository.findStatusDates(514L, 2020)).thenReturn(Optional.empty());
    when(repository.findStatusDescription("D")).thenReturn(Optional.empty());

    WorkingContext ctx = service.resolveWorkingContext("514", "2020");

    assertEquals("D", ctx.schedules1To10Status().code());
    assertNull(ctx.schedules1To10Status().description());
    assertNull(ctx.schedule11Status());
  }

  @Test
  void resolve_null1To10CodeButPresentSilvi_rendersOnlySchedule11() {
    // Symmetric to nullSilviCode: a NULL 1-10 code with a present silviculture code yields a null
    // schedules1To10Status and a rendered schedule11Status (tracks are independent, AR6).
    stubSelectable(MILL_514, 2020);
    when(repository.findTrackStatusCodes(514L, 2020))
        .thenReturn(Optional.of(new TrackCodes(null, "S")));
    when(repository.findStatusDates(514L, 2020)).thenReturn(Optional.empty());
    when(repository.findStatusDescription("S")).thenReturn(Optional.of("Submitted"));

    WorkingContext ctx = service.resolveWorkingContext("514", "2020");

    assertNull(ctx.schedules1To10Status());
    assertEquals("S", ctx.schedule11Status().code());
    assertEquals("Submitted", ctx.schedule11Status().description());
  }

  @Test
  void resolve_blankOrShortDateStrings_becomeNull() {
    stubSelectable(MILL_514, 2021);
    when(repository.findTrackStatusCodes(514L, 2021))
        .thenReturn(Optional.of(new TrackCodes("O", "V")));
    // 'O' -> open1To10 = "---" (legacy empty sentinel: substring(3) -> "" -> Not Initiated);
    // 'V'/else -> verifySilvi = "   " (blank remainder).
    when(repository.findStatusDates(514L, 2021)).thenReturn(Optional.of(new StatusDates(
        "---", null, null, null, null, null, "      ")));
    when(repository.findStatusDescription("O")).thenReturn(Optional.of("Opened"));
    when(repository.findStatusDescription("V")).thenReturn(Optional.of("Verified"));

    WorkingContext ctx = service.resolveWorkingContext("514", "2021");

    assertNull(ctx.schedules1To10Status().date());
    assertNull(ctx.schedule11Status().date());
  }
}
