package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.StatusDates;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository.TrackCodes;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatus;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import ca.bc.gov.nrs.ilcr.millcontext.dto.WorkingContext;
import ca.bc.gov.nrs.ilcr.security.JwtRoleChecker;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import ca.bc.gov.nrs.ilcr.util.LegacyDateText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Single owner of mill/reporting-year validation for schedule-workflow endpoints (AD-4). Schedule
 * services call this and never re-check. Closed-mill status codes are the legacy {@code
 * MILL_STATUS_CODES}.
 */
@Service
@Slf4j
public class MillContextService {

  private static final String STATUS_ACTIVE = "ACT";

  // Reused legacy bundle key (messages.properties:37) — the same SUC-001 key Schedule 1's save
  // uses.
  // No new key is added; the text is resolved server-side (AD-8) and never hardcoded in Java.
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";

  private final MillContextRepository repository;
  private final MessageSource messageSource;
  private final JwtRoleChecker roleChecker;

  /**
   * Creates the mill-context service.
   *
   * @param repository the mill/context reads
   * @param messageSource the bundle for server-resolved messages (AD-8)
   * @param roleChecker resolves the caller's role for Story 5.7 mill-scope enforcement
   */
  public MillContextService(
      MillContextRepository repository, MessageSource messageSource, JwtRoleChecker roleChecker) {
    this.repository = repository;
    this.messageSource = messageSource;
    this.roleChecker = roleChecker;
  }

  /**
   * Per-endpoint mill-scope enforcement (Story 5.7, closing 5.5 AC1/FR3). A submitter may only
   * reach a mill they are ACTIVELY associated to; a forged/guessed {@code millId} is rejected with
   * 403 (audited via {@code GlobalExceptionHandler.handleAccessDenied}, Story 5.4). Called from the
   * shared guards so every mill-scoped endpoint inherits it without a per-controller check (AD-4).
   *
   * <ul>
   *   <li><b>Admin</b> ({@code ILCR_ADMIN}) bypasses — tied to no mill (DL-22).
   *   <li><b>Real submitter</b> (a FAM {@code Jwt} principal): denied unless {@code
   *       userHasActiveAssignment(millId, idp_user_id)}; a blank GUID is denied (fail-closed).
   *   <li><b>Mock principal</b> (security off — a non-{@code Jwt} token with no directory GUID):
   *       the check is skipped. Recorded AC6 exemption: the mock is dev-only and the startup guard
   *       forbids it in a deployed environment (FR1), so mill-scope enforcement is exercised by
   *       real identities only. A production principal is always a {@code Jwt}.
   * </ul>
   *
   * @param millId the mill the caller is trying to reach
   * @throws AccessDeniedException 403 — a real submitter not actively associated to {@code millId}
   */
  public void validateMillAccess(long millId) {
    if (roleChecker.hasConcreteRole(Role.ADMIN.name())) {
      return;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Object principal = (auth != null) ? auth.getPrincipal() : null;
    if (!(principal instanceof Jwt jwt)) {
      return; // mock/security-off (AC6 exemption) — no real directory identity to scope by
    }
    String userGuid = JwtPrincipalUtil.getIdpUserId(jwt);
    if (userGuid == null
        || userGuid.isBlank()
        || !repository.userHasActiveAssignment(millId, userGuid)) {
      // Mill/user only — never a token or cost data (NFR3/AD-11).
      log.info("Mill-scope 403: submitter not associated to millId={}", millId);
      throw new AccessDeniedException("Mill is not associated to the caller.");
    }
  }

  /**
   * The mills offered on the Home page, scoped to the caller (Story 5.5, UC-SEC-001/UC-SEC-003).
   *
   * <p>An {@code ILCR_ADMIN} is tied to no mill and sees every listable mill INCLUDING closed
   * (DL-22, legacy admin {@code getMills()} → {@link MillContextRepository#findAllMills()}). An
   * {@code ILCR_SUBMITTER} sees ONLY mills they are actively associated to (legacy {@code
   * getMills(userGuid)} → {@link MillContextRepository#findMillsForUser(String)}); closed
   * associated mills still appear (no status filter, S06). A submitter whose identity cannot be
   * resolved — a blank {@code custom:idp_user_id}, e.g. the dev mock principal, which carries no
   * directory GUID — sees an EMPTY list: fail-closed, never all-mills (a submitter must never see
   * mills that aren't theirs). Replaces the Story 1.1 unfiltered read now that real identity exists
   * (AR4).
   *
   * @param isAdmin whether the caller holds {@code ILCR_ADMIN}
   * @param userGuid the caller's raw {@code custom:idp_user_id} directory GUID (blank if
   *     unavailable)
   * @return the caller-scoped mills, ordered by mill number ascending
   */
  public List<MillSummary> listMills(boolean isAdmin, String userGuid) {
    if (isAdmin) {
      return repository.findAllMills();
    }
    if (userGuid == null || userGuid.isBlank()) {
      return List.of();
    }
    return repository.findMillsForUser(userGuid);
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
   * <p>Semantics differ deliberately from the schedule-page guards above: a closed mill is a {@code
   * millViewable:false} FLAG (S06), never the 409; a missing status row nulls the statuses (S07),
   * never the 404. Raw request params arrive as Strings so that missing, blank, AND non-numeric
   * values all resolve to the verbatim legacy required-field message — and BOTH fields report
   * together when both are absent (S08), which a typed {@code @RequestParam} cannot do.
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

    MillSummary mill =
        repository
            .findSelectableMillById(millId)
            .orElseThrow(MillYearContextNotFoundException::new);
    validateMillAccess(
        millId); // Story 5.7: a submitter can only resolve/save context for own mill.
    if (!repository.reportingYearExists(year)) {
      throw new MillYearContextNotFoundException();
    }

    Optional<TrackCodes> codes = repository.findTrackStatusCodes(millId, year);
    Optional<StatusDates> dates = repository.findStatusDates(millId, year);
    String code1To10 = codes.map(TrackCodes::schedules1To10Code).orElse(null);
    String code11 = codes.map(TrackCodes::schedule11Code).orElse(null);

    TrackStatus schedules1To10 =
        trackStatus(code1To10, dates.map(d -> pick1To10Date(code1To10, d)).orElse(null));
    TrackStatus schedule11 =
        trackStatus(code11, dates.map(d -> pickSchedule11Date(code11, d)).orElse(null));

    boolean millViewable = STATUS_ACTIVE.equalsIgnoreCase(mill.millStatusCode());
    return new WorkingContext(
        mill.millId(),
        mill.millNumber(),
        mill.millName(),
        year,
        schedules1To10,
        schedule11,
        millViewable,
        savedMessage());
  }

  /**
   * The SUC-001 confirmation carried on every 200 (Story 1.3, AC7). Resolves the reused legacy
   * bundle key to its verbatim text via the wired {@code MessageSource} (AD-8) — mirrors how
   * Schedule 1's controllers build their success {@code MessageInfo}. The frontend only DISPLAYS it
   * after a Save.
   */
  private MessageInfo savedMessage() {
    return new MessageInfo(
        MSG_SAVED,
        messageSource.getMessage(MSG_SAVED, null, MSG_SAVED, LocaleContextHolder.getLocale()));
  }

  /** Null when the code is null (S07 / NULL code column); description resolved from the lookup. */
  private TrackStatus trackStatus(String code, String rawDate) {
    if (code == null) {
      return null;
    }
    String description = repository.findStatusDescription(code).orElse(null);
    return new TrackStatus(code, description, stripDatePrefix(rawDate));
  }

  /**
   * Legacy 1–10 date pick: O→opened, D→draft(started), S→submit(finalized), else→verify(audited).
   */
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
   * Strip the legacy 3-character sort prefix from a view date string; blank/absent remainder → null
   * (the frontend renders null as {@code Not Initiated}, Story 1.4). Delegates to the shared rule
   * so the Home banner and the Mill Information report can never disagree about the same date.
   */
  private String stripDatePrefix(String raw) {
    return LegacyDateText.stripPrefix(raw);
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
   *
   * <ol>
   *   <li>No per-year context (unknown mill or no report-status row) &rarr; {@link
   *       ScheduleNotFoundException} (404).
   *   <li>Mill not active ({@code ACT}) for the year &rarr; {@link MillClosedException} (409).
   *   <li>No schedule summary for the category &rarr; {@link ScheduleNotFoundException} (404).
   * </ol>
   *
   * <p>Returns normally when the context is viewable. Legacy mill status is {@code ACT}/{@code
   * CLS}; we whitelist {@code ACT} rather than blacklisting {@code CLS} so any unexpected status is
   * treated as not-viewable rather than silently viewable.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param categoryId the schedule category id (Schedule 1 = {@code "1"})
   */
  public void validateScheduleViewable(long millId, int year, String categoryId) {
    validateMillYearActive(millId, year);

    if (!repository.scheduleSummaryExists(millId, year, categoryId)) {
      log.info(
          "Schedule 404: no category-{} summary for millId={} year={} (guard 2)",
          categoryId,
          millId,
          year);
      throw new ScheduleNotFoundException();
    }
  }

  /**
   * Validate that the mill/reporting-year context exists and the mill is active — the shared guards
   * 1–2 of every schedule endpoint, WITHOUT requiring a schedule summary to exist. Callers that
   * need only these guards are the ones for which zero saved data is a valid 200: document reads
   * rendering a "not initiated" empty document, and list schedules, where legacy fires "Schedule
   * not found." only when the {@code ILCR_MILL_REPORT_STATUS} row is absent ({@code
   * Schedule11MB.init()} &rarr; {@code scheduleNotFound}, UC-SCH11-001 S12/S13), never on an empty
   * list. {@link #validateScheduleViewable} layers the summary-exists guard on top.
   *
   * <p>Guard order:
   *
   * <ol>
   *   <li>No per-year context (unknown mill or no report-status row) &rarr; {@link
   *       ScheduleNotFoundException} (404).
   *   <li>Mill not active ({@code ACT}) for the year &rarr; {@link MillClosedException} (409).
   * </ol>
   *
   * <p>Returns normally when the mill/year is a known, active context.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @throws ScheduleNotFoundException 404 — no {@code ILCR_MILL_REPORT_STATUS} row (ERR-003)
   * @throws MillClosedException 409 — mill not active ({@code ACT}) for the year (ERR-002)
   */
  public void validateMillYearActive(long millId, int year) {
    validateMillAccess(millId); // Story 5.7: submitter↔mill scope, before any per-year read.
    String millStatus =
        repository
            .findMillStatusCodeForYear(millId, year)
            .orElseThrow(
                () -> {
                  // Diagnostic (mill/year only — no cost/volume, AD-11): no ACT/CLS status row was
                  // found for
                  // this mill/year, i.e. the ILCR_MILL_STATUS_XREF ⋈ ILCR_MILL_REPORT_STATUS lookup
                  // was empty.
                  log.info(
                      "Schedule 404: no mill/year status row for millId={} year={} (guard 1)",
                      millId,
                      year);
                  return new ScheduleNotFoundException();
                });

    if (!STATUS_ACTIVE.equalsIgnoreCase(millStatus)) {
      log.info(
          "Schedule 409: mill not ACT for millId={} year={} (status={})", millId, year, millStatus);
      throw new MillClosedException();
    }
  }

  /**
   * Raw-parameter overload of {@link #validateMillYearActive(long, int)} for endpoints that take
   * mill/year straight off the query string (introduced Story 25.1 AC3 / S11). Params arrive as
   * Strings so missing, blank, AND non-numeric values all resolve to the ONE verbatim legacy
   * ERR-001 message — a typed {@code @RequestParam} cannot produce it (the {@code
   * resolveWorkingContext} idiom; legacy shows the combined message, not per-field texts, when the
   * schedule page lacks a session context — {@code schedule11.xhtml:11–26}).
   *
   * @param millIdParam the raw {@code millId} request param (may be null/blank/non-numeric)
   * @param yearParam the raw {@code year} request param (may be null/blank/non-numeric)
   * @return the parsed (millId, year) pair for downstream reads
   * @throws MillYearNotSelectedException 400 — missing/blank/non-numeric params (ERR-001)
   * @throws ScheduleNotFoundException 404 — no {@code ILCR_MILL_REPORT_STATUS} row (ERR-003)
   * @throws MillClosedException 409 — mill not active ({@code ACT}) for the year (ERR-002)
   */
  public MillYearContext validateMillYearActive(String millIdParam, String yearParam) {
    Long millId = parseAsLong(millIdParam);
    Integer year = parseAsInt(yearParam);
    if (millId == null || year == null) {
      throw new MillYearNotSelectedException();
    }
    validateMillYearActive(millId, year);
    return new MillYearContext(millId, year);
  }

  /**
   * A validated (mill, year) pair parsed from raw request params by {@link
   * #validateMillYearActive(String, String)}.
   *
   * @param millId the parsed mill id
   * @param year the parsed reporting year
   */
  public record MillYearContext(long millId, int year) {}

  /**
   * The report title-block string for a mill — {@code MILL_NAME + "-" + MILL_NUMBER} (legacy {@code
   * Schedule*Report.createReportDataSource}), the same value Schedule 9's embedded-SQL template
   * renders, so every combined-PDF section's header block reads identically. Resolves through the
   * existing selectable-mill read (mill/year context is already validated by the caller); returns
   * just the id if the mill row cannot be resolved, rather than failing the render.
   *
   * @param millId the validated mill id
   * @return the {@code name-number} title block
   */
  public String resolveMillTitleBlock(long millId) {
    return repository
        .findSelectableMillById(millId)
        .map(mill -> mill.millName() + "-" + mill.millNumber())
        .orElse(String.valueOf(millId));
  }

  /**
   * The Schedule 11 track's status code ({@code MILL_SILVICULTUR_STATUS_CODE}) for a mill/year —
   * millcontext is the single owner of the track-status read (AD-9); schedule services never query
   * the status row themselves. NEVER the 1–10 track's {@code ILCR_MILL_REPORT_STATUS_CODE} (AR7).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the silviculture track code ({@code D}/{@code S}/{@code V}, dead {@code O} passes
   *     through per A-8); empty when no status row exists OR its silviculture code column is null
   */
  public Optional<String> findSchedule11TrackStatusCode(long millId, int year) {
    return repository.findTrackStatusCodes(millId, year).map(TrackCodes::schedule11Code);
  }

  /**
   * BOTH tracks' status codes for a mill/year in one read (Story 15.1) — the cheap shape for a
   * caller that has already passed {@link #validateMillYearActive(long, int)} and needs the codes
   * without the descriptions, dates and the four-to-seven queries {@link #resolveWorkingContext}
   * spends on them. Codes only, never a per-schedule {@code findTrackStatus}: there are already
   * eleven of those in the schedule repositories and this is the read that stops the count.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return both codes (either may be null); empty when no {@code ILCR_MILL_REPORT_STATUS} row
   *     exists
   */
  public Optional<TrackStatusCodes> findTrackStatusCodes(long millId, int year) {
    return repository
        .findTrackStatusCodes(millId, year)
        .map(codes -> new TrackStatusCodes(codes.schedules1To10Code(), codes.schedule11Code()));
  }
}
