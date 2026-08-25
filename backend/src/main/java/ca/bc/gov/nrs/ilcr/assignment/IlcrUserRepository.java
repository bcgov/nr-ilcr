package ca.bc.gov.nrs.ilcr.assignment;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the existing {@code THE.ILCR_USER} table (AD-3): repository interface
 * plus {@code @Table} record entity {@link IlcrUserEntity} plus explicit named-parameter SQL. SQL
 * only — decisions belong in the service, and the service maps entities to DTOs so entities never
 * cross the service boundary.
 *
 * <p>Read side only: neither the account activate/deactivate update nor the
 * provision-on-first-assign insert is implemented here.
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
}
