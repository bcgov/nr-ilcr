package ca.bc.gov.nrs.ilcr.schedule4.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 4 Check Status result (Story 4.4, AD-5/AD-12) — a read-only MET/ISSUES evaluation that
 * mutates nothing. Unlike the single-level Schedule 1/2 {@code CheckStatusResponse}, Schedule 4 needs
 * a per-location breakdown (S31): {@code locations} carries one {@link LocationCheckResult} per
 * location, and the schedule-level {@code outcome} is {@code "MET"} only when EVERY location passes
 * (all-or-nothing).
 *
 * <p>{@code messages} is the schedule-level banner: one {@code scheduleRequirementsMetMsg} ("All
 * requirements for this schedule have been met") when {@code outcome == "MET"}, empty otherwise (no
 * schedule banner on ISSUES). The service emits bundle keys; the controller resolves the verbatim
 * text (AD-8).
 *
 * @param outcome {@code "MET"} (all locations pass) or {@code "ISSUES"}
 * @param messages the schedule-level message(s) — the MET banner when passing, empty otherwise
 * @param locations the per-location results
 */
public record Schedule4CheckStatusResponse(
    String outcome,
    List<MessageInfo> messages,
    List<LocationCheckResult> locations) {
}
