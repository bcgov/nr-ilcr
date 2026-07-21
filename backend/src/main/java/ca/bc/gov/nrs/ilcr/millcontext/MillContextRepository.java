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
   * Shared projection for the {@code MILL} ⋈ {@code ILCR_MILL_STATUS_XREF} selection columns, used
   * by both {@link #findAllMills()} and {@link #findSelectableMillById(long)} so the two queries
   * cannot drift. Reads by {@code THE} column name; {@code MILL_NUMBER} ({@code NUMBER(15)}) is read
   * as a String display identifier (contract-pinned).
   */
  private static final org.springframework.jdbc.core.RowMapper<MillSummary> MILL_SUMMARY_MAPPER =
      (rs, rowNum) -> new MillSummary(
          rs.getLong("MILL_ID"),
          rs.getString("MILL_NUMBER"),
          rs.getString("MILL_NAME"),
          rs.getString("ILCR_MILL_STATUS_CODE"));

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
        .query(MILL_SUMMARY_MAPPER)
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
   * The selectable mill with this id — same join and enrollment predicate as {@link #findAllMills()}
   * (legacy {@code getMills()} parity: status xref present AND at least one
   * {@code ILCR_MILL_REPORT_STATUS} row for any year). Empty when the id is unknown or the mill is
   * not selectable (Story 1.2 resolves that to 404, matching what legacy's server-controlled
   * dropdown made unreachable).
   *
   * @param millId the mill id
   * @return the mill as {@link MillSummary}, or empty when not selectable
   */
  public Optional<MillSummary> findSelectableMillById(long millId) {
    return jdbcClient.sql(
            """
            SELECT m.MILL_ID, m.MILL_NUMBER, m.MILL_NAME, x.ILCR_MILL_STATUS_CODE
              FROM THE.MILL m
              JOIN THE.ILCR_MILL_STATUS_XREF x
                ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
             WHERE m.MILL_ID = :millId
               AND EXISTS (SELECT 1
                             FROM THE.ILCR_MILL_REPORT_STATUS s
                            WHERE s.ILCR_MILL_ID = m.MILL_ID)
            """)
        .param("millId", millId)
        .query(MILL_SUMMARY_MAPPER)
        .optional();
  }

  /**
   * Whether the reporting year is opened (an {@code THE.ILCR_REPORTING_PERIOD} row exists).
   *
   * @param year the reporting year
   * @return true when the year is opened
   */
  public boolean reportingYearExists(int year) {
    Integer count = jdbcClient.sql(
            """
            SELECT COUNT(*)
              FROM THE.ILCR_REPORTING_PERIOD
             WHERE REPORT_YEAR = :year
            """)
        .param("year", year)
        .query(Integer.class)
        .single();
    return count != null && count > 0;
  }

  /**
   * The two independent track status codes for a (mill, year) pair — 0..1 row (PK). Either column
   * may be NULL (legacy would NPE on a NULL silviculture code; Story 1.2 tolerates it — the track
   * simply has no status).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the pair of codes, or empty when no {@code ILCR_MILL_REPORT_STATUS} row exists (S07)
   */
  public Optional<TrackCodes> findTrackStatusCodes(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT s.ILCR_MILL_REPORT_STATUS_CODE, s.MILL_SILVICULTUR_STATUS_CODE
              FROM THE.ILCR_MILL_REPORT_STATUS s
             WHERE s.ILCR_MILL_ID = :millId
               AND s.REPORT_YEAR = :year
            """)
        .param("millId", millId)
        .param("year", year)
        .query((rs, rowNum) -> new TrackCodes(
            rs.getString("ILCR_MILL_REPORT_STATUS_CODE"),
            rs.getString("MILL_SILVICULTUR_STATUS_CODE")))
        .optional();
  }

  /**
   * The description for a report-status code from {@code THE.ILCR_MILL_REPORT_STATUS_CODE}
   * (legacy {@code ILCRMillReportStatusCode} lookup cache).
   *
   * @param code the status code ({@code D}/{@code S}/{@code V}/{@code O})
   * @return the description, or empty when the code has no lookup row
   */
  public Optional<String> findStatusDescription(String code) {
    return jdbcClient.sql(
            """
            SELECT DESCRIPTION
              FROM THE.ILCR_MILL_REPORT_STATUS_CODE
             WHERE ILCR_MILL_REPORT_STATUS_CODE = :code
            """)
        .param("code", code)
        .query(String.class)
        .optional();
  }

  /**
   * The per-status display-date strings for a (mill, year) pair from
   * {@code THE.ILCR_MILL_REPORT_STATUS_RPT_VW} (a VIEW on the delivery DB; the test snapshot stands
   * it in as a table — see {@code V6}). Values carry the legacy 3-character prefix; stripping is the
   * service's job (mirrors {@code UserSessionMB.substring(3)}). Empty when the view has no row.
   *
   * <p>Uses first-row semantics ({@code .list()} + {@code findFirst}) rather than {@code .optional()}
   * because {@code ILCR_MILL_REPORT_STATUS_RPT_VW} is a VIEW with no PK/uniqueness guarantee: legacy
   * read it as a list and took {@code get(0)} ({@code MillReportStatusDAO.getMillReportStatusList}),
   * so a multi-row view must resolve to the first row, not throw {@code IncorrectResultSizeDataAccessException}.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the raw date strings, or empty when the view has no row for the pair
   */
  public Optional<StatusDates> findStatusDates(long millId, int year) {
    return jdbcClient.sql(
            """
            SELECT MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE,
                   MILL_STATUS_VERIFY_DATE, SILVI_STATUS_DRAFT_DATE, SILVI_STATUS_SUBMIT_DATE,
                   SILVI_STATUS_VERIFY_DATE
              FROM THE.ILCR_MILL_REPORT_STATUS_RPT_VW
             WHERE ILCR_MILL_ID = :millId
               AND REPORT_YEAR = :year
            """)
        .param("millId", millId)
        .param("year", year)
        .query((rs, rowNum) -> new StatusDates(
            rs.getString("MILL_STATUS_OPEN_DATE"),
            rs.getString("MILL_STATUS_DRAFT_DATE"),
            rs.getString("MILL_STATUS_SUBMIT_DATE"),
            rs.getString("MILL_STATUS_VERIFY_DATE"),
            rs.getString("SILVI_STATUS_DRAFT_DATE"),
            rs.getString("SILVI_STATUS_SUBMIT_DATE"),
            rs.getString("SILVI_STATUS_VERIFY_DATE")))
        .list()
        .stream()
        .findFirst();
  }

  /**
   * Projection: the two independent track status codes of one {@code ILCR_MILL_REPORT_STATUS} row.
   *
   * @param schedules1To10Code the Schedules 1–10 code ({@code ILCR_MILL_REPORT_STATUS_CODE}); nullable
   * @param schedule11Code the Schedule 11 code ({@code MILL_SILVICULTUR_STATUS_CODE}); nullable
   */
  public record TrackCodes(String schedules1To10Code, String schedule11Code) {}

  /**
   * Projection: the raw (still-prefixed) per-status date strings of one
   * {@code ILCR_MILL_REPORT_STATUS_RPT_VW} row. Any component may be null.
   */
  public record StatusDates(
      String open1To10, String draft1To10, String submit1To10, String verify1To10,
      String draftSilvi, String submitSilvi, String verifySilvi) {}

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
