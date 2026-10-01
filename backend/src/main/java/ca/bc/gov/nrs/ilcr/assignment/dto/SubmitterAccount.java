package ca.bc.gov.nrs.ilcr.assignment.dto;

/**
 * Wire shape for a licensee's ILCR account record, as the administration screen shows it (AD-12
 * pin, Story 2.1).
 *
 * <p>{@code activeInd} drives the on-screen Activate/Deactivate control and nothing else. It is not
 * an authorization input: access is decided by whether the user holds an active mill assignment, so
 * an account sitting at {@code "N"} still works normally. Accounts provisioned as a side effect of
 * a first mill assignment are created at {@code "N"} for that reason, and real data contains such
 * users. Consumers must not treat this flag as a lockout.
 *
 * @param userGuid directory GUID ({@code custom:idp_user_id})
 * @param activeInd {@code "Y"} or {@code "N"}; display and administrative state only
 * @param roleName the legacy ILCR role name held against the account
 * @param revisionCount optimistic-lock token
 */
public record SubmitterAccount(
    String userGuid, String activeInd, String roleName, int revisionCount) {

  /** The {@code activeInd} value for an account flagged active. */
  public static final String ACTIVE = "Y";

  /** The {@code activeInd} value for an account flagged inactive. */
  public static final String INACTIVE = "N";
}
