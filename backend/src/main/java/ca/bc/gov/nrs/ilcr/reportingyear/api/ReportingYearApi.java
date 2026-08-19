package ca.bc.gov.nrs.ilcr.reportingyear.api;

import ca.bc.gov.nrs.ilcr.reportingyear.dto.OpenReportingYearRequest;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.OpenReportingYearResponse;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.ReportingYearAdminView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Open Reporting Year API contract (UC-RY-001; controller + api-interface split). The interface owns
 * the request mapping; {@code ReportingYearController} implements it and adds the ADMIN-only
 * {@code OPEN_REPORTING_YEAR} authorization so a non-admin is denied 403 (S13). This is a DELIBERATELY
 * SEPARATE admin path from the unauthenticated Home read {@code GET /api/v1/reporting-years} (Story
 * 1.1); the create is never bolted onto that ungated interface.
 */
@RequestMapping("/api/v1/admin/reporting-years")
public interface ReportingYearApi {

  /**
   * The admin-page state: open years, the recurring next year, and the first-time starting-year options.
   *
   * @param authentication the caller (must hold {@code OPEN_REPORTING_YEAR})
   * @return 200 with the reporting-year admin view
   */
  @GetMapping
  ResponseEntity<ReportingYearAdminView> view(Authentication authentication);

  /**
   * Open the next reporting year (recurring {@code max + 1}) or, first-time, the selected starting year,
   * creating a report-status row for every active mill. Missing/out-of-range selection → 400 (FLD-001);
   * zero active mills on the recurring path → 409 (INF-001); year already open → 409.
   *
   * @param request the optional first-time starting-year selection (null on the recurring path)
   * @param authentication the caller (must hold {@code OPEN_REPORTING_YEAR}; drives the audit user)
   * @return 200 with the created year, count of initialized mills, and the verbatim success message
   */
  @PostMapping
  ResponseEntity<OpenReportingYearResponse> open(
      @RequestBody(required = false) OpenReportingYearRequest request, Authentication authentication);
}
