package ca.bc.gov.nrs.ilcr.schedule1;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 1 tables. Query SQL stays explicit
 * because the API document is an aggregate projection over existing Oracle tables; aggregate
 * derivation and transaction boundaries remain in {@link Schedule1Service}.
 */
public interface Schedule1Repository extends Repository<Schedule1SummaryEntity, Integer> {

  /** Summary-level fields for a schedule. */
  record SummaryRow(Integer summaryId, Integer crownVolume, String comments, Integer revisionCount) {
  }

  /** One cost-report-detail row. */
  record DetailRow(Integer costItemCode, BigDecimal volume, Integer cost, String itemDescription) {
  }

  /**
   * The report summary for a mill/year/category, or empty if none exists.
   */
  default Optional<SummaryRow> findSummary(long millId, int year, String categoryId) {
    return findSummaryEntity(millId, year, categoryId)
        .map(row -> new SummaryRow(
            row.summaryId(),
            row.crownVolume(),
            row.comments(),
            row.revisionCount()));
  }

  @Query("""
      SELECT ILCR_REPORT_SUMMARY_ID, CROWN_VOLUME, COMMENTS, REVISION_COUNT
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = :categoryId
      """)
  Optional<Schedule1SummaryEntity> findSummaryEntity(
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("categoryId") String categoryId);

  /**
   * All cost-report-detail rows for a summary.
   */
  default List<DetailRow> findDetails(int summaryId) {
    return findDetailEntities(summaryId).stream()
        .map(row -> new DetailRow(
            row.costItemCode(),
            row.volume(),
            row.cost(),
            row.itemDescription()))
        .toList();
  }

  @Query("""
      SELECT ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION
        FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
       ORDER BY ILCR_COST_REPORT_DETAIL_ID
      """)
  List<Schedule1DetailEntity> findDetailEntities(@Param("summaryId") int summaryId);

  /**
   * Optimistic-lock bump of the summary (AR11): increments {@code REVISION_COUNT} and updates
   * {@code COMMENTS} + audit columns ONLY when the stored revision still matches
   * {@code expectedRevision}.
   *
   * @param summaryId the summary PK
   * @param expectedRevision the revision the caller last read
   * @param comments the new comments (nullable)
   * @param user the acting user id (audit)
   * @return rows affected - {@code 1} on success, {@code 0} when the revision is stale (409)
   */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_REPORT_SUMMARY
         SET REVISION_COUNT = REVISION_COUNT + 1,
             COMMENTS = :comments,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND REVISION_COUNT = :expectedRevision
      """)
  int bumpRevision(
      @Param("summaryId") int summaryId,
      @Param("expectedRevision") int expectedRevision,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Upsert a fixed / shared-volume detail row by {@code (summaryId, costItemCode)} where the row has
   * a NULL {@code ITEM_DESCRIPTION}. The {@code ITEM_DESCRIPTION IS NULL} guard means itemized
   * Other-Costs rows (code 19 WITH a description) are never touched (AC2). Update-in-place first;
   * insert only when absent (preserves audit continuity - no delete/re-insert churn).
   *
   * @param summaryId the summary PK
   * @param costItemCode the fixed cost-item id
   * @param volume the entered volume (nullable)
   * @param cost the entered cost (nullable)
   * @param user the acting user id (audit)
   */
  default void upsertFixedDetail(
      int summaryId, int costItemCode, BigDecimal volume, Integer cost, String user) {
    int updated = updateFixedDetail(summaryId, costItemCode, volume, cost, user);
    if (updated == 0) {
      insertFixedDetail(summaryId, costItemCode, volume, cost, user);
    }
  }

  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET VOLUME = :volume,
             COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
         AND ITEM_DESCRIPTION IS NULL
      """)
  int updateFixedDetail(
      @Param("summaryId") int summaryId,
      @Param("costItemCode") int costItemCode,
      @Param("volume") BigDecimal volume,
      @Param("cost") Integer cost,
      @Param("user") String user);

  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, :costItemCode,
           :volume, :cost, NULL, :user, SYSTIMESTAMP)
      """)
  int insertFixedDetail(
      @Param("summaryId") int summaryId,
      @Param("costItemCode") int costItemCode,
      @Param("volume") BigDecimal volume,
      @Param("cost") Integer cost,
      @Param("user") String user);

  /**
   * Delete every cost-report-detail row for a summary (fixed, shared-volume, and itemized), then the
   * summary row itself (BR-08 whole-schedule delete, S13).
   *
   * @param summaryId the summary PK
   */
  default void deleteSchedule(int summaryId) {
    deleteDetails(summaryId);
    deleteSummary(summaryId);
  }

  @Modifying
  @Query("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_REPORT_SUMMARY_ID = :summaryId")
  int deleteDetails(@Param("summaryId") int summaryId);

  @Modifying
  @Query("DELETE FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = :summaryId")
  int deleteSummary(@Param("summaryId") int summaryId);

  /**
   * The Schedules 1-10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year -
   * NOT the silviculture track (AD-9).
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);
}
