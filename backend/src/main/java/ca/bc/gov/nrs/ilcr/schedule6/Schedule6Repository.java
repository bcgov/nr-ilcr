package ca.bc.gov.nrs.ilcr.schedule6;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 6 tables (AD-3): explicit
 * {@code @Query} named-param SQL + {@code @Table} record entities — no derived queries, no
 * {@code CrudRepository}. SQL stays explicit because the model is a legacy-projection; every
 * derivation and (for Story 8.2) the transaction boundaries live in {@link Schedule6Service}.
 *
 * <p>Storage shape (delivery-DB confirmed, Story 8.1 Task 1): a road record is one
 * {@code ROAD_MAINTENANCE_REPORT} row (category {@code '6'}, classification stored as codes,
 * general comment in {@code COMMENTS}, own {@code REVISION_COUNT}); its cost/volume/per-record
 * comment is the single {@code ILCR_COST_REPORT_DETAIL} row for cost item {@code 69}, joined by
 * {@code ROAD_MAINTENANCE_REPORT_ID}. There is no category-{@code '6'} {@code ILCR_REPORT_SUMMARY}
 * row, so {@code trackStatus} comes straight from {@code ILCR_MILL_REPORT_STATUS}.
 *
 * <p>The public {@code default} methods expose plain service-facing records ({@link RoadRecordRow},
 * {@link CostDetailRow}); the {@code @Query} methods are the explicit SQL, so entities never cross
 * the service boundary.
 */
public interface Schedule6Repository extends Repository<RoadMaintenanceReportEntity, Integer> {

  /** The Schedule 6 cost-item id ({@code Constant.REPORT_COST_ITEMS.Schedule6_1_Cost}). */
  int SCHEDULE_6_COST_ITEM = 69;

  /** One Schedule 6 road record (a {@code ROAD_MAINTENANCE_REPORT} row); codes stored inline. */
  record RoadRecordRow(int recordId, String tsaNumber, String tsbNumberCode, String tflNumberCode,
      String generalComment, Integer revisionCount) {
  }

  /** The cost/volume/comment detail (item 69) for a road record. */
  record CostDetailRow(
      int roadMaintenanceReportId, BigDecimal volume, Integer cost, String comments) {
  }

  // ---------------------------------------------------------------------------------------------
  // Reads — @Query returns @Table entities / scalars; default methods adapt to the service records.
  // ---------------------------------------------------------------------------------------------

  /** The category-{@code '6'} road-record rows for a mill/year, ordered by id (legacy order). */
  @Query("""
      SELECT ROAD_MAINTENANCE_REPORT_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE,
             COMMENTS, REVISION_COUNT
        FROM THE.ROAD_MAINTENANCE_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '6'
       ORDER BY ROAD_MAINTENANCE_REPORT_ID
      """)
  List<RoadMaintenanceReportEntity> findRoadReportEntities(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * The road records for a mill/year (empty = the valid no-records state, not an error). Maps the
   * report entities to the service-facing {@link RoadRecordRow}.
   */
  default List<RoadRecordRow> findRoadRecords(long millId, int year) {
    return findRoadReportEntities(millId, year).stream()
        .map(e -> new RoadRecordRow(
            e.roadMaintenanceReportId(), e.tsaNumber(), e.tsbNumberCode(), e.tflNumberCode(),
            e.comments(), e.revisionCount()))
        .toList();
  }

  /**
   * The Schedule 6 cost detail rows (item {@code 69}) for a mill/year, joined to their road records
   * by {@code ROAD_MAINTENANCE_REPORT_ID} so the category/mill/year filter applies.
   */
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.ROAD_MAINTENANCE_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID,
             d.VOLUME, d.COST, d.COMMENTS
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.ROAD_MAINTENANCE_REPORT r
          ON r.ROAD_MAINTENANCE_REPORT_ID = d.ROAD_MAINTENANCE_REPORT_ID
       WHERE r.ILCR_MILL_ID = :millId
         AND r.REPORT_YEAR = :year
         AND r.ILCR_CATEGORY_ID = '6'
         AND d.ILCR_REPORT_COST_ITEM_ID = 69
       ORDER BY d.ROAD_MAINTENANCE_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostReportDetailEntity> findCostDetailEntities(
      @Param("millId") long millId, @Param("year") int year);

  /** The cost detail rows mapped to the service-facing {@link CostDetailRow}. */
  default List<CostDetailRow> findCostDetails(long millId, int year) {
    return findCostDetailEntities(millId, year).stream()
        .map(d ->
            new CostDetailRow(d.roadMaintenanceReportId(), d.volume(), d.cost(), d.comments()))
        .toList();
  }

  /**
   * The Schedules 1-10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9). Empty when there is no report-status row.
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);
}
