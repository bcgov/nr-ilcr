package ca.bc.gov.nrs.ilcr.schedule4;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 4 tables (AD-3, re-pinned 2026-07-20:
 * domain repositories are Spring Data JDBC {@code Repository} interfaces with explicit {@code @Query}
 * named-param SQL + {@code @Table} record entities — no derived queries, no {@code CrudRepository.save}).
 * SQL stays explicit because the model is a legacy-projection; every derivation and the transaction
 * boundaries remain in {@link Schedule4Service}.
 *
 * <p>Storage shape (delivery-DB confirmed): a location is a FAMILY of {@code TRANSPORTATION_REPORT}
 * rows sharing a {@code LOCATION_DESCRIPTION} — one primary report (distance null) carries the 9 fixed
 * categories, each distance-based category (47/48/52) lives on its own report with its own
 * {@code DISTANCE}, and each sub-page list row (43/46/55, code 54 dead) is likewise its own report.
 * Category amounts are {@code ILCR_COST_REPORT_DETAIL} rows joined by {@code TRANSPORTATION_REPORT_ID}.
 *
 * <p>The public {@code default} methods expose plain service-facing records ({@link LocationRow},
 * {@link DetailRow}, {@link SubPageRowRow}) and compose the sequence-insert / upsert / delete
 * sequences; the {@code @Query}/{@code @Modifying} methods are the explicit SQL. New id generation
 * uses {@code ILCR_REPORT_COMMON_SEQ}; detail ids come from {@code ILCR_COST_REPORT_DETAIL_SEQ}.
 */
public interface Schedule4Repository extends Repository<TransportationReportEntity, Integer> {

  String SCHEDULE_4_CATEGORY = "4";

  /** One Schedule 4 location (a {@code TRANSPORTATION_REPORT} row). */
  record LocationRow(int transportationReportId, String locationDescription, BigDecimal distance,
      Integer revisionCount) {
  }

  /** One in-scope transportation-category detail row for a location. */
  record DetailRow(int transportationReportId, Integer costItemCode, BigDecimal volume,
      Integer cost) {
  }

  /**
   * One sub-page list row (Story 4.3): its own report ({@code transportationReportId}) sharing the
   * location name, its {@code costItemCode} (43/46/55), free-text {@code description}, per-report
   * {@code distance}/{@code cycle}, and the single detail's {@code volume}/{@code cost}.
   */
  record SubPageRowRow(int transportationReportId, String locationDescription, Integer costItemCode,
      String description, BigDecimal distance, Integer cycle, BigDecimal volume, Integer cost) {
  }

  // -------------------------------------------------------------------------------------------------
  // Reads — @Query returns @Table entities / scalars; default methods adapt to the service records.
  // -------------------------------------------------------------------------------------------------

  /** The category-{@code "4"} report rows for a mill/year, ordered by report id (legacy order). */
  @Query("""
      SELECT TRANSPORTATION_REPORT_ID, LOCATION_DESCRIPTION, DISTANCE,
             TRANSPORTATION_CYCLE_TIME, REVISION_COUNT
        FROM THE.TRANSPORTATION_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '4'
       ORDER BY TRANSPORTATION_REPORT_ID
      """)
  List<TransportationReportEntity> findReportEntities(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * The location rows for a mill/year (empty = the valid no-locations state, not an error). Maps the
   * report entities to the service-facing {@link LocationRow}.
   */
  default List<LocationRow> findLocations(long millId, int year) {
    return findReportEntities(millId, year).stream()
        .map(e -> new LocationRow(
            e.transportationReportId(), e.locationDescription(), e.distance(), e.revisionCount()))
        .toList();
  }

  /**
   * The in-scope category detail rows (fixed 40,41,42,44,45,49,50,51,53; distance-based 47,48,52),
   * joined by {@code TRANSPORTATION_REPORT_ID}. Deferred sub-page codes (43,46,55) are excluded.
   */
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.TRANSPORTATION_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID,
             d.VOLUME, d.COST, d.ITEM_DESCRIPTION
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.TRANSPORTATION_REPORT tr
          ON tr.TRANSPORTATION_REPORT_ID = d.TRANSPORTATION_REPORT_ID
       WHERE tr.ILCR_MILL_ID = :millId
         AND tr.REPORT_YEAR = :year
         AND tr.ILCR_CATEGORY_ID = '4'
         AND d.ILCR_REPORT_COST_ITEM_ID IN (40,41,42,44,45,49,50,51,53,47,48,52)
       ORDER BY d.TRANSPORTATION_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostReportDetailEntity> findInScopeDetailEntities(
      @Param("millId") long millId, @Param("year") int year);

  /** The in-scope category detail rows mapped to the service-facing {@link DetailRow}. */
  default List<DetailRow> findInScopeDetails(long millId, int year) {
    return findInScopeDetailEntities(millId, year).stream()
        .map(d -> new DetailRow(d.transportationReportId(), d.costItemCode(), d.volume(), d.cost()))
        .toList();
  }

  /** The sub-page detail rows (codes 43,46,55) for a mill/year, joined for the category filter. */
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.TRANSPORTATION_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID,
             d.VOLUME, d.COST, d.ITEM_DESCRIPTION
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.TRANSPORTATION_REPORT tr
          ON tr.TRANSPORTATION_REPORT_ID = d.TRANSPORTATION_REPORT_ID
       WHERE tr.ILCR_MILL_ID = :millId
         AND tr.REPORT_YEAR = :year
         AND tr.ILCR_CATEGORY_ID = '4'
         AND d.ILCR_REPORT_COST_ITEM_ID IN (43,46,55)
       ORDER BY d.TRANSPORTATION_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostReportDetailEntity> findSubPageDetailEntities(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * The sub-page list rows for a mill/year. Each row's own report supplies its
   * {@code LOCATION_DESCRIPTION}/{@code DISTANCE}/{@code CYCLE}; joined in-memory to the report
   * entities (avoids a cross-table result projection).
   */
  default List<SubPageRowRow> findSubPageRows(long millId, int year) {
    Map<Integer, TransportationReportEntity> reportsById = new HashMap<>();
    for (TransportationReportEntity report : findReportEntities(millId, year)) {
      reportsById.put(report.transportationReportId(), report);
    }
    return findSubPageDetailEntities(millId, year).stream()
        .map(d -> {
          TransportationReportEntity report = reportsById.get(d.transportationReportId());
          return new SubPageRowRow(
              d.transportationReportId(),
              report == null ? null : report.locationDescription(),
              d.costItemCode(),
              d.itemDescription(),
              report == null ? null : report.distance(),
              report == null ? null : report.transportationCycleTime(),
              d.volume(),
              d.cost());
        })
        .toList();
  }

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9). Empty when there is no report-status row.
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  /** The current {@code LOCATION_DESCRIPTION} of a report (the edit target's name), or empty. */
  @Query("""
      SELECT LOCATION_DESCRIPTION
        FROM THE.TRANSPORTATION_REPORT
       WHERE TRANSPORTATION_REPORT_ID = :reportId
      """)
  Optional<String> findLocationName(@Param("reportId") int reportId);

  /**
   * The distance-child report id holding {@code code}'s detail for the named location, or empty when
   * the family has no report for that distance code yet (an insert is needed).
   */
  @Query("""
      SELECT tr.TRANSPORTATION_REPORT_ID
        FROM THE.TRANSPORTATION_REPORT tr
        JOIN THE.ILCR_COST_REPORT_DETAIL d
          ON d.TRANSPORTATION_REPORT_ID = tr.TRANSPORTATION_REPORT_ID
       WHERE tr.ILCR_MILL_ID = :millId
         AND tr.REPORT_YEAR = :year
         AND tr.ILCR_CATEGORY_ID = '4'
         AND tr.LOCATION_DESCRIPTION = :name
         AND d.ILCR_REPORT_COST_ITEM_ID = :code
       ORDER BY tr.TRANSPORTATION_REPORT_ID
       FETCH FIRST 1 ROWS ONLY
      """)
  Optional<Integer> findDistanceReportId(
      @Param("millId") long millId, @Param("year") int year, @Param("name") String name,
      @Param("code") int code);

  // ---- name uniqueness (BR-02, case-insensitive) — branch on excludeName to avoid binding a null
  // ---- into UPPER(:excludeName) (ojdbc treats it as CLOB → ORA-22848).

  @Query("""
      SELECT COUNT(*)
        FROM THE.TRANSPORTATION_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '4'
         AND UPPER(LOCATION_DESCRIPTION) = UPPER(:name)
      """)
  int countByName(@Param("millId") long millId, @Param("year") int year, @Param("name") String name);

  @Query("""
      SELECT COUNT(*)
        FROM THE.TRANSPORTATION_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '4'
         AND UPPER(LOCATION_DESCRIPTION) = UPPER(:name)
         AND UPPER(LOCATION_DESCRIPTION) <> UPPER(:excludeName)
      """)
  int countByNameExcluding(
      @Param("millId") long millId, @Param("year") int year, @Param("name") String name,
      @Param("excludeName") String excludeName);

  /**
   * Server-side name-uniqueness check (BR-02, case-INSENSITIVE): does another location for the
   * mill/year already use {@code name}? {@code excludeName} (the edited location's current name, or
   * null on create) removes the location's own family from the comparison.
   */
  default boolean nameExists(long millId, int year, String name, String excludeName) {
    int count = excludeName == null
        ? countByName(millId, year, name)
        : countByNameExcluding(millId, year, name, excludeName);
    return count > 0;
  }

  /**
   * Whether {@code reportId} is a sub-page-list row (its own report for this mill/year with a single
   * detail of code 43/46/55) — the guard that keeps the row-delete endpoint from removing a primary
   * or category report.
   */
  @Query("""
      SELECT COUNT(*)
        FROM THE.TRANSPORTATION_REPORT tr
        JOIN THE.ILCR_COST_REPORT_DETAIL d
          ON d.TRANSPORTATION_REPORT_ID = tr.TRANSPORTATION_REPORT_ID
       WHERE tr.TRANSPORTATION_REPORT_ID = :reportId
         AND tr.ILCR_MILL_ID = :millId
         AND tr.REPORT_YEAR = :year
         AND tr.ILCR_CATEGORY_ID = '4'
         AND d.ILCR_REPORT_COST_ITEM_ID IN (43,46,55)
      """)
  int countSubPageRow(
      @Param("reportId") int reportId, @Param("millId") long millId, @Param("year") int year);

  /** Whether {@code reportId} is a sub-page-list row for this mill/year (guarded, idempotent delete). */
  default boolean isSubPageRow(int reportId, long millId, int year) {
    return countSubPageRow(reportId, millId, year) > 0;
  }

  // -------------------------------------------------------------------------------------------------
  // Writes — @Modifying explicit SQL; default methods compose sequence-insert / upsert / delete.
  // Transaction boundaries live in Schedule4Service (@Transactional).
  // -------------------------------------------------------------------------------------------------

  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  int nextReportId();

  @Modifying
  @Query("""
      INSERT INTO THE.TRANSPORTATION_REPORT
          (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (:id, :year, :millId, '4', :name, :distance, :cycle, 0, :user, SYSTIMESTAMP)
      """)
  int insertReportRow(
      @Param("id") int id, @Param("millId") long millId, @Param("year") int year,
      @Param("name") String name, @Param("distance") BigDecimal distance,
      @Param("cycle") Integer cycle, @Param("user") String user);

  /**
   * Insert a new category-{@code "4"} report at {@code REVISION_COUNT} 0 (primary: distance null) and
   * return its generated id. Sequence-then-insert (Spring Data JDBC {@code @Modifying} cannot return
   * a generated key).
   */
  default int insertReport(long millId, int year, String name, BigDecimal distance, String user) {
    int id = nextReportId();
    insertReportRow(id, millId, year, name, distance, null, user);
    return id;
  }

  /** Insert a new sub-page-row report (own distance + cycle for Truck Rehaul) and return its id. */
  default int insertSubPageReport(
      long millId, int year, String name, BigDecimal distance, Integer cycle, String user) {
    int id = nextReportId();
    insertReportRow(id, millId, year, name, distance, cycle, user);
    return id;
  }

  /**
   * Optimistic-lock bump of a report (§Decision 3): increments {@code REVISION_COUNT} + audit ONLY
   * when the stored revision still matches {@code expectedRevision}. Returns rows affected —
   * {@code 1} on success, {@code 0} when stale (→ 409).
   */
  @Modifying
  @Query("""
      UPDATE THE.TRANSPORTATION_REPORT
         SET REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE TRANSPORTATION_REPORT_ID = :reportId
         AND REVISION_COUNT = :expectedRevision
      """)
  int bumpRevision(
      @Param("reportId") int reportId, @Param("expectedRevision") int expectedRevision,
      @Param("user") String user);

  /** Re-stamp the distance on a distance-child report (audit updated). */
  @Modifying
  @Query("""
      UPDATE THE.TRANSPORTATION_REPORT
         SET DISTANCE = :distance,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE TRANSPORTATION_REPORT_ID = :reportId
      """)
  void updateReportDistance(
      @Param("reportId") int reportId, @Param("distance") BigDecimal distance,
      @Param("user") String user);

  /**
   * Rename a whole location family: re-stamp {@code LOCATION_DESCRIPTION} on every category-{@code "4"}
   * report for the mill/year currently under {@code oldName} (primary + distance children + sub-page
   * rows stay consistent).
   */
  @Modifying
  @Query("""
      UPDATE THE.TRANSPORTATION_REPORT
         SET LOCATION_DESCRIPTION = :newName,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '4'
         AND LOCATION_DESCRIPTION = :oldName
      """)
  void renameFamily(
      @Param("millId") long millId, @Param("year") int year, @Param("oldName") String oldName,
      @Param("newName") String newName, @Param("user") String user);

  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET VOLUME = :volume,
             COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE TRANSPORTATION_REPORT_ID = :reportId
         AND ILCR_REPORT_COST_ITEM_ID = :code
      """)
  int updateDetailRow(
      @Param("reportId") int reportId, @Param("code") int code,
      @Param("volume") BigDecimal volume, @Param("cost") Integer cost, @Param("user") String user);

  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :reportId, :code,
           :volume, :cost, NULL, :user, SYSTIMESTAMP)
      """)
  int insertDetailRow(
      @Param("reportId") int reportId, @Param("code") int code,
      @Param("volume") BigDecimal volume, @Param("cost") Integer cost, @Param("user") String user);

  /**
   * Upsert a detail row by {@code (transportationReportId, costItemCode)}: update-in-place first,
   * insert only when absent. A null {@code volume}/{@code cost} is still written so clearing a field
   * persists null.
   */
  default void upsertDetail(int reportId, int costItemCode, BigDecimal volume, Integer cost,
      String user) {
    if (updateDetailRow(reportId, costItemCode, volume, cost, user) == 0) {
      insertDetailRow(reportId, costItemCode, volume, cost, user);
    }
  }

  /**
   * Insert the single {@code ILCR_COST_REPORT_DETAIL} for a sub-page row (item 43/46/55) with its
   * free-text {@code ITEM_DESCRIPTION}.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID, ENTRY_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, :reportId, :code,
           :volume, :cost, :description, :user, SYSTIMESTAMP)
      """)
  void insertDetailWithDescription(
      @Param("reportId") int reportId, @Param("code") int costItemCode,
      @Param("volume") BigDecimal volume, @Param("cost") Integer cost,
      @Param("description") String description, @Param("user") String user);

  @Modifying
  @Query("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE TRANSPORTATION_REPORT_ID = :reportId")
  int deleteDetailsByReport(@Param("reportId") int reportId);

  @Modifying
  @Query("DELETE FROM THE.TRANSPORTATION_REPORT WHERE TRANSPORTATION_REPORT_ID = :reportId")
  int deleteReportRow(@Param("reportId") int reportId);

  /** Delete one report and its cost-detail rows (used to clear an emptied distance-child / a sub-page row). */
  default void deleteReport(int reportId) {
    deleteDetailsByReport(reportId);
    deleteReportRow(reportId);
  }

  @Modifying
  @Query("""
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE TRANSPORTATION_REPORT_ID IN (
             SELECT TRANSPORTATION_REPORT_ID
               FROM THE.TRANSPORTATION_REPORT
              WHERE ILCR_MILL_ID = :millId
                AND REPORT_YEAR = :year
                AND ILCR_CATEGORY_ID = '4'
                AND LOCATION_DESCRIPTION = :name)
      """)
  int deleteFamilyDetails(
      @Param("millId") long millId, @Param("year") int year, @Param("name") String name);

  @Modifying
  @Query("""
      DELETE FROM THE.TRANSPORTATION_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '4'
         AND LOCATION_DESCRIPTION = :name
      """)
  int deleteFamilyReports(
      @Param("millId") long millId, @Param("year") int year, @Param("name") String name);

  /**
   * Delete a whole location family for a mill/year — every category-{@code "4"} report under
   * {@code name} and their cascaded cost-detail rows (BR-08). Idempotency is handled in the service.
   */
  default void deleteFamily(long millId, int year, String name) {
    deleteFamilyDetails(millId, year, name);
    deleteFamilyReports(millId, year, name);
  }
}
