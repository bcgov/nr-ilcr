package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.reporting.api.ReportApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

/**
 * Print Schedule PDF endpoints (Epic 20). Authorizes by naming the action (AD-7) — {@code
 * VIEW_SCHEDULE}, because printing is read-only for every role (BR-01) — delegates ALL mill/year
 * validation to {@link MillContextService} as its first line (AD-4), and streams the PDF from
 * {@link ReportService} (AD-16). It never touches repositories directly (AD-1).
 *
 * <p>Mirrors the Schedule 9 read controller's guard/auth posture; the only difference is the
 * binary response ({@code application/pdf} + an attachment Content-Disposition). The empty-schedule
 * 404 is
 * raised inside the service ({@code ScheduleNotFoundException}) and rendered by the global handler
 * as the verbatim {@code Schedule not found.} (ERR-005) — no PDF is produced.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class ReportController implements ReportApi {

  private final MillContextService millContextService;
  private final ReportService reportService;

  public ReportController(MillContextService millContextService, ReportService reportService) {
    this.millContextService = millContextService;
    this.reportService = reportService;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<byte[]> getSchedule9Pdf(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    byte[] pdf = reportService.renderSchedule9Pdf(context.millId(), context.year());
    String filename = "schedule9_" + context.millId() + "_" + context.year() + ".pdf";
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(pdf);
  }
}
