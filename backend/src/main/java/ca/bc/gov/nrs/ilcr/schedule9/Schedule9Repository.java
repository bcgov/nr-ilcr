package ca.bc.gov.nrs.ilcr.schedule9;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC access to the legacy {@code THE} Schedule 9 tables (AD-3): explicit {@code @Query}
 * named-param SQL — no derived queries, no {@code CrudRepository} save()/delete(). Every derivation
 * ($/Unit) lives in {@link Schedule9Service} (AD-6 — repositories are dumb SQL).
 *
 * <p>Storage shape (legacy-confirmed; delivery Task-1 gate): a record is one
 * {@code CONTRACTUAL_WORK_REPORT} row (category {@code '9'}, descriptors inline, own
 * {@code REVISION_COUNT}); its Cost + Contractual Item are ONE keyed row in the shared
 * {@code ILCR_COST_REPORT_DETAIL}, joined by {@code CONTRACTUAL_WORK_REPORT_ID} and discriminated by
 * {@code ILCR_REPORT_COST_ITEM_ID} (108–114). Summary-less like Schedules 4/5/6, so {@code
 * trackStatus} comes straight from {@code ILCR_MILL_REPORT_STATUS} (the per-schedule house pattern).
 *
 * <p>The two joined reads resolve the four code-list descriptions in SQL (LEFT JOINs to the
 * reference tables), returned as {@link RecordRow}/{@link CostRow} projections so entities never
 * cross the service boundary.
 */
public interface Schedule9Repository extends Repository<ContractualWorkReportEntity, Integer> {

  /** One {@code CONTRACTUAL_WORK_REPORT} row with its three code-list descriptions resolved. */
  record RecordRow(
      int id, int revisionCount, String contractorId, BigDecimal numberOfUnits, Integer sideSlopePct,
      String comments, String unitCode, String unitCodeDescription, String unitDescription,
      String sourceCode, String sourceCodeDescription, String sourceDescription, String becCode,
      String becDescription) {
  }

  /**
   * One record's cost line: the Contractual Item (108–114 code + its catalogue name), the "Other"
   * item free-text ({@code ITEM_DESCRIPTION}, present only for item 114), and the cost.
   */
  record CostRow(
      int reportId, Integer itemCode, String itemName, String itemDescription, Integer cost) {
  }

  /**
   * The category-'9' records for a mill/year, code descriptions resolved, ordered by id asc (a REST
   * contract needs a stable order; legacy iterates an unordered map).
   */
  @Query("""
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
   * The cost line for each record of the mill/year (Cost + Contractual Item 108–114 + its name),
   * joined to {@code CONTRACTUAL_WORK_REPORT} so the mill/year/category filter applies.
   *
   * <p>Ordered by {@code CONTRACTUAL_WORK_REPORT_ID} then {@code ILCR_COST_REPORT_DETAIL_ID} so the
   * service's lowest-detail-id-wins dedup (a record should own exactly one cost line, but the FK has
   * no unique constraint) is deterministic — Schedule 5's recorded deviation (c).
   */
  @Query("""
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
  @Query("""
      SELECT ILCR_MILL_REPORT_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  Optional<String> findTrackStatus(@Param("millId") long millId, @Param("year") int year);
}
