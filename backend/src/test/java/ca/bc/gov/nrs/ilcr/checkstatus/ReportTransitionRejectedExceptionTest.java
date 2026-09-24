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
        new ReportTransitionRejectedException(TrackTransition.SUBMIT);

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("submitNotDraftErrorMsg");
  }

  @Test
  @DisplayName("a null transition is still a 409, with legacy's generic text — never a 500")
  void nullTransition() {
    ReportTransitionRejectedException ex = new ReportTransitionRejectedException(null);

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
  }

  @Test
  @SuppressWarnings("deprecation")
  @DisplayName("the no-arg constructor kept for older callers maps to the generic text at 409")
  void legacyConstructor() {
    ReportTransitionRejectedException ex = new ReportTransitionRejectedException();

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
  }
}
