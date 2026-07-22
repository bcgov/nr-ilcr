package ca.bc.gov.nrs.ilcr.schedule1;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.RowMapper;

/**
 * Reads/writes the stored Schedule 1 in the legacy {@code THE} tables (AD-3: Spring Data JDBC —
 * repository interface + {@code @Table} record entity {@link ReportSummary} + explicit
 * {@code @Query} named-parameter SQL). SQL only — derivation and transactions live in
 * {@link Schedule1Service} (AD-6). Query results project into the row records below; the service
 * maps those to DTOs (entities never cross the service boundary).
 */
public interface Schedule1Repository extends Repository<ReportSummary, Long> {

  /** Summary-level fields for a schedule. */
  record SummaryRow(Integer summaryId, Integer crownVolume, String comments,
      Integer revisionCount) {
  }

  /** One cost-report-detail row. */
  record DetailRow(Integer costItemCode, BigDecimal volume, Integer cost,
      String itemDescription) {
  }

  /** One itemized Other-Costs (item-19, non-null description) row, keyed by its detail id. */
  record OtherCostDetailRow(Integer id, String description, Integer cost, BigDecimal volume) {
  }

  /**
   * The report summary for a mill/year/category, or empty if none exists.
   */
  @Query(value = """
      SELECT ILCR_REPORT_SUMMARY_ID, CROWN_VOLUME, COMMENTS, REVISION_COUNT
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = :categoryId
      """, rowMapperClass = SummaryRowMapper.class)
  Optional<SummaryRow> findSummary(
      @Param("millId") long millId, @Param("year") int year, @Param("categoryId") String categoryId);

  /**
   * All cost-report-detail rows for a summary.
   */
  @Query(value = """
      SELECT ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION
        FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
       ORDER BY ILCR_COST_REPORT_DETAIL_ID
      """, rowMapperClass = DetailRowMapper.class)
  List<DetailRow> findDetails(@Param("summaryId") int summaryId);

  /**
   * All Schedule 3 (category {@code "3"}) cost-report-detail rows for a mill/year, or empty when no
   * Schedule 3 summary exists. Story 2.3 reads the Crown Timber volume (item 119) for the BR-03
   * pre-fill and the admin-cost source rows (items 37/115/135) for the BR-04 pulls (D1: the Crown
   * Timber source is the item-119 detail VOLUME, not the summary {@code CROWN_VOLUME} column). SQL
   * only — the derivation (crownCost = harvest &minus; PO&amp;P, pre-fill decision) lives in
   * {@link Schedule1Service} (AD-6). Tolerant of a missing Schedule 3 (returns an empty list).
   */
  @Query(value = """
      SELECT d.ILCR_REPORT_COST_ITEM_ID, d.VOLUME, d.COST, d.ITEM_DESCRIPTION
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.ILCR_REPORT_SUMMARY s
          ON d.ILCR_REPORT_SUMMARY_ID = s.ILCR_REPORT_SUMMARY_ID
       WHERE s.ILCR_MILL_ID = :millId
         AND s.REPORT_YEAR = :year
         AND s.ILCR_CATEGORY_ID = '3'
       ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
      """, rowMapperClass = DetailRowMapper.class)
  List<DetailRow> findSchedule3Details(@Param("millId") long millId, @Param("year") int year);

  // ---------------------------------------------------------------------------------------------
  // Other-Costs itemized rows (Story 2.4) — item-19 WITH a non-null ITEM_DESCRIPTION. The sole
  // writer of these rows; the shared-volume row (null description) is owned by the main PUT.
  // ---------------------------------------------------------------------------------------------

  /** The itemized Other-Costs rows for a summary (non-null description), ordered by detail id. */
  @Query(value = """
      SELECT ILCR_COST_REPORT_DETAIL_ID, ITEM_DESCRIPTION, COST, VOLUME
        FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = 19
         AND ITEM_DESCRIPTION IS NOT NULL
       ORDER BY ILCR_COST_REPORT_DETAIL_ID
      """, rowMapperClass = OtherCostDetailRowMapper.class)
  List<OtherCostDetailRow> findOtherCostRows(@Param("summaryId") int summaryId);

  /**
   * The shared Other-Costs volume (the item-19 row with a NULL description), or empty if none.
   * Tolerant of a corrupt duplicate shared row: {@code FETCH FIRST 1 ROW ONLY} takes the first by
   * detail id rather than throwing (matching the Story 2.3 Sch 3 read's first-wins defensiveness —
   * the single-shared-row invariant is a write-side guarantee, not a runtime assumption).
   */
  @Query("""
      SELECT VOLUME
        FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = 19
         AND ITEM_DESCRIPTION IS NULL
       ORDER BY ILCR_COST_REPORT_DETAIL_ID
       FETCH FIRST 1 ROW ONLY
      """)
  Optional<BigDecimal> findSharedOtherCostsVolume(@Param("summaryId") int summaryId);

  /**
   * Insert one itemized Other-Costs row (item-19, non-null description) inheriting the shared volume
   * (BR-06). Uses {@code ILCR_COST_REPORT_DETAIL_SEQ}; audit {@code ENTRY_*} set (DB triggers own the
   * rest). Never writes the shared null-description row.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, 19,
           :volume, :cost, :description, :user, SYSTIMESTAMP)
      """)
  void insertOtherCost(
      @Param("summaryId") int summaryId, @Param("description") String description,
      @Param("cost") Integer cost, @Param("volume") BigDecimal volume, @Param("user") String user);

  /**
   * Update an itemized Other-Costs row's description + cost, guarded so only an item-19 row WITH a
   * description under this summary is touched (never the shared row, never another summary's row).
   *
   * @return rows affected — {@code 0} when the id is not a matching itemized row (→ 404)
   */
  @Modifying
  @Query("""
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
  int updateOtherCost(
      @Param("detailId") int detailId, @Param("summaryId") int summaryId,
      @Param("description") String description, @Param("cost") Integer cost,
      @Param("user") String user);

  /**
   * Delete an itemized Other-Costs row, guarded to an item-19 row WITH a description under this
   * summary (never the shared row).
   *
   * @return rows affected — {@code 0} when the id is not a matching itemized row (→ 404)
   */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_COST_REPORT_DETAIL_ID = :detailId
         AND ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = 19
         AND ITEM_DESCRIPTION IS NOT NULL
      """)
  int deleteOtherCost(@Param("detailId") int detailId, @Param("summaryId") int summaryId);

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
  default void upsertFixedDetail(
      int summaryId, int costItemCode, BigDecimal volume, Integer cost, String user) {
    int updated = updateFixedDetail(summaryId, costItemCode, volume, cost, user);
    if (updated == 0) {
      insertFixedDetail(summaryId, costItemCode, volume, cost, user);
    }
  }

  /** Update-in-place half of {@link #upsertFixedDetail}; {@code 0} rows when the row is absent. */
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
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("volume") BigDecimal volume, @Param("cost") Integer cost, @Param("user") String user);

  /** Insert half of {@link #upsertFixedDetail} (NULL description); uses the detail sequence. */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, :costItemCode,
           :volume, :cost, NULL, :user, SYSTIMESTAMP)
      """)
  void insertFixedDetail(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("volume") BigDecimal volume, @Param("cost") Integer cost, @Param("user") String user);

  /**
   * Delete every cost-report-detail row for a summary (fixed, shared-volume, and itemized), then the
   * summary row itself (BR-08 whole-schedule delete, S13).
   *
   * @param summaryId the summary PK
   */
  default void deleteSchedule(int summaryId) {
    deleteDetailsBySummary(summaryId);
    deleteSummary(summaryId);
  }

  /** Delete all detail rows for a summary (first half of {@link #deleteSchedule}). */
  @Modifying
  @Query("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_REPORT_SUMMARY_ID = :summaryId")
  void deleteDetailsBySummary(@Param("summaryId") int summaryId);

  /** Delete the summary row (second half of {@link #deleteSchedule}). */
  @Modifying
  @Query("DELETE FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = :summaryId")
  void deleteSummary(@Param("summaryId") int summaryId);

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

  // ---------------------------------------------------------------------------------------------
  // Row mappers — explicit column-name mapping (AD-3 record row-mapping), reading the legacy THE
  // columns exactly as the previous JdbcClient lambdas did. Referenced by the @Query methods above.
  // ---------------------------------------------------------------------------------------------

  /** Maps an {@code ILCR_REPORT_SUMMARY} row into a {@link SummaryRow}. */
  class SummaryRowMapper implements RowMapper<SummaryRow> {
    @Override
    public SummaryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SummaryRow(
          nullableInt(rs, "ILCR_REPORT_SUMMARY_ID"),
          nullableInt(rs, "CROWN_VOLUME"),
          rs.getString("COMMENTS"),
          nullableInt(rs, "REVISION_COUNT"));
    }
  }

  /** Maps an {@code ILCR_COST_REPORT_DETAIL} row into a {@link DetailRow}. */
  class DetailRowMapper implements RowMapper<DetailRow> {
    @Override
    public DetailRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new DetailRow(
          nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
          rs.getBigDecimal("VOLUME"),
          nullableInt(rs, "COST"),
          rs.getString("ITEM_DESCRIPTION"));
    }
  }

  /** Maps an itemized {@code ILCR_COST_REPORT_DETAIL} row into an {@link OtherCostDetailRow}. */
  class OtherCostDetailRowMapper implements RowMapper<OtherCostDetailRow> {
    @Override
    public OtherCostDetailRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new OtherCostDetailRow(
          nullableInt(rs, "ILCR_COST_REPORT_DETAIL_ID"),
          rs.getString("ITEM_DESCRIPTION"),
          nullableInt(rs, "COST"),
          rs.getBigDecimal("VOLUME"));
    }
  }

  /** Reads an Oracle NUMBER column (returned by the driver as BigDecimal) as a nullable Integer. */
  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    BigDecimal value = rs.getBigDecimal(column);
    return value == null ? null : value.intValue();
  }
}
