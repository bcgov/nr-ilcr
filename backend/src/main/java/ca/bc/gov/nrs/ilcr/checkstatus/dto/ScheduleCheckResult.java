package ca.bc.gov.nrs.ilcr.checkstatus.dto;

/**
 * One schedule's entry in the Check Status sweep (Story 15.1 AC 1/2/3).
 *
 * <p>{@code verdict} is the schedule's OWN check-status response, exactly as its own endpoint
 * returns it — one of the two shipped families, {@code {outcome, messages, …}} for Schedules
 * 2/4/5/6/8/10 or {@code {requirementsMet, errors, …}} for Schedules 1/3/7A/7B/9/11, with every
 * message's text already resolved. The sweep adds nothing to it and reads nothing out of it except
 * the validity signal, which is why it is typed as {@code Object} here: the families are
 * deliberately NOT unified (Story 29.12 refused to, and AC 2 extends the shipped reality rather
 * than re-pinning it), and a client that already renders a schedule's own check-status response can
 * render its entry here with the same code. There is no {@code severity}; a client derives it from
 * {@code requirementsMet} and from which list an entry lands in, as it does for every schedule
 * today.
 *
 * @param schedule the schedule code as the legacy UI names it — {@code "1"} … {@code "6"}, {@code
 *     "7A"}, {@code "7B"}, {@code "8"} … {@code "11"}
 * @param requirementsMet the one uniform validity verdict, normalized from the schedule's own
 *     signal (AC 3) — Schedule 7B's is its OWN, never 7A's (AC 4)
 * @param verdict the schedule's own check-status response, verbatim
 */
public record ScheduleCheckResult(String schedule, boolean requirementsMet, Object verdict) {}
