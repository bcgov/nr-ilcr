package ca.bc.gov.nrs.ilcr.security;

import java.util.Set;

/**
 * The track-status codes a caller is permitted to edit, resolved once per request from the roles
 * the caller actually holds.
 *
 * <p>Carrying the permitted codes rather than a single boolean is what lets a schedule service
 * answer "may this caller edit a track sitting at this status?" without re-deriving the rule. The
 * role&times;status rule lives only in {@link ScheduleEditability}; a service performs a membership
 * test against the codes resolved for its caller, so the formula has exactly one home (AD-9).
 *
 * <p>An absent or unrecognised status code is never editable. Legacy reached the same outcome by
 * mapping any unknown code onto an {@code INVALID} enum constant that fell through to the read-only
 * branch of {@code UserSessionMB.disableUserInput()}.
 */
public record EditableStatuses(Set<String> codes) {

  /** Nothing is editable: the caller holds no edit action, or holds no recognised role. */
  public static final EditableStatuses NONE = new EditableStatuses(Set.of());

  public EditableStatuses(Set<String> codes) {
    this.codes = Set.copyOf(codes);
  }

  /**
   * Whether this caller may edit a track sitting at the given status.
   *
   * @param trackStatusCode the track's stored status code, or null when the track has no status row
   * @return true only when the code is present and this caller may edit at it
   */
  public boolean allows(String trackStatusCode) {
    return trackStatusCode != null && codes.contains(trackStatusCode);
  }
}
