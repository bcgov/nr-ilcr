package ca.bc.gov.nrs.ilcr.assignment;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to {@code THE.ILCR_MILL_USER_PROFILE_XREF} (AD-3: repository interface +
 * {@code @Table} record entity {@link MillUserProfileXrefEntity} + explicit {@code @Query}
 * named-parameter SQL). SQL only — decisions live in the service; entities never cross the service
 * boundary (the service maps them to the {@code MillSubmitter} DTO in Story 2.2).
 *
 * <p>Story 2.1 scaffolds the read side; the assign/end write methods and the mill-join enrichment
 * are added in Story 2.2. These reads are exercised (red) by Story 2.2's tests.
 */
@org.springframework.stereotype.Repository
public interface MillUserProfileXrefRepository extends Repository<MillUserProfileXrefEntity, Long> {

  /**
   * All assignment rows for a mill — active (END_DATE null) and ended — most-recently-started
   * first. The service filters/derives status and joins {@code THE.MILL} for the mill number/name
   * (2.2).
   *
   * @param millId the {@code ILCR_MILL_ID}
   * @return the mill's assignment rows
   */
  @Query(
      """
      SELECT ILCR_MILL_USER_PROFILE_XREF_ID, USER_GUID, ILCR_MILL_ID, USER_DISPLAY_NAME, IDP_USERNAME,
             START_DATE, END_DATE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID,
             UPDATE_TIMESTAMP
        FROM THE.ILCR_MILL_USER_PROFILE_XREF
       WHERE ILCR_MILL_ID = :millId
       ORDER BY START_DATE DESC NULLS LAST, ILCR_MILL_USER_PROFILE_XREF_ID DESC
      """)
  List<MillUserProfileXrefEntity> findByMill(@Param("millId") long millId);

  /**
   * All assignment rows for a submitter (by FAM {@code custom:idp_user_id}) — the user-centric
   * read.
   *
   * @param userGuid the FAM user GUID ({@code USER_GUID})
   * @return the submitter's assignment rows
   */
  @Query(
      """
      SELECT ILCR_MILL_USER_PROFILE_XREF_ID, USER_GUID, ILCR_MILL_ID, USER_DISPLAY_NAME, IDP_USERNAME,
             START_DATE, END_DATE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID,
             UPDATE_TIMESTAMP
        FROM THE.ILCR_MILL_USER_PROFILE_XREF
       WHERE USER_GUID = :userGuid
       ORDER BY START_DATE DESC NULLS LAST, ILCR_MILL_USER_PROFILE_XREF_ID DESC
      """)
  List<MillUserProfileXrefEntity> findByUser(@Param("userGuid") String userGuid);
}
