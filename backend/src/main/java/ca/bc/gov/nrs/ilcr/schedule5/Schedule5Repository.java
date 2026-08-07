package ca.bc.gov.nrs.ilcr.schedule5;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 5 tables (AD-3): explicit
 * {@code @Query} named-param SQL + {@code @Table} record entities — no derived queries, no
 * {@code CrudRepository}. SQL stays explicit because the model is a legacy-projection; every
 * derivation lives in {@link Schedule5Service} (AD-6 — repositories are dumb SQL).
 *
 * <p>Storage shape (delivery-DB confirmed, Story 7.1 Task 1): a camp is one {@code CAMP_REPORT} row
 * (category {@code '5'}, descriptors inline, own {@code REVISION_COUNT}); its twelve category
 * amounts are keyed rows in the shared {@code ILCR_COST_REPORT_DETAIL}, joined by
 * {@code CAMP_REPORT_ID} and discriminated by {@code ILCR_REPORT_COST_ITEM_ID}. There is no
 * category-{@code '5'} {@code ILCR_REPORT_SUMMARY} row (gate (ii): zero rows), so
 * {@code trackStatus} comes straight from {@code ILCR_MILL_REPORT_STATUS} — the per-schedule
 * {@code findTrackStatus} house pattern (Schedules 1/2/6/8 each declare their own).
 *
 * <p>The public {@code default} methods expose plain service-facing records ({@link CampRow},
 * {@link DetailRow}); the {@code @Query} methods are the explicit SQL, so entities never cross the
 * service boundary.
 */
public interface Schedule5Repository extends Repository<CampReportEntity, Integer> {

  /**
   * One Schedule 5 camp (a {@code CAMP_REPORT} row); indicator stays raw {@code Y}/{@code N}.
   *
   * <p>{@code campId} and {@code revisionCount} are primitives because both columns are
   * {@code NOT NULL}; the nullable descriptors stay boxed.
   */
  record CampRow(int campId, String campName, BigDecimal distanceToOperatingArea,
      Integer sizeOfCamp, BigDecimal associatedCampVolume, String isolatedCampInd, String comments,
      int revisionCount) {
  }

  /**
   * One keyed category-amount row; {@code costItemId} decides which category it feeds.
   *
   * <p>{@code costItemId} is boxed even though delivery declares the column {@code NOT NULL} (Task
   * 1 gate (iii)): the V1 snapshot does not, and an unboxing conversion here would turn the one
   * unrecognized item id the service cannot name into an NPE that 500s the entire document —
   * defeating the log-and-drop path {@link Schedule5Service} exists to provide. A null id is
   * dropped by the same branch that drops item 57.
   */
  record DetailRow(int detailId, int campId, Integer costItemId, BigDecimal volume, Integer cost) {
  }

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
  @Query("""
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
        .map(e -> new CampRow(e.campReportId(), e.campName(), e.distanceToOperatingArea(),
            e.campSizeCapacity(), e.associatedCampVolume(), e.isolatedCampInd(), e.comments(),
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
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.CAMP_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID,
             d.VOLUME, d.COST
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
        .map(d -> new DetailRow(
            d.detailId(), d.campReportId(), d.costItemId(), d.volume(), d.cost()))
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
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);
}
