package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.reporting.api.PrintRequest;
import ca.bc.gov.nrs.ilcr.reporting.api.ReportApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report PDF endpoints. Authorizes by naming the action (AD-7), delegates mill/year validation to
 * {@link MillContextService} as its first line (AD-4), and streams the PDF from {@link
 * ReportService} (AD-16). It never touches repositories directly (AD-1).
 *
 * <p>The schedule endpoints authorize on {@code VIEW_SCHEDULE} — printing is read-only for every
 * role (BR-01) — and validate a mill/year working context. The two Mill Information endpoints do
 * NEITHER: they are administrator-only ({@code GENERATE_MILL_REPORTS}) and answer for a chosen
 * report year rather than the Home selection, so there is no working context to validate. Both
 * differences are behaviour, not oversight.
 *
 * <p>The per-mill drill-down DOES take a mill, and still runs no context guard — it must not, or a
 * closed mill would stop being drillable from a status table that deliberately lists it.
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
  private final ReportYearGuard reportYearGuard;
  private final PdfSpooler pdfSpooler;

  /**
   * Constructs a new ReportController.
   *
   * @param millContextService the mill context service
   * @param reportService the report service
   * @param printService the print service
   * @param reportYearGuard the shared report-year guard (Story 19.2 hoisted this out of a private
   *     method here so the Mill Report Status endpoint rejects a bad year with identical text)
   * @param pdfSpooler exports each filled report to a temp file before the response is built, so an
   *     export failure is a {@code problem+json} error rather than a truncated 200
   */
  public ReportController(
      MillContextService millContextService,
      ReportService reportService,
      PrintService printService,
      ReportYearGuard reportYearGuard,
      PdfSpooler pdfSpooler) {
    this.millContextService = millContextService;
    this.reportService = reportService;
    this.printService = printService;
    this.reportYearGuard = reportYearGuard;
    this.pdfSpooler = pdfSpooler;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Resource> getSchedule9Pdf(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    // Fill synchronously (may throw the empty-schedule 404) BEFORE the response is built. The
    // export is synchronous too, inside pdfResponse — only the finished file's bytes are streamed —
    // so a failure at EITHER stage still produces a problem+json error, never a half-written PDF.
    RenderedReport report = reportService.renderSchedule9(context.millId(), context.year());
    String filename = "schedule9_" + context.millId() + "_" + context.year() + ".pdf";
    return pdfResponse(filename, context.millId(), context.year(), report);
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Resource> printSchedules(
      String millId, String year, PrintRequest request, Authentication authentication) {
    // Guard order: mill/year context first (400/404/409), THEN the selection ladder before any
    // fill.
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    validateSelection(request);
    RenderedReport report = printService.render(context, request);
    return pdfResponse("schedules_print.pdf", context.millId(), context.year(), report);
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'GENERATE_MILL_REPORTS')")
  public ResponseEntity<Resource> getMillInformationPdf(
      String year, Authentication authentication) {
    // No MillContextService call here, deliberately: this report has no mill and no working context
    // (BR-08). The year is the only input, and it is the only thing to validate.
    int reportYear = reportYearGuard.requireOpenYear(year);
    RenderedReport report = reportService.renderMillInformation(reportYear);
    return pdfResponse("mills_print.pdf", null, reportYear, report);
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'GENERATE_MILL_REPORTS')")
  public ResponseEntity<Resource> getMillDrillDownPdf(
      long millId, String year, Authentication authentication) {
    // No MillContextService call here either, and for one MORE reason than the all-mills endpoint
    // above. Beyond having no Home working context, this endpoint must not reject a CLOSED mill:
    // closed mills appear in the Mill Status Report table this drill-down is launched from, and
    // reprinting a year in which such a mill was active is exactly what an administrator does.
    // BOTH validateMillYearActive overloads would refuse it (they apply submitter mill scope and
    // the active-mill rule), so neither is called. The year guard is the only guard, and the
    // mill's existence in the year is answered by the read itself as a 404.
    int reportYear = reportYearGuard.requireOpenYear(year);
    ReportService.MillDrillDown drillDown = reportService.renderMillInformation(millId, reportYear);
    return pdfResponse(
        drillDownFilename(drillDown.millNumber(), millId), millId, reportYear, drillDown.report());
  }

  /**
   * The drill-down's parity filename, {@code mill_<millNumber>_print.pdf} — legacy's {@code "mill_"
   * + millReportStatusType.getIlcrMillNumber() + "_print.pdf"} ({@code PrintSchedulesMB.java:332}).
   *
   * <p>Note MILL NUMBER, not the mill id the endpoint is keyed by: they are different values
   * (fixture mill 730 carries mill number 7300), and using the id would break the parity name on
   * every mill.
   *
   * <p>The fall back to the mill id covers a mill whose {@code MILL_NUMBER} is null — nullable in
   * {@code THE.MILL} — where interpolating it raw would offer the administrator a file called
   * {@code mill_null_print.pdf}. The frontend applies the SAME fallback to the same row rather than
   * parsing this header (19.1's report page never reads it either), so the two derivations have to
   * agree: see {@code millReportStatus/index.tsx}.
   */
  private static String drillDownFilename(String millNumber, long millId) {
    // Stripped, because the frontend's millNumberOrNull trims and this must produce the SAME name
    // — the two derivations agreeing is the entire reason the frontend is allowed not to parse this
    // header. MILL_NUMBER is a nullable text column with no non-blank constraint, so a padded value
    // is a reachable delivery state; isBlank already agrees with the frontend on whitespace-only,
    // and this makes them agree on padding too.
    String name =
        millNumber == null || millNumber.isBlank() ? String.valueOf(millId) : millNumber.strip();
    return "mill_" + name + "_print.pdf";
  }

  /**
   * Send a filled report as an {@code application/pdf} attachment, exporting it BEFORE the response
   * is built.
   *
   * <p>This is the ordering that makes the endpoint's failure contract true. The fill already
   * happened on the synchronous path; {@link PdfSpooler#spool} now runs the EXPORT there too,
   * against a temp file. So the last step that can fail for a report reason has finished — and has
   * either produced a whole PDF or thrown — before a status code is chosen. A throw travels to the
   * global handler as a 500 {@code undefinedError} with no bytes written, which is exactly the "no
   * file, inline retryable error" the print criteria ask for (MRPT-002 S07 / MRPT-004 S05).
   *
   * <p>Previously the export ran inside a {@code StreamingResponseBody}, on the far side of the
   * commit. A {@code JRException} or an async timeout there could not change the already-sent 200 +
   * {@code application/pdf} headers, so the browser saved a truncated file and the only defence was
   * the client inspecting the bytes — which cannot be made sound, because a cut PDF can still carry
   * a plausible header and {@code %%EOF} trailer.
   *
   * <p>The spooled file also gives the response a real {@code Content-Length}. That closes the
   * remaining window: the body is length-delimited instead of chunked, so a transfer that stops
   * part-way is a short read the browser fails outright rather than a file it saves. Between the
   * two, EVERY failed export is distinguishable from a successful one — an error response before
   * the commit, a failed request after it — and neither leaves a file on disk.
   *
   * <p>The body is a {@link Resource}, written synchronously, NOT a {@code StreamingResponseBody}.
   * Async streaming was there to keep the export off the heap while it ran, and it has nothing left
   * to do now that the export finishes first — while combining it with a declared {@code
   * Content-Length} actively raced (see {@link ExportedPdf#asResource()}). The heap is unaffected
   * either way: the converter copies from disk in a small buffer, and the spool deletes itself when
   * the response stream closes.
   */
  private ResponseEntity<Resource> pdfResponse(
      String filename, Long millId, int year, RenderedReport report) {
    // Before the ResponseEntity exists, deliberately: a throw here is still a normal 500.
    ExportedPdf pdf = pdfSpooler.spool(report);
    log.debug(
        "Sending {} ({} bytes) for mill {} year {}",
        filename,
        pdf.size(),
        millId == null ? "n/a (not mill-scoped)" : millId,
        year);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(pdf.size())
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(pdf.asResource());
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
