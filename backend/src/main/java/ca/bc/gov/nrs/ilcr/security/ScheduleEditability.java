package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * The role&times;status editability rule — the single server-side authority on whether a caller may
 * write to a schedule (AD-9).
 *
 * <p>Ported from legacy {@code UserSessionMB.disableUserInput():458-481}, which enabled input only
 * for (Draft &and; Licensee), (Submitted &and; non-Licensee) or (Verified &and; Administrator) and
 * was read-only otherwise. Under the two-role model the merged administrator carries the union of
 * the legacy Administrator and Auditor permission sets, so the legacy Submitted row — written as a
 * negative test against Licensee — becomes the ADMIN row here:
 *
 * <table>
 *   <caption>Editable track statuses by role</caption>
 *   <tr><th></th><th>Draft (D)</th><th>Submitted (S)</th><th>Verified (V)</th><th>Opened (O)</th></tr>
 *   <tr><td>SUBMITTER</td><td>edit</td><td>read-only</td><td>read-only</td><td>read-only</td></tr>
 *   <tr><td>ADMIN</td><td>read-only</td><td>edit</td><td>edit</td><td>read-only</td></tr>
 * </table>
 *
 * <p>Administrator is deliberately not a superset of submitter: the statuses form a hand-off chain,
 * so an administrator is read-only while the mill still owns its Draft.
 *
 * <p>This is distinct from {@link SchedulePermissions}, which answers only whether a role holds an
 * action at all and carries no status dimension. Both are consulted: a caller must hold {@link
 * Action#EDIT_SCHEDULE} <em>and</em> satisfy the matrix. Losing the action yields 403 at the
 * endpoint; holding it and failing the matrix is a 409 raised by the schedule service (AD-5).
 *
 * <p>The rule is evaluated per track. The Schedules 1&ndash;10 track and the Schedule 11
 * silviculture track carry the same matrix over different status columns — legacy proved this by
 * duplicating the method verbatim and changing only the getter it read — so callers supply their
 * own track's status code and no track parameter is needed here.
 *
 * <p>Two legacy behaviours are deliberately not reproduced. Legacy tested Submitted as {@code
 * !role.equals(ILCR_LICENSEE)}, so a role it could not recognise <em>gained</em> edit rights there;
 * this resolves unknown roles to no permission instead, per the fail-closed posture (AD-7). Legacy
 * also read only the first of a user's roles and ignored the rest; permitted statuses are unioned
 * across every role held.
 */
@Component
public class ScheduleEditability {

  /** Draft — the mill is entering its own data. */
  static final String DRAFT = "D";

  /** Submitted — the mill has handed the report to the ministry. */
  static final String SUBMITTED = "S";

  /** Verified — the ministry has signed the report off. */
  static final String VERIFIED = "V";

  private static final Map<Role, Set<String>> EDITABLE_STATUSES = new EnumMap<>(Role.class);

  static {
    EDITABLE_STATUSES.put(Role.SUBMITTER, Set.of(DRAFT));
    EDITABLE_STATUSES.put(Role.ADMIN, Set.of(SUBMITTED, VERIFIED));
  }

  private final SchedulePermissions permissions;

  public ScheduleEditability(SchedulePermissions permissions) {
    this.permissions = permissions;
  }

  /**
   * Resolve the track statuses this caller may edit, unioned across the roles it holds.
   *
   * <p>Resolved once per request at the endpoint and threaded into the schedule service, which
   * knows the track's status only after reading it. The service then performs a membership test
   * rather than re-deriving the rule.
   *
   * @param authentication the current authentication; may be null
   * @return the permitted status codes, never null and never widened beyond the matrix
   */
  public EditableStatuses forCaller(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return EditableStatuses.NONE;
    }
    Set<String> permitted = new HashSet<>();
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      Role role = Role.fromValue(authority.getAuthority());
      if (role != null && permissions.grants(role, Action.EDIT_SCHEDULE)) {
        permitted.addAll(EDITABLE_STATUSES.getOrDefault(role, Set.of()));
      }
    }
    return permitted.isEmpty() ? EditableStatuses.NONE : new EditableStatuses(permitted);
  }
}
