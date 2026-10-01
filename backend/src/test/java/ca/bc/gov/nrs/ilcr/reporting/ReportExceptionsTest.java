package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The status and message key each report rejection carries.
 *
 * <p>Small, but not ceremonial: the key is what {@code GlobalExceptionHandler} resolves to the
 * user-facing text, so a typo here surfaces as a blank or wrong message rather than as a failure,
 * and the status is what separates "you asked for the wrong thing" from "we broke".
 */
@DisplayName("Report exceptions")
class ReportExceptionsTest {

  @Test
  @DisplayName("a missing report year is a 400 carrying the required-field key")
  void reportYearRequired() {
    ReportYearRequiredException exception = new ReportYearRequiredException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessageKey()).isEqualTo("reportYearRequiredErrorMsg");
    assertThat(exception.getMessageArgs()).isNull();
  }

  @Test
  @DisplayName("a year that is not an open period is a 400, distinct from the missing-year key")
  void reportYearNotOpen() {
    ReportYearNotOpenException exception = new ReportYearNotOpenException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessageKey()).isEqualTo("reportYearNotOpenErrorMsg");
    // Distinct from the required-field rejection: one is "you gave me nothing", the other is
    // "you gave me a year nobody opened", and they must not collapse into one message.
    assertThat(exception.getMessageKey())
        .isNotEqualTo(new ReportYearRequiredException().getMessageKey());
  }

  @Test
  @DisplayName("an open year with no mills is a 404 with its own key, never undefinedError")
  void millInformationNoMills() {
    MillInformationNoMillsException exception = new MillInformationNoMillsException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exception.getMessageKey()).isEqualTo("millInformationNoMillsErrorMsg");
    // Must not collapse into the catch-all: one means "no data for that year", the other means
    // "something broke", and only the second should ever raise an alert.
    assertThat(exception.getMessageKey())
        .isNotEqualTo(new MillInformationReportException().getMessageKey());
  }

  @Test
  @DisplayName("a failed build is a 500 carrying the legacy undefinedError key")
  void millInformationReportFailure() {
    MillInformationReportException exception = new MillInformationReportException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(exception.getMessageKey()).isEqualTo("undefinedError");
  }

  @Test
  @DisplayName("the deferred mill-info-only print rejection still answers 404 with its own key")
  void millInformationReportUnavailable() {
    MillInformationReportUnavailableException exception =
        new MillInformationReportUnavailableException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exception.getMessageKey()).isEqualTo("millInformationReportUnavailableMsg");
  }
}
