package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.assignment.dto.MillLabel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to {@code THE.ILCR_MILL_USER_PROFILE_XREF} (AD-3: repository interface +
 * {@code @Table} record entity {@link MillUserProfileXrefEntity} + explicit {@code @Query}
 * named-parameter SQL). SQL only — assign/end decisions live in {@code MillAssignmentService}; entities
 * never cross the service boundary (the service maps them to the {@code MillSubmitter} DTO).
 *
 * <p>Writes are {@code @Modifying} explicit SQL returning rows-affected (AD-9 optimistic locking on
 * {@code REVISION_COUNT}). "Reactivate an ended pair" is modelled as inserting a fresh active row — the
 * ended rows remain as history, coexisting under the ended-row-safe active-unique index (D4).
 */
@org.springframework.stereotype.Repository
public interface MillUserProfileXrefRepository
    extends Repository<MillUserProfileXrefEntity, Long> {

  /** The full column list, in {@link MillUserProfileXrefEntity} order (interface constant). */
  String COLS =
      "ILCR_MILL_USER_PROFILE_XREF_ID, USER_GUID, ILCR_MILL_ID, USER_DISPLAY_NAME, IDP_USERNAME,"
          + " START_DATE, END_DATE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID,"
          + " UPDATE_TIMESTAMP";

  // ---- reads -------------------------------------------------------------------------------------

  /** All assignment rows for a mill — active + ended — most-recently-started first. */
  @Query("SELECT " + COLS + " FROM THE.ILCR_MILL_USER_PROFILE_XREF WHERE ILCR_MILL_ID = :millId"
      + " ORDER BY START_DATE DESC NULLS LAST, ILCR_MILL_USER_PROFILE_XREF_ID DESC")
  List<MillUserProfileXrefEntity> findByMill(@Param("millId") long millId);

  /** All assignment rows for a submitter (by FAM {@code custom:idp_user_id}). */
  @Query("SELECT " + COLS + " FROM THE.ILCR_MILL_USER_PROFILE_XREF WHERE USER_GUID = :userGuid"
      + " ORDER BY START_DATE DESC NULLS LAST, ILCR_MILL_USER_PROFILE_XREF_ID DESC")
  List<MillUserProfileXrefEntity> findByUser(@Param("userGuid") String userGuid);

  /** The single ACTIVE assignment (END_DATE null) for a (mill, user) pair, if any — the D4 invariant. */
  @Query("SELECT " + COLS + " FROM THE.ILCR_MILL_USER_PROFILE_XREF"
      + " WHERE ILCR_MILL_ID = :millId AND USER_GUID = :userGuid AND END_DATE IS NULL")
  Optional<MillUserProfileXrefEntity> findActive(
      @Param("millId") long millId, @Param("userGuid") String userGuid);

  /** The mill's display identifiers from {@code THE.MILL} (number rendered as text). */
  @Query("SELECT TO_CHAR(MILL_NUMBER) AS MILL_NUMBER, MILL_NAME FROM THE.MILL WHERE MILL_ID = :millId")
  Optional<MillLabel> findMillLabel(@Param("millId") long millId);

  // ---- writes ------------------------------------------------------------------------------------

  /** Next surrogate id from the sequence (Spring Data JDBC {@code @Modifying} cannot return a key). */
  @Query("SELECT THE.ILCR_MILL_USER_PROFILE_XREF_SEQ.NEXTVAL FROM DUAL")
  long nextId();

  /**
   * Insert a new ACTIVE assignment at {@code REVISION_COUNT} 0 (START_DATE now, END_DATE null). Audit
   * columns are populated (never omitted — the systemic Sch 2/4/8 bug class). {@code displayName}/
   * {@code idpUsername} are the FAM name snapshot (Q5; may be null until the picker supplies them).
   */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_MILL_USER_PROFILE_XREF
          (ILCR_MILL_USER_PROFILE_XREF_ID, USER_GUID, ILCR_MILL_ID, USER_DISPLAY_NAME, IDP_USERNAME,
           START_DATE, END_DATE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID,
           UPDATE_TIMESTAMP)
      VALUES
          (:id, :userGuid, :millId, :displayName, :idpUsername,
           SYSDATE, NULL, 0, :user, SYSDATE, :user, SYSDATE)
      """)
  int insertActive(
      @Param("id") long id, @Param("userGuid") String userGuid, @Param("millId") long millId,
      @Param("displayName") String displayName, @Param("idpUsername") String idpUsername,
      @Param("user") String user);

  /**
   * Soft-end the ACTIVE assignment for a (mill, user) pair (AD-11 — never delete): set END_DATE,
   * bump revision + audit, ONLY when the stored revision still matches {@code expectedRevision} and the
   * row is still active. Returns rows affected — {@code 1} on success, {@code 0} when stale/absent.
   */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_MILL_USER_PROFILE_XREF
         SET END_DATE = SYSDATE,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND USER_GUID = :userGuid
         AND END_DATE IS NULL
         AND REVISION_COUNT = :expectedRevision
      """)
  int endActive(
      @Param("millId") long millId, @Param("userGuid") String userGuid,
      @Param("expectedRevision") int expectedRevision, @Param("user") String user);
}
