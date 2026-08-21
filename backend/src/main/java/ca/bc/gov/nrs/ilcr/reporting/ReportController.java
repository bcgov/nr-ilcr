package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.reporting.api.PrintRequest;
import ca.bc.gov.nrs.ilcr.reporting.api.ReportApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Print Schedule PDF endpoints (Epic 20). Authorizes by naming the action (AD-7) — {@code
 * VIEW_SCHEDULE}, because printing is read-only for every role (BR-01) — delegates ALL mill/year
 * validation to {@link MillContextService} as its first line (AD-4), and streams the PDF from
 * {@link ReportService} (AD-16). It never touches repositories directly (AD-1).
 *
 * <p>Mirrors the Schedule 9 read controller's guard/auth posture; the only difference is the binary
 * response ({@code application/pdf} + an attachment Content-Disposition). The empty-schedule 404 is
 * raised inside the service ({@code ScheduleNotFoundException}) and rendered by the global handler
 * as the verbatim {@code Schedule not found.} (ERR-005) — no PDF is produced.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class ReportController implements ReportApi {

  private static final Logger log = LoggerFactory.getLogger(ReportController.class);

  private final MillContextService millContextService;
  private final ReportService reportService;
  private final PrintService printService;

  /**
   * Constructs a new ReportController.
   *
   * @param millContextService the mill context service
   * @param reportService the report service
   * @param printService the print service
   */
  public ReportController(
      MillContextService millContextService,
      ReportService reportService,
      PrintService printService) {
    this.millContextService = millContextService;
    this.reportService = reportService;
    this.printService = printService;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<StreamingResponseBody> getSchedule9Pdf(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    // Fill synchronously (may throw the empty-schedule 404) BEFORE the response is built; only the
    // export streams, so a rejected render still produces a problem+json error, never a
    // half-written PDF.
    RenderedReport report = reportService.renderSchedule9(context.millId(), context.year());
    String filename = "schedule9_" + context.millId() + "_" + context.year() + ".pdf";
    return pdfResponse(filename, context.millId(), context.year(), report);
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<StreamingResponseBody> printSchedules(
      String millId, String year, PrintRequest request, Authentication authentication) {
    // Guard order: mill/year context first (400/404/409), THEN the selection ladder before any
    // fill.
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    validateSelection(request);
    RenderedReport report = printService.render(context, request);
    return pdfResponse("schedules_print.pdf", context.millId(), context.year(), report);
  }

  /**
   * Stream a filled report as an {@code application/pdf} attachment. The {@link
   * StreamingResponseBody} exports directly to the servlet output stream (no full-PDF {@code
   * byte[]} on the heap, Story 29.2) and try-with-resources closes the {@link RenderedReport} on
   * both success and failure, so the virtualizer's swap file is never leaked. The status + headers
   * are set on the ResponseEntity here, before any byte is written, so the attachment filename and
   * content type are always applied.
   *
   * <p>An export failure surfaces DIFFERENTLY from the pre-fill guards: by the time bytes are
   * written the 200 + {@code application/pdf} headers are already committed, so no
   * {@code @ExceptionHandler} can turn it into a {@code problem+json} — the client just gets a
   * truncated PDF. It is therefore logged at ERROR with the mill/year (the only server-side signal
   * ops can correlate with a user's "the PDF won't open") before being rethrown so the container
   * aborts the response. The async render runs under {@code spring.mvc.async.request-timeout}; a
   * timeout produces the same truncated shape.
   */
  private static ResponseEntity<StreamingResponseBody> pdfResponse(
      String filename, long millId, int year, RenderedReport report) {
    StreamingResponseBody body =
        out -> {
          try (report) {
            report.writeTo(out);
          } catch (RuntimeException e) {
            log.error(
                "Report export failed after the response was committed for mill {} year {} ({}) — "
                    + "the client received a truncated PDF",
                millId,
                year,
                filename,
                e);
            throw e;
          }
        };
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(body);
  }

  /**
   * The legacy selection validation ladder in verbatim order (first-match-wins, {@code
   * PrintSchedulesMB.print()}): ERR-002 when a content option is on but no schedule is selected;
   * ERR-003 when a schedule is selected but neither content option is; ERR-004 when no print option
   * at all is selected. Each throws a 400 carrying the verbatim legacy message (AD-8).
   */
  private static void validateSelection(PrintRequest request) {
    if (request.anyContentOptionSelected() && !request.anyScheduleSelected()) {
      throw PrintSelectionException.noScheduleSelected();
    }
    if (request.anyScheduleSelected() && !request.anyContentOptionSelected()) {
      throw PrintSelectionException.noContentOption();
    }
    if (!request.anyPrintOptionSelected()) {
      throw PrintSelectionException.noPrintOption();
    }
  }
}
