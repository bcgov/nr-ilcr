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
