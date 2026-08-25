package ca.bc.gov.nrs.ilcr.assignment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the existing {@code THE.ILCR_MILL_USER_XREF} table (AD-3): repository
 * interface plus {@code @Table} record entity {@link MillUserXrefEntity} plus explicit
 * named-parameter SQL. SQL only — the service derives status, joins {@code THE.MILL} for the mill
 * number and name, and maps to DTOs, so entities never cross the service boundary.
 *
 * <p>Read side only: the assign, reactivate and soft-end writes are not implemented here. Every
 * method selects the full column list rather than {@code *} so a snapshot that drifts from delivery
 * fails here rather than surfacing as a null field far downstream.
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
}
