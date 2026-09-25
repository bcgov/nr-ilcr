package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReportTransitionRejectedExceptionTest {

  @Test
  @DisplayName("a named transition carries its own rejection key at 409")
  void namedTransition() {
    ReportTransitionRejectedException ex =
        new ReportTransitionRejectedException(
            TrackTransition.SUBMIT, ScheduleTrack.SCHEDULES_1_TO_10);

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("submitNotDraftErrorMsg");
  }

  @Test
  @DisplayName("the same transition on Schedule 11 names Schedule 11, not Schedules 1-10")
  void namedTransitionOnSchedule11() {
    ReportTransitionRejectedException ex =
        new ReportTransitionRejectedException(TrackTransition.SUBMIT, ScheduleTrack.SCHEDULE_11);

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("sch11SubmitNotDraftErrorMsg");
  }

  @Test
  @DisplayName("a null transition is still a 409, with legacy's generic text — never a 500")
  void nullTransition() {
    ReportTransitionRejectedException ex =
        new ReportTransitionRejectedException(null, ScheduleTrack.SCHEDULES_1_TO_10);

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
  }

  @Test
  @DisplayName("the no-arg constructor maps to the generic text at 409")
  void genericConstructor() {
    ReportTransitionRejectedException ex = new ReportTransitionRejectedException();

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
  }
}
