package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.TrackCheckResult;
import ca.bc.gov.nrs.ilcr.millcontext.MillClosedException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.millcontext.MillYearNotSelectedException;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import ca.bc.gov.nrs.ilcr.security.ReportSubmission;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Unit test for {@link CheckStatusController} — the ONE mill/year guard both endpoints own (Story
 * 15.1 AC 9, Story 15.3 AC 8) and the page's own not-found semantics (AC 5). Mocked guard, sweep
 * and transition, no Spring: the HTTP statuses and verbatim texts are proven by {@code
 * CheckStatusContextGuardIT} and {@code CheckStatusSubmitIT}; this pins the ORDER (guard before any
 * schedule is touched), the exception translation, the offer flag riding on the 1–10 track only,
 * and the success envelope's text resolution — which an IT can only infer.
 */
@ExtendWith(MockitoExtension.class)
class CheckStatusControllerTest {

  private static final Authentication SUBMITTER =
      new TestingAuthenticationToken("dev-submitter", "N/A", "SUBMITTER");

  private static final ScheduleTrack ONE_TO_TEN = ScheduleTrack.SCHEDULES_1_TO_10;

  @Mock private MillContextService millContextService;
  @Mock private CheckStatusSweepService sweepService;
  @Mock private ReportTrackTransitionService transitionService;
  @Mock private ReportSubmission reportSubmission;
  @Mock private MessageSource messageSource;
  @Mock private Authentication authentication;
  @InjectMocks private CheckStatusController controller;

  private static CheckStatusSweepResponse emptySweep() {
    return new CheckStatusSweepResponse(
        514, 2021, TrackCheckResult.of("D", List.of()), TrackCheckResult.of(null, List.of()));
  }

  @Test
  @DisplayName("AC 9: the guard runs exactly once, BEFORE the sweep, on the raw params")
  void guardRunsOnceBeforeTheSweep() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514, 2021));
    when(sweepService.sweep(514, 2021)).thenReturn(emptySweep());

    var response = controller.checkStatus("514", "2021", SUBMITTER);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    InOrder order = inOrder(millContextService, sweepService);
    order.verify(millContextService).validateMillYearActive("514", "2021");
    order.verify(sweepService).sweep(514, 2021);
    verify(millContextService).validateMillYearActive("514", "2021");
    verify(millContextService, never()).validateMillYearActive(anyLong(), anyInt());
  }

  @Test
  @DisplayName(
      "AC 9: a closed mill stops at the guard — no schedule is touched (409 passes through)")
  void closedMill_neverReachesTheSweep() {
    when(millContextService.validateMillYearActive("516", "2021"))
        .thenThrow(new MillClosedException());

    assertThatThrownBy(() -> controller.checkStatus("516", "2021", SUBMITTER))
        .isInstanceOf(MillClosedException.class);
    verifyNoInteractions(sweepService);
  }

  @Test
  @DisplayName("AC 5/S05: missing mill/year is the guard's ERR-001, untranslated")
  void missingParams_millYearNotSelectedPassesThrough() {
    when(millContextService.validateMillYearActive(null, "2021"))
        .thenThrow(new MillYearNotSelectedException());

    assertThatThrownBy(() -> controller.checkStatus(null, "2021", SUBMITTER))
        .isInstanceOf(MillYearNotSelectedException.class);
    verifyNoInteractions(sweepService);
  }

  @Test
  @DisplayName(
      "AC 5/S06: an absent mill-year is the CHECK STATUS not-found (legacy checkStatus.xhtml:20),"
          + " not the schedule page's")
  void absentContext_isTheCheckStatusNotFound() {
    ScheduleNotFoundException guardFailure = new ScheduleNotFoundException();
    when(millContextService.validateMillYearActive("999999", "2021")).thenThrow(guardFailure);

    assertThatThrownBy(() -> controller.checkStatus("999999", "2021", SUBMITTER))
        .isInstanceOfSatisfying(
            CheckStatusScheduleNotFoundException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getMessageKey()).isEqualTo("checkStatusScheduleNotFoundErrorMsg");
              assertThat(ex.getCause()).isSameAs(guardFailure);
            });
    verifyNoInteractions(sweepService);
  }

  @Test
  @DisplayName("S06: a schedule's own not-found mid-sweep is the same check-status not-found")
  void scheduleNotFoundMidSweep_isTheCheckStatusNotFound() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514, 2021));
    when(sweepService.sweep(514, 2021)).thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.checkStatus("514", "2021", SUBMITTER))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("verify: the guard runs BEFORE the transition, on the raw params")
  void verify_guardRunsBeforeTheTransition() {
    when(millContextService.validateMillYearActive("764", "2021"))
        .thenReturn(new MillYearContext(764, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verify(ScheduleTrack.SCHEDULES_1_TO_10, 764, 2021, "verifyadmin", null))
        .thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 status has been updated to verified.");

    var response = controller.verifySchedules1To10("764", "2021", authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().trackStatus()).isEqualTo("V");
    assertThat(response.getBody().message().key()).isEqualTo("sch1-10VerifiedMsg");
    assertThat(response.getBody().message().text())
        .isEqualTo("Schedules 1-10 status has been updated to verified.");

    InOrder order = inOrder(millContextService, transitionService);
    order.verify(millContextService).validateMillYearActive("764", "2021");
    order
        .verify(transitionService)
        .verify(ScheduleTrack.SCHEDULES_1_TO_10, 764, 2021, "verifyadmin", null);
  }

  @Test
  @DisplayName("verify: a closed mill stops at the guard — no transition is attempted")
  void verify_closedMillNeverReachesTheTransition() {
    when(millContextService.validateMillYearActive("768", "2021"))
        .thenThrow(new MillClosedException());

    assertThatThrownBy(() -> controller.verifySchedules1To10("768", "2021", authentication))
        .isInstanceOf(MillClosedException.class);
    verifyNoInteractions(transitionService);
  }

  @Test
  @DisplayName("verify: missing mill/year is the guard's ERR-001, untranslated")
  void verify_missingParamsPassThrough() {
    when(millContextService.validateMillYearActive(null, "2021"))
        .thenThrow(new MillYearNotSelectedException());

    assertThatThrownBy(() -> controller.verifySchedules1To10(null, "2021", authentication))
        .isInstanceOf(MillYearNotSelectedException.class);
    verifyNoInteractions(transitionService);
  }

  @Test
  @DisplayName("verify Schedule 11: same guard, the Schedule 11 track, and Schedule 11's text")
  void verifySchedule11_guardThenTheSchedule11Track() {
    when(millContextService.validateMillYearActive("807", "2021"))
        .thenReturn(new MillYearContext(807, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verify(ScheduleTrack.SCHEDULE_11, 807, 2021, "verifyadmin", null))
        .thenReturn("V");
    when(messageSource.getMessage(eq("sch11VerifiedMsg"), any(), any(), any()))
        .thenReturn("Schedule 11 status has been updated to verified.");

    var response = controller.verifySchedule11("807", "2021", authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().trackStatus()).isEqualTo("V");
    assertThat(response.getBody().message().key()).isEqualTo("sch11VerifiedMsg");
    InOrder order = inOrder(millContextService, transitionService);
    order.verify(millContextService).validateMillYearActive("807", "2021");
    order
        .verify(transitionService)
        .verify(ScheduleTrack.SCHEDULE_11, 807, 2021, "verifyadmin", null);
  }

  @Test
  @DisplayName("verify Schedule 11: a missing status row is re-keyed to the Check Status 404")
  void verifySchedule11_notFoundIsReKeyed() {
    when(millContextService.validateMillYearActive("807", "2021"))
        .thenReturn(new MillYearContext(807, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verify(ScheduleTrack.SCHEDULE_11, 807, 2021, "verifyadmin", null))
        .thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.verifySchedule11("807", "2021", authentication))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  // -----------------------------------------------------------------------------------------------
  // Story 18.1 — the two admin reversals
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("set-to-draft: the guard runs BEFORE the transition, on the raw params")
  void setToDraft_guardRunsBeforeTheTransition() {
    when(millContextService.validateMillYearActive("790", "2021"))
        .thenReturn(new MillYearContext(790, 2021));
    when(authentication.getName()).thenReturn("reversaladmin");
    when(transitionService.reverse(790, 2021, TrackTransition.SET_TO_DRAFT, "reversaladmin"))
        .thenReturn("D");
    when(messageSource.getMessage(eq("sch1-10DraftMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 have been set back to draft.");

    var response = controller.setSchedules1To10ToDraft("790", "2021", authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().trackStatus()).isEqualTo("D");
    assertThat(response.getBody().message().key()).isEqualTo("sch1-10DraftMsg");
    assertThat(response.getBody().message().text())
        .isEqualTo("Schedules 1-10 have been set back to draft.");

    InOrder order = inOrder(millContextService, transitionService);
    order.verify(millContextService).validateMillYearActive("790", "2021");
    order
        .verify(transitionService)
        .reverse(790, 2021, TrackTransition.SET_TO_DRAFT, "reversaladmin");
  }

  @Test
  @DisplayName("set-to-submit: the guard runs first and legacy's submit text is reused")
  void setToSubmit_guardRunsBeforeTheTransition() {
    when(millContextService.validateMillYearActive("791", "2021"))
        .thenReturn(new MillYearContext(791, 2021));
    when(authentication.getName()).thenReturn("reversaladmin");
    when(transitionService.reverse(791, 2021, TrackTransition.SET_TO_SUBMIT, "reversaladmin"))
        .thenReturn("S");
    // Legacy minted no "verification reversed" message — CheckStatusMB.submitReport:281-283
    // branches on the TARGET code, so V->S lands on the same key a fresh submit does.
    when(messageSource.getMessage(eq("sch1-10SubmittedMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 are successfully submitted.");

    var response = controller.setSchedules1To10ToSubmit("791", "2021", authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().trackStatus()).isEqualTo("S");
    assertThat(response.getBody().message().key()).isEqualTo("sch1-10SubmittedMsg");

    InOrder order = inOrder(millContextService, transitionService);
    order.verify(millContextService).validateMillYearActive("791", "2021");
    order
        .verify(transitionService)
        .reverse(791, 2021, TrackTransition.SET_TO_SUBMIT, "reversaladmin");
  }

  @Test
  @DisplayName("both reversals: a closed mill stops at the guard — no transition is attempted")
  void reversals_closedMillNeverReachesTheTransition() {
    when(millContextService.validateMillYearActive("796", "2021"))
        .thenThrow(new MillClosedException());

    assertThatThrownBy(() -> controller.setSchedules1To10ToDraft("796", "2021", authentication))
        .isInstanceOf(MillClosedException.class);
    assertThatThrownBy(() -> controller.setSchedules1To10ToSubmit("796", "2021", authentication))
        .isInstanceOf(MillClosedException.class);
    verifyNoInteractions(transitionService);
  }

  @Test
  @DisplayName("both reversals: missing mill/year is the guard's ERR-001, untranslated")
  void reversals_missingParamsPassThrough() {
    when(millContextService.validateMillYearActive(null, "2021"))
        .thenThrow(new MillYearNotSelectedException());

    assertThatThrownBy(() -> controller.setSchedules1To10ToDraft(null, "2021", authentication))
        .isInstanceOf(MillYearNotSelectedException.class);
    assertThatThrownBy(() -> controller.setSchedules1To10ToSubmit(null, "2021", authentication))
        .isInstanceOf(MillYearNotSelectedException.class);
    verifyNoInteractions(transitionService);
  }

  @Test
  @DisplayName("both reversals: the guard's 404 is re-keyed to the page's own not-found text")
  void reversals_scheduleNotFoundIsReKeyed() {
    when(millContextService.validateMillYearActive("790", "2021"))
        .thenReturn(new MillYearContext(790, 2021));
    when(authentication.getName()).thenReturn("reversaladmin");
    when(transitionService.reverse(
            eq(790L), eq(2021), any(TrackTransition.class), eq("reversaladmin")))
        .thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.setSchedules1To10ToDraft("790", "2021", authentication))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
    assertThatThrownBy(() -> controller.setSchedules1To10ToSubmit("790", "2021", authentication))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("AC3/AC6: a reversal resolves no submitter scope and no directory GUID")
  void reversals_takeNoScopeCheckAndNoGuid() {
    when(millContextService.validateMillYearActive("790", "2021"))
        .thenReturn(new MillYearContext(790, 2021));
    when(authentication.getName()).thenReturn("reversaladmin");
    when(transitionService.reverse(790, 2021, TrackTransition.SET_TO_DRAFT, "reversaladmin"))
        .thenReturn("D");
    when(messageSource.getMessage(eq("sch1-10DraftMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 have been set back to draft.");

    controller.setSchedules1To10ToDraft("790", "2021", authentication);

    // validateSubmitterMillAccess is submit's alone: it stops a dual-role ADMIN+SUBMITTER using
    // ADMIN's browsing scope to submit outside their assignment. Reversing is an ADMIN capability
    // and ADMIN is all-mills, so applying it here would refuse a ministry user acting in their own
    // authority (CheckStatusController:94, MillContextService:81-83).
    verifyNoInteractions(reportSubmission);
    // No getPrincipal() call either — with Recorded.NONE on both reversals there is no identity
    // pair to write and therefore no GUID to resolve.
    verify(authentication, never()).getPrincipal();
  }

  @Test
  @DisplayName(
      "26.1 AC 7: canSubmit is decided per track, each against its OWN status code, never shared")
  void sweep_decidesCanSubmitPerTrackAgainstItsOwnCode() {
    // The two codes differ on purpose: with equal codes a swapped argument would pass unseen.
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514, 2021));
    when(sweepService.sweep(514, 2021))
        .thenReturn(
            new CheckStatusSweepResponse(
                514,
                2021,
                TrackCheckResult.of("S", List.of()),
                TrackCheckResult.of("D", List.of())));
    when(reportSubmission.canSubmit(SUBMITTER, "S", 514)).thenReturn(false);
    when(reportSubmission.canSubmit(SUBMITTER, "D", 514)).thenReturn(true);

    CheckStatusSweepResponse body = controller.checkStatus("514", "2021", SUBMITTER).getBody();

    assertThat(body).isNotNull();
    assertThat(body.schedules1To10().statusCode()).isEqualTo("S");
    assertThat(body.schedules1To10().canSubmit()).isFalse();
    assertThat(body.schedule11().statusCode()).isEqualTo("D");
    assertThat(body.schedule11().canSubmit()).isTrue();
    verify(reportSubmission).canSubmit(SUBMITTER, "S", 514);
    verify(reportSubmission).canSubmit(SUBMITTER, "D", 514);
  }

  @Test
  @DisplayName("26.1 AC 7: the mirror — 1-10 at Draft offered, Schedule 11 at Submitted not")
  void sweep_decidesCanSubmitPerTrack_mirror() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514, 2021));
    when(sweepService.sweep(514, 2021))
        .thenReturn(
            new CheckStatusSweepResponse(
                514,
                2021,
                TrackCheckResult.of("D", List.of()),
                TrackCheckResult.of("S", List.of())));
    when(reportSubmission.canSubmit(SUBMITTER, "D", 514)).thenReturn(true);
    when(reportSubmission.canSubmit(SUBMITTER, "S", 514)).thenReturn(false);

    CheckStatusSweepResponse body = controller.checkStatus("514", "2021", SUBMITTER).getBody();

    assertThat(body).isNotNull();
    assertThat(body.schedules1To10().canSubmit()).isTrue();
    assertThat(body.schedule11().canSubmit()).isFalse();
  }

  @Test
  @DisplayName("26.1 AC 7: a Schedule 11 track with no code is still decided — false, never absent")
  void sweep_schedule11CanSubmitIsAlwaysPresent() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514, 2021));
    when(sweepService.sweep(514, 2021)).thenReturn(emptySweep());

    CheckStatusSweepResponse body = controller.checkStatus("514", "2021", SUBMITTER).getBody();

    assertThat(body).isNotNull();
    assertThat(body.schedule11().canSubmit()).isFalse();
    verify(reportSubmission).canSubmit(eq(SUBMITTER), isNull(), eq(514L));
  }

  @Test
  @DisplayName(
      "15.3 AC 8: submit runs the SAME guard once, before the transition, on the raw params")
  void submit_guardRunsBeforeTheTransition() {
    when(millContextService.validateMillYearActive("760", "2021"))
        .thenReturn(new MillYearContext(760, 2021));
    when(transitionService.submit(ONE_TO_TEN, 760, 2021, SUBMITTER, "dev-submitter"))
        .thenReturn("sch1-10SubmittedMsg");
    when(messageSource.getMessage(
            eq("sch1-10SubmittedMsg"), isNull(), eq("sch1-10SubmittedMsg"), any(Locale.class)))
        .thenReturn("Schedules 1-10 are successfully submitted.");

    var response = controller.submit("760", "2021", SUBMITTER);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message().key()).isEqualTo("sch1-10SubmittedMsg");
    assertThat(response.getBody().message().text())
        .isEqualTo("Schedules 1-10 are successfully submitted.");
    InOrder order = inOrder(millContextService, reportSubmission, transitionService);
    order.verify(millContextService).validateMillYearActive("760", "2021");
    order.verify(reportSubmission).validateSubmitterMillAccess(SUBMITTER, 760);
    order.verify(transitionService).submit(ONE_TO_TEN, 760, 2021, SUBMITTER, "dev-submitter");
    verifyNoInteractions(sweepService);
  }

  @Test
  @DisplayName("15.3 AC 8: missing mill/year on submit is the guard's ERR-001, untranslated")
  void submit_missingParams_millYearNotSelectedPassesThrough() {
    when(millContextService.validateMillYearActive("abc", null))
        .thenThrow(new MillYearNotSelectedException());

    assertThatThrownBy(() -> controller.submit("abc", null, SUBMITTER))
        .isInstanceOf(MillYearNotSelectedException.class);
    verifyNoInteractions(transitionService);
  }

  @Test
  @DisplayName(
      "verify: an absent mill-year is re-keyed to the Check Status not-found, as the GET is")
  void verify_absentContextIsTheCheckStatusNotFound() {
    ScheduleNotFoundException guardFailure = new ScheduleNotFoundException();
    when(millContextService.validateMillYearActive("999999", "2021")).thenThrow(guardFailure);

    assertThatThrownBy(() -> controller.verifySchedules1To10("999999", "2021", authentication))
        .isInstanceOfSatisfying(
            CheckStatusScheduleNotFoundException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getMessageKey()).isEqualTo("checkStatusScheduleNotFoundErrorMsg");
            });
    verifyNoInteractions(transitionService);
  }

  @Test
  @DisplayName("15.3 AC 8: no status row on submit is the CHECK STATUS not-found, re-keyed")
  void submit_absentContext_isTheCheckStatusNotFound() {
    ScheduleNotFoundException guardFailure = new ScheduleNotFoundException();
    when(millContextService.validateMillYearActive("999999", "2021")).thenThrow(guardFailure);

    assertThatThrownBy(() -> controller.submit("999999", "2021", SUBMITTER))
        .isInstanceOfSatisfying(
            CheckStatusScheduleNotFoundException.class,
            ex -> {
              assertThat(ex.getMessageKey()).isEqualTo("checkStatusScheduleNotFoundErrorMsg");
              assertThat(ex.getCause()).isSameAs(guardFailure);
            });
    verifyNoInteractions(transitionService);
  }

  @Test
  @DisplayName("verify: security off — the mock principal's GUID is recorded, not NULL")
  void verify_mockPrincipalGuidIsResolved() {
    // MockPrincipalFilter seeds this shape when ilcr.security.enabled=false, which is dev/UAT and
    // the whole e2e suite. Handling only JwtAuthenticationToken wrote NULL into both auditor
    // columns in every environment the e2e suite can reach.
    var mockAuth =
        new UsernamePasswordAuthenticationToken(
            new MockUserPrincipal("mockadmin", "MOCKGUID0000111122223333AAAA0001"), "n/a");
    when(millContextService.validateMillYearActive("764", "2021"))
        .thenReturn(new MillYearContext(764, 2021));
    when(transitionService.verify(
            ScheduleTrack.SCHEDULES_1_TO_10,
            764,
            2021,
            "mockadmin",
            "MOCKGUID0000111122223333AAAA0001"))
        .thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 status has been updated to verified.");

    var response = controller.verifySchedules1To10("764", "2021", mockAuth);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(transitionService)
        .verify(
            ScheduleTrack.SCHEDULES_1_TO_10,
            764,
            2021,
            "mockadmin",
            "MOCKGUID0000111122223333AAAA0001");
  }

  @Test
  @DisplayName("verify: a blank directory GUID is passed as null, not as an empty string")
  void verify_blankGuidBecomesNull() {
    // JwtPrincipalUtil.getIdpUserId returns "" — never null — when custom:idp_user_id is absent,
    // so without normalizing, the documented no-identity short-circuit was dead for real tokens
    // and a pointless cross-reference lookup on an empty GUID ran instead.
    var mockAuth =
        new UsernamePasswordAuthenticationToken(new MockUserPrincipal("mockadmin", "  "), "n/a");
    when(millContextService.validateMillYearActive("764", "2021"))
        .thenReturn(new MillYearContext(764, 2021));
    when(transitionService.verify(ScheduleTrack.SCHEDULES_1_TO_10, 764, 2021, "mockadmin", null))
        .thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 status has been updated to verified.");

    controller.verifySchedules1To10("764", "2021", mockAuth);

    verify(transitionService).verify(ScheduleTrack.SCHEDULES_1_TO_10, 764, 2021, "mockadmin", null);
  }

  @Test
  @DisplayName("verify: a missing bundle key falls back to the key, never a post-commit 500")
  void verify_missingMessageKeyFallsBackToTheKey() {
    // The transition has already COMMITTED by the time the message is resolved, so the 3-arg
    // getMessage's NoSuchMessageException would answer a succeeded sign-off with a 500.
    when(millContextService.validateMillYearActive("764", "2021"))
        .thenReturn(new MillYearContext(764, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verify(ScheduleTrack.SCHEDULES_1_TO_10, 764, 2021, "verifyadmin", null))
        .thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("sch1-10VerifiedMsg");

    var response = controller.verifySchedules1To10("764", "2021", authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().message().text()).isEqualTo("sch1-10VerifiedMsg");
  }

  @Test
  @DisplayName("verify: a not-found raised by the transition itself is re-keyed the same way")
  void verify_transitionNotFoundIsReKeyed() {
    when(millContextService.validateMillYearActive("764", "2021"))
        .thenReturn(new MillYearContext(764, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verify(ScheduleTrack.SCHEDULES_1_TO_10, 764, 2021, "verifyadmin", null))
        .thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.verifySchedules1To10("764", "2021", authentication))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  @Test
  @DisplayName(
      "a not-found raised inside the transaction (row gone under the lock) is re-keyed too")
  void submit_notFoundInsideTheTransaction_isTheCheckStatusNotFound() {
    when(millContextService.validateMillYearActive("760", "2021"))
        .thenReturn(new MillYearContext(760, 2021));
    when(transitionService.submit(ONE_TO_TEN, 760, 2021, SUBMITTER, "dev-submitter"))
        .thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.submit("760", "2021", SUBMITTER))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("the transition's own 409s pass through untranslated (two different texts)")
  void submit_transitionRefusalsPassThrough() {
    when(millContextService.validateMillYearActive("515", "2021"))
        .thenReturn(new MillYearContext(515, 2021));
    when(transitionService.submit(ONE_TO_TEN, 515, 2021, SUBMITTER, "dev-submitter"))
        .thenThrow(new ReportNotSubmittedException());

    assertThatThrownBy(() -> controller.submit("515", "2021", SUBMITTER))
        .isInstanceOf(ReportNotSubmittedException.class);
  }

  @Test
  @DisplayName(
      "26.1 D4: Schedule 11 submit runs the same guard and mill scope, then names SCHEDULE_11")
  void submitSchedule11_guardThenScopeThenTheSchedule11Track() {
    when(millContextService.validateMillYearActive("784", "2021"))
        .thenReturn(new MillYearContext(784, 2021));
    when(transitionService.submit(ScheduleTrack.SCHEDULE_11, 784, 2021, SUBMITTER, "dev-submitter"))
        .thenReturn("sch11SubmittedMsg");
    when(messageSource.getMessage(
            eq("sch11SubmittedMsg"), isNull(), eq("sch11SubmittedMsg"), any(Locale.class)))
        .thenReturn("Schedule 11 has been successfully submitted.");

    var response = controller.submitSchedule11("784", "2021", SUBMITTER);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message().key()).isEqualTo("sch11SubmittedMsg");
    assertThat(response.getBody().message().text())
        .isEqualTo("Schedule 11 has been successfully submitted.");
    InOrder order = inOrder(millContextService, reportSubmission, transitionService);
    order.verify(millContextService).validateMillYearActive("784", "2021");
    order.verify(reportSubmission).validateSubmitterMillAccess(SUBMITTER, 784);
    order
        .verify(transitionService)
        .submit(ScheduleTrack.SCHEDULE_11, 784, 2021, SUBMITTER, "dev-submitter");
    verify(transitionService, never())
        .submit(eq(ScheduleTrack.SCHEDULES_1_TO_10), anyLong(), anyInt(), any(), any());
    verifyNoInteractions(sweepService);
  }

  @Test
  @DisplayName("26.1 AC 4: a Schedule 11 not-found inside the transaction is re-keyed too")
  void submitSchedule11_notFound_isTheCheckStatusNotFound() {
    when(millContextService.validateMillYearActive("784", "2021"))
        .thenReturn(new MillYearContext(784, 2021));
    when(transitionService.submit(ScheduleTrack.SCHEDULE_11, 784, 2021, SUBMITTER, "dev-submitter"))
        .thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.submitSchedule11("784", "2021", SUBMITTER))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("26.1 AC 4: a Schedule 11 submit whose context guard refuses reaches no service")
  void submitSchedule11_guardRefusal_neverReachesTheService() {
    when(millContextService.validateMillYearActive(null, "2021"))
        .thenThrow(new MillYearNotSelectedException());

    assertThatThrownBy(() -> controller.submitSchedule11(null, "2021", SUBMITTER))
        .isInstanceOf(MillYearNotSelectedException.class);
    verifyNoInteractions(transitionService, reportSubmission);
  }
}
