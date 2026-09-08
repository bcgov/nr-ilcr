package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.checkstatus.ScheduleCheck.ScheduleCheckAdapter;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.TrackCheckResult;
import ca.bc.gov.nrs.ilcr.dto.base.CheckStatusOutcome;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckStatusResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The Check Status sweep (Story 15.1, AD-5): every schedule's own validation called — never
 * re-implemented — for one mill/year, partitioned by track, read-only. This is the domain-level
 * aggregation both the page ({@code GET /api/v1/check-status}) and Story 15.3's submit gate call;
 * the gate re-runs {@link #checkTrack} for Schedules 1–10 inside its own write transaction and
 * leaves Schedule 11 untouched (UC-CHK-002 BR-08), which is why the two tracks are answerable
 * independently.
 *
 * <p><strong>The caller owns the mill/year guard.</strong> None of the twelve entry points
 * validates its own context, and Schedules 4, 5, 6, 8, 10 and 11 all report zero rows as a vacuous
 * MET — so a sweep over an absent or closed mill-year would answer COMPLETE instead of 404/409.
 * {@code MillContextService.validateMillYearActive} must run exactly once before this is called
 * (the controller does; 15.3 will inside its transaction). This service adds no per-schedule guard:
 * twelve redundant context reads on the path the correct-and-re-check loop presses repeatedly.
 *
 * <p><strong>Deliberately NOT {@code @Transactional}.</strong> Ten of the twelve validations manage
 * their own {@code readOnly} transaction (the six resolvers delegate to one; Schedules 1 and 3 have
 * none), so twelve sequential calls hold one pooled connection at a time. A method-wide transaction
 * would pin one connection for the whole fan-out — the {@code PrintService.render} trap, whose
 * conclusion still holds even though its arithmetic predates the separate reporting pool (Story
 * 29.1): don't wrap a fan-out.
 *
 * <p><strong>Failure isolation — decided: a failure anywhere fails the whole call.</strong> Four of
 * the twelve resolve message text with no default (Schedules 5, 6, 9 and 10) and throw {@code
 * NoSuchMessageException} on a missing or renamed bundle key; the other eight echo the key. This
 * service catches nothing: a bundle defect is a programming error, and degrading the affected
 * schedule to "unknown" — or worse, omitting it — would report a report as more complete than
 * anyone has verified, the same silent direction the missing-guard trap fails in. The convergence
 * of the twelve paths on one resolution form is tracked in the deferred-work register and is not
 * this story's decision to pre-empt.
 *
 * <p>Owns zero schedule SQL (AD-14) and logs only mill/year and the two roll-ups — never a cost or
 * volume value, never a per-row WARN for a stored-data condition (AD-11/NFR3).
 */
@Service
@Slf4j
public class CheckStatusSweepService {

  private final MillContextService millContextService;
  private final List<ScheduleCheck> checks;

  /**
   * Wires the twelve in-process entry points Story 15.0 made callable, each adapted once to the
   * {@link ScheduleCheck} port with its own validity signal (AC 3), in legacy tab order.
   */
  public CheckStatusSweepService(
      MillContextService millContextService,
      Schedule1Service schedule1Service,
      Schedule2CheckStatusResolver schedule2,
      Schedule3Service schedule3Service,
      Schedule4CheckStatusResolver schedule4,
      Schedule5CheckStatusResolver schedule5,
      Schedule6CheckStatusResolver schedule6,
      Schedule7aService schedule7aService,
      Schedule7bService schedule7bService,
      Schedule8CheckStatusResolver schedule8,
      Schedule9Service schedule9Service,
      Schedule10CheckStatusResolver schedule10,
      Schedule11Service schedule11Service) {
    this.millContextService = millContextService;
    this.checks =
        List.of(
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_1,
                schedule1Service::checkSchedule1Status,
                Schedule1CheckStatusResponse::requirementsMet),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_2,
                schedule2::checkStatus,
                response -> outcomeMet(response.outcome())),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_3,
                schedule3Service::checkSchedule3Status,
                Schedule3CheckStatusResponse::requirementsMet),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_4,
                schedule4::checkStatus,
                response -> outcomeMet(response.outcome())),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_5,
                schedule5::checkStatus,
                response -> outcomeMet(response.outcome())),
            // The stored-data path: Schedule 6's endpoint validates the posted screen instead, so
            // this sweep is checkStatusStored's only caller.
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_6,
                schedule6::checkStatusStored,
                response -> outcomeMet(response.outcome())),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_7A,
                schedule7aService::checkStatus,
                Schedule7aCheckStatusResponse::requirementsMet),
            // 7B's OWN verdict (AC 4). Legacy's checkStatus.xhtml:96-99 gated the 7B tab's banner
            // on isSchedule7aValid(); only the display was wrong — the submit gate always read 7B.
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_7B,
                schedule7bService::checkStatus,
                Schedule7bCheckStatusResponse::requirementsMet),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_8,
                schedule8::checkStatus,
                response -> outcomeMet(response.outcome())),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_9,
                schedule9Service::checkStatus,
                Schedule9CheckStatusResponse::requirementsMet),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_10,
                schedule10::checkStatus,
                response -> outcomeMet(response.outcome())),
            new ScheduleCheckAdapter<>(
                CheckedSchedule.SCHEDULE_11,
                schedule11Service::checkStatus,
                Schedule11CheckStatusResponse::requirementsMet));
  }

  /**
   * Sweep both tracks for a validated mill/year: the twelve verdicts in legacy order, partitioned
   * 11 + 1, plus both persisted track status codes read once from millcontext.
   *
   * @param millId the mill id (context already validated by the caller — see the class javadoc)
   * @param year the reporting year
   * @return the full sweep
   */
  public CheckStatusSweepResponse sweep(long millId, int year) {
    Optional<TrackStatusCodes> codes = millContextService.findTrackStatusCodes(millId, year);
    TrackCheckResult schedules1To10 =
        TrackCheckResult.of(
            codes.map(TrackStatusCodes::schedules1To10Code).orElse(null),
            checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, millId, year));
    TrackCheckResult schedule11 =
        TrackCheckResult.of(
            codes.map(TrackStatusCodes::schedule11Code).orElse(null),
            checkTrack(ScheduleTrack.SCHEDULE_11, millId, year));
    log.debug(
        "Check Status sweep for millId={} year={}: 1-10 met={}, 11 met={}",
        millId,
        year,
        schedules1To10.requirementsMet(),
        schedule11.requirementsMet());
    return new CheckStatusSweepResponse(millId, year, schedules1To10, schedule11);
  }

  /**
   * Evaluate one track's schedules, in legacy order, touching nothing on the other track. This is
   * the ten-schedule gate Story 15.3 re-runs at submission (UC-CHK-002 BR-02) and Epics 17/18
   * reuse.
   *
   * @param track the track to evaluate
   * @param millId the mill id (context already validated by the caller)
   * @param year the reporting year
   * @return the per-schedule verdicts for that track only
   */
  public List<ScheduleCheckResult> checkTrack(ScheduleTrack track, long millId, int year) {
    Objects.requireNonNull(track, "track");
    List<ScheduleCheckResult> results = new ArrayList<>();
    for (ScheduleCheck check : checks) {
      if (check.schedule().track() == track) {
        results.add(check.check(millId, year));
      }
    }
    return List.copyOf(results);
  }

  /** The {@code outcome} family's validity signal: met exactly when the token is {@code MET}. */
  private static boolean outcomeMet(String outcome) {
    return CheckStatusOutcome.MET.equals(outcome);
  }
}
