package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.schedule1.ReportSummary;
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
 * Reads the stored Schedule 3 (Forest Management Administration Costs) from the legacy {@code THE}
 * tables (AD-3: Spring Data JDBC — a repository interface over the shared {@link ReportSummary}
 * aggregate root, explicit {@code @Query} named-parameter SQL projecting into the row records below).
 * SQL only — all derivation (crown = harvest &minus; pop, subtotals/totals, timber costs, overhead)
 * lives in {@link Schedule3Service} (AD-6); entities never cross the service boundary.
 *
 * <p>Registered for scanning in {@code SpringDataJdbcConfiguration.@EnableJdbcRepositories}. The
 * write path (upsert / delete / Crown Timber push) arrives with Story 4.2.
 */
public interface Schedule3Repository extends Repository<ReportSummary, Long> {

  /**
   * Summary-level fields for a Schedule 3 report. {@code location} carries the legacy
   * Override Harvest/Total PO&amp;P indicator ({@code Schedule3DAO} reads it from
   * {@code ILCR_REPORT_SUMMARY.LOCATION}). {@code revisionCount} is the optimistic-lock token.
   */
  record SummaryRow(Integer summaryId, String location, String comments, Integer revisionCount) {
  }

  /** One cost-report-detail row (with {@code comments}, needed for the item-124 group keys). */
  record DetailRow(Integer costItemCode, BigDecimal volume, Integer cost, String itemDescription,
      String comments) {
  }

  /**
   * One sub-page (item 124 / 38) detail row keyed by its detail id (Story 4.4). {@code comments}
   * carries the item-124 {@code SCH3_2_<TYPE>_<GRP>} group encoding; null for item-38 rows.
   */
  record SubPageRow(Integer detailId, Integer cost, String itemDescription, String comments) {
  }

  /** The Schedule 3 (category {@code "3"}) report summary for a mill/year, or empty if none exists. */
  @Query(value = """
      SELECT ILCR_REPORT_SUMMARY_ID, LOCATION, COMMENTS, REVISION_COUNT
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '3'
      """, rowMapperClass = SummaryRowMapper.class)
  Optional<SummaryRow> findSummary(@Param("millId") long millId, @Param("year") int year);

  /** All cost-report-detail rows for a summary, ordered by detail id (first-row-per-code wins). */
  @Query(value = """
      SELECT ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
       ORDER BY ILCR_COST_REPORT_DETAIL_ID
      """, rowMapperClass = DetailRowMapper.class)
  List<DetailRow> findDetails(@Param("summaryId") int summaryId);

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9). Drives {@code editable} (Draft-only).
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  // ---------------------------------------------------------------------------------------------
  // Write path (Story 4.2) — dumb SQL; the transaction boundary + rules live in Schedule3Service.
  // Cost rows (11 fixed lines) carry COST (VOLUME null); the two timber rows (118/119) carry VOLUME
  // (COST null). All fixed/timber rows have a NULL ITEM_DESCRIPTION (sub-page rows 124/38 differ and
  // are owned by Story 4.4). Upsert = update-in-place then insert (preserves audit continuity).
  // ---------------------------------------------------------------------------------------------

  /**
   * Optimistic-lock bump of the summary (AR11): increments {@code REVISION_COUNT} and updates
   * {@code COMMENTS}, the Override flag ({@code LOCATION}), and audit columns ONLY when the stored
   * revision still matches {@code expectedRevision}.
   *
   * @return rows affected — {@code 1} on success, {@code 0} when the revision is stale (→ 409)
   */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_REPORT_SUMMARY
         SET REVISION_COUNT = REVISION_COUNT + 1,
             COMMENTS = :comments,
             LOCATION = :override,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND REVISION_COUNT = :expectedRevision
      """)
  int bumpRevision(
      @Param("summaryId") int summaryId, @Param("expectedRevision") int expectedRevision,
      @Param("comments") String comments, @Param("override") String override,
      @Param("user") String user);

  /** Upsert a fixed-line COST row (NULL description); the cost columns only. */
  default void upsertFixedDetailCost(int summaryId, int costItemCode, Integer cost, String user) {
    if (updateFixedDetailCost(summaryId, costItemCode, cost, user) == 0) {
      insertFixedDetailCost(summaryId, costItemCode, cost, user);
    }
  }

  /** Update-in-place half of {@link #upsertFixedDetailCost}; {@code 0} rows when absent. */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost, UPDATE_USERID = :user, UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
         AND ITEM_DESCRIPTION IS NULL
      """)
  int updateFixedDetailCost(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("cost") Integer cost, @Param("user") String user);

  /** Insert half of {@link #upsertFixedDetailCost} (NULL description, NULL volume). */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, :costItemCode,
           NULL, :cost, NULL, :user, SYSTIMESTAMP)
      """)
  void insertFixedDetailCost(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("cost") Integer cost, @Param("user") String user);

  /** Upsert a timber VOLUME row (items 118/119; NULL description, NULL cost). */
  default void upsertVolume(int summaryId, int costItemCode, BigDecimal volume, String user) {
    if (updateVolume(summaryId, costItemCode, volume, user) == 0) {
      insertVolume(summaryId, costItemCode, volume, user);
    }
  }

  /** Update-in-place half of {@link #upsertVolume}; {@code 0} rows when absent. */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET VOLUME = :volume, UPDATE_USERID = :user, UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
         AND ITEM_DESCRIPTION IS NULL
      """)
  int updateVolume(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("volume") BigDecimal volume, @Param("user") String user);

  /** Insert half of {@link #upsertVolume} (NULL description, NULL cost). */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, :costItemCode,
           :volume, NULL, NULL, :user, SYSTIMESTAMP)
      """)
  void insertVolume(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("volume") BigDecimal volume, @Param("user") String user);

  /**
   * Delete the whole Schedule 3 row family (all detail rows for the summary, then the summary row) —
   * S08 whole-schedule delete.
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

  // ---------------------------------------------------------------------------------------------
  // Sub-page rows (Story 4.4) — item 124 (Other Acceptable, paired TOT+PO&P) / item 38 (Included
  // Unacceptable, single). Dumb SQL; the group encoding + transaction boundary live in the service.
  // ---------------------------------------------------------------------------------------------

  /** All sub-page detail rows for a summary + cost item (124 or 38), ordered by detail id. */
  @Query(value = """
      SELECT ILCR_COST_REPORT_DETAIL_ID, COST, ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
       ORDER BY ILCR_COST_REPORT_DETAIL_ID
      """, rowMapperClass = SubPageRowMapper.class)
  List<SubPageRow> findSubPageRows(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode);

  /**
   * Unconditionally bump the summary revision (Story 4.4). The {@code UPDATE} takes a row lock on the
   * summary, serializing concurrent Other-Acceptable group inserts so two racing adds cannot mint the
   * same {@code SCH3_2_*_GRP{n}} key; it also invalidates any stale main-page optimistic-lock token.
   */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_REPORT_SUMMARY
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user, UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
      """)
  void touchSummary(@Param("summaryId") int summaryId, @Param("user") String user);

  /** Insert one sub-page row (COST + description + optional group {@code comments}; NULL volume). */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, COMMENTS, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :summaryId, :costItemCode,
           NULL, :cost, :description, :comments, :user, SYSTIMESTAMP)
      """)
  void insertSubPageRow(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("cost") Integer cost, @Param("description") String description,
      @Param("comments") String comments, @Param("user") String user);

  /** Update a sub-page row's COST + description by detail id (guarded to this summary + cost item). */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost, ITEM_DESCRIPTION = :description,
             UPDATE_USERID = :user, UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_COST_REPORT_DETAIL_ID = :detailId
         AND ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
      """)
  int updateSubPageRowById(
      @Param("detailId") int detailId, @Param("summaryId") int summaryId,
      @Param("costItemCode") int costItemCode, @Param("cost") Integer cost,
      @Param("description") String description, @Param("user") String user);

  /** Update the PO&amp;P peer of an item-124 group by its exact group {@code comments}. */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost, ITEM_DESCRIPTION = :description,
             UPDATE_USERID = :user, UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
         AND COMMENTS = :comments
      """)
  int updateSubPageRowByComments(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("cost") Integer cost, @Param("description") String description,
      @Param("comments") String comments, @Param("user") String user);

  /** Delete a sub-page row by detail id (guarded to this summary + cost item); {@code 0} when absent. */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_COST_REPORT_DETAIL_ID = :detailId
         AND ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
      """)
  int deleteSubPageRowById(
      @Param("detailId") int detailId, @Param("summaryId") int summaryId,
      @Param("costItemCode") int costItemCode);

  /** Delete the PO&amp;P peer of an item-124 group by its exact group {@code comments}. */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemCode
         AND COMMENTS = :comments
      """)
  void deleteSubPageRowByComments(
      @Param("summaryId") int summaryId, @Param("costItemCode") int costItemCode,
      @Param("comments") String comments);

  // ---------------------------------------------------------------------------------------------
  // Row mappers — explicit column-name mapping (AD-3), reading the legacy THE columns exactly.
  // ---------------------------------------------------------------------------------------------

  /** Maps an {@code ILCR_REPORT_SUMMARY} row into a {@link SummaryRow}. */
  class SummaryRowMapper implements RowMapper<SummaryRow> {
    @Override
    public SummaryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SummaryRow(
          nullableInt(rs, "ILCR_REPORT_SUMMARY_ID"),
          rs.getString("LOCATION"),
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
          rs.getString("ITEM_DESCRIPTION"),
          rs.getString("COMMENTS"));
    }
  }

  /** Maps an {@code ILCR_COST_REPORT_DETAIL} sub-page row into a {@link SubPageRow}. */
  class SubPageRowMapper implements RowMapper<SubPageRow> {
    @Override
    public SubPageRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SubPageRow(
          nullableInt(rs, "ILCR_COST_REPORT_DETAIL_ID"),
          nullableInt(rs, "COST"),
          rs.getString("ITEM_DESCRIPTION"),
          rs.getString("COMMENTS"));
    }
  }

  /** Reads an Oracle NUMBER column (returned by the driver as BigDecimal) as a nullable Integer. */
  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    BigDecimal value = rs.getBigDecimal(column);
    return value == null ? null : value.intValue();
  }
}
