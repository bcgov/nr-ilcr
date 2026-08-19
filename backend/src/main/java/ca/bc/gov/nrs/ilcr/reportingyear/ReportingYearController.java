package ca.bc.gov.nrs.ilcr.reportingyear;

import ca.bc.gov.nrs.ilcr.reportingyear.api.ReportingYearApi;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.OpenReportingYearRequest;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.OpenReportingYearResponse;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.OpenReportingYearResult;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.ReportingYearAdminView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open Reporting Year endpoints (UC-RY-001). Every method is gated on the ADMIN-only
 * {@code OPEN_REPORTING_YEAR} action (AD-7, S13) so a non-admin is denied 403 server-side. Delegates
 * to {@link ReportingYearService}; resolves the verbatim SUC-001 success text here (AD-8) so message
 * text never lives in Java.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class ReportingYearController implements ReportingYearApi {

  private static final String MSG_CREATED = "successNewReportingYearMsg";

  private final ReportingYearService service;
  private final MessageSource messageSource;

  public ReportingYearController(ReportingYearService service, MessageSource messageSource) {
    this.service = service;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'OPEN_REPORTING_YEAR')")
  public ResponseEntity<ReportingYearAdminView> view(Authentication authentication) {
    return ResponseEntity.ok(service.view());
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'OPEN_REPORTING_YEAR')")
  public ResponseEntity<OpenReportingYearResponse> open(
      OpenReportingYearRequest request, Authentication authentication) {
    Integer requestedYear = request == null ? null : request.year();
    OpenReportingYearResult result = service.open(requestedYear, authentication.getName());
    // Pass the year as a String so MessageFormat renders "2025", not the number-grouped "2,025".
    String message = messageSource.getMessage(
        MSG_CREATED, new Object[] {String.valueOf(result.year())}, MSG_CREATED,
        LocaleContextHolder.getLocale());
    return ResponseEntity.ok(new OpenReportingYearResponse(
        result.year(), result.millsInitialized(), MSG_CREATED, message));
  }
}
