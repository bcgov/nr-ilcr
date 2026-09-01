package ca.bc.gov.nrs.ilcr.millreportstatus.api;

import ca.bc.gov.nrs.ilcr.millreportstatus.dto.MillReportStatusRow;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Mill Status Report API contract (Story 19.2 / UC-MRPT-004; controller + api-interface split). The
 * interface owns the request mapping; {@code MillReportStatusController} implements it and adds the
 * ADMIN-only {@code GENERATE_MILL_REPORTS} authorization.
 *
 * <p>It lives in its own package rather than as a fourth method on {@code ReportApi} because that
 * contract is explicitly PDF-streaming — every method there returns {@code StreamingResponseBody} —
 * and a {@code ResponseEntity<List<…>>} would break its single-shape story. {@code CodeTableApi} is
 * the comparator this follows: an ADMIN-only, read-only JSON list.
 */
@RequestMapping("/api/v1/reports")
public interface MillReportStatusApi {

  /**
   * Read where every mill stands in the reporting cycle for one reporting year — the Mill Status
   * Report table: mill number, mill name, region, per-year active flag, and the Opened/Draft/
   * Submitted/Verified milestones of BOTH independent schedule tracks.
   *
   * <p>The milestone strings are served RAW, carrying the legacy status prefix the page's O/D/S/V
   * legend decodes. A null milestone is served as {@code null} and must render as an empty line.
   *
   * <p>Unscoped: no {@code millId} parameter exists and none is ever sent. The mill set is every
   * mill with a report status for the year, ordered by mill id.
   *
   * <p>Guards, in the order they run: missing/blank/non-numeric {@code year} → <b>400</b> {@code
   * Report Year: Value is required.}; a parseable year that is not an OPEN reporting period →
   * <b>400</b> {@code Report Year is not an open reporting period.}. An open year that no mill has
   * a report status for answers <b>200</b> with {@code []} — an empty sortable table is a correct
   * render, deliberately unlike the sibling PDF endpoint's 404. Without {@code
   * GENERATE_MILL_REPORTS} → 403; anonymous → 401.
   *
   * @param year the reporting year (optional raw String, so the guard owns the rejection text)
   * @param authentication the caller (authorized for GENERATE_MILL_REPORTS — administrators only)
   * @return 200 with one row per mill in mill-id order; {@code []} when the year has no mills
   */
  @GetMapping("/mill-status")
  ResponseEntity<List<MillReportStatusRow>> getMillReportStatus(
      @RequestParam(required = false) String year, Authentication authentication);
}
