package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.reporting.api.PrintRequest;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Unit tests for the report endpoints' guards and response shape.
 *
 * <p>The integration tests drive these through the real security chain and a real Oracle; these
 * cover the same decisions without a container, which is what the coverage analysis actually sees.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportController")
class ReportControllerTest {

  @Mock private MillContextService millContextService;
  @Mock private ReportService reportService;
  @Mock private PrintService printService;
  @Mock private RenderedReport renderedReport;

  // Constructed by hand rather than @InjectMocks: the report-year guard is a thin @Component over
  // MillContextService (Story 19.2 hoisted it out of a private method on the controller), and a
  // MOCK of it would answer 0 for every year and silently bypass the two 400s these tests assert.
  // The REAL guard over the mocked context service keeps `yearsAre(...)` driving the decision.
  private ReportController controller;

  @BeforeEach
  void createController() {
    controller =
        new ReportController(
            millContextService,
            reportService,
            printService,
            new ReportYearGuard(millContextService));
  }

  private void yearsAre(int... years) {
    when(millContextService.listReportingYears())
        .thenReturn(java.util.Arrays.stream(years).mapToObj(ReportingYear::new).toList());
  }

  @Test
  @DisplayName("an open year streams mills_print.pdf as an application/pdf attachment")
  void openYearStreamsThePdf() throws Exception {
    yearsAre(2021, 2020);
    when(reportService.renderMillInformation(2021)).thenReturn(renderedReport);

    ResponseEntity<StreamingResponseBody> response = controller.getMillInformationPdf("2021", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"mills_print.pdf\"");

    // The body streams on demand, so nothing is written until the container consumes it.
    verify(renderedReport, never()).writeTo(org.mockito.ArgumentMatchers.any());
    response.getBody().writeTo(new ByteArrayOutputStream());
    verify(renderedReport).writeTo(org.mockito.ArgumentMatchers.any());
    verify(renderedReport).close();
  }

  @Test
  @DisplayName("a surrounding whitespace-padded year is still accepted")
  void paddedYearIsAccepted() {
    yearsAre(2021);
    when(reportService.renderMillInformation(2021)).thenReturn(renderedReport);

    assertThat(controller.getMillInformationPdf(" 2021 ", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("absent, blank and non-numeric years all reject as a missing required field")
  void unusableYearsRejectAsRequired() {
    for (String year : new String[] {null, "", "   ", "not-a-year", "20x1"}) {
      assertThatThrownBy(() -> controller.getMillInformationPdf(year, null))
          .as("year=%s", year)
          .isInstanceOf(ReportYearRequiredException.class)
          .extracting(e -> ((ReportYearRequiredException) e).getStatus())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    // The report is never built for an unusable year.
    verify(reportService, never()).renderMillInformation(anyInt());
  }

  @Test
  @DisplayName(
      "a parseable year that is not an open period rejects, rather than reaching the report")
  void yearNotOpenRejects() {
    yearsAre(2021, 2020);

    // "99999999999" is all digits but overflows an int. A year WAS supplied, so it must land here
    // rather than in the required-field rejection above (P9).
    for (String year : new String[] {"1999", "0", "-1", "99999", "99999999999", "-99999999999"}) {
      assertThatThrownBy(() -> controller.getMillInformationPdf(year, null))
          .as("year=%s", year)
          .isInstanceOf(ReportYearNotOpenException.class)
          .extracting(e -> ((ReportYearNotOpenException) e).getStatus())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    verify(reportService, never()).renderMillInformation(anyInt());
  }

  @Test
  @DisplayName("the print selection ladder rejects in the legacy order, first match winning")
  void printSelectionLadder() {
    // A content option with no schedule selected.
    PrintRequest contentOnly = request(false, true, false);
    assertThatThrownBy(() -> controller.printSchedules("514", "2021", contentOnly, null))
        .isInstanceOf(PrintSelectionException.class);

    // A schedule with neither content option.
    PrintRequest scheduleOnly = request(true, false, false);
    assertThatThrownBy(() -> controller.printSchedules("514", "2021", scheduleOnly, null))
        .isInstanceOf(PrintSelectionException.class);

    // Nothing selected at all.
    PrintRequest nothing = request(false, false, false);
    assertThatThrownBy(() -> controller.printSchedules("514", "2021", nothing, null))
        .isInstanceOf(PrintSelectionException.class);
  }

  private static PrintRequest request(
      boolean schedule1, boolean scheduleInformation, boolean comments) {
    return new PrintRequest(
        schedule1,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        scheduleInformation,
        comments,
        false);
  }
}
