package ca.bc.gov.nrs.ilcr.assignment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the existing {@code THE.ILCR_MILL_USER_XREF} table (AD-3): repository
 * interface plus {@code @Table} record entity {@link MillUserXrefEntity} plus explicit
 * named-parameter SQL. SQL only — the service derives status, joins {@code THE.MILL} for the mill
 * number and name, and maps to DTOs, so entities never cross the service boundary.
 *
 * <p>Every read selects the full column list rather than {@code *} so a snapshot that drifts from
 * delivery fails here rather than surfacing as a null field far downstream. The two toggling
 * updates are guarded by {@code REVISION_COUNT} and report their row count, so a lost update is
 * detectable rather than silent; nothing here ever deletes a row.
 */
@org.springframework.stereotype.Repository
public interface MillUserXrefRepository extends Repository<MillUserXrefEntity, Long> {

  /**
   * Every assignment row for a mill, both active and ended.
   *
   * <p>Legacy applied no ordering to this read, so a deterministic one is imposed here — active
   * rows first, then most-recently-dated — because an unordered list makes the resulting screen and
   * its tests non-reproducible.
   *
   * @param millId the {@code ILCR_MILL_ID}
   * @return the mill's assignment rows
   */
  @Query(
      """
      SELECT ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
        FROM THE.ILCR_MILL_USER_XREF
       WHERE ILCR_MILL_ID = :millId
       ORDER BY CASE WHEN INACTIVE_DATE IS NULL THEN 0 ELSE 1 END,
                COALESCE(ACTIVE_DATE, INACTIVE_DATE) DESC NULLS LAST,
                USER_GUID
      """)
  List<MillUserXrefEntity> findByMill(@Param("millId") long millId);

  /**
   * Every assignment row for a submitter, both active and ended.
   *
   * <p>Ordered by mill id ascending to match the legacy user-centric read, which sorted on the mill
   * key.
   *
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @return the submitter's assignment rows
   */
  @Query(
      """
      SELECT ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
        FROM THE.ILCR_MILL_USER_XREF
       WHERE USER_GUID = :userGuid
       ORDER BY ILCR_MILL_ID
      """)
  List<MillUserXrefEntity> findByUser(@Param("userGuid") String userGuid);

  /**
   * The single assignment row for one user↔mill pair, or empty when the pair was never assigned.
   *
   * <p>Because the composite key permits only one row per pair, this identifies an assignment
   * outright — which is what lets the write path distinguish a first assignment from a reactivation
   * of an ended row, and what makes toggling in place correct.
   *
   * @param millId the {@code ILCR_MILL_ID}
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @return the assignment row, or empty when the pair has no row
   */
  @Query(
      """
      SELECT ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
        FROM THE.ILCR_MILL_USER_XREF
       WHERE ILCR_MILL_ID = :millId AND USER_GUID = :userGuid
      """)
  Optional<MillUserXrefEntity> findAssignment(
      @Param("millId") long millId, @Param("userGuid") String userGuid);

  /**
   * Whether the user holds at least one ACTIVE assignment, evaluated live against the xref.
   *
   * <p>This is the guard the account-deactivate path depends on, and it deliberately queries rather
   * than trusting anything already loaded: deactivation must be refused while any assignment is
   * still active, and a stale in-memory view would let it through. It lives here rather than on the
   * user repository so the {@code INACTIVE_DATE IS NULL ⇒ active} convention has a single owner —
   * this table's repository and {@link MillUserXrefEntity#isActive()}.
   *
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @return true when at least one assignment row has a null {@code INACTIVE_DATE}
   */
  @Query(
      """
      SELECT CASE WHEN EXISTS (
               SELECT 1 FROM THE.ILCR_MILL_USER_XREF
                WHERE USER_GUID = :userGuid AND INACTIVE_DATE IS NULL)
             THEN 1 ELSE 0 END
        FROM DUAL
      """)
  boolean hasActiveAssignment(@Param("userGuid") String userGuid);

  /**
   * Create a new ACTIVE assignment for a pair that has no row yet.
   *
   * <p>Created active, matching the legacy Users-page path: an administrator assigning a submitter
   * intends them to be able to report. The composite key means a second call for the same pair
   * cannot insert a duplicate — it violates the key instead, which the service translates into the
   * legacy already-associated warning rather than letting it surface as a server error.
   *
   * @param millId the {@code ILCR_MILL_ID}
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @param user the acting administrator's {@code custom:idp_username}, for both audit pairs
   * @return the number of rows inserted, always 1
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ILCR_MILL_USER_XREF
          (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (:millId, :userGuid, SYSDATE, NULL, 0, :user, SYSDATE, :user, SYSDATE)
      """)
  int insertActiveAssignment(
      @Param("millId") long millId, @Param("userGuid") String userGuid, @Param("user") String user);

  /**
   * Bring an ended assignment back, in place: set the active date and clear the inactive one.
   *
   * <p>Because one pair admits only one row, this reuses the existing row rather than appending, so
   * the previous assignment period is overwritten and no reactivation history survives. That
   * matches the legacy activate action exactly.
   *
   * @param millId the {@code ILCR_MILL_ID}
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @param revisionCount the revision the caller last read, for the optimistic-lock check
   * @param user the acting administrator's {@code custom:idp_username}
   * @return 1 when the row was updated, 0 when another write got there first
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_MILL_USER_XREF
         SET ACTIVE_DATE = SYSDATE,
             INACTIVE_DATE = NULL,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND USER_GUID = :userGuid
         AND REVISION_COUNT = :revisionCount
      """)
  int reactivateAssignment(
      @Param("millId") long millId,
      @Param("userGuid") String userGuid,
      @Param("revisionCount") int revisionCount,
      @Param("user") String user);

  /**
   * End an assignment in place: set the inactive date and clear the active one.
   *
   * <p>Never a row delete. Clearing the active date is what the legacy deactivate did, and it is
   * load-bearing — the wire contract promises an ended assignment has no active date, and nothing
   * in the database enforces that.
   *
   * @param millId the {@code ILCR_MILL_ID}
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @param revisionCount the revision the caller last read, for the optimistic-lock check
   * @param user the acting administrator's {@code custom:idp_username}
   * @return 1 when the row was updated, 0 when another write got there first
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_MILL_USER_XREF
         SET INACTIVE_DATE = SYSDATE,
             ACTIVE_DATE = NULL,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND USER_GUID = :userGuid
         AND REVISION_COUNT = :revisionCount
      """)
  int endAssignment(
      @Param("millId") long millId,
      @Param("userGuid") String userGuid,
      @Param("revisionCount") int revisionCount,
      @Param("user") String user);
}
