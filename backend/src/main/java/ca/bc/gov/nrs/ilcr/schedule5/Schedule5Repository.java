package ca.bc.gov.nrs.ilcr.schedule5;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 5 tables (AD-3): explicit
 * {@code @Query} named-param SQL + {@code @Table} record entities — no derived queries, no {@code
 * CrudRepository}. SQL stays explicit because the model is a legacy-projection; every derivation
 * lives in {@link Schedule5Service} (AD-6 — repositories are dumb SQL).
 *
 * <p>Storage shape (delivery-DB confirmed, Story 7.1 Task 1): a camp is one {@code CAMP_REPORT} row
 * (category {@code '5'}, descriptors inline, own {@code REVISION_COUNT}); its twelve category
 * amounts are keyed rows in the shared {@code ILCR_COST_REPORT_DETAIL}, joined by {@code
 * CAMP_REPORT_ID} and discriminated by {@code ILCR_REPORT_COST_ITEM_ID}. There is no
 * category-{@code '5'} {@code ILCR_REPORT_SUMMARY} row (gate (ii): zero rows), so {@code
 * trackStatus} comes straight from {@code ILCR_MILL_REPORT_STATUS} — the per-schedule {@code
 * findTrackStatus} house pattern (Schedules 1/2/6/8 each declare their own).
 *
 * <p>The public {@code default} methods expose plain service-facing records ({@link CampRow},
 * {@link DetailRow}); the {@code @Query} methods are the explicit SQL, so entities never cross the
 * service boundary.
 */
public interface Schedule5Repository extends Repository<CampReportEntity, Integer> {

  /**
   * One Schedule 5 camp (a {@code CAMP_REPORT} row); indicator stays raw {@code Y}/{@code N}.
   *
   * <p>{@code campId} and {@code revisionCount} are primitives because both columns are {@code NOT
   * NULL}; the nullable descriptors stay boxed.
   */
  record CampRow(
      int campId,
      String campName,
      BigDecimal distanceToOperatingArea,
      Integer sizeOfCamp,
      BigDecimal associatedCampVolume,
      String isolatedCampInd,
      String comments,
      int revisionCount) {}

  /**
   * One keyed category-amount row; {@code costItemId} decides which category it feeds.
   *
   * <p>{@code costItemId} is boxed even though delivery declares the column {@code NOT NULL} (Task
   * 1 gate (iii)): the V1 snapshot does not, and an unboxing conversion here would turn the one
   * unrecognized item id the service cannot name into an NPE that 500s the entire document —
   * defeating the log-and-drop path {@link Schedule5Service} exists to provide. A null id is
   * dropped by the same branch that drops item 57.
   */
  record DetailRow(
      int detailId,
      int campId,
      Integer costItemId,
      BigDecimal volume,
      Integer cost,
      String itemDescription) {}

  // ---------------------------------------------------------------------------------------------
  // Reads — @Query returns @Table entities / scalars; default methods adapt to the service records.
  // ---------------------------------------------------------------------------------------------

  /**
   * The category-{@code '5'} camp rows for a mill/year, ordered by {@code CAMP_REPORT_ID} asc.
   *
   * <p>The ORDER BY is deliberate and is recorded deviation (c): legacy has no ORDER BY anywhere on
   * this path and materializes camps through a {@code HashMap} ({@code Schedule5DAO.java:58, 91,
   * 111-113}), whose iteration order merely LOOKS ascending for small dense ids and reorders across
   * a bucket resize. A REST contract cannot ship that.
   */
  @Query(
      """
      SELECT CAMP_REPORT_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY,
             ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT
        FROM THE.CAMP_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
       ORDER BY CAMP_REPORT_ID
      """)
  List<CampReportEntity> findCampEntities(@Param("millId") long millId, @Param("year") int year);

  /**
   * The camps for a mill/year (empty = the valid no-camps state, not an error — deviation (a)).
   * Maps the master entities to the service-facing {@link CampRow}.
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @return the camps in {@code CAMP_REPORT_ID} order; empty when the mill/year stores none
   */
  default List<CampRow> findCamps(long millId, int year) {
    return findCampEntities(millId, year).stream()
        .map(
            e ->
                new CampRow(
                    e.campReportId(),
                    e.campName(),
                    e.distanceToOperatingArea(),
                    e.campSizeCapacity(),
                    e.associatedCampVolume(),
                    e.isolatedCampInd(),
                    e.comments(),
                    e.revisionCount()))
        .toList();
  }

  /**
   * Every category-amount row for the mill/year's camps, joined to {@code CAMP_REPORT} so the
   * category/mill/year filter applies, ordered by camp then {@code ILCR_COST_REPORT_DETAIL_ID}.
   *
   * <p>Deliberately NOT filtered to the fifteen known item ids: legacy drops an unrecognized id
   * with a log line rather than failing ({@code Schedule5DAO.java:283-285}), and porting that needs
   * the service to SEE the unknown row. The detail-id ordering is what makes deviation (f)'s
   * first-by-id-wins rule deterministic — legacy resolved duplicates by whichever row an
   * identity-hashed {@code HashSet} happened to yield last, which is not stable between JVM runs on
   * identical data ({@code CampReport.java:93-94}).
   */
  @Query(
      """
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.CAMP_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID,
             d.VOLUME, d.COST, d.ITEM_DESCRIPTION
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.CAMP_REPORT c
          ON c.CAMP_REPORT_ID = d.CAMP_REPORT_ID
       WHERE c.ILCR_MILL_ID = :millId
         AND c.REPORT_YEAR = :year
         AND c.ILCR_CATEGORY_ID = '5'
       ORDER BY d.CAMP_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostReportDetailEntity> findCostDetailEntities(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * The category-amount rows mapped to the service-facing {@link DetailRow}.
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @return the rows in (camp id, detail id) order; empty is normal — the delivery image carries no
   *     camp-linked detail rows at all (Task 1 gate (v)), so every real camp is a zero-detail camp
   */
  default List<DetailRow> findCostDetails(long millId, int year) {
    return findCostDetailEntities(millId, year).stream()
        .map(
            d ->
                new DetailRow(
                    d.detailId(),
                    d.campReportId(),
                    d.costItemId(),
                    d.volume(),
                    d.cost(),
                    d.itemDescription()))
        .toList();
  }

  /**
   * The Schedules 1-10 track status code ({@code ILCR_MILL_REPORT_STATUS_CODE}) for a mill/year —
   * NOT the silviculture track (AD-9/AR7). Empty when there is no report-status row.
   *
   * <p>Assumes at most one status row per mill/year, exactly as every shipped schedule does; a
   * duplicate would surface as an incorrect-result-size failure rather than a silent wrong answer
   * (known, recorded at {@code deferred-work.md:197} — copy the assumption, do not invent a fix).
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @return the 1-10 track code ({@code D}/{@code S}/{@code V}; dead {@code O} passes through)
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
   * per-mill/year report-status row. Every WRITE path's Draft gate uses this one; the read path
   * keeps the unlocked variant. Copied from Schedule 2, which introduced it for the same reason
   * ({@code Schedule2Repository.java:90-106}).
   *
   * <p><strong>It is the concurrency backstop that Schedule 5's schema cannot provide.</strong>
   * This project owns no DDL on {@code THE} — there are no {@code src/main/resources/db/migration}
   * migrations at all, because the schema is shared with the legacy JSF application still in
   * production — so neither of the two unique constraints the write paths would otherwise want can
   * be added here: none on {@code (ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID, UPPER(CAMP_NAME))}
   * for BR-02, and none on {@code (CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID)} for the keyed detail
   * rows. Locking this row instead serializes all Schedule 5 writes for one mill/year, which closes
   * three check-then-act races at once:
   *
   * <ul>
   *   <li>BR-02's count-then-insert ({@code Schedule5Service.addCamp}) — two concurrent creates of
   *       the same name can no longer both count zero and both commit;
   *   <li>the Draft gate itself — the status this returns cannot transition between the gate and
   *       the INSERT/UPDATE/DELETE it guards, which is what made the gate advisory rather than
   *       binding;
   *   <li>{@link #upsertCostDetail}'s update-then-insert — two concurrent first-edits of one camp
   *       can no longer both find zero rows and both insert a row for the same item id, which is
   *       the only way this story could have MANUFACTURED the duplicate keyed rows the read path
   *       merely tolerates.
   * </ul>
   *
   * <p>Contention is bounded by the lock's granularity: one row per (mill, year), held for the
   * duration of one camp write by one licensee editing one reporting year.
   *
   * <p>Must run inside the write {@code @Transactional} so the lock is held until commit. A
   * mill/year with NO status row locks nothing and returns empty — which the gate already answers
   * as 409, so the absent-row case needs no separate handling.
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
  // Writes (Story 7.2) — explicit @Modifying SQL only. This interface is a bare
  // Repository<CampReportEntity, Integer>, so save()/delete() do not exist and are deliberately not
  // introduced: the model is a legacy projection, not a save()-driven aggregate, and REPORT_YEAR /
  // ILCR_MILL_ID / ILCR_CATEGORY_ID are unmapped on CampReportEntity (they live only in WHERE
  // clauses). Binding them as @Params here is sufficient and keeps the read entity from growing
  // write-only columns. EVERY UPDATE and DELETE is scoped to (id, ILCR_MILL_ID, REPORT_YEAR,
  // ILCR_CATEGORY_ID='5') as the IDOR guard — deviation (M): legacy's deleteCampFromReport loads by
  // primary key alone with no tenancy check at all (Schedule5DAO.java:550). EVERY INSERT supplies
  // REVISION_COUNT plus both audit pairs. All five are NOT NULL with NO defaults on both tables in
  // delivery (Task 1 gate (vi): CAMP_REPORT's only constraints are its PK, the ILCR_REPORT_CATEGORY
  // FK, and eleven IS NOT NULL checks), and no trigger populates them — CAMP_RAUD_B_I_U and
  // ILCR_CRDA_B_I_U only copy :NEW into their _AUDIT shadows. So an insert that skips one fails
  // here exactly as it would in delivery.
  // ===============================================================================================

  /**
   * The next camp PK from the shared delivery sequence (legacy {@code @SequenceGenerator}; {@code
   * LAST_NUMBER} 25155, re-verified Task 1 gate (v)). Fetched separately because {@code @Modifying}
   * cannot return generated keys.
   */
  @Query("SELECT THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL FROM DUAL")
  int nextCampReportId();

  /** The next cost-detail PK from the delivery sequence ({@code LAST_NUMBER} 25135, gate (v)). */
  @Query("SELECT THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL FROM DUAL")
  int nextCostDetailId();

  /**
   * Insert one camp (category {@code '5'}, {@code REVISION_COUNT = 0}, all four audit stamps).
   *
   * <p>{@code REVISION_COUNT = 0} matches legacy ({@code Schedule5DAO.java:363}) — but legacy then
   * NEVER increments it (:649 leaves it alone on update), so Schedule 5 had no lost-update
   * protection of any kind. The increment in {@link #updateCamp} is new (deviation (K)).
   *
   * <p>The composite FK {@code CMP_RPT_ILCR_RCAT_FK} → {@code ILCR_REPORT_CATEGORY} needs no help
   * from us: delivery carries a category row for every category 1–11 across all 118 mill/years, and
   * zero mill/years with a status row lack a category-{@code '5'} row (Task 1 gate (i)), so this
   * insert cannot trip it. The local V1 snapshot has no such table, so no local IT can prove that.
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.CAMP_REPORT
          (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME,
           ISOLATED_CAMP_IND, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :year, :millId, '5',
           :campName, :distance, :sizeOfCamp, :campVolume,
           :isolatedCampInd, :comments,
           0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCamp(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("campName") String campName,
      @Param("distance") BigDecimal distance,
      @Param("sizeOfCamp") Integer sizeOfCamp,
      @Param("campVolume") BigDecimal campVolume,
      @Param("isolatedCampInd") String isolatedCampInd,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Optimistic-lock update of one camp's descriptors (AR11, keyed per camp — 7.1 deviation (b),
   * because Schedule 5 is summary-less and has no schedule-level revision to key on). Bumps {@code
   * REVISION_COUNT} and stamps {@code UPDATE_*} ONLY when the stored revision still matches AND the
   * row belongs to this mill/year/category. {@code ENTRY_*} is untouched, so audit provenance
   * survives an edit.
   *
   * @return rows affected — {@code 1} on success; {@code 0} when the id is absent/foreign (→ 404)
   *     OR the revision is stale (→ 409). {@link #countCamp} disambiguates: the count alone cannot,
   *     because both failures produce the same zero here.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.CAMP_REPORT
         SET CAMP_NAME = :campName,
             DISTANCE_TO_OPERATING_AREA = :distance,
             CAMP_SIZE_CAPACITY = :sizeOfCamp,
             ASSOCIATED_CAMP_VOLUME = :campVolume,
             ISOLATED_CAMP_IND = :isolatedCampInd,
             COMMENTS = :comments,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CAMP_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
         AND REVISION_COUNT = :expectedRevision
      """)
  int updateCamp(
      @Param("id") int id,
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("expectedRevision") int expectedRevision,
      @Param("campName") String campName,
      @Param("distance") BigDecimal distance,
      @Param("sizeOfCamp") Integer sizeOfCamp,
      @Param("campVolume") BigDecimal campVolume,
      @Param("isolatedCampInd") String isolatedCampInd,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * How many camps with this id exist under the mill/year — the scoped existence probe that turns a
   * zero-row {@link #updateCamp} into 404 (unknown or foreign id) rather than 409 (stale token).
   * Returns {@code 0} or {@code 1}; the PK makes more impossible.
   */
  @Query(
      """
      SELECT COUNT(*)
        FROM THE.CAMP_REPORT
       WHERE CAMP_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
      """)
  int countCamp(@Param("id") int id, @Param("millId") long millId, @Param("year") int year);

  /**
   * Upsert one keyed category-amount row: update in place when it exists, else insert with a fresh
   * sequence PK.
   *
   * <p><strong>Never delete-and-reinsert.</strong> Legacy's {@code saveOrUpdateCampCostDetails}
   * ({@code Schedule5DAO.java:635-661}) branches on {@code containsKey(itemID)} and neither deletes
   * nor bulk-reinserts (deviation (N)). Churning the rows would rotate every {@code
   * ILCR_COST_REPORT_DETAIL_ID}, destroy {@code ENTRY_*}, and make every ordinary edit look like a
   * create in the {@code ILCR_COST_REPORT_DETAIL_AUD} shadow.
   *
   * <p><strong>The insert branch is load-bearing on EDITS, not just creates.</strong> Delivery
   * holds ZERO detail rows parented by a {@code CAMP_REPORT_ID} (Task 1 gate (vii) re-confirmed
   * 7.1's gate (v)), so every one of the 61 real camps is a zero-detail camp and the first edit of
   * a real camp takes this insert path twelve times over. It must create the rows, never fail.
   */
  default void upsertCostDetail(
      int campId, int itemId, BigDecimal volume, Integer cost, String user) {
    if (updateCostDetail(campId, itemId, volume, cost, user) == 0) {
      insertCostDetail(nextCostDetailId(), campId, itemId, volume, cost, user);
    }
  }

  /**
   * Update-in-place half of {@link #upsertCostDetail}; {@code 0} rows when the item has no row yet.
   * Scoped by camp AND item id — retained in the outer predicate alongside the {@code MIN} subquery
   * that already implies them, so the statement is self-evidently safe read in isolation and one
   * category can never overwrite another's row.
   *
   * <p>A null {@code volume}/{@code cost} writes {@code NULL} — a cleared field is cleared, not
   * zeroed, and its row SURVIVES (deviation (N)). Detail {@code REVISION_COUNT} is deliberately NOT
   * bumped: legacy only moves {@code UPDATE_*} here ({@code Schedule5DAO.java:641-642}), the same
   * parity Schedule 6 keeps.
   *
   * <p><strong>Narrowed to the CANONICAL row, not every row for the camp/item.</strong> Nothing in
   * delivery makes {@code (CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID)} unique, so a camp/item pair
   * can in principle hold more than one row. The read path resolves such a pair
   * FIRST-BY-DETAIL-ID-WINS ({@code Schedule5Service.groupDetails}, the {@code putIfAbsent} at
   * {@code :197}) and deliberately ignores the rest. An unqualified {@code WHERE camp AND item}
   * would have written all of them, including the rows the read path never serves — rotating {@code
   * UPDATE_*} on data the API presents as untouched, and making "first row wins" meaningless the
   * moment anyone edited. The {@code MIN} subquery makes the surviving row the ONLY row written, so
   * read and write agree on which one is canonical.
   *
   * <p>Unreachable on delivery data today (Task 1 gate (vii): zero camp-parented detail rows
   * exist), and this story can no longer create such a pair either — see {@link
   * #findTrackStatusForUpdate} for why the update-then-insert below is serialized. Kept because
   * correctness here must not rest on the absence of legacy rows that the schema still permits.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET VOLUME = :volume,
             COST = :cost,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE CAMP_REPORT_ID = :campId
         AND ILCR_REPORT_COST_ITEM_ID = :itemId
         AND ILCR_COST_REPORT_DETAIL_ID = (SELECT MIN(d.ILCR_COST_REPORT_DETAIL_ID)
                                             FROM THE.ILCR_COST_REPORT_DETAIL d
                                            WHERE d.CAMP_REPORT_ID = :campId
                                              AND d.ILCR_REPORT_COST_ITEM_ID = :itemId)
      """)
  int updateCostDetail(
      @Param("campId") int campId,
      @Param("itemId") int itemId,
      @Param("volume") BigDecimal volume,
      @Param("cost") Integer cost,
      @Param("user") String user);

  /**
   * Insert half of {@link #upsertCostDetail}. Every parent FK except {@code CAMP_REPORT_ID} stays
   * NULL, which is exactly what the {@code ICRD_CHK_B_I_U} delivery trigger requires: it counts
   * populated parent FKs across all nine columns and rejects zero or more than one, so a camp-only
   * row yields a count of 1 and passes (Task 1 gate (iii) — this story writes the first such rows
   * that have ever existed).
   *
   * <p>{@code ITEM_DESCRIPTION} and {@code COMMENTS} stay NULL: legacy sets neither on the twelve
   * fixed-grid rows (they belong to the item-62/68 sub-page rows, which are 7.4's). {@code
   * REVISION_COUNT = 0} plus both audit pairs, per legacy :649-653.
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :campId, :itemId, :volume, :cost, NULL, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertCostDetail(
      @Param("id") int id,
      @Param("campId") int campId,
      @Param("itemId") int itemId,
      @Param("volume") BigDecimal volume,
      @Param("cost") Integer cost,
      @Param("user") String user);

  /**
   * How many camps in this mill/year already hold this name, case-insensitively (BR-02, the CREATE
   * path). {@code UPPER(CAMP_NAME) = UPPER(:name)} is the case-insensitive comparison legacy did in
   * memory with {@code equalsIgnoreCase} ({@code Schedule5MB.java:313, 315}).
   *
   * <p>The STORED side is deliberately not {@code TRIM}med: legacy persisted names untrimmed
   * ({@code Schedule5DAO.java:373}) and compared the raw stored value, so a legacy-persisted {@code
   * " Cedar "} does not collide with a new {@code "Cedar"} there either. Matching that is legacy
   * parity, not an oversight (deviation (I) note) — adding {@code TRIM(CAMP_NAME)} would
   * retroactively 409 edits next to padded incumbents legacy accepted.
   */
  @Query(
      """
      SELECT COUNT(*)
        FROM THE.CAMP_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
         AND UPPER(CAMP_NAME) = UPPER(:name)
      """)
  int countCampsNamed(
      @Param("millId") long millId, @Param("year") int year, @Param("name") String name);

  /**
   * The same count for the EDIT path, excluding the camp being edited — otherwise every save of an
   * unrenamed camp would collide with itself.
   *
   * <p><strong>A SEPARATE query rather than one with a nullable exclusion parameter.</strong>
   * Binding a null into an {@code UPPER(:param)} makes ojdbc infer a CLOB and Oracle raises {@code
   * ORA-22848} ({@code Schedule4Repository.java:201-225}). Excluding by {@code CAMP_REPORT_ID} —
   * never by the old name — also means a camp whose name is unchanged, or whose name duplicates a
   * THIRD camp, is judged on identity rather than on a string comparison that a rename would
   * invalidate.
   */
  @Query(
      """
      SELECT COUNT(*)
        FROM THE.CAMP_REPORT
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
         AND UPPER(CAMP_NAME) = UPPER(:name)
         AND CAMP_REPORT_ID <> :excludeCampId
      """)
  int countCampsNamedExcluding(
      @Param("millId") long millId,
      @Param("year") int year,
      @Param("name") String name,
      @Param("excludeCampId") int excludeCampId);

  /**
   * Delete every category-amount row of one camp — the twelve fixed-grid rows AND the item-62/68
   * sub-page rows, since the camp family goes together (BR-09/AC5).
   *
   * <p>This runs BEFORE the camp itself and that ordering is MANDATORY, not stylistic: {@code
   * ILCR_LCRD_CMP_RPT_FK} is {@code ON DELETE NO ACTION} in delivery (Task 1 gate (ii)), as are all
   * nine parent FKs on this table, so deleting the parent first raises ORA-02292. Legacy appeared
   * to cascade only because {@code CampReport.java:93} declares Hibernate's {@code CascadeType.ALL}
   * — there is no DDL cascade anywhere.
   *
   * <p>Scoped by an {@code EXISTS} against the mill/year/category-{@code '5'} parent, like every
   * other write here (the IDOR guard, deviation (M)): the service's prior probe makes the scope
   * redundant at today's one call site, but a guard that holds only by call-order convention is
   * exactly the hole the guarded UPDATE closes on the master row — this method must be safe in
   * isolation too.
   *
   * @return rows deleted — {@code 0} is normal, and is in fact the delivery-typical case
   */
  @Modifying
  @Query(
      """
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL d
       WHERE d.CAMP_REPORT_ID = :campId
         AND EXISTS (SELECT 1
                       FROM THE.CAMP_REPORT c
                      WHERE c.CAMP_REPORT_ID = d.CAMP_REPORT_ID
                        AND c.ILCR_MILL_ID = :millId
                        AND c.REPORT_YEAR = :year
                        AND c.ILCR_CATEGORY_ID = '5')
      """)
  int deleteCostDetailsForCamp(
      @Param("campId") int campId, @Param("millId") long millId, @Param("year") int year);

  /**
   * Delete one camp, scoped to mill/year/category (the IDOR guard — deviation (M)). Call only after
   * {@link #deleteCostDetailsForCamp}.
   *
   * <p>{@code CAMP_RAUD_B_I_U} does NOT fire on DELETE — it is declared {@code BEFORE INSERT OR
   * UPDATE} and its {@code ELSE v_spar_audit_code := 'DELETE'} branch is dead code (Task 1 gate
   * (iv)) — so a deleted camp leaves no audit-shadow trace. That is legacy behaviour, not something
   * this story introduces.
   *
   * @return rows deleted — the service acts on this count rather than assuming success (the 8.2
   *     {@code deletePlaceholder}-returned-{@code void} defect, which reported "saved" after doing
   *     nothing)
   */
  @Modifying
  @Query(
      """
      DELETE FROM THE.CAMP_REPORT
       WHERE CAMP_REPORT_ID = :id
         AND ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = '5'
      """)
  int deleteCamp(@Param("id") int id, @Param("millId") long millId, @Param("year") int year);

  // ===============================================================================================
  // Sub-page rows (Story 7.4) — items 62 (Other Camp) and 68 (Other Access).
  //
  // These are SEPARATE primitives from the camp-path upsert above, and the separation is mandatory
  // rather than stylistic. The camp path is keyed by (CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID),
  // which identifies exactly one row per category; a sub-page holds a LIST under that same pair, so
  // the key here has to be the row's own ILCR_COST_REPORT_DETAIL_ID. Concretely, reusing the camp
  // primitives fails three ways, each of which passes a naive test:
  //   * insertCostDetail hard-codes ITEM_DESCRIPTION to NULL (:392) — descriptions silently vanish;
  //   * updateCostDetail never mentions ITEM_DESCRIPTION and is narrowed to the MIN(detail_id)
  //     canonical row (:365-368), so editing row three writes row one;
  //   * upsertCostDetail is the two composed, so it is structurally single-row.
  // deleteCostDetailsForCamp is deliberately NOT touched: the camp family delete must keep removing
  // 62/68 rows along with the twelve fixed ones (BR-09/AC5).
  //
  // Every statement is scoped by camp AND item id. Legacy scoped by neither: its update and delete
  // loops match a detail id against the camp's ENTIRE detail collection (Schedule5DAO.java:585-595,
  // :606-614), so a Camp-page save carrying an item-68 row's id would overwrite that Access row.
  // Deviation (O) — a hardening, recorded rather than silently applied.
  // ===============================================================================================

  /**
   * One camp's rows for ONE sub-page item, oldest first.
   *
   * <p>Ordered by {@code ILCR_COST_REPORT_DETAIL_ID} (deviation (G)). Legacy's order is {@code
   * HashSet} iteration order ({@code CampReport.java:93-94, 228-231}) — not stable between JVM runs
   * on identical data — and the correctly-ordered named query {@code findCostDetailsForCampReport}
   * exists but is never called. A REST contract cannot ship that, and the batch reconcile below
   * depends on a stable identity per row.
   *
   * <p>Joined to {@code CAMP_REPORT} for the mill/year/category-{@code '5'} filter, exactly as
   * {@link #findCostDetails} does, so a row cannot be read across tenants.
   */
  @Query(
      """
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.CAMP_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID,
             d.VOLUME, d.COST, d.ITEM_DESCRIPTION
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.CAMP_REPORT c
          ON c.CAMP_REPORT_ID = d.CAMP_REPORT_ID
       WHERE d.CAMP_REPORT_ID = :campId
         AND d.ILCR_REPORT_COST_ITEM_ID = :itemId
         AND c.ILCR_MILL_ID = :millId
         AND c.REPORT_YEAR = :year
         AND c.ILCR_CATEGORY_ID = '5'
       ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<CostReportDetailEntity> findSubPageRowEntities(
      @Param("campId") int campId,
      @Param("itemId") int itemId,
      @Param("millId") long millId,
      @Param("year") int year);

  /**
   * The sub-page rows mapped to the service-facing {@link DetailRow}.
   *
   * @param campId the parent camp id
   * @param itemId 62 (Other Camp) or 68 (Other Access)
   * @param millId the validated mill id — the tenancy scope
   * @param year the validated reporting year
   * @return the rows in detail-id order; empty is the normal delivery state (Task 1 gate (iv): no
   *     camp-parented detail row of any item id exists yet)
   */
  default List<DetailRow> findSubPageRows(int campId, int itemId, long millId, int year) {
    return findSubPageRowEntities(campId, itemId, millId, year).stream()
        .map(
            d ->
                new DetailRow(
                    d.detailId(),
                    d.campReportId(),
                    d.costItemId(),
                    d.volume(),
                    d.cost(),
                    d.itemDescription()))
        .toList();
  }

  /**
   * Insert one sub-page row.
   *
   * <p>{@code VOLUME} is written {@code NULL} and that is not an omission (deviation (B)): legacy's
   * {@code getNewCostReportDetail} never calls {@code setVolume} ({@code
   * Schedule5DAO.java:617-633}), so no stored sub-page row has ever carried one, and the displayed
   * volume is stamped from the camp's item-141/142 row at read time.
   *
   * <p>{@code ITEM_DESCRIPTION} is written verbatim including {@code NULL} — the server does not
   * police it (deviation (F)). {@code ICRD_CHK_B_I_U} is indifferent: it counts populated parent-FK
   * columns only and never reads this one (Task 1 gate (ii)), so a camp-only row passes with a
   * count of 1 whatever the description holds.
   *
   * <p>{@code REVISION_COUNT = 0} and ALL FOUR audit columns, because all five are {@code NOT NULL}
   * with no defaults in delivery and no trigger populates them (Task 1 gates (i) and (iii)) — an
   * insert that skips one fails here exactly as it would in delivery.
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (:id, :campId, :itemId, NULL, :cost, :description, 0,
           :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)
      """)
  void insertSubPageRow(
      @Param("id") int id,
      @Param("campId") int campId,
      @Param("itemId") int itemId,
      @Param("cost") Integer cost,
      @Param("description") String description,
      @Param("user") String user);

  /**
   * Update one sub-page row in place, by its own id and scoped to the camp and item.
   *
   * <p>Stamps {@code UPDATE_*} ONLY. {@code ENTRY_*} and {@code REVISION_COUNT} are left alone, and
   * that is LEGACY PARITY rather than a deviation — the story's deviation (H) misreads {@code
   * Schedule5DAO.java:626-628}. Those lines belong to {@code getNewCostReportDetail}, whose output
   * is only a CARRIER on the update path; the update branch at {@code :585-595} mutates the already
   * persistent row and copies just cost, description, volume and {@code UPDATE_*}, discarding the
   * transient object's {@code ENTRY_*} and its {@code REVISION_COUNT = 0}. So legacy already stamps
   * {@code ENTRY_*} on insert only. Detail {@code REVISION_COUNT} is not bumped, matching the camp
   * path and Schedule 6.
   *
   * <p>A null {@code cost} or {@code description} writes {@code NULL} — a cleared field is cleared,
   * not zeroed, and the row survives.
   *
   * @return rows affected — {@code 1} on success, {@code 0} when the id is unknown, belongs to
   *     another camp, or belongs to the OTHER sub-page item. The service treats every zero as 404,
   *     which is what makes a stale id fail loudly instead of drifting into a re-insert.
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_COST_REPORT_DETAIL
         SET COST = :cost,
             ITEM_DESCRIPTION = :description,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSTIMESTAMP
       WHERE ILCR_COST_REPORT_DETAIL_ID = :rowId
         AND CAMP_REPORT_ID = :campId
         AND ILCR_REPORT_COST_ITEM_ID = :itemId
      """)
  int updateSubPageRow(
      @Param("rowId") int rowId,
      @Param("campId") int campId,
      @Param("itemId") int itemId,
      @Param("cost") Integer cost,
      @Param("description") String description,
      @Param("user") String user);

  /**
   * Delete one sub-page row, by its own id and scoped to the camp and item.
   *
   * <p>Carries no revision token (AR11 house deviation (N), shared with Schedules 4/7A/11), so a
   * delete is never rejected as stale.
   *
   * @return rows deleted — {@code 0} means unknown, foreign, or the other item's row. The service
   *     acts on this count rather than assuming success (the 4.4 lesson: an edit that silently
   *     dropped its value because nothing checked rows-affected).
   */
  @Modifying
  @Query(
      """
      DELETE FROM THE.ILCR_COST_REPORT_DETAIL
       WHERE ILCR_COST_REPORT_DETAIL_ID = :rowId
         AND CAMP_REPORT_ID = :campId
         AND ILCR_REPORT_COST_ITEM_ID = :itemId
      """)
  int deleteSubPageRow(
      @Param("rowId") int rowId, @Param("campId") int campId, @Param("itemId") int itemId);
}
