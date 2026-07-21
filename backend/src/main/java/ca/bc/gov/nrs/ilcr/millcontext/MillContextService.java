package ca.bc.gov.nrs.ilcr.millcontext;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Single owner of mill/reporting-year validation for schedule-workflow endpoints (AD-4). Schedule
 * services call this and never re-check. Closed-mill status codes are the legacy {@code MILL_STATUS_CODES}.
 */
@Service
@Slf4j
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
        .orElseThrow(() -> {
          // Diagnostic (mill/year only — no cost/volume, AD-11): no ACT/CLS status row was found for
          // this mill/year, i.e. the ILCR_MILL_STATUS_XREF ⋈ ILCR_MILL_REPORT_STATUS lookup was empty.
          log.info("Schedule 404: no mill/year status row for millId={} year={} (guard 1)", millId, year);
          return new ScheduleNotFoundException();
        });

    if (!STATUS_ACTIVE.equalsIgnoreCase(millStatus)) {
      log.info("Schedule 409: mill not ACT for millId={} year={} (status={})", millId, year, millStatus);
      throw new MillClosedException();
    }

    if (!repository.scheduleSummaryExists(millId, year, categoryId)) {
      log.info("Schedule 404: no category-{} summary for millId={} year={} (guard 2)",
          categoryId, millId, year);
      throw new ScheduleNotFoundException();
    }
  }
}
