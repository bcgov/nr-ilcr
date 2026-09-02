package ca.bc.gov.nrs.ilcr.millreportstatus;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Reads every mill's reporting-cycle status for one reporting year — the Mill Status Report table
 * (UC-MRPT-004). AD-3: repository interface + {@code @Table} record entity + explicit
 * {@code @Query} named-parameter SQL. SQL only — decisions live in {@link MillReportStatusService}.
 *
 * <p>ONE query for the whole year, not one per mill. Legacy read the view for the year and then
 * re-queried {@code MILL} (with its zone, status xref, client location and both contacts eagerly
 * mapped) inside a per-row loop — {@code MillReportStatusDAO.java:115}, {@code getMills(millId)} —
 * so a 17-mill table cost 18+ round trips to render one page. The join below is the same data in a
 * single pass.
 */
@org.springframework.stereotype.Repository
public interface MillReportStatusRepository extends Repository<MillReportStatusRowEntity, Long> {

  /**
   * Every mill with a report-status row for the year, with its number, name, per-year active
   * status, region code and all seven milestone strings.
   *
   * <p>Unscoped by design, and WIDER than legacy's row set in two ways — both deliberate, and both
   * visible in a side-by-side comparison, so read this before filing a bug against the row count.
   *
   * <ol>
   *   <li><b>No user scope.</b> Legacy passed the logged-in user's associated mills into a {@code
   *       Restrictions.in} ({@code MillReportStatusDAO.java:173}), but under DL-23 the Auditor role
   *       is merged into ADMIN and this page is administrator-only, so the mill set is every mill.
   *       There is deliberately no user or mill predicate — and therefore no reachable empty-{@code
   *       IN ()} defect to port either.
   *   <li><b>No "active today" filter.</b> That same mill list came from {@code
   *       getMillSelection(null, true)} → {@code UserSessionDAO.getActiveMills()}, which keeps only
   *       mills whose {@code ILCR_MILL_STATUS_XREF.ILCR_MILL_STATUS_CODE} is {@code 'ACT'} — the
   *       status TODAY. So legacy silently dropped every mill that has since closed, however
   *       completely it reported in the year on screen. This query keeps them, with the Active
   *       column reporting their status FOR THAT YEAR. Measured on {@code fortmp1} 2026-09-02, that
   *       is 1-3 extra rows on 2015-2018 (2017: 19 here, 16 in legacy) and 0 on 2019-2022. It also
   *       means this table is the first place Active can read {@code No}: on that data every mill
   *       with a non-{@code ACT} year is also closed today, so legacy's Active column could only
   *       ever say {@code Yes}. Listing closed mills is ratified story scope, not an oversight.
   * </ol>
   *
   * <p>{@code THE.MILL} is joined INNER, matching the driving-table contract: the table lists mills
   * that have a report status for the year, and a view row whose {@code ILCR_MILL_ID} has no {@code
   * MILL} row is referential corruption with no number or name to display. This mirrors {@code
   * MillInformationRepository.findSectionRows}.
   *
   * <p>{@code ILCR_MILL_STATUS_XREF} is deliberately NOT joined. The Active column reads the VIEW's
   * {@code ILCR_MILL_STATUS_CODE} — the mill's status FOR THAT REPORTING YEAR — not the xref's,
   * which is its status today. Legacy reads the view ({@code MillReportStatusDAO.java:106}), and
   * taking the xref's would make a 2021 table change the moment a mill closed.
   *
   * <p>{@code m.ISP_SELL_PRICE_ZONE_CODE} is selected as a bare CODE, not joined to its description
   * table — note that the description table is {@code THE.APPRAISAL_SELL_PRICE_ZONE_CODE}, NOT the
   * column's namesake; see {@code MillInformationRepository.findZoneDescriptions}. Keeping it out
   * of this statement is the point: a shared ministry code table reached through a PUBLIC synonym
   * whose target is missing fails the whole statement at parse time with ORA-00942, join type
   * notwithstanding, so joining it here would trade one blank column for the whole table.
   *
   * <p>Ordered by mill id, matching legacy's {@code Criteria} {@code Order.asc("ilcr_mill_id")}
   * ({@code MillReportStatusDAO.java:175}) and Story 19.1's committed ordering — even though the
   * table's first column is mill NUMBER. The user can sort any scalar column client-side; this is
   * only the arrival order.
   *
   * @param year the reporting year
   * @return one row per mill, ordered by mill id; empty when the year has no report-status rows
   */
  @Query(
      """
      SELECT v.ILCR_MILL_ID,
             m.MILL_NUMBER,
             m.MILL_NAME,
             v.ILCR_MILL_STATUS_CODE,
             m.ISP_SELL_PRICE_ZONE_CODE AS REGION_CODE,
             v.MILL_STATUS_OPEN_DATE,
             v.MILL_STATUS_DRAFT_DATE,
             v.MILL_STATUS_SUBMIT_DATE,
             v.MILL_STATUS_VERIFY_DATE,
             v.SILVI_STATUS_DRAFT_DATE,
             v.SILVI_STATUS_SUBMIT_DATE,
             v.SILVI_STATUS_VERIFY_DATE
        FROM THE.ILCR_MILL_REPORT_STATUS_RPT_VW v
        JOIN THE.MILL m
          ON m.MILL_ID = v.ILCR_MILL_ID
       WHERE v.REPORT_YEAR = :year
       ORDER BY v.ILCR_MILL_ID
      """)
  List<MillReportStatusRowEntity> findStatusRows(@Param("year") int year);
}
