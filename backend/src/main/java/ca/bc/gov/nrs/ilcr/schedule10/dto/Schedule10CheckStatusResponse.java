package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.CheckStatusOutcome;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import java.util.List;

/**
 * Result of {@code POST /api/v1/schedule10/check-status}. Read-only: the endpoint mutates nothing
 * and is gated on {@code VIEW_SCHEDULE} rather than {@code EDIT_SCHEDULE}, so a submitted or
 * verified schedule can still be checked.
 *
 * <p><strong>Two mutually exclusive branches, mirroring legacy.</strong> When everything passes,
 * {@code outcome} is {@code "MET"}, {@code messages} carries the single schedule-level banner, and
 * {@code pages} is EMPTY — legacy's pass branch never enters its per-row loop, so emitting per-row
 * results here would invent output. When anything is outstanding, {@code outcome} is {@code
 * "ISSUES"}, {@code messages} is empty, and every visible page and road detail appears with its own
 * issues.
 *
 * <p>A schedule with zero pages is a vacuous {@code "MET"} — legacy's loop simply never runs.
 *
 * <p>Scope is always the whole schedule for one mill and year. Legacy has no per-page mode, and
 * neither does any other schedule in this application; a scope parameter would be new behaviour
 * rather than parity.
 *
 * @param outcome {@code "MET"} or {@code "ISSUES"}
 * @param messages the schedule-level banner, populated only when {@code outcome} is {@code "MET"}
 * @param pages the per-page outcomes, populated only when {@code outcome} is {@code "ISSUES"}
 */
public record Schedule10CheckStatusResponse(
    String outcome, List<MessageInfo> messages, List<PageCheckResult> pages) {

  /**
   * {@code outcome} when every checked requirement passes.
   *
   * <p>Kept as a member of this record (it is existing public API and several tests name it) but
   * sourced from {@link CheckStatusOutcome} since Story 15.0, so the token has ONE definition
   * rather than six.
   */
  public static final String MET = CheckStatusOutcome.MET;

  /** {@code outcome} when at least one requirement is outstanding. */
  public static final String ISSUES = CheckStatusOutcome.ISSUES;
}
