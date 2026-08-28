package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.millcontext.api.MillContextApi;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.millcontext.dto.WorkingContext;
import ca.bc.gov.nrs.ilcr.security.JwtRoleChecker;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home-page option-list endpoints (Story 1.1). Delegates to {@link MillContextService} and never
 * touches the repository directly (AD-1 layering). Since O4 (fam-auth-1-1) these are NOT public:
 * with {@code ilcr.security.enabled=true} they require an authenticated caller like the rest of
 * {@code /api/**} (Home renders after sign-in).
 *
 * <p>Story 5.5: {@code listMills} is no longer identical for every caller — it resolves the
 * principal's role and directory GUID and scopes the list (admin = all mills incl. closed;
 * submitter = actively-associated mills only). Identity is read from the SAME source in both
 * deployment modes (the SecurityContext authentication), so the mock principal (security off, AD-7)
 * exercises the same branch. The sibling reads ({@code reporting-years}, {@code mill-context}) are
 * unchanged and stay authenticated-by-the-global-filter.
 */
@RestController
@RequiredArgsConstructor
public class MillContextController implements MillContextApi {

  private final MillContextService millContextService;
  private final JwtRoleChecker roleChecker;

  @Override
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<MillSummary>> listMills() {
    boolean isAdmin = roleChecker.hasConcreteRole(Role.ADMIN.name());
    return ResponseEntity.ok(millContextService.listMills(isAdmin, currentUserGuid()));
  }

  /**
   * The caller's raw {@code custom:idp_user_id} directory GUID (the {@code ILCR_MILL_USER_XREF}
   * association key), or {@code ""} when unavailable — i.e. the dev mock principal, which is a
   * {@code UsernamePasswordAuthenticationToken} carrying no JWT claims. Blank ⇒ a submitter is
   * scoped to an empty list (fail-closed) by {@link MillContextService#listMills(boolean, String)}.
   */
  private static String currentUserGuid() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      return JwtPrincipalUtil.getIdpUserId(jwt);
    }
    return "";
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
