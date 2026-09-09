package ca.bc.gov.nrs.ilcr.millcontext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.StatusDates;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.TrackCodes;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.WorkingContext;
import ca.bc.gov.nrs.ilcr.security.JwtRoleChecker;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit test for the mill/year guard decisions (AD-4). Mocked repository — no DB, no Spring. Covers
 * the decision table pinned in the story (getActiveMills / CLS shape).
 */
@ExtendWith(MockitoExtension.class)
class MillContextServiceTest {

  private static final int YEAR = 2021;
  private static final String CATEGORY = "1";

  @Mock private MillContextRepository repository;

  // Story 1.3 (AC7): the service resolves the SUC-001 text via MessageSource for the 200 message.
  // Unstubbed here except where a success test asserts the message; unused-mock is fine under
  // strict
  // Mockito (only unused STUBS fail). @InjectMocks wires it through the two-arg constructor.
  @Mock private MessageSource messageSource;

  // Story 5.7: the shared guards now call validateMillAccess, which checks the caller's role.
  @Mock private JwtRoleChecker roleChecker;

  @InjectMocks private MillContextService service;

  @BeforeEach
  void bypassMillScopeAsAdmin() {
    // The mill/year guard tests below exercise the status/summary decision table, not Story 5.7
    // mill-scope; default the caller to ADMIN so validateMillAccess bypasses. Lenient: the
    // listMills
    // tests (which pass isAdmin explicitly) never consult the role checker. Mill-scope enforcement
    // itself is proven end-to-end in MillScopeEnforcementIT.
    lenient().when(roleChecker.hasConcreteRole(anyString())).thenReturn(true);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateJwtWithGuid(String guid) {
    Jwt jwt =
        Jwt.withTokenValue("t").header("alg", "none").claim("custom:idp_user_id", guid).build();
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(new JwtAuthenticationToken(jwt));
    SecurityContextHolder.setContext(ctx);
  }

  @Test
  void validateMillAccess_admin_bypasses_withoutTouchingTheXref() {
    // roleChecker→true (admin) from @BeforeEach: returns before any repository read (strict Mockito
    // proves userHasActiveAssignment is never called — no stub for it here).
    assertDoesNotThrow(() -> service.validateMillAccess(514L));
  }

  @Test
  void validateMillAccess_submitterAssociated_isAllowed() {
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    authenticateJwtWithGuid("SUBGUID");
    when(repository.userHasActiveAssignment(514L, "SUBGUID")).thenReturn(true);
    assertDoesNotThrow(() -> service.validateMillAccess(514L));
  }

  @Test
  void validateMillAccess_submitterNotAssociated_isDenied() {
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    authenticateJwtWithGuid("SUBGUID");
    when(repository.userHasActiveAssignment(514L, "SUBGUID")).thenReturn(false);
    assertThrows(AccessDeniedException.class, () -> service.validateMillAccess(514L));
  }

  @Test
  void validateMillAccess_submitterBlankGuid_isDenied_failClosed() {
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    authenticateJwtWithGuid("   "); // no resolvable custom:idp_user_id
    // Strict Mockito proves the xref is never consulted — blank guid is denied before the query.
    assertThrows(AccessDeniedException.class, () -> service.validateMillAccess(514L));
  }

  @Test
  void validateMillAccess_mockNonJwtPrincipal_isExempt() {
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    // Security-off dev mock: a non-Jwt principal carries no directory GUID — AC6 exemption, no
    // throw,
    // no xref read (strict Mockito).
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(
        new UsernamePasswordAuthenticationToken("dev-submitter", "N/A", List.of()));
    SecurityContextHolder.setContext(ctx);
    assertDoesNotThrow(() -> service.validateMillAccess(514L));
  }

  @Test
  void unknownContext_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(999999L, YEAR)).thenReturn(Optional.empty());
    assertThrows(
        ScheduleNotFoundException.class,
        () -> service.validateScheduleViewable(999999L, YEAR, CATEGORY));
  }

  @Test
  void millClosedForYear_throwsMillClosed() {
    when(repository.findMillStatusCodeForYear(516L, YEAR)).thenReturn(Optional.of("CLS"));
    assertThrows(
        MillClosedException.class, () -> service.validateScheduleViewable(516L, YEAR, CATEGORY));
  }

  @Test
  void unexpectedNonActiveStatus_throwsMillClosed() {
    // Legacy has only ACT/CLS, but the guard whitelists ACT: any other status is not viewable.
    when(repository.findMillStatusCodeForYear(518L, YEAR)).thenReturn(Optional.of("SUS"));
    assertThrows(
        MillClosedException.class, () -> service.validateScheduleViewable(518L, YEAR, CATEGORY));
  }

  @Test
  void activeButNoSummary_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(515L, YEAR)).thenReturn(Optional.of("ACT"));
    when(repository.scheduleSummaryExists(515L, YEAR, CATEGORY)).thenReturn(false);
    assertThrows(
        ScheduleNotFoundException.class,
        () -> service.validateScheduleViewable(515L, YEAR, CATEGORY));
  }

  @Test
  void activeWithSummary_isValid() {
    when(repository.findMillStatusCodeForYear(514L, YEAR)).thenReturn(Optional.of("ACT"));
    when(repository.scheduleSummaryExists(514L, YEAR, CATEGORY)).thenReturn(true);
    assertDoesNotThrow(() -> service.validateScheduleViewable(514L, YEAR, CATEGORY));
  }

  // ---- listMills (Story 5.5): caller-scoped Home mill list ----

  @Test
  void listMills_admin_returnsAllMills_ignoringGuid() {
    // Admin is tied to no mill: all listable mills incl. closed (findAllMills), guid irrelevant.
    when(repository.findAllMills()).thenReturn(List.of(MILL_514, MILL_516));
    assertEquals(List.of(MILL_514, MILL_516), service.listMills(true, "any-guid-ignored"));
  }

  @Test
  void listMills_submitter_returnsOnlyActivelyAssociatedMills_closedIncluded() {
    // Submitter sees only their active associations — here a single CLOSED mill (S06: still shown).
    when(repository.findMillsForUser("GUID-1")).thenReturn(List.of(MILL_516));
    assertEquals(List.of(MILL_516), service.listMills(false, "GUID-1"));
  }

  @Test
  void listMills_submitterBlankOrNullGuid_returnsEmpty_failClosed() {
    // No resolvable identity (e.g. the dev mock principal, no custom:idp_user_id): EMPTY, never
    // all.
    // Strict Mockito proves no repository read happens (no findAllMills / findMillsForUser stub).
    assertTrue(service.listMills(false, "   ").isEmpty());
    assertTrue(service.listMills(false, null).isEmpty());
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
    FieldValuesRequiredException ex =
        assertThrows(
            FieldValuesRequiredException.class, () -> service.resolveWorkingContext(null, "  "));
    // S08: BOTH fields reported together, Mill first (home.xhtml screen order).
    assertEquals(List.of("Mill", "Reporting Year"), ex.getFieldLabels());
  }

  @Test
  void resolve_nonNumericMill_reportsMillRequired() {
    FieldValuesRequiredException ex =
        assertThrows(
            FieldValuesRequiredException.class, () -> service.resolveWorkingContext("abc", "2021"));
    assertEquals(List.of("Mill"), ex.getFieldLabels());
  }

  @Test
  void resolve_unknownMill_throwsNotFound() {
    when(repository.findSelectableMillById(999L)).thenReturn(Optional.empty());
    assertThrows(
        MillYearContextNotFoundException.class, () -> service.resolveWorkingContext("999", "2021"));
  }

  @Test
  void resolve_unopenedYear_throwsNotFound() {
    when(repository.findSelectableMillById(514L)).thenReturn(Optional.of(MILL_514));
    when(repository.reportingYearExists(2019)).thenReturn(false);
    assertThrows(
        MillYearContextNotFoundException.class, () -> service.resolveWorkingContext("514", "2019"));
  }

  @Test
  void resolve_noStatusRow_returnsNullStatuses_S07() {
    stubSelectable(MILL_514, 2020);
    when(repository.findTrackStatusCodes(514L, 2020)).thenReturn(Optional.empty());
    when(repository.findStatusDates(514L, 2020)).thenReturn(Optional.empty());
    // AC7: the success path resolves the reused SUC-001 key to its verbatim text (server-side,
    // AD-8).
    when(messageSource.getMessage(
            eq("dataSavedSuccesfullyInfoMsg"),
            isNull(),
            eq("dataSavedSuccesfullyInfoMsg"),
            any(Locale.class)))
        .thenReturn("Data saved successfully");

    WorkingContext ctx = service.resolveWorkingContext("514", "2020");

    assertNull(ctx.schedules1To10Status());
    assertNull(ctx.schedule11Status());
    assertTrue(ctx.millViewable());
    assertEquals(514L, ctx.millId());
    assertEquals(2020, ctx.reportYear());
    // Every 200 carries the SUC-001 message (key + resolved text); the frontend displays it on
    // Save.
    assertEquals("dataSavedSuccesfullyInfoMsg", ctx.message().key());
    assertEquals("Data saved successfully", ctx.message().text());
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
    when(repository.findStatusDates(514L, 2020))
        .thenReturn(
            Optional.of(
                new StatusDates(
                    "00 2020-01-01",
                    "01 2020-02-02",
                    "02 2020-11-30",
                    "03 2020-12-31",
                    "01 2020-08-01",
                    "02 2020-09-09",
                    "03 2020-10-10")));
    when(repository.findStatusDescription("S")).thenReturn(Optional.of("Submitted"));
    when(repository.findStatusDescription("D")).thenReturn(Optional.of("Draft"));

    WorkingContext ctx = service.resolveWorkingContext("514", "2020");

    assertEquals("2020-11-30", ctx.schedules1To10Status().date()); // S -> submit, prefix stripped
    assertEquals("2020-08-01", ctx.schedule11Status().date()); // D -> SILVI draft
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
    when(repository.findStatusDates(514L, 2021))
        .thenReturn(Optional.of(new StatusDates("---", null, null, null, null, null, "      ")));
    when(repository.findStatusDescription("O")).thenReturn(Optional.of("Opened"));
    when(repository.findStatusDescription("V")).thenReturn(Optional.of("Verified"));

    WorkingContext ctx = service.resolveWorkingContext("514", "2021");

    assertNull(ctx.schedules1To10Status().date());
    assertNull(ctx.schedule11Status().date());
  }

  // ---- validateMillYearActive (AD-4): the summary-free guards 1-2, shared by LIST schedules
  // (Schedule11Controller, Story 25.1) and "not initiated" document reads (Schedule2Controller),
  // and delegated to by validateScheduleViewable for its guards 1-2. Unlike
  // validateScheduleViewable there is deliberately NO summary-exists check — list schedules
  // (Schedule 11, UC-SCH11-001 S13) have no ILCR_REPORT_SUMMARY row; the 404 keys solely on the
  // ILCR_MILL_REPORT_STATUS row (legacy Schedule11MB.init -> scheduleNotFound). Strict Mockito
  // proves the no-summary-check semantics: stubbing scheduleSummaryExists here would fail the run.

  @Test
  void millYearActive_noStatusRow_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(999999L, YEAR)).thenReturn(Optional.empty());
    assertThrows(
        ScheduleNotFoundException.class, () -> service.validateMillYearActive(999999L, YEAR));
  }

  @Test
  void millYearActive_millClosedForYear_throwsMillClosed() {
    when(repository.findMillStatusCodeForYear(516L, YEAR)).thenReturn(Optional.of("CLS"));
    assertThrows(MillClosedException.class, () -> service.validateMillYearActive(516L, YEAR));
  }

  @Test
  void millYearActive_unexpectedNonActiveStatus_throwsMillClosed() {
    // Same ACT whitelist as validateScheduleViewable: any unexpected status is not viewable.
    when(repository.findMillStatusCodeForYear(518L, YEAR)).thenReturn(Optional.of("SUS"));
    assertThrows(MillClosedException.class, () -> service.validateMillYearActive(518L, YEAR));
  }

  @Test
  void millYearActive_activeMill_isValid_withoutAnySummaryCheck() {
    when(repository.findMillStatusCodeForYear(515L, YEAR)).thenReturn(Optional.of("ACT"));
    // Mill 515 has NO summary of any category (V2 seed) — a list schedule is still viewable (AC2).
    assertDoesNotThrow(() -> service.validateMillYearActive(515L, YEAR));
  }

  // ---- validateMillYearActive String overload (Story 25.1 AC3 / S11): missing, blank, and
  // non-numeric params all resolve to the verbatim legacy ERR-001 message (400) — the raw-String
  // idiom mirrors resolveWorkingContext, because a typed @RequestParam cannot produce ERR-001.

  @Test
  void millYearActive_missingMillId_throwsMillYearNotSelected() {
    assertThrows(
        MillYearNotSelectedException.class, () -> service.validateMillYearActive(null, "2021"));
  }

  @Test
  void millYearActive_blankYear_throwsMillYearNotSelected() {
    assertThrows(
        MillYearNotSelectedException.class, () -> service.validateMillYearActive("514", "   "));
  }

  @Test
  void millYearActive_nonNumericMillId_throwsMillYearNotSelected() {
    assertThrows(
        MillYearNotSelectedException.class, () -> service.validateMillYearActive("abc", "2021"));
  }

  @Test
  void millYearActive_bothMissing_throwsMillYearNotSelected() {
    // Legacy shows ONE combined message (schedule11.xhtml guard), not per-field texts — unlike
    // resolveWorkingContext's S08 per-field list.
    assertThrows(
        MillYearNotSelectedException.class, () -> service.validateMillYearActive(null, null));
  }

  @Test
  void millYearActive_validStrings_delegateToTypedGuard() {
    when(repository.findMillStatusCodeForYear(514L, YEAR)).thenReturn(Optional.of("ACT"));
    assertDoesNotThrow(() -> service.validateMillYearActive("514", "2021"));
  }

  // --- Story 15.1: the cheap both-tracks read for the Check Status sweep ---

  @Test
  void findTrackStatusCodes_mapsBothCodesFromTheOneRow() {
    when(repository.findTrackStatusCodes(514L, YEAR))
        .thenReturn(Optional.of(new TrackCodes("D", "S")));

    var codes = service.findTrackStatusCodes(514L, YEAR).orElseThrow();

    assertEquals("D", codes.schedules1To10Code());
    assertEquals("S", codes.schedule11Code());
  }

  @Test
  void findTrackStatusCodes_nullSilvicultureCode_isCarriedNotThrown() {
    // Legacy NPE'd on a null MILL_SILVICULTUR_STATUS_CODE; Story 1.2 tolerates it and so does this.
    when(repository.findTrackStatusCodes(514L, YEAR))
        .thenReturn(Optional.of(new TrackCodes("D", null)));

    var codes = service.findTrackStatusCodes(514L, YEAR).orElseThrow();

    assertEquals("D", codes.schedules1To10Code());
    assertNull(codes.schedule11Code());
  }

  @Test
  void findTrackStatusCodes_noStatusRow_isEmpty() {
    when(repository.findTrackStatusCodes(514L, YEAR)).thenReturn(Optional.empty());

    assertTrue(service.findTrackStatusCodes(514L, YEAR).isEmpty());
  }
}
