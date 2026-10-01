package ca.bc.gov.nrs.ilcr.support;

import ca.bc.gov.nrs.ilcr.security.EditableStatuses;
import ca.bc.gov.nrs.ilcr.security.ScheduleEditability;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Editability fixtures for service-level tests, resolved through the production {@link
 * ScheduleEditability} rather than hand-written status sets.
 *
 * <p>Going through the real component is deliberate: a test that hardcoded {@code Set.of("D")}
 * would keep passing if the matrix itself regressed. These constants therefore carry whatever the
 * matrix actually grants, and {@code ScheduleEditabilityTest} stays the one place the table is
 * asserted.
 */
public final class CallerRights {

  private static final ScheduleEditability EDITABILITY =
      new ScheduleEditability(new SchedulePermissions());

  /** A licensee: may write only while the track is Draft. */
  public static final EditableStatuses SUBMITTER = forGroups("ILCR_SUBMITTER");

  /** A ministry administrator: may write at Submitted and Verified, never at Draft. */
  public static final EditableStatuses ADMIN = forGroups("ILCR_ADMIN");

  /** A caller permitted nothing — an unrecognised role, or a read-only consumer. */
  public static final EditableStatuses NONE = EditableStatuses.NONE;

  private CallerRights() {}

  /**
   * Resolve editability for a caller holding the given authorities.
   *
   * @param groups the granted authorities to resolve
   * @return the statuses those authorities may edit
   */
  public static EditableStatuses forGroups(String... groups) {
    return EDITABILITY.forCaller(
        new UsernamePasswordAuthenticationToken(
            "test", "test", Arrays.stream(groups).map(SimpleGrantedAuthority::new).toList()));
  }
}
