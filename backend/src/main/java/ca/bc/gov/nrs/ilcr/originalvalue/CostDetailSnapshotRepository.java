package ca.bc.gov.nrs.ilcr.originalvalue;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.RowMapper;

/**
 * Reads the licensee's submitted cost-detail rows from {@code THE.ILCR_COST_REPORT_DETAIL_S_VW}
 * (AD-3: Spring Data JDBC — repository interface + {@code @Table} record entity {@link
 * CostDetailSnapshot} + explicit {@code @Query} named-parameter SQL). SQL only.
 *
 * <p>One repository rather than one per schedule, because there is one view: {@code
 * ILCR_COST_REPORT_DETAIL} is the cost table every schedule shares, and a row's owning schedule is
 * whichever of its parent foreign keys is non-null. The finders below are therefore named for the
 * parent column they filter on, and a schedule picks the one matching how it stores its costs —
 * summary-shaped schedules (1, 2, 3) by {@code ILCR_REPORT_SUMMARY_ID}, list-shaped ones (5, 6, 7A,
 * 7B, 9, 10, 11 and Schedule 4) by their own row's id.
 *
 * <p><b>Read-only, always.</b> The view has no write path and must never gain one; the snapshot it
 * exposes is produced by database triggers stamping {@code RECORD_STATE_CODE = 'S'}, never by this
 * application (PRD DL-18).
 */
public interface CostDetailSnapshotRepository extends Repository<CostDetailSnapshot, Long> {

  /**
   * One submitted cost-detail row, keyed for lookup by the cost item it carries.
   *
   * @param detailId the row's own {@code ILCR_COST_REPORT_DETAIL_ID} — the key an itemized
   *     Other-Costs row is addressed by, since cost item 19 repeats within one parent and so cannot
   *     be keyed by its cost item
   * @param parentId the value of whichever parent foreign key the finder filtered on, so a caller
   *     grouping several parents' rows in one query can bucket them without a second read
   */
  record Row(
      Integer detailId,
      Integer parentId,
      Integer costItemCode,
      BigDecimal volume,
      Integer cost,
      String itemDescription,
      String comments) {}

  /** Submitted cost details for one report summary — Schedules 1, 2 and 3. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             ILCR_REPORT_SUMMARY_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findBySummary(@Param("summaryId") int summaryId);

  /** Submitted cost details for a set of camp reports — Schedule 5. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             CAMP_REPORT_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE CAMP_REPORT_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findByCampReports(@Param("parentIds") List<Integer> parentIds);

  /** Submitted cost details for a set of transportation reports — Schedule 4. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             TRANSPORTATION_REPORT_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE TRANSPORTATION_REPORT_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findByTransportationReports(@Param("parentIds") List<Integer> parentIds);

  /** Submitted cost details for a set of road-maintenance reports — Schedule 6. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             ROAD_MAINTENANCE_REPORT_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE ROAD_MAINTENANCE_REPORT_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findByRoadMaintenanceReports(@Param("parentIds") List<Integer> parentIds);

  /** Submitted cost details for a set of bridge reports — Schedule 7A. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             BRIDGE_REPORT_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE BRIDGE_REPORT_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findByBridgeReports(@Param("parentIds") List<Integer> parentIds);

  /** Submitted cost details for a set of culvert reports — Schedule 7B. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             CULVERT_REPORT_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE CULVERT_REPORT_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findByCulvertReports(@Param("parentIds") List<Integer> parentIds);

  /** Submitted cost details for a set of contractual-work reports — Schedule 9. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             CONTRACTUAL_WORK_REPORT_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE CONTRACTUAL_WORK_REPORT_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findByContractualWorkReports(@Param("parentIds") List<Integer> parentIds);

  /** Submitted cost details for a set of road-construction detail rows — Schedule 10. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             ROAD_CONSTRUCTION_REPRT_DTL_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findByRoadConstructionDetails(@Param("parentIds") List<Integer> parentIds);

  /** Submitted cost details for a set of silviculture locations — Schedule 11. */
  @Query(
      value =
          """
      SELECT ILCR_COST_REPORT_DETAIL_ID,
             BASIC_SILVICULTURE_REPORT_ID AS PARENT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST,
             ITEM_DESCRIPTION, COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL_S_VW
       WHERE BASIC_SILVICULTURE_REPORT_ID IN (:parentIds)
      """,
      rowMapperClass = RowMapperImpl.class)
  List<Row> findBySilvicultureLocations(@Param("parentIds") List<Integer> parentIds);

  /** Maps a snapshot cost-detail row, whichever parent column the finder aliased to PARENT_ID. */
  class RowMapperImpl implements RowMapper<Row> {
    @Override
    public Row mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new Row(
          nullableInt(rs, "ILCR_COST_REPORT_DETAIL_ID"),
          nullableInt(rs, "PARENT_ID"),
          nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
          rs.getBigDecimal("VOLUME"),
          nullableInt(rs, "COST"),
          rs.getString("ITEM_DESCRIPTION"),
          rs.getString("COMMENTS"));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
      int value = rs.getInt(column);
      return rs.wasNull() ? null : value;
    }
  }
}
