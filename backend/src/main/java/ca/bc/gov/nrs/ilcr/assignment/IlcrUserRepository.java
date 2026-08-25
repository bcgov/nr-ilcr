package ca.bc.gov.nrs.ilcr.assignment;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the existing {@code THE.ILCR_USER} table (AD-3): repository interface
 * plus {@code @Table} record entity {@link IlcrUserEntity} plus explicit named-parameter SQL. SQL
 * only — decisions belong in the service, and the service maps entities to DTOs so entities never
 * cross the service boundary.
 *
 * <p>Every write supplies {@code REVISION_COUNT} and both audit pairs explicitly. All five columns
 * are NOT NULL with no DEFAULT in delivery and no trigger populates them, so an insert that skips
 * one fails here exactly as it would in production.
 */
@org.springframework.stereotype.Repository
public interface IlcrUserRepository extends Repository<IlcrUserEntity, String> {

  /**
   * The ILCR account row for a directory GUID, or empty when the user has never held one.
   *
   * <p>An empty result is a normal, expected state rather than an error: it is exactly the
   * "directory user with no ILCR account yet" case, which the write path provisions on first
   * activate or first mill assignment.
   *
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @return the account row, or empty when no account exists
   */
  @Query(
      """
      SELECT USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
        FROM THE.ILCR_USER
       WHERE USER_GUID = :userGuid
      """)
  Optional<IlcrUserEntity> findUser(@Param("userGuid") String userGuid);

  /**
   * Create the account row for a directory user who has never held one.
   *
   * <p>The caller supplies {@code activeInd} because the two legacy entry paths disagree on it: the
   * activate path creates the account active, while provisioning it as a side effect of a first
   * mill assignment creates it inactive. That asymmetry is preserved deliberately, so the value is
   * a parameter rather than a constant.
   *
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @param roleName the legacy ILCR role to stamp
   * @param activeInd {@code "Y"} or {@code "N"}
   * @param user the acting administrator's {@code custom:idp_username}, for both audit pairs
   * @return the number of rows inserted, always 1
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ILCR_USER
          (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (:userGuid, :roleName, :activeInd, 0, :user, SYSDATE, :user, SYSDATE)
      """)
  int insertAccount(
      @Param("userGuid") String userGuid,
      @Param("roleName") String roleName,
      @Param("activeInd") String activeInd,
      @Param("user") String user);

  /**
   * Flip an existing account's flag, bumping the revision and re-stamping the update audit pair.
   *
   * <p>The flag is display and administrative state only — it gates neither login nor access — so
   * this write never changes what the user may do. Only the assignment rows do that.
   *
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @param activeInd {@code "Y"} or {@code "N"}
   * @param user the acting administrator's {@code custom:idp_username}
   * @return the number of rows updated; 0 when no account row exists
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_USER
         SET ACTIVE_IND = :activeInd,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE USER_GUID = :userGuid
      """)
  int setAccountActive(
      @Param("userGuid") String userGuid,
      @Param("activeInd") String activeInd,
      @Param("user") String user);
}
