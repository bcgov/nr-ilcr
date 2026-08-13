package ca.bc.gov.nrs.ilcr.schedule7b;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC reads and writes for Schedule 7B (Culvert Costs) — AD-3: a {@code Repository}
 * interface of explicit {@code @Query} named-parameter SQL over {@code @Table} record entities,
 * {@code THE}-qualified; no derived queries, no {@code CrudRepository.save}, no {@code JdbcClient}.
 * SQL only — all derivations, the Draft gate, and 404-vs-409 disambiguation live in {@link
 * Schedule7bService}.
 *
 * <p>A culvert = one {@code THE.CULVERT_REPORT} row keyed {@code (ILCR_MILL_ID, REPORT_YEAR,
 * ILCR_CATEGORY_ID = '7')}; its two costs = {@code THE.ILCR_COST_REPORT_DETAIL} rows keyed by
 * {@code CULVERT_REPORT_ID} + one of the fixed Schedule 7B cost items ({@code 77} material / {@code
 * 78} install — legacy {@code Constant.REPORT_COST_ITEMS.Schedule7_30_Material}/ {@code
 * Schedule7_30_Installation}), with a NULL {@code ILCR_REPORT_SUMMARY_ID} (list schedule). The type
 * FK resolves to {@code THE.ILCR_CULVERT_TYPE_CODE} via the nested {@code @Table} record at the
 * foot of this interface.
 *
 * <p><strong>IDOR scoping, precisely.</strong> Every statement against {@code THE.CULVERT_REPORT}
 * itself — {@link #updateCulvert}, {@link #deleteCulvert}, {@link #countCulvert}, and both reads —
 * is scoped to {@code (ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID='7')}, so one mill's write can
 * never touch another's row. The two cost-CHILD writes ({@link #updateCost}, {@link
 * #deleteCostsForCulvert}) are keyed on {@code CULVERT_REPORT_ID} alone: their ownership rests on
 * the service reaching them only after a mill-scoped statement has proven the parent belongs to the
 * caller — a fresh sequence id on insert, or the 1-row {@code updateCulvert}/{@code deleteCulvert}
 * on a correction or delete. That is call-order safety, not SQL-level safety. An {@code EXISTS}
 * backstop is recorded in {@code deferred-work.md} to be added across 7A and 7B together; until
 * then, do not add a caller that writes costs without first proving the parent's ownership.
 */
public interface Schedule7bRepository extends Repository<CulvertReportEntity, Long> {

  /**
   * The two Schedule 7B cost-item ids (legacy {@code Constant.java:349}). The service routes cost
   * rows by these; {@link #findCostDetails} repeats them as SQL literals because Spring Data JDBC
   * cannot bind an {@code IN} list from an interface constant. {@code Schedule7bRepositoryTest}
   * asserts the literals and these constants agree, so a change to one that misses the other fails
   * a test rather than silently loading no costs.
   */
  int ITEM_MATERIAL = 77;
  int ITEM_INSTALL = 78;

  // ===============================================================================================
  // Reads (Story 13.1)
  // ===============================================================================================

  /**
   * The culverts for a mill/year, ordered by {@code CULVERT_REPORT_ID} ascending — the legacy list
   * order, straight from the named query {@code findCulvertReportDetails} ({@code
   * model/CulvertReport.java:38}, {@code order by cr.culvert_report_id}). It is also the {@code
   * rowCounter} order the service assigns 1..N, which the Check Status messages quote back to the
   * reporter, so this ORDER BY is contractual rather than cosmetic.
   */
  @Query("""
      SELECT CULVERT_REPORT_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH,
             CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT
        FROM THE.CULVERT_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
       ORDER BY CULVERT_REPORT_ID
      """)
  List<CulvertReportEntity> findCulverts(@Param("millId") long millId, @Param("year") int year);

  /**
   * Every Schedule 7B cost row (items 77/78) for all culverts of a mill/year, in one read. Filtered
   * to the two fixed item ids here AND re-routed by id in the service — an out-of-scope item
   * attached to a culvert must reach no field. The join is what scopes the read to the mill/year,
   * since {@code ILCR_COST_REPORT_DETAIL} carries no mill or year of its own.
   */
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.CULVERT_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID, d.COST
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.CULVERT_REPORT c ON c.CULVERT_REPORT_ID = d.CULVERT_REPORT_ID
       WHERE c.ILCR_MILL_ID = :millId
         AND c.REPORT_YEAR = :year
         AND c.ILCR_CATEGORY_ID = '7'
         AND d.ILCR_REPORT_COST_ITEM_ID IN (77, 78)
       ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CulvertCostEntity> findCostDetails(@Param("millId") long millId, @Param("year") int year);

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * Schedule 7B rides this track (BR-01), NOT the silviculture track (AD-9). Empty when there is no
   * report-status row.
   */
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  /**
   * True iff a category-{@code '7'} culvert with this id exists under the mill/year (404-vs-409).
   */
  @Query("""
      SELECT COUNT(*)
        FROM THE.CULVERT_REPORT
       WHERE CULVERT_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int countCulvert(@Param("id") long id, @Param("millId") long millId, @Param("year") int year);

  // ===============================================================================================
  // Writes (Story 13.2)
  // ===============================================================================================

  /** The next culvert PK (delivery: {@code THE.ILCR_REPORT_COMMON_SEQ}, per legacy's generator). */
  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  long nextCulvertReportId();

  /** The next cost-detail PK (delivery: {@code THE.ILCR_COST_REPORT_DETAIL_SEQ}). */
  @Query("SELECT THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL FROM DUAL")
  long nextCostDetailId();

  /**
   * Insert one culvert (category {@code '7'}, {@code REVISION_COUNT = 0}, audit {@code ENTRY_*}/
   * {@code UPDATE_*} set). The entered columns arrive as one {@link CulvertReportEntity} (its
   * {@code culvertReportId} is the PK supplied from {@link #nextCulvertReportId()} so the service
   * can key the cost-child writes to it); {@code millId}/{@code year}/{@code user} are the context.
   * The culvert columns bind by SpEL accessor so the write mirrors the {@code @Table} record shape
   * rather than a flat parameter list.
   */
  @Modifying
  @Query("""
      INSERT INTO THE.CULVERT_REPORT
          (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE,
           SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:#{#culvert.culvertReportId()}, :year, :millId, '7', :#{#culvert.culvertTypeCode()},
           :#{#culvert.spanSize()}, :#{#culvert.riseSize()}, :#{#culvert.length()},
           :#{#culvert.culvertPieceCount()}, :#{#culvert.comments()},
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCulvert(
      @Param("culvert") CulvertReportEntity culvert, @Param("millId") long millId,
      @Param("year") int year, @Param("user") String user);

  /**
   * Optimistic-lock update of one culvert: sets the entered fields, bumps {@code REVISION_COUNT},
   * and stamps {@code UPDATE_*} ONLY when the stored revision still matches {@code
   * expectedRevision} and the row belongs to this mill/year and category.
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the id is absent (→ 404) OR the
   *     revision is stale (→ 409). The service disambiguates via {@link #countCulvert}.
   */
  @Modifying
  @Query("""
      UPDATE THE.CULVERT_REPORT
         SET ILCR_CULVERT_TYPE_CODE = :#{#culvert.culvertTypeCode()},
             SPAN_SIZE = :#{#culvert.spanSize()},
             RISE_SIZE = :#{#culvert.riseSize()},
             LENGTH = :#{#culvert.length()},
             CULVERT_PIECE_COUNT = :#{#culvert.culvertPieceCount()},
             COMMENTS = :#{#culvert.comments()},
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CULVERT_REPORT_ID = :#{#culvert.culvertReportId()}
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
         AND REVISION_COUNT = :expectedRevision
      """)
  int updateCulvert(
      @Param("culvert") CulvertReportEntity culvert, @Param("millId") long millId,
      @Param("year") int year, @Param("expectedRevision") int expectedRevision,
      @Param("user") String user);

  /**
   * Delete one culvert, scoped to the mill/year/category (never another mill's row). Runs LAST — the
   * cost children go first ({@link #deleteCostsForCulvert}), because delivery's FK on
   * {@code ILCR_COST_REPORT_DETAIL.CULVERT_REPORT_ID} has no {@code ON DELETE CASCADE} and would
   * reject a parent still holding children. Legacy got the same order from Hibernate {@code
   * CascadeType.ALL} ({@code model/CulvertReport.java:231}), which deletes the collection before its
   * owner.
   *
   * @return rows affected — {@code 0} when the id is not a category-{@code '7'} culvert under this
   *     mill/year (the service has already 404'd on that via {@link #countCulvert})
   */
  @Modifying
  @Query("""
      DELETE FROM THE.CULVERT_REPORT
       WHERE CULVERT_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '7'
      """)
  int deleteCulvert(@Param("id") long id, @Param("millId") long millId, @Param("year") int year);

  /** Delete EVERY cost child of a culvert — explicit cascade (no FK in delivery). */
  @Modifying
  @Query("DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE CULVERT_REPORT_ID = :culvertReportId")
  void deleteCostsForCulvert(@Param("culvertReportId") long culvertReportId);

  /**
   * Upsert one culvert cost row (item 77 or 78): update-in-place when the row exists (audit
   * continuity — no delete/re-insert churn), else insert with a fresh sequence PK. Cost rows carry
   * a NULL {@code ILCR_REPORT_SUMMARY_ID} (list schedule).
   */
  default void upsertCost(long culvertReportId, int costItemId, Integer cost, String user) {
    int updated = updateCost(culvertReportId, costItemId, cost, user);
    if (updated == 0) {
      insertCost(nextCostDetailId(), culvertReportId, costItemId, cost, user);
    }
  }

  /** Update-in-place half of {@link #upsertCost}; {@code 0} rows when the item row is absent. */
  @Modifying
  @Query("""
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CULVERT_REPORT_ID = :culvertReportId
         AND ILCR_REPORT_COST_ITEM_ID = :costItemId
      """)
  int updateCost(
      @Param("culvertReportId") long culvertReportId, @Param("costItemId") int costItemId,
      @Param("cost") Integer cost, @Param("user") String user);

  /** Insert half of {@link #upsertCost} (summary id NULL; PK from the sequence; audit cols set). */
  @Modifying
  @Query("""
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID,
           ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, NULL, :culvertReportId, :costItemId, NULL, :cost, NULL, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCost(
      @Param("id") long id, @Param("culvertReportId") long culvertReportId,
      @Param("costItemId") int costItemId, @Param("cost") Integer cost,
      @Param("user") String user);

  // ===============================================================================================
  // Code table (schedule7a/schedule8 idiom): a nested @Table record + @Query, mapped to a
  // CodeDescriptionDto option list for the served document and to a membership check for writes.
  // ===============================================================================================

  /**
   * The instant a reporting year's code list is evaluated at: JANUARY 1 of that year, matching
   * legacy {@code CoreUtil.getDate(year)} feeding {@code LookupCache.getCacheList(year)}, which
   * kept a code only when {@code effective_date <= that date <= expiry_date}. A code retired before
   * the reporting year, or not yet in force at its start, was not offered — and is not offered
   * here.
   *
   * <p>The query NVLs both bounds because a bare comparison against NULL is false in SQL, which
   * would drop a row encoding "never expires" as a NULL {@code EXPIRY_DATE} — and dropping it would
   * not merely hide the option, it would make {@code validateCulvertType} reject a value already
   * stored on an existing culvert. Legacy could not encode that case at all ({@code LookupCache}
   * calls {@code date.before(c.getEffective_date())} with no null check, so a NULL there would have
   * thrown an NPE building the list), so the guard cannot change behaviour for any data legacy
   * could serve; it only stops an unrepresentable row from silently breaking saves.
   */
  private static LocalDate effectiveOn(int year) {
    return LocalDate.of(year, 1, 1);
  }

  /**
   * A {@code THE.ILCR_CULVERT_TYPE_CODE} row (legacy {@code model/ILCRCulvertTypeCode.java} — the
   * single-column code IS the PK, with {@code DESCRIPTION}/{@code EFFECTIVE_DATE}/{@code
   * EXPIRY_DATE} inherited from {@code AbstractILCRCode}).
   *
   * @param code the culvert type code
   * @param description the human-readable label shown in the Type dropdown
   */
  @Table(name = "ILCR_CULVERT_TYPE_CODE", schema = "THE")
  record CulvertTypeCode(
      @Id @Column("ILCR_CULVERT_TYPE_CODE") String code,
      @Column("DESCRIPTION") String description) {
  }

  @Query("""
      SELECT ILCR_CULVERT_TYPE_CODE, DESCRIPTION
        FROM THE.ILCR_CULVERT_TYPE_CODE
       WHERE NVL(EFFECTIVE_DATE, DATE '0001-01-01') <= :asOf
         AND NVL(EXPIRY_DATE, DATE '9999-12-31') >= :asOf
       ORDER BY ILCR_CULVERT_TYPE_CODE
      """)
  List<CulvertTypeCode> findCulvertTypeCodes(@Param("asOf") LocalDate asOf);

  /** The Type options as offered for THIS reporting year, mapped to the shared option DTO. */
  default List<CodeDescriptionDto> culvertTypeOptions(int year) {
    return findCulvertTypeCodes(effectiveOn(year)).stream()
        .map(r -> new CodeDescriptionDto(r.code(), r.description()))
        .toList();
  }
}
