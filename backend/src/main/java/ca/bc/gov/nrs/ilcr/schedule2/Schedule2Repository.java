package ca.bc.gov.nrs.ilcr.schedule2;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the stored Schedule 2 and the cross-schedule figures it carries from the
 * legacy {@code THE} tables (AD-3, re-pinned 2026-07-20: domain repositories are Spring Data JDBC
 * {@code Repository} interfaces with explicit {@code @Query} named-param SQL + {@code @Table} record
 * entities). SQL only — every derivation lives in {@link Schedule2Service} (AD-6). This repository
 * reads Schedule 1 and Schedule 3 <em>data</em>; it does not build or modify those features.
 *
 * <p>All queries are {@code THE.}-qualified (tests connect as {@code THE}). Cross-schedule sources:
 * Schedule 3 PO&amp;P Timber volume (item 118) and PO&amp;P actual cost (item 135) are
 * {@code ILCR_COST_REPORT_DETAIL} rows on the category-{@code "3"} summary; Schedule 3 Crown Timber
 * volume (item 119) is {@code ILCR_REPORT_SUMMARY.CROWN_VOLUME} on the category-{@code "3"} summary;
 * Schedule 1 Subtotal Company Logging (item 144) is a detail row on the category-{@code "1"} summary.
 *
 * <p>The public {@code default} methods expose the plain service-facing records ({@link SummaryRow},
 * {@link DetailRow}) and compose the create-on-absent / upsert / delete sequences; the
 * {@code @Query}/{@code @Modifying} methods are the explicit SQL. The summary sequence is
 * {@code ILCR_REPORT_SUMMARY_SEQ}; detail ids come from {@code ILCR_COST_REPORT_DETAIL_SEQ}.
 */
public interface Schedule2Repository extends Repository<Schedule2SummaryEntity, Integer> {

  /** Summary-level fields for a schedule. */
  record SummaryRow(Integer summaryId, String comments, Integer revisionCount) {
  }

  /** One cost-report-detail row (volume + cost for a cost-item). */
  record DetailRow(Integer costItemCode, BigDecimal volume, Integer cost) {
  }

  // -------------------------------------------------------------------------------------------------
  // Reads.
  // -------------------------------------------------------------------------------------------------

  /**
   * The Schedule 2 (category {@code "2"}) report summary for a mill/year, or empty if none exists —
   * an empty result is the valid unsaved-schedule state (AC6), NOT an error.
   */
  default Optional<SummaryRow> findSummary(long millId, int year) {
    return findSummaryEntity(millId, year)
        .map(e -> new SummaryRow(e.summaryId(), e.comments(), e.revisionCount()));
  }

  @Query("""
      SELECT ILCR_REPORT_SUMMARY_ID, COMMENTS, REVISION_COUNT
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '2'
      """)
  Optional<Schedule2SummaryEntity> findSummaryEntity(
      @Param("millId") long millId, @Param("year") int year);

  /** The Schedule 2 detail rows (cost-items 25 and 26) for a summary. */
  default List<DetailRow> findDetails(int summaryId) {
    return findDetailEntities(summaryId).stream()
        .map(d -> new DetailRow(d.costItemCode(), d.volume(), d.cost()))
        .toList();
  }

  @Query("""
      SELECT ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST
        FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
       ORDER BY ILCR_COST_REPORT_DETAIL_ID
      """)
  List<Schedule2DetailEntity> findDetailEntities(@Param("summaryId") int summaryId);

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9).
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  /**
   * Schedule 3 PO&amp;P Timber volume (cost-item 118), carried onto {@code purchasedLogCost.volume}
   * and {@code purchasedWoodOverhead.volume} (BR-03). Empty when the Schedule 3 source is absent.
   */
  @Query("""
      SELECT d.VOLUME
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.ILCR_REPORT_SUMMARY s
          ON s.ILCR_REPORT_SUMMARY_ID = d.ILCR_REPORT_SUMMARY_ID
       WHERE s.ILCR_MILL_ID = :millId
         AND s.REPORT_YEAR = :year
         AND s.ILCR_CATEGORY_ID = '3'
         AND d.ILCR_REPORT_COST_ITEM_ID = 118
      """)
  Optional<BigDecimal> findSch3PopTimberVolume(@Param("millId") long millId, @Param("year") int year);

  /**
   * Schedule 3 PO&amp;P actual cost (cost-item 135), carried onto {@code purchasedWoodOverhead.cost}
   * (BR-04). Empty when the Schedule 3 source is absent.
   */
  @Query("""
      SELECT d.COST
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.ILCR_REPORT_SUMMARY s
          ON s.ILCR_REPORT_SUMMARY_ID = d.ILCR_REPORT_SUMMARY_ID
       WHERE s.ILCR_MILL_ID = :millId
         AND s.REPORT_YEAR = :year
         AND s.ILCR_CATEGORY_ID = '3'
         AND d.ILCR_REPORT_COST_ITEM_ID = 135
      """)
  Optional<Integer> findSch3PopActualCost(@Param("millId") long millId, @Param("year") int year);

  /**
   * Schedule 3 Crown Timber volume (item 119), stored as {@code ILCR_REPORT_SUMMARY.CROWN_VOLUME} on
   * the category-{@code "3"} summary. Empty when the Schedule 3 summary is absent (or Crown null).
   */
  @Query("""
      SELECT CROWN_VOLUME
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '3'
      """)
  Optional<BigDecimal> findSch3CrownVolume(@Param("millId") long millId, @Param("year") int year);

  /**
   * Schedule 1 Subtotal Company Logging cost (cost-item 144), a detail row on the category-{@code "1"}
   * summary. Feeds {@code totalCompanyLogging.cost}. Empty when the Schedule 1 source is absent.
   */
  @Query("""
      SELECT d.COST
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.ILCR_REPORT_SUMMARY s
          ON s.ILCR_REPORT_SUMMARY_ID = d.ILCR_REPORT_SUMMARY_ID
       WHERE s.ILCR_MILL_ID = :millId
         AND s.REPORT_YEAR = :year
         AND s.ILCR_CATEGORY_ID = '1'
         AND d.ILCR_REPORT_COST_ITEM_ID = 144
      """)
  Optional<Integer> findSch1SubtotalLoggingCost(@Param("millId") long millId, @Param("year") int year);

  // -------------------------------------------------------------------------------------------------
  // Writes (Story 3.2) — @Modifying explicit SQL; default methods compose create-on-absent / upsert /
  // delete. Transaction boundary + rules live in Schedule2Service (@Transactional).
  // -------------------------------------------------------------------------------------------------

  @Query("SELECT THE.ILCR_REPORT_SUMMARY_SEQ.NEXTVAL FROM DUAL")
  int nextSummaryId();

  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_REPORT_SUMMARY
          (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (:id, :year, :millId, '2', :comments, 0, :user, SYSTIMESTAMP)
      """)
  int insertSummaryRow(
      @Param("id") int id, @Param("millId") long millId, @Param("year") int year,
      @Param("comments") String comments, @Param("user") String user);

  /**
   * Insert a new, empty category-{@code "2"} report summary for a mill/year at {@code REVISION_COUNT}
   * 0 and return its generated id (the Schedule 2 create-on-absent divergence — Schedule 2 never
   * 404s). The freshly-inserted revision 0 is then bumped to 1 by the normal {@link #bumpRevision}.
   */
  default int insertSummary(long millId, int year, String comments, String user) {
    int id = nextSummaryId();
    insertSummaryRow(id, millId, year, comments, user);
    return id;
  }

  /**
   * Optimistic-lock bump of the summary (AR11): increments {@code REVISION_COUNT} + {@code COMMENTS}
   * + audit ONLY when the stored revision still matches {@code expectedRevision}. Returns rows
   * affected — {@code 1} on success, {@code 0} when the revision is stale (→ 409).
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
      @Param("summaryId") int summaryId, @Param("expectedRevision") int expectedRevision,
      @Param("comments") String comments, @Param("user") String user);

  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET VOLUME = :volume,
             COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :code
      """)
  int updateDetail(
      @Param("summaryId") int summaryId, @Param("code") int costItemCode,
      @Param("volume") BigDecimal volume, @Param("cost") Integer cost, @Param("user") String user);

  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, :code,
           :volume, :cost, NULL, :user, SYSTIMESTAMP)
      """)
  int insertDetail(
      @Param("summaryId") int summaryId, @Param("code") int costItemCode,
      @Param("volume") BigDecimal volume, @Param("cost") Integer cost, @Param("user") String user);

  /**
   * Upsert a Schedule 2 detail row by {@code (summaryId, costItemCode)}. Update-in-place first; insert
   * only when absent. A null {@code volume}/{@code cost} is still written so clearing a field persists
   * null.
   */
  default void upsertDetail(
      int summaryId, int costItemCode, BigDecimal volume, Integer cost, String user) {
    if (updateDetail(summaryId, costItemCode, volume, cost, user) == 0) {
      insertDetail(summaryId, costItemCode, volume, cost, user);
    }
  }

  @Modifying
  @Query("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_REPORT_SUMMARY_ID = :summaryId")
  int deleteDetails(@Param("summaryId") int summaryId);

  @Modifying
  @Query("DELETE FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = :summaryId")
  int deleteSummary(@Param("summaryId") int summaryId);

  /**
   * Delete every cost-report-detail row for a summary (items 25/26), then the summary row itself
   * (whole-schedule delete). Idempotency (Draft + no summary → no-op 200) is handled in the service.
   */
  default void deleteSchedule(int summaryId) {
    deleteDetails(summaryId);
    deleteSummary(summaryId);
  }
}
