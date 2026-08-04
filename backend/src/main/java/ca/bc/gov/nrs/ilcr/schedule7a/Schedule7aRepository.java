package ca.bc.gov.nrs.ilcr.schedule7a;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC reads and writes for Schedule 7A (Bridge Costs) — AD-3: a {@code Repository}
 * interface of explicit {@code @Query} named-parameter SQL over {@code @Table} record entities,
 * {@code THE}-qualified; no derived queries, no {@code CrudRepository.save}, no {@code JdbcClient}.
 * SQL only — all derivations, the Draft gate, and 404-vs-409 disambiguation live in
 * {@link Schedule7aService}.
 *
 * <p>A bridge = one {@code THE.BRIDGE_REPORT} row keyed {@code (ILCR_MILL_ID, REPORT_YEAR,
 * ILCR_CATEGORY_ID = '7')}; its ten costs = {@code THE.ILCR_COST_REPORT_DETAIL} rows keyed by
 * {@code BRIDGE_REPORT_ID} + one of the fixed Schedule 7 cost items
 * ({70,71,72,73,74,75,76,79,80,81} — legacy {@code Constant.REPORT_COST_ITEMS.Schedule7_*}), with a
 * NULL {@code ILCR_REPORT_SUMMARY_ID} (list schedule). Every UPDATE/DELETE is scoped to
 * {@code (id, ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID='7')} so one mill's write can never touch
 * another's rows (IDOR guard). The five code FKs resolve to {@code THE.*_CODE} tables via the nested
 * {@code @Table} records at the foot of this interface.
 */
public interface Schedule7aRepository extends Repository<BridgeReportEntity, Long> {

  // ===============================================================================================
  // Reads (Story 12.1)
  // ===============================================================================================

  /**
   * The bridges for a mill/year, ordered by {@code BRIDGE_REPORT_ID} ascending (the legacy list
   * order; also the {@code rowCounter} order the service assigns 1..N).
   */
  @Query("""
      SELECT BRIDGE_REPORT_ID, LOCATION_NAME, BUILT_DATE, EXPECTED_BRIDGE_LIFE_SPAN, HEIGHT, LENGTH,
             DECK_WIDTH, DISTANCE_FROM_STORAGE, ILCR_BRIDGE_CNSTRCTN_TYPE_CODE,
             ILCR_BRIDGE_SUPERSTRUCTR_CODE, ILCR_DECK_CODE, ILCR_BRIDGE_ABUTMENT_TYPE_CODE,
             ILCR_BRIDGE_LOAD_RATING_CODE, COMMENTS, REVISION_COUNT
        FROM THE.BRIDGE_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
       ORDER BY BRIDGE_REPORT_ID
      """)
  List<BridgeReportEntity> findBridges(@Param("millId") long millId, @Param("year") int year);

  /**
   * Every Schedule 7 cost row (items 70-76/79-81) for all bridges of a mill/year, in one read.
   * Filtered to the ten fixed item ids here AND re-routed by id in the service — an out-of-scope
   * item attached to a bridge must reach no field.
   */
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.BRIDGE_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID, d.COST
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.BRIDGE_REPORT b ON b.BRIDGE_REPORT_ID = d.BRIDGE_REPORT_ID
       WHERE b.ILCR_MILL_ID = :millId
         AND b.REPORT_YEAR = :year
         AND b.ILCR_CATEGORY_ID = '7'
         AND d.ILCR_REPORT_COST_ITEM_ID IN (70, 71, 72, 73, 74, 75, 76, 79, 80, 81)
       ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<BridgeCostEntity> findCostDetails(@Param("millId") long millId, @Param("year") int year);

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * Schedule 7A rides this track (BR-01), NOT the silviculture track (AD-9). Empty when there is no
   * report-status row.
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  /** True iff a category-{@code '7'} bridge with this id exists under the mill/year (404-vs-409). */
  @Query("""
      SELECT COUNT(*)
        FROM THE.BRIDGE_REPORT
       WHERE BRIDGE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int countBridge(@Param("id") long id, @Param("millId") long millId, @Param("year") int year);

  /** How many bridges remain for a mill/year (drives the SUC-003 empty-schedule message on delete). */
  @Query("""
      SELECT COUNT(*)
        FROM THE.BRIDGE_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int countBridges(@Param("millId") long millId, @Param("year") int year);

  // ===============================================================================================
  // Writes (Story 12.2)
  // ===============================================================================================

  /** The next bridge PK (delivery: {@code THE.ILCR_REPORT_COMMON_SEQ}). */
  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  long nextBridgeReportId();

  /** The next cost-detail PK (delivery: {@code THE.ILCR_COST_REPORT_DETAIL_SEQ}). */
  @Query("SELECT THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL FROM DUAL")
  long nextCostDetailId();

  /**
   * Insert one bridge (category {@code '7'}, {@code REVISION_COUNT = 0}, audit {@code ENTRY_*}/
   * {@code UPDATE_*} set). The entered columns arrive as one {@link BridgeReportEntity} (its
   * {@code bridgeReportId} is the PK supplied from {@link #nextBridgeReportId()} so the service can
   * key the cost-child inserts to it); {@code millId}/{@code year}/{@code user} are the context.
   * The bridge columns bind by SpEL accessor so the write mirrors the {@code @Table} record shape
   * rather than a flat parameter list.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.BRIDGE_REPORT
          (BRIDGE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_NAME, BUILT_DATE,
           EXPECTED_BRIDGE_LIFE_SPAN, HEIGHT, LENGTH, DECK_WIDTH, DISTANCE_FROM_STORAGE,
           ILCR_BRIDGE_CNSTRCTN_TYPE_CODE, ILCR_BRIDGE_SUPERSTRUCTR_CODE, ILCR_DECK_CODE,
           ILCR_BRIDGE_ABUTMENT_TYPE_CODE, ILCR_BRIDGE_LOAD_RATING_CODE, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:#{#bridge.bridgeReportId()}, :year, :millId, '7', :#{#bridge.locationName()},
           :#{#bridge.builtDate()}, :#{#bridge.lifeSpan()}, :#{#bridge.abutmentHeight()},
           :#{#bridge.length()}, :#{#bridge.deckWidth()}, :#{#bridge.distance()},
           :#{#bridge.constructionTypeCode()}, :#{#bridge.superstructureTypeCode()},
           :#{#bridge.deckTypeCode()}, :#{#bridge.abutmentTypeCode()},
           :#{#bridge.loadRatingCode()}, :#{#bridge.comments()},
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertBridge(
      @Param("bridge") BridgeReportEntity bridge, @Param("millId") long millId,
      @Param("year") int year, @Param("user") String user);

  /**
   * Optimistic-lock update of one bridge: sets the entered fields, bumps {@code REVISION_COUNT}, and
   * stamps {@code UPDATE_*} ONLY when the stored revision still matches {@code expectedRevision} and
   * the row belongs to this mill/year and category. The entered columns arrive as one
   * {@link BridgeReportEntity} ({@code bridgeReportId} is the row to correct), bound by SpEL
   * accessor; {@code millId}/{@code year}/{@code expectedRevision}/{@code user} are the context and
   * lock token.
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the id is absent (→ 404) OR the
   *     revision is stale (→ 409). The service disambiguates via {@link #countBridge}.
   */
  @Modifying
  @Query("""
      UPDATE THE.BRIDGE_REPORT
         SET LOCATION_NAME = :#{#bridge.locationName()},
             BUILT_DATE = :#{#bridge.builtDate()},
             EXPECTED_BRIDGE_LIFE_SPAN = :#{#bridge.lifeSpan()},
             HEIGHT = :#{#bridge.abutmentHeight()},
             LENGTH = :#{#bridge.length()},
             DECK_WIDTH = :#{#bridge.deckWidth()},
             DISTANCE_FROM_STORAGE = :#{#bridge.distance()},
             ILCR_BRIDGE_CNSTRCTN_TYPE_CODE = :#{#bridge.constructionTypeCode()},
             ILCR_BRIDGE_SUPERSTRUCTR_CODE = :#{#bridge.superstructureTypeCode()},
             ILCR_DECK_CODE = :#{#bridge.deckTypeCode()},
             ILCR_BRIDGE_ABUTMENT_TYPE_CODE = :#{#bridge.abutmentTypeCode()},
             ILCR_BRIDGE_LOAD_RATING_CODE = :#{#bridge.loadRatingCode()},
             COMMENTS = :#{#bridge.comments()},
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE BRIDGE_REPORT_ID = :#{#bridge.bridgeReportId()}
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
         AND REVISION_COUNT = :expectedRevision
      """)
  int updateBridge(
      @Param("bridge") BridgeReportEntity bridge, @Param("millId") long millId,
      @Param("year") int year, @Param("expectedRevision") int expectedRevision,
      @Param("user") String user);

  /**
   * Delete one bridge, scoped to the mill/year/category (never another mill's row). The service runs
   * this FIRST — its 0-rows result is the ownership/404 check — and only then cascades the cost
   * children ({@link #deleteCostsForBridge}, delivery has no FK cascade).
   *
   * @return rows affected — {@code 0} when the id is not a category-{@code '7'} bridge under this
   *     mill/year (→ 404)
   */
  @Modifying
  @Query("""
      DELETE FROM THE.BRIDGE_REPORT
       WHERE BRIDGE_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int deleteBridge(@Param("id") long id, @Param("millId") long millId, @Param("year") int year);

  /** Delete EVERY cost child of a bridge — explicit cascade (no FK in delivery). */
  @Modifying
  @Query("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE BRIDGE_REPORT_ID = :bridgeReportId")
  void deleteCostsForBridge(@Param("bridgeReportId") long bridgeReportId);

  /**
   * Upsert one bridge cost row (items 70-76/79-81): update-in-place when the row exists (audit
   * continuity — no delete/re-insert churn), else insert with a fresh sequence PK. Cost rows carry a
   * NULL {@code ILCR_REPORT_SUMMARY_ID} (list schedule).
   */
  default void upsertCost(long bridgeReportId, int costItemId, Integer cost, String user) {
    int updated = updateCost(bridgeReportId, costItemId, cost, user);
    if (updated == 0) {
      insertCost(nextCostDetailId(), bridgeReportId, costItemId, cost, user);
    }
  }

  /** Update-in-place half of {@link #upsertCost}; {@code 0} rows when the item row is absent. */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE BRIDGE_REPORT_ID = :bridgeReportId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemId
      """)
  int updateCost(
      @Param("bridgeReportId") long bridgeReportId, @Param("costItemId") int costItemId,
      @Param("cost") Integer cost, @Param("user") String user);

  /** Insert half of {@link #upsertCost} (summary id NULL; PK from the sequence; audit cols set). */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, BRIDGE_REPORT_ID,
           ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, NULL, :bridgeReportId, :costItemId, NULL, :cost, NULL, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCost(
      @Param("id") long id, @Param("bridgeReportId") long bridgeReportId,
      @Param("costItemId") int costItemId, @Param("cost") Integer cost, @Param("user") String user);

  /** Delete one cost child (used to CLEAR a cost the edit set to null — clear semantics). */
  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE BRIDGE_REPORT_ID = :bridgeReportId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemId
      """)
  void deleteCost(@Param("bridgeReportId") long bridgeReportId, @Param("costItemId") int costItemId);

  // ===============================================================================================
  // Code tables (schedule8 idiom): nested @Table records + @Query, mapped to CodeDescriptionDto
  // option lists for the served document and to a membership check for write validation.
  // ===============================================================================================

  private static List<CodeDescriptionDto> asOptions(List<? extends CodeLabel> rows) {
    return rows.stream().map(r -> new CodeDescriptionDto(r.code(), r.description())).toList();
  }

  /** Marker for a {@code (code, description)} code-table row. */
  interface CodeLabel {
    String code();

    String description();
  }

  @org.springframework.data.relational.core.mapping.Table(
      name = "ILCR_BRIDGE_CNSTRCTN_TYPE_CODE", schema = "THE")
  record ConstructionTypeCode(
      @org.springframework.data.annotation.Id
      @org.springframework.data.relational.core.mapping.Column("ILCR_BRIDGE_CNSTRCTN_TYPE_CODE")
      String code,
      @org.springframework.data.relational.core.mapping.Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_BRIDGE_CNSTRCTN_TYPE_CODE, DESCRIPTION FROM THE.ILCR_BRIDGE_CNSTRCTN_TYPE_CODE"
      + " ORDER BY ILCR_BRIDGE_CNSTRCTN_TYPE_CODE")
  List<ConstructionTypeCode> findConstructionTypeCodes();

  default List<CodeDescriptionDto> constructionTypeOptions() {
    return asOptions(findConstructionTypeCodes());
  }

  @org.springframework.data.relational.core.mapping.Table(
      name = "ILCR_BRIDGE_SUPERSTRUCTR_CODE", schema = "THE")
  record SuperstructureTypeCode(
      @org.springframework.data.annotation.Id
      @org.springframework.data.relational.core.mapping.Column("ILCR_BRIDGE_SUPERSTRUCTR_CODE")
      String code,
      @org.springframework.data.relational.core.mapping.Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_BRIDGE_SUPERSTRUCTR_CODE, DESCRIPTION FROM THE.ILCR_BRIDGE_SUPERSTRUCTR_CODE"
      + " ORDER BY ILCR_BRIDGE_SUPERSTRUCTR_CODE")
  List<SuperstructureTypeCode> findSuperstructureTypeCodes();

  default List<CodeDescriptionDto> superstructureTypeOptions() {
    return asOptions(findSuperstructureTypeCodes());
  }

  @org.springframework.data.relational.core.mapping.Table(name = "ILCR_DECK_CODE", schema = "THE")
  record DeckTypeCode(
      @org.springframework.data.annotation.Id
      @org.springframework.data.relational.core.mapping.Column("ILCR_DECK_CODE") String code,
      @org.springframework.data.relational.core.mapping.Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_DECK_CODE, DESCRIPTION FROM THE.ILCR_DECK_CODE ORDER BY ILCR_DECK_CODE")
  List<DeckTypeCode> findDeckTypeCodes();

  default List<CodeDescriptionDto> deckTypeOptions() {
    return asOptions(findDeckTypeCodes());
  }

  @org.springframework.data.relational.core.mapping.Table(
      name = "ILCR_BRIDGE_ABUTMENT_TYPE_CODE", schema = "THE")
  record AbutmentTypeCode(
      @org.springframework.data.annotation.Id
      @org.springframework.data.relational.core.mapping.Column("ILCR_BRIDGE_ABUTMENT_TYPE_CODE")
      String code,
      @org.springframework.data.relational.core.mapping.Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_BRIDGE_ABUTMENT_TYPE_CODE, DESCRIPTION FROM THE.ILCR_BRIDGE_ABUTMENT_TYPE_CODE"
      + " ORDER BY ILCR_BRIDGE_ABUTMENT_TYPE_CODE")
  List<AbutmentTypeCode> findAbutmentTypeCodes();

  default List<CodeDescriptionDto> abutmentTypeOptions() {
    return asOptions(findAbutmentTypeCodes());
  }

  @org.springframework.data.relational.core.mapping.Table(
      name = "ILCR_BRIDGE_LOAD_RATING_CODE", schema = "THE")
  record LoadRatingCode(
      @org.springframework.data.annotation.Id
      @org.springframework.data.relational.core.mapping.Column("ILCR_BRIDGE_LOAD_RATING_CODE")
      String code,
      @org.springframework.data.relational.core.mapping.Column("DESCRIPTION") String description)
      implements CodeLabel {
  }

  @Query("SELECT ILCR_BRIDGE_LOAD_RATING_CODE, DESCRIPTION FROM THE.ILCR_BRIDGE_LOAD_RATING_CODE"
      + " ORDER BY ILCR_BRIDGE_LOAD_RATING_CODE")
  List<LoadRatingCode> findLoadRatingCodes();

  default List<CodeDescriptionDto> loadRatingOptions() {
    return asOptions(findLoadRatingCodes());
  }
}
