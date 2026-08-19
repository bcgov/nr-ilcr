package ca.bc.gov.nrs.ilcr.reportingyear;

import ca.bc.gov.nrs.ilcr.reportingyear.dto.OpenReportingYearResult;
import ca.bc.gov.nrs.ilcr.reportingyear.dto.ReportingYearAdminView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a reporting year (UC-RY-001) — the annual-cycle trigger. In one transaction it creates the
 * reporting period and, for every active mill, the report-status row (both tracks, Draft) that the
 * mill/year guards read; on any failure nothing commits (S08). The per-category report records the
 * legacy {@code saveReportCategory} wrote to {@code ILCR_REPORT_CATEGORY} are NOT created here: that
 * table is not modelled anywhere in this codebase and its shape is unconfirmed against the delivery
 * database, so seeding it is deferred to the Task-1 delivery-DB gate rather than guessed.
 *
 * <p>Per active mill it also seeds the per-category records (11 categories, Draft, reportable-detail Y)
 * the delivery DB pre-creates on open (verified against DEV); {@code ILCR_REPORT_SUMMARY} is left for
 * first schedule save.
 *
 * <p>Two entry paths (BR-05/BR-07): recurring ({@code max + 1}) when a year already exists, and
 * first-time setup (an administrator-selected year within {@code currentYear - 2 .. currentYear + 1})
 * when none does. Zero active mills blocks the recurring path (S03) but is allowed on first-time setup
 * (S07, recorded decision D-1) so the first year can be opened before any mill is activated.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class ReportingYearService {

  private static final String STATUS_DRAFT = "D";
  private static final String NOT_COMPLETED = "N";
  private static final int START_YEAR_LOOKBACK = 2;
  private static final int START_YEAR_LOOKAHEAD = 1;
  // The 11 schedule categories the delivery DB pre-seeds per active mill on open (verified against DEV).
  private static final List<String> CATEGORY_IDS =
      List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");

  private final ReportingYearRepository repository;
  private final Clock clock;

  @Autowired
  public ReportingYearService(ReportingYearRepository repository) {
    this(repository, Clock.systemDefaultZone());
  }

  ReportingYearService(ReportingYearRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** The state the admin page renders: open years, the recurring next year, and the first-time options. */
  public ReportingYearAdminView view() {
    List<Integer> openYears = repository.findOpenYears();
    boolean firstTime = openYears.isEmpty();
    Integer nextYear = firstTime ? null : openYears.get(0) + 1;
    List<Integer> startYears = firstTime ? selectableStartYears() : List.of();
    return new ReportingYearAdminView(openYears, nextYear, firstTime, startYears);
  }

  /**
   * Open the next reporting year and initialize every active mill's report-status (both tracks, Draft).
   *
   * @param requestedYear the administrator-selected starting year (first-time setup only; ignored on
   *     the recurring path)
   * @param user the acting administrator (audit)
   * @return the year created and how many active mills were initialized
   */
  @Transactional
  public OpenReportingYearResult open(Integer requestedYear, String user) {
    Integer maxYear = repository.findMaxReportYear();
    boolean firstTime = maxYear == null;
    int targetYear = firstTime ? resolveFirstTimeYear(requestedYear) : maxYear + 1;

    if (repository.reportingYearExists(targetYear)) {
      throw ReportingYearException.yearAlreadyOpen();
    }

    List<Long> activeMillIds = repository.findActiveMillIds();
    if (!firstTime && activeMillIds.isEmpty()) {
      throw ReportingYearException.noActiveMills();
    }

    LocalDate today = LocalDate.now(clock);
    repository.insertReportingPeriod(targetYear, today, LocalDate.of(targetYear, 12, 31), user);
    for (long millId : activeMillIds) {
      repository.insertMillReportStatus(
          targetYear, millId, STATUS_DRAFT, STATUS_DRAFT, NOT_COMPLETED, user);
      for (String categoryId : CATEGORY_IDS) {
        repository.insertReportCategory(targetYear, millId, categoryId, user);
      }
    }

    log.info("Reporting year opened: {} initialized {} active mill(s) by {}",
        targetYear, activeMillIds.size(), user);
    return new OpenReportingYearResult(targetYear, activeMillIds.size());
  }

  private int resolveFirstTimeYear(Integer requestedYear) {
    if (requestedYear == null || !selectableStartYears().contains(requestedYear)) {
      throw ReportingYearException.invalidStartYear();
    }
    return requestedYear;
  }

  private List<Integer> selectableStartYears() {
    int current = LocalDate.now(clock).getYear();
    return java.util.stream.IntStream
        .rangeClosed(current - START_YEAR_LOOKBACK, current + START_YEAR_LOOKAHEAD)
        .boxed()
        .toList();
  }
}
