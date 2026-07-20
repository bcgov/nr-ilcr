package ca.bc.gov.nrs.ilcr.schedule2;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the stored Schedule 2 and the cross-schedule figures it carries from the legacy {@code THE}
 * tables (AD-3, JdbcClient named params, record row-mapping). SQL only — every derivation lives in
 * {@link Schedule2Service} (AD-6). This repository reads Schedule 1 and Schedule 3 <em>data</em>; it
 * does not build or modify those features.
 *
 * <p>All queries are {@code THE.}-qualified (tests connect as {@code THE}). Cross-schedule sources
 * (per the Story 3.1 storage resolution recorded in the spec): Schedule 3 PO&amp;P Timber volume
 * (item 118) and PO&amp;P actual cost (item 135) are {@code ILCR_COST_REPORT_DETAIL} rows on the
 * category-{@code "3"} summary; Schedule 3 Crown Timber volume (item 119) is the
 * {@code ILCR_REPORT_SUMMARY.CROWN_VOLUME} column on the category-{@code "3"} summary; Schedule 1
 * Subtotal Company Logging (item 144) is a detail row on the category-{@code "1"} summary.
 */
@Repository
public class Schedule2Repository {

  private static final String SCHEDULE_2_CATEGORY = "2";
  private static final String SCHEDULE_3_CATEGORY = "3";
  private static final String SCHEDULE_1_CATEGORY = "1";

  private static final int ITEM_POP_TIMBER_VOLUME = 118;
  private static final int ITEM_POP_ACTUAL_COST = 135;
  private static final int ITEM_SCH1_SUBTOTAL_LOGGING = 144;

  /** Summary-level fields for a schedule. */
  public record SummaryRow(Integer summaryId, String comments, Integer revisionCount) {
  }

  /** One cost-report-detail row (volume + cost for a cost-item). */
  public record DetailRow(Integer costItemCode, BigDecimal volume, Integer cost) {
  }

  private final JdbcClient jdbcClient;

  public Schedule2Repository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * The Schedule 2 (category {@code "2"}) report summary for a mill/year, or empty if none exists —
   * an empty result is the valid unsaved-schedule state (AC6), NOT an error.
   */
  public Optional<SummaryRow> findSummary(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT ILCR_REPORT_SUMMARY_ID, COMMENTS, REVISION_COUNT
              FROM THE.ILCR_REPORT_SUMMARY
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = :categoryId
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_2_CATEGORY)
        .query((rs, rowNum) -> new SummaryRow(
            nullableInt(rs, "ILCR_REPORT_SUMMARY_ID"),
            rs.getString("COMMENTS"),
            nullableInt(rs, "REVISION_COUNT")))
        .optional();
  }

  /** The Schedule 2 detail rows (cost-items 25 and 26) for a summary. */
  public List<DetailRow> findDetails(int summaryId) {
    return jdbcClient.sql(
            """
            SELECT ILCR_REPORT_COST_ITEM_ID, VOLUME, COST
              FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
             ORDER BY ILCR_COST_REPORT_DETAIL_ID
            """)
        .param("summaryId", summaryId)
        .query((rs, rowNum) -> new DetailRow(
            nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
            rs.getBigDecimal("VOLUME"),
            nullableInt(rs, "COST")))
        .list();
  }

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9).
   */
  public Optional<String> findTrackStatus(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT ILCR_MILL_REPORT_STATUS_CODE
              FROM THE.ILCR_MILL_REPORT_STATUS
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
            """)
        .param("millId", millId)
        .param("year", year)
        .query(String.class)
        .optional();
  }

  /**
   * Schedule 3 PO&amp;P Timber volume (cost-item 118), carried onto {@code purchasedLogCost.volume}
   * and {@code purchasedWoodOverhead.volume} (BR-03). Empty when the Schedule 3 source is absent.
   */
  public Optional<BigDecimal> findSch3PopTimberVolume(long millId, int year) {
    return findSch3DetailVolume(millId, year, ITEM_POP_TIMBER_VOLUME);
  }

  /**
   * Schedule 3 PO&amp;P actual cost (cost-item 135), carried onto {@code purchasedWoodOverhead.cost}
   * (BR-04). Empty when the Schedule 3 source is absent.
   */
  public Optional<Integer> findSch3PopActualCost(long millId, int year) {
    return findSch3DetailCost(millId, year, ITEM_POP_ACTUAL_COST);
  }

  /**
   * Schedule 3 Crown Timber volume (item 119), stored as {@code ILCR_REPORT_SUMMARY.CROWN_VOLUME} on
   * the category-{@code "3"} summary. Carried onto {@code totalCompanyLogging.volume}. Empty when the
   * Schedule 3 summary is absent (or its Crown volume is null).
   */
  public Optional<BigDecimal> findSch3CrownVolume(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT CROWN_VOLUME
              FROM THE.ILCR_REPORT_SUMMARY
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = :categoryId
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_3_CATEGORY)
        .query(BigDecimal.class)
        .optional();
  }

  /**
   * Schedule 1 Subtotal Company Logging cost (cost-item 144), a detail row on the category-{@code "1"}
   * summary. Feeds {@code totalCompanyLogging.cost}. Empty when the Schedule 1 source is absent.
   */
  public Optional<Integer> findSch1SubtotalLoggingCost(long millId, int year) {
    return findDetailCost(millId, year, SCHEDULE_1_CATEGORY, ITEM_SCH1_SUBTOTAL_LOGGING);
  }

  private Optional<BigDecimal> findSch3DetailVolume(long millId, int year, int costItemCode) {
    return jdbcClient.sql(
            """
            SELECT d.VOLUME
              FROM THE.ILCR_COST_REPORT_DETAIL d
              JOIN THE.ILCR_REPORT_SUMMARY s
                ON s.ILCR_REPORT_SUMMARY_ID = d.ILCR_REPORT_SUMMARY_ID
             WHERE s.ILCR_MILL_ID = :millId
               AND s.REPORT_YEAR = :year
               AND s.ILCR_CATEGORY_ID = :categoryId
               AND d.ILCR_REPORT_COST_ITEM_ID = :costItemCode
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_3_CATEGORY)
        .param("costItemCode", costItemCode)
        .query(BigDecimal.class)
        .optional();
  }

  private Optional<Integer> findSch3DetailCost(long millId, int year, int costItemCode) {
    return findDetailCost(millId, year, SCHEDULE_3_CATEGORY, costItemCode);
  }

  private Optional<Integer> findDetailCost(long millId, int year, String categoryId,
      int costItemCode) {
    return jdbcClient.sql(
            """
            SELECT d.COST
              FROM THE.ILCR_COST_REPORT_DETAIL d
              JOIN THE.ILCR_REPORT_SUMMARY s
                ON s.ILCR_REPORT_SUMMARY_ID = d.ILCR_REPORT_SUMMARY_ID
             WHERE s.ILCR_MILL_ID = :millId
               AND s.REPORT_YEAR = :year
               AND s.ILCR_CATEGORY_ID = :categoryId
               AND d.ILCR_REPORT_COST_ITEM_ID = :costItemCode
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", categoryId)
        .param("costItemCode", costItemCode)
        .query(Integer.class)
        .optional();
  }

  /** Reads an Oracle NUMBER column (returned by the driver as BigDecimal) as a nullable Integer. */
  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    BigDecimal value = rs.getBigDecimal(column);
    return value == null ? null : value.intValue();
  }

  // ---------------------------------------------------------------------------------------------
  // Write path (Story 3.2) — AD-3 dumb SQL; transaction boundary + rules live in Schedule2Service.
  // ---------------------------------------------------------------------------------------------

  /**
   * Insert a new, empty category-{@code "2"} report summary for a mill/year at {@code REVISION_COUNT}
   * 0 and return its generated id (the Schedule 2 create-on-absent divergence — Schedule 2 never
   * 404s). The freshly-inserted revision 0 is then bumped to 1 by the normal {@link #bumpRevision}
   * write, so a saved Schedule 2 always reads back with a non-null, monotonically-increasing revision.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param comments the entered comments (nullable)
   * @param user the acting user id (audit)
   * @return the generated {@code ILCR_REPORT_SUMMARY_ID}
   */
  public int insertSummary(long millId, int year, String comments, String user) {
    int summaryId = jdbcClient.sql("SELECT THE.ILCR_REPORT_SUMMARY_SEQ.NEXTVAL FROM DUAL")
        .query(Integer.class)
        .single();
    jdbcClient.sql(
            """
            INSERT INTO THE.ILCR_REPORT_SUMMARY
                (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
                 COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP)
            VALUES
                (:id, :year, :millId, :categoryId, :comments, 0, :user, SYSTIMESTAMP)
            """)
        .param("id", summaryId)
        .param("year", year)
        .param("millId", millId)
        .param("categoryId", SCHEDULE_2_CATEGORY)
        .param("comments", comments)
        .param("user", user)
        .update();
    return summaryId;
  }

  /**
   * Optimistic-lock bump of the summary (AR11): increments {@code REVISION_COUNT} and updates
   * {@code COMMENTS} + audit columns ONLY when the stored revision still matches
   * {@code expectedRevision}.
   *
   * @param summaryId the summary PK
   * @param expectedRevision the revision the caller last read
   * @param comments the new comments (nullable)
   * @param user the acting user id (audit)
   * @return rows affected — {@code 1} on success, {@code 0} when the revision is stale (→ 409)
   */
  public int bumpRevision(int summaryId, int expectedRevision, String comments, String user) {
    return jdbcClient.sql(
            """
            UPDATE THE.ILCR_REPORT_SUMMARY
               SET REVISION_COUNT = REVISION_COUNT + 1,
                   COMMENTS = :comments,
                   UPDATE_USERID = :user,
                   UPDATE_TIMESTAMP = SYSTIMESTAMP
             WHERE ILCR_REPORT_SUMMARY_ID = :id
               AND REVISION_COUNT = :expectedRevision
            """)
        .param("comments", comments)
        .param("user", user)
        .param("id", summaryId)
        .param("expectedRevision", expectedRevision)
        .update();
  }

  /**
   * Upsert a Schedule 2 detail row by {@code (summaryId, costItemCode)}. Update-in-place first; insert
   * only when absent (preserves audit continuity — no delete/re-insert churn). Unlike Schedule 1's
   * fixed-detail upsert, Schedule 2 has NO {@code ITEM_DESCRIPTION} itemization, so this is a plain
   * key match. A null {@code volume}/{@code cost} is still written so clearing a field persists null.
   *
   * @param summaryId the summary PK
   * @param costItemCode the Schedule 2 cost-item id (25 or 26)
   * @param volume the entered volume (nullable; only item 26 carries a volume)
   * @param cost the entered cost (nullable)
   * @param user the acting user id (audit)
   */
  public void upsertDetail(
      int summaryId, int costItemCode, BigDecimal volume, Integer cost, String user) {
    int updated = jdbcClient.sql(
            """
            UPDATE THE.ILCR_COST_REPORT_DETAIL
               SET VOLUME = :volume,
                   COST = :cost,
                   UPDATE_USERID = :user,
                   UPDATE_TIMESTAMP = SYSTIMESTAMP
             WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
               AND ILCR_REPORT_COST_ITEM_ID = :code
            """)
        .param("volume", volume)
        .param("cost", cost)
        .param("user", user)
        .param("summaryId", summaryId)
        .param("code", costItemCode)
        .update();

    if (updated == 0) {
      jdbcClient.sql(
              """
              INSERT INTO THE.ILCR_COST_REPORT_DETAIL
                  (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
                   VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
              VALUES
                  (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, :code,
                   :volume, :cost, NULL, :user, SYSTIMESTAMP)
              """)
          .param("summaryId", summaryId)
          .param("code", costItemCode)
          .param("volume", volume)
          .param("cost", cost)
          .param("user", user)
          .update();
    }
  }

  /**
   * Delete every cost-report-detail row for a summary (items 25/26), then the summary row itself
   * (whole-schedule delete). Idempotency (Draft + no summary → no-op 200) is handled in the service.
   *
   * @param summaryId the summary PK
   */
  public void deleteSchedule(int summaryId) {
    jdbcClient.sql("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_REPORT_SUMMARY_ID = :id")
        .param("id", summaryId)
        .update();
    jdbcClient.sql("DELETE FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = :id")
        .param("id", summaryId)
        .update();
  }
}
