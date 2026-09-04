package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.reporting.api.PrintRequest;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
  //
  // The spooler is real for the same class of reason: it is the component that moves the export in
  // FRONT of the response commit, so mocking it would erase the very ordering these tests exist to
  // pin. Pointed at a @TempDir, so the spool files are visible to assertions and reaped by JUnit.
  private ReportController controller;

  @TempDir private Path spoolDirectory;

  @BeforeEach
  void createController() {
    controller =
        new ReportController(
            millContextService,
            reportService,
            printService,
            new ReportYearGuard(millContextService),
            new PdfSpooler(spoolDirectory.toString()));
  }

  /** The bytes a stubbed export writes, standing in for a real Jasper PDF. */
  private static final byte[] EXPORTED =
      "%PDF-1.4 ... %%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);

  /** Make the mocked report export {@link #EXPORTED} to whatever stream the spooler hands it. */
  private void exportWrites() {
    doAnswer(
            invocation -> {
              ((OutputStream) invocation.getArgument(0)).write(EXPORTED);
              return null;
            })
        .when(renderedReport)
        .writeTo(any());
  }

  /** The spool files still on disk — empty once a response has been fully streamed. */
  private java.util.List<Path> spooled() throws Exception {
    try (var files = Files.list(spoolDirectory)) {
      return files.toList();
    }
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
    exportWrites();

    ResponseEntity<Resource> response = controller.getMillInformationPdf("2021", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"mills_print.pdf\"");

    // The export has ALREADY run — before the ResponseEntity existed, which is the whole point.
    // (It used to be asserted the other way round: writeTo was deferred into a streaming body,
    // which is exactly what put export failures past the point of no return.)
    verify(renderedReport).writeTo(any());
    verify(renderedReport).close();

    // The body is the finished file, not a deferred callback.
    try (InputStream sent = response.getBody().getInputStream()) {
      assertThat(sent.readAllBytes()).isEqualTo(EXPORTED);
    }
  }

  @Test
  @DisplayName(
      "the response declares the exported PDF's exact length, so a short read is detectable")
  void responseCarriesContentLength() {
    yearsAre(2021);
    when(reportService.renderMillInformation(2021)).thenReturn(renderedReport);
    exportWrites();

    ResponseEntity<Resource> response = controller.getMillInformationPdf("2021", null);

    // Content-Length is only expressible because the export finished first. It length-delimits the
    // body, so a transfer cut short after the commit fails the request at the browser instead of
    // arriving as a plausible-looking short PDF.
    assertThat(response.getHeaders().getContentLength()).isEqualTo(EXPORTED.length);
  }

  @Test
  @DisplayName("an export failure throws before any response exists, and leaves no spool behind")
  void exportFailureNeverBecomesAResponse() throws Exception {
    yearsAre(2021);
    when(reportService.renderMillInformation(2021)).thenReturn(renderedReport);
    doThrow(new ReportGenerationException("export blew up", null))
        .when(renderedReport)
        .writeTo(any());

    // The failure Paulo's review was about. It now surfaces as an ordinary throw on the synchronous
    // path — so the global handler renders 500 undefinedError and the caller gets no file — rather
    // than as a truncated 200 the browser saves.
    assertThatThrownBy(() -> controller.getMillInformationPdf("2021", null))
        .isInstanceOf(ReportGenerationException.class);

    verify(renderedReport).close();
    assertThat(spooled()).as("the partial spool is deleted").isEmpty();
  }

  @Test
  @DisplayName("the spooled file is deleted once the body has been streamed")
  void spoolIsReapedAfterStreaming() throws Exception {
    yearsAre(2021);
    when(reportService.renderMillInformation(2021)).thenReturn(renderedReport);
    exportWrites();

    ResponseEntity<Resource> response = controller.getMillInformationPdf("2021", null);
    assertThat(spooled()).as("held until the body is sent").hasSize(1);

    // The converter closes the stream once the response is written; that is what reaps the spool.
    response.getBody().getInputStream().close();
    assertThat(spooled()).as("reaped after the body is sent").isEmpty();
  }

  @Test
  @DisplayName("a partially read body still reaps its spool when the stream closes")
  void spoolIsReapedWhenTheTransferIsCutShort() throws Exception {
    yearsAre(2021);
    when(reportService.renderMillInformation(2021)).thenReturn(renderedReport);
    exportWrites();

    ResponseEntity<Resource> response = controller.getMillInformationPdf("2021", null);

    // A client that disconnects mid-download: the converter's copy aborts and it closes the stream
    // having read only part of the file. Cleanup hangs off close(), not off completion, so the
    // spool goes either way.
    try (InputStream body = response.getBody().getInputStream()) {
      assertThat(body.read()).isNotEqualTo(-1);
    }
    assertThat(spooled()).isEmpty();
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
  @DisplayName("the drill-down streams mill_<millNumber>_print.pdf — the mill NUMBER, not the id")
  void drillDownStreamsTheParityFilename() {
    yearsAre(2021, 2020);
    // Mill id 730 carries mill number 7300. Legacy named the file from the NUMBER
    // (PrintSchedulesMB.java:332), so a filename built from the path's id would be wrong on every
    // mill — and would still look plausible, which is why the two differ in this fixture.
    when(reportService.renderMillInformation(730L, 2021))
        .thenReturn(new ReportService.MillDrillDown("7300", renderedReport));

    ResponseEntity<Resource> response = controller.getMillDrillDownPdf(730L, "2021", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"mill_7300_print.pdf\"");
  }

  @Test
  @DisplayName("a padded mill number is stripped, so both sides derive the same filename")
  void drillDownFilenameStripsPadding() {
    yearsAre(2021);
    // The frontend's millNumberOrNull trims before building the name it saves under, and the
    // frontend is only allowed to skip parsing this header BECAUSE the two derivations agree. A
    // padded MILL_NUMBER — a nullable text column with no non-blank constraint — used to make them
    // disagree: "mill_ 7300 _print.pdf" here against "mill_7300_print.pdf" there.
    when(reportService.renderMillInformation(730L, 2021))
        .thenReturn(new ReportService.MillDrillDown("  7300  ", renderedReport));

    assertThat(
            controller
                .getMillDrillDownPdf(730L, "2021", null)
                .getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("attachment; filename=\"mill_7300_print.pdf\"");
  }

  @Test
  @DisplayName("a mill with no mill number names the file by mill id, never \"mill_null\"")
  void drillDownFilenameFallsBackToTheMillId() {
    // MILL.MILL_NUMBER is NUMBER(15) and nullable (V1__the_schedule1_snapshot.sql:10), so NULL is
    // the reachable delivery state and interpolating it raw would offer the administrator
    // "mill_null_print.pdf".
    //
    // The two BLANK cases are defence-in-depth at the DTO layer, NOT delivery states: a NUMBER
    // column cannot hold "" or "   ". They are kept because the value reaches here as a String —
    // MillInformationSection.millNumber — after crossing the row-entity boundary, and because the
    // frontend's own fallback has to handle blanks for millNAME, which IS a nullable VARCHAR2(100).
    // Review round 1 corrected this comment: it previously claimed MILL_NUMBER was "a VARCHAR2 with
    // no NOT-NULL-or-non-blank constraint", which is false, and that false claim made this test
    // read as proof that the two-sided filename contract held on blanks — while the frontend half
    // was still using `??` and did not handle them at all (patch P5).
    //
    // The frontend applies the SAME fallback to the same row (it never reads this header), so the
    // two must agree.
    for (String millNumber : new String[] {null, "", "   "}) {
      yearsAre(2021);
      when(reportService.renderMillInformation(730L, 2021))
          .thenReturn(new ReportService.MillDrillDown(millNumber, renderedReport));

      assertThat(
              controller
                  .getMillDrillDownPdf(730L, "2021", null)
                  .getHeaders()
                  .getFirst(HttpHeaders.CONTENT_DISPOSITION))
          .as("millNumber=%s", millNumber)
          .isEqualTo("attachment; filename=\"mill_730_print.pdf\"");
    }
  }

  @Test
  @DisplayName("the drill-down rejects an unusable or unopened year without building anything")
  void drillDownRejectsBadYears() {
    // The SAME guard as the all-mills endpoint, deliberately: ReportYearGuard is shared so the two
    // 400 texts cannot drift. Asserted on this path too, because "the guard runs FIRST" is the
    // property that keeps a bad year from reaching the read.
    for (String year : new String[] {null, "", "   ", "not-a-year"}) {
      assertThatThrownBy(() -> controller.getMillDrillDownPdf(730L, year, null))
          .as("year=%s", year)
          .isInstanceOf(ReportYearRequiredException.class);
    }

    yearsAre(2021, 2020);
    for (String year : new String[] {"1899", "1999", "0", "99999999999"}) {
      assertThatThrownBy(() -> controller.getMillDrillDownPdf(730L, year, null))
          .as("year=%s", year)
          .isInstanceOf(ReportYearNotOpenException.class);
    }

    verify(reportService, never()).renderMillInformation(anyLong(), anyInt());
  }

  @Test
  @DisplayName("the drill-down runs NO mill/year context guard — a closed mill stays drillable")
  void drillDownNeverValidatesAMillYearContext() {
    // The fence. MillContextService.validateMillYearActive applies submitter mill scope and rejects
    // a CLOSED mill; closed mills appear in the status table this drill-down launches from, so
    // calling it would make them undrillable. Without this test, "aligning" the endpoint with its
    // schedule siblings is a one-line change that breaks a requirement and passes every other test.
    yearsAre(2021);
    when(reportService.renderMillInformation(730L, 2021))
        .thenReturn(new ReportService.MillDrillDown("7300", renderedReport));

    controller.getMillDrillDownPdf(730L, "2021", null);

    verify(millContextService, never()).validateMillYearActive(anyString(), anyString());
    verify(millContextService, never()).validateMillYearActive(anyLong(), anyInt());
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
