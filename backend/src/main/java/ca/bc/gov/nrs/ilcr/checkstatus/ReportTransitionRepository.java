package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.millcontext.MillReportStatusEntity;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * SQL for a report-status transition on the Schedules 1&ndash;10 track, Spring Data JDBC with
 * explicit queries (AD-3). No decisions live here.
 *
 * <p>This is the only writer of {@code THE.ILCR_MILL_REPORT_STATUS} and the only writer of {@code
 * THE.ILCR_REPORT_CATEGORY.CATEGORY_STATE_CODE}. Both rows are INSERTed when a reporting year is
 * opened ({@code ReportingYearRepository}) and were never updated before this.
 *
 * <p>The audit sweep reproduces legacy's {@code SubmitReportDAO.submitReport} loop: a transition
 * stamps the actor and timestamp on every table holding the track's data &mdash; <strong>thirteen
 * distinct tables reached by twenty statements</strong>, because {@code ILCR_COST_REPORT_DETAIL}
 * hangs off eight different parents and is stamped once per parent &mdash; and changes business
 * data on only two. Five of the fifteen also have {@code REVISION_COUNT} incremented; see below.
 * Thirteen + those two is the "fifteen tables" the story's ACs speak of; all three numbers describe
 * the same sweep. Legacy walked Hibernate object graphs row by row; these are set-based UPDATEs
 * scoped by the same keys, which means one timestamp per statement instead of one per row. Nothing
 * reads the difference.
 *
 * <p><strong>{@code SYSDATE} everywhere, and that is delivery-verified rather than
 * inferred.</strong> All fifteen tables this class writes hold {@code UPDATE_TIMESTAMP} as {@code
 * DATE NOT NULL} in delivery {@code THE} (probed on {@code fortmp1}, 2026-09-16, Oracle 19.10). An
 * earlier cut chose {@code SYSDATE} for six and {@code SYSTIMESTAMP} for nine, on the strength of
 * the <em>test snapshot's</em> types — the snapshot declares several as {@code TIMESTAMP} and is
 * simply wrong about delivery. Writing {@code SYSTIMESTAMP} into a {@code DATE} column is not an
 * error (Oracle narrows it implicitly, dropping the fractional seconds) which is exactly why no
 * test could catch it. {@code SYSDATE} is correct against both shapes, since {@code DATE} widens to
 * {@code TIMESTAMP} without loss, and it matches legacy, whose single Java-side value carried date
 * precision.
 *
 * <p><strong>{@code REVISION_COUNT} follows legacy entity by entity, which means five of the
 * fifteen tables ARE bumped.</strong> Legacy's sweep dirtied each entity and let Hibernate flush
 * it, so any entity mapping {@code revision_count} with {@code @Version} had it incremented. Five
 * do: {@code ILCRReportSummary:82}, {@code ILCRReportCategory:39}, {@code TreeToTruckReport:116},
 * {@code TreeToTruckDetailReport:126}, {@code TreeToTruckRateDetail:83}. The other ten — including
 * {@code ILCRMillReportStatus}, whose column is a plain {@code @Column} — carry no {@code @Version}
 * and were written back unchanged, so they are not bumped here either. This is load-bearing on
 * {@code ILCR_REPORT_SUMMARY}: it is the row this codebase's own {@code StaleRevisionException}
 * guards, so under legacy a concurrent schedule save holding revision <em>n</em> was REJECTED after
 * a transition, and omitting the bump would silently let it through. There is no row lock on this
 * path; see {@link #updateTrackStatusWithAuditor}.
 */
@org.springframework.stereotype.Repository
public interface ReportTransitionRepository extends Repository<MillReportStatusEntity, Long> {

  /**
   * The acting user's mill-user cross-reference key, which is what the report records as its
   * auditor. Returns empty when the user holds no association for the mill — legacy then wrote NULL
   * into both auditor columns, which this story reproduces.
   *
   * <p><strong>Usually empty in production, by design.</strong> Only an administrator holds {@code
   * SET_REPORT_STATUS}, and an administrator is normally tied to no mill, so a real verify will
   * commonly write NULL into both auditor columns and {@code UPDATE_USERID} is then the only trace
   * of who signed off. That is legacy-faithful, not a defect — but do not read the four auditor
   * columns as a reliable record of the verifier.
   *
   * <p>No active/inactive-date predicate, deliberately: legacy's named query is {@code FROM
   * ILCRMillUserXref mux WHERE mux.id.ilcr_mill_id = :mill_id AND mux.id.user_guid = :user_guid}
   * ({@code model/ILCRMillUserXref.java:26}) with no date filter, and the ratified user/mill
   * association decision records the legacy account-active flag as non-enforcing and replicated
   * as-is. Adding the filter the mill-scope gate uses would be a parity deviation, not a fix.
   *
   * <p>Returns the column rather than a flag because this is an existence check whose result is the
   * FK value: {@code ILCR_MILL_USER_XREF.ILCR_MILL_ID} is an FK to {@code
   * ILCR_MILL_STATUS_XREF.ILCR_MILL_STATUS_XREF_ID}, which equals {@code MILL_ID} only by the
   * snapshot's 1:1 invariant — so the value read back is not independent evidence of the lookup.
   *
   * @param millId the mill, matched against {@code ILCR_MILL_ID} (an FK to {@code
   *     ILCR_MILL_STATUS_XREF_ID}, not the mill's own natural id)
   * @param userGuid the acting user's directory GUID
   * @return the cross-reference's mill id, or empty when there is no such association
   */
  @Query(
      """
      SELECT x.ILCR_MILL_ID
        FROM THE.ILCR_MILL_USER_XREF x
       WHERE x.ILCR_MILL_ID = :millId
         AND x.USER_GUID = :userGuid
      """)
  Optional<Long> findUserXrefMillId(
      @Param("millId") long millId, @Param("userGuid") String userGuid);

  /**
   * Move the Schedules 1&ndash;10 track status and record the auditor. {@code
   * MILL_SILVICULTUR_STATUS_CODE} is deliberately absent from the SET list: the Schedule 11 track
   * is independent and never moves with this one (AD-9).
   *
   * <p><strong>No optimistic guard and no {@code REVISION_COUNT} bump — legacy parity, ratified
   * 2026-09-16.</strong> {@code ILCRMillReportStatus} maps the column as a plain
   * {@code @Column(name="REVISION_COUNT") private int revision_count} ({@code
   * model/ILCRMillReportStatus.java:44-45}), <em>not</em> {@code @Version}, and {@code
   * updateILCRMillReportStatus} set only the status code, the timestamp, the actor and the
   * auditor/licensee association — so Hibernate wrote the loaded revision straight back, unchanged.
   * An earlier cut of this method guarded on the revision and incremented it; both were removed as
   * deviations from legacy. The status row therefore carries no lost-update protection, exactly as
   * it did not in 2.0.4.
   *
   * <p>Both auditor columns are always assigned, including to NULL, because that is what legacy's
   * Hibernate association write did when the acting user had no cross-reference.
   *
   * <p>⚠️ <strong>Correct for {@code S→V} only — do NOT reuse this method for the
   * reversals.</strong> Legacy ({@code SubmitReportDAO.updateILCRMillReportStatus:403-414}) skips
   * the association block <em>entirely</em> when the target is {@code 'D'}, leaving both pairs
   * untouched, and writes the <em>licensee</em> pair (not the auditor pair) when the target is
   * {@code 'S'}. Only a non-{@code D}, non-{@code S} target writes the auditor pair. So {@code S→D}
   * or {@code V→S} routed through this statement would null out an auditor legacy preserves. The
   * rule tables in {@link ReportTransitionService} are genuinely shared; this write path is not —
   * Story 15.3 and Epic 18 need their own SET lists.
   *
   * @param millId the mill
   * @param year the reporting year
   * @param statusCode the target status code
   * @param auditorMillId the auditor cross-reference's mill id, or null
   * @param auditorUserGuid the auditor's directory GUID, or null
   * @param user the acting user for the audit column
   * @return rows updated: 1 on success, 0 only if the row vanished
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_MILL_REPORT_STATUS
         SET ILCR_MILL_REPORT_STATUS_CODE = :statusCode,
             AUDITOR_MILL_ID = :auditorMillId,
             AUDITOR_USER_GUID = :auditorUserGuid,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  int updateTrackStatusWithAuditor(
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("statusCode") String statusCode,
      @Param("auditorMillId") Long auditorMillId,
      @Param("auditorUserGuid") String auditorUserGuid,
      @Param("user") String user);

  /**
   * Advance the ten Schedules 1&ndash;10 category rows to the state legacy's transition pair table
   * yields. Category {@code '11'} is Schedule 11's and is excluded: a mill/year carries eleven
   * category rows, and a 1&ndash;10 transition moves exactly ten of them.
   *
   * @param millId the mill
   * @param year the reporting year
   * @param categoryState the target category state code
   * @param user the acting user for the audit column
   * @return rows updated, expected to be ten
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_REPORT_CATEGORY
         SET CATEGORY_STATE_CODE = :categoryState,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID IN ('1','2','3','4','5','6','7','8','9','10')
      """)
  int advanceCategoryStates(
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("categoryState") String categoryState,
      @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // CATEGORY SCOPING IS LEGACY'S — all EIGHT parents, not five. An earlier cut scoped only five,
  // on the strength of the DAO method SIGNATURES: three of them take (reportingYear, millID) with
  // no category parameter. That was wrong. Those three hardcode the category INSIDE the DAO and
  // their named queries bind it in the WHERE, so legacy filtered them too:
  //   TRANSPORTATION_REPORT '4' (Schedule4DAO:390 setParameter + TransportationReport:36)
  //   CAMP_REPORT           '5' (Schedule5DAO:308 setParameter + CampReport:36)
  //   TREE_TO_TRUCK_REPORT  '8' (Schedule8DAO:134 setParameter + TreeToTruckReport:35)
  // The other five pass it through the signature:
  //   ROAD_MAINTENANCE_REPORT '6'  (Schedule6DAO.getRoadMaintenanceReports:164)
  //   BRIDGE_REPORT          '7'  (Schedule7aDAO.getBridgeReports:154)
  //   CULVERT_REPORT         '7'  (Schedule7bDAO.getCulvertReports:115 — the SAME '7'; the table,
  //                                not the category, is what separates 7A from 7B)
  //   CONTRACTUAL_WORK_REPORT '9' (Schedule9DAO.getContractualWorkReports:712)
  //   ROAD_CONSTRUCTION_REPRT '10' (Schedule10DAO.findRoadConstructionReprt:776)
  // Omitting any of them over-scopes the stamp to rows legacy left alone — silent
  // UPDATE_USERID/UPDATE_TIMESTAMP corruption on another category's data. All eight predicates
  // also match this codebase's own convention for these tables (e.g. Schedule9Repository scopes
  // every read and write to ILCR_CATEGORY_ID = '9', Schedule4Repository to '4').
  //
  // Child tables carry no predicate of their own: legacy reached them by walking the object graph
  // from an already-filtered parent, so the predicate lives in each subselect instead.
  //
  // Audit sweep — thirteen distinct tables via twenty statements, actor and timestamp only.
  // Schedules 1/2/3 share the summary table, and ILCR_COST_REPORT_DETAIL is stamped once per parent
  // FK (eight of them), which is why the statement count exceeds the table count. Schedule 11's
  // BASIC_SILVICULTURE_REPORT is absent by design: legacy's 1-10 loop never touched it.
  // -----------------------------------------------------------------------------------------------

  /**
   * Schedules 1, 2 and 3 summaries. Scoped to those three categories because the table also holds
   * rows for categories legacy's 1&ndash;10 loop did not stamp through this path.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_REPORT_SUMMARY
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID IN ('1','2','3')
      """)
  int stampReportSummaries(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** The cost details hanging off those three summaries. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_REPORT_SUMMARY_ID IN (
               SELECT s.ILCR_REPORT_SUMMARY_ID
                 FROM THE.ILCR_REPORT_SUMMARY s
                WHERE s.ILCR_MILL_ID = :millId
                  AND s.REPORT_YEAR = :year
                  AND s.ILCR_CATEGORY_ID IN ('1','2','3'))
      """)
  int stampSummaryCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 4 transportation reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.TRANSPORTATION_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '4'
      """)
  int stampTransportationReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 4 cost details. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE TRANSPORTATION_REPORT_ID IN (
               SELECT t.TRANSPORTATION_REPORT_ID
                 FROM THE.TRANSPORTATION_REPORT t
                WHERE t.ILCR_MILL_ID = :millId
                  AND t.REPORT_YEAR = :year
                  AND t.ILCR_CATEGORY_ID = '4')
      """)
  int stampTransportationCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 5 camp reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.CAMP_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
      """)
  int stampCampReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 5 cost details. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE CAMP_REPORT_ID IN (
               SELECT c.CAMP_REPORT_ID
                 FROM THE.CAMP_REPORT c
                WHERE c.ILCR_MILL_ID = :millId
                  AND c.REPORT_YEAR = :year
                  AND c.ILCR_CATEGORY_ID = '5')
      """)
  int stampCampCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 6 road maintenance reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_MAINTENANCE_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
      """)
  int stampRoadMaintenanceReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 6 cost details. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ROAD_MAINTENANCE_REPORT_ID IN (
               SELECT r.ROAD_MAINTENANCE_REPORT_ID
                 FROM THE.ROAD_MAINTENANCE_REPORT r
                WHERE r.ILCR_MILL_ID = :millId
                  AND r.REPORT_YEAR = :year
                  AND r.ILCR_CATEGORY_ID = '6')
      """)
  int stampRoadMaintenanceCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 7A bridge reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.BRIDGE_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int stampBridgeReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 7A cost details. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE BRIDGE_REPORT_ID IN (
               SELECT b.BRIDGE_REPORT_ID
                 FROM THE.BRIDGE_REPORT b
                WHERE b.ILCR_MILL_ID = :millId
                  AND b.REPORT_YEAR = :year
                  AND b.ILCR_CATEGORY_ID = '7')
      """)
  int stampBridgeCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 7B culvert reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.CULVERT_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int stampCulvertReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 7B cost details. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE CULVERT_REPORT_ID IN (
               SELECT c.CULVERT_REPORT_ID
                 FROM THE.CULVERT_REPORT c
                WHERE c.ILCR_MILL_ID = :millId
                  AND c.REPORT_YEAR = :year
                  AND c.ILCR_CATEGORY_ID = '7')
      """)
  int stampCulvertCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 8 tree-to-truck reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.TREE_TO_TRUCK_REPORT
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '8'
      """)
  int stampTreeToTruckReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 8 detail rows. */
  @Modifying
  @Query(
      """
      UPDATE THE.TREE_TO_TRUCK_DETAIL_REPORT
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE TREE_TO_TRUCK_REPORT_ID IN (
               SELECT t.TREE_TO_TRUCK_REPORT_ID
                 FROM THE.TREE_TO_TRUCK_REPORT t
                WHERE t.ILCR_MILL_ID = :millId
                  AND t.REPORT_YEAR = :year
                  AND t.ILCR_CATEGORY_ID = '8')
      """)
  int stampTreeToTruckDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /**
   * Schedule 8 rate-detail rows, two levels below the report.
   *
   * <p>Schedule 8 has NO cost-detail sweep, and that is legacy's own behaviour, not an omission
   * here: {@code SubmitReportDAO.updateTreeToTruckReports:249-266} walks report &rarr; detail
   * &rarr; rate-detail and is the one per-schedule method that never calls {@code
   * updateCostReportDetailSchedule}. Do not add one.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.TREE_TO_TRUCK_RATE_DETAIL
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE TREE_TO_TRUCK_DETAIL_REPORT_ID IN (
               SELECT d.TREE_TO_TRUCK_DETAIL_REPORT_ID
                 FROM THE.TREE_TO_TRUCK_DETAIL_REPORT d
                WHERE d.TREE_TO_TRUCK_REPORT_ID IN (
                        SELECT t.TREE_TO_TRUCK_REPORT_ID
                          FROM THE.TREE_TO_TRUCK_REPORT t
                         WHERE t.ILCR_MILL_ID = :millId
                           AND t.REPORT_YEAR = :year
                           AND t.ILCR_CATEGORY_ID = '8'))
      """)
  int stampTreeToTruckRateDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 9 contractual work reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.CONTRACTUAL_WORK_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '9'
      """)
  int stampContractualWorkReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 9 cost details. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE CONTRACTUAL_WORK_REPORT_ID IN (
               SELECT c.CONTRACTUAL_WORK_REPORT_ID
                 FROM THE.CONTRACTUAL_WORK_REPORT c
                WHERE c.ILCR_MILL_ID = :millId
                  AND c.REPORT_YEAR = :year
                  AND c.ILCR_CATEGORY_ID = '9')
      """)
  int stampContractualWorkCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 10 road construction reports. */
  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_CONSTRUCTION_REPRT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '10'
      """)
  int stampRoadConstructionReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 10 detail rows. */
  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_CONSTRUCTION_REPRT_DTL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ROAD_CONSTRUCTION_REPRT_ID IN (
               SELECT r.ROAD_CONSTRUCTION_REPRT_ID
                 FROM THE.ROAD_CONSTRUCTION_REPRT r
                WHERE r.ILCR_MILL_ID = :millId
                  AND r.REPORT_YEAR = :year
                  AND r.ILCR_CATEGORY_ID = '10')
      """)
  int stampRoadConstructionDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Schedule 10 cost details, which hang off the detail row rather than the report. */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID IN (
               SELECT d.ROAD_CONSTRUCTION_REPRT_DTL_ID
                 FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
                WHERE d.ROAD_CONSTRUCTION_REPRT_ID IN (
                        SELECT r.ROAD_CONSTRUCTION_REPRT_ID
                          FROM THE.ROAD_CONSTRUCTION_REPRT r
                         WHERE r.ILCR_MILL_ID = :millId
                           AND r.REPORT_YEAR = :year
                           AND r.ILCR_CATEGORY_ID = '10'))
      """)
  int stampRoadConstructionCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);
}
