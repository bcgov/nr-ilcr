package ca.bc.gov.nrs.ilcr.millcontext;

import org.springframework.stereotype.Service;

/**
 * Single owner of mill/reporting-year validation for schedule-workflow endpoints (AD-4). Schedule
 * services call this and never re-check. Closed-mill status codes are the legacy {@code MILL_STATUS_CODES}.
 */
@Service
public class MillContextService {

  private static final String STATUS_ACTIVE = "ACT";

  private final MillContextRepository repository;

  public MillContextService(MillContextRepository repository) {
    this.repository = repository;
  }

  /**
   * Validate that the given schedule is viewable for the mill/reporting-year context.
   *
   * <p>Guard order (UC-SCH1-001 S20/S21):
   * <ol>
   *   <li>No per-year context (unknown mill or no report-status row) &rarr;
   *       {@link ScheduleNotFoundException} (404).</li>
   *   <li>Mill not active ({@code ACT}) for the year &rarr; {@link MillClosedException} (409).</li>
   *   <li>No schedule summary for the category &rarr; {@link ScheduleNotFoundException} (404).</li>
   * </ol>
   * Returns normally when the context is viewable. Legacy mill status is {@code ACT}/{@code CLS};
   * we whitelist {@code ACT} rather than blacklisting {@code CLS} so any unexpected status is treated
   * as not-viewable rather than silently viewable.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param categoryId the schedule category id (Schedule 1 = {@code "1"})
   */
  public void validateScheduleViewable(long millId, int year, String categoryId) {
    String millStatus = repository.findMillStatusCodeForYear(millId, year)
        .orElseThrow(ScheduleNotFoundException::new);

    if (!STATUS_ACTIVE.equalsIgnoreCase(millStatus)) {
      throw new MillClosedException();
    }

    if (!repository.scheduleSummaryExists(millId, year, categoryId)) {
      throw new ScheduleNotFoundException();
    }
  }

  /**
   * Validate that the mill/reporting-year context is viewable, WITHOUT requiring a schedule summary
   * to exist for any category (Schedule 2, UC-SCH2-001 AC4/AC6). Unlike
   * {@link #validateScheduleViewable}, a valid, active mill/year with no saved schedule is NOT a
   * 404 — it is the legitimate "unsaved schedule" state (the legacy
   * {@code Schedule2DAO.getReportSummaryID()} never returns null; it falls back to a new empty
   * summary). The caller (Schedule 2) then serves a 200 empty editable document.
   *
   * <p>Guard order: unknown mill / no report-status row for the year &rarr;
   * {@link ScheduleNotFoundException} (404); mill not active ({@code ACT}) for the year &rarr;
   * {@link MillClosedException} (409). Returns normally when the context is viewable.
   *
   * @param millId the mill id
   * @param year the reporting year
   */
  public void validateMillYearActive(long millId, int year) {
    String millStatus = repository.findMillStatusCodeForYear(millId, year)
        .orElseThrow(ScheduleNotFoundException::new);

    if (!STATUS_ACTIVE.equalsIgnoreCase(millStatus)) {
      throw new MillClosedException();
    }
  }
}
