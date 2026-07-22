package ca.bc.gov.nrs.ilcr.millcontext;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Reads the legacy {@code THE} tables that determine whether a mill/reporting-year context is
 * viewable (AD-3: Spring Data JDBC — repository interface + {@code @Table} record entity
 * {@link MillStatusXref} + explicit {@code @Query} named-parameter SQL). SQL only — decisions live in
 * {@link MillContextService}.
 *
 * <p>The "active for the year" shape mirrors the legacy {@code getActiveMills} query: the per-mill
 * XREF status ({@code ACT}/{@code CLS}) joined to an {@code ILCR_MILL_REPORT_STATUS} row for the
 * year supplies the per-year dimension.
 */
public interface MillContextRepository extends Repository<MillStatusXref, Long> {

  /**
   * The mill's XREF status code ({@code ACT}/{@code CLS}) when an {@code ILCR_MILL_REPORT_STATUS}
   * row exists for this mill and year; empty when the mill is unknown or has no status row for the
   * year (i.e. no per-year context).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the XREF status code, or empty if no per-year context exists
   */
  @Query("""
      SELECT x.ILCR_MILL_STATUS_CODE
        FROM THE.ILCR_MILL_STATUS_XREF x
        JOIN THE.ILCR_MILL_REPORT_STATUS s
          ON s.ILCR_MILL_ID = x.ILCR_MILL_STATUS_XREF_ID
       WHERE x.ILCR_MILL_STATUS_XREF_ID = :millId
         AND s.REPORT_YEAR = :year
      """)
  Optional<String> findMillStatusCodeForYear(@Param("millId") long millId, @Param("year") int year);

  /**
   * Whether a report summary exists for this mill/year and schedule category (Schedule 1 =
   * category {@code "1"}).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param categoryId the ILCR category id
   * @return true if a matching {@code ILCR_REPORT_SUMMARY} row exists
   */
  default boolean scheduleSummaryExists(long millId, int year, String categoryId) {
    return countScheduleSummaries(millId, year, categoryId) > 0;
  }

  /** Count of {@code ILCR_REPORT_SUMMARY} rows for a mill/year/category (backs {@link #scheduleSummaryExists}). */
  @Query("""
      SELECT COUNT(*)
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = :categoryId
      """)
  long countScheduleSummaries(
      @Param("millId") long millId, @Param("year") int year, @Param("categoryId") String categoryId);
}
