package ca.bc.gov.nrs.ilcr.schedule4;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the stored Schedule 4 locations and their transportation-category amounts from the legacy
 * {@code THE} tables (AD-3, JdbcClient named params, record row-mapping). SQL only — every derivation
 * (perUnit, editable, kind) lives in {@link Schedule4Service} (AD-6).
 *
 * <p>Storage shape (delivery-DB confirmed, see the spec Completion Notes): a location is a FAMILY of
 * {@code TRANSPORTATION_REPORT} rows sharing a {@code LOCATION_DESCRIPTION}, keyed by
 * {@code ILCR_MILL_ID} + {@code REPORT_YEAR} + {@code ILCR_CATEGORY_ID='4'} — one primary report
 * (distance null) carries the 9 fixed categories, and each distance-based category (47/48/52) lives
 * on its own report with its own {@code DISTANCE}. Category amounts are
 * {@code ILCR_COST_REPORT_DETAIL} rows joined by {@code TRANSPORTATION_REPORT_ID}. Report rows are
 * ordered by {@code TRANSPORTATION_REPORT_ID} (legacy {@code findTransportationReportDetails} order);
 * details are ordered by {@code ILCR_COST_REPORT_DETAIL_ID}. The Story 4.2 write methods live at the
 * bottom of this class (AD-3 dumb SQL; the transaction + rules live in {@link Schedule4Service}).
 *
 * <p>The detail query filters to the 12 in-scope cost-item codes only (9 fixed + 3 distance-based),
 * so the deferred sub-page list codes (43 Towing, 46 Truck Rehaul, 54 Other Towing, 55 Other) never
 * leak into this read and a later sub-page read can add them without changing this contract.
 *
 * <p>All queries are {@code THE.}-qualified (tests connect as {@code THE}).
 */
@Repository
public class Schedule4Repository {

  private static final String SCHEDULE_4_CATEGORY = "4";

  /** One Schedule 4 location (a {@code TRANSPORTATION_REPORT} row). */
  public record LocationRow(int transportationReportId, String locationDescription,
      BigDecimal distance, Integer revisionCount) {
  }

  /** One in-scope transportation-category detail row for a location. */
  public record DetailRow(int transportationReportId, Integer costItemCode, BigDecimal volume,
      Integer cost) {
  }

  /**
   * One sub-page list row (Story 4.3): its own report ({@code transportationReportId}) sharing the
   * location name, its {@code costItemCode} (43/46/55), free-text {@code description}, per-report
   * {@code distance}/{@code cycle}, and the single detail's {@code volume}/{@code cost}.
   */
  public record SubPageRowRow(int transportationReportId, String locationDescription,
      Integer costItemCode, String description, BigDecimal distance, Integer cycle,
      BigDecimal volume, Integer cost) {
  }

  private final JdbcClient jdbcClient;

  public Schedule4Repository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * The Schedule 4 (category {@code "4"}) location rows for a mill/year, ordered by
   * {@code TRANSPORTATION_REPORT_ID} (legacy order). Empty when the mill/year has no Schedule 4
   * locations — the valid no-locations state (200 empty list), NOT an error.
   */
  public List<LocationRow> findLocations(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT TRANSPORTATION_REPORT_ID, LOCATION_DESCRIPTION, DISTANCE, REVISION_COUNT
              FROM THE.TRANSPORTATION_REPORT
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = :categoryId
             ORDER BY TRANSPORTATION_REPORT_ID
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .query((rs, rowNum) -> new LocationRow(
            rs.getInt("TRANSPORTATION_REPORT_ID"),
            rs.getString("LOCATION_DESCRIPTION"),
            rs.getBigDecimal("DISTANCE"),
            nullableInt(rs, "REVISION_COUNT")))
        .list();
  }

  /**
   * The in-scope category detail rows for a mill/year's Schedule 4 locations, joined by
   * {@code TRANSPORTATION_REPORT_ID} and filtered to the 12 in-scope codes (fixed 40,41,42,44,45,49,
   * 50,51,53; distance-based 47,48,52). Ordered by ({@code TRANSPORTATION_REPORT_ID},
   * {@code ILCR_COST_REPORT_DETAIL_ID}) so the service can group by location while preserving legacy
   * within-location order. Deferred codes (43,46,54,55) are excluded here — never leaked to the read.
   *
   * <p>Read in one query across all the mill/year's locations (rather than per-location) to avoid an
   * N+1 fan-out; the service groups the flat rows by {@code transportationReportId}.
   */
  public List<DetailRow> findInScopeDetails(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT d.TRANSPORTATION_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID, d.VOLUME, d.COST
              FROM THE.ILCR_COST_REPORT_DETAIL d
              JOIN THE.TRANSPORTATION_REPORT tr
                ON tr.TRANSPORTATION_REPORT_ID = d.TRANSPORTATION_REPORT_ID
             WHERE tr.ILCR_MILL_ID = :millId
               AND tr.REPORT_YEAR = :year
               AND tr.ILCR_CATEGORY_ID = :categoryId
               AND d.ILCR_REPORT_COST_ITEM_ID IN (40,41,42,44,45,49,50,51,53,47,48,52)
             ORDER BY d.TRANSPORTATION_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .query((rs, rowNum) -> new DetailRow(
            rs.getInt("TRANSPORTATION_REPORT_ID"),
            nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
            rs.getBigDecimal("VOLUME"),
            nullableInt(rs, "COST")))
        .list();
  }

  /**
   * The sub-page list rows (Story 4.3) for a mill/year: category-{@code "4"}
   * {@code TRANSPORTATION_REPORT} rows whose single detail is a sub-page code (43 Towing, 46 Truck
   * Rehaul, 55 Other). Dead code 54 is excluded. Ordered by ({@code TRANSPORTATION_REPORT_ID},
   * {@code ILCR_COST_REPORT_DETAIL_ID}); the service groups by {@code LOCATION_DESCRIPTION}.
   */
  public List<SubPageRowRow> findSubPageRows(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT tr.TRANSPORTATION_REPORT_ID, tr.LOCATION_DESCRIPTION,
                   d.ILCR_REPORT_COST_ITEM_ID, d.ITEM_DESCRIPTION,
                   tr.DISTANCE, tr.TRANSPORTATION_CYCLE_TIME, d.VOLUME, d.COST
              FROM THE.TRANSPORTATION_REPORT tr
              JOIN THE.ILCR_COST_REPORT_DETAIL d
                ON d.TRANSPORTATION_REPORT_ID = tr.TRANSPORTATION_REPORT_ID
             WHERE tr.ILCR_MILL_ID = :millId
               AND tr.REPORT_YEAR = :year
               AND tr.ILCR_CATEGORY_ID = :categoryId
               AND d.ILCR_REPORT_COST_ITEM_ID IN (43, 46, 55)
             ORDER BY tr.TRANSPORTATION_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .query((rs, rowNum) -> new SubPageRowRow(
            rs.getInt("TRANSPORTATION_REPORT_ID"),
            rs.getString("LOCATION_DESCRIPTION"),
            nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
            rs.getString("ITEM_DESCRIPTION"),
            rs.getBigDecimal("DISTANCE"),
            nullableInt(rs, "TRANSPORTATION_CYCLE_TIME"),
            rs.getBigDecimal("VOLUME"),
            nullableInt(rs, "COST")))
        .list();
  }

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9). Empty when there is no report-status row.
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

  /** Reads an Oracle NUMBER column (returned by the driver as BigDecimal) as a nullable Integer. */
  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    BigDecimal value = rs.getBigDecimal(column);
    return value == null ? null : value.intValue();
  }

  // ---------------------------------------------------------------------------------------------
  // Write path (Story 4.2) — AD-3 dumb SQL; transaction boundary + rules live in Schedule4Service.
  // A location is a family of TRANSPORTATION_REPORT rows: one primary (distance null, fixed
  // categories) + one per distance code (47/48/52) with its own distance. Legacy generator seq is
  // ILCR_REPORT_COMMON_SEQ; detail ids come from ILCR_COST_REPORT_DETAIL_SEQ.
  // ---------------------------------------------------------------------------------------------

  /**
   * Insert a new category-{@code "4"} {@code TRANSPORTATION_REPORT} row at {@code REVISION_COUNT} 0
   * and return its generated id. Used for both the primary report ({@code distance} null) and each
   * distance-child report ({@code distance} set). {@code TRANSPORTATION_CYCLE_TIME} is not written
   * here (the Truck-Rehaul cycle is Story 4.3).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param name the location description
   * @param distance the report's distance (null for the primary/fixed report)
   * @param user the acting user id (audit)
   * @return the generated {@code TRANSPORTATION_REPORT_ID}
   */
  public int insertReport(long millId, int year, String name, BigDecimal distance, String user) {
    int reportId = jdbcClient.sql("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
        .query(Integer.class)
        .single();
    jdbcClient.sql(
            """
            INSERT INTO THE.TRANSPORTATION_REPORT
                (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
                 LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, REVISION_COUNT,
                 ENTRY_USERID, ENTRY_TIMESTAMP)
            VALUES
                (:id, :year, :millId, :categoryId, :name, :distance, NULL, 0, :user, SYSTIMESTAMP)
            """)
        .param("id", reportId)
        .param("year", year)
        .param("millId", millId)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .param("name", name)
        .param("distance", distance)
        .param("user", user)
        .update();
    return reportId;
  }

  /**
   * Optimistic-lock bump of a report (§Decision 3): increments {@code REVISION_COUNT} + audit ONLY
   * when the stored revision still matches {@code expectedRevision}. Applied to the primary report;
   * the family's children are re-stamped by the same save.
   *
   * @param reportId the report PK
   * @param expectedRevision the revision the caller last read
   * @param user the acting user id (audit)
   * @return rows affected — {@code 1} on success, {@code 0} when the revision is stale (→ 409)
   */
  public int bumpRevision(int reportId, int expectedRevision, String user) {
    return jdbcClient.sql(
            """
            UPDATE THE.TRANSPORTATION_REPORT
               SET REVISION_COUNT = REVISION_COUNT + 1,
                   UPDATE_USERID = :user,
                   UPDATE_TIMESTAMP = SYSTIMESTAMP
             WHERE TRANSPORTATION_REPORT_ID = :id
               AND REVISION_COUNT = :expectedRevision
            """)
        .param("user", user)
        .param("id", reportId)
        .param("expectedRevision", expectedRevision)
        .update();
  }

  /** Re-stamp the distance on a distance-child report (audit updated). */
  public void updateReportDistance(int reportId, BigDecimal distance, String user) {
    jdbcClient.sql(
            """
            UPDATE THE.TRANSPORTATION_REPORT
               SET DISTANCE = :distance,
                   UPDATE_USERID = :user,
                   UPDATE_TIMESTAMP = SYSTIMESTAMP
             WHERE TRANSPORTATION_REPORT_ID = :id
            """)
        .param("distance", distance)
        .param("user", user)
        .param("id", reportId)
        .update();
  }

  /**
   * Rename a whole location family: re-stamp {@code LOCATION_DESCRIPTION} on every category-{@code "4"}
   * report for the mill/year currently under {@code oldName} (primary + distance children stay
   * consistent). Case-sensitive match on {@code oldName} (the exact stored value from the read).
   */
  public void renameFamily(long millId, int year, String oldName, String newName, String user) {
    jdbcClient.sql(
            """
            UPDATE THE.TRANSPORTATION_REPORT
               SET LOCATION_DESCRIPTION = :newName,
                   UPDATE_USERID = :user,
                   UPDATE_TIMESTAMP = SYSTIMESTAMP
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = :categoryId
               AND LOCATION_DESCRIPTION = :oldName
            """)
        .param("newName", newName)
        .param("user", user)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .param("oldName", oldName)
        .update();
  }

  /**
   * Upsert a detail row by {@code (transportationReportId, costItemCode)}: update-in-place first,
   * insert only when absent. A null {@code volume}/{@code cost} is still written so clearing a field
   * persists null (legacy {@code saveOrUpdateTransportationCostDetails}).
   */
  public void upsertDetail(int reportId, int costItemCode, BigDecimal volume, Integer cost,
      String user) {
    int updated = jdbcClient.sql(
            """
            UPDATE THE.ILCR_COST_REPORT_DETAIL
               SET VOLUME = :volume,
                   COST = :cost,
                   UPDATE_USERID = :user,
                   UPDATE_TIMESTAMP = SYSTIMESTAMP
             WHERE TRANSPORTATION_REPORT_ID = :reportId
               AND ILCR_REPORT_COST_ITEM_ID = :code
            """)
        .param("volume", volume)
        .param("cost", cost)
        .param("user", user)
        .param("reportId", reportId)
        .param("code", costItemCode)
        .update();

    if (updated == 0) {
      jdbcClient.sql(
              """
              INSERT INTO THE.ILCR_COST_REPORT_DETAIL
                  (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
                   VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
              VALUES
                  (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :reportId, :code,
                   :volume, :cost, NULL, :user, SYSTIMESTAMP)
              """)
          .param("reportId", reportId)
          .param("code", costItemCode)
          .param("volume", volume)
          .param("cost", cost)
          .param("user", user)
          .update();
    }
  }

  /** Delete one report and its cost-detail rows (used to clear an emptied distance-child report). */
  public void deleteReport(int reportId) {
    jdbcClient.sql("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE TRANSPORTATION_REPORT_ID = :id")
        .param("id", reportId)
        .update();
    jdbcClient.sql("DELETE FROM THE.TRANSPORTATION_REPORT WHERE TRANSPORTATION_REPORT_ID = :id")
        .param("id", reportId)
        .update();
  }

  /**
   * Delete a whole location family for a mill/year — every category-{@code "4"} report under
   * {@code name} and their cascaded cost-detail rows (BR-08, legacy
   * {@code deleteTransportationFromReport}). Idempotency is handled in the service.
   */
  public void deleteFamily(long millId, int year, String name) {
    jdbcClient.sql(
            """
            DELETE FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE TRANSPORTATION_REPORT_ID IN (
                   SELECT TRANSPORTATION_REPORT_ID
                     FROM THE.TRANSPORTATION_REPORT
                    WHERE ILCR_MILL_ID = :millId
                      AND REPORT_YEAR = :year
                      AND ILCR_CATEGORY_ID = :categoryId
                      AND LOCATION_DESCRIPTION = :name)
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .param("name", name)
        .update();
    jdbcClient.sql(
            """
            DELETE FROM THE.TRANSPORTATION_REPORT
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = :categoryId
               AND LOCATION_DESCRIPTION = :name
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .param("name", name)
        .update();
  }

  /**
   * Server-side name-uniqueness check (BR-02, case-INSENSITIVE): does another location for the
   * mill/year already use {@code name}? {@code excludeName} (the edited location's current name, or
   * null on create) removes the location's own family from the comparison, so a no-op or case-only
   * self-rename never collides. Legacy {@code doesLocationNameExist}.
   */
  public boolean nameExists(long millId, int year, String name, String excludeName) {
    // Branch on excludeName rather than binding it into `UPPER(:excludeName)`: ojdbc binds a Java
    // null there as a CLOB, and UPPER(CLOB)/comparison raises ORA-22848 ("cannot use CLOB type as
    // comparison key"). The create path (excludeName null) simply omits the self-exclusion clause.
    String sql =
        """
        SELECT COUNT(*)
          FROM THE.TRANSPORTATION_REPORT
         WHERE ILCR_MILL_ID = :millId
           AND REPORT_YEAR = :year
           AND ILCR_CATEGORY_ID = :categoryId
           AND UPPER(LOCATION_DESCRIPTION) = UPPER(:name)
        """;
    if (excludeName != null) {
      sql += "   AND UPPER(LOCATION_DESCRIPTION) <> UPPER(:excludeName)\n";
    }
    var spec = jdbcClient.sql(sql)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .param("name", name);
    if (excludeName != null) {
      spec = spec.param("excludeName", excludeName);
    }
    Integer count = spec.query(Integer.class).single();
    return count != null && count > 0;
  }

  /** The current {@code LOCATION_DESCRIPTION} of a report (the edit target's name), or empty. */
  public Optional<String> findLocationName(int reportId) {
    return jdbcClient.sql(
            """
            SELECT LOCATION_DESCRIPTION
              FROM THE.TRANSPORTATION_REPORT
             WHERE TRANSPORTATION_REPORT_ID = :id
            """)
        .param("id", reportId)
        .query(String.class)
        .optional();
  }

  /**
   * The distance-child report id holding {@code code}'s detail for the named location, or empty when
   * the family has no report for that distance code yet (an insert is needed).
   */
  public Optional<Integer> findDistanceReportId(long millId, int year, String name, int code) {
    return jdbcClient.sql(
            """
            SELECT tr.TRANSPORTATION_REPORT_ID
              FROM THE.TRANSPORTATION_REPORT tr
              JOIN THE.ILCR_COST_REPORT_DETAIL d
                ON d.TRANSPORTATION_REPORT_ID = tr.TRANSPORTATION_REPORT_ID
             WHERE tr.ILCR_MILL_ID = :millId
               AND tr.REPORT_YEAR = :year
               AND tr.ILCR_CATEGORY_ID = :categoryId
               AND tr.LOCATION_DESCRIPTION = :name
               AND d.ILCR_REPORT_COST_ITEM_ID = :code
             ORDER BY tr.TRANSPORTATION_REPORT_ID
             FETCH FIRST 1 ROWS ONLY
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .param("name", name)
        .param("code", code)
        .query(Integer.class)
        .optional();
  }

  // ---------------------------------------------------------------------------------------------
  // Sub-page list rows (Story 4.3) — each row is its own TRANSPORTATION_REPORT (shared name) + one
  // detail (item 43/46/55, ITEM_DESCRIPTION). Delete reuses deleteReport(rowId), guarded by
  // isSubPageRow so a row-delete can never remove a location's primary/category report.
  // ---------------------------------------------------------------------------------------------

  /**
   * Insert a new sub-page-row {@code TRANSPORTATION_REPORT} (category {@code "4"}, shared
   * {@code LOCATION_DESCRIPTION}, own {@code DISTANCE} + {@code TRANSPORTATION_CYCLE_TIME}) at
   * {@code REVISION_COUNT} 0 and return its id. {@code cycle} is written for Truck Rehaul only (null
   * otherwise).
   */
  public int insertSubPageReport(long millId, int year, String name, BigDecimal distance,
      Integer cycle, String user) {
    int reportId = jdbcClient.sql("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
        .query(Integer.class)
        .single();
    jdbcClient.sql(
            """
            INSERT INTO THE.TRANSPORTATION_REPORT
                (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
                 LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, REVISION_COUNT,
                 ENTRY_USERID, ENTRY_TIMESTAMP)
            VALUES
                (:id, :year, :millId, :categoryId, :name, :distance, :cycle, 0, :user, SYSTIMESTAMP)
            """)
        .param("id", reportId)
        .param("year", year)
        .param("millId", millId)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .param("name", name)
        .param("distance", distance)
        .param("cycle", cycle)
        .param("user", user)
        .update();
    return reportId;
  }

  /**
   * Insert the single {@code ILCR_COST_REPORT_DETAIL} for a sub-page row (item 43/46/55) with its
   * free-text {@code ITEM_DESCRIPTION} (legacy {@code saveOrUpdateTransportationCostDetails}).
   */
  public void insertDetailWithDescription(int reportId, int costItemCode, BigDecimal volume,
      Integer cost, String description, String user) {
    jdbcClient.sql(
            """
            INSERT INTO THE.ILCR_COST_REPORT_DETAIL
                (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
                 VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
            VALUES
                (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :reportId, :code,
                 :volume, :cost, :description, :user, SYSTIMESTAMP)
            """)
        .param("reportId", reportId)
        .param("code", costItemCode)
        .param("volume", volume)
        .param("cost", cost)
        .param("description", description)
        .param("user", user)
        .update();
  }

  /**
   * Whether {@code reportId} is a sub-page-list row (its own report for this mill/year with a single
   * detail of code 43/46/55) — the guard that keeps the row-delete endpoint from removing a primary
   * or category report. False for an unknown/foreign id (→ idempotent no-op delete).
   */
  public boolean isSubPageRow(int reportId, long millId, int year) {
    Integer count = jdbcClient.sql(
            """
            SELECT COUNT(*)
              FROM THE.TRANSPORTATION_REPORT tr
              JOIN THE.ILCR_COST_REPORT_DETAIL d
                ON d.TRANSPORTATION_REPORT_ID = tr.TRANSPORTATION_REPORT_ID
             WHERE tr.TRANSPORTATION_REPORT_ID = :id
               AND tr.ILCR_MILL_ID = :millId
               AND tr.REPORT_YEAR = :year
               AND tr.ILCR_CATEGORY_ID = :categoryId
               AND d.ILCR_REPORT_COST_ITEM_ID IN (43, 46, 55)
            """)
        .param("id", reportId)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", SCHEDULE_4_CATEGORY)
        .query(Integer.class)
        .single();
    return count != null && count > 0;
  }
}
