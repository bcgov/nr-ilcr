package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * The "may Submit be offered?" rule for the Schedules 1&ndash;10 track &mdash; the single
 * server-side authority both the Check Status sweep (its {@code canSubmit} flag) and the submit
 * endpoint consult (AD-9), so the button the page renders and the guard the transition applies can
 * never disagree.
 *
 * <p>Ported from legacy {@code UserSessionMB.canUserSubmitReport(false):502-509}: the button was
 * enabled exactly when the 1&ndash;10 track sat at Draft AND the session role was {@code
 * ILCR_LICENSEE}. Nothing about validity &mdash; a Licensee whose schedules still fail sees an
 * enabled button, clicks it, and is told by the gate that the report cannot be submitted. That is
 * the legacy experience and it is preserved on purpose (Story 15.2 D1 rider): the flag means
 * "offered", never "will succeed".
 *
 * <p>Distinct from {@link ScheduleEditability}, which answers whether a caller may WRITE to a
 * schedule at a status; submitting is a transition, not an edit, and carries its own action ({@link
 * Action#SUBMIT_REPORT}). Unknown roles resolve to no permission (fail closed, AD-7).
 */
@Component
public class ReportSubmission {

  /** Draft &mdash; the only status from which the 1&ndash;10 track can be submitted. */
  static final String DRAFT = "D";

  private final SchedulePermissions permissions;

  public ReportSubmission(SchedulePermissions permissions) {
    this.permissions = permissions;
  }

  /**
   * Whether Submit is offered to this caller for a track at the given status.
   *
   * @param authentication the current authentication; may be null
   * @param trackStatusCode the Schedules 1&ndash;10 track's stored status code, or null when the
   *     track has no status row
   * @return true only when the caller holds {@link Action#SUBMIT_REPORT} and the track is at Draft
   */
  public boolean canSubmit(Authentication authentication, String trackStatusCode) {
    return DRAFT.equals(trackStatusCode) && holdsSubmitAction(authentication);
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
}
