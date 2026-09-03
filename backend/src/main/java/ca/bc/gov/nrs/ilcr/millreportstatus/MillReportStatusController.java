package ca.bc.gov.nrs.ilcr.millreportstatus;

import ca.bc.gov.nrs.ilcr.millreportstatus.api.MillReportStatusApi;
import ca.bc.gov.nrs.ilcr.millreportstatus.dto.MillReportStatusRow;
import ca.bc.gov.nrs.ilcr.reporting.ReportYearGuard;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mill Status Report endpoint (Story 19.2 / UC-MRPT-004). Gated on the ADMIN-only {@code
 * GENERATE_MILL_REPORTS} action (AD-7) so a SUBMITTER is denied 403 server-side — the hidden
 * Generate Reports menu is UX only, not the boundary. Delegates all work to {@link
 * MillReportStatusService}; never touches a repository directly (AD-1 layering).
 *
 * <p><b>No mill/year working-context guard runs here, deliberately.</b> Neither {@code
 * MillContextService.validateMillYearActive} overload ({@code MillContextService.java:348}, {@code
 * :388}) is called, and neither may be added. Both apply submitter mill scope and REJECT a mill
 * that is closed for the year — and this table's whole purpose is to list every mill including the
 * closed ones (mill 733 in the test fixtures is ACT in 2021 and CLS today precisely to prove it).
 * Legacy carried no working context on this page either: its {@code areMillYearSelected()} render
 * guard is commented out ({@code millReportStatus.xhtml:10-24}). Do not "align" this endpoint with
 * the mill-scoped schedule endpoints; the difference is behaviour, not oversight.
 *
 * <p>The year is the only input, and {@link ReportYearGuard} — the same guard the Mill Information
 * PDF uses — is the only validation, so both endpoints reject a bad year with byte-identical text.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class MillReportStatusController implements MillReportStatusApi {

  private final MillReportStatusService service;
  private final ReportYearGuard reportYearGuard;

  /**
   * Constructs a new MillReportStatusController.
   *
   * @param service the mill report status read
   * @param reportYearGuard the shared report-year guard
   */
  public MillReportStatusController(
      MillReportStatusService service, ReportYearGuard reportYearGuard) {
    this.service = service;
    this.reportYearGuard = reportYearGuard;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'GENERATE_MILL_REPORTS')")
  public ResponseEntity<List<MillReportStatusRow>> getMillReportStatus(
      String year, Authentication authentication) {
    int reportYear = reportYearGuard.requireOpenYear(year);
    // An empty list is a 200, not a 404: an empty sortable table is a correct render of a year no
    // mill reported in. Contrast the Mill Information PDF, where no mills means no document.
    return ResponseEntity.ok(service.findRows(reportYear));
  }
}
