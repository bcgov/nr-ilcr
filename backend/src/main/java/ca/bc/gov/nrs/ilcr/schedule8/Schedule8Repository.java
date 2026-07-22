package ca.bc.gov.nrs.ilcr.schedule8;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 8 (Tree to Truck) tables (AD-3, re-pinned
 * 2026-07-20): a {@code Repository} interface of explicit {@code @Query} named-param SQL returning
 * {@code @Table} record entities — no {@code JdbcClient}, no derived queries. Every derivation and the
 * transaction boundaries live in {@code Schedule8Service}.
 *
 * <p>Storage shape (delivery-DB confirmed 2026-07-22): a document is a three-level hierarchy —
 * category-{@code '8'} {@code TREE_TO_TRUCK_REPORT} pages → {@code TREE_TO_TRUCK_DETAIL_REPORT}
 * samples (by {@code TREE_TO_TRUCK_REPORT_ID}) → {@code TREE_TO_TRUCK_RATE_DETAIL} rate rows (by
 * {@code TREE_TO_TRUCK_DETAIL_REPORT_ID}). A rate row is an addition or a deduction by its cost item's
 * {@code ILCR_SUBCATEGORY_ID} (§Decision 1); the service splits them using {@link #costItemSubcategories()}.
 *
 * <p>The eight code FKs resolve to a {@code DESCRIPTION} label (§Decision 3): each {@code *Labels()}
 * default method loads its code table (via a {@code @Query} over a nested {@code @Table} code entity —
 * the ratified entity-mapping pattern, never a multi-column DTO projection) into a code→label map the
 * service applies. Following the Schedule 4 pattern, {@code @Query} returns entities/scalars only.
 */
public interface Schedule8Repository extends Repository<TreeToTruckReportEntity, Integer> {

  String SCHEDULE_8_CATEGORY = "8";

  // -------------------------------------------------------------------------------------------------
  // Hierarchy reads — flat entity lists the service assembles into pages → samples → rate rows.
  // -------------------------------------------------------------------------------------------------

  /** The category-{@code '8'} page rows for a mill/year, ordered by id (legacy page order). */
  @Query("""
      SELECT TREE_TO_TRUCK_REPORT_ID, ILCR_SUPPORT_CENTRE_CODE, ILCR_FOREST_REGION_CODE, BEC_ZONE_CODE,
             TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, CUTTING_PERMIT_NUMBER, HARVEST_LICENSE_NUMBER,
             DIVISION_LOCATION, CONTACT_NAME, CONTACT_PHONE_NUMBER, COMMENTS, REVISION_COUNT
        FROM THE.TREE_TO_TRUCK_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '8'
       ORDER BY TREE_TO_TRUCK_REPORT_ID
      """)
  List<TreeToTruckReportEntity> findPages(@Param("millId") long millId, @Param("year") int year);

  /** Every sample under the mill/year's category-{@code '8'} pages, ordered by page then sample id. */
  @Query("""
      SELECT s.TREE_TO_TRUCK_DETAIL_REPORT_ID, s.TREE_TO_TRUCK_REPORT_ID, s.CONTRACTOR_ID, s.CUT_BLOCK,
             s.GROUND_BASE_PCT, s.GRAPPLE_PCT, s.SKYLINE_PCT, s.HIGHLEAD_PCT, s.HELICOPTER_PCT,
             s.OTHER_SKIDDING_PCT, s.SKYLINE_SLOPE_DISTANCE, s.SKYLINE_SUPPORT_NUMBER,
             s.SUPPORT_AVERAGE_DISTANCE, s.CYCLE_TIME, s.DISTANCE, s.WATER_DUMP_DESTINATION_IND,
             s.UPHILL_DIRECTION_IND, s.ILCR_SKID_TYPE_CODE, s.CONIFEROUS_VOLUME, s.DECIDUOUS_VOLUME,
             s.ORIGINAL_TREE_TO_TRUCK_RATE, s.REVISION_COUNT
        FROM THE.TREE_TO_TRUCK_DETAIL_REPORT s
        JOIN THE.TREE_TO_TRUCK_REPORT p
          ON p.TREE_TO_TRUCK_REPORT_ID = s.TREE_TO_TRUCK_REPORT_ID
       WHERE p.ILCR_MILL_ID = :millId
         AND p.REPORT_YEAR = :year
         AND p.ILCR_CATEGORY_ID = '8'
       ORDER BY s.TREE_TO_TRUCK_REPORT_ID, s.TREE_TO_TRUCK_DETAIL_REPORT_ID
      """)
  List<TreeToTruckDetailReportEntity> findSamples(
      @Param("millId") long millId, @Param("year") int year);

  /** Every rate row under the mill/year's category-{@code '8'} samples, ordered by sample then id. */
  @Query("""
      SELECT r.TREE_TO_TRUCK_RATE_DETAIL_ID, r.TREE_TO_TRUCK_DETAIL_REPORT_ID, r.ILCR_RATE_COST_TYPE_CODE,
             r.ILCR_REPORT_COST_ITEM_ID, r.ITEM_DESCRIPTION, r.COSTING_RATE, r.REVISION_COUNT
        FROM THE.TREE_TO_TRUCK_RATE_DETAIL r
        JOIN THE.TREE_TO_TRUCK_DETAIL_REPORT s
          ON s.TREE_TO_TRUCK_DETAIL_REPORT_ID = r.TREE_TO_TRUCK_DETAIL_REPORT_ID
        JOIN THE.TREE_TO_TRUCK_REPORT p
          ON p.TREE_TO_TRUCK_REPORT_ID = s.TREE_TO_TRUCK_REPORT_ID
       WHERE p.ILCR_MILL_ID = :millId
         AND p.REPORT_YEAR = :year
         AND p.ILCR_CATEGORY_ID = '8'
       ORDER BY r.TREE_TO_TRUCK_DETAIL_REPORT_ID, r.TREE_TO_TRUCK_RATE_DETAIL_ID
      """)
  List<TreeToTruckRateDetailEntity> findRateRows(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year — NOT
   * the silviculture track (AD-9). Empty when there is no report-status row.
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  // -------------------------------------------------------------------------------------------------
  // Addition/deduction split (§Decision 1) — cost item id → its ILCR_SUBCATEGORY_ID.
  // -------------------------------------------------------------------------------------------------

  /** Minimal {@code ILCR_REPORT_COST_ITEM} projection: a category-{@code '8'} item and its subcategory. */
  @Table(name = "ILCR_REPORT_COST_ITEM", schema = "THE")
  record CostItemRow(
      @Id @Column("ILCR_REPORT_COST_ITEM_ID") Integer id,
      @Column("ILCR_SUBCATEGORY_ID") String subcategoryId) {
  }

  @Query("""
      SELECT ILCR_REPORT_COST_ITEM_ID, ILCR_SUBCATEGORY_ID
        FROM THE.ILCR_REPORT_COST_ITEM
       WHERE ILCR_CATEGORY_ID = '8'
      """)
  List<CostItemRow> findCategory8CostItems();

  /** Cost-item id → subcategory id for category {@code '8'} — the addition/deduction discriminator. */
  default Map<Integer, String> costItemSubcategories() {
    Map<Integer, String> byId = new LinkedHashMap<>();
    for (CostItemRow row : findCategory8CostItems()) {
      byId.put(row.id(), row.subcategoryId());
    }
    return byId;
  }

  // -------------------------------------------------------------------------------------------------
  // Code-table label lookups (§Decision 3). Each nested @Table entity is <CODE>, DESCRIPTION; the
  // default methods load the table into a code→label map. Entity mapping (not DTO projection) keeps
  // this on the ratified Schedule 4 pattern.
  // -------------------------------------------------------------------------------------------------

  /** A resolved code→label pair; every code entity below exposes it so one adapter builds the map. */
  interface CodeLabel {
    String code();

    String description();
  }

  private static Map<String, String> asLabelMap(List<? extends CodeLabel> rows) {
    Map<String, String> byCode = new LinkedHashMap<>();
    for (CodeLabel row : rows) {
      byCode.put(row.code(), row.description());
    }
    return byCode;
  }

  @Table(name = "ILCR_SUPPORT_CENTRE_CODE", schema = "THE")
  record SupportCentreCode(
      @Id @Column("ILCR_SUPPORT_CENTRE_CODE") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_SUPPORT_CENTRE_CODE, DESCRIPTION FROM THE.ILCR_SUPPORT_CENTRE_CODE")
  List<SupportCentreCode> findSupportCentreCodes();

  default Map<String, String> supportCentreLabels() {
    return asLabelMap(findSupportCentreCodes());
  }

  @Table(name = "ILCR_FOREST_REGION_CODE", schema = "THE")
  record ForestRegionCode(
      @Id @Column("ILCR_FOREST_REGION_CODE") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_FOREST_REGION_CODE, DESCRIPTION FROM THE.ILCR_FOREST_REGION_CODE")
  List<ForestRegionCode> findForestRegionCodes();

  default Map<String, String> regionLabels() {
    return asLabelMap(findForestRegionCodes());
  }

  @Table(name = "BEC_ZONE_CODE", schema = "THE")
  record BecZoneCode(
      @Id @Column("BEC_ZONE_CODE") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT BEC_ZONE_CODE, DESCRIPTION FROM THE.BEC_ZONE_CODE")
  List<BecZoneCode> findBecZoneCodes();

  default Map<String, String> becZoneLabels() {
    return asLabelMap(findBecZoneCodes());
  }

  @Table(name = "TSA_NUMBER_CODE", schema = "THE")
  record TsaNumberCode(
      @Id @Column("TSA_NUMBER") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT TSA_NUMBER, DESCRIPTION FROM THE.TSA_NUMBER_CODE")
  List<TsaNumberCode> findTsaNumberCodes();

  default Map<String, String> tsaNumberLabels() {
    return asLabelMap(findTsaNumberCodes());
  }

  @Table(name = "TSB_NUMBER_CODE", schema = "THE")
  record TsbNumberCode(
      @Id @Column("TSB_NUMBER_CODE") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT TSB_NUMBER_CODE, DESCRIPTION FROM THE.TSB_NUMBER_CODE")
  List<TsbNumberCode> findTsbNumberCodes();

  default Map<String, String> supplyBlockLabels() {
    return asLabelMap(findTsbNumberCodes());
  }

  @Table(name = "TFL_NUMBER_CODE", schema = "THE")
  record TflNumberCode(
      @Id @Column("TFL_NUMBER") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT TFL_NUMBER, DESCRIPTION FROM THE.TFL_NUMBER_CODE")
  List<TflNumberCode> findTflNumberCodes();

  default Map<String, String> tflNumberLabels() {
    return asLabelMap(findTflNumberCodes());
  }

  @Table(name = "ILCR_SKID_TYPE_CODE", schema = "THE")
  record SkidTypeCode(
      @Id @Column("ILCR_SKID_TYPE_CODE") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_SKID_TYPE_CODE, DESCRIPTION FROM THE.ILCR_SKID_TYPE_CODE")
  List<SkidTypeCode> findSkidTypeCodes();

  default Map<String, String> skidTypeLabels() {
    return asLabelMap(findSkidTypeCodes());
  }

  @Table(name = "ILCR_RATE_COST_TYPE_CODE", schema = "THE")
  record RateCostTypeCode(
      @Id @Column("ILCR_RATE_COST_TYPE_CODE") String code, @Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_RATE_COST_TYPE_CODE, DESCRIPTION FROM THE.ILCR_RATE_COST_TYPE_CODE")
  List<RateCostTypeCode> findRateCostTypeCodes();

  default Map<String, String> costTypeLabels() {
    return asLabelMap(findRateCostTypeCodes());
  }
}
