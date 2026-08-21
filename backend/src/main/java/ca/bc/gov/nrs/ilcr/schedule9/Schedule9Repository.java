package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CodeLists;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 9 tables (AD-3): explicit
 * {@code @Query} named-param SQL — no derived queries, no {@code CrudRepository} save()/delete().
 * Every derivation ($/Unit) lives in {@link Schedule9Service} (AD-6 — repositories are dumb SQL).
 *
 * <p>Storage shape (legacy-confirmed; delivery Task-1 gate): a record is one {@code
 * CONTRACTUAL_WORK_REPORT} row (category {@code '9'}, descriptors inline, own {@code
 * REVISION_COUNT}); its Cost + Contractual Item are ONE keyed row in the shared {@code
 * ILCR_COST_REPORT_DETAIL}, joined by {@code CONTRACTUAL_WORK_REPORT_ID} and discriminated by
 * {@code ILCR_REPORT_COST_ITEM_ID} (108–114). Summary-less like Schedules 4/5/6, so {@code
 * trackStatus} comes straight from {@code ILCR_MILL_REPORT_STATUS} (the per-schedule house
 * pattern).
 *
 * <p>The two joined reads resolve the four code-list descriptions in SQL (LEFT JOINs to the
 * reference tables), returned as {@link RecordRow}/{@link CostRow} projections so entities never
 * cross the service boundary.
 */
public interface Schedule9Repository extends Repository<ContractualWorkReportEntity, Integer> {

  /** One {@code CONTRACTUAL_WORK_REPORT} row with its three code-list descriptions resolved. */
  record RecordRow(
      int id,
      int revisionCount,
      String contractorId,
      BigDecimal numberOfUnits,
      Integer sideSlopePct,
      String comments,
      String unitCode,
      String unitCodeDescription,
      String unitDescription,
      String sourceCode,
      String sourceCodeDescription,
      String sourceDescription,
      String becCode,
      String becDescription) {}

  /**
   * One record's cost line: the Contractual Item (108–114 code + its catalogue name), the "Other"
   * item free-text ({@code ITEM_DESCRIPTION}, present only for item 114), and the cost.
   */
  record CostRow(
      int reportId, Integer itemCode, String itemName, String itemDescription, Integer cost) {}

  /**
   * The category-'9' records for a mill/year, code descriptions resolved, ordered by id asc (a REST
   * contract needs a stable order; legacy iterates an unordered map).
   */
  @Query(
      """
      SELECT cwr.CONTRACTUAL_WORK_REPORT_ID AS ID,
             cwr.REVISION_COUNT AS REVISION_COUNT,
             cwr.CONTRACTOR_ID AS CONTRACTOR_ID,
             cwr.PERFORMED_UNIT AS NUMBER_OF_UNITS,
             cwr.SIDE_SLOPE_PCT AS SIDE_SLOPE_PCT,
             cwr.COMMENTS AS COMMENTS,
             cwr.ILCR_UNIT_CODE AS UNIT_CODE,
             u.DESCRIPTION AS UNIT_CODE_DESCRIPTION,
             cwr.UNIT_DESCRIPTION AS UNIT_DESCRIPTION,
             cwr.ILCR_CONTRACTUAL_SOURCE_CODE AS SOURCE_CODE,
             s.DESCRIPTION AS SOURCE_CODE_DESCRIPTION,
             cwr.SOURCE_DESCRIPTION AS SOURCE_DESCRIPTION,
             cwr.BEC_ZONE_CODE AS BEC_CODE,
             b.DESCRIPTION AS BEC_DESCRIPTION
        FROM THE.CONTRACTUAL_WORK_REPORT cwr
        LEFT JOIN THE.ILCR_UNIT_CODE u ON u.ILCR_UNIT_CODE = cwr.ILCR_UNIT_CODE
        LEFT JOIN THE.ILCR_CONTRACTUAL_SOURCE_CODE s
          ON s.ILCR_CONTRACTUAL_SOURCE_CODE = cwr.ILCR_CONTRACTUAL_SOURCE_CODE
        LEFT JOIN THE.BEC_ZONE_CODE b ON b.BEC_ZONE_CODE = cwr.BEC_ZONE_CODE
       WHERE cwr.ILCR_MILL_ID = :millId
         AND cwr.REPORT_YEAR = :year
         AND cwr.ILCR_CATEGORY_ID = '9'
       ORDER BY cwr.CONTRACTUAL_WORK_REPORT_ID
      """)
  List<RecordRow> findRecords(@Param("millId") long millId, @Param("year") int year);

  /**
   * How many category-{@code '9'} records a mill/year has — the empty-schedule pre-check for the
   * report fill (Story 20.1/20.2). Mirrors {@link #findRecords}' WHERE without the code-list joins
   * or projection, so the "is there anything to render?" decision costs one COUNT instead of
   * materializing (and discarding) the full row list the template's own query then re-runs.
   */
  @Query(
      """
      SELECT COUNT(*)
        FROM THE.CONTRACTUAL_WORK_REPORT cwr
       WHERE cwr.ILCR_MILL_ID = :millId
         AND cwr.REPORT_YEAR = :year
         AND cwr.ILCR_CATEGORY_ID = '9'
      """)
  int countRecords(@Param("millId") long millId, @Param("year") int year);

  /**
   * The cost line for each record of the mill/year (Cost + Contractual Item 108–114 + its name),
   * joined to {@code CONTRACTUAL_WORK_REPORT} so the mill/year/category filter applies.
   *
   * <p>Ordered by {@code CONTRACTUAL_WORK_REPORT_ID} then {@code ILCR_COST_REPORT_DETAIL_ID} so the
   * service's lowest-detail-id-wins dedup (a record should own exactly one cost line, but the FK
   * has no unique constraint) is deterministic — Schedule 5's recorded deviation (c).
   */
  @Query(
      """
      SELECT d.CONTRACTUAL_WORK_REPORT_ID AS REPORT_ID,
             d.ILCR_REPORT_COST_ITEM_ID AS ITEM_CODE,
             ci.ITEM_NAME AS ITEM_NAME,
             d.ITEM_DESCRIPTION AS ITEM_DESCRIPTION,
             d.COST AS COST
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.CONTRACTUAL_WORK_REPORT cwr
          ON cwr.CONTRACTUAL_WORK_REPORT_ID = d.CONTRACTUAL_WORK_REPORT_ID
        LEFT JOIN THE.ILCR_REPORT_COST_ITEM ci
          ON ci.ILCR_REPORT_COST_ITEM_ID = d.ILCR_REPORT_COST_ITEM_ID
       WHERE cwr.ILCR_MILL_ID = :millId
         AND cwr.REPORT_YEAR = :year
         AND cwr.ILCR_CATEGORY_ID = '9'
         AND d.ILCR_REPORT_COST_ITEM_ID BETWEEN 108 AND 114
       ORDER BY d.CONTRACTUAL_WORK_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostRow> findCostLines(@Param("millId") long millId, @Param("year") int year);

  /**
   * The Schedules 1–10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9). Empty when there is no report-status row.
   */
  @Query(
      """
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);

  /**
   * Same as {@link #findTrackStatus} but takes an Oracle {@code FOR UPDATE} row lock on the
   * per-mill/year report-status row — every WRITE path's Draft gate uses this; the read path keeps
   * the unlocked variant (Schedule 5's {@code findTrackStatusForUpdate}). Holding the row for the
   * whole write transaction makes the Draft gate binding rather than advisory: a transition that
   * commits between the gate and the INSERT/UPDATE/DELETE it guards cannot slip in. Must run inside
   * the write {@code @Transactional}. A mill/year with no status row locks nothing and returns
   * empty, which the gate already answers as 409.
   */
  @Query(
      """
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
       FOR UPDATE
      """)
  Optional<String> findTrackStatusForUpdate(@Param("millId") long millId, @Param("year") int year);

  // ===============================================================================================
  // Writes (Story 9.2) — explicit @Modifying SQL only; this stays a bare Repository, so
  // save()/delete() do not exist and are not introduced (the model is a legacy projection). EVERY
  // UPDATE and DELETE is scoped to (id, ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID='9') as the
  // IDOR
  // guard. EVERY INSERT supplies REVISION_COUNT plus both audit pairs: on CONTRACTUAL_WORK_REPORT
  // all
  // four audit columns are NOT NULL with NO defaults (read seed), so an omission fails here as it
  // would in delivery. Insert order is CWR master first, then its ILCR_COST_REPORT_DETAIL cost line
  // (T1 gate (i): the ILCR_CRDA_B_I_U-style trigger resolves the parent, so a cost line written
  // first
  // would find none). Costs/units are never logged (AD-11).
  // ===============================================================================================

  /**
   * The next {@code CONTRACTUAL_WORK_REPORT} PK from the shared delivery sequence (legacy
   * {@code @SequenceGenerator(sequenceName = "ILCR_REPORT_COMMON_SEQ")} — the same sequence
   * Schedule 5's camps draw from). Fetched separately because {@code @Modifying} cannot return
   * generated keys.
   */
  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  int nextContractualWorkReportId();

  /**
   * The next {@code ILCR_COST_REPORT_DETAIL} PK from the delivery sequence (legacy
   * {@code @SequenceGenerator(sequenceName = "ILCR_COST_REPORT_DETAIL_SEQ")}).
   */
  @Query("SELECT THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL FROM DUAL")
  int nextCostDetailId();

  /**
   * Insert one contractual-work master (category {@code '9'}, {@code REVISION_COUNT = 0}, all four
   * audit stamps). Legacy {@code Schedule9DO.addContractualWorkReport} sets {@code REVISION_COUNT =
   * 0} and stamps {@code ENTRY_*}/{@code UPDATE_*} from the acting user + now.
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.CONTRACTUAL_WORK_REPORT
          (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION,
           ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :year, :millId, '9',
           :contractorId, :sideSlopePct, :numberOfUnits, :unitCode, :unitDescription,
           :sourceCode, :sourceDescription, :becZoneCode, :comments,
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertRecord(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("contractorId") String contractorId,
      @Param("sideSlopePct") Integer sideSlopePct,
      @Param("numberOfUnits") BigDecimal numberOfUnits,
      @Param("unitCode") String unitCode,
      @Param("unitDescription") String unitDescription,
      @Param("sourceCode") String sourceCode,
      @Param("sourceDescription") String sourceDescription,
      @Param("becZoneCode") String becZoneCode,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Optimistic-lock update of one master's descriptors (AR11, keyed per record — Schedule 9 is
   * summary-less, so there is no schedule-level revision to key on). Bumps {@code REVISION_COUNT}
   * and stamps {@code UPDATE_*} ONLY when the stored revision still matches AND the row belongs to
   * this mill/year/category. {@code ENTRY_*} is untouched, so audit provenance survives an edit.
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the id is absent/foreign (→ 404)
   *     OR the revision is stale (→ 409). {@link #countRecord} disambiguates.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.CONTRACTUAL_WORK_REPORT
         SET CONTRACTOR_ID = :contractorId,
             SIDE_SLOPE_PCT = :sideSlopePct,
             PERFORMED_UNIT = :numberOfUnits,
             ILCR_UNIT_CODE = :unitCode,
             UNIT_DESCRIPTION = :unitDescription,
             ILCR_CONTRACTUAL_SOURCE_CODE = :sourceCode,
             SOURCE_DESCRIPTION = :sourceDescription,
             BEC_ZONE_CODE = :becZoneCode,
             COMMENTS = :comments,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CONTRACTUAL_WORK_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '9'
         AND REVISION_COUNT = :expectedRevision
      """)
  int updateRecord(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("expectedRevision") int expectedRevision,
      @Param("contractorId") String contractorId,
      @Param("sideSlopePct") Integer sideSlopePct,
      @Param("numberOfUnits") BigDecimal numberOfUnits,
      @Param("unitCode") String unitCode,
      @Param("unitDescription") String unitDescription,
      @Param("sourceCode") String sourceCode,
      @Param("sourceDescription") String sourceDescription,
      @Param("becZoneCode") String becZoneCode,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * How many category-{@code '9'} records with this id exist under the mill/year — the scoped
   * existence probe that turns a zero-row {@link #updateRecord}/delete into 404 (unknown or foreign
   * id) rather than 409 (stale token). Returns {@code 0} or {@code 1}; the PK makes more
   * impossible.
   */
  @Query(
      """
      SELECT COUNT(*)
        FROM THE.CONTRACTUAL_WORK_REPORT
       WHERE CONTRACTUAL_WORK_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '9'
      """)
  int countRecord(@Param("id") int id, @Param("millId") long millId, @Param("year") int year);

  /**
   * Insert the record's single cost line — the Contractual Item (108–114), the cost, and the
   * "Other" item free text ({@code ITEM_DESCRIPTION}). {@code REVISION_COUNT = 0} plus both audit
   * pairs (legacy stamps the child too). Only {@code CONTRACTUAL_WORK_REPORT_ID} is populated among
   * the parent FKs, which the shared-table insert trigger requires (exactly one parent FK).
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, CONTRACTUAL_WORK_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :recordId, :itemCode, :cost, :itemDescription, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCostLine(
      @Param("id") int id,
      @Param("recordId") int recordId,
      @Param("itemCode") int itemCode,
      @Param("cost") Integer cost,
      @Param("itemDescription") String itemDescription,
      @Param("user") String user);

  /**
   * Update the record's cost line in place — Contractual Item, cost, and "Other" item free text.
   * Narrowed to the CANONICAL row (lowest {@code ILCR_COST_REPORT_DETAIL_ID}) so that, even though
   * nothing in delivery makes {@code (CONTRACTUAL_WORK_REPORT_ID)} unique on this shared table,
   * read and write agree on which line is the record's — the read's {@code toMap} first-wins dedup
   * keys on the same lowest id. {@code REVISION_COUNT} is deliberately NOT bumped (legacy moves
   * only {@code UPDATE_*} on the child); a null cost/description writes {@code NULL}.
   *
   * @return rows affected — {@code 0} only when the record has no cost line yet (then insert)
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET ILCR_REPORT_COST_ITEM_ID = :itemCode,
             COST = :cost,
             ITEM_DESCRIPTION = :itemDescription,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CONTRACTUAL_WORK_REPORT_ID = :recordId
         AND ILCR_COST_REPORT_DETAIL_ID = (SELECT MIN(d.ILCR_COST_REPORT_DETAIL_ID)
                                             FROM THE.ILCR_COST_REPORT_DETAIL d
                                            WHERE d.CONTRACTUAL_WORK_REPORT_ID = :recordId
                                              AND d.ILCR_REPORT_COST_ITEM_ID BETWEEN 108 AND 114)
      """)
  int updateCostLine(
      @Param("recordId") int recordId,
      @Param("itemCode") int itemCode,
      @Param("cost") Integer cost,
      @Param("itemDescription") String itemDescription,
      @Param("user") String user);

  /**
   * Upsert the record's single cost line: update the canonical row in place when it exists, else
   * insert one with a fresh sequence PK. A well-formed record always has exactly one, so the insert
   * branch is the create path; the update branch is every edit.
   */
  default void upsertCostLine(
      int recordId, int itemCode, Integer cost, String itemDescription, String user) {
    if (updateCostLine(recordId, itemCode, cost, itemDescription, user) == 0) {
      insertCostLine(nextCostDetailId(), recordId, itemCode, cost, itemDescription, user);
    }
  }

  /**
   * Delete the record's cost line(s) — runs BEFORE the master (child-first; the parent FK is {@code
   * ON DELETE NO ACTION} in delivery, so deleting the master first would raise ORA-02292). Scoped
   * by an {@code EXISTS} against the mill/year/category-{@code '9'} parent (the IDOR guard), so the
   * method is safe in isolation and not only by call-order convention.
   *
   * @return rows deleted (normally {@code 1})
   */
  @Modifying
  @Query(
      """
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL d
       WHERE d.CONTRACTUAL_WORK_REPORT_ID = :id
         AND EXISTS (SELECT 1
                       FROM THE.CONTRACTUAL_WORK_REPORT cwr
                      WHERE cwr.CONTRACTUAL_WORK_REPORT_ID = d.CONTRACTUAL_WORK_REPORT_ID
                        AND cwr.ILCR_MILL_ID = :millId
                        AND cwr.REPORT_YEAR = :year
                        AND cwr.ILCR_CATEGORY_ID = '9')
      """)
  int deleteCostLinesForRecord(
      @Param("id") int id, @Param("millId") long millId, @Param("year") int year);

  /**
   * Delete one master, scoped to mill/year/category (the IDOR guard). Call only after {@link
   * #deleteCostLinesForRecord}.
   *
   * @return rows deleted — the service acts on this count rather than assuming success (the 8.2
   *     void-delete-reported-success defect)
   */
  @Modifying
  @Query(
      """
      DELETE FROM THE.CONTRACTUAL_WORK_REPORT
       WHERE CONTRACTUAL_WORK_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '9'
      """)
  int deleteRecord(@Param("id") int id, @Param("millId") long millId, @Param("year") int year);

  /**
   * Whether a code value resolves to a live {@code ILCR_UNIT_CODE} row (force-selection FLD-005).
   * Not year-scoped — the 9.1 read path resolves unit descriptions with an unfiltered join, so a
   * write must accept exactly the codes the read can name.
   */
  @Query("SELECT COUNT(*) FROM THE.ILCR_UNIT_CODE WHERE ILCR_UNIT_CODE = :code")
  int countUnitCode(@Param("code") String code);

  /** Whether a code value resolves to a {@code BEC_ZONE_CODE} row (force-selection FLD-005). */
  @Query("SELECT COUNT(*) FROM THE.BEC_ZONE_CODE WHERE BEC_ZONE_CODE = :code")
  int countBecZoneCode(@Param("code") String code);

  /**
   * Whether a code value resolves to an {@code ILCR_CONTRACTUAL_SOURCE_CODE} row (force-selection
   * FLD-005).
   */
  @Query(
      """
      SELECT COUNT(*)
        FROM THE.ILCR_CONTRACTUAL_SOURCE_CODE
       WHERE ILCR_CONTRACTUAL_SOURCE_CODE = :code
      """)
  int countSourceCode(@Param("code") String code);

  // ===============================================================================================
  // Code tables (Story 9.3): the four dropdown option lists carried on the GET document. Each row
  // is
  // a (code, description) pair mapped to the shared CodeDescriptionDto. NOT year-scoped — matching
  // the 9.1 read joins (see Schedule9CodeLists). Contractual items are the fixed category-'9'
  // catalogue; the unit/BEC/source lists are the whole small reference tables.
  // ===============================================================================================

  /** A generic {@code (code, description)} option row projection for the four code lists. */
  record OptionRow(String code, String description) {}

  /** The Contractual Item catalogue (108–114, BR-09); the numeric id is the option code. */
  @Query(
      """
      SELECT TO_CHAR(ILCR_REPORT_COST_ITEM_ID) AS CODE, ITEM_NAME AS DESCRIPTION
        FROM THE.ILCR_REPORT_COST_ITEM
       WHERE ILCR_CATEGORY_ID = '9'
       ORDER BY ILCR_REPORT_COST_ITEM_ID
      """)
  List<OptionRow> findContractualItemOptions();

  /** The {@code ILCR_UNIT_CODE} options, ordered by code. */
  @Query(
      """
      SELECT ILCR_UNIT_CODE AS CODE, DESCRIPTION AS DESCRIPTION
        FROM THE.ILCR_UNIT_CODE
       ORDER BY ILCR_UNIT_CODE
      """)
  List<OptionRow> findUnitTypeOptions();

  /** The {@code BEC_ZONE_CODE} options, ordered by code. */
  @Query(
      """
      SELECT BEC_ZONE_CODE AS CODE, DESCRIPTION AS DESCRIPTION
        FROM THE.BEC_ZONE_CODE
       ORDER BY BEC_ZONE_CODE
      """)
  List<OptionRow> findBiogeoclimaticZoneOptions();

  /** The {@code ILCR_CONTRACTUAL_SOURCE_CODE} options, ordered by code. */
  @Query(
      """
      SELECT ILCR_CONTRACTUAL_SOURCE_CODE AS CODE, DESCRIPTION AS DESCRIPTION
        FROM THE.ILCR_CONTRACTUAL_SOURCE_CODE
       ORDER BY ILCR_CONTRACTUAL_SOURCE_CODE
      """)
  List<OptionRow> findSourceOptions();

  /** The four option lists assembled into the served {@link Schedule9CodeLists}. */
  default Schedule9CodeLists codeLists() {
    return new Schedule9CodeLists(
        toOptions(findContractualItemOptions()),
        toOptions(findUnitTypeOptions()),
        toOptions(findBiogeoclimaticZoneOptions()),
        toOptions(findSourceOptions()));
  }

  private static List<CodeDescriptionDto> toOptions(List<OptionRow> rows) {
    return rows.stream().map(r -> new CodeDescriptionDto(r.code(), r.description())).toList();
  }
}
