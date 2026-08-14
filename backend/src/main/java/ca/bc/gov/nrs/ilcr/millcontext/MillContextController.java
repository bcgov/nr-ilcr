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
 * {@code @PreAuthorize} — no role gate yet (per-user mill filtering is deferred to the FAM auth
 * story). Since O4 (fam-auth-1-1) they are NOT public: with {@code ilcr.security.enabled=true} they
 * require an authenticated caller like the rest of {@code /api/**} (Home renders after sign-in).
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
