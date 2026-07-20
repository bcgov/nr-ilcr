package ca.bc.gov.nrs.ilcr.millcontext;

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
   * The mill's XREF status code ({@code ACT}/{@code CLS}) when an {@code ILCR_MILL_REPORT_STATUS}
   * row exists for this mill and year; empty when the mill is unknown or has no status row for the
   * year (i.e. no per-year context).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the XREF status code, or empty if no per-year context exists
   */
  public Optional<String> findMillStatusCodeForYear(long millId, int year) {
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
