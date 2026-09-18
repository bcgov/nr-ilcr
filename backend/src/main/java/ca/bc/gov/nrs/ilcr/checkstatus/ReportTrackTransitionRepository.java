package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.millcontext.MillReportStatusEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The one transactional writer of a Schedules 1&ndash;10 status transition (Story 15.3; AD-3 Spring
 * Data JDBC, explicit {@code @Modifying @Query} SQL). It is the modern shape of legacy {@code
 * SubmitReportDAO.submitReport():61-142}: the status row, then an audit-only touch of every
 * Schedule 1&ndash;10 row for the mill/year, then the ten {@code ILCR_REPORT_CATEGORY} rows.
 *
 * <p><strong>The touch is not decoration.</strong> In delivery, {@code RECORD_STATE_CODE} on every
 * {@code *_AUD} row is written by a per-table BEFORE INSERT OR UPDATE trigger that reads the track
 * status together with the row's {@code CATEGORY_STATE_CODE}, and ONLY the pair (category {@code
 * D}, status {@code S}) produces the {@code S} snapshot Story 16.2's original-value indicators read
 * from the {@code *_S_VW} views. Legacy earned that snapshot by touching every row between writing
 * {@code S} and advancing the category to {@code A}; skip a row family here and that page's
 * indicators never fire for a report submitted through this application. The touch writes no
 * business column &mdash; {@code UPDATE_USERID} and {@code UPDATE_TIMESTAMP} only &mdash; which is
 * why one writer across thirteen row families is compatible with AD-5's one-writer rule for
 * business columns (recorded deviation (E)).
 *
 * <p>{@code REVISION_COUNT} is bumped on exactly the five row families whose legacy entity declared
 * {@code @Version} and was therefore bumped by Hibernate's {@code saveOrUpdate} &mdash; {@code
 * ILCR_REPORT_SUMMARY}, {@code ILCR_REPORT_CATEGORY} and Schedule 8's three tables &mdash; and left
 * alone everywhere else (legacy parity, D12). The status row is the one deliberate exception: it is
 * bumped as every modern write does (D13, deviation (B)).
 *
 * <p>Every predicate is copied from the schedule's own repository read for a mill/year, cited
 * inline, so this class never invents a row selection. A touch that affects zero rows is normal
 * &mdash; a MET schedule may have no rows at all (Schedules 4&ndash;10 are vacuously met when
 * empty); only the status UPDATE and each category UPDATE must affect exactly one row, which the
 * service enforces.
 */
@org.springframework.stereotype.Repository
public interface ReportTrackTransitionRepository extends Repository<MillReportStatusEntity, Long> {

  /**
   * Move the Schedules 1&ndash;10 track's status code and record the submitting Licensee (BR-05).
   * Keyed on BOTH primary-key columns and on the expected current code, so a concurrent transition
   * that slipped in cannot be overwritten: zero rows affected means the row was not where the
   * caller left it. The {@code LICENSEE_*} pair is written as legacy {@code
   * updateILCRMillReportStatus():403-410} wrote it &mdash; the caller's {@code ILCR_MILL_USER_XREF}
   * key, or NULLs when the caller has no xref row for the mill. {@code
   * MILL_SILVICULTUR_STATUS_CODE}, {@code REPORT_COMPLETED_IND} and {@code COMMENTS} are never
   * named (BR-08).
   *
   * @return rows affected &mdash; 1 on success, 0 when the row is absent or no longer at {@code
   *     expectedCode}
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_MILL_REPORT_STATUS
         SET ILCR_MILL_REPORT_STATUS_CODE = :newCode,
             LICENSEE_MILL_ID = :licenseeMillId,
             LICENSEE_USER_GUID = :licenseeUserGuid,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_MILL_REPORT_STATUS_CODE = :expectedCode
      """)
  int updateTrackStatus(
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("expectedCode") String expectedCode,
      @Param("newCode") String newCode,
      @Param("licenseeMillId") Long licenseeMillId,
      @Param("licenseeUserGuid") String licenseeUserGuid,
      @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedules 1, 2 and 3: the three category summaries and their cost details (legacy
  // getReportSummary/updateReportSummary/updateCostReportDetailSchedule, SubmitReportDAO:357-388).
  // Predicate: Schedule1Repository.findSummary — ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID.
  // -----------------------------------------------------------------------------------------------

  /**
   * Summary rows of categories 1, 2 and 3 &mdash; {@code @Version} in legacy, so the revision
   * moves.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_REPORT_SUMMARY
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID IN ('1', '2', '3')
      """)
  int touchReportSummaries(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /** Cost details under the category 1, 2 and 3 summaries (audit columns only). */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID IN (
             SELECT s.ILCR_REPORT_SUMMARY_ID
               FROM THE.ILCR_REPORT_SUMMARY s
              WHERE s.ILCR_MILL_ID = :millId
                AND s.REPORT_YEAR = :year
                AND s.ILCR_CATEGORY_ID IN ('1', '2', '3'))
      """)
  int touchReportSummaryCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedule 4 (legacy updateTransportationReports, :316-325).
  // Predicate: Schedule4Repository.findReports — ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID = '4'.
  // -----------------------------------------------------------------------------------------------

  @Modifying
  @Query(
      """
      UPDATE THE.TRANSPORTATION_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '4'
      """)
  int touchTransportationReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE TRANSPORTATION_REPORT_ID IN (
             SELECT tr.TRANSPORTATION_REPORT_ID
               FROM THE.TRANSPORTATION_REPORT tr
              WHERE tr.ILCR_MILL_ID = :millId
                AND tr.REPORT_YEAR = :year
                AND tr.ILCR_CATEGORY_ID = '4')
      """)
  int touchTransportationCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedule 5 (legacy updateCamps, :301-310).
  // Predicate: Schedule5Repository.findCamps — ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID = '5'.
  // -----------------------------------------------------------------------------------------------

  @Modifying
  @Query(
      """
      UPDATE THE.CAMP_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
      """)
  int touchCamps(@Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CAMP_REPORT_ID IN (
             SELECT c.CAMP_REPORT_ID
               FROM THE.CAMP_REPORT c
              WHERE c.ILCR_MILL_ID = :millId
                AND c.REPORT_YEAR = :year
                AND c.ILCR_CATEGORY_ID = '5')
      """)
  int touchCampCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedule 6 (legacy updateRoadMaintenanceReports, :290-299).
  // Predicate: Schedule6Repository.findRecords — ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID = '6'.
  // -----------------------------------------------------------------------------------------------

  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_MAINTENANCE_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
      """)
  int touchRoadMaintenanceReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_MAINTENANCE_REPORT_ID IN (
             SELECT r.ROAD_MAINTENANCE_REPORT_ID
               FROM THE.ROAD_MAINTENANCE_REPORT r
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '6')
      """)
  int touchRoadMaintenanceCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedule 7 — ONE category ('7'), two row families: bridges (7A, legacy updateBridge :268-277)
  // and
  // culverts (7B, legacy updateCulverts :279-288).
  // Predicates: Schedule7aRepository.findBridges / Schedule7bRepository.findCulverts.
  // -----------------------------------------------------------------------------------------------

  @Modifying
  @Query(
      """
      UPDATE THE.BRIDGE_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int touchBridges(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE BRIDGE_REPORT_ID IN (
             SELECT b.BRIDGE_REPORT_ID
               FROM THE.BRIDGE_REPORT b
              WHERE b.ILCR_MILL_ID = :millId
                AND b.REPORT_YEAR = :year
                AND b.ILCR_CATEGORY_ID = '7')
      """)
  int touchBridgeCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.CULVERT_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int touchCulverts(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CULVERT_REPORT_ID IN (
             SELECT c.CULVERT_REPORT_ID
               FROM THE.CULVERT_REPORT c
              WHERE c.ILCR_MILL_ID = :millId
                AND c.REPORT_YEAR = :year
                AND c.ILCR_CATEGORY_ID = '7')
      """)
  int touchCulvertCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedule 8 — the three-level tree, no ILCR_COST_REPORT_DETAIL children (legacy
  // updateTreeToTruckReports :248-267). All three entities carried @Version, so all three bump.
  // Predicates: Schedule8Repository.findPages / findSamples / findRateRows — page ILCR_MILL_ID,
  // REPORT_YEAR, ILCR_CATEGORY_ID = '8', joined down the tree.
  // -----------------------------------------------------------------------------------------------

  @Modifying
  @Query(
      """
      UPDATE THE.TREE_TO_TRUCK_REPORT
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '8'
      """)
  int touchTreeToTruckReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.TREE_TO_TRUCK_DETAIL_REPORT
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE TREE_TO_TRUCK_REPORT_ID IN (
             SELECT p.TREE_TO_TRUCK_REPORT_ID
               FROM THE.TREE_TO_TRUCK_REPORT p
              WHERE p.ILCR_MILL_ID = :millId
                AND p.REPORT_YEAR = :year
                AND p.ILCR_CATEGORY_ID = '8')
      """)
  int touchTreeToTruckDetailReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.TREE_TO_TRUCK_RATE_DETAIL
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE TREE_TO_TRUCK_DETAIL_REPORT_ID IN (
             SELECT s.TREE_TO_TRUCK_DETAIL_REPORT_ID
               FROM THE.TREE_TO_TRUCK_DETAIL_REPORT s
               JOIN THE.TREE_TO_TRUCK_REPORT p
                 ON p.TREE_TO_TRUCK_REPORT_ID = s.TREE_TO_TRUCK_REPORT_ID
              WHERE p.ILCR_MILL_ID = :millId
                AND p.REPORT_YEAR = :year
                AND p.ILCR_CATEGORY_ID = '8')
      """)
  int touchTreeToTruckRateDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedule 9 (legacy updateContractualWorkReports, :237-246).
  // Predicate: Schedule9Repository.findRecords — ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID = '9'.
  // -----------------------------------------------------------------------------------------------

  @Modifying
  @Query(
      """
      UPDATE THE.CONTRACTUAL_WORK_REPORT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '9'
      """)
  int touchContractualWorkReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CONTRACTUAL_WORK_REPORT_ID IN (
             SELECT cwr.CONTRACTUAL_WORK_REPORT_ID
               FROM THE.CONTRACTUAL_WORK_REPORT cwr
              WHERE cwr.ILCR_MILL_ID = :millId
                AND cwr.REPORT_YEAR = :year
                AND cwr.ILCR_CATEGORY_ID = '9')
      """)
  int touchContractualWorkCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  // -----------------------------------------------------------------------------------------------
  // Schedule 10 — page, road detail, and the cost details hanging off the road detail (legacy
  // updateRoadConstructionReports, :217-231).
  // Predicates: Schedule10Repository.findPages / findRoadDetails — page ILCR_MILL_ID, REPORT_YEAR,
  // ILCR_CATEGORY_ID = '10', joined down.
  // -----------------------------------------------------------------------------------------------

  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_CONSTRUCTION_REPRT
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '10'
      """)
  int touchRoadConstructionReports(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_CONSTRUCTION_REPRT_DTL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_CONSTRUCTION_REPRT_ID IN (
             SELECT r.ROAD_CONSTRUCTION_REPRT_ID
               FROM THE.ROAD_CONSTRUCTION_REPRT r
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
      """)
  int touchRoadConstructionDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID IN (
             SELECT d.ROAD_CONSTRUCTION_REPRT_DTL_ID
               FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
               JOIN THE.ROAD_CONSTRUCTION_REPRT r
                 ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
      """)
  int touchRoadConstructionCostDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("user") String user);

  /**
   * Advance one category row's state (legacy {@code updateReportCategory():339-355} &mdash;
   * {@code @Version}, so the revision moves). Keyed on the full primary key; zero rows affected
   * means the open-year process never wrote this category's row, which the service treats as a
   * persistence failure (D6) rather than inventing the row or leaving the category behind at Draft.
   *
   * @return rows affected &mdash; 1 on success, 0 when the category row is absent
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_REPORT_CATEGORY
         SET CATEGORY_STATE_CODE = :newState,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE REPORT_YEAR = :year
         AND ILCR_MILL_ID = :millId
         AND ILCR_CATEGORY_ID = :categoryId
      """)
  int advanceCategoryState(
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("categoryId") String categoryId,
      @Param("newState") String newState,
      @Param("user") String user);
}
