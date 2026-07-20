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
 * <p>Storage shape (resolved from legacy {@code Schedule4DAO.buildSchedule4Results} / {@code saveReport},
 * see the spec Completion Notes): one {@code TRANSPORTATION_REPORT} row = one location
 * ({@code LOCATION_DESCRIPTION} + a single per-location {@code DISTANCE} +
 * {@code TRANSPORTATION_CYCLE_TIME}), keyed by {@code ILCR_MILL_ID} + {@code REPORT_YEAR} +
 * {@code ILCR_CATEGORY_ID='4'}; its category amounts are {@code ILCR_COST_REPORT_DETAIL} rows joined by
 * {@code TRANSPORTATION_REPORT_ID}. Locations are ordered by {@code TRANSPORTATION_REPORT_ID} (legacy
 * {@code findTransportationReportDetails} order); details are ordered by
 * {@code ILCR_COST_REPORT_DETAIL_ID}.
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
      BigDecimal distance) {
  }

  /** One in-scope transportation-category detail row for a location. */
  public record DetailRow(int transportationReportId, Integer costItemCode, BigDecimal volume,
      Integer cost) {
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
            SELECT TRANSPORTATION_REPORT_ID, LOCATION_DESCRIPTION, DISTANCE
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
            rs.getBigDecimal("DISTANCE")))
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
}
