package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the legacy {@code THE} tables that determine whether a mill/reporting-year context is
 * viewable (AD-3, JdbcClient with named params). SQL only — decisions live in
 * {@link MillContextService}.
 *
 * <p>The "active for the year" shape mirrors the legacy {@code getActiveMills} query: the per-mill
 * XREF status ({@code ACT}/{@code CLS}) joined to an {@code ILCR_MILL_REPORT_STATUS} row for the
 * year supplies the per-year dimension.
 */
@Repository
public class MillContextRepository {

  private final JdbcClient jdbcClient;

  public MillContextRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * The mills for the Home page selection list, ordered by mill number ascending — full legacy
   * {@code getMills()} parity: {@code from Mill m join fetch m.millStatusXref x join fetch
   * x.millReportStatuses order by m.mill_number}. Both legacy inner joins are reproduced: a mill
   * must have its one-to-one {@code ILCR_MILL_STATUS_XREF} row AND at least one
   * {@code ILCR_MILL_REPORT_STATUS} row (any year, i.e. ever enrolled in reporting) to be listed
   * (2026-07-21 review decision: match legacy exactly). Closed ({@code CLS}) mills are included —
   * no status filter — so the closed-mill selection path (S06) stays reachable; no per-user
   * association filter is applied (deferred to the auth story, AR4).
   *
   * <p>The status code comes from {@code THE.ILCR_MILL_STATUS_XREF}, whose PK
   * {@code ILCR_MILL_STATUS_XREF_ID} is the one-to-one mill id (legacy
   * {@code ILCRMillStatusXref} maps {@code @OneToOne @PrimaryKeyJoinColumn} to {@code Mill};
   * consistent with the existing {@link #findMillStatusCodeForYear} join). Reads columns by their
   * {@code THE} names via an explicit {@code RowMapper} (avoids Oracle uppercase-alias mapping
   * pitfalls). {@code MILL_ID} tiebreaker keeps equal/NULL mill numbers deterministically ordered.
   *
   * @return the listable mills as {@link MillSummary}, ordered by mill number ascending
   */
  public List<MillSummary> findAllMills() {
    return jdbcClient.sql(
            """
            SELECT m.MILL_ID, m.MILL_NUMBER, m.MILL_NAME, x.ILCR_MILL_STATUS_CODE
              FROM THE.MILL m
              JOIN THE.ILCR_MILL_STATUS_XREF x
                ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
             WHERE EXISTS (SELECT 1
                             FROM THE.ILCR_MILL_REPORT_STATUS s
                            WHERE s.ILCR_MILL_ID = m.MILL_ID)
             ORDER BY m.MILL_NUMBER, m.MILL_ID
            """)
        .query((rs, rowNum) -> new MillSummary(
            rs.getLong("MILL_ID"),
            // MILL_NUMBER is NUMBER(15); read as String (display identifier, contract-pinned).
            rs.getString("MILL_NUMBER"),
            rs.getString("MILL_NAME"),
            rs.getString("ILCR_MILL_STATUS_CODE")))
        .list();
  }

  /**
   * The opened reporting years for the Home page selection list — every existing
   * {@code THE.ILCR_REPORTING_PERIOD} row — ordered by {@code REPORT_YEAR} descending (BR-03,
   * legacy {@code getReportingPeriods()}).
   *
   * @return the opened years as {@link ReportingYear}, most recent first
   */
  public List<ReportingYear> findAllReportingYears() {
    return jdbcClient.sql(
            """
            SELECT REPORT_YEAR
              FROM THE.ILCR_REPORTING_PERIOD
             ORDER BY REPORT_YEAR DESC
            """)
        .query((rs, rowNum) -> new ReportingYear(rs.getInt("REPORT_YEAR")))
        .list();
  }

  /**
   * The mill's XREF status code ({@code ACT}/{@code CLS}) when an {@code ILCR_MILL_REPORT_STATUS}
   * row exists for this mill and year; empty when the mill is unknown or has no status row for the
   * year (i.e. no per-year context).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the XREF status code, or empty if no per-year context exists
   */
  public Optional<String> findMillStatusCodeForYear(long millId, int year) {
    // Legacy ILCR maps ILCR_MILL_REPORT_STATUS.ILCR_MILL_ID to the shared one-to-one
    // THE.ILCR_MILL_STATUS_XREF.ILCR_MILL_STATUS_XREF_ID primary key.
    return jdbcClient.sql(
            """
            SELECT x.ILCR_MILL_STATUS_CODE
              FROM THE.ILCR_MILL_STATUS_XREF x
              JOIN THE.ILCR_MILL_REPORT_STATUS s
                ON s.ILCR_MILL_ID = x.ILCR_MILL_STATUS_XREF_ID
             WHERE x.ILCR_MILL_STATUS_XREF_ID = :millId
               AND s.REPORT_YEAR = :year
            """)
        .param("millId", millId)
        .param("year", year)
        .query(String.class)
        .optional();
  }

  /**
   * Whether a report summary exists for this mill/year and schedule category (Schedule 1 =
   * category {@code "1"}).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param categoryId the ILCR category id
   * @return true if a matching {@code ILCR_REPORT_SUMMARY} row exists
   */
  public boolean scheduleSummaryExists(long millId, int year, String categoryId) {
    Integer count = jdbcClient.sql(
            """
            SELECT COUNT(*)
              FROM THE.ILCR_REPORT_SUMMARY
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
               AND ILCR_CATEGORY_ID = :categoryId
            """)
        .param("millId", millId)
        .param("year", year)
        .param("categoryId", categoryId)
        .query(Integer.class)
        .single();
    return count != null && count > 0;
  }
}
