package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Reads the legacy {@code THE} tables that determine whether a mill/reporting-year context is
 * viewable (AD-3: Spring Data JDBC — repository interface + {@code @Table} record entity
 * {@link MillStatusXref} + explicit {@code @Query} named-parameter SQL). SQL only — decisions live in
 * {@link MillContextService}.
 * Spring Data JDBC access to the legacy {@code THE} tables that determine whether a mill/reporting-year
 * context is viewable (AD-3: repository interface + {@code @Table} record entities + explicit
 * {@code @Query} named-parameter SQL; migrated from the retired {@code JdbcClient} idiom, behavior
 * preserved). SQL only — decisions live in {@link MillContextService}; entities never cross the
 * service boundary (the {@code default} methods below map them to DTO / projection records).
 *
 * <p>The "active for the year" shape mirrors the legacy {@code getActiveMills} query: the per-mill
 * XREF status ({@code ACT}/{@code CLS}) joined to an {@code ILCR_MILL_REPORT_STATUS} row for the
 * year supplies the per-year dimension.
 */
public interface MillContextRepository extends Repository<SelectableMillEntity, Long> {

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
   * @return the listable mills as {@link MillSummary}, ordered by mill number ascending
   */
  default List<MillSummary> findAllMills() {
    return findAllMillEntities().stream()
        .map(e -> new MillSummary(e.millId(), e.millNumber(), e.millName(), e.millStatusCode()))
        .toList();
  }

  /**
   * Backing read for {@link #findAllMills()}. The status code comes from
   * {@code THE.ILCR_MILL_STATUS_XREF}, whose PK {@code ILCR_MILL_STATUS_XREF_ID} is the one-to-one
   * mill id (legacy {@code ILCRMillStatusXref} maps {@code @OneToOne @PrimaryKeyJoinColumn} to
   * {@code Mill}). {@code MILL_ID} tiebreaker keeps equal/NULL mill numbers deterministically
   * ordered. Columns map to {@link SelectableMillEntity} by their {@code THE} names.
   */
  @Query("""
      SELECT m.MILL_ID, m.MILL_NUMBER, m.MILL_NAME, x.ILCR_MILL_STATUS_CODE
        FROM THE.MILL m
        JOIN THE.ILCR_MILL_STATUS_XREF x
          ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
       WHERE EXISTS (SELECT 1
                       FROM THE.ILCR_MILL_REPORT_STATUS s
                      WHERE s.ILCR_MILL_ID = m.MILL_ID)
       ORDER BY m.MILL_NUMBER, m.MILL_ID
      """)
  List<SelectableMillEntity> findAllMillEntities();

  /**
   * The opened reporting years for the Home page selection list — every existing
   * {@code THE.ILCR_REPORTING_PERIOD} row — ordered by {@code REPORT_YEAR} descending (BR-03,
   * legacy {@code getReportingPeriods()}).
   *
   * @return the opened years as {@link ReportingYear}, most recent first
   */
  default List<ReportingYear> findAllReportingYears() {
    return findAllReportYearValues().stream().map(ReportingYear::new).toList();
  }

  @Query("""
      SELECT REPORT_YEAR
        FROM THE.ILCR_REPORTING_PERIOD
       ORDER BY REPORT_YEAR DESC
      """)
  List<Integer> findAllReportYearValues();

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
  default Optional<MillSummary> findSelectableMillById(long millId) {
    return findSelectableMillEntityById(millId)
        .map(e -> new MillSummary(e.millId(), e.millNumber(), e.millName(), e.millStatusCode()));
  }

  @Query("""
      SELECT m.MILL_ID, m.MILL_NUMBER, m.MILL_NAME, x.ILCR_MILL_STATUS_CODE
        FROM THE.MILL m
        JOIN THE.ILCR_MILL_STATUS_XREF x
          ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
       WHERE m.MILL_ID = :millId
         AND EXISTS (SELECT 1
                       FROM THE.ILCR_MILL_REPORT_STATUS s
                      WHERE s.ILCR_MILL_ID = m.MILL_ID)
      """)
  Optional<SelectableMillEntity> findSelectableMillEntityById(@Param("millId") long millId);

  /**
   * Whether the reporting year is opened (an {@code THE.ILCR_REPORTING_PERIOD} row exists).
   *
   * @param year the reporting year
   * @return true when the year is opened
   */
  default boolean reportingYearExists(int year) {
    return countReportingYear(year) > 0;
  }

  @Query("""
      SELECT COUNT(*)
        FROM THE.ILCR_REPORTING_PERIOD
       WHERE REPORT_YEAR = :year
      """)
  long countReportingYear(@Param("year") int year);

  /**
   * The two independent track status codes for a (mill, year) pair — 0..1 row (PK). Either column
   * may be NULL (legacy would NPE on a NULL silviculture code; Story 1.2 tolerates it — the track
   * simply has no status).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the pair of codes, or empty when no {@code ILCR_MILL_REPORT_STATUS} row exists (S07)
   */
  default Optional<TrackCodes> findTrackStatusCodes(long millId, int year) {
    return findMillReportStatus(millId, year)
        .map(e -> new TrackCodes(e.schedules1To10Code(), e.schedule11Code()));
  }

  @Query("""
      SELECT s.ILCR_MILL_ID, s.ILCR_MILL_REPORT_STATUS_CODE, s.MILL_SILVICULTUR_STATUS_CODE
        FROM THE.ILCR_MILL_REPORT_STATUS s
       WHERE s.ILCR_MILL_ID = :millId
         AND s.REPORT_YEAR = :year
      """)
  Optional<MillReportStatusEntity> findMillReportStatus(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * The description for a report-status code from {@code THE.ILCR_MILL_REPORT_STATUS_CODE}
   * (legacy {@code ILCRMillReportStatusCode} lookup cache).
   *
   * @param code the status code ({@code D}/{@code S}/{@code V}/{@code O})
   * @return the description, or empty when the code has no lookup row
   */
  @Query("""
      SELECT DESCRIPTION
        FROM THE.ILCR_MILL_REPORT_STATUS_CODE
       WHERE ILCR_MILL_REPORT_STATUS_CODE = :code
      """)
  Optional<String> findStatusDescription(@Param("code") String code);

  /**
   * The per-status display-date strings for a (mill, year) pair from
   * {@code THE.ILCR_MILL_REPORT_STATUS_RPT_VW} (a VIEW on the delivery DB; the test snapshot stands
   * it in as a table — see {@code V6}). Values carry the legacy 3-character prefix; stripping is the
   * service's job (mirrors {@code UserSessionMB.substring(3)}). Empty when the view has no row.
   *
   * <p>Uses first-row semantics (list + {@code findFirst}) rather than a single-row read because
   * {@code ILCR_MILL_REPORT_STATUS_RPT_VW} is a VIEW with no PK/uniqueness guarantee: legacy read it
   * as a list and took {@code get(0)} ({@code MillReportStatusDAO.getMillReportStatusList}), so a
   * multi-row view must resolve to the first row, not throw {@code IncorrectResultSizeDataAccessException}.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the raw date strings, or empty when the view has no row for the pair
   */
  default Optional<StatusDates> findStatusDates(long millId, int year) {
    return findStatusDateEntities(millId, year).stream()
        .findFirst()
        .map(e -> new StatusDates(
            e.open1To10(), e.draft1To10(), e.submit1To10(), e.verify1To10(),
            e.draftSilvi(), e.submitSilvi(), e.verifySilvi()));
  }

  @Query("""
      SELECT ILCR_MILL_ID, MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE,
             MILL_STATUS_VERIFY_DATE, SILVI_STATUS_DRAFT_DATE, SILVI_STATUS_SUBMIT_DATE,
             SILVI_STATUS_VERIFY_DATE
        FROM THE.ILCR_MILL_REPORT_STATUS_RPT_VW
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
      """)
  List<StatusDatesEntity> findStatusDateEntities(
      @Param("millId") long millId, @Param("year") int year);

  /**
   * Projection: the two independent track status codes of one {@code ILCR_MILL_REPORT_STATUS} row.
   *
   * @param schedules1To10Code the Schedules 1–10 code ({@code ILCR_MILL_REPORT_STATUS_CODE}); nullable
   * @param schedule11Code the Schedule 11 code ({@code MILL_SILVICULTUR_STATUS_CODE}); nullable
   */
  record TrackCodes(String schedules1To10Code, String schedule11Code) {}

  /**
   * Projection: the raw (still-prefixed) per-status date strings of one
   * {@code ILCR_MILL_REPORT_STATUS_RPT_VW} row. Any component may be null.
   */
  record StatusDates(
      String open1To10, String draft1To10, String submit1To10, String verify1To10,
      String draftSilvi, String submitSilvi, String verifySilvi) {}

  /**
   * The mill's XREF status code ({@code ACT}/{@code CLS}) when an {@code ILCR_MILL_REPORT_STATUS}
   * row exists for this mill and year; empty when the mill is unknown or has no status row for the
   * year (i.e. no per-year context).
   *
   * <p>Legacy ILCR maps {@code ILCR_MILL_REPORT_STATUS.ILCR_MILL_ID} to the shared one-to-one
   * {@code THE.ILCR_MILL_STATUS_XREF.ILCR_MILL_STATUS_XREF_ID} primary key.
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
    return countScheduleSummary(millId, year, categoryId) > 0;
  }

  @Query("""
      SELECT COUNT(*)
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE ILCR_MILL_ID = :millId
         AND REPORT_YEAR = :year
         AND ILCR_CATEGORY_ID = :categoryId
      """)
  long countScheduleSummary(
      @Param("millId") long millId, @Param("year") int year, @Param("categoryId") String categoryId);
}
