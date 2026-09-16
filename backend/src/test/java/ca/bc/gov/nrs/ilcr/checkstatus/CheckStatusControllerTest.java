package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Unit test for {@link CheckStatusController} — the ONE mill/year guard the sweep owns (Story 15.1
 * AC 9) and the sweep's own not-found semantics (AC 5). Mocked guard and sweep, no Spring: the HTTP
 * statuses and verbatim texts are proven by {@code CheckStatusContextGuardIT}; this pins the ORDER
 * (guard before any schedule is touched) and the exception translation, which an IT can only infer.
 */
@ExtendWith(MockitoExtension.class)
class CheckStatusControllerTest {

  @Mock private MillContextService millContextService;
  @Mock private CheckStatusSweepService sweepService;
  @Mock private ReportTransitionService transitionService;
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

    var response = controller.checkStatus("514", "2021");

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

    assertThatThrownBy(() -> controller.checkStatus("516", "2021"))
        .isInstanceOf(MillClosedException.class);
    verifyNoInteractions(sweepService);
  }

  @Test
  @DisplayName("AC 5/S05: missing mill/year is the guard's ERR-001, untranslated")
  void missingParams_millYearNotSelectedPassesThrough() {
    when(millContextService.validateMillYearActive(null, "2021"))
        .thenThrow(new MillYearNotSelectedException());

    assertThatThrownBy(() -> controller.checkStatus(null, "2021"))
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

    assertThatThrownBy(() -> controller.checkStatus("999999", "2021"))
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

    assertThatThrownBy(() -> controller.checkStatus("514", "2021"))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("verify: the guard runs BEFORE the transition, on the raw params")
  void verify_guardRunsBeforeTheTransition() {
    when(millContextService.validateMillYearActive("757", "2021"))
        .thenReturn(new MillYearContext(757, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verifySchedules1To10(757, 2021, "verifyadmin", null)).thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 status has been updated to verified.");

    var response = controller.verifySchedules1To10("757", "2021", authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().trackStatus()).isEqualTo("V");
    assertThat(response.getBody().message().key()).isEqualTo("sch1-10VerifiedMsg");
    assertThat(response.getBody().message().text())
        .isEqualTo("Schedules 1-10 status has been updated to verified.");

    InOrder order = inOrder(millContextService, transitionService);
    order.verify(millContextService).validateMillYearActive("757", "2021");
    order.verify(transitionService).verifySchedules1To10(757, 2021, "verifyadmin", null);
  }

  @Test
  @DisplayName("verify: a closed mill stops at the guard — no transition is attempted")
  void verify_closedMillNeverReachesTheTransition() {
    when(millContextService.validateMillYearActive("761", "2021"))
        .thenThrow(new MillClosedException());

    assertThatThrownBy(() -> controller.verifySchedules1To10("761", "2021", authentication))
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
  @DisplayName("verify: security off — the mock principal's GUID is recorded, not NULL")
  void verify_mockPrincipalGuidIsResolved() {
    // MockPrincipalFilter seeds this shape when ilcr.security.enabled=false, which is dev/UAT and
    // the whole e2e suite. Handling only JwtAuthenticationToken wrote NULL into both auditor
    // columns in every environment the e2e suite can reach.
    var mockAuth =
        new UsernamePasswordAuthenticationToken(
            new MockUserPrincipal("mockadmin", "MOCKGUID0000111122223333AAAA0001"), "n/a");
    when(millContextService.validateMillYearActive("757", "2021"))
        .thenReturn(new MillYearContext(757, 2021));
    when(transitionService.verifySchedules1To10(
            757, 2021, "mockadmin", "MOCKGUID0000111122223333AAAA0001"))
        .thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 status has been updated to verified.");

    var response = controller.verifySchedules1To10("757", "2021", mockAuth);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(transitionService)
        .verifySchedules1To10(757, 2021, "mockadmin", "MOCKGUID0000111122223333AAAA0001");
  }

  @Test
  @DisplayName("verify: a blank directory GUID is passed as null, not as an empty string")
  void verify_blankGuidBecomesNull() {
    // JwtPrincipalUtil.getIdpUserId returns "" — never null — when custom:idp_user_id is absent,
    // so without normalizing, the documented no-identity short-circuit was dead for real tokens
    // and a pointless cross-reference lookup on an empty GUID ran instead.
    var mockAuth =
        new UsernamePasswordAuthenticationToken(new MockUserPrincipal("mockadmin", "  "), "n/a");
    when(millContextService.validateMillYearActive("757", "2021"))
        .thenReturn(new MillYearContext(757, 2021));
    when(transitionService.verifySchedules1To10(757, 2021, "mockadmin", null)).thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("Schedules 1-10 status has been updated to verified.");

    controller.verifySchedules1To10("757", "2021", mockAuth);

    verify(transitionService).verifySchedules1To10(757, 2021, "mockadmin", null);
  }

  @Test
  @DisplayName("verify: a missing bundle key falls back to the key, never a post-commit 500")
  void verify_missingMessageKeyFallsBackToTheKey() {
    // The transition has already COMMITTED by the time the message is resolved, so the 3-arg
    // getMessage's NoSuchMessageException would answer a succeeded sign-off with a 500.
    when(millContextService.validateMillYearActive("757", "2021"))
        .thenReturn(new MillYearContext(757, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verifySchedules1To10(757, 2021, "verifyadmin", null)).thenReturn("V");
    when(messageSource.getMessage(eq("sch1-10VerifiedMsg"), any(), any(), any()))
        .thenReturn("sch1-10VerifiedMsg");

    var response = controller.verifySchedules1To10("757", "2021", authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().message().text()).isEqualTo("sch1-10VerifiedMsg");
  }

  @Test
  @DisplayName("verify: a not-found raised by the transition itself is re-keyed the same way")
  void verify_transitionNotFoundIsReKeyed() {
    when(millContextService.validateMillYearActive("757", "2021"))
        .thenReturn(new MillYearContext(757, 2021));
    when(authentication.getName()).thenReturn("verifyadmin");
    when(transitionService.verifySchedules1To10(757, 2021, "verifyadmin", null))
        .thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.verifySchedules1To10("757", "2021", authentication))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }
}
