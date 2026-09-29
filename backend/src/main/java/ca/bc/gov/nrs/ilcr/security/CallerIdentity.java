package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The ONE place the caller's directory identity is read off the security context — the {@code
 * ILCR_MILL_USER_XREF} association key every mill-scoped read keys on (#468 review).
 *
 * <p>Two callers share it so they cannot drift: the Home mill list ({@code
 * MillContextController.listMills}) and the report scope ({@code
 * MillContextService.callerMillScope}). Before #468 each had its own copy of this logic; a
 * claim-mapping regression fixed in one would have survived in the other.
 *
 * <ul>
 *   <li>A FAM {@code Jwt} principal: its {@code custom:idp_user_id} claim.
 *   <li>The dev/UAT {@link MockUserPrincipal} (security off): the stand-in GUID {@code
 *       MockPrincipalFilter} gives it — not the principal NAME, which feeds the VARCHAR2(30) audit
 *       columns (a FAM GUID is 32 chars). Unreachable when deployed: that filter is not registered.
 *   <li>Anything else, or a blank claim: EMPTY. The caller has no identity that can be scoped by.
 * </ul>
 *
 * <p>Returns an {@link Optional} rather than {@code ""} so that "no identity" is a distinct state a
 * caller must decide about, instead of a value that quietly behaves like "a user with no mills".
 * The Home list chooses to fail closed to an empty list; the report scope refuses outright (403),
 * so a broken claim mapping surfaces as audited denials rather than as plausible empty reports.
 */
public final class CallerIdentity {

  private CallerIdentity() {}

  /**
   * The caller's directory GUID, if the current principal carries one.
   *
   * @return the GUID; empty when the principal is neither a {@code Jwt} nor a mock, or the claim is
   *     blank
   */
  public static Optional<String> currentUserGuid() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Object principal = auth != null ? auth.getPrincipal() : null;
    String guid;
    if (principal instanceof Jwt jwt) {
      guid = JwtPrincipalUtil.getIdpUserId(jwt);
    } else if (principal instanceof MockUserPrincipal mock) {
      guid = mock.userGuid();
    } else {
      guid = null;
    }
    return guid == null || guid.isBlank() ? Optional.empty() : Optional.of(guid);
  }
}
