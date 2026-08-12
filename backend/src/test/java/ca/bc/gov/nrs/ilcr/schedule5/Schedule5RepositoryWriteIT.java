package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-level acceptance for the Schedule 5 WRITE SQL — the statement-level behaviour the endpoint
 * tests structurally cannot observe.
 *
 * <p>Five things live here rather than in {@link Schedule5WriteIT}:
 *
 * <ul>
 *   <li><strong>The upsert's two branches, distinguished by row COUNT.</strong> Through the API both
 *       branches produce a correct-looking document; only a direct count can prove the update branch
 *       UPDATED rather than inserting a second row.
 *   <li><strong>The guarded update's return value.</strong> The service turns {@code 0} into a 404 or a
 *       409, so the endpoint can only ever see the outcome — never whether the guard clause itself is
 *       present. Deleting {@code AND REVISION_COUNT = :expectedRevision} would make every stale write
 *       succeed, and only this class notices.
 *   <li><strong>The two name-count queries, including the {@code ORA-22848} shape.</strong> A single
 *       query with a nullable exclusion parameter raises {@code ORA-22848} because ojdbc infers a CLOB
 *       for a null bound into {@code UPPER(:param)} ({@code Schedule4Repository.java:201-225}); proving
 *       the two-query split works needs a direct call.
 *   <li><strong>The delete's child-then-parent ordering</strong>, which is MANDATORY in delivery
 *       ({@code ILCR_LCRD_CMP_RPT_FK} is {@code ON DELETE NO ACTION}, Task 1 gate (ii)) but which the
 *       local snapshot cannot enforce because it has no such FK.
 *   <li><strong>The IDOR scoping of every UPDATE and DELETE</strong>, asserted by proving the
 *       neighbouring mill's row SURVIVES — not by asserting the target changed.
 * </ul>
 *
 * <p>Each method is {@code @Transactional} so its writes roll back, keeping this class from consuming
 * the mill/year contexts {@link Schedule5WriteIT} owns. Sequence values drawn inside a rolled-back
 * transaction are NOT returned to the sequence, which is fine: the fixtures all sit below the
 * sequence starts by design.
 */
@DisplayName("Schedule5Repository — the write statements the endpoint tests cannot see")
@Transactional
class Schedule5RepositoryWriteIT extends AbstractOracleIT {

  private static final long MILL = 670L;
  private static final int EDIT_YEAR = 2017;
  private static final int EDIT_CAMP = 8201;
  // Camp 8202 (the fixtures' empty 2018 camp) is deliberately NOT a constant here: it belongs to
  // Schedule5WriteIT, which COMMITS rows onto it. Every proof in this class that needs a pristine
  // camp seeds its own inside its rolled-back transaction.
  private static final int BARE_YEAR = 2018;
  private static final String USER = "repo-it";

  @Autowired
  private Schedule5Repository repository;

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private int rowCountFor(int campId, int itemId) {
    return jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = ?",
        Integer.class, campId, itemId);
  }

  @Test
  @DisplayName("upsert UPDATES IN PLACE when the row exists — COUNT(*) stays 1, and the id is the same")
  void upsertUpdatesInPlace() {
    assertThat(rowCountFor(EDIT_CAMP, 56)).isEqualTo(1);
    Integer originalId = jdbc().queryForObject(
        "SELECT ILCR_COST_REPORT_DETAIL_ID FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 56",
        Integer.class, EDIT_CAMP);

    repository.upsertCostDetail(EDIT_CAMP, 56, new BigDecimal("123.45"), 777, USER);

    // The count is the assertion. An upsert that inserted unconditionally would leave TWO rows for
    // item 56 and the served document would still look plausible — the read side's first-by-detail-id
    // rule (deviation (f)) would quietly pick the OLD one and the licensee's edit would vanish.
    assertThat(rowCountFor(EDIT_CAMP, 56)).isEqualTo(1);
    Map<String, Object> row = jdbc().queryForMap(
        "SELECT ILCR_COST_REPORT_DETAIL_ID, VOLUME, COST, UPDATE_USERID "
            + "FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 56", EDIT_CAMP);
    assertThat(((Number) row.get("ILCR_COST_REPORT_DETAIL_ID")).intValue()).isEqualTo(originalId);
    assertThat(((Number) row.get("COST")).intValue()).isEqualTo(777);
    assertThat(row.get("UPDATE_USERID")).isEqualTo(USER);
  }

  @Test
  @DisplayName("upsert INSERTS when the row is absent — the branch every real delivery camp needs")
  void upsertInsertsWhenAbsent() {
    // Delivery holds ZERO detail rows parented by a CAMP_REPORT_ID (Task 1 gate (vii)), so this is the
    // branch a real camp's first edit takes, twelve times over.
    //
    // The empty camp is seeded HERE, inside this rolled-back transaction, rather than borrowing
    // fixture camp 8202 — Schedule5WriteIT.zeroDetailEdit COMMITS twelve detail rows onto 8202, so a
    // fixture-based version of this test passes or fails on class execution order alone (the same
    // collision the PR #242 review found for camps 8203 and "Duplicate Name Camp").
    int campId = repository.nextCampReportId();
    repository.insertCamp(campId, MILL, BARE_YEAR, "Bare Upsert Camp", null, null, null, "N", null,
        USER);
    assertThat(rowCountFor(campId, 56)).isZero();

    repository.upsertCostDetail(campId, 56, new BigDecimal("500"), 1000, USER);

    assertThat(rowCountFor(campId, 56)).isEqualTo(1);
    Map<String, Object> row = jdbc().queryForMap(
        "SELECT VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, "
            + "UPDATE_USERID, UPDATE_TIMESTAMP FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 56", campId);
    assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isZero();
    assertThat(row.get("ITEM_DESCRIPTION")).isNull();
    assertThat(row.get("ENTRY_USERID")).isEqualTo(USER);
    assertThat(row.get("ENTRY_TIMESTAMP")).isNotNull();
    assertThat(row.get("UPDATE_USERID")).isEqualTo(USER);
    assertThat(row.get("UPDATE_TIMESTAMP")).isNotNull();
  }

  @Test
  @DisplayName("the detail UPDATE is scoped by ITEM id — one category cannot overwrite another's row")
  void updateCostDetailIsScopedByItem() {
    Integer item58Before = jdbc().queryForObject(
        "SELECT COST FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 58",
        Integer.class, EDIT_CAMP);

    repository.upsertCostDetail(EDIT_CAMP, 56, null, 1, USER);

    // Without AND ILCR_REPORT_COST_ITEM_ID the statement would rewrite all twelve of the camp's rows
    // to the same value, and the served grid would show one category's figure twelve times.
    assertThat(jdbc().queryForObject(
        "SELECT COST FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 58",
        Integer.class, EDIT_CAMP)).isEqualTo(item58Before);
  }

  @Test
  @DisplayName("the detail UPDATE leaves REVISION_COUNT alone and moves only UPDATE_* (legacy parity)")
  void detailRevisionCountIsNotBumped() {
    Integer before = jdbc().queryForObject(
        "SELECT REVISION_COUNT FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 56",
        Integer.class, EDIT_CAMP);

    repository.upsertCostDetail(EDIT_CAMP, 56, null, 2, USER);

    // The detail row's REVISION_COUNT is not a lock token — legacy moves only UPDATE_* here
    // (Schedule5DAO.java:641-642) and Schedule 6 keeps the same parity.
    assertThat(jdbc().queryForObject(
        "SELECT REVISION_COUNT FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 56",
        Integer.class, EDIT_CAMP)).isEqualTo(before);
  }

  @Test
  @DisplayName("the guarded camp UPDATE returns 1 on a matching revision and 0 on a stale one")
  void guardedUpdateReturnsRowCount() {
    int current = jdbc().queryForObject(
        "SELECT REVISION_COUNT FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = ?",
        Integer.class, EDIT_CAMP);

    assertThat(updateWith(EDIT_CAMP, MILL, EDIT_YEAR, current + 99)).isZero();
    assertThat(updateWith(EDIT_CAMP, MILL, EDIT_YEAR, current)).isEqualTo(1);
    // And the increment happened, so the same token no longer matches.
    assertThat(updateWith(EDIT_CAMP, MILL, EDIT_YEAR, current)).isZero();
  }

  @Test
  @DisplayName("every camp UPDATE is mill/year/category-scoped — the neighbour survives (dev. (M))")
  void campUpdateIsTenancyScoped() {
    Map<String, Object> neighbourBefore = jdbc().queryForMap(
        "SELECT CAMP_NAME, REVISION_COUNT, UPDATE_USERID FROM THE.CAMP_REPORT "
            + "WHERE CAMP_REPORT_ID = 8216");

    // Camp 8216 belongs to mill 675/2020. Addressing it from mill 670 must affect nothing at all.
    assertThat(updateWith(8216, MILL, EDIT_YEAR, 0)).isZero();

    assertThat(jdbc().queryForMap(
        "SELECT CAMP_NAME, REVISION_COUNT, UPDATE_USERID FROM THE.CAMP_REPORT "
            + "WHERE CAMP_REPORT_ID = 8216")).isEqualTo(neighbourBefore);
  }

  private int updateWith(int campId, long millId, int year, int expectedRevision) {
    return repository.updateCamp(campId, millId, year, expectedRevision, "Repo IT Camp",
        new BigDecimal("1.00"), 2, new BigDecimal("3000"), "N", null, USER);
  }

  @Test
  @DisplayName("countCamp is mill/year/category-scoped — it is the 404-vs-409 discriminator")
  void countCampIsScoped() {
    assertThat(repository.countCamp(EDIT_CAMP, MILL, EDIT_YEAR)).isEqualTo(1);
    // Right camp, wrong year -> invisible. Wrong mill -> invisible. Unknown id -> invisible.
    assertThat(repository.countCamp(EDIT_CAMP, MILL, 2019)).isZero();
    assertThat(repository.countCamp(EDIT_CAMP, 675L, EDIT_YEAR)).isZero();
    assertThat(repository.countCamp(999999, MILL, EDIT_YEAR)).isZero();
  }

  @Test
  @DisplayName("countCampsNamed is case-insensitive and scoped to (mill, year, category '5')")
  void nameCountIsCaseInsensitiveAndScoped() {
    // 670/2023 holds exactly one "Duplicate Name Camp" (8206) and every endpoint attempt to add
    // another is a 409, so these three counts are order-safe against the committed suites.
    assertThat(repository.countCampsNamed(MILL, 2023, "Duplicate Name Camp")).isEqualTo(1);
    assertThat(repository.countCampsNamed(MILL, 2023, "duplicate name camp")).isEqualTo(1);
    assertThat(repository.countCampsNamed(MILL, 2023, "DUPLICATE NAME CAMP")).isEqualTo(1);
    // The SCOPE proof seeds its own name inside this rolled-back transaction. It must not probe
    // "Duplicate Name Camp" in other contexts: Schedule5WriteIT.sameNameOtherMillYear_succeeds
    // COMMITS that name into 675/2023 and 670/2019, so zero-count assertions there would hold or
    // fail depending on class execution order (the review's collision finding).
    repository.insertCamp(repository.nextCampReportId(), MILL, EDIT_YEAR, "Scope Probe Camp",
        null, null, null, "N", null, USER);
    assertThat(repository.countCampsNamed(MILL, EDIT_YEAR, "scope probe camp")).isEqualTo(1);
    assertThat(repository.countCampsNamed(MILL, BARE_YEAR, "Scope Probe Camp")).isZero();
    assertThat(repository.countCampsNamed(675L, EDIT_YEAR, "Scope Probe Camp")).isZero();
  }

  @Test
  @DisplayName("countCampsNamedExcluding omits the camp being edited, and takes a non-null name only")
  void excludingNameCountOmitsTheEditedCamp() {
    // Excluding ITSELF -> zero, which is why an unrenamed save is not a self-conflict.
    assertThat(repository.countCampsNamedExcluding(MILL, 2023, "Duplicate Name Camp", 8206))
        .isZero();
    // Excluding some OTHER camp -> the incumbent is still visible.
    assertThat(repository.countCampsNamedExcluding(MILL, 2023, "Duplicate Name Camp", 8201))
        .isEqualTo(1);

    // The two queries are separate ON PURPOSE. A single query with a nullable exclusion parameter
    // needs a null bound into a comparison, and ojdbc infers a CLOB for a null in UPPER(:param),
    // raising ORA-22848 (Schedule4Repository.java:201-225). Both forms must run cleanly with a real
    // string; neither is ever called with a null name (@NotBlank guarantees it).
    assertThatCode(() -> repository.countCampsNamed(MILL, 2023, "x")).doesNotThrowAnyException();
    assertThatCode(() -> repository.countCampsNamedExcluding(MILL, 2023, "x", 1))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("delete removes children FIRST, then the parent — the order delivery's FK demands")
  void deleteRemovesChildrenThenParent() {
    // The family is seeded HERE, inside this rolled-back transaction, rather than borrowing camp
    // 8203 — Schedule5WriteIT.deleteCamp_removesTheWholeFamily COMMITS 8203's deletion, so a
    // fixture-based version of this test passes or fails on class execution order alone (the
    // review's collision finding). Sub-page items 62/68 are included: the family goes together.
    int campId = repository.nextCampReportId();
    repository.insertCamp(campId, MILL, BARE_YEAR, "Family Delete Camp", null, null, null, "N",
        null, USER);
    repository.insertCostDetail(repository.nextCostDetailId(), campId, 56, new BigDecimal("1"), 10,
        USER);
    repository.insertCostDetail(repository.nextCostDetailId(), campId, 62, null, 20, USER);
    repository.insertCostDetail(repository.nextCostDetailId(), campId, 68, null, 30, USER);
    assertThat(childCount(campId)).isEqualTo(3);

    int children = repository.deleteCostDetailsForCamp(campId, MILL, BARE_YEAR);
    assertThat(children).isEqualTo(3);
    // The sub-page rows go too — the camp family goes together (AC5).
    assertThat(childCount(campId)).isZero();

    assertThat(repository.deleteCamp(campId, MILL, BARE_YEAR)).isEqualTo(1);
    assertThat(jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = ?", Integer.class, campId))
        .isZero();
  }

  @Test
  @DisplayName("BOTH deletes are tenancy-scoped — a foreign id affects zero rows, parent and child")
  void deleteIsTenancyScoped() {
    // Legacy's deleteCampFromReport loaded by primary key alone with no tenancy check
    // (Schedule5DAO.java:550). Camp 8216 belongs to mill 675.
    assertThat(repository.deleteCamp(8216, MILL, 2020)).isZero();
    assertThat(jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = 8216", Integer.class))
        .isEqualTo(1);
    // Right camp, wrong year -> also zero.
    assertThat(repository.deleteCamp(8216, 675L, 2023)).isZero();

    // The CHILD delete is scoped the same way (the review's call-order-convention finding): a
    // foreign camp id removes none of the neighbour's detail rows.
    int neighbourChildren = childCount(8216);
    assertThat(repository.deleteCostDetailsForCamp(8216, MILL, 2020)).isZero();
    assertThat(childCount(8216)).isEqualTo(neighbourChildren);
  }

  @Test
  @DisplayName("both sequences yield increasing values ABOVE every seeded fixture id")
  void sequencesAreUsableAndAboveTheFixtures() {
    int firstCamp = repository.nextCampReportId();
    int secondCamp = repository.nextCampReportId();
    int firstDetail = repository.nextCostDetailId();

    assertThat(secondCamp).isGreaterThan(firstCamp);
    // The fixture blocks (82xx, 83xx, 84xx, 85xx+) all sit below the snapshot's sequence starts —
    // 9500 for camps, 9000 for details — so a runtime NEXTVAL can never collide with a seeded row.
    assertThat(firstCamp).isGreaterThanOrEqualTo(9500);
    assertThat(firstDetail).isGreaterThanOrEqualTo(9000);
  }

  @Test
  @DisplayName("insertCamp writes category '5' plus REVISION_COUNT 0 and BOTH audit pairs")
  void insertCampWritesEveryAuditColumn() {
    int id = repository.nextCampReportId();

    repository.insertCamp(id, MILL, 2019, "Repo Inserted Camp", new BigDecimal("12.34"), 5,
        new BigDecimal("1000"), "Y", "A comment.", USER);

    Map<String, Object> row = jdbc().queryForMap("""
        SELECT ILCR_CATEGORY_ID, REPORT_YEAR, ILCR_MILL_ID, CAMP_NAME,
               DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME,
               ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT,
               ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
          FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = ?
        """, id);

    assertThat(row.get("ILCR_CATEGORY_ID")).isEqualTo("5");
    assertThat(((Number) row.get("REPORT_YEAR")).intValue()).isEqualTo(2019);
    assertThat(((Number) row.get("ILCR_MILL_ID")).longValue()).isEqualTo(MILL);
    assertThat(row.get("ISOLATED_CAMP_IND")).isEqualTo("Y");
    assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isZero();
    // Asserted per column: three of the five are only ever exercised by an INSERT, and the local
    // snapshot's NOT NULL declarations catch an omitted column but not a wrong one.
    assertThat(row.get("ENTRY_USERID")).isEqualTo(USER);
    assertThat(row.get("ENTRY_TIMESTAMP")).isNotNull();
    assertThat(row.get("UPDATE_USERID")).isEqualTo(USER);
    assertThat(row.get("UPDATE_TIMESTAMP")).isNotNull();
  }

  @Test
  @DisplayName("a detail row parented ONLY by CAMP_REPORT_ID is accepted (Task 1 gate (iii))")
  void campOnlyParentFkIsAccepted() {
    // In delivery the ICRD_CHK_B_I_U trigger counts populated parent FKs across all nine columns and
    // rejects zero or more than one. A camp-only row yields a count of 1 and passes — this story writes
    // the FIRST such rows that have ever existed. The local snapshot has no trigger, so this test
    // documents the shape rather than proving the trigger's verdict; the verdict itself was read
    // directly from the delivery trigger body and recorded in the story.
    int id = repository.nextCostDetailId();

    repository.insertCostDetail(id, EDIT_CAMP, 56, new BigDecimal("1"), 1, USER);

    List<Map<String, Object>> parents = jdbc().queryForList(
        "SELECT CAMP_REPORT_ID, ILCR_REPORT_SUMMARY_ID FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE ILCR_COST_REPORT_DETAIL_ID = ?", id);
    assertThat(parents).hasSize(1);
    assertThat(parents.getFirst().get("CAMP_REPORT_ID")).isNotNull();
    assertThat(parents.getFirst().get("ILCR_REPORT_SUMMARY_ID")).isNull();
  }

  private int childCount(int campId) {
    return jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE CAMP_REPORT_ID = ?",
        Integer.class, campId);
  }

  // ----- Added 2026-08-10 by the PR #242 review -------------------------------------------------

  @Test
  @DisplayName("updateCostDetail touches ONLY the canonical row when a camp/item holds duplicates")
  void updateCostDetailWritesOnlyTheSurvivingRow() {
    // Nothing in delivery makes (CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID) unique, so this state is
    // permitted by the schema even though gate (vii) found no camp-parented rows at all today. The read
    // path resolves the pair FIRST-BY-DETAIL-ID-WINS (Schedule5Service's putIfAbsent) and IGNORES the
    // rest; an unqualified "WHERE camp AND item" UPDATE wrote all of them, rotating UPDATE_* on rows the
    // API presents as untouched and making "first row wins" meaningless after any edit.
    //
    // The camp is seeded HERE for the same reason the INSERT-branch proof above seeds its own: camp
    // 8202 carries Schedule5WriteIT.zeroDetailEdit's twelve COMMITTED rows once that class has run,
    // and one of them is item 60 — which would make the pair below a triple.
    int campId = repository.nextCampReportId();
    repository.insertCamp(campId, MILL, BARE_YEAR, "Duplicate Rows Camp", null, null, null, "N",
        null, USER);
    int survivingId = repository.nextCostDetailId();
    int ignoredId = survivingId + 1;
    jdbc().update(
        "INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, "
            + "ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID, "
            + "ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP) "
            + "VALUES (?, ?, 60, 100, 111, 0, 'SEED', SYSTIMESTAMP, 'SEED', SYSTIMESTAMP)",
        survivingId, campId);
    jdbc().update(
        "INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, "
            + "ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID, "
            + "ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP) "
            + "VALUES (?, ?, 60, 200, 222, 0, 'SEED', SYSTIMESTAMP, 'SEED', SYSTIMESTAMP)",
        ignoredId, campId);
    assertThat(rowCountFor(campId, 60)).isEqualTo(2);

    int updated = repository.updateCostDetail(campId, 60, new BigDecimal("999"), 888, USER);

    // One row, not two: the guard is the affected-row COUNT, so an unscoped statement fails here even
    // if every column value it wrote happened to be right.
    assertThat(updated).isEqualTo(1);

    Map<String, Object> surviving = jdbc().queryForMap(
        "SELECT COST, UPDATE_USERID FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE ILCR_COST_REPORT_DETAIL_ID = ?", survivingId);
    assertThat(((Number) surviving.get("COST")).intValue()).isEqualTo(888);
    assertThat(surviving.get("UPDATE_USERID")).isEqualTo(USER);

    // The ignored row is untouched down to its audit user — the claim the API makes about it.
    Map<String, Object> ignored = jdbc().queryForMap(
        "SELECT COST, UPDATE_USERID FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE ILCR_COST_REPORT_DETAIL_ID = ?", ignoredId);
    assertThat(((Number) ignored.get("COST")).intValue()).isEqualTo(222);
    assertThat(ignored.get("UPDATE_USERID")).isEqualTo("SEED");
  }

  @Test
  @DisplayName("findTrackStatusForUpdate reads the same code as the unlocked variant, and locks the row")
  void findTrackStatusForUpdateMatchesTheUnlockedRead() {
    // The write gate's read. Worth its own case because FOR UPDATE is the kind of clause that parses
    // everywhere and misbehaves in exactly one place: Spring Data JDBC has to return the projection
    // unchanged, and Oracle has to accept the clause on this single-row query. A write IT would surface
    // a break too, but as a confusing 409 rather than as "the status read is broken".
    assertThat(repository.findTrackStatusForUpdate(MILL, EDIT_YEAR))
        .isEqualTo(repository.findTrackStatus(MILL, EDIT_YEAR))
        .contains("D");
  }

  @Test
  @DisplayName("findTrackStatusForUpdate is empty for a mill/year with no status row — locks nothing")
  void findTrackStatusForUpdateIsEmptyWhenAbsent() {
    // FOR UPDATE on a query matching no rows is a no-op, not an error. This is the path that makes a
    // never-enrolled mill/year a clean 409 from the gate rather than an exception inside it.
    assertThat(repository.findTrackStatusForUpdate(MILL, 1999)).isEmpty();
  }
}
