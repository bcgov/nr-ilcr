package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.TrackCheckResult;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import ca.bc.gov.nrs.ilcr.security.ReportSubmission;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submits the Schedules 1&ndash;10 track (UC-CHK-002, FR5) &mdash; the modern shape of legacy
 * {@code CheckStatusMB.submitReport("S")} plus {@code SubmitReportDAO.submitReport()}, in ONE write
 * transaction that holds the status row from its first statement to its commit.
 *
 * <p><strong>The order is the feature.</strong> Inside the transaction: (1) lock the status row;
 * (2) refuse unless the caller may submit at the row's current status; (3) re-run all eleven
 * Schedule 1&ndash;10 validations and refuse unless every one is met; (4) write status {@code S};
 * (5) touch every Schedule 1&ndash;10 row's audit columns; (6) advance the ten category rows to
 * {@code A}. Steps 4&ndash;6 must run in that order because the delivery audit triggers stamp the
 * {@code S} snapshot Story 16.2 reads only while the row's category is still {@code D} and the
 * track is already {@code S} &mdash; legacy got it by accident of its loop; this class gets it on
 * purpose. Legacy interleaved touch-then-advance per category ({@code SubmitReportDAO:74-120}); the
 * sequence here is trigger-equivalent because each row's trigger reads only its own category's
 * state.
 *
 * <p>The lock is taken BEFORE the eleven checks (D10), so no write that also locks the status row
 * &mdash; every schedule's editability gate, since Story 15.3 &mdash; can land between the gate and
 * the transition; Story 15.1's don't-wrap-a-fan-out warning does not apply because all eleven
 * checks join this transaction on its one connection (none uses {@code REQUIRES_NEW}, a thread or a
 * raw connection). The cost is lock duration for the length of the fan-out, and only writers to
 * this mill/year wait behind it.
 *
 * <p>Two legacy defects are not reproduced: the guard-{@code false} path that left the Hibernate
 * transaction open ({@code SubmitReportDAO:69-72}) &mdash; every refusal here is a rollback or no
 * write at all &mdash; and the impossible double message when {@code saveSession()} failed after
 * the commit ({@code CheckStatusMB:278-283}) &mdash; the success message is issued only after
 * commit. Neither is a "silent success": legacy's service threw on the guard's {@code false} and
 * the bean showed the error, exactly as this class does.
 *
 * <p>Logs mill/year and the failure class only &mdash; never a cost or volume value, never a
 * directory identifier (AD-11).
 */
@Service
@Slf4j
public class ReportTrackTransitionService {

  private final MillContextService millContextService;
  private final CheckStatusSweepService sweepService;
  private final ReportSubmission reportSubmission;
  private final MillUserXrefRepository millUserXrefRepository;
  private final ReportTrackTransitionRepository repository;

  /** Wires the locked status read, the ten-schedule gate, the offer rule and the one writer. */
  public ReportTrackTransitionService(
      MillContextService millContextService,
      CheckStatusSweepService sweepService,
      ReportSubmission reportSubmission,
      MillUserXrefRepository millUserXrefRepository,
      ReportTrackTransitionRepository repository) {
    this.millContextService = millContextService;
    this.sweepService = sweepService;
    this.reportSubmission = reportSubmission;
    this.millUserXrefRepository = millUserXrefRepository;
    this.repository = repository;
  }

  /**
   * Draft &rarr; Submitted for the Schedules 1&ndash;10 track of a mill/year whose context the
   * caller has already validated ({@code MillContextService.validateMillYearActive}, AD-4 &mdash;
   * not repeated here; the locked read is the in-transaction existence check). Schedule 11's status
   * column and category row {@code '11'} are never touched (BR-08).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param authentication the caller &mdash; decides whether Submit is offered at this status and
   *     which {@code ILCR_MILL_USER_XREF} row becomes the report's licensee
   * @param user the caller's audit name ({@code Authentication.getName()})
   * @return the legacy bundle key of the success message
   * @throws ScheduleNotFoundException no status row for the mill/year (the controller re-keys it)
   * @throws ReportTransitionRejectedException 409 {@code submitNotDraftErrorMsg} &mdash; the track
   *     is not at Draft
   * @throws ReportNotSubmittedException 409 &mdash; at least one of the eleven checks fails
   * @throws ReportSubmissionException 500 &mdash; a write failed or affected no row; rolled back
   */
  @Transactional
  public String submit(long millId, int year, Authentication authentication, String user) {
    ScheduleTrack track = ScheduleTrack.SCHEDULES_1_TO_10;

    TrackStatusCodes codes =
        millContextService
            .lockTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);
    String current = codes.schedules1To10Code();
    TrackTransition transition =
        TrackTransition.resolve(current, TrackTransition.SUBMIT.to())
            .filter(TrackTransition.SUBMIT::equals)
            .orElseThrow(() -> new ReportTransitionRejectedException(TrackTransition.SUBMIT));
    if (!reportSubmission.canSubmit(authentication, current)) {
      log.debug("Submit refused for millId={} year={}: track not at Draft", millId, year);
      throw new ReportTransitionRejectedException(TrackTransition.SUBMIT);
    }

    try {
      TrackCheckResult gate =
          TrackCheckResult.of(current, sweepService.checkTrack(track, millId, year));
      if (!gate.requirementsMet()) {
        log.debug("Submit refused for millId={} year={}: validation gate not met", millId, year);
        throw new ReportNotSubmittedException();
      }

      Licensee licensee = resolveLicensee(authentication, millId);
      requireOneRow(
          repository.updateTrackStatus(
              millId, year, current, transition.to(), licensee.millId(), licensee.userGuid(), user),
          millId,
          year,
          "status row");
      touchEveryScheduleRow(millId, year, user);
      for (String categoryId : track.categoryIds()) {
        requireOneRow(
            repository.advanceCategoryState(
                millId, year, categoryId, transition.categoryState(), user),
            millId,
            year,
            "category row '" + categoryId + "'");
      }
    } catch (DataAccessException ex) {
      log.error(
          "Submit failed for millId={} year={}: {}",
          millId,
          year,
          NestedExceptionUtils.getMostSpecificCause(ex).toString());
      throw new ReportSubmissionException();
    }
    log.info("Schedules 1-10 submitted for millId={} year={}", millId, year);
    return transition.successKey();
  }

  /**
   * The thirteen Schedule 1&ndash;10 row families, in legacy tab order, audit columns only. Each
   * statement is scoped by its schedule's own mill/year predicate; zero rows is normal for a
   * schedule with no data.
   */
  private void touchEveryScheduleRow(long millId, int year, String user) {
    repository.touchReportSummaries(millId, year, user);
    repository.touchReportSummaryCostDetails(millId, year, user);
    repository.touchTransportationReports(millId, year, user);
    repository.touchTransportationCostDetails(millId, year, user);
    repository.touchCamps(millId, year, user);
    repository.touchCampCostDetails(millId, year, user);
    repository.touchRoadMaintenanceReports(millId, year, user);
    repository.touchRoadMaintenanceCostDetails(millId, year, user);
    repository.touchBridges(millId, year, user);
    repository.touchBridgeCostDetails(millId, year, user);
    repository.touchCulverts(millId, year, user);
    repository.touchCulvertCostDetails(millId, year, user);
    repository.touchTreeToTruckReports(millId, year, user);
    repository.touchTreeToTruckDetailReports(millId, year, user);
    repository.touchTreeToTruckRateDetails(millId, year, user);
    repository.touchContractualWorkReports(millId, year, user);
    repository.touchContractualWorkCostDetails(millId, year, user);
    repository.touchRoadConstructionReports(millId, year, user);
    repository.touchRoadConstructionDetails(millId, year, user);
    repository.touchRoadConstructionCostDetails(millId, year, user);
  }

  /**
   * The report's licensee, as legacy recorded it ({@code SubmitReportDAO:405-410}): the caller's
   * {@code ILCR_MILL_USER_XREF} row for this mill, or NULLs when there is none. The directory GUID
   * comes from the {@code custom:idp_user_id} claim of a real principal, or from the typed dev/e2e
   * {@link MockUserPrincipal} &mdash; deliberately not from {@code getName()}, which is the short
   * audit name.
   */
  private Licensee resolveLicensee(Authentication authentication, long millId) {
    String userGuid = directoryGuid(authentication);
    if (userGuid == null || userGuid.isBlank()) {
      return Licensee.NONE;
    }
    return millUserXrefRepository
        .findAssignment(millId, userGuid)
        .map(xref -> new Licensee(xref.millId(), xref.userGuid()))
        .orElse(Licensee.NONE);
  }

  private static String directoryGuid(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof Jwt jwt) {
      return JwtPrincipalUtil.getIdpUserId(jwt);
    }
    if (principal instanceof MockUserPrincipal mock) {
      return mock.userGuid();
    }
    return null;
  }

  private static void requireOneRow(int rowsAffected, long millId, int year, String what) {
    if (rowsAffected != 1) {
      log.error(
          "Submit failed for millId={} year={}: {} update affected {} rows",
          millId,
          year,
          what,
          rowsAffected);
      throw new ReportSubmissionException();
    }
  }

  /**
   * The {@code LICENSEE_MILL_ID} / {@code LICENSEE_USER_GUID} pair to record; both null when none.
   */
  private record Licensee(Long millId, String userGuid) {
    static final Licensee NONE = new Licensee(null, null);
  }
}
