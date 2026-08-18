package ca.bc.gov.nrs.ilcr.schedule10;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 10 tables (AD-3): explicit
 * {@code @Query} named-param SQL plus {@code @Table} record entities — no derived queries, no
 * {@code CrudRepository}.
 *
 * <p><strong>Assembly shape: three queries, fixed, regardless of how many pages or
 * details exist.</strong>
 * The grandchild (cost lines) is joined UP to the root mill/year rather than fetched per detail
 * row,
 * so depth never multiplies round-trips. Fetching cost lines inside a per-row loop is the
 * documented
 * anti-pattern this deliberately avoids.
 *
 * <p>Storage shape (delivery-confirmed, Story 11.1 Task 1):
 * <ul>
 *   <li>Pages are {@code ROAD_CONSTRUCTION_REPRT} rows filtered on mill + year + category
 *  {@code '10'}. There is <strong>no category-{@code '10'} {@code ILCR_REPORT_SUMMARY}
 * row</strong>,
 *  so {@code trackStatus} comes straight from {@code ILCR_MILL_REPORT_STATUS} and the guard must
 *       be {@code validateMillYearActive}, never {@code validateScheduleViewable}.</li>
 *   <li>Road details hang off a page by {@code ROAD_CONSTRUCTION_REPRT_ID}.</li>
 *   <li>Costs are keyed {@code ILCR_COST_REPORT_DETAIL} rows joined by
 *  {@code ROAD_CONSTRUCTION_REPRT_DTL_ID}, carrying {@code ILCR_REPORT_SUMMARY_ID} NULL (BR-08).
 *       Delivery holds ZERO such rows today, so an empty cost set is the normal case.</li>
 * </ul>
 *
 * <p>Ordering is pinned explicitly in SQL (deviation (c)) — legacy relied on a collection
 * {@code @OrderBy} for details and the named-query order for pages.
 *
 * <p>The {@code default} methods expose service-facing records so entities never cross the service
 * boundary (AD-3).
 */
public interface Schedule10Repository extends Repository<RoadConstructionReportEntity, Integer> {

  /** One cost line for a road detail, keyed by its legacy cost-item ordinal. */
  record CostLineRow(int roadDetailId, int costItemId, BigDecimal cost) {
  }

  /** One BEC classification, as offered through the surviving BR-06 xref gate. */
  record BecClassificationRow(
      int biogeoclimaticCatalogueId, String becZoneCode, String subzone, String variant,
      String phase) {

    /**
     * The legacy display label: {@code becZoneCode + subzone + variant + phase} with nulls rendered
     * as empty strings ({@code BiogeoclimaticCatalogue.getBiogeoSubZoneVariantPase} :208-212). Note
     * legacy does NOT null-guard {@code subzone}; it is {@code NOT NULL} in delivery.
     *
     * @return the concatenated label, e.g. {@code "ICHdw1"}
     */
    String label() {
      return becZoneCode
          + subzone
          + (variant != null ? variant : "")
          + (phase != null ? phase : "");
    }
  }

  /** A code/description pair from one of the year-filtered lookup tables. */
  record CodeRow(String code, String description) {
  }

  /**
   * The 1–10 track report status for a mill/year, straight from {@code ILCR_MILL_REPORT_STATUS}.
   *
   * <p>There is no category-{@code '10'} {@code ILCR_REPORT_SUMMARY} row to read it from — only
   * categories 1, 2 and 3 have summary rows (delivery-confirmed, gate (ii)). Never reads the
   * silviculture track (AD-9).
   *
   * @param millId the mill
   * @param year the reporting year
   * @return the track status code, or empty when no context row exists
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  // -----------------------------------------------------------------------------------------
  // Query 1 of 3 — the pages.
  // -----------------------------------------------------------------------------------------

  /**
   * The category-{@code '10'} construction pages for a mill/year, ordered by id (legacy order).
   *
   * @param millId the mill
   * @param year the reporting year
   * @return the pages, empty when the mill/year has none (a valid 200 state, not an error)
   */
  @Query("""
      SELECT ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
             CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE,
             TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT
        FROM THE.ROAD_CONSTRUCTION_REPRT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '10'
       ORDER BY ROAD_CONSTRUCTION_REPRT_ID
      """)
  List<RoadConstructionReportEntity> findPages(
      @Param("millId") long millId, @Param("year") int year);

  // -----------------------------------------------------------------------------------------
  // Query 2 of 3 — every road detail for the mill/year, joined up to its page.
  // -----------------------------------------------------------------------------------------

  /**
   * All road details for a mill/year in one query, joined to their pages so the mill/year/category
   * filter applies. The service groups them by {@code roadConstructionReprtId}.
   *
   * <p>The three LD-removed columns are deliberately not selected.
   *
   * @param millId the mill
   * @param year the reporting year
   * @return the details, ordered by page then detail id
   */
  @Query("""
      SELECT d.ROAD_CONSTRUCTION_REPRT_DTL_ID, d.ROAD_CONSTRUCTION_REPRT_ID, d.ROAD_NAME,
             d.SIDE_SLOPE_PCT, d.ILCR_ROAD_LIFETIME_CODE, d.RIPPABLE_ROCK_PCT, d.SOLID_ROCK_PCT,
             d.COARSE_MATERIAL_PCT, d.BECBIOGEO_CATALOGUE_ID, d.FINE_MATERIAL_PCT,
             d.ORGANIC_MATERIAL_PCT, d.SUB_GRADE_LENGTH, d.DETAIL_ENGINEERING_COST_IND,
             d.END_HAUL_DISTANCE, d.END_HAUL_VOLUME, d.OVERLAND_DISTANCE, d.OVERLAND_VOLUME,
             d.ILCR_ROAD_BALLAST_METHOD_CODE, d.SUB_GRADE_SURFACE_WIDTH,
             d.ILCR_ROAD_BALLAST_MATERL_CODE, d.STABILIZING_LENGTH, d.STABILIZING_SURFACE_WIDTH,
             d.STABILIZING_DEPTH, d.STABILIZING_DISTANCE_TO_SOURCE, d.REL_SOIL_MOIST_RGM_CLS_CODE,
             d.COMMENTS, d.REVISION_COUNT
        FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
        JOIN THE.ROAD_CONSTRUCTION_REPRT r
          ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
       WHERE r.ILCR_MILL_ID = :millId
         AND r.REPORT_YEAR = :year
         AND r.ILCR_CATEGORY_ID = '10'
       ORDER BY d.ROAD_CONSTRUCTION_REPRT_ID, d.ROAD_CONSTRUCTION_REPRT_DTL_ID
      """)
  List<RoadConstructionReportDetailEntity> findRoadDetails(
      @Param("millId") long millId, @Param("year") int year);

  // -----------------------------------------------------------------------------------------
  // Query 3 of 3 — every cost line for the mill/year, joined up to the ROOT (two levels).
  // -----------------------------------------------------------------------------------------

  /**
   * All Schedule 10 cost lines for a mill/year, joined through the detail to the page so the
   * mill/year/category filter applies. This is the grandchild-joined-to-root shape: one query for
   * every cost line in the document, not one per road detail.
   *
   * <p>Delivery currently holds zero of these rows for Schedule 10, so an empty result is normal.
   *
   * @param millId the mill
   * @param year the reporting year
   * @return the cost lines, ordered deterministically
   */
  @Query("""
      SELECT c.ROAD_CONSTRUCTION_REPRT_DTL_ID AS road_detail_id,
             c.ILCR_REPORT_COST_ITEM_ID       AS cost_item_id,
             c.COST                           AS cost
        FROM THE.ILCR_COST_REPORT_DETAIL c
        JOIN THE.ROAD_CONSTRUCTION_REPRT_DTL d
          ON d.ROAD_CONSTRUCTION_REPRT_DTL_ID = c.ROAD_CONSTRUCTION_REPRT_DTL_ID
        JOIN THE.ROAD_CONSTRUCTION_REPRT r
          ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
       WHERE r.ILCR_MILL_ID = :millId
         AND r.REPORT_YEAR = :year
         AND r.ILCR_CATEGORY_ID = '10'
       ORDER BY c.ROAD_CONSTRUCTION_REPRT_DTL_ID, c.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostLineRow> findCostLines(@Param("millId") long millId, @Param("year") int year);

  // -----------------------------------------------------------------------------------------
  // Code lists — served in-document (there is no standalone /codes endpoint in this app).
  // Every list is year-filtered exactly as legacy LookupCache did (:77-98).
  // -----------------------------------------------------------------------------------------

  /**
   * Forest regions effective for the reporting year, PLUS any region a stored page already
   * references.
   *
   * <p>The second leg matters: the year filter would otherwise hide a code that a stored row still
   * uses, so the page would serve {@code forestRegionCode: "ROLD"} with no matching entry in its
   * own dropdown and the field would render unresolved. The FK guarantees the code exists in the
   * table; it guarantees nothing about the year-filtered list. Added at code review 2026-08-17,
   * mirroring the equivalent leg already present for BEC.
   *
   * @param millId the mill, used to find referenced codes
   * @param year the reporting year
   * @return code/description pairs, ordered by code
   */
  @Query("""
      SELECT ILCR_FOREST_REGION_CODE AS code, DESCRIPTION AS description
        FROM THE.ILCR_FOREST_REGION_CODE
       WHERE ((EFFECTIVE_DATE IS NULL
            OR EFFECTIVE_DATE <= TO_DATE(:year || '-01-01', 'YYYY-MM-DD'))
         AND (EXPIRY_DATE IS NULL
           OR EXPIRY_DATE >= TO_DATE(:year || '-01-01', 'YYYY-MM-DD')))
          OR ILCR_FOREST_REGION_CODE IN (
             SELECT r.ILCR_FOREST_REGION_CODE
               FROM THE.ROAD_CONSTRUCTION_REPRT r
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
       ORDER BY ILCR_FOREST_REGION_CODE
      """)
  List<CodeRow> findForestRegions(@Param("millId") long millId, @Param("year") int year);

  /**
   * Road lifetime (Road Type) codes effective for the reporting year.
   *
   * @param year the reporting year
   * @return code/description pairs, ordered by code
   */
  @Query("""
      SELECT ILCR_ROAD_LIFETIME_CODE AS code, DESCRIPTION AS description
        FROM THE.ILCR_ROAD_LIFETIME_CODE
       WHERE ((EFFECTIVE_DATE IS NULL
            OR EFFECTIVE_DATE <= TO_DATE(:year || '-01-01', 'YYYY-MM-DD'))
         AND (EXPIRY_DATE IS NULL
           OR EXPIRY_DATE >= TO_DATE(:year || '-01-01', 'YYYY-MM-DD')))
          OR ILCR_ROAD_LIFETIME_CODE IN (
             SELECT d.ILCR_ROAD_LIFETIME_CODE
               FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
               JOIN THE.ROAD_CONSTRUCTION_REPRT r
                 ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
       ORDER BY ILCR_ROAD_LIFETIME_CODE
      """)
  List<CodeRow> findRoadLifetimes(@Param("millId") long millId, @Param("year") int year);

  /**
   * Ballast method codes effective for the reporting year.
   *
   * @param year the reporting year
   * @return code/description pairs, ordered by code
   */
  @Query("""
      SELECT ILCR_ROAD_BALLAST_METHOD_CODE AS code, DESCRIPTION AS description
        FROM THE.ILCR_ROAD_BALLAST_METHOD_CODE
       WHERE ((EFFECTIVE_DATE IS NULL
            OR EFFECTIVE_DATE <= TO_DATE(:year || '-01-01', 'YYYY-MM-DD'))
         AND (EXPIRY_DATE IS NULL
           OR EXPIRY_DATE >= TO_DATE(:year || '-01-01', 'YYYY-MM-DD')))
          OR ILCR_ROAD_BALLAST_METHOD_CODE IN (
             SELECT d.ILCR_ROAD_BALLAST_METHOD_CODE
               FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
               JOIN THE.ROAD_CONSTRUCTION_REPRT r
                 ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
       ORDER BY ILCR_ROAD_BALLAST_METHOD_CODE
      """)
  List<CodeRow> findBallastMethods(@Param("millId") long millId, @Param("year") int year);

  /**
   * Ballast material codes effective for the reporting year.
   *
   * @param year the reporting year
   * @return code/description pairs, ordered by code
   */
  @Query("""
      SELECT ILCR_ROAD_BALLAST_MATERL_CODE AS code, DESCRIPTION AS description
        FROM THE.ILCR_ROAD_BALLAST_MATERL_CODE
       WHERE ((EFFECTIVE_DATE IS NULL
            OR EFFECTIVE_DATE <= TO_DATE(:year || '-01-01', 'YYYY-MM-DD'))
         AND (EXPIRY_DATE IS NULL
           OR EXPIRY_DATE >= TO_DATE(:year || '-01-01', 'YYYY-MM-DD')))
          OR ILCR_ROAD_BALLAST_MATERL_CODE IN (
             SELECT d.ILCR_ROAD_BALLAST_MATERL_CODE
               FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
               JOIN THE.ROAD_CONSTRUCTION_REPRT r
                 ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
       ORDER BY ILCR_ROAD_BALLAST_MATERL_CODE
      """)
  List<CodeRow> findBallastMaterials(@Param("millId") long millId, @Param("year") int year);

  /**
   * RSMR class codes effective for the reporting year. This is the ONE list legacy renders as
   * {@code "{code} - {description}"} ({@code schedule10.xhtml:762}); the composition is left to the
   * frontend, so the raw pair is served.
   *
   * @param year the reporting year
   * @return code/description pairs, ordered by code
   */
  @Query("""
      SELECT REL_SOIL_MOIST_RGM_CLS_CODE AS code, DESCRIPTION AS description
        FROM THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE
       WHERE ((EFFECTIVE_DATE IS NULL
            OR EFFECTIVE_DATE <= TO_DATE(:year || '-01-01', 'YYYY-MM-DD'))
         AND (EXPIRY_DATE IS NULL
           OR EXPIRY_DATE >= TO_DATE(:year || '-01-01', 'YYYY-MM-DD')))
          OR REL_SOIL_MOIST_RGM_CLS_CODE IN (
             SELECT d.REL_SOIL_MOIST_RGM_CLS_CODE
               FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
               JOIN THE.ROAD_CONSTRUCTION_REPRT r
                 ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
              WHERE r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
       ORDER BY REL_SOIL_MOIST_RGM_CLS_CODE
      """)
  List<CodeRow> findRsmrClasses(@Param("millId") long millId, @Param("year") int year);

  /**
   * The BEC classifications a Schedule 10 road detail may reference.
   *
   * <p><strong>Joined through {@code ILCR_BEC_SOIL_MOISTUR_XREF} on purpose.</strong> Removing ASM
   * Code and Soil Moisture Code (LD-1/LD-2) kills BR-06's runtime FILTERING of those two lists, but
   * this xref is also the join that decides which catalogue rows the BEC control may offer at all
   * ({@code BiogeoclimaticCatalogue.java:28}). That second leg survives the departures — serving
   * the
   * unfiltered catalogue instead would be an unflagged behaviour change (deviation (e)).
   *
   * <p>{@code DISTINCT} because a catalogue row can appear in the xref more than once. Legacy
   * applies no ordering here; an explicit one is added for determinism (deviation (c)).
   *
   * @return the offerable BEC classifications
   */
  @Query("""
      SELECT DISTINCT b.BIOGEOCLIMATIC_CATALOGUE_ID AS biogeoclimatic_catalogue_id,
             b.BEC_ZONE_CODE AS bec_zone_code, b.SUBZONE AS subzone,
             b.VARIANT AS variant, b.PHASE AS phase
        FROM THE.BIOGEOCLIMATIC_CATALOGUE b
        JOIN THE.ILCR_BEC_SOIL_MOISTUR_XREF x
          ON x.BIOGEOCLIMATIC_CATALOGUE_ID = b.BIOGEOCLIMATIC_CATALOGUE_ID
       ORDER BY b.BIOGEOCLIMATIC_CATALOGUE_ID
      """)
  List<BecClassificationRow> findOfferableBecClassifications();

  /**
   * Every BEC classification referenced by the mill/year's road details, whether or not the xref
   * currently offers it. A row saved before the xref changed must still render its stored
   * classification — serving only the offerable set would blank it out.
   *
   * @param millId the mill
   * @param year the reporting year
   * @return the referenced BEC classifications
   */
  @Query("""
      SELECT DISTINCT b.BIOGEOCLIMATIC_CATALOGUE_ID AS biogeoclimatic_catalogue_id,
             b.BEC_ZONE_CODE AS bec_zone_code, b.SUBZONE AS subzone,
             b.VARIANT AS variant, b.PHASE AS phase
        FROM THE.BIOGEOCLIMATIC_CATALOGUE b
        JOIN THE.ROAD_CONSTRUCTION_REPRT_DTL d
          ON d.BECBIOGEO_CATALOGUE_ID = b.BIOGEOCLIMATIC_CATALOGUE_ID
        JOIN THE.ROAD_CONSTRUCTION_REPRT r
          ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
       WHERE r.ILCR_MILL_ID = :millId
         AND r.REPORT_YEAR = :year
         AND r.ILCR_CATEGORY_ID = '10'
       ORDER BY b.BIOGEOCLIMATIC_CATALOGUE_ID
      """)
  List<BecClassificationRow> findReferencedBecClassifications(
      @Param("millId") long millId, @Param("year") int year);
}
