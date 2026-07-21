package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.StatusDates;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.TrackCodes;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatus;
import ca.bc.gov.nrs.ilcr.millcontext.dto.WorkingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
   * The mills offered on the Home page (Story 1.1, BR-02). Unfiltered read — closed mills included,
   * no per-user association filter (deferred to the auth story, AR4) — so no validation logic here;
   * the {@code validate*} guards above are untouched.
   *
   * @return every mill, ordered by mill number ascending
   */
  public List<MillSummary> listMills() {
    return repository.findAllMills();
  }

  /**
   * The opened reporting years offered on the Home page (Story 1.1, BR-03), most recent first.
   *
   * @return the opened reporting years, ordered by year descending
   */
  public List<ReportingYear> listReportingYears() {
    return repository.findAllReportingYears();
  }

  // Field labels for the required-selection messages — verbatim from home.xhtml's label attributes,
  // in screen order (Mill first).
  private static final String LABEL_MILL = "Mill";
  private static final String LABEL_YEAR = "Reporting Year";

  /**
   * Resolve the Home working context for a selected (mill, year) pair (Story 1.2; UC-SEC-001
   * S01/S06/S07 + S04/S05/S08 validation). This service is the single owner of the validation
   * (AR4/NFR6) — the controller only delegates.
   *
   * <p>Semantics differ deliberately from the schedule-page guards above: a closed mill is a
   * {@code millViewable:false} FLAG (S06), never the 409; a missing status row nulls the statuses
   * (S07), never the 404. Raw request params arrive as Strings so that missing, blank, AND
   * non-numeric values all resolve to the verbatim legacy required-field message — and BOTH fields
   * report together when both are absent (S08), which a typed {@code @RequestParam} cannot do.
   *
   * <p>Track dates mirror legacy {@code UserSessionMB.findMillReportStatus} with one recorded
   * deviation: EACH track selects its date by its OWN status code (legacy's Schedule 11 branch
   * tests the 1–10 code and assigns the 1–10 date variable — a copy-paste bug not reproduced), and
   * the two tracks render independently (AR6).
   *
   * @param millIdParam the raw {@code millId} request param (may be null/blank/non-numeric)
   * @param yearParam the raw {@code year} request param (may be null/blank/non-numeric)
   * @return the resolved working context
   * @throws FieldValuesRequiredException 400 — missing/blank/non-numeric params (S04/S05/S08)
   * @throws MillYearContextNotFoundException 404 — mill not selectable or year not opened
   */
  public WorkingContext resolveWorkingContext(String millIdParam, String yearParam) {
    List<String> missing = new ArrayList<>();
    Long millId = parseAsLong(millIdParam);
    Integer year = parseAsInt(yearParam);
    if (millId == null) {
      missing.add(LABEL_MILL);
    }
    if (year == null) {
      missing.add(LABEL_YEAR);
    }
    if (!missing.isEmpty()) {
      throw new FieldValuesRequiredException(missing);
    }

    MillSummary mill = repository.findSelectableMillById(millId)
        .orElseThrow(MillYearContextNotFoundException::new);
    if (!repository.reportingYearExists(year)) {
      throw new MillYearContextNotFoundException();
    }

    Optional<TrackCodes> codes = repository.findTrackStatusCodes(millId, year);
    Optional<StatusDates> dates = repository.findStatusDates(millId, year);
    String code1To10 = codes.map(TrackCodes::schedules1To10Code).orElse(null);
    String code11 = codes.map(TrackCodes::schedule11Code).orElse(null);

    TrackStatus schedules1To10 = trackStatus(
        code1To10, dates.map(d -> pick1To10Date(code1To10, d)).orElse(null));
    TrackStatus schedule11 = trackStatus(
        code11, dates.map(d -> pickSchedule11Date(code11, d)).orElse(null));

    boolean millViewable = STATUS_ACTIVE.equalsIgnoreCase(mill.millStatusCode());
    return new WorkingContext(
        mill.millId(), mill.millNumber(), mill.millName(), year,
        schedules1To10, schedule11, millViewable);
  }

  /** Null when the code is null (S07 / NULL code column); description resolved from the lookup. */
  private TrackStatus trackStatus(String code, String rawDate) {
    if (code == null) {
      return null;
    }
    String description = repository.findStatusDescription(code).orElse(null);
    return new TrackStatus(code, description, stripDatePrefix(rawDate));
  }

  /** Legacy 1–10 date pick: O→opened, D→draft(started), S→submit(finalized), else→verify(audited). */
  private String pick1To10Date(String code, StatusDates d) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case "O" -> d.open1To10();
      case "D" -> d.draft1To10();
      case "S" -> d.submit1To10();
      default -> d.verify1To10();
    };
  }

  /**
   * Schedule 11 date pick BY ITS OWN CODE (recorded deviation from the legacy cross-track bug):
   * D→silvi draft, S→silvi submit, else→silvi verify (the view has no silvi opened column).
   */
  private String pickSchedule11Date(String code, StatusDates d) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case "D" -> d.draftSilvi();
      case "S" -> d.submitSilvi();
      default -> d.verifySilvi();
    };
  }

  /**
   * Strip the legacy 3-character sort prefix from a view date string (mirrors
   * {@code UserSessionMB.java:374} {@code substring(3)}); blank/absent remainder → null (the
   * frontend renders null as {@code Not Initiated}, Story 1.4).
   */
  private String stripDatePrefix(String raw) {
    if (raw == null || raw.length() <= 3) {
      return null;
    }
    String rest = raw.substring(3);
    return rest.isBlank() ? null : rest;
  }

  private Long parseAsLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Integer parseAsInt(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
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
