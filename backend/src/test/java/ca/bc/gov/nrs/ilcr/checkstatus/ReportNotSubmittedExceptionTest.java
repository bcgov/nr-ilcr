package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReportNotSubmittedExceptionTest {

  @Test
  @DisplayName("with no transition to name, the gate refusal is legacy's one text at 409")
  void generic() {
    ReportNotSubmittedException ex = new ReportNotSubmittedException();

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("reportNotSubmittedErrorMsg");
  }

  @Test
  @DisplayName("a reversal's gate refusal carries the reversal's own text")
  void reversalOnSchedules1To10() {
    ReportNotSubmittedException ex =
        new ReportNotSubmittedException(
            TrackTransition.SET_TO_DRAFT, ScheduleTrack.SCHEDULES_1_TO_10);

    assertThat(ex.getMessageKey()).isEqualTo("setToDraftNotValidErrorMsg");
  }

  @Test
  @DisplayName("Schedule 11's submit gate keeps legacy's shared text (CheckStatusMB:235)")
  void submitOnSchedule11() {
    ReportNotSubmittedException ex =
        new ReportNotSubmittedException(TrackTransition.SUBMIT, ScheduleTrack.SCHEDULE_11);

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("reportNotSubmittedErrorMsg");
  }
}
