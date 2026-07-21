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
   * Validate only that the mill/reporting-year context exists and the mill is active — WITHOUT
   * requiring a schedule summary to exist. Used by reads that must render a "not initiated" empty
   * document (200) for a valid, active mill/year that has no saved schedule yet.
   *
   * <p>Guard order (same as {@link #validateScheduleViewable} minus the summary-exists check):
   * <ol>
   *   <li>No per-year context (unknown mill or no report-status row) &rarr;
   *       {@link ScheduleNotFoundException} (404).</li>
   *   <li>Mill not active ({@code ACT}) for the year &rarr; {@link MillClosedException} (409).</li>
   * </ol>
   * Returns normally when the mill/year is a known, active context.
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
