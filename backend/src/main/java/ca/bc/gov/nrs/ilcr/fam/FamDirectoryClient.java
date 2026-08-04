package ca.bc.gov.nrs.ilcr.fam;

import ca.bc.gov.nrs.ilcr.fam.dto.FamSubmitter;
import java.util.List;

/**
 * Seam over the FAM user directory (Story 2.3). "Submitter-eligible" = membership in the
 * {@code ILCR_SUBMITTER} FAM/Cognito group (Q1-resolved from the nr-ilcr-old prototype's
 * {@code getUsersByRole} → Cognito {@code ListUsersInGroup}).
 *
 * <p>Two implementations are anticipated: a local {@link StubFamDirectoryClient} (seeded, offline) and
 * a real Cognito {@code ListUsersInGroup} / FAM external-API client wired when AWS credentials land. An
 * implementation SHOULD throw {@link FamDirectoryUnavailableException} on an upstream failure so the
 * admin screen degrades gracefully (the local assignments view still works).
 */
public interface FamDirectoryClient {

  /**
   * List submitter-eligible FAM users, optionally filtered by a free-text query (matched against the
   * display name / username). A blank query returns all.
   *
   * @param query the optional search text (nullable/blank = all)
   * @return the matching submitter candidates
   */
  List<FamSubmitter> searchSubmitters(String query);
}
