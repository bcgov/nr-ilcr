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

  @Mock private MillContextService millContextService;
  @Mock private CheckStatusSweepService sweepService;
  @Mock private ReportTrackTransitionService transitionService;
  @Mock private ReportSubmission reportSubmission;
  @Mock private MessageSource messageSource;
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
  @DisplayName(
      "15.3 AC 9: canSubmit is the offer rule's answer for the 1-10 status; Schedule 11 carries none")
  void sweep_carriesCanSubmitOnTheOneToTenTrackOnly() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514, 2021));
    when(sweepService.sweep(514, 2021)).thenReturn(emptySweep());
    when(reportSubmission.canSubmit(SUBMITTER, "D")).thenReturn(true);

    CheckStatusSweepResponse body = controller.checkStatus("514", "2021", SUBMITTER).getBody();

    assertThat(body).isNotNull();
    assertThat(body.schedules1To10().canSubmit()).isTrue();
    assertThat(body.schedules1To10().statusCode()).isEqualTo("D");
    assertThat(body.schedule11().canSubmit()).isNull();
    verify(reportSubmission).canSubmit(SUBMITTER, "D");
    verify(reportSubmission, never()).canSubmit(any(), isNull());
  }

  @Test
  @DisplayName(
      "15.3 AC 8: submit runs the SAME guard once, before the transition, on the raw params")
  void submit_guardRunsBeforeTheTransition() {
    when(millContextService.validateMillYearActive("760", "2021"))
        .thenReturn(new MillYearContext(760, 2021));
    when(transitionService.submit(760, 2021, SUBMITTER, "dev-submitter"))
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
    InOrder order = inOrder(millContextService, transitionService);
    order.verify(millContextService).validateMillYearActive("760", "2021");
    order.verify(transitionService).submit(760, 2021, SUBMITTER, "dev-submitter");
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
  @DisplayName(
      "a not-found raised inside the transaction (row gone under the lock) is re-keyed too")
  void submit_notFoundInsideTheTransaction_isTheCheckStatusNotFound() {
    when(millContextService.validateMillYearActive("760", "2021"))
        .thenReturn(new MillYearContext(760, 2021));
    when(transitionService.submit(760, 2021, SUBMITTER, "dev-submitter"))
        .thenThrow(new ScheduleNotFoundException());

    assertThatThrownBy(() -> controller.submit("760", "2021", SUBMITTER))
        .isInstanceOf(CheckStatusScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("the transition's own 409s pass through untranslated (two different texts)")
  void submit_transitionRefusalsPassThrough() {
    when(millContextService.validateMillYearActive("515", "2021"))
        .thenReturn(new MillYearContext(515, 2021));
    when(transitionService.submit(515, 2021, SUBMITTER, "dev-submitter"))
        .thenThrow(new ReportNotSubmittedException());

    assertThatThrownBy(() -> controller.submit("515", "2021", SUBMITTER))
        .isInstanceOf(ReportNotSubmittedException.class);
  }
}
