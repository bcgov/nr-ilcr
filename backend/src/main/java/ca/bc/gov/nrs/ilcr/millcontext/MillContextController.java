package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.millcontext.api.MillContextApi;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.millcontext.dto.WorkingContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home-page option-list endpoints (Story 1.1). Delegates to {@link MillContextService} and never
 * touches the repository directly (AD-1 layering). These are pre-selection reads with NO
 * {@code @PreAuthorize} — there are no roles/authorization yet (see the story's Authorization note);
 * the security filter chain permits the two paths even when {@code ilcr.security.enabled=true}.
 */
@RestController
@RequiredArgsConstructor
public class MillContextController implements MillContextApi {

  private final MillContextService millContextService;

  @Override
  public ResponseEntity<List<MillSummary>> listMills() {
    return ResponseEntity.ok(millContextService.listMills());
  }

  @Override
  public ResponseEntity<List<ReportingYear>> listReportingYears() {
    return ResponseEntity.ok(millContextService.listReportingYears());
  }

  @Override
  public ResponseEntity<WorkingContext> getMillContext(String millId, String year) {
    return ResponseEntity.ok(millContextService.resolveWorkingContext(millId, year));
  }
}
