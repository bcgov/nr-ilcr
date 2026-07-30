package ca.bc.gov.nrs.ilcr.schedule11;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC reads for the Schedule 11 locations document (AD-3: repository interface +
 * {@code @Table} record entities + explicit {@code @Query} named-parameter SQL, {@code THE}-
 * qualified; no derived queries, no {@code CrudRepository.save}, no {@code JdbcClient}). SQL only —
 * all derivations live in {@link Schedule11Service}.
 *
 * <p>Both reads return LISTS (duplicate-row safety: no {@code .optional()} single-row expectations
 * — a query that can legally return multiples must never 500 on duplicates). The category literal
 * {@code '11'} is legacy {@code Constant.CATEGORIES.Schedule11}; items {@code 23}/{@code 24} are
 * {@code REPORT_COST_ITEMS.Schedule11_1_Planned}/{@code _Actual}.
 */
public interface Schedule11Repository extends Repository<SilvicultureLocationEntity, Long> {

  /**
   * The Schedule 11 locations for a mill/year, ordered by {@code BASIC_SILVICULTURE_REPORT_ID}
   * ascending (legacy {@code Schedule11DAO.getBasicSilvicultureReports} HQL order). LEFT join to
   * the BEC catalogue so a dangling catalogue id (no FK in delivery — AC9) degrades to null label
   * parts instead of dropping the row.
   *
   * @param year the reporting year
   * @param millId the mill id
   * @return the location rows in serving order; empty when the mill/year has none (a valid state)
   */
  @Query("""
      SELECT b.BASIC_SILVICULTURE_REPORT_ID, b.LOCATION, b.ENHANCED_IND,
             b.BECBIOGEOCLIMATIC_CATALOGUE_ID, c.BEC_ZONE_CODE, c.SUBZONE, c.VARIANT, c.PHASE,
             b.REFORESTED_NET_AREA, b.COMMENTS, b.REVISION_COUNT
        FROM THE.BASIC_SILVICULTURE_REPORT b
        LEFT JOIN THE.BIOGEOCLIMATIC_CATALOGUE c
          ON c.BIOGEOCLIMATIC_CATALOGUE_ID = b.BECBIOGEOCLIMATIC_CATALOGUE_ID
       WHERE b.REPORT_YEAR = :year
         AND b.ILCR_MILL_ID = :millId
         AND b.ILCR_CATEGORY_ID = '11'
       ORDER BY b.BASIC_SILVICULTURE_REPORT_ID
      """)
  List<SilvicultureLocationEntity> findLocations(
      @Param("year") int year, @Param("millId") long millId);

  /**
   * The Actual/Planned cost rows (items 24/23) for every location of a mill/year, in one read.
   * Filtered to 23/24 here AND re-checked in the service's unpacking loop (legacy
   * {@code getSilvicultureReport} assigns only those two) — out-of-scope items attached to a
   * location must reach no figure.
   *
   * @param year the reporting year
   * @param millId the mill id
   * @return the cost rows; a location may have 0, 1, or both items (real data: 0 is dominant)
   */
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.BASIC_SILVICULTURE_REPORT_ID,
             d.ILCR_REPORT_COST_ITEM_ID, d.COST
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.BASIC_SILVICULTURE_REPORT b
          ON b.BASIC_SILVICULTURE_REPORT_ID = d.BASIC_SILVICULTURE_REPORT_ID
       WHERE b.REPORT_YEAR = :year
         AND b.ILCR_MILL_ID = :millId
         AND b.ILCR_CATEGORY_ID = '11'
         AND d.ILCR_REPORT_COST_ITEM_ID IN (23, 24)
       ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<SilvicultureCostEntity> findCostDetails(
      @Param("year") int year, @Param("millId") long millId);

  // ===============================================================================================
  // Write path (Story 25.2) — AD-3 dumb SQL; transaction boundary, Draft gate, cost upsert/clear,
  // and 404-vs-409 disambiguation live in Schedule11Service. All writes are THE-qualified and
  // scope every UPDATE/DELETE to (id, ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID='11') so one
  // mill's write can never touch another's rows.
  // ===============================================================================================

  /** True iff the BEC id resolves to a catalogue row (force-selection backend enforcement, S16). */
  @Query("""
      SELECT COUNT(*)
        FROM THE.BIOGEOCLIMATIC_CATALOGUE
       WHERE BIOGEOCLIMATIC_CATALOGUE_ID = :biogeoId
      """)
  int countBiogeo(@Param("biogeoId") long biogeoId);

  /**
   * Type-ahead search over the GLOBAL BEC catalogue for the forced-selection field (BR-09, S16;
   * legacy {@code Schedule11MB.completeBiogeoSubzoneVariant}, {@code minQueryLength=1}). Matches
   * {@code :term} case-insensitively as a PREFIX of the concatenated
   * zone+subzone+variant+phase label — the SAME concat the served location rows use, so the WHERE
   * and ORDER BY agree with the label the service derives in Java. Oracle {@code ||} treats a NULL
   * variant/phase as {@code ""}, mirroring {@code getBiogeoSubZoneVariantPase()}. Ordered by that
   * label and capped so the type-ahead payload stays bounded; the blank/whitespace short-circuit
   * (empty result, no query) and the {@code LIKE}-metacharacter escaping (a user-typed {@code %}/
   * {@code _} must match LITERALLY, not as a wildcard — legacy {@code String.startsWith}) live in
   * {@link Schedule11Service}, paired with the {@code ESCAPE '\'} clause here.
   *
   * @param term the already-trimmed, non-blank, LIKE-escaped search prefix
   * @return the matching catalogue rows, label-ordered, at most 50
   */
  @Query("""
      SELECT c.BIOGEOCLIMATIC_CATALOGUE_ID, c.BEC_ZONE_CODE, c.SUBZONE, c.VARIANT, c.PHASE
        FROM THE.BIOGEOCLIMATIC_CATALOGUE c
       WHERE UPPER(c.BEC_ZONE_CODE || c.SUBZONE || c.VARIANT || c.PHASE)
             LIKE UPPER(:term) || '%' ESCAPE '\\'
       ORDER BY c.BEC_ZONE_CODE || c.SUBZONE || c.VARIANT || c.PHASE
       FETCH FIRST 50 ROWS ONLY
      """)
  List<BiogeoclimaticCatalogueEntity> searchBiogeoCatalogue(@Param("term") String term);

  /** True iff a Schedule 11 location with this id exists under the mill/year (404-vs-409, AC7). */
  @Query("""
      SELECT COUNT(*)
        FROM THE.BASIC_SILVICULTURE_REPORT
       WHERE BASIC_SILVICULTURE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '11'
      """)
  int countLocation(@Param("id") long id, @Param("millId") long millId, @Param("year") int year);

  /** The next location PK from the delivery sequence (BSR PK generator). */
  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  long nextLocationId();

  /** The next cost-detail PK from the delivery sequence. */
  @Query("SELECT THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL FROM DUAL")
  long nextCostDetailId();

  /**
   * Insert one Schedule 11 location (category {@code '11'}, {@code REVISION_COUNT = 0}, audit
   * {@code ENTRY_*}/{@code UPDATE_*} set — DB triggers own the {@code _AUD} rows). The PK is supplied
   * from {@link #nextLocationId()} so the service can key the cost-child inserts to it.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.BASIC_SILVICULTURE_REPORT
          (BASIC_SILVICULTURE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION,
           BECBIOGEOCLIMATIC_CATALOGUE_ID, REFORESTED_NET_AREA, ENHANCED_IND, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :year, :millId, '11', :location, :biogeoId, :netArea, :enhancedInd, :comments,
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertLocation(
      @Param("id") long id, @Param("millId") long millId, @Param("year") int year,
      @Param("location") String location, @Param("biogeoId") long biogeoId,
      @Param("netArea") BigDecimal netArea, @Param("enhancedInd") String enhancedInd,
      @Param("comments") String comments, @Param("user") String user);

  /**
   * Optimistic-lock update of one location (AR11): sets the entered fields, bumps
   * {@code REVISION_COUNT}, and stamps {@code UPDATE_*} ONLY when the stored revision still matches
   * {@code expectedRevision} and the row belongs to this mill/year.
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the id is absent (→ 404) OR the
   *     revision is stale (→ 409). The service disambiguates via {@link #countLocation}.
   */
  @Modifying
  @Query("""
      UPDATE THE.BASIC_SILVICULTURE_REPORT
         SET LOCATION = :location,
             BECBIOGEOCLIMATIC_CATALOGUE_ID = :biogeoId,
             REFORESTED_NET_AREA = :netArea,
             ENHANCED_IND = :enhancedInd,
             COMMENTS = :comments,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE BASIC_SILVICULTURE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '11'
         AND REVISION_COUNT = :expectedRevision
      """)
  int updateLocation(
      @Param("id") long id, @Param("millId") long millId, @Param("year") int year,
      @Param("expectedRevision") int expectedRevision, @Param("location") String location,
      @Param("biogeoId") long biogeoId, @Param("netArea") BigDecimal netArea,
      @Param("enhancedInd") String enhancedInd, @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Delete one location, scoped to the mill/year (never another mill's row). The service runs this
   * FIRST — its 0-rows result is the ownership/404 check — and only then cascades the cost children
   * ({@link #deleteCostsForLocation}, which is scoped by location id alone; delivery has no FK
   * cascade, AC9).
   *
   * @return rows affected — {@code 0} when the id is not a Schedule 11 row under this mill/year (→ 404)
   */
  @Modifying
  @Query("""
      DELETE FROM THE.BASIC_SILVICULTURE_REPORT
       WHERE BASIC_SILVICULTURE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '11'
      """)
  int deleteLocation(@Param("id") long id, @Param("millId") long millId, @Param("year") int year);

  /**
   * Delete EVERY cost child of a location — explicit cascade (no FK in delivery). Deliberately NOT
   * filtered to items 23/24: real data attaches other items to a location (V20 models an item-19
   * row), and delivery has no FK to block the delete — a scoped delete would strand those rows on a
   * dangling {@code BASIC_SILVICULTURE_REPORT_ID} forever (legacy removes the whole row family).
   * The service enforces mill/year ownership via {@link #deleteLocation} BEFORE calling this.
   */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE BASIC_SILVICULTURE_REPORT_ID = :locationId
      """)
  void deleteCostsForLocation(@Param("locationId") long locationId);

  /**
   * Upsert one cost child (item 23 Planned / 24 Actual) for a location: update-in-place when the row
   * exists (audit continuity — no delete/re-insert churn), else insert with a fresh sequence PK.
   * Cost rows carry a NULL {@code ILCR_REPORT_SUMMARY_ID} (a list schedule has no summary) and null
   * {@code VOLUME}/{@code ITEM_DESCRIPTION} (unused by Schedule 11).
   */
  default void upsertCost(long locationId, int costItemId, Integer cost, String user) {
    int updated = updateCost(locationId, costItemId, cost, user);
    if (updated == 0) {
      insertCost(nextCostDetailId(), locationId, costItemId, cost, user);
    }
  }

  /** Update-in-place half of {@link #upsertCost}; {@code 0} rows when the item row is absent. */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE BASIC_SILVICULTURE_REPORT_ID = :locationId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemId
      """)
  int updateCost(
      @Param("locationId") long locationId, @Param("costItemId") int costItemId,
      @Param("cost") Integer cost, @Param("user") String user);

  /**
   * Insert half of {@link #upsertCost} (summary id NULL; PK from the sequence). Stamps
   * {@code REVISION_COUNT = 0} and BOTH {@code ENTRY_*}/{@code UPDATE_*} like {@link #insertLocation}:
   * all three are NOT NULL in delivery with no defaults, and the {@code ILCR_CRDA_B_I_U} audit
   * trigger propagates them into the {@code _AUD} shadow row — omitting them fails the insert
   * (ORA-01400/ORA-20001, verified against the seeded real-data image 2026-07-29).
   */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, BASIC_SILVICULTURE_REPORT_ID,
           ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, NULL, :locationId, :costItemId, NULL, :cost, NULL, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCost(
      @Param("id") long id, @Param("locationId") long locationId,
      @Param("costItemId") int costItemId, @Param("cost") Integer cost, @Param("user") String user);

  /** Delete one cost child (used to CLEAR a cost that the edit set to null — clear semantics). */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE BASIC_SILVICULTURE_REPORT_ID = :locationId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemId
      """)
  void deleteCost(@Param("locationId") long locationId, @Param("costItemId") int costItemId);
}
