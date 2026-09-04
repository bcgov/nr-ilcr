package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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
}
