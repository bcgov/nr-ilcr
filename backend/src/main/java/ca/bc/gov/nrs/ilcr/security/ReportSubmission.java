package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The "may Submit be offered?" rule for either track &mdash; the single server-side authority both
 * the Check Status sweep (its {@code canSubmit} flag) and the submit endpoint consult (AD-9), so
 * the button the page renders and the guard the transition applies can never disagree.
 *
 * <p>Ported from legacy {@code UserSessionMB.canUserSubmitReport(boolean):502-517}: the button was
 * enabled exactly when the track's status sat at Draft AND the session role was {@code
 * ILCR_LICENSEE} &mdash; the same rule for both tracks, the boolean choosing only which status code
 * it read ({@code :510-517} for Schedule 11). Nothing about validity &mdash; a Licensee whose
 * schedules still fail sees an enabled button, clicks it, and is told by the gate that the report
 * cannot be submitted. That is the legacy experience and it is preserved on purpose (Story 15.2 D1
 * rider): the flag means "offered", never "will succeed".
 *
 * <p>Distinct from {@link ScheduleEditability}, which answers whether a caller may WRITE to a
 * schedule at a status; submitting is a transition, not an edit, and carries its own action ({@link
 * Action#SUBMIT_REPORT}). Unknown roles resolve to no permission (fail closed, AD-7).
 */
@Component
public class ReportSubmission {

  /** Draft &mdash; the only status from which either track can be submitted. */
  static final String DRAFT = "D";

  private final SchedulePermissions permissions;
  private final MillContextRepository millContextRepository;

  public ReportSubmission(
      SchedulePermissions permissions, MillContextRepository millContextRepository) {
    this.permissions = permissions;
    this.millContextRepository = millContextRepository;
  }

  /**
   * Whether Submit is offered to this caller for a track at the given status.
   *
   * @param authentication the current authentication; may be null
   * @param trackStatusCode the track's stored status code (either track's), or null when the track
   *     has no status row
   * @return true only when the caller holds {@link Action#SUBMIT_REPORT} and the track is at Draft
   */
  public boolean canSubmit(Authentication authentication, String trackStatusCode) {
    return DRAFT.equals(trackStatusCode) && holdsSubmitAction(authentication);
  }

  /**
   * Whether Submit is offered for this mill after applying the action's contextual scope.
   *
   * <p>Epic 16 deliberately unions capabilities across every role held. A caller carrying both
   * ADMIN and SUBMITTER therefore keeps {@link Action#SUBMIT_REPORT}, but ADMIN's all-mills
   * browsing scope does not widen that action: the caller must still have an active submitter
   * assignment to the mill. Single-role submitters have already passed the shared mill-context
   * guard, so only the dual-role case needs the additional read.
   *
   * @param authentication the current authentication
   * @param trackStatusCode the track's stored status code (either track's)
   * @param millId the mill whose report would be submitted
   * @return true only when the role/status rule and the Submit-specific mill scope both pass
   */
  public boolean canSubmit(Authentication authentication, String trackStatusCode, long millId) {
    return canSubmit(authentication, trackStatusCode)
        && hasSubmitterMillAccess(authentication, millId);
  }

  /**
   * Enforce Submit-specific mill scope at request entry.
   *
   * <p>This intentionally does not hold an assignment lock for the transaction. Authorization is a
   * request-entry snapshot: a concurrent revocation affects the next request, matching the legacy
   * interaction and the Story 15.3 review decision.
   *
   * @throws AccessDeniedException when a dual-role caller is not actively assigned to the mill
   */
  public void validateSubmitterMillAccess(Authentication authentication, long millId) {
    if (!hasSubmitterMillAccess(authentication, millId)) {
      throw new AccessDeniedException("Mill is not associated to the submitting caller.");
    }
  }

  private boolean holdsSubmitAction(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      Role role = Role.fromValue(authority.getAuthority());
      if (permissions.grants(role, Action.SUBMIT_REPORT)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasSubmitterMillAccess(Authentication authentication, long millId) {
    if (!holdsRole(authentication, Role.ADMIN)) {
      return true;
    }
    String userGuid = directoryGuid(authentication);
    return userGuid != null
        && !userGuid.isBlank()
        && millContextRepository.userHasActiveAssignment(millId, userGuid);
  }

  private static boolean holdsRole(Authentication authentication, Role expected) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(Role::fromValue)
        .anyMatch(expected::equals);
  }

  private static String directoryGuid(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof Jwt jwt) {
      return JwtPrincipalUtil.getIdpUserId(jwt);
    }
    if (principal instanceof MockUserPrincipal mock) {
      return mock.userGuid();
    }
    return null;
  }
}
