package ca.bc.gov.nrs.ilcr.reporting.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Print Schedule PDF API contract (Epic 20). The interface owns the request mapping; {@code
 * ReportController} implements it and adds authorization — the established controller/api-interface
 * idiom (mirrors {@code Schedule9Api}).
 *
 * <p>This is the backend's first binary-response endpoint: {@code ResponseEntity<byte[]>} with
 * {@code application/pdf}. Later Epic 20 report stories copy this shape.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like the Schedule read
 * endpoints — so the shared {@code MillContextService} guard can emit the verbatim ERR-003 for
 * missing, blank, AND non-numeric values, which a typed required {@code @RequestParam} cannot
 * produce.
 */
@RequestMapping("/api/v1/reports")
public interface ReportApi {

  /**
   * Download the Schedule 9 (Miscellaneous/Unique Logging Costs) report as a PDF for a mill and
   * reporting year.
   *
   * <p>Guards (identical to the Schedule 9 read, verbatim legacy text): missing/blank/non-numeric
   * params → 400 (ERR-003); mill not active for the year → 409 (ERR-004); no report-status
   * context → 404 (ERR-005); no {@code VIEW_SCHEDULE} → 403. A valid mill/year whose Schedule 9
   * has zero records yields no PDF → 404 {@code Schedule not found.} (legacy single-schedule
   * semantics).
   *
   * @param millId the mill id (optional raw String)
   * @param year the reporting year (optional raw String)
   * @param authentication the caller (authorized for VIEW_SCHEDULE — print is read-only, BR-01)
   * @return 200 with the PDF bytes ({@code application/pdf} + attachment Content-Disposition)
   */
  @GetMapping("/schedule9")
  ResponseEntity<byte[]> getSchedule9Pdf(
      @RequestParam(required = false) String millId,
      @RequestParam(required = false) String year,
      Authentication authentication);
}
