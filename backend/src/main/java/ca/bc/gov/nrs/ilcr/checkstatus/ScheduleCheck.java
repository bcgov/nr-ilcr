package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import java.util.function.Predicate;

/**
 * The readiness port (Story 15.1 AC 10): one schedule's Check Status, callable with only a mill and
 * a year, answering in the one shape the sweep iterates over.
 *
 * <p><strong>Why a port and not a twelve-branch switch.</strong> Story 15.0 made the twelve
 * validations callable in process but not uniform: four method names ({@code checkSchedule1Status},
 * {@code checkStatus}, {@code checkStatusStored}, …), twelve return types and two DTO families. A
 * sweep written as a switch over those would restate that irregularity once per consumer — the
 * page, the submit gate (15.3) and the admin transitions (Epics 17/18). Each schedule is adapted
 * here ONCE, in one line, and every consumer iterates.
 *
 * <p><strong>This is orthogonal to Story 29.12.</strong> 29.12 refused to unify the response DTOs,
 * and this does not: {@link ScheduleCheckAdapter} returns each schedule's own response, untouched,
 * as the {@code verdict} — the adapter only adds the code it reports under and the normalized
 * validity. The two families survive on the wire exactly as shipped (AC 2).
 */
interface ScheduleCheck {

  /**
   * Which schedule this port evaluates.
   *
   * @return the schedule
   */
  CheckedSchedule schedule();

  /**
   * Evaluate the schedule for a mill/year whose context the caller has already validated.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @return the schedule's verdict in the sweep's uniform shape
   */
  ScheduleCheckResult check(long millId, int year);

  /**
   * One schedule's own check-status method, whatever it happens to be called.
   *
   * @param <R> the schedule's own response type
   */
  @FunctionalInterface
  interface Evaluation<R> {
    R evaluate(long millId, int year);
  }

  /**
   * The per-schedule adapter: the schedule's own method plus its own validity signal.
   *
   * <p>The validity predicate is the AC 3 normalization made explicit. Legacy read validity three
   * different ways — {@code isPassedCheckStatus()} for 1/2/3/4/5/6/7A/7B/11, {@code
   * isScheduleValid()} alone for 8, and {@code isScheduleValid() && isScheduleValidated()} for 9
   * and 10 ({@code CheckStatusMB.java:531,540,622}). The modern services have already folded each
   * of those into their own response, which leaves two shapes to read: the {@code requirementsMet}
   * boolean on Schedules 1/3/7A/7B/9/11, and the {@code outcome} token on Schedules 2/4/5/6/8/10,
   * which is met exactly when it equals {@code CheckStatusOutcome.MET}.
   *
   * @param <R> the schedule's own response type
   * @param schedule the schedule this adapter evaluates
   * @param evaluation the schedule's own check-status method
   * @param met how to read that response's own validity signal
   */
  record ScheduleCheckAdapter<R>(
      CheckedSchedule schedule, Evaluation<R> evaluation, Predicate<R> met)
      implements ScheduleCheck {

    @Override
    public ScheduleCheckResult check(long millId, int year) {
      R verdict = evaluation.evaluate(millId, year);
      return new ScheduleCheckResult(schedule.code(), met.test(verdict), verdict);
    }
  }
}
