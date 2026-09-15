package ca.bc.gov.nrs.ilcr.originalvalue;

import static ca.bc.gov.nrs.ilcr.util.ResultSetUtil.nullableInt;
import static ca.bc.gov.nrs.ilcr.util.ResultSetUtil.nullableLong;

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
 *
 * <h2>Why the {@code IN}-list finders come in pairs</h2>
 *
 * <p>Every multi-parent finder below is a {@code default} guard in front of an {@code ...In}
 * {@code @Query}. Spring expands a named collection parameter one placeholder per element and does
 * no empty-case rewriting &mdash; {@code NamedParameterUtils.substituteNamedParameters} turns an
 * empty list into the literal SQL {@code IN ()}, which Oracle rejects with {@code ORA-00936:
 * missing expression}. An empty parent set is a perfectly ordinary state here (a document beyond
 * Draft that happens to have no rows yet), so the guard answers it with an empty list and never
 * issues the statement. Callers therefore need no {@code isEmpty()} check of their own, and a
 * future caller cannot reintroduce the fault by forgetting one.
 */
public interface CostDetailSnapshotRepository extends Repository<CostDetailSnapshot, Long> {

  /**
   * One submitted cost-detail row, keyed for lookup by the cost item it carries.
   *
   * @param detailId the row's own {@code ILCR_COST_REPORT_DETAIL_ID} — the key an itemized
   *     Other-Costs row is addressed by, since cost item 19 repeats within one parent and so cannot
   *     be keyed by its cost item
   * @param parentId the value of whichever parent foreign key the finder filtered on, so a caller
   *     grouping several parents' rows in one query can bucket them without a second read. A {@code
   *     Long} because the owning entities' ids are: {@code BRIDGE_REPORT_ID}, {@code
   *     CULVERT_REPORT_ID} and {@code BASIC_SILVICULTURE_REPORT_ID} are all {@code long} on their
   *     entity records, and an {@code int} here would have had those callers cast on the way in — a
   *     silent truncation past {@link Integer#MAX_VALUE} that turns into an empty snapshot rather
   *     than an error. Callers whose own ids are {@code int} widen losslessly instead.
   */
  record Row(
      Integer detailId,
      Long parentId,
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
  List<Row> findByCampReportsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findByCampReports(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty() ? List.of() : findByCampReportsIn(parentIds);
  }

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
  List<Row> findByTransportationReportsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findByTransportationReports(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty()
        ? List.of()
        : findByTransportationReportsIn(parentIds);
  }

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
  List<Row> findByRoadMaintenanceReportsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findByRoadMaintenanceReports(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty()
        ? List.of()
        : findByRoadMaintenanceReportsIn(parentIds);
  }

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
  List<Row> findByBridgeReportsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findByBridgeReports(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty() ? List.of() : findByBridgeReportsIn(parentIds);
  }

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
  List<Row> findByCulvertReportsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findByCulvertReports(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty() ? List.of() : findByCulvertReportsIn(parentIds);
  }

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
  List<Row> findByContractualWorkReportsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findByContractualWorkReports(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty()
        ? List.of()
        : findByContractualWorkReportsIn(parentIds);
  }

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
  List<Row> findByRoadConstructionDetailsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findByRoadConstructionDetails(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty()
        ? List.of()
        : findByRoadConstructionDetailsIn(parentIds);
  }

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
  List<Row> findBySilvicultureLocationsIn(@Param("parentIds") List<Long> parentIds);

  /** Guarded entry point: an empty parent set reads as no rows, with no statement issued. */
  default List<Row> findBySilvicultureLocations(List<Long> parentIds) {
    return parentIds == null || parentIds.isEmpty()
        ? List.of()
        : findBySilvicultureLocationsIn(parentIds);
  }

  /** Maps a snapshot cost-detail row, whichever parent column the finder aliased to PARENT_ID. */
  class RowMapperImpl implements RowMapper<Row> {
    @Override
    public Row mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new Row(
          nullableInt(rs, "ILCR_COST_REPORT_DETAIL_ID"),
          nullableLong(rs, "PARENT_ID"),
          nullableInt(rs, "ILCR_REPORT_COST_ITEM_ID"),
          rs.getBigDecimal("VOLUME"),
          nullableInt(rs, "COST"),
          rs.getString("ITEM_DESCRIPTION"),
          rs.getString("COMMENTS"));
    }
  }
}
