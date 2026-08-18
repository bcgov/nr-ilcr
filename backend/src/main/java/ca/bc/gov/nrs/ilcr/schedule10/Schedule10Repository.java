package ca.bc.gov.nrs.ilcr.schedule10;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
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

  // ===============================================================================================
  // WRITES
  //
  // Two rules govern everything below.
  //
  // AUDIT COLUMNS. REVISION_COUNT and both ENTRY_*/UPDATE_* pairs are NOT NULL with no defaults
  // on both Schedule 10 tables, in delivery and in the test schema, and the delivery triggers
  // only feed the _AUD shadows — they populate nothing. Every INSERT therefore supplies all five
  // explicitly, and every UPDATE re-stamps UPDATE_* while leaving ENTRY_* untouched. An omission
  // is ORA-01400 locally, exactly as it would be in delivery.
  //
  // MILL SCOPE. There is no shared mill-scope guard in this application, so the SQL predicate IS
  // the ownership check: every UPDATE and DELETE carries mill + year + category, and a zero-row
  // result is how an IDOR attempt or an unknown id is detected. The detail table has no
  // ILCR_MILL_ID of its own, so its statements reach that scope through an EXISTS on the parent
  // page.
  // ===============================================================================================

  /** One derived moisture-code pair, as the surviving cross-reference offers it. */
  record MoistureCodePair(String asmCode, String soilMoistureCode) {
  }

  /**
   * The moisture codes offered for a BEC classification and RSMR class, through the surviving
   * cross-reference.
   *
   * <p>This one query does double duty, which is why there is no separate offerable-BEC probe: the
   * join through {@code ILCR_BEC_SOIL_MOISTUR_XREF} is exactly the gate that decides which
   * catalogue rows may be chosen at all, so a BEC id outside the offerable set yields zero rows —
   * the same outcome as an offerable id with no pair for the given RSMR class, and both are a 400.
   *
   * <p>The service picks from the result: exactly one candidate is used as-is, several are resolved
   * by a documented deterministic rule (legacy left that choice to a field that no longer exists),
   * and none is an error. Ordering here is only for stable results; the domain tie-break lives in
   * the service where it is unit-testable.
   *
   * <p>{@code ACTIVE_IND} is honoured on both tables. Every row carries {@code 'Y'} today, but the
   * columns exist and a de-activated row must not be offered.
   *
   * @param becId the BEC classification id
   * @param rsmrClass the RSMR class code
   * @return the candidate pairs, empty when the combination is not offered
   */
  @Query("""
      SELECT DISTINCT x.RELATIVE_SOIL_MOISTUR_RGM_CODE AS asm_code,
             x.ILCR_SOIL_MOISTURE_CODE                 AS soil_moisture_code
        FROM THE.ILCR_SOIL_MOISTURE_XREF x
        JOIN THE.ILCR_BEC_SOIL_MOISTUR_XREF b
          ON b.SOIL_MOISTURE_XREF_ID = x.SOIL_MOISTURE_XREF_ID
       WHERE b.BIOGEOCLIMATIC_CATALOGUE_ID = :becId
         AND x.REL_SOIL_MOIST_RGM_CLS_CODE = :rsmrClass
         AND x.ACTIVE_IND = 'Y'
         AND b.ACTIVE_IND = 'Y'
       ORDER BY x.RELATIVE_SOIL_MOISTUR_RGM_CODE, x.ILCR_SOIL_MOISTURE_CODE
      """)
  List<MoistureCodePair> findMoistureCodes(
      @Param("becId") int becId, @Param("rsmrClass") String rsmrClass);

  /**
   * Next construction-page id, from the sequence legacy declares for this table.
   *
   * <p>Deliberately NOT the shared {@code ILCR_REPORT_COMMON_SEQ}: repointing the master at a
   * cross-schedule sequence would diverge from legacy on something every other report family draws
   * from. This sequence sits un-advanced in the seeded delivery image because Schedule 10's rows
   * were bulk-loaded rather than written through the application, which is an environment defect to
   * be corrected by advancing the sequence past the current maximum id — not a reason to change the
   * code.
   */
  @Query("SELECT THE.ROAD_CONSTRUCTION_REPORT_SEQ.NEXTVAL FROM DUAL")
  int nextPageId();

  /** Next road-detail id, from the shared report sequence legacy declares for the detail table. */
  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  int nextRoadDetailId();

  /** Next cost-line id. */
  @Query("SELECT THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL FROM DUAL")
  int nextCostDetailId();

  /**
   * Insert one construction page (category {@code '10'}, {@code REVISION_COUNT = 0}, all four audit
   * columns stamped). The entered columns arrive as one entity whose id is the PK the service took
   * from {@link #nextPageId()}, so it can key the child writes to it.
   *
   * <p>{@code CONSTRUCTION_DATE} is deliberately not written: the legacy converter never sets it,
   * and all 52 real delivery pages hold NULL.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.ROAD_CONSTRUCTION_REPRT
          (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE,
           TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:#{#page.roadConstructionReprtId()}, :year, :millId, '10',
           :#{#page.constructionPeriod()}, :#{#page.constructionDivisionName()},
           :#{#page.ilcrForestRegionCode()},
           :#{#page.tsbNumberCode()}, :#{#page.tsaNumber()}, :#{#page.tflNumberCode()},
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertPage(
      @Param("page") RoadConstructionReportEntity page, @Param("millId") long millId,
      @Param("year") int year, @Param("user") String user);

  /**
   * Optimistic-lock update of one page: sets the entered fields, bumps {@code REVISION_COUNT} and
   * stamps {@code UPDATE_*}, only while the stored revision still matches and the row belongs to
   * this mill, year and category.
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the id is absent or foreign (→
   *     404) OR the revision is stale (→ 409), which the service disambiguates via {@link
   *     #countPage}
   */
  @Modifying
  @Query("""
      UPDATE THE.ROAD_CONSTRUCTION_REPRT
         SET CONSTRUCTION_PERIOD = :#{#page.constructionPeriod()},
             CONSTRUCTION_DIVISION_NAME = :#{#page.constructionDivisionName()},
             ILCR_FOREST_REGION_CODE = :#{#page.ilcrForestRegionCode()},
             TSB_NUMBER_CODE = :#{#page.tsbNumberCode()},
             TSA_NUMBER = :#{#page.tsaNumber()},
             TFL_NUMBER_CODE = :#{#page.tflNumberCode()},
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_CONSTRUCTION_REPRT_ID = :#{#page.roadConstructionReprtId()}
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '10'
         AND REVISION_COUNT = :expectedRevision
      """)
  int updatePage(
      @Param("page") RoadConstructionReportEntity page, @Param("millId") long millId,
      @Param("year") int year, @Param("expectedRevision") int expectedRevision,
      @Param("user") String user);

  /** Existence probe scoped to mill/year/category — the 404-versus-409 disambiguator for a page. */
  @Query("""
      SELECT COUNT(*)
        FROM THE.ROAD_CONSTRUCTION_REPRT
       WHERE ROAD_CONSTRUCTION_REPRT_ID = :pageId
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '10'
      """)
  int countPage(@Param("pageId") int pageId, @Param("millId") long millId, @Param("year") int year);

  /**
   * Insert one road detail. All five NOT NULL audit/revision columns are supplied, as are the two
   * moisture codes.
   *
   * <p><strong>The two moisture codes are parameters, not entity fields, on purpose.</strong> The
   * entity models the read shape, which omits them because the business departures removed those
   * fields from the UI and the API. Their columns are nevertheless NOT NULL with enabled foreign
   * keys, so the write must supply values — and it supplies DERIVED ones, resolved from the BEC
   * classification and RSMR class through {@link #findMoistureCodes}. Passing them separately keeps
   * the entity faithful to the departure while letting the insert satisfy the schema.
   *
   * <p>{@code BOULDER_AREA_PCT} is likewise removed and simply never written; it is nullable.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL
          (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME,
           SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, RIPPABLE_ROCK_PCT, SOLID_ROCK_PCT,
           COARSE_MATERIAL_PCT, BECBIOGEO_CATALOGUE_ID, FINE_MATERIAL_PCT, ORGANIC_MATERIAL_PCT,
           SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, END_HAUL_DISTANCE, END_HAUL_VOLUME,
           OVERLAND_DISTANCE, OVERLAND_VOLUME, ILCR_ROAD_BALLAST_METHOD_CODE,
           SUB_GRADE_SURFACE_WIDTH, ILCR_ROAD_BALLAST_MATERL_CODE, STABILIZING_LENGTH,
           STABILIZING_SURFACE_WIDTH, STABILIZING_DEPTH, STABILIZING_DISTANCE_TO_SOURCE,
           REL_SOIL_MOIST_RGM_CLS_CODE, COMMENTS,
           ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:#{#detail.roadConstructionReprtDtlId()}, :#{#detail.roadConstructionReprtId()},
           :#{#detail.roadName()}, :#{#detail.sideSlopePct()}, :#{#detail.ilcrRoadLifetimeCode()},
           :#{#detail.rippableRockPct()}, :#{#detail.solidRockPct()},
           :#{#detail.coarseMaterialPct()}, :#{#detail.becbiogeoCatalogueId()},
           :#{#detail.fineMaterialPct()}, :#{#detail.organicMaterialPct()},
           :#{#detail.subGradeLength()}, :#{#detail.detailEngineeringCostInd()},
           :#{#detail.endHaulDistance()}, :#{#detail.endHaulVolume()},
           :#{#detail.overlandDistance()}, :#{#detail.overlandVolume()},
           :#{#detail.ilcrRoadBallastMethodCode()}, :#{#detail.subGradeSurfaceWidth()},
           :#{#detail.ilcrRoadBallastMaterlCode()}, :#{#detail.stabilizingLength()},
           :#{#detail.stabilizingSurfaceWidth()}, :#{#detail.stabilizingDepth()},
           :#{#detail.stabilizingDistanceToSource()}, :#{#detail.relSoilMoistRgmClsCode()},
           :#{#detail.comments()},
           :soilMoistureCode, :asmCode,
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertRoadDetail(
      @Param("detail") RoadConstructionReportDetailEntity detail,
      @Param("soilMoistureCode") String soilMoistureCode, @Param("asmCode") String asmCode,
      @Param("user") String user);

  /**
   * Optimistic-lock update of one road detail, scoped to its parent page AND that page's mill, year
   * and category.
   *
   * <p>The detail table has no {@code ILCR_MILL_ID}, so the mill scope arrives through the {@code
   * EXISTS} on the parent. Pinning {@code ROAD_CONSTRUCTION_REPRT_ID} as well means a detail id
   * that exists under a DIFFERENT page cannot be edited through this page's path.
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the detail is absent, foreign, or
   *     the revision is stale, which the service disambiguates via {@link #countRoadDetail}
   */
  @Modifying
  @Query("""
      UPDATE THE.ROAD_CONSTRUCTION_REPRT_DTL
         SET ROAD_NAME = :#{#detail.roadName()},
             SIDE_SLOPE_PCT = :#{#detail.sideSlopePct()},
             ILCR_ROAD_LIFETIME_CODE = :#{#detail.ilcrRoadLifetimeCode()},
             RIPPABLE_ROCK_PCT = :#{#detail.rippableRockPct()},
             SOLID_ROCK_PCT = :#{#detail.solidRockPct()},
             COARSE_MATERIAL_PCT = :#{#detail.coarseMaterialPct()},
             BECBIOGEO_CATALOGUE_ID = :#{#detail.becbiogeoCatalogueId()},
             FINE_MATERIAL_PCT = :#{#detail.fineMaterialPct()},
             ORGANIC_MATERIAL_PCT = :#{#detail.organicMaterialPct()},
             SUB_GRADE_LENGTH = :#{#detail.subGradeLength()},
             DETAIL_ENGINEERING_COST_IND = :#{#detail.detailEngineeringCostInd()},
             END_HAUL_DISTANCE = :#{#detail.endHaulDistance()},
             END_HAUL_VOLUME = :#{#detail.endHaulVolume()},
             OVERLAND_DISTANCE = :#{#detail.overlandDistance()},
             OVERLAND_VOLUME = :#{#detail.overlandVolume()},
             ILCR_ROAD_BALLAST_METHOD_CODE = :#{#detail.ilcrRoadBallastMethodCode()},
             SUB_GRADE_SURFACE_WIDTH = :#{#detail.subGradeSurfaceWidth()},
             ILCR_ROAD_BALLAST_MATERL_CODE = :#{#detail.ilcrRoadBallastMaterlCode()},
             STABILIZING_LENGTH = :#{#detail.stabilizingLength()},
             STABILIZING_SURFACE_WIDTH = :#{#detail.stabilizingSurfaceWidth()},
             STABILIZING_DEPTH = :#{#detail.stabilizingDepth()},
             STABILIZING_DISTANCE_TO_SOURCE = :#{#detail.stabilizingDistanceToSource()},
             REL_SOIL_MOIST_RGM_CLS_CODE = :#{#detail.relSoilMoistRgmClsCode()},
             COMMENTS = :#{#detail.comments()},
             ILCR_SOIL_MOISTURE_CODE = :soilMoistureCode,
             RELATIVE_SOIL_MOISTUR_RGM_CODE = :asmCode,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = :#{#detail.roadConstructionReprtDtlId()}
         AND ROAD_CONSTRUCTION_REPRT_ID = :#{#detail.roadConstructionReprtId()}
         AND REVISION_COUNT = :expectedRevision
         AND EXISTS (SELECT 1
                       FROM THE.ROAD_CONSTRUCTION_REPRT r
                      WHERE r.ROAD_CONSTRUCTION_REPRT_ID = :#{#detail.roadConstructionReprtId()}
                        AND r.ILCR_MILL_ID = :millId
                        AND r.REPORT_YEAR = :year
                        AND r.ILCR_CATEGORY_ID = '10')
      """)
  int updateRoadDetail(
      @Param("detail") RoadConstructionReportDetailEntity detail,
      @Param("soilMoistureCode") String soilMoistureCode, @Param("asmCode") String asmCode,
      @Param("millId") long millId, @Param("year") int year,
      @Param("expectedRevision") int expectedRevision, @Param("user") String user);

  /**
   * Existence probe for a road detail under a specific page and mill/year — the 404-versus-409
   * disambiguator, and the IDOR check for the detail level.
   */
  @Query("""
      SELECT COUNT(*)
        FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
        JOIN THE.ROAD_CONSTRUCTION_REPRT r
          ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
       WHERE d.ROAD_CONSTRUCTION_REPRT_DTL_ID = :roadDetailId
         AND d.ROAD_CONSTRUCTION_REPRT_ID = :pageId
         AND r.ILCR_MILL_ID = :millId
         AND r.REPORT_YEAR = :year
         AND r.ILCR_CATEGORY_ID = '10'
      """)
  int countRoadDetail(
      @Param("roadDetailId") int roadDetailId, @Param("pageId") int pageId,
      @Param("millId") long millId, @Param("year") int year);

  /**
   * Upsert one cost line for a road detail: update in place when the row exists, else insert with a
   * fresh sequence PK.
   *
   * <p>Update-in-place rather than delete-and-reinsert preserves the audit trail, and a blank cost
   * is an UPDATE to {@code COST = NULL} rather than a row delete — legacy never deletes a cost row
   * on save, and the read path treats a stored NULL exactly as it treats an absent row.
   *
   * @param roadDetailId the owning road detail
   * @param costItemId the legacy cost-item ordinal
   * @param cost the amount, or {@code null} to clear it in place
   * @param user the actor stamped into the audit columns
   * @param millId the mill the owning page must belong to
   * @param year the reporting year the owning page must belong to
   */
  default void upsertCostLine(
      int roadDetailId, int costItemId, Integer cost, String user, long millId, int year) {
    if (updateCostLine(roadDetailId, costItemId, cost, user, millId, year) == 0) {
      insertCostLine(nextCostDetailId(), roadDetailId, costItemId, cost, user);
    }
  }

  /**
   * Update-in-place half of {@link #upsertCostLine}; {@code 0} rows when the item row is absent.
   */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = :roadDetailId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemId
         AND EXISTS (SELECT 1
                       FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
                       JOIN THE.ROAD_CONSTRUCTION_REPRT r
                         ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
                      WHERE d.ROAD_CONSTRUCTION_REPRT_DTL_ID = :roadDetailId
                        AND r.ILCR_MILL_ID = :millId
                        AND r.REPORT_YEAR = :year
                        AND r.ILCR_CATEGORY_ID = '10')
      """)
  int updateCostLine(
      @Param("roadDetailId") int roadDetailId, @Param("costItemId") int costItemId,
      @Param("cost") Integer cost, @Param("user") String user, @Param("millId") long millId,
      @Param("year") int year);

  /**
   * Insert half of {@link #upsertCostLine}. Schedule 10 cost rows carry a NULL {@code
   * ILCR_REPORT_SUMMARY_ID} — there is no category-{@code '10'} summary row to hang them from — and
   * link to their road detail instead. {@code VOLUME}, {@code ITEM_DESCRIPTION} and {@code
   * COMMENTS} stay NULL: legacy writes none of them for this schedule.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ROAD_CONSTRUCTION_REPRT_DTL_ID,
           ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, NULL, :roadDetailId, :costItemId, NULL, :cost, NULL, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCostLine(
      @Param("id") int id, @Param("roadDetailId") int roadDetailId,
      @Param("costItemId") int costItemId, @Param("cost") Integer cost,
      @Param("user") String user);

  /**
   * Delete every cost line of one road detail. Runs before the detail itself: delivery's FK from
   * {@code ILCR_COST_REPORT_DETAIL} to the detail table is ENABLED with {@code NO ACTION}, so a
   * parent still holding children is rejected with {@code ORA-02292}.
   */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = :roadDetailId
         AND EXISTS (SELECT 1
                       FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
                       JOIN THE.ROAD_CONSTRUCTION_REPRT r
                         ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
                      WHERE d.ROAD_CONSTRUCTION_REPRT_DTL_ID = :roadDetailId
                        AND r.ILCR_MILL_ID = :millId
                        AND r.REPORT_YEAR = :year
                        AND r.ILCR_CATEGORY_ID = '10')
      """)
  int deleteCostsForRoadDetail(
      @Param("roadDetailId") int roadDetailId, @Param("millId") long millId,
      @Param("year") int year);

  /**
   * Delete every cost line belonging to any road detail of one page — the first step of the page
   * cascade, since the grandchildren must go before the children.
   */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID IN (
             SELECT d.ROAD_CONSTRUCTION_REPRT_DTL_ID
               FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
               JOIN THE.ROAD_CONSTRUCTION_REPRT r
                 ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID
              WHERE d.ROAD_CONSTRUCTION_REPRT_ID = :pageId
                AND r.ILCR_MILL_ID = :millId
                AND r.REPORT_YEAR = :year
                AND r.ILCR_CATEGORY_ID = '10')
      """)
  int deleteCostsForPage(
      @Param("pageId") int pageId, @Param("millId") long millId, @Param("year") int year);

  /** Delete one road detail, scoped to its parent page. Its cost lines must already be gone. */
  @Modifying
  @Query("""
      DELETE FROM THE.ROAD_CONSTRUCTION_REPRT_DTL
       WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = :roadDetailId
         AND ROAD_CONSTRUCTION_REPRT_ID = :pageId
         AND EXISTS (SELECT 1
                       FROM THE.ROAD_CONSTRUCTION_REPRT r
                      WHERE r.ROAD_CONSTRUCTION_REPRT_ID = :pageId
                        AND r.ILCR_MILL_ID = :millId
                        AND r.REPORT_YEAR = :year
                        AND r.ILCR_CATEGORY_ID = '10')
      """)
  int deleteRoadDetail(
      @Param("roadDetailId") int roadDetailId, @Param("pageId") int pageId,
      @Param("millId") long millId, @Param("year") int year);

  /** Delete every road detail of one page — the second step of the page cascade. */
  @Modifying
  @Query("""
      DELETE FROM THE.ROAD_CONSTRUCTION_REPRT_DTL
       WHERE ROAD_CONSTRUCTION_REPRT_ID = :pageId
         AND EXISTS (SELECT 1
                       FROM THE.ROAD_CONSTRUCTION_REPRT r
                      WHERE r.ROAD_CONSTRUCTION_REPRT_ID = :pageId
                        AND r.ILCR_MILL_ID = :millId
                        AND r.REPORT_YEAR = :year
                        AND r.ILCR_CATEGORY_ID = '10')
      """)
  int deleteRoadDetailsForPage(
      @Param("pageId") int pageId, @Param("millId") long millId, @Param("year") int year);

  /**
   * Delete one page, scoped to mill/year/category. Runs LAST in the cascade, after its cost lines
   * and road details.
   *
   * @return rows affected — {@code 0} when the id is not a category-{@code '10'} page under this
   *     mill/year, which the service has already answered as a 404
   */
  @Modifying
  @Query("""
      DELETE FROM THE.ROAD_CONSTRUCTION_REPRT
       WHERE ROAD_CONSTRUCTION_REPRT_ID = :pageId
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '10'
      """)
  int deletePage(
      @Param("pageId") int pageId, @Param("millId") long millId, @Param("year") int year);

  /**
   * The stored classification codes of one road detail, for the unchanged-expired-code exemption.
   */
  @Query("""
      SELECT d.ILCR_ROAD_LIFETIME_CODE        AS road_lifetime_code,
             d.ILCR_ROAD_BALLAST_METHOD_CODE  AS ballast_method_code,
             d.ILCR_ROAD_BALLAST_MATERL_CODE  AS ballast_material_code,
             d.REL_SOIL_MOIST_RGM_CLS_CODE    AS rsmr_class_code,
             d.BECBIOGEO_CATALOGUE_ID         AS bec_id,
             d.RELATIVE_SOIL_MOISTUR_RGM_CODE AS asm_code,
             d.ILCR_SOIL_MOISTURE_CODE        AS soil_moisture_code
        FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d
       WHERE d.ROAD_CONSTRUCTION_REPRT_DTL_ID = :roadDetailId
      """)
  Optional<StoredClassification> findStoredClassification(@Param("roadDetailId") int roadDetailId);

  /**
   * The classification codes a road detail already carries, plus the moisture pair derived from
   * them.
   *
   * <p>Read on every edit so an UNCHANGED classification keeps the moisture pair already stored.
   * Legacy's {@code filterMoistureCodeLists()} ({@code Schedule10MB:665-689}) rebuilds only the two
   * dropdown LISTS — it never assigns to the detail — so the stored ASM and soil-moisture codes
   * change in legacy only when the user picks new ones. Re-deriving unconditionally would rewrite
   * two NOT NULL columns the legacy print reports consume during an edit that touched neither input
   * (code review 2026-08-18).
   *
   * <p>This also delivers the unchanged-code exemption for the BEC classification: a stored id that
   * has since dropped out of the offerable xref-gated set would otherwise make its road detail
   * permanently unsaveable, because the derivation rejects a zero-candidate pair.
   */
  record StoredClassification(
      String roadLifetimeCode, String ballastMethodCode, String ballastMaterialCode,
      String rsmrClassCode, Integer becId, String asmCode, String soilMoistureCode) {
  }
}
