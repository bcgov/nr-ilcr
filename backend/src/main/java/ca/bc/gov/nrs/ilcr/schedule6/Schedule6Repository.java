package ca.bc.gov.nrs.ilcr.schedule6;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 6 tables (AD-3): explicit
 * {@code @Query} named-param SQL + {@code @Table} record entities — no derived queries, no {@code
 * CrudRepository}. SQL stays explicit because the model is a legacy-projection; every derivation
 * and (for Story 8.2) the transaction boundaries live in {@link Schedule6Service}.
 *
 * <p>Storage shape (delivery-DB confirmed, Story 8.1 Task 1): a road record is one {@code
 * ROAD_MAINTENANCE_REPORT} row (category {@code '6'}, classification stored as codes, general
 * comment in {@code COMMENTS}, own {@code REVISION_COUNT}); its cost/volume/per-record comment is
 * the single {@code ILCR_COST_REPORT_DETAIL} row for cost item {@code 69}, joined by {@code
 * ROAD_MAINTENANCE_REPORT_ID}. There is no category-{@code '6'} {@code ILCR_REPORT_SUMMARY} row, so
 * {@code trackStatus} comes straight from {@code ILCR_MILL_REPORT_STATUS}.
 *
 * <p>The public {@code default} methods expose plain service-facing records ({@link RoadRecordRow},
 * {@link CostDetailRow}); the {@code @Query} methods are the explicit SQL, so entities never cross
 * the service boundary.
 */
public interface Schedule6Repository extends Repository<RoadMaintenanceReportEntity, Integer> {

  /** One Schedule 6 road record (a {@code ROAD_MAINTENANCE_REPORT} row); codes stored inline. */
  record RoadRecordRow(
      int recordId,
      String tsaNumber,
      String tsbNumberCode,
      String tflNumberCode,
      String generalComment,
      Integer revisionCount) {}

  /** The cost/volume/comment detail (item 69) for a road record. */
  record CostDetailRow(
      int roadMaintenanceReportId, BigDecimal volume, Integer cost, String comments) {}

  // ---------------------------------------------------------------------------------------------
  // Reads — @Query returns @Table entities / scalars; default methods adapt to the service records.
  // ---------------------------------------------------------------------------------------------

  /** The category-{@code '6'} road-record rows for a mill/year, ordered by id (legacy order). */
  @Query(
      """
      SELECT ROAD_MAINTENANCE_REPORT_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE,
             COMMENTS, REVISION_COUNT
        FROM THE.ROAD_MAINTENANCE_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
       ORDER BY ROAD_MAINTENANCE_REPORT_ID
      """)
  List<RoadMaintenanceReportEntity> findRoadReportEntities(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * The road records for a mill/year (empty = the valid no-records state, not an error). Maps the
   * report entities to the service-facing {@link RoadRecordRow}.
   */
  default List<RoadRecordRow> findRoadRecords(long millId, int year) {
    return findRoadReportEntities(millId, year).stream()
        .map(
            e ->
                new RoadRecordRow(
                    e.roadMaintenanceReportId(),
                    e.tsaNumber(),
                    e.tsbNumberCode(),
                    e.tflNumberCode(),
                    e.comments(),
                    e.revisionCount()))
        .toList();
  }

  /**
   * The Schedule 6 cost detail rows (item {@code 69}) for a mill/year, joined to their road records
   * by {@code ROAD_MAINTENANCE_REPORT_ID} so the category/mill/year filter applies.
   */
  @Query(
      """
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.ROAD_MAINTENANCE_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID,
             d.VOLUME, d.COST, d.COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.ROAD_MAINTENANCE_REPORT r
          ON r.ROAD_MAINTENANCE_REPORT_ID = d.ROAD_MAINTENANCE_REPORT_ID
       WHERE r.ILCR_MILL_ID = :millId
         AND r.REPORT_YEAR = :year
         AND r.ILCR_CATEGORY_ID = '6'
         AND d.ILCR_REPORT_COST_ITEM_ID = 69
       ORDER BY d.ROAD_MAINTENANCE_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostReportDetailEntity> findCostDetailEntities(
      @Param("millId") long millId, @Param("year") int year);

  /** The cost detail rows mapped to the service-facing {@link CostDetailRow}. */
  default List<CostDetailRow> findCostDetails(long millId, int year) {
    return findCostDetailEntities(millId, year).stream()
        .map(
            d -> new CostDetailRow(d.roadMaintenanceReportId(), d.volume(), d.cost(), d.comments()))
        .toList();
  }

  /**
   * The Schedules 1-10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9). Empty when there is no report-status row.
   */
  @Query(
      """
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  // ===============================================================================================
  // Write path (Story 8.2) — AD-3 dumb SQL; transaction boundary, Draft gate, BR-02 counterpart-
  // clear, BR-09 placeholder logic, and 404-vs-409 disambiguation live in Schedule6Service. All
  // writes are THE-qualified and scope every UPDATE/DELETE to (id, ILCR_MILL_ID, REPORT_YEAR,
  // ILCR_CATEGORY_ID='6') so one mill's write can never touch another's rows (the Schedule 4 IDOR
  // guard). Every INSERT supplies REVISION_COUNT + both audit pairs: all five are NOT NULL in
  // delivery with NO defaults and no trigger population (Task 1 gate (iii) — RMR_AUD_B_I_U only
  // feeds the _AUD shadow from :NEW), so an insert that skips any of them fails here exactly as it
  // would in delivery.
  // ===============================================================================================

  /**
   * The next road-record PK from the shared delivery sequence (legacy {@code @SequenceGenerator},
   * {@code RoadMaintenanceReport.java:41}; delivery identity re-verified, Task 1 gate (iv)).
   * Fetched separately because {@code @Modifying} cannot return generated keys.
   */
  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  int nextRoadReportId();

  /** The next cost-detail PK from the delivery sequence. */
  @Query("SELECT THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL FROM DUAL")
  int nextCostDetailId();

  /**
   * Insert one road-maintenance record (category {@code '6'}, {@code REVISION_COUNT = 0}, all four
   * audit stamps). {@code COMMENTS} carries the CURRENT general comment — the legacy replication
   * invariant (BR-09): every cat-6 row stores the same schedule-level comment ({@code
   * Schedule6DAO.java:229}). Classification arrives pre-cleared by the service (BR-02).
   *
   * <p>The comment is sourced by a scalar sub-select over the mill/year's existing cat-6 rows
   * (highest id — the row the read side's last-row-wins loop would take) rather than passed in from
   * a value the service read earlier. That read-then-insert shape lost a concurrent {@code PUT
   * /general-comments}: the new row draws the highest sequence id, so its stale COMMENTS became the
   * served {@code generalComments} and silently reverted the just-saved comment. Reading inside the
   * INSERT collapses the window to the statement (code review 2026-08-04). NULL when the mill/year
   * has no rows yet, which is the correct value for the first record.
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ROAD_MAINTENANCE_REPORT
          (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :year, :millId, '6', :tsaNumber, :tsbNumberCode, :tflNumberCode,
           (SELECT COMMENTS
              FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = '6'
               AND ROAD_MAINTENANCE_REPORT_ID =
                   (SELECT MAX(ROAD_MAINTENANCE_REPORT_ID)
                      FROM THE.ROAD_MAINTENANCE_REPORT
                     WHERE ILCR_MILL_ID = :millId
                       AND REPORT_YEAR = :year
                       AND ILCR_CATEGORY_ID = '6')),
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertRoadReport(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("tsaNumber") String tsaNumber,
      @Param("tsbNumberCode") String tsbNumberCode,
      @Param("tflNumberCode") String tflNumberCode,
      @Param("user") String user);

  /**
   * Optimistic-lock update of one record's classification (AR11 per-record keying): sets the
   * pre-cleared codes, bumps {@code REVISION_COUNT} (recorded deviation (c) — legacy never
   * increments), and stamps {@code UPDATE_*} ONLY when the stored revision still matches and the
   * row belongs to this mill/year/category (the IDOR guard). Never touches {@code COMMENTS} — the
   * general comment is not part of the record request (S04 independence).
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the id is absent (→ 404) OR the
   *     revision is stale (→ 409). The service disambiguates via {@link #countRoadRecord}.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_MAINTENANCE_REPORT
         SET TSA_NUMBER = :tsaNumber,
             TSB_NUMBER_CODE = :tsbNumberCode,
             TFL_NUMBER_CODE = :tflNumberCode,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_MAINTENANCE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
         AND REVISION_COUNT = :expectedRevision
      """)
  int updateRoadReport(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("expectedRevision") int expectedRevision,
      @Param("tsaNumber") String tsaNumber,
      @Param("tsbNumberCode") String tsbNumberCode,
      @Param("tflNumberCode") String tflNumberCode,
      @Param("user") String user);

  /**
   * True iff a road record with this id exists under the mill/year (404-vs-409 disambiguation, the
   * Schedule 11 pattern). Placeholder rows count — the service routes an edit that targets a
   * placeholder to 404 before this runs (a placeholder is not a served record).
   */
  @Query(
      """
      SELECT COUNT(*)
        FROM THE.ROAD_MAINTENANCE_REPORT
       WHERE ROAD_MAINTENANCE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
      """)
  int countRoadRecord(@Param("id") int id, @Param("millId") long millId, @Param("year") int year);

  /**
   * Upsert the single item-69 cost detail of a road record: update-in-place when the row exists
   * (audit continuity), else insert with a fresh sequence PK. The insert-on-zero-rows branch is
   * LOAD-BEARING on edits, not just adds: the real delivery cat-6 rows have ZERO item-69 details
   * (8.1 Task 1 — 1315 detail rows, none with the road FK), so editing a real record must create
   * its detail, never fail.
   */
  default void upsertCostDetail(
      int recordId, BigDecimal volume, Integer cost, String comments, String user) {
    int updated = updateCostDetail(recordId, volume, cost, comments, user);
    if (updated == 0) {
      insertCostDetail(nextCostDetailId(), recordId, volume, cost, comments, user);
    }
  }

  /**
   * Update-in-place half of {@link #upsertCostDetail}; {@code 0} rows when the detail is absent.
   * Detail {@code REVISION_COUNT} stays untouched (legacy never bumps it — parity), only {@code
   * UPDATE_*} moves ({@code Schedule6DAO.java:337–339}).
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET VOLUME = :volume,
             COST = :cost,
             COMMENTS = :comments,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_MAINTENANCE_REPORT_ID = :recordId
         AND ILCR_REPORT_COST_ITEM_ID = 69
      """)
  int updateCostDetail(
      @Param("recordId") int recordId,
      @Param("volume") BigDecimal volume,
      @Param("cost") Integer cost,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Insert half of {@link #upsertCostDetail} (item 69; summary id NULL — a road detail hangs off
   * its report, not a summary; {@code ITEM_DESCRIPTION} stays NULL — legacy never sets it). Stamps
   * {@code REVISION_COUNT = 0} and BOTH audit pairs; the {@code ICRD_CHK_B_I_U} delivery trigger
   * requires exactly one parent FK, which {@code ROAD_MAINTENANCE_REPORT_ID} alone satisfies (Task
   * 1 gate (iii)).
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ROAD_MAINTENANCE_REPORT_ID,
           ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, NULL, :recordId, 69, :volume, :cost, :comments, NULL, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCostDetail(
      @Param("id") int id,
      @Param("recordId") int recordId,
      @Param("volume") BigDecimal volume,
      @Param("cost") Integer cost,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Replicate the general comment onto EVERY cat-6 row of the mill/year (BR-09 replication
   * invariant — legacy writes it on every row on every save, {@code Schedule6DAO.java:229,286}).
   *
   * @return rows affected — {@code 0} means the mill/year has no cat-6 rows at all (the service's
   *     insert-placeholder branch)
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_MAINTENANCE_REPORT
         SET COMMENTS = :comments,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
      """)
  int updateAllComments(
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Insert the BR-09 general-comment placeholder: classification all NULL, {@code COMMENTS} = the
   * text, NO item-69 detail ({@code Schedule6DAO.java:263–267} — the comment-storage row is bare).
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ROAD_MAINTENANCE_REPORT
          (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :year, :millId, '6', NULL, NULL, NULL, :comments,
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertPlaceholder(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Delete one placeholder row (the BR-09 third branch: clearing the comment when it is the only
   * thing stored — legacy {@code generalCommentRemovedLastRecord}, {@code
   * Schedule6DAO.java:327–330}). Mill/year-scoped and re-checked classification-NULL so a real
   * record can never be deleted.
   *
   * @return rows affected — {@code 0} when the row is not (or is no longer) NULL-classification:
   *     whitespace rather than NULL, or claimed by a concurrent {@code addRecord}. The service MUST
   *     act on that, or the clear silently no-ops behind a success message (code review
   *     2026-08-04).
   */
  @Modifying
  @Query(
      """
      DELETE FROM THE.ROAD_MAINTENANCE_REPORT
       WHERE ROAD_MAINTENANCE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
         AND TSA_NUMBER IS NULL
         AND TSB_NUMBER_CODE IS NULL
         AND TFL_NUMBER_CODE IS NULL
      """)
  int deletePlaceholder(@Param("id") int id, @Param("millId") long millId, @Param("year") int year);

  /**
   * Convert the placeholder into a real record — the BR-09 reuse branch ({@code
   * Schedule6DAO.java:268–278}): {@code addRecord} when the only existing row is the placeholder
   * updates the classification ONTO that row so its id and {@code ENTRY_*} survive. No revision
   * predicate: the placeholder is invisible to clients (excluded from {@code roadRecords[]}), so no
   * token exists to check; scoped like every other write.
   *
   * @return rows affected — {@code 0} when the row is no longer a placeholder (raced by another
   *     writer); the service treats that as a fresh-insert fallback
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ROAD_MAINTENANCE_REPORT
         SET TSA_NUMBER = :tsaNumber,
             TSB_NUMBER_CODE = :tsbNumberCode,
             TFL_NUMBER_CODE = :tflNumberCode,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_MAINTENANCE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
         AND TSA_NUMBER IS NULL
         AND TSB_NUMBER_CODE IS NULL
         AND TFL_NUMBER_CODE IS NULL
      """)
  int claimPlaceholder(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("tsaNumber") String tsaNumber,
      @Param("tsbNumberCode") String tsbNumberCode,
      @Param("tflNumberCode") String tflNumberCode,
      @Param("user") String user);
}
