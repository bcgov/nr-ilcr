package ca.bc.gov.nrs.ilcr.schedule1;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the stored Schedule 1 from the legacy {@code THE} tables (AD-3, JdbcClient named params,
 * record row-mapping). SQL only — derivation lives in {@link Schedule1Service} (AD-6).
 */
@Repository
public class Schedule1Repository {

  /** Summary-level fields for a schedule. */
  public record SummaryRow(Integer summaryId, Integer crownVolume, String comments,
      Integer revisionCount) {
  }

  /** One cost-report-detail row. */
  public record DetailRow(Integer costItemCode, BigDecimal volume, Integer cost,
      String itemDescription) {
  }

  /** One itemized Other-Costs (item-19, non-null description) row, keyed by its detail id. */
  public record OtherCostDetailRow(Integer id, String description, Integer cost, BigDecimal volume) {
  }

  private final JdbcClient jdbcClient;

  public Schedule1Repository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * The report summary for a mill/year/category, or empty if none exists.
   */
  public Optional<SummaryRow> findSummary(long millId, int year, String categoryId) {
    return jdbcClient.sql(
            """
            SELECT ILCR_REPORT_SUMMARY_ID, CROWN_VOLUME, COMMENTS, REVISION_COUNT
              FROM THE.ILCR_REPORT_SUMMARY
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = :categoryId
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", categoryId)
        .query((rs, rowNum) -> new SummaryRow(
            nullableInt(rs, "ILCR_REPORT_SUMMARY_ID"),
            nullableInt(rs, "CROWN_VOLUME"),
            rs.getString("COMMENTS"),
            nullableInt(rs, "REVISION_COUNT")))
        .optional();
  }

  /**
   * All cost-report-detail rows for a summary.
   */
  public List<DetailRow> findDetails(int summaryId) {
    return jdbcClient.sql(
            """
            SELECT ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION
              FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
             ORDER BY ILCR_COST_REPORT_DETAIL_ID
            """)
        .param("summaryId", summaryId)
        .query((rs, rowNum) -> new DetailRow(
            nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
            rs.getBigDecimal("VOLUME"),
            nullableInt(rs, "COST"),
            rs.getString("ITEM_DESCRIPTION")))
        .list();
  }

  /**
   * All Schedule 3 (category {@code "3"}) cost-report-detail rows for a mill/year, or empty when no
   * Schedule 3 summary exists. Story 2.3 reads the Crown Timber volume (item 119) for the BR-03
   * pre-fill and the admin-cost source rows (items 37/115/135) for the BR-04 pulls (D1: the Crown
   * Timber source is the item-119 detail VOLUME, not the summary {@code CROWN_VOLUME} column). SQL
   * only — the derivation (crownCost = harvest &minus; PO&amp;P, pre-fill decision) lives in
   * {@link Schedule1Service} (AD-6). Tolerant of a missing Schedule 3 (returns an empty list).
   */
  public List<DetailRow> findSchedule3Details(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT d.ILCR_REPORT_COST_ITEM_ID, d.VOLUME, d.COST, d.ITEM_DESCRIPTION
              FROM THE.ILCR_COST_REPORT_DETAIL d
              JOIN THE.ILCR_REPORT_SUMMARY s
                ON d.ILCR_REPORT_SUMMARY_ID = s.ILCR_REPORT_SUMMARY_ID
             WHERE s.ILCR_MILL_ID = :millId
               AND s.REPORT_YEAR = :year
               AND s.ILCR_CATEGORY_ID = '3'
             ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
            """)
        .param("millId", millId)
        .param("year", year)
        .query((rs, rowNum) -> new DetailRow(
            nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
            rs.getBigDecimal("VOLUME"),
            nullableInt(rs, "COST"),
            rs.getString("ITEM_DESCRIPTION")))
        .list();
  }

  // ---------------------------------------------------------------------------------------------
  // Other-Costs itemized rows (Story 2.4) — item-19 WITH a non-null ITEM_DESCRIPTION. The sole
  // writer of these rows; the shared-volume row (null description) is owned by the main PUT.
  // ---------------------------------------------------------------------------------------------

  /** The itemized Other-Costs rows for a summary (non-null description), ordered by detail id. */
  public List<OtherCostDetailRow> findOtherCostRows(int summaryId) {
    return jdbcClient.sql(
            """
            SELECT ILCR_COST_REPORT_DETAIL_ID, ITEM_DESCRIPTION, COST, VOLUME
              FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
               AND ILCR_REPORT_COST_ITEM_ID = 19
               AND ITEM_DESCRIPTION IS NOT NULL
             ORDER BY ILCR_COST_REPORT_DETAIL_ID
            """)
        .param("summaryId", summaryId)
        .query((rs, rowNum) -> new OtherCostDetailRow(
            nullableInt(rs, "ILCR_COST_REPORT_DETAIL_ID"),
            rs.getString("ITEM_DESCRIPTION"),
            nullableInt(rs, "COST"),
            rs.getBigDecimal("VOLUME")))
        .list();
  }

  /**
   * The shared Other-Costs volume (the item-19 row with a NULL description), or empty if none.
   * Tolerant of a corrupt duplicate shared row: takes the first by detail id rather than throwing
   * (matching the Story 2.3 Sch 3 read's first-wins defensiveness — the single-shared-row invariant
   * is a write-side guarantee, not a runtime assumption).
   */
  public Optional<BigDecimal> findSharedOtherCostsVolume(int summaryId) {
    return jdbcClient.sql(
            """
            SELECT VOLUME
              FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
               AND ILCR_REPORT_COST_ITEM_ID = 19
               AND ITEM_DESCRIPTION IS NULL
             ORDER BY ILCR_COST_REPORT_DETAIL_ID
            """)
        .param("summaryId", summaryId)
        .query(BigDecimal.class)
        .list()
        .stream()
        .findFirst();
  }

  /**
   * Insert one itemized Other-Costs row (item-19, non-null description) inheriting the shared volume
   * (BR-06). Uses {@code ILCR_COST_REPORT_DETAIL_SEQ}; audit {@code ENTRY_*} set (DB triggers own the
   * rest). Never writes the shared null-description row.
   */
  public void insertOtherCost(
      int summaryId, String description, Integer cost, BigDecimal volume, String user) {
    jdbcClient.sql(
            """
            INSERT INTO THE.ILCR_COST_REPORT_DETAIL
                (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
                 VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
            VALUES
                (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, 19,
                 :volume, :cost, :description, :user, SYSTIMESTAMP)
            """)
        .param("summaryId", summaryId)
        .param("volume", volume)
        .param("cost", cost)
        .param("description", description)
        .param("user", user)
        .update();
  }

  /**
   * Update an itemized Other-Costs row's description + cost, guarded so only an item-19 row WITH a
   * description under this summary is touched (never the shared row, never another summary's row).
   *
   * @return rows affected — {@code 0} when the id is not a matching itemized row (→ 404)
   */
  public int updateOtherCost(
      int detailId, int summaryId, String description, Integer cost, String user) {
    return jdbcClient.sql(
            """
            UPDATE THE.ILCR_COST_REPORT_DETAIL
               SET ITEM_DESCRIPTION = :description,
                   COST = :cost,
                   UPDATE_USERID = :user,
                   UPDATE_TIMESTAMP = SYSTIMESTAMP
             WHERE ILCR_COST_REPORT_DETAIL_ID = :detailId
               AND ILCR_REPORT_SUMMARY_ID = :summaryId
               AND ILCR_REPORT_COST_ITEM_ID = 19
               AND ITEM_DESCRIPTION IS NOT NULL
            """)
        .param("description", description)
        .param("cost", cost)
        .param("user", user)
        .param("detailId", detailId)
        .param("summaryId", summaryId)
        .update();
  }

  /**
   * Delete an itemized Other-Costs row, guarded to an item-19 row WITH a description under this
   * summary (never the shared row).
   *
   * @return rows affected — {@code 0} when the id is not a matching itemized row (→ 404)
   */
  public int deleteOtherCost(int detailId, int summaryId) {
    return jdbcClient.sql(
            """
            DELETE FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ILCR_COST_REPORT_DETAIL_ID = :detailId
               AND ILCR_REPORT_SUMMARY_ID = :summaryId
               AND ILCR_REPORT_COST_ITEM_ID = 19
               AND ITEM_DESCRIPTION IS NOT NULL
            """)
        .param("detailId", detailId)
        .param("summaryId", summaryId)
        .update();
  }

  /** Reads an Oracle NUMBER column (returned by the driver as BigDecimal) as a nullable Integer. */
  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    BigDecimal value = rs.getBigDecimal(column);
    return value == null ? null : value.intValue();
  }

  // ---------------------------------------------------------------------------------------------
  // Write path (Story 2.1) — AD-3 dumb SQL; transaction boundary + rules live in Schedule1Service.
  // ---------------------------------------------------------------------------------------------

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
   * Upsert a fixed / shared-volume detail row by {@code (summaryId, costItemCode)} where the row has
   * a NULL {@code ITEM_DESCRIPTION}. The {@code ITEM_DESCRIPTION IS NULL} guard means itemized
   * Other-Costs rows (code 19 WITH a description) are never touched (AC2). Update-in-place first;
   * insert only when absent (preserves audit continuity — no delete/re-insert churn).
   *
   * @param summaryId the summary PK
   * @param costItemCode the fixed cost-item id
   * @param volume the entered volume (nullable)
   * @param cost the entered cost (nullable)
   * @param user the acting user id (audit)
   */
  public void upsertFixedDetail(
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
               AND ITEM_DESCRIPTION IS NULL
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
   * Delete every cost-report-detail row for a summary (fixed, shared-volume, and itemized), then the
   * summary row itself (BR-08 whole-schedule delete, S13).
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
}
