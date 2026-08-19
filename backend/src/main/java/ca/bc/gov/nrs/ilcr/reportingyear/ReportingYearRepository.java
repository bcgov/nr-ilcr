package ca.bc.gov.nrs.ilcr.reportingyear;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes the legacy {@code THE} tables that opening a reporting year touches (UC-RY-001).
 * Per active mill, opening a year creates: one {@code ILCR_REPORTING_PERIOD} row (the year exists →
 * selectable on Home), one {@code ILCR_MILL_REPORT_STATUS} row (both tracks → {@code validateMillYearActive}
 * passes), and one {@code ILCR_REPORT_CATEGORY} row per schedule category (Draft, reportable-detail Y —
 * the delivery DB pre-seeds these on open, verified against the DEV database). {@code ILCR_REPORT_SUMMARY}
 * is NOT created here — it appears on first schedule save. Every VALUE is a bound named parameter; the
 * full audit quartet ({@code ENTRY_/UPDATE_USERID} + timestamps) and {@code REVISION_COUNT} are stamped
 * explicitly because all are NOT NULL in delivery (AD-11).
 */
@Repository
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class ReportingYearRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public ReportingYearRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The highest opened reporting year, or {@code null} when none exist (first-time setup). */
  public Integer findMaxReportYear() {
    return jdbc.queryForObject(
        "SELECT MAX(REPORT_YEAR) FROM THE.ILCR_REPORTING_PERIOD", new MapSqlParameterSource(),
        Integer.class);
  }

  /** The opened reporting years, most recent first. */
  public List<Integer> findOpenYears() {
    return jdbc.queryForList(
        "SELECT REPORT_YEAR FROM THE.ILCR_REPORTING_PERIOD ORDER BY REPORT_YEAR DESC",
        new MapSqlParameterSource(), Integer.class);
  }

  /**
   * The ids of the active ({@code ACT}) mills — the whitelist that receives report-status rows for a
   * new year (DL-22: closed mills are excluded). Mirrors {@code MillContextService.STATUS_ACTIVE}.
   */
  public List<Long> findActiveMillIds() {
    return jdbc.queryForList(
        "SELECT ILCR_MILL_STATUS_XREF_ID FROM THE.ILCR_MILL_STATUS_XREF "
            + "WHERE ILCR_MILL_STATUS_CODE = 'ACT' ORDER BY ILCR_MILL_STATUS_XREF_ID",
        new MapSqlParameterSource(), Long.class);
  }

  /**
   * Insert the reporting period row for the new year: official start = the creation date, official end
   * = December 31 of that year (BR-06). {@code ENTRY_/UPDATE_USERID}, both timestamps, and
   * {@code REVISION_COUNT} are all NOT NULL in delivery, so all are stamped explicitly.
   */
  public void insertReportingPeriod(int year, LocalDate start, LocalDate end, String user) {
    jdbc.update(
        "INSERT INTO THE.ILCR_REPORTING_PERIOD "
            + "(REPORT_YEAR, REPORT_OFFICIAL_START_DATE, REPORT_OFFICIAL_END_DATE, REVISION_COUNT, "
            + "ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP) "
            + "VALUES (:year, :start, :end, 0, :user, SYSTIMESTAMP, :user, SYSTIMESTAMP)",
        new MapSqlParameterSource()
            .addValue("year", year)
            .addValue("start", start)
            .addValue("end", end)
            .addValue("user", user));
  }

  /**
   * Insert one mill's report-status row for the new year, initializing BOTH independent tracks
   * (Schedules 1–10 and Schedule 11) to the same status code and report-completed indicator (A-8:
   * Draft, not completed). The audit quartet + {@code REVISION_COUNT} are NOT NULL in delivery and
   * stamped explicitly.
   */
  public void insertMillReportStatus(
      int year, long millId, String statusCode, String silvicultureCode, String completedInd,
      String user) {
    jdbc.update(
        "INSERT INTO THE.ILCR_MILL_REPORT_STATUS "
            + "(REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, "
            + "REPORT_COMPLETED_IND, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, "
            + "UPDATE_TIMESTAMP) "
            + "VALUES (:year, :millId, :statusCode, :silvicultureCode, :completedInd, 0, :user, "
            + "SYSTIMESTAMP, :user, SYSTIMESTAMP)",
        new MapSqlParameterSource()
            .addValue("year", year)
            .addValue("millId", millId)
            .addValue("statusCode", statusCode)
            .addValue("silvicultureCode", silvicultureCode)
            .addValue("completedInd", completedInd)
            .addValue("user", user));
  }

  /**
   * Insert one mill's per-category record for the new year (Draft state, reportable-detail Y) — the
   * delivery DB pre-seeds one row per schedule category on open (verified against DEV). The audit
   * quartet + {@code REVISION_COUNT} are NOT NULL in delivery and stamped explicitly.
   */
  public void insertReportCategory(int year, long millId, String categoryId, String user) {
    jdbc.update(
        "INSERT INTO THE.ILCR_REPORT_CATEGORY "
            + "(REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CATEGORY_STATE_CODE, "
            + "REPORTABLE_DETAIL_IND, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, "
            + "UPDATE_TIMESTAMP) "
            + "VALUES (:year, :millId, :categoryId, 'D', 'Y', 0, :user, SYSTIMESTAMP, :user, "
            + "SYSTIMESTAMP)",
        new MapSqlParameterSource()
            .addValue("year", year)
            .addValue("millId", millId)
            .addValue("categoryId", categoryId)
            .addValue("user", user));
  }
}
